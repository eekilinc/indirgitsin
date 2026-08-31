package com.indirgitsin.app.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException
import com.indirgitsin.app.data.extractor.awaitBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.Cache
import java.io.File

object UpdateChecker {
    private val baseClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .callTimeout(15, TimeUnit.SECONDS)
        .build()
    @Volatile private var cachedClient: OkHttpClient? = null
    private val automaticCheck = Mutex()
    private fun client(context: Context): OkHttpClient = cachedClient ?: synchronized(this) {
        cachedClient ?: baseClient.newBuilder().cache(Cache(File(context.cacheDir, "release-http"), 2L * 1024 * 1024))
            .build().also { cachedClient = it }
    }

    data class UpdateInfo(val latestTag: String, val htmlUrl: String, val body: String)
    sealed interface CheckResult {
        data class Available(val info: UpdateInfo) : CheckResult
        data object Current : CheckResult
        data object Unavailable : CheckResult
    }
    const val RELEASES_PAGE = "https://github.com/eekilinc/indirgitsin/releases/latest"

    suspend fun check(context: Context): UpdateInfo? = withContext(Dispatchers.IO) {
        automaticCheck.withLock {
            val prefs = context.getSharedPreferences("update-check", Context.MODE_PRIVATE)
            val now = System.currentTimeMillis()
            val last = prefs.getLong("lastAttempt", 0)
            if (now >= last && now - last < TimeUnit.HOURS.toMillis(6)) return@withLock null
            prefs.edit().putLong("lastAttempt", now).apply()
            (checkDetailed(context) as? CheckResult.Available)?.info
        }
    }

    suspend fun checkDetailed(context: Context): CheckResult = withContext(Dispatchers.IO) {
        try {
            val current = try { context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "0" } catch (_: Exception) { "0" }
            val req = Request.Builder()
                .url("https://api.github.com/repos/eekilinc/indirgitsin/releases/latest")
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "IndirGitsin/$current")
                .build()
            val body = client(context).newCall(req).awaitBody()
            val json = JSONObject(body)
            val tag = json.optString("tag_name", "")
            val htmlUrl = json.optString("html_url", "https://github.com/eekilinc/indirgitsin/releases/latest")
            val notes = json.optString("body", "")
            if (tag.isBlank()) return@withContext CheckResult.Unavailable
            if (VersionComparator.isNewer(tag, current)) CheckResult.Available(UpdateInfo(tag, htmlUrl, notes)) else CheckResult.Current
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { CheckResult.Unavailable }
    }

    fun clearCache() { cachedClient?.cache?.evictAll() }

    fun openUpdatePage(context: Context, info: UpdateInfo) {
        try {
            val trustedUrl = info.htmlUrl.takeIf { it.startsWith("https://github.com/eekilinc/indirgitsin/releases/") } ?: RELEASES_PAGE
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(trustedUrl))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (_: Exception) {}
    }
}
