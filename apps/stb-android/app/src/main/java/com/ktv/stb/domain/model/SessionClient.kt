package com.ktv.stb.domain.model

data class SessionClient(
    val clientId: String,
    val clientName: String?,
    val connectedAt: Long,
    val lastSeenAt: Long,
)
