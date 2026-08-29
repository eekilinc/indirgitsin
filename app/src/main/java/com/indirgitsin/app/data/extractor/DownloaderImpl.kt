package com.indirgitsin.app.data.extractor

import okhttp3.OkHttpClient
import okhttp3.Request
import org.schabi.newpipe.extractor.downloader.Downloader
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext

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

    override fun download(url: String, headers: Map<String, MutableList<String>>?): org.schabi.newpipe.extractor.downloader.Response {
        val builder = Request.Builder().url(url).get()
        headers?.forEach { (k, v) -> v.forEach { builder.header(k, it) } }
        if (headers?.containsKey("User-Agent") != true) {
            builder.header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
        }
        val request = builder.build()
        val response = client.newCall(request).execute()
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
