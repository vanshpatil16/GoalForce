package app.drift.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.drift.ui.DriftViewModel
import app.drift.ui.theme.AccentPurple
import app.drift.ui.theme.PastelBackgroundGradient
import app.drift.ui.theme.TextPrimary
import app.drift.ui.theme.TextSecondary

/**
 * Profile hub allowing easy switching between Progress Stats, Rewards, and Settings.
 */
@Composable
fun ProfileHubScreen(vm: DriftViewModel, modifier: Modifier = Modifier) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Progress", "Attention paths", "Rewards", "Settings")

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(PastelBackgroundGradient)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Segmented pill bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                tabs.forEachIndexed { index, tab ->
                    val isSelected = selectedTab == index
                    Box(
                        modifier = Modifier
                            .shadow(if (isSelected) 3.dp else 1.dp, CircleShape)
                            .clip(CircleShape)
                            .background(if (isSelected) AccentPurple else Color.White)
                            .clickable { selectedTab = index }
                            .padding(horizontal = 18.dp, vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = tab,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isSelected) Color.White else TextPrimary
                        )
                    }
                }
            }

            // Screen content
            when (selectedTab) {
                0 -> StatsScreen(vm)
                1 -> AttentionPathsScreen(vm)
                2 -> RewardsScreen(vm)
                else -> SettingsScreen(vm)
            }
        }
    }
}
