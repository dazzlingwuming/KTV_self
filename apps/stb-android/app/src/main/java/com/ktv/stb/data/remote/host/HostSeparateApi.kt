package com.ktv.stb.data.remote.host

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.DataOutputStream
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

class HostSeparateApi(
    private val gson: Gson,
) {
    fun createTask(
        hostBaseUrl: String,
        songId: String,
        sourceType: String,
        sourceId: String,
        audioFile: File,
    ): CreateSeparateTaskResult {
        val boundary = "----KtvBoundary${UUID.randomUUID()}"
        val connection = openConnection("$hostBaseUrl/api/separate/create").apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 15000
            readTimeout = 60000
            setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            setRequestProperty("Accept", "application/json")
        }

        DataOutputStream(connection.outputStream).use { output ->
            writeFormField(output, boundary, "song_id", songId)
            writeFormField(output, boundary, "source_type", sourceType)
            writeFormField(output, boundary, "source_id", sourceId)
            writeFileField(output, boundary, "audio_file", audioFile)
            output.writeBytes("--$boundary--\r\n")
            output.flush()
        }

        val responseCode = connection.responseCode
        val responseText = readResponse(connection, responseCode)
        if (responseCode !in 200..299) {
            throw IOException("host create task http $responseCode: $responseText")
        }
        val root = JsonParser.parseString(responseText).asJsonObject
        if (root.intValue("code") != 0) {
            throw IOException(root.stringValue("message") ?: "host create task failed")
        }
        return CreateSeparateTaskResult(
            taskId = root.stringValue("task_id") ?: throw IOException("task_id missing"),
            status = root.stringValue("status") ?: "pending",
            progress = root.intValue("progress"),
        )
    }

    fun getStatus(hostBaseUrl: String, taskId: String): SeparateStatusResult {
        val url = "$hostBaseUrl/api/separate/status?task_id=$taskId"
        val connection = openConnection(url).apply {
            requestMethod = "GET"
            connectTimeout = 10000
            readTimeout = 30000
            setRequestProperty("Accept", "application/json")
        }
        val responseCode = connection.responseCode
        val responseText = readResponse(connection, responseCode)
        if (responseCode !in 200..299) {
            throw IOException("host status http $responseCode: $responseText")
        }
        val root = JsonParser.parseString(responseText).asJsonObject
        if (root.intValue("code") != 0) {
            throw IOException(root.stringValue("message") ?: "host status failed")
        }
        return SeparateStatusResult(
            taskId = root.stringValue("task_id") ?: taskId,
            status = root.stringValue("status") ?: "pending",
            progress = root.intValue("progress"),
            errorMessage = root.stringValue("error_message"),
        )
    }

    fun getResult(hostBaseUrl: String, taskId: String): SeparateResult {
        val url = "$hostBaseUrl/api/separate/result?task_id=$taskId"
        val connection = openConnection(url).apply {
            requestMethod = "GET"
            connectTimeout = 10000
            readTimeout = 30000
            setRequestProperty("Accept", "application/json")
        }
        val responseCode = connection.responseCode
        val responseText = readResponse(connection, responseCode)
        if (responseCode !in 200..299) {
            throw IOException("host result http $responseCode: $responseText")
        }
        val root = JsonParser.parseString(responseText).asJsonObject
        if (root.intValue("code") != 0) {
            throw IOException(root.stringValue("message") ?: "host result failed")
        }
        return SeparateResult(
            taskId = root.stringValue("task_id") ?: taskId,
            status = root.stringValue("status") ?: "pending",
            progress = root.intValue("progress"),
            accompanimentUrl = root.stringValue("accompaniment_url"),
            vocalUrl = root.stringValue("vocal_url"),
            errorMessage = root.stringValue("error_message"),
        )
    }

    fun downloadFile(hostBaseUrl: String, relativeOrAbsoluteUrl: String, targetFile: File) {
        val url = if (relativeOrAbsoluteUrl.startsWith("http")) {
            relativeOrAbsoluteUrl
        } else {
            hostBaseUrl + relativeOrAbsoluteUrl
        }
        val connection = openConnection(url).apply {
            requestMethod = "GET"
            connectTimeout = 10000
            readTimeout = 120000
        }
        val responseCode = connection.responseCode
        if (responseCode !in 200..299) {
            throw IOException("host file download http $responseCode")
        }
        targetFile.parentFile?.mkdirs()
        connection.inputStream.use { input ->
            targetFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
    }

    private fun writeFormField(output: DataOutputStream, boundary: String, name: String, value: String) {
        output.writeBytes("--$boundary\r\n")
        output.writeBytes("Content-Disposition: form-data; name=\"$name\"\r\n\r\n")
        output.write(value.toByteArray(Charsets.UTF_8))
        output.writeBytes("\r\n")
    }

    private fun writeFileField(output: DataOutputStream, boundary: String, name: String, file: File) {
        output.writeBytes("--$boundary\r\n")
        output.writeBytes("Content-Disposition: form-data; name=\"$name\"; filename=\"${file.name}\"\r\n")
        output.writeBytes("Content-Type: application/octet-stream\r\n\r\n")
        file.inputStream().use { input -> input.copyTo(output) }
        output.writeBytes("\r\n")
    }

    private fun openConnection(url: String): HttpURLConnection {
        return URL(url).openConnection() as HttpURLConnection
    }

    private fun readResponse(connection: HttpURLConnection, code: Int): String {
        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
        return stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
    }
}

data class CreateSeparateTaskResult(
    val taskId: String,
    val status: String,
    val progress: Int,
)

data class SeparateStatusResult(
    val taskId: String,
    val status: String,
    val progress: Int,
    val errorMessage: String?,
)

data class SeparateResult(
    val taskId: String,
    val status: String,
    val progress: Int,
    val accompanimentUrl: String?,
    val vocalUrl: String?,
    val errorMessage: String?,
)

private fun JsonObject.stringValue(key: String): String? {
    val value = get(key) ?: return null
    return if (value.isJsonNull) null else value.asString
}

private fun JsonObject.intValue(key: String): Int {
    val value = get(key) ?: return 0
    return if (value.isJsonNull) 0 else value.asInt
}
