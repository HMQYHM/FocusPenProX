package io.github.hmqyhm.focuspenpro.gesture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MultiTapHoldDetectorTest {
    private class FakeScheduler : MultiTapHoldDetector.Scheduler {
        var pending: (() -> Unit)? = null
        override fun schedule(delayMs: Long, block: () -> Unit) =
            object : MultiTapHoldDetector.Cancellable {
                init { pending = block }
                override fun cancel() { if (pending === block) pending = null }
            }
        fun fire() { pending?.also { pending = null }?.invoke() }
    }

    @Test
    fun fourthReleaseEmitsOnlyRegularGesture() {
        val scheduler = FakeScheduler()
        val gestures = mutableListOf<MultiTapHoldDetector.Gesture>()
        val detector = detector(scheduler, gestures)
        tap(detector, 0L)
        tap(detector, 200L)
        tap(detector, 400L)
        assertTrue(detector.onDown(600L))
        assertTrue(detector.onUp(680L, 80L))
        assertEquals(listOf(MultiTapHoldDetector.Gesture.MULTI_TAP), gestures)
    }

    @Test
    fun holdingFourthEmitsHoldAndReleaseDoesNotEmitRegular() {
        val scheduler = FakeScheduler()
        val gestures = mutableListOf<MultiTapHoldDetector.Gesture>()
        val detector = detector(scheduler, gestures)
        tap(detector, 0L)
        tap(detector, 200L)
        tap(detector, 400L)
        assertTrue(detector.onDown(600L))
        scheduler.fire()
        assertTrue(detector.onUp(1_300L, 700L))
        assertEquals(listOf(MultiTapHoldDetector.Gesture.MULTI_TAP_HOLD), gestures)
    }

    @Test
    fun timeoutPreventsFourthHoldCandidate() {
        val scheduler = FakeScheduler()
        val gestures = mutableListOf<MultiTapHoldDetector.Gesture>()
        val detector = detector(scheduler, gestures)
        tap(detector, 0L)
        tap(detector, 200L)
        tap(detector, 400L)
        assertTrue(detector.onDown(1_000L))
        assertEquals(null, scheduler.pending)
        assertFalse(gestures.isNotEmpty())
    }

    private fun detector(
        scheduler: FakeScheduler,
        gestures: MutableList<MultiTapHoldDetector.Gesture>,
    ) = MultiTapHoldDetector(
        requiredTaps = 4,
        scheduler = scheduler,
        intervalMs = { 360L },
        holdMs = { 650L },
        onGesture = gestures::add,
    )

    private fun tap(detector: MultiTapHoldDetector, down: Long) {
        assertTrue(detector.onDown(down))
        assertTrue(detector.onUp(down + 80L, 80L))
    }
}
