package com.ktv.stb.storage

import com.ktv.stb.common.util.TitleSanitizer
import com.ktv.stb.data.local.dao.QueueDao
import com.ktv.stb.data.local.dao.SongDao
import com.ktv.stb.data.local.entity.SongEntity
import kotlinx.coroutines.runBlocking
import java.io.File

class LocalFileStatusService(
    private val songDao: SongDao,
    private val queueDao: QueueDao,
    private val mediaFileInspector: MediaFileInspector,
) {
    fun getStatus(songId: String): FileStatusResult = runBlocking {
        val song = songDao.findBySongId(songId)
            ?: return@runBlocking FileStatusResult.notFound(songId)
        toStatus(song)
    }

    fun listAll(): List<FileStatusResult> = runBlocking {
        songDao.listAll().map { toStatus(it) }
    }

    fun deleteSong(songId: String): Boolean = runBlocking {
        val song = songDao.findBySongId(songId) ?: return@runBlocking false
        listOf(song.videoPath, song.originalAudioPath, song.accompanimentPath, song.vocalPath).forEach { path ->
            val file = path?.let { File(it) }
            if (file != null && file.exists()) {
                file.delete()
                file.parentFile?.takeIf { it.exists() && it.listFiles().isNullOrEmpty() }?.delete()
            }
        }
        queueDao.deleteBySongId(songId)
        songDao.deleteBySongId(songId)
        true
    }

    private fun toStatus(song: SongEntity): FileStatusResult {
        val videoPath = song.videoPath
        val audioPath = song.originalAudioPath
        val videoFile = videoPath?.let { File(it) }
        val audioFile = audioPath?.let { File(it) }
        val videoExists = videoFile?.exists() == true
        val audioExists = audioFile?.exists() == true
        val usesExternalAudio = !audioPath.isNullOrBlank()
        val size = (if (videoExists) videoFile?.length() ?: 0L else 0L) + (if (audioExists) audioFile?.length() ?: 0L else 0L)
        val videoInspect = if (videoExists) mediaFileInspector.inspect(videoPath) else MediaInspectResult.empty("video missing")
        val audioInspect = if (audioExists) mediaFileInspector.inspect(audioPath) else MediaInspectResult.empty("audio missing")
        val videoTrackUsable = videoInspect.videoTrackExists || (usesExternalAudio && videoExists)
        val hasPlayableAudio = videoInspect.audioTrackExists || audioInspect.audioTrackExists || (usesExternalAudio && audioExists)
        val valid = videoExists && size > 0L && song.downloadStatus == "success" && videoTrackUsable && hasPlayableAudio
        val errorCode = when {
            song.downloadStatus != "success" && !song.lastErrorCode.isNullOrBlank() -> song.lastErrorCode
            song.downloadStatus == "success" && !videoExists -> "FINAL_FILE_INVALID"
            song.downloadStatus == "success" && size <= 0L -> "FINAL_FILE_INVALID"
            song.downloadStatus == "success" && !videoTrackUsable -> "MISSING_VIDEO_TRACK"
            song.downloadStatus == "success" && !hasPlayableAudio -> "MISSING_AUDIO_TRACK"
            else -> song.lastErrorCode
        }
        val errorMessage = when {
            song.downloadStatus != "success" && !song.lastErrorMessage.isNullOrBlank() -> song.lastErrorMessage
            song.downloadStatus == "success" && !videoExists -> "db success but video file missing"
            song.downloadStatus == "success" && size <= 0L -> "db success but file invalid"
            song.downloadStatus == "success" && !videoTrackUsable -> videoInspect.errorMessage ?: "video track missing"
            song.downloadStatus == "success" && !hasPlayableAudio -> audioInspect.errorMessage ?: videoInspect.errorMessage ?: "audio track missing"
            else -> song.lastErrorMessage
        }

        return FileStatusResult(
            songId = song.songId,
            sourceId = song.sourceId,
            title = TitleSanitizer.sanitize(song.title),
            downloadStatus = song.downloadStatus,
            separateStatus = song.separateStatus,
            fileExists = videoExists,
            filePath = videoPath,
            fileSize = size,
            isValid = valid,
            errorCode = errorCode,
            errorMessage = errorMessage,
            accompanimentPath = song.accompanimentPath,
            vocalPath = song.vocalPath,
            videoTrackExists = videoTrackUsable,
            audioTrackExists = hasPlayableAudio,
            durationMs = videoInspect.durationMs.takeIf { it > 0 } ?: audioInspect.durationMs,
            width = videoInspect.width,
            height = videoInspect.height,
        )
    }
}

data class FileStatusResult(
    val songId: String,
    val sourceId: String?,
    val title: String?,
    val downloadStatus: String,
    val separateStatus: String,
    val fileExists: Boolean,
    val filePath: String?,
    val fileSize: Long,
    val isValid: Boolean,
    val errorCode: String?,
    val errorMessage: String?,
    val accompanimentPath: String?,
    val vocalPath: String?,
    val videoTrackExists: Boolean,
    val audioTrackExists: Boolean,
    val durationMs: Long,
    val width: Int?,
    val height: Int?,
) {
    companion object {
        fun notFound(songId: String): FileStatusResult = FileStatusResult(
            songId = songId,
            sourceId = null,
            title = null,
            downloadStatus = "not_found",
            separateStatus = "not_found",
            fileExists = false,
            filePath = null,
            fileSize = 0L,
            isValid = false,
            errorCode = "SONG_NOT_FOUND",
            errorMessage = "song not found",
            accompanimentPath = null,
            vocalPath = null,
            videoTrackExists = false,
            audioTrackExists = false,
            durationMs = 0L,
            width = null,
            height = null,
        )
    }
}
