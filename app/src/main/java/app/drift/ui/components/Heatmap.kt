package app.drift.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.drift.domain.model.DailyProgress
import java.time.LocalDate

/**
 * A GitHub-style contribution heatmap built from *persisted daily progress* —
 * never fabricated. Colour intensity maps to qualifying minutes.
 */
@Composable
fun ContributionHeatmap(daily: List<DailyProgress>, weeks: Int = 12, modifier: Modifier = Modifier) {
    val byDate = daily.associateBy { it.date }
    val today = LocalDate.now()
    val weekStart = today.minusDays((today.dayOfWeek.value - 1).toLong())
    val start = weekStart.minusWeeks((weeks - 1).toLong())

    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
    ) {
        for (w in 0 until weeks) {
            Column {
                for (d in 0 until 7) {
                    val date = start.plusDays((w * 7 + d).toLong())
                    val minutes = byDate[date.toString()]?.qualifyingMinutes ?: 0
                    Box(
                        Modifier
                            .padding(2.dp)
                            .size(14.dp)
                            .background(heatColor(minutes), RoundedCornerShape(3.dp)),
                    )
                }
            }
            Spacer(Modifier.size(4.dp))
        }
    }
}

@Composable
private fun heatColor(minutes: Int): Color = when {
    minutes <= 0 -> MaterialTheme.colorScheme.surfaceVariant
    minutes < 20 -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.35f)
    minutes < 60 -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.7f)
    else -> MaterialTheme.colorScheme.tertiary
}
