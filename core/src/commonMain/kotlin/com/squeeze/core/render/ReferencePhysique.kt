package com.squeeze.core.render

import com.squeeze.core.model.Sex
import kotlin.math.abs

/**
 * Which reference photograph shows a body at this composition.
 *
 * The drawn figure ([BodyFigureBuilder]) is this person: every width in it came from their
 * record. What it cannot be is photographic, and a photograph is what makes someone say "yes,
 * that is roughly me" — which is the whole use for it. Two different jobs, so two different
 * pictures: the drawing shows *your measurements*, the reference photograph shows *what this
 * number looks like on a body*.
 *
 * The photographs are not of the user and never claim to be. They are the same idea as the
 * appearance bands in `VisualAssessment`, which already ask the user to place themselves
 * against described physiques — this gives those descriptions pictures, and lets the
 * comparison run the other way as a check on the scan.
 *
 * **No image is generated at run time.** This app holds no `INTERNET` permission, so nothing
 * can be fetched, and an on-device diffusion model would add a gigabyte to the download to
 * produce a face that belongs to nobody. The photographs ship in the APK, chosen once, and
 * this object does nothing but pick the nearest one.
 */
object ReferencePhysique {

    /**
     * The body-fat levels the male reference set is shot at.
     *
     * Closely spaced where the appearance changes fastest. Between 8% and 15% a man goes from
     * visible separation to none, and the bands have to be tight enough that the nearest one
     * is recognisably the right one; above 30% four points either way looks much the same.
     */
    val MALE_BANDS = listOf(8, 12, 15, 20, 25, 30, 35)

    /**
     * The female set, shifted up and spaced the same way.
     *
     * Women carry roughly ten points more essential fat, so the visually equivalent physique
     * sits about that much higher — matching a woman against the male ladder would show her a
     * body ten points leaner than the one she has.
     */
    val FEMALE_BANDS = listOf(16, 20, 24, 28, 32, 38, 44)

    fun bands(sex: Sex): List<Int> = if (sex == Sex.MALE) MALE_BANDS else FEMALE_BANDS

    /**
     * The nearest band to [percent], or null when there is no estimate to match against.
     *
     * Nearest rather than bracketing: showing a body noticeably leaner than the reader is a
     * worse failure than showing one slightly heavier, and rounding in a fixed direction does
     * one or the other systematically.
     */
    fun bandFor(percent: Double?, sex: Sex): Int? {
        if (percent == null || percent.isNaN()) return null
        return bands(sex).minByOrNull { abs(it - percent) }
    }

    /**
     * The drawable name for one band, without extension.
     *
     * A name rather than a resource id so this stays in the platform-free module. The app
     * resolves it and falls back to the drawn figure when the asset is not present, which is
     * what happens on any build where the photograph set has not been added.
     */
    fun assetName(sex: Sex, band: Int): String =
        "physique_${if (sex == Sex.MALE) "male" else "female"}_$band"

    /** Every asset name the shipped set would contain, for both sexes. */
    fun allAssetNames(): List<String> =
        Sex.entries.flatMap { sex -> bands(sex).map { assetName(sex, it) } }
}
