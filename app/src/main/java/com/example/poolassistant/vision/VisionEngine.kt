package com.example.poolassistant.vision

import android.graphics.Bitmap
import com.example.poolassistant.core.CalibrationProfile
import com.example.poolassistant.core.TableObservation

interface VisionEngine {
    fun analyze(bitmap: Bitmap, calibration: CalibrationProfile, timestampNanos: Long): TableObservation?
    fun resetTracking()
}

