package com.squeeze.app.ui.scan

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.squeeze.core.program.BlockOverview
import com.squeeze.core.program.MuscleAssessment
import com.squeeze.core.program.MuscleProfile
import com.squeeze.core.program.MuscleProfiler
import com.squeeze.core.scan.Development
import kotlin.math.roundToInt

/**
 * Ten muscles, each with its verdict, how sure it is, and every piece of evidence behind it.
 *
 * Built so a reader can check any line: the score, the band of uncertainty drawn around it,
 * and the lift, girth or photo reading that produced it. A verdict with nothing to check is
 * the kind the app used to give from one photograph.
 */
@Composable
fun MuscleBreakdown(profile: MuscleProfile, hiddenNote: String?) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Muscle by muscle", style = MaterialTheme.typography.titleSmall)
        Text(
            "Lifts, proportions and photos, each weighted by how reliable it is. The shaded band is the range the score could be in.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        profile.assessments.forEachIndexed { i, a ->
            if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            MuscleRow(a)
        }

        if (profile.unassessed.isNotEmpty()) {
            Text(
                "Not assessed yet: " + profile.unassessed.joinToString("; ") {
                    "${BlockOverview.label(it).lowercase()} (${MuscleProfiler.toAssess(it)})"
                } + ".",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        hiddenNote?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Text("Strengths", style = MaterialTheme.typography.titleSmall)
        if (profile.strengths.isEmpty()) {
            Text("None stands out yet. That's normal early on, and it's what training builds.", style = MaterialTheme.typography.bodyMedium)
        }
        profile.strengths.forEach { group ->
            val a = profile.assessments.first { it.group == group }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("✓ ${a.label}", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                Text(
                    "${(a.score * 100).roundToInt()}/100 · ${a.evidence.first().text}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Text("Weak points for your goal", style = MaterialTheme.typography.titleSmall)
        if (profile.weaknesses.isEmpty()) {
            Text("Nothing is lagging for this goal. Keep every muscle progressing.", style = MaterialTheme.typography.bodyMedium)
        }
        profile.weaknesses.forEachIndexed { index, focus ->
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    "${index + 1}. ${BlockOverview.label(focus.group)}",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(focus.why, style = MaterialTheme.typography.bodyMedium)
                Text(focus.how, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun MuscleRow(a: MuscleAssessment) {
    val tone = when (a.development) {
        Development.DEVELOPED -> MaterialTheme.colorScheme.primary
        Development.AVERAGE -> MaterialTheme.colorScheme.onSurfaceVariant
        Development.LAGGING -> MaterialTheme.colorScheme.error
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(a.label, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Text(
                "${(a.score * 100).roundToInt()}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                "/100",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 2.dp),
            )
        }
        ScoreBar(a.score.toFloat(), a.sd.toFloat(), tone)
        Text(
            "${a.development.label} · ${a.confidence.label}",
            style = MaterialTheme.typography.labelSmall,
            color = tone,
        )
        a.evidence.forEach { e ->
            Text(
                "${e.source.label} · ${e.text}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        a.note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }
    }
}

/** The score as a bar, with its uncertainty as a lighter band either side of the tip. */
@Composable
private fun ScoreBar(score: Float, sd: Float, tone: Color) {
    val track = MaterialTheme.colorScheme.surfaceContainerHigh
    Canvas(Modifier.fillMaxWidth().height(10.dp)) {
        val r = CornerRadius(size.height / 2)
        drawRoundRect(track, cornerRadius = r)
        val lo = ((score - 1.96f * sd).coerceIn(0f, 1f)) * size.width
        val hi = ((score + 1.96f * sd).coerceIn(0f, 1f)) * size.width
        drawRoundRect(tone.copy(alpha = 0.18f), topLeft = Offset(lo, 0f), size = Size(hi - lo, size.height), cornerRadius = r)
        drawRoundRect(tone, size = Size(score.coerceIn(0f, 1f) * size.width, size.height), cornerRadius = r)
    }
}
