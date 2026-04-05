package com.ktv.stb.domain.model

data class DeviceConfig(
    val deviceId: String,
    val deviceName: String,
    val hostAddress: String,
    val defaultPlayMode: String,
    val allowDuplicateOrder: Boolean,
    val cacheLimitMb: Long,
)
