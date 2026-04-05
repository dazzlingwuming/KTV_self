package com.ktv.stb.queue

import com.ktv.stb.data.local.dao.QueueDao
import com.ktv.stb.data.local.dao.SongDao
import com.ktv.stb.data.local.entity.QueueEntity
import com.ktv.stb.data.local.entity.SongEntity
import com.ktv.stb.common.util.TitleSanitizer
import com.ktv.stb.domain.model.QueueItem
import com.ktv.stb.domain.model.Song
import com.ktv.stb.server.route.CreateOrderRequest
import kotlinx.coroutines.runBlocking
import java.io.File
import java.util.UUID

class QueueManager(
    private val songDao: SongDao,
    private val queueDao: QueueDao,
) {
    fun initialize() {
        runBlocking {
            val queue = queueDao.listAll()
            if (queue.any { it.queueStatus == "playing" }) {
                persistQueue(
                    queue.map {
                        if (it.queueStatus == "playing") {
                            it.copy(queueStatus = "waiting")
                        } else {
                            it
                        }
                    },
                )
            }
            songDao.listAll().forEach { song ->
                val sanitizedTitle = TitleSanitizer.sanitize(song.title)
                if (sanitizedTitle != song.title) {
                    songDao.update(song.copy(title = sanitizedTitle, updatedAt = System.currentTimeMillis()))
                }
            }
        }
    }

    fun createOrder(request: CreateOrderRequest): CreateOrderResult = runBlocking {
        val existingSong = songDao.findBySourceId(request.sourceId)
        if (existingSong != null) {
            return@runBlocking CreateOrderResult(
                accepted = false,
                message = "song already exists in local library",
                queueItem = null,
                songEntity = existingSong,
            )
        }
        if (queueDao.countBySourceId(request.sourceId) > 0) {
            return@runBlocking CreateOrderResult(
                accepted = false,
                message = "song already exists in queue",
                queueItem = null,
                songEntity = null,
            )
        }

        val queueId = UUID.randomUUID().toString()
        val position = queueDao.listAll().size + 1

        val song = buildSongEntity(request)

        val queueItem = QueueEntity(
            queueId = queueId,
            songId = song.songId,
            queueStatus = "waiting",
            skipReason = null,
            errorMessage = null,
            playMode = "original",
            position = position,
            orderedByClientId = request.clientId,
            orderedByClientName = request.clientName,
            createdAt = System.currentTimeMillis(),
        )

        songDao.insert(song)
        queueDao.insert(queueItem)

        CreateOrderResult(
            accepted = true,
            message = "ok",
            queueItem = queueItem.toDomain(song),
            songEntity = song,
        )
    }

    fun createDownloadOnly(request: CreateOrderRequest): CreateOrderResult = runBlocking {
        val existingSong = songDao.findBySourceId(request.sourceId)
        if (existingSong != null) {
            return@runBlocking CreateOrderResult(
                accepted = false,
                message = "song already exists in local library",
                queueItem = null,
                songEntity = existingSong,
            )
        }

        val song = buildSongEntity(request)
        songDao.insert(song)
        CreateOrderResult(
            accepted = true,
            message = "download queued",
            queueItem = null,
            songEntity = song,
        )
    }

    fun createLocalOrder(songId: String, clientId: String, clientName: String?): Boolean = runBlocking {
        val song = songDao.findBySongId(songId) ?: return@runBlocking false
        val fileValid = !song.videoPath.isNullOrBlank() && File(song.videoPath).exists() && File(song.videoPath).length() > 0L
        if (song.downloadStatus != "success" || !fileValid) return@runBlocking false
        if (queueDao.countBySourceId(song.sourceId) > 0) return@runBlocking false

        val queueId = UUID.randomUUID().toString()
        val position = queueDao.listAll().size + 1
        val queueItem = QueueEntity(
            queueId = queueId,
            songId = song.songId,
            queueStatus = "waiting",
            skipReason = null,
            errorMessage = null,
            playMode = "original",
            position = position,
            orderedByClientId = clientId,
            orderedByClientName = clientName,
            createdAt = System.currentTimeMillis(),
        )
        queueDao.insert(queueItem)
        true
    }

    fun getQueueSnapshot(): QueueSnapshot = runBlocking {
        val queueEntities = queueDao.listAll()
        val songsById = songDao.listAll().associateBy { it.songId }
        val allItems = queueEntities.mapNotNull { entity ->
            val song = songsById[entity.songId] ?: return@mapNotNull null
            entity.toDomain(song)
        }
        val activeItems = allItems
            .filter { it.queueStatus in listOf("waiting", "playing") }
            .sortedBy { it.position }

        QueueSnapshot(
            current = activeItems.firstOrNull { it.queueStatus == "playing" },
            upcoming = activeItems.filter { it.queueStatus == "waiting" },
            items = activeItems,
        )
    }

    fun removeQueueItem(queueId: String): Boolean = runBlocking {
        val existing = queueDao.listAll()
        val target = existing.firstOrNull {
            it.queueId == queueId && it.queueStatus != "playing" && it.queueStatus != "removed"
        } ?: return@runBlocking false
        val updated = existing.map {
            if (it.queueId == target.queueId) it.copy(queueStatus = "removed") else it
        }
        persistQueue(updated)
        true
    }

    fun moveNext(queueId: String): Boolean = runBlocking {
        val existing = queueDao.listAll().toMutableList()
        val targetIndex = existing.indexOfFirst { it.queueId == queueId && it.queueStatus == "waiting" }
        if (targetIndex < 0) return@runBlocking false

        val target = existing.removeAt(targetIndex)
        val currentPlayingIndex = existing.indexOfFirst { it.queueStatus == "playing" }
        val insertIndex = if (currentPlayingIndex >= 0) currentPlayingIndex + 1 else 0
        existing.add(insertIndex.coerceAtMost(existing.size), target)
        persistQueue(existing)
        true
    }

    fun seedPlaying(queueId: String) = runBlocking {
        val updated = queueDao.listAll().map {
            when {
                it.queueId == queueId -> it.copy(queueStatus = "playing", skipReason = null, errorMessage = null)
                it.queueStatus == "playing" -> it.copy(queueStatus = "finished")
                else -> it
            }
        }
        persistQueue(updated)
    }

    fun currentSongs(): List<Song> = runBlocking {
        songDao.listAll().map { it.toDomain() }
    }

    fun findNextPlayableItem(): PlayableQueueItem? = runBlocking {
        val waitingItems = queueDao.listAll()
            .filter { it.queueStatus == "waiting" }
            .sortedBy { it.position }

        for (queueEntity in waitingItems) {
            val song = songDao.findBySongId(queueEntity.songId) ?: continue
            val path = song.videoPath
            val audioPath = song.originalAudioPath
            val fileValid = !path.isNullOrBlank() && File(path).exists() && File(path).length() > 0L
            if (song.downloadStatus == "success" && fileValid) {
                return@runBlocking PlayableQueueItem(
                    queueId = queueEntity.queueId,
                    songId = song.songId,
                    title = TitleSanitizer.sanitize(song.title),
                    filePath = path,
                    audioPath = audioPath,
                    accompanimentPath = song.accompanimentPath,
                    vocalPath = song.vocalPath,
                )
            }
            val updated = queueDao.listAll().map {
                if (it.songId == song.songId && it.queueStatus == "waiting") {
                    it.copy(
                        queueStatus = "skipped",
                        skipReason = if (song.downloadStatus != "success") "DOWNLOAD_NOT_READY" else "INVALID_LOCAL_FILE",
                        errorMessage = song.lastErrorMessage,
                    )
                } else {
                    it
                }
            }
            persistQueue(updated)
        }
        null
    }

    fun markPlaying(queueId: String) = runBlocking {
        val updated = queueDao.listAll().map {
            when {
                it.queueId == queueId -> it.copy(queueStatus = "playing", skipReason = null, errorMessage = null)
                it.queueStatus == "playing" -> it.copy(queueStatus = "finished")
                else -> it
            }
        }
        persistQueue(updated)
    }

    fun markFinished(songId: String) = runBlocking {
        val updated = queueDao.listAll().map {
            if (it.songId == songId && it.queueStatus == "playing") {
                it.copy(queueStatus = "finished", errorMessage = null)
            } else {
                it
            }
        }
        persistQueue(updated)
    }

    fun markSkipped(songId: String, skipReason: String, errorMessage: String?) = runBlocking {
        val updated = queueDao.listAll().map {
            if (it.songId == songId && it.queueStatus in listOf("waiting", "playing")) {
                it.copy(queueStatus = "skipped", skipReason = skipReason, errorMessage = errorMessage)
            } else {
                it
            }
        }
        persistQueue(updated)
    }

    fun markSongUnavailable(songId: String, skipReason: String, errorMessage: String?) = runBlocking {
        markSkipped(songId, skipReason, errorMessage)
    }

    fun findQueueItemBySongId(songId: String): QueueItem? = runBlocking {
        val queueEntities = queueDao.listAll()
        val songsById = songDao.listAll().associateBy { it.songId }
        val entity = queueEntities.firstOrNull { it.songId == songId } ?: return@runBlocking null
        val song = songsById[entity.songId] ?: return@runBlocking null
        entity.toDomain(song)
    }

    fun listAllQueueItems(): List<QueueItem> = runBlocking {
        val queueEntities = queueDao.listAll()
        val songsById = songDao.listAll().associateBy { it.songId }
        queueEntities.mapNotNull { entity ->
            val song = songsById[entity.songId] ?: return@mapNotNull null
            entity.toDomain(song)
        }.sortedBy { it.position }
    }

    fun removeSongFromQueue(songId: String) = runBlocking {
        val updated = queueDao.listAll().map {
            if (it.songId == songId && it.queueStatus in listOf("waiting", "skipped", "playing")) {
                it.copy(queueStatus = "removed", errorMessage = "local cache deleted")
            } else {
                it
            }
        }
        persistQueue(updated)
    }

    fun findPlayableItemBySongId(songId: String): PlayableQueueItem? = runBlocking {
        val song = songDao.findBySongId(songId) ?: return@runBlocking null
        val path = song.videoPath ?: return@runBlocking null
        val file = File(path)
        if (!file.exists() || file.length() <= 0L || song.downloadStatus != "success") {
            return@runBlocking null
        }
        PlayableQueueItem(
            queueId = queueDao.listAll().firstOrNull { it.songId == songId && it.queueStatus in listOf("waiting", "playing") }?.queueId.orEmpty(),
            songId = song.songId,
            title = TitleSanitizer.sanitize(song.title),
            filePath = path,
            audioPath = song.originalAudioPath,
            accompanimentPath = song.accompanimentPath,
            vocalPath = song.vocalPath,
        )
    }

    private suspend fun persistQueue(items: List<QueueEntity>) {
        queueDao.clearAll()
        var nextPosition = 1
        items.map { entity ->
            if (entity.queueStatus == "removed") {
                entity
            } else {
                entity.copy(position = nextPosition++)
            }
        }.forEach { queueDao.insert(it) }
    }

    private fun buildSongEntity(request: CreateOrderRequest): SongEntity {
        val now = System.currentTimeMillis()
        return SongEntity(
            songId = UUID.randomUUID().toString(),
            sourceType = request.sourceType,
            sourceId = request.sourceId,
            title = TitleSanitizer.sanitize(request.title),
            artist = request.artist ?: fallbackArtist(request.title),
            duration = request.duration ?: 0L,
            coverUrl = request.coverUrl,
            coverLocalPath = null,
            videoPath = null,
            originalAudioPath = null,
            accompanimentPath = null,
            vocalPath = null,
            downloadStatus = "pending",
            separateStatus = "pending",
            resourceLevel = "not_exist",
            fileSize = 0L,
            lastErrorCode = null,
            lastErrorMessage = null,
            createdAt = now,
            updatedAt = now,
        )
    }

    private fun fallbackArtist(title: String): String {
        return title.substringBefore("-").trim().ifBlank { "Unknown" }
    }
}

private fun QueueEntity.toDomain(song: SongEntity): QueueItem {
    return QueueItem(
        queueId = queueId,
        songId = songId,
        title = TitleSanitizer.sanitize(song.title),
        sourceId = song.sourceId,
        downloadStatus = song.downloadStatus,
        queueStatus = queueStatus,
        skipReason = skipReason,
        errorMessage = errorMessage,
        playMode = playMode,
        position = position,
        orderedByClientId = orderedByClientId.orEmpty(),
        orderedByClientName = orderedByClientName,
        createdAt = createdAt,
    )
}

private fun SongEntity.toDomain(): Song {
    return Song(
        songId = songId,
        sourceType = sourceType,
        sourceId = sourceId,
        title = title,
        artist = artist,
        duration = duration,
        coverUrl = coverUrl,
        downloadStatus = downloadStatus,
        separateStatus = separateStatus,
        resourceLevel = resourceLevel,
        lastErrorCode = lastErrorCode,
        lastErrorMessage = lastErrorMessage,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
}

data class QueueSnapshot(
    val current: QueueItem?,
    val upcoming: List<QueueItem>,
    val items: List<QueueItem>,
)

data class CreateOrderResult(
    val accepted: Boolean,
    val message: String,
    val queueItem: QueueItem?,
    val songEntity: SongEntity?,
)

data class PlayableQueueItem(
    val queueId: String,
    val songId: String,
    val title: String,
    val filePath: String,
    val audioPath: String?,
    val accompanimentPath: String?,
    val vocalPath: String?,
)
