package com.squeeze.core.bodycomp

import com.squeeze.core.model.Circumferences
import com.squeeze.core.model.Sex
import com.squeeze.core.program.MuscleGroup
import com.squeeze.core.program.WeakPointAnalysis

/** Whether a finding is something the body has going for it, or something to work on. */
enum class FindingKind { STRENGTH, WEAKNESS }

/**
 * One thing this record says about the body, in the terms a coach would use.
 *
 * @param title the claim, short enough to scan in a list
 * @param detail why, naming the figure it came from so it can be checked against the panel
 */
data class BodyFinding(val kind: FindingKind, val title: String, val detail: String)

/**
 * What a record is good at and what it is not.
 *
 * This replaced a drawn figure of the body, and the reason is worth keeping. The drawing was
 * built from four girths and a body fat percentage, then filled out from population averages
 * for everything else — arms, calves, shoulders — so two different people with the same waist
 * were drawn identically, and its own caption had to say so. It occupied the most valuable
 * space on the screen to tell the reader something they already knew: what a body looks like.
 *
 * These findings use the same inputs and answer the question the drawing was standing in for.
 * A person looking at a record wants to know whether it is good news. Twenty numbers do not
 * say; two short lists do.
 *
 * **Nothing here is computed.** Every finding reads a figure the panel already produced and
 * the [ReferenceBand] already attached to it, so a finding cannot disagree with the card
 * underneath it — there is no second calculation to drift. That constraint is also why the
 * lists are short and why some records produce very few: a metric with no published reference
 * gets no verdict rather than an invented one.
 *
 * **[BandPosition] is not the verdict.** As [ReferenceBands] says in its own header, HIGH on
 * fat-free mass index is a good outcome and HIGH on waist-to-height is not. Each metric below
 * states its own direction, which is the whole substance of this file.
 */
object BodyFindings {

    /**
     * Chest-to-waist ratios that count as a marked taper, and as none.
     *
     * The only thresholds here not taken from a published band, and they are named as
     * conventional rather than dressed up. Chest-to-waist has no reference table of the kind
     * ACE publishes for body fat or the WHO for waist-to-hip; what exists is a training
     * convention, in which a chest around 1.4 times the waist reads as a clear V and a chest
     * within about 1.15 of it reads as a straight torso.
     *
     * They are used only to decide whether to *mention* the ratio, never to compute anything.
     * A number that would move an estimate has to be earned; a number that decides whether a
     * sentence appears is a different kind of claim, and this is the honest place to put an
     * unfitted constant if there is one at all.
     */
    const val MALE_STRONG_TAPER = 1.40
    const val MALE_FLAT_TAPER = 1.15
    const val FEMALE_STRONG_TAPER = 1.30
    const val FEMALE_FLAT_TAPER = 1.10

    /**
     * @param panel this record's analysis, or null when it has none
     * @param sex selects the taper convention; every other threshold is inside the bands
     * @return strengths first, then weak points, each in a fixed order; empty when the record
     *   holds too little to say anything, which is a legitimate outcome and not a failure
     */
    fun from(
        panel: CompositionPanel?,
        sex: Sex,
        circumferences: Circumferences? = null,
    ): List<BodyFinding> {
        if (panel == null) return emptyList()

        val byName = (panel.composition + panel.shape).associateBy { it.name }
        val findings = mutableListOf<BodyFinding>()

        // Body parts first, because they are what the reader can act on.
        //
        // Everything below this answers "how much fat is on this body". None of it answers
        // "which part of me is behind", which is the question someone training actually has
        // and the one a coach answers first. Ordered under the whole-body figures, the parts
        // were the last thing on a screen most people stop reading before the end.
        //
        // Proportional, never absolute, which is what makes it usable from a photograph whose
        // centimetres may be off: a ratio divides two measurements taken from one image at
        // one scale, so the scale error cancels. It says nothing at all when the girths it
        // needs are missing, which is the honest answer for a torso-framed scan rather than a
        // gap to fill with generalities.
        circumferences?.let { girths ->
            WeakPointAnalysis.strongPoints(girths, sex).take(MAX_PART_FINDINGS).forEach { group ->
                findings += strength(
                    "${label(group)} ahead of the rest of you",
                    "Measurably above the balanced proportion for your frame. Worth knowing " +
                        "when you cut: this is the part with the most to lose.",
                )
            }

            WeakPointAnalysis.analyse(girths, sex)
                .distinctBy { it.group }
                .take(MAX_PART_FINDINGS)
                .forEach { point ->
                    findings += weakness(
                        "${label(point.group)} behind the rest of you",
                        "${point.finding} ${point.prescription}",
                    )
                }
        }

        // Waist-to-height leads because it is the most direct thing here: a ratio of two
        // measured lengths, no equation between them, and the one figure that holds across
        // sexes, ages and ethnicities.
        byName["Waist-to-height"]?.let { metric ->
            when (metric.band?.label) {
                "Healthy" -> findings += strength(
                    "Waist under half your height",
                    "${metric.formatted()} — clear of the 0.5 boundary, which is the single " +
                        "best screen there is for central fat.",
                )

                "Slim" -> findings += strength(
                    "Narrow waist for your height",
                    "${metric.formatted()} — below the usual range, and well under the 0.5 " +
                        "boundary.",
                )

                "Increased", "High" -> findings += weakness(
                    "Waist is over half your height",
                    "${metric.formatted()} against a 0.5 target. Of everything on this " +
                        "screen, this is the number most worth moving.",
                )
            }
        }

        byName["Body fat"]?.let { metric ->
            // Read into a local because the branches use the band's own text. Matching on
            // `metric.band?.label` tells the compiler nothing about the property's
            // nullability, so `metric.band.detail` inside a branch would not compile.
            val band = metric.band
            when (band?.label) {
                "Athletic", "Fitness" -> findings += strength(
                    "Body fat in the ${band.label.lowercase()} range",
                    "${metric.formatted()} — " +
                        band.detail.replaceFirstChar { it.lowercase() },
                )

                "Above average" -> findings += weakness(
                    "Body fat above the typical range",
                    "${metric.formatted()}. Read it next to your waist-to-height, which says " +
                        "where the fat sits rather than how much of it there is.",
                )

                "Below essential" -> findings += weakness(
                    "Below the fat your body needs",
                    "${metric.formatted()}. This is not a target — it is a level competitors " +
                        "hold briefly and on purpose. Check the scan before acting on it.",
                )
            }
        }

        byName["Waist-to-hip"]?.let { metric ->
            when (metric.band?.label) {
                "Low risk" -> findings += strength(
                    "Fat is not carried centrally",
                    "Waist-to-hip ${metric.formatted()}. Both numbers come from one photo at " +
                        "one scale, so scale error cancels — this is among the most " +
                        "trustworthy things a scan produces.",
                )

                "High" -> findings += weakness(
                    "Fat sits around the middle",
                    "Waist-to-hip ${metric.formatted()}. This pattern matters more for " +
                        "metabolic risk than the total does.",
                )
            }
        }

        byName["Chest-to-waist"]?.let { metric ->
            val strong = if (sex == Sex.FEMALE) FEMALE_STRONG_TAPER else MALE_STRONG_TAPER
            val flat = if (sex == Sex.FEMALE) FEMALE_FLAT_TAPER else MALE_FLAT_TAPER

            when {
                metric.value >= strong -> findings += strength(
                    "Marked V-taper",
                    "Chest ${metric.formatted()} times your waist. It rises when you build " +
                        "your upper back and chest and when you lose from the waist, so it " +
                        "moves for two good reasons at once.",
                )

                metric.value < flat -> findings += weakness(
                    "Little taper through the torso",
                    "Chest ${metric.formatted()} times your waist. Upper-back and shoulder " +
                        "work moves this faster than anything done at the waist.",
                )
            }
        }

        // Last of the strengths and last of the weak points, deliberately. Lean mass is the
        // slowest thing on this screen to change and the one most worth knowing about, so it
        // is the note the reader leaves with.
        byName["FFMI"]?.let { metric ->
            val band = metric.band
            when (band?.label) {
                "Well trained", "Above average" -> findings += strength(
                    "Lean mass above the untrained norm",
                    "FFMI ${metric.formatted()}. ${band.detail}",
                )

                "Below average", "Average" -> findings += weakness(
                    "Lean mass is the lever here",
                    "FFMI ${metric.formatted()}, " +
                        band.detail.replaceFirstChar { it.lowercase() } +
                        " Resistance training moves this more reliably than anything else, " +
                        "and it moves slowly enough to be worth starting now.",
                )

                "Exceptional — check your inputs" -> findings += weakness(
                    "This reading needs checking",
                    "FFMI ${metric.formatted()}, above the level usually considered " +
                        "attainable drug-free. It is derived from your weight and your body " +
                        "fat, and an underestimated body fat inflates it directly.",
                )
            }
        }

        // Strengths before weak points, and within each the order they were added — body
        // parts first, then the whole-body figures. sortedBy is stable, so grouping by kind
        // does not disturb the ordering the block above chose.
        return findings.sortedBy { it.kind == FindingKind.WEAKNESS }
    }

    /**
     * The most body parts named in either column.
     *
     * A list of every proportion that missed is not a plan. Three is what a training block
     * can absorb, which is the same number WeakPointAnalysis prioritises for volume, and a
     * reader given eight lagging parts will act on none of them.
     */
    private const val MAX_PART_FINDINGS = 3

    /** The muscle group as a coach would say it, rather than as an enum constant. */
    private fun label(group: MuscleGroup): String = when (group) {
        MuscleGroup.CHEST -> "Chest"
        MuscleGroup.BACK -> "Back"
        MuscleGroup.QUADS -> "Quads"
        MuscleGroup.HAMSTRINGS -> "Hamstrings"
        MuscleGroup.GLUTES -> "Glutes"
        MuscleGroup.SHOULDERS -> "Shoulders"
        MuscleGroup.BICEPS -> "Biceps"
        MuscleGroup.TRICEPS -> "Triceps"
        MuscleGroup.CALVES -> "Calves"
        MuscleGroup.ABS -> "Abs"
    }

    private fun strength(title: String, detail: String) =
        BodyFinding(FindingKind.STRENGTH, title, detail)

    private fun weakness(title: String, detail: String) =
        BodyFinding(FindingKind.WEAKNESS, title, detail)
}
