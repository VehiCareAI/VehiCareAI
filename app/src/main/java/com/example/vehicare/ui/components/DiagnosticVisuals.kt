package com.example.vehicare.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.example.vehicare.domain.diagnostic.models.Severity
import com.example.vehicare.ui.theme.CriticalRed
import com.example.vehicare.ui.theme.Dimens
import com.example.vehicare.ui.theme.HealthyGreen
import com.example.vehicare.ui.theme.InfoBlue
import com.example.vehicare.ui.theme.Navy700
import com.example.vehicare.ui.theme.Teal600
import com.example.vehicare.ui.theme.WarningAmber

/**
 * Probability is always shown as a number **and** a graphic (Section 3), and the graphic is always
 * accompanied by the plain-language note that the value is a relative estimate among the
 * possibilities considered (Section 6.2).
 */
@Composable
fun ProbabilityBar(
    probability: Double,
    modifier: Modifier = Modifier,
    tint: Color = Navy700,
    showLabel: Boolean = true
) {
    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(Dimens.ProgressBarHeight)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(probability.coerceIn(0.0, 1.0).toFloat())
                        .height(Dimens.ProgressBarHeight)
                        .clip(CircleShape)
                        .background(tint)
                )
            }
            if (showLabel) {
                Spacer(Modifier.size(Dimens.SpaceSm))
                Text(
                    text = formatPercent(probability),
                    style = MaterialTheme.typography.titleSmall,
                    color = tint
                )
            }
        }
    }
}

/** Ring visualisation of a posterior estimate (Canvas only, no chart dependency). */
@Composable
fun ProbabilityRing(
    probability: Double,
    modifier: Modifier = Modifier,
    label: String = "estimate",
    size: androidx.compose.ui.unit.Dp = 132.dp
) {
    val track = MaterialTheme.colorScheme.surfaceVariant
    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(size)) {
            val stroke = 14.dp.toPx()
            val inset = stroke / 2f
            drawArc(
                color = track,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = Size(this.size.width - stroke, this.size.height - stroke),
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
            drawArc(
                color = Teal600,
                startAngle = -90f,
                sweepAngle = (360.0 * probability.coerceIn(0.0, 1.0)).toFloat(),
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = Size(this.size.width - stroke, this.size.height - stroke),
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = formatPercent(probability),
                style = com.example.vehicare.ui.theme.ProbabilityNumber,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** Severity shown separately from probability: filled segments plus its written level. */
@Composable
fun SeverityMeter(severity: Severity, modifier: Modifier = Modifier) {
    val color = severityColor(severity)
    Column(modifier = modifier) {
        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceXs)) {
            repeat(4) { index ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(Dimens.SpaceSm)
                        .clip(CircleShape)
                        .background(if (index < severity.rank) color else MaterialTheme.colorScheme.surfaceVariant)
                )
            }
        }
        Spacer(Modifier.height(Dimens.SpaceXs))
        Text(
            text = "Severity: ${severity.label}",
            style = MaterialTheme.typography.labelMedium,
            color = color
        )
    }
}

/** Simple horizontal bar chart (Canvas) used for counts over time and per-system frequencies. */
@Composable
fun CountBarChart(
    entries: List<Pair<String, Int>>,
    modifier: Modifier = Modifier,
    barColor: Color = InfoBlue,
    emptyMessage: String = "No data yet"
) {
    if (entries.isEmpty() || entries.all { it.second == 0 }) {
        ChartPlaceholder(message = emptyMessage, modifier = modifier)
        return
    }
    Column(modifier = modifier) {
        entries.forEach { (label, value) ->
            val max = entries.maxOf { it.second }.coerceAtLeast(1)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = Dimens.SpaceXs),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1.1f)
                )
                Box(
                    modifier = Modifier
                        .weight(2f)
                        .height(14.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(value.toFloat() / max.toFloat())
                            .height(14.dp)
                            .clip(CircleShape)
                            .background(barColor)
                    )
                }
                Spacer(Modifier.size(Dimens.SpaceSm))
                Text(text = value.toString(), style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

/** Line chart of a probability series over time (Canvas). */
@Composable
fun ProbabilityTrendChart(
    points: List<Double>,
    modifier: Modifier = Modifier,
    emptyMessage: String = "Not enough assessments yet"
) {
    if (points.size < 2) {
        ChartPlaceholder(message = emptyMessage, modifier = modifier)
        return
    }
    val lineColor = Navy700
    val axisColor = MaterialTheme.colorScheme.outlineVariant
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(Dimens.ChartHeight)
    ) {
        val padding = 8.dp.toPx()
        val usableHeight = size.height - padding * 2
        val stepX = (size.width - padding * 2) / (points.size - 1)

        drawLine(
            color = axisColor,
            start = Offset(padding, size.height - padding),
            end = Offset(size.width - padding, size.height - padding),
            strokeWidth = 1.dp.toPx()
        )

        points.forEachIndexed { index, value ->
            val x = padding + stepX * index
            val y = padding + usableHeight * (1f - value.coerceIn(0.0, 1.0).toFloat())
            if (index > 0) {
                val previous = points[index - 1]
                val previousX = padding + stepX * (index - 1)
                val previousY = padding + usableHeight * (1f - previous.coerceIn(0.0, 1.0).toFloat())
                drawLine(
                    color = lineColor,
                    start = Offset(previousX, previousY),
                    end = Offset(x, y),
                    strokeWidth = 3.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }
            drawCircle(color = Teal600, radius = 5.dp.toPx(), center = Offset(x, y))
        }
    }
}

@Composable
private fun ChartPlaceholder(message: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(Dimens.ChartHeightSmall)
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

fun severityColor(severity: Severity): Color = when (severity) {
    Severity.LOW -> HealthyGreen
    Severity.MEDIUM -> InfoBlue
    Severity.HIGH -> WarningAmber
    Severity.CRITICAL -> CriticalRed
}

/**
 * Percentage formatting lives in utils/Formats so the UI, the PDF export and tests all print the
 * same string. Kept here as a thin alias for component call sites.
 */
fun formatPercent(value: Double): String = com.example.vehicare.utils.Formats.percent(value)
