package app.drift.ui.screens

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.drift.service.UsageStatsCache
import app.drift.ui.DriftViewModel

@Composable
fun SettingsScreen(vm: DriftViewModel, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val settings = vm.settings.collectAsStateWithLifecycle().value
    val streak = vm.streak.collectAsStateWithLifecycle().value

    val usageAccess = UsageStatsCache.hasUsageAccess(context)
    val a11yEnabled = isAccessibilityEnabled(context)

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium)

        PermissionRow(
            title = "Usage access",
            subtitle = "Lets Drift read foreground time for pattern analysis.",
            granted = usageAccess,
            actionLabel = if (usageAccess) "Granted" else "Grant",
            onAction = { context.startActivity(UsageStatsCache.usageAccessSettingsIntent()) },
        )

        PermissionRow(
            title = "Accessibility service",
            subtitle = "Lets Drift detect app switches and session overruns in real time.",
            granted = a11yEnabled,
            actionLabel = if (a11yEnabled) "Enabled" else "Enable",
            onAction = { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Streak reminders", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Gentle nudges when your streak is on the line.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = settings.notificationsEnabled,
                onCheckedChange = { vm.setNotifications(it) },
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Streak wager", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Bet your streak: meet today's intent for +2 days, or miss it and reset to zero.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = streak.wagerActive,
                onCheckedChange = { vm.setWager(it) },
            )
        }
    }
}

@Composable
private fun PermissionRow(
    title: String,
    subtitle: String,
    granted: Boolean,
    actionLabel: String,
    onAction: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Button(onClick = onAction, enabled = !granted) { Text(actionLabel) }
    }
}

private fun isAccessibilityEnabled(context: Context): Boolean {
    val enabled = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
    ) ?: return false
    return enabled.split(':').any {
        it.contains("DriftAccessibilityService", ignoreCase = true)
    }
}
