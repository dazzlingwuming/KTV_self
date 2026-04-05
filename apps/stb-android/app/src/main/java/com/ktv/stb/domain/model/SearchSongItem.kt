package com.ktv.stb.domain.model

data class SearchSongItem(
    val sourceType: String = "bilibili",
    val sourceId: String,
    val title: String,
    val artist: String?,
    val duration: Long,
    val coverUrl: String?,
)
