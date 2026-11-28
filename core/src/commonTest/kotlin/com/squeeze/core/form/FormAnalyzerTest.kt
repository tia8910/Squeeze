package com.squeeze.core.form

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FormAnalyzerTest {

    /**
     * A side-on body. The knee bends to [knee] degrees between thigh and shin, the torso leans
     * [lean] degrees forward, and the elbow bends to [elbow] degrees. Left side only, clear.
     */
    private fun body(knee: Double = 175.0, lean: Double = 5.0, elbow: Double = 170.0, hipLine: Double = 0.0): Map<Int, Joint> {
        val ankle = Joint(0.5, 0.9)
        val kneeJ = Joint(0.5, 0.7)
        // Thigh from the knee, at (180 - knee) degrees off straight up.
        val t = (180.0 - knee) * PI / 180.0
        val hip = Joint(kneeJ.x - 0.2 * sin(t), kneeJ.y - 0.2 * cos(t) + hipLine)
        val l = lean * PI / 180.0
        val shoulder = Joint(hip.x + 0.25 * sin(l), hip.y - 0.25 * cos(l))
        val elbowJ = Joint(shoulder.x, shoulder.y + 0.13)
        val e = (180.0 - elbow) * PI / 180.0
        val wrist = Joint(elbowJ.x + 0.12 * sin(e), elbowJ.y + 0.12 * cos(e))
        return mapOf(11 to shoulder, 13 to elbowJ, 15 to wrist, 23 to hip, 25 to kneeJ, 27 to ankle)
    }

    private fun run(a: FormAnalyzer, frames: List<Map<Int, Joint>>) = frames.map { a.update(it) }

    @Test
    fun `angle reads a right angle`() {
        assertEquals(90.0, FormAnalyzer.angle(Joint(0.0, 0.0), Joint(0.0, 1.0), Joint(1.0, 1.0)), 0.001)
    }

    @Test
    fun `a deep upright squat counts as a clean rep`() {
        val a = FormAnalyzer(FormExercise.SQUAT)
        val out = run(a, listOf(175.0, 140.0, 95.0, 85.0, 120.0, 170.0).map { body(knee = it) })
        val done = out.last().completed!!
        assertEquals(1, done.number)
        assertTrue(done.clean)
        assertEquals("Good rep, 1.", out.last().cue)
    }

    @Test
    fun `a shallow squat with a forward lean is counted and coached`() {
        val a = FormAnalyzer(FormExercise.SQUAT)
        val out = run(a, listOf(
            body(knee = 175.0), body(knee = 130.0, lean = 40.0), body(knee = 112.0, lean = 60.0), body(knee = 170.0),
        ))
        val done = out.last().completed!!
        assertEquals(listOf("Go a little deeper.", "Chest up."), done.issues)
        assertEquals("Go a little deeper.", out.last().cue)
    }

    @Test
    fun `a half squat does not count`() {
        val a = FormAnalyzer(FormExercise.SQUAT)
        val out = run(a, listOf(175.0, 140.0, 130.0, 170.0).map { body(knee = it) })
        assertNull(out.last().completed)
        assertEquals(0, out.last().reps)
    }

    @Test
    fun `a full curl counts, a swinging one is called out`() {
        val a = FormAnalyzer(FormExercise.BICEP_CURL)
        val clean = run(a, listOf(170.0, 120.0, 45.0, 120.0, 165.0).map { body(elbow = it) }).last().completed!!
        assertTrue(clean.clean)
        val swing = run(a, listOf(body(elbow = 170.0), body(elbow = 120.0), body(elbow = 45.0, lean = 25.0), body(elbow = 165.0)))
            .last().completed!!
        assertFalse(swing.clean)
        assertTrue("Keep your body still, no swinging." in swing.issues)
    }

    @Test
    fun `nobody in frame asks the user to step back once`() {
        val a = FormAnalyzer(FormExercise.SQUAT)
        val first = a.update(emptyMap())
        val second = a.update(emptyMap())
        assertFalse(first.visible)
        assertEquals("Step back so I can see your whole body.", first.cue)
        assertNull(second.cue)
    }
}
