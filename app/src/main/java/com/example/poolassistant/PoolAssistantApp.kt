package com.example.poolassistant

import android.app.Application
import android.util.Log
import dagger.hilt.android.HiltAndroidApp
import org.opencv.android.OpenCVLoader

@HiltAndroidApp
class PoolAssistantApp : Application() {
    override fun onCreate() {
        super.onCreate()
        if (OpenCVLoader.initLocal()) Log.i("PoolAssistant", "OpenCV initialized")
        else Log.e("PoolAssistant", "OpenCV initialization failed")
    }
}

