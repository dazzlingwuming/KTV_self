package com.ktv.stb.data.remote.host

import java.net.HttpURLConnection
import java.net.URL

class HostSeparatorDiagnostics {
    fun ping(hostBaseUrl: String): HostPingResult {
        val normalized = hostBaseUrl.trim().trimEnd('/')
        if (normalized.isBlank()) {
            return HostPingResult(false, null, "host address is empty")
        }
        return runCatching {
            val connection = URL("$normalized/api/ping").openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            val code = connection.responseCode
            val body = (if (code in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader(Charsets.UTF_8)
                ?.use { it.readText() }
                .orEmpty()
            HostPingResult(code in 200..299, code, body.ifBlank { null })
        }.getOrElse { HostPingResult(false, null, it.message ?: "unknown error") }
    }
}

data class HostPingResult(
    val success: Boolean,
    val httpCode: Int?,
    val message: String?,
)
