package com.squeeze.core.audio

import com.squeeze.core.audio.ToneSynth.Timbre
import com.squeeze.core.audio.ToneSynth.Voice
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * These check the properties that decide whether a generated sound is bearable.
 *
 * Clipping, clicks at note boundaries and a seam at the loop point are the three ways
 * synthesised audio goes wrong, and all three are obvious on a device and invisible in code
 * review. Asserting them on the JVM is the only way they get caught before shipping.
 */
class ToneSynthTest {

    private val rate = 44_100

    @Test
    fun `buffer length matches the requested duration`() {
        val pcm = ToneSynth.render(
            voices = listOf(Voice(440.0, startMs = 0, durationMs = 500)),
            totalMs = 1_000,
            sampleRate = rate,
        )

        assertEquals(rate, pcm.size)
    }

    @Test
    fun `no voices produces digital silence rather than noise`() {
        val pcm = ToneSynth.render(voices = emptyList(), totalMs = 100, sampleRate = rate)

        // The normalisation step divides by the peak. An empty buffer has none, so this is
        // really a check that the zero-peak guard holds and no NaN reached the output.
        assertTrue(pcm.all { it.toInt() == 0 }, "silence should stay silent")
    }

    @Test
    fun `output never clips even when many voices sum`() {
        // Twelve simultaneous voices at full gain would sum well past full scale unnormalised.
        val voices = (0 until 12).map { index ->
            Voice(
                frequencyHz = ToneSynth.pitch(index),
                startMs = 0,
                durationMs = 800,
                gain = 1.0,
                timbre = Timbre.PAD,
            )
        }

        val pcm = ToneSynth.render(voices, totalMs = 900, sampleRate = rate)
        val peak = pcm.maxOf { abs(it.toInt()) }

        assertTrue(peak <= Short.MAX_VALUE.toInt(), "peak $peak exceeded full scale")
        // Normalisation should also actually be reaching its target, not silently collapsing
        // the mix to something inaudible.
        assertTrue(peak > 25_000, "normalised peak $peak is far below the intended level")
    }

    @Test
    fun `a note starts and ends in silence so it cannot click`() {
        val pcm = ToneSynth.render(
            voices = listOf(Voice(440.0, startMs = 0, durationMs = 500, attackMs = 10, releaseMs = 100)),
            totalMs = 500,
            sampleRate = rate,
        )

        assertEquals(0, pcm.first().toInt(), "attack must begin at zero amplitude")
        assertTrue(abs(pcm.last().toInt()) < 40, "release must decay to silence, ended at ${pcm.last()}")
    }

    @Test
    fun `the envelope rises and falls rather than switching on`() {
        val pcm = ToneSynth.render(
            voices = listOf(Voice(440.0, startMs = 0, durationMs = 1_000, attackMs = 200, releaseMs = 300)),
            totalMs = 1_000,
            sampleRate = rate,
        )

        fun peakNear(ms: Int): Int {
            val centre = ms * rate / 1000
            val window = rate / 100
            return (centre - window until centre + window)
                .filter { it in pcm.indices }
                .maxOf { abs(pcm[it].toInt()) }
        }

        val early = peakNear(50)
        val sustain = peakNear(500)
        val late = peakNear(950)

        assertTrue(early < sustain, "attack should still be climbing at 50ms ($early vs $sustain)")
        assertTrue(late < sustain, "release should be falling at 950ms ($late vs $sustain)")
    }

    @Test
    fun `a pure tone oscillates at the frequency it was asked for`() {
        val hz = 440.0
        val pcm = ToneSynth.render(
            // No attack or release shaping in the measured region: the envelope does not
            // change the period, but a decaying tail makes crossings harder to count.
            voices = listOf(
                Voice(hz, startMs = 0, durationMs = 1_000, attackMs = 5, releaseMs = 5, timbre = Timbre.PURE),
            ),
            totalMs = 1_000,
            sampleRate = rate,
        )

        // Count sign changes over the sustained middle half, then extrapolate to a second.
        val from = rate / 4
        val to = rate * 3 / 4
        var crossings = 0
        for (i in from + 1 until to) {
            val previous = pcm[i - 1].toInt()
            val current = pcm[i].toInt()
            if (previous <= 0 && current > 0) crossings++
        }

        val measuredHz = crossings * 2.0 // half a second of samples
        assertTrue(
            abs(measuredHz - hz) < 3.0,
            "expected about $hz Hz, measured $measuredHz Hz",
        )
    }

    @Test
    fun `a voice reaching past the buffer end is truncated rather than wrapping`() {
        val pcm = ToneSynth.render(
            voices = listOf(Voice(440.0, startMs = 400, durationMs = 1_000)),
            totalMs = 500,
            sampleRate = rate,
        )

        assertEquals(rate / 2, pcm.size)
        // Everything before the voice starts must be untouched.
        val beforeStart = pcm.take(400 * rate / 1000)
        assertTrue(beforeStart.all { it.toInt() == 0 }, "audio appeared before the voice began")
    }

    @Test
    fun `an envelope longer than the note is rejected at construction`() {
        assertFailsWith<IllegalArgumentException> {
            Voice(440.0, startMs = 0, durationMs = 100, attackMs = 80, releaseMs = 80)
        }
    }

    @Test
    fun `pitch follows equal temperament`() {
        assertEquals(440.0, ToneSynth.pitch(0), 1e-9)
        assertEquals(880.0, ToneSynth.pitch(12), 1e-9)
        assertEquals(220.0, ToneSynth.pitch(-12), 1e-9)
        // Middle C, nine semitones below A4.
        assertEquals(261.63, ToneSynth.pitch(-9), 0.01)
        // And the octave below it, which is where the ambient bed's root sits.
        assertEquals(130.81, ToneSynth.pitch(-21), 0.01)
    }
}

/**
 * The cues are content, not machinery, so these check they are well formed rather than that
 * they sound a particular way — taste is not testable, but a chord that clips is.
 */
class CueTest {

    @Test
    fun `every cue renders without clipping and to the right length`() {
        Cue.entries.forEach { cue ->
            val pcm = ToneSynth.render(cue.voices(), cue.totalMs, cue.sampleRate)

            val expected = (cue.totalMs.toLong() * cue.sampleRate / 1000L).toInt()
            assertEquals(expected, pcm.size, "${cue.name} rendered the wrong number of samples")

            val peak = pcm.maxOf { abs(it.toInt()) }
            assertTrue(peak <= Short.MAX_VALUE.toInt(), "${cue.name} clipped at $peak")
            assertTrue(peak > 20_000, "${cue.name} rendered far too quietly (peak $peak)")
        }
    }

    @Test
    fun `every cue begins in silence`() {
        Cue.entries.forEach { cue ->
            val pcm = ToneSynth.render(cue.voices(), cue.totalMs, cue.sampleRate)
            assertEquals(0, pcm.first().toInt(), "${cue.name} started with a discontinuity")
        }
    }

    @Test
    fun `a looping cue joins to itself without a seam`() {
        val looping = Cue.entries.filter { it.loops }
        assertTrue(looping.isNotEmpty(), "no looping cue exists to check")

        looping.forEach { cue ->
            val pcm = ToneSynth.render(cue.voices(), cue.totalMs, cue.sampleRate)

            // The loop point is the join between the last sample and the first. If both are
            // at silence the transition is inaudible; a large step there is a click, once
            // per repeat, which is the most irritating way for a bed to fail.
            val step = abs(pcm.last().toInt() - pcm.first().toInt())
            assertTrue(step < 200, "${cue.name} steps by $step at the loop point")
        }
    }

    @Test
    fun `no voice in any cue outlives its buffer`() {
        Cue.entries.forEach { cue ->
            cue.voices().forEach { voice ->
                val end = voice.startMs + voice.durationMs
                assertTrue(
                    end <= cue.totalMs,
                    "${cue.name} has a voice ending at ${end}ms, past its ${cue.totalMs}ms buffer",
                )
            }
        }
    }
}
