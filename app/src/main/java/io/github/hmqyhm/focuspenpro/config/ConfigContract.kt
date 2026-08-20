package io.github.hmqyhm.focuspenpro.config

import android.net.Uri

object ConfigContract {
    const val AUTHORITY = "io.github.hmqyhm.focuspenpro.config"
    val URI: Uri = Uri.parse("content://$AUTHORITY/config")

    const val METHOD_GET_SNAPSHOT = "get_snapshot"
    const val METHOD_REPORT_RUNTIME = "report_runtime"
    const val METHOD_APPEND_EVENT = "append_event"
    const val METHOD_GET_RUNTIME = "get_runtime"

    const val KEY_ENABLED = "enabled"
    // Kept only so upgrades can remove the legacy preference.
    const val KEY_SAFE_MODE = "safe_mode"
    const val KEY_WORK_MODE = "work_mode"
    const val KEY_WHITELIST = "whitelist"
    const val KEY_BLACKLIST = "blacklist"
    const val KEY_LASER_MOUSE_APPS = "laser_mouse_apps"
    const val KEY_MULTI_CLICK_MS = "multi_click_ms"
    const val KEY_GLOBAL_ACTIONS_ENABLED = "global_actions_enabled"
    const val KEY_TRIPLE_ACTION = "triple_action"
    const val KEY_TRIPLE_PACKAGE = "triple_package"
    const val KEY_FOUR_HOLD_ACTION = "four_hold_action"
    const val KEY_FOUR_HOLD_PACKAGE = "four_hold_package"
    const val KEY_GESTURE_ACTIONS = "gesture_actions"
    const val KEY_SCROLL_AMOUNT = "scroll_amount"
    const val KEY_LASER_BRUSH_COLOR = "laser_brush_color"
    const val KEY_LASER_BRUSH_COLOR_MODE = "laser_brush_color_mode"
    const val KEY_LASER_GRADIENT_COLORS = "laser_gradient_colors"
    const val KEY_LASER_FLASHING_COLORS = "laser_flashing_colors"
    const val KEY_LASER_MARQUEE_SPEED = "laser_marquee_speed"
    const val KEY_LASER_MARQUEE_SPEED_TENTHS = "laser_marquee_speed_tenths"
    const val KEY_LASER_MARQUEE_RANDOM_SPEED = "laser_marquee_random_speed"
    const val KEY_REVISION = "revision"

    const val KEY_RUNTIME_ACTIVE = "runtime_active"
    const val KEY_RUNTIME_COMPATIBLE = "runtime_compatible"
    const val KEY_RUNTIME_MESSAGE = "runtime_message"
    const val KEY_RUNTIME_LASER = "runtime_laser"
    const val KEY_RUNTIME_FOREGROUND = "runtime_foreground"
    const val KEY_RUNTIME_EVENTS = "runtime_events"

    const val WORK_MODE_WHITELIST = "whitelist"
    const val WORK_MODE_GLOBAL = "global"
    const val TRIPLE_ACTION_NONE = "none"
    const val TRIPLE_ACTION_ENABLE_LASER = "enable_laser"
    const val TRIPLE_ACTION_LAUNCH_APP = "launch_app"

    const val GESTURE_PINCH = "laser_pinch"
    const val GESTURE_PINCH_HOLD = "laser_pinch_hold"
    const val GESTURE_DOUBLE_TAP = "laser_double_tap"
    const val GESTURE_DOUBLE_HOLD = "laser_double_hold"
    const val GESTURE_SWIPE_UP = "laser_swipe_up"
    const val GESTURE_SWIPE_DOWN = "laser_swipe_down"
    const val GESTURE_OFF_DOUBLE_TAP = "off_double_tap"
    const val GESTURE_OFF_DOUBLE_HOLD = "off_double_hold"
    const val GESTURE_OFF_SWIPE_UP = "off_swipe_up"
    const val GESTURE_OFF_SWIPE_DOWN = "off_swipe_down"

    const val ACTION_SYSTEM = "system"
    const val ACTION_CONSUME = "consume"
    const val ACTION_MOUSE_LEFT_CLICK = "mouse_left_click"
    const val ACTION_MOUSE_LEFT_HOLD = "mouse_left_hold"
    const val ACTION_MOUSE_RIGHT_CLICK = "mouse_right_click"
    const val ACTION_MOUSE_RIGHT_HOLD = "mouse_right_hold"
    const val ACTION_VOLUME_UP = "volume_up"
    const val ACTION_VOLUME_DOWN = "volume_down"
    const val ACTION_BACK = "back"
    const val ACTION_HOME = "home"
    const val ACTION_RECENTS = "recents"
    const val ACTION_SCROLL_UP = "scroll_up"
    const val ACTION_SCROLL_DOWN = "scroll_down"

    const val SCROLL_SHORT = "short"
    const val SCROLL_MEDIUM = "medium"
    const val SCROLL_LONG = "long"

    const val LASER_BRUSH_COLOR_SYSTEM = 0
    const val LASER_COLOR_MODE_SYSTEM = "system"
    const val LASER_COLOR_MODE_SOLID = "solid"
    const val LASER_COLOR_MODE_RAINBOW = "rainbow"
    const val LASER_COLOR_MODE_GRADIENT = "gradient"
    const val LASER_COLOR_MODE_MARQUEE = "marquee"
    val LASER_COLOR_MODES = setOf(
        LASER_COLOR_MODE_SYSTEM,
        LASER_COLOR_MODE_SOLID,
        LASER_COLOR_MODE_RAINBOW,
        LASER_COLOR_MODE_GRADIENT,
        LASER_COLOR_MODE_MARQUEE,
    )
    val DEFAULT_LASER_GRADIENT_COLORS = listOf(
        0xFFFF3B30.toInt(),
        0xFF007AFF.toInt(),
    )

    val DEFAULT_GESTURE_ACTIONS: Map<String, String> = linkedMapOf(
        GESTURE_PINCH to ACTION_MOUSE_LEFT_CLICK,
        GESTURE_PINCH_HOLD to ACTION_MOUSE_LEFT_HOLD,
        GESTURE_DOUBLE_TAP to ACTION_MOUSE_RIGHT_CLICK,
        GESTURE_DOUBLE_HOLD to ACTION_MOUSE_RIGHT_HOLD,
        GESTURE_SWIPE_UP to ACTION_VOLUME_UP,
        GESTURE_SWIPE_DOWN to ACTION_VOLUME_DOWN,
        GESTURE_OFF_DOUBLE_TAP to ACTION_SYSTEM,
        GESTURE_OFF_DOUBLE_HOLD to ACTION_SYSTEM,
        GESTURE_OFF_SWIPE_UP to ACTION_SYSTEM,
        GESTURE_OFF_SWIPE_DOWN to ACTION_SYSTEM,
    )

    fun encodeGestureActions(actions: Map<String, String>): Set<String> =
        actions.mapTo(linkedSetOf()) { (gesture, action) -> "$gesture\t$action" }

    fun decodeGestureActions(entries: Collection<String>?): Map<String, String> {
        val decoded = entries.orEmpty().mapNotNull { entry ->
            val separator = entry.indexOf('\t')
            if (separator <= 0 || separator == entry.lastIndex) null
            else entry.substring(0, separator) to entry.substring(separator + 1)
        }.toMap()
        return DEFAULT_GESTURE_ACTIONS + decoded
    }
}
