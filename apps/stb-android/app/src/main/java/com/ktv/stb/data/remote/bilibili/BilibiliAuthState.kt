package com.ktv.stb.data.remote.bilibili

data class BilibiliAuthState(
    val isLoggedIn: Boolean = false,
    val userName: String? = null,
    val userId: String? = null,
    val avatarUrl: String? = null,
    val isVip: Boolean = false,
    val cookieExpireAt: Long = 0L,
    val cookieHeader: String? = null,
    val qrLoginUrl: String? = null,
    val qrKey: String? = null,
    val qrExpireAt: Long = 0L,
    val qrStatus: String = "idle",
    val statusMessage: String? = null,
)
