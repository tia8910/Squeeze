package com.squeeze.core.scan

import com.squeeze.core.model.Goal
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** The parts of a body the on-device model judges one at a time, from the front. */
enum class MuscleGroup(val label: String) {
    SHOULDERS("Shoulders"),
    CHEST("Chest"),
    ARMS("Arms"),
    ABS("Abs"),
    V_TAPER("Back width"),
    LEGS("Legs"),
}

/** How a group reads, in the three words a coach would use. */
enum class Development(val label: String) {
    DEVELOPED("Strong point"),
    AVERAGE("Average"),
    LAGGING("Lagging"),
}

/**
 * One wording of a group, developed against undeveloped, as the model embeds the two.
 */
class MusclePromptPair(val developed: DoubleArray, val undeveloped: DoubleArray)

/**
 * Where on a front photograph each group is, from the pose landmarks.
 *
 * Every group is judged on a crop of its own, because a vision-language model shown a whole
 * body answers about the whole body: asked about arms, it scored the same lean man's arms
 * 0.02 in one photograph and 0.63 in another, depending on how much of the frame they filled.
 * Cropped to the arm itself, the question it answers is the one that was asked.
 */
object PhysiqueRegions {

    fun regions(pose: FrontPoseGeometry): Map<MuscleGroup, List<CropRegion>> {
        val shoulderY = (pose.shoulderLeft.y + pose.shoulderRight.y) / 2.0
        val hipY = (pose.hipLeft.y + pose.hipRight.y) / 2.0
        val span = hipY - shoulderY
        val left = min(pose.shoulderLeft.x, pose.shoulderRight.x)
        val right = max(pose.shoulderLeft.x, pose.shoulderRight.x)
        val width = right - left
        if (span <= 0.0 || width <= 0.0) return emptyMap()
        val centre = (left + right) / 2.0

        val out = mutableMapOf<MuscleGroup, List<CropRegion>>()
        fun put(group: MuscleGroup, vararg boxes: CropRegion?) {
            val kept = boxes.filterNotNull()
            if (kept.isNotEmpty()) out[group] = kept
        }

        put(
            MuscleGroup.SHOULDERS,
            box(left - 0.3 * width, shoulderY - 0.25 * span, right + 0.3 * width, shoulderY + 0.3 * span),
        )
        put(
            MuscleGroup.CHEST,
            box(left - 0.05 * width, shoulderY - 0.05 * span, right + 0.05 * width, shoulderY + 0.5 * span),
        )
        put(
            MuscleGroup.ABS,
            box(centre - 0.4 * width, shoulderY + 0.35 * span, centre + 0.4 * width, hipY + 0.1 * span),
        )
        put(
            MuscleGroup.V_TAPER,
            box(centre - width, shoulderY - 0.35 * span, centre + width, hipY + 0.15 * span),
        )

        // Each arm from shoulder to wrist, and only when the elbow is in the frame — an arm
        // the pose model placed off the edge of the photograph is a guess, not a crop.
        val margin = 0.12 * width
        val arms = listOf(
            Triple(pose.shoulderLeft, pose.elbowLeft, pose.wristLeft),
            Triple(pose.shoulderRight, pose.elbowRight, pose.wristRight),
        ).mapNotNull { (shoulder, elbow, wrist) ->
            if (elbow == null || !elbow.inFrame()) return@mapNotNull null
            val points = listOfNotNull(shoulder, elbow, wrist)
            box(
                points.minOf { it.x } - margin,
                points.minOf { it.y } - margin,
                points.maxOf { it.x } + margin,
                points.maxOf { it.y } + margin,
            )
        }
        put(MuscleGroup.ARMS, *arms.toTypedArray())

        // Legs only when both knees are in the photograph; a waist-up scan has no legs to judge.
        val knees = listOfNotNull(pose.kneeLeft, pose.kneeRight).filter { it.inFrame() }
        if (knees.size == 2) {
            val ankles = listOfNotNull(pose.ankleLeft, pose.ankleRight).filter { it.inFrame() }
            val bottom = (if (ankles.size == 2) ankles else knees).maxOf { it.y } + 0.03
            val hipLeft = min(pose.hipLeft.x, pose.hipRight.x)
            val hipRight = max(pose.hipLeft.x, pose.hipRight.x)
            put(
                MuscleGroup.LEGS,
                box(hipLeft - 0.35 * width, hipY - 0.05 * span, hipRight + 0.35 * width, bottom),
            )
        }
        return out
    }

    private fun PosePoint.inFrame() = x in 0.0..1.0 && y in 0.0..1.0

    private fun box(left: Double, top: Double, right: Double, bottom: Double): CropRegion? {
        val region = CropRegion(
            left = left.coerceIn(0.0, 1.0),
            top = top.coerceIn(0.0, 1.0),
            right = right.coerceIn(0.0, 1.0),
            bottom = bottom.coerceIn(0.0, 1.0),
        )
        return region.takeIf { it.right - it.left >= MIN_EDGE && it.bottom - it.top >= MIN_EDGE }
    }

    private const val MIN_EDGE = 0.03
}

/**
 * How developed one group looks: the probability the model gives the developed side of each
 * wording, averaged over every wording and every crop of the group.
 */
object MuscleScorer {

    fun score(crops: List<DoubleArray>, pairs: List<MusclePromptPair>): Double? {
        if (crops.isEmpty() || pairs.isEmpty()) return null
        val readings = crops.flatMap { image ->
            val unit = normalise(image) ?: return null
            pairs.map { pair ->
                if (pair.developed.size != unit.size || pair.undeveloped.size != unit.size) return null
                val strong = AppearanceEstimator.LOGIT_SCALE * dot(pair.developed, unit)
                val weak = AppearanceEstimator.LOGIT_SCALE * dot(pair.undeveloped, unit)
                // The two-way softmax, written so neither exponential can overflow.
                1.0 / (1.0 + exp(weak - strong))
            }
        }
        return readings.average().takeIf { it.isFinite() }
    }

    private fun normalise(v: DoubleArray): DoubleArray? {
        if (v.isEmpty() || v.any { !it.isFinite() }) return null
        val norm = sqrt(v.sumOf { it * it })
        if (norm == 0.0) return null
        return DoubleArray(v.size) { v[it] / norm }
    }

    private fun dot(a: DoubleArray, b: DoubleArray) = a.indices.sumOf { a[it] * b[it] }
}

/** One group's reading. [score] runs 0 to 1: how much the model sides with "developed". */
data class MuscleScore(val group: MuscleGroup, val score: Double, val development: Development)

/** What to do about one group, and why it matters for this user's goal. */
data class FocusAdvice(val group: MuscleGroup, val why: String, val how: String)

/**
 * The physique read group by group, and what it means for the goal the user set.
 *
 * @param scores every group the photograph showed, strongest first
 * @param strengths groups that read developed. Empty when none do: an earlier draft named the
 *   best of an undeveloped set instead, and so told a man with a soft stomach that his abs
 *   were his strength — best of a weak set is not a strength, and saying so is flattery
 * @param weaknesses the groups the goal most needs brought up, most urgent first
 * @param focus what to do about each weakness
 * @param summary one line on what the goal asks of this physique
 */
data class PhysiqueReport(
    val goal: Goal,
    val scores: List<MuscleScore>,
    val strengths: List<MuscleGroup>,
    val weaknesses: List<MuscleGroup>,
    val focus: List<FocusAdvice>,
    val summary: String,
    /** Groups the photo did not show well enough to judge — covered, or out of frame. */
    val hidden: List<MuscleGroup> = emptyList(),
    /** Why each strength is one: what the AI saw, what was measured, or both. */
    val strengthEvidence: Map<MuscleGroup, String> = emptyMap(),
    /** What the measurements add to a weak point, when they agree with the AI. */
    val weaknessEvidence: Map<MuscleGroup, String> = emptyMap(),
)

/**
 * Turns per-group scores into strengths, weaknesses and a plan for the user's goal.
 *
 * **What it is.** The on-device model's impression of each group from one front photograph:
 * what a coach sees at a glance, not a measurement. It cannot see the back, cannot weigh a
 * lift, and reads a pumped, oiled or flexed body as more developed than the same body
 * relaxed. It was checked on five photographs — it separated a bodybuilder from an untrained
 * man on every group — which says it is looking at the right thing, not how precisely.
 *
 * **Why the goal changes the answer.** A weakness is a gap between the physique and what the
 * user is training for. Lagging arms are the first thing to fix for size and nearly the last
 * for a heavier squat; soft abs are the whole point of a cut and a side effect of a bulk.
 */
object PhysiqueAnalysis {

    /** At or above this the model clearly sides with "developed". */
    const val DEVELOPED_AT = 0.6

    /** Below this it clearly sides with "undeveloped". */
    const val LAGGING_BELOW = 0.35

    fun development(score: Double): Development = when {
        score >= DEVELOPED_AT -> Development.DEVELOPED
        score < LAGGING_BELOW -> Development.LAGGING
        else -> Development.AVERAGE
    }

    /** Below this share of bare skin, a group's region is clothing and is not judged. */
    const val MIN_VISIBLE = 0.5

    /**
     * A group ahead of the rest of this physique by at least this much counts as a relative
     * strength even below [DEVELOPED_AT] — "your back is your best area" is true and useful
     * for someone whose whole physique is average.
     */
    private const val RELATIVE_LEAD = 0.08
    private const val RELATIVE_MIN = 0.45

    /**
     * Strengths and weak points from everything known about this body.
     *
     * **Only what the photo shows.** [hidden] groups — under clothing or out of frame — are
     * not scored, not ranked, and not named a weakness. The first version judged thighs in a
     * pair of shorts and put them first among the weak points; that was a verdict on the
     * shorts. The same goes for measured findings about a hidden group: a thigh girth taken
     * through cloth is the cloth's girth.
     *
     * **Every strength, not only the obvious one.** A group is a strength when the AI sees it
     * developed, when it clearly leads the rest of this physique, or when the measurements
     * say so ([measuredStrong]: proportions like chest-to-waist, which read the V-taper more
     * directly than any photo impression). When the AI and the tape disagree about a group,
     * the tape wins the strength and the group is not called weak.
     *
     * **Abs are never a relative strength.** The abdominal descriptions score every stomach a
     * little high — next to "a smooth stomach" any stomach looks firm — so abs count only
     * when the AI reads them developed outright.
     */
    fun report(
        scores: Map<MuscleGroup, Double>,
        goal: Goal,
        hidden: Set<MuscleGroup> = emptySet(),
        measuredStrong: Map<MuscleGroup, String> = emptyMap(),
        measuredWeak: Map<MuscleGroup, String> = emptyMap(),
    ): PhysiqueReport? {
        val read = scores
            .filterKeys { it !in hidden }
            .filterValues { it.isFinite() }
            .map { (group, score) -> MuscleScore(group, score.coerceIn(0.0, 1.0), development(score)) }
            .sortedByDescending { it.score }
        if (read.isEmpty() && measuredStrong.isEmpty()) return null

        val mean = read.map { it.score }.average().takeIf { it.isFinite() } ?: 0.0
        val evidence = linkedMapOf<MuscleGroup, String>()
        read.filter { it.development == Development.DEVELOPED }.forEach {
            evidence[it.group] = "Looks developed to the AI (${pct(it.score)}/100)."
        }
        read.filter {
            it.group != MuscleGroup.ABS && it.group !in evidence &&
                it.score >= RELATIVE_MIN && it.score >= mean + RELATIVE_LEAD
        }.forEach {
            evidence[it.group] = "Ahead of the rest of your physique (${pct(it.score)}/100 against an average of ${pct(mean)})."
        }
        measuredStrong.filterKeys { it !in hidden }.forEach { (group, why) ->
            evidence[group] = evidence[group]?.let { "$it $why" } ?: why
        }
        val strengths = evidence.keys.toList()

        // Need: how far a group is from developed, weighted by how much the goal cares, with
        // a nudge when the measurements agree it is behind.
        val scoreOf = read.associate { it.group to it.score }
        val candidates = (read.filter { it.development != Development.DEVELOPED }.map { it.group } +
            measuredWeak.keys).distinct().filter { it !in strengths && it !in hidden }
        val weaknesses = candidates
            .sortedByDescending { g ->
                importance(goal, g) * (1.0 - (scoreOf[g] ?: 0.4)) + if (g in measuredWeak) 0.1 else 0.0
            }
            .take(MAX_WEAKNESSES)

        return PhysiqueReport(
            goal = goal,
            scores = read,
            strengths = strengths,
            weaknesses = weaknesses,
            focus = weaknesses.map { FocusAdvice(it, why(goal, it), how(goal, it)) },
            summary = summary(goal, weaknesses.isEmpty(), strengths.isEmpty()),
            hidden = hidden.sortedBy { it.ordinal },
            strengthEvidence = evidence,
            weaknessEvidence = measuredWeak.filterKeys { it in weaknesses },
        )
    }

    /** The groups a scan's part mask shows too little skin of to judge. */
    fun hiddenGroups(visibility: Map<MuscleGroup, Double>, regions: Set<MuscleGroup>): Set<MuscleGroup> =
        if (visibility.isEmpty()) {
            emptySet()
        } else {
            regions.filter { (visibility[it] ?: 0.0) < MIN_VISIBLE }.toSet()
        }

    private fun pct(v: Double) = (v * 100).toInt()

    private const val MAX_WEAKNESSES = 2

    /** How much each group matters to each goal, 0 to 1. */
    fun importance(goal: Goal, group: MuscleGroup): Double = when (goal) {
        Goal.HYPERTROPHY -> if (group == MuscleGroup.ABS) 0.6 else 1.0
        Goal.STRENGTH -> when (group) {
            MuscleGroup.LEGS, MuscleGroup.V_TAPER -> 1.0
            MuscleGroup.CHEST, MuscleGroup.SHOULDERS -> 0.8
            MuscleGroup.ARMS, MuscleGroup.ABS -> 0.5
        }
        Goal.CUT, Goal.MAKE_WEIGHT -> if (group == MuscleGroup.ABS) 1.0 else 0.6
        Goal.RECOMP -> if (group == MuscleGroup.ABS) 0.8 else 0.9
    }

    private fun summary(goal: Goal, balanced: Boolean, noStrengths: Boolean): String = when {
        noStrengths && goal != Goal.CUT && goal != Goal.MAKE_WEIGHT ->
            "No group stands out yet — a full-body programme will build the base fastest, " +
                "starting with the gaps below."
        else -> goalSummary(goal, balanced)
    }

    private fun goalSummary(goal: Goal, balanced: Boolean): String = when (goal) {
        Goal.HYPERTROPHY -> if (balanced) {
            "Balanced for building size — keep progressing every group."
        } else {
            "Building size: bring up the lagging groups first, so the physique grows in proportion."
        }
        Goal.STRENGTH -> "Strength: the legs, back and pressing muscles move the big lifts, " +
            "so those gaps come first."
        Goal.CUT -> "Cutting: abdominal definition is what the cut is judged by; everything " +
            "else is muscle to keep while the fat comes off."
        Goal.RECOMP -> "Recomposition: build the lagging groups while definition improves."
        Goal.MAKE_WEIGHT -> "Making weight: keep the muscle you have while the scale comes down."
    }

    private fun why(goal: Goal, group: MuscleGroup): String = when (goal) {
        Goal.STRENGTH -> when (group) {
            MuscleGroup.LEGS -> "Your legs drive the squat and the deadlift."
            MuscleGroup.V_TAPER -> "Your lats and upper back hold the bar in the deadlift and " +
                "stabilise the bench."
            MuscleGroup.CHEST -> "Your chest moves the bench press."
            MuscleGroup.SHOULDERS -> "Your shoulders drive the overhead press and support the bench."
            MuscleGroup.ARMS -> "Your triceps lock out every press."
            MuscleGroup.ABS -> "A strong trunk is what lets you brace under a heavy bar."
        }
        Goal.CUT, Goal.MAKE_WEIGHT -> if (group == MuscleGroup.ABS) {
            "Your midsection still reads soft — this is where the cut shows last."
        } else {
            "It reads behind the rest; train it hard through the deficit so you lose fat here, " +
                "not muscle."
        }
        Goal.HYPERTROPHY, Goal.RECOMP -> if (group == MuscleGroup.ABS) {
            "Your abs don't show yet; they need both muscle and a lower body fat."
        } else {
            "It reads behind the rest of your physique."
        }
    }

    private fun how(goal: Goal, group: MuscleGroup): String = when (group) {
        MuscleGroup.SHOULDERS -> "Overhead press and lateral raises, 10–20 hard sets a week. " +
            "The side delts are what widen the frame."
        MuscleGroup.CHEST -> "Bench or dumbbell press plus an incline press, with dips or " +
            "flyes, 10–20 sets a week."
        MuscleGroup.ARMS -> "Curls and triceps extensions or close-grip press, 8–14 sets " +
            "each a week on top of your pressing and pulling."
        MuscleGroup.ABS -> if (goal == Goal.CUT || goal == Goal.MAKE_WEIGHT) {
            "Keep the deficit going — abs show when body fat drops. Add hanging leg raises " +
                "and cable crunches to thicken them."
        } else {
            "Hanging leg raises and weighted cable crunches, 6–10 sets a week. They show as " +
                "body fat comes down."
        }
        MuscleGroup.V_TAPER -> "Pull-ups, lat pulldowns and rows, 10–20 sets a week. Wider " +
            "lats make the waist look narrower."
        MuscleGroup.LEGS -> "Squats or leg press, Romanian deadlifts and lunges, 10–20 sets " +
            "a week."
    }
}
