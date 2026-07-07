package com.jeremysu0818.caption.whisper

import android.content.Context
import com.jeremysu0818.caption.data.WhisperModelOption
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class ModelDownloadState(
    val model: WhisperModelOption = WhisperModelOption.default,
    val isDownloaded: Boolean = false,
    val isDownloading: Boolean = false,
    val progress: Float = 0f,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = -1L,
    val errorMessage: String? = null,
)

class WhisperModelRepository(context: Context) {
    private val modelDir = File(context.filesDir, "whisper_models")
    private val downloadMutex = Mutex()
    private val _downloadState = MutableStateFlow(
        ModelDownloadState(
            model = WhisperModelOption.default,
            isDownloaded = modelFile(WhisperModelOption.default).exists(),
        )
    )

    val downloadState: StateFlow<ModelDownloadState> = _downloadState.asStateFlow()

    fun modelFile(option: WhisperModelOption): File = File(modelDir, option.fileName)

    fun refresh(option: WhisperModelOption) {
        _downloadState.value = ModelDownloadState(
            model = option,
            isDownloaded = modelFile(option).exists(),
        )
    }

    suspend fun ensureModel(option: WhisperModelOption): File = downloadMutex.withLock {
        withContext(Dispatchers.IO) {
            modelDir.mkdirs()
            val destination = modelFile(option)
            if (destination.exists()) {
                _downloadState.value = ModelDownloadState(
                    model = option,
                    isDownloaded = true,
                    progress = 1f,
                    downloadedBytes = destination.length(),
                    totalBytes = destination.length(),
                )
                return@withContext destination
            }

            val tempFile = File(modelDir, "${option.fileName}.download")
            if (tempFile.exists()) tempFile.delete()

            _downloadState.value = ModelDownloadState(
                model = option,
                isDownloaded = false,
                isDownloading = true,
            )

            try {
                downloadToFile(option, tempFile)
                val sha1 = sha1(tempFile)
                if (!sha1.equals(option.sha1, ignoreCase = true)) {
                    tempFile.delete()
                    throw IllegalStateException(
                        "模型校驗失敗：${option.displayName} 的 SHA-1 不符合官方檔案。"
                    )
                }
                if (!tempFile.renameTo(destination)) {
                    tempFile.copyTo(destination, overwrite = true)
                    tempFile.delete()
                }
                _downloadState.value = ModelDownloadState(
                    model = option,
                    isDownloaded = true,
                    isDownloading = false,
                    progress = 1f,
                    downloadedBytes = destination.length(),
                    totalBytes = destination.length(),
                )
                destination
            } catch (error: Throwable) {
                tempFile.delete()
                _downloadState.value = ModelDownloadState(
                    model = option,
                    isDownloaded = false,
                    isDownloading = false,
                    errorMessage = error.message ?: "模型下載失敗",
                )
                throw error
            }
        }
    }

    private suspend fun downloadToFile(option: WhisperModelOption, outputFile: File) {
        val connection = (URL(option.downloadUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "Caption Android")
        }

        try {
            val code = connection.responseCode
            if (code !in 200..299) {
                throw IllegalStateException("模型下載 HTTP $code")
            }

            val totalBytes = connection.contentLengthLong
            var downloadedBytes = 0L
            val digest = MessageDigest.getInstance("SHA-1")
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)

            connection.inputStream.use { input ->
                FileOutputStream(outputFile).use { output ->
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val read = input.read(buffer)
                        if (read == -1) break
                        digest.update(buffer, 0, read)
                        output.write(buffer, 0, read)
                        downloadedBytes += read

                        val progress = if (totalBytes > 0) {
                            downloadedBytes.toFloat() / totalBytes.toFloat()
                        } else {
                            0f
                        }
                        _downloadState.value = ModelDownloadState(
                            model = option,
                            isDownloaded = false,
                            isDownloading = true,
                            progress = progress.coerceIn(0f, 1f),
                            downloadedBytes = downloadedBytes,
                            totalBytes = totalBytes,
                        )
                    }
                }
            }

            val downloadedSha1 = digest.digest().toHexString()
            if (!downloadedSha1.equals(option.sha1, ignoreCase = true)) {
                throw IllegalStateException(
                    "模型校驗失敗：${option.displayName} 的 SHA-1 不符合官方檔案。"
                )
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun sha1(file: File): String {
        val digest = MessageDigest.getInstance("SHA-1")
        file.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read == -1) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().toHexString()
    }

    private fun ByteArray.toHexString(): String = joinToString(separator = "") {
        "%02x".format(it.toInt() and 0xff)
    }
}
