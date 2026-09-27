package com.squeeze.core.program

import com.squeeze.core.model.Circumferences
import com.squeeze.core.model.Goal
import com.squeeze.core.model.Sex
import com.squeeze.core.scan.Development
import com.squeeze.core.scan.PhysiqueAnalysis
import com.squeeze.core.scan.PlausibleRanges
import com.squeeze.core.text.fixed
import com.squeeze.core.workout.DatedSet
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt
import com.squeeze.core.scan.MuscleGroup as PhotoGroup

/** Where a piece of evidence about a muscle came from. */
enum class EvidenceSource(val label: String) {
    LIFTS("Lifts"),
    SIZE("Size"),
    PHOTO("Photo"),
}

/**
 * One reason for a muscle's verdict.
 *
 * @param score on the app's 0–1 scale
 * @param sd how far it could plausibly be off; the verdict weighs evidence by this
 */
data class MuscleEvidence(val source: EvidenceSource, val score: Double, val sd: Double, val text: String)

/** How sure the verdict is, from the combined uncertainty of its evidence. */
enum class MuscleConfidence(val label: String) { HIGH("High confidence"), MEDIUM("Medium confidence"), LOW("Low confidence") }

/**
 * One muscle, judged on everything the app knows about it.
 *
 * @param note what to make of it when the evidence disagrees, e.g. strong but not visible
 */
data class MuscleAssessment(
    val group: MuscleGroup,
    val score: Double,
    val sd: Double,
    val development: Development,
    val confidence: MuscleConfidence,
    val evidence: List<MuscleEvidence>,
    val note: String? = null,
) {
    val label: String get() = BlockOverview.label(group)
}

/** A weak point and what to do about it. */
data class MuscleFocus(val group: MuscleGroup, val why: String, val how: String)

/**
 * @param assessments every muscle with any evidence, strongest first
 * @param unassessed muscles nothing could speak to yet, and what would
 */
data class MuscleProfile(
    val assessments: List<MuscleAssessment>,
    val strengths: List<MuscleGroup>,
    val weaknesses: List<MuscleFocus>,
    val unassessed: List<MuscleGroup>,
)

/** A photo-model reading for one of its coarse groups, fused across scans where possible. */
data class PhotoReading(val score: Double, val sd: Double, val reads: Int)

/**
 * Ten muscles, each judged on up to three independent kinds of evidence.
 *
 * **Why three.** The photo model judges five coarse groups from one front view, and one photo
 * swings by tens of points with light and pose. That was the whole verdict. But the app also
 * holds the user's lifts, which measure strength directly, and their girths, whose ratios
 * measure proportion. Each answers a different question and fails in a different way, so a
 * verdict built on all three is both more accurate and harder to fool.
 *
 * **How they combine.** Each piece of evidence carries an uncertainty, and the verdict is the
 * inverse-variance average, the same rule the scan fusion uses: a barbell lift against a
 * published standard counts for far more than one photo's impression. Indirect evidence is
 * down-weighted rather than dropped: a front photo's V-taper says a little about the back,
 * and a thigh girth a little about hamstrings.
 *
 * **What it will not do.** Invent a verdict. A muscle with no evidence is listed as not yet
 * assessed, with what would assess it, rather than given an average from nowhere.
 */
object MuscleProfiler {

    /** Standards are population guides; no lift pins a muscle closer than this. */
    private const val LIFT_FLOOR_SD = 0.07

    /** One proportion against a classical ideal. */
    private const val SIZE_SD = 0.12

    /** A photo reading is never trusted beyond this, however many scans agree. */
    private const val PHOTO_FLOOR_SD = 0.09

    private const val STALE_FACTOR = 1.4

    fun assess(
        sex: Sex,
        goal: Goal,
        bodyweightKg: Double?,
        sets: List<DatedSet>,
        today: Long,
        circumferences: Circumferences?,
        photo: Map<PhotoGroup, PhotoReading>,
        hiddenInPhoto: Set<PhotoGroup> = emptySet(),
    ): MuscleProfile {
        val evidence = mutableMapOf<MuscleGroup, MutableList<MuscleEvidence>>()
        fun add(group: MuscleGroup, e: MuscleEvidence) { evidence.getOrPut(group) { mutableListOf() } += e }

        liftEvidence(sex, bodyweightKg, sets, today).forEach { (g, e) -> add(g, e) }
        circumferences?.let { c -> sizeEvidence(c, sex).forEach { (g, e) -> add(g, e) } }
        photoEvidence(photo, hiddenInPhoto).forEach { (g, e) -> add(g, e) }

        val assessments = evidence.map { (group, list) -> combine(group, list) }.sortedByDescending { it.score }
        val mean = assessments.map { it.score }.average().takeIf { it.isFinite() } ?: 0.0

        val strengths = assessments.filter {
            it.development == Development.DEVELOPED ||
                (it.group != MuscleGroup.ABS && it.score >= RELATIVE_MIN && it.score >= mean + RELATIVE_LEAD)
        }.map { it.group }

        val weaknesses = assessments
            .filter { it.group !in strengths && it.score < WEAK_BELOW }
            .sortedByDescending { importance(goal, it.group) * (1.0 - it.score) / (1.0 + it.sd) }
            .take(MAX_WEAKNESSES)
            .map { MuscleFocus(it.group, why(it, mean), how(it.group)) }

        return MuscleProfile(
            assessments = assessments,
            strengths = strengths,
            weaknesses = weaknesses,
            unassessed = MuscleGroup.entries.filter { it !in evidence },
        )
    }

    private const val RELATIVE_LEAD = 0.08
    private const val RELATIVE_MIN = 0.45
    private const val WEAK_BELOW = 0.5
    private const val MAX_WEAKNESSES = 3

    // ── Lifts ────────────────────────────────────────────────────────────────────────────

    private fun liftEvidence(
        sex: Sex,
        bodyweightKg: Double?,
        sets: List<DatedSet>,
        today: Long,
    ): List<Pair<MuscleGroup, MuscleEvidence>> {
        val weight = bodyweightKg ?: return emptyList()
        val bests = StrengthStandards.bests(sets, weight, sex, today)
        if (bests.isEmpty()) return emptyList()
        return MuscleGroup.entries.mapNotNull { group ->
            val relevant = bests.mapNotNull { b -> b.lift.trains[group]?.let { c -> b to c } }
            if (relevant.isEmpty()) return@mapNotNull null
            // A lift that trains the muscle only partly speaks for it less.
            val weighted = relevant.map { (b, c) ->
                val sd = b.lift.sd / sqrt(c) * (if (b.stale) STALE_FACTOR else 1.0)
                Triple(b, b.score, sd)
            }
            val (score, sd) = inverseVariance(weighted.map { it.second to it.third })
            val best = weighted.minBy { it.third }.first
            val others = weighted.size - 1
            val text = "${best.exercise} ${best.estimatedMaxKg.roundToInt()} kg estimated max, " +
                "${best.ratio.fixed(2)}× bodyweight (${best.level.label.lowercase()})" +
                (if (others > 0) " + $others more" else "") +
                (if (best.stale) ", not logged recently" else "")
            group to MuscleEvidence(EvidenceSource.LIFTS, score, sd.coerceAtLeast(LIFT_FLOOR_SD), text)
        }
    }

    // ── Size ─────────────────────────────────────────────────────────────────────────────

    private data class Ratio(val value: Double, val ideal: Double, val text: String, val groups: Map<MuscleGroup, Double>)

    private fun sizeEvidence(c: Circumferences, sex: Sex): List<Pair<MuscleGroup, MuscleEvidence>> {
        val femaleArm = if (sex == Sex.FEMALE) 0.92 else 1.0
        val ratios = buildList {
            val arm = c.armCm
            val chest = c.chestCm
            val waist = c.waistCm
            val thigh = c.thighCm
            val calf = c.calfCm
            val neck = c.neckCm
            val chestOk = chest != null && waist != null && PlausibleRanges.plausibleTaper(chest, waist)
            if (arm != null && chest != null && chestOk) {
                add(Ratio(arm / chest, 0.36 * femaleArm, "arm ${(arm / chest * 100).roundToInt()}% of chest",
                    mapOf(MuscleGroup.BICEPS to 1.2, MuscleGroup.TRICEPS to 1.2)))
            }
            if (arm != null && neck != null && neck > 0) {
                add(Ratio(arm / neck, if (sex == Sex.FEMALE) 0.88 else 1.0, "arm ${(arm / neck).fixed(2)}× neck",
                    mapOf(MuscleGroup.BICEPS to 1.4, MuscleGroup.TRICEPS to 1.4)))
            }
            if (chest != null && waist != null && chestOk) {
                add(Ratio(chest / waist, 1.25, "chest ${(chest / waist).fixed(2)}× waist",
                    mapOf(MuscleGroup.CHEST to 1.3, MuscleGroup.BACK to 1.3)))
            }
            if (thigh != null && waist != null && waist > 0) {
                add(Ratio(thigh / waist, 0.68, "thigh ${(thigh / waist * 100).roundToInt()}% of waist",
                    mapOf(MuscleGroup.QUADS to 1.1, MuscleGroup.HAMSTRINGS to 1.5, MuscleGroup.GLUTES to 1.6)))
            }
            if (calf != null && thigh != null && thigh > 0) {
                add(Ratio(calf / thigh, 0.63, "calf ${(calf / thigh * 100).roundToInt()}% of thigh",
                    mapOf(MuscleGroup.CALVES to 1.0)))
            }
        }
        return MuscleGroup.entries.mapNotNull { group ->
            val relevant = ratios.mapNotNull { r -> r.groups[group]?.let { k -> r to k } }
            if (relevant.isEmpty()) return@mapNotNull null
            val (score, sd) = inverseVariance(relevant.map { (r, k) -> sizeScore(r.value, r.ideal) to SIZE_SD * k })
            val text = relevant.joinToString(", ") { (r, _) ->
                "${r.text} (balanced ≈ ${idealText(r)})"
            }
            group to MuscleEvidence(EvidenceSource.SIZE, score, sd, text)
        }
    }

    private fun idealText(r: Ratio): String =
        if (r.text.contains("%")) "${(r.ideal * 100).roundToInt()}%" else "${r.ideal.fixed(2)}×"

    /** At the ideal ratio 0.55; 6 % short of it is the lagging edge (0.35), 6 % over developed-ish. */
    private fun sizeScore(ratio: Double, ideal: Double): Double =
        (0.55 + (ratio / ideal - 1.0) / 0.06 * 0.2).coerceIn(0.05, 0.95)

    // ── Photo ────────────────────────────────────────────────────────────────────────────

    /**
     * How much a photo group says about each muscle: 1 is direct, higher numbers inflate the
     * uncertainty. A front photo sees chest, shoulders and abs directly, the arm but not which
     * head of it, and the back only as a V-shape. Glutes it cannot see at all.
     */
    private val PHOTO_MAP: Map<PhotoGroup, Map<MuscleGroup, Double>> = mapOf(
        PhotoGroup.CHEST to mapOf(MuscleGroup.CHEST to 1.0),
        PhotoGroup.SHOULDERS to mapOf(MuscleGroup.SHOULDERS to 1.0),
        PhotoGroup.ABS to mapOf(MuscleGroup.ABS to 1.0),
        PhotoGroup.ARMS to mapOf(MuscleGroup.BICEPS to 1.2, MuscleGroup.TRICEPS to 1.4),
        PhotoGroup.V_TAPER to mapOf(MuscleGroup.BACK to 1.7),
        PhotoGroup.LEGS to mapOf(MuscleGroup.QUADS to 1.2, MuscleGroup.HAMSTRINGS to 1.8, MuscleGroup.CALVES to 1.6),
    )

    private fun photoEvidence(
        photo: Map<PhotoGroup, PhotoReading>,
        hidden: Set<PhotoGroup>,
    ): List<Pair<MuscleGroup, MuscleEvidence>> = photo
        .filterKeys { it !in hidden }
        .flatMap { (photoGroup, reading) ->
            PHOTO_MAP[photoGroup].orEmpty().map { (group, inflation) ->
                val sd = reading.sd.coerceAtLeast(PHOTO_FLOOR_SD) * inflation
                val scans = if (reading.reads > 1) "across ${reading.reads} scans" else "one scan"
                val how = if (inflation > 1.3) "indirect, " else ""
                group to MuscleEvidence(
                    EvidenceSource.PHOTO,
                    reading.score.coerceIn(0.0, 1.0),
                    sd,
                    "${(reading.score * 100).roundToInt()}/100 ($how$scans)",
                )
            }
        }

    // ── Combining ────────────────────────────────────────────────────────────────────────

    private fun inverseVariance(items: List<Pair<Double, Double>>): Pair<Double, Double> {
        val weights = items.map { 1.0 / (it.second * it.second) }
        val total = weights.sum()
        val score = items.indices.sumOf { items[it].first * weights[it] } / total
        return score to sqrt(1.0 / total)
    }

    private fun combine(group: MuscleGroup, list: List<MuscleEvidence>): MuscleAssessment {
        val (score, sd) = inverseVariance(list.map { it.score to it.sd })
        val lifts = list.firstOrNull { it.source == EvidenceSource.LIFTS }
        val photo = list.firstOrNull { it.source == EvidenceSource.PHOTO }
        val note = if (lifts != null && photo != null && abs(lifts.score - photo.score) >= DISAGREE) {
            if (lifts.score > photo.score) {
                "Strong for your bodyweight but not showing yet. Lower body fat or more size will bring it out."
            } else {
                "Looks developed, but your lifts lag behind. Progress the load on it."
            }
        } else {
            null
        }
        return MuscleAssessment(
            group = group,
            score = score.coerceIn(0.0, 1.0),
            sd = sd,
            development = PhysiqueAnalysis.development(score),
            confidence = when {
                sd < 0.075 -> MuscleConfidence.HIGH
                sd < 0.11 -> MuscleConfidence.MEDIUM
                else -> MuscleConfidence.LOW
            },
            evidence = list.sortedBy { it.sd },
            note = note,
        )
    }

    private const val DISAGREE = 0.3

    // ── Advice ───────────────────────────────────────────────────────────────────────────

    fun importance(goal: Goal, group: MuscleGroup): Double = when (goal) {
        Goal.HYPERTROPHY -> if (group == MuscleGroup.ABS) 0.6 else 1.0
        Goal.STRENGTH -> when (group) {
            MuscleGroup.QUADS, MuscleGroup.HAMSTRINGS, MuscleGroup.GLUTES, MuscleGroup.BACK -> 1.0
            MuscleGroup.CHEST, MuscleGroup.SHOULDERS, MuscleGroup.TRICEPS -> 0.8
            MuscleGroup.BICEPS, MuscleGroup.ABS, MuscleGroup.CALVES -> 0.5
        }
        Goal.CUT, Goal.MAKE_WEIGHT -> if (group == MuscleGroup.ABS) 1.0 else 0.6
        Goal.RECOMP -> if (group == MuscleGroup.ABS) 0.8 else 0.9
    }

    private fun why(a: MuscleAssessment, mean: Double): String {
        val strongest = a.evidence.first()
        return "${(a.score * 100).roundToInt()}/100 against your average of ${(mean * 100).roundToInt()}, " +
            "led by ${strongest.source.label.lowercase()}."
    }

    fun how(group: MuscleGroup): String = when (group) {
        MuscleGroup.CHEST -> "Bench or incline press plus dips or flyes, 10–20 hard sets a week."
        MuscleGroup.BACK -> "Rows plus pull-ups or pulldowns, 10–20 sets a week. Wider lats make the waist look narrower."
        MuscleGroup.SHOULDERS -> "Overhead press and lateral raises, 10–20 sets a week. Side delts widen the frame."
        MuscleGroup.BICEPS -> "Curls, 8–14 sets a week on top of your pulling."
        MuscleGroup.TRICEPS -> "Close-grip press or dips plus overhead extensions, 8–14 sets a week."
        MuscleGroup.QUADS -> "Squats or leg press plus leg extensions, 10–20 sets a week."
        MuscleGroup.HAMSTRINGS -> "Romanian deadlifts and leg curls, 8–14 sets a week. They need both a hinge and a curl."
        MuscleGroup.GLUTES -> "Hip thrusts plus deep squats or lunges, 8–16 sets a week."
        MuscleGroup.CALVES -> "Standing and seated calf raises, 10–16 sets across three days, pausing at the bottom."
        MuscleGroup.ABS -> "Hanging leg raises and cable crunches, 6–10 sets a week. They show as body fat drops."
    }

    /** What would let the app judge a muscle it has no evidence for. */
    fun toAssess(group: MuscleGroup): String = when (group) {
        MuscleGroup.CHEST -> "log a bench press"
        MuscleGroup.BACK -> "log rows or pulldowns"
        MuscleGroup.SHOULDERS -> "log an overhead press"
        MuscleGroup.BICEPS -> "log barbell curls"
        MuscleGroup.TRICEPS -> "log pushdowns or bench"
        MuscleGroup.QUADS -> "log squats or leg press"
        MuscleGroup.HAMSTRINGS -> "log Romanian deadlifts"
        MuscleGroup.GLUTES -> "log hip thrusts or squats"
        MuscleGroup.CALVES -> "add calf and thigh girths"
        MuscleGroup.ABS -> "scan with your midsection bare"
    }
}
