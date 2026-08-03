package io.github.hmqyhm.focuspenpro.gesture

import org.junit.Assert.assertEquals
import org.junit.Test

class PinchGestureMachineTest {
    @Test
    fun singleIsDelayedUntilTimeout() {
        val scheduler = FakeScheduler()
        val gestures = mutableListOf<PinchGestureMachine.Gesture>()
        val machine = PinchGestureMachine(scheduler, { 360L }, gestures::add)

        machine.onDown()
        machine.onUp()
        assertEquals(emptyList<PinchGestureMachine.Gesture>(), gestures)

        scheduler.runAll()
        assertEquals(listOf(PinchGestureMachine.Gesture.SINGLE_PINCH), gestures)
    }

    @Test
    fun secondClickCancelsSingleAndEmitsDouble() {
        val scheduler = FakeScheduler()
        val gestures = mutableListOf<PinchGestureMachine.Gesture>()
        val machine = PinchGestureMachine(scheduler, { 360L }, gestures::add)

        machine.onDown()
        machine.onUp()
        machine.onDown()
        machine.onUp()
        scheduler.runAll()

        assertEquals(listOf(PinchGestureMachine.Gesture.DOUBLE_TAP), gestures)
    }

    @Test
    fun cancellationSuppressesPendingGesture() {
        val scheduler = FakeScheduler()
        val gestures = mutableListOf<PinchGestureMachine.Gesture>()
        val machine = PinchGestureMachine(scheduler, { 360L }, gestures::add)

        machine.onDown()
        machine.onUp()
        machine.cancel()
        scheduler.runAll()

        assertEquals(emptyList<PinchGestureMachine.Gesture>(), gestures)
    }

    @Test
    fun repeatedDownDoesNotCreateExtraClick() {
        val scheduler = FakeScheduler()
        val gestures = mutableListOf<PinchGestureMachine.Gesture>()
        val machine = PinchGestureMachine(scheduler, { 360L }, gestures::add)

        machine.onDown()
        machine.onDown()
        machine.onUp()
        scheduler.runAll()

        assertEquals(listOf(PinchGestureMachine.Gesture.SINGLE_PINCH), gestures)
    }

    @Test
    fun strayUpIsIgnored() {
        val scheduler = FakeScheduler()
        val gestures = mutableListOf<PinchGestureMachine.Gesture>()
        val machine = PinchGestureMachine(scheduler, { 360L }, gestures::add)

        machine.onUp()
        scheduler.runAll()

        assertEquals(emptyList<PinchGestureMachine.Gesture>(), gestures)
    }

    @Test
    fun heldFirstPinchStartsAndReleasesLongPress() {
        val scheduler = FakeScheduler()
        val gestures = mutableListOf<PinchGestureMachine.Gesture>()
        val machine = PinchGestureMachine(
            scheduler,
            { 360L },
            gestures::add,
            { 500L },
        )

        machine.onDown()
        scheduler.runAll()
        assertEquals(
            listOf(PinchGestureMachine.Gesture.LONG_PINCH_START),
            gestures,
        )

        machine.onUp()
        assertEquals(
            listOf(
                PinchGestureMachine.Gesture.LONG_PINCH_START,
                PinchGestureMachine.Gesture.LONG_PINCH_END,
            ),
            gestures,
        )
    }

    @Test
    fun cancellationReleasesActiveLongPress() {
        val scheduler = FakeScheduler()
        val gestures = mutableListOf<PinchGestureMachine.Gesture>()
        val machine = PinchGestureMachine(
            scheduler,
            { 360L },
            gestures::add,
            { 500L },
        )

        machine.onDown()
        scheduler.runAll()
        machine.cancel()

        assertEquals(
            listOf(
                PinchGestureMachine.Gesture.LONG_PINCH_START,
                PinchGestureMachine.Gesture.LONG_PINCH_CANCEL,
            ),
            gestures,
        )
    }

    @Test
    fun heldSecondPinchStartsAndReleasesDoubleHold() {
        val scheduler = FakeScheduler()
        val gestures = mutableListOf<PinchGestureMachine.Gesture>()
        val machine = PinchGestureMachine(scheduler, { 360L }, gestures::add, { 500L })

        machine.onDown()
        machine.onUp()
        machine.onDown()
        scheduler.runAll()
        assertEquals(listOf(PinchGestureMachine.Gesture.DOUBLE_TAP_HOLD_START), gestures)

        machine.onUp()
        assertEquals(
            listOf(
                PinchGestureMachine.Gesture.DOUBLE_TAP_HOLD_START,
                PinchGestureMachine.Gesture.DOUBLE_TAP_HOLD_END,
            ),
            gestures,
        )
    }

    private class FakeScheduler : PinchGestureMachine.Scheduler {
        private data class Task(var cancelled: Boolean, val block: () -> Unit)
        private val tasks = mutableListOf<Task>()

        override fun schedule(
            delayMs: Long,
            block: () -> Unit,
        ): PinchGestureMachine.Cancellable {
            val task = Task(false, block)
            tasks += task
            return object : PinchGestureMachine.Cancellable {
                override fun cancel() {
                    task.cancelled = true
                }
            }
        }

        fun runAll() {
            tasks.toList().also(tasks::removeAll).forEach {
                if (!it.cancelled) it.block()
            }
        }
    }
}
