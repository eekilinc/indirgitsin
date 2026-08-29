package com.indirgitsin.app.data.extractor

import com.indirgitsin.app.data.model.PlaylistInfo
import com.indirgitsin.app.data.model.PlaylistVideo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubePlaylistLinkHandlerFactory

object NewPipePlaylistHelper {

    suspend fun extract(playlistId: String): PlaylistInfo? = withContext(Dispatchers.IO) {
        try {
            val service = ServiceList.YouTube
            val url = "https://www.youtube.com/playlist?list=$playlistId"
            val linkHandler = service.playlistLHFactory.fromUrl(url)
            val extractor = service.getPlaylistExtractor(linkHandler)
            extractor.fetchPage()
            val title = extractor.name ?: "Çalma Listesi"
            val author = extractor.uploaderName ?: ""
            val thumb = extractor.thumbnails.maxByOrNull { it.height }?.url ?: ""
            val initial = extractor.initialPage
            val items = initial.items ?: emptyList()
            val videos = items.mapNotNull { item ->
                try {
                    // StreamInfoItem
                    val id = when {
                        item.url?.contains("v=") == true -> Regex("[?&]v=([a-zA-Z0-9_-]{11})").find(item.url ?: "")?.groupValues?.get(1)
                        item.url?.contains("youtu.be") == true -> item.url?.substringAfterLast("/")
                        else -> null
                    } ?: return@mapNotNull null
                    val t = item.name ?: "Video"
                    val th = item.thumbnails.maxByOrNull { it.height }?.url ?: ""
                    val dur = (item as? org.schabi.newpipe.extractor.stream.StreamInfoItem)?.duration ?: 0L
                    PlaylistVideo(id, t, th, dur)
                } catch (_: Exception) { null }
            }
            // If no items but extractor has related? Try alternative: use getPage via extractor.getInitialPage
            PlaylistInfo(playlistId, title, author, thumb, videos, videos.size)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
