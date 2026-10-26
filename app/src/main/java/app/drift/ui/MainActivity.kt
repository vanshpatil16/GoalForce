package app.drift.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.drift.ui.screens.ActiveSessionScreen
import app.drift.ui.screens.AssistantScreen
import app.drift.ui.screens.HomeScreen
import app.drift.ui.screens.IntentionScreen
import app.drift.ui.screens.ProfileHubScreen
import app.drift.ui.screens.SetupScreen
import app.drift.ui.theme.AccentPurple
import app.drift.ui.theme.DriftTheme
import app.drift.ui.theme.PastelBackgroundGradient
import app.drift.ui.theme.TextSecondary

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DriftTheme {
                val vm: DriftViewModel = viewModel(factory = DriftViewModel.Factory)
                DriftApp(vm)
            }
        }
    }
}
private data class NavTab(val label: String, val icon: ImageVector)

private val tabs = listOf(
    NavTab("Home", Icons.Outlined.Home),
    NavTab("Focus", Icons.Outlined.Flag),
    NavTab("Session", Icons.Outlined.Timer),
    NavTab("Assistant", Icons.Outlined.AutoAwesome),
    NavTab("Profile", Icons.Outlined.Person),
)

@Composable
private fun DriftApp(vm: DriftViewModel) {
    val onboardingComplete = vm.settings.collectAsStateWithLifecycle().value.onboardingComplete
    var setupDone by rememberSaveable { mutableStateOf(false) }

    // Screen 4: Welcome & Onboarding with 3D Iridescent Orb
    if (!onboardingComplete && !setupDone) {
        SetupScreen(vm, onDone = { setupDone = true })
        return
    }

    var selectedIndex by rememberSaveable { mutableIntStateOf(0) }

    Box(modifier = Modifier.fillMaxSize().background(PastelBackgroundGradient)) {
        // Screen Body with smooth crossfade
        AnimatedContent(
            targetState = selectedIndex,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "ScreenTransition",
            modifier = Modifier.fillMaxSize().statusBarsPadding()
        ) { targetIndex ->
            when (targetIndex) {
                0 -> HomeScreen(
                    vm = vm,
                    onNavigateToCreate = { selectedIndex = 1 },
                    onNavigateToSchedule = { selectedIndex = 2 }
                )
                1 -> IntentionScreen(
                    vm = vm,
                    onSessionStarted = { selectedIndex = 2 },
                    onCancel = { selectedIndex = 0 }
                )
                2 -> ActiveSessionScreen(
                    vm = vm,
                    onBack = { selectedIndex = 0 },
                    onNavigateToCreate = { selectedIndex = 1 }
                )
                3 -> AssistantScreen(
                    vm = vm,
                    onBack = { selectedIndex = 0 },
                )
                else -> ProfileHubScreen(vm = vm)
            }
        }

        // Floating Pill Bottom Navigation Bar (Image 1 & Image 3)
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 14.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(68.dp)
                    .shadow(
                        elevation = 14.dp,
                        shape = RoundedCornerShape(34.dp),
                        spotColor = Color(0x247C5CFC)
                    )
                    .clip(RoundedCornerShape(34.dp))
                    .background(Color.White)
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                tabs.forEachIndexed { index, tab ->
                    val isSelected = selectedIndex == index

                    if (isSelected) {
                        // Expanded active purple pill container
                        Row(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(AccentPurple)
                                .clickable { selectedIndex = index }
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = tab.icon,
                                contentDescription = tab.label,
                                tint = Color.White,
                                modifier = Modifier.size(19.dp)
                            )
                            Text(
                                text = tab.label,
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    } else {
                        // Unselected icon + label
                        Column(
                            modifier = Modifier
                                .clip(CircleShape)
                                .clickable { selectedIndex = index }
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Icon(
                                imageVector = tab.icon,
                                contentDescription = tab.label,
                                tint = TextSecondary,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = tab.label,
                                color = TextSecondary,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }
    }
}
