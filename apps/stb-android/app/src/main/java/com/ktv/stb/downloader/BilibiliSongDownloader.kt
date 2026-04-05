package com.ktv.stb.downloader

import android.util.Log
import com.ktv.stb.data.local.entity.SongEntity
import com.ktv.stb.data.remote.bilibili.BilibiliAuthManager
import com.ktv.stb.data.remote.bilibili.BilibiliMediaResolver
import com.ktv.stb.storage.SongFileManager
import java.io.BufferedOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

class BilibiliSongDownloader(
    private val mediaResolver: BilibiliMediaResolver,
    private val authManager: BilibiliAuthManager,
    private val fileAllocator: FileAllocator,
    private val songFileManager: SongFileManager,
) : SongDownloader {
    private val unsafeSslSocketFactory by lazy {
        val trustManagers = arrayOf<TrustManager>(
            object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
                override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
                override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
            },
        )
        SSLContext.getInstance("TLS").apply {
            init(null, trustManagers, SecureRandom())
        }.socketFactory
    }
    private val unsafeHostnameVerifier = HostnameVerifier { _, _ -> true }

    override fun download(song: SongEntity, onProgress: (Int) -> Unit): DownloadResult {
        val tempVideoFile = fileAllocator.tempVideoFile(song.songId)
        val tempAudioFile = fileAllocator.tempAudioFile(song.songId)
        val finalVideoFile = fileAllocator.finalVideoFile(song.songId)
        val finalAudioFile = fileAllocator.finalOriginalAudioFile(song.songId)

        return try {
            val resolved = mediaResolver.resolve(song.sourceId)
            val media = resolved.media
            if (media == null) {
                songFileManager.cleanupTemps(tempVideoFile, tempAudioFile)
                return DownloadResult(
                    success = false,
                    videoPath = null,
                    originalAudioPath = null,
                    fileSize = 0L,
                    errorCode = resolved.errorCode,
                    errorMessage = resolved.errorMessage ?: "media resolve failed",
                )
            }

            when (media.mediaMode) {
                "dash" -> downloadDash(song.sourceId, media.videoUrl, media.videoBackupUrls, media.audioUrl, media.audioBackupUrls, tempVideoFile, tempAudioFile, finalVideoFile, finalAudioFile, onProgress)
                else -> downloadSingle(song.sourceId, media.mediaUrl, media.mediaBackupUrls, tempVideoFile, finalVideoFile, onProgress)
            }
        } catch (ex: Exception) {
            Log.e("SongDownloader", "download exception ${song.songId}", ex)
            songFileManager.cleanupTemps(tempVideoFile, tempAudioFile)
            songFileManager.cleanupFinals(finalVideoFile, finalAudioFile)
            DownloadResult(
                success = false,
                videoPath = null,
                originalAudioPath = null,
                fileSize = 0L,
                errorCode = DownloadErrorCode.DOWNLOAD_HTTP_FAILED,
                errorMessage = ex.message,
            )
        }
    }

    private fun downloadSingle(
        sourceId: String,
        mediaUrl: String?,
        backupUrls: List<String>,
        tempVideoFile: File,
        finalVideoFile: File,
        onProgress: (Int) -> Unit,
    ): DownloadResult {
        if (mediaUrl.isNullOrBlank()) {
            return DownloadResult(false, null, null, 0L, DownloadErrorCode.EMPTY_DURL, "single media url missing")
        }

        val single = downloadWithFallback(sourceId, listOf(mediaUrl) + backupUrls, tempVideoFile) { progress ->
            onProgress(progress.coerceIn(0, 99))
        }
        if (!single.success) {
            songFileManager.cleanupTemps(tempVideoFile)
            return DownloadResult(
                success = false,
                videoPath = null,
                originalAudioPath = null,
                fileSize = 0L,
                errorCode = single.errorCode,
                errorMessage = single.errorMessage,
            )
        }

        if (!songFileManager.moveTempToFinal(tempVideoFile, finalVideoFile)) {
            songFileManager.cleanupTemps(tempVideoFile)
            songFileManager.cleanupFinals(finalVideoFile)
            return DownloadResult(false, null, null, 0L, DownloadErrorCode.MOVE_FILE_FAILED, "file move failed")
        }
        if (!finalVideoFile.exists() || finalVideoFile.length() <= 0L) {
            songFileManager.cleanupFinals(finalVideoFile)
            return DownloadResult(false, null, null, 0L, DownloadErrorCode.FINAL_FILE_INVALID, "final video invalid")
        }

        return DownloadResult(
            success = true,
            videoPath = finalVideoFile.absolutePath,
            originalAudioPath = null,
            fileSize = finalVideoFile.length(),
        )
    }

    private fun downloadDash(
        sourceId: String,
        videoUrl: String?,
        videoBackupUrls: List<String>,
        audioUrl: String?,
        audioBackupUrls: List<String>,
        tempVideoFile: File,
        tempAudioFile: File,
        finalVideoFile: File,
        finalAudioFile: File,
        onProgress: (Int) -> Unit,
    ): DownloadResult {
        if (videoUrl.isNullOrBlank() || audioUrl.isNullOrBlank()) {
            return DownloadResult(false, null, null, 0L, DownloadErrorCode.EMPTY_DURL, "dash video or audio url missing")
        }

        val video = downloadWithFallback(sourceId, listOf(videoUrl) + videoBackupUrls, tempVideoFile) { progress ->
            onProgress((progress * 0.7f).toInt().coerceIn(0, 69))
        }
        if (!video.success) {
            songFileManager.cleanupTemps(tempVideoFile, tempAudioFile)
            return DownloadResult(false, null, null, 0L, video.errorCode, video.errorMessage)
        }

        val audio = downloadWithFallback(sourceId, listOf(audioUrl) + audioBackupUrls, tempAudioFile) { progress ->
            onProgress((70 + progress * 0.3f).toInt().coerceIn(70, 99))
        }
        if (!audio.success) {
            songFileManager.cleanupTemps(tempVideoFile, tempAudioFile)
            return DownloadResult(false, null, null, 0L, audio.errorCode, audio.errorMessage)
        }

        val movedVideo = songFileManager.moveTempToFinal(tempVideoFile, finalVideoFile)
        val movedAudio = songFileManager.moveTempToFinal(tempAudioFile, finalAudioFile)
        if (!movedVideo || !movedAudio) {
            songFileManager.cleanupTemps(tempVideoFile, tempAudioFile)
            songFileManager.cleanupFinals(finalVideoFile, finalAudioFile)
            return DownloadResult(false, null, null, 0L, DownloadErrorCode.MOVE_FILE_FAILED, "dash file move failed")
        }
        if (!finalVideoFile.exists() || finalVideoFile.length() <= 0L || !finalAudioFile.exists() || finalAudioFile.length() <= 0L) {
            songFileManager.cleanupFinals(finalVideoFile, finalAudioFile)
            return DownloadResult(false, null, null, 0L, DownloadErrorCode.FINAL_FILE_INVALID, "dash final file invalid")
        }

        return DownloadResult(
            success = true,
            videoPath = finalVideoFile.absolutePath,
            originalAudioPath = finalAudioFile.absolutePath,
            fileSize = finalVideoFile.length() + finalAudioFile.length(),
        )
    }

    private fun downloadWithFallback(
        sourceId: String,
        urls: List<String>,
        tempFile: File,
        onProgress: (Int) -> Unit,
    ): PartialDownloadResult {
        var lastResult: PartialDownloadResult = PartialDownloadResult(false, DownloadErrorCode.DOWNLOAD_HTTP_FAILED, "download url missing")
        urls.filter { it.isNotBlank() }.forEachIndexed { index, targetUrl ->
            songFileManager.cleanupTemp(tempFile)
            val result = downloadToTemp(sourceId, targetUrl, tempFile, onProgress)
            if (result.success) return result
            lastResult = result
            Log.w("SongDownloader", "download fallback failed index=$index url=$targetUrl error=${result.errorMessage}")
        }
        return lastResult
    }

    private fun downloadToTemp(
        sourceId: String,
        targetUrl: String,
        tempFile: File,
        onProgress: (Int) -> Unit,
    ): PartialDownloadResult {
        return try {
            val connection = openConnection(targetUrl).apply {
                requestMethod = "GET"
                connectTimeout = 10000
                readTimeout = 30000
                setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0 Safari/537.36")
                setRequestProperty("Referer", "https://www.bilibili.com/video/$sourceId")
                setRequestProperty("Origin", "https://www.bilibili.com")
                setRequestProperty("Accept", "*/*")
                setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                setRequestProperty("Range", "bytes=0-")
                authManager.getCookieHeader()?.takeIf { it.isNotBlank() }?.let {
                    setRequestProperty("Cookie", it)
                }
            }
            if (connection.responseCode !in 200..299) {
                return PartialDownloadResult(false, DownloadErrorCode.DOWNLOAD_HTTP_FAILED, "download http ${connection.responseCode}")
            }

            val totalBytes = connection.contentLengthLong.takeIf { it > 0 } ?: 1L
            var written = 0L

            connection.inputStream.use { input ->
                BufferedOutputStream(tempFile.outputStream()).use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        output.write(buffer, 0, read)
                        written += read
                        onProgress(((written * 100) / totalBytes).toInt())
                    }
                }
            }

            if (!tempFile.exists() || tempFile.length() <= 0L) {
                PartialDownloadResult(false, DownloadErrorCode.TEMP_FILE_EMPTY, "downloaded temp file is empty")
            } else {
                PartialDownloadResult(true, null, null)
            }
        } catch (e: Exception) {
            PartialDownloadResult(false, DownloadErrorCode.DOWNLOAD_HTTP_FAILED, e.message ?: "download failed")
        }
    }

    private fun openConnection(targetUrl: String): HttpURLConnection {
        val connection = URL(targetUrl).openConnection()
        if (connection is HttpsURLConnection) {
            connection.sslSocketFactory = unsafeSslSocketFactory
            connection.hostnameVerifier = unsafeHostnameVerifier
        }
        return connection as HttpURLConnection
    }
}

data class PartialDownloadResult(
    val success: Boolean,
    val errorCode: String?,
    val errorMessage: String?,
)
