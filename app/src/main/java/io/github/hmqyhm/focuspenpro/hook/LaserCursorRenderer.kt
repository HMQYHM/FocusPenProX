package io.github.hmqyhm.focuspenpro.hook

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.view.View
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import java.util.WeakHashMap

/**
 * Keeps Xiaomi's LaserView mode and lifecycle untouched. The three laser-dot
 * paints are temporarily transparent while a mouse-arrow Drawable is attached
 * to the ViewOverlay.
 */
internal object LaserCursorRenderer {
    private const val CURSOR_KEY = "focus_pen_laser_cursor"
    private const val PAINT_ALPHAS_KEY = "focus_pen_laser_paint_alphas"
    private const val INSTALL_LOGGED_KEY = "focus_pen_cursor_install_logged"

    private val activeViews = WeakHashMap<Any, Boolean>()

    fun isLaserMode(laserView: Any): Boolean =
        XposedHelpers.getIntField(laserView, "mMode") == 0

    fun isActive(laserView: Any): Boolean = synchronized(activeViews) {
        activeViews.containsKey(laserView)
    }

    fun hasActiveCursor(): Boolean = synchronized(activeViews) {
        activeViews.isNotEmpty()
    }

    fun install(laserView: Any, x: Float, y: Float): Boolean {
        if (!x.isFinite() || !y.isFinite() || !isLaserMode(laserView)) return false
        val view = laserView as View
        val paints = laserPaints(laserView)

        if (XposedHelpers.getAdditionalInstanceField(
                laserView,
                PAINT_ALPHAS_KEY,
            ) == null
        ) {
            XposedHelpers.setAdditionalInstanceField(
                laserView,
                PAINT_ALPHAS_KEY,
                paints.map(Paint::getAlpha).toIntArray(),
            )
        }
        paints.forEach { it.alpha = 0 }

        var cursor = XposedHelpers.getAdditionalInstanceField(
            laserView,
            CURSOR_KEY,
        ) as? MouseCursorDrawable
        if (cursor == null) {
            cursor = MouseCursorDrawable(view.resources.displayMetrics.density)
            XposedHelpers.setAdditionalInstanceField(laserView, CURSOR_KEY, cursor)
            view.overlay.add(cursor)
        }

        val density = view.resources.displayMetrics.density.coerceAtLeast(1f)
        cursor.setBounds(
            x.toInt(),
            y.toInt(),
            (x + 22f * density).toInt(),
            (y + 29f * density).toInt(),
        )
        synchronized(activeViews) {
            activeViews[laserView] = true
        }
        view.invalidate()

        if (XposedHelpers.getAdditionalInstanceField(
                laserView,
                INSTALL_LOGGED_KEY,
            ) == null
        ) {
            XposedHelpers.setAdditionalInstanceField(
                laserView,
                INSTALL_LOGGED_KEY,
                true,
            )
            XposedBridge.log(
                "FocusPenPro: Xiaomi laser dot replaced by overlay mouse cursor",
            )
        }
        return true
    }

    fun restore(laserView: Any) {
        val view = laserView as? View ?: return
        val wasActive = isActive(laserView)
        val cursor = XposedHelpers.getAdditionalInstanceField(
            laserView,
            CURSOR_KEY,
        ) as? Drawable
        if (cursor != null) {
            view.overlay.remove(cursor)
            XposedHelpers.removeAdditionalInstanceField(laserView, CURSOR_KEY)
        }

        val originalAlphas = XposedHelpers.getAdditionalInstanceField(
            laserView,
            PAINT_ALPHAS_KEY,
        ) as? IntArray
        if (originalAlphas != null) {
            laserPaints(laserView).forEachIndexed { index, paint ->
                paint.alpha = originalAlphas.getOrElse(index) { 255 }
            }
            XposedHelpers.removeAdditionalInstanceField(
                laserView,
                PAINT_ALPHAS_KEY,
            )
        }
        synchronized(activeViews) {
            activeViews.remove(laserView)
        }
        if (wasActive) {
            runCatching { XposedHelpers.callMethod(laserView, "clearPath") }
        }
        view.invalidate()
    }

    fun restoreAll() {
        val views = synchronized(activeViews) { activeViews.keys.toList() }
        views.forEach { laserView ->
            val view = laserView as? View ?: return@forEach
            view.post { runCatching { restore(laserView) } }
        }
    }

    private fun laserPaints(laserView: Any): List<Paint> = listOf(
        XposedHelpers.getObjectField(laserView, "mOutBlurPaint") as Paint,
        XposedHelpers.getObjectField(laserView, "mOutFillPaint") as Paint,
        XposedHelpers.getObjectField(laserView, "mInPaint") as Paint,
        XposedHelpers.getObjectField(laserView, "mPathPaint") as Paint,
    )

    private class MouseCursorDrawable(density: Float) : Drawable() {
        private val scale = density.coerceAtLeast(1f)
        private val path = Path()
        private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }
        private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            style = Paint.Style.STROKE
            strokeWidth = 1.6f * scale
            strokeJoin = Paint.Join.ROUND
        }

        override fun draw(canvas: Canvas) {
            val left = bounds.left.toFloat()
            val top = bounds.top.toFloat()
            path.reset()
            path.moveTo(left, top)
            path.lineTo(left + 2.5f * scale, top + 22f * scale)
            path.lineTo(left + 7.5f * scale, top + 16.8f * scale)
            path.lineTo(left + 12.5f * scale, top + 27f * scale)
            path.lineTo(left + 17.2f * scale, top + 24.7f * scale)
            path.lineTo(left + 12.2f * scale, top + 14.8f * scale)
            path.lineTo(left + 20f * scale, top + 14.8f * scale)
            path.close()
            canvas.drawPath(path, fill)
            canvas.drawPath(path, stroke)
        }

        override fun setAlpha(alpha: Int) {
            fill.alpha = alpha
            stroke.alpha = alpha
        }

        override fun setColorFilter(colorFilter: ColorFilter?) {
            fill.colorFilter = colorFilter
            stroke.colorFilter = colorFilter
        }

        @Deprecated("Deprecated in Android")
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    }
}
