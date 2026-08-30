package com.indirgitsin.app.data.extractor

import com.indirgitsin.app.data.model.StreamOption
import com.indirgitsin.app.data.model.VideoInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.VideoStream

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
            
            // MediaMuxer için en uyumlu AAC / M4A ses akışını seç (itag 140 / mp4a)
            val bestAacAudio: AudioStream? = rawAudioStreams.filter { as_ ->
                val fmt = as_.getFormat()?.name ?: ""
                val suffix = as_.getFormat()?.suffix ?: ""
                val codec = as_.codec ?: ""
                fmt.contains("M4A", true) || suffix.equals("m4a", true) || codec.contains("mp4a", true) || as_.itag == 140
            }.maxByOrNull { it.averageBitrate } ?: rawAudioStreams.firstOrNull { as_ ->
                !(as_.getFormat()?.name?.contains("WEBM", true) == true)
            } ?: rawAudioStreams.firstOrNull()

            for (as_ in rawAudioStreams) {
                val url = as_.content ?: as_.url ?: continue
                if (url.isBlank()) continue
                val bitrate = as_.averageBitrate
                val fmtName = as_.getFormat()?.name ?: ""
                val suffix = as_.getFormat()?.suffix ?: ""
                val isAac = fmtName.contains("M4A", true) || suffix.equals("m4a", true) || as_.codec?.contains("mp4a", true) == true
                val baseExt = if (isAac) "m4a" else "webm"
                val q = if (bitrate > 0) "${bitrate}kbps" else fmtName

                streams.add(StreamOption("Ses • ${baseExt.uppercase()} ${if (bitrate > 0) "${bitrate}kbps" else ""}".trim(), baseExt, q, url, false, true, bitrate = bitrate))
                
                // Kullanıcı için MP3 etiketli alternatif ekle
                val mp3Q = if (bitrate > 0) "${bitrate}kbps" else q.ifBlank { "128kbps" }
                val mp3Bitrate = if (bitrate > 0) bitrate else 128
                streams.add(StreamOption("Ses • MP3 $mp3Q", "mp3", mp3Q, url, false, true, bitrate = mp3Bitrate))
            }

            val aacAudioUrl = bestAacAudio?.content ?: bestAacAudio?.url

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

            // 3) DASH Video-Only Akışları (1080p, 720p, 480p, 360p vb.)
            val videoOnlyStreams = try { extractor.videoOnlyStreams ?: emptyList() } catch (_: Exception) { emptyList() }
            
            // H.264 / AVC video akışlarını önceliklendir (MediaMuxer için %100 uyumlu)
            val h264VideoStreams = videoOnlyStreams.filter { vs ->
                val fmt = vs.getFormat()?.name ?: ""
                val suffix = vs.getFormat()?.suffix ?: ""
                val codec = vs.codec ?: ""
                fmt.contains("MPEG_4", true) || suffix.equals("mp4", true) || codec.startsWith("avc", true) || vs.itag in listOf(137, 136, 135, 134, 133, 160)
            }
            
            // Eğer H264 listesi boşsa tüm video akışlarını kullan
            val targetVideoStreams = if (h264VideoStreams.isNotEmpty()) h264VideoStreams else videoOnlyStreams

            for (vs in targetVideoStreams) {
                val url = vs.content ?: vs.url ?: continue
                if (url.isBlank()) continue
                val quality = vs.resolution ?: ""
                if (quality.isBlank()) continue

                val cleanLabel = "$quality • MP4"
                // Aynı kalite ve uzantı zaten listede yoksa ekle
                if (streams.none { it.quality == quality && it.isVideo && it.audioUrl == aacAudioUrl }) {
                    streams.add(
                        StreamOption(
                            label = cleanLabel,
                            extension = "mp4",
                            quality = quality,
                            url = url,
                            isVideo = true,
                            isAudioOnly = false,
                            bitrate = vs.bitrate,
                            audioUrl = aacAudioUrl
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
