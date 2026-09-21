package com.example.vehicare.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Dangerous
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PriorityHigh
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.example.vehicare.domain.diagnostic.models.Severity
import com.example.vehicare.domain.diagnostic.models.SafetyLevel
import com.example.vehicare.ui.theme.CriticalRed
import com.example.vehicare.ui.theme.CriticalRedContainer
import com.example.vehicare.ui.theme.Dimens
import com.example.vehicare.ui.theme.HealthyGreen
import com.example.vehicare.ui.theme.HealthyGreenContainer
import com.example.vehicare.ui.theme.InfoBlue
import com.example.vehicare.ui.theme.InfoBlueContainer
import com.example.vehicare.ui.theme.WarningAmber
import com.example.vehicare.ui.theme.WarningAmberContainer

/** Tone of a chip. Tone is always paired with a label and usually an icon (Section 3). */
enum class ChipTone { NEUTRAL, HEALTHY, WARNING, CRITICAL, INFO }

private data class ToneColors(val container: Color, val content: Color)

@Composable
private fun toneColors(tone: ChipTone): ToneColors = when (tone) {
    ChipTone.NEUTRAL -> ToneColors(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)
    ChipTone.HEALTHY -> ToneColors(HealthyGreenContainer, HealthyGreen)
    ChipTone.WARNING -> ToneColors(WarningAmberContainer, WarningAmber)
    ChipTone.CRITICAL -> ToneColors(CriticalRedContainer, CriticalRed)
    ChipTone.INFO -> ToneColors(InfoBlueContainer, InfoBlue)
}

@Composable
fun StatusChip(
    label: String,
    tone: ChipTone = ChipTone.NEUTRAL,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null
) {
    val colors = toneColors(tone)
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(colors.container)
            .padding(horizontal = Dimens.SpaceMd, vertical = Dimens.SpaceXs + 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceXs)
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = colors.content, modifier = Modifier.size(14.dp))
        }
        Text(label, style = MaterialTheme.typography.labelMedium, color = colors.content)
    }
}

/**
 * Severity badge for a *possible issue*. Severity is independent of probability, so it always
 * carries its own icon and wording rather than reusing the probability visual.
 */
@Composable
fun SeverityBadge(severity: Severity, modifier: Modifier = Modifier) {
    val (tone, icon) = when (severity) {
        Severity.LOW -> ChipTone.HEALTHY to Icons.Default.CheckCircle
        Severity.MEDIUM -> ChipTone.INFO to Icons.Default.Info
        Severity.HIGH -> ChipTone.WARNING to Icons.Default.Warning
        Severity.CRITICAL -> ChipTone.CRITICAL to Icons.Default.Dangerous
    }
    StatusChip(label = "Severity: ${severity.label}", tone = tone, icon = icon, modifier = modifier)
}

/** Safety alert badge: urgent alerts are visually distinct and never colour-only. */
@Composable
fun SafetyBadge(level: SafetyLevel, modifier: Modifier = Modifier) {
    when (level) {
        SafetyLevel.URGENT -> StatusChip(
            label = "Safety alert",
            tone = ChipTone.CRITICAL,
            icon = Icons.Default.ReportProblem,
            modifier = modifier
        )

        SafetyLevel.ADVISORY -> StatusChip(
            label = "Safety advisory",
            tone = ChipTone.WARNING,
            icon = Icons.Default.PriorityHigh,
            modifier = modifier
        )
    }
}

/** Badge for "preliminary assessment" wording required by Section 5.11. */
@Composable
fun PreliminaryBadge(modifier: Modifier = Modifier) {
    StatusChip(
        label = "Preliminary assessment",
        tone = ChipTone.INFO,
        icon = Icons.Default.Shield,
        modifier = modifier
    )
}

/** Small chip marking seeded demonstration content (Section 9). */
@Composable
fun SampleDataBadge(modifier: Modifier = Modifier) {
    StatusChip(label = "Sample data", tone = ChipTone.NEUTRAL, icon = Icons.Default.Info, modifier = modifier)
}

/** Wording that maps a posterior estimate to plain language, always with the number shown. */
@Composable
fun LikelihoodLabel(probability: Double, modifier: Modifier = Modifier) {
    val tone = when {
        probability >= 0.6 -> ChipTone.WARNING
        probability >= 0.35 -> ChipTone.INFO
        else -> ChipTone.NEUTRAL
    }
    StatusChip(label = likelihoodText(probability), tone = tone, modifier = modifier)
}

fun likelihoodText(probability: Double): String = when {
    probability >= 0.7 -> "Strongest estimate"
    probability >= 0.5 -> "Leading estimate"
    probability >= 0.3 -> "Possible"
    probability >= 0.15 -> "Less likely"
    else -> "Unlikely"
}
