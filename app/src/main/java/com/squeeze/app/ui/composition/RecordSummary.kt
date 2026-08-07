package com.squeeze.app.ui.composition

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import com.squeeze.core.bodycomp.HeadlineFigure
import com.squeeze.core.model.Sex
import com.squeeze.core.render.BodyFigure
import com.squeeze.core.render.ReferencePhysique

/**
 * The top of a record: what the session concluded, over a body drawn from it.
 *
 * The two halves belong together and are drawn as one block for that reason. A body-fat
 * percentage is an assertion about a body, and a column of centimetres is the one form in
 * which a two-centimetre change at the waist is invisible. Putting the figures directly above
 * a figure built from those same figures makes the comparison the default rather than an
 * effort — and makes a broken scan obvious, because a wrong waist draws a body the user knows
 * is not theirs.
 *
 * Everything here is also printed below in full, with its confidence and its interval. This
 * block is a summary and is deliberately silent about uncertainty; the cards underneath are
 * where a figure is allowed to argue for itself.
 */
@Composable
fun RecordSummary(
    figures: List<HeadlineFigure>,
    views: List<BodyFigure>,
    estimatedSites: List<String>,
    reference: ReferencePhysiqueMatch?,
    modifier: Modifier = Modifier,
) {
    if (figures.isEmpty() && views.isEmpty()) return

    val dark = LocalIsDarkTheme.current
    val muted = if (dark) Brand.DarkMuted else Brand.Muted

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))) {
            if (figures.isNotEmpty()) HeadlineStrip(figures)

            // The photograph when the set has shipped, the drawing when it has not. Never
            // both: they answer the same question and stacking them would leave the reader
            // deciding which of two bodies is the answer.
            when {
                reference != null -> Image(
                    painter = painterResource(reference.drawableId),
                    contentDescription = "A reference body at about " +
                        "${reference.bandPercent} per cent body fat",
                    contentScale = ContentScale.FillWidth,
                    modifier = Modifier.fillMaxWidth(),
                )

                views.isNotEmpty() -> FigureRow(views)
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
        } else if (views.isNotEmpty()) {
            Text(
                text = caption(estimatedSites),
                style = MaterialTheme.typography.bodySmall,
                color = muted,
            )
        }
    }
}

/** A shipped reference photograph, already resolved to a drawable. */
data class ReferencePhysiqueMatch(val drawableId: Int, val bandPercent: Int)

/**
 * Resolves the reference photograph for an estimate, or null when the set is not in this
 * build.
 *
 * Looked up by name so [com.squeeze.core.render.ReferencePhysique] can stay in the
 * platform-free module, and so that a build shipping no photographs degrades to the drawn
 * figure rather than failing to compile.
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
 * What the drawing is, and what in it was not measured.
 *
 * Stated every time rather than hidden behind a tap. A drawing is far more persuasive than a
 * table — someone who would question a chest circumference will accept a chest they can see —
 * so the figure has to say plainly which parts of it came from this record and which came
 * from a population average.
 */
private fun caption(estimatedSites: List<String>): String {
    val base = "Drawn from this record's own measurements. Proportion is real; the likeness " +
        "is not — two people with these measurements are drawn the same."
    if (estimatedSites.isEmpty()) return base
    return "$base Not measured here, so taken from population averages: " +
        estimatedSites.joinToString(", ") { it.lowercase() } + "."
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
 * The views, side by side, all at one scale.
 *
 * Front and back share an outline, because a girth is a loop and nothing in a record says how
 * it divides between the two halves. They are both shown anyway: the profile is what changes
 * most visibly as the waist moves, and a row of three reads as a body where a single figure
 * reads as an icon.
 */
@Composable
private fun FigureRow(views: List<BodyFigure>) {
    val dark = LocalIsDarkTheme.current

    Row(
        Modifier
            .fillMaxWidth()
            .background(if (dark) Brand.DarkSunken else Brand.Sunken),
        horizontalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        views.forEach { view ->
            Box(
                Modifier
                    .weight(1f)
                    .aspectRatio(FIGURE_PANEL_ASPECT),
            ) {
                BodyFigureView(
                    figure = view,
                    fill = if (dark) Brand.DarkBlue else Brand.BlueDeep,
                    // Drawn onto the filled body, so it has to lift off it rather than
                    // contrast with the panel behind.
                    line = if (dark) Brand.DarkGround.copy(alpha = 0.55f)
                    else Color.White.copy(alpha = 0.65f),
                )
                Text(
                    text = view.view.label,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (dark) Brand.DarkMuted else Brand.Muted,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                )
            }
        }
    }
}

/**
 * The frame each view is drawn in.
 *
 * Tall and narrow, because a standing body is: a squarer panel would leave the figure small
 * in the middle of it, and the whole value of the drawing is being able to see the waist.
 */
private const val FIGURE_PANEL_ASPECT = 0.56f
