package app.drift.intervention

import android.content.Intent
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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.drift.DriftApp
import app.drift.domain.model.DriftKind
import app.drift.domain.model.InterventionTemplate
import app.drift.ui.theme.DriftTheme
import kotlinx.coroutines.launch

/**
 * The full-screen intervention — styled after one sec: a breathing animation with
 * "Continue to X" / "I don't want to open X". It never hard-blocks; it offers a
 * mindful moment and lets the user decide.
 */
class InterventionActivity : ComponentActivity() {

    private val container get() = (application as DriftApp).container

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val template = intent.getStringExtra(EXTRA_TEMPLATE)
            ?.let { runCatching { InterventionTemplate.valueOf(it) }.getOrNull() }
            ?: InterventionTemplate.BREATH_PAUSE
        val drift = intent.getStringExtra(EXTRA_DRIFT)
            ?.let { runCatching { DriftKind.valueOf(it) }.getOrNull() }
            ?: DriftKind.OPENED
        val label = intent.getStringExtra(EXTRA_LABEL) ?: ""
        val purpose = intent.getStringExtra(EXTRA_PURPOSE) ?: ""
        val intended = intent.getIntExtra(EXTRA_INTENDED_MINUTES, 10)
        val elapsed = intent.getIntExtra(EXTRA_ELAPSED_MINUTES, intended)

        setContent {
            DriftTheme {
                InterventionScreen(
                    template = template,
                    drift = drift,
                    label = label,
                    purpose = purpose,
                    intended = intended,
                    elapsed = elapsed,
                    onContinue = { finish() },
                    onExit = {
                        container.appScope.launch { container.coordinator.finishSession(achieved = true) }
                        val home = Intent(Intent.ACTION_MAIN)
                            .addCategory(Intent.CATEGORY_HOME)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        startActivity(home)
                        finish()
                    },
                    onExtend = { minutes ->
                        container.appScope.launch { container.coordinator.extendSession(minutes) }
                        finish()
                    },
                    onSnooze = { finish() },
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

@Composable
private fun InterventionScreen(
    template: InterventionTemplate,
    drift: DriftKind,
    label: String,
    purpose: String,
    intended: Int,
    elapsed: Int,
    onContinue: () -> Unit,
    onExit: () -> Unit,
    onExtend: (Int) -> Unit,
    onSnooze: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(app.drift.ui.theme.PastelBackgroundGradient),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            BreathCircle()

            Spacer(Modifier.height(36.dp))
            Text(
                text = titleFor(template, drift, label, intended, elapsed),
                fontSize = 24.sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                textAlign = TextAlign.Center,
                color = app.drift.ui.theme.TextPrimary,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = bodyFor(template, label, purpose),
                fontSize = 15.sp,
                lineHeight = 22.sp,
                textAlign = TextAlign.Center,
                color = app.drift.ui.theme.TextSecondary,
            )
            Spacer(Modifier.height(40.dp))

            // Primary Purple Pill Button
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .shadow(8.dp, androidx.compose.foundation.shape.RoundedCornerShape(27.dp), spotColor = app.drift.ui.theme.AccentPurple)
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(27.dp))
                    .background(
                        androidx.compose.ui.graphics.Brush.horizontalGradient(
                            listOf(app.drift.ui.theme.AccentPurpleLight, app.drift.ui.theme.AccentPurple)
                        )
                    )
                    .clickable { onContinue() },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (label.isNotEmpty()) "Continue to $label" else "Continue",
                    color = Color.White,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    fontSize = 15.sp
                )
            }

            Spacer(Modifier.height(12.dp))

            // White pill button
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .shadow(4.dp, androidx.compose.foundation.shape.RoundedCornerShape(27.dp))
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(27.dp))
                    .background(Color.White)
                    .clickable { onExit() },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (label.isNotEmpty()) "I don't want to open $label" else "I'm done",
                    color = app.drift.ui.theme.TextPrimary,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                    fontSize = 15.sp
                )
            }

            if (drift == DriftKind.OVER_RUN || drift == DriftKind.COMPULSIVE_REENTRY) {
                Spacer(Modifier.height(12.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(25.dp))
                        .background(Color(0xFFEDE9FE))
                        .clickable { onExtend(5) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Give me 5 more min",
                        color = app.drift.ui.theme.AccentPurple,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun BreathCircle() {
    val transition = rememberInfiniteTransition(label = "breath")
    val scale by transition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.3f,
        animationSpec = infiniteRepeatable(tween(3400, easing = LinearEasing), RepeatMode.Reverse),
        label = "breathScale",
    )
    Box(
        modifier = Modifier
            .size(170.dp)
            .scale(scale)
            .shadow(16.dp, CircleShape, spotColor = app.drift.ui.theme.AccentPurple)
            .background(
                androidx.compose.ui.graphics.Brush.radialGradient(
                    listOf(
                        Color(0xFFC0AFFE),
                        app.drift.ui.theme.AccentPurpleLight,
                        app.drift.ui.theme.AccentPurple
                    )
                ),
                CircleShape
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "breathe",
            color = Color.White,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
            fontSize = 18.sp
        )
    }
}

@Composable
private fun titleFor(
    template: InterventionTemplate,
    drift: DriftKind,
    label: String,
    intended: Int,
    elapsed: Int,
): String = when {
    template == InterventionTemplate.REFLECTION -> "Was this what you came for?"
    drift == DriftKind.OVER_RUN -> "You planned $intended min. It's been $elapsed."
    drift == DriftKind.COMPULSIVE_REENTRY -> "Back again so soon?"
    else -> "It's time to take a deep breath…"
}

@Composable
private fun bodyFor(template: InterventionTemplate, label: String, purpose: String): String = when {
    template == InterventionTemplate.REFLECTION && purpose.isNotBlank() ->
        "You came to \"$purpose\". Did you get what you needed?"
    label.isNotEmpty() -> "You're about to open $label."
    else -> "Pause for a moment before you continue."
}
