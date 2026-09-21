package com.example.vehicare.ui.components

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.vehicare.domain.diagnostic.models.SafetyAlert
import com.example.vehicare.domain.diagnostic.models.SafetyLevel
import com.example.vehicare.ui.theme.CriticalRed
import com.example.vehicare.ui.theme.CriticalRedContainer
import com.example.vehicare.ui.theme.Dimens
import com.example.vehicare.ui.theme.WarningAmber
import com.example.vehicare.ui.theme.WarningAmberContainer

/** Indeterminate loading state (no fake percentages anywhere in the app). */
@Composable
fun LoadingState(modifier: Modifier = Modifier, message: String? = null) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            if (message != null) {
                Spacer(Modifier.height(Dimens.SpaceLg))
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** Skeleton placeholder used while lists load from Room. */
@Composable
fun SkeletonCard(modifier: Modifier = Modifier, height: androidx.compose.ui.unit.Dp = 92.dp) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surfaceVariant)
    )
}

@Composable
fun SkeletonList(rows: Int = 3, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(Dimens.ScreenPadding),
        verticalArrangement = Arrangement.spacedBy(Dimens.SpaceMd)
    ) {
        repeat(rows) { SkeletonCard() }
    }
}

/** Empty state with a required, working action (no dead ends, Section 10). */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    actionText: String? = null,
    onAction: (() -> Unit)? = null
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(Dimens.SpaceXxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(52.dp)
        )
        Spacer(Modifier.height(Dimens.SpaceLg))
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(Dimens.SpaceSm))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        if (actionText != null && onAction != null) {
            Spacer(Modifier.height(Dimens.SpaceXl))
            PrimaryButton(text = actionText, onClick = onAction)
        }
    }
}

/** Error state with a Retry action (Section 10: database/load failure). */
@Composable
fun ErrorState(
    title: String,
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(Dimens.SpaceXxl),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.ErrorOutline,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(52.dp)
        )
        Spacer(Modifier.height(Dimens.SpaceLg))
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(Dimens.SpaceSm))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(Dimens.SpaceXl))
        PrimaryButton(text = "Retry", onClick = onRetry)
    }
}

/**
 * Safety alert card (Section 7). Shown at the top of results and inside reports. It is rendered
 * from a [SafetyAlert] produced by the SafetyEvaluator, so it never depends on ranking, and it
 * always pairs colour with an icon and explicit wording.
 */
@Composable
fun SafetyAlertCard(alert: SafetyAlert, modifier: Modifier = Modifier) {
    val urgent = alert.level == SafetyLevel.URGENT
    val container = if (urgent) CriticalRedContainer else WarningAmberContainer
    val content = if (urgent) CriticalRed else WarningAmber

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(container)
            .padding(Dimens.SpaceLg)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSm)
        ) {
            Icon(
                imageVector = if (urgent) Icons.Default.ReportProblem else Icons.Default.Warning,
                contentDescription = null,
                tint = content
            )
            Text(
                text = alert.title,
                style = MaterialTheme.typography.titleSmall,
                color = content
            )
        }
        Spacer(Modifier.height(Dimens.SpaceSm))
        Text(
            text = alert.message,
            style = MaterialTheme.typography.bodyMedium,
            color = content
        )
    }
}

/**
 * The standard probability disclaimer. Used in results, reports, PDF exports and Terms.
 * Never claim certainty, never present probability as severity or urgency.
 */
const val PRELIMINARY_DISCLAIMER =
    "This is a probability estimate based on the available symptom data, not a confirmed diagnosis."

const val REPORT_DISCLAIMER =
    "VehiCare AI provides preliminary probability-based vehicle health assessments using reported " +
        "symptoms and a diagnostic knowledge base. Results are not a confirmed mechanical diagnosis. " +
        "Always consult a qualified automotive professional for verification, especially when " +
        "safety-related symptoms are present."

const val RELATIVE_ESTIMATE_NOTE =
    "Percentages are the model's estimates among the possibilities it considered, so they are " +
        "relative rather than absolute confidence."

@Composable
fun DisclaimerNote(modifier: Modifier = Modifier, text: String = PRELIMINARY_DISCLAIMER) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(Dimens.SpaceMd),
        horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSm)
    ) {
        Icon(
            imageVector = Icons.Default.Warning,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** Subtle safety banner used on the Home dashboard (Section 5.3). */
@Composable
fun SafetyBanner(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(WarningAmberContainer)
            .padding(Dimens.SpaceMd),
        horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Warning,
            contentDescription = null,
            tint = WarningAmber,
            modifier = Modifier.size(18.dp)
        )
        Text(
            text = "Notice a serious vehicle problem? Stop driving if necessary and consult a qualified mechanic.",
            style = MaterialTheme.typography.bodySmall,
            color = WarningAmber
        )
    }
}
