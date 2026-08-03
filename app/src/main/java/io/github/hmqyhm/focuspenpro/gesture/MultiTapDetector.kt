package io.github.hmqyhm.focuspenpro.gesture

class MultiTapDetector(
    private val requiredTaps: Int,
    private val intervalMs: () -> Long,
) {
    init {
        require(requiredTaps >= 2)
    }

    private var completedTaps = 0
    private var lastUpTime = Long.MIN_VALUE

    @Synchronized
    fun onTap(upTime: Long, heldMs: Long): Boolean {
        if (heldMs !in 0L..MAX_TAP_HOLD_MS) {
            reset()
            return false
        }
        val interval = intervalMs().coerceIn(220L, 600L)
        completedTaps = if (
            completedTaps > 0 &&
            upTime >= lastUpTime &&
            upTime - lastUpTime <= interval
        ) {
            completedTaps + 1
        } else {
            1
        }
        lastUpTime = upTime
        if (completedTaps < requiredTaps) return false
        reset()
        return true
    }

    @Synchronized
    fun reset() {
        completedTaps = 0
        lastUpTime = Long.MIN_VALUE
    }

    private companion object {
        const val MAX_TAP_HOLD_MS = 600L
    }
}
