package com.ktv.stb.server.route

import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.google.gson.annotations.SerializedName
import com.ktv.stb.common.error.AppErrorCode
import com.ktv.stb.common.result.ApiResult
import com.ktv.stb.qrcode.QrCodeManager
import com.ktv.stb.session.SessionManager
import fi.iki.elonen.NanoHTTPD

class SessionRoutes(
    private val sessionManager: SessionManager,
    private val qrCodeManager: QrCodeManager,
    private val gson: Gson,
) {
    fun getSessionStatus(): NanoHTTPD.Response {
        val session = sessionManager.getCurrentSession()
        return json(
            ApiResult(
                data = SessionStatusData(
                    sessionId = session.sessionId,
                    bindStatus = session.bindStatus.name.lowercase(),
                    deviceName = session.deviceName,
                    clientCount = session.clientCount,
                    clients = session.clients.map {
                        SessionClientData(
                            clientId = it.clientId,
                            clientName = it.clientName,
                            connectedAt = it.connectedAt,
                            lastSeenAt = it.lastSeenAt,
                        )
                    },
                    currentSong = null,
                    webUrl = sessionManager.resolveWebEntryUrl(),
                    qrPayload = qrCodeManager.buildQrPayload(),
                ),
            ),
        )
    }

    fun bindSession(httpSession: NanoHTTPD.IHTTPSession): NanoHTTPD.Response {
        return try {
            val request = gson.fromJson(parseBody(httpSession), BindSessionRequest::class.java)
            val session = sessionManager.bindSession(
                requestSessionId = request.sessionId,
                clientId = request.clientId,
                clientName = request.clientName,
            )
            json(
                ApiResult(
                    data = BindSessionData(
                        sessionId = session.sessionId,
                        bindStatus = session.bindStatus.name.lowercase(),
                        clientId = request.clientId ?: session.clients.lastOrNull()?.clientId.orEmpty(),
                        clientCount = session.clientCount,
                        clients = session.clients.map {
                            SessionClientData(
                                clientId = it.clientId,
                                clientName = it.clientName,
                                connectedAt = it.connectedAt,
                                lastSeenAt = it.lastSeenAt,
                            )
                        },
                    ),
                ),
            )
        } catch (ex: IllegalArgumentException) {
            json(ApiResult<Any>(code = AppErrorCode.SESSION_INVALID, message = ex.message ?: "bind failed"))
        } catch (ex: JsonSyntaxException) {
            json(ApiResult<Any>(code = AppErrorCode.INVALID_REQUEST, message = "invalid json"))
        } catch (ex: Exception) {
            json(ApiResult<Any>(code = AppErrorCode.INTERNAL_ERROR, message = ex.message ?: "internal error"))
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
            addHeader("Access-Control-Allow-Headers", "Content-Type")
            addHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
        }
    }
}

data class BindSessionRequest(
    @SerializedName("session_id") val sessionId: String,
    @SerializedName("client_id") val clientId: String? = null,
    @SerializedName("client_name") val clientName: String? = null,
    @SerializedName("sign") val sign: String = "",
)

data class SessionStatusData(
    @SerializedName("session_id") val sessionId: String,
    @SerializedName("bind_status") val bindStatus: String,
    @SerializedName("device_name") val deviceName: String,
    @SerializedName("client_count") val clientCount: Int,
    @SerializedName("clients") val clients: List<SessionClientData>,
    @SerializedName("current_song") val currentSong: CurrentSongData?,
    @SerializedName("web_url") val webUrl: String,
    @SerializedName("qr_payload") val qrPayload: String,
)

data class SessionClientData(
    @SerializedName("client_id") val clientId: String,
    @SerializedName("client_name") val clientName: String?,
    @SerializedName("connected_at") val connectedAt: Long,
    @SerializedName("last_seen_at") val lastSeenAt: Long,
)

data class CurrentSongData(
    @SerializedName("song_id") val songId: String,
    @SerializedName("title") val title: String,
)

data class BindSessionData(
    @SerializedName("session_id") val sessionId: String,
    @SerializedName("bind_status") val bindStatus: String,
    @SerializedName("client_id") val clientId: String,
    @SerializedName("client_count") val clientCount: Int,
    @SerializedName("clients") val clients: List<SessionClientData>,
)
