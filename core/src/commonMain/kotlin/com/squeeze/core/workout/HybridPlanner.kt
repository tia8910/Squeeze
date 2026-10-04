package com.squeeze.core.workout

import com.squeeze.core.model.Goal
import com.squeeze.core.model.TrainingAge
import com.squeeze.core.program.Equipment
import com.squeeze.core.program.ExerciseLibrary
import com.squeeze.core.program.MuscleGroup
import com.squeeze.core.program.SplitPlanner
import kotlin.math.roundToInt

/** What a discipline does for the body, which decides how the week is shared out. */
enum class DisciplineKind { STRENGTH, ENDURANCE, CONDITIONING, MOBILITY, SKILL }

/** A sport the user trains, as they would name it. */
enum class Discipline(val label: String, val kind: DisciplineKind, val sport: Sport) {
    GYM("Gym", DisciplineKind.STRENGTH, Sport.STRENGTH),
    CALISTHENICS("Calisthenics", DisciplineKind.STRENGTH, Sport.STRENGTH),
    PILATES("Pilates", DisciplineKind.MOBILITY, Sport.YOGA),
    YOGA("Yoga", DisciplineKind.MOBILITY, Sport.YOGA),
    RUNNING("Running", DisciplineKind.ENDURANCE, Sport.RUNNING),
    CYCLING("Cycling", DisciplineKind.ENDURANCE, Sport.CYCLING),
    SWIMMING("Swimming", DisciplineKind.ENDURANCE, Sport.SWIMMING),
    HIIT("HIIT", DisciplineKind.CONDITIONING, Sport.HIIT),
    CROSSFIT("CrossFit", DisciplineKind.CONDITIONING, Sport.CROSSFIT),
    MARTIAL_ARTS("Boxing / martial arts", DisciplineKind.SKILL, Sport.MARTIAL_ARTS),
    TEAM_SPORTS("Football / team sports", DisciplineKind.SKILL, Sport.FOOTBALL),
    CLIMBING("Climbing", DisciplineKind.SKILL, Sport.CLIMBING),
    RACKET("Tennis / padel", DisciplineKind.SKILL, Sport.TENNIS),
}

/**
 * One thing to do in a session.
 *
 * @param detail how much: sets × reps, a duration, a pace
 * @param group the muscle group a strength item counts toward, so the log can credit it
 */
data class PlannedItem(
    val name: String,
    val detail: String,
    val group: MuscleGroup? = null,
    val compound: Boolean = false,
    val prescription: Prescription? = null,
    val weakPoint: Boolean = false,
)

/**
 * @param addOn a short session stacked after another on the same day, because the user chose
 *   more sports than days
 */
data class PlannedSession(
    val discipline: Discipline,
    val title: String,
    val minutes: Int,
    val intensity: Intensity,
    val items: List<PlannedItem>,
    val why: String,
    val addOn: Boolean = false,
) {
    fun netKcal(weightKg: Double): Int = ActivityCalories.netKcal(discipline.sport, intensity, minutes, weightKg)
}

data class PlannedDay(val index: Int, val name: String, val sessions: List<PlannedSession>) {
    val rest: Boolean get() = sessions.isEmpty()
}

/**
 * A week across every sport the user chose.
 *
 * @param notes why the week is shaped the way it is, in the order the decisions were made
 */
data class HybridWeek(
    val disciplines: List<Discipline>,
    val days: List<PlannedDay>,
    val notes: List<String>,
) {
    val trainingDays: Int get() = days.count { !it.rest }

    /** Planned exercise calories above rest, per day, averaged over the week. */
    fun netKcalPerDay(weightKg: Double): Double =
        days.sumOf { d -> d.sessions.sumOf { it.netKcal(weightKg) } } / 7.0

    /** Planned hard sets per muscle group across the week's strength sessions. */
    fun weeklySets(): Map<MuscleGroup, Int> = days
        .flatMap { d -> d.sessions.flatMap { it.items } }
        .filter { it.group != null && it.prescription != null }
        .groupBy { it.group!! }
        .mapValues { (_, items) -> items.sumOf { it.prescription!!.sets } }
}

/**
 * Builds one week from any mix of sports, for the user's goal.
 *
 * **Sharing the days.** Every chosen sport gets at least one session — the user picked it, so
 * it is theirs — and the rest are shared by how much each one serves the goal: strength work
 * leads a build or a strength phase, endurance work carries more of a cut, and a skill sport
 * the user plays is kept at its practice sessions. More sports than days, and the lightest ones
 * (Pilates, yoga) become short add-ons after another session rather than being dropped.
 *
 * **Spacing.** Strength sessions are spread across the week so the same muscles get a day
 * between them, and the hardest endurance session never lands the day after legs.
 *
 * **Content.** Gym days reuse the programme's split and exercise library; calisthenics picks
 * each movement's progression by experience; endurance sessions progress from easy to
 * intervals to a long session as they are added. The AI scan's weak points come first on
 * every strength day and get an extra set.
 */
object HybridPlanner {

    private val DAY_NAMES = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")

    fun plan(
        disciplines: Set<Discipline>,
        daysPerWeek: Int,
        goal: Goal,
        trainingAge: TrainingAge,
        weakGroups: List<MuscleGroup> = emptyList(),
        equipment: Set<Equipment> = Equipment.entries.toSet(),
    ): HybridWeek {
        val chosen = disciplines.sortedBy { it.ordinal }.ifEmpty { listOf(Discipline.GYM) }
        val days = daysPerWeek.coerceIn(1, 7)
        val notes = mutableListOf<String>()

        // ── Share the sessions out ───────────────────────────────────────────────────────
        val counts = allocate(chosen, days, goal)
        notes += "${goalLabel(goal)} across ${chosen.joinToString { it.label }} on $days days: " +
            counts.entries.joinToString { (d, n) -> "${d.label} ×$n" } + "."

        // ── Build each discipline's sessions ─────────────────────────────────────────────
        val strengthSessions = mutableListOf<PlannedSession>()
        val otherSessions = mutableListOf<PlannedSession>()
        counts.forEach { (discipline, n) ->
            val sessions = sessionsFor(discipline, n, goal, trainingAge, weakGroups, equipment)
            if (discipline.kind == DisciplineKind.STRENGTH) strengthSessions += sessions else otherSessions += sessions
        }
        if (weakGroups.isNotEmpty() && strengthSessions.isNotEmpty()) {
            notes += "Your AI scan's weak points — ${weakGroups.joinToString { it.name.lowercase() }} — come " +
                "first on strength days and get an extra set."
        }

        // ── Lay them out ─────────────────────────────────────────────────────────────────
        val slots = WeekLayout.days(days).sorted()
        val total = strengthSessions.size + otherSessions.size
        val mainSessions: List<PlannedSession>
        val extra: List<PlannedSession>
        if (total > days) {
            // More sessions than days: the lightest become add-ons.
            // By index, not by value: two identical easy runs are two sessions.
            val lightest = otherSessions.indices
                .sortedBy { if (otherSessions[it].discipline.kind == DisciplineKind.MOBILITY) 0 else 1 }
                .take(total - days)
                .toSet()
            extra = lightest.map { shortVersion(otherSessions[it]) }
            mainSessions = strengthSessions + otherSessions.filterIndexed { i, _ -> i !in lightest }
            notes += "You chose more sessions than days, so " +
                extra.joinToString { it.discipline.label } + " become short add-ons after another session."
        } else {
            extra = emptyList()
            mainSessions = strengthSessions + otherSessions
        }

        val ordered = interleave(
            mainSessions.filter { it.discipline.kind == DisciplineKind.STRENGTH },
            mainSessions.filter { it.discipline.kind != DisciplineKind.STRENGTH },
        ).let(::keepIntervalsOffLegDay)

        val byDay = mutableMapOf<Int, MutableList<PlannedSession>>()
        ordered.forEachIndexed { i, s -> byDay.getOrPut(slots[i % slots.size]) { mutableListOf() } += s }
        // Add-ons go on the days that are not already carrying the hardest work.
        extra.forEachIndexed { i, s ->
            val target = slots.sortedBy { day -> byDay[day]?.count { it.intensity == Intensity.HARD } ?: 0 }[i % slots.size]
            byDay.getOrPut(target) { mutableListOf() } += s
        }

        if (strengthSessions.size >= 2) {
            notes += "Strength sessions are spread out so each muscle gets at least a day to recover."
        }
        if (ordered.any { it.title.startsWith("Intervals") } && ordered.any { it.title.contains("Legs") || it.title.contains("Lower") }) {
            notes += "Interval sessions are kept off the day after legs, when your legs are least ready for them."
        }

        return HybridWeek(
            disciplines = chosen,
            days = DAY_NAMES.mapIndexed { i, name -> PlannedDay(i, name, byDay[i].orEmpty()) },
            notes = notes,
        )
    }

    // ── Allocation ───────────────────────────────────────────────────────────────────────

    private fun weight(kind: DisciplineKind, goal: Goal): Double = when (kind) {
        DisciplineKind.STRENGTH -> when (goal) {
            Goal.HYPERTROPHY, Goal.STRENGTH -> 3.0
            Goal.RECOMP -> 2.5
            Goal.CUT -> 2.0
            Goal.MAKE_WEIGHT -> 1.5
        }
        DisciplineKind.ENDURANCE -> when (goal) {
            Goal.CUT, Goal.MAKE_WEIGHT -> 2.0
            Goal.RECOMP -> 1.5
            else -> 1.0
        }
        DisciplineKind.CONDITIONING -> if (goal == Goal.CUT || goal == Goal.MAKE_WEIGHT) 1.5 else 1.0
        DisciplineKind.MOBILITY -> 0.8
        DisciplineKind.SKILL -> 1.5
    }

    private fun cap(d: Discipline): Int = when (d.kind) {
        DisciplineKind.STRENGTH -> if (d == Discipline.GYM) 6 else 5
        DisciplineKind.ENDURANCE -> 5
        DisciplineKind.CONDITIONING -> 3
        DisciplineKind.MOBILITY -> 3
        DisciplineKind.SKILL -> 4
    }

    /**
     * Sessions per discipline: one each, then the remaining days by highest average weight —
     * the D'Hondt method, which is how seats are shared in proportion without rounding
     * anyone out.
     */
    fun allocate(chosen: List<Discipline>, days: Int, goal: Goal): Map<Discipline, Int> {
        val counts = chosen.associateWith { 1 }.toMutableMap()
        var remaining = days - chosen.size
        while (remaining > 0) {
            val next = chosen
                .filter { counts.getValue(it) < cap(it) }
                .maxByOrNull { weight(it.kind, goal) / (counts.getValue(it) + 1) } ?: break
            counts[next] = counts.getValue(next) + 1
            remaining--
        }
        return counts
    }

    // ── Layout ───────────────────────────────────────────────────────────────────────────

    /** Strength sessions evenly spaced, everything else in the gaps. */
    private fun interleave(strength: List<PlannedSession>, other: List<PlannedSession>): List<PlannedSession> {
        if (strength.isEmpty()) return other
        if (other.isEmpty()) return strength
        val out = mutableListOf<PlannedSession>()
        val total = strength.size + other.size
        var s = 0
        var o = 0
        for (i in 0 until total) {
            val strengthDue = s < strength.size &&
                (o >= other.size || (i + 0.5) * strength.size / total > s)
            if (strengthDue) out += strength[s++] else out += other[o++]
        }
        return out
    }

    private fun keepIntervalsOffLegDay(sessions: List<PlannedSession>): List<PlannedSession> {
        val list = sessions.toMutableList()
        for (i in 1 until list.size) {
            val prev = list[i - 1]
            val legs = prev.title.contains("Legs") || prev.title.contains("Lower")
            if (legs && list[i].title.startsWith("Intervals")) {
                val swap = (i + 1 until list.size).firstOrNull { !list[it].title.startsWith("Intervals") } ?: continue
                val tmp = list[i]; list[i] = list[swap]; list[swap] = tmp
            }
        }
        return list
    }

    private fun shortVersion(s: PlannedSession) = s.copy(
        title = "${s.title} (short)",
        minutes = minOf(s.minutes, 20),
        items = s.items.take(4),
        addOn = true,
    )

    // ── Session content ─────────────────────────────────────────────────────────────────

    private fun sessionsFor(
        d: Discipline,
        n: Int,
        goal: Goal,
        age: TrainingAge,
        weak: List<MuscleGroup>,
        equipment: Set<Equipment>,
    ): List<PlannedSession> = when (d) {
        Discipline.GYM -> gym(n, goal, age, weak, equipment)
        Discipline.CALISTHENICS -> calisthenics(n, goal, age, weak)
        Discipline.PILATES -> (0 until n).map { pilates(it) }
        Discipline.YOGA -> (0 until n).map { yoga(it) }
        Discipline.RUNNING, Discipline.CYCLING, Discipline.SWIMMING -> endurance(d, n, goal, age)
        Discipline.HIIT, Discipline.CROSSFIT -> (0 until n).map { conditioning(d, it, age) }
        Discipline.MARTIAL_ARTS, Discipline.TEAM_SPORTS, Discipline.CLIMBING, Discipline.RACKET ->
            (0 until n).map { skill(d, it) }
    }

    private fun gym(n: Int, goal: Goal, age: TrainingAge, weak: List<MuscleGroup>, equipment: Set<Equipment>): List<PlannedSession> {
        val split = if (n == 1) {
            listOf(com.squeeze.core.program.SplitDay("Full Body", listOf(MuscleGroup.QUADS, MuscleGroup.CHEST, MuscleGroup.BACK, MuscleGroup.HAMSTRINGS, MuscleGroup.SHOULDERS, MuscleGroup.ABS)))
        } else {
            SplitPlanner.plan(n.coerceIn(2, 6))
        }
        return split.take(n).map { day ->
            val groups = day.groups.sortedByDescending { it in weak }
            val items = groups.flatMap { group ->
                val options = ExerciseLibrary.forGroup(group, equipment)
                val isWeak = group in weak
                options.take(if (isWeak) 2 else 1).map { ex ->
                    val p = Coaching.prescribe(goal, age, ex.compound, isWeak)
                    PlannedItem(ex.name, p.summary, group, ex.compound, p, isWeak)
                }
            }
            PlannedSession(
                Discipline.GYM, "Gym — ${day.name}", minutes = 45 + 5 * items.size.coerceAtMost(6),
                intensity = Intensity.MODERATE, items = items,
                why = "${day.name}: ${day.groups.joinToString { it.name.lowercase() }}.",
            )
        }
    }

    /** Each calisthenics pattern as a ladder, easiest first. */
    private val LADDERS: Map<MuscleGroup, List<String>> = mapOf(
        MuscleGroup.CHEST to listOf("Incline push-up", "Push-up", "Diamond push-up", "Archer push-up", "Pseudo-planche push-up"),
        MuscleGroup.BACK to listOf("Australian row", "Negative pull-up", "Pull-up", "Archer pull-up", "Weighted pull-up"),
        MuscleGroup.TRICEPS to listOf("Bench dip", "Assisted parallel-bar dip", "Parallel-bar dip", "Ring dip", "Weighted dip"),
        MuscleGroup.SHOULDERS to listOf("Pike push-up", "Elevated pike push-up", "Wall handstand hold", "Handstand push-up negative", "Handstand push-up"),
        MuscleGroup.BICEPS to listOf("Australian chin-up row", "Negative chin-up", "Chin-up", "Chin-up (slow eccentric)", "Weighted chin-up"),
        MuscleGroup.QUADS to listOf("Bodyweight squat", "Split squat", "Bulgarian split squat", "Shrimp squat", "Pistol squat"),
        MuscleGroup.HAMSTRINGS to listOf("Glute bridge", "Single-leg glute bridge", "Nordic curl negative", "Nordic curl (band)", "Nordic curl"),
        MuscleGroup.CALVES to listOf("Calf raise", "Single-leg calf raise", "Single-leg calf raise (deficit)", "Single-leg calf raise (deficit)", "Single-leg calf raise (weighted)"),
        MuscleGroup.ABS to listOf("Dead bug", "Hollow body hold", "Hanging knee raise", "Hanging leg raise", "L-sit"),
    )

    private val HOLDS = setOf("Hollow body hold", "L-sit", "Wall handstand hold")

    private fun calisthenics(n: Int, goal: Goal, age: TrainingAge, weak: List<MuscleGroup>): List<PlannedSession> {
        val level = when (age) {
            TrainingAge.NOVICE -> 0
            TrainingAge.INTERMEDIATE -> 2
            TrainingAge.ADVANCED -> 3
        }
        val fullBody = listOf(MuscleGroup.CHEST, MuscleGroup.BACK, MuscleGroup.QUADS, MuscleGroup.ABS)
        val upper = listOf(MuscleGroup.CHEST, MuscleGroup.BACK, MuscleGroup.TRICEPS, MuscleGroup.SHOULDERS, MuscleGroup.BICEPS)
        val lower = listOf(MuscleGroup.QUADS, MuscleGroup.HAMSTRINGS, MuscleGroup.CALVES, MuscleGroup.ABS)
        return (0 until n).map { i ->
            val (name, base) = when {
                n >= 4 -> if (i % 2 == 0) "Upper" to upper else "Lower & core" to lower
                i % 2 == 0 -> "Full body A" to (fullBody + MuscleGroup.TRICEPS)
                else -> "Full body B" to (fullBody + MuscleGroup.HAMSTRINGS + MuscleGroup.SHOULDERS)
            }
            // A weak point missing from this day is added, then everything weak goes first.
            val groups = (base + weak.filter { it in LADDERS && it !in base && n < 4 }).distinct()
                .sortedByDescending { it in weak }
            val items = groups.mapNotNull { group ->
                val ladder = LADDERS[group] ?: return@mapNotNull null
                val move = ladder[level.coerceAtMost(ladder.lastIndex)]
                val isWeak = group in weak
                val p = Coaching.prescribe(goal, age, compound = group != MuscleGroup.ABS && group != MuscleGroup.CALVES, weakPoint = isWeak)
                val detail = if (move in HOLDS) "${p.sets} × 20–40 s holds · rest ${p.restSeconds / 60} min" else p.summary +
                    " · when every set hits ${p.reps.last}, move to: ${ladder.getOrNull(level + 1) ?: "add weight"}"
                PlannedItem(move, detail, group, group != MuscleGroup.ABS, p, isWeak)
            }
            PlannedSession(
                Discipline.CALISTHENICS, "Calisthenics — $name", minutes = 50, intensity = Intensity.MODERATE,
                items = items,
                why = "Bodyweight progressions at your level; each one names the next step up.",
            )
        }
    }

    private fun pilates(i: Int): PlannedSession {
        val a = listOf(
            PlannedItem("The Hundred", "100 arm pumps, legs tabletop"),
            PlannedItem("Roll-up", "8 slow reps"),
            PlannedItem("Single-leg circles", "6 each direction, each leg"),
            PlannedItem("Single-leg stretch", "10 each side"),
            PlannedItem("Criss-cross", "10 each side"),
            PlannedItem("Swimming", "3 × 30 s"),
            PlannedItem("Side-kick series", "10 each leg"),
            PlannedItem("Teaser", "5 reps"),
        )
        val b = listOf(
            PlannedItem("Pelvic curl", "10 slow reps"),
            PlannedItem("Spine stretch forward", "6 reps"),
            PlannedItem("Saw", "6 each side"),
            PlannedItem("Double-leg stretch", "8 reps"),
            PlannedItem("Swan", "6 reps"),
            PlannedItem("Side plank", "2 × 20–30 s each side"),
            PlannedItem("Leg pull front", "6 reps"),
            PlannedItem("Seal", "8 reps"),
        )
        return PlannedSession(
            Discipline.PILATES, "Pilates — mat ${if (i % 2 == 0) "A" else "B"}", 40, Intensity.EASY,
            if (i % 2 == 0) a else b,
            "Core control and posture — the trunk strength that holds heavy lifts and long runs together.",
        )
    }

    private fun yoga(i: Int): PlannedSession {
        val a = listOf(
            PlannedItem("Sun salutation A", "5 rounds"),
            PlannedItem("Warrior II → triangle → side angle", "5 breaths each, both sides"),
            PlannedItem("Low lunge", "1 min each side — hip flexors"),
            PlannedItem("Pigeon", "1 min each side"),
            PlannedItem("Seated forward fold", "1 min"),
            PlannedItem("Supine twist", "1 min each side"),
            PlannedItem("Savasana", "3 min"),
        )
        val b = listOf(
            PlannedItem("Cat–cow", "10 breaths"),
            PlannedItem("Downward dog", "1 min"),
            PlannedItem("Crescent lunge", "5 breaths each side"),
            PlannedItem("Chair pose", "5 breaths × 3"),
            PlannedItem("Tree pose", "30 s each side"),
            PlannedItem("Bridge", "5 breaths × 3"),
            PlannedItem("Happy baby", "1 min"),
        )
        return PlannedSession(
            Discipline.YOGA, "Yoga — flow ${if (i % 2 == 0) "A" else "B"}", 35, Intensity.EASY,
            if (i % 2 == 0) a else b,
            "Mobility and recovery: range you can load in the gym and breath you can use in hard efforts.",
        )
    }

    private fun endurance(d: Discipline, n: Int, goal: Goal, age: TrainingAge): List<PlannedSession> {
        val scale = when (age) {
            TrainingAge.NOVICE -> 0.7
            TrainingAge.INTERMEDIATE -> 1.0
            TrainingAge.ADVANCED -> 1.3
        }
        fun min(base: Int) = (base * scale / 5).roundToInt() * 5
        val noun = when (d) {
            Discipline.RUNNING -> "run"
            Discipline.CYCLING -> "ride"
            else -> "swim"
        }
        val intervals = when (d) {
            Discipline.RUNNING -> when (age) {
                TrainingAge.NOVICE -> "8 × 1 min fast / 2 min walk"
                TrainingAge.INTERMEDIATE -> "5 × 3 min hard / 2 min easy jog"
                TrainingAge.ADVANCED -> "6 × 800 m at 5 km pace, 2 min jog between"
            }
            Discipline.CYCLING -> when (age) {
                TrainingAge.NOVICE -> "6 × 1 min hard / 2 min easy"
                TrainingAge.INTERMEDIATE -> "5 × 4 min hard / 3 min easy"
                TrainingAge.ADVANCED -> "4 × 8 min at threshold / 4 min easy"
            }
            else -> when (age) {
                TrainingAge.NOVICE -> "10 × 25 m fast / 30 s rest"
                TrainingAge.INTERMEDIATE -> "8 × 50 m hard / 30 s rest"
                TrainingAge.ADVANCED -> "10 × 100 m at race pace / 20 s rest"
            }
        }
        // Order of addition: a cut starts with intervals (most burn per minute), everyone
        // else with easy aerobic work (the base everything else sits on).
        val order = if (goal == Goal.CUT || goal == Goal.MAKE_WEIGHT) listOf("I", "E", "L", "E", "E") else listOf("E", "I", "L", "E", "E")
        return order.take(n).map { kind ->
            when (kind) {
                "I" -> PlannedSession(
                    d, "Intervals — ${d.label.lowercase()}", min(40), Intensity.HARD,
                    listOf(
                        PlannedItem("Warm-up", "10 min easy"),
                        PlannedItem("Intervals", intervals),
                        PlannedItem("Cool-down", "5–10 min easy"),
                    ),
                    "Hard efforts raise your ceiling — VO₂max — and burn the most per minute.",
                )
                "L" -> PlannedSession(
                    d, "Long ${noun}", min(70), Intensity.MODERATE,
                    listOf(PlannedItem("Long $noun", "${min(70)} min steady, conversational pace")),
                    "Your longest session of the week: endurance is built here.",
                )
                else -> PlannedSession(
                    d, "Easy ${noun}", min(35), Intensity.EASY,
                    listOf(PlannedItem("Easy $noun", "${min(35)} min at a pace you can talk in full sentences")),
                    "Easy volume builds the aerobic base without stealing recovery from strength work.",
                )
            }
        }
    }

    private fun conditioning(d: Discipline, i: Int, age: TrainingAge): PlannedSession {
        val rounds = when (age) {
            TrainingAge.NOVICE -> 3
            TrainingAge.INTERMEDIATE -> 4
            TrainingAge.ADVANCED -> 5
        }
        return if (d == Discipline.HIIT) {
            PlannedSession(
                d, "HIIT circuit ${if (i % 2 == 0) "A" else "B"}", 30, Intensity.HARD,
                listOf(
                    PlannedItem("Format", "$rounds rounds · 40 s work / 20 s rest · 1 min between rounds"),
                    PlannedItem(if (i % 2 == 0) "Jump squats" else "Kettlebell swings", "40 s"),
                    PlannedItem("Push-ups", "40 s"),
                    PlannedItem("Mountain climbers", "40 s"),
                    PlannedItem(if (i % 2 == 0) "Burpees" else "Skater jumps", "40 s"),
                    PlannedItem("Plank", "40 s"),
                ),
                "Short and hard: conditioning that fits between strength days.",
            )
        } else {
            PlannedSession(
                d, "CrossFit WOD ${if (i % 2 == 0) "— AMRAP" else "— EMOM"}", 45, Intensity.HARD,
                if (i % 2 == 0) {
                    listOf(
                        PlannedItem("Warm-up", "10 min: row, air squats, band pull-aparts"),
                        PlannedItem("AMRAP 15 min", "5 pull-ups · 10 push-ups · 15 air squats"),
                        PlannedItem("Finisher", "50 kettlebell swings for time"),
                    )
                } else {
                    listOf(
                        PlannedItem("Warm-up", "10 min: bike, lunges, shoulder circles"),
                        PlannedItem("EMOM 16 min", "odd: 12 wall balls · even: 10 box jumps"),
                        PlannedItem("Strength", "5 × 5 deadlift, moderate weight"),
                    )
                },
                "Mixed-modal conditioning — keep strength sessions heavy and let this be the fast work.",
            )
        }
    }

    private fun skill(d: Discipline, i: Int): PlannedSession {
        val prehab = when (d) {
            Discipline.CLIMBING -> listOf(
                PlannedItem("Antagonist push-ups", "3 × 12 — balances all that pulling"),
                PlannedItem("Wrist extensor curls", "2 × 15"),
            )
            Discipline.TEAM_SPORTS -> listOf(
                PlannedItem("Nordic curl negatives", "3 × 5 — the best-evidenced hamstring-injury prevention"),
                PlannedItem("Copenhagen plank", "3 × 20 s each side — groin"),
            )
            Discipline.MARTIAL_ARTS -> listOf(
                PlannedItem("Neck isometrics", "4 directions × 15 s"),
                PlannedItem("Rotational medicine-ball throws", "3 × 8 each side"),
            )
            else -> listOf(
                PlannedItem("Band external rotations", "3 × 15 — shoulder health"),
                PlannedItem("Lateral lunges", "3 × 10 each side"),
            )
        }
        return PlannedSession(
            d, "${d.label} — practice", 75, Intensity.HARD,
            listOf(PlannedItem("Your session / club training", "60–90 min")) + prehab,
            "Your sport comes first; the 10-minute add-on protects what it loads most.",
        )
    }

    private fun goalLabel(goal: Goal) = when (goal) {
        Goal.HYPERTROPHY -> "Building muscle"
        Goal.STRENGTH -> "Getting stronger"
        Goal.CUT -> "Losing fat"
        Goal.RECOMP -> "Recomposition"
        Goal.MAKE_WEIGHT -> "Making weight"
    }
}

/** Which days of the week to train on, spread out rather than stacked. */
object WeekLayout {
    fun days(n: Int): Set<Int> = when (n.coerceIn(0, 7)) {
        0 -> emptySet()
        1 -> setOf(2)
        2 -> setOf(1, 4)
        3 -> setOf(0, 2, 4)
        4 -> setOf(0, 1, 3, 4)
        5 -> setOf(0, 1, 2, 4, 5)
        6 -> setOf(0, 1, 2, 3, 4, 5)
        else -> (0..6).toSet()
    }
}
