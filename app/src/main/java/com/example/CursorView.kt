package com.example

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.View

/**
 * Kumanda (D-pad) için WebView üstünde gezdirilen sanal fare imleci.
 * Dokunma olaylarını almaz; yalnızca çizim yapar. Konum MainActivity tarafından yönetilir.
 */
class CursorView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    var cursorX = 0f
        private set
    var cursorY = 0f
        private set

    private val density = resources.displayMetrics.density
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
        style = Paint.Style.FILL
    }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF06B6D4.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 2f * density
    }
    private val arrow = Path()
    private val handler = Handler(Looper.getMainLooper())
    private val fadeRunnable = Runnable { animate().alpha(0.25f).setDuration(400).start() }
    private val hideRunnable = Runnable { animate().alpha(0f).setDuration(600).start() }

    init {
        isClickable = false
        isFocusable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        alpha = 0f
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (cursorX == 0f && cursorY == 0f) {
            cursorX = w / 2f
            cursorY = h / 2f
        } else {
            cursorX = cursorX.coerceIn(0f, w.toFloat() - 1f)
            cursorY = cursorY.coerceIn(0f, h.toFloat() - 1f)
        }
    }

    /** İmleci hareket ettirir; ekran kenarına taşan miktarı (overflowX, overflowY) döndürür. */
    fun moveBy(dx: Float, dy: Float): Pair<Float, Float> {
        val maxX = (width - 1).coerceAtLeast(0).toFloat()
        val maxY = (height - 1).coerceAtLeast(0).toFloat()
        val newX = cursorX + dx
        val newY = cursorY + dy
        cursorX = newX.coerceIn(0f, maxX)
        cursorY = newY.coerceIn(0f, maxY)
        val overflowX = newX - cursorX
        val overflowY = newY - cursorY
        wake()
        invalidate()
        return Pair(overflowX, overflowY)
    }

    /** İmleci görünür yapar; 3 sn hareketsiz kalınca soluklaşır, sonra gizlenir. */
    fun wake() {
        handler.removeCallbacks(fadeRunnable)
        handler.removeCallbacks(hideRunnable)
        animate().cancel()
        alpha = 1f
        handler.postDelayed(fadeRunnable, 3000)
        handler.postDelayed(hideRunnable, 8000)
    }

    fun hideNow() {
        handler.removeCallbacks(fadeRunnable)
        handler.removeCallbacks(hideRunnable)
        animate().cancel()
        alpha = 0f
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val s = density
        arrow.reset()
        arrow.moveTo(cursorX, cursorY)
        arrow.lineTo(cursorX, cursorY + 20f * s)
        arrow.lineTo(cursorX + 5f * s, cursorY + 15f * s)
        arrow.lineTo(cursorX + 9f * s, cursorY + 23f * s)
        arrow.lineTo(cursorX + 12f * s, cursorY + 21.5f * s)
        arrow.lineTo(cursorX + 8f * s, cursorY + 14f * s)
        arrow.lineTo(cursorX + 15f * s, cursorY + 14f * s)
        arrow.close()
        canvas.drawPath(arrow, fillPaint)
        canvas.drawPath(arrow, strokePaint)
    }

    override fun onDetachedFromWindow() {
        handler.removeCallbacks(fadeRunnable)
        handler.removeCallbacks(hideRunnable)
        super.onDetachedFromWindow()
    }
}
