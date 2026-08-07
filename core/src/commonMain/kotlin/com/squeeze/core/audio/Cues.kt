package com.squeeze.core.audio

import com.squeeze.core.audio.ToneSynth.Timbre
import com.squeeze.core.audio.ToneSynth.Voice
import com.squeeze.core.audio.ToneSynth.pitch

/**
 * Every sound the app can make, as note data.
 *
 * Kept here rather than in the Android module so the music is testable and so changing a
 * chord does not mean touching playback code. Each entry declares its own sample rate: the
 * short cues are bright and want the full rate, while the ambient bed has no content above
 * a few kilohertz and is rendered at half, which halves both the synthesis cost and the
 * memory it occupies for the whole time it is looping.
 */
enum class Cue(
    val totalMs: Int,
    val sampleRate: Int,
    val loops: Boolean,
) {
    /** The celebration screen. A major arpeggio that arrives and opens out. */
    CELEBRATION(2_100, 44_100, false),

    /** Shutter confirmation. Short and dry, so it reads as a mechanism rather than music. */
    CAPTURE(150, 44_100, false),

    /**
     * The motivational bed. A slow four-chord progression that breathes.
     *
     * Long on purpose. A short loop announces itself within a minute and then grates; at
     * fourteen seconds with chords that swell and fade, the repeat is hard to fix on, which
     * is the difference between atmosphere and a ringtone.
     */
    AMBIENT(14_000, 22_050, true),
    ;

    /** The notes, built on demand — a cue that is never played is never assembled. */
    fun voices(): List<Voice> = when (this) {
        CELEBRATION -> celebration()
        CAPTURE -> capture()
        AMBIENT -> ambient()
    }
}

// C major arpeggio, each note held so they accumulate into the full chord by the last one.
private fun celebration(): List<Voice> = listOf(
    Voice(pitch(3), startMs = 0, durationMs = 1_500, attackMs = 8, releaseMs = 900, gain = 0.85),
    Voice(pitch(7), startMs = 130, durationMs = 1_500, attackMs = 8, releaseMs = 900, gain = 0.85),
    Voice(pitch(10), startMs = 260, durationMs = 1_500, attackMs = 8, releaseMs = 900, gain = 0.9),
    Voice(pitch(15), startMs = 390, durationMs = 1_710, attackMs = 8, releaseMs = 1_200),
    // An octave above the root, quiet, arriving last — it reads as sparkle rather than as a
    // fifth note in the run.
    Voice(pitch(27), startMs = 470, durationMs = 1_600, attackMs = 12, releaseMs = 1_300, gain = 0.3),
)

private fun capture(): List<Voice> = listOf(
    Voice(
        frequencyHz = pitch(15),
        startMs = 0,
        durationMs = 150,
        attackMs = 4,
        releaseMs = 120,
        gain = 0.7,
        timbre = Timbre.PURE,
    ),
)

/**
 * C - Am - F - G, voiced low and wide, each chord swelling over its neighbour.
 *
 * The chords overlap by 500ms so one is always fading as the next arrives, and the last
 * finishes its release exactly at the end of the buffer. That is what makes the loop
 * seamless: the join is silence meeting silence, so there is no discontinuity to click.
 */
private fun ambient(): List<Voice> {
    val chords = listOf(
        listOf(-21, -14, -5, 5), // Cadd9
        listOf(-24, -17, -9, -2), // Am7
        listOf(-28, -21, -12, -5), // Fmaj7
        listOf(-26, -19, -10, -4), // G7 — the seventh is what pulls it back round to C
    )

    return chords.flatMapIndexed { index, chord ->
        val start = index * 3_500
        chord.mapIndexed { voiceIndex, semitone ->
            Voice(
                frequencyHz = pitch(semitone),
                startMs = start,
                durationMs = 3_500,
                // Upper voices sit back, so the chord reads as a bed rather than as a melody
                // competing with whatever the user is reading on screen.
                gain = if (voiceIndex >= 2) 0.55 else 0.85,
                attackMs = 1_400,
                releaseMs = 1_800,
                timbre = Timbre.PAD,
            )
        }
    }
}
