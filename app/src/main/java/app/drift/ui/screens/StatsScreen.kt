package app.drift.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.drift.domain.model.DailyInsight
import app.drift.ui.DriftViewModel
import app.drift.ui.components.ContributionHeatmap
import app.drift.ui.theme.AccentPurple
import app.drift.ui.theme.CardBackground
import app.drift.ui.theme.PastelBackgroundGradient
import app.drift.ui.theme.TextPrimary
import app.drift.ui.theme.TextSecondary
import kotlin.math.max

@Composable
fun StatsScreen(vm: DriftViewModel, modifier: Modifier = Modifier) {
    val insights = vm.insights.collectAsStateWithLifecycle().value
    val daily = vm.dailyInsights.collectAsStateWithLifecycle().value
    val intents = vm.intents.collectAsStateWithLifecycle().value
    val dailyProgress = vm.dailyProgress.collectAsStateWithLifecycle().value
    val streakView = vm.streakView.collectAsStateWithLifecycle().value

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
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                "Progress & Insights",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Text(
                "How your behaviour is changing — and whether Drift is helping you meet your own goals.",
                fontSize = 14.sp,
                color = TextSecondary,
            )

            if (insights == null) {
                Text("Not enough data yet.", color = TextSecondary)
                return@Column
            }

            val topAppLabel = intents.firstOrNull { it.targetPackage == insights.topOverrunApp }?.targetLabel
                ?: insights.topOverrunApp?.substringAfterLast('.') ?: "—"

            MetricCard("Level", "${insights.levelTitle} (L${insights.level})")
            MetricCard("Sessions", "${insights.totalSessions}")
            MetricCard("Overrun rate", pct(insights.overrunRate))
            MetricCard(
                "Trend",
                when {
                    insights.overrunTrend < -0.01 -> "Improving ↑"
                    insights.overrunTrend > 0.01 -> "Slipping ↓"
                    else -> "Steady →"
                },
            )
            MetricCard("Most overrun app", topAppLabel)
            MetricCard(
                "Notifications",
                "${pct(insights.notificationClickRate)} clicked (${insights.notificationsPosted} sent)",
                "best time to reach you: ${hourLabel(insights.bestHour)}",
            )
            MetricCard(
                "Most effective nudge",
                insights.mostEffectiveTemplate?.let { it.name.lowercase().replace('_', ' ') } ?: "—",
            )

            if (daily.isNotEmpty()) {
                Text(
                    "Last ${daily.takeLast(14).size} days",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                DailyChart(daily.takeLast(14))
            }

            Text(
                "Contribution Heatmap",
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Text(
                "Daily streak ${streakView.currentStreak} · weekly ${streakView.qualifyingDaysThisWeek}/${streakView.weeklyTarget} days",
                fontSize = 13.sp,
                color = TextSecondary,
            )
            ContributionHeatmap(dailyProgress)

            Spacer(Modifier.height(80.dp))
        }
    }
}

@Composable
private fun MetricCard(label: String, value: String, hint: String? = null) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(4.dp, RoundedCornerShape(20.dp)),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = CardBackground)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextSecondary)
            Text(value, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            hint?.let {
                Text(it, fontSize = 12.sp, color = TextSecondary)
            }
        }
    }
}


@Composable
private fun DailyChart(daily: List<DailyInsight>) {
    val maxVal = max(1, daily.maxOf { max(it.achieved, it.overruns) })
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(130.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        daily.forEach { d ->
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                Bar(d.achieved, maxVal, MaterialTheme.colorScheme.tertiary, Modifier.weight(1f))
                Bar(d.overruns, maxVal, MaterialTheme.colorScheme.primary, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun Bar(value: Int, maxVal: Int, color: Color, modifier: Modifier = Modifier) {
    val fraction = if (maxVal == 0) 0f else (value.toFloat() / maxVal).coerceIn(0f, 1f)
    Box(
        modifier = modifier
            .height((120 * fraction).dp)
            .background(color, RoundedCornerShape(3.dp)),
    )
}

private fun pct(v: Double): String = "${(v * 100).toInt()}%"

private fun hourLabel(hour: Int): String = when {
    hour == 0 -> "12am"
    hour < 12 -> "${hour}am"
    hour == 12 -> "12pm"
    else -> "${hour - 12}pm"
}
