package com.jeremysu0818.caption

import android.annotation.SuppressLint
import android.content.Context
import com.jeremysu0818.caption.data.CaptionPreferences
import com.jeremysu0818.caption.data.CaptionRuntimeStore
import com.jeremysu0818.caption.mlkit.MlKitSpeechTranscriber
import com.jeremysu0818.caption.translation.CaptionTranslator
import com.jeremysu0818.caption.whisper.WhisperModelRepository
import com.jeremysu0818.caption.whisper.WhisperTranscriber

@SuppressLint("StaticFieldLeak")
object CaptionGraph {
    val runtimeStore: CaptionRuntimeStore = CaptionRuntimeStore

    lateinit var preferences: CaptionPreferences
        private set

    lateinit var modelRepository: WhisperModelRepository
        private set

    lateinit var transcriber: WhisperTranscriber
        private set

    lateinit var translator: CaptionTranslator
        private set

    lateinit var mlKitSpeechTranscriber: MlKitSpeechTranscriber
        private set

    @Volatile
    private var initialized = false

    fun init(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            val appContext = context.applicationContext
            preferences = CaptionPreferences(appContext)
            modelRepository = WhisperModelRepository(appContext)
            transcriber = WhisperTranscriber(appContext)
            translator = CaptionTranslator()
            mlKitSpeechTranscriber = MlKitSpeechTranscriber()
            initialized = true
        }
    }

    fun ensureInitialized(context: Context) {
        init(context)
    }
}
