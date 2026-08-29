package com.indirgitsin.app.ui.screen

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

@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    fun yakinda() = Toast.makeText(context, "Yakında", Toast.LENGTH_SHORT).show()
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Ayarlar", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold)
        Text("Premium deneyim • YouTube & Spotify tarzı", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

        PremiumSettingCard(
            icon = Icons.Rounded.Folder,
            title = "İndirme Konumu",
            subtitle = "İndirilenler klasörü • /Download",
            action = { TextButton(onClick = { yakinda() }) { Text("Aç", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold) } }
        )
        PremiumSettingCard(
            icon = Icons.Rounded.HighQuality,
            title = "Varsayılan Kalite",
            subtitle = "En yüksek kaliteyi otomatik seç",
            action = { Switch(checked = true, onCheckedChange = { yakinda() }) }
        )
        PremiumSettingCard(
            icon = Icons.Rounded.AudioFile,
            title = "Ses Formatı",
            subtitle = "M4A (önerilen) • MP3 dönüşümü yakında",
            action = { Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.primary) { Text(" M4A ", modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), color = Color.White, style = MaterialTheme.typography.labelSmall) } }
        )
        PremiumSettingCard(
            icon = Icons.Rounded.Shield,
            title = "Gizlilik",
            subtitle = "Geçmiş sadece cihazında saklanır • 0 iz",
            action = { Icon(Icons.Rounded.VerifiedUser, null, tint = MaterialTheme.colorScheme.primary) }
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
