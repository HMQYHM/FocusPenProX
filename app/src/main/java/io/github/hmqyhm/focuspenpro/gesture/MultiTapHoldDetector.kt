package io.github.hmqyhm.focuspenpro.gesture

class MultiTapHoldDetector(
    private val requiredTaps: Int,
    private val scheduler: Scheduler,
    private val intervalMs: () -> Long,
    private val holdMs: () -> Long,
    private val onGesture: (Gesture) -> Unit,
) {
    enum class Gesture { MULTI_TAP, MULTI_TAP_HOLD }

    interface Cancellable { fun cancel() }

    fun interface Scheduler {
        fun schedule(delayMs: Long, block: () -> Unit): Cancellable
    }

    init { require(requiredTaps >= 2) }

    private var pressed = false
    private var completedTaps = 0
    private var lastUpTime = Long.MIN_VALUE
    private var fourthDownCandidate = false
    private var holdFired = false
    private var holdPending: Cancellable? = null
    private var generation = 0L

    @Synchronized
    fun onDown(downTime: Long): Boolean {
        if (pressed) return false
        pressed = true
        holdFired = false
        val interval = intervalMs().coerceIn(220L, 600L)
        val continues = completedTaps > 0 &&
            downTime >= lastUpTime &&
            downTime - lastUpTime <= interval
        if (!continues) completedTaps = 0
        fourthDownCandidate = continues && completedTaps == requiredTaps - 1
        if (fourthDownCandidate) {
            val expectedGeneration = ++generation
            holdPending = scheduler.schedule(holdMs().coerceIn(300L, 1_500L)) {
                val emit = synchronized(this) {
                    if (!pressed || !fourthDownCandidate || generation != expectedGeneration) {
                        false
                    } else {
                        holdFired = true
                        completedTaps = 0
                        lastUpTime = Long.MIN_VALUE
                        holdPending = null
                        true
                    }
                }
                if (emit) onGesture(Gesture.MULTI_TAP_HOLD)
            }
        }
        return true
    }

    @Synchronized
    fun onUp(upTime: Long, heldMs: Long): Boolean {
        if (!pressed) return false
        pressed = false
        holdPending?.cancel()
        holdPending = null
        generation += 1
        if (holdFired) {
            holdFired = false
            fourthDownCandidate = false
            return true
        }
        if (heldMs !in 0L..MAX_TAP_HOLD_MS) {
            resetLocked()
            return false
        }
        if (fourthDownCandidate) {
            resetLocked()
            onGesture(Gesture.MULTI_TAP)
            return true
        }
        completedTaps += 1
        lastUpTime = upTime
        fourthDownCandidate = false
        return true
    }

    @Synchronized
    fun reset() {
        resetLocked()
    }

    private fun resetLocked() {
        pressed = false
        completedTaps = 0
        lastUpTime = Long.MIN_VALUE
        fourthDownCandidate = false
        holdFired = false
        generation += 1
        holdPending?.cancel()
        holdPending = null
    }

    private companion object {
        const val MAX_TAP_HOLD_MS = 600L
    }
}
