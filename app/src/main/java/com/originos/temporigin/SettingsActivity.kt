package com.originos.temporigin

import android.content.Context
import android.os.Bundle
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import rikka.shizuku.Shizuku

class SettingsActivity : ComponentActivity(), Shizuku.OnRequestPermissionResultListener {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        try {
            Shizuku.addRequestPermissionResultListener(this)
        } catch (e: Exception) {
            // Ignore if Shizuku is not available
        }

        enableEdgeToEdge()
        setContent {
            val context = LocalContext.current
            val prefs = remember { context.getSharedPreferences("dsu_helper_prefs", Context.MODE_PRIVATE) }
            
            // Reactive states from SharedPreferences
            var appTheme by remember { mutableStateOf(prefs.getString("theme", "auto") ?: "auto") }
            var appLang by remember { mutableStateOf(prefs.getString("lang", "ru") ?: "ru") }
            
            val systemDark = isSystemInDarkTheme()
            val darkTheme = when (appTheme) {
                "light" -> false
                "dark" -> true
                else -> systemDark
            }
            
            val colors = ThemeHelper.getColorScheme(context, darkTheme)
            
            // Listener for preferences updates
            DisposableEffect(prefs) {
                val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                    if (key == "theme") {
                        appTheme = prefs.getString("theme", "auto") ?: "auto"
                    } else if (key == "lang") {
                        appLang = prefs.getString("lang", "ru") ?: "ru"
                    }
                }
                prefs.registerOnSharedPreferenceChangeListener(listener)
                onDispose {
                    prefs.unregisterOnSharedPreferenceChangeListener(listener)
                }
            }
            
            MaterialTheme(colorScheme = colors) {
                androidx.compose.runtime.CompositionLocalProvider(LocalAppLanguage provides appLang) {
                    SettingsScreen(
                        currentTheme = appTheme,
                        onThemeChange = { theme ->
                            prefs.edit().putString("theme", theme).apply()
                        },
                        currentLang = appLang,
                        onLangChange = { lang ->
                            prefs.edit().putString("lang", lang).apply()
                        },
                        onBackClick = { finish() }
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            Shizuku.removeRequestPermissionResultListener(this)
        } catch (e: Exception) {
            // Safe ignore
        }
    }

    override fun onRequestPermissionResult(requestCode: Int, grantResult: Int) {
        if (requestCode == 1001) {
            if (grantResult == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Разрешение Shizuku предоставлено!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Разрешение Shizuku отклонено", Toast.LENGTH_SHORT).show()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    currentTheme: String,
    onThemeChange: (String) -> Unit,
    currentLang: String,
    onLangChange: (String) -> Unit,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    var isShizukuRunning by remember { mutableStateOf(false) }
    var hasShizukuPermission by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        while (true) {
            isShizukuRunning = try {
                Shizuku.pingBinder()
            } catch (e: Exception) {
                false
            }
            
            hasShizukuPermission = try {
                isShizukuRunning && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
            } catch (e: Exception) {
                false
            }
            
            kotlinx.coroutines.delay(2000)
        }
    }

    Scaffold(
        topBar = {
            MediumTopAppBar(
                title = { 
                    Text(
                        loc(LocString.SETTINGS_TITLE),
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.mediumTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.primary
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Shizuku Permission Box Card
            ShizukuPermissionCard(
                isShizukuRunning = isShizukuRunning,
                hasPermission = hasShizukuPermission,
                onRequestPermission = {
                    try {
                        Shizuku.requestPermission(1001)
                    } catch (e: Exception) {
                        Toast.makeText(context, "Ошибка запроса: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            )

            // 2. Settings Options and About Card
            SettingsAndAboutCard(
                currentTheme = currentTheme,
                onThemeChange = onThemeChange,
                currentLang = currentLang,
                onLangChange = onLangChange
            )
        }
    }
}
