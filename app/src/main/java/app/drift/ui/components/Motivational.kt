package app.drift.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.drift.ui.theme.AccentLime
import app.drift.ui.theme.AccentPurple
import app.drift.ui.theme.AccentPurpleLight
import kotlin.math.cos
import kotlin.math.sin

/** A gently pulsing streak flame. */
@Composable
fun FlameIcon(modifier: Modifier = Modifier, size: Int = 48) {
    val transition = rememberInfiniteTransition(label = "flame")
    val scale by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "flameScale",
    )
    Box(modifier = modifier.size(size.dp), contentAlignment = Alignment.Center) {
        Text("🔥", fontSize = (size * 0.6f).sp, modifier = Modifier.scale(scale))
    }
}

/**
 * Modern circular progress ring matching Image 1 (Today's Progress).
 * Features smooth purple gradient arc with a bright chartreuse/lime indicator dot.
 */
@Composable
fun ModernProgressRing(
    progress: Float,
    modifier: Modifier = Modifier,
    size: Int = 110,
    stroke: Float = 12f,
    percentageText: String = "${(progress * 100).toInt()}%",
) {
    val clampedProgress = progress.coerceIn(0.01f, 1f)
    val trackColor = Color(0xFFEEEBFA)

    Box(modifier = modifier.size(size.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size.dp)) {
            val strokePx = stroke * density
            val inset = strokePx / 2
            val arcSize = Size(this.size.width - strokePx, this.size.height - strokePx)
            val centerOffset = Offset(this.size.width / 2, this.size.height / 2)
            val radius = (this.size.width - strokePx) / 2

            // Background track
            drawArc(
                color = trackColor,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = strokePx, cap = StrokeCap.Round),
            )

            // Progress Arc
            val sweep = 360f * clampedProgress
            drawArc(
                brush = Brush.sweepGradient(
                    colors = listOf(
                        AccentPurpleLight,
                        AccentPurple,
                        Color(0xFF9873FE),
                        AccentPurpleLight,
                    ),
                    center = centerOffset
                ),
                startAngle = -90f,
                sweepAngle = sweep,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = strokePx, cap = StrokeCap.Round),
            )

            // Lime indicator dot at the progress tip
            val angleRad = Math.toRadians((-90.0 + sweep))
            val dotX = centerOffset.x + radius * cos(angleRad).toFloat()
            val dotY = centerOffset.y + radius * sin(angleRad).toFloat()

            // Outer white ring for dot
            drawCircle(
                color = Color.White,
                radius = (strokePx * 0.7f),
                center = Offset(dotX, dotY),
            )
            // Inner lime dot
            drawCircle(
                color = AccentLime,
                radius = (strokePx * 0.5f),
                center = Offset(dotX, dotY),
            )
        }

        Text(
            text = percentageText,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.ExtraBold,
            color = AccentPurple,
        )
    }
}

/** Legacy support */
@Composable
fun ProgressRing(
    progress: Float,
    modifier: Modifier = Modifier,
    size: Int = 96,
    stroke: Float = 10f,
    label: String = "",
) {
    ModernProgressRing(
        progress = progress,
        modifier = modifier,
        size = size,
        stroke = stroke,
        percentageText = label,
    )
}

