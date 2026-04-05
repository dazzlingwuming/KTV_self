package com.ktv.stb.domain.model

import com.ktv.stb.domain.enum.BindStatus

data class Session(
    val sessionId: String,
    val deviceId: String,
    val deviceName: String,
    val bindStatus: BindStatus,
    val clientCount: Int,
    val clients: List<SessionClient>,
    val hostIp: String,
    val port: Int,
    val expireAt: Long,
    val lastActiveTime: Long,
)
