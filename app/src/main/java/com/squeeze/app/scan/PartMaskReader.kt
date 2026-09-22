package com.squeeze.app.scan

import com.google.mediapipe.framework.image.ByteBufferExtractor
import com.google.mediapipe.framework.image.MPImage
import com.squeeze.core.scan.BodyPartMap
import com.squeeze.core.scan.FrontPoseGeometry
import com.squeeze.core.scan.NeckReading

/**
 * Copies a part-segmentation category mask out of MediaPipe and into plain bytes.
 *
 * The thinnest possible layer, on purpose. Everything that decides anything lives in
 * [BodyPartMap] in `:core`, where it is a function of an array and can be tested without a
 * device, a model or a photograph. This file exists only because `MPImage` is an Android
 * type and the geometry must not depend on one.
 *
 * The copy is deliberate rather than a view over the native buffer. MediaPipe owns that
 * memory and reuses it for the next inference, so holding a `ByteBuffer` past the call that
 * produced it is a read of whatever the model wrote next.
 */
object PartMaskReader {

    /**
     * Reads the neck from a part mask, in the mask's own coordinate space.
     *
     * @param geometry supplies the shoulder line, which bounds the neck search from below.
     *   Skin alone cannot say where a neck ends — a bare chest is body-skin too — so the
     *   skeleton has to draw that line.
     */
    fun readNeck(mask: MPImage, geometry: FrontPoseGeometry): NeckReading? {
        val width = mask.width
        val height = mask.height
        if (width <= 0 || height <= 0) return null

        val labels = copyLabels(mask, width, height) ?: return null

        // The part mask has its own resolution, so the landmark is projected into *its* rows
        // rather than the silhouette's. Pose landmarks are normalised 0..1, which is what
        // makes that a multiplication instead of a guess.
        val shoulderRow =
            (((geometry.shoulderLeft.y + geometry.shoulderRight.y) / 2.0) * height).toInt()

        val neck = BodyPartMap.readNeck(labels, width, height, shoulderRow) ?: return null

        // **The waist from this same mask, which is what makes the neck usable.**
        //
        // Not to report — the silhouette's waist is the better measurement and keeps its job.
        // This one exists only so the pair can be compared inside one coordinate space. See
        // NeckReading.waistWidthFraction: comparing them across two masks produced a neck of
        // 54.4 cm from a neck the model had measured correctly.
        val hipRow = (((geometry.hipLeft.y + geometry.hipRight.y) / 2.0) * height).toInt()

        return neck.copy(
            waistWidthFraction = BodyPartMap.readWaist(
                labels, width, height, shoulderRow, hipRow,
            ),
        )
    }

    /**
     * What share of the abdomen is bare skin rather than clothing.
     *
     * The band runs from the shoulders to the hips, which is the same span
     * [com.squeeze.core.scan.AbdominalDefinition] scores, so the answer is about the pixels
     * that metric actually reads.
     *
     * @return null when the band holds neither skin nor clothing, i.e. the landmarks put it
     *   somewhere that is not a body
     */
    fun bareAbdomenFraction(mask: MPImage, geometry: FrontPoseGeometry): Double? {
        val width = mask.width
        val height = mask.height
        if (width <= 0 || height <= 0) return null

        val labels = copyLabels(mask, width, height) ?: return null

        val shoulderRow =
            (((geometry.shoulderLeft.y + geometry.shoulderRight.y) / 2.0) * height).toInt()
        val hipRow = (((geometry.hipLeft.y + geometry.hipRight.y) / 2.0) * height).toInt()

        return BodyPartMap.bareSkinFraction(labels, width, height, shoulderRow, hipRow)
    }

    private fun copyLabels(mask: MPImage, width: Int, height: Int): ByteArray? {
        val buffer = ByteBufferExtractor.extract(mask)
        val pixels = width * height
        if (buffer.limit() < pixels) return null

        val labels = ByteArray(pixels)
        // Absolute gets, so the buffer's own position is never consumed and a second reader
        // of the same mask sees what this one saw.
        for (index in 0 until pixels) {
            labels[index] = buffer.get(index)
        }
        return labels
    }
}
