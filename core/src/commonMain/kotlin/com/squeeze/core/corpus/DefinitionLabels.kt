package com.squeeze.core.corpus

/**
 * A body region a definition classifier is trained for.
 *
 * Three, and separately rather than as one model. Each is independently testable, independently
 * shippable, and a region whose classifier cannot beat the lighting confound can be dropped
 * without touching the other two. A single model producing all three would have to be accepted
 * or rejected whole.
 */
enum class DefinitionRegion {
    /** Rectus separation, visible relaxed. The hardest of the three and the most requested. */
    ABDOMEN,

    /** Deltoid and pectoral separation at the shoulder line. */
    CHEST_AND_DELTS,

    /** Upper-arm separation and forearm vascularity. */
    ARMS,
}

/**
 * What a human judged about one region of one photograph.
 *
 * **Yes or no, never a scale.** A 0-3 rating invites disagreement between labellers that the
 * model then has to average away, and averaging two people's idea of "moderate definition"
 * produces a target that describes neither of them. A binary question has one right answer per
 * photograph, which is the only kind a small classifier can be held to.
 *
 * @param visible whether the separation is visible with the subject relaxed. Relaxed matters:
 *   almost anyone shows abdominal separation braced, so a corpus that does not fix the
 *   condition teaches the model to detect bracing.
 * @param unusable set when the photograph cannot answer the question at all — the region is
 *   out of frame, blown out, or in shadow. Kept as a label rather than discarded, because
 *   which photographs are unusable is itself worth measuring, and silently dropping them
 *   would hide a capture problem behind a smaller corpus.
 */
data class RegionLabel(
    val region: DefinitionRegion,
    val visible: Boolean,
    val unusable: Boolean = false,
)

/**
 * Every judgement about one photograph, keyed by the hash of the image bytes.
 *
 * **The hash is the key, and the image never travels with it.** The corpus commits labels
 * only; the photographs stay in the app's encrypted storage on the device that took them.
 * That is what makes the set shareable — a labels file describes bodies without containing
 * any, so it can go in the repository, run in CI, and be reviewed in a pull request.
 *
 * It also makes the set verifiable. A label pointing at a hash either matches a photograph on
 * the machine running the training or it does not; there is no way for a mislabelled file to
 * quietly attach itself to the wrong judgement.
 *
 * @param photoHash lowercase hex SHA-256 of the decrypted image bytes
 * @param capturedEpochDay when the photograph was taken, for splitting train and test by date
 *   rather than at random — a random split puts two photographs of the same body a week apart
 *   on both sides of the line, and the model scores well by recognising the person
 */
data class PhotoLabels(
    val photoHash: String,
    val capturedEpochDay: Long,
    val regions: List<RegionLabel>,
) {
    init {
        require(photoHash.length == 64 && photoHash.all { it in HEX }) {
            "photoHash must be a 64-character hex SHA-256: $photoHash"
        }
        require(regions.map { it.region }.toSet().size == regions.size) {
            "one label per region, per photograph"
        }
    }

    /** The judgement for [region], or null when this photograph was not asked about it. */
    fun labelFor(region: DefinitionRegion): RegionLabel? = regions.firstOrNull {
        it.region == region
    }

    private companion object {
        val HEX = ('0'..'9') + ('a'..'f')
    }
}

/**
 * The corpus, as one line of JSON per photograph.
 *
 * JSON Lines rather than a JSON array, and hand-written rather than through a serialisation
 * library, for reasons that are all about the file being reviewed by a person:
 *
 *  - **Append-only.** A new label is a new line. An array would rewrite the whole file on
 *    every addition, and every pull request would show the entire corpus as changed.
 *  - **One label, one diff line.** A reviewer sees exactly which judgements were added and
 *    which were revised, which matters because a revised label is someone changing their mind
 *    about a body and is worth noticing.
 *  - **No dependency.** This module has none, and adding one so a file can hold three fields
 *    per region would be a poor trade.
 *
 * Sorted by hash on write, so two people labelling the same photographs in different orders
 * produce the same file and the diff stays meaningful.
 */
object LabelFile {

    /** Serialises the set. Deterministic: same labels in, same bytes out. */
    fun write(labels: Collection<PhotoLabels>): String =
        labels.sortedBy { it.photoHash }.joinToString("\n") { line(it) }

    /**
     * Parses a labels file, skipping blank lines.
     *
     * Malformed lines throw rather than being skipped. A corpus that quietly drops what it
     * cannot read reports a smaller set and a better score, and gives no sign which is which.
     */
    fun read(text: String): List<PhotoLabels> = text.lineSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .map { parse(it) }
        .toList()

    private fun line(labels: PhotoLabels): String {
        val regions = labels.regions.sortedBy { it.region.name }.joinToString(",") { region ->
            """{"region":"${region.region.name}","visible":${region.visible},""" +
                """"unusable":${region.unusable}}"""
        }
        return """{"hash":"${labels.photoHash}","day":${labels.capturedEpochDay},""" +
            """"regions":[$regions]}"""
    }

    private fun parse(line: String): PhotoLabels {
        val hash = requireNotNull(stringField(line, "hash")) { "no hash in: $line" }
        val day = requireNotNull(longField(line, "day")) { "no day in: $line" }

        val regions = REGION_ENTRY.findAll(line).map { match ->
            val (name, visible, unusable) = match.destructured
            RegionLabel(
                region = DefinitionRegion.valueOf(name),
                visible = visible.toBooleanStrict(),
                unusable = unusable.toBooleanStrict(),
            )
        }.toList()

        return PhotoLabels(photoHash = hash, capturedEpochDay = day, regions = regions)
    }

    private val REGION_ENTRY = Regex(
        """\{"region":"(\w+)","visible":(true|false),"unusable":(true|false)}""",
    )

    private fun stringField(line: String, name: String): String? =
        Regex(""""$name":"([^"]*)"""").find(line)?.groupValues?.get(1)

    private fun longField(line: String, name: String): Long? =
        Regex(""""$name":(-?\d+)""").find(line)?.groupValues?.get(1)?.toLong()
}

/**
 * How a candidate classifier scored, and whether it may ship.
 *
 * @param correct judgements matching the human label
 * @param total judgements made, excluding photographs marked unusable
 */
data class RegionScore(val region: DefinitionRegion, val correct: Int, val total: Int) {
    val accuracy: Double get() = if (total == 0) 0.0 else correct.toDouble() / total

    /**
     * Whether this classifier has earned the right to say anything.
     *
     * Two conditions, and the second is the one that matters. Accuracy above [MIN_ACCURACY] is
     * ordinary. [MIN_LABELS] exists because this project's defining failure was a number
     * derived from a sample of one: the abdominal texture score ran 5.76 at eight per cent and
     * 6.03 at twenty, which is not a weak signal but no signal, and it shipped because nobody
     * had scored it against a labelled set at all.
     */
    val shippable: Boolean get() = total >= MIN_LABELS && accuracy >= MIN_ACCURACY

    companion object {
        /**
         * The least a classifier may be right and still speak.
         *
         * Not a high bar in absolute terms, and deliberately so: the alternative to a
         * classifier at 0.78 is not a better one, it is the app saying nothing about
         * definition. What it does exclude is the range this project has actually produced —
         * a coin flip dressed as a measurement.
         */
        const val MIN_ACCURACY = 0.78

        /** Below this the accuracy figure is noise about noise. */
        const val MIN_LABELS = 120
    }
}

/** Scores a classifier's judgements against the corpus, region by region. */
object CorpusScore {

    /**
     * @param labels the human judgements
     * @param predict what the classifier says about one photograph and region, or null when it
     *   declines — a decline is not counted either way, so a model that abstains on hard
     *   photographs cannot buy accuracy with silence: [RegionScore.MIN_LABELS] still applies
     */
    fun score(
        labels: Collection<PhotoLabels>,
        predict: (PhotoLabels, DefinitionRegion) -> Boolean?,
    ): List<RegionScore> = DefinitionRegion.entries.map { region ->
        var correct = 0
        var total = 0

        labels.forEach { photo ->
            val truth = photo.labelFor(region) ?: return@forEach
            if (truth.unusable) return@forEach
            val guess = predict(photo, region) ?: return@forEach

            total++
            if (guess == truth.visible) correct++
        }

        RegionScore(region, correct, total)
    }
}
