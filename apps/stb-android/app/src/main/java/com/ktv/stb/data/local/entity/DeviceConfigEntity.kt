package com.ktv.stb.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "device_config")
data class DeviceConfigEntity(
    @PrimaryKey val deviceId: String,
    val deviceName: String,
    val hostAddress: String,
    val defaultPlayMode: String,
    val allowDuplicateOrder: Boolean,
    val cacheLimitMb: Long,
    val bilibiliCookies: String = "",
    val bilibiliSessData: String = "",
    val bilibiliJct: String = "",
    val bilibiliDedeUserId: String = "",
    val bilibiliUserName: String = "",
    val bilibiliAvatarUrl: String = "",
    val bilibiliIsVip: Boolean = false,
    val bilibiliCookieExpireAt: Long = 0L,
)
