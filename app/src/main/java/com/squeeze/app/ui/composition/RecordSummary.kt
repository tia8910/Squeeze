package com.squeeze.app.ui.composition

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.squeeze.app.ui.theme.Brand
import com.squeeze.app.ui.theme.LocalIsDarkTheme
import com.squeeze.core.bodycomp.BodyFinding
import com.squeeze.core.bodycomp.FindingKind
import com.squeeze.core.bodycomp.HeadlineFigure
import com.squeeze.core.model.Sex
import com.squeeze.core.render.ReferencePhysique

/**
 * The top of a record: what the session concluded, and what it means.
 *
 * This used to be the figures over a body drawn from them, and the drawing is gone. It was
 * built from four girths and a body fat percentage with everything else — arms, calves,
 * shoulders — filled in from population averages, so two people with the same waist were
 * drawn identically and the caption underneath had to admit it. It took the most valuable
 * space on the screen to show the reader what a body looks like.
 *
 * In its place is the question the drawing was standing in for: is this good news? A column of
 * centimetres does not say, and neither did a silhouette. Two short lists do.
 *
 * Everything here is also printed below in full, with its confidence and its interval. This
 * block is a summary and is deliberately silent about uncertainty; the cards underneath are
 * where a figure is allowed to argue for itself.
 */
@Composable
fun RecordSummary(
    figures: List<HeadlineFigure>,
    findings: List<BodyFinding>,
    reference: ReferencePhysiqueMatch?,
    modifier: Modifier = Modifier,
) {
    if (figures.isEmpty() && findings.isEmpty() && reference == null) return

    val dark = LocalIsDarkTheme.current
    val muted = if (dark) Brand.DarkMuted else Brand.Muted

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))) {
            if (figures.isNotEmpty()) HeadlineStrip(figures)

            reference?.let {
                Image(
                    painter = painterResource(it.drawableId),
                    contentDescription = "A reference body at about " +
                        "${it.bandPercent} per cent body fat",
                    contentScale = ContentScale.FillWidth,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        if (reference != null) {
            Text(
                text = "A reference body at about ${reference.bandPercent}% — not you, and " +
                    "not built from your measurements. It is here for one purpose: if this " +
                    "looks nothing like you, the scan is wrong.",
                style = MaterialTheme.typography.bodySmall,
                color = muted,
            )
        }

        if (findings.isNotEmpty()) FindingsBlock(findings)
    }
}

/** A shipped reference photograph, already resolved to a drawable. */
data class ReferencePhysiqueMatch(val drawableId: Int, val bandPercent: Int)

/**
 * Resolves the reference photograph for an estimate, or null when the set is not in this
 * build.
 *
 * Looked up by name so [com.squeeze.core.render.ReferencePhysique] can stay in the
 * platform-free module, and so that a build shipping no photographs simply omits the image
 * rather than failing to compile.
 */
@Composable
fun rememberReferencePhysique(percent: Double?, sex: Sex?): ReferencePhysiqueMatch? {
    val context = LocalContext.current
    return remember(percent, sex) {
        val subjectSex = sex ?: return@remember null
        val band = ReferencePhysique.bandFor(percent, subjectSex) ?: return@remember null
        val id = context.resources.getIdentifier(
            ReferencePhysique.assetName(subjectSex, band),
            "drawable",
            context.packageName,
        )
        if (id == 0) null else ReferencePhysiqueMatch(id, band)
    }
}

/**
 * The figures, across the top, on the brand navy.
 *
 * Laid out even-width when they fit and scrolled when they do not, rather than shrunk. Five
 * cells across a phone gives each about seventy density-independent pixels, which is not
 * enough for "Waist-to-height" at a legible size — so the layout admits it and scrolls
 * instead of ellipsising a label into meaninglessness. On a sparse record with two or three
 * figures the row fills the width and never scrolls.
 */
@Composable
private fun HeadlineStrip(figures: List<HeadlineFigure>) {
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .background(Brand.Navy),
    ) {
        val even = maxWidth / figures.size >= MIN_CELL_WIDTH

        if (even) {
            Row(Modifier.fillMaxWidth()) {
                figures.forEachIndexed { index, figure ->
                    if (index > 0) CellDivider()
                    HeadlineCell(figure, Modifier.weight(1f))
                }
            }
        } else {
            Row(Modifier.horizontalScroll(rememberScrollState())) {
                figures.forEachIndexed { index, figure ->
                    if (index > 0) CellDivider()
                    HeadlineCell(figure, Modifier.width(MIN_CELL_WIDTH))
                }
            }
        }
    }
}

/** How narrow a headline cell may get before the strip scrolls instead. */
private val MIN_CELL_WIDTH = 108.dp

@Composable
private fun HeadlineCell(figure: HeadlineFigure, modifier: Modifier = Modifier) {
    Column(
        modifier.padding(horizontal = 8.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = figure.label,
            style = MaterialTheme.typography.labelMedium,
            color = Brand.OutlineBlue,
            textAlign = TextAlign.Center,
        )
        Text(
            text = figure.value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun CellDivider() {
    Box(
        Modifier
            .width(1.dp)
            .height(52.dp)
            .background(Color.White.copy(alpha = 0.18f)),
    )
}

/**
 * Strengths, then weak points.
 *
 * Two headed lists rather than one mixed list with coloured dots. A reader scanning a mixed
 * list has to decode each row before knowing which pile it belongs to, and the whole value of
 * this block is being readable in the two seconds before the reader starts on the cards below.
 *
 * Nothing is padded. A record that supports one strength and no weak points shows exactly
 * that — an empty column would invite the reader to wonder what was being withheld, and a
 * manufactured finding would be worse than either.
 */
@Composable
private fun FindingsBlock(findings: List<BodyFinding>) {
    val dark = LocalIsDarkTheme.current

    val strengths = findings.filter { it.kind == FindingKind.STRENGTH }
    val weaknesses = findings.filter { it.kind == FindingKind.WEAKNESS }

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(if (dark) Brand.DarkSunken else Brand.Sunken)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (strengths.isNotEmpty()) FindingGroup("Strengths", strengths, Brand.Success)
        if (weaknesses.isNotEmpty()) FindingGroup("Weak points", weaknesses, Brand.Warning)
    }
}

@Composable
private fun FindingGroup(heading: String, findings: List<BodyFinding>, accent: Color) {
    val dark = LocalIsDarkTheme.current

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = heading.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = accent,
        )

        findings.forEach { finding ->
            // IntrinsicSize.Min so the row measures its tallest child first, which is what
            // lets the bar below fill the finding's real height rather than a guessed one.
            Row(
                Modifier.height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // A bar rather than a dot: it grows with the text, so a three-line finding
                // still reads as one item and the eye can follow the column down the list.
                Box(
                    Modifier
                        .width(3.dp)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(2.dp))
                        .background(accent.copy(alpha = 0.55f)),
                )
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = finding.title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (dark) Brand.DarkInk else Brand.Navy,
                    )
                    Text(
                        text = finding.detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (dark) Brand.DarkMuted else Brand.Muted,
                    )
                }
            }
        }
    }
}
