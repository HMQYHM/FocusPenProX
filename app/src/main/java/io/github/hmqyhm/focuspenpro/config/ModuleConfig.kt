package io.github.hmqyhm.focuspenpro.config

import android.os.Bundle

data class ModuleConfig(
    val enabled: Boolean = false,
    val workMode: String = ConfigContract.WORK_MODE_WHITELIST,
    val whitelist: Set<String> = emptySet(),
    val blacklist: Set<String> = emptySet(),
    val multiClickMs: Long = 360L,
    val globalActionsEnabled: Boolean = false,
    val tripleAction: String = ConfigContract.TRIPLE_ACTION_ENABLE_LASER,
    val triplePackage: String = "",
    val fourHoldAction: String = ConfigContract.TRIPLE_ACTION_ENABLE_LASER,
    val fourHoldPackage: String = "",
    val gestureActions: Map<String, String> = ConfigContract.DEFAULT_GESTURE_ACTIONS,
    val scrollAmount: String = ConfigContract.SCROLL_MEDIUM,
    val revision: Long = 0L,
) {
    fun appliesTo(packageName: String?): Boolean {
        if (!enabled || packageName.isNullOrBlank() || packageName in blacklist) return false
        // Pen enhancement is deliberately whitelist-only. Global mode is retained
        // in the serialized schema solely so existing installations can migrate
        // without losing the rest of their configuration.
        return packageName in whitelist
    }

    fun blocksAllHooks(packageName: String?): Boolean =
        !packageName.isNullOrBlank() && packageName in blacklist

    fun toBundle() = Bundle().apply {
        putBoolean(ConfigContract.KEY_ENABLED, enabled)
        putString(ConfigContract.KEY_WORK_MODE, workMode)
        putStringArrayList(ConfigContract.KEY_WHITELIST, ArrayList(whitelist))
        putStringArrayList(ConfigContract.KEY_BLACKLIST, ArrayList(blacklist))
        putLong(ConfigContract.KEY_MULTI_CLICK_MS, multiClickMs)
        putBoolean(ConfigContract.KEY_GLOBAL_ACTIONS_ENABLED, globalActionsEnabled)
        putString(ConfigContract.KEY_TRIPLE_ACTION, tripleAction)
        putString(ConfigContract.KEY_TRIPLE_PACKAGE, triplePackage)
        putString(ConfigContract.KEY_FOUR_HOLD_ACTION, fourHoldAction)
        putString(ConfigContract.KEY_FOUR_HOLD_PACKAGE, fourHoldPackage)
        putStringArrayList(
            ConfigContract.KEY_GESTURE_ACTIONS,
            ArrayList(ConfigContract.encodeGestureActions(gestureActions)),
        )
        putString(ConfigContract.KEY_SCROLL_AMOUNT, scrollAmount)
        putLong(ConfigContract.KEY_REVISION, revision)
    }

    companion object {
        fun fromBundle(bundle: Bundle): ModuleConfig = ModuleConfig(
            enabled = bundle.getBoolean(ConfigContract.KEY_ENABLED, false),
            workMode = bundle.getString(
                ConfigContract.KEY_WORK_MODE,
                ConfigContract.WORK_MODE_WHITELIST,
            ) ?: ConfigContract.WORK_MODE_WHITELIST,
            whitelist = bundle.getStringArrayList(ConfigContract.KEY_WHITELIST)
                ?.filter(String::isNotBlank)
                ?.toSet()
                .orEmpty(),
            blacklist = bundle.getStringArrayList(ConfigContract.KEY_BLACKLIST)
                ?.filter(String::isNotBlank)
                ?.toSet()
                .orEmpty(),
            multiClickMs = bundle.getLong(ConfigContract.KEY_MULTI_CLICK_MS, 360L)
                .coerceIn(220L, 600L),
            globalActionsEnabled = bundle.getBoolean(
                ConfigContract.KEY_GLOBAL_ACTIONS_ENABLED,
                false,
            ),
            tripleAction = bundle.getString(
                ConfigContract.KEY_TRIPLE_ACTION,
                ConfigContract.TRIPLE_ACTION_ENABLE_LASER,
            ) ?: ConfigContract.TRIPLE_ACTION_ENABLE_LASER,
            triplePackage = bundle.getString(ConfigContract.KEY_TRIPLE_PACKAGE).orEmpty(),
            fourHoldAction = bundle.getString(
                ConfigContract.KEY_FOUR_HOLD_ACTION,
                ConfigContract.TRIPLE_ACTION_ENABLE_LASER,
            ) ?: ConfigContract.TRIPLE_ACTION_ENABLE_LASER,
            fourHoldPackage = bundle.getString(ConfigContract.KEY_FOUR_HOLD_PACKAGE).orEmpty(),
            gestureActions = ConfigContract.decodeGestureActions(
                bundle.getStringArrayList(ConfigContract.KEY_GESTURE_ACTIONS),
            ),
            scrollAmount = bundle.getString(
                ConfigContract.KEY_SCROLL_AMOUNT,
                ConfigContract.SCROLL_MEDIUM,
            ) ?: ConfigContract.SCROLL_MEDIUM,
            revision = bundle.getLong(ConfigContract.KEY_REVISION, 0L),
        )
    }
}
