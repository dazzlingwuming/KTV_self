package com.ktv.stb.server.route

import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.google.gson.annotations.SerializedName
import android.util.Base64
import com.ktv.stb.common.result.ApiResult
import com.ktv.stb.data.remote.bilibili.BilibiliSearchDataSource
import com.ktv.stb.domain.usecase.GetQueueUseCase
import com.ktv.stb.downloader.DownloadManager
import com.ktv.stb.queue.QueueManager
import com.ktv.stb.server.ws.MobileWebSocketHub
import fi.iki.elonen.NanoHTTPD
class SearchRoutes(
    private val searchDataSource: BilibiliSearchDataSource,
    private val queueManager: QueueManager,
    private val getQueueUseCase: GetQueueUseCase,
    private val downloadManager: DownloadManager,
    private val webSocketHub: MobileWebSocketHub,
    private val gson: Gson,
) {
    fun searchBilibili(session: NanoHTTPD.IHTTPSession): NanoHTTPD.Response {
        val keyword = session.parameters["keyword"]?.firstOrNull().orEmpty()
        return try {
            val list = searchDataSource.search(keyword)
            NanoHTTPD.newFixedLengthResponse(
                NanoHTTPD.Response.Status.OK,
                "application/json; charset=utf-8",
                gson.toJson(
                    ApiResult(
                        data = SearchBilibiliResponse(
                            list = list.map {
                                SearchSongItemResponse(
                                    sourceType = it.sourceType,
                                    sourceId = it.sourceId,
                                    title = it.title,
                                    artist = it.artist ?: "Unknown",
                                    duration = it.duration,
                                    coverUrl = it.coverUrl,
                                )
                            },
                        ),
                    ),
                ),
            ).apply {
                addHeader("Access-Control-Allow-Origin", "*")
            }
        } catch (ex: Exception) {
            NanoHTTPD.newFixedLengthResponse(
                NanoHTTPD.Response.Status.OK,
                "application/json; charset=utf-8",
                gson.toJson(
                    ApiResult<Any>(
                        code = 502,
                        message = ex.message ?: "bilibili search failed",
                    ),
                ),
            ).apply {
                addHeader("Access-Control-Allow-Origin", "*")
            }
        }
    }

    fun download(session: NanoHTTPD.IHTTPSession): NanoHTTPD.Response {
        return try {
            val request = gson.fromJson(parseBody(session), SearchDownloadRequest::class.java).normalized()
            val result = queueManager.createDownloadOnly(
                CreateOrderRequest(
                    clientId = request.clientId,
                    clientName = request.clientName,
                    sourceType = request.sourceType,
                    sourceId = request.sourceId,
                    title = request.title,
                    titleBase64 = null,
                    artist = request.artist,
                    artistBase64 = null,
                    duration = request.duration,
                    coverUrl = request.coverUrl,
                ),
            )
            if (result.accepted) {
                result.songEntity?.let { downloadManager.enqueue(it) }
                webSocketHub.broadcastQueueUpdated(getQueueUseCase.execute().toBroadcastPayload())
                json(
                    ApiResult(
                        data = SearchDownloadResponse(
                            accepted = true,
                            message = result.message,
                            songId = result.songEntity?.songId,
                            downloadStatus = result.songEntity?.downloadStatus ?: "pending",
                        ),
                    ),
                )
            } else {
                json(ApiResult<SearchDownloadResponse>(code = 409, message = result.message))
            }
        } catch (ex: JsonSyntaxException) {
            json(ApiResult<SearchDownloadResponse>(code = 400, message = "invalid json"))
        } catch (ex: Exception) {
            json(ApiResult<SearchDownloadResponse>(code = 500, message = ex.message ?: "download create failed"))
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

data class SearchBilibiliResponse(
    @SerializedName("list") val list: List<SearchSongItemResponse>,
)

data class SearchSongItemResponse(
    @SerializedName("source_type") val sourceType: String,
    @SerializedName("source_id") val sourceId: String,
    @SerializedName("title") val title: String,
    @SerializedName("artist") val artist: String?,
    @SerializedName("duration") val duration: Long,
    @SerializedName("cover_url") val coverUrl: String?,
)

data class SearchDownloadRequest(
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

data class SearchDownloadResponse(
    @SerializedName("accepted") val accepted: Boolean,
    @SerializedName("message") val message: String,
    @SerializedName("song_id") val songId: String?,
    @SerializedName("download_status") val downloadStatus: String?,
)

private fun SearchDownloadRequest.normalized(): SearchDownloadRequest {
    val decodedTitle = decodeBase64Utf8(titleBase64) ?: title
    val decodedArtist = decodeBase64Utf8(artistBase64) ?: artist
    return copy(title = decodedTitle, artist = decodedArtist)
}

private fun decodeBase64Utf8(value: String?): String? {
    if (value.isNullOrBlank()) return null
    return try {
        String(Base64.decode(value, Base64.DEFAULT), Charsets.UTF_8)
    } catch (_: Exception) {
        null
    }
}
