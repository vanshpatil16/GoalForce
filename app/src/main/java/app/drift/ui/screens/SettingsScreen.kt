package app.drift.ui.screens

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
import app.drift.domain.content.ContentBlockMode
import app.drift.service.UsageStatsCache
import app.drift.ui.DriftViewModel

@Composable
fun SettingsScreen(vm: DriftViewModel, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val settings = vm.settings.collectAsStateWithLifecycle().value
    val streak = vm.streak.collectAsStateWithLifecycle().value
    val name = vm.profileName.collectAsStateWithLifecycle().value
    var nameInput by remember(name) { mutableStateOf(name) }

    val usageAccess = UsageStatsCache.hasUsageAccess(context)
    val a11yEnabled = isAccessibilityEnabled(context)
    val mode = runCatching { ContentBlockMode.valueOf(settings.contentBlockMode) }
        .getOrDefault(ContentBlockMode.COMPLETE_BLOCK)

    androidx.compose.foundation.layout.Box(
        modifier = modifier
            .fillMaxSize()
            .background(app.drift.ui.theme.PastelBackgroundGradient)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                "Settings",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = app.drift.ui.theme.TextPrimary
            )

            // Re-run setup
            androidx.compose.foundation.layout.Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(2.dp, androidx.compose.foundation.shape.RoundedCornerShape(18.dp))
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(18.dp))
                    .background(Color.White)
                    .clickable { vm.resetOnboarding() }
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("Re-run Onboarding & Setup", color = app.drift.ui.theme.AccentPurple, fontWeight = FontWeight.Bold)
            }

            // Name card
            Card(
                modifier = Modifier.fillMaxWidth().shadow(3.dp, androidx.compose.foundation.shape.RoundedCornerShape(20.dp)),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
                colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Profile Name", fontWeight = FontWeight.Bold, color = app.drift.ui.theme.TextPrimary)
                    OutlinedTextField(
                        value = nameInput,
                        onValueChange = { nameInput = it },
                        label = { Text("Your name") },
                        singleLine = true,
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (nameInput != name) {
                        Button(
                            onClick = { vm.setProfileName(nameInput.ifBlank { "You" }) },
                            colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = app.drift.ui.theme.AccentPurple)
                        ) {
                            Text("Save name")
                        }
                    }
                }
            }

            // Permissions Card
            Card(
                modifier = Modifier.fillMaxWidth().shadow(3.dp, androidx.compose.foundation.shape.RoundedCornerShape(20.dp)),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
                colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text("Permissions", fontWeight = FontWeight.Bold, color = app.drift.ui.theme.TextPrimary)
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
                }
            }

            // Notifications & Wager Card
            Card(
                modifier = Modifier.fillMaxWidth().shadow(3.dp, androidx.compose.foundation.shape.RoundedCornerShape(20.dp)),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
                colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    SettingsToggle(
                        title = "Streak reminders",
                        subtitle = "Gentle nudges when your streak is on the line.",
                        checked = settings.notificationsEnabled,
                        onCheckedChange = { vm.setNotifications(it) },
                    )
                    SettingsToggle(
                        title = "Streak wager",
                        subtitle = "Bet your streak: meet today's intent for +2 days, or miss it and reset.",
                        checked = streak.wagerActive,
                        onCheckedChange = { vm.setWager(it) },
                    )
                }
            }

            // Gamification Card
            Card(
                modifier = Modifier.fillMaxWidth().shadow(3.dp, androidx.compose.foundation.shape.RoundedCornerShape(20.dp)),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
                colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text("Gamification & Social", fontWeight = FontWeight.Bold, color = app.drift.ui.theme.TextPrimary)
                    SettingsToggle(
                        title = "Gamification",
                        subtitle = "XP, streaks, badges and rewards.",
                        checked = settings.gamificationEnabled,
                        onCheckedChange = { vm.setGamificationEnabled(it) },
                    )
                    SettingsToggle(
                        title = "Variable rewards",
                        subtitle = "Optional random XP bonuses (bounded).",
                        checked = settings.variableRewardsEnabled,
                        onCheckedChange = { vm.setVariableRewardsEnabled(it) },
                    )
                    SettingsToggle(
                        title = "Notification-cost reminders",
                        subtitle = "Show estimated interruption cost when enough history exists.",
                        checked = settings.notificationCostEnabled,
                        onCheckedChange = { vm.setNotificationCostEnabled(it) },
                    )
                    SettingsToggle(
                        title = "Social sharing",
                        subtitle = "Share progress with friends.",
                        checked = settings.socialSharingEnabled,
                        onCheckedChange = { vm.setSocialSharingEnabled(it) },
                    )
                    SettingsToggle(
                        title = "Leaderboard participation",
                        subtitle = "Appear on weekly and monthly leaderboards.",
                        checked = settings.leaderboardEnabled,
                        onCheckedChange = { vm.setLeaderboardEnabled(it) },
                    )
                }
            }

            // Privacy & Screen Analysis Card
            Card(
                modifier = Modifier.fillMaxWidth().shadow(3.dp, androidx.compose.foundation.shape.RoundedCornerShape(20.dp)),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
                colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text("Privacy & Screen Analysis", fontWeight = FontWeight.Bold, color = app.drift.ui.theme.TextPrimary)
                    SettingsToggle(
                        title = "Periodic screen analysis",
                        subtitle = "Run local goal-alignment checks on a schedule without changing the main Drift focus flow.",
                        checked = settings.screenAnalysisEnabled,
                        onCheckedChange = { vm.setScreenAnalysisEnabled(it) },
                    )
                    SettingsToggle(
                        title = "Privacy notice accepted",
                        subtitle = "Confirm the app will only use local metadata and never store raw screenshots.",
                        checked = settings.privacyNoticeAccepted,
                        enabled = settings.screenAnalysisEnabled,
                        onCheckedChange = { vm.setPrivacyNoticeAccepted(it) },
                    )
                    SettingsToggle(
                        title = "External AI analysis",
                        subtitle = "Allow reviewed, opt-in backend analysis through a secure proxy when configured.",
                        checked = settings.externalAiAnalysisEnabled,
                        enabled = settings.screenAnalysisEnabled && settings.privacyNoticeAccepted,
                        onCheckedChange = { vm.setExternalAiAnalysisEnabled(it) },
                    )
                    Text(
                        text = "Observation interval: ${settings.observationIntervalMinutes} min",
                        style = MaterialTheme.typography.bodyMedium,
                        color = app.drift.ui.theme.TextPrimary,
                    )
                    androidx.compose.material3.Slider(
                        value = settings.observationIntervalMinutes.toFloat(),
                        onValueChange = { vm.setObservationIntervalMinutes(it.toInt()) },
                        valueRange = 1f..60f,
                        steps = 58,
                        enabled = settings.screenAnalysisEnabled,
                    )
                }
            }

            // Content Blocking Card
            Card(
                modifier = Modifier.fillMaxWidth().shadow(3.dp, androidx.compose.foundation.shape.RoundedCornerShape(20.dp)),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
                colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text("Content Blocking", fontWeight = FontWeight.Bold, color = app.drift.ui.theme.TextPrimary)
                    SettingsToggle(
                        title = "Block Reels & Shorts",
                        subtitle = "Auto-exit Instagram Reels, YouTube Shorts and TikTok when they open.",
                        checked = settings.contentBlockingEnabled,
                        onCheckedChange = { vm.setContentBlockingEnabled(it) },
                    )
                    SettingsToggle(
                        title = "Keep videos sent in DMs",
                        subtitle = "Don't block Reels a friend sent you directly.",
                        checked = settings.allowDmVideos,
                        onCheckedChange = { vm.setAllowDmVideos(it) },
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = mode == ContentBlockMode.COMPLETE_BLOCK,
                            onClick = { vm.setContentBlockMode(ContentBlockMode.COMPLETE_BLOCK) },
                            label = { Text("Complete block") },
                        )
                        FilterChip(
                            selected = mode == ContentBlockMode.COVER_OVERLAY,
                            onClick = { vm.setContentBlockMode(ContentBlockMode.COVER_OVERLAY) },
                            label = { Text("Cover overlay") },
                        )
                    }
                }
            }

            Spacer(Modifier.height(80.dp))
        }
    }
}

@Composable
private fun SettingsToggle(
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
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
