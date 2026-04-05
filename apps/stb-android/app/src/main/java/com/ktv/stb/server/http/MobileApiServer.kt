package com.ktv.stb.server.http

import android.util.Log
import com.ktv.stb.server.route.RouteRegistry
import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.NanoWSD

class MobileApiServer(
    private val serverConfig: ServerConfig,
    private val routeRegistry: RouteRegistry,
) {
    private val server = object : NanoWSD(serverConfig.port) {
        override fun serveHttp(session: IHTTPSession): Response {
            return routeRegistry.handle(session)
        }

        override fun openWebSocket(handshake: IHTTPSession): WebSocket {
            return routeRegistry.openWebSocket(handshake)
        }
    }

    fun startServer() {
        if (!server.wasStarted()) {
            server.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false)
            Log.i("MobileApiServer", "server started on ${serverConfig.port}")
        }
    }

    fun stopServer() {
        if (server.wasStarted()) {
            server.stop()
            Log.i("MobileApiServer", "server stopped")
        }
    }

    fun isRunning(): Boolean = server.wasStarted()
}
