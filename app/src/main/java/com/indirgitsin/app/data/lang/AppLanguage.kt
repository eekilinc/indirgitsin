package com.indirgitsin.app.data.lang

import androidx.compose.runtime.compositionLocalOf

val LocalAppLanguage = compositionLocalOf { "tr" }

// Modüler dil sistemi - yeni dil eklemek için sadece map'e ekle
object AppStrings {
    private val tr = mapOf(
        "app_name" to "İndir Gitsin",
        "home" to "Ana Sayfa",
        "library" to "Kitaplık",
        "downloads" to "İndirilenler",
        "settings" to "Ayarlar",
        "paste" to "Yapıştır",
        "resolve" to "Çözümle",
        "download" to "İndir",
        "preview" to "Önizle",
        "video" to "Video",
        "audio" to "Ses",
        "quality" to "Kalite",
        "theme" to "Tema",
        "color" to "Renk",
        "language" to "Dil",
        "download_location" to "İndirme Konumu",
        "default_quality" to "Varsayılan Kalite",
        "audio_format" to "Ses Formatı",
        "privacy" to "Gizlilik",
        "update" to "Güncelleme",
        "about" to "Program Hakkında",
        "all" to "Tümü",
        "music" to "Music",
        "shorts" to "Shorts",
        "select_all" to "Tümünü seç",
        "deselect_all" to "Tümünü kaldır",
        "selected" to "seçili",
        "downloading" to "İndiriliyor",
        "downloaded" to "İndirildi",
        "no_downloads" to "Henüz indirme yok",
        "light" to "Açık",
        "dark" to "Koyu",
        "system" to "Sistem",
        "red" to "Kırmızı",
        "blue" to "Mavi",
        "green" to "Yeşil",
        "purple" to "Mor",
        "orange" to "Turuncu"
    )
    private val en = mapOf(
        "app_name" to "Download Go",
        "home" to "Home",
        "library" to "Library",
        "downloads" to "Downloads",
        "settings" to "Settings",
        "paste" to "Paste",
        "resolve" to "Resolve",
        "download" to "Download",
        "preview" to "Preview",
        "video" to "Video",
        "audio" to "Audio",
        "quality" to "Quality",
        "theme" to "Theme",
        "color" to "Color",
        "language" to "Language",
        "download_location" to "Download Location",
        "default_quality" to "Default Quality",
        "audio_format" to "Audio Format",
        "privacy" to "Privacy",
        "update" to "Update",
        "about" to "About",
        "all" to "All",
        "music" to "Music",
        "shorts" to "Shorts",
        "select_all" to "Select all",
        "deselect_all" to "Deselect all",
        "selected" to "selected",
        "downloading" to "Downloading",
        "downloaded" to "Downloaded",
        "no_downloads" to "No downloads yet",
        "light" to "Light",
        "dark" to "Dark",
        "system" to "System",
        "red" to "Red",
        "blue" to "Blue",
        "green" to "Green",
        "purple" to "Purple",
        "orange" to "Orange"
    )

    private val maps = mapOf("tr" to tr, "en" to en)

    fun get(lang: String, key: String): String = maps[lang]?.get(key) ?: tr[key] ?: key

    // Desteklenen diller
    val supported = listOf("tr" to "Türkçe", "en" to "English")
    // Yeni dil: 1) en gibi map ekle 2) supported'a ekle 3) maps'e ekle - bu kadar
}

// Composable helper
@androidx.compose.runtime.Composable
fun t(key: String): String {
    val lang = LocalAppLanguage.current
    return AppStrings.get(lang, key)
}
