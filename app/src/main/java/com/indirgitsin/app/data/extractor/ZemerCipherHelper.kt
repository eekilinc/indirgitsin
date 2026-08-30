package com.indirgitsin.app.data.extractor

import android.content.Context
import com.zemer.cipher.CipherDeobfuscator
import com.zemer.cipher.ZemerCipher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object ZemerCipherHelper {
    @Volatile private var initialized = false

    @Synchronized
    fun initialize(context: Context) {
        if (!initialized) {
            ZemerCipher.initialize(context.applicationContext)
            initialized = true
        }
    }

    suspend fun signatureTimestamp(): Int? = CipherDeobfuscator.signatureTimestamp()

    suspend fun resolve(rawUrl: String, signatureCipher: String, videoId: String): String? =
        withContext(Dispatchers.Main) {
            try {
                val url = rawUrl.takeIf { it.isNotBlank() } ?: signatureCipher.takeIf { it.isNotBlank() }?.let {
                    CipherDeobfuscator.deobfuscateStreamUrl(it, videoId)
                } ?: return@withContext null
                CipherDeobfuscator.transformNParamInUrl(url)
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { null }
        }
}
