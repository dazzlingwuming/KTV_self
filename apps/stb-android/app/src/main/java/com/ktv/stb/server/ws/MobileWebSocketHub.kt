package com.ktv.stb.server.ws

import android.util.Log
import com.google.gson.Gson
import java.util.concurrent.ConcurrentHashMap

class MobileWebSocketHub(
    private val gson: Gson,
) {
    private val connections = ConcurrentHashMap<String, (String) -> Unit>()

    fun registerConnection(clientId: String, sender: (String) -> Unit) {
        connections[clientId] = sender
    }

    fun unregisterConnection(clientId: String) {
        connections.remove(clientId)
    }

    fun broadcast(event: String, data: Any) {
        val payload = gson.toJson(mapOf("event" to event, "data" to data))
        val failedClients = mutableListOf<String>()
        connections.forEach { (clientId, sender) ->
            try {
                sender(payload)
            } catch (throwable: Throwable) {
                Log.e("MobileWebSocketHub", "broadcast failed event=$event client=$clientId", throwable)
                failedClients += clientId
            }
        }
        failedClients.forEach { unregisterConnection(it) }
    }

    fun connectionCount(): Int = connections.size

    fun broadcastPlayerUpdated(data: Any) = broadcast("player_updated", data)

    fun broadcastQueueUpdated(data: Any) = broadcast("queue_updated", data)

    fun broadcastDownloadUpdated(data: Any) = broadcast("download_updated", data)

    fun broadcastSeparateUpdated(data: Any) = broadcast("separate_updated", data)
}
