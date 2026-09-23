package com.squeeze.app.data

import com.squeeze.app.data.db.MeasurementDao
import com.squeeze.app.data.db.PhysiqueDao
import com.squeeze.app.data.db.PhysiqueReadEntity
import com.squeeze.app.data.db.ProfileDao
import com.squeeze.app.data.db.ProfileEntity
import com.squeeze.core.model.Goal
import com.squeeze.core.model.Profile
import com.squeeze.core.model.Sex
import com.squeeze.core.model.TrainingAge
import com.squeeze.core.model.UnitSystem
import com.squeeze.core.nutrition.NutritionInputs
import com.squeeze.core.nutrition.NutritionPlan
import com.squeeze.core.nutrition.NutritionPlanner
import com.squeeze.core.program.WeakPoint
import com.squeeze.core.scan.MuscleGroup
import com.squeeze.core.scan.PhysiqueAnalysis
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import com.squeeze.core.program.MuscleGroup as TrainingGroup

/**
 * The one place the app's parts meet.
 *
 * Every screen used to hold its own copy of what it needed: the training screen its own goal
 * and day count, the scan its own reading of the body, nothing at all for food. Change the
 * goal in Training and Settings never heard; scan a weak chest and the programme did not
 * change. This class is where those facts live once, so that each feature reads the others:
 *
 *  - an **AI scan** stores its physique read here → the **programme** prioritises its weak
 *    groups → the **nutrition plan** names them when it explains the surplus;
 *  - the **programme's** training days → the nutrition plan's activity factor and carb split;
 *  - **weight logs** and the **body-fat trend** → lean mass, resting burn, and the weekly
 *    correction when the scale disagrees with the plan;
 *  - the **goal** and its deadline, set anywhere → all of the above.
 */
@Singleton
class CoachRepository @Inject constructor(
    private val profileDao: ProfileDao,
    private val measurementDao: MeasurementDao,
    private val physiqueDao: PhysiqueDao,
    private val composition: BodyCompositionRepository,
) {

    /** Stores the AI's per-group scores from a saved scan. */
    suspend fun savePhysique(epochDay: Long, goal: Goal, scores: Map<MuscleGroup, Double>) {
        if (scores.isEmpty()) return
        physiqueDao.upsert(
            PhysiqueReadEntity(
                epochDay = epochDay,
                goal = goal.name,
                scores = scores.entries.joinToString(",") { (g, s) -> "${g.name}=$s" },
            ),
        )
    }

    /** The most recent physique read, or null before the first AI scan. */
    suspend fun latestPhysique(): Map<MuscleGroup, Double>? = physiqueDao.latest()?.let(::decode)

    /** The read before [epochDay], for showing what moved since last time. */
    suspend fun physiqueBefore(epochDay: Long): Pair<Long, Map<MuscleGroup, Double>>? =
        physiqueDao.before(epochDay)?.let { it.epochDay to decode(it) }

    /** Remembers the programme's settings so nutrition and settings see the same ones. */
    suspend fun saveTrainingChoices(goal: Goal, trainingAge: TrainingAge, daysPerWeek: Int) {
        val existing = profileDao.get() ?: return
        profileDao.upsert(
            existing.copy(
                goal = goal.name,
                trainingAge = trainingAge.name,
                trainingDaysPerWeek = daysPerWeek,
            ),
        )
    }

    /**
     * The AI scan's weak groups for the user's current goal, as training priorities.
     *
     * Ranked by [PhysiqueAnalysis] against today's goal rather than the goal at scan time, so
     * switching from a bulk to a cut re-ranks the same photograph instead of waiting for a
     * new one.
     */
    suspend fun aiWeakPoints(goal: Goal): List<WeakPoint> {
        val scores = latestPhysique() ?: return emptyList()
        val report = PhysiqueAnalysis.report(scores, goal) ?: return emptyList()
        return report.focus.flatMap { advice ->
            val score = scores[advice.group] ?: 0.0
            trainingGroups(advice.group).map { group ->
                WeakPoint(
                    group = group,
                    severity = (1.0 - score).coerceIn(0.0, 1.0),
                    finding = "AI scan: your ${advice.group.label.lowercase()} read " +
                        "${PhysiqueAnalysis.development(score).label.lowercase()} " +
                        "(${(score * 100).toInt()}/100). ${advice.why}",
                    prescription = advice.how,
                )
            }
        }
    }

    /** The groups the AI flagged, by name, for the nutrition plan's explanation. */
    private suspend fun aiWeakGroupNames(goal: Goal): List<String> {
        val scores = latestPhysique() ?: return emptyList()
        return PhysiqueAnalysis.report(scores, goal)?.weaknesses
            ?.map { it.label.lowercase() }
            .orEmpty()
    }

    /**
     * Today's nutrition plan from everything the app knows, or null without a profile or a
     * logged weight — a plan built on an invented weight would be precise and wrong.
     */
    suspend fun nutritionPlan(): NutritionContext? {
        val stored = profileDao.get() ?: return null
        val profile = stored.toDomain()
        val today = LocalDate.now().toEpochDay()

        val measurements = measurementDao.since(Long.MIN_VALUE).sortedByDescending { it.epochDay }
        val latestWeight = measurements.firstOrNull { it.weightKg != null } ?: return null

        val snapshot = composition.snapshot(profile)
        val bodyFat = snapshot.latest?.level
        val weightTrend = snapshot.weightTrend
        val slope = weightTrend.lastOrNull()?.takeIf { it.isChangeSignificant }?.weeklyChange
        val trendDays = if (weightTrend.size >= 2) {
            weightTrend.last().epochDay - weightTrend.first().epochDay
        } else {
            null
        }
        val aiScanned = measurements.any { it.visualBodyFatPercent != null }

        val input = NutritionInputs(
            sex = profile.sex,
            ageYears = LocalDate.now().year - profile.birthYear,
            heightCm = profile.heightCm,
            weightKg = weightTrend.lastOrNull()?.level ?: latestWeight.weightKg!!,
            weightDaysOld = today - latestWeight.epochDay,
            bodyFatPercent = bodyFat,
            bodyFatSource = if (aiScanned) "your body-fat trend, led by the AI scan" else "your body-fat trend",
            goal = profile.goal,
            trainingAge = profile.trainingAge,
            trainingDaysPerWeek = stored.trainingDaysPerWeek ?: DEFAULT_TRAINING_DAYS,
            trainingDaysFromProgramme = stored.trainingDaysPerWeek != null,
            weightTrendKgPerWeek = slope,
            trendDays = trendDays,
            targetWeightKg = profile.targetWeightKg,
            targetBodyFatPercent = profile.targetBodyFatPercent,
            daysToDeadline = profile.targetEpochDay?.let { it - today },
            priorityGroups = aiWeakGroupNames(profile.goal),
        )
        return NutritionContext(
            plan = NutritionPlanner.plan(input),
            goal = profile.goal,
            trainingDaysPerWeek = input.trainingDaysPerWeek,
            trainingDaysFromProgramme = input.trainingDaysFromProgramme,
            hasPhysique = physiqueDao.latest() != null,
        )
    }

    private fun decode(read: PhysiqueReadEntity): Map<MuscleGroup, Double> =
        read.scores.split(",").mapNotNull { pair ->
            val (name, value) = pair.split("=").takeIf { it.size == 2 } ?: return@mapNotNull null
            val group = runCatching { MuscleGroup.valueOf(name) }.getOrNull() ?: return@mapNotNull null
            value.toDoubleOrNull()?.let { group to it }
        }.toMap()

    private fun trainingGroups(group: MuscleGroup): List<TrainingGroup> = when (group) {
        MuscleGroup.SHOULDERS -> listOf(TrainingGroup.SHOULDERS)
        MuscleGroup.CHEST -> listOf(TrainingGroup.CHEST)
        MuscleGroup.ARMS -> listOf(TrainingGroup.BICEPS, TrainingGroup.TRICEPS)
        MuscleGroup.ABS -> listOf(TrainingGroup.ABS)
        MuscleGroup.V_TAPER -> listOf(TrainingGroup.BACK)
        MuscleGroup.LEGS -> listOf(TrainingGroup.QUADS, TrainingGroup.HAMSTRINGS)
    }

    private companion object {
        const val DEFAULT_TRAINING_DAYS = 4
    }
}

/** A nutrition plan with the facts the page needs to link back to where they came from. */
data class NutritionContext(
    val plan: NutritionPlan,
    val goal: Goal,
    val trainingDaysPerWeek: Int,
    val trainingDaysFromProgramme: Boolean,
    val hasPhysique: Boolean,
)

private fun ProfileEntity.toDomain() = Profile(
    heightCm = heightCm,
    birthYear = birthYear,
    sex = Sex.valueOf(sex),
    trainingAge = runCatching { TrainingAge.valueOf(trainingAge) }.getOrDefault(TrainingAge.NOVICE),
    goal = runCatching { Goal.valueOf(goal) }.getOrDefault(Goal.HYPERTROPHY),
    unitSystem = runCatching { UnitSystem.valueOf(unitSystem) }.getOrDefault(UnitSystem.METRIC),
    targetBodyFatPercent = targetBodyFatPercent,
    targetWeightKg = targetWeightKg,
    targetEpochDay = targetEpochDay,
)
