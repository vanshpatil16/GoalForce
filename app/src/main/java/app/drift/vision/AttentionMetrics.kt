package app.drift.vision

import kotlin.math.hypot

/** Relative landmark metrics only; they are not medical or absolute gaze measurements. */
object AttentionMetrics {
    data class Point(val x: Float, val y: Float)

    fun ear(points: List<Point>, indices: IntArray): Float? {
        if (indices.size != 6 || indices.any { it !in points.indices }) return null
        val p = indices.map(points::get)
        val width = distance(p[0], p[3])
        if (width <= 0f) return null
        return (distance(p[1], p[5]) + distance(p[2], p[4])) / (2f * width)
    }

    fun mar(upperLip: Point, lowerLip: Point, leftCorner: Point, rightCorner: Point): Float? {
        val width = distance(leftCorner, rightCorner)
        if (width <= 0f) return null
        return distance(upperLip, lowerLip) / width
    }

    /** Iris displacement normalized to the eye contour's horizontal and vertical span. */
    fun normalizeIris(iris: Point, eyeLeft: Point, eyeRight: Point, eyeTop: Point, eyeBottom: Point): Point? {
        val width = eyeRight.x - eyeLeft.x
        val height = eyeBottom.y - eyeTop.y
        if (width <= 0f || height <= 0f) return null
        return Point((iris.x - (eyeLeft.x + eyeRight.x) / 2f) / width,
            (iris.y - (eyeTop.y + eyeBottom.y) / 2f) / height)
    }

    private fun distance(a: Point, b: Point) = hypot(a.x - b.x, a.y - b.y)
}

enum class FatigueStatus { NORMAL, POSSIBLE_DROWSINESS, POSSIBLE_YAWN, ELEVATED_INDICATORS, MONITORING_UNAVAILABLE }

/** Simple hysteresis/time gate; measurements are estimates and callers should calibrate EAR per user. */
class FatigueStateMachine(
    private val closedEarThreshold: Float = 0.18f,
    private val sustainedClosureMs: Long = 1_500,
    private val yawnMarThreshold: Float = 0.55f,
    private val sustainedYawnMs: Long = 1_200,
) {
    private var closedSince: Long? = null
    private var mouthSince: Long? = null
    private var lastValidAt: Long? = null
    private var drowsyUntil: Long = 0
    private var yawnUntil: Long = 0
    private val baselineEyes = mutableListOf<Float>()
    private var calibratedClosedThreshold = closedEarThreshold
    val isCalibrating: Boolean get() = baselineEyes.size < 10

    fun update(nowMs: Long, ear: Float?, mar: Float?): FatigueStatus {
        if (ear == null || mar == null) {
            if (lastValidAt == null || nowMs - lastValidAt!! > 1_500) return FatigueStatus.MONITORING_UNAVAILABLE
            return status(nowMs)
        }
        lastValidAt = nowMs
        // Build a small open-eye baseline from early valid frames; only accept values
        // above the conservative default so a blink at startup cannot define the baseline.
        if (baselineEyes.size < 10 && ear > closedEarThreshold) {
            baselineEyes += ear
            if (baselineEyes.size == 10) calibratedClosedThreshold = (baselineEyes.average().toFloat() * 0.65f).coerceIn(0.12f, 0.28f)
        }
        closedSince = if (ear < calibratedClosedThreshold) closedSince ?: nowMs else null
        mouthSince = if (mar > yawnMarThreshold) mouthSince ?: nowMs else null
        if (closedSince?.let { nowMs - it >= sustainedClosureMs } == true) drowsyUntil = nowMs + 3_000
        if (mouthSince?.let { nowMs - it >= sustainedYawnMs } == true) yawnUntil = nowMs + 3_000
        return status(nowMs)
    }

    private fun status(nowMs: Long): FatigueStatus {
        val sleepy = nowMs < drowsyUntil
        val yawn = nowMs < yawnUntil
        return when { sleepy && yawn -> FatigueStatus.ELEVATED_INDICATORS; sleepy -> FatigueStatus.POSSIBLE_DROWSINESS; yawn -> FatigueStatus.POSSIBLE_YAWN; else -> FatigueStatus.NORMAL }
    }
}
