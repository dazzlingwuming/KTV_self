package com.ktv.stb.server.route

import android.util.Log
import android.util.Base64
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.google.gson.annotations.SerializedName
import com.ktv.stb.common.result.ApiResult
import com.ktv.stb.domain.usecase.CreateOrderUseCase
import com.ktv.stb.domain.usecase.GetQueueUseCase
import com.ktv.stb.downloader.DownloadManager
import com.ktv.stb.server.ws.MobileWebSocketHub
import fi.iki.elonen.NanoHTTPD

class OrderRoutes(
    private val createOrderUseCase: CreateOrderUseCase,
    private val getQueueUseCase: GetQueueUseCase,
    private val downloadManager: DownloadManager,
    private val webSocketHub: MobileWebSocketHub,
    private val gson: Gson,
) {
    fun createOrder(session: NanoHTTPD.IHTTPSession): NanoHTTPD.Response {
        return try {
            val request = gson.fromJson(parseBody(session), CreateOrderRequest::class.java).normalized()
            val result = createOrderUseCase.execute(request)
            if (result.accepted) {
                result.songEntity?.let { downloadManager.enqueue(it) }
                val snapshot = getQueueUseCase.execute()
                webSocketHub.broadcastQueueUpdated(snapshot.toBroadcastPayload())
                json(
                    ApiResult(
                        data = CreateOrderResponse(
                            accepted = true,
                            message = result.message,
                            queueId = result.queueItem?.queueId,
                            songId = result.queueItem?.songId,
                            downloadStatus = result.songEntity?.downloadStatus ?: "pending",
                        ),
                    ),
                )
            } else {
                json(ApiResult<Any>(code = 409, message = result.message))
            }
        } catch (ex: JsonSyntaxException) {
            json(ApiResult<Any>(code = 400, message = "invalid json"))
        } catch (ex: Exception) {
            Log.e("OrderRoutes", "createOrder failed", ex)
            json(ApiResult<Any>(code = 500, message = ex.message ?: "create order failed"))
        }
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

data class CreateOrderRequest(
    @SerializedName("client_id") val clientId: String,
    @SerializedName("client_name") val clientName: String?,
    @SerializedName("source_type") val sourceType: String,
    @SerializedName("source_id") val sourceId: String,
    @SerializedName("title") val title: String,
    @SerializedName("title_b64") val titleBase64: String? = null,
    @SerializedName("artist") val artist: String?,
    @SerializedName("artist_b64") val artistBase64: String? = null,
    @SerializedName("duration") val duration: Long?,
    @SerializedName("cover_url") val coverUrl: String?,
)

data class CreateOrderResponse(
    @SerializedName("accepted") val accepted: Boolean,
    @SerializedName("message") val message: String,
    @SerializedName("queue_id") val queueId: String?,
    @SerializedName("song_id") val songId: String?,
    @SerializedName("download_status") val downloadStatus: String?,
)

private fun CreateOrderRequest.normalized(): CreateOrderRequest {
    val decodedTitle = decodeBase64Utf8(titleBase64) ?: title
    val decodedArtist = decodeBase64Utf8(artistBase64) ?: artist
    return copy(
        title = decodedTitle,
        artist = decodedArtist,
    )
}

private fun decodeBase64Utf8(value: String?): String? {
    if (value.isNullOrBlank()) return null
    return try {
        String(Base64.decode(value, Base64.DEFAULT), Charsets.UTF_8)
    } catch (_: Exception) {
        null
    }
}
