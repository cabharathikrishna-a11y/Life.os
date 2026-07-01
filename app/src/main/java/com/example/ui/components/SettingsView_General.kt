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
                                Screen.CALENDAR_OPTIMIZATION_ONBOARDING -> "Calendar Optimization"
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

            // Staging Mode Card
            val isStagingMode by viewModel.isStagingMode.collectAsState()
            Card(
                modifier = Modifier.fillMaxWidth().testTag("staging_mode_card"),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0C0C0C)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Staging & Mock Users", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
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
