package com.example.poolassistant

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.poolassistant.core.BallState
import com.example.poolassistant.core.CalibrationProfile
import com.example.poolassistant.core.TrajectoryMode
import com.example.poolassistant.core.Vec2
import com.example.poolassistant.game.EightBallPoolProfile
import com.example.poolassistant.physics.NativePhysicsBridge
import com.example.poolassistant.vision.OpenCvVisionEngine
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class VisionFixtureTest {
    private fun fixture(name: String): Bitmap {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = context.getExternalFilesDir(null)?.let { File(it, name) }
        assertTrue("Fixture missing at ${file?.absolutePath}", file?.isFile == true)
        return requireNotNull(BitmapFactory.decodeFile(file!!.absolutePath))
    }

    @Test
    fun unmarkedGameplayFrameDoesNotUseUncertainTableBounds() {
        val bitmap = fixture("vision-fixture.png")
        val calibration = CalibrationProfile()
        val observation = OpenCvVisionEngine().analyze(bitmap, calibration, System.nanoTime())
        bitmap.recycle()
        if (observation != null) {
            val bounds = observation.tableBounds
            assertTrue("Automatic table includes rails/UI: $bounds", bounds.left in 290f..350f && bounds.right in 1250f..1320f && bounds.top in 150f..200f && bounds.bottom in 620f..680f)
        }
    }

    @Test
    fun blueTableFrameFindsCueBallAfterMarking() {
        val bitmap = fixture("blue-table-fixture.png")
        val calibration = CalibrationProfile(
            tableLeft = 320f / bitmap.width, tableTop = 170f / bitmap.height,
            tableRight = 1295f / bitmap.width, tableBottom = 655f / bitmap.height,
            tableMarked = true
        )
        val observation = OpenCvVisionEngine().analyze(bitmap, calibration, System.nanoTime())
        bitmap.recycle()
        assertNotNull("Blue table or balls not detected", observation)
        val table = observation!!
        val cue = table.balls.firstOrNull { it.cueBall }
        assertNotNull("Blue-table cue missing; balls=${table.balls}", cue)
        assertTrue("Wrong blue-table cue: ${cue!!.center}", cue.center.x in 1190f..1270f && cue.center.y in 365f..435f)
        val aim = table.cueDirection
        assertNotNull("Blue-table aim missing", aim)
        assertTrue("Wrong blue-table aim: $aim", aim!!.x < -.3f && aim.y > .4f)
    }

    @Test
    fun markedTableRestrictsDetectionToCloth() {
        val bitmap = fixture("vision-fixture.png")
        val calibration = CalibrationProfile(
            tableLeft = 320f / bitmap.width,
            tableTop = 170f / bitmap.height,
            tableRight = 1295f / bitmap.width,
            tableBottom = 655f / bitmap.height,
            tableMarked = true
        )
        val observation = OpenCvVisionEngine().analyze(bitmap, calibration, System.nanoTime())
        bitmap.recycle()
        assertNotNull("Marked table produced no ball observation", observation)
        val table = observation!!
        assertTrue("Unexpected manual table bounds: ${table.tableBounds}", table.tableBounds.left in 315f..325f && table.tableBounds.right in 1290f..1300f)
        assertTrue("False balls outside marked table: ${table.balls.size}", table.balls.size in 3..16)
        val cue = table.balls.firstOrNull { it.cueBall }
        assertNotNull("Cue ball not found inside marked table: ${table.balls}", cue)
        val cueBall = requireNotNull(cue)
        assertTrue("Wrong cue after marking table: ${cueBall.center}", cueBall.center.x in 1160f..1230f && cueBall.center.y in 175f..245f)
        val aim = table.cueDirection
        assertNotNull("No aim direction after marking table", aim)
        val direction = requireNotNull(aim)
        assertTrue("Wrong marked-table aim: $direction", direction.y > .6f)
        val speed = calibration.assumedShotSpeed * table.tableBounds.width()
        val states = table.balls.map { ball ->
            BallState(ball.id, ball.center, if (ball.id == cueBall.id) direction * speed else Vec2(0f, 0f), ball.radius, ball.cueBall)
        }
        val prediction = NativePhysicsBridge().simulate(table.tableBounds, table.pockets, states,
            TrajectoryMode.BASIC, EightBallPoolProfile.physics(calibration, table.tableBounds.width()), table.confidence)
        assertTrue("No trajectory after marking table", prediction.segments.isNotEmpty())
    }

    @Test
    fun loadingScreenDoesNotProduceTrajectories() {
        val bitmap = fixture("loading-fixture.png")
        val observation = OpenCvVisionEngine().analyze(bitmap, CalibrationProfile(), System.nanoTime())
        bitmap.recycle()
        assertNull("Loading screen was mistaken for a table: $observation", observation)
    }
}
