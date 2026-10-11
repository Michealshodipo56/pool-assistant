package com.example.poolassistant.vision

import com.example.poolassistant.core.Vec2
import org.junit.Assert.assertEquals
import org.junit.Test

class CoordinateMapperTest {
    @Test fun scalesCaptureToOverlay() {
        val result = CoordinateMapper(200f, 100f, 1000f, 500f).captureToOverlay(Vec2(50f, 20f))
        assertEquals(250f, result.x, .001f); assertEquals(100f, result.y, .001f)
    }
    @Test fun rotatesClockwise() {
        val result = CoordinateMapper(200f, 100f, 500f, 1000f, 90).captureToOverlay(Vec2(20f, 30f))
        assertEquals(350f, result.x, .001f); assertEquals(100f, result.y, .001f)
    }
}

