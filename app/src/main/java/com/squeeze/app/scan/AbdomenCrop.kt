package com.squeeze.app.scan

import android.graphics.Bitmap
import androidx.core.graphics.get
import com.squeeze.core.scan.AbdominalDefinition
import com.squeeze.core.scan.DefinitionReading
import com.squeeze.core.scan.FrontPoseGeometry
import com.squeeze.core.scan.LightingQuality
import com.squeeze.core.scan.LightingVerdict

/**
 * Pulls the midsection out of a full-body photograph and measures its definition.
 *
 * The crop is what makes this worth doing. Abdominal definition is a few centimetres of
 * structure on a body that occupies most of a frame, so at full-frame scale it is a handful
 * of pixels competing with everything else in the picture. Cutting to the region between the
 * shoulders and the hips, and to the trunk's own width, throws away the background, the
 * arms and the legs — none of which carry the signal, all of which carry texture that would
 * be mistaken for it.
 *
 * Landmark-driven rather than a fixed fraction of the frame. Where someone's midsection sits
 * in a photograph depends on how they framed it, and a fixed box would measure a chest on a
 * tall crop and a waistband on a short one.
 */
object AbdomenCrop {

    /**
     * Vertical span of the crop, as a fraction of the shoulder-to-hip distance.
     *
     * Starts below the sternum and stops above the waistband. Both ends matter: the ribcage
     * has its own strong edges that are not adiposity, and underwear is the single highest
     * contrast object anywhere on a scan photograph.
     */
    private const val TOP_FRACTION = 0.35
    private const val BOTTOM_FRACTION = 0.82

    /**
     * Horizontal half-width, as a fraction of the hip landmark span.
     *
     * Narrower than the body. The flanks curve away from the camera and fall into shadow,
     * and that shading is a lighting gradient rather than muscle separation — including it
     * would let a hard side light read as definition.
     */
    private const val HALF_WIDTH = 0.62

    /** Below this many pixels across, the crop cannot resolve what it is looking for. */
    private const val MIN_CROP_PIXELS = 48

    /**
     * @return the reading, or an unusable one when the landmarks or the frame do not support
     *   a crop. Never a fabricated score.
     */
    fun measure(bitmap: Bitmap, geometry: FrontPoseGeometry?): DefinitionReading {
        val crop = luminanceOf(bitmap, geometry)
            ?: return DefinitionReading(0.0, usable = false)
        return AbdominalDefinition.measure(crop.first, crop.second)
    }

    /** Luminance of the abdomen crop and its row length, or null when it cannot be taken. */
    private fun luminanceOf(bitmap: Bitmap, geometry: FrontPoseGeometry?): Pair<IntArray, Int>? {
        if (geometry == null) return null

        val shoulderY = (geometry.shoulderLeft.y + geometry.shoulderRight.y) / 2.0
        val hipY = (geometry.hipLeft.y + geometry.hipRight.y) / 2.0
        if (hipY <= shoulderY) return null

        val span = hipY - shoulderY
        val top = ((shoulderY + span * TOP_FRACTION) * bitmap.height).toInt()
        val bottom = ((shoulderY + span * BOTTOM_FRACTION) * bitmap.height).toInt()

        val centreX = (geometry.hipLeft.x + geometry.hipRight.x) / 2.0
        val halfSpan = kotlin.math.abs(geometry.hipRight.x - geometry.hipLeft.x) / 2.0
        val half = halfSpan * HALF_WIDTH
        val left = ((centreX - half) * bitmap.width).toInt()
        val right = ((centreX + half) * bitmap.width).toInt()

        val x0 = left.coerceIn(0, bitmap.width - 1)
        val x1 = right.coerceIn(0, bitmap.width - 1)
        val y0 = top.coerceIn(0, bitmap.height - 1)
        val y1 = bottom.coerceIn(0, bitmap.height - 1)

        val cropWidth = x1 - x0
        val cropHeight = y1 - y0
        if (cropWidth < MIN_CROP_PIXELS || cropHeight < MIN_CROP_PIXELS) return null

        val luminance = IntArray(cropWidth * cropHeight)
        for (y in 0 until cropHeight) {
            for (x in 0 until cropWidth) {
                val pixel = bitmap[x0 + x, y0 + y]
                // Rec. 601 luma. Definition is a brightness structure, and working in luma
                // rather than a single channel keeps a tattoo or a skin-tone shift from
                // registering as one.
                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF
                luminance[y * cropWidth + x] = (299 * r + 587 * g + 114 * b) / 1000
            }
        }

        return luminance to cropWidth
    }

    /**
     * The light the abdomen was under, from the same crop.
     *
     * Read here rather than from the whole frame because the background's lighting is not
     * the subject's: a bright window behind someone says nothing about whether their
     * midsection is side-lit, and that is the only thing that matters for definition.
     */
    fun lighting(bitmap: Bitmap, geometry: FrontPoseGeometry?): LightingVerdict? {
        val crop = luminanceOf(bitmap, geometry) ?: return null
        return LightingQuality.evaluate(crop.first, crop.second)
    }
}
