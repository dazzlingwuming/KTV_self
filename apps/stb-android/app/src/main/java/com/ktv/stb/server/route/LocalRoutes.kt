package com.ktv.stb.server.route

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.ktv.stb.common.result.ApiResult
import com.ktv.stb.queue.QueueManager
import com.ktv.stb.server.ws.MobileWebSocketHub
import com.ktv.stb.storage.LocalFileStatusService
import fi.iki.elonen.NanoHTTPD

class LocalRoutes(
    private val localFileStatusService: LocalFileStatusService,
    private val queueManager: QueueManager,
    private val webSocketHub: MobileWebSocketHub,
    private val broadcastQueueSnapshot: () -> Unit,
    private val onSongReadyToPlay: () -> Unit,
    private val onSongSeparate: (String) -> Unit,
    private val onSongDeleted: (String) -> Unit,
    private val gson: Gson,
) {
    fun list(): NanoHTTPD.Response {
        val list = localFileStatusService.listAll()
        return json(ApiResult(data = LocalListResponse(list = list.map { it.toDto() })))
    }

    fun fileStatus(session: NanoHTTPD.IHTTPSession): NanoHTTPD.Response {
        val songId = session.parameters["song_id"]?.firstOrNull().orEmpty()
        val status = localFileStatusService.getStatus(songId)
        return json(ApiResult(data = status.toDto()))
    }

    fun localOrder(session: NanoHTTPD.IHTTPSession): NanoHTTPD.Response {
        val request = gson.fromJson(parseBody(session), LocalActionRequest::class.java)
        val accepted = queueManager.createLocalOrder(request.songId, request.clientId, request.clientName)
        if (accepted) {
            broadcastQueueSnapshot()
            onSongReadyToPlay()
        }
        return json(ApiResult(data = mapOf("success" to accepted)))
    }

    fun localDelete(session: NanoHTTPD.IHTTPSession): NanoHTTPD.Response {
        val request = gson.fromJson(parseBody(session), LocalDeleteRequest::class.java)
        val deleted = localFileStatusService.deleteSong(request.songId)
        if (deleted) {
            val status = localFileStatusService.getStatus(request.songId)
            webSocketHub.broadcastDownloadUpdated(
                mapOf(
                    "song_id" to status.songId,
                    "source_id" to status.sourceId,
                    "title" to status.title,
                    "download_status" to status.downloadStatus,
                    "progress" to 0,
                    "file_path" to status.filePath,
                    "error_code" to status.errorCode,
                    "error_message" to status.errorMessage,
                ),
            )
            onSongDeleted(request.songId)
            broadcastQueueSnapshot()
        }
        return json(ApiResult(data = mapOf("success" to deleted)))
    }

    fun localSeparate(session: NanoHTTPD.IHTTPSession): NanoHTTPD.Response {
        val request = gson.fromJson(parseBody(session), LocalSeparateRequest::class.java)
        val status = localFileStatusService.getStatus(request.songId)
        val accepted = status.songId.isNotBlank() && status.downloadStatus == "success"
        if (accepted) {
            onSongSeparate(request.songId)
        }
        return json(ApiResult(data = mapOf("success" to accepted)))
    }

    private fun parseBody(session: NanoHTTPD.IHTTPSession): String {
        val files = HashMap<String, String>()
        session.parseBody(files)
        return files["postData"] ?: "{}"
    }

    private fun json(any: Any): NanoHTTPD.Response {
        return NanoHTTPD.newFixedLengthResponse(
            NanoHTTPD.Response.Status.OK,
            "application/json; charset=utf-8",
            gson.toJson(any),
        ).apply { addHeader("Access-Control-Allow-Origin", "*") }
    }
}

private fun com.ktv.stb.storage.FileStatusResult.toDto(): LocalFileStatusResponse {
    return LocalFileStatusResponse(
        songId = songId,
        sourceId = sourceId,
        title = title,
        downloadStatus = downloadStatus,
        separateStatus = separateStatus,
        fileExists = fileExists,
        filePath = filePath,
        fileSize = fileSize,
        isValid = isValid,
        errorCode = errorCode,
        errorMessage = errorMessage,
        accompanimentPath = accompanimentPath,
        vocalPath = vocalPath,
        videoTrackExists = videoTrackExists,
        audioTrackExists = audioTrackExists,
        durationMs = durationMs,
        width = width,
        height = height,
    )
}

data class LocalFileStatusResponse(
    @SerializedName("song_id") val songId: String,
    @SerializedName("source_id") val sourceId: String?,
    @SerializedName("title") val title: String?,
    @SerializedName("download_status") val downloadStatus: String,
    @SerializedName("separate_status") val separateStatus: String,
    @SerializedName("file_exists") val fileExists: Boolean,
    @SerializedName("file_path") val filePath: String?,
    @SerializedName("file_size") val fileSize: Long,
    @SerializedName("is_valid") val isValid: Boolean,
    @SerializedName("error_code") val errorCode: String?,
    @SerializedName("error_message") val errorMessage: String?,
    @SerializedName("accompaniment_path") val accompanimentPath: String?,
    @SerializedName("vocal_path") val vocalPath: String?,
    @SerializedName("video_track_exists") val videoTrackExists: Boolean,
    @SerializedName("audio_track_exists") val audioTrackExists: Boolean,
    @SerializedName("duration_ms") val durationMs: Long,
    @SerializedName("width") val width: Int?,
    @SerializedName("height") val height: Int?,
)

data class LocalListResponse(
    @SerializedName("list") val list: List<LocalFileStatusResponse>,
)

data class LocalActionRequest(
    @SerializedName("song_id") val songId: String,
    @SerializedName("client_id") val clientId: String,
    @SerializedName("client_name") val clientName: String?,
)

data class LocalDeleteRequest(
    @SerializedName("song_id") val songId: String,
)

data class LocalSeparateRequest(
    @SerializedName("song_id") val songId: String,
)
