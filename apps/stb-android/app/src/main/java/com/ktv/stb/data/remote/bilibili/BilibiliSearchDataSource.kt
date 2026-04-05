package com.ktv.stb.data.remote.bilibili

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.ktv.stb.common.util.TitleSanitizer
import com.ktv.stb.domain.model.SearchSongItem
import java.io.IOException
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.LinkedHashMap
import java.util.regex.Pattern
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

class BilibiliSearchDataSource {
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

    fun search(keyword: String): List<SearchSongItem> {
        val normalized = keyword.trim()
        if (normalized.isEmpty()) return emptyList()

        return try {
            searchViaApi(normalized)
        } catch (_: Exception) {
            searchViaHtmlFallbacks(normalized)
        }
    }

    private fun searchViaApi(keyword: String): List<SearchSongItem> {
        val encodedKeyword = URLEncoder.encode(keyword, Charsets.UTF_8.name())
        val endpoint = "https://api.bilibili.com/x/web-interface/search/type?search_type=video&keyword=$encodedKeyword&page=1&page_size=20"
        val connection = openConnection(endpoint).apply {
            requestMethod = "GET"
            connectTimeout = 8000
            readTimeout = 8000
            setRequestProperty("User-Agent", USER_AGENT)
            setRequestProperty("Referer", "https://www.bilibili.com/")
            setRequestProperty("Origin", "https://www.bilibili.com")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
            setRequestProperty("X-Requested-With", "XMLHttpRequest")
        }

        val responseCode = connection.responseCode
        if (responseCode !in 200..299) {
            throw IOException("search http $responseCode")
        }

        return connection.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
            val json = JsonParser.parseString(reader.readText()).asJsonObject
            parseResults(json)
        }
    }

    private fun searchViaHtmlFallbacks(keyword: String): List<SearchSongItem> {
        val attempts = listOf(
            "https://www.bilibili.com/search?keyword=%s",
            "https://www.bilibili.com/v/search?keyword=%s",
            "https://search.bilibili.com/all?keyword=%s",
        )
        var lastError: Exception? = null
        for (pattern in attempts) {
            try {
                return searchViaHtml(keyword, pattern)
            } catch (ex: Exception) {
                lastError = ex
            }
        }
        throw lastError ?: IOException("html fallback failed")
    }

    private fun searchViaHtml(keyword: String, pattern: String): List<SearchSongItem> {
        val encodedKeyword = URLEncoder.encode(keyword, Charsets.UTF_8.name())
        val endpoint = pattern.format(encodedKeyword)
        val connection = openConnection(endpoint).apply {
            requestMethod = "GET"
            connectTimeout = 8000
            readTimeout = 8000
            setRequestProperty("User-Agent", USER_AGENT)
            setRequestProperty("Referer", "https://www.bilibili.com/")
            setRequestProperty("Origin", "https://www.bilibili.com")
            setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
        }

        val responseCode = connection.responseCode
        if (responseCode !in 200..299) {
            throw IOException("search html http $responseCode")
        }

        return connection.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
            val html = reader.readText()
            parseResultsFromHtml(html)
        }
    }

    private fun parseResults(root: JsonObject): List<SearchSongItem> {
        val data = root.getAsJsonObject("data") ?: return emptyList()
        val result = data.getAsJsonArray("result") ?: return emptyList()
        return result.mapNotNull { element ->
            val item = element.asJsonObject
            val sourceId = item.stringValue("bvid")
                ?: item.stringValue("id")
                ?: return@mapNotNull null
            val rawTitle = item.stringValue("title") ?: "Unknown Title"
            val title = TitleSanitizer.sanitize(rawTitle)
            val artist = item.stringValue("author")
                ?: item.stringValue("upic")
                ?: "Unknown"
            val duration = parseDuration(item.stringValue("duration"))
            val coverUrl = normalizeCover(item.stringValue("pic"))

            SearchSongItem(
                sourceType = "bilibili",
                sourceId = sourceId,
                title = title,
                artist = artist,
                duration = duration,
                coverUrl = coverUrl,
            )
        }
    }

    private fun parseDuration(raw: String?): Long {
        if (raw.isNullOrBlank()) return 0L
        if (raw.all { it.isDigit() }) return raw.toLongOrNull() ?: 0L
        val parts = raw.split(":").mapNotNull { it.toLongOrNull() }
        return when (parts.size) {
            3 -> parts[0] * 3600 + parts[1] * 60 + parts[2]
            2 -> parts[0] * 60 + parts[1]
            1 -> parts[0]
            else -> 0L
        }
    }

    private fun normalizeCover(raw: String?): String? {
        return when {
            raw.isNullOrBlank() -> null
            raw.startsWith("//") -> "https:$raw"
            raw.startsWith("http") -> raw
            else -> "https://$raw"
        }
    }

    private fun parseResultsFromHtml(html: String): List<SearchSongItem> {
        val initialState = extractInitialStateJson(html)
        if (initialState != null) {
            val root = JsonParser.parseString(initialState).asJsonObject
            val collected = LinkedHashMap<String, SearchSongItem>()
            collectCandidateItems(root, collected)
            if (collected.isNotEmpty()) return collected.values.take(20)
        }

        val regexResults = parseResultsWithRegex(html)
        if (regexResults.isNotEmpty()) return regexResults

        throw IOException("initial state missing")
    }

    private fun extractInitialStateJson(html: String): String? {
        val markers = listOf("window.__INITIAL_STATE__=", "__INITIAL_STATE__=")
        for (marker in markers) {
            val start = html.indexOf(marker)
            if (start < 0) continue
            val from = start + marker.length
            val endCandidates = listOf(";(function", "</script>", ";\n")
            var end = -1
            for (candidate in endCandidates) {
                val idx = html.indexOf(candidate, from)
                if (idx > from) {
                    end = idx
                    break
                }
            }
            if (end > from) {
                return html.substring(from, end).trim()
            }
        }
        return null
    }

    private fun collectCandidateItems(element: JsonElement?, sink: LinkedHashMap<String, SearchSongItem>) {
        when {
            element == null || element.isJsonNull -> return
            element.isJsonArray -> {
                val array = element.asJsonArray
                for (index in 0 until array.size()) {
                    collectCandidateItems(array[index], sink)
                }
            }
            element.isJsonObject -> {
                val obj = element.asJsonObject
                val sourceId = obj.stringValue("bvid")
                val title = obj.stringValue("title")
                if (!sourceId.isNullOrBlank() && !title.isNullOrBlank() && !sink.containsKey(sourceId)) {
                    sink[sourceId] = SearchSongItem(
                        sourceType = "bilibili",
                        sourceId = sourceId,
                        title = TitleSanitizer.sanitize(title),
                        artist = obj.stringValue("author") ?: obj.stringValue("up_name") ?: obj.stringValue("uname") ?: "Unknown",
                        duration = parseDuration(obj.stringValue("duration")),
                        coverUrl = normalizeCover(obj.stringValue("pic") ?: obj.stringValue("cover")),
                    )
                }
                obj.entrySet().forEach { (_, value) -> collectCandidateItems(value, sink) }
            }
        }
    }

    private fun parseResultsWithRegex(html: String): List<SearchSongItem> {
        val normalizedHtml = html.replace("\\u002F", "/")
        val results = LinkedHashMap<String, SearchSongItem>()

        val videoPattern = Pattern.compile(
            "(BV[0-9A-Za-z]{10})",
            Pattern.CASE_INSENSITIVE,
        )
        val titlePattern = Pattern.compile(
            "title=\"([^\"]{1,200})\"",
            Pattern.CASE_INSENSITIVE,
        )
        val coverPattern = Pattern.compile(
            "(https?:)?//[^\"']+(?:jpg|jpeg|png|webp)",
            Pattern.CASE_INSENSITIVE,
        )

        val bvMatcher = videoPattern.matcher(normalizedHtml)
        while (bvMatcher.find() && results.size < 20) {
            val sourceId = bvMatcher.group(1) ?: continue
            if (results.containsKey(sourceId)) continue

            val windowStart = (bvMatcher.start() - 500).coerceAtLeast(0)
            val windowEnd = (bvMatcher.end() + 1500).coerceAtMost(normalizedHtml.length)
            val snippet = normalizedHtml.substring(windowStart, windowEnd)

            val rawTitle = titlePattern.matcher(snippet).let { matcher ->
                if (matcher.find()) matcher.group(1) else null
            } ?: continue

            val artist = extractArtist(snippet) ?: "Unknown"
            val duration = parseDuration(extractDuration(snippet))
            val coverUrl = coverPattern.matcher(snippet).let { matcher ->
                if (matcher.find()) normalizeCover(matcher.group()) else null
            }

            results[sourceId] = SearchSongItem(
                sourceType = "bilibili",
                sourceId = sourceId,
                title = TitleSanitizer.sanitize(rawTitle),
                artist = TitleSanitizer.sanitize(artist),
                duration = duration,
                coverUrl = coverUrl,
            )
        }

        return results.values.toList()
    }

    private fun extractQuotedValue(snippet: String, keys: List<String>): String? {
        for (key in keys) {
            val directMarkers = listOf("\"$key\":\"", "\"$key\": \"", "$key\":\"", "$key\": \"")
            for (marker in directMarkers) {
                val start = snippet.indexOf(marker)
                if (start >= 0) {
                    val from = start + marker.length
                    val end = snippet.indexOf('"', from)
                    if (end > from) {
                        return snippet.substring(from, end)
                    }
                }
            }
        }
        return null
    }

    private fun extractArtist(snippet: String): String? {
        return extractQuotedValue(snippet, listOf("author", "up_name", "uname", "name", "upName"))
            ?.takeIf { it.isNotBlank() }
    }

    private fun extractDuration(snippet: String): String? {
        val direct = extractQuotedValue(snippet, listOf("duration", "length", "play_time"))
        if (!direct.isNullOrBlank()) return direct

        val numericPattern = Pattern.compile(
            "\"(?:duration|length|play_time)\"\\s*:\\s*([0-9]{1,5})",
            Pattern.CASE_INSENSITIVE,
        )
        val numericMatcher = numericPattern.matcher(snippet)
        if (numericMatcher.find()) {
            return numericMatcher.group(1)
        }

        val textPattern = Pattern.compile(
            "([0-9]{1,2}:[0-9]{2}(?::[0-9]{2})?)",
            Pattern.CASE_INSENSITIVE,
        )
        val textMatcher = textPattern.matcher(snippet)
        if (textMatcher.find()) {
            return textMatcher.group(1)
        }

        return null
    }

    private fun openConnection(endpoint: String): HttpURLConnection {
        val connection = URL(endpoint).openConnection()
        if (connection is HttpsURLConnection) {
            connection.sslSocketFactory = unsafeSslSocketFactory
            connection.hostnameVerifier = unsafeHostnameVerifier
        }
        return connection as HttpURLConnection
    }

    companion object {
        private const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0 Safari/537.36"
    }
}

private fun JsonObject.stringValue(key: String): String? {
    val value = get(key) ?: return null
    return if (value.isJsonNull) null else value.asString
}
