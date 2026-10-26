package app.drift.domain

import android.content.Context
import app.drift.domain.model.Intervention
import app.drift.domain.model.Outcome
import app.drift.domain.model.Session
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.time.Instant
import java.time.ZonedDateTime

/** Predicts the probability (0..1) that the user overruns an intention today. */
interface DriftRiskPredictor {
    fun predict(features: FloatArray): Float
}

/** Feature vector for the drift-risk model. */
object DriftRiskFeatures {
    const val SIZE = 5

    /** [hour/24, weekday/7, overrunRate, streak/30, sessionsToday/10] */
    fun extract(
        sessions: List<Session>,
        streakDays: Int,
        now: ZonedDateTime = ZonedDateTime.now(),
    ): FloatArray {
        val finished = sessions.filter { it.outcome != null }
        val overrunRate =
            if (finished.isEmpty()) 0f
            else finished.count { it.outcome == Outcome.OVERRAN }.toFloat() / finished.size
        val today = now.toLocalDate()
        val sessionsToday = sessions.count {
            Instant.ofEpochMilli(it.startedAt).atZone(now.zone).toLocalDate() == today
        }
        return floatArrayOf(
            now.hour / 24f,
            now.dayOfWeek.value / 7f,
            overrunRate,
            (streakDays / 30f).coerceIn(0f, 1f),
            (sessionsToday / 10f).coerceIn(0f, 1f),
        )
    }
}

/**
 * A hand-tuned logistic scorer used when no trained model is bundled. It is a
 * real (if simple) model: late-night hours and a high historical overrun rate
 * raise risk; an active streak lowers it.
 */
class HeuristicDriftRiskPredictor : DriftRiskPredictor {
    override fun predict(features: FloatArray): Float {
        val hour = features.getOrElse(0) { 0f }
        val overrunRate = features.getOrElse(2) { 0f }
        val streakFrac = features.getOrElse(3) { 0f }
        val lateNight = if (hour >= 0.833f || hour < 0.208f) 1f else 0f // ~20:00–05:00
        val score = 0.55f * overrunRate + 0.35f * lateNight - 0.25f * streakFrac + 0.15f
        return sigmoid(score)
    }

    private fun sigmoid(x: Float): Float =
        (1f / (1f + kotlin.math.exp(-x.toDouble()))).toFloat()
}

/**
 * Runs a bundled `.tflite` model (drop `drift_risk_model.tflite` into
 * `app/src/main/assets/`). Falls back to [HeuristicDriftRiskPredictor] when no
 * model is present, so the app always works.
 */
class TfliteDriftRiskPredictor(context: Context) : DriftRiskPredictor {

    private val fallback = HeuristicDriftRiskPredictor()

    private val interpreter: Interpreter? = try {
        val bytes = context.assets.open("drift_risk_model.tflite").use { it.readBytes() }
        val model = ByteBuffer.allocateDirect(bytes.size)
        model.order(ByteOrder.nativeOrder())
        model.put(bytes)
        Interpreter(model)
    } catch (_: Exception) {
        null
    }

    override fun predict(features: FloatArray): Float {
        val interpreter = this.interpreter ?: return fallback.predict(features)
        return try {
            val input = ByteBuffer.allocateDirect(4 * features.size).order(ByteOrder.nativeOrder())
            features.forEach { input.putFloat(it) }
            val output = Array(1) { FloatArray(1) }
            interpreter.run(input, output)
            output[0][0].coerceIn(0f, 1f)
        } catch (_: Exception) {
            fallback.predict(features)
        }
    }
}

/** Risk band used by the UI. */
enum class DriftRisk {
    LOW, MEDIUM, HIGH;

    companion object {
        fun fromScore(score: Float): DriftRisk = when {
            score < 0.4f -> LOW
            score < 0.7f -> MEDIUM
            else -> HIGH
        }
    }
}
