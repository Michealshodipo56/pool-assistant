package com.example.poolassistant.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import kotlin.math.max
import kotlin.math.min

/** A temporary, in-game touch layer. It is removed as soon as four corners are saved. */
class TableCornerSelectionView(
    context: Context,
    private val onComplete: (RectF) -> Unit,
    private val onCancel: () -> Unit
) : View(context) {
    private val corners = mutableListOf<PointF>()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val labels = arrayOf("top-left", "top-right", "bottom-right", "bottom-left")
    private val density = resources.displayMetrics.density
    private var error: String? = null

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val buttonWidth = 86f * density
        val buttonHeight = 42f * density
        paint.style = Paint.Style.FILL
        paint.color = Color.argb(210, 13, 28, 26)
        canvas.drawRoundRect(8f * density, 8f * density, width - 8f * density, 56f * density, 12f * density, 12f * density, paint)
        paint.color = Color.WHITE
        paint.textSize = 16f * density
        val next = corners.size.coerceAtMost(3)
        canvas.drawText(error ?: "Tap the inside ${labels[next]} corner (${next + 1}/4)", 18f * density, 39f * density, paint)
        paint.color = Color.rgb(38, 126, 111)
        canvas.drawRoundRect(8f * density, height - buttonHeight - 8f * density, 8f * density + buttonWidth, height - 8f * density, 10f * density, 10f * density, paint)
        canvas.drawRoundRect(width - buttonWidth - 8f * density, height - buttonHeight - 8f * density, width - 8f * density, height - 8f * density, 10f * density, 10f * density, paint)
        paint.color = Color.WHITE
        paint.textSize = 15f * density
        canvas.drawText("Cancel", 23f * density, height - 23f * density, paint)
        canvas.drawText("Undo", width - buttonWidth + 8f * density, height - 23f * density, paint)
        paint.color = Color.rgb(55, 228, 191)
        paint.strokeWidth = 3f * density
        paint.style = Paint.Style.STROKE
        for (i in 1 until corners.size) {
            canvas.drawLine(corners[i - 1].x, corners[i - 1].y, corners[i].x, corners[i].y, paint)
        }
        paint.style = Paint.Style.FILL
        corners.forEach { canvas.drawCircle(it.x, it.y, 7f * density, paint) }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked != MotionEvent.ACTION_UP) return true
        val buttonWidth = 86f * density
        val buttonHeight = 42f * density
        if (event.y >= height - buttonHeight - 8f * density) {
            if (event.x <= buttonWidth + 8f * density) { onCancel(); return true }
            if (event.x >= width - buttonWidth - 8f * density) {
                if (corners.isNotEmpty()) corners.removeAt(corners.lastIndex)
                error = null
                invalidate()
                return true
            }
        }
        if (event.y < 65f * density) return true
        corners += PointF(event.x, event.y)
        error = null
        if (corners.size == 4) {
            val left = min(corners[0].x, corners[3].x)
            val top = min(corners[0].y, corners[1].y)
            val right = max(corners[1].x, corners[2].x)
            val bottom = max(corners[2].y, corners[3].y)
            val boxWidth = right - left
            val boxHeight = bottom - top
            if (boxWidth < width * .25f || boxHeight < height * .25f ||
                boxWidth / boxHeight !in 1.2f..3.5f ||
                corners[0].x >= corners[1].x || corners[3].x >= corners[2].x ||
                corners[0].y >= corners[3].y || corners[1].y >= corners[2].y) {
                corners.clear()
                error = "Corners didn't form a table. Tap top-left to retry."
            } else {
                onComplete(RectF(left, top, right, bottom))
                return true
            }
        }
        invalidate()
        return true
    }
}
