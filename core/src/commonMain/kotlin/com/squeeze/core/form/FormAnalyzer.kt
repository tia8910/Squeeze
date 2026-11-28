package com.squeeze.core.form

import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.sqrt

/** The exercises the form coach can judge from a phone camera. */
enum class FormExercise(val label: String, val logName: String, val muscleGroup: String, val sideOn: Boolean) {
    SQUAT("Squat", "Bodyweight squat", "QUADS", sideOn = true),
    PUSH_UP("Push-up", "Push-up", "CHEST", sideOn = true),
    BICEP_CURL("Bicep curl", "Bicep curl", "BICEPS", sideOn = false),
}

/** One pose landmark: position as a fraction of the frame (y grows downward) and how sure the model is. */
data class Joint(val x: Double, val y: Double, val visibility: Double = 1.0)

/** One judged rep: clean, or the slips the coach called out. */
data class RepResult(val number: Int, val issues: List<String>) {
    val clean: Boolean get() = issues.isEmpty()
}

/**
 * What the coach makes of the latest frame.
 *
 * @param cue something to say now, or null to stay quiet
 * @param completed the rep that just finished, if one did
 * @param slipping the landmark indices to draw amber, where form is slipping right now
 */
data class FormUpdate(
    val reps: Int,
    val cue: String?,
    val completed: RepResult?,
    val slipping: Set<Int>,
    val score: Int,
    val visible: Boolean,
)

/**
 * Counts reps and judges form from pose landmarks, one frame at a time.
 *
 * Each exercise is a joint angle that swings between two ends: the knee for a squat, the elbow
 * for a push-up and a curl. A rep is one full swing out and back, so a half rep never counts.
 * While the rep is in progress the worst value of each form check is kept, and when the rep
 * closes it is judged once, so the voice says one thing per rep instead of nagging every frame.
 *
 * Landmark indices follow the 33-point pose model: 11/12 shoulders, 13/14 elbows, 15/16 wrists,
 * 23/24 hips, 25/26 knees, 27/28 ankles, left then right. The side facing the camera is used.
 */
class FormAnalyzer(val exercise: FormExercise) {

    private var reps = 0
    private var inRep = false
    private var extreme = 0.0
    private var worstLean = 0.0
    private var worstLine = 180.0
    private var worstDrift = 0.0
    private var startLean: Double? = null
    private val results = mutableListOf<RepResult>()
    private var lastVisible = true

    val history: List<RepResult> get() = results

    fun update(joints: Map<Int, Joint>): FormUpdate {
        val side = pickSide(joints)
        if (side == null) {
            val cue = if (lastVisible) "Step back so I can see your whole body." else null
            lastVisible = false
            return FormUpdate(reps, cue, null, emptySet(), score(), visible = false)
        }
        lastVisible = true
        val s = side
        val angle = when (exercise) {
            FormExercise.SQUAT -> angle(s.hip, s.knee, s.ankle)
            FormExercise.PUSH_UP, FormExercise.BICEP_CURL -> angle(s.shoulder, s.elbow, s.wrist)
        }
        val lean = leanFromVertical(s.hip, s.shoulder)
        val line = angle(s.shoulder, s.hip, s.ankle)
        val torso = dist(s.shoulder, s.hip).coerceAtLeast(1e-6)
        val drift = abs(s.elbow.x - s.shoulder.x) / torso

        // Which way the angle goes during the working half of the rep.
        val (top, bottom, flexesDown) = when (exercise) {
            FormExercise.SQUAT -> Triple(TOP_SQUAT, BOTTOM_SQUAT, true)
            FormExercise.PUSH_UP -> Triple(TOP_PUSH, BOTTOM_PUSH, true)
            FormExercise.BICEP_CURL -> Triple(TOP_CURL_EXTENDED, BOTTOM_CURL_FLEXED, true)
        }

        var completed: RepResult? = null
        val slipping = mutableSetOf<Int>()

        if (!inRep) {
            // A rep starts when the angle leaves the extended end.
            if (flexesDown && angle < top - START_MARGIN) {
                inRep = true
                extreme = angle
                worstLean = lean
                worstLine = line
                worstDrift = drift
                startLean = lean
            }
        } else {
            extreme = minOf(extreme, angle)
            worstLean = maxOf(worstLean, lean)
            worstLine = minOf(worstLine, line)
            worstDrift = maxOf(worstDrift, drift)

            // Live colouring while the rep is under way.
            when (exercise) {
                FormExercise.SQUAT -> if (lean > MAX_SQUAT_LEAN) slipping += s.shoulderIndex
                FormExercise.PUSH_UP -> if (line < MIN_BODY_LINE) slipping += s.hipIndex
                FormExercise.BICEP_CURL -> if (drift > MAX_ELBOW_DRIFT) slipping += s.elbowIndex
            }

            if (angle > top) {
                inRep = false
                // Only a full swing counts: the angle has to have reached the working end.
                if (extreme <= bottom + COUNT_GRACE) {
                    reps++
                    completed = RepResult(reps, issues())
                    results += completed
                }
            }
        }

        val cue = completed?.let { r -> r.issues.firstOrNull() ?: "Good rep, ${r.number}." }
        return FormUpdate(reps, cue, completed, slipping, score(), visible = true)
    }

    private fun issues(): List<String> = buildList {
        when (exercise) {
            FormExercise.SQUAT -> {
                if (extreme > DEEP_SQUAT) add("Go a little deeper.")
                if (worstLean > MAX_SQUAT_LEAN) add("Chest up.")
            }
            FormExercise.PUSH_UP -> {
                if (extreme > DEEP_PUSH) add("Lower your chest a little more.")
                if (worstLine < MIN_BODY_LINE) add("Keep your hips in line with your body.")
            }
            FormExercise.BICEP_CURL -> {
                if (extreme > FULL_CURL) add("Curl all the way up.")
                if (abs(worstLean - (startLean ?: worstLean)) > MAX_SWING) add("Keep your body still, no swinging.")
                if (worstDrift > MAX_ELBOW_DRIFT) add("Keep your elbows by your sides.")
            }
        }
    }

    private fun score(): Int =
        if (results.isEmpty()) 100 else results.count { it.clean } * 100 / results.size

    private class Side(
        val shoulder: Joint, val elbow: Joint, val wrist: Joint, val hip: Joint, val knee: Joint, val ankle: Joint,
        val shoulderIndex: Int, val elbowIndex: Int, val hipIndex: Int,
    )

    /** The side nearer the camera: the one the model sees best. Null when neither is clear. */
    private fun pickSide(j: Map<Int, Joint>): Side? {
        fun side(o: Int): Side? {
            val pts = listOf(11, 13, 15, 23, 25, 27).map { j[it + o] ?: return null }
            val needed = when (exercise) {
                FormExercise.BICEP_CURL -> pts.take(4)
                else -> pts
            }
            if (needed.any { it.visibility < MIN_VISIBILITY }) return null
            return Side(pts[0], pts[1], pts[2], pts[3], pts[4], pts[5], 11 + o, 13 + o, 23 + o)
        }
        val left = side(0)
        val right = side(1)
        if (left == null) return right
        if (right == null) return left
        val l = listOf(11, 13, 15, 23, 25, 27).sumOf { j[it]!!.visibility }
        val r = listOf(12, 14, 16, 24, 26, 28).sumOf { j[it]!!.visibility }
        return if (l >= r) left else right
    }

    companion object {
        const val MIN_VISIBILITY = 0.5
        const val START_MARGIN = 15.0
        const val COUNT_GRACE = 15.0

        const val TOP_SQUAT = 160.0
        const val BOTTOM_SQUAT = 105.0
        const val DEEP_SQUAT = 100.0
        const val MAX_SQUAT_LEAN = 50.0

        const val TOP_PUSH = 155.0
        const val BOTTOM_PUSH = 100.0
        const val DEEP_PUSH = 95.0
        const val MIN_BODY_LINE = 155.0

        const val TOP_CURL_EXTENDED = 150.0
        const val BOTTOM_CURL_FLEXED = 60.0
        const val FULL_CURL = 60.0
        const val MAX_SWING = 15.0
        const val MAX_ELBOW_DRIFT = 0.35

        /** The angle at [b] between [a] and [c], in degrees. */
        fun angle(a: Joint, b: Joint, c: Joint): Double {
            val v1x = a.x - b.x
            val v1y = a.y - b.y
            val v2x = c.x - b.x
            val v2y = c.y - b.y
            val n = sqrt(v1x * v1x + v1y * v1y) * sqrt(v2x * v2x + v2y * v2y)
            if (n == 0.0) return 180.0
            return acos(((v1x * v2x + v1y * v2y) / n).coerceIn(-1.0, 1.0)) * 180.0 / kotlin.math.PI
        }

        /** How far the hip-to-shoulder line leans from upright, in degrees. */
        fun leanFromVertical(hip: Joint, shoulder: Joint): Double =
            abs(atan2(shoulder.x - hip.x, hip.y - shoulder.y)) * 180.0 / kotlin.math.PI

        private fun dist(a: Joint, b: Joint) = sqrt((a.x - b.x) * (a.x - b.x) + (a.y - b.y) * (a.y - b.y))
    }
}
