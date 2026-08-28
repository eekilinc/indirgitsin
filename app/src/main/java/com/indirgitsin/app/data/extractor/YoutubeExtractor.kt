package com.indirgitsin.app.data.extractor

import com.indirgitsin.app.data.model.StreamOption
import com.indirgitsin.app.data.model.VideoInfo
import com.indirgitsin.app.util.YoutubeLinkHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeStreamLinkHandlerFactory
import org.schabi.newpipe.extractor.stream.StreamInfo

object YoutubeExtractor {

    init {
        try {
            NewPipe.init(DownloaderImpl.getInstance(), null)
        } catch (_: Exception) {}
    }

    suspend fun extract(url: String): Result<VideoInfo> = withContext(Dispatchers.IO) {
        try {
            val normalized = YoutubeLinkHelper.normalizeUrl(url)
            val videoId = YoutubeLinkHelper.extractVideoId(normalized)
                ?: return@withContext Result.failure(Exception("Geçersiz YouTube linki"))

            val service = ServiceList.YouTube
            val linkHandler = YoutubeStreamLinkHandlerFactory.getInstance().fromUrl(normalized)
            val extractor = service.getStreamExtractor(linkHandler)
            extractor.fetchPage()

            val title = extractor.name ?: "Bilinmeyen Başlık"
            val author = extractor.uploaderName ?: "Bilinmeyen Kanal"
            val thumb = extractor.thumbnails.maxByOrNull { it.height * it.width }?.url ?: ""
            val duration = extractor.length
            val viewCount = extractor.viewCount

            val streams = mutableListOf<StreamOption>()

            // Video + Audio muxed
            extractor.videoStreams?.forEach { vs ->
                streams.add(
                    StreamOption(
                        label = "${vs.resolution} • ${vs.getFormat()?.name ?: "MP4"}",
                        extension = vs.getFormat()?.suffix ?: "mp4",
                        quality = vs.resolution ?: "",
                        url = vs.content ?: "",
                        isVideo = true,
                        isAudioOnly = false
                    )
                )
            }
            // Audio only
            extractor.audioStreams?.forEach { a ->
                val br = a.averageBitrate
                streams.add(
                    StreamOption(
                        label = "Ses • ${a.getFormat()?.name ?: "M4A"} ${if (br>0) "${br}kbps" else ""}",
                        extension = a.getFormat()?.suffix ?: "m4a",
                        quality = "${br}kbps",
                        url = a.content ?: "",
                        isVideo = false,
                        isAudioOnly = true,
                        bitrate = br
                    )
                )
            }

            // Fallback: eğer extractor boşsa StreamInfo kullan
            if (streams.isEmpty()) {
                try {
                    val info = StreamInfo.getInfo(service, linkHandler)
                    info.videoStreams.forEach { vs ->
                        streams.add(StreamOption("${vs.resolution}", vs.getFormat()?.suffix ?: "mp4", vs.resolution ?: "", vs.content ?: "", true, false))
                    }
                    info.audioStreams.forEach { a ->
                        streams.add(StreamOption("Ses ${a.averageBitrate}kbps", a.getFormat()?.suffix ?: "m4a", "${a.averageBitrate}", a.content ?: "", false, true, bitrate = a.averageBitrate))
                    }
                } catch (_: Exception) {}
            }

            // MP3 seçeneği için en iyi audio'yu mp3 gibi göster (dönüşüm sunucuda değil, cihazda m4a olarak iner)
            if (streams.none { it.isAudioOnly }) {
                // nothing
            }

            val video = VideoInfo(
                id = videoId,
                title = title,
                author = author,
                thumbnailUrl = thumb,
                durationSeconds = duration,
                viewCount = viewCount,
                url = normalized,
                streams = streams.distinctBy { it.url }.sortedWith(compareBy({ !it.isVideo }, { -it.bitrate }))
            )
            Result.success(video)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
