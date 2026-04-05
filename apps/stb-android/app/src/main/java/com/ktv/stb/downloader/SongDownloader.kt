package com.ktv.stb.downloader

import com.ktv.stb.data.local.entity.SongEntity

interface SongDownloader {
    fun download(song: SongEntity, onProgress: (Int) -> Unit): DownloadResult
}

data class DownloadResult(
    val success: Boolean,
    val videoPath: String?,
    val originalAudioPath: String?,
    val fileSize: Long,
    val errorCode: String? = null,
    val errorMessage: String? = null,
)
