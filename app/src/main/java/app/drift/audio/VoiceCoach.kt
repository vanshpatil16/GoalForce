package app.drift.audio

import android.content.Context
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import app.drift.R
import java.util.Locale

/** Optional, on-device voice prompts and short UI sound cues. */
object VoiceCoach {
    private const val PREFS = "drift_voice_coach"
    private const val KEY_ENABLED = "enabled"
    private const val SPEECH_ID = "drift_voice_prompt"

    fun isEnabled(context: Context): Boolean = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ENABLED, true)

    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    fun sessionStarted(context: Context) = playSequence(context, listOf(R.raw.sfx_click, R.raw.voice_ready, R.raw.voice_set, R.raw.voice_go))

    fun sessionCompleted(context: Context) = playSequence(context, listOf(R.raw.voice_mission_completed, R.raw.voice_level_up))

    fun tap(context: Context) = play(context, R.raw.sfx_click)

    fun selection(context: Context) = play(context, R.raw.sfx_switch)

    fun speak(context: Context, phrase: String) {
        if (!isEnabled(context) || phrase.isBlank()) return
        val appContext = context.applicationContext
        lateinit var speech: TextToSpeech
        speech = TextToSpeech(appContext) { status ->
            if (status != TextToSpeech.SUCCESS) { speech.shutdown(); return@TextToSpeech }
            val language = Locale.US
            if (speechLanguageAvailable(speech, language)) speech.language = language
            speech.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) = Unit
                override fun onDone(utteranceId: String?) { speech.shutdown() }
                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) { speech.shutdown() }
            })
            speech.speak(phrase, TextToSpeech.QUEUE_FLUSH, null, SPEECH_ID)
        }
    }

    private fun speechLanguageAvailable(speech: TextToSpeech, locale: Locale): Boolean {
        val availability = speech.isLanguageAvailable(locale)
        return availability >= TextToSpeech.LANG_AVAILABLE
    }

    private fun playSequence(context: Context, clips: List<Int>) {
        if (!isEnabled(context)) return
        val appContext = context.applicationContext
        val handler = Handler(Looper.getMainLooper())
        fun playAt(index: Int) {
            if (index >= clips.size) return
            val player = MediaPlayer.create(appContext, clips[index]) ?: run { playAt(index + 1); return }
            player.setOnCompletionListener {
                it.release()
                handler.postDelayed({ playAt(index + 1) }, 110)
            }
            player.setOnErrorListener { failed, _, _ ->
                failed.release()
                handler.post { playAt(index + 1) }
                true
            }
            player.start()
        }
        handler.post { playAt(0) }
    }

    private fun play(context: Context, clip: Int) {
        if (!isEnabled(context)) return
        val player = MediaPlayer.create(context.applicationContext, clip) ?: return
        player.setOnCompletionListener { it.release() }
        player.setOnErrorListener { failed, _, _ -> failed.release(); true }
        player.start()
    }
}
