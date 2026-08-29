package com.indirgitsin.app.ui.screen

import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.indirgitsin.app.data.SettingsStore
import com.indirgitsin.app.data.history.AppDatabase
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val autoHigh by SettingsStore.autoHighFlow(context).collectAsState(initial = true)
    val audioFormat by SettingsStore.audioFormatFlow(context).collectAsState(initial = "M4A")
    val downloadFolder by SettingsStore.downloadSubfolderFlow(context).collectAsState(initial = "IndirGitsin")
    var showFolderDialog by remember { mutableStateOf(false) }
    var folderInput by remember { mutableStateOf("") }
    val version = remember { try { context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0.0" } catch (_: Exception) { "1.0.0" } }
    var checking by remember { mutableStateOf(false) }
    var manualUpdate by remember { mutableStateOf<com.indirgitsin.app.util.UpdateChecker.UpdateInfo?>(null) }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Ayarlar", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold)
        Text("Premium deneyim • YouTube & Spotify tarzı", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

        PremiumSettingCard(
            icon = Icons.Rounded.Folder,
            title = "İndirme Konumu",
            subtitle = "İndirilenler • /Download/$downloadFolder",
            action = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = {
                        try {
                            val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS + "/$downloadFolder")
                            if (!dir.exists()) dir.mkdirs()
                            val uri = Uri.parse(dir.absolutePath)
                            val intent = Intent(Intent.ACTION_VIEW).apply {
                                setDataAndType(uri, "resource/folder")
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(Intent.createChooser(intent, "Klasörü aç"))
                        } catch (e: Exception) {
                            Toast.makeText(context, "/Download/$downloadFolder", Toast.LENGTH_SHORT).show()
                        }
                    }) { Text("Aç", fontWeight = FontWeight.Bold) }
                    TextButton(onClick = { folderInput = downloadFolder; showFolderDialog = true }) { Text("Değiştir", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold) }
                }
            }
        )
        if (showFolderDialog) {
            AlertDialog(
                onDismissRequest = { showFolderDialog = false },
                title = { Text("Klasör Seç") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("İndirilenler altında oluşturulacak klasör adı:", style = MaterialTheme.typography.bodySmall)
                        OutlinedTextField(value = folderInput, onValueChange = { folderInput = it }, singleLine = true, placeholder = { Text("IndirGitsin") })
                        Text("Örn: IndirGitsin, Muzikler, Videolar", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        scope.launch {
                            val clean = folderInput.take(30).replace(Regex("[\\\\/:*?\"<>|]"), "_").trim().ifBlank { "IndirGitsin" }
                            SettingsStore.setDownloadSubfolder(context, clean)
                            Toast.makeText(context, "Klasör: /Download/$clean", Toast.LENGTH_SHORT).show()
                        }
                        showFolderDialog = false
                    }) { Text("Kaydet") }
                },
                dismissButton = { TextButton(onClick = { showFolderDialog = false }) { Text("İptal") } }
            )
        }
        if (manualUpdate != null) {
            AlertDialog(
                onDismissRequest = { manualUpdate = null },
                title = { Text("Güncelleme var: ${manualUpdate!!.latestTag}") },
                text = { Text(manualUpdate!!.body.take(350).ifBlank { "Yeni sürüm mevcut." }) },
                confirmButton = {
                    TextButton(onClick = {
                        com.indirgitsin.app.util.UpdateChecker.openUpdatePage(context, manualUpdate!!)
                        manualUpdate = null
                    }) { Text("Güncelle") }
                },
                dismissButton = { TextButton(onClick = { manualUpdate = null }) { Text("Kapat") } }
            )
        }
        PremiumSettingCard(
            icon = Icons.Rounded.HighQuality,
            title = "Varsayılan Kalite",
            subtitle = if (autoHigh) "En yüksek kaliteyi otomatik seç" else "Her seferinde sor",
            action = {
                Switch(checked = autoHigh, onCheckedChange = { v ->
                    scope.launch { SettingsStore.setAutoHigh(context, v) }
                    Toast.makeText(context, if (v) "Otomatik yüksek kalite" else "Manuel seçim", Toast.LENGTH_SHORT).show()
                })
            }
        )
        PremiumSettingCard(
            icon = Icons.Rounded.AudioFile,
            title = "Ses Formatı",
            subtitle = "Varsayılan ses çıkışı: $audioFormat",
            action = {
                Surface(
                    onClick = {
                        val next = when (audioFormat) { "M4A" -> "MP3"; "MP3" -> "WEBM"; else -> "M4A" }
                        scope.launch { SettingsStore.setAudioFormat(context, next) }
                        Toast.makeText(context, "Ses formatı: $next", Toast.LENGTH_SHORT).show()
                    },
                    shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.primary
                ) { Text(" $audioFormat ", modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp), color = Color.White, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold) }
            }
        )
        PremiumSettingCard(
            icon = Icons.Rounded.Shield,
            title = "Gizlilik",
            subtitle = "Geçmiş sadece cihazında saklanır • 0 iz",
            action = {
                TextButton(onClick = {
                    scope.launch {
                        try {
                            val db = AppDatabase.get(context)
                            db.historyDao().clearAll()
                            Toast.makeText(context, "Geçmiş temizlendi", Toast.LENGTH_SHORT).show()
                        } catch (e: Exception) {
                            Toast.makeText(context, "Gizlilik: veriler cihazında kalır", Toast.LENGTH_SHORT).show()
                        }
                    }
                }) { Text("Temizle", fontWeight = FontWeight.Bold) }
            }
        )
        PremiumSettingCard(
            icon = Icons.Rounded.SystemUpdate,
            title = "Güncelleme",
            subtitle = "v$version • GitHub'dan denetle",
            action = {
                if (checking) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                else TextButton(onClick = {
                    checking = true
                    scope.launch {
                        val info = com.indirgitsin.app.util.UpdateChecker.check(context)
                        checking = false
                        if (info != null) manualUpdate = info else Toast.makeText(context, "En güncel sürümdesin • v$version", Toast.LENGTH_SHORT).show()
                    }
                }) { Text("Denetle", fontWeight = FontWeight.Bold) }
            }
        )

        // Hakkinda - Program hakkinda + versiyon + github
        Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Info, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text("Program Hakkında", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.weight(1f))
                    Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.primary) { Text(" v$version ", modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp), color = Color.White, style = MaterialTheme.typography.labelSmall) }
                }
                Text(
                    "İndir Gitsin — YouTube ve YouTube Music linklerinden video ve sesleri cihazına hızlıca indir. Gizlilik odaklı, reklamsız, tek dokunuşla. İndirilenler cihazında kalır, iz bırakmaz.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssistChip(onClick = {
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/eekilinc/indirgitsin"))
                            context.startActivity(intent)
                        } catch (_: Exception) {
                            Toast.makeText(context, "github.com/eekilinc/indirgitsin", Toast.LENGTH_SHORT).show()
                        }
                    }, label = { Text("GitHub") }, leadingIcon = { Icon(Icons.Rounded.Code, null, modifier = Modifier.size(16.dp)) })
                    Text("github.com/eekilinc/indirgitsin", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Card(
            onClick = {
                try {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/eekilinc/indirgitsin"))
                    context.startActivity(intent)
                } catch (_: Exception) {
                    Toast.makeText(context, "github.com/eekilinc/indirgitsin", Toast.LENGTH_SHORT).show()
                }
            },
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(1.dp)
        ) {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = RoundedCornerShape(12.dp), color = Color(0xFF24292F), modifier = Modifier.size(40.dp)) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Icon(Icons.Rounded.Code, null, tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Kaynak Kod", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                    Text("github.com/eekilinc/indirgitsin • Açık kaynak", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(Icons.Rounded.OpenInNew, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        PremiumSettingCard(
            icon = Icons.Rounded.Info,
            title = "Lisans",
            subtitle = "MIT • Sadece izinli içerikler için kullanın",
            action = {
                TextButton(onClick = {
                    try {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/eekilinc/indirgitsin/blob/main/LICENSE"))
                        context.startActivity(intent)
                    } catch (_: Exception) {
                        Toast.makeText(context, "MIT Lisansı", Toast.LENGTH_SHORT).show()
                    }
                }) { Text("Gör", fontWeight = FontWeight.Bold) }
            }
        )

        Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Info, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("İndir Gitsin v$version", fontWeight = FontWeight.Bold)
                    Text("Sadece izinli içerikler için kullanın. YouTube ToS'a uyun.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun PremiumSettingCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    action: @Composable () -> Unit = {}
) {
    Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(1.dp)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), modifier = Modifier.size(40.dp)) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            action()
        }
    }
}
