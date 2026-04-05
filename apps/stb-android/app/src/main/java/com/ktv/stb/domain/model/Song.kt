package com.ktv.stb.domain.model

data class Song(
    val songId: String,
    val sourceType: String,
    val sourceId: String,
    val title: String,
    val artist: String?,
    val duration: Long,
    val coverUrl: String?,
    val downloadStatus: String,
    val separateStatus: String,
    val resourceLevel: String,
    val lastErrorCode: String?,
    val lastErrorMessage: String?,
    val createdAt: Long,
    val updatedAt: Long,
)
