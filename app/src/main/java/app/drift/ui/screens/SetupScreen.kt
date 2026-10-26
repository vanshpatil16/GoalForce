package app.drift.ui.screens

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import app.drift.ui.DriftViewModel
import app.drift.ui.theme.AccentPurple
import app.drift.ui.theme.BorderLight
import app.drift.ui.theme.CardBackground
import app.drift.ui.theme.PastelBackgroundGradient
import app.drift.ui.theme.PriorityLowBg
import app.drift.ui.theme.PriorityLowText
import app.drift.ui.theme.TextMuted
import app.drift.ui.theme.TextPrimary
import app.drift.ui.theme.TextSecondary
import app.drift.util.Permissions

/**
 * Welcome & Onboarding screen matching Image 4.
 * Features an iridescent metallic pearl orb, modern typography,
 * "Get Started" CTA, and smooth permission activation.
 */
@Composable
fun SetupScreen(vm: DriftViewModel, onDone: () -> Unit) {
    val context = LocalContext.current
    var showPermissions by remember { mutableStateOf(false) }
    var refresh by remember { mutableIntStateOf(0) }

    LifecycleResumeEffect(Unit) {
        refresh++
        onPauseOrDispose { }
    }

    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        refresh++
    }

    val usage = remember(refresh) { Permissions.hasUsageAccess(context) }
    val a11y = remember(refresh) { Permissions.isAccessibilityEnabled(context) }
    val notif = remember(refresh) { Permissions.hasNotificationPermission(context) }
    val battery = remember(refresh) { Permissions.isIgnoringBatteryOptimizations(context) }
    val overlay = remember(refresh) { Permissions.hasOverlayPermission(context) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PastelBackgroundGradient)
    ) {
        if (!showPermissions) {
            // Screen 4: Welcome / Splash view
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 28.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Top status spacer
                Spacer(Modifier.height(20.dp))

                // Centerpiece: Iridescent pearl orb
                IridescentPearlOrb(modifier = Modifier.size(240.dp))

                // Bottom Content
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.Start,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // App Badge: Icon + TaskPilot / Drift
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.8f))
                            .border(1.dp, BorderLight, CircleShape)
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Verified,
                            contentDescription = null,
                            tint = AccentPurple,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "TaskPilot · Drift AI",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    }

                    // Headline
                    Text(
                        text = "Everyday Partner,\nYour AI Agent",
                        fontSize = 32.sp,
                        lineHeight = 38.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )

                    // Subtitle
                    Text(
                        text = "Plan, create, and get things done smarter with your personal AI agent.",
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        color = TextSecondary
                    )

                    Spacer(Modifier.height(10.dp))

                    // "Get Started" Pill Button
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .shadow(8.dp, RoundedCornerShape(28.dp), spotColor = Color(0x18000000))
                            .clip(RoundedCornerShape(28.dp))
                            .background(Color.White)
                            .clickable { showPermissions = true }
                            .padding(horizontal = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Get Started",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    }

                    // "Already have an account? Sign In"
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "Already have an account? ",
                            fontSize = 13.sp,
                            color = TextSecondary
                        )
                        Text(
                            text = "Sign In",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            modifier = Modifier.clickable {
                                vm.completeOnboarding()
                                onDone()
                            }
                        )
                    }
                }
            }
        } else {
            // Permissions setup flow styled with matching sleek aesthetic
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 22.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Spacer(Modifier.height(8.dp))

                Text(
                    "Set up Drift Agent",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Text(
                    "To intervene gently and protect your focus on-device, Drift needs a few system permissions.",
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    color = TextSecondary
                )

                Spacer(Modifier.height(4.dp))

                ModernPermissionCard(
                    title = "Accessibility service",
                    description = "Detects app switches and offers mindful pauses before infinite scroll triggers.",
                    granted = a11y,
                    action = "Enable",
                    onAction = { context.startActivity(Permissions.accessibilityIntent()); refresh++ }
                )

                ModernPermissionCard(
                    title = "Usage access",
                    description = "Gathers on-device app focus patterns to compute your daily progress.",
                    granted = usage,
                    action = "Enable",
                    onAction = { context.startActivity(Permissions.usageAccessIntent()); refresh++ }
                )

                ModernPermissionCard(
                    title = "Notifications",
                    description = "Delivers smart plan summaries and streak updates at your ideal hour.",
                    granted = notif,
                    action = "Allow",
                    onAction = { notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }
                )

                ModernPermissionCard(
                    title = "Overlay permission",
                    description = "Displays the breathing pause cleanly over distracting feeds.",
                    granted = overlay,
                    action = "Enable",
                    onAction = { context.startActivity(Permissions.overlaySettingsIntent(context)); refresh++ }
                )

                ModernPermissionCard(
                    title = "Battery optimization",
                    description = "Ensures background timer and intervention triggers stay reliable.",
                    granted = battery,
                    action = "Disable",
                    onAction = { context.startActivity(Permissions.batteryOptimizationIntent(context)); refresh++ }
                )

                Spacer(Modifier.height(8.dp))

                // Continue CTA Button
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .shadow(10.dp, RoundedCornerShape(28.dp), ambientColor = AccentPurple, spotColor = AccentPurple)
                        .clip(RoundedCornerShape(28.dp))
                        .background(AccentPurple)
                        .clickable {
                            vm.completeOnboarding()
                            onDone()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Continue to App",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

/**
 * 3D Holographic Pearl Orb matching Image 4
 */
@Composable
fun IridescentPearlOrb(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "orbPulse")
    val floatOffset by infiniteTransition.animateFloat(
        initialValue = -10f,
        targetValue = 10f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "orbFloat"
    )
    val pulse by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.07f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "orbPulse"
    )
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(14000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "orbRotation"
    )

    Box(
        modifier = modifier.graphicsLayer {
            scaleX = pulse
            scaleY = pulse
            rotationZ = rotation
        },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2, (size.height / 2) + floatOffset)
            val radius = size.minDimension * 0.44f

            // Ambient background glow
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFFE8DBFF),
                        Color(0xFFFDE5F1),
                        Color(0x00FFFFFF)
                    ),
                    center = center,
                    radius = radius * 1.35f
                ),
                radius = radius * 1.35f,
                center = center
            )

            // Outer pearl base
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFFFFFFFF),
                        Color(0xFFEFE9FE),
                        Color(0xFFD6C8FB),
                        Color(0xFFC0AFFE),
                        Color(0xFF9C87EB)
                    ),
                    center = Offset(center.x - radius * 0.35f, center.y - radius * 0.35f),
                    radius = radius * 1.15f
                ),
                radius = radius,
                center = center
            )

            // Iridescent cyan/pink metallic swirls
            drawCircle(
                brush = Brush.sweepGradient(
                    colors = listOf(
                        Color(0x80B3E5FC), // soft cyan
                        Color(0x80F8BBD0), // soft rose
                        Color(0x80E1BEE7), // soft lavender
                        Color(0x80FFF9C4), // warm pearl
                        Color(0x80B3E5FC)
                    ),
                    center = center
                ),
                radius = radius * 0.95f,
                center = center
            )

            // Highlight shine / specular glow
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFFFFFFFF),
                        Color(0x60FFFFFF),
                        Color(0x00FFFFFF)
                    ),
                    center = Offset(center.x - radius * 0.42f, center.y - radius * 0.42f),
                    radius = radius * 0.55f
                ),
                radius = radius * 0.55f,
                center = Offset(center.x - radius * 0.42f, center.y - radius * 0.42f)
            )
        }
    }
}

@Composable
private fun ModernPermissionCard(
    title: String,
    description: String,
    granted: Boolean,
    action: String,
    onAction: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(4.dp, RoundedCornerShape(22.dp)),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = CardBackground)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = description,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    color = TextSecondary
                )
            }

            Spacer(Modifier.width(12.dp))

            if (granted) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(PriorityLowBg),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Granted",
                        tint = PriorityLowText,
                        modifier = Modifier.size(20.dp)
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(AccentPurple)
                        .clickable(onClick = onAction)
                        .padding(horizontal = 16.dp, vertical = 9.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = action,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }
    }
}

