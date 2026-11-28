package com.squeeze.app.ui.scan

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.SystemClock
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

/**
 * Speaks the auto camera's instructions, so a person three metres from the screen can follow
 * them without walking back to read it.
 *
 * Uses the phone's own text to speech, on the device, nothing sent anywhere. It talks only when
 * there is something new to say: the same instruction is repeated at most every few seconds,
 * and a new one replaces whatever is still being said, because the newest position is the one
 * that matters.
 *
 * It plays through whatever the phone is using for sound: earbuds, a Bluetooth headset, a car's
 * hands-free, or the speaker. While it speaks it asks for a short "duck" of other audio, so a
 * playlist dips for the cue and comes straight back, instead of the cue being lost under it.
 */
class VoiceGuide(context: Context) : TextToSpeech.OnInitListener {

    private val tts = TextToSpeech(context.applicationContext, this)
    private val audio = context.applicationContext.getSystemService(AudioManager::class.java)
    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()
    private val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        .setAudioAttributes(attributes)
        .build()
    private var ready = false
    private var last: String? = null
    private var lastAt = 0L
    private var count = 0

    override fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) return
        val wanted = Locale.getDefault()
        val result = tts.setLanguage(wanted)
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            tts.setLanguage(Locale.US)
        }
        tts.setAudioAttributes(attributes)
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit
            override fun onDone(utteranceId: String?) { release() }
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) { release() }
        })
        tts.setSpeechRate(0.95f)
        ready = true
    }

    /**
     * @param force speak even if the same words were just said, for countdowns and confirmations
     */
    fun say(text: String, force: Boolean = false) {
        if (!ready) return
        val now = SystemClock.elapsedRealtime()
        if (!force && text == last && now - lastAt < REPEAT_MS) return
        // A different instruction may cut in, but not mid-word every frame.
        if (!force && text != last && tts.isSpeaking && now - lastAt < MIN_GAP_MS) return
        last = text
        lastAt = now
        runCatching { audio?.requestAudioFocus(focus) }
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "guide${count++}")
    }

    private fun release() {
        runCatching { audio?.abandonAudioFocusRequest(focus) }
    }

    fun stop() {
        if (ready) tts.stop()
        release()
        last = null
    }

    fun shutdown() {
        tts.stop()
        release()
        tts.shutdown()
    }

    private companion object {
        const val REPEAT_MS = 6_000L
        const val MIN_GAP_MS = 1_800L
    }
}
