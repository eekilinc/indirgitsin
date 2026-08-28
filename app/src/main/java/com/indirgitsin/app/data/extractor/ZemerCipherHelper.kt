package com.indirgitsin.app.data.extractor

import android.content.Context
import com.zemer.cipher.CipherDeobfuscator
import com.zemer.cipher.PlayerConfigStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

object ZemerCipherHelper {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private var initialized = false

    suspend fun initialize(context: Context) {
        if (initialized) return
        await(PlayerConfigStore.initialize(context, null, true))
        initialized = true
    }

    private suspend fun await(task: PlayerConfigStore.InitializeTask) = withContext(Dispatchers.IO) {
        task.await()
    }

    // SABR URL'yi (serverAbrStreamingUrl) CipherDeobfuscator ile çöz
    // zemer-cipher: CipherDeobfuscator.deobfuscateStreamUrl(sabrUrl) -> gerçek indirme URL'si
    suspend fun deobfuscateSabrUrl(sabrUrl: String): String = withContext(Dispatchers.IO) {
        try {
            CipherDeobfuscator.deobfuscateStreamUrl(sabrUrl)
        } catch (e: Exception) {
            sabrUrl // fallback: orijinal URL
        }
    }

    // Adaptive format'lerden itag bazlı SABR URL oluştur + çöz
    suspend fun deobfuscateSabrUrlWithItag(baseSabrUrl: String, itag: String): String = withContext(Dispatchers.IO) {
        val urlWithItag = if (baseSabrUrl.contains("?")) "$baseSabrUrl&itag=$itag" else "$baseSabrUrl?itag=$itag"
        try {
            CipherDeobfuscator.deobfuscateStreamUrl(urlWithItag)
        } catch (e: Exception) {
            urlWithItag
        }
    }

    // Player config store durumu
    fun isInitialized(): Boolean = initialized
}