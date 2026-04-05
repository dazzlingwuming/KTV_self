package com.ktv.stb.common.util

import android.text.Html

object TitleSanitizer {
    fun sanitize(raw: String?): String {
        if (raw.isNullOrBlank()) return "Unknown Title"

        val repaired = repairMojibake(raw)
        val withoutTags = Html.fromHtml(repaired, Html.FROM_HTML_MODE_LEGACY).toString()
        return withoutTags
            .replace('\u00A0', ' ')
            .replace(Regex("\\s+"), " ")
            .trim()
            .ifBlank { "Unknown Title" }
    }

    private fun repairMojibake(raw: String): String {
        if (containsChinese(raw)) return raw
        if (!looksLikeMojibake(raw)) return raw

        val latin1Fixed = runCatching { String(raw.toByteArray(Charsets.ISO_8859_1), Charsets.UTF_8) }.getOrNull()
        if (!latin1Fixed.isNullOrBlank() && containsChinese(latin1Fixed)) return latin1Fixed

        val win1252Fixed = runCatching { String(raw.toByteArray(charset("windows-1252")), Charsets.UTF_8) }.getOrNull()
        if (!win1252Fixed.isNullOrBlank() && containsChinese(win1252Fixed)) return win1252Fixed

        return raw
    }

    private fun containsChinese(text: String): Boolean {
        return text.any { it.code in 0x4E00..0x9FFF }
    }

    private fun looksLikeMojibake(text: String): Boolean {
        val mojibakeMarkers = listOf("Ã", "Â", "å", "æ", "ç", "é", "¤", "½")
        return mojibakeMarkers.any { text.contains(it) }
    }
}
