package com.example.poolassistant.overlay

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Point
import android.graphics.drawable.GradientDrawable
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.Display
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.*
import androidx.core.app.NotificationCompat
import com.example.poolassistant.MainActivity
import com.example.poolassistant.R
import com.example.poolassistant.capture.ScreenCaptureController
import com.example.poolassistant.core.TrajectoryMode
import com.example.poolassistant.pipeline.PredictionCoordinator
import com.example.poolassistant.settings.SettingsRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import kotlin.math.abs

@AndroidEntryPoint
class AssistantService : Service() {
    @Inject lateinit var capture: ScreenCaptureController
    @Inject lateinit var coordinator: PredictionCoordinator
    @Inject lateinit var settingsRepository: SettingsRepository
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var wm: WindowManager
    private lateinit var lineView: TrajectoryOverlayView
    private lateinit var bubble: TextView
    private var menu: View? = null
    private var cornerView: TableCornerSelectionView? = null
    private var menuStatusJob: Job? = null
    private lateinit var bubbleParams: WindowManager.LayoutParams

    override fun onCreate() {
        super.onCreate()
        createChannel(); startForegroundCompat(); wm = getSystemService(WindowManager::class.java)
        if (!Settings.canDrawOverlays(this)) { stopSelf(); return }
        addLineOverlay(); addBubble()
        scope.launch {
            combine(settingsRepository.settings, coordinator.observation, coordinator.prediction) { s, o, p -> Triple(s, o, p) }
                .collect { (s, o, p) -> lineView.settings = s; lineView.observation = o; lineView.result = p }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> { stopSelf(); return START_NOT_STICKY }
            ACTION_START -> {
                val code = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
                val data = projectionIntent(intent)
                if (code == Activity.RESULT_OK && data != null) {
                    capture.start(code, data) { }
                    scope.launch { settingsRepository.setHidden(false) }
                }
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        capture.stop(); scope.cancel()
        if (::lineView.isInitialized) runCatching { wm.removeView(lineView) }
        if (::bubble.isInitialized) runCatching { wm.removeView(bubble) }
        menu?.let { runCatching { wm.removeView(it) } }; menu = null
        cornerView?.let { runCatching { wm.removeView(it) } }; cornerView = null
        super.onDestroy()
    }
    override fun onBind(intent: Intent?): IBinder? = null

    private fun overlayParams(width: Int, height: Int, flags: Int) = WindowManager.LayoutParams(
        width, height, if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
        flags, PixelFormat.TRANSLUCENT
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        if (Build.VERSION.SDK_INT >= 28) {
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
    }

    private fun addLineOverlay() {
        lineView = TrajectoryOverlayView(this)
        wm.addView(lineView, overlayParams(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS).apply {
            // Android 12+ blocks touches through an opaque untrusted overlay window.
            alpha = .79f
        })
    }

    private fun addBubble() {
        val size = (52 * resources.displayMetrics.density).toInt()
        bubble = TextView(this).apply {
            text = "◎"; textSize = 28f; gravity = Gravity.CENTER; setTextColor(Color.WHITE)
            background = roundDrawable(Color.rgb(8, 125, 109), size / 2f)
            elevation = 8f * resources.displayMetrics.density
        }
        bubbleParams = overlayParams(size, size, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN)
        scope.launch { settingsRepository.settings.collect { s -> if (!bubble.isPressed) { bubbleParams.x = s.bubbleX; bubbleParams.y = s.bubbleY; runCatching { wm.updateViewLayout(bubble, bubbleParams) } } } }
        var startX = 0; var startY = 0; var touchX = 0f; var touchY = 0f; var moved = false
        bubble.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> { startX = bubbleParams.x; startY = bubbleParams.y; touchX = event.rawX; touchY = event.rawY; moved = false; true }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - touchX).toInt(); val dy = (event.rawY - touchY).toInt(); moved = moved || abs(dx) + abs(dy) > 8
                    val bounds = displaySize()
                    bubbleParams.x = (startX + dx).coerceIn(0, (bounds.x - size).coerceAtLeast(0)); bubbleParams.y = (startY + dy).coerceIn(0, (bounds.y - size).coerceAtLeast(0))
                    wm.updateViewLayout(bubble, bubbleParams); true
                }
                MotionEvent.ACTION_UP -> {
                    if (moved) {
                        val width = displaySize().x
                        bubbleParams.x = if (bubbleParams.x + size / 2 < width / 2) 0 else width - size
                        wm.updateViewLayout(bubble, bubbleParams); scope.launch { settingsRepository.saveBubble(bubbleParams.x, bubbleParams.y) }
                    } else toggleMenu()
                    true
                }
                else -> false
            }
        }
        wm.addView(bubble, bubbleParams)
    }

    private fun toggleMenu() { if (menu == null) showMenu() else hideMenu() }
    private fun hideMenu() { menuStatusJob?.cancel(); menuStatusJob = null; menu?.let { wm.removeView(it) }; menu = null }
    private fun showMenu() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = roundDrawable(Color.rgb(18, 29, 27), dp(14).toFloat())
        }
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(14), dp(4), dp(14), dp(12))
        }
        fun label(text: String, size: Float = 14f) = TextView(this).apply { this.text = text; textSize = size; setTextColor(Color.WHITE); setPadding(0, dp(5), 0, dp(5)) }
        root.addView(Button(this).apply { text = "Close Menu"; setOnClickListener { hideMenu() } })
        panel.addView(label("POOL ASSISTANT", 12f))
        val statusView = label("Status: Waiting for screen capture", 12f)
        panel.addView(statusView)
        menuStatusJob = scope.launch { coordinator.status.collect { statusView.text = "Status: $it" } }
        val enabled = Switch(this).apply { text = "Enable aiming lines"; setTextColor(Color.WHITE) }
        val modes = RadioGroup(this).apply { orientation = RadioGroup.HORIZONTAL }
        val basic = RadioButton(this).apply { text = "Basic"; setTextColor(Color.WHITE); id = View.generateViewId() }
        val all = RadioButton(this).apply { text = "All Lines"; setTextColor(Color.WHITE); id = View.generateViewId() }
        modes.addView(basic); modes.addView(all); panel.addView(enabled); panel.addView(modes)
        panel.addView(label("Line opacity")); val opacity = SeekBar(this).apply { max = 100; min = 15 }; panel.addView(opacity)
        panel.addView(label("Line thickness")); val thickness = SeekBar(this).apply { max = 80; min = 10 }; panel.addView(thickness)
        fun action(text: String, run: () -> Unit) { panel.addView(Button(this).apply { this.text = text; setOnClickListener { run() } }) }
        action("Mark Table Corners (in game)") { startCornerSelection() }
        action("Calibrate in App (leaves game)") { openApp(true) }
        action("Hide / Show Lines") { scope.launch { val s = settingsRepository.settings.first(); settingsRepository.setHidden(!s.hidden) } }
        action("Open App (leaves game)") { openApp(false) }
        action("Stop Assistant") { stopSelf() }
        scope.launch {
            val s = settingsRepository.settings.first()
            enabled.isChecked = s.linesEnabled; if (s.mode == TrajectoryMode.BASIC) basic.isChecked = true else all.isChecked = true
            opacity.progress = (s.opacity * 100).toInt(); thickness.progress = (s.thickness * 10).toInt()
        }
        enabled.setOnCheckedChangeListener { _, value -> scope.launch { settingsRepository.setEnabled(value) } }
        modes.setOnCheckedChangeListener { _, id -> scope.launch { settingsRepository.setMode(if (id == all.id) TrajectoryMode.ALL else TrajectoryMode.BASIC) } }
        opacity.setOnSeekBarChangeListener(simpleSeek { scope.launch { settingsRepository.setOpacity(it / 100f) } })
        thickness.setOnSeekBarChangeListener(simpleSeek { scope.launch { settingsRepository.setThickness(it / 10f) } })
        val displaySize = displaySize()
        val screenHeight = displaySize.y
        val menuHeight = minOf(dp(460), (screenHeight - dp(24)).coerceAtLeast(dp(120)))
        val scroll = ScrollView(this).apply { isVerticalScrollBarEnabled = true; isFillViewport = false; addView(panel) }
        root.addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        val params = overlayParams(dp(275), menuHeight, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN)
        val screenWidth = displaySize.x
        val panelWidth = dp(275)
        val bubbleWidth = bubbleParams.width
        val gap = dp(8)
        params.x = if (bubbleParams.x + bubbleWidth + gap + panelWidth <= screenWidth) {
            bubbleParams.x + bubbleWidth + gap
        } else {
            (bubbleParams.x - panelWidth - gap).coerceAtLeast(0)
        }
        params.y = bubbleParams.y.coerceIn(dp(8), (screenHeight - menuHeight - dp(8)).coerceAtLeast(dp(8)))
        menu = root; wm.addView(root, params)
    }

    private fun simpleSeek(update: (Int) -> Unit) = object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) { if (fromUser) update(progress) }
        override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
        override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
    }
    private fun startCornerSelection() {
        hideMenu()
        if (cornerView != null) return
        capture.setPaused(true)
        coordinator.invalidate()
        lateinit var selector: TableCornerSelectionView
        selector = TableCornerSelectionView(this, { bounds ->
            val overlayWidth = selector.width.coerceAtLeast(1)
            val overlayHeight = selector.height.coerceAtLeast(1)
            endCornerSelection(resume = false)
            scope.launch {
                try {
                    val current = settingsRepository.settings.first().calibration
                    settingsRepository.saveCalibration(current.copy(
                        tableLeft = (bounds.left / overlayWidth).coerceIn(0f, 1f),
                        tableTop = (bounds.top / overlayHeight).coerceIn(0f, 1f),
                        tableRight = (bounds.right / overlayWidth).coerceIn(0f, 1f),
                        tableBottom = (bounds.bottom / overlayHeight).coerceIn(0f, 1f),
                        tableMarked = true
                    ))
                    Toast.makeText(this@AssistantService, "Table area saved", Toast.LENGTH_SHORT).show()
                } finally { capture.setPaused(false) }
            }
        }, { endCornerSelection(resume = true) })
        cornerView = selector
        try {
            wm.addView(selector, overlayParams(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS))
        } catch (error: RuntimeException) {
            cornerView = null
            capture.setPaused(false)
            Toast.makeText(this, "Could not open table marker", Toast.LENGTH_SHORT).show()
        }
    }

    private fun endCornerSelection(resume: Boolean) {
        cornerView?.let { runCatching { wm.removeView(it) } }
        cornerView = null
        if (resume) capture.setPaused(false)
    }

    private fun openApp(calibrate: Boolean) { startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("calibrate", calibrate)); hideMenu() }
    private fun roundDrawable(color: Int, radius: Float) = GradientDrawable().apply { setColor(color); cornerRadius = radius }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun displaySize(): Point = Point().also { point ->
        @Suppress("DEPRECATION")
        getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY)?.getRealSize(point)
        if (point.x <= 0 || point.y <= 0) {
            point.x = resources.displayMetrics.widthPixels
            point.y = resources.displayMetrics.heightPixels
        }
    }

    private fun createChannel() { if (Build.VERSION.SDK_INT >= 26) getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel(CHANNEL, "Pool assistant", NotificationManager.IMPORTANCE_LOW)) }
    @Suppress("DEPRECATION")
    private fun projectionIntent(intent: Intent): Intent? =
        if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java) else intent.getParcelableExtra(EXTRA_RESULT_DATA)

    private fun startForegroundCompat() {
        val stop = PendingIntent.getService(this, 1, Intent(this, AssistantService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val open = PendingIntent.getActivity(this, 2, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notification = NotificationCompat.Builder(this, CHANNEL).setSmallIcon(android.R.drawable.ic_menu_compass).setContentTitle("Pool Assistant active")
            .setContentText("Screen analysis stays on this device").setContentIntent(open).setOngoing(true).addAction(0, "Stop", stop).build()
        if (Build.VERSION.SDK_INT >= 29) startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION) else startForeground(NOTIFICATION_ID, notification)
    }

    companion object {
        const val ACTION_START = "com.example.poolassistant.START"
        const val ACTION_STOP = "com.example.poolassistant.STOP"
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_RESULT_DATA = "result_data"
        private const val CHANNEL = "pool_assistant_service"
        private const val NOTIFICATION_ID = 2107
    }
}
