package com.example.dmsonly.web

import kotlin.math.abs

/** Detects a vertical gesture while a DM-shared Reel is pinned. */
internal class DmReelSwipeDetector(private val thresholdPx: Float) {
    private var startX: Float? = null
    private var startY: Float? = null
    private var gestureBlocked = false

    fun start(x: Float, y: Float, enabled: Boolean) {
        // ACTION_DOWN starts a new touch sequence; discard any stale state.
        reset()
        startX = x.takeIf { enabled }
        startY = y.takeIf { enabled }
    }

    /** Returns true for every move in a blocked sequence and calls [onBlocked] once. */
    fun consumeVerticalMove(
        x: Float,
        y: Float,
        enabled: Boolean,
        onBlocked: () -> Unit
    ): Boolean {
        if (gestureBlocked) return true

        val originX = startX
        val originY = startY
        if (!enabled || originX == null || originY == null) return false
        val dx = x - originX
        val dy = y - originY
        if (abs(dy) < thresholdPx || abs(dy) <= abs(dx)) return false

        gestureBlocked = true
        onBlocked()
        return true
    }

    /** A second pointer disqualifies an unblocked candidate; an active block stays latched. */
    fun onPointerChange(): Boolean {
        if (gestureBlocked) return true
        startX = null
        startY = null
        return false
    }

    /** Ends or cancels the current touch sequence and reports whether it was blocked. */
    fun endGesture(): Boolean {
        val wasBlocked = gestureBlocked
        reset()
        return wasBlocked
    }

    private fun reset() {
        startX = null
        startY = null
        gestureBlocked = false
    }
}
