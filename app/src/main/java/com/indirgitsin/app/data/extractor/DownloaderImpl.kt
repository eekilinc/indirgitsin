package com.indirgitsin.app.data.extractor

import okhttp3.OkHttpClient
import okhttp3.Request as OkRequest
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import java.io.IOException
import java.util.concurrent.TimeUnit

class DownloaderImpl private constructor(private val client: OkHttpClient) : Downloader() {

    companion object {
        @Volatile private var INSTANCE: DownloaderImpl? = null
        fun getInstance(): DownloaderImpl {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: DownloaderImpl(
                    OkHttpClient.Builder()
                        .connectTimeout(15, TimeUnit.SECONDS)
                        .readTimeout(15, TimeUnit.SECONDS)
                        .build()
                ).also { INSTANCE = it }
            }
        }
    }

    @Throws(IOException::class, ReCaptchaException::class)
    override fun execute(request: org.schabi.newpipe.extractor.downloader.Request): org.schabi.newpipe.extractor.downloader.Response {
        val headers = request.headers()
        val builder = OkRequest.Builder().url(request.url())
        // method handling
        val data = request.dataToSend()
        when (request.httpMethod()) {
            "HEAD" -> builder.head()
            "POST" -> {
                val body = if (data != null) okhttp3.RequestBody.create(null, data) else okhttp3.RequestBody.create(null, ByteArray(0))
                builder.post(body)
            }
            else -> builder.get()
        }
        headers.forEach { (k, v) -> v.forEach { builder.header(k, it) } }
        if (!headers.containsKey("User-Agent")) {
            builder.header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
        }
        val okRequest = builder.build()
        val response = client.newCall(okRequest).execute()
        val body = response.body?.string() ?: ""
        val latestUrl = response.request.url.toString()
        val responseHeaders = response.headers.toMultimap().mapValues { it.value.toMutableList() }
        return org.schabi.newpipe.extractor.downloader.Response(
            response.code,
            body,
            responseHeaders,
            latestUrl,
            null
        )
    }
}
