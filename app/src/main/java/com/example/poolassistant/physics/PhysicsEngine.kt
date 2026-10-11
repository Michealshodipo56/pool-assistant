package com.example.poolassistant.physics

import android.graphics.RectF
import com.example.poolassistant.core.BallState
import com.example.poolassistant.core.PhysicsConfig
import com.example.poolassistant.core.SimulationResult
import com.example.poolassistant.core.TrajectoryMode
import com.example.poolassistant.core.Vec2

interface PhysicsEngine {
    fun simulate(
        table: RectF,
        pockets: List<Vec2>,
        balls: List<BallState>,
        mode: TrajectoryMode,
        config: PhysicsConfig,
        observationConfidence: Float
    ): SimulationResult
}

