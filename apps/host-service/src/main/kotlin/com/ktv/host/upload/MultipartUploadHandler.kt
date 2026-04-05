package com.ktv.host.upload

import com.ktv.host.storage.HostStoragePaths
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receiveMultipart

class MultipartUploadHandler(
    private val storagePaths: HostStoragePaths,
) {
    suspend fun receive(call: ApplicationCall, songId: String): String {
        var storedPath = ""
        val multipart = call.receiveMultipart()
        multipart.forEachPart { part ->
            if (part is PartData.FileItem) {
                val originalName = part.originalFileName ?: "input.bin"
                val file = storagePaths.uploadFile(songId, originalName)
                part.streamProvider().use { input ->
                    file.outputStream().use { output -> input.copyTo(output) }
                }
                storedPath = file.absolutePath
            }
            part.dispose()
        }
        return storedPath
    }
}
