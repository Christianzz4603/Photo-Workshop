package com.photo.workspace.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val PhotoWorkspaceDarkColorScheme = darkColorScheme(
    primary = VioletPrimary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF4C1D95),
    onPrimaryContainer = Color(0xFFDDD6FE),
    secondary = CyanAccent,
    onSecondary = Color.Black,
    secondaryContainer = Color(0xFF164E63),
    onSecondaryContainer = Color(0xFFCFFAFE),
    tertiary = GoldAccent,
    onTertiary = Color.Black,
    background = StudioObsidian,
    onBackground = TextPrimary,
    surface = StudioSurface,
    onSurface = TextPrimary,
    surfaceVariant = StudioCard,
    onSurfaceVariant = TextSecondary,
    outline = StudioBorder
)

@Composable
fun PhotoWorkspaceTheme(
    darkTheme: Boolean = true, // Creative studio defaults to rich dark UI
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = PhotoWorkspaceDarkColorScheme,
        typography = Typography,
        content = content
    )
}
