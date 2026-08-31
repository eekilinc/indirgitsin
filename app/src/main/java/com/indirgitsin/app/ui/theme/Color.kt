package com.indirgitsin.app.ui.theme

import androidx.compose.ui.graphics.Color

// App palette. Legacy symbol names are retained for existing screens.
val YtRed = Color(0xFFED334B)
val YtRedDark = Color(0xFFAF1737)
val YtBlue = Color(0xFF3EA6FF)
val YtBackground = Color(0xFF0F0F0F) // YouTube koyu
val YtSurface = Color(0xFF212121)
val YtSurfaceVariant = Color(0xFF303030)
val YtElevated = Color(0xFF3F3F3F)
val YtTextPrimary = Color(0xFFF1F1F1)
val YtTextSecondary = Color(0xFFAAAAAA)
val YtTextTertiary = Color(0xFF717171)
val YtBorder = Color(0xFF303030)
val YtChipSelected = Color(0xFFF1F1F1)
val YtChipUnselected = Color(0xFF272727)

// Renk seçenekleri için palet
enum class AppColor(val key: String, val primary: Color, val primaryDark: Color, val label: String) {
    RED("red", YtRed, YtRedDark, "Kırmızı"),
    BLUE("blue", Color(0xFF1565C0), Color(0xFF0D47A1), "Mavi"),
    GREEN("green", Color(0xFF2E7D32), Color(0xFF1B5E20), "Yeşil"),
    PURPLE("purple", Color(0xFF6A1B9A), Color(0xFF4A148C), "Mor"),
    ORANGE("orange", Color(0xFFEF6C00), Color(0xFFE65100), "Turuncu");
    companion object { fun fromKey(k: String) = entries.find { it.key == k } ?: RED }
}

// Premium için korunanlar
val PremiumGold = Color(0xFFFFD60A)

// Geriye dönük uyumluluk
val PremiumRed = YtRed
val PremiumRedDark = YtRedDark
val BgDark = YtBackground
val BgDarkSurface = YtSurface
val BgDarkCard = YtSurfaceVariant
val BgDarkElevated = YtElevated
val TextPrimaryDark = YtTextPrimary
val TextSecondaryDark = YtTextSecondary
val BorderDark = YtBorder
