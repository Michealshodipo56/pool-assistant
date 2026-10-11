package com.example.poolassistant.vision

import android.graphics.Bitmap
import android.graphics.RectF
import com.example.poolassistant.core.CalibrationProfile
import com.example.poolassistant.core.DetectedBall
import com.example.poolassistant.core.TableObservation
import com.example.poolassistant.core.Vec2
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Scalar
import org.opencv.imgproc.Imgproc
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

@Singleton
class OpenCvVisionEngine @Inject constructor() : VisionEngine {
    private data class BallCandidate(val ball: DetectedBall, val whiteness: Float)
    private val tracked = mutableMapOf<Int, Vec2>()
    private var nextId = 1

    override fun resetTracking() { tracked.clear(); nextId = 1 }

    override fun analyze(bitmap: Bitmap, calibration: CalibrationProfile, timestampNanos: Long): TableObservation? {
        val rgba = Mat()
        val hsv = Mat()
        return try {
            Utils.bitmapToMat(bitmap, rgba)
            Imgproc.cvtColor(rgba, hsv, Imgproc.COLOR_RGBA2RGB)
            Imgproc.cvtColor(hsv, hsv, Imgproc.COLOR_RGB2HSV)
            val table = if (calibration.tableMarked) {
                RectF(
                    calibration.tableLeft * bitmap.width, calibration.tableTop * bitmap.height,
                    calibration.tableRight * bitmap.width, calibration.tableBottom * bitmap.height
                ).takeIf { it.width() / it.height().coerceAtLeast(1f) in 1.2f..3.5f }
            } else detectTable(hsv)
            table ?: return null
            val candidates = detectBalls(rgba, hsv, table, calibration)
            if (candidates.isEmpty() || candidates.size > 16) return null
            val cueEstimate = detectCueDirection(rgba, candidates, table)
            val balls = candidates.mapIndexed { index, candidate -> candidate.ball.copy(cueBall = cueEstimate?.first == index) }
            val trackedBalls = assignStableIds(balls)
            val cueDirection = cueEstimate?.second
            val pockets = listOf(
                Vec2(table.left, table.top), Vec2(table.centerX(), table.top), Vec2(table.right, table.top),
                Vec2(table.left, table.bottom), Vec2(table.centerX(), table.bottom), Vec2(table.right, table.bottom)
            )
            val tableConfidence = if (calibration.validated) .92f else .68f
            val confidence = (trackedBalls.map { it.confidence }.average().toFloat() * .7f + tableConfidence * .3f)
            TableObservation(Vec2(bitmap.width.toFloat(), bitmap.height.toFloat()), table, pockets, trackedBalls, cueDirection, confidence, timestampNanos)
        } finally { rgba.release(); hsv.release() }
    }

    private fun detectTable(hsv: Mat): RectF? {
        val mask = Mat()
        val contours = ArrayList<MatOfPoint>()
        return try {
            val hue = dominantClothHue(hsv)
            if (hue != null) {
                val low = hue - 14
                val high = hue + 14
                Core.inRange(hsv, Scalar(low.coerceAtLeast(0).toDouble(), 40.0, 30.0),
                    Scalar(high.coerceAtMost(179).toDouble(), 255.0, 255.0), mask)
                if (low < 0 || high > 179) {
                    val wrapped = Mat()
                    try {
                        if (low < 0) Core.inRange(hsv, Scalar((180 + low).toDouble(), 40.0, 30.0), Scalar(179.0, 255.0, 255.0), wrapped)
                        else Core.inRange(hsv, Scalar(0.0, 40.0, 30.0), Scalar((high - 180).toDouble(), 255.0, 255.0), wrapped)
                        Core.bitwise_or(mask, wrapped, mask)
                    } finally { wrapped.release() }
                }
            } else {
                mask.create(hsv.rows(), hsv.cols(), CvType.CV_8UC1)
                mask.setTo(Scalar(0.0))
            }
            Imgproc.morphologyEx(mask, mask, Imgproc.MORPH_CLOSE, Imgproc.getStructuringElement(Imgproc.MORPH_RECT, org.opencv.core.Size(15.0, 15.0)))
            val hierarchy = Mat()
            try { Imgproc.findContours(mask, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE) }
            finally { hierarchy.release() }
            val rect = contours.map { it to Imgproc.boundingRect(it) }.filter { (contour, candidate) ->
                val areaFraction = candidate.width.toDouble() * candidate.height / (hsv.cols() * hsv.rows()).toDouble()
                val filledFraction = Imgproc.contourArea(contour) / (candidate.width * candidate.height).coerceAtLeast(1)
                val aspect = candidate.width.toDouble() / candidate.height.coerceAtLeast(1)
                val centerX = candidate.x + candidate.width / 2.0
                val centerY = candidate.y + candidate.height / 2.0
                areaFraction >= .18 && areaFraction <= .75 && filledFraction >= .55 && aspect >= 1.5 && aspect <= 3.5 &&
                    candidate.x >= hsv.cols() * .08 && candidate.y >= hsv.rows() * .10 &&
                    candidate.x + candidate.width <= hsv.cols() * .92 &&
                    candidate.y + candidate.height <= hsv.rows() * .93 &&
                    centerX >= hsv.cols() * .2 && centerX <= hsv.cols() * .8 &&
                    centerY >= hsv.rows() * .2 && centerY <= hsv.rows() * .8
            }.maxByOrNull { it.second.width * it.second.height }?.second
            if (rect != null) RectF(rect.x.toFloat(), rect.y.toFloat(), (rect.x + rect.width).toFloat(), (rect.y + rect.height).toFloat()) else null
        } finally { mask.release(); contours.forEach(Mat::release) }
    }

    private fun dominantClothHue(hsv: Mat, table: RectF? = null): Int? {
        val bins = IntArray(30)
        val left = table?.left ?: 0f
        val top = table?.top ?: 0f
        val width = table?.width() ?: hsv.cols().toFloat()
        val height = table?.height() ?: hsv.rows().toFloat()
        for (y in (top + height * .25f).toInt().coerceAtLeast(0) until (top + height * .75f).toInt().coerceAtMost(hsv.rows()) step 8) {
            for (x in (left + width * .25f).toInt().coerceAtLeast(0) until (left + width * .75f).toInt().coerceAtMost(hsv.cols()) step 8) {
                val pixel = hsv.get(y, x) ?: continue
                if (pixel[1] < 40.0 || pixel[2] < 35.0) continue
                bins[(pixel[0] / 6.0).toInt().coerceIn(0, 29)]++
            }
        }
        val strongest = bins.indices.maxByOrNull { bins[it] } ?: return null
        return if (bins[strongest] >= 25) strongest * 6 + 3 else null
    }

    private fun detectBalls(rgba: Mat, hsv: Mat, table: RectF, calibration: CalibrationProfile): List<BallCandidate> {
        val gray = Mat(); val circles = Mat()
        val roiBounds = org.opencv.core.Rect(
            table.left.toInt().coerceIn(0, rgba.cols() - 1),
            table.top.toInt().coerceIn(0, rgba.rows() - 1),
            table.width().toInt().coerceAtLeast(1).coerceAtMost(rgba.cols() - table.left.toInt().coerceIn(0, rgba.cols() - 1)),
            table.height().toInt().coerceAtLeast(1).coerceAtMost(rgba.rows() - table.top.toInt().coerceIn(0, rgba.rows() - 1))
        )
        val region = rgba.submat(roiBounds)
        return try {
            Imgproc.cvtColor(region, gray, Imgproc.COLOR_RGBA2GRAY)
            Imgproc.GaussianBlur(gray, gray, org.opencv.core.Size(7.0, 7.0), 1.5)
            val expected = (min(table.width(), table.height()) * calibration.ballRadiusFraction).coerceAtLeast(4f)
            Imgproc.HoughCircles(gray, circles, Imgproc.HOUGH_GRADIENT, 1.2, expected * 1.7, 110.0, 20.0,
                (expected * .65f).toInt(), (expected * 1.4f).toInt())
            val raw = ArrayList<BallCandidate>()
            val clothHue = dominantClothHue(hsv, table) ?: return emptyList()
            fun isCloth(pixel: DoubleArray): Boolean {
                val hueDistance = min(abs(pixel[0] - clothHue), 180.0 - abs(pixel[0] - clothHue))
                return hueDistance <= 16.0 && pixel[1] >= 40.0 && pixel[2] >= 30.0
            }
            for (i in 0 until circles.cols()) {
                val c = circles.get(0, i) ?: continue
                val x = c[0].toFloat() + roiBounds.x; val y = c[1].toFloat() + roiBounds.y; val radius = c[2].toFloat()
                if (!table.contains(x, y)) continue
                // A real ball lies on cloth. Rails, pockets, score icons and the
                // floating menu may also be circular, but are not cloth-surrounded.
                val surroundings = (0 until 16).mapNotNull { sample ->
                    val angle = sample * Math.PI / 8.0
                    val cx = (x + cos(angle) * radius * 1.65).toInt()
                    val cy = (y + sin(angle) * radius * 1.65).toInt()
                    if (cx !in 0 until hsv.cols() || cy !in 0 until hsv.rows()) null else hsv.get(cy, cx)
                }
                if (surroundings.size < 14 || surroundings.count(::isCloth) < 11) continue
                // Sample the outer face, not the center: numbered/striped balls can
                // have a white center or highlight, while the cue ball stays neutral.
                val ring = (0 until 8).mapNotNull { sample ->
                    val angle = sample * Math.PI / 4.0
                    val cx = (x + cos(angle) * radius * .7).toInt().coerceIn(0, hsv.cols() - 1)
                    val cy = (y + sin(angle) * radius * .7).toInt().coerceIn(0, hsv.rows() - 1)
                    hsv.get(cy, cx)
                }
                if (ring.size < 6) continue
                if (ring.count { !isCloth(it) } < 3) continue
                val neutralFraction = ring.count { it[1] < 90.0 && it[2] > 100.0 }.toDouble() / ring.size.coerceAtLeast(1)
                val brightness = ring.map { it[2] / 255.0 }.average()
                val whiteness = neutralFraction * .85 + brightness * .15
                val radiusScore = (1f - abs(radius - expected) / expected).coerceIn(0f, 1f)
                raw += BallCandidate(DetectedBall(-1, Vec2(x, y), radius, false, (.55f + .4f * radiusScore).coerceAtMost(.95f)), whiteness.toFloat())
            }
            raw
        } finally { region.release(); gray.release(); circles.release() }
    }

    private fun assignStableIds(detections: List<DetectedBall>): List<DetectedBall> {
        val unused = tracked.keys.toMutableSet()
        val result = detections.map { ball ->
            val match = unused.minByOrNull { id -> (tracked.getValue(id) - ball.center).length() }
                ?.takeIf { id -> (tracked.getValue(id) - ball.center).length() < ball.radius * 2.5f }
            val id = match ?: nextId++
            unused.remove(id)
            val smoothed = tracked[id]?.let { old -> old * .58f + ball.center * .42f } ?: ball.center
            tracked[id] = smoothed
            ball.copy(id = id, center = smoothed)
        }
        val live = result.mapTo(mutableSetOf()) { it.id }
        tracked.keys.retainAll(live)
        return result
    }

    private fun detectCueDirection(rgba: Mat, candidates: List<BallCandidate>, table: RectF): Pair<Int, Vec2>? {
        val gray = Mat(); val edges = Mat(); val lines = Mat()
        val roiBounds = org.opencv.core.Rect(
            table.left.toInt().coerceIn(0, rgba.cols() - 1),
            table.top.toInt().coerceIn(0, rgba.rows() - 1),
            table.width().toInt().coerceAtLeast(1).coerceAtMost(rgba.cols() - table.left.toInt().coerceIn(0, rgba.cols() - 1)),
            table.height().toInt().coerceAtLeast(1).coerceAtMost(rgba.rows() - table.top.toInt().coerceIn(0, rgba.rows() - 1))
        )
        val region = rgba.submat(roiBounds)
        return try {
            Imgproc.cvtColor(region, gray, Imgproc.COLOR_RGBA2GRAY)
            Imgproc.Canny(gray, edges, 60.0, 150.0)
            val minRadius = candidates.minOf { it.ball.radius }
            Imgproc.HoughLinesP(edges, lines, 1.0, Math.PI / 180.0, 45, minRadius * 3.0, minRadius * .8)
            var best: Pair<Int, Vec2>? = null
            var bestScore = Double.NEGATIVE_INFINITY
            for (i in 0 until lines.rows()) {
                val l = lines.get(i, 0) ?: continue
                val p1 = Vec2(l[0].toFloat() + roiBounds.x, l[1].toFloat() + roiBounds.y)
                val p2 = Vec2(l[2].toFloat() + roiBounds.x, l[3].toFloat() + roiBounds.y)
                val length = (p2 - p1).length()
                val midX = ((p1.x + p2.x) / 2f).toInt().coerceIn(0, rgba.cols() - 1)
                val midY = ((p1.y + p2.y) / 2f).toInt().coerceIn(0, rgba.rows() - 1)
                val color = rgba.get(midY, midX) ?: continue
                val brightGuide = max(color[0], max(color[1], color[2])) - min(color[0], min(color[1], color[2])) < 65.0 &&
                    (color[0] + color[1] + color[2]) / 3.0 > 135.0 && table.contains(midX.toFloat(), midY.toFloat())
                if (!brightGuide) continue
                for ((index, candidate) in candidates.withIndex()) {
                    val cue = candidate.ball
                    if (candidate.whiteness < .55f || length < cue.radius * 3f) continue
                    val d1 = (p1 - cue.center).length(); val d2 = (p2 - cue.center).length()
                    val near = min(d1, d2)
                    if (near > cue.radius * 3f) continue
                    val perpendicular = abs((p2.x - p1.x) * (p1.y - cue.center.y) - (p1.x - cue.center.x) * (p2.y - p1.y)) / length
                    if (perpendicular > cue.radius * 1.5f) continue
                    val far = if (d1 > d2) p1 else p2
                    val direction = (far - cue.center).normalized()
                    val score = length + candidate.whiteness * 50.0 - near * 2.0 - perpendicular * 4.0
                    if (score > bestScore) { bestScore = score; best = index to direction }
                }
            }
            best
        } finally { region.release(); gray.release(); edges.release(); lines.release() }
    }
}

