package com.squeeze.app.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import androidx.compose.runtime.staticCompositionLocalOf
import com.squeeze.app.data.settings.UiSettings
import com.squeeze.core.audio.Cue
import com.squeeze.core.audio.ToneSynth
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Plays the app's synthesised audio.
 *
 * Two kinds of sound, deliberately governed by different rules.
 *
 * **Cues** are short and tied to something the user just did. They are tagged as
 * sonification, which means the system lets them sound over music instead of ducking it —
 * a 600ms chime has no business interrupting whatever someone is listening to while they
 * train. They follow the ringer: silenced phone, silent app.
 *
 * **The ambient bed** is music, and behaves like it. It takes real audio focus and gives it
 * up on demand, and it will not start at all if something else is already playing. That last
 * rule is the important one: a fitness app whose background loop stops the user's own
 * playlist is a fitness app that gets uninstalled. It is off by default for the same reason.
 *
 * PCM is generated once per cue and cached. Synthesis of the fourteen-second bed is tens of
 * millions of transcendental calls, so it happens off the main thread and never twice.
 */
@Singleton
class SoundEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val uiSettings: UiSettings,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private val cache = mutableMapOf<Cue, ShortArray>()
    private val cacheLock = Mutex()

    /** The bed's track, held so it can be stopped, and its focus request so it can be dropped. */
    private var ambientTrack: AudioTrack? = null
    private var focusRequest: AudioFocusRequest? = null

    /** Guards [ambientTrack] against overlapping start and stop calls. */
    private val ambientLock = Mutex()

    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        when (change) {
            // Something took over for good — a call, another media app. Stop and stay
            // stopped; silently resuming later would be startling.
            AudioManager.AUDIOFOCUS_LOSS -> stopAmbient()

            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> ambientTrack?.pause()

            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK ->
                ambientTrack?.setVolume(AMBIENT_DUCKED_VOLUME)

            AudioManager.AUDIOFOCUS_GAIN -> ambientTrack?.let {
                it.setVolume(AMBIENT_VOLUME)
                if (it.playState != AudioTrack.PLAYSTATE_PLAYING) it.play()
            }
        }
    }

    /**
     * Plays a one-shot cue, if sound is on and the phone is not silenced.
     *
     * Fire and forget: the track releases itself once the buffer has been consumed, so a
     * caller never has to manage its lifetime.
     */
    fun play(cue: Cue) {
        if (cue.loops) return
        if (!uiSettings.soundEnabled.value) return
        if (audioManager.ringerMode != AudioManager.RINGER_MODE_NORMAL) return

        scope.launch {
            val pcm = pcmFor(cue)

            val track = buildTrack(
                pcm = pcm,
                sampleRate = cue.sampleRate,
                usage = AudioAttributes.USAGE_ASSISTANCE_SONIFICATION,
                contentType = AudioAttributes.CONTENT_TYPE_SONIFICATION,
            ) ?: return@launch

            val started = runCatching {
                track.setVolume(CUE_VOLUME)
                track.play()
            }.isSuccess

            if (!started) {
                track.release()
                return@launch
            }

            // AudioTrack has no completion callback in static mode. A position marker looks
            // like the tidy answer but is not reliable — playback can finish without the
            // marker frame being reported, and the track then leaks for the life of the
            // process. Waiting out the cue's own known duration always terminates.
            delay(cue.totalMs.toLong() + RELEASE_MARGIN_MS)
            runCatching {
                track.stop()
                track.release()
            }
        }
    }

    /**
     * Starts the motivational bed, unless something else is already making sound.
     *
     * Idempotent — calling it while it is already playing does nothing, so it is safe to
     * drive straight from a lifecycle callback.
     */
    fun startAmbient() {
        if (!uiSettings.ambientEnabled.value) return

        scope.launch {
            ambientLock.withLock {
                if (ambientTrack != null) return@withLock

                // Deferring to whatever is already playing. isMusicActive covers the case
                // that matters most: the user's own music, from another app, mid-workout.
                if (audioManager.isMusicActive) return@withLock
                if (!requestFocus()) return@withLock

                val cue = Cue.AMBIENT
                val pcm = pcmFor(cue)

                val track = buildTrack(
                    pcm = pcm,
                    sampleRate = cue.sampleRate,
                    usage = AudioAttributes.USAGE_MEDIA,
                    contentType = AudioAttributes.CONTENT_TYPE_MUSIC,
                )

                if (track == null) {
                    abandonFocus()
                    return@withLock
                }

                runCatching {
                    // -1 repeats forever. The buffer was built so its end meets its start in
                    // silence, which is what makes that repeat inaudible.
                    track.setLoopPoints(0, pcm.size, -1)
                    track.setVolume(AMBIENT_VOLUME)
                    track.play()
                    ambientTrack = track
                }.onFailure {
                    track.release()
                    abandonFocus()
                }
            }
        }
    }

    /** Stops the bed and releases both the track and the audio focus it was holding. */
    fun stopAmbient() {
        scope.launch {
            ambientLock.withLock {
                ambientTrack?.let { track ->
                    runCatching {
                        track.stop()
                        track.release()
                    }
                }
                ambientTrack = null
                abandonFocus()
            }
        }
    }

    /** Renders on first use and caches. Concurrent callers wait rather than each rendering. */
    private suspend fun pcmFor(cue: Cue): ShortArray = cacheLock.withLock {
        cache.getOrPut(cue) {
            withContext(Dispatchers.Default) {
                ToneSynth.render(cue.voices(), cue.totalMs, cue.sampleRate)
            }
        }
    }

    /**
     * Builds a static-mode track already loaded with [pcm].
     *
     * Returns null rather than throwing if the device refuses the format or the buffer.
     * Audio is a nicety here; failing to obtain a track must never take down a screen.
     */
    private fun buildTrack(
        pcm: ShortArray,
        sampleRate: Int,
        usage: Int,
        contentType: Int,
    ): AudioTrack? = runCatching {
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(usage)
                    .setContentType(contentType)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setBufferSizeInBytes(pcm.size * BYTES_PER_SAMPLE)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()

        val written = track.write(pcm, 0, pcm.size)
        if (written < pcm.size) {
            track.release()
            return null
        }

        track
    }.getOrNull()

    private fun requestFocus(): Boolean {
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build(),
            )
            // The bed is atmosphere. If the system wants to duck it for a notification,
            // that is exactly the right outcome and there is no reason to refuse.
            .setWillPauseWhenDucked(false)
            .setOnAudioFocusChangeListener(focusListener)
            .build()

        focusRequest = request
        return audioManager.requestAudioFocus(request) ==
            AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    private fun abandonFocus() {
        focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        focusRequest = null
    }

    private companion object {
        const val BYTES_PER_SAMPLE = 2

        /** Slack beyond a cue's nominal length before its track is torn down. */
        const val RELEASE_MARGIN_MS = 250L

        /** Cues sit just below full scale; they are already short and shaped. */
        const val CUE_VOLUME = 0.85f

        /** The bed sits well back. It is meant to be noticed only if it stops. */
        const val AMBIENT_VOLUME = 0.32f
        const val AMBIENT_DUCKED_VOLUME = 0.12f
    }
}

/**
 * Reaches the engine from composables without threading it through every screen's signature.
 *
 * Defaults to null so a preview or a test that never provides one simply makes no sound,
 * rather than failing to compose.
 */
val LocalSoundEngine = staticCompositionLocalOf<SoundEngine?> { null }
