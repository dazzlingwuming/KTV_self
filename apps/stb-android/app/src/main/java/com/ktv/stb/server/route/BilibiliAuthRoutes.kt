package com.ktv.stb.server.route

import com.google.gson.Gson
import com.ktv.stb.common.result.ApiResult
import com.ktv.stb.data.remote.bilibili.BilibiliAuthManager
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.runBlocking

class BilibiliAuthRoutes(
    private val authManager: BilibiliAuthManager,
    private val gson: Gson,
) {
    fun status(): NanoHTTPD.Response {
        val state = authManager.getState()
        return json(
            mapOf(
                "logged_in" to state.isLoggedIn,
                "user_name" to state.userName,
                "user_id" to state.userId,
                "avatar_url" to state.avatarUrl,
                "is_vip" to state.isVip,
                "cookie_expire_at" to state.cookieExpireAt,
                "qr_status" to state.qrStatus,
                "status_message" to state.statusMessage,
                "has_qr" to !state.qrLoginUrl.isNullOrBlank(),
                "qr_url" to state.qrLoginUrl,
            ),
        )
    }

    fun generateQr(): NanoHTTPD.Response {
        val state = runBlocking { authManager.ensureQrLogin() }
        return json(
            mapOf(
                "logged_in" to state.isLoggedIn,
                "qr_status" to state.qrStatus,
                "status_message" to state.statusMessage,
                "qr_url" to state.qrLoginUrl,
                "qr_expire_at" to state.qrExpireAt,
            ),
        )
    }

    fun poll(): NanoHTTPD.Response {
        val state = runBlocking { authManager.pollQrLogin() }
        return json(
            mapOf(
                "logged_in" to state.isLoggedIn,
                "user_name" to state.userName,
                "user_id" to state.userId,
                "is_vip" to state.isVip,
                "qr_status" to state.qrStatus,
                "status_message" to state.statusMessage,
            ),
        )
    }

    fun clear(): NanoHTTPD.Response {
        runBlocking { authManager.clearLogin() }
        return json(mapOf("cleared" to true))
    }

    private fun json(data: Any): NanoHTTPD.Response {
        return NanoHTTPD.newFixedLengthResponse(
            NanoHTTPD.Response.Status.OK,
            "application/json; charset=utf-8",
            gson.toJson(ApiResult(data = data)),
        ).apply {
            addHeader("Access-Control-Allow-Origin", "*")
        }
    }
}
