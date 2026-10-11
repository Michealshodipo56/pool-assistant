package com.example.poolassistant

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.poolassistant.core.CalibrationProfile
import com.example.poolassistant.core.TrajectoryMode
import com.example.poolassistant.pipeline.PredictionCoordinator
import com.example.poolassistant.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    coordinator: PredictionCoordinator
) : ViewModel() {
    val settings = settingsRepository.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), com.example.poolassistant.core.AssistantSettings())
    val telemetry = coordinator.telemetry
    val observation = coordinator.observation
    val pipelineStatus = coordinator.status

    fun setMode(mode: TrajectoryMode) = viewModelScope.launch { settingsRepository.setMode(mode) }
    fun setOpacity(value: Float) = viewModelScope.launch { settingsRepository.setOpacity(value) }
    fun setThickness(value: Float) = viewModelScope.launch { settingsRepository.setThickness(value) }
    fun saveCalibration(value: CalibrationProfile) = viewModelScope.launch { settingsRepository.saveCalibration(value) }
    fun resetCalibration() = viewModelScope.launch { settingsRepository.resetCalibration() }
}

