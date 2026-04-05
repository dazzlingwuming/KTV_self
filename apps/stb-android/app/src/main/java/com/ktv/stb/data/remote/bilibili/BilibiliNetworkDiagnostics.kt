package com.ktv.stb.data.remote.bilibili

import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

class BilibiliNetworkDiagnostics {
    private val unsafeSslSocketFactory by lazy {
        val trustManagers = arrayOf<TrustManager>(
            object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
                override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
                override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
            },
        )
        SSLContext.getInstance("TLS").apply {
            init(null, trustManagers, SecureRandom())
        }.socketFactory
    }

    private val unsafeHostnameVerifier = HostnameVerifier { _, _ -> true }

    fun run(): DiagnosticResult {
        val host = "api.bilibili.com"
        val dns = runCatching { InetAddress.getByName(host).hostAddress.orEmpty() }
        val searchApi = runCatching {
            val url =
                "https://api.bilibili.com/x/web-interface/search/type?search_type=video&keyword=test&page=1&page_size=1"
            openConnection(url).useConnection { connection ->
                connection.requestMethod = "GET"
                connection.connectTimeout = 8000
                connection.readTimeout = 8000
                connection.setRequestProperty("User-Agent", USER_AGENT)
                connection.setRequestProperty("Referer", "https://www.bilibili.com/")
                connection.setRequestProperty("Origin", "https://www.bilibili.com")
                connection.setRequestProperty("Accept", "application/json")
                connection.setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                connection.setRequestProperty("X-Requested-With", "XMLHttpRequest")
                connection.responseCode
            }
        }
        return DiagnosticResult(
            dnsOk = dns.isSuccess,
            dnsAddress = dns.getOrNull(),
            dnsMessage = dns.exceptionOrNull()?.message,
            searchApiOk = searchApi.getOrNull() in 200..299,
            searchApiCode = searchApi.getOrNull(),
            searchApiMessage = searchApi.exceptionOrNull()?.message,
        )
    }

    private fun openConnection(targetUrl: String): HttpURLConnection {
        val connection = URL(targetUrl).openConnection()
        if (connection is HttpsURLConnection) {
            connection.sslSocketFactory = unsafeSslSocketFactory
            connection.hostnameVerifier = unsafeHostnameVerifier
        }
        return connection as HttpURLConnection
    }

    private inline fun <T> HttpURLConnection.useConnection(block: (HttpURLConnection) -> T): T {
        return try {
            block(this)
        } finally {
            disconnect()
        }
    }

    companion object {
        private const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0 Safari/537.36"
    }
}

data class DiagnosticResult(
    val dnsOk: Boolean,
    val dnsAddress: String?,
    val dnsMessage: String?,
    val searchApiOk: Boolean,
    val searchApiCode: Int?,
    val searchApiMessage: String?,
)
