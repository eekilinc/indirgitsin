package com.indirgitsin.app.data.downloader

/** Recent throughput of both streams together, using a monotonic clock. */
class TransferProgress(startMillis: Long) {
    data class Estimate(val bytesPerSecond: Long, val remainingSeconds: Long?)
    private data class Sample(val time: Long, val bytes: Long)
    private val samples = java.util.ArrayDeque<Sample>().apply { add(Sample(startMillis, 0)) }

    @Synchronized
    fun update(nowMillis: Long, bytes: Long, total: Long): Estimate {
        val last = samples.last()
        if (nowMillis < last.time || bytes < last.bytes) {
            samples.clear()
            samples.add(Sample(nowMillis, bytes))
            return Estimate(0, null)
        }
        if (nowMillis > last.time) samples.add(Sample(nowMillis, bytes))
        while (samples.size > 2 && samples.elementAt(1).time <= nowMillis - 5_000) samples.removeFirst()
        val first = samples.first()
        val elapsed = nowMillis - first.time
        val rate = if (elapsed > 0) ((bytes - first.bytes).toDouble() * 1_000 / elapsed).toLong() else 0
        val remaining = when {
            total >= 0 && bytes >= total -> 0L
            total > 0 && rate > 0 -> kotlin.math.ceil((total - bytes).toDouble() / rate).toLong()
            else -> null
        }
        return Estimate(rate, remaining)
    }
}
