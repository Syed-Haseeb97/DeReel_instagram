package com.example.dmsonly.web

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DmReelSwipeDetectorTest {
    @Test
    fun verticalMovementIsConsumedWhilePinned() {
        val detector = DmReelSwipeDetector(thresholdPx = 20f)
        detector.start(100f, 200f, enabled = true)
        assertFalse(detector.consumeVerticalMove(104f, 210f, enabled = true) {})
        assertTrue(detector.consumeVerticalMove(104f, 230f, enabled = true) {})
        assertTrue(detector.consumeVerticalMove(104f, 250f, enabled = true) {})
    }

    @Test
    fun horizontalMovementAndUnpinnedTouchesRemainAvailableToReelControls() {
        val detector = DmReelSwipeDetector(thresholdPx = 20f)
        detector.start(100f, 200f, enabled = true)
        assertFalse(detector.consumeVerticalMove(140f, 205f, enabled = true) {})

        detector.start(100f, 200f, enabled = false)
        assertFalse(detector.consumeVerticalMove(100f, 250f, enabled = false) {})
    }

    @Test
    fun invokesReturnActionExactlyOnceAndConsumesEveryMoveUntilGestureEnds() {
        val detector = DmReelSwipeDetector(thresholdPx = 20f)
        var returnActions = 0
        val onBlocked = { returnActions += 1 }
        detector.start(10f, 10f, enabled = true)

        assertFalse(detector.consumeVerticalMove(10f, 20f, enabled = true, onBlocked = onBlocked))
        assertTrue(detector.consumeVerticalMove(10f, 35f, enabled = true, onBlocked = onBlocked))
        assertEquals(1, returnActions)
        assertTrue(detector.consumeVerticalMove(10f, 50f, enabled = false, onBlocked = onBlocked))
        assertTrue(detector.consumeVerticalMove(10f, 80f, enabled = false, onBlocked = onBlocked))
        assertEquals(1, returnActions)
    }

    @Test
    fun upAndCancelClearTheLatchAndAllowANewGesture() {
        val detector = DmReelSwipeDetector(thresholdPx = 20f)
        var returnActions = 0
        val onBlocked = { returnActions += 1 }

        detector.start(0f, 0f, enabled = true)
        assertTrue(detector.consumeVerticalMove(0f, 25f, enabled = true, onBlocked = onBlocked))
        assertTrue(detector.endGesture()) // ACTION_UP
        assertFalse(detector.endGesture())

        detector.start(0f, 0f, enabled = true)
        assertTrue(detector.consumeVerticalMove(0f, 25f, enabled = true, onBlocked = onBlocked))
        assertTrue(detector.endGesture()) // ACTION_CANCEL
        assertFalse(detector.consumeVerticalMove(0f, 50f, enabled = true, onBlocked = onBlocked))
        detector.start(0f, 0f, enabled = true)
        assertTrue(detector.consumeVerticalMove(0f, 25f, enabled = true, onBlocked = onBlocked))
        assertTrue(detector.endGesture())
        assertEquals(3, returnActions)
    }

    @Test
    fun pointerChangeDisqualifiesCandidateButDoesNotReleaseAnActiveBlock() {
        val detector = DmReelSwipeDetector(thresholdPx = 20f)
        detector.start(0f, 0f, enabled = true)
        assertFalse(detector.onPointerChange())
        assertFalse(detector.consumeVerticalMove(0f, 30f, enabled = true) {})

        detector.start(0f, 0f, enabled = true)
        assertTrue(detector.consumeVerticalMove(0f, 25f, enabled = true) {})
        assertTrue(detector.onPointerChange())
        assertTrue(detector.consumeVerticalMove(0f, 30f, enabled = false) {})
        assertTrue(detector.endGesture())
    }

    @Test
    fun tapAndNonQualifyingMovementDoNotTriggerBlock() {
        val detector = DmReelSwipeDetector(thresholdPx = 20f)
        var returnActions = 0
        val onBlocked = { returnActions += 1 }
        detector.start(0f, 0f, enabled = true)
        assertFalse(detector.consumeVerticalMove(3f, 4f, enabled = true, onBlocked = onBlocked))
        assertFalse(detector.endGesture())

        detector.start(0f, 0f, enabled = true)
        assertFalse(detector.consumeVerticalMove(25f, 10f, enabled = true, onBlocked = onBlocked))
        assertEquals(0, returnActions)
        assertFalse(detector.endGesture())
    }
}
