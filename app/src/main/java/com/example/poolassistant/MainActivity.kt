package com.example.poolassistant

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.poolassistant.core.CalibrationProfile
import com.example.poolassistant.core.TrajectoryMode
import com.example.poolassistant.overlay.AssistantService
import dagger.hilt.android.AndroidEntryPoint
import java.util.Locale

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { PoolAssistantTheme { MainScreen() } }
    }

    @Composable
    private fun MainScreen(vm: MainViewModel = hiltViewModel()) {
        val appSettings by vm.settings.collectAsState()
        val telemetry by vm.telemetry.collectAsState()
        val observation by vm.observation.collectAsState()
        val pipelineStatus by vm.pipelineStatus.collectAsState()
        var running by remember { mutableStateOf(false) }
        var message by remember { mutableStateOf<String?>(null) }
        val projectionManager = remember { getSystemService(MediaProjectionManager::class.java) }
        val captureLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK && result.data != null) {
                val service = Intent(this, AssistantService::class.java).setAction(AssistantService.ACTION_START)
                    .putExtra(AssistantService.EXTRA_RESULT_CODE, result.resultCode).putExtra(AssistantService.EXTRA_RESULT_DATA, result.data)
                ContextCompat.startForegroundService(this, service); running = true; message = null
            } else message = "Screen capture was not granted. No frames are captured."
        }
        val overlayLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (Settings.canDrawOverlays(this)) captureLauncher.launch(projectionManager.createScreenCaptureIntent())
            else message = "Display-over-other-apps permission is required for the bubble and lines."
        }
        val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

        fun begin() {
            if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            if (!Settings.canDrawOverlays(this)) {
                overlayLauncher.launch(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            } else captureLauncher.launch(projectionManager.createScreenCaptureIntent())
        }
        fun stop() { startService(Intent(this, AssistantService::class.java).setAction(AssistantService.ACTION_STOP)); running = false }

        Scaffold(containerColor = Color(0xFF07110F), contentColor = Color(0xFFEAF7F4)) { padding ->
            Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Pool Assistant", style = MaterialTheme.typography.headlineMedium)
                Text("Local, permission-based trajectory analysis for supported pool games.", color = Color(0xFFA9C8C1))
                StatusPanel(running, Settings.canDrawOverlays(this@MainActivity), observation != null)
                message?.let { Text(it, color = Color(0xFFFFB74D)) }
                Button(onClick = { if (running) stop() else begin() }, modifier = Modifier.fillMaxWidth()) { Text(if (running) "Stop Assistant" else "Start Assistant") }
                Text("Android asks for screen-capture consent for every session. Frames are processed in memory on-device and are not saved or uploaded.", style = MaterialTheme.typography.bodySmall, color = Color(0xFFA9C8C1))
                HorizontalDivider(color = Color(0xFF27443D))
                Text("Trajectory mode", style = MaterialTheme.typography.titleMedium)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(appSettings.mode == TrajectoryMode.BASIC, { vm.setMode(TrajectoryMode.BASIC) }); Text("Basic Lines")
                    Spacer(Modifier.width(18.dp)); RadioButton(appSettings.mode == TrajectoryMode.ALL, { vm.setMode(TrajectoryMode.ALL) }); Text("All Lines")
                }
                SettingSlider("Line opacity", appSettings.opacity, .15f..1f, vm::setOpacity)
                SettingSlider("Line thickness", appSettings.thickness, 1f..8f, vm::setThickness)
                CalibrationPanel(appSettings.calibration, vm::saveCalibration, vm::resetCalibration)
                HorizontalDivider(color = Color(0xFF27443D))
                Text("Diagnostics", style = MaterialTheme.typography.titleMedium)
                Text("Pipeline: $pipelineStatus", color = Color(0xFFA9C8C1))
                Text("Detection ${format(telemetry.detectionMs)} ms  •  Physics ${format(telemetry.physicsMs)} ms  •  End-to-end ${format(telemetry.endToEndMs)} ms")
                Text("Processing ${format(telemetry.processedFps)} fps  •  Heap ${format(telemetry.memoryMb)} MB", color = Color(0xFFA9C8C1))
                Text(if (observation?.cueDirection == null) "Cue direction: unavailable — trajectories are suppressed" else "Cue direction: detected", color = if (observation?.cueDirection == null) Color(0xFFFFB74D) else Color(0xFF54E0B8))
            }
        }
    }

    @Composable private fun StatusPanel(running: Boolean, overlay: Boolean, detecting: Boolean) {
        Surface(color = Color(0xFF11231F), shape = MaterialTheme.shapes.medium) {
            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(if (running) "Assistant running" else "Assistant stopped", color = if (running) Color(0xFF54E0B8) else Color.White)
                Text("Overlay permission: ${if (overlay) "Granted" else "Required"}")
                Text("Frame state: ${if (detecting) "Table observed" else "Waiting"}")
                Text("Game profile: 8 Ball Pool", color = Color(0xFFA9C8C1))
            }
        }
    }

    @Composable private fun SettingSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, update: (Float) -> Unit) {
        Column { Text("$label: ${format(value)}"); Slider(value, update, valueRange = range) }
    }

    @Composable private fun CalibrationPanel(current: CalibrationProfile, save: (CalibrationProfile) -> Unit, reset: () -> Unit) {
        var value by remember(current) { mutableStateOf(current) }
        Text("Calibration", style = MaterialTheme.typography.titleMedium)
        Text("Values are assumptions until verified against recorded shots.", color = Color(0xFFFFB74D), style = MaterialTheme.typography.bodySmall)
        SettingSlider("Table left", value.tableLeft, 0f..0.3f) { value = value.copy(tableLeft = it) }
        SettingSlider("Table right", value.tableRight, .7f..1f) { value = value.copy(tableRight = it) }
        SettingSlider("Table top", value.tableTop, 0f..0.35f) { value = value.copy(tableTop = it) }
        SettingSlider("Table bottom", value.tableBottom, .65f..1f) { value = value.copy(tableBottom = it) }
        SettingSlider("Ball radius / short side", value.ballRadiusFraction, 0.008f..0.035f) { value = value.copy(ballRadiusFraction = it) }
        SettingSlider("Cushion restitution", value.cushionRestitution, 0.5f..0.98f) { value = value.copy(cushionRestitution = it) }
        SettingSlider("Rolling deceleration", value.rollingDeceleration, 0.05f..0.7f) { value = value.copy(rollingDeceleration = it) }
        SettingSlider("Assumed shot speed", value.assumedShotSpeed, .3f..3.5f) { value = value.copy(assumedShotSpeed = it) }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = { save(value) }) { Text("Save calibration") }
            OutlinedButton(onClick = reset) { Text("Reset") }
        }
    }
    private fun format(value: Float) = String.format(Locale.US, "%.1f", value)
}

@Composable
private fun PoolAssistantTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = darkColorScheme(primary = Color(0xFF2DD4B0), secondary = Color(0xFF63A9FF)), content = content)
}

