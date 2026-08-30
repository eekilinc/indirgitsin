package com.indirgitsin.app.ui.screen

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.navigation.NavController
import com.indirgitsin.app.ui.navigation.Screen
import java.io.File
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.*

data class DownloadedFile(
    val name: String,
    val uri: Uri,
    val sizeBytes: Long,
    val dateMillis: Long,
    val mimeType: String,
    val file: File? = null
)

data class ActiveDownload(
    val id: Long,
    val name: String,
    val bytesDownloaded: Long,
    val totalBytes: Long,
    val status: Int
)

@Composable
fun DownloadsScreen(navController: NavController) {
    val context = LocalContext.current
    var files by remember { mutableStateOf<List<DownloadedFile>>(emptyList()) }
    var active by remember { mutableStateOf<List<ActiveDownload>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    fun refresh() {
        loading = true
        val activeNow = scanActiveDownloads(context)
        active = activeNow
        val allFiles = scanDownloads(context)
        // Bitmeden asagiya ekleme: aktif isimde olan dosyalari tamamlanan listeden cikar
        val activeNames = activeNow.map { it.name }.toSet()
        files = allFiles.filterNot { f -> activeNames.contains(f.name) }
        loading = false
    }

    LaunchedEffect(Unit) { refresh() }
    // Aktif indirmeler varsa her saniye yenile
    LaunchedEffect(active.isNotEmpty()) {
        while (active.isNotEmpty()) {
            kotlinx.coroutines.delay(1000)
            active = scanActiveDownloads(context)
            if (active.isEmpty()) {
                val allFiles = scanDownloads(context)
                val activeNames = active.map { it.name }.toSet()
                files = allFiles.filterNot { f -> activeNames.contains(f.name) }
            }
        }
    }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f)) {
                Text("İndirilenler", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold)
                Text("${files.size} dosya • İndirilenler klasörü", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = { refresh() }) { Icon(Icons.Rounded.Refresh, contentDescription = "Yenile") }
            if (files.isNotEmpty()) {
                IconButton(onClick = {
                    // Dosya yöneticisini aç
                    try {
                        val intent = Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(Uri.parse(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).toString()), "resource/folder")
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(Intent.createChooser(intent, "Klasörü aç"))
                    } catch (_: Exception) {
                        Toast.makeText(context, "Dosya yöneticisi bulunamadı", Toast.LENGTH_SHORT).show()
                    }
                }) { Icon(Icons.Rounded.FolderOpen, contentDescription = "Klasör") }
            }
        }

        // Aktif indirmeler - en ustte, Spotify tarzı
        if (active.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("İndiriliyor • ${active.size} dosya", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                active.forEach { item ->
                    ActiveDownloadCard(item, onCancel = {
                        try {
                            val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as android.app.DownloadManager
                            dm.remove(item.id)
                            Toast.makeText(context, "İptal edildi: ${item.name}", Toast.LENGTH_SHORT).show()
                        } catch (_: Exception) {}
                        val an = scanActiveDownloads(context)
                        active = an
                        val all = scanDownloads(context)
                        val anNames = an.map { it.name }.toSet()
                        files = all.filterNot { f -> anNames.contains(f.name) }
                    })
                }
            }
        }

        if (loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else if (files.isEmpty() && active.isEmpty()) {
            Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), modifier = Modifier.size(64.dp)) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            Icon(Icons.Rounded.DownloadDone, null, modifier = Modifier.size(32.dp), tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                    Text("Henüz indirme yok", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Text("YouTube linkini yapıştırıp bir kalite seçtiğinde dosyalar buraya gelecek.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(onClick = { refresh() }, shape = RoundedCornerShape(20.dp)) { Text("Yenile") }
                }
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxSize()) {
                items(files, key = { it.name + it.dateMillis }) { item ->
                    DownloadCard(item = item, onPlay = {
                        val encodedUri = URLEncoder.encode(item.uri.toString(), StandardCharsets.UTF_8.toString())
                        navController.navigate(com.indirgitsin.app.ui.navigation.Screen.Player.createRoute(encodedUri, item.name))
                    }, onShare = { shareFile(context, item) }, onDelete = {
                        deleteFile(context, item)
                        refresh()
                    })
                }
            }
        }
    }
}

@Composable
private fun DownloadCard(item: DownloadedFile, onPlay: () -> Unit, onShare: () -> Unit, onDelete: () -> Unit) {
    val sizeText = formatSize(item.sizeBytes)
    val dateText = formatDate(item.dateMillis)
    val ext = item.name.substringAfterLast('.', "").uppercase()
    val isAudioExt = ext == "M4A" || ext == "MP3" || ext == "OPUS" || ext == "AAC" || ext == "FLAC"
    val isVideo = !isAudioExt && (item.mimeType.startsWith("video") || item.name.endsWith(".mp4", true) || item.name.endsWith(".webm", true) || item.name.endsWith(".mkv", true))
    val typeLabel = when {
        isAudioExt -> "SES"
        isVideo -> "VİDEO"
        else -> "DOSYA"
    }
    val typeColor = if (isVideo) MaterialTheme.colorScheme.primary else Color(0xFF0F9D58)
    val qualityFromName = Regex("_(\\d{3,4}p|\\d+kbps|\\d+k)_?").find(item.name)?.groupValues?.getOrNull(1)?.uppercase() ?: ""
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(12.dp), color = typeColor.copy(alpha = 0.12f), modifier = Modifier.size(48.dp)) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Icon(if (isVideo) Icons.Rounded.VideoFile else Icons.Rounded.AudioFile, null, tint = typeColor)
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(item.name, maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(shape = RoundedCornerShape(6.dp), color = typeColor, modifier = Modifier) {
                        Text(typeLabel, color = Color.White, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                    }
                    Surface(shape = RoundedCornerShape(6.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                        Text(ext.ifBlank { "?" }, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                    }
                    if (qualityFromName.isNotBlank()) {
                        Surface(shape = RoundedCornerShape(6.dp), color = MaterialTheme.colorScheme.tertiaryContainer) {
                            Text(qualityFromName, color = MaterialTheme.colorScheme.onTertiaryContainer, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                        }
                    }
                }
                Text("$sizeText • $dateText", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onPlay) { Icon(Icons.Rounded.PlayArrow, contentDescription = "Oynat", tint = MaterialTheme.colorScheme.primary) }
            IconButton(onClick = onShare) { Icon(Icons.Rounded.Share, contentDescription = "Paylaş") }
            IconButton(onClick = onDelete) { Icon(Icons.Rounded.Delete, contentDescription = "Sil", tint = MaterialTheme.colorScheme.error) }
        }
    }
}

private fun scanDownloads(context: Context): List<DownloadedFile> {
    val result = mutableListOf<DownloadedFile>()
    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val projection = arrayOf(
                MediaStore.Downloads.DISPLAY_NAME,
                MediaStore.Downloads.SIZE,
                MediaStore.Downloads.DATE_MODIFIED,
                MediaStore.Downloads.MIME_TYPE,
                MediaStore.Downloads._ID
            )
            val selection = "${MediaStore.Downloads.RELATIVE_PATH} LIKE ?"
            val args = arrayOf("%Download%")
            context.contentResolver.query(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                projection, selection, args, "${MediaStore.Downloads.DATE_MODIFIED} DESC"
            )?.use { cursor ->
                val nameIdx = cursor.getColumnIndexOrThrow(MediaStore.Downloads.DISPLAY_NAME)
                val sizeIdx = cursor.getColumnIndexOrThrow(MediaStore.Downloads.SIZE)
                val dateIdx = cursor.getColumnIndexOrThrow(MediaStore.Downloads.DATE_MODIFIED)
                val mimeIdx = cursor.getColumnIndexOrThrow(MediaStore.Downloads.MIME_TYPE)
                val idIdx = cursor.getColumnIndexOrThrow(MediaStore.Downloads._ID)
                while (cursor.moveToNext()) {
                    val name = cursor.getString(nameIdx) ?: continue
                    if (!name.endsWith(".mp4", true) && !name.endsWith(".m4a", true) && !name.endsWith(".mp3", true) && !name.endsWith(".webm", true) && !name.endsWith(".mkv", true)) continue
                    val size = cursor.getLong(sizeIdx)
                    val dateSec = cursor.getLong(dateIdx)
                    val mime = cursor.getString(mimeIdx) ?: "video/mp4"
                    val id = cursor.getLong(idIdx)
                    val uri = Uri.withAppendedPath(MediaStore.Downloads.EXTERNAL_CONTENT_URI, id.toString())
                    result.add(DownloadedFile(name, uri, size, dateSec * 1000, mime))
                }
            }
        } else {
            @Suppress("DEPRECATION")
            val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            dir.listFiles()?.sortedByDescending { it.lastModified() }?.forEach { f ->
                if (f.isFile && (f.name.endsWith(".mp4", true) || f.name.endsWith(".m4a", true) || f.name.endsWith(".mp3", true) || f.name.endsWith(".webm", true))) {
                    val mime = when {
                        f.name.endsWith(".mp3", true) -> "audio/mpeg"
                        f.name.endsWith(".m4a", true) -> "audio/mp4"
                        f.name.endsWith(".webm", true) -> "video/webm"
                        else -> "video/mp4"
                    }
                    result.add(DownloadedFile(f.name, Uri.fromFile(f), f.length(), f.lastModified(), mime, f))
                }
            }
        }
    } catch (_: Exception) {}
    return result
}

private fun scanActiveDownloads(context: Context): List<ActiveDownload> {
    val result = mutableListOf<ActiveDownload>()
    try {
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as android.app.DownloadManager
        val q = android.app.DownloadManager.Query().setFilterByStatus(android.app.DownloadManager.STATUS_RUNNING or android.app.DownloadManager.STATUS_PAUSED or android.app.DownloadManager.STATUS_PENDING)
        dm.query(q)?.use { c ->
            val idIdx = c.getColumnIndexOrThrow(android.app.DownloadManager.COLUMN_ID)
            val titleIdx = c.getColumnIndexOrThrow(android.app.DownloadManager.COLUMN_TITLE)
            val bytesIdx = c.getColumnIndexOrThrow(android.app.DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
            val totalIdx = c.getColumnIndexOrThrow(android.app.DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
            val statusIdx = c.getColumnIndexOrThrow(android.app.DownloadManager.COLUMN_STATUS)
            while (c.moveToNext()) {
                val id = c.getLong(idIdx)
                val name = c.getString(titleIdx) ?: "İndiriliyor"
                val dl = c.getLong(bytesIdx)
                val total = c.getLong(totalIdx)
                val status = c.getInt(statusIdx)
                result.add(ActiveDownload(id, name, dl, total, status))
            }
        }
    } catch (_: Exception) {}
    return result
}

@Composable
private fun ActiveDownloadCard(item: ActiveDownload, onCancel: () -> Unit) {
    val progress = if (item.totalBytes > 0) item.bytesDownloaded.toFloat() / item.totalBytes else 0f
    val statusText = when (item.status) {
        android.app.DownloadManager.STATUS_RUNNING -> "İndiriliyor"
        android.app.DownloadManager.STATUS_PAUSED -> "Duraklatıldı"
        android.app.DownloadManager.STATUS_PENDING -> "Beklemede"
        else -> "İşleniyor"
    }
    Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(progress = { progress.coerceIn(0f, 1f) }, modifier = Modifier.size(24.dp), strokeWidth = 3.dp)
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(item.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                    Text("$statusText • ${formatSize(item.bytesDownloaded)} / ${if (item.totalBytes>0) formatSize(item.totalBytes) else "?"}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f))
                }
                Text("${(progress*100).toInt()}%", fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.labelLarge)
                IconButton(onClick = onCancel) { Icon(Icons.Rounded.Close, contentDescription = "İptal", tint = MaterialTheme.colorScheme.onPrimaryContainer) }
            }
            LinearProgressIndicator(progress = { progress.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)))
        }
    }
}

private fun playFile(context: Context, item: DownloadedFile) {
    try {
        val uri = if (item.file != null) {
            try { FileProvider.getUriForFile(context, "${context.packageName}.provider", item.file) } catch (_: Exception) { item.uri }
        } else item.uri
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, item.mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(Intent.createChooser(intent, "Oynat: ${item.name}"))
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, "Oynatıcı bulunamadı", Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
        Toast.makeText(context, "Açılamadı: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}

private fun shareFile(context: Context, item: DownloadedFile) {
    try {
        val uri = if (item.file != null) {
            try { FileProvider.getUriForFile(context, "${context.packageName}.provider", item.file) } catch (_: Exception) { item.uri }
        } else item.uri
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = item.mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Paylaş: ${item.name}"))
    } catch (e: Exception) {
        Toast.makeText(context, "Paylaşılamadı: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}

private fun deleteFile(context: Context, item: DownloadedFile) {
    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && item.file == null) {
            context.contentResolver.delete(item.uri, null, null)
        } else {
            item.file?.delete()
        }
        Toast.makeText(context, "Silindi: ${item.name}", Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
        Toast.makeText(context, "Silinemedi: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}

private fun formatSize(bytes: Long): String = when {
    bytes >= 1_000_000_000 -> String.format("%.1f GB", bytes / 1_000_000_000.0)
    bytes >= 1_000_000 -> String.format("%.1f MB", bytes / 1_000_000.0)
    bytes >= 1_000 -> String.format("%.0f KB", bytes / 1_000.0)
    bytes == 0L -> "—"
    else -> "$bytes B"
}

private fun formatDate(millis: Long): String = try {
    SimpleDateFormat("dd MMM yyyy HH:mm", Locale("tr", "TR")).format(Date(millis))
} catch (_: Exception) { "" }
