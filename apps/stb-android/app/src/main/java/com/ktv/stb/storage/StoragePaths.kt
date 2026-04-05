package com.ktv.stb.storage

import android.content.Context
import java.io.File

class StoragePaths(context: Context) {
    private val rootDir = File(context.getExternalFilesDir(null), "ktv")
    private val songsDir = File(rootDir, "songs")
    private val tempDir = File(rootDir, "temp")

    init {
        songsDir.mkdirs()
        tempDir.mkdirs()
    }

    fun songDir(songId: String): File = File(songsDir, songId).apply { mkdirs() }

    fun tempVideoFile(songId: String): File = File(tempDir, "$songId.video.tmp")
    fun tempAudioFile(songId: String): File = File(tempDir, "$songId.audio.tmp")
    fun tempAccompanimentFile(songId: String): File = File(tempDir, "$songId.accompaniment.tmp")
    fun tempVocalFile(songId: String): File = File(tempDir, "$songId.vocal.tmp")

    fun finalVideoFile(songId: String): File = File(songDir(songId), "video.mp4")
    fun finalOriginalAudioFile(songId: String): File = File(songDir(songId), "original_audio.m4a")
    fun finalAccompanimentFile(songId: String): File = File(songDir(songId), "accompaniment.wav")
    fun finalVocalFile(songId: String): File = File(songDir(songId), "vocal.wav")
}
