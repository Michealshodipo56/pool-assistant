package com.example.poolassistant.game

import com.example.poolassistant.core.CalibrationProfile
import com.example.poolassistant.core.PhysicsConfig

interface GameProfile {
    val id: String
    val displayName: String
    fun physics(calibration: CalibrationProfile, tableWidthPixels: Float): PhysicsConfig
}

object EightBallPoolProfile : GameProfile {
    override val id = "miniclip-eight-ball-pool"
    override val displayName = "8 Ball Pool"
    override fun physics(calibration: CalibrationProfile, tableWidthPixels: Float) = PhysicsConfig(
        rollingDeceleration = calibration.rollingDeceleration * tableWidthPixels,
        cushionRestitution = calibration.cushionRestitution
    )
}

