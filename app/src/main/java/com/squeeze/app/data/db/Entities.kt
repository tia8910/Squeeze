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
)
