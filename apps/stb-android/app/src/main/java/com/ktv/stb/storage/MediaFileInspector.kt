package com.ktv.stb.storage

import android.media.MediaMetadataRetriever

class MediaFileInspector {
    fun inspect(path: String?): MediaInspectResult {
        if (path.isNullOrBlank()) {
            return MediaInspectResult.empty("file path missing")
        }

        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(path)
            val hasVideo = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO) == "yes"
            val hasAudio = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO) == "yes"
            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull()
            val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull()
            MediaInspectResult(
                videoTrackExists = hasVideo,
                audioTrackExists = hasAudio,
                durationMs = durationMs,
                width = width,
                height = height,
                errorMessage = when {
                    !hasVideo -> "video track missing"
                    !hasAudio -> "audio track missing"
                    else -> null
                },
            )
        } catch (ex: Exception) {
            MediaInspectResult.empty(ex.message ?: "media inspect failed")
        } finally {
            runCatching { retriever.release() }
        }
    }
}

data class MediaInspectResult(
    val videoTrackExists: Boolean,
    val audioTrackExists: Boolean,
    val durationMs: Long,
    val width: Int?,
    val height: Int?,
    val errorMessage: String?,
) {
    companion object {
        fun empty(errorMessage: String?) = MediaInspectResult(
            videoTrackExists = false,
            audioTrackExists = false,
            durationMs = 0L,
            width = null,
            height = null,
            errorMessage = errorMessage,
        )
    }
}
