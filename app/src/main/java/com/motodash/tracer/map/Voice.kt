package com.motodash.tracer.map

import android.content.Context
import android.media.AudioAttributes
import android.speech.tts.TextToSpeech
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.motodash.tracer.Prefs
import java.util.Locale

/** เสียงนำทางภาษาไทย (Text-to-Speech ของเครื่อง) */
object Voice {
    private var tts: TextToSpeech? = null
    private var ready = false
    private var prefs: Prefs? = null

    /** เปิด/ปิดเสียง (จำค่าไว้) */
    var enabled by mutableStateOf(true)
        private set

    fun init(ctx: Context) {
        if (tts != null) return
        val p = Prefs(ctx)
        prefs = p
        enabled = p.voiceNav
        tts = TextToSpeech(ctx.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val r = tts?.setLanguage(Locale("th", "TH"))
                ready = r != TextToSpeech.LANG_MISSING_DATA && r != TextToSpeech.LANG_NOT_SUPPORTED
                tts?.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
            }
        }
    }

    fun toggle() {
        enabled = !enabled
        prefs?.voiceNav = enabled
        if (enabled) speak("เปิดเสียงนำทาง") else tts?.stop()
    }

    fun speak(text: String) {
        if (enabled && ready) tts?.speak(text.replace(" · ", " "), TextToSpeech.QUEUE_FLUSH, null, "nav")
    }

    /** ระยะทางแบบพูด เช่น "300 เมตร", "1.2 กิโลเมตร" */
    fun spoken(m: Double): String =
        if (m >= 1000) "%.1f กิโลเมตร".format(m / 1000) else "${((m / 50).toInt().coerceAtLeast(1)) * 50} เมตร"
}
