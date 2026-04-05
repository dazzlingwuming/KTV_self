package com.ktv.stb.downloader

import com.ktv.stb.storage.StoragePaths
import java.io.File

class FileAllocator(
    private val storagePaths: StoragePaths,
) {
    fun tempVideoFile(songId: String): File = storagePaths.tempVideoFile(songId)
    fun tempAudioFile(songId: String): File = storagePaths.tempAudioFile(songId)

    fun finalVideoFile(songId: String): File = storagePaths.finalVideoFile(songId)
    fun finalOriginalAudioFile(songId: String): File = storagePaths.finalOriginalAudioFile(songId)
}
