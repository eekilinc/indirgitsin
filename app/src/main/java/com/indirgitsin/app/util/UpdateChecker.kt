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

    suspend fun check(context: Context): UpdateInfo? = withContext(Dispatchers.IO) {
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
            if (tag.isBlank()) return@withContext null
            if (VersionComparator.isNewer(tag, current)) UpdateInfo(tag, htmlUrl, notes) else null
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { null }
    }

    fun openUpdatePage(context: Context, info: UpdateInfo) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(info.htmlUrl))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (_: Exception) {}
    }
}
