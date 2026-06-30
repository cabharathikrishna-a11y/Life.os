package com.example.ui

import android.app.KeyguardManager
import android.app.NotificationManager
import android.content.Context
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import kotlinx.coroutines.flow.first
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.example.data.AppDatabase
import com.example.ui.theme.MyApplicationTheme
import com.example.util.AlarmScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ReminderActivity : ComponentActivity() {

    private var ringtone: Ringtone? = null
    private var vibrator: Vibrator? = null
    private var taskId: Int = -1
    private var rawTaskId: Int = -1
    private var taskTitle: String = ""
    private var taskTime: String = ""
    private var taskPriority: String = "MEDIUM"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Parse extras
        taskId = intent.getIntExtra("TASK_ID", -1)
        rawTaskId = intent.getIntExtra("RAW_TASK_ID", -1)
        taskTitle = intent.getStringExtra("TASK_TITLE") ?: "Task Reminder"
        taskTime = intent.getStringExtra("TASK_TIME") ?: ""
        taskPriority = intent.getStringExtra("TASK_PRIORITY") ?: "MEDIUM"

        Log.d("ReminderActivity", "Reminder activity launched for task $taskId, priority: $taskPriority")

        // Lockscreen / Keep Screen active parameters compatibility for modern Android versions
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            val keyguardManager = getSystemService(KEYGUARD_SERVICE) as KeyguardManager
            keyguardManager.requestDismissKeyguard(this, null)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        enableEdgeToEdge()
        startAlert()

        setContent {
            MyApplicationTheme {
                // Priority-specific auto-dismiss
                if (taskPriority.uppercase() == "LOW" || taskPriority.uppercase() == "NONE") {
                    LaunchedEffect(Unit) {
                        Log.d("ReminderActivity", "Auto-dismissing in 5 minutes.")
                        kotlinx.coroutines.delay(5 * 60 * 1000L) // 5 minutes (300,000 ms)
                        stopAlert()
                        finish()
                    }
                }

                ReminderScreen(
                    title = taskTitle.substringBefore(" (").trim(),
                    time = taskTime,
                    onDismiss = {
                        stopAlert()
                        cancelNotification()
                        finish()
                    },
                    onSnooze = { minutes ->
                        stopAlert()
                        cancelNotification()
                        AlarmScheduler.scheduleSnooze(applicationContext, taskId, taskTitle, taskTime, taskPriority, minutes)
                        finish()
                    },
                    onComplete = {
                        stopAlert()
                        cancelNotification()
                        markTaskCompletedAndFinish()
                    }
                )
            }
        }
    }

    @Composable
    private fun ReminderScreen(
        title: String,
        time: String,
        onDismiss: () -> Unit,
        onSnooze: (Int) -> Unit,
        onComplete: () -> Unit
    ) {
        var snoozeMinutes by remember { mutableStateOf(15) }

        // Stunning blurred ambient background glow mimicking the user screenshot
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF070709))
                .drawBehind {
                    // Top-right teal glow aura
                    drawCircle(
                        color = Color(0xFF0F766E).copy(alpha = 0.28f),
                        radius = size.width * 0.8f,
                        center = androidx.compose.ui.geometry.Offset(size.width * 0.95f, size.height * 0.25f)
                    )
                    // Left purple glow aura
                    drawCircle(
                        color = Color(0xFF6D28D9).copy(alpha = 0.22f),
                        radius = size.width * 0.7f,
                        center = androidx.compose.ui.geometry.Offset(0f, size.height * 0.55f)
                    )
                    // Bottom sandy gold glow aura
                    drawCircle(
                        color = Color(0xFFD97706).copy(alpha = 0.24f),
                        radius = size.width * 0.9f,
                        center = androidx.compose.ui.geometry.Offset(size.width * 0.5f, size.height * 0.95f)
                    )
                }
                .padding(horizontal = 24.dp, vertical = 48.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxHeight().fillMaxWidth()
            ) {
                // Header details matching clean aesthetics
                Text(
                    text = "L I F E   O S   R E M I N D E R",
                    color = Color.White.copy(alpha = 0.4f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp,
                    modifier = Modifier.padding(top = 16.dp)
                )

                // Central Focus Text & Buttons (Take my vitamins, 2:00 pm, Dismiss, Complete)
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(32.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = title,
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .padding(horizontal = 16.dp)
                                .testTag("reminder_task_title")
                        )

                        if (time.isNotEmpty() && time != "None") {
                            Text(
                                text = time,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Normal,
                                color = Color.White.copy(alpha = 0.6f)
                            )
                        }
                    }

                    // Centered stack buttons: Dismiss and Complete (styled beautifully like pill shape buttons)
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.width(180.dp)
                    ) {
                        // Dismiss button with dark translucent container
                        Button(
                            onClick = onDismiss,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF2A3D3C).copy(alpha = 0.8f)
                            ),
                            shape = RoundedCornerShape(24.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("reminder_dismiss_btn")
                        ) {
                            Text(
                                text = "Dismiss",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
                        }

                        // Complete button with elegant cyan/teal container
                        Button(
                            onClick = onComplete,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF0F766E).copy(alpha = 0.9f)
                            ),
                            shape = RoundedCornerShape(24.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("reminder_completed_btn")
                        ) {
                            Text(
                                text = "Complete",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
                        }
                    }
                }

                // Bottom Interactive Capsule Bar for Custom Snoozing
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .padding(bottom = 16.dp)
                        .testTag("reminder_snooze_container")
                ) {
                    // Minus Button
                    IconButton(
                        onClick = { if (snoozeMinutes > 5) snoozeMinutes -= 5 },
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.08f))
                    ) {
                        Box(
                            modifier = Modifier
                                .width(14.dp)
                                .height(2.dp)
                                .background(Color.White.copy(alpha = 0.7f), shape = RoundedCornerShape(1.dp))
                        )
                    }

                    // Central Snooze Button
                    Button(
                        onClick = { onSnooze(snoozeMinutes) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFC7A168).copy(alpha = 0.15f)
                        ),
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier
                            .widthIn(min = 160.dp)
                            .height(48.dp)
                            .border(1.dp, Color(0xFFC7A168).copy(alpha = 0.3f), RoundedCornerShape(24.dp))
                            .testTag("reminder_snooze_btn")
                    ) {
                        Text(
                            text = "Snooze $snoozeMinutes mins",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFFE5C088)
                        )
                    }

                    // Plus Button
                    IconButton(
                        onClick = { if (snoozeMinutes < 120) snoozeMinutes += 5 },
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.08f))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Increase Snooze",
                            tint = Color.White.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        }
    }

    private fun startAlert() {
        val prefsForSilent = getSharedPreferences("app_prefs", MODE_PRIVATE)
        if (prefsForSilent.getBoolean("master_silent_mode", false) || prefsForSilent.getBoolean("task_silent_mode", false)) {
            Log.d("ReminderActivity", "Silent mode or master silent mode is ON. Suppressing sound and vibration.")
            return
        }

        // Audio playback configured using TYPE_NOTIFICATION (single shot alert)
        try {
            val alertUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            ringtone = RingtoneManager.getRingtone(applicationContext, alertUri)?.apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    val attributes = android.media.AudioAttributes.Builder()
                        .setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION)
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                    this.audioAttributes = attributes
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    // Always false: "single notification sound for any type of priority"
                    isLooping = false
                }
                play()
            }
        } catch (e: Exception) {
            Log.e("ReminderActivity", "Ringtone launch omitted: ${e.message}")
        }

        // Single short vibration for all priorities
        try {
            val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
            val taskVibrationEnabled = prefs.getBoolean("task_vibration_enabled", true)
            if (taskVibrationEnabled) {
                vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                vibrator?.let { v ->
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        v.vibrate(VibrationEffect.createOneShot(600, VibrationEffect.DEFAULT_AMPLITUDE))
                    } else {
                        @Suppress("DEPRECATION")
                        v.vibrate(600)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("ReminderActivity", "Vibrator launch failed: ${e.message}")
        }
    }

    private fun stopAlert() {
        try {
            ringtone?.stop()
            vibrator?.cancel()
        } catch (e: Exception) {
            Log.e("ReminderActivity", "Alert stop failed: ${e.message}")
        }
    }

    private fun cancelNotification() {
        try {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (taskId != -1) {
                notificationManager.cancel(taskId)
            }
            if (rawTaskId != -1) {
                notificationManager.cancel(rawTaskId)
            }
        } catch (e: Exception) {
            Log.e("ReminderActivity", "Notification cancellation failed: ${e.message}")
        }
    }

    private fun markTaskCompletedAndFinish() {
        if (taskId == -1) {
            finish()
            return
        }

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val db = AppDatabase.getInstance(applicationContext)
                val taskList = db.taskDao().getAllTasks().first()
                val targetTask = taskList.find { it.id == taskId }
                
                if (targetTask != null) {
                    db.taskDao().updateTask(targetTask.copy(isCompleted = true))
                    Log.d("ReminderActivity", "Task $taskId completed successfully in background.")
                    AlarmScheduler.cancelReminder(applicationContext, taskId)
                }
            } catch (e: Exception) {
                Log.e("ReminderActivity", "Error updates completion state in DB: ${e.message}")
            } finally {
                withContext(Dispatchers.Main) {
                    finish()
                }
            }
        }
    }

    override fun onDestroy() {
        stopAlert()
        super.onDestroy()
    }
}
