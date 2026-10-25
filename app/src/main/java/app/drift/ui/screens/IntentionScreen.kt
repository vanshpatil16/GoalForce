package app.drift.ui.screens

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.drift.domain.model.IntentKind
import app.drift.ui.DriftViewModel

private data class AppEntry(val packageName: String, val label: String)

private fun launchableApps(context: Context): List<AppEntry> {
    val pm = context.packageManager
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    return pm.queryIntentActivities(intent, 0)
        .map { AppEntry(it.activityInfo.packageName, it.loadLabel(pm).toString()) }
        .distinctBy { it.packageName }
        .sortedBy { it.label.lowercase() }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun IntentionScreen(vm: DriftViewModel, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val active = vm.activeSession.collectAsStateWithLifecycle().value

    var purpose by remember { mutableStateOf("") }
    var minutes by remember { mutableStateOf(10) }
    var kind by remember { mutableStateOf(IntentKind.QUICK_CHECK) }
    var target by remember { mutableStateOf<AppEntry?>(null) }
    var showPicker by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text("What are you here for?", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Tell Drift your purpose and how long you need. We'll only step in when your actual usage drifts from this.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        OutlinedTextField(
            value = purpose,
            onValueChange = { purpose = it },
            label = { Text("Purpose") },
            placeholder = { Text("e.g. reply to DMs, watch one lecture") },
            modifier = Modifier.fillMaxWidth(),
        )

        Text("How long do you need?", style = MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(5, 10, 15, 30, 45, 60).forEach { m ->
                FilterChip(
                    selected = minutes == m,
                    onClick = { minutes = m },
                    label = { Text("${m}m") },
                )
            }
        }

        Text("What kind of session?", style = MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IntentKind.entries.forEach { k ->
                FilterChip(
                    selected = kind == k,
                    onClick = { kind = k },
                    label = { Text(labelFor(k)) },
                )
            }
        }

        Text("Where?", style = MaterialTheme.typography.titleMedium)
        Button(onClick = { showPicker = true }) {
            Text(target?.label ?: "Choose an app")
        }

        Spacer(Modifier.height(8.dp))
        Button(
            onClick = {
                val t = target ?: return@Button
                vm.startIntent(purpose.ifBlank { t.label }, minutes, kind, t.packageName, t.label)
                purpose = ""
            },
            enabled = target != null,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (active == null) "Start session" else "Start a new session")
        }

        if (active != null) {
            Text(
                "A session is already active (${active.intent.targetLabel}). Starting a new one ends the current.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.tertiary,
            )
        }
    }

    if (showPicker) {
        AppPickerDialog(
            apps = remember { launchableApps(context) },
            onPick = { target = it; showPicker = false },
            onDismiss = { showPicker = false },
        )
    }
}

@Composable
private fun AppPickerDialog(
    apps: List<AppEntry>,
    onPick: (AppEntry) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(apps, query) {
        if (query.isBlank()) apps else apps.filter { it.label.contains(query, ignoreCase = true) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choose an app") },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Search") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                LazyColumn(Modifier.height(360.dp)) {
                    items(filtered) { app ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            TextButton(onClick = { onPick(app) }) { Text(app.label) }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private fun labelFor(kind: IntentKind): String = when (kind) {
    IntentKind.QUICK_CHECK -> "Quick check"
    IntentKind.LONG_FORM -> "Long-form / focused"
    IntentKind.TASK -> "A specific task"
    IntentKind.ENTERTAINMENT -> "Unwind"
}
