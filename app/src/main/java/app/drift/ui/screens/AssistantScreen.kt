package app.drift.ui.screens

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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.drift.ui.DriftViewModel
import app.drift.ui.theme.AccentPurple
import app.drift.ui.theme.BorderLight
import app.drift.ui.theme.CardBackground
import app.drift.ui.theme.PastelBackgroundGradient
import app.drift.ui.theme.TextMuted
import app.drift.ui.theme.TextPrimary
import app.drift.ui.theme.TextSecondary
import kotlinx.coroutines.launch

private data class ChatMsg(val text: String, val isUser: Boolean)

/**
 * Assistant workspace for chat, session coaching, saved memory and planning.
 */
@Composable
fun AssistantScreen(
    vm: DriftViewModel,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
) {
    val name = vm.profileName.collectAsStateWithLifecycle().value
    val brief = vm.dailyBrief.collectAsStateWithLifecycle().value

    val greeting = if (brief.isBlank()) {
        "Hi ${name.ifBlank { "you" }}! I'm your focus coach. Set an intention and I'll help you spot the patterns that pull you off track."
    } else {
        "Hi ${name.ifBlank { "you" }}! Here's your day so far:\n\n$brief"
    }

    var selectedTab by remember { mutableIntStateOf(0) }
    var inputText by remember { mutableStateOf("") }
    var panelContent by remember { mutableStateOf("") }
    var panelLoading by remember { mutableStateOf(false) }
    val messages = remember { mutableStateListOf<ChatMsg>() }
    val scope = rememberCoroutineScope()

    val featureTabs = listOf(
        TabChipData(icon = Icons.Outlined.ChatBubbleOutline, label = ""),
        TabChipData(icon = Icons.Outlined.SmartToy, label = "Coach"),
        TabChipData(icon = Icons.Outlined.Description, label = "Memory"),
        TabChipData(icon = Icons.Outlined.Insights, label = "Plan")
    )

    LaunchedEffect(selectedTab) {
        panelContent = ""
        if (selectedTab == 0) return@LaunchedEffect
        panelLoading = true
        panelContent = try {
            when (selectedTab) {
                1 -> vm.coachLastSession()
                2 -> vm.assistantMemoryMarkdown().ifBlank {
                    "# What Drift remembers about you\n\nNothing saved yet. Chat with Drift about your goals or preferences, for example: “I want to spend less time scrolling before bed.”"
                }
                else -> vm.createFocusPlan()
            }
        } catch (_: Exception) {
            "I couldn't load this section. Please try again."
        }
        panelLoading = false
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
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Top Bar: Back circle, 'Get Plus ✓' badge, Share circle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Back Circle
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

                // Centered Title (replaces the 'Get Plus' upsell)
                Text(
                    text = "Assistant",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )

                // Share Circle
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .shadow(3.dp, CircleShape)
                        .clip(CircleShape)
                        .background(Color.White),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "Share",
                        tint = TextPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Feature Tabs (Horizontal Scroll)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                featureTabs.forEachIndexed { index, tab ->
                    val isSelected = selectedTab == index
                    Row(
                        modifier = Modifier
                            .shadow(if (isSelected) 3.dp else 1.dp, CircleShape)
                            .clip(CircleShape)
                            .background(if (isSelected) Color.White else Color(0x70FFFFFF))
                            .border(1.dp, if (isSelected) BorderLight else Color.Transparent, CircleShape)
                            .clickable { selectedTab = index }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = tab.icon,
                            contentDescription = tab.label,
                            tint = if (isSelected) AccentPurple else TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                        if (tab.label.isNotBlank()) {
                            Text(
                                text = tab.label,
                                fontSize = 13.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) TextPrimary else TextSecondary
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(4.dp))

            // AI presence: animated pearl orb
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                IridescentPearlOrb(modifier = Modifier.size(84.dp))
            }

            Spacer(Modifier.height(4.dp))

            if (selectedTab == 0) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(4.dp, RoundedCornerShape(22.dp)),
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = CardBackground)
                ) {
                    Text(
                        text = greeting,
                        modifier = Modifier.padding(18.dp),
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        color = TextPrimary,
                    )
                }

                messages.forEach { msg ->
                    if (msg.isUser) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(AccentPurple)
                                    .padding(horizontal = 18.dp, vertical = 12.dp)
                            ) {
                                Text(text = msg.text, color = Color.White, fontSize = 14.sp)
                            }
                        }
                    } else {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = CardBackground),
                        ) {
                            Text(msg.text, Modifier.padding(16.dp), color = TextPrimary, fontSize = 14.sp)
                        }
                    }
                }
            } else {
                Card(
                    modifier = Modifier.fillMaxWidth().shadow(4.dp, RoundedCornerShape(22.dp)),
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = CardBackground),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            text = listOf("", "Coach", "Memory", "Plan")[selectedTab],
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                        )
                        Text(
                            text = if (panelLoading) "Loading your saved context…" else panelContent,
                            fontSize = 14.sp,
                            lineHeight = 21.sp,
                            color = TextPrimary,
                        )
                    }
                }
            }

            Spacer(Modifier.height(if (selectedTab == 0) 150.dp else 32.dp))
        }

        // Bottom Floating Input Bar matching Image 5
        if (selectedTab == 0) {
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = 20.dp, end = 20.dp, bottom = 88.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .shadow(12.dp, RoundedCornerShape(28.dp), spotColor = Color(0x18000000))
                    .clip(RoundedCornerShape(28.dp))
                    .background(Color.White)
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Circular '+' button
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFF3F1F8))
                        .clickable { },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Attach",
                        tint = TextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Text Input Field
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (inputText.isEmpty()) {
                        Text(
                            text = "Ask anything...",
                            fontSize = 14.sp,
                            color = TextMuted
                        )
                    }
                    BasicTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        textStyle = TextStyle(
                            fontSize = 14.sp,
                            color = TextPrimary
                        ),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Circular Microphone / Send button
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(if (inputText.isNotBlank()) AccentPurple else Color(0xFFF3F1F8))
                        .clickable {
                            if (inputText.isNotBlank()) {
                                val question = inputText
                                messages.add(ChatMsg(text = question, isUser = true))
                                inputText = ""
                                scope.launch {
                                    messages.add(ChatMsg(text = vm.askAssistant(question), isUser = false))
                                }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (inputText.isNotBlank()) Icons.AutoMirrored.Outlined.Send else Icons.Default.Mic,
                        contentDescription = "Voice",
                        tint = if (inputText.isNotBlank()) Color.White else AccentPurple,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
        }
    }
}

private data class TabChipData(val icon: ImageVector, val label: String)
