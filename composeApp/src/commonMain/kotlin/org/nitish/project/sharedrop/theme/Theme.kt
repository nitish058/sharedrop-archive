package org.nitish.project.sharedrop.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val ShareDropColors = lightColorScheme(
    primary = Color(0xFF5D93FF),
    onPrimary = Color.White,

    primaryContainer = Color(0xFF639EEE),
    onPrimaryContainer = Color(0xFF001A41),

    secondary = Color(0xFF6891CB),

    surfaceVariant = Color(0xCEE0F1FF),

    background = Color(0xFFF8FAFC),
    surface = Color.White,

    onBackground = Color(0xFF0F172A),
    onSurface = Color(0xFF0F172A)
)
@Composable
fun ShareDropTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = ShareDropColors,
        content = content
    )
}