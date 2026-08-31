package com.indirgitsin.app.data.downloader

import java.net.URI

data class HlsRange(val length: Long, val offset: Long)
data class HlsResource(val url: String, val range: HlsRange? = null)
data class HlsSegment(val sequence: Long, val resource: HlsResource, val seconds: Double,
                      val initialization: HlsResource?, val discontinuity: Boolean, val gap: Boolean)
data class HlsVariant(val url: String, val height: Int, val bandwidth: Long, val codecs: String, val externalAudio: Boolean)
data class HlsPlaylist(val variants: List<HlsVariant>, val segments: List<HlsSegment>,
                       val targetSeconds: Int, val ended: Boolean) {
    fun selectVariant(): HlsVariant = variants.filter {
        !it.externalAudio && (it.codecs.isBlank() ||
            (it.codecs.contains("avc1") && it.codecs.contains("mp4a")))
    }.let { compatible ->
        (compatible.filter { it.height in 1..1080 }.maxByOrNull { it.height * 1_000_000L + it.bandwidth }
            ?: compatible.minByOrNull { it.bandwidth })
            ?: error("Bu yayın ayrı ses/DRM veya desteklenmeyen codec kullanıyor. Sesli AVC/AAC HLS gerekli.")
    }

    companion object {
        fun parse(text: String, baseUrl: String, allowHttp: Boolean = false): HlsPlaylist {
            require(text.length <= 2 * 1024 * 1024) { "HLS listesi çok büyük." }
            val lines = text.removePrefix("\uFEFF").lineSequence().map { it.trim() }.filter { it.isNotBlank() }.toList()
            require(lines.firstOrNull() == "#EXTM3U") { "Geçerli bir HLS listesi bulunamadı." }
            fun resolve(value: String): String {
                val uri = URI(baseUrl).resolve(value)
                require(uri.scheme == "https" || (allowHttp && uri.scheme == "http")) { "Güvenli olmayan HLS bağlantısı." }
                require(!uri.host.isNullOrBlank() && uri.userInfo == null) { "Geçersiz HLS adresi." }
                return uri.toASCIIString()
            }
            val externalGroups = lines.filter { it.startsWith("#EXT-X-MEDIA:") }.map { attributes(it.substringAfter(':')) }
                .filter { it["TYPE"] == "AUDIO" && it.containsKey("URI") }.mapNotNull { it["GROUP-ID"] }.toSet()
            var sequence = 0L
            var duration: Double? = null
            var init: HlsResource? = null
            var rangeText: String? = null
            var previousRange: Pair<String, HlsRange>? = null
            var variant: Map<String, String>? = null
            var discontinuity = false
            var gap = false
            var target = 6
            var ended = false
            val segments = mutableListOf<HlsSegment>()
            val variants = mutableListOf<HlsVariant>()
            for (line in lines.drop(1)) when {
                line.startsWith("#EXT-X-SESSION-KEY:") || line.startsWith("#EXT-X-KEY:") ->
                    require(attributes(line.substringAfter(':'))["METHOD"] == "NONE") { "Şifreli/DRM HLS kaydı desteklenmiyor." }
                line.startsWith("#EXT-X-MEDIA-SEQUENCE:") -> {
                    require(segments.isEmpty())
                    sequence = line.substringAfter(':').toLong().also { require(it >= 0) }
                }
                line.startsWith("#EXT-X-TARGETDURATION:") -> target = line.substringAfter(':').toInt().also { require(it in 1..120) }
                line.startsWith("#EXTINF:") -> {
                    require(duration == null)
                    duration = line.substringAfter(':').substringBefore(',').toDouble().also { require(it.isFinite() && it > 0 && it <= 120) }
                }
                line.startsWith("#EXT-X-STREAM-INF:") -> variant = attributes(line.substringAfter(':'))
                line.startsWith("#EXT-X-BYTERANGE:") -> rangeText = line.substringAfter(':')
                line.startsWith("#EXT-X-MAP:") -> {
                    val attrs = attributes(line.substringAfter(':'))
                    init = HlsResource(resolve(requireNotNull(attrs["URI"])), attrs["BYTERANGE"]?.let { range(it, null) })
                }
                line == "#EXT-X-DISCONTINUITY" -> discontinuity = true
                line == "#EXT-X-GAP" -> gap = true
                line == "#EXT-X-ENDLIST" -> ended = true
                line.startsWith("#EXT-X-SKIP:") -> error("Atlanan parçalı HLS listesi desteklenmiyor.")
                !line.startsWith('#') -> {
                    val url = resolve(line)
                    val attributes = variant
                    if (attributes != null) {
                        variants += HlsVariant(url, attributes["RESOLUTION"]?.substringAfter('x')?.toIntOrNull() ?: 0,
                            attributes["BANDWIDTH"]?.toLongOrNull()?.coerceAtLeast(0) ?: 0,
                            attributes["CODECS"].orEmpty(), attributes["AUDIO"] in externalGroups)
                        variant = null
                    } else {
                        val seconds = requireNotNull(duration) { "HLS parçasının süresi eksik." }
                        val range = rangeText?.let { range(it, previousRange?.takeIf { old -> old.first == url }?.second) }
                        segments += HlsSegment(sequence, HlsResource(url, range), seconds, init, discontinuity, gap)
                        sequence = Math.addExact(sequence, 1)
                        previousRange = range?.let { url to it }
                        duration = null; rangeText = null; discontinuity = false; gap = false
                        require(segments.size <= 5000) { "HLS listesinde çok fazla parça var." }
                    }
                }
            }
            require(duration == null && variant == null) { "HLS listesi kesilmiş." }
            require(variants.isEmpty() || segments.isEmpty()) { "Karışık HLS listesi desteklenmiyor." }
            return HlsPlaylist(variants, segments, target, ended)
        }

        private fun range(text: String, previous: HlsRange?): HlsRange {
            val parts = text.split('@')
            require(parts.size in 1..2)
            val length = parts[0].toLong().also { require(it in 1..64L * 1024 * 1024) }
            val offset = if (parts.size == 2) parts[1].toLong() else
                previous?.let { Math.addExact(it.offset, it.length) } ?: error("HLS parça konumu eksik.")
            require(offset >= 0 && offset <= Long.MAX_VALUE - length)
            return HlsRange(length, offset)
        }

        private fun attributes(text: String): Map<String, String> =
            Regex("([A-Z0-9-]+)=(\"[^\"]*\"|[^,]*)").findAll(text)
                .associate { it.groupValues[1] to it.groupValues[2].removeSurrounding("\"") }
    }
}
