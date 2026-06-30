package com.example.ui.components

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.AppViewModel
import com.example.util.AppLockHelper
import com.example.util.AppBlockHelper
import com.example.util.GoogleDriveSyncManager
import com.google.android.gms.auth.api.signin.GoogleSignIn
import android.content.Context
import android.app.Activity
import java.text.SimpleDateFormat
import java.util.Date
import kotlinx.coroutines.launch

@Composable
fun LifeOSBackupSection(viewModel: AppViewModel) {
    val context = LocalContext.current
    var statusText by remember { mutableStateOf<String?>(null) }
    
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            statusText = "Exporting data..."
            viewModel.exportBackup(context, uri) { success ->
                statusText = if (success) "Export completed successfully!" else "Failed to export data."
            }
        }
    }
    
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            statusText = "Importing and reconciling..."
            viewModel.importBackup(context, uri) { success ->
                statusText = if (success) "Import completed successfully! Life OS data synced." else "Failed to import backup."
            }
        }
    }
    
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxWidth().padding(8.dp)
    ) {
        Text("Manage Manual Snapshots", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Text("Export or restore your localized databases, settings, task lists, and history records securely.", color = Color.Gray, fontSize = 11.sp)
        
        Button(
            onClick = {
                exportLauncher.launch("life_os_backup_${System.currentTimeMillis()}.json")
            },
            modifier = Modifier.fillMaxWidth().height(48.dp)
        ) {
            Icon(Icons.Default.Share, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Export Manual Backup (JSON)")
        }
        
        Button(
            onClick = {
                importLauncher.launch(arrayOf("application/json", "application/octet-stream"))
            },
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1B1B1E), contentColor = Color.White),
            modifier = Modifier.fillMaxWidth().height(48.dp)
        ) {
            Icon(Icons.Default.Refresh, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Import and Reconcile Backup (JSON)")
        }
        
        statusText?.let {
            Text(it, color = if (it.contains("successfully")) Color.Green else Color.Red, fontSize = 12.sp)
        }
    }
}

@Composable
fun AppLockSettingsSection() {
    val context = LocalContext.current
    var isEnabled by remember { mutableStateOf(AppLockHelper.isAppLockEnabled(context)) }
    var lockType by remember { mutableStateOf(AppLockHelper.getLockType(context)) }
    var code by remember { mutableStateOf(AppLockHelper.getLockCode(context) ?: "") }
    var biometricsEnabled by remember { mutableStateOf(AppLockHelper.isBiometricsEnabled(context)) }
    var showSetupDialog by remember { mutableStateOf(false) }
    
    // Recovery Questions State
    val questions = remember { AppLockHelper.getSecurityQuestions(context) }
    var q1 by remember { mutableStateOf(questions[0].first) }
    var a1 by remember { mutableStateOf(questions[0].second) }
    var q2 by remember { mutableStateOf(questions[1].first) }
    var a2 by remember { mutableStateOf(questions[1].second) }
    var q3 by remember { mutableStateOf(questions[2].first) }
    var a3 by remember { mutableStateOf(questions[2].second) }
    
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxWidth().padding(8.dp)
    ) {
        Text("App Lock Configuration", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Enable App Lock", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Text("Require PIN or password when opening Life OS", color = Color.Gray, fontSize = 11.sp)
            }
            Switch(
                checked = isEnabled,
                onCheckedChange = { enabled ->
                    if (enabled) {
                        showSetupDialog = true
                    } else {
                        AppLockHelper.setAppLockEnabled(context, false)
                        AppLockHelper.setLockCode(context, null)
                        isEnabled = false
                        code = ""
                    }
                }
            )
        }
        
        if (isEnabled) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Lock Type", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Text("Select authentication mode", color = Color.Gray, fontSize = 11.sp)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = lockType == "pin",
                        onClick = {
                            lockType = "pin"
                            AppLockHelper.setLockType(context, "pin")
                        },
                        label = { Text("PIN") }
                    )
                    FilterChip(
                        selected = lockType == "password",
                        onClick = {
                            lockType = "password"
                            AppLockHelper.setLockType(context, "password")
                        },
                        label = { Text("Password") }
                    )
                }
            }
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Biometric Unlock", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Text("Allow face or fingerprint unlock if supported", color = Color.Gray, fontSize = 11.sp)
                }
                Switch(
                    checked = biometricsEnabled,
                    onCheckedChange = {
                        biometricsEnabled = it
                        AppLockHelper.setBiometricsEnabled(context, it)
                    }
                )
            }
        }
        
        if (showSetupDialog) {
            AlertDialog(
                onDismissRequest = { showSetupDialog = false },
                title = { Text("Setup Secure Lock") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Configure your security code and security questions to enable recovery in case you forget it.", color = Color.Gray, fontSize = 11.sp)
                        
                        OutlinedTextField(
                            value = code,
                            onValueChange = { code = it },
                            label = { Text(if (lockType == "pin") "Enter PIN (Digits)" else "Enter Password") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = if (lockType == "pin") KeyboardType.Number else KeyboardType.Password)
                        )
                        
                        Text("Recovery Security Questions", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 12.sp)
                        
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(q1, color = Color.LightGray, fontSize = 11.sp)
                            OutlinedTextField(
                                value = a1,
                                onValueChange = { a1 = it },
                                label = { Text("Answer 1") },
                                singleLine = true
                            )
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(q2, color = Color.LightGray, fontSize = 11.sp)
                            OutlinedTextField(
                                value = a2,
                                onValueChange = { a2 = it },
                                label = { Text("Answer 2") },
                                singleLine = true
                            )
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (code.isNotBlank() && a1.isNotBlank() && a2.isNotBlank()) {
                                AppLockHelper.setLockCode(context, code)
                                AppLockHelper.setAppLockEnabled(context, true)
                                AppLockHelper.saveSecurityQuestions(context, q1, a1, q2, a2, q3, a3)
                                AppLockHelper.setSecuritySetupComplete(context, true)
                                isEnabled = true
                                showSetupDialog = false
                            }
                        }
                    ) {
                        Text("Enable App Lock")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showSetupDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

@Composable
fun AppBlocksSettingsSection() {
    val context = LocalContext.current
    var hasPermission by remember { mutableStateOf(AppBlockHelper.hasUsageStatsPermission(context)) }
    var blockedApps by remember { mutableStateOf(AppBlockHelper.getBlockedApps(context)) }
    var selectedAppForLimit by remember { mutableStateOf<String?>(null) }
    var limitMinutesText by remember { mutableStateOf("30") }
    var showAddAppDialog by remember { mutableStateOf(false) }
    var newAppPackage by remember { mutableStateOf("") }
    
    // Refresh permission status when entering
    LaunchedEffect(Unit) {
        hasPermission = AppBlockHelper.hasUsageStatsPermission(context)
    }
    
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxWidth().padding(8.dp)
    ) {
        Text("App Blocks & Usage Limits", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Text("Set daily tracked screen-time limit quotas for distracting applications.", color = Color.Gray, fontSize = 11.sp)
        
        if (!hasPermission) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF2D1515)),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Usage Stats Permission Required", color = Color.Red, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text("Life OS requires the Usage Access permission to track open times and enforce screen limits.", color = Color.LightGray, fontSize = 11.sp)
                    Button(
                        onClick = {
                            try {
                                val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                // Fallback
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                    ) {
                        Text("Grant Permission", fontSize = 12.sp)
                    }
                }
            }
        }
        
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0C0C0E)),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Blocked Apps List", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    IconButton(onClick = { showAddAppDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = "Add App", tint = Color.White)
                    }
                }
                
                if (blockedApps.isEmpty()) {
                    Text("No apps added yet.", color = Color.Gray, fontSize = 11.sp)
                } else {
                    blockedApps.forEach { pkg ->
                        val limitMins = AppBlockHelper.getDailyLimitMinutes(context, pkg)
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(pkg, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                Text("Daily Limit: $limitMins minutes", color = Color.Gray, fontSize = 10.sp)
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                IconButton(onClick = {
                                    selectedAppForLimit = pkg
                                    limitMinutesText = limitMins.toString()
                                }) {
                                    Icon(Icons.Default.Edit, contentDescription = "Edit Limit", tint = Color.Gray, modifier = Modifier.size(16.dp))
                                }
                                IconButton(onClick = {
                                    AppBlockHelper.removeBlockedApp(context, pkg)
                                    blockedApps = AppBlockHelper.getBlockedApps(context)
                                }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Red, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
        
        selectedAppForLimit?.let { pkg ->
            AlertDialog(
                onDismissRequest = { selectedAppForLimit = null },
                title = { Text("Edit App Limit") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(pkg, color = Color.Gray, fontSize = 11.sp)
                        OutlinedTextField(
                            value = limitMinutesText,
                            onValueChange = { limitMinutesText = it },
                            label = { Text("Daily Limit (minutes)") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val mins = limitMinutesText.toIntOrNull() ?: 30
                            AppBlockHelper.setDailyLimitMinutes(context, pkg, mins)
                            selectedAppForLimit = null
                            blockedApps = AppBlockHelper.getBlockedApps(context)
                        }
                    ) {
                        Text("Save")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { selectedAppForLimit = null }) {
                        Text("Cancel")
                    }
                }
            )
        }
        
        if (showAddAppDialog) {
            AlertDialog(
                onDismissRequest = { showAddAppDialog = false },
                title = { Text("Add App to Block List") },
                text = {
                    OutlinedTextField(
                        value = newAppPackage,
                        onValueChange = { newAppPackage = it },
                        label = { Text("Package Name (e.g., com.facebook.katana)") },
                        singleLine = true
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (newAppPackage.isNotBlank()) {
                                AppBlockHelper.addBlockedApp(context, newAppPackage.trim())
                                blockedApps = AppBlockHelper.getBlockedApps(context)
                                showAddAppDialog = false
                                newAppPackage = ""
                            }
                        }
                    ) {
                        Text("Add")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showAddAppDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

@Composable
fun GoogleDriveSyncSection(viewModel: AppViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    var syncStatus by remember { mutableStateOf<String?>(null) }
    var isOperating by remember { mutableStateOf(false) }
    
    // Last Sync Time representation
    val prefs = remember { context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE) }
    var lastSyncTs by remember { mutableStateOf(prefs.getLong("gd_focus_last_sync_timestamp", 0L)) }
    
    val googleAccount = remember { GoogleSignIn.getLastSignedInAccount(context) }
    val hasPermission = remember { GoogleDriveSyncManager.hasDrivePermission(context) }
    
    // Auth resolution launcher
    val authResolutionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            syncStatus = "Google Drive authorized! Tap Sync or Restore to begin."
        } else {
            syncStatus = "Google Drive authorization declined."
        }
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF09090C)),
        border = androidx.compose.foundation.BorderStroke(1.dp, WaterBlue.copy(alpha = 0.2f)),
        modifier = Modifier.fillMaxWidth().padding(8.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.CloudSync,
                    contentDescription = "Cloud Sync",
                    tint = WaterBlue,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = "Google Drive Sync (Focus Data)",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }
            
            Text(
                text = "Keep your focus sessions, logs, and pomodoro metrics synchronized securely in a private, hidden folder on your Google Drive. Supports seamless retrieval in case of app uninstallation or reinstallation.",
                color = Color.Gray,
                fontSize = 11.sp,
                lineHeight = 15.sp
            )
            
            HorizontalDivider(color = Color(0xFF1E1E22), thickness = 0.5.dp)

            // Account status card
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "ACCOUNT STATUS",
                        color = Color.Gray,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold
                    )
                    if (googleAccount != null) {
                        Text(
                            text = googleAccount.email ?: "Signed In",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    } else {
                        Text(
                            text = "Not Connected (Sign in via Google first)",
                            color = Color(0xFFFF5252),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
                
                if (googleAccount != null) {
                    if (hasPermission) {
                        Box(
                            modifier = Modifier
                                .background(Color(0xFF1B5E20), RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text("AUTHORIZED", color = Color.Green, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Button(
                            onClick = {
                                scope.launch {
                                    GoogleDriveSyncManager.getAccessToken(context) { intent ->
                                        authResolutionLauncher.launch(intent)
                                    }
                                }
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            modifier = Modifier.height(28.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = WaterBlue)
                        ) {
                            Text("AUTHORIZE", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Last Sync card
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "LAST SYNCED",
                        color = Color.Gray,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold
                    )
                    val syncStr = if (lastSyncTs > 0L) {
                        SimpleDateFormat("yyyy-MM-dd hh:mm:ss a", java.util.Locale.getDefault()).format(Date(lastSyncTs))
                    } else {
                        "Never"
                    }
                    Text(
                        text = syncStr,
                        color = if (lastSyncTs > 0L) Color.LightGray else Color.Gray,
                        fontSize = 11.sp
                    )
                }
            }

            if (isOperating) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(color = WaterBlue, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("Synchronizing with Google Drive...", color = Color.Gray, fontSize = 11.sp)
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = {
                            if (googleAccount == null) {
                                syncStatus = "Please sign in to your Google Account on the Login screen first."
                                return@Button
                            }
                            isOperating = true
                            syncStatus = "Backing up..."
                            scope.launch {
                                val (success, msg) = GoogleDriveSyncManager.backupFocusData(context) { intent ->
                                    authResolutionLauncher.launch(intent)
                                }
                                isOperating = false
                                syncStatus = msg
                                if (success) {
                                    lastSyncTs = prefs.getLong("gd_focus_last_sync_timestamp", 0L)
                                }
                            }
                        },
                        modifier = Modifier.weight(1f).height(40.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E1E24), contentColor = Color.White),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF333333))
                    ) {
                        Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Backup Now", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = {
                            if (googleAccount == null) {
                                syncStatus = "Please sign in to your Google Account on the Login screen first."
                                return@Button
                            }
                            isOperating = true
                            syncStatus = "Restoring..."
                            scope.launch {
                                val (success, msg) = GoogleDriveSyncManager.restoreFocusData(context) { intent ->
                                    authResolutionLauncher.launch(intent)
                                }
                                isOperating = false
                                syncStatus = msg
                                if (success) {
                                    lastSyncTs = prefs.getLong("gd_focus_last_sync_timestamp", 0L)
                                }
                            }
                        },
                        modifier = Modifier.weight(1f).height(40.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = WaterBlue, contentColor = Color.Black)
                    ) {
                        Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Restore & Merge", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            syncStatus?.let {
                Text(
                    text = it,
                    color = if (it.contains("Successfully") || it.contains("granted") || it.contains("restored")) Color(0xFF4CAF50) else Color(0xFFFF9800),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    lineHeight = 14.sp
                )
            }
        }
    }
}
