package app.drift.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.drift.ui.DriftViewModel

@Composable
fun StatsScreen(vm: DriftViewModel, modifier: Modifier = Modifier) {
    val metrics = vm.metrics.collectAsStateWithLifecycle().value

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Progress", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Does Drift actually help you meet your own goals? This compares sessions with and without an intervention.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (metrics == null) {
            Text("Not enough data yet.")
            return@Column
        }

        MetricCard("Sessions", "${metrics.totalSessions}")
        MetricCard("Overall overrun rate", pct(metrics.overrunRate))
        MetricCard("Overrun rate — with intervention", pct(metrics.overrunRateWithIntervention))
        MetricCard("Overrun rate — without intervention", pct(metrics.overrunRateWithoutIntervention))
        MetricCard("Interventions shown", "${metrics.interventionCount}")
        MetricCard(
            "Intervention success rate",
            metrics.rewardRate?.let { pct(it) } ?: "—",
            "share of interventions followed by an on-time / on-purpose end",
        )
        MetricCard("Avg overrun", "${metrics.averageOverrunMinutes.toInt()} min")
    }
}

@Composable
private fun MetricCard(label: String, value: String, hint: String? = null) {
    Card(Modifier.fillMaxSize().padding(bottom = 0.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.headlineSmall)
            hint?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private fun pct(v: Double): String = "${(v * 100).toInt()}%"
