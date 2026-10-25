package app.drift.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF3D5AFE),
    onPrimary = Color.White,
    secondary = Color(0xFF7C9EFC),
    tertiary = Color(0xFF00BFA5),
    background = Color(0xFFFAFBFF),
    surface = Color.White,
    surfaceVariant = Color(0xFFEFF1FB),
    onSurfaceVariant = Color(0xFF5B6478),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8C9EFF),
    onPrimary = Color(0xFF00124F),
    secondary = Color(0xFF7C9EFC),
    tertiary = Color(0xFF00BFA5),
    background = Color(0xFF111318),
    surface = Color(0xFF191B22),
    surfaceVariant = Color(0xFF23262F),
)

@Composable
fun DriftTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
