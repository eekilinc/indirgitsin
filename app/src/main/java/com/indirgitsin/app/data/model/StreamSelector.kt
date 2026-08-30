package com.indirgitsin.app.data.model

/** Resolution does not describe track layout: 360p may also be video-only. */
object StreamSelector {
    fun prepare(streams: List<StreamOption>, sdkInt: Int): List<StreamOption> {
        val audio = streams.filter { it.isAudioOnly && it.url.isNotBlank() }
        return streams.mapNotNull { stream ->
            if (stream.url.isBlank()) return@mapNotNull null
            if (stream.codec.startsWith("av01") && sdkInt < 34) return@mapNotNull null
            if (!stream.isVideoOnly) return@mapNotNull stream.takeIf { it.isDownloadable }
            val compatible = audio.filter { candidate ->
                when (stream.extension) {
                    "mp4" -> candidate.extension == "m4a" &&
                        (candidate.codec.isBlank() || candidate.codec.startsWith("mp4a"))
                    "webm" -> candidate.extension == "webm" &&
                        (sdkInt >= 29 || candidate.codec.contains("vorbis", true))
                    else -> false
                }
            }.maxByOrNull { it.bitrate } ?: return@mapNotNull null
            stream.copy(audioUrl = compatible.url, audioCodec = compatible.codec,
                label = "${stream.quality} • ${stream.extension.uppercase()} • Video + Ses")
        }.distinctBy { listOf(it.url, it.audioUrl, it.extension) }
            .sortedWith(compareBy<StreamOption> { !it.isVideo }
                .thenByDescending { qualityNumber(it.quality) }
                .thenBy { if (it.codec.startsWith("avc")) 0 else 1 }
                .thenByDescending { it.bitrate })
    }

    fun preferred(streams: List<StreamOption>, highQuality: Boolean, audioFormat: String, audioOnly: Boolean = false): StreamOption? {
        val videos = streams.filter { it.isVideo && it.isDownloadable }
        if (!audioOnly && videos.isNotEmpty()) {
            val ordered = videos.sortedByDescending { qualityNumber(it.quality) }
            return if (highQuality) ordered.first() else ordered.firstOrNull { qualityNumber(it.quality) <= 720 } ?: ordered.last()
        }
        return streams.filter { it.isAudioOnly && it.isDownloadable }
            .sortedWith(compareByDescending<StreamOption> { it.extension.equals(audioFormat, true) }.thenByDescending { it.bitrate })
            .firstOrNull()
    }

    fun qualityNumber(value: String): Int = Regex("(\\d+)p").find(value)?.groupValues?.get(1)?.toIntOrNull() ?: 0
}
