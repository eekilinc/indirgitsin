package com.indirgitsin.app

import com.indirgitsin.app.data.downloader.AdtsFrames
import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer

class AdtsFramesTest {
    private fun packet(payload: ByteArray, crc: Boolean = false): ByteArray {
        val header = if (crc) 9 else 7
        val length = header + payload.size
        return byteArrayOf(0xff.toByte(), if (crc) 0xf0.toByte() else 0xf1.toByte(), 0x50, 0x40,
            (length shr 3).toByte(), (((length and 7) shl 5) or 31).toByte(), 0xfc.toByte()) +
            (if (crc) byteArrayOf(0, 0) else byteArrayOf()) + payload
    }

    @Test fun separatesBatchedFramesAndPreservesEncodedBytesAndTime() {
        val data = packet(byteArrayOf(1, 2, 3)) + packet(byteArrayOf(4, 5))
        val frames = AdtsFrames.split(ByteBuffer.wrap(data), data.size)
        assertEquals(2, frames.size)
        assertArrayEquals(byteArrayOf(1, 2, 3), data.copyOfRange(frames[0].offset, frames[0].offset + frames[0].size))
        assertArrayEquals(byteArrayOf(4, 5), data.copyOfRange(frames[1].offset, frames[1].offset + frames[1].size))
        assertEquals(1024_000_000L / 44100, frames[1].timeOffsetUs)
    }

    @Test fun handlesCrcAndRejectsTruncatedOrNonAdtsInput() {
        val data = packet(byteArrayOf(1, 2), crc = true)
        assertEquals(9, AdtsFrames.split(ByteBuffer.wrap(data), data.size).single().offset)
        for (broken in listOf(data.copyOf(data.size - 1), byteArrayOf(0, 1), data.clone().apply { this[0] = 0 })) {
            assertTrue(runCatching { AdtsFrames.split(ByteBuffer.wrap(broken), broken.size) }.isFailure)
        }
    }
}
