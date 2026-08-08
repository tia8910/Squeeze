package com.squeeze.core.render

import com.squeeze.core.model.Circumferences
import com.squeeze.core.model.Profile
import com.squeeze.core.model.Sex
import kotlin.math.PI
import kotlin.math.sqrt

/** Which way the figure is facing. */
enum class BodyView(val label: String) {
    FRONT("Front"),
    SIDE("Side"),
    BACK("Back"),
}

/**
 * A point in figure space.
 *
 * [x] is measured from the body's midline and [y] downward from the crown, both in units of
 * the person's own stature — so the figure is scale-free and the renderer only needs to know
 * how many pixels tall to draw it. x is positive to the figure's left in [BodyView.FRONT] and
 * forward (the direction it is facing) in [BodyView.SIDE].
 */
data class FigurePoint(val x: Double, val y: Double)

/**
 * A drawable body, built from one record's measurements.
 *
 * [outline] holds closed polygons meant to be filled in a single colour and allowed to
 * overlap — head, neck, torso, arms, legs. Building them separately rather than tracing one
 * continuous silhouette is what keeps this readable: an arm is a shape, not a detour in the
 * torso's boundary.
 *
 * [detail] holds open polylines drawn as thin lines on top — a collarbone, a midline, the
 * spine on a back view. They carry no measurement and exist so the three views are
 * distinguishable at thumbnail size.
 */
data class BodyFigure(
    val view: BodyView,
    val outline: List<List<FigurePoint>>,
    val detail: List<List<FigurePoint>>,
    /**
     * Sites drawn from population proportions rather than from this person's measurements.
     *
     * Surfaced so the UI can say so. A drawing is far more persuasive than a table, and a
     * figure whose thighs came from a lookup table must not be allowed to imply that the
     * thighs were measured.
     */
    val estimatedSites: List<String>,
)

/**
 * Draws the body a record describes.
 *
 * The record already holds everything needed: a stature, a set of girths, and a body fat
 * percentage. What it does not hold is any way to *look* at them, and a column of
 * centimetres is the one form in which a change of two centimetres at the waist is invisible.
 * A user who wants to see their own numbers as a body should not have to photograph
 * themselves again, and — this app having no internet permission at all — cannot send them
 * anywhere to have a picture made.
 *
 * So the figure is constructed, not generated: every width here is either a measurement of
 * this person or a documented population proportion, and [BodyFigure.estimatedSites] says
 * which is which. Nothing is invented to make the drawing look better.
 *
 * **What this is not.** It is not a likeness. Two people with identical girths are drawn
 * identically, and the figure has no face for that reason — a portrait would claim a
 * resemblance the measurements cannot support. What it does show truthfully is proportion:
 * where the mass sits, how the waist compares to the chest, and how those change between two
 * records.
 */
object BodyFigureBuilder {

    // --- Heights, as fractions of stature measured down from the crown ---------------------
    //
    // Standard adult proportions (Drillis & Contini; NASA-STD-3000 anthropometry). They vary
    // between people by a few per cent and are not measured by this app, which is acceptable
    // for the vertical axis in a way it would not be for the horizontal one: the figure's
    // job is to show girth, and stretching a torso segment by three per cent does not change
    // what the reader takes from it.

    private const val CROWN = 0.000
    private const val CHIN = 0.130
    private const val SHOULDER = 0.190
    private const val ARMPIT = 0.245
    private const val CHEST = 0.285
    private const val CROTCH = 0.520
    private const val ELBOW = 0.375
    private const val WRIST = 0.515
    private const val KNEE = 0.715
    private const val CALF = 0.780
    private const val ANKLE = 0.950
    private const val FLOOR = 1.000

    /**
     * Where the waist sits, which is not the same place in both sexes.
     *
     * A woman's narrowest trunk level is higher — nearer the tenth rib than the navel — and
     * her widest hip level is lower, because the flare is at the greater trochanter rather
     * than at the iliac crest. Together those two shifts are most of what makes a female
     * outline read as female, and getting them wrong draws a woman as a slightly narrower
     * man, which is exactly the thing this figure exists not to do.
     */
    private fun waistLevel(sex: Sex) = if (sex == Sex.MALE) 0.380 else 0.368

    private fun hipLevel(sex: Sex) = if (sex == Sex.MALE) 0.470 else 0.487

    private const val HEAD_HALF_WIDTH = 0.044
    private const val HEAD_HALF_DEPTH = 0.058
    private const val FOOT_LENGTH = 0.152

    // --- Cross-section shapes -------------------------------------------------------------
    //
    // A circumference is one number and a cross-section needs two, so each site carries the
    // ratio of its width to its depth. These are population means: the trunk is wider than it
    // is deep at every level, most so at the hip. Getting one wrong tilts width against depth
    // at that site but cannot change the girth, because both axes are solved from the
    // measured circumference — the error shows up as a body slightly too wide and equally too
    // shallow, which is the mildest way for this to be wrong.

    private fun chestWidthToDepth(sex: Sex) = if (sex == Sex.MALE) 1.42 else 1.24
    private fun waistWidthToDepth(sex: Sex) = if (sex == Sex.MALE) 1.28 else 1.22
    private fun hipWidthToDepth(sex: Sex) = if (sex == Sex.MALE) 1.35 else 1.45

    /**
     * Half-widths of the shoulders, as a fraction of stature.
     *
     * Biacromial breadth runs about 0.245 of stature in men and 0.222 in women, and the
     * silhouette is wider than the skeleton by the deltoids. Not measured from the record
     * because shoulder girth is not one of the app's sites — which is why the shoulders are
     * always reported as estimated, even on a complete tape entry.
     */
    private fun shoulderHalfWidth(sex: Sex): Double =
        if (sex == Sex.MALE) 0.245 * 1.12 / 2.0 else 0.222 * 1.12 / 2.0

    /**
     * Girths for a site the record does not have, as a fraction of stature.
     *
     * Population means, used so that a record holding only a waist still draws a whole body
     * rather than a torso floating above nothing. Every site filled in this way is named in
     * [BodyFigure.estimatedSites].
     */
    private fun defaultGirth(site: String, sex: Sex): Double = when (site) {
        "Neck" -> if (sex == Sex.MALE) 0.213 else 0.196
        "Chest" -> if (sex == Sex.MALE) 0.560 else 0.530
        "Hip" -> if (sex == Sex.MALE) 0.545 else 0.575
        "Thigh" -> if (sex == Sex.MALE) 0.320 else 0.330
        "Arm" -> if (sex == Sex.MALE) 0.180 else 0.165
        "Calf" -> if (sex == Sex.MALE) 0.210 else 0.205
        else -> 0.0
    }

    /**
     * Solves an ellipse's axes from its perimeter and its width-to-depth ratio.
     *
     * Ramanujan's approximation for the perimeter is exact enough here — under 0.02% error at
     * these eccentricities — and being homogeneous in the axes it inverts by simple scaling:
     * find the perimeter of the unit-width ellipse with this ratio, then scale.
     *
     * @return width and depth, in the same unit as [perimeter]
     */
    internal fun ellipseAxes(perimeter: Double, widthToDepth: Double): Pair<Double, Double> {
        val a = 1.0
        val b = 1.0 / widthToDepth
        val unit = PI * (3.0 * (a + b) - sqrt((3.0 * a + b) * (a + 3.0 * b)))
        val scale = perimeter / unit
        return (2.0 * a * scale) to (2.0 * b * scale)
    }

    /**
     * How much of the trunk's depth lies ahead of the midline at the waist.
     *
     * The one place body fat changes the drawing directly rather than through a girth. Two
     * people can carry the same waist circumference with the belly forward or with the mass
     * distributed round the flank, and it is the leaner of the two whose profile is flatter
     * in front. Below runs the interpolation, from a flat front at ten per cent to a clearly
     * protruding one at thirty-five.
     */
    internal fun bellyForwardFraction(bodyFatPercent: Double?, sex: Sex): Double {
        // The same protrusion happens at a higher percentage in women, who carry more
        // essential fat and less of the remainder viscerally. Reading a female record on the
        // male scale would draw every woman with a belly she does not have.
        val lean = if (sex == Sex.MALE) 10.0 else 18.0
        val high = if (sex == Sex.MALE) 35.0 else 43.0
        val percent = (bodyFatPercent ?: (if (sex == Sex.MALE) 20.0 else 28.0))
            .coerceIn(lean, high)
        return 0.50 + (percent - lean) / (high - lean) * 0.12
    }

    /**
     * Builds one view.
     *
     * @return null when the record has no waist, which is the one site with no substitute.
     *   Everything else can be filled from proportions without the figure ceasing to be about
     *   this person; a waist cannot, because the waist is the measurement the reader came for.
     */
    fun build(
        profile: Profile,
        circumferences: Circumferences,
        bodyFatPercent: Double?,
        view: BodyView,
    ): BodyFigure? {
        val height = profile.heightCm
        if (height <= 0.0) return null
        val waistCm = circumferences.waistCm ?: return null

        val estimated = mutableListOf<String>()

        /** A girth as a fraction of stature, measured if present and noted if not. */
        fun girth(site: String, measured: Double?): Double {
            measured?.let { return it / height }
            estimated += site
            return defaultGirth(site, profile.sex)
        }

        val neck = girth("Neck", circumferences.neckCm)
        val chest = girth("Chest", circumferences.chestCm)
        val hip = girth("Hip", circumferences.hipCm)
        val thigh = girth("Thigh", circumferences.thighCm)
        val arm = girth("Arm", circumferences.armCm)
        val calf = girth("Calf", circumferences.calfCm)
        val waist = waistCm / height

        // Shoulders are never measured by this app; say so once, here.
        estimated += "Shoulders"

        val (chestW, chestD) = ellipseAxes(chest, chestWidthToDepth(profile.sex))
        val (waistW, waistD) = ellipseAxes(waist, waistWidthToDepth(profile.sex))
        val (hipW, hipD) = ellipseAxes(hip, hipWidthToDepth(profile.sex))

        // Limbs and neck are near enough circular that a ratio would be false precision.
        val neckHalf = neck / PI / 2.0
        val armHalf = arm / PI / 2.0
        val thighHalf = thigh / PI / 2.0
        val calfHalf = calf / PI / 2.0

        val shoulderHalf = shoulderHalfWidth(profile.sex)

        val figure = when (view) {
            BodyView.SIDE -> sideFigure(
                sex = profile.sex,
                neckHalf = neckHalf,
                chestHalfDepth = chestD / 2.0,
                waistHalfDepth = waistD / 2.0,
                hipHalfDepth = hipD / 2.0,
                armHalf = armHalf,
                thighHalf = thighHalf,
                calfHalf = calfHalf,
                bellyForward = bellyForwardFraction(bodyFatPercent, profile.sex),
            )

            else -> frontFigure(
                view = view,
                sex = profile.sex,
                neckHalf = neckHalf,
                shoulderHalf = shoulderHalf,
                chestHalf = chestW / 2.0,
                waistHalf = waistW / 2.0,
                hipHalf = hipW / 2.0,
                armHalf = armHalf,
                thighHalf = thighHalf,
                calfHalf = calfHalf,
            )
        }

        return figure.copy(estimatedSites = estimated.distinct())
    }

    // --- Front and back -------------------------------------------------------------------

    /**
     * The coronal view, which front and back share exactly.
     *
     * They share it because the measurements say they must: a girth is a loop, and nothing in
     * a record distinguishes the front half of one from the back half. Drawing the back
     * narrower — as an illustrator would — would be inventing a difference the data does not
     * contain. What separates the two views here is only the detail lines, and those are
     * decoration and marked as such.
     */
    private fun frontFigure(
        view: BodyView,
        sex: Sex,
        neckHalf: Double,
        shoulderHalf: Double,
        chestHalf: Double,
        waistHalf: Double,
        hipHalf: Double,
        armHalf: Double,
        thighHalf: Double,
        calfHalf: Double,
    ): BodyFigure {
        val head = ellipse(0.0, (CROWN + CHIN) / 2.0, HEAD_HALF_WIDTH, (CHIN - CROWN) / 2.0)

        val neckShape = listOf(
            FigurePoint(-neckHalf, CHIN - 0.01),
            FigurePoint(neckHalf, CHIN - 0.01),
            FigurePoint(neckHalf, SHOULDER),
            FigurePoint(-neckHalf, SHOULDER),
        )

        val waistY = waistLevel(sex)
        val hipY = hipLevel(sex)

        // Down the figure's right edge and back up its left. The armpit is where the torso
        // stops following the shoulder and starts following the ribcage.
        //
        // The extra point between waist and hip is what stops a woman's outline reading as a
        // cone: the female hip flares below the waist and comes back in above the thigh, and
        // a straight line between the two levels draws the flare away. In men the same point
        // sits almost exactly on that line and changes nothing.
        val flareY = waistY + (hipY - waistY) * 0.55
        val flareHalf = if (sex == Sex.MALE) {
            waistHalf + (hipHalf - waistHalf) * 0.55
        } else {
            waistHalf + (hipHalf - waistHalf) * 0.74
        }

        // The first two points are the deltoid cap. A single vertex at the shoulder line
        // draws a spike — the neck-to-shoulder edge is far steeper than the shoulder-to-
        // armpit one, and the corner between them reads as a shoulder pad.
        val torso = mirrored(
            listOf(
                neckHalf * 1.30 to SHOULDER - 0.015,
                shoulderHalf * 0.86 to SHOULDER + 0.008,
                shoulderHalf to SHOULDER + 0.040,
                shoulderHalf * 0.93 to ARMPIT,
                chestHalf to CHEST,
                waistHalf to waistY,
                flareHalf to flareY,
                hipHalf to hipY,
                hipHalf * 0.96 to CROTCH,
            ),
        )

        // Tracking the trunk's own outline, always just outside it.
        //
        // Not a stylistic choice. Arms drawn hanging straight down sit exactly over the
        // narrowest part of the torso, and their union with it is a rectangle — which erased
        // the taper completely on the first version of this figure, drawing a lean man and a
        // heavy one as the same box. Following the body keeps a gap at the waist whatever
        // the waist happens to be, which is the one thing the reader is here to see.
        val arms = listOf(1, -1).map { side ->
            limb(
                listOf(
                    side * (shoulderHalf - armHalf) to SHOULDER + 0.040,
                    side * (maxOf(chestHalf, waistHalf) + armHalf * 1.30) to ELBOW,
                    side * (hipHalf + armHalf * 1.35) to WRIST,
                ),
                listOf(armHalf, armHalf * 0.78, armHalf * 0.55),
            )
        }

        val legs = listOf(1, -1).flatMap { side ->
            val hipX = side * hipHalf * 0.48
            val ankleX = side * hipHalf * 0.30
            listOf(
                limb(
                    listOf(
                        hipX to CROTCH - 0.02,
                        (hipX * 0.85) to KNEE,
                        (hipX * 0.7 + ankleX * 0.3) to CALF,
                        ankleX to ANKLE,
                    ),
                    listOf(thighHalf, thighHalf * 0.66, calfHalf, calfHalf * 0.55),
                ),
                // A separate polygon. Appended to the leg it closed through the leg's own
                // edge and filled as a bow tie.
                foot(ankleX, side),
            )
        }

        val detail = if (view == BodyView.BACK) {
            listOf(
                // The spine, and the inner borders of the scapulae.
                listOf(FigurePoint(0.0, SHOULDER + 0.03), FigurePoint(0.0, waistY + 0.01)),
                listOf(
                    FigurePoint(-chestHalf * 0.55, ARMPIT + 0.01),
                    FigurePoint(-chestHalf * 0.28, CHEST + 0.03),
                ),
                listOf(
                    FigurePoint(chestHalf * 0.55, ARMPIT + 0.01),
                    FigurePoint(chestHalf * 0.28, CHEST + 0.03),
                ),
            )
        } else {
            listOf(
                // Collarbones and the abdominal midline.
                listOf(
                    FigurePoint(-shoulderHalf * 0.62, SHOULDER + 0.012),
                    FigurePoint(0.0, SHOULDER + 0.026),
                    FigurePoint(shoulderHalf * 0.62, SHOULDER + 0.012),
                ),
                listOf(
                    FigurePoint(0.0, CHEST + 0.02),
                    FigurePoint(0.0, waistY + 0.02),
                ),
            )
        }

        return BodyFigure(view, listOf(head, neckShape, torso) + arms + legs, detail, emptyList())
    }

    // --- Side -----------------------------------------------------------------------------

    /**
     * The sagittal view, facing the figure's left — the direction x is positive in.
     *
     * This is the view a front-only scan cannot photograph, and the one where a waist
     * measurement says the most: the same girth drawn front-on is a width, but drawn side-on
     * it is the belly, which is where the reader is actually looking.
     */
    private fun sideFigure(
        sex: Sex,
        neckHalf: Double,
        chestHalfDepth: Double,
        waistHalfDepth: Double,
        hipHalfDepth: Double,
        armHalf: Double,
        thighHalf: Double,
        calfHalf: Double,
        bellyForward: Double,
    ): BodyFigure {
        val waistY = waistLevel(sex)
        val hipY = hipLevel(sex)

        // How much of each level's depth lies in front of the midline. The chest leads, the
        // waist follows body fat, and the hip sits back because the glutes are behind the
        // axis the trunk is measured about.
        //
        // The chest fraction is where the female profile differs most: the bust puts a much
        // larger share of the chest's depth in front, and the same measured girth drawn on
        // the male fraction gives a woman a flat profile and a thickened back.
        val chestForward = if (sex == Sex.MALE) 0.55 else 0.66
        val hipForward = if (sex == Sex.MALE) 0.40 else 0.36

        val front = listOf(
            chestHalfDepth * 2.0 * chestForward to CHEST,
            waistHalfDepth * 2.0 * bellyForward to waistY,
            hipHalfDepth * 2.0 * hipForward to hipY,
        )
        val back = listOf(
            -chestHalfDepth * 2.0 * (1.0 - chestForward) to CHEST,
            -waistHalfDepth * 2.0 * (1.0 - bellyForward) to waistY,
            -hipHalfDepth * 2.0 * (1.0 - hipForward) to hipY,
        )

        val shoulderFront = chestHalfDepth * 2.0 * 0.50
        val shoulderBack = -chestHalfDepth * 2.0 * 0.42

        val head = ellipse(
            HEAD_HALF_DEPTH * 0.12,
            (CROWN + CHIN) / 2.0,
            HEAD_HALF_DEPTH,
            (CHIN - CROWN) / 2.0,
        )

        val neckShape = listOf(
            FigurePoint(-neckHalf * 0.9, CHIN - 0.01),
            FigurePoint(neckHalf * 0.9, CHIN - 0.01),
            FigurePoint(neckHalf * 0.8, SHOULDER),
            FigurePoint(-neckHalf * 1.1, SHOULDER),
        )

        val torso = buildList {
            // Capped at both ends for the same reason the front view is: a single vertex on
            // the shoulder line draws a corner where a body has a curve.
            add(FigurePoint(shoulderFront * 0.62, SHOULDER - 0.015))
            add(FigurePoint(shoulderFront, SHOULDER + 0.030))
            front.forEach { (x, y) -> add(FigurePoint(x, y)) }
            add(FigurePoint(hipHalfDepth * 2.0 * 0.34, CROTCH))
            add(FigurePoint(-hipHalfDepth * 2.0 * 0.52, CROTCH))
            back.reversed().forEach { (x, y) -> add(FigurePoint(x, y)) }
            add(FigurePoint(shoulderBack, SHOULDER + 0.030))
            add(FigurePoint(shoulderBack * 0.62, SHOULDER - 0.015))
        }

        // One arm, hanging inside the trunk's own profile — which is where it is in life,
        // and so mostly invisible in silhouette. Kept just inside deliberately: drawn any
        // further forward it adds a bulge at the waist that the reader would take for a
        // belly, and the belly is the one thing this view exists to show honestly.
        val arm = limb(
            listOf(
                shoulderFront * 0.50 to SHOULDER + 0.040,
                waistHalfDepth * 2.0 * bellyForward * 0.62 to ELBOW,
                hipHalfDepth * 2.0 * hipForward * 0.70 to WRIST,
            ),
            listOf(armHalf, armHalf * 0.78, armHalf * 0.55),
        )

        val leg = limb(
            listOf(
                -hipHalfDepth * 0.18 to CROTCH - 0.02,
                0.0 to KNEE,
                -calfHalf * 0.5 to CALF,
                0.0 to ANKLE,
            ),
            listOf(thighHalf, thighHalf * 0.66, calfHalf, calfHalf * 0.55),
        )

        val detail = listOf(
            // The line of the back, which is the profile's most readable feature.
            listOf(
                FigurePoint(shoulderBack * 0.82, SHOULDER + 0.02),
                FigurePoint(-chestHalfDepth * 2.0 * 0.38, CHEST + 0.02),
                FigurePoint(-waistHalfDepth * 2.0 * (1.0 - bellyForward) * 0.80, waistY),
            ),
        )

        return BodyFigure(
            BodyView.SIDE,
            listOf(head, neckShape, torso, arm, leg, sideFoot(0.0)),
            detail,
            emptyList(),
        )
    }

    // --- Shape helpers --------------------------------------------------------------------

    private fun ellipse(cx: Double, cy: Double, rx: Double, ry: Double, steps: Int = 24) =
        (0 until steps).map { step ->
            val angle = 2.0 * PI * step / steps
            FigurePoint(cx + rx * kotlin.math.cos(angle), cy + ry * kotlin.math.sin(angle))
        }

    /** Turns half-widths down one side into a closed, symmetric outline. */
    private fun mirrored(halfWidths: List<Pair<Double, Double>>): List<FigurePoint> =
        halfWidths.map { (x, y) -> FigurePoint(x, y) } +
            halfWidths.reversed().map { (x, y) -> FigurePoint(-x, y) }

    /**
     * A tapered tube through a centreline.
     *
     * Walks down one side offsetting by the half-width at each joint, then back up the other.
     * Good enough for a limb, and deliberately not a swept curve: at the size these are drawn
     * the difference is invisible and the extra machinery is not.
     */
    private fun limb(
        centreline: List<Pair<Double, Double>>,
        halfWidths: List<Double>,
    ): List<FigurePoint> {
        val right = centreline.mapIndexed { index, (x, y) ->
            FigurePoint(x + halfWidths[index], y)
        }
        val left = centreline.mapIndexed { index, (x, y) ->
            FigurePoint(x - halfWidths[index], y)
        }.reversed()
        return right + left
    }

    private fun foot(ankleX: Double, side: Int): List<FigurePoint> = listOf(
        FigurePoint(ankleX - 0.022, ANKLE - 0.005),
        FigurePoint(ankleX + 0.022, ANKLE - 0.005),
        FigurePoint(ankleX + 0.022, FLOOR),
        FigurePoint(ankleX - 0.022 + side * -0.012, FLOOR),
    )

    private fun sideFoot(ankleX: Double): List<FigurePoint> = listOf(
        FigurePoint(ankleX - 0.030, ANKLE - 0.008),
        FigurePoint(ankleX + 0.026, ANKLE - 0.008),
        FigurePoint(ankleX + FOOT_LENGTH * 0.62, FLOOR),
        FigurePoint(ankleX - 0.040, FLOOR),
    )
}
