package com.example.poolassistant.calibration

import com.example.poolassistant.core.Vec2
import kotlin.math.abs

data class TimedPosition(val timeSeconds: Float, val position: Vec2)
data class CalibrationEstimate(val value: Float?, val samples: Int, val residual: Float, val reliable: Boolean)

object CalibrationEstimator {
    fun rollingDeceleration(track: List<TimedPosition>): CalibrationEstimate {
        if (track.size < 4) return CalibrationEstimate(null, 0, Float.POSITIVE_INFINITY, false)
        val speeds = track.zipWithNext().mapNotNull { (a, b) ->
            val dt = b.timeSeconds - a.timeSeconds
            if (dt <= 0f) null else (b.position - a.position).length() / dt to (a.timeSeconds + b.timeSeconds) * .5f
        }
        if (speeds.size < 3) return CalibrationEstimate(null, speeds.size, Float.POSITIVE_INFINITY, false)
        val meanT = speeds.map { it.second }.average().toFloat(); val meanV = speeds.map { it.first }.average().toFloat()
        val denominator = speeds.sumOf { ((it.second - meanT) * (it.second - meanT)).toDouble() }.toFloat()
        if (denominator < 1e-6f) return CalibrationEstimate(null, speeds.size, Float.POSITIVE_INFINITY, false)
        val slope = speeds.sumOf { ((it.second - meanT) * (it.first - meanV)).toDouble() }.toFloat() / denominator
        val residual = speeds.map { (v, t) -> abs(v - (meanV + slope * (t - meanT))) }.average().toFloat()
        val deceleration = -slope
        return CalibrationEstimate(deceleration.takeIf { it > 0f }, speeds.size, residual, deceleration > 0f && residual < meanV * .15f)
    }

    fun cushionRestitution(incomingSpeed: Float, outgoingSpeed: Float): CalibrationEstimate {
        if (incomingSpeed <= 0f || outgoingSpeed < 0f) return CalibrationEstimate(null, 0, Float.POSITIVE_INFINITY, false)
        val ratio = outgoingSpeed / incomingSpeed
        return CalibrationEstimate(ratio, 1, 0f, ratio in .3f..1f)
    }
}

