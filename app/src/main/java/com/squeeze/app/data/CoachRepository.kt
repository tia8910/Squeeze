package com.squeeze.app.data

import com.squeeze.app.data.db.ActivityDao
import com.squeeze.app.data.db.ActivitySessionEntity
import com.squeeze.app.data.db.LoggedSetEntity
import com.squeeze.app.data.db.MeasurementDao
import com.squeeze.app.data.db.WorkoutDao
import com.squeeze.core.nutrition.FoodLibrary
import com.squeeze.core.workout.ActivityCalories
import com.squeeze.core.workout.Discipline
import com.squeeze.core.workout.HybridPlanner
import com.squeeze.core.workout.HybridWeek
import com.squeeze.core.workout.Intensity
import com.squeeze.core.workout.LoggedSet
import com.squeeze.core.workout.PlannedItem
import com.squeeze.core.workout.PlannedSession
import com.squeeze.core.coach.Journey
import com.squeeze.core.coach.JourneyFacts
import com.squeeze.core.coach.JourneyPlanner
import com.squeeze.core.workout.Sport
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
    private val workoutDao: WorkoutDao,
    private val activityDao: ActivityDao,
) {

    /**
     * What the log screen should open with: a session from the week, or an exercise the
     * machine scanner just identified. Handed over in memory — it is a navigation argument,
     * not something to keep.
     */
    @Volatile
    var pendingLog: PendingLog? = null

    // ── The journey and the dashboard ────────────────────────────────────────────────────

    /** Today's planned sessions, empty on a rest day or before a week exists. */
    private suspend fun todaysSessions(week: HybridWeek?): List<PlannedSession> =
        week?.days?.getOrNull(LocalDate.now().dayOfWeek.value - 1)?.sessions.orEmpty()

    /** Where the user is, and the one thing to do next. */
    suspend fun journey(): Journey {
        val today = LocalDate.now().toEpochDay()
        val measurements = measurementDao.since(Long.MIN_VALUE)
        val lastWeigh = measurements.filter { it.weightKg != null }.maxOfOrNull { it.epochDay }
        val lastScan = listOfNotNull(
            physiqueDao.latest()?.epochDay,
            measurements.filter { it.photoId != null }.maxOfOrNull { it.epochDay },
        ).maxOrNull()
        val week = week()
        return JourneyPlanner.plan(
            JourneyFacts(
                daysSinceWeight = lastWeigh?.let { today - it },
                daysSinceScan = lastScan?.let { today - it },
                hasWeek = week != null,
                hasFavourites = favouriteFoods().isNotEmpty(),
                todaysSession = todaysSessions(week).firstOrNull()?.title,
                loggedToday = activityDao.since(today).isNotEmpty() || workoutDao.since(today).isNotEmpty(),
            ),
        )
    }

    /** Hands today's first planned session to the log screen. */
    suspend fun prepareTodaysSession() {
        val session = todaysSessions(week()).firstOrNull() ?: return
        pendingLog = PendingLog(
            sport = session.discipline.sport,
            title = session.title,
            minutes = session.minutes,
            intensity = session.intensity,
            exercises = session.items.filter { it.prescription != null },
        )
    }

    /** One summary of everything, for the dashboard. */
    suspend fun dashboard(): DashboardSummary {
        val week = week()
        val monday = LocalDate.now().with(java.time.DayOfWeek.MONDAY).toEpochDay()
        val sessionsDone = activityDao.since(monday).size
        val setsDone = workoutDao.since(monday).size
        val goal = profileDao.get()?.let { runCatching { Goal.valueOf(it.goal) }.getOrNull() } ?: Goal.HYPERTROPHY
        val report = latestPhysique()?.let { PhysiqueAnalysis.report(it, goal) }
        val nutrition = nutritionPlan()
        return DashboardSummary(
            journey = journey(),
            todaysTraining = if (week == null) {
                null
            } else {
                todaysSessions(week).takeIf { it.isNotEmpty() }?.joinToString(" + ") { it.title }
                    ?: "Rest day — recovery is part of the plan"
            },
            sessionsDone = sessionsDone,
            sessionsPlanned = week?.days?.sumOf { it.sessions.size },
            setsDone = setsDone,
            strengths = report?.strengths?.map { it.label }.orEmpty(),
            weakPoints = report?.weaknesses?.map { it.label }.orEmpty(),
            fuelToday = nutrition?.plan?.let { plan ->
                val training = todaysSessions(week).isNotEmpty()
                val m = if (training) plan.trainingDay else plan.restDay
                "%,d kcal · %d g protein · %d g carbs · %d g fat".format(m.calories, m.proteinG, m.carbsG, m.fatG)
            },
            microGaps = nutrition?.plan?.micros?.filter { it.short }?.map { it.nutrient.label }.orEmpty(),
            goalRate = nutrition?.plan?.intendedKgPerWeek,
        )
    }

    // ── Sports and foods the user chose ──────────────────────────────────────────────────

    suspend fun disciplines(): Set<Discipline> = profileDao.get()?.disciplines
        ?.split(",")
        ?.mapNotNull { runCatching { Discipline.valueOf(it) }.getOrNull() }
        ?.toSet()
        .orEmpty()

    suspend fun saveDisciplines(chosen: Set<Discipline>) {
        val existing = profileDao.get() ?: return
        profileDao.upsert(existing.copy(disciplines = chosen.joinToString(",") { it.name }))
    }

    suspend fun favouriteFoods(): Set<String> = profileDao.get()?.favouriteFoods
        ?.split("|")
        ?.filter { FoodLibrary.byName(it) != null }
        ?.toSet()
        .orEmpty()

    suspend fun saveFavouriteFoods(foods: Set<String>) {
        val existing = profileDao.get() ?: return
        profileDao.upsert(existing.copy(favouriteFoods = foods.joinToString("|")))
    }

    // ── The week ─────────────────────────────────────────────────────────────────────────

    /** The AI scan's weak groups for today's goal, as programme muscle groups. */
    suspend fun aiWeakTrainingGroups(goal: Goal): List<TrainingGroup> =
        aiWeakPoints(goal).map { it.group }.distinct()

    /** The combined week for the chosen sports, or null before any are chosen. */
    suspend fun week(): HybridWeek? {
        val stored = profileDao.get() ?: return null
        val chosen = disciplines().takeIf { it.isNotEmpty() } ?: return null
        val profile = stored.toDomain()
        return HybridPlanner.plan(
            disciplines = chosen,
            daysPerWeek = stored.trainingDaysPerWeek ?: DEFAULT_TRAINING_DAYS,
            goal = profile.goal,
            trainingAge = profile.trainingAge,
            weakGroups = aiWeakTrainingGroups(profile.goal),
        )
    }

    // ── The log ──────────────────────────────────────────────────────────────────────────

    suspend fun logSet(exercise: String, group: TrainingGroup?, set: LoggedSet) {
        workoutDao.insert(
            LoggedSetEntity(
                epochDay = LocalDate.now().toEpochDay(),
                exerciseName = exercise,
                muscleGroup = group?.name ?: "OTHER",
                weightKg = set.weightKg,
                reps = set.reps,
                rir = set.rir,
                programWeekIndex = null,
            ),
        )
    }

    suspend fun deleteSet(set: LoggedSetEntity) = workoutDao.delete(set)

    suspend fun todaysSets(): List<LoggedSetEntity> = workoutDao.since(LocalDate.now().toEpochDay())

    /** The last time this exercise was trained, and what to do today because of it. */
    suspend fun lastSession(exercise: String): List<LoggedSet> =
        workoutDao.lastSession(exercise, LocalDate.now().toEpochDay())
            .map { LoggedSet(it.weightKg, it.reps, it.rir) }

    suspend fun logSession(sport: Sport, title: String, minutes: Int, intensity: Intensity, distanceKm: Double?): Int {
        val weight = measurementDao.latestWeightKg() ?: DEFAULT_WEIGHT_KG
        val kcal = ActivityCalories.netKcal(sport, intensity, minutes, weight)
        activityDao.insert(
            ActivitySessionEntity(
                epochDay = LocalDate.now().toEpochDay(),
                sport = sport.name,
                title = title,
                minutes = minutes,
                intensity = intensity.name,
                distanceKm = distanceKm,
                netKcal = kcal,
            ),
        )
        return kcal
    }

    suspend fun recentSessions(days: Long = 14): List<ActivitySessionEntity> =
        activityDao.since(LocalDate.now().toEpochDay() - days)

    suspend fun deleteSession(session: ActivitySessionEntity) = activityDao.delete(session)

    /**
     * Sets done this week per muscle group against what the week plans, weak points marked —
     * the check that the programme's priorities are actually being trained.
     */
    suspend fun weeklyVolume(): List<VolumeRow> {
        val today = LocalDate.now()
        val monday = today.with(java.time.DayOfWeek.MONDAY).toEpochDay()
        val done = workoutDao.since(monday)
            .mapNotNull { set -> runCatching { TrainingGroup.valueOf(set.muscleGroup) }.getOrNull() }
            .groupingBy { it }.eachCount()
        val week = week()
        val planned = week?.weeklySets().orEmpty()
        val goal = profileDao.get()?.let { runCatching { Goal.valueOf(it.goal) }.getOrNull() } ?: Goal.HYPERTROPHY
        val weak = aiWeakTrainingGroups(goal).toSet()
        return (planned.keys + done.keys).distinct().map { g ->
            VolumeRow(g, done[g] ?: 0, planned[g] ?: 0, g in weak)
        }.sortedWith(compareByDescending<VolumeRow> { it.weakPoint }.thenByDescending { it.planned })
    }


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
        val logged = recentSessions(14)
        val week = week()

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
            trainingDaysPerWeek = week?.trainingDays ?: stored.trainingDaysPerWeek ?: DEFAULT_TRAINING_DAYS,
            trainingDaysFromProgramme = stored.trainingDaysPerWeek != null,
            weightTrendKgPerWeek = slope,
            trendDays = trendDays,
            targetWeightKg = profile.targetWeightKg,
            targetBodyFatPercent = profile.targetBodyFatPercent,
            daysToDeadline = profile.targetEpochDay?.let { it - today },
            priorityGroups = aiWeakGroupNames(profile.goal),
            favouriteFoods = favouriteFoods(),
            loggedExerciseKcalPerDay = logged.takeIf { it.isNotEmpty() }?.let { s -> s.sumOf { it.netKcal } / 14.0 },
            loggedSessions = logged.size,
            plannedExerciseKcalPerDay = week?.netKcalPerDay(weightTrend.lastOrNull()?.level ?: latestWeight.weightKg!!),
            plannedSummary = week?.let { w ->
                w.days.flatMap { it.sessions }.groupingBy { it.discipline.label }.eachCount()
                    .entries.joinToString { (d, n) -> "$d ×$n" }
            },
        )
        return NutritionContext(
            plan = NutritionPlanner.plan(input),
            goal = profile.goal,
            trainingDaysPerWeek = input.trainingDaysPerWeek,
            favourites = input.favouriteFoods,
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

        /** Used for calorie estimates only until a first weight is logged. */
        const val DEFAULT_WEIGHT_KG = 75.0
    }
}

/** A nutrition plan with the facts the page needs to link back to where they came from. */
data class NutritionContext(
    val plan: NutritionPlan,
    val goal: Goal,
    val trainingDaysPerWeek: Int,
    val trainingDaysFromProgramme: Boolean,
    val hasPhysique: Boolean,
    val favourites: Set<String> = emptySet(),
)

/**
 * Everything on one card: where the user is in their journey, and a line from every feature.
 */
data class DashboardSummary(
    val journey: Journey,
    val todaysTraining: String?,
    val sessionsDone: Int,
    val sessionsPlanned: Int?,
    val setsDone: Int,
    val strengths: List<String>,
    val weakPoints: List<String>,
    val fuelToday: String?,
    val microGaps: List<String>,
    val goalRate: Double?,
)

/** One muscle group's week: sets logged since Monday against sets planned. */
data class VolumeRow(val group: TrainingGroup, val done: Int, val planned: Int, val weakPoint: Boolean)

/**
 * A session or exercise to open the log with.
 *
 * @param exercises strength items to log set by set; empty for a sport logged as a whole
 */
data class PendingLog(
    val sport: Sport,
    val title: String,
    val minutes: Int,
    val intensity: Intensity,
    val exercises: List<PlannedItem> = emptyList(),
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
