package com.indirgitsin.app

import com.indirgitsin.app.data.downloader.HlsPlaylist
import org.junit.Assert.*
import org.junit.Test

class HlsPlaylistTest {
    private fun parse(text: String) = HlsPlaylist.parse(text.trimIndent(), "https://media.example/path/live.m3u8")

    @Test fun resolvesRelativeSegmentsAndTracksSequence() {
        val list = parse("""
            #EXTM3U
            #EXT-X-MEDIA-SEQUENCE:42
            #EXT-X-TARGETDURATION:6
            #EXTINF:5.5,
            one.ts?token=abc
            #EXTINF:6,
            ../two.ts
            #EXT-X-ENDLIST
        """)
        assertEquals(listOf(42L, 43L), list.segments.map { it.sequence })
        assertEquals("https://media.example/two.ts", list.segments[1].resource.url)
        assertEquals(5.5, list.segments[0].seconds, 0.001)
        assertTrue(list.ended)
    }

    @Test fun selectsMuxedAvcAacAndBoundsResolution() {
        val list = parse("""
            #EXTM3U
            #EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID="audio",URI="sound.m3u8"
            #EXT-X-STREAM-INF:BANDWIDTH=9000000,RESOLUTION=3840x2160,CODECS="avc1.640028,mp4a.40.2"
            four.m3u8
            #EXT-X-STREAM-INF:BANDWIDTH=3000000,RESOLUTION=1920x1080,CODECS="avc1.640028,mp4a.40.2"
            full.m3u8
            #EXT-X-STREAM-INF:BANDWIDTH=5000000,RESOLUTION=1920x1080,CODECS="avc1.640028,mp4a.40.2",AUDIO="audio"
            separate.m3u8
        """)
        assertEquals("https://media.example/path/full.m3u8", list.selectVariant().url)
    }

    @Test fun tracksInitializationAndImplicitByteRanges() {
        val list = parse("""
            #EXTM3U
            #EXT-X-MAP:URI="all.mp4",BYTERANGE="100@0"
            #EXTINF:2,
            #EXT-X-BYTERANGE:200@100
            all.mp4
            #EXTINF:2,
            #EXT-X-BYTERANGE:250
            all.mp4
        """)
        assertEquals(300L, list.segments[1].resource.range!!.offset)
        assertEquals(100L, list.segments[0].initialization!!.range!!.length)
    }

    @Test fun rejectsEncryptionAndUnsafeSchemes() {
        for (value in listOf("#EXT-X-KEY:METHOD=AES-128,URI=\"key\"", "#EXT-X-SESSION-KEY:METHOD=SAMPLE-AES,URI=\"key\"")) {
            assertTrue(runCatching { parse("#EXTM3U\n$value\n#EXTINF:6,\na.ts") }.isFailure)
        }
        for (url in listOf("file:///etc/passwd", "http://media.example/a.ts", "https://user:pass@media.example/a.ts")) {
            assertTrue(runCatching { parse("#EXTM3U\n#EXTINF:6,\n$url") }.isFailure)
        }
    }

    @Test fun rejectsBrokenTimelineAndMissingRangeOffset() {
        for (text in listOf("#EXTM3U\n#EXTINF:NaN,\na.ts", "#EXTM3U\n#EXTINF:-1,\na.ts", "#EXTM3U\n#EXTINF:6,",
            "#EXTM3U\n#EXTINF:2,\n#EXT-X-BYTERANGE:50\na.ts")) assertTrue(runCatching { parse(text) }.isFailure)
    }

    @Test fun preservesDiscontinuityAndGapMarkers() {
        val list = parse("#EXTM3U\n#EXT-X-DISCONTINUITY\n#EXTINF:6,\na.ts\n#EXT-X-GAP\n#EXTINF:6,\nb.ts")
        assertTrue(list.segments[0].discontinuity)
        assertFalse(list.segments[0].gap)
        assertTrue(list.segments[1].gap)
    }
}
