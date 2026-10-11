package com.example.poolassistant.calibration

import com.example.poolassistant.core.Vec2
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CalibrationEstimatorTest {
    @Test fun estimatesConstantDeceleration() {
        val samples = listOf(0f to 0f, 1f to 9f, 2f to 16f, 3f to 21f, 4f to 24f).map { TimedPosition(it.first, Vec2(it.second, 0f)) }
        val result = CalibrationEstimator.rollingDeceleration(samples)
        assertTrue(result.reliable); assertEquals(2f, result.value!!, .001f)
    }
    @Test fun rejectsImpossibleCushionGain() {
        assertTrue(!CalibrationEstimator.cushionRestitution(1f, 1.4f).reliable)
    }
}

