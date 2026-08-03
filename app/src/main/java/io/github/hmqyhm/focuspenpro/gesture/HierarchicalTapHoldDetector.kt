package io.github.hmqyhm.focuspenpro.gesture

/** Resolves a lower tap count only after a higher-priority sequence can no longer win. */
class HierarchicalTapHoldDetector(
    private val maxTaps: Int,
    private val tapMilestones: Set<Int>,
    private val holdMilestones: Set<Int>,
    private val scheduler: Scheduler,
    private val intervalMs: () -> Long,
    private val holdMs: () -> Long,
    private val onGesture: (tapCount: Int, hold: Boolean) -> Unit,
    private val onIncomplete: (tapCount: Int) -> Unit,
) {
    fun interface Cancellable { fun cancel() }
    fun interface Scheduler {
        fun schedule(delayMs: Long, block: () -> Unit): Cancellable
    }

    private var pressed = false
    private var completedTaps = 0
    private var lastUpTime = Long.MIN_VALUE
    private var downTapCount = 0
    private var holdFired = false
    private var generation = 0L
    private var pending: Cancellable? = null

    init {
        require(maxTaps >= 2)
        require(tapMilestones.all { it in 2..maxTaps })
        require(holdMilestones.all { it in 2..maxTaps })
    }

    @Synchronized
    fun onDown(eventTime: Long): Boolean {
        if (pressed) return false
        pending?.cancel()
        pending = null
        val interval = intervalMs().coerceIn(220L, 600L)
        val continues = completedTaps > 0 && eventTime >= lastUpTime &&
            eventTime - lastUpTime <= interval
        if (!continues) completedTaps = 0
        pressed = true
        holdFired = false
        downTapCount = completedTaps + 1
        if (downTapCount in holdMilestones) {
            val expected = ++generation
            pending = scheduler.schedule(holdMs().coerceIn(300L, 1_500L)) {
                val count = synchronized(this) {
                    if (!pressed || generation != expected || downTapCount !in holdMilestones) {
                        0
                    } else {
                        holdFired = true
                        val result = downTapCount
                        completedTaps = 0
                        lastUpTime = Long.MIN_VALUE
                        pending = null
                        result
                    }
                }
                if (count > 0) onGesture(count, true)
            }
        }
        return true
    }

    @Synchronized
    fun onUp(eventTime: Long, heldMs: Long): Boolean {
        if (!pressed) return false
        pressed = false
        pending?.cancel()
        pending = null
        generation += 1
        if (holdFired) {
            holdFired = false
            downTapCount = 0
            return true
        }
        if (heldMs !in 0L..MAX_TAP_HOLD_MS) {
            val incomplete = completedTaps
            resetLocked()
            if (incomplete > 0) onIncomplete(incomplete)
            return false
        }
        completedTaps = downTapCount
        lastUpTime = eventTime
        downTapCount = 0
        if (completedTaps == maxTaps) {
            val count = completedTaps
            resetLocked()
            onGesture(count, false)
            return true
        }
        val expected = ++generation
        pending = scheduler.schedule(intervalMs().coerceIn(220L, 600L)) {
            val count = synchronized(this) {
                if (pressed || generation != expected) 0 else completedTaps.also(::resetLocked)
            }
            if (count > 0) {
                if (count in tapMilestones) onGesture(count, false)
                else onIncomplete(count)
            }
        }
        return true
    }

    @Synchronized
    fun reset() = resetLocked()

    private fun resetLocked(@Suppress("UNUSED_PARAMETER") ignored: Int = 0) {
        pressed = false
        completedTaps = 0
        lastUpTime = Long.MIN_VALUE
        downTapCount = 0
        holdFired = false
        generation += 1
        pending?.cancel()
        pending = null
    }

    private companion object {
        const val MAX_TAP_HOLD_MS = 600L
    }
}
