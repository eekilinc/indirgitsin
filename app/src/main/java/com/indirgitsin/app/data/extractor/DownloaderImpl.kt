package com.indirgitsin.app.data.extractor

import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.downloader.Downloader
import java.util.concurrent.TimeUnit

class DownloaderImpl private constructor() : Downloader() {

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    override fun get(url: String, headers: Map<String, List<String>>?): org.schabi.newpipe.extractor.downloader.Response {
        val requestBuilder = okhttp3.Request.Builder().url(url!!).get()
        headers?.forEach { (k, v) -> v.forEach { requestBuilder.addHeader(k, it) } }
        val response = client.newCall(requestBuilder.build()).execute()
        val body = response.body?.string() ?: ""
        val latestUrl = response.request.url.toString()
        val h = mutableMapOf<String, MutableList<String>>()
        response.headers.forEach { (k, v) -> h.getOrPut(k) { mutableListOf() }.add(v) }
        return org.schabi.newpipe.extractor.downloader.Response(response.code, body, h, latestUrl, null)
    }

    override fun post(url: String, headers: Map<String, List<String>>?, data: ByteArray?): org.schabi.newpipe.extractor.downloader.Response {
        val body = data?.toRequestBody()
        val builder = okhttp3.Request.Builder().url(url).post(body!!)
        headers?.forEach { (k, v) -> v.forEach { builder.addHeader(k, it) } }
        val resp = client.newCall(builder.build()).execute()
        val b = resp.body?.string() ?: ""
        val h = mutableMapOf<String, MutableList<String>>()
        resp.headers.forEach { (k, v) -> h.getOrPut(k) { mutableListOf() }.add(v) }
        return org.schabi.newpipe.extractor.downloader.Response(resp.code, b, h, resp.request.url.toString(), null)
    }

    companion object {
        @Volatile private var INSTANCE: DownloaderImpl? = null
        fun getInstance(): DownloaderImpl = INSTANCE ?: synchronized(this) {
            INSTANCE ?: DownloaderImpl().also { INSTANCE = it }
        }
    }
}
