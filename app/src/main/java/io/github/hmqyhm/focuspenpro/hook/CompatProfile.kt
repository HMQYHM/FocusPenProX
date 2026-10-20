package io.github.hmqyhm.focuspenpro.hook

import android.os.Build
import io.github.hmqyhm.focuspenpro.config.ConfigContract

internal object CompatProfile {
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

    fun effectivePlatform(selection: String): String {
        if (selection == ConfigContract.PLATFORM_OS3 ||
            selection == ConfigContract.PLATFORM_OS4
        ) return selection
        val display = Build.DISPLAY.orEmpty()
        return when {
            display.contains("OS4", ignoreCase = true) || Build.VERSION.SDK_INT >= 37 ->
                ConfigContract.PLATFORM_OS4
            display.contains("OS3", ignoreCase = true) || Build.VERSION.SDK_INT == 36 ->
                ConfigContract.PLATFORM_OS3
            else -> ConfigContract.PLATFORM_AUTO
        }
    }

}
