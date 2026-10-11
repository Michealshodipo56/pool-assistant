package com.example.poolassistant.core

import android.graphics.RectF
import kotlin.math.hypot

data class Vec2(val x: Float, val y: Float) {
    operator fun plus(other: Vec2) = Vec2(x + other.x, y + other.y)
    operator fun minus(other: Vec2) = Vec2(x - other.x, y - other.y)
    operator fun times(scale: Float) = Vec2(x * scale, y * scale)
    fun length() = hypot(x, y)
    fun normalized(): Vec2 = length().takeIf { it > 1e-6f }?.let { Vec2(x / it, y / it) } ?: Vec2(0f, 0f)
}

enum class TrajectoryMode { BASIC, ALL }
enum class SegmentKind { CUE_INCOMING, OBJECT_OUTGOING, CUE_DEFLECTION, SECONDARY, UNCERTAIN }
enum class EventKind { BALL_COLLISION, CUSHION, POCKET, STOPPED, LIMIT_REACHED }

data class DetectedBall(val id: Int, val center: Vec2, val radius: Float, val cueBall: Boolean, val confidence: Float)
data class TableObservation(
    val captureSize: Vec2,
    val tableBounds: RectF,
    val pockets: List<Vec2>,
    val balls: List<DetectedBall>,
    val cueDirection: Vec2?,
    val confidence: Float,
    val timestampNanos: Long
)

data class PhysicsConfig(
    val rollingDeceleration: Float = 0.24f,
    val cushionRestitution: Float = 0.82f,
    val ballRestitution: Float = 0.96f,
    val stopSpeed: Float = 0.025f,
    val timeStep: Float = 1f / 180f,
    val maxSeconds: Float = 8f,
    val maxEvents: Int = 128
)

data class BallState(val id: Int, val position: Vec2, val velocity: Vec2, val radius: Float, val cueBall: Boolean)
data class TrajectorySegment(val ballId: Int, val from: Vec2, val to: Vec2, val kind: SegmentKind, val confidence: Float)
data class SimulationEvent(val time: Float, val kind: EventKind, val primaryId: Int, val secondaryId: Int? = null, val at: Vec2)
data class SimulationResult(
    val segments: List<TrajectorySegment>,
    val events: List<SimulationEvent>,
    val restingPositions: Map<Int, Vec2>,
    val complete: Boolean,
    val confidence: Float
)

data class CalibrationProfile(
    val tableLeft: Float = 0.08f,
    val tableTop: Float = 0.18f,
    val tableRight: Float = 0.92f,
    val tableBottom: Float = 0.82f,
    val ballRadiusFraction: Float = 0.027f,
    val cushionRestitution: Float = 0.82f,
    val rollingDeceleration: Float = 0.24f,
    val assumedShotSpeed: Float = 1.8f,
    val tableMarked: Boolean = false,
    val validated: Boolean = false
)

data class AssistantSettings(
    val linesEnabled: Boolean = true,
    val hidden: Boolean = false,
    val mode: TrajectoryMode = TrajectoryMode.BASIC,
    val opacity: Float = 0.82f,
    val thickness: Float = 3f,
    val bubbleX: Int = 24,
    val bubbleY: Int = 240,
    val calibration: CalibrationProfile = CalibrationProfile()
)

data class PerformanceTelemetry(
    val captureMs: Float = 0f,
    val detectionMs: Float = 0f,
    val physicsMs: Float = 0f,
    val renderMs: Float = 0f,
    val endToEndMs: Float = 0f,
    val processedFps: Float = 0f,
    val memoryMb: Float = 0f
)
