package com.indirgitsin.app.data.extractor

import com.indirgitsin.app.data.model.StreamOption
import com.indirgitsin.app.data.model.VideoInfo
import com.indirgitsin.app.util.YoutubeLinkHelper
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object YoutubeExtractor {

    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    private val pipedInstances = listOf(
        "https://pipedapi.kavin.rocks",
        "https://pipedapi.adminforge.de",
        "https://piped-api.garudalinux.org",
        "https://api.piped.projectsegfau.lt"
    )

    // Cache - aynı videoyu tekrar çözme
    private val cache = mutableMapOf<String, VideoInfo>()
    private val cacheTime = mutableMapOf<String, Long>()
    private const val CACHE_TTL_MS = 10 * 60 * 1000L

    suspend fun extract(url: String): Result<VideoInfo> = withContext(Dispatchers.IO) {
        try {
            val normalized = YoutubeLinkHelper.normalizeUrl(url)
            val videoId = YoutubeLinkHelper.extractVideoId(normalized)
                ?: return@withContext Result.failure(Exception("Geçersiz YouTube linki"))

            // Cache hit?
            synchronized(cache) {
                val cached = cache[videoId]
                val t = cacheTime[videoId] ?: 0
                if (cached != null && System.currentTimeMillis() - t < CACHE_TTL_MS) {
                    return@withContext Result.success(cached)
                }
            }

            val pipedResult = tryPipedParallel(videoId)
            if (pipedResult != null) {
                synchronized(cache) {
                    cache[videoId] = pipedResult
                    cacheTime[videoId] = System.currentTimeMillis()
                }
                return@withContext Result.success(pipedResult)
            }

            val oembed = tryOEmbed(videoId)
            if (oembed != null) {
                return@withContext Result.failure(
                    Exception("Sunucular yoğun, ${oembed.title} için kalite listesi alınamadı. 5 sn sonra tekrar dene.")
                )
            }

            Result.failure(Exception("Video bilgileri alınamadı. İnternet bağlantını kontrol et."))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Paralel race - ilk başarılı dönen kazanır, 5sn timeout
    private suspend fun tryPipedParallel(videoId: String): VideoInfo? = coroutineScope {
        val deferreds = pipedInstances.map { base ->
            async(Dispatchers.IO) {
                try {
                    withTimeout(5000) { fetchSinglePiped(base, videoId) }
                } catch (_: Exception) { null }
            }
        }
        // İlk başarılı sonucu al, diğerlerini iptal et
        for (d in deferreds) {
            try {
                val res = d.await()
                if (res != null) {
                    deferreds.forEach { if (it != d) it.cancel() }
                    return@coroutineScope res
                }
            } catch (_: Exception) { }
        }
        // Hiçbiri başarılı değilse, ilk non-null'u bekle
        deferreds.forEach { it.await() }
        null
    }

    private fun fetchSinglePiped(base: String, videoId: String): VideoInfo? {
        val req = Request.Builder()
            .url("$base/streams/$videoId")
            .header("User-Agent", "Mozilla/5.0")
            .header("Accept", "application/json")
            .build()
        val resp = client.newCall(req).execute()
        if (!resp.isSuccessful) {
            resp.close()
            return null
        }
        val body = resp.body?.string()
        resp.close()
        if (body.isNullOrBlank()) return null
        val json = JSONObject(body)
        if (json.has("error") || json.has("message") && json.optString("message").contains("not found", true)) return null

        val title = json.optString("title", "Bilinmeyen Başlık")
        if (title.isBlank() || title == "Bilinmeyen Başlık") return null
        val author = json.optString("uploader", json.optString("uploaderName", "Bilinmeyen Kanal"))
        val thumb = json.optString("thumbnailUrl", json.optJSONArray("thumbnails")?.optJSONObject(0)?.optString("url") ?: "https://i.ytimg.com/vi/$videoId/hqdefault.jpg")
        val duration = json.optLong("duration", 0L)
        val views = json.optLong("views", 0L)

        val streams = mutableListOf<StreamOption>()

        val vArr = json.optJSONArray("videoStreams")
        if (vArr != null) {
            for (i in 0 until vArr.length()) {
                val o = vArr.getJSONObject(i)
                val url = o.optString("url", "")
                if (url.isBlank()) continue
                val quality = o.optString("quality", "")
                val mime = o.optString("mimeType", "video/mp4")
                val ext = when {
                    mime.contains("webm") -> "webm"
                    mime.contains("mp4") -> "mp4"
                    else -> "mp4"
                }
                // Sadece muxed (video+audio) olanları öne al - itag 18,22 gibi
                streams.add(StreamOption("$quality • ${ext.uppercase()}", ext, quality, url, true, false))
            }
        }

        val aArr = json.optJSONArray("audioStreams")
        if (aArr != null) {
            for (i in 0 until aArr.length()) {
                val o = aArr.getJSONObject(i)
                val url = o.optString("url", "")
                if (url.isBlank()) continue
                val mime = o.optString("mimeType", "audio/mp4")
                val ext = when {
                    mime.contains("webm") -> "webm"
                    mime.contains("mp4") || mime.contains("m4a") -> "m4a"
                    mime.contains("mp3") -> "mp3"
                    else -> "m4a"
                }
                val bitrate = o.optInt("bitrate", 0)
                val quality = if (bitrate > 0) "${bitrate}kbps" else o.optString("quality", "")
                streams.add(
                    StreamOption(
                        label = "Ses • ${ext.uppercase()} ${if (bitrate > 0) "${bitrate}kbps" else ""}".trim(),
                        extension = ext,
                        quality = quality,
                        url = url,
                        isVideo = false,
                        isAudioOnly = true,
                        bitrate = bitrate
                    )
                )
            }
        }

        val hls = json.optString("hls", "")
        if (streams.isEmpty() && hls.isNotBlank()) {
            streams.add(StreamOption("HLS • M3U8", "m3u8", "auto", hls, true, false))
        }

        if (streams.isEmpty()) return null

        // En iyi kaliteler üste: 1080p > 720p > 480p, seslerde bitrate yüksek
        val sorted = streams.distinctBy { it.url }.sortedWith(
            compareBy<StreamOption> { !it.isVideo }
                .thenByDescending { extractQualityNumber(it.quality) }
                .thenByDescending { it.bitrate }
        )

        return VideoInfo(videoId, title, author, thumb, duration, views, "https://www.youtube.com/watch?v=$videoId", sorted)
    }

    private fun extractQualityNumber(q: String): Int {
        return Regex("""(\d+)p""").find(q)?.groupValues?.get(1)?.toIntOrNull()
            ?: Regex("""(\d+)""").find(q)?.groupValues?.get(1)?.toIntOrNull() ?: 0
    }

    private fun tryOEmbed(videoId: String): VideoInfo? {
        return try {
            val url = "https://www.youtube.com/oembed?url=https://www.youtube.com/watch?v=$videoId&format=json"
            val req = Request.Builder().url(url).header("User-Agent", "Mozilla/5.0").build()
            val resp = client.newCall(req).execute()
            if (!resp.isSuccessful) {
                resp.close()
                return null
            }
            val body = resp.body?.string()
            resp.close()
            if (body.isNullOrBlank()) return null
            val json = JSONObject(body)
            VideoInfo(
                id = videoId,
                title = json.optString("title", "Bilinmeyen Başlık"),
                author = json.optString("author_name", "Bilinmeyen Kanal"),
                thumbnailUrl = json.optString("thumbnail_url", "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"),
                durationSeconds = 0,
                viewCount = 0,
                url = "https://www.youtube.com/watch?v=$videoId",
                streams = emptyList()
            )
        } catch (_: Exception) { null }
    }
}
