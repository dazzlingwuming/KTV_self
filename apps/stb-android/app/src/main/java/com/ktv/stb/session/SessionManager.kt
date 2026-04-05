package com.ktv.stb.session

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.provider.Settings
import com.ktv.stb.common.constants.AppConstants
import com.ktv.stb.data.local.dao.DeviceConfigDao
import com.ktv.stb.domain.enum.BindStatus
import com.ktv.stb.domain.model.Session
import com.ktv.stb.domain.model.SessionClient
import kotlinx.coroutines.runBlocking
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.Collections
import java.util.UUID

class SessionManager(
    private val context: Context,
    private val deviceConfigDao: DeviceConfigDao,
) {
    @Volatile
    private var currentSession: Session? = null

    fun ensureSession(): Session {
        val now = System.currentTimeMillis()
        val session = currentSession
        if (session != null && session.expireAt > now) return session

        val newSession = Session(
            sessionId = UUID.randomUUID().toString(),
            deviceId = resolveDeviceId(),
            deviceName = resolveDeviceName(),
            bindStatus = BindStatus.UNBOUND,
            clientCount = 0,
            clients = emptyList(),
            hostIp = resolveLocalIpAddress(),
            port = AppConstants.DEFAULT_HTTP_PORT,
            expireAt = now + AppConstants.QR_EXPIRE_MILLIS,
            lastActiveTime = now,
        )
        currentSession = newSession
        return newSession
    }

    fun getCurrentSession(): Session = ensureSession()

    fun bindSession(
        requestSessionId: String,
        clientId: String?,
        clientName: String?,
    ): Session {
        val session = ensureSession()
        require(session.sessionId == requestSessionId) { "session mismatch" }
        require(session.expireAt > System.currentTimeMillis()) { "session expired" }

        val now = System.currentTimeMillis()
        val normalizedClientId = clientId?.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString()
        val existing = session.clients.firstOrNull { it.clientId == normalizedClientId }
        val updatedClients = if (existing == null) {
            session.clients + SessionClient(
                clientId = normalizedClientId,
                clientName = clientName?.takeIf { it.isNotBlank() },
                connectedAt = now,
                lastSeenAt = now,
            )
        } else {
            session.clients.map {
                if (it.clientId == normalizedClientId) {
                    it.copy(
                        clientName = clientName?.takeIf { name -> name.isNotBlank() } ?: it.clientName,
                        lastSeenAt = now,
                    )
                } else {
                    it
                }
            }
        }

        val updated = session.copy(
            bindStatus = if (updatedClients.isEmpty()) BindStatus.UNBOUND else BindStatus.BOUND,
            clientCount = updatedClients.size,
            clients = updatedClients,
            lastActiveTime = now,
        )
        currentSession = updated
        return updated
    }

    fun removeClient(clientId: String): Session {
        val session = ensureSession()
        val updatedClients = session.clients.filterNot { it.clientId == clientId }
        val updated = session.copy(
            bindStatus = if (updatedClients.isEmpty()) BindStatus.UNBOUND else BindStatus.BOUND,
            clientCount = updatedClients.size,
            clients = updatedClients,
            lastActiveTime = System.currentTimeMillis(),
        )
        currentSession = updated
        return updated
    }

    fun touchClient(clientId: String): Session {
        val session = ensureSession()
        val now = System.currentTimeMillis()
        val updatedClients = session.clients.map {
            if (it.clientId == clientId) it.copy(lastSeenAt = now) else it
        }
        val updated = session.copy(
            clientCount = updatedClients.size,
            clients = updatedClients,
            lastActiveTime = now,
        )
        currentSession = updated
        return updated
    }

    fun resolveWebEntryUrl(): String {
        val session = ensureSession()
        return "http://${session.hostIp}:${session.port}/index.html"
    }

    fun isBound(): Boolean = ensureSession().bindStatus == BindStatus.BOUND

    private fun resolveDeviceId(): String {
        return Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            ?: Build.MODEL
            ?: UUID.randomUUID().toString()
    }

    private fun resolveDeviceName(): String {
        return runBlocking { deviceConfigDao.getCurrentConfig()?.deviceName } ?: AppConstants.DEFAULT_DEVICE_NAME
    }

    private fun resolveLocalIpAddress(): String {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork = connectivityManager.activeNetwork
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork)
        val preferred = mutableListOf<String>()

        if (capabilities != null &&
            (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET))
        ) {
            preferred += listOf("wlan", "wifi", "eth", "en")
        }

        val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
            .filter { it.isUp && !it.isLoopback && !it.isVirtual }

        preferred.forEach { prefix ->
            interfaces.firstOrNull { it.name.startsWith(prefix, ignoreCase = true) }
                ?.let { networkInterface ->
                    Collections.list(networkInterface.inetAddresses).forEach { inetAddress ->
                        if (!inetAddress.isLoopbackAddress && inetAddress is Inet4Address) {
                            val host = inetAddress.hostAddress
                            if (!host.isNullOrBlank()) return host
                        }
                    }
                }
        }

        interfaces.forEach { networkInterface ->
            Collections.list(networkInterface.inetAddresses).forEach { inetAddress ->
                if (!inetAddress.isLoopbackAddress && inetAddress is Inet4Address) {
                    val host = inetAddress.hostAddress
                    if (!host.isNullOrBlank()) return host
                }
            }
        }

        return "127.0.0.1"
    }
}
