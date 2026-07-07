package com.jeremysu0818.caption.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import com.jeremysu0818.caption.CaptionGraph
import com.jeremysu0818.caption.MainActivity
import com.jeremysu0818.caption.R
import com.jeremysu0818.caption.audio.InMemoryWavWriter
import com.jeremysu0818.caption.audio.SystemAudioCapture
import com.jeremysu0818.caption.audio.VoiceActivityDetector
import com.jeremysu0818.caption.audio.WavFileWriter
import com.jeremysu0818.caption.data.CaptionRuntimeStore
import com.jeremysu0818.caption.data.SpeechEngineOption
import com.jeremysu0818.caption.overlay.FloatingCaptionWindow
import com.jeremysu0818.caption.tile.CaptionTileService
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

class CaptionCaptureService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mainHandler = Handler(Looper.getMainLooper())
    private var sessionJob: Job? = null
    private var mediaProjection: MediaProjection? = null
    private var projectionCallback: MediaProjection.Callback? = null
    private var overlayWindow: FloatingCaptionWindow? = null

    override fun onCreate() {
        super.onCreate()
        CaptionGraph.ensureInitialized(this)
        createNotificationChannel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }

            ACTION_START -> {
                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, Int.MIN_VALUE)
                val resultData = intent.projectionResultData()
                if (resultCode == Int.MIN_VALUE || resultData == null) {
                    CaptionRuntimeStore.setError("缺少 MediaProjection 授權結果。")
                    stopSelf()
                    return START_NOT_STICKY
                }
                startForegroundForProjection("準備即時字幕")
                startSession(resultCode, resultData)
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        stopSession("已停止", stopProjection = true, removeForeground = true)
        runBlocking(Dispatchers.Default) {
            CaptionGraph.transcriber.release()
            CaptionGraph.translator.close()
            CaptionGraph.mlKitSpeechTranscriber.close()
        }
        serviceScope.cancel()
        isRunning = false
        CaptionTileService.requestTileRefresh(this)
        super.onDestroy()
    }

    private fun startSession(resultCode: Int, resultData: Intent) {
        stopSession("重新啟動", stopProjection = true, removeForeground = false)
        isRunning = true
        CaptionTileService.requestTileRefresh(this)

        val overlay = FloatingCaptionWindow(this).also {
            it.show()
            it.updateStatus("準備即時字幕")
        }
        overlayWindow = overlay
        CaptionRuntimeStore.setRunning("準備即時字幕")

        sessionJob = serviceScope.launch {
            try {
                verifyRuntimeRequirements()
                val projection = createMediaProjection(resultCode, resultData)
                val settingsAtStart = CaptionGraph.preferences.settings.value
                val modelOption = settingsAtStart.model
                val modelFile = if (settingsAtStart.speechEngine == SpeechEngineOption.WHISPER) {
                    val downloadStatusJob = launch {
                        CaptionGraph.modelRepository.downloadState.collectLatest { state ->
                            if (state.model == modelOption && state.isDownloading) {
                                val status = if (state.totalBytes > 0) {
                                    "下載 ${state.model.displayName} ${state.progress.asPercent()}"
                                } else {
                                    "下載 ${state.model.displayName}"
                                }
                                overlay.updateStatus(status)
                                CaptionRuntimeStore.updateStatus(status)
                                updateNotification(status)
                            }
                        }
                    }

                    overlay.updateStatus("確認 Whisper 模型")
                    CaptionRuntimeStore.updateStatus("確認 Whisper 模型")
                    CaptionGraph.modelRepository.ensureModel(modelOption).also {
                        downloadStatusJob.cancel()
                    }
                } else {
                    null
                }

                overlay.updateStatus("擷取系統音訊")
                CaptionRuntimeStore.updateStatus("擷取系統音訊")
                updateNotification("擷取系統音訊")
                if (settingsAtStart.speechEngine == SpeechEngineOption.WHISPER) {
                    runWhisperBatchCaptureLoop(
                        projection = projection,
                        modelFile = requireNotNull(modelFile),
                        modelOption = modelOption,
                        overlay = overlay,
                    )
                } else {
                    runMlKitStreamingCaptureLoop(
                        projection = projection,
                        overlay = overlay,
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                val message = error.message ?: "即時字幕服務失敗。"
                overlay.updateStatus(message)
                CaptionRuntimeStore.setError(message)
                updateNotification(message)
                stopSelf()
            }
        }
    }

    // ========================================================================
    // Whisper batch capture: high-accuracy mode with VAD sentence commits.
    // ========================================================================

    private suspend fun runWhisperBatchCaptureLoop(
        projection: MediaProjection,
        modelFile: File,
        modelOption: com.jeremysu0818.caption.data.WhisperModelOption,
        overlay: FloatingCaptionWindow,
    ) = coroutineScope {
        withContext(Dispatchers.Default) {
            overlay.updateStatus("載入 Whisper 模型")
            CaptionRuntimeStore.updateStatus("載入 Whisper 模型")
            CaptionGraph.transcriber.ensureModelLoaded(modelFile)
        }

        val timing = modelOption.batchTiming()
        val vad = VoiceActivityDetector(
            speechThreshold = 2.5f,
            noiseFloorAlpha = 0.93f,
            absoluteMinRms = 0.003f,
        ).apply { hangoverChunks = timing.vadHangoverChunks }

        val audioFrames = Channel<ShortArray>(
            capacity = 240,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )
        val capture = SystemAudioCapture(projection)
        val captureJob = launch {
            capture.captureChunks(
                output = audioFrames,
                chunkDurationMs = WHISPER_VAD_FRAME_MS,
            )
        }

        val processingJob = launch(Dispatchers.Default) {
            val chunkDir = File(cacheDir, "caption_chunks").apply { mkdirs() }
            var index = 0L
            val preSpeechFrames = ArrayDeque<ShortArray>()
            var preSpeechSamples = 0
            val utteranceFrames = ArrayList<ShortArray>()
            var utteranceSamples = 0
            var speechDetected = false
            var silenceAfterSpeechSamples = 0

            for (frame in audioFrames) {
                val isSpeech = vad.isSpeech(frame)

                if (isSpeech) {
                    if (!speechDetected) {
                        speechDetected = true
                        utteranceFrames.clear()
                        utteranceSamples = 0
                        preSpeechFrames.forEach { buffered ->
                            utteranceFrames.add(buffered)
                            utteranceSamples += buffered.size
                        }

                        withContext(Dispatchers.Main.immediate) {
                            overlay.updateStatus("Whisper 聆聽中...")
                            CaptionRuntimeStore.updateStatus("Whisper 聆聽中...")
                        }
                    }

                    utteranceFrames.add(frame)
                    utteranceSamples += frame.size
                    silenceAfterSpeechSamples = 0
                    continue
                }

                if (!speechDetected) {
                    preSpeechFrames.addLast(frame)
                    preSpeechSamples += frame.size
                    while (preSpeechSamples > timing.preSpeechSamples && preSpeechFrames.isNotEmpty()) {
                        preSpeechSamples -= preSpeechFrames.removeFirst().size
                    }
                    continue
                }

                utteranceFrames.add(frame)
                utteranceSamples += frame.size
                silenceAfterSpeechSamples += frame.size

                val shouldCommit = silenceAfterSpeechSamples >= timing.silenceCommitSamples ||
                    utteranceSamples >= timing.maxUtteranceSamples
                if (!shouldCommit || utteranceSamples < timing.minUtteranceSamples) {
                    continue
                }

                val samples = utteranceFrames.flattenShorts(utteranceSamples)
                utteranceFrames.clear()
                utteranceSamples = 0
                speechDetected = false
                silenceAfterSpeechSamples = 0
                preSpeechFrames.clear()
                preSpeechSamples = 0

                val wavFile = File(chunkDir, "whisper_sentence_${index++}.wav")

                try {
                    withContext(Dispatchers.Main.immediate) {
                        overlay.updateStatus("Whisper 轉錄中")
                        CaptionRuntimeStore.updateStatus("Whisper 轉錄中")
                    }

                    InMemoryWavWriter.write(wavFile, samples)
                    val settings = CaptionGraph.preferences.settings.value
                    val whisperLanguage = if (settings.translationEnabled) {
                        settings.sourceLanguageTag
                    } else {
                        "auto"
                    }

                    val sourceText = CaptionGraph.transcriber.transcribe(
                        wavFile = wavFile,
                        modelFile = modelFile,
                        languageTag = whisperLanguage,
                    ).cleanWhisperText()

                    if (sourceText.isBlank()) continue

                    val translatedText = if (settings.translationEnabled) {
                        withContext(Dispatchers.Main.immediate) {
                            overlay.updateStatus("ML Kit 翻譯中")
                            CaptionRuntimeStore.updateStatus("ML Kit 翻譯中")
                        }
                        CaptionGraph.translator.translate(
                            text = sourceText,
                            sourceLanguageTag = settings.sourceLanguageTag,
                            targetLanguageTag = settings.targetLanguageTag,
                        )
                    } else {
                        null
                    }

                    withContext(Dispatchers.Main.immediate) {
                        overlay.updateCaption(sourceText, translatedText)
                        overlay.updateStatus("即時字幕執行中")
                        CaptionRuntimeStore.updateCaption(sourceText, translatedText)
                    }
                } catch (error: Throwable) {
                    if (error is CancellationException) throw error
                    Log.e(TAG, "Whisper batch inference failed", error)
                    val message = error.message ?: "Whisper 轉錄失敗。"
                    withContext(Dispatchers.Main.immediate) {
                        overlay.updateStatus(message)
                        CaptionRuntimeStore.setError(message)
                    }
                } finally {
                    wavFile.delete()
                }
            }
        }

        try {
            processingJob.join()
        } finally {
            audioFrames.close()
            captureJob.cancel()
        }
    }

    // ========================================================================
    // Legacy Whisper capture loop (kept for reference, no longer used)
    // ========================================================================

    private suspend fun runWhisperCaptureLoop(
        projection: MediaProjection,
        modelFile: File,
        overlay: FloatingCaptionWindow,
    ) = coroutineScope {
        val audioChunks = Channel<ShortArray>(
            capacity = 2,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )
        val capture = SystemAudioCapture(projection)
        val captureJob = launch {
            capture.captureChunks(audioChunks, chunkDurationMs = SpeechEngineOption.WHISPER.chunkDurationMs())
        }
        val processingJob = launch(Dispatchers.Default) {
            val chunkDir = File(cacheDir, "caption_chunks").apply { mkdirs() }
            var index = 0L
            for (samples in audioChunks) {
                val chunkFile = File(chunkDir, "chunk_${index++}.wav")
                try {
                    WavFileWriter.writePcm16Mono(chunkFile, samples)
                    val settings = CaptionGraph.preferences.settings.value
                    withContext(Dispatchers.Main.immediate) {
                        val status = "${settings.speechEngine.label} 轉錄中"
                        overlay.updateStatus(status)
                        CaptionRuntimeStore.updateStatus(status)
                    }

                    val sourceText = when (settings.speechEngine) {
                        SpeechEngineOption.WHISPER -> {
                            val whisperLanguage = if (settings.translationEnabled) {
                                settings.sourceLanguageTag
                            } else {
                                "auto"
                            }
                            CaptionGraph.transcriber.transcribe(
                                wavFile = chunkFile,
                                modelFile = modelFile,
                                languageTag = whisperLanguage,
                            )
                        }
                        else -> continue
                    }
                    if (sourceText.isBlank()) continue

                    val translatedText = if (settings.translationEnabled) {
                        withContext(Dispatchers.Main.immediate) {
                            overlay.updateStatus("ML Kit 翻譯中")
                            CaptionRuntimeStore.updateStatus("ML Kit 翻譯中")
                        }
                        CaptionGraph.translator.translate(
                            text = sourceText,
                            sourceLanguageTag = settings.sourceLanguageTag,
                            targetLanguageTag = settings.targetLanguageTag,
                        )
                    } else {
                        null
                    }

                    withContext(Dispatchers.Main.immediate) {
                        overlay.updateCaption(sourceText, translatedText)
                        overlay.updateStatus("即時字幕執行中")
                        CaptionRuntimeStore.updateCaption(sourceText, translatedText)
                    }
                } catch (error: Throwable) {
                    if (error is CancellationException) throw error
                    val message = error.message ?: "字幕處理失敗。"
                    withContext(Dispatchers.Main.immediate) {
                        overlay.updateStatus(message)
                        CaptionRuntimeStore.setError(message)
                    }
                } finally {
                    chunkFile.delete()
                }
            }
        }

        try {
            processingJob.join()
        } finally {
            audioChunks.close()
            captureJob.cancel()
        }
    }

    // ========================================================================
    // ML Kit streaming capture loop (unchanged)
    // ========================================================================

    private suspend fun runMlKitStreamingCaptureLoop(
        projection: MediaProjection,
        overlay: FloatingCaptionWindow,
    ) = coroutineScope {
        val audioChunks = Channel<ShortArray>(
            capacity = 16,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )
        val capture = SystemAudioCapture(projection)
        val captureJob = launch {
            capture.captureChunks(
                output = audioChunks,
                chunkDurationMs = SpeechEngineOption.MLKIT_BASIC.chunkDurationMs(),
            )
        }
        val recognitionJob = launch(Dispatchers.Default) {
            val initialSettings = CaptionGraph.preferences.settings.value
            CaptionGraph.mlKitSpeechTranscriber.stream(
                audioChunks = audioChunks,
                languageTag = initialSettings.sourceLanguageTag,
                engine = initialSettings.speechEngine,
                onStatus = { status ->
                    withContext(Dispatchers.Main.immediate) {
                        overlay.updateStatus(status)
                        CaptionRuntimeStore.updateStatus(status)
                    }
                },
                onPartialText = { partial ->
                    withContext(Dispatchers.Main.immediate) {
                        overlay.updateCaption(partial, null)
                        CaptionRuntimeStore.updateCaption(partial, null)
                    }
                },
                onFinalText = { sourceText ->
                    val settings = CaptionGraph.preferences.settings.value
                    val translatedText = if (settings.translationEnabled) {
                        withContext(Dispatchers.Main.immediate) {
                            overlay.updateStatus("ML Kit 翻譯中")
                            CaptionRuntimeStore.updateStatus("ML Kit 翻譯中")
                        }
                        CaptionGraph.translator.translate(
                            text = sourceText,
                            sourceLanguageTag = settings.sourceLanguageTag,
                            targetLanguageTag = settings.targetLanguageTag,
                        )
                    } else {
                        null
                    }
                    withContext(Dispatchers.Main.immediate) {
                        overlay.updateCaption(sourceText, translatedText)
                        overlay.updateStatus("即時字幕執行中")
                        CaptionRuntimeStore.updateCaption(sourceText, translatedText)
                    }
                },
            )
        }

        try {
            recognitionJob.join()
        } finally {
            audioChunks.close()
            captureJob.cancel()
        }
    }

    // ========================================================================
    // MediaProjection helpers
    // ========================================================================

    private fun createMediaProjection(resultCode: Int, resultData: Intent): MediaProjection {
        val manager = getSystemService(MediaProjectionManager::class.java)
        val projection = manager.getMediaProjection(resultCode, resultData)
            ?: throw IllegalStateException("無法取得 MediaProjection token。")
        val callback = object : MediaProjection.Callback() {
            override fun onStop() {
                mainHandler.post {
                    mediaProjection = null
                    projectionCallback = null
                    stopSelf()
                }
            }
        }
        projection.registerCallback(callback, mainHandler)
        mediaProjection = projection
        projectionCallback = callback
        return projection
    }

    private fun verifyRuntimeRequirements() {
        if (!Settings.canDrawOverlays(this)) {
            throw SecurityException("尚未允許覆蓋其他應用程式。")
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            throw SecurityException("尚未允許錄音權限。")
        }
    }

    private fun stopSession(status: String, stopProjection: Boolean, removeForeground: Boolean = true) {
        sessionJob?.cancel()
        sessionJob = null
        overlayWindow?.dismiss()
        overlayWindow = null

        val projection = mediaProjection
        val callback = projectionCallback
        if (projection != null && callback != null) {
            runCatching { projection.unregisterCallback(callback) }
        }
        projectionCallback = null
        mediaProjection = null
        if (stopProjection) {
            runCatching { projection?.stop() }
        }

        isRunning = false
        CaptionRuntimeStore.setStopped(status)
        if (removeForeground) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        }
    }

    // ========================================================================
    // Notification helpers
    // ========================================================================

    private fun startForegroundForProjection(status: String) {
        val notification = buildNotification(status)
        startForeground(
            NOTIFICATION_ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION,
        )
    }

    private fun updateNotification(status: String) {
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, buildNotification(status))
    }

    private fun buildNotification(status: String): Notification {
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, CaptionCaptureService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_caption)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(status)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openIntent)
            .addAction(R.drawable.ic_stat_caption, getString(R.string.action_stop), stopIntent)
            .build()
    }

    private fun createNotificationChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        )
        manager.createNotificationChannel(channel)
    }

    // ========================================================================
    // Utility extensions
    // ========================================================================

    private fun Intent.projectionResultData(): Intent? =
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            getParcelableExtra(EXTRA_RESULT_DATA)
        }

    private fun Float.asPercent(): String = "${(this * 100).toInt().coerceIn(0, 100)}%"

    private fun Iterable<ShortArray>.flattenShorts(sampleCount: Int): ShortArray {
        val output = ShortArray(sampleCount)
        var offset = 0
        for (chunk in this) {
            val count = minOf(chunk.size, output.size - offset)
            if (count <= 0) break
            System.arraycopy(chunk, 0, output, offset, count)
            offset += count
        }
        return output
    }

    private fun String.cleanWhisperText(): String =
        lineSequence()
            .map { it.substringAfter("]:", it).trim() }
            .filter { it.isNotBlank() }
            .joinToString(" ")
            .replace(Regex("\\s+"), " ")
            // Filter out common Whisper hallucination patterns on silence
            .replace(Regex("^\\[.*?]\\s*$"), "")
            .replace(Regex("^\\(.*?\\)\\s*$"), "")
            .trim()

    private fun SpeechEngineOption.chunkDurationMs(): Int =
        when (this) {
            SpeechEngineOption.WHISPER -> 2_500
            SpeechEngineOption.MLKIT_BASIC,
            SpeechEngineOption.MLKIT_ADVANCED -> 100
        }

    /**
     * Whisper high-accuracy mode waits for VAD silence before running one
     * batch inference over the whole utterance.
     */
    private data class WhisperBatchTiming(
        val preSpeechMs: Int = 320,
        val minUtteranceMs: Int = 500,
        val silenceCommitMs: Int,
        val maxUtteranceMs: Int = 30_000,
        val vadHangoverChunks: Int,
    ) {
        val preSpeechSamples: Int = SystemAudioCapture.SAMPLE_RATE * preSpeechMs / 1_000
        val minUtteranceSamples: Int = SystemAudioCapture.SAMPLE_RATE * minUtteranceMs / 1_000
        val silenceCommitSamples: Int = SystemAudioCapture.SAMPLE_RATE * silenceCommitMs / 1_000
        val maxUtteranceSamples: Int = SystemAudioCapture.SAMPLE_RATE * maxUtteranceMs / 1_000
    }

    private fun com.jeremysu0818.caption.data.WhisperModelOption.batchTiming(): WhisperBatchTiming =
        when (this) {
            com.jeremysu0818.caption.data.WhisperModelOption.TINY -> WhisperBatchTiming(
                silenceCommitMs = 800,
                vadHangoverChunks = 4,
            )
            com.jeremysu0818.caption.data.WhisperModelOption.BASE -> WhisperBatchTiming(
                silenceCommitMs = 900,
                vadHangoverChunks = 5,
            )
            com.jeremysu0818.caption.data.WhisperModelOption.SMALL -> WhisperBatchTiming(
                silenceCommitMs = 1_000,
                vadHangoverChunks = 5,
            )
            com.jeremysu0818.caption.data.WhisperModelOption.MEDIUM -> WhisperBatchTiming(
                silenceCommitMs = 1_200,
                vadHangoverChunks = 6,
            )
        }

    companion object {
        private const val TAG = "CaptionCapture"
        const val ACTION_START = "com.jeremysu0818.caption.action.START"
        const val ACTION_STOP = "com.jeremysu0818.caption.action.STOP"
        private const val EXTRA_RESULT_CODE = "extra_result_code"
        private const val EXTRA_RESULT_DATA = "extra_result_data"
        private const val CHANNEL_ID = "caption_capture"
        private const val NOTIFICATION_ID = 1001
        private const val WHISPER_VAD_FRAME_MS = 80

        @Volatile
        var isRunning: Boolean = false
            private set

        fun start(context: Context, resultCode: Int, resultData: Intent) {
            val intent = Intent(context, CaptionCaptureService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_RESULT_CODE, resultCode)
                .putExtra(EXTRA_RESULT_DATA, resultData)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, CaptionCaptureService::class.java)
                .setAction(ACTION_STOP)
            context.startService(intent)
        }
    }
}
