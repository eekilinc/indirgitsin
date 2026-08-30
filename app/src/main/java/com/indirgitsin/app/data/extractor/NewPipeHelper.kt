package com.indirgitsin.app.data.extractor

import com.indirgitsin.app.data.model.StreamOption
import com.indirgitsin.app.data.model.VideoInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeStreamLinkHandlerFactory
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem

object NewPipeHelper {

    init {
        try { NewPipe.init(DownloaderImpl.getInstance()) } catch (_: Exception) {}
    }

    suspend fun extract(videoId: String): VideoInfo? = withContext(Dispatchers.IO) {
        try {
            val service = ServiceList.YouTube
            val linkHandler = service.streamLHFactory.fromId(videoId)
            val extractor = service.getStreamExtractor(linkHandler)
            extractor.fetchPage()

            val title = extractor.name ?: "Bilinmeyen Başlık"
            val author = extractor.uploaderName ?: "Bilinmeyen Kanal"
            val thumb = extractor.thumbnails.maxByOrNull { it.height }?.url ?: "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"
            val duration = extractor.length ?: 0L
            // viewCount NewPipe'da -1 olabilir
            // stream infos
            val streams = mutableListOf<StreamOption>()
            // 1) Muxed (video+ses birlikte) - genelde 360p/720p
            val videoStreams = extractor.videoStreams ?: emptyList()
            for (vs in videoStreams) {
                val url = vs.content ?: vs.url ?: continue
                if (url.isBlank()) continue
                val quality = vs.resolution ?: ""
                val ext = when {
                    vs.getFormat()?.name?.contains("WEBM", true) == true -> "webm"
                    else -> "mp4"
                }
                streams.add(StreamOption("$quality • ${ext.uppercase()}", ext, quality, url, true, false))
            }
            // 2) Video-only (720p/1080p/4K) - DASH, videoOnlyStreams olmazsa bos
            val videoOnlyStreams = try { extractor.videoOnlyStreams ?: emptyList() } catch (_: Exception) { emptyList() }
            for (vs in videoOnlyStreams) {
                val url = vs.content ?: vs.url ?: continue
                if (url.isBlank()) continue
                val quality = vs.resolution ?: ""
                if (quality.isBlank()) continue
                val ext = when {
                    vs.getFormat()?.name?.contains("WEBM", true) == true -> "webm"
                    else -> "mp4"
                }
                // ayni kalite muxed'de varsa ekleme (distinctBy url zaten var)
                streams.add(StreamOption("$quality • ${ext.uppercase()} (sadece video)", ext, quality, url, true, false))
            }
            // 3) Ses - hem m4a/webm hem mp3 olarak goster (kullanici mp3 bekliyor)
            val audioStreams = extractor.audioStreams ?: emptyList()
            for (as_ in audioStreams) {
                val url = as_.content ?: as_.url ?: continue
                if (url.isBlank()) continue
                val bitrate = as_.averageBitrate
                val baseExt = when {
                    as_.getFormat()?.name?.contains("WEBM", true) == true -> "webm"
                    as_.getFormat()?.name?.contains("MP3", true) == true -> "mp3"
                    else -> "m4a"
                }
                val q = if (bitrate > 0) "${bitrate}kbps" else as_.getFormat()?.name ?: ""
                // orijinal format
                streams.add(StreamOption("Ses • ${baseExt.uppercase()} ${if (bitrate>0) "${bitrate}kbps" else ""}".trim(), baseExt, q, url, false, true, bitrate = bitrate))
                // mp3 secenegi yoksa ekle (ayni url, mp3 etiketli) - kullanici mp3 arıyor
                if (baseExt != "mp3") {
                    val mp3Q = if (bitrate > 0) "${bitrate}kbps" else q.ifBlank { "128kbps" }
                    val mp3Bitrate = if (bitrate > 0) bitrate else 128
                    streams.add(StreamOption("Ses • MP3 $mp3Q", "mp3", mp3Q, url, false, true, bitrate = mp3Bitrate))
                }
            }
            if (streams.isEmpty()) return@withContext null
            // video-only (parantezli) için en iyi sesle mux sentezle
            run {
                val bestAudio = streams.filter { it.isAudioOnly }.maxByOrNull { it.bitrate } ?: streams.filter { it.isAudioOnly }.firstOrNull()
                if (bestAudio != null) {
                    val videoOnly = streams.filter { it.isVideo && it.label.contains("(") && it.audioUrl == null }.toList()
                    for (v in videoOnly) {
                        val q = v.quality.ifBlank { Regex("""(\d+p)""").find(v.label)?.value ?: v.label }
                        if (streams.any { it.quality == q && it.isVideo && !it.label.contains("(") && it.audioUrl == null }) continue
                        if (streams.any { it.quality == q && it.audioUrl == bestAudio.url && it.url == v.url }) continue
                        val cleanLabel = "$q \u2022 ${v.extension.uppercase()}"
                        streams.add(StreamOption(label = cleanLabel, extension = v.extension, quality = q, url = v.url, isVideo = true, isAudioOnly = false, bitrate = v.bitrate, audioUrl = bestAudio.url))
                    }
                }
            }
            val sorted = streams.distinctBy { it.url + (it.audioUrl ?: "") to it.extension }.sortedWith(compareBy<StreamOption> { !it.isVideo }.thenByDescending { extractQualityNumber(it.quality) }.thenByDescending { it.bitrate })
            VideoInfo(videoId, title, author, thumb, duration, 0L, "https://www.youtube.com/watch?v=$videoId", sorted)
        } catch (e: Exception) {
            null
        }
    }

    private fun extractQualityNumber(q: String): Int {
        return Regex("""(\d+)p""").find(q)?.groupValues?.get(1)?.toIntOrNull()
            ?: Regex("""(\d+)""").find(q)?.groupValues?.get(1)?.toIntOrNull() ?: 0
    }
}
