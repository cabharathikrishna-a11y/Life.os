package com.example.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.AppViewModel
import com.example.ui.theme.*

val WaterBlue = Color(0x0FF38B0F2) // Shared WaterBlue theme accent color

@Composable
fun SettingsPageScope(content: @Composable () -> Unit) {
    Box(modifier = Modifier.fillMaxSize()) {
        content()
    }
}

@Composable
fun SettingsView(viewModel: AppViewModel, modifier: Modifier = Modifier) {
    val context = LocalContext.current

    val directToBlocks = remember {
        val shared = context.getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE)
        val value = shared.getBoolean("direct_to_blocks", false)
        if (value) {
            shared.edit().putBoolean("direct_to_blocks", false).apply()
        }
        value
    }
    var activePage by remember { mutableStateOf(if (directToBlocks) 14 else 0) }

    val vmActivePage by viewModel.settingsActivePage.collectAsState()
    LaunchedEffect(vmActivePage) {
        if (activePage != vmActivePage) {
            activePage = vmActivePage
        }
    }
    LaunchedEffect(activePage) {
        if (activePage != vmActivePage) {
            viewModel.updateSettingsActivePage(activePage)
        }
    }
    val isAdminUser by viewModel.isAdmin.collectAsState()

    when (activePage) {
        0 -> {
            LazyColumn(
                modifier = modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Centered Welcome Header
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF09090C)),
                        shape = RoundedCornerShape(20.dp),
                        border = BorderStroke(1.dp, WaterBlue.copy(alpha = 0.2f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    Brush.linearGradient(
                                        colors = listOf(WaterBlue.copy(alpha = 0.12f), Color.Transparent)
                                    )
                                )
                                .padding(18.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(WaterBlue.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Settings,
                                        contentDescription = "Settings Icon",
                                        tint = WaterBlue,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(14.dp))
                                Column {
                                    Text(
                                        text = "SETTINGS CENTER",
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Black,
                                        color = Color.White,
                                        letterSpacing = 0.8.sp
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "Configure and personalize your localized Life OS experience.",
                                        fontSize = 11.sp,
                                        color = Color.Gray,
                                        lineHeight = 14.sp
                                    )
                                }
                            }
                        }
                    }
                }

                // Group 1: Core Systems & AI
                item {
                    SettingsCategoryGroup(title = "Core Systems & AI") {
                        SettingsRowItem(
                            title = "1. GENERAL SYSTEM",
                            subtitle = "Tab alignment, navigation bar reordering, style configurations",
                            icon = Icons.Default.Settings,
                            iconBgColor = Color(0xFF2196F3)
                        ) { activePage = 1 }
                        HorizontalDivider(color = Color(0xFF1E1E22), thickness = 0.5.dp, modifier = Modifier.padding(start = 56.dp, end = 16.dp))
                        SettingsRowItem(
                            title = "DIAGNOSTICS & BACKGROUND",
                            subtitle = "Fix stopwatch lockscreen freeze & background recording on Samsung/Oppo/Lenovo/Moto",
                            icon = Icons.Default.Info,
                            iconBgColor = Color(0xFFE53935)
                        ) { activePage = 17 }
                        HorizontalDivider(color = Color(0xFF1E1E22), thickness = 0.5.dp, modifier = Modifier.padding(start = 56.dp, end = 16.dp))
                        SettingsRowItem(
                            title = "2. DEEPA AI BRAIN",
                            subtitle = "Offline model caching, memories vault management",
                            icon = Icons.Default.Face,
                            iconBgColor = Color(0xFF00E5FF)
                        ) { activePage = 11 }
                        HorizontalDivider(color = Color(0xFF1E1E22), thickness = 0.5.dp, modifier = Modifier.padding(start = 56.dp, end = 16.dp))
                        SettingsRowItem(
                            title = "3. BACKUP & RESTORE",
                            subtitle = "JSON manual database import & security exports",
                            icon = Icons.Default.Refresh,
                            iconBgColor = Color(0xFFFFB300)
                        ) { activePage = 12 }
                    }
                }

                // Group 2: Productivity Suite
                item {
                    SettingsCategoryGroup(title = "Productivity Core") {
                        SettingsRowItem(
                            title = "4. TIMER CONFIGURATION",
                            subtitle = "Session periods, default break times, vibration style toggles",
                            icon = Icons.Default.PlayArrow,
                            iconBgColor = Color(0xFFFF3D00)
                        ) { activePage = 2 }
                        HorizontalDivider(color = Color(0xFF1E1E22), thickness = 0.5.dp, modifier = Modifier.padding(start = 56.dp, end = 16.dp))
                        SettingsRowItem(
                            title = "5. TASKS ENGINE",
                            subtitle = "Reminder frequencies, custom vibrators, default lists",
                            icon = Icons.Default.List,
                            iconBgColor = Color(0xFF4CAF50)
                        ) { activePage = 3 }
                        HorizontalDivider(color = Color(0xFF1E1E22), thickness = 0.5.dp, modifier = Modifier.padding(start = 56.dp, end = 16.dp))
                        SettingsRowItem(
                            title = "6. CALENDAR PLANNER",
                            subtitle = "Style layouts, display settings, timeline filters",
                            icon = Icons.Default.DateRange,
                            iconBgColor = Color(0xFF9C27B0)
                        ) { activePage = 4 }
                        HorizontalDivider(color = Color(0xFF1E1E22), thickness = 0.5.dp, modifier = Modifier.padding(start = 56.dp, end = 16.dp))
                        SettingsRowItem(
                            title = "7. HABITS TRACKER",
                            subtitle = "Streak calculations, automatic midnight reset triggers",
                            icon = Icons.Default.Refresh,
                            iconBgColor = Color(0xFFFF8F00)
                        ) { activePage = 5 }
                    }
                }

                // Group 3: Metrics & Journal Logs
                item {
                    SettingsCategoryGroup(title = "Logs & Utilities") {
                        SettingsRowItem(
                            title = "8. COUNTDOWNS & ALERTS",
                            subtitle = "Background notifications, custom alert parameters",
                            icon = Icons.Default.Notifications,
                            iconBgColor = Color(0xFF00E676)
                        ) { activePage = 6 }
                        HorizontalDivider(color = Color(0xFF1E1E22), thickness = 0.5.dp, modifier = Modifier.padding(start = 56.dp, end = 16.dp))
                        SettingsRowItem(
                            title = "9. LIFE JOURNAL",
                            subtitle = "Storage usage indexers, backup matching constraints",
                            icon = Icons.Default.Book,
                            iconBgColor = Color(0xFFE91E63)
                        ) { activePage = 7 }
                        HorizontalDivider(color = Color(0xFF1E1E22), thickness = 0.5.dp, modifier = Modifier.padding(start = 56.dp, end = 16.dp))
                        SettingsRowItem(
                            title = "10. CONTACTS DIRECTORY",
                            subtitle = "Full syncing filters, categories pairing, anniversaries",
                            icon = Icons.Default.AccountBox,
                            iconBgColor = Color(0xFF03A9F4)
                        ) { activePage = 8 }
                    }
                }

                // Group 4: Sandbox & Wealth
                item {
                    SettingsCategoryGroup(title = "File & Financials") {
                        SettingsRowItem(
                            title = "11. FILE EXPLORER",
                            subtitle = "Workspace directories, index preferred storage",
                            icon = Icons.Default.Folder,
                            iconBgColor = Color(0xFF8D6E63)
                        ) { activePage = 9 }
                        HorizontalDivider(color = Color(0xFF1E1E22), thickness = 0.5.dp, modifier = Modifier.padding(start = 56.dp, end = 16.dp))
                        SettingsRowItem(
                            title = "12. FINANCIAL LEDGER",
                            subtitle = "Accounts, custom family members, categories reporting",
                            icon = Icons.Default.MonetizationOn,
                            iconBgColor = Color(0xFF4CAF50)
                        ) { activePage = 10 }
                    }
                }

                // Group 5: Deep Security
                item {
                    SettingsCategoryGroup(title = "Security & Privacy Settings") {
                        SettingsRowItem(
                            title = "13. SECURE APP LOCK",
                            subtitle = "Verify code settings, PIN setups, recover questions",
                            icon = Icons.Default.Lock,
                            iconBgColor = Color(0xFFE91E63)
                        ) { activePage = 13 }
                        HorizontalDivider(color = Color(0xFF1E1E22), thickness = 0.5.dp, modifier = Modifier.padding(start = 56.dp, end = 16.dp))
                        SettingsRowItem(
                            title = "14. BLOCKS & SCREEN LIMITS",
                            subtitle = "Establish application constraints, usage warnings",
                            icon = Icons.Default.Block,
                            iconBgColor = Color(0xFFD32F2F)
                        ) { activePage = 14 }
                    }
                }

                // Group 6: Account
                item {
                    SettingsCategoryGroup(title = "Account & Sync") {
                        SettingsRowItem(
                            title = "15. USER INFO",
                            subtitle = "Edit your profile details, nickname, and emoji",
                            icon = Icons.Default.Person,
                            iconBgColor = Color(0xFF673AB7)
                        ) { activePage = 15 }
                        HorizontalDivider(color = Color(0xFF1E1E22), thickness = 0.5.dp, modifier = Modifier.padding(start = 56.dp, end = 16.dp))
                        SettingsRowItem(
                            title = "LOGOUT",
                            subtitle = "Sign out from the current online account securely",
                            icon = Icons.Default.ExitToApp,
                            iconBgColor = Color(0xFFD32F2F)
                        ) { viewModel.logout() }
                    }
                }
                


                item {
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }

        1 -> {
            SettingsGeneralSystemPage(
                viewModel = viewModel,
                onBack = { activePage = 0 }
            )
        }

        2 -> {
            SettingsTimerConfigurationPage(
                viewModel = viewModel,
                onBack = { activePage = 0 }
            )
        }

        3 -> {
            SettingsTasksPage(
                viewModel = viewModel,
                onBack = { activePage = 0 }
            )
        }

        4 -> {
            SettingsSubpageWorkspace(
                title = "Calendar Planner Settings",
                description = "Custom calendar display preferences and layout rules.",
                onBack = { activePage = 0 }
            ) {
                CalendarSettingsSection(viewModel = viewModel)
            }
        }

        5 -> {
            SettingsHabitsPage(
                viewModel = viewModel,
                onBack = { activePage = 0 }
            )
        }

        6 -> {
            SettingsCountdownAlertsPage(
                viewModel = viewModel,
                onBack = { activePage = 0 }
            )
        }

        7 -> {
            SettingsJournalPage(
                viewModel = viewModel,
                onBack = { activePage = 0 }
            )
        }

        8 -> {
            SettingsContactsPage(
                viewModel = viewModel,
                onBack = { activePage = 0 }
            )
        }

        9 -> {
            SettingsFileExplorerPage(
                viewModel = viewModel,
                onBack = { activePage = 0 }
            )
        }

        10 -> {
            SettingsFinancialsPage(
                viewModel = viewModel,
                onBack = { activePage = 0 }
            )
        }

        11 -> {
            SettingsDeepaAIPage(
                viewModel = viewModel,
                onBack = { activePage = 0 }
            )
        }

        12 -> {
            SettingsSubpageWorkspace(
                title = "Backup & Restore",
                description = "Export and import your entire Life OS data via simple JSON backup.",
                onBack = { activePage = 0 }
            ) {
                LifeOSBackupSection(viewModel = viewModel)
                Spacer(modifier = Modifier.height(16.dp))
                GoogleDriveSyncSection(viewModel = viewModel)
            }
        }

        13 -> {
            SettingsSubpageWorkspace(
                title = "Secure App Lock",
                description = "Configure fingerprint/face biometric unlock, secure multi-digit PIN, or alphanumeric Password protection along with backup recovery.",
                onBack = { activePage = 0 }
            ) {
                AppLockSettingsSection()
            }
        }

        14 -> {
            SettingsSubpageWorkspace(
                title = "Blocks & Screen Limits",
                description = "Configure daily tracked limit quotas for Instagram, Facebook, Snapchat, or other manual apps.",
                onBack = { activePage = 0 }
            ) {
                AppBlocksSettingsSection()
            }
        }
        
        15 -> {
            SettingsUserInfoPage(
                viewModel = viewModel,
                onBack = { activePage = 0 }
            )
        }
        


        17 -> {
            SettingsBackgroundDiagnosticsPage(
                viewModel = viewModel,
                onBack = { activePage = 0 }
            )
        }
    }
}

@Composable
fun SettingsCategoryGroup(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Text(
            text = title.uppercase(),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = WaterBlue,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(start = 8.dp, bottom = 6.dp)
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0C0C0E)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(content = content)
        }
    }
}

@Composable
fun SettingsRowItem(
    title: String,
    subtitle: String,
    icon: ImageVector,
    iconBgColor: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(iconBgColor.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = iconBgColor,
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(1.dp))
            Text(
                text = subtitle,
                color = Color.Gray,
                fontSize = 10.5.sp,
                lineHeight = 14.sp
            )
        }
        Icon(
            imageVector = Icons.Default.KeyboardArrowRight,
            contentDescription = "Arrow",
            tint = Color.DarkGray,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
fun SettingsSubpageWorkspace(
    title: String,
    description: String,
    onBack: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = title.uppercase(),
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 0.5.sp
                )
                Text(
                    text = description,
                    color = Color.Gray,
                    fontSize = 10.sp,
                    maxLines = 1
                )
            }
        }
        HorizontalDivider(color = Color(0xFF1A1A1E), thickness = 1.dp)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            content = content
        )
    }
}

@Composable
fun CalendarSettingsSection(viewModel: AppViewModel) {
    val context = LocalContext.current
    val syncStatus by viewModel.calendarSyncStatus.collectAsState()

    var hasPermission by remember {
        mutableStateOf(
            androidx.core.content.ContextCompat.checkSelfPermission(
                context, android.Manifest.permission.READ_CALENDAR
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED &&
            androidx.core.content.ContextCompat.checkSelfPermission(
                context, android.Manifest.permission.WRITE_CALENDAR
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = androidx.compose.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasPermission = (permissions[android.Manifest.permission.READ_CALENDAR] ?: false) &&
                        (permissions[android.Manifest.permission.WRITE_CALENDAR] ?: false)
    }

    // Prefs
    val prefs = remember { context.getSharedPreferences("app_calendar_prefs", android.content.Context.MODE_PRIVATE) }
    var selectedAccount by remember { mutableStateOf(prefs.getString("selected_calendar_account", null)) }
    var selectedName by remember { mutableStateOf(prefs.getString("selected_calendar_name", null)) }
    var selectedId by remember { mutableStateOf(prefs.getLong("selected_calendar_id", -1L)) }

    // Query calendars if permission is granted
    val calendars = remember(hasPermission) {
        if (hasPermission) {
            com.example.util.GoogleCalendarSyncHelper.getAvailableCalendars(context)
        } else {
            emptyList()
        }
    }

    // Dropdown states
    var accountExpanded by remember { mutableStateOf(false) }
    var nameExpanded by remember { mutableStateOf(false) }

    val uniqueAccounts = remember(calendars) {
        calendars.map { it.accountName }.distinct()
    }

    val filteredNames = remember(calendars, selectedAccount) {
        if (selectedAccount == null) calendars else calendars.filter { it.accountName == selectedAccount }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0D0D11)),
            border = BorderStroke(1.dp, Color(0xFF222225)),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(
                        imageVector = Icons.Default.DateRange,
                        contentDescription = "GCal",
                        tint = WaterBlue,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        "Google Calendar Sync",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Text(
                    "Enable bidirectional background synchronization with Google Calendar. Whenever you open the Calendar or modify tasks, synchronization occurs silently and automatically.",
                    color = Color.LightGray,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )

                if (!hasPermission) {
                    Button(
                        onClick = {
                            permissionLauncher.launch(
                                arrayOf(
                                    android.Manifest.permission.READ_CALENDAR,
                                    android.Manifest.permission.WRITE_CALENDAR
                                )
                            )
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = WaterBlue),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Grant Calendar Permissions", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                } else {
                    Text(
                        "✓ Calendar Permissions Granted",
                        color = Color(0xFF81C784),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        if (hasPermission) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0D0D11)),
                border = BorderStroke(1.dp, Color(0xFF222225)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "Synchronized Account & Calendar",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )

                    // Dropdown for Google Account
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Google Account", color = Color.Gray, fontSize = 11.sp)
                        Box(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFF16161B), RoundedCornerShape(8.dp))
                                    .clickable { accountExpanded = true }
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = selectedAccount ?: "Choose Google Account (Default: First GCal)",
                                    color = if (selectedAccount != null) Color.White else Color.Gray,
                                    fontSize = 13.sp
                                )
                                Icon(Icons.Default.ArrowDropDown, contentDescription = "Dropdown", tint = Color.Gray)
                            }

                            DropdownMenu(
                                expanded = accountExpanded,
                                onDismissRequest = { accountExpanded = false },
                                modifier = Modifier.background(Color(0xFF1B1B22))
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Default (First Google Account)", color = Color.White) },
                                    onClick = {
                                        selectedAccount = null
                                        selectedName = null
                                        selectedId = -1L
                                        prefs.edit()
                                            .remove("selected_calendar_account")
                                            .remove("selected_calendar_name")
                                            .putLong("selected_calendar_id", -1L)
                                            .apply()
                                        accountExpanded = false
                                    }
                                )
                                uniqueAccounts.forEach { acc ->
                                    DropdownMenuItem(
                                        text = { Text(acc, color = Color.White) },
                                        onClick = {
                                            selectedAccount = acc
                                            // Reset selectedName if not belonging to this account
                                            if (calendars.none { it.accountName == acc && it.displayName == selectedName }) {
                                                selectedName = null
                                                selectedId = -1L
                                            }
                                            prefs.edit()
                                                .putString("selected_calendar_account", acc)
                                                .putString("selected_calendar_name", selectedName)
                                                .putLong("selected_calendar_id", selectedId)
                                                .apply()
                                            accountExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // Dropdown for Calendar Name
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Calendar Name", color = Color.Gray, fontSize = 11.sp)
                        Box(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFF16161B), RoundedCornerShape(8.dp))
                                    .clickable { nameExpanded = true }
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = selectedName ?: "Choose Calendar (Default: Main)",
                                    color = if (selectedName != null) Color.White else Color.Gray,
                                    fontSize = 13.sp
                                )
                                Icon(Icons.Default.ArrowDropDown, contentDescription = "Dropdown", tint = Color.Gray)
                            }

                            DropdownMenu(
                                expanded = nameExpanded,
                                onDismissRequest = { nameExpanded = false },
                                modifier = Modifier.background(Color(0xFF1B1B22))
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Default (Primary Calendar)", color = Color.White) },
                                    onClick = {
                                        selectedName = null
                                        selectedId = -1L
                                        prefs.edit()
                                            .remove("selected_calendar_name")
                                            .putLong("selected_calendar_id", -1L)
                                            .apply()
                                        nameExpanded = false
                                    }
                                )
                                filteredNames.forEach { cal ->
                                    DropdownMenuItem(
                                        text = { Text(cal.displayName, color = Color.White) },
                                        onClick = {
                                            selectedName = cal.displayName
                                            selectedId = cal.id
                                            prefs.edit()
                                                .putString("selected_calendar_name", cal.displayName)
                                                .putLong("selected_calendar_id", cal.id)
                                                .apply()
                                            nameExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Sync Status", color = Color.Gray, fontSize = 11.sp)
                            Text(syncStatus, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = { viewModel.syncGoogleCalendar(context) },
                            colors = ButtonDefaults.buttonColors(containerColor = WaterBlue),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text("Sync Now", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}
