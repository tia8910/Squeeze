package com.squeeze.app.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.aggregate.AggregateMetric
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.BodyFatRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.NutritionRecord
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.health.connect.client.units.Mass
import androidx.health.connect.client.units.Percentage
import com.squeeze.core.health.DailyActivity
import com.squeeze.core.workout.Sport
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.reflect.KClass

/** Whether Health Connect can be used on this phone, and what to offer if not. */
enum class HealthAvailability { AVAILABLE, NEEDS_UPDATE, NOT_SUPPORTED }

/**
 * The bridge to Health Connect — Android's on-device health store that watches, fitness apps
 * and nutrition apps all write to.
 *
 * **One integration instead of twenty.** A Pixel Watch or any Wear OS watch, a Galaxy Watch
 * through Samsung Health, Fitbit, Garmin Connect, Google Fit, MyFitnessPal, Cronometer — each
 * writes its steps, workouts, sleep, heart rate and meals into Health Connect, and this reads
 * them from there. The user picks, record type by record type, what the app may see.
 *
 * **Still no network.** Health Connect is a store on the phone reached over binder IPC, the
 * same way Play Billing is, so this adds no INTERNET permission and nothing leaves the device.
 *
 * **Never throws.** Every call returns null or false on any failure — the store missing, a
 * permission revoked in system settings between two calls — and the screen carries on without
 * the data rather than crashing.
 */
@Singleton
class HealthConnectManager @Inject constructor(private val context: Context) {

    private val client: HealthConnectClient? by lazy {
        if (availability() == HealthAvailability.AVAILABLE) {
            runCatching { HealthConnectClient.getOrCreate(context) }.getOrNull()
        } else {
            null
        }
    }

    fun availability(): HealthAvailability =
        when (runCatching { HealthConnectClient.getSdkStatus(context) }.getOrDefault(HealthConnectClient.SDK_UNAVAILABLE)) {
            HealthConnectClient.SDK_AVAILABLE -> HealthAvailability.AVAILABLE
            HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> HealthAvailability.NEEDS_UPDATE
            else -> HealthAvailability.NOT_SUPPORTED
        }

    /** Everything the app asks for. The user can grant any subset; each feature uses what it gets. */
    val permissions: Set<String> = (READ_TYPES.map { HealthPermission.getReadPermission(it) } +
        WRITE_TYPES.map { HealthPermission.getWritePermission(it) }).toSet()

    fun permissionContract() = PermissionController.createRequestPermissionResultContract()

    suspend fun granted(): Set<String> =
        runCatching { client?.permissionController?.getGrantedPermissions() }.getOrNull().orEmpty()

    suspend fun isConnected(): Boolean = granted().any { it in permissions }

    private fun canRead(granted: Set<String>, type: KClass<out androidx.health.connect.client.records.Record>) =
        HealthPermission.getReadPermission(type) in granted

    private fun canWrite(granted: Set<String>, type: KClass<out androidx.health.connect.client.records.Record>) =
        HealthPermission.getWritePermission(type) in granted

    /**
     * One calendar day, midnight to midnight in the phone's zone, from every source the user
     * allowed. Sleep is the night that ended that morning (18:00 the evening before to noon),
     * which is the night that affects the day.
     */
    suspend fun day(date: LocalDate = LocalDate.now()): DailyActivity? {
        val health = client ?: return null
        val granted = granted()
        if (granted.isEmpty()) return null
        val zone = ZoneId.systemDefault()
        val start = date.atStartOfDay(zone).toInstant()
        val end = if (date == LocalDate.now()) Instant.now() else date.plusDays(1).atStartOfDay(zone).toInstant()

        val metrics = buildSet<AggregateMetric<*>> {
            if (canRead(granted, StepsRecord::class)) add(StepsRecord.COUNT_TOTAL)
            if (canRead(granted, ActiveCaloriesBurnedRecord::class)) add(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL)
            if (canRead(granted, ExerciseSessionRecord::class)) add(ExerciseSessionRecord.EXERCISE_DURATION_TOTAL)
            if (canRead(granted, RestingHeartRateRecord::class)) add(RestingHeartRateRecord.BPM_AVG)
            if (canRead(granted, NutritionRecord::class)) {
                add(NutritionRecord.ENERGY_TOTAL)
                add(NutritionRecord.PROTEIN_TOTAL)
                add(NutritionRecord.TOTAL_CARBOHYDRATE_TOTAL)
                add(NutritionRecord.TOTAL_FAT_TOTAL)
            }
        }
        val result = if (metrics.isEmpty()) {
            null
        } else {
            runCatching { health.aggregate(AggregateRequest(metrics, TimeRangeFilter.between(start, end))) }.getOrNull()
        }

        val sleepMinutes = if (canRead(granted, SleepSessionRecord::class)) {
            val from = ZonedDateTime.of(date.minusDays(1).atTime(18, 0), zone).toInstant()
            val to = ZonedDateTime.of(date.atTime(12, 0), zone).toInstant()
            runCatching {
                health.aggregate(
                    AggregateRequest(setOf(SleepSessionRecord.SLEEP_DURATION_TOTAL), TimeRangeFilter.between(from, to)),
                )[SleepSessionRecord.SLEEP_DURATION_TOTAL]?.toMinutes()?.toInt()
            }.getOrNull()
        } else {
            null
        }

        return DailyActivity(
            epochDay = date.toEpochDay(),
            steps = result?.get(StepsRecord.COUNT_TOTAL),
            activeKcal = result?.get(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL)?.inKilocalories,
            exerciseMinutes = result?.get(ExerciseSessionRecord.EXERCISE_DURATION_TOTAL)?.toMinutes()?.toInt(),
            restingHeartRate = result?.get(RestingHeartRateRecord.BPM_AVG)?.toInt(),
            sleepMinutes = sleepMinutes?.takeIf { it > 0 },
            eatenKcal = result?.get(NutritionRecord.ENERGY_TOTAL)?.inKilocalories?.takeIf { it > 0 },
            proteinG = result?.get(NutritionRecord.PROTEIN_TOTAL)?.inGrams,
            carbsG = result?.get(NutritionRecord.TOTAL_CARBOHYDRATE_TOTAL)?.inGrams,
            fatG = result?.get(NutritionRecord.TOTAL_FAT_TOTAL)?.inGrams,
        )
    }

    /** The last [days] days, oldest first, for the week strip and the resting-pulse baseline. */
    suspend fun recent(days: Int = 7): List<DailyActivity> {
        if (client == null) return emptyList()
        val today = LocalDate.now()
        return (days - 1 downTo 0).mapNotNull { day(today.minusDays(it.toLong())) }
    }

    /**
     * Puts a logged session into Health Connect, so the watch app, Google Fit and anything
     * else the user relies on sees the gym session too. Ends now; starts [minutes] earlier.
     *
     * Named arguments throughout: the record constructors' parameter order differs between
     * Health Connect versions, and names keep this compiling across them.
     */
    suspend fun writeWorkout(sport: Sport, title: String, minutes: Int): Boolean {
        val health = client ?: return false
        if (!canWrite(granted(), ExerciseSessionRecord::class) || minutes <= 0) return false
        val end = Instant.now()
        val start = end.minusSeconds(minutes * 60L)
        val offset = ZoneId.systemDefault().rules.getOffset(end)
        return runCatching {
            health.insertRecords(
                listOf(
                    ExerciseSessionRecord(
                        startTime = start,
                        startZoneOffset = offset,
                        endTime = end,
                        endZoneOffset = offset,
                        exerciseType = exerciseType(sport),
                        title = title,
                    ),
                ),
            )
            true
        }.getOrDefault(false)
    }

    /** Weight and body fat from a saved measurement, for smart-scale and watch apps to share. */
    suspend fun writeBody(weightKg: Double?, bodyFatPercent: Double?): Boolean {
        val health = client ?: return false
        val granted = granted()
        val now = Instant.now()
        val offset = ZoneId.systemDefault().rules.getOffset(now)
        val records = buildList<androidx.health.connect.client.records.Record> {
            if (weightKg != null && weightKg in 20.0..400.0 && canWrite(granted, WeightRecord::class)) {
                add(WeightRecord(time = now, zoneOffset = offset, weight = Mass.kilograms(weightKg)))
            }
            if (bodyFatPercent != null && bodyFatPercent in 2.0..70.0 && canWrite(granted, BodyFatRecord::class)) {
                add(BodyFatRecord(time = now, zoneOffset = offset, percentage = Percentage(bodyFatPercent)))
            }
        }
        if (records.isEmpty()) return false
        return runCatching { health.insertRecords(records); true }.getOrDefault(false)
    }

    private fun exerciseType(sport: Sport): Int = when (sport) {
        Sport.STRENGTH -> ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING
        Sport.RUNNING -> ExerciseSessionRecord.EXERCISE_TYPE_RUNNING
        Sport.CYCLING -> ExerciseSessionRecord.EXERCISE_TYPE_BIKING
        Sport.SWIMMING -> ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_POOL
        Sport.WALKING -> ExerciseSessionRecord.EXERCISE_TYPE_WALKING
        Sport.HIKING -> ExerciseSessionRecord.EXERCISE_TYPE_HIKING
        Sport.ROWING -> ExerciseSessionRecord.EXERCISE_TYPE_ROWING
        Sport.FOOTBALL -> ExerciseSessionRecord.EXERCISE_TYPE_SOCCER
        Sport.BASKETBALL -> ExerciseSessionRecord.EXERCISE_TYPE_BASKETBALL
        Sport.TENNIS -> ExerciseSessionRecord.EXERCISE_TYPE_TENNIS
        Sport.PADEL -> ExerciseSessionRecord.EXERCISE_TYPE_SQUASH
        Sport.VOLLEYBALL -> ExerciseSessionRecord.EXERCISE_TYPE_VOLLEYBALL
        Sport.MARTIAL_ARTS -> ExerciseSessionRecord.EXERCISE_TYPE_MARTIAL_ARTS
        Sport.HIIT -> ExerciseSessionRecord.EXERCISE_TYPE_HIGH_INTENSITY_INTERVAL_TRAINING
        Sport.CROSSFIT -> ExerciseSessionRecord.EXERCISE_TYPE_BOOT_CAMP
        Sport.YOGA -> ExerciseSessionRecord.EXERCISE_TYPE_YOGA
        Sport.CLIMBING -> ExerciseSessionRecord.EXERCISE_TYPE_ROCK_CLIMBING
        Sport.DANCE -> ExerciseSessionRecord.EXERCISE_TYPE_DANCING
        Sport.SKIING -> ExerciseSessionRecord.EXERCISE_TYPE_SKIING
        Sport.OTHER -> ExerciseSessionRecord.EXERCISE_TYPE_OTHER_WORKOUT
    }

    companion object {
        /** The Play Store listing, for phones where Health Connect is an app (Android 13 and below). */
        const val PROVIDER_PACKAGE = "com.google.android.apps.healthdata"

        val READ_TYPES: List<KClass<out androidx.health.connect.client.records.Record>> = listOf(
            StepsRecord::class,
            ActiveCaloriesBurnedRecord::class,
            ExerciseSessionRecord::class,
            SleepSessionRecord::class,
            RestingHeartRateRecord::class,
            NutritionRecord::class,
        )

        val WRITE_TYPES: List<KClass<out androidx.health.connect.client.records.Record>> = listOf(
            ExerciseSessionRecord::class,
            WeightRecord::class,
            BodyFatRecord::class,
        )
    }
}
