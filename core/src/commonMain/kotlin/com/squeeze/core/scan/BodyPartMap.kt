package com.squeeze.core.scan

/**
 * A neck, as a part-segmentation model saw it.
 *
 * Positions are fractions rather than rows because the part mask has its own resolution —
 * 256 square, whatever the photograph was — and nothing downstream should have to know that.
 * A fraction converts into any other mask's row space by multiplication, which is the one
 * operation that cannot silently walk the wrong buffer at the wrong stride.
 *
 * @param heightFraction where down the mask the neck was measured, 0.0 at the top edge
 * @param widthFraction the neck's coronal width as a fraction of image width, the same unit
 *   [WidthProfile.torsoWidths] uses, so [ScaleRecovery] converts it exactly as it converts a
 *   silhouette width
 * @param bandCoverage share of the chin-to-shoulder rows that actually had bare neck skin in
 *   them. Carried rather than discarded because a collar is the difference between a neck
 *   this model could see and one it inferred, and the caller is entitled to know which.
 */
data class NeckReading(
    val heightFraction: Double,
    val widthFraction: Double,
    val bandCoverage: Double,
)

/**
 * Reads anatomy off a part-segmentation mask.
 *
 * **Why this exists.** Every measurement in this app until now came from a silhouette: one
 * bit per pixel, person or not person. A silhouette is enough to find a waist, because a
 * waist is a shape — the narrowest place between the shoulders and the hips, and narrowness
 * survives the loss of every other fact about the pixel.
 *
 * A neck does not survive it. In a front-on photograph the region between the jaw and the
 * shoulders contains neck, hair falling either side of it, a collar, and the tops of the
 * trapezius, and in a one-bit mask those are the same colour. The app searched that region
 * for its narrowest row and got the silhouette of head-plus-hair, or a row the trunk bound
 * had already cut back, or nothing at all. The consequence was not a slightly wrong neck: it
 * was **no neck**, which meant `waist − neck` could not be formed, which meant the Navy
 * equation returned null, which meant the tape path vanished and the app printed the
 * silhouette method's constant — 11.6% — to a competition-lean bodybuilder and to a
 * soft-midsectioned man alike, with the words "not resolved by the photo" underneath.
 *
 * **What the model adds.** MediaPipe's `selfie_multiclass_256x256` is a segmentation network
 * that labels each pixel as background, hair, body-skin, face-skin, clothes or accessory.
 * Those six classes turn the unreadable band into an unambiguous one:
 *
 *  - **face-skin** ends at the chin, so its last row is the top of the neck. Not a landmark
 *    guess, not a fraction of the trunk — the bottom of the face, located by a model trained
 *    on faces.
 *  - **hair** is its own class, so the hair hanging beside a neck stops being part of the
 *    neck. This is the single largest source of the old width error.
 *  - **body-skin** is the neck itself, and only where it is actually bare. A run of neck skin
 *    containing the midline is a neck in a way that a run of silhouette never was.
 *  - **clothes** bounds it from below at a collar, and bounds the abdomen too — see
 *    [bareSkinFraction], which is how the definition metric learns it is scoring fabric.
 *
 * The model runs on the device, from an asset in the APK. The app holds no INTERNET
 * permission, so this adds inference without adding any way for a photograph to leave the
 * phone — which is the only form of AI this app is allowed to have.
 *
 * **What it refuses to do.** Every one of these reads returns null rather than a fallback.
 * A hooded top, a scarf, a head out of frame, a mask that found no face — each produces no
 * neck, and no neck is a state the app already knows how to say out loud. The failure this
 * file was written to end was a *silent* one; replacing it with a confident guess would be
 * the same bug wearing a neural network.
 */
object BodyPartMap {

    /** Class indices, in the order of the `labels.txt` embedded in the model file. */
    const val BACKGROUND = 0
    const val HAIR = 1
    const val BODY_SKIN = 2
    const val FACE_SKIN = 3
    const val CLOTHES = 4
    const val ACCESSORY = 5

    /**
     * Rows with fewer face pixels than this do not count as face.
     *
     * A segmenter leaves a scatter of stray face-skin pixels below the chin, on a collarbone
     * or a hand. One such pixel taken as the bottom of the face would push the neck band
     * down past the neck.
     */
    const val MIN_FACE_PIXELS_PER_ROW = 3

    /**
     * Gaps narrower than this inside a run of skin are bridged.
     *
     * The same defect the silhouette extractor bridges in `:app`, for the same reason: a
     * shadow under the jaw or a chain across the throat splits the neck into two runs, and
     * the midline run would then be half a neck — narrower, entirely believable, and wrong
     * in the direction that makes the equation report a leaner body.
     */
    const val MAX_HOLE_PIXELS = 4

    /** A run this thin is a segmentation artefact rather than a neck. */
    const val MIN_NECK_PIXELS = 2

    /**
     * Share of the chin-to-shoulder band that must contain bare neck skin.
     *
     * Below this the neck is covered — a collar, a hood, a scarf — and what skin remains is a
     * sliver at the jaw whose narrowest row is decided by where the fabric starts rather than
     * by the person's neck. Refused, because a neck read off the edge of a collar is wrong by
     * an unbounded amount and looks exactly like a neck.
     */
    const val MIN_BAND_COVERAGE = 0.5

    /**
     * How much wider than the face's own skin a neck run may be before it is disbelieved.
     *
     * A neck is narrower than a head, but not by as much as it looks: bare face skin is
     * cheekbone to cheekbone, hair and ears excluded, and a beard or a fringe takes more of
     * it away. On a clean-shaven man the two widths are within a few centimetres of each
     * other, so a guard set at parity would throw away good necks.
     *
     * Set where it only catches the failure it is for. A band that has bled into the
     * shoulders measures a trapezius, which is roughly twice a neck; thirty per cent over the
     * face leaves every real neck alone and rejects that one outright.
     */
    const val MAX_NECK_TO_FACE_WIDTH = 1.3

    /**
     * Share of the midsection that must be bare skin before a definition score means anything.
     *
     * [AbdominalDefinition] measures the contrast of shadow across an abdomen. Fabric has
     * shadow too — more of it, and with harder edges, because a fold casts a line that no
     * amount of adiposity does. A score taken over a t-shirt is not a weak reading of a body,
     * it is a reading of a shirt, and it comes back in the same units and the same range.
     *
     * Three fifths leaves room for shorts at the bottom of the band and a waistband at the
     * hips, which are present on almost every photograph this app will ever see, and refuses
     * a covered torso.
     */
    const val MIN_BARE_ABDOMEN = 0.6

    /**
     * Locates the neck between the chin and the shoulders.
     *
     * @param labels one class index per pixel, row-major, as the model emits it
     * @param shoulderRow the acromion level in this mask's row space, from the pose model.
     *   The band is bounded below by the skeleton because skin alone cannot say where a neck
     *   stops being a neck — chest and shoulders are body-skin too.
     * @return null whenever the photograph does not actually show a neck, which is a
     *   perfectly ordinary outcome and never a reason to substitute a number
     */
    fun readNeck(
        labels: ByteArray,
        width: Int,
        height: Int,
        shoulderRow: Int,
    ): NeckReading? {
        if (width <= 0 || height <= 0 || labels.size < width * height) return null

        // The face, which supplies both ends of the search: its lowest row is the top of the
        // band, and its horizontal centre is the midline the neck run has to contain.
        var faceBottom = -1
        // Int rather than Long: the largest mask this sees is a few hundred square, so the
        // column sum cannot approach the range, and Kotlin/JS pays for every Long it is given.
        var faceSum = 0
        var faceCount = 0
        var widestFaceRow = 0
        for (row in 0 until height) {
            var rowCount = 0
            var rowSum = 0
            var first = -1
            var last = -1
            for (column in 0 until width) {
                if (labels[row * width + column].toInt() != FACE_SKIN) continue
                rowCount++
                rowSum += column
                if (first < 0) first = column
                last = column
            }
            if (rowCount < MIN_FACE_PIXELS_PER_ROW) continue
            faceBottom = row
            faceSum += rowSum
            faceCount += rowCount
            widestFaceRow = maxOf(widestFaceRow, last - first + 1)
        }

        if (faceBottom < 0 || faceCount == 0) return null

        val midline = faceSum / faceCount

        val bandStart = faceBottom + 1
        val bandEnd = minOf(shoulderRow, height - 1)
        if (bandStart > bandEnd) return null

        // Width of the bare-skin run containing the midline, per row; -1 where the midline is
        // not on skin at all, which is a collar or a beard rather than a narrow neck.
        val runWidths = IntArray(bandEnd - bandStart + 1) { -1 }
        for (row in bandStart..bandEnd) {
            runWidths[row - bandStart] = midlineRunWidth(labels, width, row, midline)
        }

        val covered = runWidths.count { it >= MIN_NECK_PIXELS }
        val coverage = covered.toDouble() / runWidths.size.toDouble()
        if (coverage < MIN_BAND_COVERAGE) return null

        // **Smoothed over three rows before the minimum is taken.**
        //
        // The neck genuinely narrows towards the jaw, so a minimum is the right selector —
        // but a minimum over raw rows is decided by the single worst row in the band, and one
        // row is exactly what a segmentation notch costs. Replacing each row by the median of
        // itself and its neighbours leaves a real narrowing untouched and cannot be moved by
        // a one-row defect at all.
        var bestRow = -1
        var bestWidth = Int.MAX_VALUE
        for (index in runWidths.indices) {
            if (runWidths[index] < MIN_NECK_PIXELS) continue
            val smoothed = medianOfNeighbours(runWidths, index) ?: continue
            if (smoothed < bestWidth) {
                bestWidth = smoothed
                bestRow = bandStart + index
            }
        }

        if (bestRow < 0 || bestWidth == Int.MAX_VALUE) return null

        // A neck is narrower than the head above it. When it is not, the band has bled into
        // the shoulders — a shoulder landmark placed low, or a subject leaning back — and the
        // run being measured is a trapezius. That reading is what drove the equation to a
        // negative body fat before any of this existed.
        if (widestFaceRow > 0 && bestWidth > widestFaceRow * MAX_NECK_TO_FACE_WIDTH) return null

        return NeckReading(
            heightFraction = (bestRow + 0.5) / height.toDouble(),
            widthFraction = bestWidth.toDouble() / width.toDouble(),
            bandCoverage = coverage,
        )
    }

    /**
     * What share of the covered body between two rows is bare skin rather than clothing.
     *
     * For [com.squeeze.core.scan.AbdominalDefinition], which reads shadow gradients across a
     * midsection and cannot tell a waistband from a linea alba. Scoring a clothed abdomen is
     * not a small error — the metric returns a number, the number is fabric, and nothing on
     * screen says so.
     *
     * @return null when neither skin nor clothing is present in the band at all, i.e. the
     *   rows are background and there is nothing to take a share of
     */
    fun bareSkinFraction(
        labels: ByteArray,
        width: Int,
        height: Int,
        fromRow: Int,
        toRow: Int,
    ): Double? {
        if (width <= 0 || height <= 0 || labels.size < width * height) return null

        val start = maxOf(fromRow, 0)
        val end = minOf(toRow, height - 1)
        if (start > end) return null

        var skin = 0
        var clothed = 0
        for (row in start..end) {
            for (column in 0 until width) {
                when (labels[row * width + column].toInt()) {
                    BODY_SKIN -> skin++
                    CLOTHES -> clothed++
                }
            }
        }

        val total = skin + clothed
        return if (total == 0) null else skin.toDouble() / total.toDouble()
    }

    /**
     * Width of the bare-skin run containing [midline] on this row, or -1.
     *
     * The run has to be *on* the midline, give or take a defect's width — not merely the
     * nearest one to it. The nearest run to a midline is always some run, and on a row where
     * the neck is behind a collar that run is an ear or a shoulder. Saying "no neck on this
     * row" is what lets [MIN_BAND_COVERAGE] detect a covered neck rather than measure a
     * collar, so the tolerance stops at [MAX_HOLE_PIXELS] and does not widen from there.
     */
    private fun midlineRunWidth(labels: ByteArray, width: Int, row: Int, midline: Int): Int {
        val centre = midline.coerceIn(0, width - 1)

        // The midline pixel itself, or the nearest skin within a hole's width of it. A chain,
        // a shadow under the chin or a compression artefact sits exactly where this looks,
        // and refusing the row for it would count against [MIN_BAND_COVERAGE] as though the
        // neck were covered. Anything further away than a hole is not the neck.
        val column = (0..MAX_HOLE_PIXELS)
            .flatMap { listOf(centre - it, centre + it) }
            .firstOrNull { it in 0 until width && labels[row * width + it].toInt() == BODY_SKIN }
            ?: return -1

        var start = column
        while (start > 0) {
            val next = start - 1
            if (labels[row * width + next].toInt() == BODY_SKIN) {
                start = next
                continue
            }
            // Bridge a short defect: keep walking if skin resumes within the hole allowance.
            val resumed = (next - MAX_HOLE_PIXELS..next - 1)
                .lastOrNull { it >= 0 && labels[row * width + it].toInt() == BODY_SKIN }
                ?: break
            start = resumed
        }

        var end = column
        while (end < width - 1) {
            val next = end + 1
            if (labels[row * width + next].toInt() == BODY_SKIN) {
                end = next
                continue
            }
            val resumed = (next + 1..next + MAX_HOLE_PIXELS)
                .firstOrNull { it < width && labels[row * width + it].toInt() == BODY_SKIN }
                ?: break
            end = resumed
        }

        return end - start + 1
    }

    /** Median of this row's width and its present neighbours', or null if the row is absent. */
    private fun medianOfNeighbours(widths: IntArray, index: Int): Int? {
        val window = (index - 1..index + 1)
            .mapNotNull { widths.getOrNull(it)?.takeIf { w -> w >= MIN_NECK_PIXELS } }
            .sorted()
        return if (window.isEmpty()) null else window[window.size / 2]
    }
}
