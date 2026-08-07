package com.squeeze.app.scan

import com.google.mediapipe.framework.image.ByteBufferExtractor
import com.google.mediapipe.framework.image.MPImage
import com.squeeze.core.scan.TrunkBounds
import com.squeeze.core.scan.WidthProfile
import java.nio.ByteBuffer

/** A horizontal stretch of subject pixels in one row. */
private data class Run(val start: Int, val end: Int) {
    val width: Int get() = end - start + 1
    val centre: Int get() = (start + end) / 2
}

/**
 * Reduces a segmentation mask to per-row torso and leg widths.
 *
 * The naive reading of a mask row — leftmost subject pixel to rightmost — is wrong for a
 * human body, and wrong in a way that looks like data. A front-on person at chest height is
 * three separate runs: arm, torso, arm. Measuring the full span there returns the
 * shoulder-and-arms width, which is close to twice a chest diameter and yields a chest
 * circumference no human has. At thigh height the same span covers both legs and the air
 * between them.
 *
 * So each row is decomposed into runs, and two widths are taken from it:
 *
 *  - **torso**: the run containing the body's vertical midline, so arms are excluded
 *    whenever a gap separates them from the trunk
 *  - **leg**: the widest run that is not the torso run, on either side of the midline,
 *    which below the hips is a single leg
 *
 * Interior holes still matter — segmenters routinely punch them through dark clothing — so
 * runs separated by less than [MAX_HOLE_PIXELS] are merged before anything is measured.
 */
object MaskWidthExtractor {

    /** Rows with fewer subject pixels than this are noise, not body. */
    private const val MIN_ROW_PIXELS = 3

    /** At least this fraction of rows must contain body for the mask to be usable. */
    private const val MIN_BODY_ROWS_FRACTION = 0.15

    /**
     * Gaps narrower than this are treated as mask defects and bridged.
     *
     * A hole in the middle of a torso would otherwise split it into two runs, and the
     * midline run would then be half a torso — a narrower, entirely believable, wrong waist.
     */
    private const val MAX_HOLE_PIXELS = 6

    /**
     * @param mask category mask, one byte per pixel
     * @param trunk pose-derived bound on where the trunk can be, used to cut an arm off a
     *   torso run when the two touch. Without it, a hand on a hip or an arm hanging against
     *   the waist is measured as part of the body — the largest error this pipeline can make
     *   and the one that still lands inside every plausibility range.
     * @return null when the mask is too sparse to represent a body
     */
    fun extract(
        mask: MPImage,
        width: Int,
        height: Int,
        trunk: TrunkBounds? = null,
    ): WidthProfile? {
        if (width <= 0 || height <= 0) return null

        val buffer = ByteBufferExtractor.extract(mask)

        // Which byte value means "person" is decided from the image, not assumed. The
        // corners of a framed full-body photo are background almost by definition, so
        // whichever value dominates them is background and the other is the subject.
        // Guessing wrong would trace the background as a full-frame body.
        val subjectIsNonZero = cornersAreMostlyZero(buffer, width, height)

        val rowRuns = arrayOfNulls<List<Run>>(height)
        var topRow = -1
        var bottomRow = -1
        var populatedRows = 0

        for (row in 0 until height) {
            val runs = runsInRow(buffer, row, width, subjectIsNonZero)
            val pixels = runs.sumOf { it.width }
            if (pixels < MIN_ROW_PIXELS) continue

            rowRuns[row] = runs
            if (topRow < 0) topRow = row
            bottomRow = row
            populatedRows++
        }

        if (topRow < 0 || bottomRow <= topRow) return null
        if (populatedRows < height * MIN_BODY_ROWS_FRACTION) return null

        // The midline is taken from the shoulders-to-hips band rather than the whole body,
        // because arms and stance skew a whole-body centroid sideways.
        val midlineX = estimateMidline(rowRuns, topRow, bottomRow, width)

        val torsoWidths = DoubleArray(height)
        val legWidths = DoubleArray(height)
        val clippedRows = BooleanArray(height)

        for (row in topRow..bottomRow) {
            val runs = rowRuns[row] ?: continue

            val torso = runs.minByOrNull { distanceToRun(it, midlineX) }
            if (torso != null) {
                // Where an arm touches the trunk the two are a single run, so the run is
                // cut back to where the skeleton says the trunk can reach. When no pose
                // geometry is available the raw run is used, which is the previous
                // behaviour rather than a failure.
                val clipped = trunk?.clipRun(
                    startX = torso.start.toDouble() / width.toDouble(),
                    endX = (torso.end + 1).toDouble() / width.toDouble(),
                    row = row,
                )

                // Where the bound cut only one side, the other edge is still the skin, and
                // doubling that clean half about the trunk's centre recovers the width from
                // the side that was never in doubt. Where it cut both, there is nothing left
                // to measure and the row is marked unusable.
                //
                // Refusing outright was the previous behaviour and it was too strict by half:
                // a body standing with its arms down has one arm against it far more often
                // than two, and the scan returned no figure at all rather than the good half
                // of a good photograph.
                val recovered = clipped?.mirroredWidth()

                torsoWidths[row] = when {
                    trunk == null -> torso.width.toDouble() / width.toDouble()
                    recovered != null -> recovered
                    // Either the midline run fell entirely outside the trunk bound, or both
                    // of its edges were contaminated. Neither is a narrow torso, and
                    // recording a width here would invent one.
                    else -> 0.0
                }

                clippedRows[row] = clipped != null && recovered == null
            }

            runs.filter { it !== torso }
                .maxByOrNull { it.width }
                ?.let { legWidths[row] = it.width.toDouble() / width.toDouble() }

            // Below the hips the legs usually touch, leaving one run for both. Halving it
            // approximates a single leg, which is far closer than counting the pair — and
            // the plausibility gate rejects the result if that approximation breaks down.
            if (legWidths[row] == 0.0 && torso != null && torso.width > 0) {
                legWidths[row] = torso.width.toDouble() / (2.0 * width.toDouble())
            }
        }

        return WidthProfile(
            torsoWidths = torsoWidths,
            legWidths = legWidths,
            topRow = topRow,
            bottomRow = bottomRow,
            clippedRows = clippedRows,
        )
    }

    private fun runsInRow(
        buffer: ByteBuffer,
        row: Int,
        width: Int,
        subjectIsNonZero: Boolean,
    ): List<Run> {
        val runs = mutableListOf<Run>()
        val rowStart = row * width

        var runStart = -1
        for (column in 0 until width) {
            val index = rowStart + column
            if (index >= buffer.limit()) break

            val isSubject = (buffer.get(index).toInt() != 0) == subjectIsNonZero
            if (isSubject) {
                if (runStart < 0) runStart = column
            } else if (runStart >= 0) {
                runs += Run(runStart, column - 1)
                runStart = -1
            }
        }
        if (runStart >= 0) runs += Run(runStart, width - 1)

        return mergeSmallGaps(runs)
    }

    private fun mergeSmallGaps(runs: List<Run>): List<Run> {
        if (runs.size < 2) return runs

        val merged = mutableListOf(runs.first())
        for (run in runs.drop(1)) {
            val last = merged.last()
            if (run.start - last.end - 1 <= MAX_HOLE_PIXELS) {
                merged[merged.lastIndex] = Run(last.start, run.end)
            } else {
                merged += run
            }
        }
        return merged
    }

    /** Median centre of the widest run in each row, which tracks the trunk. */
    private fun estimateMidline(
        rowRuns: Array<List<Run>?>,
        topRow: Int,
        bottomRow: Int,
        width: Int,
    ): Int {
        val centres = (topRow..bottomRow)
            .mapNotNull { rowRuns[it]?.maxByOrNull { run -> run.width }?.centre }
            .sorted()

        return if (centres.isEmpty()) width / 2 else centres[centres.size / 2]
    }

    private fun distanceToRun(run: Run, x: Int): Int = when {
        x < run.start -> run.start - x
        x > run.end -> x - run.end
        else -> 0
    }

    /** Samples a small square in each corner and reports whether zero dominates there. */
    private fun cornersAreMostlyZero(buffer: ByteBuffer, width: Int, height: Int): Boolean {
        val patch = (minOf(width, height) / 16).coerceIn(2, 24)
        var zero = 0
        var total = 0

        for (originY in intArrayOf(0, height - patch)) {
            for (originX in intArrayOf(0, width - patch)) {
                for (y in originY until originY + patch) {
                    for (x in originX until originX + patch) {
                        val index = y * width + x
                        if (index < 0 || index >= buffer.limit()) continue
                        total++
                        if (buffer.get(index).toInt() == 0) zero++
                    }
                }
            }
        }

        return total == 0 || zero * 2 >= total
    }
}
