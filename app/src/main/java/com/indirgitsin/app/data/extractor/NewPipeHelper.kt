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

            val streams = mutableListOf<StreamOption>()

            // 1) Ses Akışlarını Topla (M4A / AAC ses akışını bul)
            val rawAudioStreams = extractor.audioStreams ?: emptyList()
            for (as_ in rawAudioStreams) {
                val url = as_.content ?: as_.url ?: continue
                if (url.isBlank()) continue
                val bitrate = as_.averageBitrate
                val fmtName = as_.getFormat()?.name ?: ""
                val baseExt = when {
                    fmtName.contains("WEBM", true) || fmtName.contains("OPUS", true) -> "webm"
                    fmtName.contains("MP3", true) -> "mp3"
                    else -> "m4a"
                }
                val q = if (bitrate > 0) "${bitrate}kbps" else fmtName
                streams.add(StreamOption("Ses • ${baseExt.uppercase()} ${if (bitrate > 0) "${bitrate}kbps" else ""}".trim(), baseExt, q, url, false, true, bitrate = bitrate))
                if (baseExt != "mp3") {
                    val mp3Q = if (bitrate > 0) "${bitrate}kbps" else q.ifBlank { "128kbps" }
                    val mp3Bitrate = if (bitrate > 0) bitrate else 128
                    streams.add(StreamOption("Ses • MP3 $mp3Q", "mp3", mp3Q, url, false, true, bitrate = mp3Bitrate))
                }
            }

            // En iyi M4A/AAC ses akışı (MediaMuxer MP4 uyumlu)
            val bestM4aAudio = streams.filter { it.isAudioOnly && it.extension == "m4a" }.maxByOrNull { it.bitrate }
                ?: streams.filter { it.isAudioOnly }.maxByOrNull { it.bitrate }

            // 2) YouTube'un Doğrudan Sesli Video Akışları (Legacy Muxed - Genelde 360p / 720p)
            val legacyVideoStreams = extractor.videoStreams ?: emptyList()
            for (vs in legacyVideoStreams) {
                val url = vs.content ?: vs.url ?: continue
                if (url.isBlank()) continue
                val quality = vs.resolution ?: ""
                if (quality.isBlank()) continue
                val fmtName = vs.getFormat()?.name ?: ""
                val ext = if (fmtName.contains("WEBM", true)) "webm" else "mp4"
                streams.add(StreamOption("$quality • ${ext.uppercase()}", ext, quality, url, true, false, bitrate = vs.bitrate))
            }

            // 3) DASH Video-Only Akışları (1080p, 720p, 480p vb.) -> Otomatik En İyi Ses ile Birleştir
            val videoOnlyStreams = try { extractor.videoOnlyStreams ?: emptyList() } catch (_: Exception) { emptyList() }
            for (vs in videoOnlyStreams) {
                val url = vs.content ?: vs.url ?: continue
                if (url.isBlank()) continue
                val quality = vs.resolution ?: ""
                if (quality.isBlank()) continue
                val fmtName = vs.getFormat()?.name ?: ""
                val isWebm = fmtName.contains("WEBM", true)
                val ext = if (isWebm) "webm" else "mp4"

                // Eğer MP4 uyumlu video ise ve sesimiz varsa, sesli olarak mux seçeneği ekle
                if (!isWebm && bestM4aAudio != null) {
                    val cleanLabel = "$quality • MP4"
                    // Aynı kalite zaten inherent legacy muxed'de yoksa veya daha yüksek bitrate ise
                    if (streams.none { it.quality == quality && it.isVideo && it.audioUrl == null && it.extension == "mp4" }) {
                        streams.add(
                            StreamOption(
                                label = cleanLabel,
                                extension = "mp4",
                                quality = quality,
                                url = url,
                                isVideo = true,
                                isAudioOnly = false,
                                bitrate = vs.bitrate,
                                audioUrl = bestM4aAudio.url
                            )
                        )
                    }
                } else if (isWebm && bestM4aAudio != null) {
                    // WebM video için de ses bağla
                    streams.add(
                        StreamOption(
                            label = "$quality • WEBM",
                            extension = "webm",
                            quality = quality,
                            url = url,
                            isVideo = true,
                            isAudioOnly = false,
                            bitrate = vs.bitrate,
                            audioUrl = bestM4aAudio.url
                        )
                    )
                }
            }

            if (streams.isEmpty()) return@withContext null

            // Tekilleştir ve sırala: Videolar önce (yüksek çözünürlükten düşüğe), sonra sesler
            val sorted = streams.distinctBy { (it.quality + it.extension + (it.audioUrl ?: "")) to it.isVideo }
                .sortedWith(
                    compareBy<StreamOption> { !it.isVideo }
                        .thenByDescending { extractQualityNumber(it.quality) }
                        .thenByDescending { it.bitrate }
                )

            VideoInfo(videoId, title, author, thumb, duration, 0L, "https://www.youtube.com/watch?v=$videoId", sorted)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun extractQualityNumber(q: String): Int {
        return Regex("""(\d+)p""").find(q)?.groupValues?.get(1)?.toIntOrNull()
            ?: Regex("""(\d+)""").find(q)?.groupValues?.get(1)?.toIntOrNull() ?: 0
    }
}
