package com.ktv.stb.storage

import java.io.File

class SongFileManager {
    fun moveTempToFinal(tempFile: File, finalFile: File): Boolean {
        finalFile.parentFile?.mkdirs()
        if (finalFile.exists()) {
            finalFile.delete()
        }
        return tempFile.exists() && tempFile.renameTo(finalFile)
    }

    fun cleanupTemp(tempFile: File) {
        if (tempFile.exists()) {
            tempFile.delete()
        }
    }

    fun cleanupTemps(vararg files: File?) {
        files.forEach { file ->
            if (file != null && file.exists()) {
                file.delete()
            }
        }
    }

    fun cleanupFinals(vararg files: File?) {
        files.forEach { file ->
            if (file != null && file.exists()) {
                file.delete()
            }
        }
    }
}
