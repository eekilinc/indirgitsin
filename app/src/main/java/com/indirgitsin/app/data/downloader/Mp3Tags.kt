package com.indirgitsin.app.data.downloader

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream

/** ID3v2.3 tags precede the MPEG stream; audio never needs to be copied or re-encoded twice. */
object Mp3Tags {
    const val MAX_COVER_BYTES = 512 * 1024

    fun create(title: String, artist: String, jpeg: ByteArray? = null): ByteArray {
        require(jpeg == null || jpeg.size <= MAX_COVER_BYTES)
        val frames = ByteArrayOutputStream()
        fun frame(id: String, body: ByteArray) {
            val sink = DataOutputStream(frames)
            sink.writeBytes(id)
            sink.writeInt(body.size) // Frame lengths are NOT synchsafe in ID3v2.3.
            sink.writeShort(0)
            sink.write(body)
        }
        fun text(id: String, value: String) {
            if (value.isNotBlank()) frame(id, byteArrayOf(1) + value.replace("\u0000", "").take(500).toByteArray(Charsets.UTF_16))
        }
        text("TIT2", title)
        text("TPE1", artist)
        if (jpeg != null && jpeg.isNotEmpty()) {
            frame("APIC", byteArrayOf(0) + "image/jpeg".toByteArray(Charsets.ISO_8859_1) + byteArrayOf(0, 3, 0) + jpeg)
        }
        val size = frames.size()
        val header = byteArrayOf(73, 68, 51, 3, 0, 0,
            ((size shr 21) and 127).toByte(), ((size shr 14) and 127).toByte(),
            ((size shr 7) and 127).toByte(), (size and 127).toByte())
        return header + frames.toByteArray()
    }
}
