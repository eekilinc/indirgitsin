package com.indirgitsin.app.data.extractor

import android.content.Context
import com.zemer.cipher.CipherDeobfuscator
import com.zemer.cipher.ZemerCipher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object ZemerCipherHelper {

    private var initialized = false

    suspend fun initialize(context: Context) = withContext(Dispatchers.IO) {
        if (initialized) return@withContext
        try {
            // ZemerCipher.initialize -> CipherDeobfuscator + PlayerConfigStore + PlayerDatesStore
            ZemerCipher.initialize(context.applicationContext)
            initialized = true
        } catch (e: Exception) {
            initialized = false
        }
    }

    // SABR URL (serverAbrStreamingUrl + itag) içindeki n-parametresini dönüştür.
    // zemer-cipher CipherDeobfuscator.transformNParamInUrl(url) -> n-transform + 403/limit aşımı.
    // deobfuscateStreamUrl, signatureCipher (s/sp/url) string'i ister; SABR URL'si zaten tam URL
    // olduğu için n-transform yeterlidir.
    suspend fun deobfuscateSabrUrlWithItag(baseSabrUrl: String, itag: String): String = withContext(Dispatchers.IO) {
        val urlWithItag = if (baseSabrUrl.contains("?")) "$baseSabrUrl&itag=$itag" else "$baseSabrUrl?itag=$itag"
        try {
            val transformed = CipherDeobfuscator.transformNParamInUrl(urlWithItag)
            if (transformed.isNullOrBlank()) urlWithItag else transformed
        } catch (e: Exception) {
            urlWithItag
        }
    }
}