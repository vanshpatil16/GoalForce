package app.drift.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.drift.ui.DriftViewModel

@Composable
fun HomeScreen(vm: DriftViewModel, modifier: Modifier = Modifier) {
    val active = vm.activeSession.collectAsStateWithLifecycle().value
    val streak = vm.streak.collectAsStateWithLifecycle().value
    val intents = vm.intents.collectAsStateWithLifecycle().value

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Today", style = MaterialTheme.typography.headlineMedium)

        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            StatCard("🔥 Streak", "${streak.currentStreak} days", Modifier.weight(1f))
            StatCard("⚡ XP", "${streak.xp}", Modifier.weight(1f))
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (active != null) {
                    Text("Active session", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "${active.intent.targetLabel} — \"${active.intent.purpose}\"",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        "Planned ${active.intent.intendedMinutes} min (+${active.session.extendedMinutes} extended)",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(onClick = { vm.finishActiveSession() }) { Text("End session") }
                } else {
                    Text("No active session", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Set an intention before you open a distracting app.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        Text("Your intentions", style = MaterialTheme.typography.titleMedium)
        if (intents.isEmpty()) {
            Text(
                "None yet. Use the Intent tab to declare a purpose.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            intents.forEach { i ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text(i.targetLabel, style = MaterialTheme.typography.titleSmall)
                        Text(
                            "\"${i.purpose}\" · ${i.intendedMinutes} min",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier) {
        Column(Modifier.padding(16.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            Text(value, style = MaterialTheme.typography.headlineSmall)
        }
    }
}
