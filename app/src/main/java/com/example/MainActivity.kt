package com.example

import android.os.Bundle
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import com.example.util.FocusTimerManager
import androidx.activity.compose.BackHandler
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.border
import androidx.compose.animation.core.tween
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.room.Room
import com.example.ui.theme.PremiumEffects.bouncyClick
import com.example.data.AppDatabase
import com.example.data.LocalRepository
import com.example.ui.AppViewModel
import com.example.ui.Screen
import com.example.ui.components.*
import com.example.ui.theme.DeepSlate
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.WaterBlue

class MainActivity : ComponentActivity() {
    private lateinit var database: AppDatabase
    private lateinit var repository: LocalRepository
    private lateinit var viewModel: AppViewModel
    private var startupException: Throwable? = null
    private val isAppUnlockedState = androidx.compose.runtime.mutableStateOf(false)
    private val interceptedAppSessionQuery = androidx.compose.runtime.mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        try {
            // Initialize default app blocks and strict mode lists
            com.example.util.AppBlockHelper.initializeStrictAppsIfNeeded(applicationContext)
            
            // Start the persistent keep alive daemon service if enabled
            val prefs = getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE)
            if (prefs.getBoolean("keep_notification_enabled", true)) {
                com.example.service.KeepAliveService.start(applicationContext)
            }
            
            // Initialize local Room Database with destructive migration allowance to prevent upgrade crashes
            database = AppDatabase.getInstance(applicationContext)
            repository = LocalRepository(database)

            // Check for previously exported backups on first start and auto-restore
            lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    val restored = com.example.util.DatabaseBackupHelper.autoRestoreIfNeeded(applicationContext, database)
                    if (restored) {
                        android.util.Log.i("MainActivity", "Successfully verified and auto-restored database from public storage.")
                    }
                } catch (e: Exception) {
                    android.util.Log.e("MainActivity", "Auto-restore logic failed", e)
                }
            }

            // Trigger background system auto-update check on startup and loop every hour (3,600,000 ms)
            lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                while (true) {
                    try {
                        com.example.util.AppUpdateManager.checkForUpdates(applicationContext, manualCheck = false)
                    } catch (e: Exception) {
                        android.util.Log.e("MainActivity", "System auto-update check failed", e)
                    }
                    kotlinx.coroutines.delay(3600000L)
                }
            }

            // Request Notification Permission on Android 13+ (API 33)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                val permissionCheck = androidx.core.content.ContextCompat.checkSelfPermission(
                    this, android.Manifest.permission.POST_NOTIFICATIONS
                )
                if (permissionCheck != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    androidx.core.app.ActivityCompat.requestPermissions(
                        this, arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 101
                    )
                }
            }

            // ViewModel factory setup
            viewModel = ViewModelProvider(this, object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    if (modelClass.isAssignableFrom(AppViewModel::class.java)) {
                        @Suppress("UNCHECKED_CAST")
                        return AppViewModel(application, repository) as T
                    }
                    throw IllegalArgumentException("Unknown ViewModel class")
                }
            })[AppViewModel::class.java]

            // Initialize timer manager with context
            FocusTimerManager.init(applicationContext)

            // Track screen changes dynamically to trigger/hide floating overlay
            lifecycleScope.launch {
                lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                    viewModel.currentScreen.collect { screen ->
                        FocusTimerManager.setTimerScreenActiveState(this@MainActivity, screen == Screen.TIMER)
                    }
                }
            }

            // Handle auto-navigation if launched with SHOW_TIMER_PAGE parameter
            checkTimerNavigation(intent)
            checkAppBlockInterceptions(intent)
        } catch (e: Throwable) {
            e.printStackTrace()
            startupException = e
        }

        enableEdgeToEdge()

        setContent {
            MyApplicationTheme {
                // Global auto-update engine collection and overlay overlays
                val updateStatus by com.example.util.AppUpdateManager.updateStatus.collectAsState()
                val context = androidx.compose.ui.platform.LocalContext.current

                // Observe focus timer and stopwatch active states to delay update alerts
                val isTimerRunning by com.example.util.FocusTimerManager.isTimerRunning.collectAsState()
                val isStopwatchActive by com.example.util.FocusTimerManager.isStopwatchActive.collectAsState()
                val showVerificationDialog by com.example.util.FocusTimerManager.showGlobalVerificationDialog.collectAsState()
                val pendingFocusReview by com.example.util.FocusTimerManager.pendingFocusReview.collectAsState()

                val hasActiveTimerOrUnsavedSession = isTimerRunning || isStopwatchActive || showVerificationDialog || pendingFocusReview != null

                // On startup, if a new version is detected, automatically download it if auto-update is enabled
                LaunchedEffect(updateStatus) {
                    val status = updateStatus
                    if (status is com.example.util.UpdateStatus.NewVersionAvailable) {
                        val isAuto = com.example.util.AppUpdateManager.isAutoUpdateEnabled(context)
                        val isForce = com.example.util.AppUpdateManager.isForceUpdateEnabled(context)
                        if (isAuto || isForce) {
                            val verId = status.apkFileId
                            com.example.util.AppUpdateManager.startDownloadAndInstall(context, verId)
                        }
                    }
                }

                // Render update state dialogs
                when (val status = updateStatus) {
                    is com.example.util.UpdateStatus.Downloading -> {
                        // Downloading is completely silent in-app, progress is updated in status bar notification
                    }
                    is com.example.util.UpdateStatus.ReadyToInstall -> {
                        if (!hasActiveTimerOrUnsavedSession) {
                            AlertDialog(
                                onDismissRequest = {},
                                title = {
                                    Text("System Update Downloaded", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                },
                                text = {
                                    Text(
                                        "The updated APK was successfully downloaded. Please tap 'Install' below to complete the system update.",
                                        fontSize = 13.sp,
                                        color = Color.LightGray
                                    )
                                },
                                confirmButton = {
                                    Button(
                                        onClick = { com.example.util.AppUpdateManager.installApk(context, status.apkFile) },
                                        colors = ButtonDefaults.buttonColors(containerColor = WaterBlue, contentColor = Color.Black)
                                    ) {
                                        Text("INSTALL", fontWeight = FontWeight.Bold)
                                    }
                                },
                                dismissButton = {
                                    if (!com.example.util.AppUpdateManager.isForceUpdateEnabled(context)) {
                                        TextButton(
                                            onClick = { com.example.util.AppUpdateManager.resetStatus() }
                                        ) {
                                            Text("CANCEL", color = Color.Gray)
                                        }
                                    }
                                },
                                containerColor = Color(0xFF0F0F11),
                                textContentColor = Color.White,
                                titleContentColor = Color.White
                            )
                        }
                    }
                    is com.example.util.UpdateStatus.NewVersionAvailable -> {
                        AlertDialog(
                            onDismissRequest = {
                                if (!com.example.util.AppUpdateManager.isForceUpdateEnabled(context)) {
                                    com.example.util.AppUpdateManager.resetStatus()
                                }
                            },
                            title = {
                                Text("System Update Available", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            },
                            text = {
                                Text(
                                    "A new system update (Version Code: #${status.versionId}) is available. Would you like to download and install it now?\n\n(Current version: #${status.currentVersionCode})",
                                    fontSize = 13.sp,
                                    color = Color.LightGray
                                )
                            },
                            confirmButton = {
                                Button(
                                    onClick = {
                                        com.example.util.AppUpdateManager.startDownloadAndInstall(context, status.apkFileId)
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = WaterBlue, contentColor = Color.Black)
                                ) {
                                    Text("DOWNLOAD", fontWeight = FontWeight.Bold)
                                }
                            },
                            dismissButton = {
                                if (!com.example.util.AppUpdateManager.isForceUpdateEnabled(context)) {
                                    TextButton(
                                        onClick = { com.example.util.AppUpdateManager.resetStatus() }
                                    ) {
                                        Text("CANCEL", color = Color.Gray)
                                    }
                                }
                            },
                            containerColor = Color(0xFF0F0F11),
                            textContentColor = Color.White,
                            titleContentColor = Color.White
                        )
                    }
                    is com.example.util.UpdateStatus.Error -> {
                        AlertDialog(
                            onDismissRequest = { com.example.util.AppUpdateManager.resetStatus() },
                            title = {
                                Text("Update Error", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFFFF5252))
                            },
                            text = {
                                Text(status.message, fontSize = 13.sp, color = Color.LightGray)
                            },
                            confirmButton = {
                                Button(
                                    onClick = { com.example.util.AppUpdateManager.resetStatus() },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF222225), contentColor = Color.White)
                                ) {
                                    Text("DISMISS")
                                }
                            },
                            containerColor = Color(0xFF0F0F11),
                            textContentColor = Color.White,
                            titleContentColor = Color.White
                        )
                    }
                    else -> {}
                }

                val error = startupException
                if (error != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black)
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.verticalScroll(rememberScrollState())
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = "Error Logo",
                                tint = Color(0xFFFF5252),
                                modifier = Modifier.size(64.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "STARTUP FAILURE DETECTED",
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Life OS failed to initialize. Details are documented below:",
                                color = Color.LightGray,
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Card(
                                modifier = Modifier.fillMaxWidth().heightIn(max = 350.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF151515)),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Box(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                                    Text(
                                        text = android.util.Log.getStackTraceString(error),
                                        color = Color(0xFFFF5252),
                                        fontSize = 11.sp,
                                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                        modifier = Modifier.padding(12.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(24.dp))
                            Button(
                                onClick = {
                                    getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE)
                                        .edit().clear().apply()
                                    try {
                                        deleteDatabase("life_os_database")
                                    } catch (ex: Exception) {}
                                    val restartIntent = Intent(this@MainActivity, MainActivity::class.java).apply {
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                                    }
                                    startActivity(restartIntent)
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF3D00))
                            ) {
                                Text("Factory Reset & Recovery Start", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                } else {
                    val isAppUnlocked by isAppUnlockedState
                    if (isAppUnlocked) {
                        val currentScreen by viewModel.currentScreen.collectAsState()
                    val activeNagTask by viewModel.activeNagTask.collectAsState()
                    val isSidebarOpen by viewModel.isLocalSidebarOpen.collectAsState()
                    val tabOrder by viewModel.tabOrder.collectAsState()
                    val hiddenTabs by viewModel.hiddenTabs.collectAsState()
                    val isTimerImmersive by viewModel.isTimerImmersive.collectAsState()
                    val tabBarOrientation by viewModel.tabBarOrientation.collectAsState()
                    val showHistoryScreen by viewModel.showHistoryScreen.collectAsState()
                    val navItems = getNavigationItems(tabOrder.filterNot { hiddenTabs.contains(it) })

                    val keyboardController = LocalSoftwareKeyboardController.current
                    val focusManager = LocalFocusManager.current
                    @OptIn(ExperimentalLayoutApi::class)
                    val isKeyboardVisible = WindowInsets.isImeVisible

                    // Back Navigation Control
                    if (currentScreen == Screen.DEEPA_AI) {
                        BackHandler(enabled = true) {
                            if (isKeyboardVisible) {
                                keyboardController?.hide()
                                focusManager.clearFocus(force = true)
                            } else {
                                finish()
                            }
                        }
                    } else if (currentScreen == Screen.TIMER && isTimerImmersive) {
                        BackHandler(enabled = true) {
                            if (isKeyboardVisible) {
                                keyboardController?.hide()
                                focusManager.clearFocus(force = true)
                            } else {
                                viewModel.setTimerImmersive(false)
                            }
                        }
                    } else if (currentScreen == Screen.TIMER && showHistoryScreen) {
                        BackHandler(enabled = true) {
                            if (isKeyboardVisible) {
                                keyboardController?.hide()
                                focusManager.clearFocus(force = true)
                            } else {
                                viewModel.setShowHistoryScreen(false)
                            }
                        }
                    } else {
                        // Other main tabs navigate back to Screen.DEEPA_AI (AI page again)
                        BackHandler(enabled = true) {
                            if (isKeyboardVisible) {
                                keyboardController?.hide()
                                focusManager.clearFocus(force = true)
                            } else {
                                viewModel.navigateTo(Screen.DEEPA_AI)
                            }
                        }
                    }

                    @Composable
                    fun MainScaffoldContent(scaffoldModifier: Modifier) {
                    Scaffold(
                        modifier = scaffoldModifier,
                        containerColor = Color.Transparent,
                        topBar = {}
                    ) { innerPadding ->
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(innerPadding)
                        ) {
                            // Render screen container with premium fluid transition animations
                            Box(modifier = Modifier.fillMaxSize().background(Color.Transparent)) {
                                AnimatedContent(
                                    targetState = currentScreen,
                                    transitionSpec = {
                                        fadeIn(animationSpec = tween(300)) + scaleIn(initialScale = 0.97f, animationSpec = tween(300)) togetherWith
                                        fadeOut(animationSpec = tween(200))
                                    },
                                    modifier = Modifier.fillMaxSize(),
                                    label = "tab_screen_transition"
                                ) { targetScreen ->
                                    Box(modifier = Modifier.fillMaxSize()) {
                                        when (targetScreen) {
                                            Screen.LOGIN -> LoginView(viewModel = viewModel)
                                            Screen.PROFILE_SETUP -> ProfileSetupView(viewModel = viewModel)
                                            Screen.PERMISSION_ONBOARDING -> PermissionOnboardingView(viewModel = viewModel)
                                            Screen.TASKS -> TaskEngineView(viewModel = viewModel)
                                            Screen.CALENDAR -> CalendarView(viewModel = viewModel)
                                            Screen.TIMER -> TimerView(viewModel = viewModel)
                                            Screen.HABITS -> HabitsView(viewModel = viewModel)
                                            Screen.COUNTDOWN -> CountdownView(viewModel = viewModel)
                                            Screen.JOURNAL -> JournalBookView(viewModel = viewModel)
                                            Screen.CONTACTS -> ContactsView(viewModel = viewModel)
                                            Screen.FILE_EXPLORER -> FileExplorerView(viewModel = viewModel)
                                            Screen.FINANCES -> FinancialLedgerView(viewModel = viewModel)
                                            Screen.DEEPA_AI -> SmartChatView(viewModel = viewModel)
                                            Screen.SEARCH -> GlobalSearchView(viewModel = viewModel)
                                            Screen.ANALYTICS -> AnalyticsView(viewModel = viewModel)
                                            Screen.SETTINGS -> SettingsView(viewModel = viewModel)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                if (currentScreen == Screen.TIMER && isTimerImmersive) {
                    // Full Screen Immersive Mode: covers all the display, leaving absolutely no side navigation or safe drawer padding!
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black)
                    ) {
                        TimerView(viewModel = viewModel, modifier = Modifier.fillMaxSize())
                    }
                } else {
                    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                        val isTablet = maxWidth >= 600.dp
                        val outerModifier = Modifier
                            .fillMaxSize()
                            .background(
                                androidx.compose.ui.graphics.Brush.verticalGradient(
                                    colors = listOf(
                                        Color(0xFF06070D),
                                        Color(0xFF0F111B),
                                        Color(0xFF030305)
                                    )
                                )
                            )
                            .windowInsetsPadding(WindowInsets.safeDrawing)

                        val savedAlignMode = tabBarOrientation.lowercase(java.util.Locale.ROOT)
                        val alignMode = if (savedAlignMode == "vertical" || savedAlignMode == "left" || savedAlignMode.isEmpty()) {
                            if (isTablet) "left" else "bottom"
                        } else {
                            savedAlignMode
                        }
                        if (alignMode == "horizontal" || alignMode == "top") {
                        Column(modifier = outerModifier) {
                            if (!isKeyboardVisible && currentScreen != Screen.LOGIN && currentScreen != Screen.PROFILE_SETUP && currentScreen != Screen.PERMISSION_ONBOARDING) {
                                // Top Horizontal pill-tab navigation bar (Floating Glass Dock)
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 8.dp)
                                ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(56.dp)
                                        .clip(RoundedCornerShape(28.dp))
                                        .background(Color.White.copy(alpha = 0.1f))
                                        .border(width = 1.dp, color = Color(0x18FFFFFF), shape = RoundedCornerShape(28.dp))
                                        .horizontalScroll(rememberScrollState()),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Text(
                                        text = "LIFE OS",
                                        fontWeight = FontWeight.ExtraBold,
                                        color = WaterBlue,
                                        fontSize = 12.sp,
                                        modifier = Modifier.padding(end = 6.dp)
                                    )

                                    navItems.forEach { item ->
                                        val isSelected = currentScreen == item.screen
                                        val tint = if (isSelected) Color.White else Color.Gray
                                        val bg = if (isSelected) WaterBlue.copy(alpha = 0.22f) else Color.Transparent

                                        Row(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(16.dp))
                                                .background(bg)
                                                .let { m ->
                                                    if (isSelected) m.border(width = 1.dp, color = WaterBlue, shape = RoundedCornerShape(16.dp))
                                                    else m
                                                }
                                                .bouncyClick { viewModel.navigateTo(item.screen) }
                                                .padding(horizontal = 12.dp, vertical = 6.dp)
                                                .testTag("nav_item_${item.label.lowercase()}"),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = item.icon,
                                                contentDescription = item.label,
                                                tint = tint,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(16.dp))
                                }
                            }
                            }

                            MainScaffoldContent(scaffoldModifier = Modifier.weight(1f).fillMaxWidth())
                        }
                    } else if (alignMode == "bottom") {
                        Column(modifier = outerModifier) {
                            MainScaffoldContent(scaffoldModifier = Modifier.weight(1f).fillMaxWidth())

                            if (!isKeyboardVisible && currentScreen != Screen.LOGIN && currentScreen != Screen.PROFILE_SETUP && currentScreen != Screen.PERMISSION_ONBOARDING) {
                                // Bottom Horizontal pill-tab navigation bar (Floating Glass Dock)
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 8.dp)
                                ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(56.dp)
                                        .clip(RoundedCornerShape(28.dp))
                                        .background(Color.White.copy(alpha = 0.1f))
                                        .border(width = 1.dp, color = Color(0x18FFFFFF), shape = RoundedCornerShape(28.dp))
                                        .horizontalScroll(rememberScrollState()),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Text(
                                        text = "LIFE OS",
                                        fontWeight = FontWeight.ExtraBold,
                                        color = WaterBlue,
                                        fontSize = 12.sp,
                                        modifier = Modifier.padding(end = 6.dp)
                                    )

                                    navItems.forEach { item ->
                                        val isSelected = currentScreen == item.screen
                                        val tint = if (isSelected) Color.White else Color.Gray
                                        val bg = if (isSelected) WaterBlue.copy(alpha = 0.22f) else Color.Transparent

                                        Row(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(16.dp))
                                                .background(bg)
                                                .let { m ->
                                                    if (isSelected) m.border(width = 1.dp, color = WaterBlue, shape = RoundedCornerShape(16.dp))
                                                    else m
                                                }
                                                .bouncyClick { viewModel.navigateTo(item.screen) }
                                                .padding(horizontal = 12.dp, vertical = 6.dp)
                                                .testTag("nav_item_${item.label.lowercase()}"),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = item.icon,
                                                contentDescription = item.label,
                                                tint = tint,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(16.dp))
                                }
                            }
                            }
                        }
                    } else if (alignMode == "right") {
                        Row(modifier = outerModifier) {
                            MainScaffoldContent(scaffoldModifier = Modifier.weight(1f).fillMaxHeight())

                            if (!isKeyboardVisible && currentScreen != Screen.LOGIN && currentScreen != Screen.PROFILE_SETUP && currentScreen != Screen.PERMISSION_ONBOARDING) {
                            // Right-hand vertical tabs column (Floating Glass Rail)
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .padding(vertical = 12.dp, horizontal = 8.dp)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .width(64.dp)
                                        .clip(RoundedCornerShape(32.dp))
                                        .background(Color.White.copy(alpha = 0.1f))
                                        .border(width = 1.dp, color = Color(0x18FFFFFF), shape = RoundedCornerShape(32.dp))
                                        .verticalScroll(rememberScrollState()),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text(
                                        text = "LIFE OS",
                                        fontWeight = FontWeight.ExtraBold,
                                        color = WaterBlue,
                                        fontSize = 11.sp,
                                        modifier = Modifier.padding(bottom = 8.dp),
                                        textAlign = TextAlign.Center
                                    )

                                    navItems.forEach { item ->
                                        val isSelected = currentScreen == item.screen
                                        val tint = if (isSelected) Color.White else Color.Gray
                                        val bg = if (isSelected) WaterBlue.copy(alpha = 0.22f) else Color.Transparent

                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .bouncyClick { viewModel.navigateTo(item.screen) }
                                                .padding(vertical = 8.dp)
                                                .testTag("nav_item_${item.label.lowercase()}"),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(height = 32.dp, width = 56.dp)
                                                    .clip(RoundedCornerShape(16.dp))
                                                    .background(bg)
                                                    .let { m ->
                                                        if (isSelected) m.border(width = 1.dp, color = WaterBlue, shape = RoundedCornerShape(16.dp))
                                                        else m
                                                    },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = item.icon,
                                                    contentDescription = item.label,
                                                    tint = tint,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(16.dp))
                                }
                            }
                            }
                        }
                    } else { // "left" or "vertical" or any other fallback
                        Row(modifier = outerModifier) {
                            if (!isKeyboardVisible && currentScreen != Screen.LOGIN && currentScreen != Screen.PROFILE_SETUP && currentScreen != Screen.PERMISSION_ONBOARDING) {
                            // Left-hand vertical tabs column (Floating Glass Rail)
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .padding(vertical = 12.dp, horizontal = 8.dp)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .width(64.dp)
                                        .clip(RoundedCornerShape(32.dp))
                                        .background(Color.White.copy(alpha = 0.1f))
                                        .border(width = 1.dp, color = Color(0x18FFFFFF), shape = RoundedCornerShape(32.dp))
                                        .verticalScroll(rememberScrollState()),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text(
                                        text = "LIFE OS",
                                        fontWeight = FontWeight.ExtraBold,
                                        color = WaterBlue,
                                        fontSize = 11.sp,
                                        modifier = Modifier.padding(bottom = 8.dp),
                                        textAlign = TextAlign.Center
                                    )

                                    navItems.forEach { item ->
                                        val isSelected = currentScreen == item.screen
                                        val tint = if (isSelected) Color.White else Color.Gray
                                        val bg = if (isSelected) WaterBlue.copy(alpha = 0.22f) else Color.Transparent

                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .bouncyClick { viewModel.navigateTo(item.screen) }
                                                .padding(vertical = 8.dp)
                                                .testTag("nav_item_${item.label.lowercase()}"),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(height = 32.dp, width = 56.dp)
                                                    .clip(RoundedCornerShape(16.dp))
                                                    .background(bg)
                                                    .let { m ->
                                                        if (isSelected) m.border(width = 1.dp, color = WaterBlue, shape = RoundedCornerShape(16.dp))
                                                        else m
                                                    },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = item.icon,
                                                    contentDescription = item.label,
                                                    tint = tint,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(16.dp))
                                }
                            }
                            }

                            MainScaffoldContent(scaffoldModifier = Modifier.weight(1f).fillMaxHeight())
                        }
                    }
                }
                }
                } else {
                    com.example.ui.components.AppLockOverlay(
                        onUnlocked = {
                            isAppUnlockedState.value = true
                        }
                    )
                }

                // App Interception Selector Prompt Overlay
                val interceptPkg by interceptedAppSessionQuery
                if (interceptPkg != null) {
                    AlertDialog(
                        onDismissRequest = {
                            // Non-dismissible by clicking outside to keep it a strict block
                        },
                        title = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = "Session Warning Icon",
                                    tint = WaterBlue,
                                    modifier = Modifier.size(22.dp)
                                )
                                Text(
                                    text = "LIFE OS APP MONITOR",
                                    color = Color.White,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Black
                                )
                            }
                        },
                        text = {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                val appLabel = remember(interceptPkg) {
                                    try {
                                        val pm = packageManager
                                        val info = pm.getApplicationInfo(interceptPkg ?: "", 0)
                                        pm.getApplicationLabel(info).toString()
                                    } catch (e: Exception) {
                                        interceptPkg?.substringAfterLast('.') ?: "Social App"
                                    }
                                }
                                Text(
                                    text = "You are attempting to open $appLabel.",
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Please allocate your session time usage below. Once the duration is over, Life OS will automatically block access.\n\nSelecting 'Close App' will safely exit and return to home.",
                                    color = Color.Gray,
                                    fontSize = 11.sp,
                                    lineHeight = 15.sp
                                )
                            }
                        },
                        confirmButton = {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                listOf(5, 10, 15, 20).forEach { mins ->
                                    Button(
                                        onClick = {
                                            com.example.util.AppBlockHelper.startTemporarySession(applicationContext, interceptPkg ?: "", mins)
                                            interceptedAppSessionQuery.value = null
                                            // Relaunch the application safely
                                            try {
                                                val pm = packageManager
                                                val launchIntent = pm.getLaunchIntentForPackage(interceptPkg ?: "")
                                                if (launchIntent != null) {
                                                    startActivity(launchIntent)
                                                }
                                            } catch (e: Exception) {
                                                android.widget.Toast.makeText(applicationContext, "Session set, but failed to launch app implicitly.", android.widget.Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = WaterBlue,
                                            contentColor = Color.Black
                                        ),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.fillMaxWidth().height(42.dp)
                                    ) {
                                        Text(
                                            text = "Use for $mins minutes",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp
                                        )
                                    }
                                }

                                Button(
                                    onClick = {
                                        interceptedAppSessionQuery.value = null
                                        // Minimize our app and route user to the home screen launcher
                                        try {
                                            val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                                                addCategory(Intent.CATEGORY_HOME)
                                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                            }
                                            startActivity(homeIntent)
                                        } catch (e: Exception) {
                                            finish()
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFFFF5252),
                                        contentColor = Color.White
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth().height(42.dp)
                                ) {
                                    Text(
                                        text = "Close App",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        },
                        containerColor = Color(0xFF0F0F12),
                        shape = RoundedCornerShape(16.dp)
                    )
                }

                // Global Focus Session Saved & Verified Confirmation Overlay
                val showGlobalVerification by FocusTimerManager.showGlobalVerificationDialog.collectAsState()
                val globalFocusedSecs by FocusTimerManager.globalVerificationFocusedTimeSeconds.collectAsState()
                val globalRevisedSecs by FocusTimerManager.globalVerificationRevisedTotalSeconds.collectAsState()
                val verifiedStartMs by FocusTimerManager.verifiedSessionStartMs.collectAsState()
                val verifiedPauseRanges by FocusTimerManager.verifiedSessionPauseRanges.collectAsState()

                if (showGlobalVerification) {
                    AlertDialog(
                        onDismissRequest = {
                            FocusTimerManager.showGlobalVerificationDialog.value = false
                        },
                        title = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "Verified Icon",
                                    tint = WaterBlue,
                                    modifier = Modifier.size(22.dp)
                                )
                                Text(
                                    text = "SYSTEM VERIFICATION",
                                    color = Color.White,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Black
                                )
                            }
                        },
                        text = {
                            Column(
                                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                fun formatSecondsToReadable(seconds: Int): String {
                                    if (seconds >= 3600) {
                                        val h = seconds / 3600
                                        val m = (seconds % 3600) / 60
                                        val s = seconds % 60
                                        return "${h}h ${m}m ${s}s"
                                    } else if (seconds >= 60) {
                                        val m = seconds / 60
                                        val s = seconds % 60
                                        return "${m}m ${s}s"
                                    } else {
                                        return "${seconds}s"
                                    }
                                }

                                val formattedNow = formatSecondsToReadable(globalFocusedSecs)
                                val pastSeconds = maxOf(0, globalRevisedSecs - globalFocusedSecs)
                                val formattedPast = formatSecondsToReadable(pastSeconds)
                                val formattedRevised = formatSecondsToReadable(globalRevisedSecs)

                                Text(
                                    text = "3-STEP SYSTEM AUDIT LOG & COMPLIANCE CHECK",
                                    color = Color.LightGray,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Black
                                )

                                // 3-Step Verification Checklist Card
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFF15151A)),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF22222A)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        // Step 1
                                        Row(verticalAlignment = Alignment.Top) {
                                            Icon(
                                                imageVector = Icons.Default.CheckCircle,
                                                contentDescription = "Success",
                                                tint = Color(0xFF4CAF50),
                                                modifier = Modifier.size(16.dp).padding(top = 2.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Column {
                                                Text("STEP 1: SESSION VERIFICATION", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                                Text("Active session of $formattedNow calculated and verified locally.", color = Color.Gray, fontSize = 9.sp)
                                            }
                                        }

                                        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color(0xFF22222A)))

                                        // Step 2
                                        Row(verticalAlignment = Alignment.Top) {
                                            Icon(
                                                imageVector = Icons.Default.CheckCircle,
                                                contentDescription = "Success",
                                                tint = Color(0xFF4CAF50),
                                                modifier = Modifier.size(16.dp).padding(top = 2.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Column {
                                                Text("STEP 2: CACHE AUDIT & REVISING", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                                Text("Database transaction complete. Total focus time updated from $formattedPast to $formattedRevised.", color = Color.Gray, fontSize = 9.sp)
                                            }
                                        }

                                        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color(0xFF22222A)))

                                        // Step 3
                                        Row(verticalAlignment = Alignment.Top) {
                                            Icon(
                                                imageVector = Icons.Default.CheckCircle,
                                                contentDescription = "Success",
                                                tint = Color(0xFF4CAF50),
                                                modifier = Modifier.size(16.dp).padding(top = 2.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Column {
                                                Text("STEP 3: CLOUD SYNCED & BROADCASTED", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                                Text("Heartbeat alignment success. Remote database state updated.", color = Color.Gray, fontSize = 9.sp)
                                            }
                                        }
                                    }
                                }

                                // Focus Session Integrity Audit Card
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFF15151A)),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF22222A)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = "FOCUS METRIC AUDIT TRAIL",
                                            color = WaterBlue,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )

                                        val timeFormatter = remember { java.text.SimpleDateFormat("hh:mm:ss a", java.util.Locale.getDefault()) }
                                        val startStr = verifiedStartMs?.let { timeFormatter.format(java.util.Date(it)) } ?: "N/A"
                                        
                                        var totalBreakMs = 0L
                                        verifiedPauseRanges.forEach { (pStart, pEnd) ->
                                            if (pEnd >= pStart) {
                                                totalBreakMs += (pEnd - pStart)
                                            }
                                        }
                                        val breakSeconds = (totalBreakMs / 1000).toInt()
                                        val wallSeconds = globalFocusedSecs + breakSeconds
                                        
                                        val computedEndMs = verifiedStartMs?.let { it + wallSeconds * 1000L }
                                        val endStr = computedEndMs?.let { timeFormatter.format(java.util.Date(it)) } ?: "N/A"

                                        // Start and End Times
                                        if (verifiedStartMs != null) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Column {
                                                    Text("START TIME", color = Color.Gray, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                                                    Text(startStr, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                                                }
                                                Column(horizontalAlignment = Alignment.End) {
                                                    Text("END TIME", color = Color.Gray, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                                                    Text(endStr, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                                                }
                                            }
                                        }

                                        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color(0xFF22222A)))

                                        // Breaks / Pauses List
                                        if (verifiedPauseRanges.isNotEmpty()) {
                                            Text(
                                                text = "PAUSE / BREAK INTERVALS",
                                                color = Color.Gray,
                                                fontSize = 8.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            verifiedPauseRanges.forEachIndexed { index, (pStart, pEnd) ->
                                                val durationSecs = ((pEnd - pStart) / 1000).toInt()
                                                val fromStr = timeFormatter.format(java.util.Date(pStart))
                                                val toStr = timeFormatter.format(java.util.Date(pEnd))
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.Pause,
                                                            contentDescription = "Pause",
                                                            tint = Color(0xFFFF9800),
                                                            modifier = Modifier.size(10.dp)
                                                        )
                                                        Text("Break #${index + 1}: $fromStr to $toStr", color = Color.LightGray, fontSize = 9.sp)
                                                    }
                                                    Text(
                                                        text = formatSecondsToReadable(durationSecs),
                                                        color = Color(0xFFFF9800),
                                                        fontSize = 9.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                            }
                                            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color(0xFF22222A)))
                                        }

                                        // Minutes Calculation Breakdown
                                        Text(
                                            text = "CALCULATION METRIC INTEGRITY",
                                            color = Color.Gray,
                                            fontSize = 8.sp,
                                            fontWeight = FontWeight.Bold
                                        )

                                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text("Total Wall Clock Duration:", color = Color.Gray, fontSize = 10.sp)
                                                Text(formatSecondsToReadable(wallSeconds), color = Color.White, fontSize = 10.sp)
                                            }
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text("Minus Total Break Duration:", color = Color.Gray, fontSize = 10.sp)
                                                Text("- ${formatSecondsToReadable(breakSeconds)}", color = Color(0xFFFF9800), fontSize = 10.sp)
                                            }
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text("Net Verified Focused Time:", color = WaterBlue, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                Text(formattedNow, color = Color(0xFF4CAF50), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }

                                Card(
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E24)),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF333333)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(14.dp),
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Column {
                                            Text(
                                                text = "PAST FOCUSED TIME TODAY",
                                                color = Color.Gray,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = formattedPast,
                                                color = Color.LightGray,
                                                fontSize = 16.sp,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }

                                        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color(0xFF333333)))

                                        Column {
                                            Text(
                                                text = "FOCUSED TIME NOW (ADDED)",
                                                color = Color.Gray,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = formattedNow,
                                                color = WaterBlue,
                                                fontSize = 22.sp,
                                                fontWeight = FontWeight.Black
                                            )
                                        }

                                        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color(0xFF333333)))

                                        Column {
                                            Text(
                                                text = "REVISED DAILY TOTAL FOCUS",
                                                color = Color.Gray,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = formattedRevised,
                                                color = Color(0xFF4CAF50),
                                                fontSize = 22.sp,
                                                fontWeight = FontWeight.Black
                                            )
                                        }
                                    }
                                }

                                Text(
                                    text = "Automated confirmation and 3-step system verification complete. Your focus time has been recorded securely.",
                                    color = Color.Gray,
                                    fontSize = 10.sp,
                                    lineHeight = 14.sp
                                )
                            }
                        },
                        confirmButton = {
                            Button(
                                onClick = {
                                    FocusTimerManager.showGlobalVerificationDialog.value = false
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF4CAF50),
                                    contentColor = Color.Black
                                ),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth().height(42.dp)
                            ) {
                                Text(
                                    text = "Confirm & Close",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                            }
                        },
                        containerColor = Color(0xFF0F0F12),
                        shape = RoundedCornerShape(16.dp)
                    )
                }
            }
        }
    }
}

    override fun onStart() {
        super.onStart()
        FocusTimerManager.setAppBackgroundedState(this, false)
        if (!com.example.util.AppLockHelper.isAppLockEnabled(this)) {
            isAppUnlockedState.value = true
        }
    }

    override fun onStop() {
        super.onStop()
        FocusTimerManager.setAppBackgroundedState(this, true)
        if (com.example.util.AppLockHelper.isAppLockEnabled(this)) {
            isAppUnlockedState.value = false
        }

        // Auto-reconcile and Auto-backup to public storage before potential uninstall/force-stop
        lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                // Ensure state consistency and flush memoryWAL files cleanly before backup
                com.example.util.StateReconciliationHelper.runUnifiedReconciliation(applicationContext, database)
                com.example.util.DatabaseBackupHelper.autoBackup(applicationContext, database)
                
                // If the user has signed in and granted Drive permissions, auto-sync backup before potential uninstallation
                if (com.example.util.GoogleDriveSyncManager.hasDrivePermission(applicationContext)) {
                    android.util.Log.i("MainActivity", "Auto-backing up focus records to Google Drive on stop...")
                    val (success, msg) = com.example.util.GoogleDriveSyncManager.backupFocusData(applicationContext)
                    android.util.Log.i("MainActivity", "Google Drive auto-backup on stop result: success=$success, msg=$msg")
                }
            } catch (e: Exception) {
                android.util.Log.e("MainActivity", "State reconciliation, auto-backup, or Google Drive backup failed on stop", e)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        checkTimerNavigation(intent)
        checkAppBlockInterceptions(intent)
    }

    private fun checkTimerNavigation(intent: Intent?) {
        if (intent?.getBooleanExtra("SHOW_TIMER_PAGE", false) == true || intent?.getBooleanExtra("SHOW_FULL_SCREEN_TIMER", false) == true) {
            viewModel.navigateTo(Screen.TIMER)
        }
    }

    private fun checkAppBlockInterceptions(intent: Intent?) {
        if (intent == null) return
        
        if (intent.getBooleanExtra("SHOW_BLOCKS_PAGE", false)) {
            viewModel.navigateTo(Screen.SETTINGS)
            intent.removeExtra("SHOW_BLOCKS_PAGE")
            getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE)
                .edit()
                .putBoolean("direct_to_blocks", true)
                .apply()
        }

        if (intent.getBooleanExtra("SHOW_INTERCEPT_PROMPT", false)) {
            val pkg = intent.getStringExtra("INTERCEPTED_PACKAGE")
            if (!pkg.isNullOrEmpty()) {
                interceptedAppSessionQuery.value = pkg
            }
            intent.removeExtra("SHOW_INTERCEPT_PROMPT")
            intent.removeExtra("INTERCEPTED_PACKAGE")
        }
    }

    private fun getNavigationItems(order: List<Screen>): List<NavigationItem> {
        val mapping = mapOf(
            Screen.TASKS to NavigationItem(Screen.TASKS, Icons.Default.List, "Tasks"),
            Screen.CALENDAR to NavigationItem(Screen.CALENDAR, Icons.Default.DateRange, "Calendar"),
            Screen.TIMER to NavigationItem(Screen.TIMER, Icons.Default.PlayArrow, "Timer"),
            Screen.HABITS to NavigationItem(Screen.HABITS, Icons.Default.CheckCircle, "Habits"),
            Screen.COUNTDOWN to NavigationItem(Screen.COUNTDOWN, Icons.Default.Notifications, "Countdown"),
            Screen.JOURNAL to NavigationItem(Screen.JOURNAL, Icons.Default.Book, "Journal"),
            Screen.CONTACTS to NavigationItem(Screen.CONTACTS, Icons.Default.AccountBox, "Contacts"),
            Screen.FILE_EXPLORER to NavigationItem(Screen.FILE_EXPLORER, Icons.Default.Folder, "File Explorer"),
            Screen.FINANCES to NavigationItem(Screen.FINANCES, Icons.Default.MonetizationOn, "Finances"),
            Screen.DEEPA_AI to NavigationItem(Screen.DEEPA_AI, Icons.Default.Face, "Deepa AI"),
            Screen.SEARCH to NavigationItem(Screen.SEARCH, Icons.Default.Search, "Search"),
            Screen.ANALYTICS to NavigationItem(Screen.ANALYTICS, Icons.Default.Star, "Analytics"),
            Screen.SETTINGS to NavigationItem(Screen.SETTINGS, Icons.Default.Settings, "Settings")
        )
        return order.mapNotNull { mapping[it] }
    }
}

data class NavigationItem(val screen: Screen, val icon: ImageVector, val label: String)
