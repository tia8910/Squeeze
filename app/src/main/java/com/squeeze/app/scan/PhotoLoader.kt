package com.squeeze.app.scan

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface

/**
 * Loads a photograph the user picked from their gallery, ready for inference.
 *
 * Two corrections are applied, and both are load-bearing rather than cosmetic:
 *
 *  - **Orientation.** Cameras record rotation in EXIF instead of rotating the pixels, so a
 *    portrait photo is stored landscape with a "rotate 90°" tag. Feeding that to a pose
 *    model means asking it to find a person lying on their side, which it will refuse to
 *    do, and the user would see "no person detected" on a photo that plainly contains one.
 *  - **Downsampling.** A modern phone camera produces a 12 megapixel image. Segmenting one
 *    at full resolution is slow and risks running out of memory mid-scan, while adding
 *    nothing: the models operate on far smaller inputs internally, and the measurements are
 *    normalised fractions, so resolution beyond a point buys no accuracy at all.
 *
 * The picked image is read but never copied into app storage. Nothing new is written to
 * disk, so the guarantee the scan makes is unchanged by allowing uploads.
 */
object PhotoLoader {

    /**
     * Longest edge of the decoded bitmap.
     *
     * Comfortably above what the segmenter and pose models consume, so it does not limit
     * accuracy, while keeping a single frame to a few megabytes.
     */
    const val MAX_DIMENSION = 1440

    /**
     * @return the decoded, correctly-oriented bitmap, or null if the image cannot be read —
     *   a revoked URI permission or an unsupported format, neither of which should crash a
     *   scan the user can simply retry.
     */
    fun load(context: Context, uri: Uri, maxDimension: Int = MAX_DIMENSION): Bitmap? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        val boundsStream = context.contentResolver.openInputStream(uri) ?: return null
        // With inJustDecodeBounds set, decodeStream returns null BY DESIGN — the result
        // arrives through the options object, not the return value. An elvis on this call
        // fires on every photo and made the entire upload path fail; success or failure is
        // judged solely by whether the options carry plausible dimensions afterwards.
        boundsStream.use { BitmapFactory.decodeStream(it, null, bounds) }

        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight, maxDimension)
            // ARGB_8888 is required: MediaPipe cannot consume the packed RGB_565 format.
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }

        val decoded = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        } ?: return null

        val rotation = context.contentResolver.openInputStream(uri)?.use { stream ->
            ExifInterface(stream).rotationDegrees
        } ?: 0

        if (rotation == 0) decoded else rotate(decoded, rotation)
    }.getOrNull()

    /**
     * Largest power-of-two subsample that keeps both edges within [maxDimension].
     *
     * BitmapFactory rounds a non-power-of-two down to the next power of two anyway, so
     * computing it directly makes the result predictable rather than surprising.
     */
    fun sampleSizeFor(width: Int, height: Int, maxDimension: Int): Int {
        var sample = 1
        while (width / (sample * 2) >= maxDimension || height / (sample * 2) >= maxDimension) {
            sample *= 2
        }
        return sample
    }

    private fun rotate(bitmap: Bitmap, degrees: Int): Bitmap {
        val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)

        // createBitmap returns the original instance when the transform is a no-op, so
        // recycling unconditionally would destroy the bitmap being returned.
        if (rotated != bitmap) bitmap.recycle()
        return rotated
    }
}
