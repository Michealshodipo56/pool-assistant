package com.example.poolassistant.capture

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.graphics.Point
import android.hardware.display.DisplayManager.DisplayListener
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.Display
import android.view.WindowManager
import com.example.poolassistant.pipeline.PredictionCoordinator
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ScreenCaptureController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val coordinator: PredictionCoordinator
) {
    private var projection: MediaProjection? = null
    private var reader: ImageReader? = null
    private var display: VirtualDisplay? = null
    private var thread: HandlerThread? = null
    private var handler: Handler? = null
    private var displayListener: DisplayListener? = null
    private var captureWidth = 0
    private var captureHeight = 0
    private var lastFrameNanos = 0L
    @Volatile private var paused = false

    fun setPaused(value: Boolean) {
        paused = value
        if (!value) lastFrameNanos = 0L
    }

    fun start(resultCode: Int, data: Intent, onRevoked: () -> Unit) {
        stop()
        val metrics = context.resources.displayMetrics
        val (width, height) = screenSize()
        val manager = context.getSystemService(MediaProjectionManager::class.java)
        val mediaProjection = manager.getMediaProjection(resultCode, data)
        val handlerThread = HandlerThread("pool-capture").also { it.start() }
        val captureHandler = Handler(handlerThread.looper)
        handler = captureHandler
        captureWidth = width; captureHeight = height
        val imageReader = createReader(width, height, captureHandler)
        mediaProjection.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() { stopInternal(false); coordinator.invalidate(); onRevoked() }
            override fun onCapturedContentResize(width: Int, height: Int) {
                if (android.os.Build.VERSION.SDK_INT >= 34) resizeCapture(width, height)
            }
        }, captureHandler)
        val virtualDisplay = mediaProjection.createVirtualDisplay(
            "PoolAssistantCapture", width, height, metrics.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, imageReader.surface, null, captureHandler
        )
        projection = mediaProjection; reader = imageReader; display = virtualDisplay; thread = handlerThread
        displayListener = object : DisplayListener {
            override fun onDisplayAdded(displayId: Int) = Unit
            override fun onDisplayRemoved(displayId: Int) = Unit
            override fun onDisplayChanged(displayId: Int) {
                if (displayId == Display.DEFAULT_DISPLAY) {
                    val (newWidth, newHeight) = screenSize()
                    resizeCapture(newWidth, newHeight)
                }
            }
        }.also { context.getSystemService(DisplayManager::class.java).registerDisplayListener(it, captureHandler) }
    }

    private fun createReader(width: Int, height: Int, captureHandler: Handler): ImageReader =
        ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2).also { imageReader ->
            imageReader.setOnImageAvailableListener({ source ->
            val now = System.nanoTime()
            val image = source.acquireLatestImage() ?: return@setOnImageAvailableListener
            try {
                // Pool aiming changes relatively slowly; cap expensive vision work
                // at five frames per second instead of analyzing every display frame.
                if (paused || now - lastFrameNanos < 200_000_000L) return@setOnImageAvailableListener
                lastFrameNanos = now
                val plane = image.planes[0]
                val rowPadding = plane.rowStride - plane.pixelStride * width
                val padded = Bitmap.createBitmap(width + rowPadding / plane.pixelStride, height, Bitmap.Config.ARGB_8888)
                padded.copyPixelsFromBuffer(plane.buffer)
                val frame = Bitmap.createBitmap(padded, 0, 0, width, height)
                padded.recycle()
                coordinator.submit(frame, now)
            } catch (error: Throwable) {
                Log.e("PoolAssistant", "Screen frame acquisition failed", error)
            } finally { image.close() }
            }, captureHandler)
        }

    private fun resizeCapture(width: Int, height: Int) {
        if (width <= 0 || height <= 0 || (width == captureWidth && height == captureHeight)) return
        val activeDisplay = display ?: return
        val captureHandler = handler ?: return
        val replacement = createReader(width, height, captureHandler)
        try {
            activeDisplay.resize(width, height, context.resources.configuration.densityDpi)
            activeDisplay.setSurface(replacement.surface)
            val previous = reader
            reader = replacement
            captureWidth = width; captureHeight = height
            previous?.setOnImageAvailableListener(null, null)
            previous?.close()
            coordinator.invalidate()
        } catch (error: Throwable) {
            replacement.close()
            Log.e("PoolAssistant", "Capture resize failed", error)
        }
    }

    private fun screenSize(): Pair<Int, Int> {
        val point = Point()
        @Suppress("DEPRECATION")
        context.getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY)?.getRealSize(point)
        if (point.x > 0 && point.y > 0) return point.x to point.y
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            val bounds = context.getSystemService(WindowManager::class.java).currentWindowMetrics.bounds
            return bounds.width() to bounds.height()
        }
        val metrics = context.resources.displayMetrics
        return metrics.widthPixels to metrics.heightPixels
    }

    fun stop() = stopInternal(true)
    private fun stopInternal(stopProjection: Boolean) {
        displayListener?.let { context.getSystemService(DisplayManager::class.java).unregisterDisplayListener(it) }
        displayListener = null
        reader?.setOnImageAvailableListener(null, null)
        display?.release(); display = null
        reader?.close(); reader = null
        val current = projection; projection = null
        if (stopProjection) runCatching { current?.stop() }
        thread?.quitSafely(); thread = null; handler = null
        captureWidth = 0; captureHeight = 0
        paused = false
        coordinator.invalidate()
    }
}

