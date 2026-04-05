package com.ktv.stb.server.route

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.ktv.stb.common.result.ApiResult
import com.ktv.stb.domain.usecase.GetQueueUseCase
import com.ktv.stb.domain.usecase.MoveQueueItemNextUseCase
import com.ktv.stb.domain.usecase.RemoveQueueItemUseCase
import com.ktv.stb.server.ws.MobileWebSocketHub
import fi.iki.elonen.NanoHTTPD

class QueueRoutes(
    private val getQueueUseCase: GetQueueUseCase,
    private val removeQueueItemUseCase: RemoveQueueItemUseCase,
    private val moveQueueItemNextUseCase: MoveQueueItemNextUseCase,
    private val webSocketHub: MobileWebSocketHub,
    private val gson: Gson,
) {
    fun listQueue(): NanoHTTPD.Response {
        val snapshot = getQueueUseCase.execute()
        return json(
            ApiResult(
                data = QueueListResponse(
                    current = snapshot.current?.toDto(),
                    upcoming = snapshot.upcoming.map { it.toDto() },
                    items = snapshot.items.map { it.toDto() },
                ),
            ),
        )
    }

    fun remove(session: NanoHTTPD.IHTTPSession): NanoHTTPD.Response {
        val request = gson.fromJson(parseBody(session), QueueActionRequest::class.java)
        val removed = removeQueueItemUseCase.execute(request.queueId)
        if (removed) {
            webSocketHub.broadcastQueueUpdated(getQueueUseCase.execute().toBroadcastPayload())
        }
        return json(ApiResult(data = mapOf("success" to removed)))
    }

    fun moveNext(session: NanoHTTPD.IHTTPSession): NanoHTTPD.Response {
        val request = gson.fromJson(parseBody(session), QueueActionRequest::class.java)
        val moved = moveQueueItemNextUseCase.execute(request.queueId)
        if (moved) {
            webSocketHub.broadcastQueueUpdated(getQueueUseCase.execute().toBroadcastPayload())
        }
        return json(ApiResult(data = mapOf("success" to moved)))
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
        ).apply {
            addHeader("Access-Control-Allow-Origin", "*")
        }
    }
}

private fun com.ktv.stb.domain.model.QueueItem.toDto(): QueueItemResponse {
    return QueueItemResponse(
        queueId = queueId,
        songId = songId,
        title = title,
        sourceId = sourceId,
        downloadStatus = downloadStatus,
        queueStatus = queueStatus,
        skipReason = skipReason,
        errorMessage = errorMessage,
        playMode = playMode,
        position = position,
        orderedByClientId = orderedByClientId,
        orderedByClientName = orderedByClientName,
    )
}

data class QueueListResponse(
    @SerializedName("current") val current: QueueItemResponse?,
    @SerializedName("upcoming") val upcoming: List<QueueItemResponse>,
    @SerializedName("items") val items: List<QueueItemResponse>,
)

data class QueueItemResponse(
    @SerializedName("queue_id") val queueId: String,
    @SerializedName("song_id") val songId: String,
    @SerializedName("title") val title: String,
    @SerializedName("source_id") val sourceId: String,
    @SerializedName("download_status") val downloadStatus: String,
    @SerializedName("queue_status") val queueStatus: String,
    @SerializedName("skip_reason") val skipReason: String?,
    @SerializedName("error_message") val errorMessage: String?,
    @SerializedName("play_mode") val playMode: String,
    @SerializedName("position") val position: Int,
    @SerializedName("ordered_by_client_id") val orderedByClientId: String,
    @SerializedName("ordered_by_client_name") val orderedByClientName: String?,
)

data class QueueActionRequest(
    @SerializedName("queue_id") val queueId: String,
)

fun com.ktv.stb.queue.QueueSnapshot.toBroadcastPayload(): Map<String, Any?> {
    return mapOf(
        "current" to current?.toDto(),
        "upcoming" to upcoming.map { it.toDto() },
        "items" to items.map { it.toDto() },
        "queue_count" to items.count { it.queueStatus != "removed" },
    )
}
