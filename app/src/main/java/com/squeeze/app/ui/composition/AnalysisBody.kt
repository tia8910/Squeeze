package com.squeeze.app.ui.composition

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.squeeze.app.ui.components.BrandCard
import com.squeeze.app.ui.theme.Brand
import com.squeeze.app.ui.theme.LocalIsDarkTheme
import com.squeeze.core.bodycomp.BandPosition
import com.squeeze.core.bodycomp.CompositionPanel
import com.squeeze.core.bodycomp.Confidence
import com.squeeze.core.bodycomp.Metric
import com.squeeze.core.bodycomp.ReferenceBand
import com.squeeze.core.bodycomp.formatted

/**
 * Everything the app can derive from one measurement.
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
 * This lives inside a record rather than on the dashboard, and that placement is the point.
 * On the dashboard it described a body assembled from whichever entry last carried each
 * field — a waist from Tuesday, a weight from Sunday — which is the right way to answer
 * "where am I now" and the wrong thing to call an analysis, because no such body was ever
 * measured. Attached to a record it describes one real session, and every figure in it can
 * be traced to the numbers shown directly above.
 */
@Composable
fun AnalysisBody(panel: CompositionPanel, modifier: Modifier = Modifier) {
    if (panel.isEmpty && panel.missing.isEmpty()) return

    val dark = LocalIsDarkTheme.current
    val muted = if (dark) Brand.DarkMuted else Brand.Muted

    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
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

@Composable
private fun MetricGroup(title: String, metrics: List<Metric>) {
    if (metrics.isEmpty()) return

    val dark = LocalIsDarkTheme.current
    val muted = if (dark) Brand.DarkMuted else Brand.Muted

    BrandCard(Modifier.fillMaxWidth()) {
        // The group name as an eyebrow rather than a label in the same grey as the body
        // copy underneath it. These three groups answer different questions — what am I made
        // of, how is it distributed, what does it cost to run — and a heading that does not
        // outrank its contents leaves the reader to discover that by reading all of it.
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.2.sp,
            color = if (dark) Brand.DarkBlue else Brand.Blue,
        )

        metrics.forEachIndexed { index, metric ->
            // A hairline between readings. Spacing alone left one long column in which a
            // band caption belonging to the metric above sat closer to the metric below it,
            // and the eye had to re-establish where each figure started.
            if (index > 0) {
                HorizontalDivider(
                    modifier = Modifier.padding(top = 16.dp),
                    color = if (dark) Brand.DarkLine else Brand.Line,
                )
            }

            Column(Modifier.padding(top = if (index == 0) 12.dp else 16.dp)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = metric.name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = metric.formatted(),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
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
