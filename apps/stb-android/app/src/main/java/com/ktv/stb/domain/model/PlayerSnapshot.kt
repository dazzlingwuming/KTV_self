package com.ktv.stb.domain.model

data class PlayerSnapshot(
    val playStatus: String,
    val currentSongId: String?,
    val currentTitle: String?,
    val currentTimeMs: Long,
    val durationMs: Long,
    val volume: Int,
    val playMode: String,
    val vocalVolume: Int,
    val accompanimentVolume: Int,
    val mixAvailable: Boolean,
    val errorMessage: String?,
)
