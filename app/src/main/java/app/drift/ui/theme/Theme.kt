package app.drift.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// Modern Pastel Design Tokens matching reference images
val AccentPurple = Color(0xFF7C5CFC)
val AccentPurpleLight = Color(0xFF8E70FF)
val AccentPurpleDark = Color(0xFF5B3DE0)
val AccentLime = Color(0xFFC8E067)
val AccentLimeDark = Color(0xFF283618)
val AccentBlue = Color(0xFF3B82F6)

val CardBackground = Color(0xFFFFFFFF)
val TextPrimary = Color(0xFF191824)
val TextSecondary = Color(0xFF7E7C8E)
val TextMuted = Color(0xFFA5A3B3)
val BorderLight = Color(0xFFECEAF4)
val ChipBackground = Color(0xFFF3F1F8)

// Priority Badges
val PriorityLowBg = Color(0xFFE6F7ED)
val PriorityLowText = Color(0xFF2E7D32)
val PriorityLowBorder = Color(0xFFA3E2B8)

val PriorityMedBg = Color(0xFFFEF2E4)
val PriorityMedText = Color(0xFFE67E22)
val PriorityMedBorder = Color(0xFFFFCC80)

val PriorityHighBg = Color(0xFFFDE8E8)
val PriorityHighText = Color(0xFFE53935)
val PriorityHighBorder = Color(0xFFEF9A9A)

// Multi-stop pastel background gradient
val PastelBackgroundGradient = Brush.verticalGradient(
    colors = listOf(
        Color(0xFFEDE9FE), // Soft Lavender top
        Color(0xFFFFF0E6), // Subtle Peach glow
        Color(0xFFF7F5FC), // Clean porcelain
        Color(0xFFEFF6FF), // Soft Sky bottom
    )
)

val AmbientBackgroundBrush = Brush.linearGradient(
    colors = listOf(
        Color(0xFFEDE8FC),
        Color(0xFFFDF1EB),
        Color(0xFFF6F5FC),
    ),
    start = Offset(0f, 0f),
    end = Offset(1000f, 1000f)
)

private val LightColors = lightColorScheme(
    primary = AccentPurple,
    onPrimary = Color.White,
    secondary = AccentLime,
    onSecondary = AccentLimeDark,
    tertiary = AccentBlue,
    background = Color(0xFFF7F5FC),
    surface = CardBackground,
    surfaceVariant = Color(0xFFF3F1F8),
    onSurfaceVariant = TextSecondary,
    outline = BorderLight,
)

private val DarkColors = darkColorScheme(
    primary = AccentPurpleLight,
    onPrimary = Color.White,
    secondary = AccentLime,
    tertiary = AccentBlue,
    background = Color(0xFF13121C),
    surface = Color(0xFF1E1C2B),
    surfaceVariant = Color(0xFF272438),
    onSurfaceVariant = Color(0xFFB5B2C7),
    outline = Color(0xFF36324D),
)

@Composable
fun AmbientBackground(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(PastelBackgroundGradient)
    ) {
        content()
    }
}

@Composable
fun DriftTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}

