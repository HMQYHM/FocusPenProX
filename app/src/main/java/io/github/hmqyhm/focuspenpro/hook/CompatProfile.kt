package io.github.hmqyhm.focuspenpro.hook

import android.os.Build

internal object CompatProfile {
    const val DEVICE = "piano"

    const val TOUCH_FILM_CLASS =
        "com.miui.server.input.stylus.MiuiStylusTouchFilmManager"
    const val SHORTCUT_MANAGER_CLASS =
        "com.miui.server.input.stylus.MiuiStylusShortcutManager"
    const val LASER_CONTROLLER_CLASS =
        "com.miui.server.input.stylus.laser.LaserPointerController"
    const val LASER_VIEW_CLASS =
        "com.miui.server.input.stylus.laser.LaserView"
    const val POLICY_CLASS =
        "com.android.server.policy.MiuiPhoneWindowManager"

    const val SCAN_PINCH_F19 = 189
    const val KEY_SLIDE_UP = 196
    const val KEY_SLIDE_DOWN = 197

    fun deviceMatches(): Boolean =
        Build.DEVICE == DEVICE

    fun description(): String =
        "${Build.DEVICE} / ${Build.VERSION.INCREMENTAL}"
}
