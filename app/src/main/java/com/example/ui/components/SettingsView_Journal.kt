package com.example.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.AppViewModel
import com.example.ui.theme.*

@Composable
fun SettingsJournalPage(
    viewModel: AppViewModel,
    onBack: () -> Unit
) {
    SettingsPageScope {
        val onThisDayNotificationEnabled by viewModel.onThisDayNotificationEnabled.collectAsState()
        val onThisDayNotificationTime by viewModel.onThisDayNotificationTime.collectAsState()
        val onThisDayOnScreenEnabled by viewModel.onThisDayOnScreenEnabled.collectAsState()

        SettingsSubpageWorkspace(
            title = "Life Journal Settings",
            description = "Configurations, storage maps.",
            onBack = onBack
        ) {
            Row(
                modifier = Modifier.padding(bottom = 16.dp)
            ) {
                Text(
                    text = "Journal records and metadata are completely local and private to your device.",
                    color = Color.LightGray,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0C0C0C)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("On This Day Notifications", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Sends a notification reminding you of historical journal entries written on this day in history.", color = Color.Gray, fontSize = 11.sp)
                        }
                        Switch(
                            checked = onThisDayNotificationEnabled,
                            onCheckedChange = { viewModel.updateOnThisDayNotificationEnabled(it) },
                            colors = SwitchDefaults.colors(checkedThumbColor = Color.Black, checkedTrackColor = WaterBlue),
                            modifier = Modifier.testTag("on_this_day_notification_switch")
                        )
                    }
                    
                    if (onThisDayNotificationEnabled) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Notification Trigger Time", color = Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(6.dp))
                        OutlinedTextField(
                            value = onThisDayNotificationTime,
                            onValueChange = { viewModel.updateOnThisDayNotificationTime(it) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = WaterBlue,
                                unfocusedBorderColor = Color(0xFF333333)
                            ),
                            placeholder = { Text("e.g. 09:00 AM or 18:30", color = Color.DarkGray, fontSize = 11.sp) },
                            modifier = Modifier.fillMaxWidth().testTag("on_this_day_notification_time_input")
                        )
                    }

                    HorizontalDivider(color = Color(0xFF222222), modifier = Modifier.padding(vertical = 12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("On Screen Reminders", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Displays an on-screen dialog when today's historic journal entries exist.", color = Color.Gray, fontSize = 11.sp)
                        }
                        Switch(
                            checked = onThisDayOnScreenEnabled,
                            onCheckedChange = { viewModel.updateOnThisDayOnScreenEnabled(it) },
                            colors = SwitchDefaults.colors(checkedThumbColor = Color.Black, checkedTrackColor = WaterBlue),
                            modifier = Modifier.testTag("on_this_day_onscreen_switch")
                        )
                    }
                }
            }
        }
    }
}
