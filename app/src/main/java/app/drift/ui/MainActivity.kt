package app.drift.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.viewmodel.compose.viewModel
import app.drift.ui.screens.HomeScreen
import app.drift.ui.screens.CameraLandmarkScreen
import app.drift.ui.screens.IntentionScreen
import app.drift.ui.screens.SettingsScreen
import app.drift.ui.screens.StatsScreen
import app.drift.ui.theme.DriftTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            DriftTheme {
                val vm: DriftViewModel = viewModel(factory = DriftViewModel.Factory)
                DriftApp(vm)
            }
        }
    }
}

private data class Tab(val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab("Intent", Icons.Outlined.Bolt),
    Tab("Today", Icons.Outlined.Home),
    Tab("Progress", Icons.Outlined.Insights),
    Tab("Camera", Icons.Outlined.CameraAlt),
    Tab("Settings", Icons.Outlined.Settings),
)

@Composable
private fun DriftApp(vm: DriftViewModel) {
    var selected by rememberSaveable { mutableIntStateOf(0) }

    Scaffold(
        bottomBar = {
            NavigationBar {
                tabs.forEachIndexed { index, tab ->
                    NavigationBarItem(
                        selected = selected == index,
                        onClick = { selected = index },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) },
                    )
                }
            }
        }
    ) { padding ->
        val modifier = Modifier.padding(padding)
        when (selected) {
            0 -> IntentionScreen(vm, modifier)
            1 -> HomeScreen(vm, modifier)
            2 -> StatsScreen(vm, modifier)
            3 -> CameraLandmarkScreen(modifier)
            else -> SettingsScreen(vm, modifier)
        }
    }
}
