package com.indirgitsin.app

import com.indirgitsin.app.data.downloader.ContentRange
import com.indirgitsin.app.data.model.StreamOption
import com.indirgitsin.app.data.model.StreamSelector
import com.indirgitsin.app.util.VersionComparator
import com.indirgitsin.app.util.YoutubeLinkHelper

/** Also runnable without Android SDK; these are the production selectors/parsers, not copies. */
object CoreRegressionChecks {
    private fun audio(ext: String = "m4a", codec: String = "mp4a.40.2") =
        StreamOption("Audio", ext, "128kbps", "https://media.test/audio", false, true, bitrate = 128, codec = codec)
    private fun video(quality: String = "1080p", ext: String = "mp4", only: Boolean = true, codec: String = "avc1.640028") =
        StreamOption("Video", ext, quality, "https://media.test/$quality/$ext", true, false, isVideoOnly = only, codec = codec)

    val cases: List<Pair<String, () -> Unit>> = listOf(
        "360p video-only receives audio" to {
            val result = StreamSelector.prepare(listOf(video("360p"), audio()), 34).first { it.isVideo }
            check(result.needsMuxing && result.audioUrl == audio().url)
        },
        "1080p receives AAC even when Opus has higher bitrate" to {
            val result = StreamSelector.prepare(listOf(video(), audio(), audio("webm", "opus").copy(bitrate = 256)), 34).first { it.isVideo }
            check(result.audioCodec == "mp4a.40.2")
        },
        "already muxed 720p is not remuxed" to {
            val result = StreamSelector.prepare(listOf(video("720p", only = false), audio()), 34).first { it.isVideo }
            check(!result.needsMuxing && result.hasAudio)
        },
        "video without compatible audio is not offered" to {
            check(StreamSelector.prepare(listOf(video(), audio("webm", "opus")), 34).none { it.isVideo })
        },
        "missing audio never becomes a successful silent option" to {
            check(!video("360p").isDownloadable)
        },
        "WebM is paired with WebM and stays WebM" to {
            val result = StreamSelector.prepare(listOf(video(ext = "webm", codec = "vp09"), audio("webm", "opus"), audio()), 34).first { it.isVideo }
            check(result.extension == "webm" && result.audioCodec == "opus")
        },
        "WebM Opus muxing is excluded on API 28" to {
            check(StreamSelector.prepare(listOf(video(ext = "webm", codec = "vp09"), audio("webm", "opus")), 28).none { it.isVideo })
        },
        "WebM Vorbis is available on API 24" to {
            check(StreamSelector.prepare(listOf(video(ext = "webm", codec = "vp09"), audio("webm", "vorbis")), 24).any { it.needsMuxing })
        },
        "AV1 MP4 is excluded below API 34" to {
            check(StreamSelector.prepare(listOf(video("2160p", codec = "av01.0.12"), audio()), 33).none { it.isVideo })
        },
        "audio is not relabelled as MP3" to {
            check(StreamSelector.prepare(listOf(audio()), 34).single().extension == "m4a")
        },
        "HLS manifest is not a downloadable video" to {
            check(!video(ext = "m3u8", only = false).isDownloadable)
        },
        "default quality selects highest or at most 720p" to {
            val streams = listOf(video("1080p", only = false), video("720p", only = false), video("360p", only = false))
            check(StreamSelector.preferred(streams, true, "M4A")?.quality == "1080p")
            check(StreamSelector.preferred(streams, false, "M4A")?.quality == "720p")
        },
        "audio format preference is respected" to {
            check(StreamSelector.preferred(listOf(audio(), audio("webm", "opus")), true, "WEBM", true)?.extension == "webm")
        },
        "v prefix does not trigger an update" to {
            check(!VersionComparator.isNewer("v1.0.5", "1.0.5"))
        },
        "equal versions with missing zero components match" to {
            check(!VersionComparator.isNewer("1.2.0", "1.2"))
        },
        "newer and older patch versions compare numerically" to {
            check(VersionComparator.isNewer("v1.0.10", "1.0.9"))
            check(!VersionComparator.isNewer("1.0.9", "1.0.10"))
        },
        "release supersedes prerelease" to {
            check(VersionComparator.isNewer("1.0.0", "1.0.0-rc.1"))
            check(!VersionComparator.isNewer("1.0.0-rc.1", "1.0.0"))
        },
        "prerelease numbers compare numerically" to {
            check(VersionComparator.isNewer("1.0.0-beta.10", "1.0.0-beta.2"))
        },
        "build metadata does not trigger update" to {
            check(!VersionComparator.isNewer("1.0.0+new", "1.0.0+old"))
            check(!VersionComparator.isNewer("not-a-version", "1.0.0"))
        },
        "links accept shorts music and reordered query" to {
            val id = "abcdefghijk"
            check(YoutubeLinkHelper.extractVideoId("https://www.youtube.com/shorts/$id") == id)
            check(YoutubeLinkHelper.extractVideoId("https://music.youtube.com/watch?list=PLtest&v=$id") == id)
        },
        "foreign host and overlong IDs are rejected" to {
            check(!YoutubeLinkHelper.isValidYoutubeUrl("https://evil.test/?v=abcdefghijk"))
            check(!YoutubeLinkHelper.isValidYoutubeUrl("https://youtube.com.evil.test/watch?v=abcdefghijk"))
            check(!YoutubeLinkHelper.isValidYoutubeUrl("https://youtu.be/abcdefghijkl"))
        },
        "shared text finds the trusted URL" to {
            check(YoutubeLinkHelper.findYoutubeUrlInText("Bak: https://youtu.be/abcdefghijk.") == "https://youtu.be/abcdefghijk")
        },
        "range parser retains the whole-file length" to {
            check(ContentRange.parse("bytes 0-1048575/7340032") == ContentRange(0, 1048575, 7340032))
        },
        "malformed and inconsistent ranges are rejected" to {
            check(ContentRange.parse("bytes 10-5/20") == null)
            check(ContentRange.parse("bytes 0-20/20") == null)
            check(ContentRange.parse("bytes 0-1/*") == null)
            check(ContentRange.parse("bytes 0-9999999999999999999999/9999999999999999999999999") == null)
        }
    )

    @JvmStatic fun main(args: Array<String>) {
        for ((name, body) in cases) {
            body()
            println("PASS: $name")
        }
        println("${cases.size} regression checks passed.")
    }
}
