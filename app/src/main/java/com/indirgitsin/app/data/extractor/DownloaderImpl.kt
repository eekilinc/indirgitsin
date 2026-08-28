package com.indirgitsin.app.data.extractor

import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

// Artık NewPipe kullanılmadığı için basit bir OkHttp singleton
// İleride tekrar NewPipe eklenirse buraya geri dönebiliriz
object DownloaderImpl {
    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }
}
