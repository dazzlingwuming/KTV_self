package com.ktv.stb.ui.home

data class HomeUiState(
    val currentTitle: String = "暂无播放",
    val playStatus: String = "idle",
    val currentTimeMs: Long = 0L,
    val durationMs: Long = 0L,
    val nextTitle: String? = null,
    val deviceName: String = "",
    val clientCount: Int = 0,
    val volume: Int = 100,
    val vocalVolume: Int = 100,
    val accompanimentVolume: Int = 100,
    val mixAvailable: Boolean = false,
)
