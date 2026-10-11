package com.example.poolassistant.di

import com.example.poolassistant.physics.NativePhysicsBridge
import com.example.poolassistant.physics.PhysicsEngine
import com.example.poolassistant.vision.OpenCvVisionEngine
import com.example.poolassistant.vision.VisionEngine
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AppModule {
    @Binds @Singleton abstract fun physics(impl: NativePhysicsBridge): PhysicsEngine
    @Binds @Singleton abstract fun vision(impl: OpenCvVisionEngine): VisionEngine
}

