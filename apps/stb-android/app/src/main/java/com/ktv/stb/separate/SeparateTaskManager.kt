package com.ktv.stb.separate

import android.util.Log
import com.ktv.stb.data.local.dao.DeviceConfigDao
import com.ktv.stb.data.local.dao.SongDao
import com.ktv.stb.data.local.entity.SongEntity
import com.ktv.stb.data.remote.host.HostSeparateApi
import com.ktv.stb.server.ws.MobileWebSocketHub
import com.ktv.stb.session.SessionManager
import com.ktv.stb.storage.SongFileManager
import com.ktv.stb.storage.StoragePaths
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class SeparateTaskManager(
    private val songDao: SongDao,
    private val deviceConfigDao: DeviceConfigDao,
    private val hostSeparateApi: HostSeparateApi,
    private val storagePaths: StoragePaths,
    private val songFileManager: SongFileManager,
    private val webSocketHub: MobileWebSocketHub,
    private val sessionManager: SessionManager,
    private val onSongSeparated: (String) -> Unit,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val runningJobs = LinkedHashMap<String, Job>()

    fun enqueue(songId: String) {
        if (runningJobs.containsKey(songId)) return

        val job = scope.launch {
            runCatching { separateInternal(songId) }
                .onFailure { throwable ->
                    Log.e("SeparateTaskManager", "separate failed $songId", throwable)
                    val song = songDao.findBySongId(songId) ?: return@onFailure
                    val now = System.currentTimeMillis()
                    songDao.updateSeparateState(
                        songId = songId,
                        status = "failed",
                        accompanimentPath = null,
                        vocalPath = null,
                        errorCode = "SEPARATE_FAILED",
                        errorMessage = throwable.message ?: "separate failed",
                        updatedAt = now,
                    )
                    broadcast(song.copy(separateStatus = "failed", lastErrorCode = "SEPARATE_FAILED", lastErrorMessage = throwable.message))
                }
        }

        runningJobs[songId] = job
        job.invokeOnCompletion { runningJobs.remove(songId) }
    }

    private suspend fun separateInternal(songId: String) {
        val song = songDao.findBySongId(songId) ?: return
        val deviceId = sessionManager.getCurrentSession().deviceId
        val hostBaseUrl = (
            deviceConfigDao.getByDeviceId(deviceId)?.hostAddress?.trim().orEmpty()
                .ifBlank { deviceConfigDao.getAnyConfiguredHost()?.hostAddress?.trim().orEmpty() }
            )
        if (hostBaseUrl.isBlank()) {
            throw IllegalStateException("host separator address is empty")
        }
        val audioPath = song.originalAudioPath?.takeIf { File(it).exists() }
            ?: throw IllegalStateException("original audio missing")

        val now = System.currentTimeMillis()
        songDao.updateSeparateState(songId, "processing", null, null, null, null, now)
        broadcast(song.copy(separateStatus = "processing", lastErrorCode = null, lastErrorMessage = null))

        val created = hostSeparateApi.createTask(
            hostBaseUrl = hostBaseUrl,
            songId = song.songId,
            sourceType = song.sourceType,
            sourceId = song.sourceId,
            audioFile = File(audioPath),
        )

        var progress = created.progress
        while (true) {
            val status = hostSeparateApi.getStatus(hostBaseUrl, created.taskId)
            progress = status.progress
            broadcastProgress(song, "processing", progress, null, null)
            when (status.status.lowercase()) {
                "success" -> break
                "failed" -> throw IllegalStateException(status.errorMessage ?: "host separate failed")
            }
            delay(1500)
        }

        val result = hostSeparateApi.getResult(hostBaseUrl, created.taskId)
        if (result.status.lowercase() != "success") {
            throw IllegalStateException(result.errorMessage ?: "host result not ready")
        }

        val tempAcc = storagePaths.tempAccompanimentFile(song.songId)
        val tempVocal = storagePaths.tempVocalFile(song.songId)
        val finalAcc = storagePaths.finalAccompanimentFile(song.songId)
        val finalVocal = storagePaths.finalVocalFile(song.songId)
        songFileManager.cleanupTemps(tempAcc, tempVocal)

        runCatching {
            hostSeparateApi.downloadFile(hostBaseUrl, result.accompanimentUrl ?: error("accompaniment url missing"), tempAcc)
            hostSeparateApi.downloadFile(hostBaseUrl, result.vocalUrl ?: error("vocal url missing"), tempVocal)
            if (tempAcc.length() <= 0L || tempVocal.length() <= 0L) {
                throw IllegalStateException("separate result file empty")
            }
            val accMoved = songFileManager.moveTempToFinal(tempAcc, finalAcc)
            val vocalMoved = songFileManager.moveTempToFinal(tempVocal, finalVocal)
            if (!accMoved || !vocalMoved) {
                throw IllegalStateException("move separate result failed")
            }
        }.onFailure {
            songFileManager.cleanupTemps(tempAcc, tempVocal)
            songFileManager.cleanupFinals(finalAcc, finalVocal)
            throw it
        }

        val updatedAt = System.currentTimeMillis()
        songDao.updateSeparateState(
            songId = song.songId,
            status = "success",
            accompanimentPath = finalAcc.absolutePath,
            vocalPath = finalVocal.absolutePath,
            errorCode = null,
            errorMessage = null,
            updatedAt = updatedAt,
        )
        val updatedSong = songDao.findBySongId(song.songId) ?: song.copy(
            accompanimentPath = finalAcc.absolutePath,
            vocalPath = finalVocal.absolutePath,
            separateStatus = "success",
            lastErrorCode = null,
            lastErrorMessage = null,
            updatedAt = updatedAt,
        )
        broadcast(updatedSong)
        onSongSeparated(song.songId)
    }

    private fun broadcast(song: SongEntity) {
        webSocketHub.broadcastSeparateUpdated(
            mapOf(
                "song_id" to song.songId,
                "source_id" to song.sourceId,
                "title" to song.title,
                "separate_status" to song.separateStatus,
                "accompaniment_path" to song.accompanimentPath,
                "vocal_path" to song.vocalPath,
                "error_code" to song.lastErrorCode,
                "error_message" to song.lastErrorMessage,
            ),
        )
    }

    private fun broadcastProgress(
        song: SongEntity,
        status: String,
        progress: Int,
        errorCode: String?,
        errorMessage: String?,
    ) {
        webSocketHub.broadcastSeparateUpdated(
            mapOf(
                "song_id" to song.songId,
                "source_id" to song.sourceId,
                "title" to song.title,
                "separate_status" to status,
                "progress" to progress,
                "error_code" to errorCode,
                "error_message" to errorMessage,
            ),
        )
    }
}
