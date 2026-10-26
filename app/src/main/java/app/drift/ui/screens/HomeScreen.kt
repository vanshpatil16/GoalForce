package app.drift.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.Assignment
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.NorthEast
import androidx.compose.material.icons.outlined.PendingActions
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.drift.domain.DriftRisk
import app.drift.domain.LevelSystem
import app.drift.domain.model.IntentKind
import app.drift.domain.model.Outcome
import app.drift.domain.model.UserIntent
import app.drift.ui.DriftViewModel
import app.drift.ui.components.ModernProgressRing
import app.drift.ui.theme.AccentLime
import app.drift.ui.theme.AccentLimeDark
import app.drift.ui.theme.AccentPurple
import app.drift.ui.theme.AccentPurpleLight
import app.drift.ui.theme.BorderLight
import app.drift.ui.theme.CardBackground
import app.drift.ui.theme.PastelBackgroundGradient
import app.drift.ui.theme.PriorityHighBg
import app.drift.ui.theme.PriorityHighBorder
import app.drift.ui.theme.PriorityHighText
import app.drift.ui.theme.PriorityLowBg
import app.drift.ui.theme.PriorityLowBorder
import app.drift.ui.theme.PriorityLowText
import app.drift.ui.theme.PriorityMedBg
import app.drift.ui.theme.PriorityMedBorder
import app.drift.ui.theme.PriorityMedText
import app.drift.ui.theme.TextMuted
import app.drift.ui.theme.TextPrimary
import app.drift.ui.theme.TextSecondary
import java.time.LocalTime

@Composable
fun HomeScreen(
    vm: DriftViewModel,
    modifier: Modifier = Modifier,
    onNavigateToCreate: () -> Unit = {},
    onNavigateToSchedule: () -> Unit = {},
) {
    val active = vm.activeSession.collectAsStateWithLifecycle().value
    val streak = vm.streak.collectAsStateWithLifecycle().value
    val name = vm.profileName.collectAsStateWithLifecycle().value
    val insights = vm.insights.collectAsStateWithLifecycle().value
    val intents = vm.intents.collectAsStateWithLifecycle().value
    val sessions = vm.sessions.collectAsStateWithLifecycle().value
    val riskScore = vm.driftRisk.collectAsStateWithLifecycle().value
    val risk = DriftRisk.fromScore(riskScore)
    val progress = vm.progress.collectAsStateWithLifecycle().value
    val lifetimeXp = progress?.lifetimeXp ?: 0

    var selectedFilterIndex by remember { mutableIntStateOf(0) }

    // Dynamic greeting based on current time
    val greeting = remember {
        val hour = LocalTime.now().hour
        when (hour) {
            in 5..11 -> "Good Morning"
            in 12..16 -> "Good Afternoon"
            else -> "Good Evening"
        }
    }

    // Progress metric calculations
    val totalCount = (intents.size + sessions.size).coerceAtLeast(1)
    val completedSessions = sessions.count { it.outcome == Outcome.ON_TIME || it.outcome == Outcome.ACHIEVED_PURPOSE }
    val progressPercent = if (totalCount > 0) {
        ((completedSessions.toFloat() / totalCount.toFloat()) * 100).toInt().coerceIn(25, 95)
    } else 72

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
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            // Top Bar: Avatar, Greeting, "+" and Notification buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Avatar + Greeting
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    listOf(Color(0xFFFBE2D0), Color(0xFFF6C39B), Color(0xFFE89A7B))
                                )
                            )
                            .border(2.dp, Color.White, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = name.take(2).uppercase().ifBlank { "OR" },
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = Color(0xFF5D4037)
                        )
                    }

                    Column {
                        Text(
                            text = greeting,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = name.ifBlank { "Olivia Reed" },
                            fontSize = 13.sp,
                            color = TextSecondary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                // Top Right Action Buttons: Purple '+' and White Bell
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    // Purple circle "+" button
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .shadow(6.dp, CircleShape, ambientColor = AccentPurple, spotColor = AccentPurple)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    listOf(AccentPurpleLight, AccentPurple)
                                )
                            )
                            .clickable { onNavigateToCreate() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Add Task",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    // White circle Notification button
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .shadow(3.dp, CircleShape)
                            .clip(CircleShape)
                            .background(Color.White)
                            .clickable {
                                val current = vm.settings.value.notificationsEnabled
                                vm.setNotifications(!current)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Notifications,
                            contentDescription = "Notifications",
                            tint = TextPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // Big Modern Headline
            Text(
                text = "Let's Make\nToday Productive",
                fontSize = 32.sp,
                lineHeight = 38.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                modifier = Modifier.padding(vertical = 4.dp)
            )

            // Today's Progress Card (Image 1)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(10.dp, RoundedCornerShape(26.dp), spotColor = Color(0x1F7C5CFC)),
                shape = RoundedCornerShape(26.dp),
                colors = CardDefaults.cardColors(containerColor = CardBackground)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Today's Progress",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Left Column: Modern Progress Ring with dot marker
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            ModernProgressRing(
                                progress = progressPercent / 100f,
                                size = 118,
                                stroke = 14f,
                                percentageText = "$progressPercent%"
                            )
                        }

                        // Subtle vertical divider line
                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .height(88.dp)
                                .background(BorderLight)
                        )

                        // Right Column: 3 Metric rows with soft purple circle icons
                        Column(
                            modifier = Modifier
                                .weight(1.3f)
                                .padding(start = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            ProgressStatRow(
                                count = totalCount.toString(),
                                label = "Intentions",
                                icon = Icons.Outlined.Assignment
                            )
                            ProgressStatRow(
                                count = completedSessions.toString(),
                                label = "Completed",
                                icon = Icons.Outlined.CheckCircle
                            )
                            ProgressStatRow(
                                count = "${streak.currentStreak}d",
                                label = "Streak Days",
                                icon = Icons.Outlined.PendingActions
                            )
                        }
                    }
                }
            }

            // Today's Tasks Section Header + "View All"
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Today's Intentions",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Text(
                    text = "View All",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextSecondary,
                    modifier = Modifier.clickable { onNavigateToSchedule() }
                )
            }

            // Filter Pills Row (Horizontal Scroll)
            val filters = listOf(
                FilterItem(count = intents.size + if (active != null) 1 else 0, label = "To Do"),
                FilterItem(count = if (active != null) 1 else 0, label = "In Progress"),
                FilterItem(count = completedSessions, label = "Completed")
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                filters.forEachIndexed { index, filter ->
                    val isSelected = selectedFilterIndex == index
                    TaskFilterPill(
                        count = filter.count,
                        label = filter.label,
                        isSelected = isSelected,
                        onClick = { selectedFilterIndex = index }
                    )
                }
            }

            // Active Session Card (if any)
            if (active != null) {
                TaskCard(
                    priority = "High Priority",
                    priorityType = PriorityType.HIGH,
                    title = active.intent.targetLabel + " — " + active.intent.purpose,
                    timeRange = "Active · Planned ${active.intent.intendedMinutes} min (+${active.session.extendedMinutes}m)",
                    collaboratorCount = "+${active.session.reentries + 2}",
                    isActive = true,
                    onArrowClick = onNavigateToSchedule,
                    onActionClick = { vm.finishActiveSession() },
                    actionLabel = "End Session"
                )
            }

            // List of User Intentions formatted as modern Task Cards
            if (intents.isNotEmpty()) {
                intents.forEachIndexed { idx, intent ->
                    val (prioText, prioType) = when (intent.kind) {
                        IntentKind.QUICK_CHECK -> "Quick Check" to PriorityType.LOW
                        IntentKind.LONG_FORM -> "Deep Focus" to PriorityType.HIGH
                        IntentKind.TASK -> "Medium Priority" to PriorityType.MEDIUM
                        IntentKind.ENTERTAINMENT -> "Unwind" to PriorityType.LOW
                    }

                    TaskCard(
                        priority = prioText,
                        priorityType = prioType,
                        title = "${intent.targetLabel} — ${intent.purpose}",
                        timeRange = "Duration: ${intent.intendedMinutes} min",
                        collaboratorCount = "+${(idx % 3) + 1}",
                        isActive = false,
                        onArrowClick = {
                            vm.startIntent(
                                purpose = intent.purpose,
                                minutes = intent.intendedMinutes,
                                kind = intent.kind,
                                targetPackage = intent.targetPackage,
                                targetLabel = intent.targetLabel
                            )
                        },
                        onActionClick = {
                            vm.deleteIntent(intent.id)
                        },
                        actionLabel = "Delete"
                    )
                }
            } else if (active == null) {
                // Sample placeholder card matching the image if no tasks exist yet
                TaskCard(
                    priority = "High Priority",
                    priorityType = PriorityType.HIGH,
                    title = "Start your first focus session",
                    timeRange = "e.g. 25 min of focused work",
                    collaboratorCount = "+4",
                    isActive = false,
                    onArrowClick = onNavigateToCreate,
                    onActionClick = onNavigateToCreate,
                    actionLabel = "Start focus"
                )
            }

            Spacer(Modifier.height(80.dp)) // Space for floating bottom bar
        }
    }
}

private data class FilterItem(val count: Int, val label: String)

enum class PriorityType {
    LOW, MEDIUM, HIGH
}

@Composable
private fun ProgressStatRow(
    count: String,
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(Color(0xFFEFEAFC)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = AccentPurple,
                modifier = Modifier.size(17.dp)
            )
        }
        Column {
            Text(
                text = count,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = TextSecondary
            )
        }
    }
}

@Composable
private fun TaskFilterPill(
    count: Int,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val containerColor = if (isSelected) AccentLime else CardBackground
    val contentColor = if (isSelected) AccentLimeDark else TextPrimary
    val badgeBg = if (isSelected) Color.White.copy(alpha = 0.85f) else Color(0xFFF3F1F8)
    val badgeText = if (isSelected) AccentLimeDark else TextPrimary

    Row(
        modifier = Modifier
            .shadow(if (isSelected) 3.dp else 1.dp, CircleShape)
            .clip(CircleShape)
            .background(containerColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(badgeBg),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = count.toString(),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = badgeText
            )
        }
        Text(
            text = label,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = contentColor
        )
    }
}

@Composable
fun TaskCard(
    priority: String,
    priorityType: PriorityType,
    title: String,
    timeRange: String,
    collaboratorCount: String,
    isActive: Boolean = false,
    onArrowClick: () -> Unit = {},
    onActionClick: () -> Unit = {},
    actionLabel: String = ""
) {
    val (badgeBg, badgeText, badgeBorder) = when (priorityType) {
        PriorityType.LOW -> Triple(PriorityLowBg, PriorityLowText, PriorityLowBorder)
        PriorityType.MEDIUM -> Triple(PriorityMedBg, PriorityMedText, PriorityMedBorder)
        PriorityType.HIGH -> Triple(PriorityHighBg, PriorityHighText, PriorityHighBorder)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(6.dp, RoundedCornerShape(24.dp), spotColor = Color(0x14000000)),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = CardBackground)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Priority Tag & Diagonal Arrow Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Priority pill
                Row(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(badgeBg)
                        .border(1.dp, badgeBorder, CircleShape)
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(badgeText)
                    )
                    Text(
                        text = priority,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = badgeText
                    )
                }

                // Subtle circular arrow button
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFF6F5FB))
                        .clickable { onArrowClick() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.NorthEast,
                        contentDescription = "Open",
                        tint = TextPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            // Title
            Text(
                text = title,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )

            // Bottom Info: Time range & Collaborators / Action
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.AccessTime,
                        contentDescription = "Time",
                        tint = TextSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = timeRange,
                        fontSize = 12.sp,
                        color = TextSecondary,
                        fontWeight = FontWeight.Medium
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (actionLabel.isNotBlank()) {
                        Text(
                            text = actionLabel,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isActive) AccentPurple else TextSecondary,
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(if (isActive) Color(0xFFEEEBFA) else Color(0xFFF3F1F8))
                                .clickable { onActionClick() }
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }

                    // Avatar pile (Image 1 bottom right)
                    AvatarPile(extraCount = collaboratorCount)
                }
            }
        }
    }
}

@Composable
fun AvatarPile(extraCount: String = "+4") {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(start = 4.dp)
    ) {
        val colors = listOf(Color(0xFFE2849F), Color(0xFF6C98F8))
        colors.forEachIndexed { i, color ->
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(color)
                    .border(1.5.dp, Color.White, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (i == 0) "A" else "J",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(Color(0xFF1E1B2E))
                .border(1.5.dp, Color.White, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = extraCount,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
    }
}

