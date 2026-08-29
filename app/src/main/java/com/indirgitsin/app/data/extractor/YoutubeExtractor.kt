package com.indirgitsin.app.data.extractor

import com.indirgitsin.app.data.model.StreamOption
import com.indirgitsin.app.data.model.VideoInfo
import com.indirgitsin.app.util.YoutubeLinkHelper
import kotlinx.coroutines.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object YoutubeExtractor {

    private val client = OkHttpClient.Builder()
        .connectTimeout(7, TimeUnit.SECONDS)
        .readTimeout(7, TimeUnit.SECONDS)
        .build()

    private val pipedInstances = listOf(
        "https://pipedapi.adminforge.de",
        "https://api.piped.projectsegfau.lt",
        "https://piped-api.lunar.icus.cloud",
        "https://pipedapi.in.projectsegfau.lt"
    )

    private val invidiousInstances = listOf(
        "https://inv.nadeko.net",
        "https://invidious.nerdvpn.de",
        "https://vid.puffyan.us",
        "https://inv.n8pjl.ca"
    )

    private val cache = mutableMapOf<String, VideoInfo>()
    private val cacheTime = mutableMapOf<String, Long>()
    private const val CACHE_TTL_MS = 10 * 60 * 1000L
    private const val INNERTUBE_KEY = "AIzaSyAO_FJ2SlqU8Q4STEHLGCilw_Y9_11qcW8"

    suspend fun extract(url: String, context: android.content.Context): Result<VideoInfo> = withContext(Dispatchers.IO) {
        try {
            val normalized = YoutubeLinkHelper.normalizeUrl(url)
            val videoId = YoutubeLinkHelper.extractVideoId(normalized)
                ?: return@withContext Result.failure(Exception("Geçersiz YouTube linki"))

            synchronized(cache) {
                val cached = cache[videoId]
                val t = cacheTime[videoId] ?: 0
                if (cached != null && System.currentTimeMillis() - t < CACHE_TTL_MS) {
                    return@withContext Result.success(cached)
                }
            }

            // Radikal: NewPipe (en guvenilir - tum 3.parti API'ler kapandi) + Cobalt/Piped/Invidious paralel
            val fastResult = coroutineScope {
                val newPipeDef = async { withTimeoutOrNull(15000) { NewPipeHelper.extract(videoId) } }
                val cobaltDef = async { withTimeoutOrNull(7000) { tryCobalt(videoId) } }
                val pipedDef = async { tryPipedParallel(videoId) }
                val invidiousDef = async { tryInvidiousParallel(videoId) }
                // race: ilk basarili sonucu bekle, digerlerini iptal et
                var winner: VideoInfo? = null
                // NewPipe'i once bekle (en guvenilir)
                try {
                    val np = newPipeDef.await()
                    if (np != null) winner = np
                } catch (_: Exception) {}
                if (winner == null) {
                    val jobs = listOf(cobaltDef, pipedDef, invidiousDef)
                    for (d in jobs) {
                        try {
                            val r = d.await()
                            if (r != null && winner == null) winner = r
                        } catch (_: Exception) {}
                    }
                }
                if (winner != null) {
                    listOf(newPipeDef, cobaltDef, pipedDef, invidiousDef).forEach { try { it.cancel() } catch (_: Exception) {} }
                    winner
                } else null
            }
            if (fastResult != null) {
                synchronized(cache) { cache[videoId] = fastResult; cacheTime[videoId] = System.currentTimeMillis() }
                return@withContext Result.success(fastResult)
            }

            // 2) Innertube (4 client) - SABR ise null doner, fallback'e dusmez cunku ustte zaten denedik
            val innertube = withTimeoutOrNull(8000) { tryInnertube(videoId) }
            if (innertube != null) {
                synchronized(cache) { cache[videoId] = innertube; cacheTime[videoId] = System.currentTimeMillis() }
                return@withContext Result.success(innertube)
            }

            // 3) Watch page ytInitialPlayerResponse parse (son care)
            val watchPage = withTimeoutOrNull(7000) { tryWatchPage(videoId) }
            if (watchPage != null) {
                synchronized(cache) { cache[videoId] = watchPage; cacheTime[videoId] = System.currentTimeMillis() }
                return@withContext Result.success(watchPage)
            }

            val oembed = tryOEmbed(videoId)
            if (oembed != null) {
                return@withContext Result.failure(
                    Exception("Kalite listesi alınamadı ama video bulundu: ${oembed.title}\nYouTube bu videoyu geçici olarak kısıtlamış veya yaş sınırlı olabilir. Cobalt ve Piped de yanıt vermedi. 30 sn sonra tekrar dene veya farklı bir video dene.")
                )
            }

            Result.failure(Exception("Video bilgileri alınamadı. İnternet bağlantını kontrol et."))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Innertube - çoklu client dene (ANDROID en iyi, sonra IOS/WEB fallback - cipher sorunu için)
    private suspend fun tryInnertube(videoId: String): VideoInfo? {
        val clients = listOf(
            // ANDROID - genellikle deciphered URL verir
            Triple("ANDROID", "19.09.37", "com.google.android.youtube/19.09.37 (Linux; U; Android 13) gzip"),
            // ANDROID_MUSIC - Shorts ve Music için daha iyi
            Triple("ANDROID_MUSIC", "6.21", "com.google.android.apps.youtube.music/6.21 (Linux; U; Android 13) gzip"),
            // IOS - cipher bypass için çok iyi
            Triple("IOS", "19.09.3", "com.google.ios.youtube/19.09.3 (iPhone14,3; U; CPU iOS 17_2 like Mac OS X)"),
            // WEB - son çare
            Triple("WEB", "2.20240101.00.00", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
        )
        for ((clientName, clientVersion, userAgent) in clients) {
            try {
                val result = tryInnertubeWithClient(videoId, clientName, clientVersion, userAgent)
                if (result != null) return result
            } catch (_: Exception) { continue }
        }
        return null
    }

    private suspend fun tryInnertubeWithClient(videoId: String, clientName: String, clientVersion: String, userAgent: String): VideoInfo? {
        return try {
            val clientJson = JSONObject().apply {
                put("clientName", clientName)
                put("clientVersion", clientVersion)
                put("hl", "tr")
                put("gl", "TR")
                put("utcOffsetMinutes", 180)
                if (clientName == "ANDROID" || clientName == "ANDROID_MUSIC") {
                    put("androidSdkVersion", 30)
                }
                if (clientName == "IOS") {
                    put("deviceModel", "iPhone16,2")
                    put("osName", "iPhone")
                    put("osVersion", "17.2")
                }
            }
            val jsonBody = JSONObject().apply {
                put("videoId", videoId)
                put("context", JSONObject().apply { put("client", clientJson) })
                put("contentCheckOk", true)
                put("racyCheckOk", true)
            }.toString()

            val req = Request.Builder()
                .url("https://www.youtube.com/youtubei/v1/player?key=$INNERTUBE_KEY")
                .header("User-Agent", userAgent)
                .header("Content-Type", "application/json")
                .post(jsonBody.toRequestBody("application/json".toMediaType()))
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

            val playability = json.optJSONObject("playabilityStatus")?.optString("status", "")
            if (playability != null && playability != "OK" && playability.isNotBlank()) {
                // AGE_VERIFICATION_REQUIRED vs UNPLAYABLE vs OK
                if (playability == "LOGIN_REQUIRED" || playability == "UNPLAYABLE") return null
            }

            val videoDetails = json.optJSONObject("videoDetails") ?: return null
            val title = videoDetails.optString("title", "Bilinmeyen Başlık")
            val author = videoDetails.optString("author", "Bilinmeyen Kanal")
            val thumb = run {
                val thumbArray = videoDetails.optJSONArray("thumbnail")?.optJSONObject(0)?.optJSONArray("thumbnails")
                if (thumbArray != null && thumbArray.length() > 0) {
                    var bestUrl = ""
                    var bestW = -1
                    for (i in 0 until thumbArray.length()) {
                        val o = thumbArray.getJSONObject(i)
                        val w = o.optInt("width", 0)
                        if (w > bestW) {
                            bestW = w
                            bestUrl = o.optString("url", "")
                        }
                    }
                    if (bestUrl.isNotBlank()) bestUrl else "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"
                } else {
                    "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"
                }
            }
            val duration = videoDetails.optString("lengthSeconds", "0").toLongOrNull() ?: 0L
            val views = videoDetails.optString("viewCount", "0").toLongOrNull() ?: 0L

            val streamingData = json.optJSONObject("streamingData") ?: return null
            val streams = mutableListOf<StreamOption>()
            val serverAbr = streamingData.optString("serverAbrStreamingUrl", "")
            val hls = streamingData.optString("hlsManifestUrl", "")
            val sabrVideo = serverAbr.isNotBlank() && streamingData.optJSONArray("formats")?.length() == 0
            if (sabrVideo) return null

            val formats = streamingData.optJSONArray("formats")
            if (formats != null) {
                for (i in 0 until formats.length()) {
                    val f = formats.getJSONObject(i)
                    var url = f.optString("url", "")
                    if (url.isBlank()) {
                        val cipher = f.optString("cipher", f.optString("signatureCipher", ""))
                        if (cipher.isNotBlank()) {
                            val m = Regex("""url=([^&]+)""").find(cipher)
                            if (m != null) try { url = java.net.URLDecoder.decode(m.groupValues[1], "UTF-8") } catch (_: Exception) {}
                        }
                    }
                    if (url.isBlank()) continue
                    val mime = f.optString("mimeType", "video/mp4")
                    val quality = f.optString("qualityLabel", f.optString("quality", ""))
                    if (quality.isBlank()) continue
                    val ext = when {
                        mime.contains("webm") -> "webm"
                        else -> "mp4"
                    }
                    streams.add(StreamOption("$quality • ${ext.uppercase()}", ext, quality, url, true, false))
                }
            }

            val adaptive = streamingData.optJSONArray("adaptiveFormats")
            if (adaptive != null) {
                for (i in 0 until adaptive.length()) {
                    val f = adaptive.getJSONObject(i)
                    var url = f.optString("url", "")
                    if (url.isBlank()) {
                        val cipher = f.optString("cipher", f.optString("signatureCipher", ""))
                        if (cipher.isNotBlank()) {
                            val m = Regex("""url=([^&]+)""").find(cipher)
                            if (m != null) try { url = java.net.URLDecoder.decode(m.groupValues[1], "UTF-8") } catch (_: Exception) {}
                        }
                    }
                    if (url.isBlank()) continue
                    val mime = f.optString("mimeType", "")
                    val quality = f.optString("qualityLabel", "")
                    val bitrate = f.optInt("bitrate", 0) / 1000
                    if (mime.contains("video")) {
                        if (quality.isBlank()) continue
                        val ext = if (mime.contains("webm")) "webm" else "mp4"
                        streams.add(StreamOption("$quality • ${ext.uppercase()} (video-only)", ext, quality, url, true, false))
                    } else if (mime.contains("audio")) {
                        val ext = when {
                            mime.contains("webm") || mime.contains("opus") -> "webm"
                            mime.contains("mp4") -> "m4a"
                            else -> "m4a"
                        }
                        val q = if (bitrate > 0) "${bitrate}kbps" else f.optString("quality", "")
                        streams.add(
                            StreamOption(
                                label = "Ses • ${ext.uppercase()} ${if (bitrate > 0) "${bitrate}kbps" else ""}".trim(),
                                extension = ext,
                                quality = q,
                                url = url,
                                isVideo = false,
                                isAudioOnly = true,
                                bitrate = bitrate
                            )
                        )
                    }
                }
            }

            if (streams.isEmpty() && hls.isNotBlank()) {
                streams.add(StreamOption("Canlı/HLS • M3U8", "m3u8", "auto", hls, true, false))
            }

            if (streams.isEmpty()) return null

            // Deduplicate ve sırala - muxed önce, sonra ses bitrate
            val sorted = streams.distinctBy { it.url }.sortedWith(
                compareBy<StreamOption> { !it.isVideo }
                    .thenByDescending { extractQualityNumber(it.quality) }
                    .thenByDescending { it.bitrate }
            )

            VideoInfo(videoId, title, author, thumb, duration, views, "https://www.youtube.com/watch?v=$videoId", sorted)
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun tryPipedParallel(videoId: String): VideoInfo? = coroutineScope {
        val deferreds = pipedInstances.map { base ->
            async(Dispatchers.IO) {
                try { withTimeout(5000) { fetchSinglePiped(base, videoId) } } catch (_: Exception) { null }
            }
        }
        for (d in deferreds) {
            try {
                val res = d.await()
                if (res != null) {
                    deferreds.forEach { if (it != d) it.cancel() }
                    return@coroutineScope res
                }
            } catch (_: Exception) { }
        }
        deferreds.forEach { try { it.await() } catch (_: Exception) {} }
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
        if (json.has("error")) return null

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

        val sorted = streams.distinctBy { it.url }.sortedWith(
            compareBy<StreamOption> { !it.isVideo }
                .thenByDescending { extractQualityNumber(it.quality) }
                .thenByDescending { it.bitrate }
        )

        return VideoInfo(videoId, title, author, thumb, duration, views, "https://www.youtube.com/watch?v=$videoId", sorted)
    }

    private suspend fun tryInvidiousParallel(videoId: String): VideoInfo? = coroutineScope {
        val deferreds = invidiousInstances.map { base ->
            async(Dispatchers.IO) {
                try { withTimeout(5000) { fetchSingleInvidious(base, videoId) } } catch (_: Exception) { null }
            }
        }
        for (d in deferreds) {
            try {
                val res = d.await()
                if (res != null) {
                    deferreds.forEach { if (it != d) it.cancel() }
                    return@coroutineScope res
                }
            } catch (_: Exception) {}
        }
        null
    }

    private fun fetchSingleInvidious(base: String, videoId: String): VideoInfo? {
        val req = Request.Builder()
            .url("$base/api/v1/videos/$videoId")
            .header("User-Agent", "Mozilla/5.0")
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
        if (json.has("error")) return null

        val title = json.optString("title", "Bilinmeyen Başlık")
        val author = json.optString("author", "Bilinmeyen Kanal")
        val thumb = run {
            val arr = json.optJSONArray("videoThumbnails")
            if (arr != null && arr.length() > 0) {
                var bestUrl = ""
                var bestW = -1
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val w = o.optInt("width", 0)
                    if (w > bestW) {
                        bestW = w
                        bestUrl = o.optString("url", "")
                    }
                }
                if (bestUrl.isNotBlank()) bestUrl else "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"
            } else {
                "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"
            }
        }
        val duration = json.optLong("lengthSeconds", 0L)
        val views = json.optLong("viewCount", 0L)

        val streams = mutableListOf<StreamOption>()

        val fArr = json.optJSONArray("formatStreams")
        if (fArr != null) {
            for (i in 0 until fArr.length()) {
                val o = fArr.getJSONObject(i)
                val url = o.optString("url", "")
                if (url.isBlank()) continue
                val quality = o.optString("qualityLabel", o.optString("quality", ""))
                val ext = o.optString("container", "mp4")
                streams.add(StreamOption("$quality • ${ext.uppercase()}", ext, quality, url, true, false))
            }
        }

        val aArr = json.optJSONArray("adaptiveFormats")
        if (aArr != null) {
            for (i in 0 until aArr.length()) {
                val o = aArr.getJSONObject(i)
                val type = o.optString("type", "")
                if (!type.contains("audio")) continue
                val url = o.optString("url", "")
                if (url.isBlank()) continue
                val ext = o.optString("container", "m4a")
                val bitrate = o.optString("bitrate", "0").toIntOrNull()?.div(1000) ?: 0
                streams.add(
                    StreamOption(
                        label = "Ses • ${ext.uppercase()} ${if (bitrate > 0) "${bitrate}kbps" else ""}".trim(),
                        extension = ext,
                        quality = if (bitrate > 0) "${bitrate}kbps" else "",
                        url = url,
                        isVideo = false,
                        isAudioOnly = true,
                        bitrate = bitrate
                    )
                )
            }
        }

        if (streams.isEmpty()) return null

        val sorted = streams.distinctBy { it.url }.sortedWith(
            compareBy<StreamOption> { !it.isVideo }
                .thenByDescending { extractQualityNumber(it.quality) }
                .thenByDescending { it.bitrate }
        )

        return VideoInfo(videoId, title, author, thumb, duration, views, "https://www.youtube.com/watch?v=$videoId", sorted)
    }

    private fun tryCobalt(videoId: String): VideoInfo? {
        val endpoints = listOf("https://api.cobalt.tools/api/json", "https://co.wuk.sh/api/json", "https://cobalt-api.kwiatekmiki.com/api/json")
        val ytUrl = "https://www.youtube.com/watch?v=$videoId"
        for (endpoint in endpoints) {
            try {
                // 1) Video için dene
                val videoBody = JSONObject().apply {
                    put("url", ytUrl)
                    put("vQuality", "1080")
                }.toString()
                val videoReq = Request.Builder()
                    .url(endpoint)
                    .header("Accept", "application/json")
                    .header("Content-Type", "application/json")
                    .header("User-Agent", "Mozilla/5.0")
                    .post(videoBody.toRequestBody("application/json".toMediaType()))
                    .build()
                var videoUrl: String? = null
                var videoJson: JSONObject? = null
                client.newCall(videoReq).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val body = resp.body?.string()
                        if (!body.isNullOrBlank()) {
                            videoJson = JSONObject(body)
                            val status = videoJson!!.optString("status", "")
                            if (status == "tunnel" || status == "redirect") {
                                videoUrl = videoJson!!.optString("url", "")
                            } else if (status == "picker") {
                                val picker = videoJson!!.optJSONArray("picker")
                                if (picker != null && picker.length() > 0) {
                                    videoUrl = picker.getJSONObject(0).optString("url", "")
                                }
                            }
                        }
                    }
                }

                // 2) Audio için dene (ayrı istek)
                var audioUrl: String? = null
                try {
                    val audioBody = JSONObject().apply {
                        put("url", ytUrl)
                        put("isAudioOnly", true)
                        put("aFormat", "mp3")
                    }.toString()
                    val audioReq = Request.Builder()
                        .url(endpoint)
                        .header("Accept", "application/json")
                        .header("Content-Type", "application/json")
                        .header("User-Agent", "Mozilla/5.0")
                        .post(audioBody.toRequestBody("application/json".toMediaType()))
                        .build()
                    client.newCall(audioReq).execute().use { resp ->
                        if (resp.isSuccessful) {
                            val body = resp.body?.string()
                            if (!body.isNullOrBlank()) {
                                val j = JSONObject(body)
                                val s = j.optString("status", "")
                                if (s == "tunnel" || s == "redirect") audioUrl = j.optString("url", "")
                                else if (s == "picker") {
                                    val p = j.optJSONArray("picker")
                                    if (p != null && p.length() > 0) audioUrl = p.getJSONObject(0).optString("url", "")
                                }
                            }
                        }
                    }
                } catch (_: Exception) {}

                if (videoUrl.isNullOrBlank() && audioUrl.isNullOrBlank()) continue

                val oembed = tryOEmbed(videoId)
                val title = oembed?.title ?: videoJson?.optString("text", "")?.takeIf { it.isNotBlank() } ?: "YouTube Video $videoId"
                val author = oembed?.author ?: "Bilinmeyen Kanal"
                val thumb = oembed?.thumbnailUrl ?: "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"
                val streams = mutableListOf<StreamOption>()
                if (!videoUrl.isNullOrBlank()) {
                    streams.add(StreamOption("1080p • MP4 (Cobalt)", "mp4", "1080p", videoUrl!!, true, false))
                    streams.add(StreamOption("720p • MP4 (Cobalt)", "mp4", "720p", videoUrl!!, true, false))
                }
                if (!audioUrl.isNullOrBlank()) {
                    streams.add(StreamOption("Ses • MP3 128kbps (Cobalt)", "mp3", "128kbps", audioUrl!!, false, true, bitrate = 128))
                } else if (!videoUrl.isNullOrBlank()) {
                    // Audio yoksa video URL'i ses olarak da kullan (muxed)
                    streams.add(StreamOption("Ses • M4A (Cobalt)", "m4a", "128kbps", videoUrl!!, false, true, bitrate = 128))
                }
                if (streams.isNotEmpty()) {
                    return VideoInfo(videoId, title, author, thumb, 0, 0, ytUrl, streams)
                }
            } catch (_: Exception) { continue }
        }
        // Eski picker fallback (tek istek)
        for (endpoint in endpoints) {
            try {
                val jsonBody = JSONObject().apply {
                    put("url", "https://www.youtube.com/watch?v=$videoId")
                    put("vQuality", "720")
                }.toString()
                val req = Request.Builder()
                    .url(endpoint)
                    .header("Accept", "application/json")
                    .header("Content-Type", "application/json")
                    .header("User-Agent", "Mozilla/5.0")
                    .post(jsonBody.toRequestBody("application/json".toMediaType()))
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
                val status = json.optString("status", "")
                val url = json.optString("url", "")
                if (status == "tunnel" || status == "redirect") {
                    if (url.isBlank()) continue
                    val oembed = tryOEmbed(videoId)
                    val title = oembed?.title ?: "YouTube Video $videoId"
                    val author = oembed?.author ?: "Bilinmeyen Kanal"
                    val thumb = oembed?.thumbnailUrl ?: "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"
                    val streams = listOf(
                        StreamOption("720p • MP4 (Cobalt)", "mp4", "720p", url, true, false),
                        StreamOption("Ses • M4A (Cobalt)", "m4a", "128kbps", url, false, true, bitrate = 128)
                    )
                    return VideoInfo(videoId, title, author, thumb, 0, 0, "https://www.youtube.com/watch?v=$videoId", streams)
                } else if (status == "picker") {
                    val picker = json.optJSONArray("picker")
                    if (picker != null && picker.length() > 0) {
                        val first = picker.getJSONObject(0)
                        val pUrl = first.optString("url", "")
                        if (pUrl.isNotBlank()) {
                            val oembed = tryOEmbed(videoId)
                            return VideoInfo(
                                videoId,
                                oembed?.title ?: "YouTube Video $videoId",
                                oembed?.author ?: "Bilinmeyen Kanal",
                                oembed?.thumbnailUrl ?: "https://i.ytimg.com/vi/$videoId/hqdefault.jpg",
                                0, 0, "https://www.youtube.com/watch?v=$videoId",
                                listOf(StreamOption("Video • MP4 (Cobalt)", "mp4", "720p", pUrl, true, false))
                            )
                        }
                    }
                }
            } catch (_: Exception) { continue }
        }
        return null
    }

    private suspend fun tryWatchPage(videoId: String): VideoInfo? {
        return try {
            val req = Request.Builder()
                .url("https://www.youtube.com/watch?v=$videoId&bpctr=9999999999&has_verified=1")
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .header("Accept-Language", "en-US,en;q=0.9,tr;q=0.8")
                .build()
            val resp = client.newCall(req).execute()
            if (!resp.isSuccessful) {
                resp.close()
                return null
            }
            val html = resp.body?.string()
            resp.close()
            if (html.isNullOrBlank()) return null
            // Robust JSON extraction - brace matching (SABR videoları için kritik)
            val startMarker = "ytInitialPlayerResponse = "
            val startIdx = html.indexOf(startMarker)
            if (startIdx == -1) return null
            val jsonStart = html.indexOf('{', startIdx)
            if (jsonStart == -1) return null
            var braceCount = 0
            var jsonEnd = -1
            for (i in jsonStart until html.length) {
                when (html[i]) {
                    '{' -> braceCount++
                    '}' -> braceCount--
                }
                if (braceCount == 0) {
                    jsonEnd = i
                    break
                }
                if (i - jsonStart > 600000) break // güvenlik
            }
            if (jsonEnd == -1) return null
            val jsonStr = html.substring(jsonStart, jsonEnd + 1)
            val json = JSONObject(jsonStr)
            val videoDetails = json.optJSONObject("videoDetails") ?: return null
            val title = videoDetails.optString("title", "Bilinmeyen Başlık")
            val author = videoDetails.optString("author", "Bilinmeyen Kanal")
            val thumb = "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"
            val duration = videoDetails.optString("lengthSeconds", "0").toLongOrNull() ?: 0L
            val views = videoDetails.optString("viewCount", "0").toLongOrNull() ?: 0L
            val streamingData = json.optJSONObject("streamingData") ?: return null
            val streams = mutableListOf<StreamOption>()
            val serverAbr = streamingData.optString("serverAbrStreamingUrl", "")
            val hls = streamingData.optString("hlsManifestUrl", "")
            val sabrVideo = serverAbr.isNotBlank() && streamingData.optJSONArray("formats")?.length() == 0
            if (sabrVideo) return null
            val formats = streamingData.optJSONArray("formats")
            if (formats != null) {
                for (i in 0 until formats.length()) {
                    val f = formats.getJSONObject(i)
                    var url = f.optString("url", "")
                    if (url.isBlank()) {
                        val cipher = f.optString("cipher", f.optString("signatureCipher", ""))
                        if (cipher.isNotBlank()) {
                            val urlMatch = Regex("""url=([^&]+)""").find(cipher)
                            if (urlMatch != null) url = java.net.URLDecoder.decode(urlMatch.groupValues[1], "UTF-8")
                        }
                    }
                    if (url.isBlank()) continue
                    val quality = f.optString("qualityLabel", f.optString("quality", ""))
                    val mime = f.optString("mimeType", "video/mp4")
                    val ext = if (mime.contains("webm")) "webm" else "mp4"
                    streams.add(StreamOption("$quality • ${ext.uppercase()} (web)", ext, quality, url, true, false))
                }
            }
            val adaptive = streamingData.optJSONArray("adaptiveFormats")
            if (adaptive != null) {
                for (i in 0 until adaptive.length()) {
                    val f = adaptive.getJSONObject(i)
                    var url = f.optString("url", "")
                    if (url.isBlank()) {
                        val cipher = f.optString("cipher", f.optString("signatureCipher", ""))
                        if (cipher.isNotBlank()) {
                            val urlMatch = Regex("""url=([^&]+)""").find(cipher)
                            if (urlMatch != null) url = java.net.URLDecoder.decode(urlMatch.groupValues[1], "UTF-8")
                        }
                    }
                    if (url.isBlank()) continue
                    val mime = f.optString("mimeType", "")
                    val bitrate = f.optInt("bitrate", 0) / 1000
                    if (mime.contains("video")) {
                        val quality = f.optString("qualityLabel", "")
                        if (quality.isBlank()) continue
                        val ext = if (mime.contains("webm")) "webm" else "mp4"
                        streams.add(StreamOption("$quality • ${ext.uppercase()} (web)", ext, quality, url, true, false))
                    } else if (mime.contains("audio")) {
                        val ext = if (mime.contains("webm")) "webm" else "m4a"
                        streams.add(StreamOption("Ses • ${ext.uppercase()} ${if (bitrate > 0) "${bitrate}kbps" else ""}".trim(), ext, if (bitrate > 0) "${bitrate}kbps" else "", url, false, true, bitrate = bitrate))
                    }
                }
            }
            if (streams.isEmpty() && hls.isNotBlank()) {
                streams.add(StreamOption("HLS • M3U8", "m3u8", "auto", hls, true, false))
            }
            if (streams.isEmpty()) return null
            val sorted = streams.distinctBy { it.url }.sortedWith(compareBy<StreamOption> { !it.isVideo }.thenByDescending { extractQualityNumber(it.quality) })
            VideoInfo(videoId, title, author, thumb, duration, views, "https://www.youtube.com/watch?v=$videoId", sorted)
        } catch (_: Exception) { null }
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
