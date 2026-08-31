package com.indirgitsin.app.data.downloader

/** One native encoder per conversion; callers must close it on failure/cancellation. */
internal class LameEncoder(rate: Int, channels: Int, bitrate: Int) : AutoCloseable {
    companion object { init { System.loadLibrary("indirgitsin_mp3") } }
    private var handle = openNative(rate, channels, bitrate)
    fun encode(pcm: ShortArray, count: Int): ByteArray {
        check(handle != 0L)
        return encodeNative(handle, pcm, count, false)
    }
    fun finish(): ByteArray {
        check(handle != 0L)
        return encodeNative(handle, shortArrayOf(), 0, true)
    }
    override fun close() { if (handle != 0L) { closeNative(handle); handle = 0 } }
    private external fun openNative(rate: Int, channels: Int, bitrate: Int): Long
    private external fun encodeNative(handle: Long, pcm: ShortArray, count: Int, flush: Boolean): ByteArray
    private external fun closeNative(handle: Long)
}
