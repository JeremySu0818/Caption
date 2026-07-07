package com.jeremysu0818.caption.data

enum class WhisperModelOption(
    val id: String,
    val displayName: String,
    val fileName: String,
    val sizeLabel: String,
    val sha1: String,
    val downloadUrl: String,
) {
    TINY(
        id = "tiny",
        displayName = "tiny",
        fileName = "ggml-tiny.bin",
        sizeLabel = "75 MB",
        sha1 = "bd577a113a864445d4c299885e0cb97d4ba92b5f",
        downloadUrl = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-tiny.bin",
    ),
    BASE(
        id = "base",
        displayName = "base",
        fileName = "ggml-base.bin",
        sizeLabel = "142 MB",
        sha1 = "465707469ff3a37a2b9b8d8f89f2f99de7299dac",
        downloadUrl = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-base.bin",
    ),
    SMALL(
        id = "small",
        displayName = "small",
        fileName = "ggml-small.bin",
        sizeLabel = "466 MB",
        sha1 = "55356645c2b361a969dfd0ef2c5a50d530afd8d5",
        downloadUrl = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-small.bin",
    ),
    MEDIUM(
        id = "medium",
        displayName = "medium",
        fileName = "ggml-medium.bin",
        sizeLabel = "1.5 GB",
        sha1 = "fd9727b6e1217c2f614f9b698455c4ffd82463b4",
        downloadUrl = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-medium.bin",
    );

    companion object {
        val default: WhisperModelOption = SMALL

        fun fromId(id: String?): WhisperModelOption =
            entries.firstOrNull { it.id == id } ?: default
    }
}
