package com.ktv.stb.data.remote.bilibili

import android.graphics.Bitmap
import android.graphics.Color
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.ktv.stb.data.local.dao.DeviceConfigDao
import com.ktv.stb.data.local.entity.DeviceConfigEntity
import com.ktv.stb.session.SessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.net.HttpCookie
import java.net.HttpURLConnection
import java.net.URLConnection
import java.net.URL
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

class BilibiliAuthManager(
    private val deviceConfigDao: DeviceConfigDao,
    private val sessionManager: SessionManager,
) {
    private val mutex = Mutex()
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

    @Volatile
    private var state: BilibiliAuthState = BilibiliAuthState()

    private fun currentDeviceId(): String = sessionManager.getCurrentSession().deviceId

    suspend fun loadState(): BilibiliAuthState = mutex.withLock {
        val deviceId = currentDeviceId()
        val config = withContext(Dispatchers.IO) { deviceConfigDao.getByDeviceId(deviceId) }
        state = config.toAuthState()
        state
    }

    fun getState(): BilibiliAuthState = state

    fun getCookieHeader(): String? {
        val current = state
        return current.cookieHeader?.takeIf { current.isLoggedIn && it.isNotBlank() }
    }

    suspend fun ensureQrLogin(): BilibiliAuthState = mutex.withLock {
        try {
            val current = state
            val now = System.currentTimeMillis()
            if (!current.qrLoginUrl.isNullOrBlank() && current.qrExpireAt > now && !current.isLoggedIn) {
                return current
            }

            val body = withContext(Dispatchers.IO) {
                val endpoint = "https://passport.bilibili.com/x/passport-login/web/qrcode/generate"
                val connection = openConnection(endpoint).apply {
                    requestMethod = "GET"
                    connectTimeout = 8000
                    readTimeout = 8000
                    setRequestProperty("User-Agent", USER_AGENT)
                    setRequestProperty("Referer", "https://www.bilibili.com")
                    setRequestProperty("Accept", "application/json")
                }
                connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            }
            val root = JsonParser.parseString(body).asJsonObject
            val code = root.get("code")?.asInt ?: -1
            if (code != 0) {
                state = current.copy(
                    qrStatus = "failed",
                    statusMessage = "二维码获取失败: $code",
                )
                return state
            }
            val data = root.getAsJsonObject("data")
            val url = data?.get("url")?.asString.orEmpty()
            val key = data?.get("qrcode_key")?.asString.orEmpty()
            state = current.copy(
                qrLoginUrl = url,
                qrKey = key,
                qrExpireAt = now + 170_000L,
                qrStatus = "waiting_scan",
                statusMessage = "请使用 B站 App 扫码登录",
            )
            return state
        } catch (e: Exception) {
            state = state.copy(
                qrStatus = "failed",
                statusMessage = "二维码获取异常: ${e.message ?: "unknown error"}",
            )
            return state
        }
    }

    suspend fun pollQrLogin(): BilibiliAuthState = mutex.withLock {
        try {
            val current = state
            val key = current.qrKey ?: return current.copy(qrStatus = "idle", statusMessage = "暂无登录二维码").also { state = it }
            val body = withContext(Dispatchers.IO) {
                val endpoint = "https://passport.bilibili.com/x/passport-login/web/qrcode/poll?qrcode_key=$key"
                val connection = openConnection(endpoint).apply {
                    requestMethod = "GET"
                    connectTimeout = 8000
                    readTimeout = 8000
                    setRequestProperty("User-Agent", USER_AGENT)
                    setRequestProperty("Referer", "https://www.bilibili.com")
                    setRequestProperty("Accept", "application/json")
                }
                connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            }
            val root = JsonParser.parseString(body).asJsonObject
            val code = root.get("code")?.asInt ?: -1
            if (code != 0) {
                state = current.copy(qrStatus = "failed", statusMessage = "登录轮询失败: $code")
                return state
            }
            val data = root.getAsJsonObject("data")
            val statusCode = data?.get("code")?.asInt ?: -1
            return when (statusCode) {
                0 -> handleLoginSuccess(current, data)
                86090 -> current.copy(qrStatus = "scanned", statusMessage = "已扫码，请在 B站 App 中确认").also { state = it }
                86101 -> current.copy(qrStatus = "waiting_scan", statusMessage = "等待扫码").also { state = it }
                86038 -> current.copy(qrStatus = "expired", statusMessage = "二维码已过期，请刷新").also { state = it }
                else -> current.copy(qrStatus = "failed", statusMessage = "未知登录状态: $statusCode").also { state = it }
            }
        } catch (e: Exception) {
            state = state.copy(
                qrStatus = "failed",
                statusMessage = "登录轮询异常: ${e.message ?: "unknown error"}",
            )
            return state
        }
    }

    suspend fun clearLogin(): BilibiliAuthState = mutex.withLock {
        val config = getOrCreateConfig()
        withContext(Dispatchers.IO) {
            deviceConfigDao.upsert(
                config.copy(
                    bilibiliCookies = "",
                    bilibiliSessData = "",
                    bilibiliJct = "",
                    bilibiliDedeUserId = "",
                    bilibiliUserName = "",
                    bilibiliAvatarUrl = "",
                    bilibiliIsVip = false,
                    bilibiliCookieExpireAt = 0L,
                ),
            )
        }
        state = BilibiliAuthState()
        state
    }

    fun buildQrBitmap(size: Int = 360): Bitmap? {
        val loginUrl = state.qrLoginUrl ?: return null
        val matrix = QRCodeWriter().encode(loginUrl, BarcodeFormat.QR_CODE, size, size)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
        for (x in 0 until size) {
            for (y in 0 until size) {
                bitmap.setPixel(x, y, if (matrix[x, y]) Color.BLACK else Color.WHITE)
            }
        }
        return bitmap
    }

    private suspend fun handleLoginSuccess(current: BilibiliAuthState, data: JsonObject?): BilibiliAuthState {
        val loginUrl = data?.get("url")?.asString
            ?: return current.copy(qrStatus = "failed", statusMessage = "登录成功但缺少跳转地址").also { state = it }
        val cookieResult = withContext(Dispatchers.IO) { consumeLoginRedirect(loginUrl) }
        if (cookieResult.cookieHeader.isBlank()) {
            state = current.copy(qrStatus = "failed", statusMessage = "未获取到登录 Cookie")
            return state
        }

        val profile = withContext(Dispatchers.IO) { fetchNavProfile(cookieResult.cookieHeader) }
        val config = getOrCreateConfig()
        withContext(Dispatchers.IO) {
            deviceConfigDao.upsert(
                config.copy(
                    bilibiliCookies = cookieResult.cookieHeader,
                    bilibiliSessData = cookieResult.cookies["SESSDATA"].orEmpty(),
                    bilibiliJct = cookieResult.cookies["bili_jct"].orEmpty(),
                    bilibiliDedeUserId = cookieResult.cookies["DedeUserID"].orEmpty(),
                    bilibiliUserName = profile.userName.orEmpty(),
                    bilibiliAvatarUrl = profile.avatarUrl.orEmpty(),
                    bilibiliIsVip = profile.isVip,
                    bilibiliCookieExpireAt = cookieResult.expireAt,
                ),
            )
        }

        state = BilibiliAuthState(
            isLoggedIn = true,
            userName = profile.userName,
            userId = profile.userId,
            avatarUrl = profile.avatarUrl,
            isVip = profile.isVip,
            cookieExpireAt = cookieResult.expireAt,
            cookieHeader = cookieResult.cookieHeader,
            qrStatus = "authorized",
            statusMessage = "B站登录成功",
        )
        return state
    }

    private fun consumeLoginRedirect(initialUrl: String): CookieConsumeResult {
        var currentUrl = initialUrl
        val cookies = linkedMapOf<String, String>()
        var expireAt = 0L

        repeat(6) {
            val connection = openConnection(currentUrl).apply {
                requestMethod = "GET"
                connectTimeout = 8000
                readTimeout = 8000
                instanceFollowRedirects = false
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty("Referer", "https://www.bilibili.com")
                if (cookies.isNotEmpty()) {
                    setRequestProperty("Cookie", cookies.entries.joinToString("; ") { "${it.key}=${it.value}" })
                }
            }

            connection.headerFields["Set-Cookie"]?.forEach { header ->
                runCatching { HttpCookie.parse(header) }.getOrDefault(emptyList()).forEach { cookie ->
                    cookies[cookie.name] = cookie.value
                    if (cookie.maxAge > 0) {
                        expireAt = maxOf(expireAt, System.currentTimeMillis() + cookie.maxAge * 1000L)
                    }
                }
            }

            val location = connection.getHeaderField("Location")
            if (connection.responseCode in 300..399 && !location.isNullOrBlank()) {
                currentUrl = location
            } else {
                return CookieConsumeResult(
                    cookies = cookies,
                    cookieHeader = cookies.entries.joinToString("; ") { "${it.key}=${it.value}" },
                    expireAt = expireAt,
                )
            }
        }

        return CookieConsumeResult(
            cookies = cookies,
            cookieHeader = cookies.entries.joinToString("; ") { "${it.key}=${it.value}" },
            expireAt = expireAt,
        )
    }

    private fun fetchNavProfile(cookieHeader: String): BilibiliProfile {
        return try {
            val connection = openConnection("https://api.bilibili.com/x/web-interface/nav").apply {
                requestMethod = "GET"
                connectTimeout = 8000
                readTimeout = 8000
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty("Referer", "https://www.bilibili.com")
                setRequestProperty("Cookie", cookieHeader)
                setRequestProperty("Accept", "application/json")
            }
            val body = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val root = JsonParser.parseString(body).asJsonObject
            val code = root.get("code")?.asInt ?: -1
            if (code != 0) return BilibiliProfile()
            val data = root.getAsJsonObject("data") ?: return BilibiliProfile()
            BilibiliProfile(
                userName = data.get("uname")?.asString,
                userId = data.get("mid")?.asLong?.toString(),
                avatarUrl = data.get("face")?.asString,
                isVip = (data.get("vipType")?.asInt ?: 0) > 0 || (data.get("vipStatus")?.asInt ?: 0) > 0,
            )
        } catch (_: Exception) {
            BilibiliProfile()
        }
    }

    private suspend fun getOrCreateConfig(): DeviceConfigEntity {
        val session = sessionManager.getCurrentSession()
        val existing = withContext(Dispatchers.IO) { deviceConfigDao.getByDeviceId(session.deviceId) }
        if (existing != null) return existing
        return DeviceConfigEntity(
            deviceId = session.deviceId,
            deviceName = session.deviceName,
            hostAddress = "",
            defaultPlayMode = "original",
            allowDuplicateOrder = false,
            cacheLimitMb = 4096L,
        )
    }

    private fun DeviceConfigEntity?.toAuthState(): BilibiliAuthState {
        if (this == null || bilibiliCookies.isBlank()) return BilibiliAuthState()
        return BilibiliAuthState(
            isLoggedIn = true,
            userName = bilibiliUserName.ifBlank { null },
            userId = bilibiliDedeUserId.ifBlank { null },
            avatarUrl = bilibiliAvatarUrl.ifBlank { null },
            isVip = bilibiliIsVip,
            cookieExpireAt = bilibiliCookieExpireAt,
            cookieHeader = bilibiliCookies,
            qrStatus = "authorized",
            statusMessage = "已登录 B站",
        )
    }

    companion object {
        private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0 Safari/537.36"
    }

    private fun openConnection(url: String): HttpURLConnection {
        val connection = URL(url).openConnection()
        if (connection is HttpsURLConnection) {
            connection.sslSocketFactory = unsafeSslSocketFactory
            connection.hostnameVerifier = unsafeHostnameVerifier
        }
        return connection as HttpURLConnection
    }
}

data class CookieConsumeResult(
    val cookies: Map<String, String>,
    val cookieHeader: String,
    val expireAt: Long,
)

data class BilibiliProfile(
    val userName: String? = null,
    val userId: String? = null,
    val avatarUrl: String? = null,
    val isVip: Boolean = false,
)
