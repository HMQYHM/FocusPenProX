package io.github.hmqyhm.focuspenpro.gesture

class PinchGestureMachine(
    private val scheduler: Scheduler,
    private val intervalMs: () -> Long,
    private val onGesture: (Gesture) -> Unit,
    private val longPressMs: () -> Long = { 650L },
) {
    enum class Gesture {
        SINGLE_PINCH,
        DOUBLE_TAP,
        LONG_PINCH_START,
        LONG_PINCH_END,
        LONG_PINCH_CANCEL,
        DOUBLE_TAP_HOLD_START,
        DOUBLE_TAP_HOLD_END,
        DOUBLE_TAP_HOLD_CANCEL,
    }

    interface Cancellable {
        fun cancel()
    }

    fun interface Scheduler {
        fun schedule(delayMs: Long, block: () -> Unit): Cancellable
    }

    private var pressed = false
    private var completedClicks = 0
    private var clickPending: Cancellable? = null
    private var longPressPending: Cancellable? = null
    private var longPressActive = false
    private var activeLongPressTapCount = 0
    private var generation = 0L

    @Synchronized
    fun onDown(): Boolean {
        if (pressed) return false
        pressed = true
        if (completedClicks > 0) {
            clickPending?.cancel()
            clickPending = null
        }
        val expectedClicks = completedClicks
        val expectedGeneration = ++generation
        longPressPending?.cancel()
        longPressPending = scheduler.schedule(longPressMs().coerceIn(300L, 1_500L)) {
            val tapCount = synchronized(this) {
                if (generation != expectedGeneration ||
                    !pressed ||
                    completedClicks != expectedClicks ||
                    longPressActive
                ) {
                    0
                } else {
                    longPressActive = true
                    activeLongPressTapCount = expectedClicks + 1
                    longPressPending = null
                    activeLongPressTapCount
                }
            }
            if (tapCount == 1) onGesture(Gesture.LONG_PINCH_START)
            else if (tapCount == 2) onGesture(Gesture.DOUBLE_TAP_HOLD_START)
        }
        return true
    }

    @Synchronized
    fun onUp(): Boolean {
        if (!pressed) return false
        pressed = false
        longPressPending?.cancel()
        longPressPending = null
        if (longPressActive) {
            val tapCount = activeLongPressTapCount
            longPressActive = false
            activeLongPressTapCount = 0
            completedClicks = 0
            clickPending?.cancel()
            clickPending = null
            generation += 1
            onGesture(
                if (tapCount == 2) Gesture.DOUBLE_TAP_HOLD_END
                else Gesture.LONG_PINCH_END,
            )
            return true
        }
        completedClicks += 1
        if (completedClicks == 1) {
            val expectedGeneration = ++generation
            clickPending?.cancel()
            clickPending = scheduler.schedule(intervalMs().coerceIn(220L, 600L)) {
                val shouldEmit = synchronized(this) {
                    if (generation != expectedGeneration || completedClicks != 1 || pressed) {
                        false
                    } else {
                        completedClicks = 0
                        clickPending = null
                        true
                    }
                }
                if (shouldEmit) onGesture(Gesture.SINGLE_PINCH)
            }
        } else {
            clickPending?.cancel()
            clickPending = null
            completedClicks = 0
            generation += 1
            onGesture(Gesture.DOUBLE_TAP)
        }
        return true
    }

    @Synchronized
    fun cancel() {
        val wasLongPressActive = longPressActive
        val activeTapCount = activeLongPressTapCount
        pressed = false
        completedClicks = 0
        longPressActive = false
        activeLongPressTapCount = 0
        generation += 1
        clickPending?.cancel()
        clickPending = null
        longPressPending?.cancel()
        longPressPending = null
        if (wasLongPressActive) {
            onGesture(
                if (activeTapCount == 2) Gesture.DOUBLE_TAP_HOLD_CANCEL
                else Gesture.LONG_PINCH_CANCEL,
            )
        }
    }
}
