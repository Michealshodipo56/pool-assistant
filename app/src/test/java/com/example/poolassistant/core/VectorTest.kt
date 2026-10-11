package com.example.poolassistant.core

import org.junit.Assert.assertEquals
import org.junit.Test

class VectorTest {
    @Test fun normalizesVector() {
        val v = Vec2(3f, 4f).normalized()
        assertEquals(.6f, v.x, .0001f); assertEquals(.8f, v.y, .0001f)
    }
    @Test fun zeroVectorRemainsFinite() {
        assertEquals(Vec2(0f, 0f), Vec2(0f, 0f).normalized())
    }
}
