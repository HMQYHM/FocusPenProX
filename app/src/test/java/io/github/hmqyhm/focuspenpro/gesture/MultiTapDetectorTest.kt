package io.github.hmqyhm.focuspenpro.gesture

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MultiTapDetectorTest {
    @Test
    fun emitsOnlyOnFourthTapWithinWindow() {
        val detector = MultiTapDetector(4) { 360L }
        assertFalse(detector.onTap(100L, 80L))
        assertFalse(detector.onTap(350L, 70L))
        assertFalse(detector.onTap(610L, 90L))
        assertTrue(detector.onTap(850L, 80L))
    }

    @Test
    fun timeoutStartsANewSequence() {
        val detector = MultiTapDetector(4) { 360L }
        assertFalse(detector.onTap(100L, 50L))
        assertFalse(detector.onTap(700L, 50L))
        assertFalse(detector.onTap(900L, 50L))
        assertFalse(detector.onTap(1_100L, 50L))
        assertTrue(detector.onTap(1_300L, 50L))
    }

    @Test
    fun heldPressCancelsSequence() {
        val detector = MultiTapDetector(4) { 360L }
        assertFalse(detector.onTap(100L, 50L))
        assertFalse(detector.onTap(300L, 700L))
        assertFalse(detector.onTap(500L, 50L))
        assertFalse(detector.onTap(700L, 50L))
        assertFalse(detector.onTap(900L, 50L))
    }
}
