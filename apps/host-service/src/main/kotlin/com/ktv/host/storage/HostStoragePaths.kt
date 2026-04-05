package com.ktv.host.storage

import java.io.File

class HostStoragePaths(baseDir: File = File("build/host-data")) {
    val rootDir: File = baseDir
    val uploadDir: File = File(baseDir, "uploads")
    val resultDir: File = File(baseDir, "results")

    init {
        uploadDir.mkdirs()
        resultDir.mkdirs()
    }

    fun uploadFile(songId: String, originalName: String): File {
        val songDir = File(uploadDir, songId).apply { mkdirs() }
        return File(songDir, originalName)
    }

    fun accompanimentFile(taskId: String): File {
        val taskDir = File(resultDir, taskId).apply { mkdirs() }
        return File(taskDir, "accompaniment.mp3")
    }

    fun vocalFile(taskId: String): File {
        val taskDir = File(resultDir, taskId).apply { mkdirs() }
        return File(taskDir, "vocal.mp3")
    }
}
