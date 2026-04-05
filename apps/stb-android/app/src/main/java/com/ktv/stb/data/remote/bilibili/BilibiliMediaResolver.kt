package com.ktv.stb.data.remote.bilibili

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.ktv.stb.downloader.DownloadErrorCode
import java.net.HttpURLConnection
import java.net.URL

class BilibiliMediaResolver(
    private val authManager: BilibiliAuthManager? = null,
) {
    fun resolve(sourceId: String): ResolveMediaResult {
        val cidResult = fetchCid(sourceId)
        if (cidResult.errorCode != null) {
            return ResolveMediaResult.failure(cidResult.errorCode, cidResult.errorMessage ?: "view api failed")
        }
        val cid = cidResult.cid ?: return ResolveMediaResult.failure(DownloadErrorCode.CID_NOT_FOUND, "cid not found")

        val playUrlResult = fetchPlayableUrl(sourceId, cid)
        if (playUrlResult.errorCode != null) {
            return ResolveMediaResult.failure(playUrlResult.errorCode, playUrlResult.errorMessage ?: "playurl api failed")
        }

        if (!playUrlResult.dashVideoUrl.isNullOrBlank() && !playUrlResult.dashAudioUrl.isNullOrBlank()) {
            return ResolveMediaResult.success(
                ResolvedMedia(
                    sourceId = sourceId,
                    mediaMode = "dash",
                    videoUrl = playUrlResult.dashVideoUrl,
                    videoBackupUrls = playUrlResult.dashVideoBackupUrls,
                    audioUrl = playUrlResult.dashAudioUrl,
                    audioBackupUrls = playUrlResult.dashAudioBackupUrls,
                    mediaType = "video_audio_split",
                    extension = "mp4",
                    audioExtension = "m4a",
                ),
            )
        }

        val playUrl = playUrlResult.mediaUrl
            ?: return ResolveMediaResult.failure(DownloadErrorCode.EMPTY_DURL, "playurl returned empty media")
        val extension = inferExtension(playUrl)
        if (extension != "mp4") {
            return ResolveMediaResult.failure(DownloadErrorCode.UNSUPPORTED_NON_MP4, "only direct mp4 media is supported in current build")
        }
        return ResolveMediaResult.success(
            ResolvedMedia(
                sourceId = sourceId,
                mediaMode = "single_file",
                mediaUrl = playUrl,
                mediaBackupUrls = playUrlResult.mediaBackupUrls,
                mediaType = "video",
                extension = extension,
            ),
        )
    }

    private fun fetchCid(sourceId: String): ViewResolveResult {
        val endpoint = "https://api.bilibili.com/x/web-interface/view?bvid=$sourceId"
        return try {
            val connection = openConnection(endpoint).apply {
                requestMethod = "GET"
                connectTimeout = 8000
                readTimeout = 8000
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty("Referer", "https://www.bilibili.com/video/$sourceId")
                setRequestProperty("Accept", "application/json")
            }
            if (connection.responseCode !in 200..299) {
                return ViewResolveResult(null, DownloadErrorCode.VIEW_API_FAILED, "view api http ${connection.responseCode}")
            }
            connection.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                val root = JsonParser.parseString(reader.readText()).asJsonObject
                val code = root.get("code")?.asInt ?: -1
                if (code != 0) {
                    return@use ViewResolveResult(null, DownloadErrorCode.VIEW_API_FAILED, "view api business code $code")
                }
                val data = root.getAsJsonObject("data")
                val cid = data?.get("cid")?.asLong
                if (cid == null) {
                    ViewResolveResult(null, DownloadErrorCode.CID_NOT_FOUND, "cid not found")
                } else {
                    ViewResolveResult(cid, null, null)
                }
            }
        } catch (ex: Exception) {
            ViewResolveResult(null, DownloadErrorCode.VIEW_API_FAILED, ex.message)
        }
    }

    private fun fetchPlayableUrl(sourceId: String, cid: Long): PlayUrlResolveResult {
        val hasLogin = !authManager?.getCookieHeader().isNullOrBlank()
        val preferredQn = if (hasLogin) 112 else 80
        val endpoint = "https://api.bilibili.com/x/player/playurl?bvid=$sourceId&cid=$cid&qn=$preferredQn&fnval=16&fourk=1"
        return try {
            val connection = openConnection(endpoint).apply {
                requestMethod = "GET"
                connectTimeout = 8000
                readTimeout = 8000
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty("Referer", "https://www.bilibili.com/video/$sourceId")
                setRequestProperty("Accept", "application/json")
            }
            if (connection.responseCode !in 200..299) {
                return PlayUrlResolveResult.failure("playurl api http ${connection.responseCode}")
            }
            connection.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                val root = JsonParser.parseString(reader.readText()).asJsonObject
                val code = root.get("code")?.asInt ?: -1
                if (code != 0) {
                    return@use PlayUrlResolveResult.failure("playurl api business code $code")
                }
                val data = root.getAsJsonObject("data")
                    ?: return@use PlayUrlResolveResult.failure("playurl data missing")

                val dash = data.getAsJsonObject("dash")
                if (dash != null) {
                    val dashVideo = dash.getAsJsonArray("video").pickDashStream()
                    val dashAudio = dash.getAsJsonArray("audio").pickDashStream()
                    val dashVideoUrl = dashVideo?.stringValue("baseUrl") ?: dashVideo?.stringValue("base_url")
                    val dashAudioUrl = dashAudio?.stringValue("baseUrl") ?: dashAudio?.stringValue("base_url")
                    if (!dashVideoUrl.isNullOrBlank() && !dashAudioUrl.isNullOrBlank()) {
                        return@use PlayUrlResolveResult(
                            mediaUrl = null,
                            mediaBackupUrls = emptyList(),
                            dashVideoUrl = dashVideoUrl,
                            dashVideoBackupUrls = dashVideo?.stringList("backupUrl") ?: dashVideo?.stringList("backup_url") ?: emptyList(),
                            dashAudioUrl = dashAudioUrl,
                            dashAudioBackupUrls = dashAudio?.stringList("backupUrl") ?: dashAudio?.stringList("backup_url") ?: emptyList(),
                            errorCode = null,
                            errorMessage = null,
                        )
                    }
                }

                val durlItem = data.getAsJsonArray("durl")?.firstOrNullObject()
                val mediaUrl = durlItem?.stringValue("url")
                if (mediaUrl.isNullOrBlank()) {
                    return@use PlayUrlResolveResult.failure("media url missing")
                }
                PlayUrlResolveResult(
                    mediaUrl = mediaUrl,
                    mediaBackupUrls = durlItem.stringList("backup_url"),
                    dashVideoUrl = null,
                    dashVideoBackupUrls = emptyList(),
                    dashAudioUrl = null,
                    dashAudioBackupUrls = emptyList(),
                    errorCode = null,
                    errorMessage = null,
                )
            }
        } catch (ex: Exception) {
            PlayUrlResolveResult.failure(ex.message ?: "playurl api failed")
        }
    }

    private fun inferExtension(mediaUrl: String): String {
        return when {
            mediaUrl.contains(".mp4", ignoreCase = true) -> "mp4"
            else -> "unknown"
        }
    }

    private fun openConnection(endpoint: String): HttpURLConnection {
        return (URL(endpoint).openConnection() as HttpURLConnection).apply {
            authManager?.getCookieHeader()?.takeIf { it.isNotBlank() }?.let { setRequestProperty("Cookie", it) }
        }
    }

    companion object {
        private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0 Safari/537.36"
    }
}

data class ResolvedMedia(
    val sourceId: String,
    val mediaMode: String,
    val mediaUrl: String? = null,
    val mediaBackupUrls: List<String> = emptyList(),
    val videoUrl: String? = null,
    val videoBackupUrls: List<String> = emptyList(),
    val audioUrl: String? = null,
    val audioBackupUrls: List<String> = emptyList(),
    val mediaType: String,
    val extension: String,
    val audioExtension: String? = null,
)

data class ResolveMediaResult(
    val media: ResolvedMedia?,
    val errorCode: String?,
    val errorMessage: String?,
) {
    companion object {
        fun success(media: ResolvedMedia): ResolveMediaResult = ResolveMediaResult(media = media, errorCode = null, errorMessage = null)
        fun failure(errorCode: String, message: String): ResolveMediaResult = ResolveMediaResult(media = null, errorCode = errorCode, errorMessage = message)
    }
}

data class ViewResolveResult(
    val cid: Long?,
    val errorCode: String?,
    val errorMessage: String?,
)

data class PlayUrlResolveResult(
    val mediaUrl: String?,
    val mediaBackupUrls: List<String>,
    val dashVideoUrl: String?,
    val dashVideoBackupUrls: List<String>,
    val dashAudioUrl: String?,
    val dashAudioBackupUrls: List<String>,
    val errorCode: String?,
    val errorMessage: String?,
) {
    companion object {
        fun failure(message: String): PlayUrlResolveResult = PlayUrlResolveResult(
            mediaUrl = null,
            mediaBackupUrls = emptyList(),
            dashVideoUrl = null,
            dashVideoBackupUrls = emptyList(),
            dashAudioUrl = null,
            dashAudioBackupUrls = emptyList(),
            errorCode = DownloadErrorCode.PLAYURL_API_FAILED,
            errorMessage = message,
        )
    }
}

private fun JsonArray?.pickDashStream(): JsonObject? {
    if (this == null || size() == 0) return null
    var best: JsonObject? = null
    for (index in 0 until size()) {
        val item = get(index)
        if (item != null && item.isJsonObject) {
            val obj = item.asJsonObject
            val currentId = obj.get("id")?.asInt ?: 0
            val bestId = best?.get("id")?.asInt ?: 0
            if (best == null || currentId > bestId) best = obj
        }
    }
    return best
}

private fun JsonArray.firstOrNullObject(): JsonObject? {
    if (size() == 0) return null
    val first = get(0)
    return if (first == null || !first.isJsonObject) null else first.asJsonObject
}

private fun JsonObject.stringValue(key: String): String? {
    val value = get(key) ?: return null
    return if (value.isJsonNull) null else value.asString
}

private fun JsonObject.stringList(key: String): List<String> {
    val array = getAsJsonArray(key) ?: return emptyList()
    val result = ArrayList<String>(array.size())
    for (index in 0 until array.size()) {
        val item = array[index]
        if (item != null && item.isJsonPrimitive) {
            val value = item.asString
            if (value.isNotBlank()) result += value
        }
    }
    return result
}
