package com.indirgitsin.app

import com.indirgitsin.app.data.model.StreamOption
import com.indirgitsin.app.data.model.StreamSelector
import org.junit.Assert.*
import org.junit.Test

class Mp3SelectionTest {
    @Test fun preservesSourceAndMarksRealConversionWithStableBitrateSelectors() {
        val source = StreamOption("AAC", "m4a", "128kbps", "https://media.example/audio", false, true, bitrate = 128, codec = "mp4a.40.2")
        val list = StreamSelector.withMp3Options(listOf(source))
        assertEquals(source, list.first())
        assertEquals(listOf(128, 192, 320), list.filter { it.convertToMp3 }.map { it.bitrate })
        assertTrue(list.drop(1).all { it.url == source.url && it.extension == "mp3" && it.codec == source.codec })
        assertEquals(list, StreamSelector.withMp3Options(list))
        assertTrue(StreamSelector.preferred(list, true, "MP3", audioOnly = true)!!.convertToMp3)
    }

    @Test fun doesNotOfferConversionForLiveOrMissingAudio() {
        val live = StreamOption("Live", "mp4", "live", "https://media.example/live.m3u8", true, false, isLive = true)
        assertEquals(listOf(live), StreamSelector.withMp3Options(listOf(live)))
        assertTrue(StreamSelector.withMp3Options(emptyList()).isEmpty())
    }
}
