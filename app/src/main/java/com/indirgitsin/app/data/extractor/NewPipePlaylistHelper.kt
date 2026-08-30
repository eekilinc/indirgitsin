package com.indirgitsin.app.data.extractor

import com.indirgitsin.app.data.model.PlaylistInfo
import com.indirgitsin.app.data.model.PlaylistVideo
import com.indirgitsin.app.util.YoutubeLinkHelper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.ServiceList

object NewPipePlaylistHelper {
    suspend fun extract(playlistId: String): PlaylistInfo? = withContext(Dispatchers.IO) {
        try {
            NewPipeHelper.ensureInitialized()
            val service = ServiceList.YouTube
            val extractor = service.getPlaylistExtractor(service.playlistLHFactory.fromUrl("https://www.youtube.com/playlist?list=$playlistId"))
            extractor.fetchPage()
            var page = extractor.initialPage
            val videos = linkedMapOf<String, PlaylistVideo>()
            var pageCount = 0
            while (true) {
                currentCoroutineContext().ensureActive()
                for (item in page.items) {
                    val id = YoutubeLinkHelper.extractVideoId(item.url) ?: continue
                    videos[id] = PlaylistVideo(id, item.name, item.thumbnails.maxByOrNull { it.height }?.url.orEmpty(), item.duration)
                }
                if (!page.hasNextPage()) break
                check(++pageCount < 100) { "Çalma listesi çok uzun veya sonsuz bir radyo listesi." }
                page = extractor.getPage(page.nextPage)
            }
            PlaylistInfo(playlistId, extractor.name, extractor.uploaderName,
                extractor.thumbnails.maxByOrNull { it.height }?.url.orEmpty(), videos.values.toList(), videos.size)
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { null }
    }
}
