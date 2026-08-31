package com.indirgitsin.app.data.downloader

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.DataInputStream

class Mp3TagsTest {
    @Test fun unicodeTextAndPictureHaveStandardV23FrameLengths() {
        val picture = ByteArray(257) { it.toByte() }
        val tag = Mp3Tags.create("İstanbul 🎵", "Şarkıcı", picture)
        assertEquals("ID3", String(tag, 0, 3, Charsets.US_ASCII))
        assertEquals(3, tag[3].toInt())
        val length = (6..9).fold(0) { n, i -> assertTrue(tag[i] >= 0); (n shl 7) or tag[i].toInt() }
        assertEquals(tag.size - 10, length)
        val input = DataInputStream(ByteArrayInputStream(tag, 10, length))
        val frames = linkedMapOf<String, ByteArray>()
        while (input.available() > 0) {
            val id = ByteArray(4).also(input::readFully).toString(Charsets.US_ASCII)
            val size = input.readInt()
            assertEquals(0, input.readUnsignedShort())
            frames[id] = ByteArray(size).also(input::readFully)
        }
        assertEquals(setOf("TIT2", "TPE1", "APIC"), frames.keys)
        val title = frames.getValue("TIT2")
        assertEquals(1, title[0].toInt())
        assertEquals("İstanbul 🎵", title.copyOfRange(1, title.size).toString(Charsets.UTF_16))
        val cover = frames.getValue("APIC")
        assertEquals("image/jpeg", String(cover, 1, 10, Charsets.ISO_8859_1))
        assertEquals(3, cover[12].toInt())
        assertArrayEquals(picture, cover.copyOfRange(14, cover.size))
    }

    @Test fun missingPictureStillWritesTitleAndNeverAnEmptyApic() {
        val tag = Mp3Tags.create("Title", "")
        assertTrue(tag.toString(Charsets.ISO_8859_1).contains("TIT2"))
        assertFalse(tag.toString(Charsets.ISO_8859_1).contains("APIC"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun oversizedCoverIsRejectedBeforeAllocatingTag() { Mp3Tags.create("Title", "", ByteArray(Mp3Tags.MAX_COVER_BYTES + 1)) }
}
