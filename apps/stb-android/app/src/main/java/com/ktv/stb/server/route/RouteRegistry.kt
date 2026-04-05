package com.ktv.stb.server.route

import android.util.Log
import com.ktv.stb.common.constants.ApiConstants
import com.ktv.stb.server.auth.SessionAuthInterceptor
import com.ktv.stb.server.staticweb.StaticWebAssetHandler
import com.ktv.stb.server.ws.MobileWebSocketHub
import com.ktv.stb.session.SessionManager
import com.ktv.stb.domain.usecase.GetQueueUseCase
import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.NanoWSD
import java.io.IOException

class RouteRegistry(
    private val sessionRoutes: SessionRoutes,
    private val searchRoutes: SearchRoutes,
    private val imageRoutes: ImageRoutes,
    private val bilibiliAuthRoutes: BilibiliAuthRoutes,
    private val orderRoutes: OrderRoutes,
    private val queueRoutes: QueueRoutes,
    private val playerRoutes: PlayerRoutes,
    private val localRoutes: LocalRoutes,
    private val staticWebAssetHandler: StaticWebAssetHandler,
    private val authInterceptor: SessionAuthInterceptor,
    private val sessionManager: SessionManager,
    private val webSocketHub: MobileWebSocketHub,
    private val getQueueUseCase: GetQueueUseCase,
) {
    fun handle(session: NanoHTTPD.IHTTPSession): NanoHTTPD.Response {
        return try {
            if (session.method == NanoHTTPD.Method.OPTIONS) {
                return NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.OK, "text/plain", "")
                    .apply {
                        addHeader("Access-Control-Allow-Origin", "*")
                        addHeader("Access-Control-Allow-Headers", "Content-Type")
                        addHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
                    }
            }

            when {
                session.uri == ApiConstants.SESSION_STATUS && session.method == NanoHTTPD.Method.GET ->
                    sessionRoutes.getSessionStatus()

                session.uri == ApiConstants.SESSION_BIND && session.method == NanoHTTPD.Method.POST ->
                    sessionRoutes.bindSession(session)

                session.uri == "/api/search/bilibili" && session.method == NanoHTTPD.Method.GET ->
                    searchRoutes.searchBilibili(session)

                session.uri == "/api/search/download" && session.method == NanoHTTPD.Method.POST ->
                    searchRoutes.download(session)

                session.uri == "/api/image/fetch" && session.method == NanoHTTPD.Method.GET ->
                    imageRoutes.fetch(session)

                session.uri == ApiConstants.BILIBILI_AUTH_STATUS && session.method == NanoHTTPD.Method.GET ->
                    bilibiliAuthRoutes.status()

                session.uri == ApiConstants.BILIBILI_AUTH_QR && session.method == NanoHTTPD.Method.POST ->
                    bilibiliAuthRoutes.generateQr()

                session.uri == ApiConstants.BILIBILI_AUTH_POLL && session.method == NanoHTTPD.Method.GET ->
                    bilibiliAuthRoutes.poll()

                session.uri == ApiConstants.BILIBILI_AUTH_CLEAR && session.method == NanoHTTPD.Method.POST ->
                    bilibiliAuthRoutes.clear()

                session.uri == "/api/order/create" && session.method == NanoHTTPD.Method.POST ->
                    orderRoutes.createOrder(session)

                session.uri == "/api/queue/list" && session.method == NanoHTTPD.Method.GET ->
                    queueRoutes.listQueue()

                session.uri == "/api/queue/remove" && session.method == NanoHTTPD.Method.POST ->
                    queueRoutes.remove(session)

                session.uri == "/api/queue/move-next" && session.method == NanoHTTPD.Method.POST ->
                    queueRoutes.moveNext(session)

                session.uri == "/api/player/next" && session.method == NanoHTTPD.Method.POST ->
                    playerRoutes.next()

                session.uri == "/api/player/pause" && session.method == NanoHTTPD.Method.POST ->
                    playerRoutes.pause()

                session.uri == "/api/player/resume" && session.method == NanoHTTPD.Method.POST ->
                    playerRoutes.resume()

                session.uri == "/api/player/volume" && session.method == NanoHTTPD.Method.POST ->
                    playerRoutes.volume(session)

                session.uri == "/api/player/vocal-volume" && session.method == NanoHTTPD.Method.POST ->
                    playerRoutes.vocalVolume(session)

                session.uri == "/api/player/accompaniment-volume" && session.method == NanoHTTPD.Method.POST ->
                    playerRoutes.accompanimentVolume(session)

                session.uri == "/api/player/mode" && session.method == NanoHTTPD.Method.POST ->
                    playerRoutes.mode(session)

                session.uri == "/api/player/status" && session.method == NanoHTTPD.Method.GET ->
                    playerRoutes.status()

                session.uri == "/api/local/list" && session.method == NanoHTTPD.Method.GET ->
                    localRoutes.list()

                session.uri == "/api/local/order" && session.method == NanoHTTPD.Method.POST ->
                    localRoutes.localOrder(session)

                session.uri == "/api/local/delete" && session.method == NanoHTTPD.Method.POST ->
                    localRoutes.localDelete(session)

                session.uri == "/api/local/separate" && session.method == NanoHTTPD.Method.POST ->
                    localRoutes.localSeparate(session)

                session.uri == "/api/local/file-status" && session.method == NanoHTTPD.Method.GET ->
                    localRoutes.fileStatus(session)

                session.uri == "/" || session.uri.startsWith("/index.html") || session.uri.startsWith("/assets") ->
                    staticWebAssetHandler.serveAsset(session.uri)

                else -> NanoHTTPD.newFixedLengthResponse(
                    NanoHTTPD.Response.Status.NOT_FOUND,
                    "application/json",
                    """{"code":404,"message":"not found"}""",
                )
            }
        } catch (throwable: Throwable) {
            Log.e("RouteRegistry", "request failed uri=${session.uri} method=${session.method}", throwable)
            NanoHTTPD.newFixedLengthResponse(
                NanoHTTPD.Response.Status.INTERNAL_ERROR,
                "application/json; charset=utf-8",
                """{"code":500,"message":"internal server error"}""",
            ).apply {
                addHeader("Access-Control-Allow-Origin", "*")
            }
        }
    }

    fun openWebSocket(handshake: NanoHTTPD.IHTTPSession): NanoWSD.WebSocket {
        val sessionId = handshake.parameters["session_id"]?.firstOrNull().orEmpty()
        val clientId = handshake.parameters["client_id"]?.firstOrNull().orEmpty()
        val clientName = handshake.parameters["client_name"]?.firstOrNull()
        val remoteHost = handshake.remoteIpAddress

        return object : NanoWSD.WebSocket(handshake) {
            override fun onOpen() {
                try {
                    val session = sessionManager.bindSession(
                        requestSessionId = sessionId,
                        clientId = clientId,
                        clientName = clientName,
                    )
                    webSocketHub.registerConnection(clientId.ifBlank { session.clients.last().clientId }) { payload ->
                        send(payload)
                    }
                    webSocketHub.broadcastQueueUpdated(
                        getQueueUseCase.execute().toBroadcastPayload() + mapOf("client_count" to session.clientCount),
                    )
                    webSocketHub.broadcastPlayerUpdated(mapOf("play_status" to "idle", "mode" to "original"))
                    webSocketHub.broadcastDownloadUpdated(mapOf("song_id" to "", "download_status" to "idle", "progress" to 0))
                    webSocketHub.broadcastSeparateUpdated(mapOf("song_id" to "", "status" to "idle", "progress" to 0))
                    Log.i("MobileWs", "open client=$clientId remote=$remoteHost")
                } catch (ex: Exception) {
                    Log.e("MobileWs", "open failed client=$clientId", ex)
                    close(
                        NanoWSD.WebSocketFrame.CloseCode.PolicyViolation,
                        ex.message ?: "ws open failed",
                        false,
                    )
                }
            }

            override fun onClose(code: NanoWSD.WebSocketFrame.CloseCode?, reason: String?, initiatedByRemote: Boolean) {
                if (clientId.isNotBlank()) {
                    webSocketHub.unregisterConnection(clientId)
                    val session = sessionManager.removeClient(clientId)
                    webSocketHub.broadcastQueueUpdated(
                        getQueueUseCase.execute().toBroadcastPayload() + mapOf("client_count" to session.clientCount),
                    )
                }
                Log.i("MobileWs", "close client=$clientId reason=$reason")
            }

            override fun onMessage(message: NanoWSD.WebSocketFrame) {
                if (clientId.isNotBlank()) {
                    sessionManager.touchClient(clientId)
                }
                Log.d("MobileWs", "message client=$clientId payload=${message.textPayload}")
            }

            override fun onPong(pong: NanoWSD.WebSocketFrame?) {
                if (clientId.isNotBlank()) {
                    sessionManager.touchClient(clientId)
                }
            }

            override fun onException(exception: IOException) {
                Log.e("MobileWs", "exception client=$clientId", exception)
            }
        }
    }
}
