package io.github.hmqyhm.focuspenpro.hook

import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.os.Looper
import android.os.SystemClock
import android.view.View
import de.robv.android.xposed.XposedHelpers
import java.util.WeakHashMap
import kotlin.math.floor
import kotlin.random.Random

/** Temporarily recolors Xiaomi's verified LaserView paints and restores them fail-open. */
internal object LaserBrushColorController {
    data class ColorSpec(
        val colors: List<Int>,
        val cycleMs: Long = 0L,
        val randomSlowestCycleMs: Long = 0L,
        val spatial: Boolean = false,
    ) {
        val animated: Boolean get() = !spatial && colors.size >= 2 && cycleMs > 0L
    }

    private data class PaintState(
        val paint: Paint,
        val originalColor: Int,
        val originalShader: Shader?,
    )

    private data class ViewState(
        val paints: List<PaintState>,
        var spec: ColorSpec,
        var animationRunning: Boolean = false,
        var spatialApplied: Boolean = false,
        var animationPhase: Double = 0.0,
        var lastAnimationUptime: Long = 0L,
        var activeCycleMs: Long = spec.cycleMs,
        var activeColorSegment: Int = 0,
    )

    private val states = WeakHashMap<Any, ViewState>()

    fun update(
        laserView: Any,
        requested: ColorSpec?,
        anchorX: Float? = null,
        anchorY: Float? = null,
    ) {
        if (requested == null || requested.colors.isEmpty()) {
            restore(laserView)
            return
        }

        val view = laserView as View
        val state = synchronized(states) {
            states[laserView] ?: ViewState(
                paints = listOf(
                    "mOutBlurPaint",
                    "mOutFillPaint",
                    "mInPaint",
                    "mPathPaint",
                ).map { fieldName ->
                    val paint = XposedHelpers.getObjectField(laserView, fieldName) as Paint
                    PaintState(paint, paint.color, paint.shader)
                },
                spec = requested,
            ).also { states[laserView] = it }
        }
        if (state.spec != requested) {
            restorePaints(state)
            state.spec = requested
            state.spatialApplied = false
            resetAnimation(state)
        }

        if (requested.spatial) {
            if (!state.spatialApplied && anchorX != null && anchorY != null) {
                applySpatialGradient(view, state, anchorX, anchorY)
            }
            return
        }

        clearShaders(state)
        applyColor(view, state, animatedColorAt(state, SystemClock.uptimeMillis()))
        if (requested.animated) startAnimation(laserView, view, state)
    }

    private fun applySpatialGradient(view: View, state: ViewState, x: Float, y: Float) {
        val density = view.resources.displayMetrics.density.coerceAtLeast(1f)
        val colors = (state.spec.colors + state.spec.colors.first()).toIntArray()
        val shader = LinearGradient(
            x,
            y,
            x + 300f * density,
            y + 180f * density,
            colors,
            null,
            Shader.TileMode.REPEAT,
        )
        state.paints.forEach { paintState -> paintState.paint.shader = shader }
        state.spatialApplied = true
        view.invalidate()
    }

    private fun startAnimation(laserView: Any, view: View, state: ViewState) {
        synchronized(states) {
            if (state.animationRunning) return
            state.animationRunning = true
        }
        val ticker = object : Runnable {
            override fun run() {
                val currentSpec = synchronized(states) {
                    val current = states[laserView]
                    if (current !== state || !state.spec.animated) {
                        state.animationRunning = false
                        null
                    } else {
                        state.spec
                    }
                } ?: return
                applyColor(view, state, animatedColorAt(state, SystemClock.uptimeMillis()))
                view.postDelayed(this, FRAME_INTERVAL_MS)
            }
        }
        view.postDelayed(ticker, FRAME_INTERVAL_MS)
    }

    private fun clearShaders(state: ViewState) {
        state.paints.forEach { it.paint.shader = it.originalShader }
        state.spatialApplied = false
    }

    private fun applyColor(view: View, state: ViewState, requestedColor: Int) {
        var changed = false
        state.paints.forEach { paintState ->
            val originalAlpha = Color.alpha(paintState.originalColor)
            val color = Color.argb(
                originalAlpha,
                Color.red(requestedColor),
                Color.green(requestedColor),
                Color.blue(requestedColor),
            )
            if (paintState.paint.color != color) {
                paintState.paint.color = color
                changed = true
            }
        }
        if (changed) view.invalidate()
    }

    private fun resetAnimation(state: ViewState) {
        state.animationPhase = 0.0
        state.lastAnimationUptime = 0L
        state.activeColorSegment = 0
        state.activeCycleMs = chooseCycleMs(state.spec)
    }

    private fun animatedColorAt(state: ViewState, uptimeMs: Long): Int {
        val spec = state.spec
        if (!spec.animated) return spec.colors.first()
        if (state.lastAnimationUptime == 0L) {
            state.lastAnimationUptime = uptimeMs
            state.activeCycleMs = chooseCycleMs(spec)
        } else {
            val elapsed = (uptimeMs - state.lastAnimationUptime).coerceIn(0L, 250L)
            state.lastAnimationUptime = uptimeMs
            state.animationPhase += elapsed.toDouble() / state.activeCycleMs * spec.colors.size
            state.animationPhase %= spec.colors.size.toDouble()
            val segment = floor(state.animationPhase).toInt()
            if (segment != state.activeColorSegment) {
                state.activeColorSegment = segment
                state.activeCycleMs = chooseCycleMs(spec)
            }
        }
        val colors = spec.colors
        val position = state.animationPhase
        val index = floor(position).toInt().coerceIn(0, colors.lastIndex)
        val fraction = (position - floor(position)).toFloat()
        return blend(colors[index], colors[(index + 1) % colors.size], fraction)
    }

    private fun chooseCycleMs(spec: ColorSpec): Long {
        val fastest = spec.cycleMs.coerceAtLeast(1L)
        val slowest = spec.randomSlowestCycleMs.coerceAtLeast(fastest)
        return if (slowest > fastest) Random.nextLong(fastest, slowest + 1L) else fastest
    }

    private fun blend(from: Int, to: Int, fraction: Float): Int {
        fun channel(start: Int, end: Int): Int =
            (start + (end - start) * fraction).toInt().coerceIn(0, 255)
        return Color.rgb(
            channel(Color.red(from), Color.red(to)),
            channel(Color.green(from), Color.green(to)),
            channel(Color.blue(from), Color.blue(to)),
        )
    }

    private fun restorePaints(state: ViewState): Boolean {
        var changed = false
        state.paints.forEach { paintState ->
            if (paintState.paint.color != paintState.originalColor) {
                paintState.paint.color = paintState.originalColor
                changed = true
            }
            if (paintState.paint.shader !== paintState.originalShader) {
                paintState.paint.shader = paintState.originalShader
                changed = true
            }
        }
        return changed
    }

    fun restore(laserView: Any) {
        val state = synchronized(states) { states.remove(laserView) } ?: return
        if (restorePaints(state)) (laserView as? View)?.invalidate()
    }

    fun restoreAll() {
        val views = synchronized(states) { states.keys.toList() }
        views.forEach { laserView ->
            val view = laserView as? View
            val handler = view?.handler
            if (handler != null && handler.looper != Looper.myLooper()) {
                handler.post { runCatching { restore(laserView) } }
            } else {
                runCatching { restore(laserView) }
            }
        }
    }

    private const val FRAME_INTERVAL_MS = 32L
}
