package app.drift.intervention

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.drift.DriftApp
import app.drift.audio.VoiceCoach
import app.drift.domain.model.DriftKind
import app.drift.domain.model.InterventionTemplate
import app.drift.ui.theme.DriftTheme
import kotlinx.coroutines.launch

/**
 * The full-screen contextual intervention. Launched over the tracked app by the
 * accessibility service. It never hard-blocks: it offers a stopping point and
 * lets the user decide, which is what distinguishes this from a blocker.
 */
class InterventionActivity : ComponentActivity() {

    private val container get() = (application as DriftApp).container

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val template = intent.getStringExtra(EXTRA_TEMPLATE)
            ?.let { runCatching { InterventionTemplate.valueOf(it) }.getOrNull() }
            ?: InterventionTemplate.STOPPING_POINT
        val drift = intent.getStringExtra(EXTRA_DRIFT)
            ?.let { runCatching { DriftKind.valueOf(it) }.getOrNull() }
            ?: DriftKind.OVER_RUN
        val label = intent.getStringExtra(EXTRA_LABEL) ?: ""
        val purpose = intent.getStringExtra(EXTRA_PURPOSE) ?: ""
        val intended = intent.getIntExtra(EXTRA_INTENDED_MINUTES, 10)
        val elapsed = intent.getIntExtra(EXTRA_ELAPSED_MINUTES, intended)
        VoiceCoach.speak(this, spokenPrompt(template, drift, label, purpose, intended, elapsed))

        setContent {
            DriftTheme {
                InterventionScreen(
                    template = template,
                    drift = drift,
                    label = label,
                    purpose = purpose,
                    intended = intended,
                    elapsed = elapsed,
                    onDone = {
                        VoiceCoach.sessionCompleted(this)
                        container.appScope.launch { container.coordinator.finishSession(achieved = true) }
                        finish()
                    },
                    onContinue = { VoiceCoach.tap(this); finish() },
                    onExtend = { minutes ->
                        VoiceCoach.tap(this)
                        container.appScope.launch { container.coordinator.extendSession(minutes) }
                        finish()
                    },
                    onSnooze = { VoiceCoach.tap(this); finish() },
                )
            }
        }
    }

    companion object {
        const val EXTRA_TEMPLATE = "template"
        const val EXTRA_DRIFT = "drift"
        const val EXTRA_PACKAGE = "package"
        const val EXTRA_LABEL = "label"
        const val EXTRA_PURPOSE = "purpose"
        const val EXTRA_INTENDED_MINUTES = "intended"
        const val EXTRA_ELAPSED_MINUTES = "elapsed"
    }
}

private fun spokenPrompt(template: InterventionTemplate, drift: DriftKind, label: String, purpose: String, intended: Int, elapsed: Int): String = when (template) {
    InterventionTemplate.EXIT_PROMPT -> if (drift == DriftKind.OVER_RUN) "Quick check-in. You planned $intended minutes, and it has been $elapsed. Want to wrap up?" else "Back again so soon? Is this still what you meant to do?"
    InterventionTemplate.BREATH_PAUSE -> "Pause for a second. Take one slow breath before you continue."
    InterventionTemplate.REFLECTION -> "You came to $purpose. Did you get what you needed?"
    InterventionTemplate.REWARD_NUDGE -> "You're on a roll. Keep your streak alive."
    InterventionTemplate.STOPPING_POINT -> if (label.isNotEmpty()) "Quick check. Is $label still where you want to be?" else "Quick check. Is this still what you want to be doing?"
}

@Composable
private fun InterventionScreen(
    template: InterventionTemplate,
    drift: DriftKind,
    label: String,
    purpose: String,
    intended: Int,
    elapsed: Int,
    onDone: () -> Unit,
    onContinue: () -> Unit,
    onExtend: (Int) -> Unit,
    onSnooze: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            when (template) {
                InterventionTemplate.BREATH_PAUSE -> BreathCircle()
                else -> Spacer(Modifier.height(8.dp))
            }

            Spacer(Modifier.height(24.dp))
            Text(
                text = titleFor(template, drift, label, intended, elapsed),
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = bodyFor(template, label, purpose),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(40.dp))

            when (template) {
                InterventionTemplate.EXIT_PROMPT -> {
                    OutlinedButton(onClick = { onExtend(5) }) { Text("Give me 5 more min") }
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = onDone) { Text("I'm done") }
                }
                else -> {
                    Button(onClick = onContinue) { Text("Continue intentionally") }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = onDone) { Text("I'm done") }
                }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onSnooze) { Text("Not now") }
        }
    }
}

@Composable
private fun BreathCircle() {
    val transition = rememberInfiniteTransition(label = "breath")
    val scale by transition.animateFloat(
        initialValue = 0.7f,
        targetValue = 1.3f,
        animationSpec = infiniteRepeatable(tween(3200, easing = LinearEasing), RepeatMode.Reverse),
        label = "breathScale",
    )
    Box(
        modifier = Modifier
            .size(160.dp)
            .scale(scale)
            .background(Color(0xFF7C9EFC), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text("breathe", color = Color.White, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun titleFor(
    template: InterventionTemplate,
    drift: DriftKind,
    label: String,
    intended: Int,
    elapsed: Int,
): String = when (template) {
    InterventionTemplate.STOPPING_POINT -> "This looks like a natural stopping point."
    InterventionTemplate.EXIT_PROMPT -> if (drift == DriftKind.OVER_RUN) "You planned $intended min. It's been $elapsed." else "Back again so soon?"
    InterventionTemplate.BREATH_PAUSE -> "Take one slow breath before you continue."
    InterventionTemplate.REFLECTION -> "Was this what you came for?"
    InterventionTemplate.REWARD_NUDGE -> "You're on a roll. Keep your streak alive."
}

@Composable
private fun bodyFor(template: InterventionTemplate, label: String, purpose: String): String = when (template) {
    InterventionTemplate.REFLECTION ->
        "You came to \"$purpose\". Did you get what you needed?"
    InterventionTemplate.REWARD_NUDGE ->
        "Ending this on purpose keeps your streak going."
    else ->
        if (label.isNotEmpty()) "This is $label. It's okay to step away now." else "It's okay to step away now."
}
