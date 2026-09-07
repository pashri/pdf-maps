package com.pashri.pdfmaps.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val LightColours = lightColorScheme(
    primary = Color(0xFF1F3A5F),
    secondary = Color(0xFFF2A65A),
)

private val DarkColours = darkColorScheme(
    primary = Color(0xFFA9C4E8),
    secondary = Color(0xFFF2A65A),
)

/**
 * The app's Material 3 theme, following the system light/dark
 * setting and using dynamic colour where the platform offers it.
 *
 * @param darkTheme Whether to use the dark palette.
 * @param content Composable content to theme.
 */
@Composable
fun PdfMapsTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colours = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context)
            else dynamicLightColorScheme(context)

        darkTheme -> DarkColours
        else -> LightColours
    }

    MaterialTheme(colorScheme = colours, content = content)
}
