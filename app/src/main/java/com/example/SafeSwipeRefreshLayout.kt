package com.example

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout

/**
 * Custom SwipeRefreshLayout that safely handles touch event dispatching
 * to prevent "Got ACTION_MOVE event but don't have an active pointer id" errors
 * and pointer index exceptions during fast scrolling or virtual cursor interaction.
 */
class SafeSwipeRefreshLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : SwipeRefreshLayout(context, attrs) {

    private var hasActivePointer = false

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        val action = ev.actionMasked
        if (action == MotionEvent.ACTION_DOWN) {
            hasActivePointer = true
        } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            hasActivePointer = false
        } else if (action == MotionEvent.ACTION_MOVE && !hasActivePointer) {
            // Ignore orphaned ACTION_MOVE events if ACTION_DOWN was not intercepted
            return false
        }
        return try {
            super.onInterceptTouchEvent(ev)
        } catch (_: Exception) {
            false
        }
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        val action = ev.actionMasked
        if (action == MotionEvent.ACTION_DOWN) {
            hasActivePointer = true
        } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            hasActivePointer = false
        } else if (action == MotionEvent.ACTION_MOVE && !hasActivePointer) {
            // Ignore orphaned ACTION_MOVE events if ACTION_DOWN was not received
            return false
        }
        return try {
            super.onTouchEvent(ev)
        } catch (_: Exception) {
            false
        }
    }
}
