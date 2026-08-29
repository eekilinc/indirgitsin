package com.indirgitsin.app.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
            val resp = client.newCall(req).execute()
            if (!resp.isSuccessful) return@withContext null
            val body = resp.body?.string() ?: return@withContext null
            resp.close()
            val json = JSONObject(body)
            val tag = json.optString("tag_name", "")
            val htmlUrl = json.optString("html_url", "https://github.com/eekilinc/indirgitsin/releases/latest")
            val notes = json.optString("body", "")
            if (tag.isBlank()) return@withContext null
            if (isNewer(tag, current)) UpdateInfo(tag, htmlUrl, notes) else null
        } catch (_: Exception) { null }
    }

    private fun isNewer(latest: String, current: String): Boolean {
        fun parse(v: String): List<Int> = v.removePrefix("v").split(".", "-").mapNotNull { it.filter { c -> c.isDigit() }.toIntOrNull() }
        val l = parse(latest)
        val c = parse(current)
        for (i in 0 until maxOf(l.size, c.size)) {
            val lv = l.getOrElse(i) { 0 }
            val cv = c.getOrElse(i) { 0 }
            if (lv != cv) return lv > cv
        }
        return latest != current
    }

    fun openUpdatePage(context: Context, info: UpdateInfo) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(info.htmlUrl))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (_: Exception) {}
    }
}
