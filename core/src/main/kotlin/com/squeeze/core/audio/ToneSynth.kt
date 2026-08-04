package com.squeeze.core.audio

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Additive synthesis of the app's audio, as plain PCM.
 *
 * The app ships no audio files. Every sound it makes is generated here from arithmetic,
 * which is worth the small amount of code for three reasons: nothing is added to the APK,
 * there is no licence to honour for a bundled track, and — because this is pure Kotlin with
 * no Android types — the waveform can be tested on the JVM. Audio bugs are otherwise
 * discovered by ear on a device, which is the slowest feedback loop in the project.
 *
 * The properties that actually matter for a sound not being unpleasant — starting and
 * ending in silence, never clipping, looping without a seam — are checked in tests rather
 * than assumed.
 */
object ToneSynth {

    /** How a voice is coloured. Purely a choice of harmonic weights and detune. */
    enum class Timbre(
        internal val harmonics: List<Double>,
        internal val detune: Double,
    ) {
        /** A single sine. Clean, slightly sterile — used where a sound must not draw focus. */
        PURE(listOf(1.0), 0.0),

        /** Bright, struck. Odd and even harmonics with a fast rolloff. */
        BELL(listOf(1.0, 0.45, 0.22, 0.08), 0.0),

        /**
         * Warm and wide. The 0.3% detune beats slowly against the fundamental, which is what
         * keeps a long sustained chord from sounding like a test tone.
         */
        PAD(listOf(1.0, 0.32, 0.14, 0.05), 0.003),
    }

    /**
     * One note.
     *
     * @param frequencyHz fundamental pitch
     * @param startMs offset from the beginning of the buffer
     * @param durationMs total sounding time, including [attackMs] and [releaseMs]
     * @param gain relative loudness before normalisation
     */
    data class Voice(
        val frequencyHz: Double,
        val startMs: Int,
        val durationMs: Int,
        val gain: Double = 1.0,
        val attackMs: Int = 10,
        val releaseMs: Int = 120,
        val timbre: Timbre = Timbre.BELL,
    ) {
        init {
            require(frequencyHz > 0) { "frequencyHz must be positive, was $frequencyHz" }
            require(durationMs > 0) { "durationMs must be positive, was $durationMs" }
            require(startMs >= 0) { "startMs must not be negative, was $startMs" }
            require(attackMs + releaseMs <= durationMs) {
                "attack ($attackMs) + release ($releaseMs) exceeds duration ($durationMs)"
            }
        }
    }

    /** Headroom left below full scale, so normalised output never sits hard against the rail. */
    private const val PEAK_TARGET = 0.89

    /**
     * Renders [voices] to signed 16-bit mono PCM.
     *
     * Output is normalised as a whole rather than per voice: a chord of four notes summed at
     * full gain would clip, and scaling each voice down by a fixed guess would make a single
     * note quieter than it needs to be. Normalising after summing means both a lone blip and
     * a dense chord arrive at the same perceived level.
     */
    fun render(
        voices: List<Voice>,
        totalMs: Int,
        sampleRate: Int = 44_100,
    ): ShortArray {
        require(totalMs > 0) { "totalMs must be positive, was $totalMs" }
        require(sampleRate > 0) { "sampleRate must be positive, was $sampleRate" }

        val sampleCount = (totalMs.toLong() * sampleRate / 1000L).toInt()
        val mixed = DoubleArray(sampleCount)

        for (voice in voices) {
            val start = (voice.startMs.toLong() * sampleRate / 1000L).toInt()
            val length = (voice.durationMs.toLong() * sampleRate / 1000L).toInt()
            if (start >= sampleCount || length <= 0) continue

            val attack = (voice.attackMs.toLong() * sampleRate / 1000L).toInt()
            val release = (voice.releaseMs.toLong() * sampleRate / 1000L).toInt()

            // Anything past the end of the buffer is simply not written. A voice is expected
            // to have finished releasing by then; render() does not fade it out for you,
            // because a silent truncation would be a click and hiding that would make the
            // seam impossible to find later.
            val last = minOf(start + length, sampleCount)

            for (i in start until last) {
                val position = i - start
                val t = position.toDouble() / sampleRate
                val envelope = envelopeAt(position, length, attack, release)
                if (envelope <= 0.0) continue

                var sample = 0.0
                voice.timbre.harmonics.forEachIndexed { index, weight ->
                    val partial = voice.frequencyHz * (index + 1)
                    sample += weight * sin(2.0 * PI * partial * t)

                    if (voice.timbre.detune > 0.0) {
                        val beat = partial * (1.0 + voice.timbre.detune)
                        sample += weight * sin(2.0 * PI * beat * t)
                    }
                }

                mixed[i] += sample * envelope * voice.gain
            }
        }

        return normalise(mixed)
    }

    /**
     * Raised-cosine attack and release.
     *
     * A linear ramp is audible as a corner at both ends of a note; the cosine has zero slope
     * where it meets silence and where it meets the sustain, so neither joint is heard.
     */
    private fun envelopeAt(position: Int, length: Int, attack: Int, release: Int): Double {
        if (position < 0 || position >= length) return 0.0

        if (attack > 0 && position < attack) {
            return 0.5 * (1.0 - cos(PI * position / attack))
        }

        val releaseStart = length - release
        if (release > 0 && position >= releaseStart) {
            val through = (position - releaseStart).toDouble() / release
            return 0.5 * (1.0 + cos(PI * through))
        }

        return 1.0
    }

    /** Scales the mix so its loudest point sits at [PEAK_TARGET], then quantises to 16 bit. */
    private fun normalise(mixed: DoubleArray): ShortArray {
        var peak = 0.0
        for (value in mixed) {
            val magnitude = abs(value)
            if (magnitude > peak) peak = magnitude
        }

        // An empty or silent buffer has no peak to normalise against; scaling by 1/0 would
        // turn digital silence into NaN and then into noise.
        val scale = if (peak > 1e-9) PEAK_TARGET / peak else 0.0

        return ShortArray(mixed.size) { index ->
            (mixed[index] * scale * Short.MAX_VALUE).roundToInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                .toShort()
        }
    }

    /**
     * Equal-tempered pitch, in semitones from A4.
     *
     * Negative goes down: -9 is middle C, +12 is the A above.
     */
    fun pitch(semitonesFromA4: Int): Double = 440.0 * 2.0.pow(semitonesFromA4 / 12.0)
}
