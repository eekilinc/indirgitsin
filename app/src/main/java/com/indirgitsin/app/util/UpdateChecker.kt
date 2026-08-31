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

object UpdateChecker {
    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    data class UpdateInfo(val latestTag: String, val htmlUrl: String, val body: String)
    sealed interface CheckResult {
        data class Available(val info: UpdateInfo) : CheckResult
        data object Current : CheckResult
        data object Unavailable : CheckResult
    }
    const val RELEASES_PAGE = "https://github.com/eekilinc/indirgitsin/releases/latest"

    suspend fun check(context: Context): UpdateInfo? = (checkDetailed(context) as? CheckResult.Available)?.info

    suspend fun checkDetailed(context: Context): CheckResult = withContext(Dispatchers.IO) {
        try {
            val current = try { context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "0" } catch (_: Exception) { "0" }
            val req = Request.Builder()
                .url("https://api.github.com/repos/eekilinc/indirgitsin/releases/latest")
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "IndirGitsin/$current")
                .build()
            val body = client.newCall(req).awaitBody()
            val json = JSONObject(body)
            val tag = json.optString("tag_name", "")
            val htmlUrl = json.optString("html_url", "https://github.com/eekilinc/indirgitsin/releases/latest")
            val notes = json.optString("body", "")
            if (tag.isBlank()) return@withContext CheckResult.Unavailable
            if (VersionComparator.isNewer(tag, current)) CheckResult.Available(UpdateInfo(tag, htmlUrl, notes)) else CheckResult.Current
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { CheckResult.Unavailable }
    }

    fun openUpdatePage(context: Context, info: UpdateInfo) {
        try {
            val trustedUrl = info.htmlUrl.takeIf { it.startsWith("https://github.com/eekilinc/indirgitsin/releases/") } ?: RELEASES_PAGE
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(trustedUrl))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (_: Exception) {}
    }
}
