package com.example.poolassistant.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.view.View
import com.example.poolassistant.core.AssistantSettings
import com.example.poolassistant.core.SegmentKind
import com.example.poolassistant.core.SimulationResult
import com.example.poolassistant.core.TableObservation
import com.example.poolassistant.vision.CoordinateMapper

class TrajectoryOverlayView(context: Context) : View(context) {
    var settings = AssistantSettings(); set(value) { field = value; invalidate() }
    var observation: TableObservation? = null; set(value) { field = value; invalidate(); postInvalidateDelayed(1_600) }
    var result: SimulationResult? = null; set(value) { field = value; invalidate() }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!settings.linesEnabled || settings.hidden) return
        val obs = observation ?: return
        if (System.nanoTime() - obs.timestampNanos > 1_500_000_000L) return
        val simulation = result ?: return
        val mapper = CoordinateMapper(obs.captureSize.x, obs.captureSize.y, width.toFloat(), height.toFloat())
        for (segment in simulation.segments) {
            val start = mapper.captureToOverlay(segment.from); val end = mapper.captureToOverlay(segment.to)
            val lowConfidence = segment.confidence < .55f || !simulation.complete
            paint.color = when {
                lowConfidence -> Color.rgb(255, 183, 77)
                segment.kind == SegmentKind.CUE_INCOMING -> Color.WHITE
                segment.kind == SegmentKind.OBJECT_OUTGOING -> Color.rgb(59, 224, 153)
                segment.kind == SegmentKind.CUE_DEFLECTION -> Color.rgb(54, 171, 255)
                else -> Color.rgb(212, 107, 255)
            }
            paint.alpha = (settings.opacity * 255).toInt().coerceIn(20, 255)
            paint.strokeWidth = settings.thickness * resources.displayMetrics.density
            paint.pathEffect = if (lowConfidence) DashPathEffect(floatArrayOf(14f, 10f), 0f) else null
            canvas.drawLine(start.x, start.y, end.x, end.y, paint)
        }
    }
}

