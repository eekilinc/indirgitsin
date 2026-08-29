package com.indirgitsin.app.util

import java.util.regex.Pattern

object YoutubeLinkHelper {

    private val patterns = listOf(
        Pattern.compile("(?:youtube\\.com/watch\\?v=|youtu\\.be/|youtube\\.com/embed/|music\\.youtube\\.com/watch\\?v=)([a-zA-Z0-9_-]{11})"),
        Pattern.compile("youtube\\.com/shorts/([a-zA-Z0-9_-]{11})")
    )

    fun extractVideoId(url: String?): String? {
        if (url.isNullOrBlank()) return null
        for (p in patterns) {
            val m = p.matcher(url)
            if (m.find()) return m.group(1)
        }
        // fallback: v= param
        val vParam = Regex("[?&]v=([a-zA-Z0-9_-]{11})").find(url)?.groupValues?.get(1)
        if (vParam != null) return vParam
        return null
    }

    fun extractPlaylistId(url: String?): String? {
        if (url.isNullOrBlank()) return null
        return Regex("[?&]list=([a-zA-Z0-9_-]+)").find(url)?.groupValues?.get(1)
    }

    fun isPlaylistUrl(url: String?): Boolean = extractPlaylistId(url) != null

    fun isValidYoutubeUrl(url: String?): Boolean = extractVideoId(url) != null || isPlaylistUrl(url)

    fun normalizeUrl(url: String): String {
        val id = extractVideoId(url) ?: return url
        return "https://www.youtube.com/watch?v=$id"
    }

    fun findYoutubeUrlInText(text: String?): String? {
        if (text.isNullOrBlank()) return null
        val regex = Regex("""https?://(?:www\.|m\.|music\.)?(?:youtube\.com/watch\?[^ \n]+|youtu\.be/[^ \n]+|youtube\.com/shorts/[^ \n]+|youtube\.com/playlist\?[^ \n]+)""")
        return regex.find(text)?.value
    }

    fun formatDuration(seconds: Long): String {
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
    }
}
