package com.ktv.stb.server.route

import android.util.Log
import fi.iki.elonen.NanoHTTPD
import java.net.HttpURLConnection
import java.net.URL

class ImageRoutes {
    fun fetch(session: NanoHTTPD.IHTTPSession): NanoHTTPD.Response {
        val rawUrl = session.parameters["url"]?.firstOrNull().orEmpty()
        if (rawUrl.isBlank() || !(rawUrl.startsWith("http://") || rawUrl.startsWith("https://"))) {
            return NanoHTTPD.newFixedLengthResponse(
                NanoHTTPD.Response.Status.BAD_REQUEST,
                "text/plain; charset=utf-8",
                "invalid image url",
            ).apply {
                addHeader("Access-Control-Allow-Origin", "*")
            }
        }

        return try {
            val connection = (URL(rawUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 8000
                readTimeout = 12000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "Mozilla/5.0 Android KTV/1.0")
                setRequestProperty("Referer", "https://www.bilibili.com")
                setRequestProperty("Accept", "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8")
            }
            val mimeType = connection.contentType?.substringBefore(";")?.ifBlank { null }
                ?: guessMimeType(rawUrl)
            NanoHTTPD.newChunkedResponse(
                NanoHTTPD.Response.Status.OK,
                mimeType,
                connection.inputStream,
            ).apply {
                addHeader("Access-Control-Allow-Origin", "*")
                addHeader("Cache-Control", "public, max-age=3600")
            }
        } catch (throwable: Throwable) {
            Log.e("ImageRoutes", "image fetch failed url=$rawUrl", throwable)
            NanoHTTPD.newFixedLengthResponse(
                NanoHTTPD.Response.Status.NOT_FOUND,
                "text/plain; charset=utf-8",
                "image fetch failed",
            ).apply {
                addHeader("Access-Control-Allow-Origin", "*")
            }
        }
    }

    private fun guessMimeType(url: String): String {
        val lower = url.lowercase()
        return when {
            lower.contains(".png") -> "image/png"
            lower.contains(".webp") -> "image/webp"
            lower.contains(".gif") -> "image/gif"
            else -> "image/jpeg"
        }
    }
}
