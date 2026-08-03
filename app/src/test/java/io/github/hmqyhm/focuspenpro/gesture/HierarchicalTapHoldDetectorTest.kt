package io.github.hmqyhm.focuspenpro.gesture

import org.junit.Assert.assertEquals
import org.junit.Test

class HierarchicalTapHoldDetectorTest {
    @Test fun doubleWaitsAndLosesToFourTap() {
        val scheduler = FakeScheduler()
        val gestures = mutableListOf<Pair<Int, Boolean>>()
        val detector = detector(scheduler, gestures)
        repeat(4) { tap(detector, it * 100L) }
        scheduler.runAll()
        assertEquals(listOf(4 to false), gestures)
    }

    @Test fun doubleEmitsAfterTimeout() {
        val scheduler = FakeScheduler()
        val gestures = mutableListOf<Pair<Int, Boolean>>()
        val detector = detector(scheduler, gestures)
        tap(detector, 0L)
        tap(detector, 100L)
        assertEquals(emptyList<Pair<Int, Boolean>>(), gestures)
        scheduler.runAll()
        assertEquals(listOf(2 to false), gestures)
    }

    @Test fun secondHoldWinsImmediately() {
        val scheduler = FakeScheduler()
        val gestures = mutableListOf<Pair<Int, Boolean>>()
        val detector = detector(scheduler, gestures)
        tap(detector, 0L)
        detector.onDown(100L)
        scheduler.runAll()
        assertEquals(listOf(2 to true), gestures)
        detector.onUp(800L, 700L)
        assertEquals(listOf(2 to true), gestures)
    }

    private fun detector(
        scheduler: FakeScheduler,
        gestures: MutableList<Pair<Int, Boolean>>,
    ) = HierarchicalTapHoldDetector(
        maxTaps = 4,
        tapMilestones = setOf(2, 4),
        holdMilestones = setOf(2, 4),
        scheduler = scheduler,
        intervalMs = { 360L },
        holdMs = { 650L },
        onGesture = { count, hold -> gestures += count to hold },
        onIncomplete = {},
    )

    private fun tap(detector: HierarchicalTapHoldDetector, time: Long) {
        detector.onDown(time)
        detector.onUp(time + 20L, 20L)
    }

    private class FakeScheduler : HierarchicalTapHoldDetector.Scheduler {
        private data class Task(var cancelled: Boolean, val block: () -> Unit)
        private val tasks = mutableListOf<Task>()
        override fun schedule(delayMs: Long, block: () -> Unit): HierarchicalTapHoldDetector.Cancellable {
            val task = Task(false, block)
            tasks += task
            return HierarchicalTapHoldDetector.Cancellable { task.cancelled = true }
        }
        fun runAll() {
            while (tasks.any { !it.cancelled }) {
                val current = tasks.toList()
                tasks.clear()
                current.filterNot(Task::cancelled).forEach { it.block() }
            }
        }
    }
}
