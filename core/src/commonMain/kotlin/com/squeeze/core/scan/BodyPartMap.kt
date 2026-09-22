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
    /**
     * Width of the widest bare-face row, in the same units, which is what the neck was judged
     * against.
     *
     * Carried so the result screen can print the pair. Two rounds were spent inferring from a
     * single centimetre figure whether the model had picked the neck or the top of a
     * trapezius, and the ratio answers it outright: a neck comes out near 0.9 of the face,
     * and anything approaching 1.3 is shoulder.
     */
    val faceWidthFraction: Double,
    /**
     * The waist, as **this same mask** measured it, or null when it was not in the picture.
     *
     * The most important field here, and it is not a measurement anyone displays.
     *
     * The neck comes from the part mask and the waist from the silhouette, and the Navy
     * equation subtracts one from the other. Two masks are two coordinate spaces: they can
     * differ in resolution, in aspect, in whether the segmenter letterboxed, in what a future
     * MediaPipe decides to return. Nothing downstream can detect that, because both numbers
     * are plausible on their own — and `waist − neck` is then a difference between quantities
     * measured with different rulers.
     *
     * It happened. A scan reported a neck-to-waist width ratio of 0.647 where the mask itself
     * showed 0.483, a factor of 1.34, which is very nearly the photograph's aspect ratio. The
     * neck was correct, the waist was correct, and 54.4 cm was the answer.
     *
     * Carrying the waist from this mask makes the pair self-calibrating: the ratio
     * `widthFraction / waistWidthFraction` is what the model saw, in its own space, and
     * rescaling it onto the silhouette's waist puts both sides of the subtraction on one
     * ruler whatever the two masks are doing. See [AutomaticScanBuilder.build].
     */
    val waistWidthFraction: Double? = null,
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
     * How wide a neck run may be, against the face's own bare skin, before it is disbelieved.
     *
     * A neck is narrower than a head, but not by as much as it looks: bare face skin is
     * cheekbone to cheekbone, hair and ears excluded, and a beard or a fringe takes more of
     * it away. Measured on a real photograph through this pipeline the two came out at 23 and
     * 25 pixels — a ratio of 0.92 — so a guard set at parity would throw away good necks.
     *
     * Set where it only catches the failure it is for. A run that has taken in the shoulders
     * is a trapezius, and the same photograph measured 88 to 148 pixels across one row below
     * the jaw. Thirty per cent over the face leaves every real neck alone and rejects that.
     */
    const val MAX_NECK_TO_FACE_WIDTH = 1.3

    /**
     * And how narrow, which is the guard that replaced smoothing.
     *
     * **This used to be a three-row median.** Each row's width was replaced by the median of
     * itself and its neighbours before the minimum was taken, so that one segmentation notch
     * could not decide the reading. It is a reasonable defence against a notch and it encodes
     * an assumption that is simply false: that a neck is at least three rows tall.
     *
     * On a front-double-biceps photograph it is one row tall. The arms come in beside the head
     * immediately below the jaw, so the rows under the chin measured 23, 88, 148, 147 — neck,
     * then arms — and the median turned the 23 into 88, which the guard above then rejected as
     * a trapezius. The app fell back to the silhouette, read 54.3 cm, threw that out as
     * impossible, and printed its constant. The one correct measurement in the photograph was
     * destroyed by the step protecting it.
     *
     * A ratio bound does the same job without the assumption, because it is a fact about
     * bodies rather than about how many rows a body occupies: a notch is a fraction of a neck,
     * and a neck is not a fraction of a face. Forty-five per cent is far below any real
     * ratio — the measured one was 0.92 — which is what a guard against artefacts should be.
     */
    const val MIN_NECK_TO_FACE_WIDTH = 0.45

    /**
     * How far a row may rise above the narrowest yet seen before the search stops.
     *
     * **Why the search stops rather than simply taking a minimum over the whole band.** A
     * minimum searches everywhere, and everywhere includes the shoulders. The band's lower
     * bound is a pose landmark, and a landmark placed a few rows low admits the top of the
     * trapezius — which is wider than the neck but not wildly so, so a width guard tuned to
     * reject a whole trapezius lets the top of one through. That is how a scan whose neck rows
     * were plainly in the mask still reported 51.8 cm.
     *
     * Walking down from the chin removes the question. The profile of a human neck goes one
     * way: narrow at the jaw, then widening into the shoulders, monotonically. So the first
     * substantial rise *is* the shoulder line, located from the body rather than from a
     * landmark, and everything below it is not a candidate at any width.
     *
     * Half again is well past the few per cent a neck varies over its own length and well
     * inside the jump into a deltoid, which on the photograph that prompted this went from 57
     * pixels to 220 in one row.
     */
    const val NECK_RISE = 1.5

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

        // **Walked down from the chin, not searched.**
        //
        // A neck narrows to the jaw and then widens into the shoulders, in that order and
        // without reversing, so the reading is the narrowest run met before the profile turns
        // — and the turn is the shoulder line, read off the body instead of taken from a pose
        // landmark that may sit a few rows low. See [NECK_RISE] for what a landmark a few rows
        // low costs: a 51.8 cm neck measured across the top of a trapezius, on a photograph
        // whose real neck rows were in the mask the whole time.
        //
        // The floor is the other half of it. A segmentation notch is a fraction of a neck and
        // would win any minimum; a neck is not a fraction of a face, so the ratio separates
        // them — see [MIN_NECK_TO_FACE_WIDTH] — and it judges each row alone, which is what
        // lets the neck be a single row tall.
        val minimumWidth = maxOf(
            MIN_NECK_PIXELS.toDouble(),
            if (widestFaceRow > 0) widestFaceRow * MIN_NECK_TO_FACE_WIDTH else 0.0,
        )

        var bestRow = -1
        var bestWidth = Int.MAX_VALUE
        for (index in runWidths.indices) {
            // Named for what it is rather than `width`, which is this function's image width
            // and would be shadowed here — the return below divides by that one.
            val runWidth = runWidths[index]

            // Not skin on the midline, or too thin to be anatomy. Skipped rather than ending
            // the walk: a defect mid-neck must not be read as the shoulders.
            if (runWidth < minimumWidth) continue

            // The profile has turned. Everything below this is shoulder, at any width.
            if (bestWidth != Int.MAX_VALUE && runWidth > bestWidth * NECK_RISE) break

            if (runWidth < bestWidth) {
                bestWidth = runWidth
                bestRow = bandStart + index
            }
        }

        if (bestRow < 0 || bestWidth == Int.MAX_VALUE) return null

        // A last sanity check on the answer, for the photograph with no neck in it at all —
        // shoulders directly under the face, so the walk never meets a rise and settles on a
        // trapezius that was the first thing it saw.
        if (widestFaceRow > 0 && bestWidth > widestFaceRow * MAX_NECK_TO_FACE_WIDTH) return null

        return NeckReading(
            heightFraction = (bestRow + 0.5) / height.toDouble(),
            widthFraction = bestWidth.toDouble() / width.toDouble(),
            bandCoverage = coverage,
            faceWidthFraction = widestFaceRow.toDouble() / width.toDouble(),
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
     * The waist, measured on this mask, in this mask's own units.
     *
     * Not for display and not a replacement for the silhouette's waist, which has the trunk
     * bound behind it and knows how to cut an arm off a torso. This exists so that the neck
     * and the waist can be compared **inside one coordinate space** — see
     * [NeckReading.waistWidthFraction] for the 54.4 cm reading that came of comparing them
     * across two.
     *
     * Skin and clothing together, because a waistband is at the waist and a bare midriff is
     * too; the question here is where the body's outline is, not what is covering it.
     *
     * @param shoulderRow and [hipRow] in this mask's rows, which bound the natural waist the
     *   same way they do on the silhouette
     * @return the narrowest midline run in that band as a fraction of image width, or null
     *   when the band holds nothing measurable
     */
    fun readWaist(
        labels: ByteArray,
        width: Int,
        height: Int,
        shoulderRow: Int,
        hipRow: Int,
    ): Double? {
        if (width <= 0 || height <= 0 || labels.size < width * height) return null

        val start = maxOf(shoulderRow, 0)
        val end = minOf(hipRow, height - 1)
        if (start >= end) return null

        // The midline from the torso itself rather than the face: at waist height the head may
        // be out of frame, and a leaning subject moves their head further than their navel.
        val midline = torsoMidline(labels, width, start, end) ?: return null

        var narrowest = Int.MAX_VALUE
        for (row in start..end) {
            val run = midlineRunWidth(labels, width, row, midline, COVERED)
            if (run >= MIN_NECK_PIXELS && run < narrowest) narrowest = run
        }

        return if (narrowest == Int.MAX_VALUE) null else narrowest.toDouble() / width.toDouble()
    }

    /** Median centre of the covered run in each row, which tracks the trunk. */
    private fun torsoMidline(labels: ByteArray, width: Int, from: Int, to: Int): Int? {
        val centres = mutableListOf<Int>()
        for (row in from..to) {
            var first = -1
            var last = -1
            for (column in 0 until width) {
                if (labels[row * width + column].toInt() !in COVERED) continue
                if (first < 0) first = column
                last = column
            }
            if (first >= 0) centres += (first + last) / 2
        }
        if (centres.isEmpty()) return null
        centres.sort()
        return centres[centres.size / 2]
    }

    /** Classes that count as "this is the body's outline here": bare skin or what covers it. */
    private val COVERED = setOf(BODY_SKIN, CLOTHES)

    /**
     * Width of the bare-skin run containing [midline] on this row, or -1.
     *
     * The run has to be *on* the midline, give or take a defect's width — not merely the
     * nearest one to it. The nearest run to a midline is always some run, and on a row where
     * the neck is behind a collar that run is an ear or a shoulder. Saying "no neck on this
     * row" is what lets [MIN_BAND_COVERAGE] detect a covered neck rather than measure a
     * collar, so the tolerance stops at [MAX_HOLE_PIXELS] and does not widen from there.
     */
    private fun midlineRunWidth(
        labels: ByteArray,
        width: Int,
        row: Int,
        midline: Int,
        classes: Set<Int> = setOf(BODY_SKIN),
    ): Int {
        val centre = midline.coerceIn(0, width - 1)

        // The midline pixel itself, or the nearest skin within a hole's width of it. A chain,
        // a shadow under the chin or a compression artefact sits exactly where this looks,
        // and refusing the row for it would count against [MIN_BAND_COVERAGE] as though the
        // neck were covered. Anything further away than a hole is not the neck.
        val column = (0..MAX_HOLE_PIXELS)
            .flatMap { listOf(centre - it, centre + it) }
            .firstOrNull { it in 0 until width && labels[row * width + it].toInt() in classes }
            ?: return -1

        var start = column
        while (start > 0) {
            val next = start - 1
            if (labels[row * width + next].toInt() in classes) {
                start = next
                continue
            }
            // Bridge a short defect: keep walking if skin resumes within the hole allowance.
            val resumed = (next - MAX_HOLE_PIXELS..next - 1)
                .lastOrNull { it >= 0 && labels[row * width + it].toInt() in classes }
                ?: break
            start = resumed
        }

        var end = column
        while (end < width - 1) {
            val next = end + 1
            if (labels[row * width + next].toInt() in classes) {
                end = next
                continue
            }
            val resumed = (next + 1..next + MAX_HOLE_PIXELS)
                .firstOrNull { it < width && labels[row * width + it].toInt() in classes }
                ?: break
            end = resumed
        }

        return end - start + 1
    }

}
