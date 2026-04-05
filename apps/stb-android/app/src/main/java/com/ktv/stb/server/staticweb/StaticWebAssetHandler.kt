package com.ktv.stb.server.staticweb

import android.content.res.AssetManager
import fi.iki.elonen.NanoHTTPD

class StaticWebAssetHandler(
    private val assetManager: AssetManager,
    private val rootPath: String,
) {
    fun serveAsset(requestUri: String): NanoHTTPD.Response {
        val target = when (requestUri) {
            "", "/" -> "$rootPath/index.html"
            else -> "$rootPath/${requestUri.removePrefix("/")}"
        }
        return try {
            val stream = assetManager.open(target)
            NanoHTTPD.newChunkedResponse(
                NanoHTTPD.Response.Status.OK,
                guessMimeType(target),
                stream,
            ).apply {
                addHeader("Access-Control-Allow-Origin", "*")
                addHeader("Cache-Control", "no-store, no-cache, must-revalidate, max-age=0")
                addHeader("Pragma", "no-cache")
                addHeader("Expires", "0")
            }
        } catch (_: Exception) {
            NanoHTTPD.newFixedLengthResponse(
                NanoHTTPD.Response.Status.NOT_FOUND,
                "text/plain; charset=utf-8",
                "asset not found",
            )
        }
    }

    private fun guessMimeType(path: String): String {
        return when {
            path.endsWith(".html") -> "text/html; charset=utf-8"
            path.endsWith(".js") -> "application/javascript; charset=utf-8"
            path.endsWith(".css") -> "text/css; charset=utf-8"
            else -> "application/octet-stream"
        }
    }
}
