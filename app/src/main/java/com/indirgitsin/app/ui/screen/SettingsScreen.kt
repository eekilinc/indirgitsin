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

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Ayarlar", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold)
        Text("Premium deneyim • YouTube & Spotify tarzı", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

        PremiumSettingCard(
            icon = Icons.Rounded.Folder,
            title = "İndirme Konumu",
            subtitle = "İndirilenler klasörü • /Download",
            action = {
                TextButton(onClick = {
                    try {
                        val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                        val uri = Uri.parse(dir.absolutePath)
                        val intent = Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(uri, "resource/folder")
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(Intent.createChooser(intent, "Klasörü aç"))
                    } catch (e: Exception) {
                        Toast.makeText(context, "/Download klasörü", Toast.LENGTH_SHORT).show()
                    }
                }) { Text("Aç", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold) }
            }
        )
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

        // Hakkinda - GitHub vs
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
                    Text("Hakkında", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                    Text("github.com/eekilinc/indirgitsin • Açık kaynak • v1.0.0", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                    Text("İndir Gitsin v1.0.0", fontWeight = FontWeight.Bold)
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
