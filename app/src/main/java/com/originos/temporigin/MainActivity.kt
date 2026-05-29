package com.originos.temporigin

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.draw.scale
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import rikka.shizuku.Shizuku

class MainActivity : ComponentActivity(), Shizuku.OnRequestPermissionResultListener {
    
    override fun onCreate(bundle: Bundle?) {
        super.onCreate(bundle)
        
        try {
            Shizuku.addRequestPermissionResultListener(this)
        } catch (e: Exception) {
            // Shizuku might not be installed or available on the classpath yet
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
                CompositionLocalProvider(LocalAppLanguage provides appLang) {
                    OriginDsuApp()
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

// Hidden system properties accessor
fun getSystemProperty(key: String): String {
    return try {
        val clazz = Class.forName("android.os.SystemProperties")
        val getMethod = clazz.getMethod("get", String::class.java)
        getMethod.invoke(null, key) as String
    } catch (e: Exception) {
        ""
    }
}

// Real-time staged DSU detection status
fun checkDsuStatus(): Boolean {
    return getSystemProperty("gsid.image_installed") == "1" || 
           getSystemProperty("ro.gsid.image_installed") == "1" ||
           getSystemProperty("gsid.image_installed") == "true" || 
           getSystemProperty("ro.gsid.image_installed") == "true"
}

// Bypasses Android 13+ SELinux restrictions by executing gsi_tool via Shizuku shell context
fun isDsuInstalledRobust(context: Context): Boolean {
    try {
        if (Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
            val clazz = Class.forName("rikka.shizuku.Shizuku")
            val method = clazz.getDeclaredMethods().firstOrNull { it.name == "newProcess" }
            if (method != null) {
                method.isAccessible = true
                val process = method.invoke(null, arrayOf("gsi_tool", "status"), null, null) as java.lang.Process
                val stdout = process.inputStream.bufferedReader().use { it.readText() }
                process.waitFor()
                if (stdout.contains("installed", ignoreCase = true) || stdout.contains("running", ignoreCase = true)) {
                    return true
                }
            }
        }
    } catch (e: Exception) {
        // Fallback to properties
    }
    return checkDsuStatus()
}

// Copy to clipboard helper
fun copyToClipboard(context: Context, text: String, label: String = "Команда") {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText("DSU ADB", text)
    clipboard.setPrimaryClip(clip)
    Toast.makeText(context, "$label скопирована в буфер обмена!", Toast.LENGTH_SHORT).show()
}

// Multi-language strings database
enum class LocString(val ru: String, val en: String) {
    APP_TITLE("TempOrigin", "TempOrigin"),
    SETTINGS_TITLE("Настройки", "Settings"),
    SYS_INFO("Информация о системе", "System Information"),
    DEVICE_MODEL("Модель устройства:", "Device Model:"),
    ANDROID_VER("Версия Android:", "Android Version:"),
    SYSTEM("Система:", "System:"),
    SHIZUKU_SERVICE("Сервис Shizuku:", "Shizuku Service:"),
    DYNAMIC_PARTITIONS("Динамические разделы:", "Dynamic Partitions:"),
    DSU_MODE("Режим DSU:", "DSU Mode:"),
    
    SH_ACTIVE_GRANTED("Активен (Разрешено)", "Active (Granted)"),
    SH_ACTIVE_DENIED("Активен (Нет разрешения)", "Active (No Permission)"),
    SH_NOT_RUNNING("Не запущен", "Not Running"),
    
    DSU_ACTIVE("Активен (Вторая система)", "Active (Second System)"),
    DSU_PREPARED("Подготовлен (Ждет перезагрузки)", "Prepared (Pending Reboot)"),
    DSU_INACTIVE("Не активен (Основная система)", "Inactive (Main System)"),
    
    DSU_PARAMS("Параметры DSU Space", "DSU Space Parameters"),
    USERDATA_SIZE("Размер изолированной памяти:", "Isolated Memory Size:"),
    USERDATA_DESC("Будет создано защищенное хранилище выбранного размера для второй системы.", "A protected storage of selected size will be created for the second system."),
    SLOT_NAME("Имя слота DSU", "DSU Slot Name"),
    
    LAUNCH_DSU("Запустить DSU", "Launch DSU"),
    COPY_ADB_CMD("📋 Команды для ручного ввода", "📋 Commands for Manual Entry"),
    
    HOW_IT_WORKS("Как это работает", "How it works"),
    STEPS_DESC(
        "1. Выберите желаемый размер изолированного пространства с помощью слайдера.\n\n" +
        "2. Нажмите «Запустить DSU». Приложение попытается выполнить запуск через Shizuku.\n\n" +
        "3. Если приложение попросит разрешение Shizuku, предоставьте его.\n\n" +
        "4. На экране появится стандартный системный запрос OriginOS на запуск DSU — подтвердите действие отпечатком пальца или паролем.\n\n" +
        "5. В шторке уведомлений начнется процесс подготовки. По завершении нажмите «Перезагрузить».\n\n" +
        "6. Телефон загрузится во вторую чистую изолированную систему. Вы можете вернуться в основную в любой момент, просто выбрав «Перезагрузка» в шторке.",
        "1. Select the desired size of the isolated space using the slider.\n\n" +
        "2. Tap \"Launch DSU\". The app will attempt to launch via Shizuku.\n\n" +
        "3. If the app asks for Shizuku permission, grant it.\n\n" +
        "4. The standard OriginOS system request to launch DSU will appear — confirm using your fingerprint or password.\n\n" +
        "5. The preparation process will begin in the notification drawer. Once completed, tap \"Reboot\".\n\n" +
        "6. The phone will boot into the clean second system. You can return to the main system at any time by selecting \"Reboot\" in the drawer."
    ),
    
    SH_PERM_BOX("Управление разрешением Shizuku", "Shizuku Permission Control"),
    SH_PERM_STATUS("Разрешение Shizuku:", "Shizuku Permission:"),
    SH_PERM_GRANTED("Предоставлено", "Granted"),
    SH_PERM_DENIED("Не предоставлено", "Not Granted"),
    SH_GRANT_BTN("🔑 Предоставить разрешение", "🔑 Grant Permission"),
    SH_GRANTED_BTN("✔️ Разрешение уже выдано", "✔️ Permission Already Granted"),
    
    DSU_PREPARED_TITLE("Система DSU подготовлена", "DSU System Prepared"),
    DSU_PREPARED_DESC(
        "Вторая изолированная система успешно создана и готова к запуску!\n\n" +
        "Пожалуйста, откройте шторку уведомлений вашего телефона и найдите уведомление от Dynamic System. " +
        "Нажмите кнопку «Перезагрузить» на уведомлении, чтобы загрузиться во вторую систему.\n\n" +
        "Если вы хотите удалить созданную вторую систему и очистить память, нажмите кнопку ниже.",
        "The second isolated system has been successfully created and is ready to launch!\n\n" +
        "Please open your phone's notification drawer and find the Dynamic System notification. " +
        "Tap the \"Reboot\" button on the notification to boot into the second system.\n\n" +
        "If you want to delete the created second system and free up memory, tap the button below."
    ),
    DSU_WIPE_BTN("🧹 Удалить созданную DSU", "🧹 Delete Staged DSU"),
    
    ABOUT_SETTINGS("Настройки и О программе", "Settings & About App"),
    SELECT_THEME("Тема оформления:", "App Theme:"),
    THEME_AUTO("Системная", "System"),
    THEME_LIGHT("Светлая", "Light"),
    THEME_DARK("Темная", "Dark"),
    SELECT_LANG("Язык интерфейса:", "App Language:"),
    LANG_RU("Русский", "Russian"),
    LANG_EN("English", "English"),
    
    VERSION("Версия помощника:", "App Version:"),
    AUTHOR("Автор:", "Author:"),
    TG_CHANNEL("Телеграм-канал:", "Telegram Channel:"),
    
    SH_REQ_TITLE("Требуется запущенный Shizuku", "Shizuku Service Required"),
    SH_REQ_DESC(
        "Не удалось запустить DSU автоматически.\n\n" +
        "Для запуска DSU из этого приложения необходимо:\n" +
        "• Запустить менеджер Shizuku на телефоне.\n" +
        "• Выдать разрешение DSU Helper внутри Shizuku.\n\n" +
        "Если вы не используете Shizuku, вы можете скопировать сгенерированную команду и выполнить её вручную на ПК через ADB или прямо на телефоне через LADB.",
        "Failed to launch DSU automatically.\n\n" +
        "To launch DSU from this app you need to:\n" +
        "• Start the Shizuku manager on your phone.\n" +
        "• Grant permission to DSU Helper inside Shizuku.\n\n" +
        "If you do not use Shizuku, you can copy the generated command and execute it manually on PC via ADB or directly on the phone via LADB."
    ),
    COPY_BTN("Скопировать команду", "Copy Command"),
    CANCEL("ОК", "OK"),
    DONE("Готово", "Done"),
    ADB_MANUAL_TITLE("ADB-команды для ручного ввода", "ADB Commands for Manual Entry"),
    ADB_LAUNCH_TITLE("🚀 Команда для запуска DSU:", "🚀 Command to Launch DSU:"),
    ADB_LAUNCH_BTN("Скопировать команду запуска", "Copy Launch Command"),
    ADB_WIPE_TITLE("🧹 Команда для удаления/сброса DSU:", "🧹 Command to Delete/Reset DSU:"),
    ADB_WIPE_BTN("Скопировать команду удаления", "Copy Delete Command")
}

// Dynamic localized string provider
fun getLoc(context: Context, string: LocString): String {
    val prefs = context.getSharedPreferences("dsu_helper_prefs", Context.MODE_PRIVATE)
    val lang = prefs.getString("lang", "ru") ?: "ru"
    return if (lang.equals("en", ignoreCase = true)) string.en else string.ru
}

@Composable
fun loc(string: LocString): String {
    val lang = LocalAppLanguage.current
    return if (lang.equals("en", ignoreCase = true)) string.en else string.ru
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OriginDsuApp() {
    val context = LocalContext.current
    
    var userdataGb by remember { mutableStateOf(8f) }
    var slotName by remember { mutableStateOf("dsu") }
    var showDialog by remember { mutableStateOf(false) }
    var showAdbDialog by remember { mutableStateOf(false) }
    
    // Auto-update DSU status in real-time
    var isDsuInstalled by remember { mutableStateOf(isDsuInstalledRobust(context)) }
    var isShizukuRunning by remember { mutableStateOf(false) }
    var hasShizukuPermission by remember { mutableStateOf(false) }
    
    LaunchedEffect(Unit) {
        while (true) {
            isDsuInstalled = isDsuInstalledRobust(context)
            
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
            
            delay(2000)
        }
    }
    
    val userdataBytes = userdataGb.toLong() * 1024L * 1024L * 1024L
    
    val adbInstallCommand = "adb shell am start --user 0 -n com.android.dynsystem/com.android.dynsystem.VerificationActivity " +
            "-a android.os.image.action.START_INSTALL " +
            "--el KEY_SYSTEM_SIZE 0 " +
            "--el KEY_USERDATA_SIZE $userdataBytes " +
            "--ez KEY_ENABLE_WHEN_COMPLETED true " +
            "--es KEY_DSU_SLOT $slotName"
            
    val adbWipeCommand = "adb shell gsi_tool wipe"

    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = { 
                    Text(
                        loc(LocString.APP_TITLE)
                    )
                },
                actions = {
                    IconButton(onClick = { 
                        context.startActivity(Intent(context, SettingsActivity::class.java))
                    }) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                },
                colors = TopAppBarDefaults.largeTopAppBarColors(
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
            
            // 1. Info Card
            InfoCard(
                isShizukuRunning = isShizukuRunning,
                hasShizukuPermission = hasShizukuPermission,
                isDsuInstalled = isDsuInstalled
            )

            // 2. Main Action Box
            if (isDsuInstalled) {
                InstalledDsuBox(
                    onWipeClick = {
                        wipeDsu(context) {
                            isDsuInstalled = checkDsuStatus()
                        }
                    },
                    onShowAdbDialog = { showAdbDialog = true }
                )
            } else {
                SettingsCard(
                    userdataGb = userdataGb,
                    onUserdataChange = { userdataGb = it },
                    slotName = slotName,
                    onSlotNameChange = { slotName = it }
                )

                // Action Buttons
                Button(
                    onClick = {
                        launchDsu(
                            context = context,
                            userdataGb = userdataGb.toInt(),
                            dsuSlot = slotName,
                            onShowPermissionDialog = { showDialog = true }
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(loc(LocString.LAUNCH_DSU), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }

                OutlinedButton(
                    onClick = { showAdbDialog = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Default.Share, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(loc(LocString.COPY_ADB_CMD), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }

            // 4. Instructions Card
            OutlinedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Info, 
                            contentDescription = null, 
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            loc(LocString.HOW_IT_WORKS),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                    Text(
                        loc(LocString.STEPS_DESC),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }

    // Permission Error Dialog
    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = {
                Text(loc(LocString.SH_REQ_TITLE))
            },
            text = {
                Text(loc(LocString.SH_REQ_DESC))
            },
            confirmButton = {
                Button(
                    onClick = {
                        copyToClipboard(context, adbInstallCommand, "Команда установки")
                        showDialog = false
                    }
                ) {
                    Text(loc(LocString.COPY_BTN))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDialog = false }
                ) {
                    Text(loc(LocString.CANCEL))
                }
            }
        )
    }

    // ADB Commands Dialog
    if (showAdbDialog) {
        AlertDialog(
            onDismissRequest = { showAdbDialog = false },
            title = {
                Text(loc(LocString.ADB_MANUAL_TITLE))
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(loc(LocString.ADB_LAUNCH_TITLE), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = adbInstallCommand,
                                modifier = Modifier.padding(10.dp),
                                fontSize = 11.sp,
                                lineHeight = 14.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Button(
                            onClick = { copyToClipboard(context, adbInstallCommand, "Команда запуска") },
                            modifier = Modifier.fillMaxWidth().height(36.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(loc(LocString.ADB_LAUNCH_BTN), fontSize = 12.sp)
                        }
                    }
                    
                    HorizontalDivider()
                    
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(loc(LocString.ADB_WIPE_TITLE), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = adbWipeCommand,
                                modifier = Modifier.padding(10.dp),
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Button(
                            onClick = { copyToClipboard(context, adbWipeCommand, "Команда удаления") },
                            modifier = Modifier.fillMaxWidth().height(36.dp),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError
                            )
                        ) {
                            Text(loc(LocString.ADB_WIPE_BTN), fontSize = 12.sp)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { showAdbDialog = false }
                ) {
                    Text(loc(LocString.DONE))
                }
            }
        )
    }
}

// Shizuku Permission Box Card
@Composable
fun ShizukuPermissionCard(
    isShizukuRunning: Boolean,
    hasPermission: Boolean,
    onRequestPermission: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Settings, 
                    contentDescription = null, 
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = loc(LocString.SH_PERM_BOX),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            
            HorizontalDivider()
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = loc(LocString.SH_PERM_STATUS), style = MaterialTheme.typography.bodyMedium)
                Text(
                    text = if (!isShizukuRunning) {
                        loc(LocString.SH_NOT_RUNNING)
                    } else if (hasPermission) {
                        loc(LocString.SH_PERM_GRANTED)
                    } else {
                        loc(LocString.SH_PERM_DENIED)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (!isShizukuRunning) {
                        MaterialTheme.colorScheme.outline
                    } else if (hasPermission) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.error
                    }
                )
            }
            
            if (isShizukuRunning) {
                Button(
                    onClick = onRequestPermission,
                    enabled = !hasPermission,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (hasPermission) {
                        Text(loc(LocString.SH_GRANTED_BTN), fontWeight = FontWeight.Bold)
                    } else {
                        Text(loc(LocString.SH_GRANT_BTN), fontWeight = FontWeight.Bold)
                    }
                }
            } else {
                Text(
                    text = if (loc(LocString.LANG_RU) == "Русский") {
                        "Пожалуйста, запустите менеджер Shizuku на вашем телефоне для выдачи разрешений."
                    } else {
                        "Please start the Shizuku manager on your phone to grant permissions."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

// Special Box when GSI/DSU is already prepared
@Composable
fun InstalledDsuBox(
    onWipeClick: () -> Unit,
    onShowAdbDialog: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Info, 
                    contentDescription = null, 
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = loc(LocString.DSU_PREPARED_TITLE),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            
            HorizontalDivider()
            
            Text(
                text = loc(LocString.DSU_PREPARED_DESC),
                style = MaterialTheme.typography.bodyMedium
            )
            
            Button(
                onClick = onWipeClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                )
            ) {
                Text(loc(LocString.DSU_WIPE_BTN), fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
            
            OutlinedButton(
                onClick = onShowAdbDialog,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Share, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(loc(LocString.COPY_ADB_CMD), fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
        }
    }
}

@Composable
fun InfoCard(
    isShizukuRunning: Boolean,
    hasShizukuPermission: Boolean,
    isDsuInstalled: Boolean
) {
    val manufacturer = Build.MANUFACTURER
    val model = Build.MODEL
    val androidVersion = Build.VERSION.RELEASE
    val sdkInt = Build.VERSION.SDK_INT
    
    // System properties
    val vivoName = remember { getSystemProperty("ro.vivo.os.name") }
    val vivoVer = remember { getSystemProperty("ro.vivo.os.version") }
    val dynamicPartitions = remember { getSystemProperty("ro.boot.dynamic_partitions") }
    val gsiMetadata = remember { getSystemProperty("gsi.metadata_in_data") }
    
    val shizukuPermissionStatus = when {
        isShizukuRunning && hasShizukuPermission -> loc(LocString.SH_ACTIVE_GRANTED)
        isShizukuRunning -> loc(LocString.SH_ACTIVE_DENIED)
        else -> loc(LocString.SH_NOT_RUNNING)
    }
    
    val isOriginOS = vivoName.contains("Origin", ignoreCase = true) || vivoName.isNotEmpty()
    val systemText = if (isOriginOS) {
        "OriginOS ${vivoVer.ifEmpty { "6" }}"
    } else {
        "Android $androidVersion"
    }
    
    val hasDynamicPartitions = dynamicPartitions == "true"
    val isDsuActive = gsiMetadata == "true"
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Info, 
                    contentDescription = null, 
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = loc(LocString.SYS_INFO),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            
            HorizontalDivider()
            
            InfoRow(loc(LocString.DEVICE_MODEL), "$manufacturer $model")
            InfoRow(loc(LocString.ANDROID_VER), "$androidVersion (API $sdkInt)")
            InfoRow(loc(LocString.SYSTEM), systemText)
            InfoRow(
                loc(LocString.SHIZUKU_SERVICE),
                shizukuPermissionStatus,
                valueColor = shVisualColor(shizukuPermissionStatus)
            )
            InfoRow(
                loc(LocString.DYNAMIC_PARTITIONS),
                if (hasDynamicPartitions) "Да" else "Нет",
                valueColor = if (hasDynamicPartitions) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
            )
            InfoRow(
                loc(LocString.DSU_MODE),
                if (isDsuActive) {
                    loc(LocString.DSU_ACTIVE)
                } else {
                    if (isDsuInstalled) loc(LocString.DSU_PREPARED) else loc(LocString.DSU_INACTIVE)
                },
                valueColor = if (isDsuActive || isDsuInstalled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
            )
        }
    }
}

@Composable
private fun shVisualColor(status: String): androidx.compose.ui.graphics.Color {
    val granted = loc(LocString.SH_ACTIVE_GRANTED)
    val denied = loc(LocString.SH_ACTIVE_DENIED)
    return when {
        status == granted -> MaterialTheme.colorScheme.primary
        status == denied -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.outline
    }
}

@Composable
fun InfoRow(
    label: String,
    value: String,
    valueColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label, 
            style = MaterialTheme.typography.bodyMedium, 
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value, 
            style = MaterialTheme.typography.bodyMedium, 
            fontWeight = FontWeight.SemiBold, 
            color = valueColor
        )
    }
}

@Composable
fun SettingsCard(
    userdataGb: Float,
    onUserdataChange: (Float) -> Unit,
    slotName: String,
    onSlotNameChange: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Settings, 
                    contentDescription = null, 
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = loc(LocString.DSU_PARAMS),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            
            HorizontalDivider()
            
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = loc(LocString.USERDATA_SIZE), style = MaterialTheme.typography.bodyMedium)
                    Text(
                        text = "${userdataGb.toInt()} ГБ",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                
                Slider(
                    value = userdataGb,
                    onValueChange = onUserdataChange,
                    valueRange = 2f..64f,
                    steps = 61
                )
                
                Text(
                    text = loc(LocString.USERDATA_DESC),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            OutlinedTextField(
                value = slotName,
                onValueChange = onSlotNameChange,
                label = { Text(loc(LocString.SLOT_NAME)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )
        }
    }
}

// Special settings and About Card Dashboard
@Composable
fun SettingsAndAboutCard(
    currentTheme: String,
    onThemeChange: (String) -> Unit,
    currentLang: String,
    onLangChange: (String) -> Unit
) {
    val uriHandler = LocalUriHandler.current
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Settings, 
                    contentDescription = null, 
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = loc(LocString.ABOUT_SETTINGS),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            
            HorizontalDivider()
            
            // Theme Selector Row
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = loc(LocString.SELECT_THEME), 
                    style = MaterialTheme.typography.bodyMedium, 
                    fontWeight = FontWeight.SemiBold
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val themes = listOf("auto", "light", "dark")
                    val themeLabels = listOf(loc(LocString.THEME_AUTO), loc(LocString.THEME_LIGHT), loc(LocString.THEME_DARK))
                    themes.forEachIndexed { index, t ->
                        val isSelected = currentTheme == t
                        val containerColor by animateColorAsState(
                            targetValue = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer,
                            label = "themeContainer"
                        )
                        val contentColor by animateColorAsState(
                            targetValue = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer,
                            label = "themeContent"
                        )
                        val buttonScale by animateFloatAsState(
                            targetValue = if (isSelected) 1.05f else 1.0f,
                            label = "themeScale"
                        )
                        Button(
                            onClick = { onThemeChange(t) },
                            modifier = Modifier
                                .weight(1f)
                                .height(38.dp)
                                .scale(buttonScale),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = containerColor,
                                contentColor = contentColor
                            ),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text(themeLabels[index], fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            
            // Language Selector Row
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = loc(LocString.SELECT_LANG), 
                    style = MaterialTheme.typography.bodyMedium, 
                    fontWeight = FontWeight.SemiBold
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val langs = listOf("ru", "en")
                    val langLabels = listOf(loc(LocString.LANG_RU), loc(LocString.LANG_EN))
                    langs.forEachIndexed { index, l ->
                        val isSelected = currentLang == l
                        val containerColor by animateColorAsState(
                            targetValue = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer,
                            label = "langContainer"
                        )
                        val contentColor by animateColorAsState(
                            targetValue = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer,
                            label = "langContent"
                        )
                        val buttonScale by animateFloatAsState(
                            targetValue = if (isSelected) 1.05f else 1.0f,
                            label = "langScale"
                        )
                        Button(
                            onClick = { onLangChange(l) },
                            modifier = Modifier
                                .weight(1f)
                                .height(38.dp)
                                .scale(buttonScale),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = containerColor,
                                contentColor = contentColor
                            ),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text(langLabels[index], fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            
            // About App Details Info
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                InfoRow(loc(LocString.VERSION), "1.0.0")
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = loc(LocString.AUTHOR), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(text = "tr1xx", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = loc(LocString.TG_CHANNEL), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(
                        onClick = { uriHandler.openUri("https://t.me/tr1xx_projects") },
                        contentPadding = PaddingValues(0.dp),
                        modifier = Modifier.height(24.dp)
                    ) {
                        Text(
                            text = "@tr1xx_projects",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
            }
        }
    }
}

// Function to handle the DSU Launch via Shizuku Shell Input Pipeline
fun launchDsu(
    context: Context,
    userdataGb: Int,
    dsuSlot: String,
    onShowPermissionDialog: () -> Unit
) {
    val userdataBytes = userdataGb.toLong() * 1024L * 1024L * 1024L
    
    // Explicitly add --user 0 to target system owner context
    val cmdString = "am start --user 0 -n com.android.dynsystem/com.android.dynsystem.VerificationActivity " +
            "-a android.os.image.action.START_INSTALL " +
            "--el KEY_SYSTEM_SIZE 0 " +
            "--el KEY_USERDATA_SIZE $userdataBytes " +
            "--ez KEY_ENABLE_WHEN_COMPLETED true " +
            "--es KEY_DSU_SLOT $dsuSlot"
    
    // Attempt Shizuku launch if available
    try {
        if (Shizuku.pingBinder()) {
            if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                // Bypass private newProcess via reflection using getDeclaredMethods
                val clazz = Class.forName("rikka.shizuku.Shizuku")
                val method = clazz.getDeclaredMethods().firstOrNull { it.name == "newProcess" }
                    ?: throw NoSuchMethodException("newProcess not found in Shizuku class")
                method.isAccessible = true
                
                // Start a shell session via Shizuku
                val process = method.invoke(null, arrayOf("sh"), null, null) as java.lang.Process
                
                // Write our DSU am start command directly to sh stdin, followed by exit to return exit code
                process.outputStream.use { os ->
                    os.write((cmdString + "\nexit\n").toByteArray())
                    os.flush()
                }
                
                // Drain both stdout and stderr completely to avoid process buffer deadlock
                val stdout = try {
                    process.inputStream.bufferedReader().use { it.readText() }
                } catch (e: Exception) {
                    ""
                }
                
                val stderr = try {
                    process.errorStream.bufferedReader().use { it.readText() }
                } catch (e: Exception) {
                    ""
                }
                
                val exitCode = process.waitFor()
                if (exitCode == 0) {
                    Toast.makeText(context, "DSU успешно запущен через Shizuku!", Toast.LENGTH_LONG).show()
                    return
                } else {
                    val errMsg = stderr.trim().ifEmpty { stdout.trim().ifEmpty { "код $exitCode" } }
                    Toast.makeText(context, "Ошибка запуска: $errMsg", Toast.LENGTH_LONG).show()
                }
            } else {
                Shizuku.requestPermission(1001)
                Toast.makeText(context, "Предоставьте разрешение Shizuku", Toast.LENGTH_SHORT).show()
                return
            }
        }
    } catch (e: Exception) {
        Toast.makeText(context, "Ошибка IPC Shizuku: ${e.message}", Toast.LENGTH_LONG).show()
    }
    
    // Fallback to permission warning dialog
    onShowPermissionDialog()
}

// Function to delete/wipe DSU
fun wipeDsu(
    context: Context,
    onWipeComplete: () -> Unit
) {
    try {
        if (Shizuku.pingBinder()) {
            if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                val clazz = Class.forName("rikka.shizuku.Shizuku")
                val method = clazz.getDeclaredMethods().firstOrNull { it.name == "newProcess" }
                    ?: throw NoSuchMethodException("newProcess not found in Shizuku class")
                method.isAccessible = true
                
                // Start a shell session via Shizuku
                val process = method.invoke(null, arrayOf("sh"), null, null) as java.lang.Process
                
                // Write our wipe command directly to sh stdin, followed by exit to return exit code
                process.outputStream.use { os ->
                    os.write(("gsi_tool wipe\nexit\n").toByteArray())
                    os.flush()
                }
                
                // Drain both stdout and stderr completely
                val stdout = try {
                    process.inputStream.bufferedReader().use { it.readText() }
                } catch (e: Exception) {
                    ""
                }
                
                val stderr = try {
                    process.errorStream.bufferedReader().use { it.readText() }
                } catch (e: Exception) {
                    ""
                }
                
                val exitCode = process.waitFor()
                if (exitCode == 0) {
                    Toast.makeText(context, "DSU успешно сброшен и удален!", Toast.LENGTH_LONG).show()
                    onWipeComplete()
                    return
                } else {
                    val errMsg = stderr.trim().ifEmpty { stdout.trim().ifEmpty { "код $exitCode" } }
                    Toast.makeText(context, "Ошибка очистки DSU: $errMsg", Toast.LENGTH_LONG).show()
                }
            } else {
                Shizuku.requestPermission(1001)
                Toast.makeText(context, "Предоставьте разрешение Shizuku", Toast.LENGTH_SHORT).show()
                return
            }
        }
    } catch (e: Exception) {
        Toast.makeText(context, "Ошибка IPC Shizuku при удалении: ${e.message}", Toast.LENGTH_LONG).show()
    }
}
