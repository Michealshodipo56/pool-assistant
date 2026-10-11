package com.example.poolassistant.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.poolassistant.core.AssistantSettings
import com.example.poolassistant.core.CalibrationProfile
import com.example.poolassistant.core.TrajectoryMode
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore("assistant_settings")

@Singleton
class SettingsRepository @Inject constructor(@ApplicationContext private val context: Context) {
    private object Keys {
        val enabled = booleanPreferencesKey("enabled")
        val hidden = booleanPreferencesKey("hidden")
        val mode = stringPreferencesKey("mode")
        val opacity = floatPreferencesKey("opacity")
        val thickness = floatPreferencesKey("thickness")
        val bubbleX = intPreferencesKey("bubble_x")
        val bubbleY = intPreferencesKey("bubble_y")
        val left = floatPreferencesKey("table_left")
        val top = floatPreferencesKey("table_top")
        val right = floatPreferencesKey("table_right")
        val bottom = floatPreferencesKey("table_bottom")
        val radius = floatPreferencesKey("ball_radius")
        val cushion = floatPreferencesKey("cushion_restitution")
        val friction = floatPreferencesKey("rolling_deceleration")
        val speed = floatPreferencesKey("assumed_shot_speed")
        val tableMarked = booleanPreferencesKey("table_marked")
        val validated = booleanPreferencesKey("calibration_validated")
    }

    val settings: Flow<AssistantSettings> = context.dataStore.data.map { p ->
        val defaults = AssistantSettings()
        val c = defaults.calibration
        AssistantSettings(
            linesEnabled = p[Keys.enabled] ?: defaults.linesEnabled,
            hidden = p[Keys.hidden] ?: defaults.hidden,
            mode = runCatching { TrajectoryMode.valueOf(p[Keys.mode] ?: defaults.mode.name) }.getOrDefault(defaults.mode),
            opacity = p[Keys.opacity] ?: defaults.opacity,
            thickness = p[Keys.thickness] ?: defaults.thickness,
            bubbleX = p[Keys.bubbleX] ?: defaults.bubbleX,
            bubbleY = p[Keys.bubbleY] ?: defaults.bubbleY,
            calibration = CalibrationProfile(
                tableLeft = p[Keys.left] ?: c.tableLeft,
                tableTop = p[Keys.top] ?: c.tableTop,
                tableRight = p[Keys.right] ?: c.tableRight,
                tableBottom = p[Keys.bottom] ?: c.tableBottom,
                ballRadiusFraction = p[Keys.radius] ?: c.ballRadiusFraction,
                cushionRestitution = p[Keys.cushion] ?: c.cushionRestitution,
                rollingDeceleration = p[Keys.friction] ?: c.rollingDeceleration,
                assumedShotSpeed = p[Keys.speed] ?: c.assumedShotSpeed,
                tableMarked = p[Keys.tableMarked] ?: c.tableMarked,
                validated = p[Keys.validated] ?: c.validated
            )
        )
    }

    suspend fun setEnabled(value: Boolean) = context.dataStore.edit { it[Keys.enabled] = value }
    suspend fun setHidden(value: Boolean) = context.dataStore.edit { it[Keys.hidden] = value }
    suspend fun setMode(value: TrajectoryMode) = context.dataStore.edit { it[Keys.mode] = value.name }
    suspend fun setOpacity(value: Float) = context.dataStore.edit { it[Keys.opacity] = value.coerceIn(.15f, 1f) }
    suspend fun setThickness(value: Float) = context.dataStore.edit { it[Keys.thickness] = value.coerceIn(1f, 8f) }
    suspend fun saveBubble(x: Int, y: Int) = context.dataStore.edit { it[Keys.bubbleX] = x; it[Keys.bubbleY] = y }
    suspend fun saveCalibration(c: CalibrationProfile) = context.dataStore.edit {
        it[Keys.left] = c.tableLeft; it[Keys.top] = c.tableTop; it[Keys.right] = c.tableRight; it[Keys.bottom] = c.tableBottom
        it[Keys.radius] = c.ballRadiusFraction; it[Keys.cushion] = c.cushionRestitution
        it[Keys.friction] = c.rollingDeceleration; it[Keys.speed] = c.assumedShotSpeed
        it[Keys.tableMarked] = c.tableMarked; it[Keys.validated] = c.validated
    }
    suspend fun resetCalibration() = saveCalibration(CalibrationProfile())
}

