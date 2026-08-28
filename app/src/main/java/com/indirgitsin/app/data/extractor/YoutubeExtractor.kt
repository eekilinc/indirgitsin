package com.indirgitsin.app.data.extractor

import com.indirgitsin.app.data.model.StreamOption
import com.indirgitsin.app.data.model.VideoInfo
import com.indirgitsin.app.util.YoutubeLinkHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object YoutubeExtractor {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val pipedInstances = listOf(
        "https://pipedapi.kavin.rocks",
        "https://pipedapi.adminforge.de",
        "https://piped-api.garudalinux.org",
        "https://api.piped.projectsegfau.lt"
    )

    suspend fun extract(url: String): Result<VideoInfo> = withContext(Dispatchers.IO) {
        try {
            val normalized = YoutubeLinkHelper.normalizeUrl(url)
            val videoId = YoutubeLinkHelper.extractVideoId(normalized)
                ?: return@withContext Result.failure(Exception("Geçersiz YouTube linki"))

            val pipedResult = tryPiped(videoId)
            if (pipedResult != null) {
                return@withContext Result.success(pipedResult)
            }

            val oembed = tryOEmbed(videoId)
            if (oembed != null) {
                return@withContext Result.failure(
                    Exception("Şu an Piped sunucularına ulaşılamıyor. Biraz sonra tekrar dene.\nVideo: ${oembed.title}")
                )
            }

            Result.failure(Exception("Video bilgileri alınamadı. İnternet bağlantını kontrol et veya farklı bir video dene."))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun tryPiped(videoId: String): VideoInfo? {
        for (base in pipedInstances) {
            try {
                val req = Request.Builder()
                    .url("$base/streams/$videoId")
                    .header("User-Agent", "Mozilla/5.0")
                    .build()
                val resp = client.newCall(req).execute()
                if (!resp.isSuccessful) {
                    resp.close()
                    continue
                }
                val body = resp.body?.string()
                resp.close()
                if (body.isNullOrBlank()) continue
                val json = JSONObject(body)
                if (json.has("error")) continue

                val title = json.optString("title", "Bilinmeyen Başlık")
                val author = json.optString("uploader", json.optString("uploaderName", "Bilinmeyen Kanal"))
                val thumb = json.optString("thumbnailUrl", json.optJSONArray("thumbnails")?.optJSONObject(0)?.optString("url") ?: "")
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
                        streams.add(
                            StreamOption(
                                label = "$quality • ${ext.uppercase()}",
                                extension = ext,
                                quality = quality,
                                url = url,
                                isVideo = true,
                                isAudioOnly = false
                            )
                        )
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

                if (streams.isEmpty()) continue

                val sorted = streams.distinctBy { it.url }.sortedWith(
                    compareBy<StreamOption> { !it.isVideo }.thenByDescending { it.bitrate }
                )

                return VideoInfo(
                    id = videoId,
                    title = title,
                    author = author,
                    thumbnailUrl = thumb,
                    durationSeconds = duration,
                    viewCount = views,
                    url = "https://www.youtube.com/watch?v=$videoId",
                    streams = sorted
                )
            } catch (_: Exception) {
                continue
            }
        }
        return null
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
