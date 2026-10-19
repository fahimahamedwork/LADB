package com.ladb.reborn.ui.theme

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

private val Teal = Color(0xFF2FB89A)
private val TealDark = Color(0xFF1F8E77)

private val LightScheme = lightColorScheme(
    primary = TealDark,
    secondary = Color(0xFF4A635C),
    tertiary = Color(0xFF3E6373),
)

private val DarkScheme = darkColorScheme(
    primary = Teal,
    secondary = Color(0xFFB1CCC2),
    tertiary = Color(0xFFB4CBE0),
    background = Color(0xFF101413),
    surface = Color(0xFF101413),
)

@Composable
fun LadbTheme(themeMode: Int, content: @Composable () -> Unit) {
    val dark = when (themeMode) {
        1 -> false
        2 -> true
        else -> isSystemInDarkTheme()
    }
    val context = LocalContext.current
    val scheme = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        if (dark) DarkScheme else LightScheme
    }
    MaterialTheme(colorScheme = scheme, content = content)
}

/** Colors of the terminal viewport itself (always dark, regardless of app theme). */
object TerminalPalette {
    const val BACKGROUND = 0xFF0C1210.toInt()
}
