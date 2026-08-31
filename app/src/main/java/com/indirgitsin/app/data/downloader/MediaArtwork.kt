package com.indirgitsin.app.data.downloader

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.AtomicFile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/** Optional offline artwork. Never put these bytes into WorkManager's small Data records. */
object MediaArtwork {
    data class Entry(val title: String, val artist: String, val jpeg: ByteArray?)
    private const val MAX_DOWNLOAD = 2 * 1024 * 1024
    private const val MAX_ENTRIES = 256
    private val client by lazy {
        OkHttpClient.Builder().connectTimeout(3, TimeUnit.SECONDS).readTimeout(3, TimeUnit.SECONDS)
            .callTimeout(8, TimeUnit.SECONDS).followRedirects(false).followSslRedirects(false).build()
    }

    suspend fun fetch(url: String): ByteArray? = withContext(Dispatchers.IO) {
        optional {
            val parsed = url.toHttpUrlOrNull() ?: return@optional null
            if (!parsed.isHttps || !(parsed.host.endsWith(".ytimg.com") || parsed.host == "img.youtube.com")) return@optional null
            client.newCall(Request.Builder().url(parsed).build()).execute().use { response ->
                if (!response.isSuccessful) return@optional null
                val body = response.body ?: return@optional null
                if (body.contentLength() > MAX_DOWNLOAD) return@optional null
                val bytes = ByteArrayOutputStream()
                body.byteStream().use { input ->
                    val buffer = ByteArray(16 * 1024)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        if (bytes.size() + count > MAX_DOWNLOAD) return@optional null
                        bytes.write(buffer, 0, count)
                    }
                }
                normalize(bytes.toByteArray())
            }
        }
    }

    /** Decode sampled images, never full-resolution thumbnails; retained bitmaps are at most 640px. */
    fun decode(bytes: ByteArray): Bitmap? {
        if (bytes.size > MAX_DOWNLOAD) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth !in 1..16384 || bounds.outHeight !in 1..16384) return null
        val options = BitmapFactory.Options()
        while (maxOf(bounds.outWidth, bounds.outHeight) / options.inSampleSize > 1280) options.inSampleSize *= 2
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) ?: return null
        val scale = minOf(1f, 640f / maxOf(decoded.width, decoded.height))
        if (scale == 1f) return decoded
        return Bitmap.createScaledBitmap(decoded, (decoded.width * scale).toInt().coerceAtLeast(1),
            (decoded.height * scale).toInt().coerceAtLeast(1), true).also { if (it !== decoded) decoded.recycle() }
    }

    private fun normalize(bytes: ByteArray): ByteArray? {
        val bitmap = decode(bytes) ?: return null
        return try {
            ByteArrayOutputStream().use { out ->
                if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)) null
                else out.toByteArray().takeIf { it.size <= Mp3Tags.MAX_COVER_BYTES }
            }
        } finally { bitmap.recycle() }
    }

    private fun directory(context: Context) = File(context.noBackupFilesDir, "media-artwork")
    private fun file(context: Context, uri: Uri): File {
        val key = MessageDigest.getInstance("SHA-256").digest(uri.toString().toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return File(directory(context), "$key.art")
    }

    @Synchronized
    fun save(context: Context, uri: Uri, entry: Entry) = optional {
        require(entry.jpeg == null || entry.jpeg.size <= Mp3Tags.MAX_COVER_BYTES)
        val directory = directory(context)
        check(directory.isDirectory || directory.mkdirs())
        val target = file(context, uri)
        val atomic = AtomicFile(target)
        val stream = atomic.startWrite()
        try {
            val output = DataOutputStream(stream)
            output.writeInt(1)
            output.writeUTF(entry.title.take(500))
            output.writeUTF(entry.artist.take(500))
            output.writeInt(entry.jpeg?.size ?: 0)
            entry.jpeg?.let { output.write(it) }
            output.flush()
            atomic.finishWrite(stream)
        } catch (e: Exception) { atomic.failWrite(stream); throw e }
        // Bounded to 128 MiB of picture data. MP3 also retains its embedded artwork after eviction.
        directory.listFiles { f -> f.extension == "art" }?.sortedByDescending { it.lastModified() }
            ?.drop(MAX_ENTRIES)?.forEach { AtomicFile(it).delete() }
    }

    @Synchronized
    fun read(context: Context, uri: Uri): Entry? = optional {
        val target = file(context, uri)
        val atomic = AtomicFile(target)
        atomic.openRead().use { stream ->
            require(stream.channel.size() <= Mp3Tags.MAX_COVER_BYTES + 8192)
            val input = DataInputStream(stream)
            require(input.readInt() == 1)
            val title = input.readUTF()
            val artist = input.readUTF()
            val size = input.readInt()
            require(size in 0..Mp3Tags.MAX_COVER_BYTES)
            val jpeg = if (size == 0) null else ByteArray(size).also { input.readFully(it) }
            target.setLastModified(System.currentTimeMillis())
            Entry(title, artist, jpeg)
        }
    }

    suspend fun load(context: Context, uri: Uri): Entry? = withContext(Dispatchers.IO) {
        val stored = read(context, uri)
        if (stored?.jpeg != null) return@withContext stored
        val embedded = optional {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, uri)
                Entry(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE).orEmpty(),
                    retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST).orEmpty(),
                    retriever.embeddedPicture?.let { normalize(it) })
            } finally { retriever.release() }
        }
        embedded?.copy(title = embedded.title.ifBlank { stored?.title.orEmpty() },
            artist = embedded.artist.ifBlank { stored?.artist.orEmpty() }) ?: stored
    }

    @Synchronized fun remove(context: Context, uri: Uri) { optional { AtomicFile(file(context, uri)).delete() } }

    private inline fun <T> optional(block: () -> T): T? = try { block() }
    catch (e: CancellationException) { throw e }
    catch (_: Exception) { null } // Cover/network/storage errors must never fail a valid media download.
}
