package com.example.poolassistant.vision

import android.graphics.RectF
import com.example.poolassistant.core.Vec2

class CoordinateMapper(
    private val captureWidth: Float,
    private val captureHeight: Float,
    private val overlayWidth: Float,
    private val overlayHeight: Float,
    private val rotationDegrees: Int = 0
) {
    fun captureToOverlay(point: Vec2): Vec2 {
        val rotated = when ((rotationDegrees % 360 + 360) % 360) {
            90 -> Vec2(captureHeight - point.y, point.x)
            180 -> Vec2(captureWidth - point.x, captureHeight - point.y)
            270 -> Vec2(point.y, captureWidth - point.x)
            else -> point
        }
        val sourceW = if (rotationDegrees % 180 == 0) captureWidth else captureHeight
        val sourceH = if (rotationDegrees % 180 == 0) captureHeight else captureWidth
        return Vec2(rotated.x * overlayWidth / sourceW, rotated.y * overlayHeight / sourceH)
    }

    fun normalizedTableToCapture(point: Vec2, table: RectF) = Vec2(
        table.left + point.x * table.width(),
        table.top + point.y * table.height()
    )
}

