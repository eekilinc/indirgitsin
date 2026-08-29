package com.indirgitsin.app.data.extractor

import android.content.Context
import com.zemer.cipher.ZemerCipher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object ZemerCipherHelper {

    private var initialized = false

    suspend fun initialize(context: Context) {
        if (initialized) return
        try {
            ZemerCipher.initialize(context.applicationContext)
            initialized = true
        } catch (e: Throwable) {
            initialized = false
        }
    }

    suspend fun deobfuscateSabrUrlWithItag(baseSabrUrl: String, itag: String): String {
        val urlWithItag = if (baseSabrUrl.contains("?")) "$baseSabrUrl&itag=$itag" else "$baseSabrUrl?itag=$itag"
        if (!initialized) return urlWithItag
        return try {
            // transformNParamInUrl WebView olusturur → Main thread gerekli
            val transformed = withContext(Dispatchers.Main) {
                com.zemer.cipher.CipherDeobfuscator.transformNParamInUrl(urlWithItag)
            }
            if (transformed.isNullOrBlank()) urlWithItag else transformed
        } catch (e: Throwable) {
            urlWithItag
        }
    }
}