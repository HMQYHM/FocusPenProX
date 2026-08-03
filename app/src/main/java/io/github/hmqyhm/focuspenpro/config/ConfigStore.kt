package io.github.hmqyhm.focuspenpro.config

import android.content.Context

class ConfigStore(context: Context) {
    private val storageContext = context.createDeviceProtectedStorageContext()
    private val preferences = storageContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    init {
        if (preferences.contains(ConfigContract.KEY_SAFE_MODE)) {
            preferences.edit().remove(ConfigContract.KEY_SAFE_MODE).apply()
        }
    }

    fun read(): ModuleConfig = ModuleConfig(
        enabled = preferences.getBoolean(ConfigContract.KEY_ENABLED, false),
        workMode = preferences.getString(
            ConfigContract.KEY_WORK_MODE,
            ConfigContract.WORK_MODE_WHITELIST,
        ) ?: ConfigContract.WORK_MODE_WHITELIST,
        whitelist = preferences.getStringSet(ConfigContract.KEY_WHITELIST, emptySet())
            ?.toSet()
            .orEmpty(),
        blacklist = preferences.getStringSet(ConfigContract.KEY_BLACKLIST, emptySet())
            ?.toSet()
            .orEmpty(),
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
        revision = preferences.getLong(ConfigContract.KEY_REVISION, 0L),
    )

    fun update(transform: (ModuleConfig) -> ModuleConfig): ModuleConfig {
        val next = transform(read()).copy(revision = System.currentTimeMillis())
        preferences.edit()
            .remove(LEGACY_VOLUME_MODE_KEY)
            .remove(ConfigContract.KEY_SAFE_MODE)
            .putBoolean(ConfigContract.KEY_ENABLED, next.enabled)
            .putString(ConfigContract.KEY_WORK_MODE, next.workMode)
            .putStringSet(ConfigContract.KEY_WHITELIST, next.whitelist)
            .putStringSet(ConfigContract.KEY_BLACKLIST, next.blacklist)
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
            .putLong(ConfigContract.KEY_REVISION, next.revision)
            .apply()
        storageContext.contentResolver.notifyChange(ConfigContract.URI, null)
        return next
    }

    companion object {
        private const val PREFS = "module_config"
        private const val LEGACY_VOLUME_MODE_KEY = "volume_mode"
    }
}
