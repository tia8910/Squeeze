package com.squeeze.app.scan

import com.google.mediapipe.framework.image.ByteBufferExtractor
import com.google.mediapipe.framework.image.MPImage
import com.squeeze.core.scan.WidthProfile

/**
 * Reduces a segmentation mask to a per-row width profile.
 *
 * The mask is scanned row by row for the span between the leftmost and rightmost body
 * pixels. Using the span rather than a pixel count is deliberate: a count would shrink
 * whenever the mask has interior holes, which segmentation models produce routinely around
 * dark clothing, and the resulting narrow row would be indistinguishable from a genuine
 * waist. A span is unaffected by holes as long as the outline survives.
 *
 * Once this returns, everything downstream is plain arithmetic on numbers, which is why the
 * anatomy logic lives in `:core` and is unit tested there.
 */
object MaskWidthExtractor {

    /**
     * Rows with fewer than this many body pixels are treated as noise rather than body.
     * Stray positives around the edge of a mask are common and would otherwise stretch the
     * detected body height, corrupting the scale for every measurement.
     */
    private const val MIN_ROW_PIXELS = 3

    /**
     * At least this fraction of rows must contain body for the mask to be usable.
     * A near-empty mask means the segmenter found nothing worth measuring.
     */
    private const val MIN_BODY_ROWS_FRACTION = 0.15

    /**
     * @param mask category mask, one byte per pixel, non-zero where the subject is
     * @return null when the mask is too sparse to represent a body
     */
    fun extract(mask: MPImage, width: Int, height: Int): WidthProfile? {
        if (width <= 0 || height <= 0) return null

        val buffer = ByteBufferExtractor.extract(mask)
        val widths = DoubleArray(height)

        var topRow = -1
        var bottomRow = -1
        var populatedRows = 0

        for (row in 0 until height) {
            val rowStart = row * width

            var first = -1
            var last = -1
            var count = 0

            for (column in 0 until width) {
                val index = rowStart + column
                if (index >= buffer.limit()) break

                // Selfie segmentation emits non-zero for subject pixels.
                if (buffer.get(index).toInt() != 0) {
                    if (first < 0) first = column
                    last = column
                    count++
                }
            }

            if (count >= MIN_ROW_PIXELS && first >= 0) {
                // Span, not count: interior holes must not narrow the measurement.
                widths[row] = (last - first + 1).toDouble() / width.toDouble()
                if (topRow < 0) topRow = row
                bottomRow = row
                populatedRows++
            }
        }

        if (topRow < 0 || bottomRow <= topRow) return null
        if (populatedRows < height * MIN_BODY_ROWS_FRACTION) return null

        return WidthProfile(widths = widths, topRow = topRow, bottomRow = bottomRow)
    }
}
