package io.github.hmqyhm.focuspenpro.config

import android.content.Context

class ConfigStore(context: Context) {
    private val storageContext = context.createDeviceProtectedStorageContext()
    private val preferences = storageContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    init {
        if (preferences.contains(ConfigContract.KEY_SAFE_MODE) ||
            preferences.contains(LEGACY_CHARGING_GUARD_KEY)
        ) {
            preferences.edit()
                .remove(ConfigContract.KEY_SAFE_MODE)
                .remove(LEGACY_CHARGING_GUARD_KEY)
                .apply()
        }
    }

    fun read(): ModuleConfig {
        val whitelist = preferences.getStringSet(ConfigContract.KEY_WHITELIST, emptySet())
            ?.toSet()
            .orEmpty()
        val laserMouseApps = if (preferences.contains(ConfigContract.KEY_LASER_MOUSE_APPS)) {
            preferences.getStringSet(ConfigContract.KEY_LASER_MOUSE_APPS, emptySet())
                ?.toSet()
                .orEmpty()
        } else {
            // Preserve the behavior of upgrades: existing whitelist entries used the
            // stylus mouse before this separate scope was introduced.
            whitelist
        }
        val laserBrushColor = preferences.getInt(
            ConfigContract.KEY_LASER_BRUSH_COLOR,
            ConfigContract.LASER_BRUSH_COLOR_SYSTEM,
        )
        val storedLaserBrushColorMode = preferences.getString(
            ConfigContract.KEY_LASER_BRUSH_COLOR_MODE,
            null,
        )
        val laserBrushColorMode = storedLaserBrushColorMode?.takeIf {
            it in ConfigContract.LASER_COLOR_MODES
        } ?: if (laserBrushColor == ConfigContract.LASER_BRUSH_COLOR_SYSTEM) {
            ConfigContract.LASER_COLOR_MODE_SYSTEM
        } else {
            ConfigContract.LASER_COLOR_MODE_SOLID
        }
        val laserGradientColors = preferences.getString(
            ConfigContract.KEY_LASER_GRADIENT_COLORS,
            null,
        )?.split(',')
            ?.mapNotNull { it.toLongOrNull(16)?.toInt() }
            ?.takeIf { it.size in 2..8 }
            ?: ConfigContract.DEFAULT_LASER_GRADIENT_COLORS
        val laserFlashingColors = preferences.getString(
            ConfigContract.KEY_LASER_FLASHING_COLORS,
            null,
        )?.split(',')
            ?.mapNotNull { it.toLongOrNull(16)?.toInt() }
            ?.takeIf { it.size in 2..8 }
            ?: laserGradientColors
        return ModuleConfig(
        enabled = preferences.getBoolean(ConfigContract.KEY_ENABLED, false),
        workMode = preferences.getString(
            ConfigContract.KEY_WORK_MODE,
            ConfigContract.WORK_MODE_WHITELIST,
        ) ?: ConfigContract.WORK_MODE_WHITELIST,
        whitelist = whitelist,
        blacklist = preferences.getStringSet(ConfigContract.KEY_BLACKLIST, emptySet())
            ?.toSet()
            .orEmpty(),
        laserMouseApps = laserMouseApps.intersect(whitelist),
        multiClickMs = preferences.getLong(ConfigContract.KEY_MULTI_CLICK_MS, 360L)
            .coerceIn(220L, 600L),
        globalActionsEnabled = preferences.getBoolean(
            ConfigContract.KEY_GLOBAL_ACTIONS_ENABLED,
            false,
        ),
        tripleAction = preferences.getString(
            ConfigContract.KEY_TRIPLE_ACTION,
            ConfigContract.TRIPLE_ACTION_ENABLE_LASER,
        ) ?: ConfigContract.TRIPLE_ACTION_ENABLE_LASER,
        triplePackage = preferences.getString(
            ConfigContract.KEY_TRIPLE_PACKAGE,
            "",
        ).orEmpty(),
        fourHoldAction = preferences.getString(
            ConfigContract.KEY_FOUR_HOLD_ACTION,
            ConfigContract.TRIPLE_ACTION_ENABLE_LASER,
        ) ?: ConfigContract.TRIPLE_ACTION_ENABLE_LASER,
        fourHoldPackage = preferences.getString(
            ConfigContract.KEY_FOUR_HOLD_PACKAGE,
            "",
        ).orEmpty(),
        gestureActions = ConfigContract.decodeGestureActions(
            preferences.getStringSet(ConfigContract.KEY_GESTURE_ACTIONS, emptySet()),
        ),
        scrollAmount = preferences.getString(
            ConfigContract.KEY_SCROLL_AMOUNT,
            ConfigContract.SCROLL_MEDIUM,
        ) ?: ConfigContract.SCROLL_MEDIUM,
        laserBrushColor = laserBrushColor,
        laserBrushColorMode = laserBrushColorMode,
        laserGradientColors = laserGradientColors,
        laserFlashingColors = laserFlashingColors,
        laserMarqueeSpeedTenths = if (
            preferences.contains(ConfigContract.KEY_LASER_MARQUEE_SPEED_TENTHS)
        ) {
            preferences.getInt(ConfigContract.KEY_LASER_MARQUEE_SPEED_TENTHS, 30)
        } else {
            preferences.getInt(ConfigContract.KEY_LASER_MARQUEE_SPEED, 3) * 10
        }.coerceIn(10, 100),
        laserMarqueeRandomSpeed = preferences.getBoolean(
            ConfigContract.KEY_LASER_MARQUEE_RANDOM_SPEED,
            false,
        ),
        revision = preferences.getLong(ConfigContract.KEY_REVISION, 0L),
        )
    }

    fun update(transform: (ModuleConfig) -> ModuleConfig): ModuleConfig {
        val transformed = transform(read())
        val next = transformed.copy(
            laserMouseApps = transformed.laserMouseApps.intersect(transformed.whitelist),
            laserBrushColorMode = transformed.laserBrushColorMode.takeIf {
                it in ConfigContract.LASER_COLOR_MODES
            } ?: ConfigContract.LASER_COLOR_MODE_SYSTEM,
            laserGradientColors = transformed.laserGradientColors.take(8).takeIf {
                it.size >= 2
            } ?: ConfigContract.DEFAULT_LASER_GRADIENT_COLORS,
            laserFlashingColors = transformed.laserFlashingColors.take(8).takeIf {
                it.size >= 2
            } ?: ConfigContract.DEFAULT_LASER_GRADIENT_COLORS,
            laserMarqueeSpeedTenths = transformed.laserMarqueeSpeedTenths.coerceIn(10, 100),
            revision = System.currentTimeMillis(),
        )
        preferences.edit()
            .remove(LEGACY_VOLUME_MODE_KEY)
            .remove(ConfigContract.KEY_SAFE_MODE)
            .remove(LEGACY_CHARGING_GUARD_KEY)
            .putBoolean(ConfigContract.KEY_ENABLED, next.enabled)
            .putString(ConfigContract.KEY_WORK_MODE, next.workMode)
            .putStringSet(ConfigContract.KEY_WHITELIST, next.whitelist)
            .putStringSet(ConfigContract.KEY_BLACKLIST, next.blacklist)
            .putStringSet(ConfigContract.KEY_LASER_MOUSE_APPS, next.laserMouseApps)
            .putLong(ConfigContract.KEY_MULTI_CLICK_MS, next.multiClickMs)
            .putBoolean(
                ConfigContract.KEY_GLOBAL_ACTIONS_ENABLED,
                next.globalActionsEnabled,
            )
            .putString(ConfigContract.KEY_TRIPLE_ACTION, next.tripleAction)
            .putString(ConfigContract.KEY_TRIPLE_PACKAGE, next.triplePackage)
            .putString(ConfigContract.KEY_FOUR_HOLD_ACTION, next.fourHoldAction)
            .putString(ConfigContract.KEY_FOUR_HOLD_PACKAGE, next.fourHoldPackage)
            .putStringSet(
                ConfigContract.KEY_GESTURE_ACTIONS,
                ConfigContract.encodeGestureActions(next.gestureActions),
            )
            .putString(ConfigContract.KEY_SCROLL_AMOUNT, next.scrollAmount)
            .putInt(ConfigContract.KEY_LASER_BRUSH_COLOR, next.laserBrushColor)
            .putString(ConfigContract.KEY_LASER_BRUSH_COLOR_MODE, next.laserBrushColorMode)
            .putString(
                ConfigContract.KEY_LASER_GRADIENT_COLORS,
                next.laserGradientColors.joinToString(",") { color ->
                    color.toUInt().toString(16).padStart(8, '0')
                },
            )
            .putString(
                ConfigContract.KEY_LASER_FLASHING_COLORS,
                next.laserFlashingColors.joinToString(",") { color ->
                    color.toUInt().toString(16).padStart(8, '0')
                },
            )
            .putInt(
                ConfigContract.KEY_LASER_MARQUEE_SPEED_TENTHS,
                next.laserMarqueeSpeedTenths,
            )
            .putBoolean(
                ConfigContract.KEY_LASER_MARQUEE_RANDOM_SPEED,
                next.laserMarqueeRandomSpeed,
            )
            .putLong(ConfigContract.KEY_REVISION, next.revision)
            .apply()
        storageContext.contentResolver.notifyChange(ConfigContract.URI, null)
        return next
    }

    companion object {
        private const val PREFS = "module_config"
        private const val LEGACY_VOLUME_MODE_KEY = "volume_mode"
        private const val LEGACY_CHARGING_GUARD_KEY = "charging_guard_enabled"
    }
}
