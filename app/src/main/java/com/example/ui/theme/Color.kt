package com.example.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import android.content.Context
import android.content.SharedPreferences

enum class ThemePalette {
    SILVER_MONOCHROME
}

object ThemeManager {
    private var prefs: SharedPreferences? = null
    var isDarkTheme by mutableStateOf(true)

    fun init(context: Context) {
        prefs = context.getSharedPreferences("theme_prefs", Context.MODE_PRIVATE)
        isDarkTheme = prefs?.getBoolean("is_dark_theme", true) ?: true
    }

    fun setTheme(palette: ThemePalette) {
        // Kept for backwards compatibility
    }

    fun toggleTheme() {
        isDarkTheme = !isDarkTheme
        prefs?.edit()?.putBoolean("is_dark_theme", isDarkTheme)?.apply()
    }
}

val Black = Color(0xFF000000)
val DarkGray = Color(0xFF111111)
val MediumGray = Color(0xFF333333)
val Silver = Color(0xFFC0C0C0)
val LightSilver = Color(0xFFE0E0E0)
val White = Color(0xFFFFFFFF)

// Stunning Premium Metallic Gold Palette
val GoldLight = Color(0xFFF1E5AC)
val GoldBase = Color(0xFFD4AF37)
val GoldDark = Color(0xFFAA7C11)
val GoldSheen = Color(0xFFFFF3CC)
val GoldFinish = Color(0xFFC5A059)

val GoldMetallicBrush = Brush.linearGradient(
    colors = listOf(
        GoldLight,
        GoldBase,
        GoldDark,
        GoldSheen,
        GoldFinish
    )
)

// Dynamic Backwards Compatibility bindings linked directly to ThemeManager state
val CyanPrimary: Color get() = if (ThemeManager.isDarkTheme) Silver else GoldBase
val CyanPrimaryContainer: Color get() = if (ThemeManager.isDarkTheme) MediumGray else Color(0xFFFDFBF7)
val BlueSecondary: Color get() = if (ThemeManager.isDarkTheme) LightSilver else GoldDark
val BlueSecondaryContainer: Color get() = if (ThemeManager.isDarkTheme) DarkGray else Color(0xFFFFFBF0)
val ObsidianBackground: Color get() = if (ThemeManager.isDarkTheme) Black else Color(0xFFFBFBFB)
val DarkSurface: Color get() = if (ThemeManager.isDarkTheme) DarkGray else Color(0xFFF4F4F4)
val DarkSurfaceCard: Color get() = if (ThemeManager.isDarkTheme) DarkGray else Color(0xFFFFFFFF)
val CyberRed: Color get() = if (ThemeManager.isDarkTheme) White else Color(0xFFD32F2F)
val TextPrimary: Color get() = if (ThemeManager.isDarkTheme) White else Color(0xFF1E1E1E)
val TextSecondary: Color get() = if (ThemeManager.isDarkTheme) Silver else Color(0xFF6B6B6B)
val BorderColor: Color get() = if (ThemeManager.isDarkTheme) MediumGray else Color(0xFFE5E5E5)
val ChromeYellow: Color get() = if (ThemeManager.isDarkTheme) White else GoldBase
val ChromeYellowContainer: Color get() = if (ThemeManager.isDarkTheme) MediumGray else Color(0xFFFFFDF5)
val CherryRed: Color get() = if (ThemeManager.isDarkTheme) White else Color(0xFFC62828)
val CyberGold: Color get() = if (ThemeManager.isDarkTheme) Silver else GoldFinish
val ElectricOrange: Color get() = if (ThemeManager.isDarkTheme) Silver else Color(0xFFE65100)
val DarkChocolateBg: Color get() = if (ThemeManager.isDarkTheme) Black else Color(0xFFFDFDFD)
val ChocolateSurface: Color get() = if (ThemeManager.isDarkTheme) DarkGray else Color(0xFFF5F5F5)
val ChocolateSurfaceCard: Color get() = if (ThemeManager.isDarkTheme) DarkGray else Color(0xFFFFFFFF)
val ChocolateBorder: Color get() = if (ThemeManager.isDarkTheme) MediumGray else Color(0xFFE0E0E0)
val ElectricGreen: Color get() = if (ThemeManager.isDarkTheme) Silver else Color(0xFF2E7D32)
val PureWhiteBg: Color get() = White
val LightSurface: Color get() = LightSilver
val LightSurfaceCard: Color get() = Silver
val LightBorder: Color get() = MediumGray
val TextPrimaryDark: Color get() = White
val TextSecondaryDark: Color get() = Silver
val TextPrimaryLight: Color get() = Black
val TextSecondaryLight: Color get() = MediumGray
val NeonGreen: Color get() = if (ThemeManager.isDarkTheme) White else Color(0xFF388E3C)


