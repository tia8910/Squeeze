package com.squeeze.app.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A stored measurement session.
 *
 * [source] is persisted rather than derived because the trend engine weights observations
 * by how noisy their method is, and because mixing a tape reading with a photo estimate
 * without recording which was which silently corrupts the trend.
 */
@Entity(tableName = "measurements", indices = [Index("epochDay")])
data class MeasurementEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val epochDay: Long,
    val source: String,
    val weightKg: Double?,
    val neckCm: Double?,
    val waistCm: Double?,
    val hipCm: Double?,
    val chestCm: Double?,
    val thighCm: Double?,
    val armCm: Double?,
    val calfCm: Double?,
    val chestMm: Double?,
    val abdomenMm: Double?,
    val thighMm: Double?,
    val tricepsMm: Double?,
    val suprailiacMm: Double?,
    /** A DEXA or BodPod result entered by hand; anchors personal calibration. */
    val referenceBodyFatPercent: Double?,
    val note: String?,
    /**
     * Identifier of the encrypted scan photograph, when one was kept.
     *
     * Null for tape entries and for any scan recorded before photographs were stored. The
     * file lives in the app's private storage under this name; see ScanPhotoStore.
     */
    val photoId: String? = null,
    /**
     * The appearance band the user matched themselves to, if they did.
     *
     * Defaulted so existing construction sites and migrated rows both mean "not asked",
     * which is the truth for every measurement taken before this column existed.
     */
    val visualBodyFatPercent: Double? = null,
    /**
     * Body fat read from the silhouette's proportions alone; see SilhouetteBodyFat.
     *
     * Stored rather than recomputed because it needs the pixel width profile, which lives
     * only for the duration of the scan.
     */
    val shapeBodyFatPercent: Double? = null,
    /**
     * Body fat read from the abdomen's side-on depth; see AbdominalProfile.
     *
     * Present only when a side photograph was taken. Stored separately from the shape figure
     * because the two measure different axes and carry different offsets — the front view
     * measures the axis abdominal fat moves along least, which is why it needed a companion
     * rather than a correction.
     */
    val abdominalBodyFatPercent: Double? = null,
    /**
     * How wide the shape figure's interval was when it was recorded.
     *
     * Stored because it is the only thing that distinguishes a measurement of adiposity from
     * a bound, and it was being thrown away. The scan produced an estimate carrying ±9 when
     * the outline could not resolve the body; only the percentage was written, and the
     * repository rebuilt the estimate at the method's ordinary ±5 — so a reading that had
     * said "I cannot tell" re-entered the fusion claiming full precision, and outweighed
     * methods that had actually measured something.
     *
     * Null on rows written before this column existed. See PlateauPrior.isBounded for what
     * those fall back to.
     */
    val shapeStandardErrorPercent: Double? = null,
)

/**
 * A logged set.
 *
 * @param rir reps left in reserve as judged by the lifter. The programme prescribes RIR
 *   rather than a load, so this is what closes the loop on whether the prescription landed.
 */
@Entity(tableName = "logged_sets", indices = [Index("epochDay"), Index("exerciseName")])
data class LoggedSetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val epochDay: Long,
    val exerciseName: String,
    val muscleGroup: String,
    val weightKg: Double,
    val reps: Int,
    val rir: Int?,
    val programWeekIndex: Int?,
)

/**
 * A generated training block, stored so it survives regeneration and can be exported.
 *
 * @param payload the mesocycle serialised as JSON. Programmes are portable files by
 *   design: sharing one is how a coach hands work to a client without a server, and how
 *   the app spreads without a referral backend.
 */
@Entity(tableName = "mesocycles")
data class MesocycleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val createdEpochDay: Long,
    val name: String,
    val goal: String,
    val payload: String,
    val isActive: Boolean,
)

/** The single-row user profile. */
@Entity(tableName = "profile")
data class ProfileEntity(
    @PrimaryKey val id: Int = 1,
    val heightCm: Double,
    val birthYear: Int,
    val sex: String,
    val trainingAge: String,
    val goal: String,
    val unitSystem: String,
    /** Target body fat and its deadline; see [com.squeeze.core.model.Profile]. */
    val targetBodyFatPercent: Double? = null,
    /** A bodyweight to arrive at. Null when the goal is not about weight. */
    val targetWeightKg: Double? = null,
    val targetEpochDay: Long? = null,
)

/**
 * One human judgement about one region of one scan photograph.
 *
 * Keyed by the hash of the image bytes rather than by the photo's storage id, and that is what
 * makes the corpus portable. A storage id means something only to the device that minted it; a
 * hash of the pixels means the same thing everywhere, so a labels file exported from this
 * phone can be checked against the same photographs on the machine that trains the model, and
 * a mislabelled file cannot quietly attach itself to the wrong judgement.
 *
 * The photograph itself never leaves the device. See com.squeeze.core.corpus.LabelFile.
 *
 * @param region the [com.squeeze.core.corpus.DefinitionRegion] name
 * @param visible whether separation is visible with the subject relaxed
 * @param unusable set when the photograph cannot answer the question at all. Stored rather
 *   than discarded, because how many photographs are unusable is itself worth measuring.
 */
@Entity(
    tableName = "definition_labels",
    primaryKeys = ["photoHash", "region"],
    indices = [Index("photoHash")],
)
data class DefinitionLabelEntity(
    val photoHash: String,
    val region: String,
    val capturedEpochDay: Long,
    val visible: Boolean,
    val unusable: Boolean,
    /** When the judgement was made, so a revision can be told from an original. */
    val labelledEpochDay: Long,
)
