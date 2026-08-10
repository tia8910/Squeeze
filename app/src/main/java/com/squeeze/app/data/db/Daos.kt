package com.squeeze.app.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface MeasurementDao {

    @Query("SELECT * FROM measurements ORDER BY epochDay ASC, id ASC")
    fun observeAll(): Flow<List<MeasurementEntity>>

    /** Reference scans only, used to fit personal calibration. */
    @Query("SELECT * FROM measurements WHERE referenceBodyFatPercent IS NOT NULL ORDER BY epochDay ASC")
    suspend fun referenceScans(): List<MeasurementEntity>

    @Query("SELECT * FROM measurements WHERE epochDay >= :sinceEpochDay ORDER BY epochDay ASC")
    suspend fun since(sinceEpochDay: Long): List<MeasurementEntity>

    /**
     * The most recent recorded bodyweight, if there is one.
     *
     * The scan needs it before the user has typed anything: what the outline reports on its
     * plateau depends on the body's build, and the last weight is a far better answer to
     * "what does this person weigh" than no answer at all. Overridden the moment a weight is
     * entered on the result screen.
     */
    @Query(
        """
        SELECT weightKg FROM measurements
        WHERE weightKg IS NOT NULL
        ORDER BY epochDay DESC, id DESC
        LIMIT 1
        """,
    )
    suspend fun latestWeightKg(): Double?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(measurement: MeasurementEntity): Long

    @Delete
    suspend fun delete(measurement: MeasurementEntity)

    @Query("DELETE FROM measurements")
    suspend fun deleteAll()
}

@Dao
interface WorkoutDao {

    @Query("SELECT * FROM logged_sets ORDER BY epochDay DESC, id DESC LIMIT :limit")
    fun observeRecent(limit: Int = 200): Flow<List<LoggedSetEntity>>

    @Query("SELECT * FROM logged_sets WHERE epochDay = :epochDay ORDER BY id ASC")
    fun observeDay(epochDay: Long): Flow<List<LoggedSetEntity>>

    /**
     * Best set for an exercise, ranked by weight then reps. Drives progression prompts:
     * the app suggests adding load once the user beats this at the prescribed RIR.
     */
    @Query(
        """
        SELECT * FROM logged_sets
        WHERE exerciseName = :exerciseName
        ORDER BY weightKg DESC, reps DESC
        LIMIT 1
        """,
    )
    suspend fun bestSet(exerciseName: String): LoggedSetEntity?

    @Query("SELECT COUNT(*) FROM logged_sets WHERE muscleGroup = :group AND epochDay >= :sinceEpochDay")
    suspend fun setCountSince(group: String, sinceEpochDay: Long): Int

    @Insert
    suspend fun insert(set: LoggedSetEntity): Long

    @Delete
    suspend fun delete(set: LoggedSetEntity)

    @Query("DELETE FROM logged_sets")
    suspend fun deleteAll()
}

@Dao
interface MesocycleDao {

    @Query("SELECT * FROM mesocycles WHERE isActive = 1 LIMIT 1")
    fun observeActive(): Flow<MesocycleEntity?>

    @Query("SELECT * FROM mesocycles ORDER BY createdEpochDay DESC")
    fun observeAll(): Flow<List<MesocycleEntity>>

    @Query("UPDATE mesocycles SET isActive = 0")
    suspend fun clearActive()

    @Insert
    suspend fun insert(mesocycle: MesocycleEntity): Long

    @Query("DELETE FROM mesocycles")
    suspend fun deleteAll()
}

@Dao
interface ProfileDao {

    @Query("SELECT * FROM profile WHERE id = 1")
    fun observe(): Flow<ProfileEntity?>

    @Query("SELECT * FROM profile WHERE id = 1")
    suspend fun get(): ProfileEntity?

    @Upsert
    suspend fun upsert(profile: ProfileEntity)

    @Query("DELETE FROM profile")
    suspend fun deleteAll()
}
