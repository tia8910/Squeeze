package com.squeeze.app.ui.scan

import android.content.Context
import android.media.AudioAttributes
import android.os.SystemClock
import android.speech.tts.TextToSpeech
import java.util.Locale

/**
 * Speaks the auto camera's instructions, so a person three metres from the screen can follow
 * them without walking back to read it.
 *
 * Uses the phone's own text to speech, on the device, nothing sent anywhere. It talks only when
 * there is something new to say: the same instruction is repeated at most every few seconds,
 * and a new one replaces whatever is still being said, because the newest position is the one
 * that matters.
 */
class VoiceGuide(context: Context) : TextToSpeech.OnInitListener {

    private val tts = TextToSpeech(context.applicationContext, this)
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
        tts.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build(),
        )
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
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "guide${count++}")
    }

    fun stop() {
        if (ready) tts.stop()
        last = null
    }

    fun shutdown() {
        tts.stop()
        tts.shutdown()
    }

    private companion object {
        const val REPEAT_MS = 6_000L
        const val MIN_GAP_MS = 1_800L
    }
}
