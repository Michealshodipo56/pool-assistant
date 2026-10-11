package com.example.poolassistant.pipeline

import android.graphics.Bitmap
import android.util.Log
import com.example.poolassistant.core.*
import com.example.poolassistant.physics.PhysicsEngine
import com.example.poolassistant.game.EightBallPoolProfile
import com.example.poolassistant.settings.SettingsRepository
import com.example.poolassistant.vision.VisionEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.system.measureNanoTime

@Singleton
class PredictionCoordinator @Inject constructor(
    private val vision: VisionEngine,
    private val physics: PhysicsEngine,
    private val settingsRepository: SettingsRepository
) {
    private data class Frame(val bitmap: Bitmap, val timestamp: Long)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val frames = Channel<Frame>(Channel.CONFLATED, onUndeliveredElement = { it.bitmap.recycle() })
    private val _prediction = MutableStateFlow<SimulationResult?>(null)
    val prediction: StateFlow<SimulationResult?> = _prediction.asStateFlow()
    private val _observation = MutableStateFlow<TableObservation?>(null)
    val observation: StateFlow<TableObservation?> = _observation.asStateFlow()
    private val _telemetry = MutableStateFlow(PerformanceTelemetry())
    val telemetry: StateFlow<PerformanceTelemetry> = _telemetry.asStateFlow()
    private val _status = MutableStateFlow("Waiting for screen capture")
    val status: StateFlow<String> = _status.asStateFlow()

    init { scope.launch { processLoop() } }
    fun submit(bitmap: Bitmap, timestampNanos: Long) {
        if (frames.trySend(Frame(bitmap, timestampNanos)).isFailure) bitmap.recycle()
    }
    fun invalidate() { _observation.value = null; _prediction.value = null; _status.value = "Capture stopped"; vision.resetTracking() }

    private suspend fun processLoop() {
        for (frame in frames) {
            val started = System.nanoTime()
            try {
                _status.value = "Analyzing captured frame"
                val settings = settingsRepository.settings.first()
                var observation: TableObservation? = null
                val detectionNs = measureNanoTime { observation = vision.analyze(frame.bitmap, settings.calibration, frame.timestamp) }
                // ImageReader timestamps are not a reliable age for an expensive analysis result.
                // Freshness begins when this result is ready to be drawn, not when capture began.
                observation = observation?.copy(timestampNanos = System.nanoTime())
                _observation.value = observation
                var result: SimulationResult? = null
                val physicsNs = measureNanoTime {
                    result = observation?.let { o ->
                        val cue = o.balls.firstOrNull { it.cueBall }
                        val direction = o.cueDirection
                        if (cue == null || direction == null) null else {
                            val speed = settings.calibration.assumedShotSpeed * o.tableBounds.width()
                            val states = o.balls.map { b -> BallState(b.id, b.center, if (b.id == cue.id) direction * speed else Vec2(0f, 0f), b.radius, b.cueBall) }
                            physics.simulate(o.tableBounds, o.pockets, states, settings.mode,
                                EightBallPoolProfile.physics(settings.calibration, o.tableBounds.width()),
                                o.confidence * if (settings.calibration.validated) 1f else .72f)
                        }
                    }
                }
                _prediction.value = result
                _status.value = when {
                    observation == null -> "No valid table or balls detected"
                    observation?.balls?.none { it.cueBall } != false -> "Cue ball not identified"
                    observation?.cueDirection == null -> "Cue direction not detected"
                    result == null || result?.segments?.isEmpty() != false -> "No trajectory from current frame"
                    else -> "${result?.segments?.size ?: 0} trajectory segments ready"
                }
                val totalMs = (System.nanoTime() - started) / 1_000_000f
                val rt = Runtime.getRuntime()
                _telemetry.value = PerformanceTelemetry(
                    detectionMs = detectionNs / 1_000_000f, physicsMs = physicsNs / 1_000_000f,
                    endToEndMs = totalMs, processedFps = if (totalMs > 0) 1000f / totalMs else 0f,
                    memoryMb = (rt.totalMemory() - rt.freeMemory()) / 1048576f
                )
            } catch (error: Throwable) {
                Log.e("PoolAssistant", "Frame analysis failed", error)
                _prediction.value = null
                _observation.value = null
                _status.value = "Analysis error: ${error.javaClass.simpleName}"
            } finally { frame.bitmap.recycle() }
        }
    }
}
