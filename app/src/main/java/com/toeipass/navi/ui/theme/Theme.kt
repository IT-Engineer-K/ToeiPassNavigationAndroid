package com.toeipass.navi.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val ToeiGreenDark = Color(0xFF14532D)
val ToeiGreen = Color(0xFF15803D)
val ToeiGreenLight = Color(0xFFDCFCE7)
val ToeiGreenBorder = Color(0xFF86EFAC)
val SlateBg = Color(0xFFF1F5F9)
val SlateText = Color(0xFF0F172A)
val SlateMuted = Color(0xFF475569)
val SlateBorder = Color(0xFFCBD5E1)
val AccentBlue = Color(0xFF0284C7)

private val ToeiLightColorScheme = lightColorScheme(
    primary = ToeiGreen,
    onPrimary = Color.White,
    primaryContainer = ToeiGreenLight,
    onPrimaryContainer = ToeiGreenDark,
    secondary = AccentBlue,
    onSecondary = Color.White,
    background = SlateBg,
    onBackground = SlateText,
    surface = Color.White,
    onSurface = SlateText,
    surfaceVariant = Color(0xFFF8FAFC),
    onSurfaceVariant = SlateMuted,
    outline = SlateBorder
)

@Composable
fun ToeiPassTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ToeiLightColorScheme,
        content = content
    )
}

fun parseHexColor(hex: String, fallback: Color = Color(0xFF475569)): Color {
    return try {
        val cleaned = hex.trim().removePrefix("#")
        val colorInt = when (cleaned.length) {
            6 -> (0xFF000000 or cleaned.toLong(16)).toInt()
            8 -> cleaned.toLong(16).toInt()
            else -> return fallback
        }
        Color(colorInt)
    } catch (_: Exception) {
        fallback
    }
}
