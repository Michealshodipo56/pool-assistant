package com.example.poolassistant.physics

import android.graphics.RectF
import com.example.poolassistant.core.*
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NativePhysicsBridge @Inject constructor() : PhysicsEngine {
    companion object {
        init { System.loadLibrary("poolphysics") }
    }

    private external fun simulateNative(input: FloatArray, ballCount: Int, pocketCount: Int, mode: Int, maxEvents: Int): FloatArray

    override fun simulate(
        table: RectF,
        pockets: List<Vec2>,
        balls: List<BallState>,
        mode: TrajectoryMode,
        config: PhysicsConfig,
        observationConfidence: Float
    ): SimulationResult {
        val header = floatArrayOf(
            table.left, table.top, table.right, table.bottom,
            config.rollingDeceleration, config.cushionRestitution, config.ballRestitution,
            config.stopSpeed, config.timeStep, config.maxSeconds
        )
        val input = ArrayList<Float>(header.size + pockets.size * 2 + balls.size * 7)
        header.forEach(input::add)
        pockets.forEach { input += it.x; input += it.y }
        balls.forEach {
            input += it.id.toFloat(); input += it.position.x; input += it.position.y
            input += it.velocity.x; input += it.velocity.y; input += it.radius; input += if (it.cueBall) 1f else 0f
        }
        val raw = simulateNative(input.toFloatArray(), balls.size, pockets.size, mode.ordinal, config.maxEvents)
        return decode(raw, observationConfidence)
    }

    private fun decode(raw: FloatArray, observationConfidence: Float): SimulationResult {
        if (raw.size < 4) return SimulationResult(emptyList(), emptyList(), emptyMap(), false, 0f)
        var i = 0
        val complete = raw[i++] > .5f
        val segmentCount = raw[i++].toInt()
        val eventCount = raw[i++].toInt()
        val restCount = raw[i++].toInt()
        val segments = ArrayList<TrajectorySegment>(segmentCount)
        repeat(segmentCount) {
            if (i + 7 > raw.size) return@repeat
            segments += TrajectorySegment(
                raw[i++].toInt(), Vec2(raw[i++], raw[i++]), Vec2(raw[i++], raw[i++]),
                SegmentKind.entries.getOrElse(raw[i++].toInt()) { SegmentKind.UNCERTAIN },
                (raw[i++] * observationConfidence).coerceIn(0f, 1f)
            )
        }
        val events = ArrayList<SimulationEvent>(eventCount)
        repeat(eventCount) {
            if (i + 6 > raw.size) return@repeat
            val time = raw[i++]; val kind = EventKind.entries.getOrElse(raw[i++].toInt()) { EventKind.LIMIT_REACHED }
            val first = raw[i++].toInt(); val second = raw[i++].toInt().takeIf { it >= 0 }; val at = Vec2(raw[i++], raw[i++])
            events += SimulationEvent(time, kind, first, second, at)
        }
        val rests = buildMap {
            repeat(restCount) { if (i + 3 <= raw.size) put(raw[i++].toInt(), Vec2(raw[i++], raw[i++])) }
        }
        return SimulationResult(segments, events, rests, complete, observationConfidence.coerceIn(0f, 1f))
    }
}
