package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.AppViewModel
import com.example.ui.Screen
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import kotlinx.coroutines.launch

@Composable
fun SettingsGeneralSystemPage(
    viewModel: AppViewModel,
    onBack: () -> Unit
) {
    SettingsPageScope {
        val tabOrder by viewModel.tabOrder.collectAsState()
        val tabBarOrientation by viewModel.tabBarOrientation.collectAsState()
        val additionalReminderTimes by viewModel.additionalReminderTimes.collectAsState()
        val antiBurnScreenEnabled by viewModel.antiBurnScreenEnabled.collectAsState()
        val hiddenTabs by viewModel.hiddenTabs.collectAsState()
        val allDayNotificationEnabled by viewModel.allDayNotificationEnabled.collectAsState()
        val allDayNotificationTime by viewModel.allDayNotificationTime.collectAsState()
        val onThisDayNotificationEnabled by viewModel.onThisDayNotificationEnabled.collectAsState()
        val onThisDayNotificationTime by viewModel.onThisDayNotificationTime.collectAsState()
        val onThisDayOnScreenEnabled by viewModel.onThisDayOnScreenEnabled.collectAsState()
        var tempOrder by remember(tabOrder) { mutableStateOf(tabOrder) }

        // General System Page
        SettingsSubpageWorkspace(
            title = "General System Settings",
            description = "Configure core systems, tab layout orientation and app reordering.",
            onBack = onBack
        ) {
            // Alignment Options Column Block
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0C0C0C)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Navigation Bar Position",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Set the tab position: left (sidebar), right, top, bottom, or legacy profiles.",
                        color = Color.Gray,
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf("left", "right").forEach { mode ->
                                val isSelected = tabBarOrientation.lowercase() == mode
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isSelected) WaterBlue else Color(0xFF141414))
                                        .clickable { viewModel.updateTabBarOrientation(mode) }
                                        .padding(vertical = 12.dp)
                                        .testTag("tab_mode_${mode}"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(text = mode.uppercase(), color = if (isSelected) Color.Black else Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf("top", "bottom").forEach { mode ->
                                val isSelected = tabBarOrientation.lowercase() == mode
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isSelected) WaterBlue else Color(0xFF141414))
                                        .clickable { viewModel.updateTabBarOrientation(mode) }
                                        .padding(vertical = 12.dp)
                                        .testTag("tab_mode_${mode}"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(text = mode.uppercase(), color = if (isSelected) Color.Black else Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Tab order customization
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0C0C0C)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Reorder navigation tabs",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Move tabs up and down to customize their layout position.",
                        color = Color.Gray,
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        tempOrder.forEachIndexed { index, screen ->
                            val label = when (screen) {
                                Screen.TASKS -> "Tasks"
                                Screen.CALENDAR -> "Calendar"
                                Screen.TIMER -> "Timer"
                                Screen.HABITS -> "Habits"
                                Screen.COUNTDOWN -> "Countdown"
                                Screen.JOURNAL -> "Journal"
                                Screen.CONTACTS -> "Contacts"
                                Screen.FILE_EXPLORER -> "File Explorer"
                                Screen.FINANCES -> "Finances"
                                Screen.DEEPA_AI -> "Deepa AI"
                                Screen.SEARCH -> "Search"
                                Screen.ANALYTICS -> "Analytics"
                                Screen.SETTINGS -> "Settings"
                                Screen.LOGIN -> "Login"
                                Screen.PROFILE_SETUP -> "Profile Setup"
                                Screen.PERMISSION_ONBOARDING -> "Permissions Onboarding"
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFF141414), RoundedCornerShape(6.dp))
                                    .padding(8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(label, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)

                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                                    val isTabHidden = hiddenTabs.contains(screen)
                                    IconButton(
                                        onClick = { viewModel.toggleTabVisibility(screen) },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (isTabHidden) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                            contentDescription = "Toggle Visibility",
                                            tint = if (isTabHidden) Color.Gray else WaterBlue,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }

                                    IconButton(
                                        onClick = {
                                            if (index > 0) {
                                                val list = tempOrder.toMutableList()
                                                val tmp = list.removeAt(index)
                                                list.add(index - 1, tmp)
                                                tempOrder = list
                                            }
                                        },
                                        modifier = Modifier.size(28.dp),
                                        enabled = index > 0
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.KeyboardArrowUp,
                                            contentDescription = "Move Up",
                                            tint = if (index > 0) Color.White else Color.DarkGray,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }

                                    IconButton(
                                        onClick = {
                                            if (index < tempOrder.size - 1) {
                                                val list = tempOrder.toMutableList()
                                                val tmp = list.removeAt(index)
                                                list.add(index + 1, tmp)
                                                tempOrder = list
                                            }
                                        },
                                        modifier = Modifier.size(28.dp),
                                        enabled = index < tempOrder.size - 1
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.KeyboardArrowDown,
                                            contentDescription = "Move Down",
                                            tint = if (index < tempOrder.size - 1) Color.White else Color.DarkGray,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Button(
                            onClick = { viewModel.saveTabOrder(tempOrder) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp)
                                .testTag("save_tab_order_subpage_btn"),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = WaterBlue, contentColor = Color.Black)
                        ) {
                            Text("SAVE TAB ORDER", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Master Silent Mode Card
            val masterSilentMode by viewModel.masterSilentModeEnabled.collectAsState()
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0C0C0C)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("MASTER SILENT MODE", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("When enabled, all app reminders, sounds, and vibrations are completely silenced.", color = Color.Gray, fontSize = 11.sp)
                    }
                    Switch(
                        checked = masterSilentMode,
                        onCheckedChange = { viewModel.updateMasterSilentModeEnabled(it) },
                        colors = SwitchDefaults.colors(checkedThumbColor = WaterBlue, checkedTrackColor = WaterBlue.copy(alpha = 0.5f)),
                        modifier = Modifier.testTag("master_silent_mode_switch")
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // SYSTEM UPDATE & DRIVE APK DOWNLOAD ENGINE
            val updateStatus by com.example.util.AppUpdateManager.updateStatus.collectAsState()
            val coroutineScope = rememberCoroutineScope()
            val context = LocalContext.current
            val currentVerName = remember { com.example.util.AppUpdateManager.getCurrentVersionName(context) }
            val currentVerCode = remember { com.example.util.AppUpdateManager.getCurrentVersionCode(context) }
            
            val autoUpdateEnabled = remember { mutableStateOf(com.example.util.AppUpdateManager.isAutoUpdateEnabled(context)) }
            val forceUpdateEnabled = remember { mutableStateOf(com.example.util.AppUpdateManager.isForceUpdateEnabled(context)) }
            val pauseUpdatesEnabled = remember { mutableStateOf(com.example.util.AppUpdateManager.isPauseUpdatesEnabled(context)) }

            Card(
                modifier = Modifier.fillMaxWidth().testTag("system_update_card"),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0C0C0C)),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, if (updateStatus is com.example.util.UpdateStatus.NewVersionAvailable) WaterBlue.copy(alpha = 0.5f) else Color.Transparent)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(WaterBlue.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.SystemUpdate,
                                contentDescription = "Update Icon",
                                tint = WaterBlue,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "SYSTEM AUTO-UPDATE",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Current Version: v$currentVerName ($currentVerCode)",
                                color = Color.Gray,
                                fontSize = 11.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    when (val status = updateStatus) {
                        is com.example.util.UpdateStatus.Idle -> {
                            Text(
                                text = "No update in progress. Check Firebase to see if a newer version ID has been manually published.",
                                color = Color.LightGray,
                                fontSize = 11.sp
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = {
                                    com.example.util.AppUpdateManager.triggerCheckForUpdates(context, manualCheck = true)
                                },
                                modifier = Modifier.fillMaxWidth().height(36.dp),
                                shape = RoundedCornerShape(6.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF161618), contentColor = Color.White)
                            ) {
                                Text("CHECK FOR SYSTEM UPDATES", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        is com.example.util.UpdateStatus.Checking -> {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    color = WaterBlue,
                                    strokeWidth = 2.dp
                                )
                                Text(
                                    text = "Connecting to Firebase RTDB cloud servers...",
                                    color = Color.Gray,
                                    fontSize = 11.sp
                                )
                            }
                        }
                        is com.example.util.UpdateStatus.NewVersionAvailable -> {
                            Column {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(WaterBlue.copy(alpha = 0.08f), RoundedCornerShape(6.dp))
                                        .padding(10.dp)
                                ) {
                                    Text(
                                        text = "🚨 UPDATE DETECTED!\nNew cloud version ID found: #${status.versionId} (Your current version: #$currentVerCode).\n\nReady to download the corresponding published APK file directly from GitHub/Cloud automatically.",
                                        color = WaterBlue,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        lineHeight = 15.sp
                                    )
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(
                                        onClick = {
                                            com.example.util.AppUpdateManager.startDownloadAndInstall(context, status.apkFileId)
                                        },
                                        modifier = Modifier.weight(1f).height(38.dp),
                                        shape = RoundedCornerShape(6.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = WaterBlue, contentColor = Color.Black)
                                    ) {
                                        Text("DOWNLOAD & INSTALL NOW", fontSize = 10.sp, fontWeight = FontWeight.ExtraBold)
                                    }
                                    Button(
                                        onClick = { com.example.util.AppUpdateManager.resetStatus() },
                                        modifier = Modifier.height(38.dp),
                                        shape = RoundedCornerShape(6.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF161618), contentColor = Color.LightGray)
                                    ) {
                                        Text("DISMISS", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                        is com.example.util.UpdateStatus.NoUpdateAvailable -> {
                            Column {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color(0xFF141A14), RoundedCornerShape(6.dp))
                                        .padding(10.dp)
                                ) {
                                    val cloudVer = status.cloudVersion
                                    val localVer = status.localVersion
                                    val statusText = if (cloudVer <= 0) {
                                        "✓ LIFE OS IS UP TO DATE!\n\n• Installed Version: Code #$localVer\n• Cloud Target Version: Not Set / -1\n\nNo newer version was detected. Note: If you expect an update, make sure the cloud database version ID is higher than #$localVer."
                                    } else {
                                        "✓ LIFE OS IS UP TO DATE!\n\n• Installed Version: Code #$localVer\n• Cloud Target Version: Code #$cloudVer\n\nNo newer version was detected. Your app is running the latest published version."
                                    }
                                    Text(
                                        text = statusText,
                                        color = Color(0xFF81C784),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        lineHeight = 15.sp
                                    )
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                                Button(
                                    onClick = { com.example.util.AppUpdateManager.resetStatus() },
                                    modifier = Modifier.fillMaxWidth().height(36.dp),
                                    shape = RoundedCornerShape(6.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF161618), contentColor = Color.White)
                                ) {
                                    Text("OK", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                        is com.example.util.UpdateStatus.SecuringData -> {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    color = WaterBlue,
                                    strokeWidth = 2.dp
                                )
                                Text(
                                    text = "🔒 Securing local databases & settings...",
                                    color = WaterBlue,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        is com.example.util.UpdateStatus.Downloading -> {
                            Column {
                                Text(
                                    text = if (status.progress >= 0) {
                                        "Downloading APK: ${(status.progress * 100).toInt()}%"
                                    } else {
                                        "Downloading APK from Cloud..."
                                    },
                                    color = Color.LightGray,
                                    fontSize = 11.sp
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                if (status.progress >= 0) {
                                    LinearProgressIndicator(
                                        progress = { status.progress },
                                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                                        color = WaterBlue,
                                        trackColor = Color(0xFF1A1A1E)
                                    )
                                } else {
                                    LinearProgressIndicator(
                                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                                        color = WaterBlue,
                                        trackColor = Color(0xFF1A1A1E)
                                    )
                                }
                            }
                        }
                        is com.example.util.UpdateStatus.ReadyToInstall -> {
                            Column {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color(0xFF141A14), RoundedCornerShape(6.dp))
                                        .padding(10.dp)
                                ) {
                                    Text(
                                        text = "✓ APK Download Completed!\nReady to install the new update securely.",
                                        color = Color(0xFF81C784),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(
                                        onClick = { com.example.util.AppUpdateManager.installApk(context, status.apkFile) },
                                        modifier = Modifier.weight(1f).height(38.dp),
                                        shape = RoundedCornerShape(6.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = WaterBlue, contentColor = Color.Black)
                                    ) {
                                        Text("TRIGGER INSTALLATION", fontSize = 10.sp, fontWeight = FontWeight.ExtraBold)
                                    }
                                    Button(
                                        onClick = { com.example.util.AppUpdateManager.resetStatus() },
                                        modifier = Modifier.height(38.dp),
                                        shape = RoundedCornerShape(6.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF161618), contentColor = Color.LightGray)
                                    ) {
                                        Text("CANCEL", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                        is com.example.util.UpdateStatus.Error -> {
                            Column {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color(0xFF221515), RoundedCornerShape(6.dp))
                                        .padding(10.dp)
                                ) {
                                    Text(
                                        text = status.message,
                                        color = Color(0xFFE57373),
                                        fontSize = 11.sp,
                                        lineHeight = 15.sp
                                    )
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(
                                        onClick = {
                                            com.example.util.AppUpdateManager.triggerCheckForUpdates(context, manualCheck = true)
                                        },
                                        modifier = Modifier.weight(1f).height(38.dp),
                                        shape = RoundedCornerShape(6.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = WaterBlue, contentColor = Color.Black)
                                    ) {
                                        Text("RETRY CHECK", fontSize = 10.sp, fontWeight = FontWeight.ExtraBold)
                                    }
                                    Button(
                                        onClick = { com.example.util.AppUpdateManager.resetStatus() },
                                        modifier = Modifier.height(38.dp),
                                        shape = RoundedCornerShape(6.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF161618), contentColor = Color.LightGray)
                                    ) {
                                        Text("DISMISS", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(color = Color(0xFF222225), thickness = 1.dp)
                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "GITHUB RELEASE SOURCE",
                        color = Color.LightGray,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    var githubOwnerInput by remember { mutableStateOf(com.example.util.AppUpdateManager.getGithubOwner(context)) }
                    var githubRepoInput by remember { mutableStateOf(com.example.util.AppUpdateManager.getGithubRepo(context)) }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = githubOwnerInput,
                            onValueChange = { 
                                githubOwnerInput = it
                                com.example.util.AppUpdateManager.setGithubOwner(context, it)
                            },
                            label = { Text("GitHub Owner", color = Color.Gray, fontSize = 10.sp) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = WaterBlue,
                                unfocusedBorderColor = Color(0xFF222222),
                                focusedContainerColor = Color(0xFF070707),
                                unfocusedContainerColor = Color(0xFF070707),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            modifier = Modifier.weight(1f).height(54.dp),
                            textStyle = TextStyle(fontSize = 11.sp)
                        )

                        OutlinedTextField(
                            value = githubRepoInput,
                            onValueChange = { 
                                githubRepoInput = it
                                com.example.util.AppUpdateManager.setGithubRepo(context, it)
                            },
                            label = { Text("Repository Name", color = Color.Gray, fontSize = 10.sp) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = WaterBlue,
                                unfocusedBorderColor = Color(0xFF222222),
                                focusedContainerColor = Color(0xFF070707),
                                unfocusedContainerColor = Color(0xFF070707),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            modifier = Modifier.weight(1f).height(54.dp),
                            textStyle = TextStyle(fontSize = 11.sp)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(color = Color(0xFF222225), thickness = 1.dp)
                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "UPDATE PREFERENCES",
                        color = Color.LightGray,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Row for Auto Update Enabled
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Auto-Download & Install", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Text("Download and install updates automatically in the background.", color = Color.Gray, fontSize = 11.sp)
                        }
                        Switch(
                            checked = autoUpdateEnabled.value,
                            onCheckedChange = {
                                autoUpdateEnabled.value = it
                                com.example.util.AppUpdateManager.setAutoUpdateEnabled(context, it)
                            },
                            colors = SwitchDefaults.colors(checkedThumbColor = WaterBlue, checkedTrackColor = WaterBlue.copy(alpha = 0.5f)),
                            modifier = Modifier.testTag("auto_update_switch")
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Row for Force Update Enabled
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Force Updates", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Text("Require installation and block cancellation when a new update is downloaded.", color = Color.Gray, fontSize = 11.sp)
                        }
                        Switch(
                            checked = forceUpdateEnabled.value,
                            onCheckedChange = {
                                forceUpdateEnabled.value = it
                                com.example.util.AppUpdateManager.setForceUpdateEnabled(context, it)
                            },
                            colors = SwitchDefaults.colors(checkedThumbColor = WaterBlue, checkedTrackColor = WaterBlue.copy(alpha = 0.5f)),
                            modifier = Modifier.testTag("force_update_switch")
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Row for Pause All Updates
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Pause All Updates", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Text("Temporarily suspend background and startup checks for updates.", color = Color.Gray, fontSize = 11.sp)
                        }
                        Switch(
                            checked = pauseUpdatesEnabled.value,
                            onCheckedChange = {
                                pauseUpdatesEnabled.value = it
                                com.example.util.AppUpdateManager.setPauseUpdatesEnabled(context, it)
                            },
                            colors = SwitchDefaults.colors(checkedThumbColor = WaterBlue, checkedTrackColor = WaterBlue.copy(alpha = 0.5f)),
                            modifier = Modifier.testTag("pause_updates_switch")
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Row for Staging Mode
                    val isStagingMode by viewModel.isStagingMode.collectAsState()
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Staging & Mock Users", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Text("Show simulated friends and mock records (madhavan, shalini, subash).", color = Color.Gray, fontSize = 11.sp)
                        }
                        Switch(
                            checked = isStagingMode,
                            onCheckedChange = {
                                viewModel.setStagingMode(it)
                            },
                            colors = SwitchDefaults.colors(checkedThumbColor = WaterBlue, checkedTrackColor = WaterBlue.copy(alpha = 0.5f)),
                            modifier = Modifier.testTag("staging_mode_switch")
                        )
                    }
                }
            }
        }
    }
}
