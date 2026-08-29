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
            val audioStreams = extractor.audioStreams ?: emptyList()
            for (as_ in audioStreams) {
                val url = as_.content ?: as_.url ?: continue
                if (url.isBlank()) continue
                val bitrate = as_.averageBitrate
                val ext = when {
                    as_.getFormat()?.name?.contains("WEBM", true) == true -> "webm"
                    as_.getFormat()?.name?.contains("M4A", true) == true -> "m4a"
                    else -> "m4a"
                }
                val q = if (bitrate > 0) "${bitrate}kbps" else ""
                streams.add(StreamOption("Ses • ${ext.uppercase()} ${if (bitrate>0) "${bitrate}kbps" else ""}".trim(), ext, q, url, false, true, bitrate = bitrate))
            }
            if (streams.isEmpty()) return@withContext null
            val sorted = streams.distinctBy { it.url }.sortedWith(compareBy<StreamOption> { !it.isVideo }.thenByDescending { extractQualityNumber(it.quality) }.thenByDescending { it.bitrate })
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
