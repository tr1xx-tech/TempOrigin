package com.originos.temporigin

import android.content.Context
import android.os.Build
import androidx.compose.material3.*
import androidx.compose.runtime.staticCompositionLocalOf

// Global CompositionLocal for reactive translations across all Composables
val LocalAppLanguage = staticCompositionLocalOf { "ru" }

object ThemeHelper {
    fun getColorScheme(context: Context, darkTheme: Boolean): ColorScheme {
        val dynamicColor = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        return when {
            dynamicColor && darkTheme -> dynamicDarkColorScheme(context)
            dynamicColor && !darkTheme -> dynamicLightColorScheme(context)
            darkTheme -> darkColorScheme()
            else -> lightColorScheme()
        }
    }
}
