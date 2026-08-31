package com.indirgitsin.app.data.downloader

import java.nio.ByteBuffer

data class AdtsFrame(val offset: Int, val size: Int, val timeOffsetUs: Long)

/** TS AAC can contain ADTS headers and several frames in one extractor sample; MP4 needs raw frames. */
object AdtsFrames {
    private val rates = intArrayOf(96000, 88200, 64000, 48000, 44100, 32000, 24000, 22050, 16000, 12000, 11025, 8000, 7350)

    fun split(buffer: ByteBuffer, size: Int): List<AdtsFrame> {
        require(size > 0 && size <= buffer.limit())
        fun byte(index: Int) = buffer.get(index).toInt() and 255
        val frames = mutableListOf<AdtsFrame>()
        var offset = 0
        var rate = 0
        while (offset < size) {
            require(frames.size < 1024 && size - offset >= 7) { "Eksik AAC/ADTS başlığı." }
            require(byte(offset) == 255 && byte(offset + 1) and 0xF6 == 0xF0) { "Geçersiz AAC/ADTS eşzamanlama başlığı." }
            val sampleRate = rates.getOrNull((byte(offset + 2) shr 2) and 15) ?: error("Geçersiz AAC örnekleme hızı.")
            require(rate == 0 || rate == sampleRate) { "AAC örnekleme hızı değişti." }
            rate = sampleRate
            val header = if (byte(offset + 1) and 1 != 0) 7 else 9
            val length = ((byte(offset + 3) and 3) shl 11) or (byte(offset + 4) shl 3) or (byte(offset + 5) shr 5)
            require(length > header && length <= size - offset && byte(offset + 6) and 3 == 0) { "Eksik veya desteklenmeyen AAC karesi." }
            frames += AdtsFrame(offset + header, length - header, frames.size * 1024_000_000L / sampleRate)
            offset += length
        }
        return frames
    }
}
