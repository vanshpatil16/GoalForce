package app.drift.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.drift.ui.DriftViewModel
import app.drift.ui.theme.AccentPurple
import app.drift.ui.theme.CardBackground
import app.drift.ui.theme.PastelBackgroundGradient
import app.drift.ui.theme.TextMuted
import app.drift.ui.theme.TextPrimary
import app.drift.ui.theme.TextSecondary
import app.drift.util.Permissions
import kotlinx.coroutines.delay

/**
 * The live focus-session view: shows the intention you're currently working on,
 * how long you've been at it, and lets you end the session. When nothing is
 * active, it offers a friendly start point instead of a fake schedule.
 */
@Composable
fun ActiveSessionScreen(
    vm: DriftViewModel,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    onNavigateToCreate: () -> Unit = {}
) {
    val active = vm.activeSession.collectAsStateWithLifecycle().value
    val sessionXp = vm.activeSessionXp.collectAsStateWithLifecycle().value
    val context = LocalContext.current

    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(active?.session?.id) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }

    val elapsedSec = if (active != null) ((now - active.session.startedAt) / 1000).coerceAtLeast(0) else 0L
    val intendedSec = if (active != null) active.intent.intendedMinutes * 60L else 0L
    val effectiveSec = intendedSec + (active?.session?.extendedMinutes ?: 0) * 60L
    val monitoring = Permissions.isAccessibilityEnabled(context)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(PastelBackgroundGradient)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // Top Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .shadow(3.dp, CircleShape)
                        .clip(CircleShape)
                        .background(Color.White)
                        .clickable { onBack() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Back",
                        tint = TextPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Text(
                    text = "Focus Session",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )

                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .shadow(4.dp, CircleShape, ambientColor = AccentPurple, spotColor = AccentPurple)
                        .clip(CircleShape)
                        .background(AccentPurple)
                        .clickable { onNavigateToCreate() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "New session",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            if (active == null) {
                EmptyState(onStart = onNavigateToCreate)
            } else {
                LiveSessionCard(
                    purpose = active.intent.purpose,
                    appLabel = active.intent.targetLabel,
                    elapsedSec = elapsedSec,
                    effectiveSec = effectiveSec,
                    xp = sessionXp,
                    recoveries = active.session.reentries,
                    monitoring = monitoring,
                    onEnd = { vm.finishActiveSession() },
                )
            }

            Spacer(Modifier.height(80.dp))
        }
    }
}

@Composable
private fun EmptyState(onStart: () -> Unit) {
    Spacer(Modifier.height(40.dp))
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "No active focus session",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        )
        Text(
            text = "Set an intention and Drift will gently keep you on track.",
            fontSize = 14.sp,
            color = TextSecondary
        )
        Spacer(Modifier.height(12.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .shadow(10.dp, RoundedCornerShape(28.dp), ambientColor = AccentPurple, spotColor = AccentPurple)
                .clip(RoundedCornerShape(28.dp))
                .background(AccentPurple)
                .clickable { onStart() },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Start a focus session",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
    }
}

@Composable
private fun LiveSessionCard(
    purpose: String,
    appLabel: String,
    elapsedSec: Long,
    effectiveSec: Long,
    xp: Int,
    recoveries: Int,
    monitoring: Boolean,
    onEnd: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(4.dp, RoundedCornerShape(22.dp)),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = CardBackground)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Working on",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextSecondary
            )
            Text(
                text = purpose,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Text(
                text = "in $appLabel",
                fontSize = 13.sp,
                color = TextMuted
            )

            Text(
                text = "${formatDuration(elapsedSec)} / ${formatDuration(effectiveSec)}",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = AccentPurple
            )
            Text(
                text = "+$xp XP · $recoveries drift recoveries",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = TextSecondary
            )

            if (!monitoring) {
                Text(
                    text = "Heads up: the accessibility service is off, so Drift can't detect drift right now.",
                    fontSize = 12.sp,
                    color = TextSecondary
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .clip(RoundedCornerShape(26.dp))
                    .background(Color(0xFFEDE9FE))
                    .clickable { onEnd() },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "End session",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = AccentPurple
                )
            }
        }
    }
}

private fun formatDuration(totalSeconds: Long): String {
    val m = totalSeconds / 60
    val s = totalSeconds % 60
    return "%d:%02d".format(m, s)
}
