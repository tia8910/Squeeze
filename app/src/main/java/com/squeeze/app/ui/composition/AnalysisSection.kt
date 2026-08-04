package com.squeeze.app.ui.composition

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.squeeze.app.ui.components.BrandCard
import com.squeeze.app.ui.theme.Brand
import com.squeeze.app.ui.theme.LocalIsDarkTheme
import com.squeeze.core.bodycomp.BandPosition
import com.squeeze.core.bodycomp.CompositionPanel
import com.squeeze.core.bodycomp.Confidence
import com.squeeze.core.bodycomp.Metric
import com.squeeze.core.bodycomp.ReferenceBand
import kotlin.math.abs

/**
 * Everything the app can derive from the latest measurement.
 *
 * Three groups rather than one list, because they answer different questions: composition is
 * what you are made of, shape is how it is distributed, and energy is what it costs to run.
 * Fourteen numbers in a single column is a data dump; three answers is a report.
 *
 * Every figure carries its confidence. This panel deliberately mixes things that are almost
 * directly measured — a waist-to-hip ratio, where both sides come from one photo at one
 * scale so the scale error divides out — with things that are three inferences deep, like a
 * muscle mass estimate from uncorrected girths. Presenting those at equal visual weight
 * would be the same dishonesty as printing a body fat percentage with no interval.
 *
 * Collapsed by default. The hero number answers the question most people opened the app to
 * ask; this is for the ones who want to interrogate it.
 */
@Composable
fun AnalysisSection(panel: CompositionPanel, modifier: Modifier = Modifier) {
    if (panel.isEmpty && panel.missing.isEmpty()) return

    var expanded by remember { mutableStateOf(false) }
    val dark = LocalIsDarkTheme.current
    val muted = if (dark) Brand.DarkMuted else Brand.Muted

    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .clickable { expanded = !expanded }
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Full analysis",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = if (expanded) "Hide" else "Show",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Icon(
                imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = if (expanded) "Collapse analysis" else "Expand analysis",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
        }

        AnimatedVisibility(visible = expanded) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricGroup("Composition", panel.composition)
                MetricGroup("Shape", panel.shape)
                MetricGroup("Energy", panel.energy)

                if (panel.missing.isNotEmpty()) {
                    BrandCard(Modifier.fillMaxWidth()) {
                        Text(
                            text = "Add to unlock more",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        panel.missing.forEach { gap ->
                            Text(
                                text = "${gap.input} → ${gap.unlocks}",
                                style = MaterialTheme.typography.bodySmall,
                                color = muted,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricGroup(title: String, metrics: List<Metric>) {
    if (metrics.isEmpty()) return

    val dark = LocalIsDarkTheme.current
    val muted = if (dark) Brand.DarkMuted else Brand.Muted

    BrandCard(Modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = muted,
        )

        metrics.forEachIndexed { index, metric ->
            Column(Modifier.padding(top = if (index == 0) 12.dp else 18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = metric.name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = format(metric),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }

                Row(
                    modifier = Modifier.padding(top = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    metric.band?.let { BandTag(it) }
                    ConfidenceTag(metric.confidence)
                }

                metric.band?.let { band ->
                    Text(
                        text = band.detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = muted,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }

                Text(
                    text = metric.detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = muted,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
    }
}

/**
 * Formats a value at a precision its accuracy can support.
 *
 * A resting energy figure printed to one decimal would imply a precision the equation does
 * not have; a ratio printed as a whole number would hide the change the user is looking for.
 */
private fun format(metric: Metric): String {
    val text = when {
        metric.unit == "kcal/day" -> "%.0f".format(metric.value)
        abs(metric.value) >= 100 -> "%.0f".format(metric.value)
        abs(metric.value) >= 10 -> "%.1f".format(metric.value)
        else -> "%.2f".format(metric.value)
    }
    return if (metric.unit.isEmpty()) text else "$text ${metric.unit}"
}

/**
 * Where the reading sits against the population.
 *
 * Colour comes from what the metric means rather than from the enum, because the direction
 * is not the judgement: a high fat-free mass index is a good outcome and a high
 * waist-to-height is not. Both are [BandPosition.HIGH].
 */
@Composable
private fun BandTag(band: ReferenceBand) {
    val dark = LocalIsDarkTheme.current
    val colour = when (band.position) {
        BandPosition.NORMAL -> if (dark) Brand.DarkBlue else Brand.BlueDeep
        BandPosition.LOW, BandPosition.HIGH -> Brand.Warning
    }

    Box(
        Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(colour.copy(alpha = 0.14f))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(
            text = band.label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = colour,
        )
    }
}

@Composable
private fun ConfidenceTag(confidence: Confidence) {
    val dark = LocalIsDarkTheme.current

    val (label, colour) = when (confidence) {
        Confidence.DIRECT -> "Measured" to (if (dark) Brand.DarkBlue else Brand.BlueDeep)
        Confidence.ESTIMATED -> "Estimated" to (if (dark) Brand.DarkMuted else Brand.Muted)
        Confidence.ROUGH -> "Rough" to Brand.Warning
    }

    Box(
        Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(colour.copy(alpha = 0.12f))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = colour,
        )
    }
}
