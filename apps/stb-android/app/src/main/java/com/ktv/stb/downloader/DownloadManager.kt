package com.ktv.stb.downloader

import android.util.Log
import com.ktv.stb.data.local.dao.SongDao
import com.ktv.stb.data.local.entity.SongEntity
import com.ktv.stb.server.ws.MobileWebSocketHub
import com.ktv.stb.storage.MediaFileInspector
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class DownloadManager(
    private val songDao: SongDao,
    private val songDownloader: SongDownloader,
    private val webSocketHub: MobileWebSocketHub,
    private val mediaFileInspector: MediaFileInspector,
    private val onQueueStateChanged: () -> Unit,
    private val onSongReadyToPlay: () -> Unit,
    private val onSongReadyToSeparate: (SongEntity) -> Unit,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val runningJobs = LinkedHashMap<String, Job>()

    fun enqueue(song: SongEntity) {
        if (runningJobs.containsKey(song.songId)) return

        val job = scope.launch {
            val now = System.currentTimeMillis()
            songDao.update(
                song.copy(
                    downloadStatus = "downloading",
                    lastErrorCode = null,
                    lastErrorMessage = null,
                    updatedAt = now,
                ),
            )
            broadcastStatus(song.songId, song.title, song.sourceId, "downloading", 0, null, null, null)
            onQueueStateChanged()

            val result = runCatching {
                songDownloader.download(song) { progress ->
                    broadcastStatus(song.songId, song.title, song.sourceId, "downloading", progress, null, null, null)
                }
            }.getOrElse { throwable ->
                Log.e("DownloadManager", "download failed ${song.songId}", throwable)
                DownloadResult(
                    success = false,
                    videoPath = null,
                    originalAudioPath = null,
                    fileSize = 0L,
                    errorCode = DownloadErrorCode.DOWNLOAD_HTTP_FAILED,
                    errorMessage = throwable.message,
                )
            }

            val updatedSong = buildUpdatedSong(song, result)
            songDao.update(updatedSong)

            broadcastStatus(
                song.songId,
                song.title,
                song.sourceId,
                updatedSong.downloadStatus,
                if (updatedSong.downloadStatus == "success") 100 else 0,
                updatedSong.videoPath,
                updatedSong.lastErrorCode,
                updatedSong.lastErrorMessage,
            )
            onQueueStateChanged()
            if (updatedSong.downloadStatus == "success") {
                onSongReadyToPlay()
                onSongReadyToSeparate(updatedSong)
            }
        }

        runningJobs[song.songId] = job
        job.invokeOnCompletion { runningJobs.remove(song.songId) }
    }

    private fun buildUpdatedSong(song: SongEntity, result: DownloadResult): SongEntity {
        val updatedAt = System.currentTimeMillis()
        val usesExternalAudio = !result.originalAudioPath.isNullOrBlank()
        val videoInspect = result.videoPath?.let { mediaFileInspector.inspect(it) }
        val audioInspect = result.originalAudioPath?.let { mediaFileInspector.inspect(it) }
        val videoValid = result.videoPath?.let { path ->
            val file = File(path)
            val basicOk = file.exists() && file.length() > 0L
            if (!basicOk) {
                false
            } else if (usesExternalAudio) {
                true
            } else {
                videoInspect?.videoTrackExists == true
            }
        } == true
        val audioValid = when {
            result.originalAudioPath.isNullOrBlank() -> videoInspect?.audioTrackExists == true
            else -> {
                val audioPath = result.originalAudioPath!!
                val file = File(audioPath)
                file.exists() && file.length() > 0L
            }
        }
        val success = result.success && videoValid && audioValid
        val errorCode = if (success) {
            null
        } else if (!result.success && !result.errorCode.isNullOrBlank()) {
            result.errorCode
        } else {
            when {
                !videoValid -> DownloadErrorCode.MISSING_VIDEO_TRACK
                !audioValid -> DownloadErrorCode.MISSING_AUDIO_TRACK
                else -> result.errorCode ?: DownloadErrorCode.FINAL_FILE_INVALID
            }
        }
        val errorMessage = if (success) {
            null
        } else if (!result.success && !result.errorMessage.isNullOrBlank()) {
            result.errorMessage
        } else {
            when {
                !videoValid -> videoInspect?.errorMessage ?: "video file missing or invalid"
                !audioValid -> audioInspect?.errorMessage ?: videoInspect?.errorMessage ?: "audio file missing or invalid"
                else -> result.errorMessage ?: "final file validation failed"
            }
        }

        return song.copy(
            videoPath = if (success) result.videoPath else null,
            originalAudioPath = if (success) result.originalAudioPath else null,
            fileSize = if (success) result.fileSize else 0L,
            downloadStatus = if (success) "success" else "failed",
            resourceLevel = if (success) "downloaded_only" else "not_exist",
            lastErrorCode = errorCode,
            lastErrorMessage = errorMessage,
            updatedAt = updatedAt,
        )
    }

    private fun broadcastStatus(
        songId: String,
        title: String,
        sourceId: String,
        status: String,
        progress: Int,
        filePath: String?,
        errorCode: String?,
        errorMessage: String?,
    ) {
        webSocketHub.broadcastDownloadUpdated(
            mapOf(
                "song_id" to songId,
                "title" to title,
                "download_status" to status,
                "progress" to progress,
                "source_id" to sourceId,
                "file_path" to filePath,
                "error_code" to errorCode,
                "error_message" to errorMessage,
            ),
        )
    }
}
