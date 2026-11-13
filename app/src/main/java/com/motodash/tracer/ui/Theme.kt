package com.motodash.tracer.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** สีตามดีไซน์ (ธีมมืด คอนทราสต์สูง อ่านกลางแดด) */
object C {
    val Bg = Color(0xFF07090B)
    val Bar = Color(0xFF0B0D10)
    val Panel = Color(0xFF12161B)
    val Line = Color(0xFF222831)
    val Text = Color(0xFFF2F4F7)
    val Muted = Color(0xFFAEB7C2)
    val Dim = Color(0xFF8C96A3)
    val Accent = Color(0xFFFFB020)
    val Info = Color(0xFF4FC3F7)
    val Warn = Color(0xFFFF8A3D)
    val Alert = Color(0xFFFF4D4D)
    val RecBg = Color(0xFF2A1416)
    val RecText = Color(0xFFFF8080)
    val TabActive = Color(0xFF171C22)
    val Btn = Color(0xFF1A1F26)
    val BtnBorder = Color(0xFF2E3640)
    val Placeholder = Color(0xFF1A2029)
    val NavGreen = Color(0xFF0E3B2E)
}

@Composable
fun MotoTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = C.Accent,
            onPrimary = C.Bg,
            background = C.Bg,
            onBackground = C.Text,
            surface = C.Panel,
            onSurface = C.Text,
            surfaceVariant = C.Btn,
            onSurfaceVariant = C.Muted,
        ),
        content = content,
    )
}
