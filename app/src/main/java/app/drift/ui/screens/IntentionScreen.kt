package app.drift.ui.screens

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.drift.domain.model.IntentKind
import app.drift.ui.DriftViewModel
import app.drift.ui.theme.AccentLime
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
fun IntentionScreen(
    vm: DriftViewModel,
    modifier: Modifier = Modifier,
    onSessionStarted: () -> Unit = {},
    onCancel: () -> Unit = onSessionStarted,
) {
    val context = LocalContext.current
    val active = vm.activeSession.collectAsStateWithLifecycle().value

    var taskTitle by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var minutes by remember { mutableIntStateOf(15) }
    var selectedPriority by remember { mutableStateOf(PriorityType.HIGH) }
    var target by remember { mutableStateOf<AppEntry?>(null) }
    var showPicker by remember { mutableStateOf(false) }
    var showDurationPicker by remember { mutableStateOf(false) }

    val tags = remember { mutableStateListOf("Focus", "Deep work", "Evening") }
    var showAddTagDialog by remember { mutableStateOf(false) }
    var newTagInput by remember { mutableStateOf("") }

    val saveAction = {
        val app = target ?: AppEntry("app.drift", "Drift Focus")
        val finalPurpose = taskTitle.ifBlank { description.ifBlank { "Deep Focus Session" } }
        val kind = when (selectedPriority) {
            PriorityType.LOW -> IntentKind.QUICK_CHECK
            PriorityType.MEDIUM -> IntentKind.TASK
            PriorityType.HIGH -> IntentKind.LONG_FORM
        }
        vm.startIntent(finalPurpose, minutes, kind, app.packageName, app.label)
        onSessionStarted()
    }

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
            // Top Bar: Circular Close 'X', Center Title 'Add New Task', Circular Check '✓'
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Circular 'X' button
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .shadow(3.dp, CircleShape)
                        .clip(CircleShape)
                        .background(Color.White)
                        .clickable { onCancel() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Cancel",
                        tint = TextPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Centered Title
                Text(
                    text = "New Focus Session",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )

                // Circular '✓' button
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .shadow(3.dp, CircleShape)
                        .clip(CircleShape)
                        .background(Color.White)
                        .clickable { saveAction() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Save",
                        tint = TextPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(Modifier.height(4.dp))

            // Field 1: Focus
            FormLabel("What are you focusing on?")
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(3.dp, RoundedCornerShape(20.dp), spotColor = Color(0x10000000))
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.White)
                    .padding(horizontal = 18.dp, vertical = 16.dp)
            ) {
                if (taskTitle.isEmpty()) {
                    Text(
                        "e.g. reply to DMs, finish a chapter",
                        color = TextMuted,
                        fontSize = 15.sp
                    )
                }
                BasicTextField(
                    value = taskTitle,
                    onValueChange = { taskTitle = it },
                    textStyle = TextStyle(
                        fontSize = 15.sp,
                        color = TextPrimary,
                        fontWeight = FontWeight.Medium
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Field 2: Description
            FormLabel("Description")
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(110.dp)
                    .shadow(3.dp, RoundedCornerShape(20.dp), spotColor = Color(0x10000000))
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.White)
                    .padding(horizontal = 18.dp, vertical = 16.dp)
            ) {
                if (description.isEmpty()) {
                    Text(
                        "e.g. catch up with friends without doomscrolling",
                        color = TextMuted,
                        fontSize = 14.sp,
                        lineHeight = 20.sp
                    )
                }
                BasicTextField(
                    value = description,
                    onValueChange = { description = it },
                    textStyle = TextStyle(
                        fontSize = 14.sp,
                        color = TextPrimary,
                        lineHeight = 20.sp
                    ),
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Field 3: Duration
            FormLabel("Duration")
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(3.dp, RoundedCornerShape(20.dp), spotColor = Color(0x10000000))
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.White)
                    .clickable { showDurationPicker = true }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.AccessTime,
                        contentDescription = "Duration",
                        tint = TextPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "$minutes min focus",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                }
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = "Change duration",
                    tint = TextSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }

            // Field 4: Priority
            FormLabel("Priority")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                PriorityPill(
                    label = "Low",
                    isSelected = selectedPriority == PriorityType.LOW,
                    bg = PriorityLowBg,
                    text = PriorityLowText,
                    border = PriorityLowBorder,
                    modifier = Modifier.weight(1f),
                    onClick = { selectedPriority = PriorityType.LOW }
                )
                PriorityPill(
                    label = "Medium",
                    isSelected = selectedPriority == PriorityType.MEDIUM,
                    bg = PriorityMedBg,
                    text = PriorityMedText,
                    border = PriorityMedBorder,
                    modifier = Modifier.weight(1f),
                    onClick = { selectedPriority = PriorityType.MEDIUM }
                )
                PriorityPill(
                    label = "High",
                    isSelected = selectedPriority == PriorityType.HIGH,
                    bg = PriorityHighBg,
                    text = PriorityHighText,
                    border = PriorityHighBorder,
                    modifier = Modifier.weight(1f),
                    onClick = { selectedPriority = PriorityType.HIGH }
                )
            }

            // Field 5: Target App
            FormLabel("Target app")
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(3.dp, RoundedCornerShape(20.dp), spotColor = Color(0x10000000))
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.White)
                    .clickable { showPicker = true }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Folder,
                        contentDescription = "Project",
                        tint = AccentPurple,
                        modifier = Modifier.size(22.dp)
                    )
                    Text(
                        text = target?.label ?: "Choose an app",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                }
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = "Select App",
                    tint = TextSecondary,
                    modifier = Modifier.size(22.dp)
                )
            }

            // Field 6: Tags
            FormLabel("Tags")
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                tags.forEach { tag ->
                    Row(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(Color(0xFFEDE9FE))
                            .clickable { tags.remove(tag) }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = tag,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = AccentPurple
                        )
                        Text(
                            text = "✕",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = AccentPurple
                        )
                    }
                }

                // "+ Add" Tag Button
                Row(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(Color.White)
                        .border(1.dp, BorderLight, CircleShape)
                        .clickable { showAddTagDialog = true }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "+ Add",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextSecondary
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // Bottom CTA Button: Rich Purple Gradient "Start Focus Session"
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .shadow(10.dp, RoundedCornerShape(28.dp), ambientColor = AccentPurple, spotColor = AccentPurple)
                    .clip(RoundedCornerShape(28.dp))
                    .background(
                        Brush.horizontalGradient(
                            listOf(Color(0xFF8B6BFF), AccentPurple)
                        )
                    )
                    .clickable { saveAction() },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Start Focus Session",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            Spacer(Modifier.height(40.dp))
        }
    }

    // App Picker Dialog
    if (showPicker) {
        AppPickerDialog(
            apps = remember { launchableApps(context) },
            onPick = { target = it; showPicker = false },
            onDismiss = { showPicker = false },
        )
    }

    // Duration Picker Dialog
    if (showDurationPicker) {
        AlertDialog(
            onDismissRequest = { showDurationPicker = false },
            title = { Text("Select Focus Duration", fontWeight = FontWeight.Bold) },
            text = {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(5, 10, 15, 25, 30, 45, 60, 90).forEach { m ->
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(if (minutes == m) AccentPurple else Color(0xFFF3F1F8))
                                .clickable { minutes = m; showDurationPicker = false }
                                .padding(horizontal = 16.dp, vertical = 10.dp)
                        ) {
                            Text(
                                text = "${m}m",
                                color = if (minutes == m) Color.White else TextPrimary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showDurationPicker = false }) { Text("Cancel") }
            }
        )
    }

    // Add Tag Dialog
    if (showAddTagDialog) {
        AlertDialog(
            onDismissRequest = { showAddTagDialog = false },
            title = { Text("Add Tag", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = newTagInput,
                    onValueChange = { newTagInput = it },
                    label = { Text("Tag Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (newTagInput.isNotBlank()) {
                        tags.add(newTagInput.trim())
                        newTagInput = ""
                    }
                    showAddTagDialog = false
                }) {
                    Text("Add", fontWeight = FontWeight.Bold, color = AccentPurple)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddTagDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun FormLabel(text: String) {
    Text(
        text = text,
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
        color = TextPrimary
    )
}

@Composable
private fun PriorityPill(
    label: String,
    isSelected: Boolean,
    bg: Color,
    text: Color,
    border: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .shadow(if (isSelected) 3.dp else 0.dp, RoundedCornerShape(20.dp))
            .clip(RoundedCornerShape(20.dp))
            .background(if (isSelected) bg else Color.White)
            .border(if (isSelected) 2.dp else 1.dp, if (isSelected) border else BorderLight, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
            color = if (isSelected) text else TextSecondary
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
        title = { Text("Choose Target App", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Search apps...") },
                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AccentPurple,
                        unfocusedBorderColor = BorderLight
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                LazyColumn(Modifier.height(300.dp)) {
                    items(filtered) { app ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onPick(app) }
                                .padding(horizontal = 12.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFEDE9FE)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = app.label.take(1).uppercase(),
                                    fontWeight = FontWeight.Bold,
                                    color = AccentPurple
                                )
                            }
                            Text(
                                text = app.label,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium,
                                color = TextPrimary
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

