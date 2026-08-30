package com.indirgitsin.app.data.downloader

data class ContentRange(val start: Long, val end: Long, val total: Long) {
    companion object {
        fun parse(value: String): ContentRange? {
            val match = Regex("bytes (\\d+)-(\\d+)/(\\d+)").matchEntire(value.trim()) ?: return null
            val start = match.groupValues[1].toLongOrNull() ?: return null
            val end = match.groupValues[2].toLongOrNull() ?: return null
            val total = match.groupValues[3].toLongOrNull() ?: return null
            return if (start >= 0 && end >= start && total > end) ContentRange(start, end, total) else null
        }
    }
}
