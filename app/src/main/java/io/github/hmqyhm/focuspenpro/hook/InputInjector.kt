package io.github.hmqyhm.focuspenpro.hook

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Point
import android.graphics.PointF
import android.hardware.display.DisplayManager
import android.os.SystemClock
import android.view.Display
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import de.robv.android.xposed.XposedHelpers
import java.lang.reflect.Method
import java.util.concurrent.atomic.AtomicReference

@SuppressLint("BlockedPrivateApi")
internal class InputInjector(
    private val context: Context,
    private val laserController: AtomicReference<Any?>,
    private val systemServerClassLoader: ClassLoader,
) {
    private val inputManager: Any?
    private val injectMethod: Method?
    private val inputManagerInternal: Any?
    private val getCursorPositionMethod: Method?
    private var lastValidPosition: PointF? = null
    private var mouseButtonSession: MouseButtonSession? = null

    init {
        var manager: Any? = null
        var method: Method? = null
        var internal: Any? = null
        var cursorMethod: Method? = null
        runCatching {
            val clazz = Class.forName("android.hardware.input.InputManager")
            manager = clazz.getDeclaredMethod("getInstance").apply {
                isAccessible = true
            }.invoke(null)
            method = clazz.getDeclaredMethod(
                "injectInputEvent",
                android.view.InputEvent::class.java,
                Int::class.javaPrimitiveType,
            ).apply { isAccessible = true }
        }
        runCatching {
            val localServices = Class.forName(
                "com.android.server.LocalServices",
                false,
                systemServerClassLoader,
            )
            val internalClass = Class.forName(
                "com.android.server.input.InputManagerInternal",
                false,
                systemServerClassLoader,
            )
            internal = localServices.getDeclaredMethod(
                "getService",
                Class::class.java,
            ).invoke(null, internalClass)
            cursorMethod = internalClass.getDeclaredMethod(
                "getCursorPosition",
                Int::class.javaPrimitiveType,
            ).apply { isAccessible = true }
        }
        inputManager = manager
        injectMethod = method
        inputManagerInternal = internal
        getCursorPositionMethod = cursorMethod
    }

    fun canInjectKey(): Boolean = inputManager != null && injectMethod != null

    fun capabilitySummary(): String =
        "inputManager=${inputManager != null}, inject=${injectMethod != null}, " +
            "internal=${inputManagerInternal != null}, cursor=${getCursorPositionMethod != null}"

    fun canInjectMouse(preferSystemCursor: Boolean): Boolean =
        currentPosition(preferSystemCursor) != null && canInjectKey()

    @Synchronized
    fun injectMouseClick(button: Int, preferSystemCursor: Boolean): Boolean {
        if (mouseButtonSession != null) return false
        val point = currentPosition(preferSystemCursor) ?: lastValidPosition ?: return false
        val downTime = SystemClock.uptimeMillis()
        val events = listOf(
            mouseEvent(downTime, downTime, MotionEvent.ACTION_DOWN, point, button, button),
            mouseEvent(
                downTime,
                downTime + 1,
                MotionEvent.ACTION_BUTTON_PRESS,
                point,
                button,
                button,
            ),
            mouseEvent(
                downTime,
                downTime + 2,
                MotionEvent.ACTION_BUTTON_RELEASE,
                point,
                0,
                button,
            ),
            mouseEvent(downTime, downTime + 3, MotionEvent.ACTION_UP, point, 0, button),
        )
        return try {
            var allSucceeded = true
            events.forEach { event ->
                if (!inject(event)) allSucceeded = false
            }
            allSucceeded
        } finally {
            events.forEach(MotionEvent::recycle)
        }
    }

    @Synchronized
    fun injectMouseButtonDown(button: Int, preferSystemCursor: Boolean): Boolean {
        if (mouseButtonSession != null) return false
        val current = currentPosition(preferSystemCursor) ?: lastValidPosition ?: return false
        val point = PointF(current.x, current.y)
        val downTime = SystemClock.uptimeMillis()
        val down = mouseEvent(
            downTime,
            downTime,
            MotionEvent.ACTION_DOWN,
            point,
            button,
            button,
        )
        val press = mouseEvent(
            downTime,
            downTime + 1,
            MotionEvent.ACTION_BUTTON_PRESS,
            point,
            button,
            button,
        )
        return try {
            val downSucceeded = inject(down)
            val pressSucceeded = inject(press)
            if (downSucceeded && pressSucceeded) {
                mouseButtonSession = MouseButtonSession(button, downTime, point)
                true
            } else {
                injectMouseButtonReleaseEvents(
                    MouseButtonSession(button, downTime, point),
                )
                false
            }
        } finally {
            down.recycle()
            press.recycle()
        }
    }

    @Synchronized
    fun releaseMouseButton(): Boolean {
        val session = mouseButtonSession ?: return true
        mouseButtonSession = null
        return injectMouseButtonReleaseEvents(session)
    }

    @Synchronized
    fun injectMouseMoveDuringHold(x: Float, y: Float): Boolean {
        val session = mouseButtonSession ?: return false
        if (!x.isFinite() || !y.isFinite()) return false
        val bounds = displayBounds()
        if (x < 0f || y < 0f || x >= bounds.first || y >= bounds.second) return false
        val now = SystemClock.uptimeMillis()
        if (now - session.lastMoveTime < MIN_MOUSE_MOVE_INTERVAL_MS) return true
        session.point.set(x, y)
        session.lastMoveTime = now
        lastValidPosition = PointF(x, y)
        val move = mouseEvent(
            session.downTime,
            now.coerceAtLeast(session.downTime + 2),
            MotionEvent.ACTION_MOVE,
            session.point,
            session.button,
            0,
        )
        return try {
            inject(move)
        } finally {
            move.recycle()
        }
    }

    fun injectKeyClick(keyCode: Int): Boolean {
        if (!canInjectKey()) return false
        val downTime = SystemClock.uptimeMillis()
        val down = KeyEvent(
            downTime,
            downTime,
            KeyEvent.ACTION_DOWN,
            keyCode,
            0,
            0,
            KeyCharacterMapIds.VIRTUAL_KEYBOARD,
            0,
            KeyEvent.FLAG_FROM_SYSTEM,
            InputDevice.SOURCE_KEYBOARD,
        )
        val up = KeyEvent(
            downTime,
            downTime + 1,
            KeyEvent.ACTION_UP,
            keyCode,
            0,
            0,
            KeyCharacterMapIds.VIRTUAL_KEYBOARD,
            0,
            KeyEvent.FLAG_FROM_SYSTEM,
            InputDevice.SOURCE_KEYBOARD,
        )
        val downSucceeded = inject(down)
        val upSucceeded = inject(up)
        return downSucceeded && upSucceeded
    }

    @Synchronized
    fun injectMouseScroll(verticalAmount: Float, preferSystemCursor: Boolean): Boolean {
        if (!verticalAmount.isFinite() || verticalAmount == 0f) return false
        val point = currentPosition(preferSystemCursor) ?: lastValidPosition ?: return false
        val now = SystemClock.uptimeMillis()
        val properties = arrayOf(MotionEvent.PointerProperties().apply {
            id = 0
            toolType = MotionEvent.TOOL_TYPE_MOUSE
        })
        val coordinates = arrayOf(MotionEvent.PointerCoords().apply {
            x = point.x
            y = point.y
            pressure = 0f
            size = 1f
            setAxisValue(MotionEvent.AXIS_VSCROLL, verticalAmount)
        })
        val event = MotionEvent.obtain(
            now,
            now,
            MotionEvent.ACTION_SCROLL,
            1,
            properties,
            coordinates,
            0,
            0,
            1f,
            1f,
            KeyCharacterMapIds.VIRTUAL_KEYBOARD,
            0,
            InputDevice.SOURCE_MOUSE,
            0,
        )
        return try {
            inject(event)
        } finally {
            event.recycle()
        }
    }

    private fun currentPosition(preferSystemCursor: Boolean): PointF? {
        if (preferSystemCursor) {
            systemCursorPosition()?.let {
                lastValidPosition = it
                return it
            }
        }
        val controller = laserController.get() ?: return null
        val raw = FloatArray(2)
        val valid = runCatching {
            XposedHelpers.callMethod(controller, "getPosition", raw)
            val bounds = displayBounds()
            raw[0].isFinite() && raw[1].isFinite() &&
                raw[0] >= 0f && raw[1] >= 0f &&
                raw[0] < bounds.first && raw[1] < bounds.second
        }.getOrDefault(false)
        return if (valid) {
            PointF(raw[0], raw[1]).also { lastValidPosition = it }
        } else {
            null
        }
    }

    private fun systemCursorPosition(): PointF? {
        val point = runCatching {
            getCursorPositionMethod?.invoke(
                inputManagerInternal,
                Display.DEFAULT_DISPLAY,
            ) as? PointF
        }.getOrNull() ?: return null
        val bounds = displayBounds()
        return point.takeIf {
            it.x.isFinite() && it.y.isFinite() &&
                it.x >= 0f && it.y >= 0f &&
                it.x < bounds.first && it.y < bounds.second
        }
    }

    private fun displayBounds(): Pair<Int, Int> {
        val display = context.getSystemService(DisplayManager::class.java)
            ?.getDisplay(Display.DEFAULT_DISPLAY)
        return if (display != null) {
            Point().also(display::getRealSize).let { it.x to it.y }
        } else {
            context.resources.displayMetrics.let { it.widthPixels to it.heightPixels }
        }
    }

    private fun mouseEvent(
        downTime: Long,
        eventTime: Long,
        action: Int,
        point: PointF,
        buttonState: Int,
        actionButton: Int,
    ): MotionEvent {
        val properties = arrayOf(MotionEvent.PointerProperties().apply {
            id = 0
            toolType = MotionEvent.TOOL_TYPE_MOUSE
        })
        val coordinates = arrayOf(MotionEvent.PointerCoords().apply {
            x = point.x
            y = point.y
            pressure = if (action == MotionEvent.ACTION_UP) 0f else 1f
            size = 1f
        })
        return MotionEvent.obtain(
            downTime,
            eventTime,
            action,
            1,
            properties,
            coordinates,
            0,
            buttonState,
            1f,
            1f,
            KeyCharacterMapIds.VIRTUAL_KEYBOARD,
            0,
            InputDevice.SOURCE_MOUSE,
            0,
        ).apply {
            if (action == MotionEvent.ACTION_BUTTON_PRESS ||
                action == MotionEvent.ACTION_BUTTON_RELEASE
            ) {
                runCatching {
                    javaClass.getDeclaredMethod(
                        "setActionButton",
                        Int::class.javaPrimitiveType,
                    ).apply { isAccessible = true }.invoke(this, actionButton)
                }
            }
        }
    }

    private fun inject(event: android.view.InputEvent): Boolean =
        runCatching {
            injectMethod?.invoke(inputManager, event, INJECT_INPUT_EVENT_MODE_ASYNC) as? Boolean
        }.getOrNull() == true

    private fun injectMouseButtonReleaseEvents(session: MouseButtonSession): Boolean {
        val now = SystemClock.uptimeMillis().coerceAtLeast(session.downTime + 2)
        val release = mouseEvent(
            session.downTime,
            now,
            MotionEvent.ACTION_BUTTON_RELEASE,
            session.point,
            0,
            session.button,
        )
        val up = mouseEvent(
            session.downTime,
            now + 1,
            MotionEvent.ACTION_UP,
            session.point,
            0,
            session.button,
        )
        return try {
            val releaseSucceeded = inject(release)
            val upSucceeded = inject(up)
            releaseSucceeded && upSucceeded
        } finally {
            release.recycle()
            up.recycle()
        }
    }

    private data class MouseButtonSession(
        val button: Int,
        val downTime: Long,
        val point: PointF,
        var lastMoveTime: Long = downTime,
    )

    private object KeyCharacterMapIds {
        const val VIRTUAL_KEYBOARD = -1
    }

    private companion object {
        const val INJECT_INPUT_EVENT_MODE_ASYNC = 0
        const val MIN_MOUSE_MOVE_INTERVAL_MS = 8L
    }
}
