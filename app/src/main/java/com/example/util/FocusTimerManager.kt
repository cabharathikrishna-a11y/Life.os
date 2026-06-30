package com.example.util

import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.util.TypedValue
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import android.media.AudioManager
import android.media.AudioDeviceInfo
import com.example.data.AppDatabase
import com.example.data.Task
import com.example.service.KeepAliveService
import com.example.ui.FocusRecord
import com.example.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

object FocusTimerManager {
    private val logLock = Any()
    private val recordLock = Any()
    private val initLock = Any()
    private val ONE_HOUR_MS = java.util.concurrent.TimeUnit.HOURS.toMillis(1)
    private val TWELVE_HOURS_SECONDS = java.util.concurrent.TimeUnit.HOURS.toSeconds(12).toInt()

    // System Audit Log definitions
    data class SystemLogEntry(
        val id: String = java.util.UUID.randomUUID().toString(),
        val timestamp: Long = System.currentTimeMillis(),
        val event: String,
        val category: String, // e.g. "BUTTON_PRESS", "AUTO_SAVE", "FIREBASE_SYNC", "STATE_RESTORE", "CALCULATION", "ALARM"
        val details: String
    )

    val systemLogs = MutableStateFlow<List<SystemLogEntry>>(emptyList())

    fun addSystemLog(context: Context?, event: String, category: String, details: String) {
        val log = SystemLogEntry(
            event = event,
            category = category,
            details = details
        )
        systemLogs.update { current ->
            val updated = current.toMutableList()
            updated.add(0, log)
            if (updated.size > 200) updated.take(200) else updated
        }

        context?.let { ctx ->
            scope.launch(Dispatchers.IO) {
                synchronized(logLock) {
                    try {
                        val prefs = ctx.applicationContext.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                        val serialized = systemLogs.value.joinToString("\n") { entry ->
                            val encodedEvent = android.util.Base64.encodeToString(entry.event.toByteArray(), android.util.Base64.NO_WRAP)
                            val encodedDetails = android.util.Base64.encodeToString(entry.details.toByteArray(), android.util.Base64.NO_WRAP)
                            "${entry.id}|${entry.timestamp}|${encodedEvent}|${entry.category}|${encodedDetails}"
                        }
                        prefs.edit().putString("system_logs_serialized2", serialized).apply()
                    } catch (e: Exception) {
                        Log.e("FocusTimerManager", "Failed to save system logs", e)
                    }
                }
            }
        }
    }

    fun clearSystemLogs(context: Context) {
        systemLogs.value = emptyList()
        val prefs = context.applicationContext.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        prefs.edit().remove("system_logs_serialized2").apply()
    }

    fun loadSystemLogs(context: Context): List<SystemLogEntry> {
        val prefs = context.applicationContext.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val serialized = prefs.getString("system_logs_serialized2", null) ?: return emptyList()
        if (serialized.isEmpty()) return emptyList()
        return try {
            serialized.split("\n").mapNotNull { line ->
                if (line.isBlank()) return@mapNotNull null
                val parts = line.split("|")
                if (parts.size >= 5) {
                    val id = parts[0]
                    val timestamp = parts[1].toLongOrNull() ?: System.currentTimeMillis()
                    val event = try {
                        String(android.util.Base64.decode(parts[2], android.util.Base64.NO_WRAP))
                    } catch (e: Exception) { "[Corrupted Event]" }
                    val category = parts[3]
                    val details = try {
                        String(android.util.Base64.decode(parts[4], android.util.Base64.NO_WRAP))
                    } catch (e: Exception) { "[Corrupted Details]" }
                    SystemLogEntry(id, timestamp, event, category, details)
                } else null
            }
        } catch (e: Exception) {
            Log.e("FocusTimerManager", "Failed to load system logs", e)
            emptyList()
        }
    }

    fun syncStateToFirebase(context: Context) {
        val prefs = context.applicationContext.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val currentUsername = prefs.getString("current_username", null)
        val isLoggedIn = prefs.getBoolean("is_logged_in", false)
        val isAdmin = prefs.getBoolean("is_admin", false)
        
        if (isLoggedIn && !isAdmin && currentUsername != null) {
            val isTimerActive = isTimerRunning.value
            val isSwActive = isStopwatchActive.value
            val isFocus = isFocusPhase.value
            val cumSecs = cumulativeSessionFocusSeconds.value
            val swSecs = stopwatchSeconds.value
            val attachedTaskTitle = attachedTask.value?.title

            scope.launch(Dispatchers.IO) {
                try {
                    addSystemLog(context, "Firebase Sync Started", "FIREBASE_SYNC", "TimerActive=$isTimerActive, StopwatchActive=$isSwActive, Focus=$isFocus, CumSecs=$cumSecs, SwSecs=$swSecs")
                    val response = com.example.api.FirebaseClient.api.getUsers()
                    if (response.isSuccessful) {
                        val users = response.body()
                        val baseUser = users?.get(currentUsername)
                        if (baseUser != null) {
                            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
                            val todayStr = sdf.format(java.util.Date())
                            
                            val todayRecords = focusRecords.value.filter { r -> r.dateString == todayStr || r.dateString.isEmpty() }

                            val isRunning = isTimerRunning.value || isStopwatchActive.value
                            val focusStatus = if (!isFocus) {
                                "break"
                            } else if (isTimerRunning.value || isStopwatchActive.value) {
                                "focusing"
                            } else if (accumulatedSessionTimeMs.value > 0) {
                                "paused"
                            } else {
                                "idle"
                            }

                            val updatedUser = baseUser.copy(
                                isFocusing = isRunning,
                                accumulatedTimeMs = accumulatedSessionTimeMs.value,
                                lastResumeTimeMs = if (isRunning) lastResumeTimeMs.value else null,
                                focusStatus = focusStatus,
                                currentTaskTitle = if (isFocus) attachedTaskTitle else null,
                                todaysFocusRecords = todayRecords,
                                isStopwatchMode = isSwActive,
                                lastUpdatedTimestamp = System.currentTimeMillis()
                            )
                            com.example.api.FirebaseClient.api.putUser(currentUsername, updatedUser)
                            addSystemLog(context, "Firebase Sync Success", "FIREBASE_SYNC", "User state updated: status=$focusStatus, accumulatedTime=${accumulatedSessionTimeMs.value}")
                            try {
                                com.example.widget.WidgetUpdater.updateAllWidgets(context)
                            } catch (we: Exception) {
                                Log.e("FocusTimerManager", "Widget update failed during firebase sync", we)
                            }
                        }
                    } else {
                        addSystemLog(context, "Firebase Sync Failed", "FIREBASE_SYNC", "Server returned error code: ${response.code()}")
                    }
                } catch (e: Exception) {
                    addSystemLog(context, "Firebase Sync Error", "FIREBASE_SYNC", "Error: ${e.message}")
                }
            }
        }
    }

    fun performCloudAlignmentCheck(context: Context) {
        val prefs = context.applicationContext.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val currentUsername = prefs.getString("current_username", null)
        val isLoggedIn = prefs.getBoolean("is_logged_in", false)
        val isAdmin = prefs.getBoolean("is_admin", false)
        
        if (!isLoggedIn || isAdmin || currentUsername == null) {
            addSystemLog(context, "Alignment Check Skipped", "FIREBASE_SYNC", "User is not logged in or is admin. Check skipped.")
            return
        }

        val isTimerActive = isTimerRunning.value
        val isSwActive = isStopwatchActive.value
        val isFocus = isFocusPhase.value
        val cumSecs = cumulativeSessionFocusSeconds.value
        val swSecs = stopwatchSeconds.value
        val attachedTaskTitle = attachedTask.value?.title

        scope.launch(Dispatchers.IO) {
            try {
                addSystemLog(context, "Alignment Querying", "FIREBASE_SYNC", "Fetching online records to match with local database...")
                val response = com.example.api.FirebaseClient.api.getUsers()
                if (response.isSuccessful) {
                    val users = response.body()
                    val baseUser = users?.get(currentUsername)
                    if (baseUser != null) {
                        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
                        val todayStr = sdf.format(java.util.Date())
                        
                        // 1. Calculate local today's seconds and records
                        val completedTodaySeconds = focusRecords.value.sumOf { r ->
                            getOverlapSecondsForDate(r, todayStr)
                        }
                        val localTodayRecords = focusRecords.value.filter { r -> r.dateString == todayStr || r.dateString.isEmpty() }
                        
                        val activeSessionSeconds = if (isFocus) {
                            if (cumSecs > 0) cumSecs else if (swSecs > 0) swSecs else 0
                        } else {
                            0
                        }
                        val localTotalTodayFocusedSeconds = completedTodaySeconds + activeSessionSeconds
                        
                        // 2. Fetch remote today's seconds and records
                        val remoteTodayRecords = baseUser.todaysFocusRecords ?: emptyList()
                        val remoteTotalTodayFocusedSeconds = remoteTodayRecords.sumOf { getOverlapSecondsForDate(it, todayStr) } + ((baseUser.accumulatedTimeMs ?: 0L) / 1000).toInt()

                        // Check if records lists match or need merging
                        val mergedRecordsMap = mutableMapOf<String, FocusRecord>()
                        
                        // Populate map with local records
                        localTodayRecords.forEach { record ->
                            val key = "${record.startTime}|${record.endTime}|${record.taskTitle}"
                            mergedRecordsMap[key] = record
                        }

                        var alignmentDiscrepancyFound = false
                        var newFromRemoteCount = 0

                        // Check remote records
                        remoteTodayRecords.forEach { record ->
                            val key = "${record.startTime}|${record.endTime}|${record.taskTitle}"
                            if (!mergedRecordsMap.containsKey(key)) {
                                mergedRecordsMap[key] = record
                                alignmentDiscrepancyFound = true
                                newFromRemoteCount++
                            }
                        }

                        // Also check if remote total seconds has discrepancy
                        if (localTotalTodayFocusedSeconds != remoteTotalTodayFocusedSeconds) {
                            alignmentDiscrepancyFound = true
                        }

                        if (!alignmentDiscrepancyFound) {
                            addSystemLog(
                                context,
                                "Database Match Confirmed",
                                "FIREBASE_SYNC",
                                "100% matched! Local & online databases are perfectly aligned. (Today's Focus: ${formatTime(localTotalTodayFocusedSeconds)}, Records: ${localTodayRecords.size} items)"
                            )
                        } else {
                            // Reconcile and merge!
                            addSystemLog(
                                context,
                                "Discrepancy Detected",
                                "FIREBASE_SYNC",
                                "Mismatch found! Local has ${localTodayRecords.size} records (${formatTime(localTotalTodayFocusedSeconds)}). Remote has ${remoteTodayRecords.size} records (${formatTime(remoteTotalTodayFocusedSeconds)}). Auto-healing started..."
                            )
                            
                            // Merge remote-only records into local list
                            if (newFromRemoteCount > 0) {
                                val fullList = focusRecords.value.toMutableList()
                                val existingKeys = fullList.map { "${it.startTime}|${it.endTime}|${it.taskTitle}" }.toSet()
                                var addedCount = 0
                                remoteTodayRecords.forEach { record ->
                                    val key = "${record.startTime}|${record.endTime}|${record.taskTitle}"
                                    if (!existingKeys.contains(key)) {
                                        fullList.add(0, record)
                                        addedCount++
                                    }
                                }
                                if (addedCount > 0) {
                                    launch(Dispatchers.Main) {
                                        focusRecords.value = fullList
                                        saveFocusRecords(context, fullList)
                                    }
                                }
                            }

                            // Recalculate local totals after merge
                            val updatedCompletedTodaySeconds = focusRecords.value.sumOf { r ->
                                getOverlapSecondsForDate(r, todayStr)
                            }
                            val updatedTotalTodayFocusedSeconds = updatedCompletedTodaySeconds + activeSessionSeconds

                            // Sync back to Firebase to align remote database completely
                            val isFocusing = (isTimerActive || isSwActive) && isFocus
                            val focusStatus = if (!isFocus) {
                                "break"
                            } else if (isTimerActive || isSwActive) {
                                "focusing"
                            } else if (cumSecs > 0 || swSecs > 0) {
                                "paused"
                            } else {
                                "idle"
                            }

                            val updatedUser = baseUser.copy(
                                isFocusing = isFocusing,
                                accumulatedTimeMs = accumulatedSessionTimeMs.value,
                                lastResumeTimeMs = if (isFocusing) lastResumeTimeMs.value else null,
                                currentTaskTitle = if (isFocusing) attachedTaskTitle else null,
                                todaysFocusRecords = focusRecords.value.filter { r -> r.dateString == todayStr || r.dateString.isEmpty() },
                                isStopwatchMode = isSwActive,
                                lastUpdatedTimestamp = System.currentTimeMillis(),
                                focusStatus = focusStatus
                            )
                            com.example.api.FirebaseClient.api.putUser(currentUsername, updatedUser)

                            addSystemLog(
                                context,
                                "Database Auto-Reconciled",
                                "FIREBASE_SYNC",
                                "Self-healing alignment success. Merged database contains ${mergedRecordsMap.size} records with total focus time ${formatTime(updatedTotalTodayFocusedSeconds)}."
                            )
                        }
                    } else {
                        addSystemLog(context, "Alignment Check Incomplete", "FIREBASE_SYNC", "User object not found on server.")
                    }
                } else {
                    addSystemLog(context, "Alignment Query Failed", "FIREBASE_SYNC", "Server returned error: ${response.code()}")
                }
            } catch (e: Exception) {
                addSystemLog(context, "Alignment Query Error", "FIREBASE_SYNC", "Error: ${e.message}")
            }
        }
    }

    // Current Active States
    val accumulatedSessionTimeMs = MutableStateFlow(0L)
    val lastResumeTimeMs = MutableStateFlow<Long?>(null)

    val timerSecondsLeft = MutableStateFlow(25 * 60)
    val timerDurationMinutes = MutableStateFlow(25)
    
    val pendingFocusReview = MutableStateFlow<FocusRecord?>(null)
    val isTimerRunning = MutableStateFlow(false)
    val isFocusPhase = MutableStateFlow(true)
    val attachedTask = MutableStateFlow<Task?>(null)
    val attachedTag = MutableStateFlow<String>("")
    val focusTags = MutableStateFlow<List<String>>(emptyList())
    val cumulativeSessionFocusSeconds = MutableStateFlow(0)

    // Global verification/completion dialog states
    val showGlobalVerificationDialog = MutableStateFlow(false)
    val globalVerificationFocusedTimeSeconds = MutableStateFlow(0)
    val globalVerificationRevisedTotalMinutes = MutableStateFlow(0)
    val globalVerificationRevisedTotalSeconds = MutableStateFlow(0)

    // Session Verification & Break tracking variables
    val currentSessionStartMs = MutableStateFlow<Long?>(null)
    val currentSessionPauseRanges = MutableStateFlow<List<Pair<Long, Long>>>(emptyList())
    var tempPauseStartMs: Long? = null

    val verifiedSessionStartMs = MutableStateFlow<Long?>(null)
    val verifiedSessionPauseRanges = MutableStateFlow<List<Pair<Long, Long>>>(emptyList())

    fun recordSessionStart() {
        if (currentSessionStartMs.value == null) {
            currentSessionStartMs.value = System.currentTimeMillis()
        }
        if (tempPauseStartMs != null) {
            val pauseStart = tempPauseStartMs!!
            val pauseEnd = System.currentTimeMillis()
            currentSessionPauseRanges.value = currentSessionPauseRanges.value + Pair(pauseStart, pauseEnd)
            tempPauseStartMs = null
        }
    }

    fun recordSessionPause() {
        if (tempPauseStartMs == null) {
            tempPauseStartMs = System.currentTimeMillis()
        }
    }

    fun recordSessionCompleteOrReset(isSaving: Boolean) {
        if (isSaving) {
            verifiedSessionStartMs.value = currentSessionStartMs.value
            if (tempPauseStartMs != null) {
                val finalPauseRange = Pair(tempPauseStartMs!!, System.currentTimeMillis())
                verifiedSessionPauseRanges.value = currentSessionPauseRanges.value + finalPauseRange
            } else {
                verifiedSessionPauseRanges.value = currentSessionPauseRanges.value
            }
        }
        // Always reset current session tracking after transferring (or if not saving)
        currentSessionStartMs.value = null
        currentSessionPauseRanges.value = emptyList()
        tempPauseStartMs = null
    }

    // Stopwatch Active States
    val lastLocalInteractionTimestamp = MutableStateFlow(0L)

    fun updateLocalInteractionTimestamp() {
        lastLocalInteractionTimestamp.value = System.currentTimeMillis()
    }

    val stopwatchSeconds = MutableStateFlow(0)
    val isStopwatchActive = MutableStateFlow(false)
    val stopwatchLimitReached = MutableStateFlow(false)
    val isTabFocusTimerSelected = MutableStateFlow(false)
    val stopwatchBreakDurationMinutes = MutableStateFlow(5)
    val autoStartStopwatchAfterBreak = MutableStateFlow(true)
    val wasStartedFromStopwatch = MutableStateFlow(false)

    // User Stats States
    val todayPomosCount = MutableStateFlow(0)
    val totalFocusMinutes = MutableStateFlow(0)
    val focusRecords = MutableStateFlow<List<FocusRecord>>(emptyList())

    // Option toggles
    val soundEnabled = MutableStateFlow(true)
    val isBellSilentModeEnabled = MutableStateFlow(false)
    val autoStartBreak = MutableStateFlow(true)
    val autoStartPomo = MutableStateFlow(true)

    // UI context flags
    var isTimerScreenActive = false
    var appIsBackgrounded = false

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var timerJob: Job? = null
    private var stopwatchJob: Job? = null
    private var alarmJob: Job? = null

    // Window overlay objects
    private var overlayView: View? = null
    private var tvTimerText: TextView? = null
    private var tvCollapsedArrow: TextView? = null
    private var windowManager: WindowManager? = null

    private var isOverlayCollapsed = false
    private var overlayCollapsedSide = "none" // "none", "left", "right"

    @Volatile
    private var isInitialized = false
    private var appContext: Context? = null

    fun saveActiveSessionState(context: Context) {
        val prefs = context.applicationContext.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean("timer_is_running", isTimerRunning.value)
            .putLong("accumulated_time_ms", accumulatedSessionTimeMs.value)
            .putLong("last_resume_time_ms", lastResumeTimeMs.value ?: -1L)
            .putLong("timer_session_start_ms", currentSessionStartMs.value ?: -1L)
            .putInt("timer_cumulative_seconds", cumulativeSessionFocusSeconds.value)
            .putBoolean("timer_is_focus_phase", isFocusPhase.value)
            .putBoolean("timer_is_stopwatch_active", isStopwatchActive.value)
            .putBoolean("timer_was_started_from_stopwatch", wasStartedFromStopwatch.value)
            .putInt("timer_attached_task_id", attachedTask.value?.id ?: -1)
            .putString("timer_attached_tag", attachedTag.value)
            .putLong("timer_last_active_timestamp", System.currentTimeMillis())
            .apply()
    }

    fun init(context: Context) {
        if (isInitialized) {
            if (appContext == null) appContext = context.applicationContext
            return
        }
        synchronized(initLock) {
            if (isInitialized) {
                if (appContext == null) appContext = context.applicationContext
                return
            }
            isInitialized = true
            appContext = context.applicationContext
            systemLogs.value = loadSystemLogs(context)
            addSystemLog(context, "System Core Initialized", "SYSTEM", "Loaded ${systemLogs.value.size} persisted audit logs")
            val prefs = context.applicationContext.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            timerDurationMinutes.value = prefs.getInt("timer_duration", 25)
            
            val todayStr = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
            val lastResetDate = prefs.getString("last_midnight_reset_date", "")
            if (lastResetDate != todayStr) {
                todayPomosCount.value = 0
                prefs.edit()
                    .putInt("today_pomos_count", 0)
                    .putString("last_midnight_reset_date", todayStr)
                    .putBoolean("needs_firebase_midnight_reset", true)
                    .apply()
            } else {
                todayPomosCount.value = prefs.getInt("today_pomos_count", 0)
            }
            totalFocusMinutes.value = prefs.getInt("total_focus_minutes", 0)
            focusRecords.value = loadFocusRecords(context)
            soundEnabled.value = prefs.getBoolean("timer_sound_enabled", true)
            isBellSilentModeEnabled.value = prefs.getBoolean("bell_silent_mode_enabled", false)
            autoStartBreak.value = prefs.getBoolean("timer_autostart_break", true)
            autoStartPomo.value = prefs.getBoolean("timer_autostart_pomo", true)
            stopwatchBreakDurationMinutes.value = prefs.getInt("stopwatch_break_duration", 5)
            autoStartStopwatchAfterBreak.value = prefs.getBoolean("stopwatch_autostart_after_break", true)
            
            // Recover Active Session State
            val savedIsRunning = prefs.getBoolean("timer_is_running", false)
            val savedIsFocusPhase = prefs.getBoolean("timer_is_focus_phase", true)
            val savedIsStopwatchActive = prefs.getBoolean("timer_is_stopwatch_active", false)
            val savedWasStartedFromStopwatch = prefs.getBoolean("timer_was_started_from_stopwatch", false)
            val savedAttachedTaskId = prefs.getInt("timer_attached_task_id", -1)
            attachedTag.value = prefs.getString("timer_attached_tag", "") ?: ""
            focusTags.value = loadFocusTags(context)

            val savedAccumulated = prefs.getLong("accumulated_time_ms", 0L)
            val savedLastResume = prefs.getLong("last_resume_time_ms", -1L)
            val savedSessionStart = prefs.getLong("timer_session_start_ms", -1L)
            accumulatedSessionTimeMs.value = savedAccumulated
            lastResumeTimeMs.value = if (savedLastResume != -1L) savedLastResume else null
            currentSessionStartMs.value = if (savedSessionStart != -1L) savedSessionStart else null

            isFocusPhase.value = savedIsFocusPhase
            wasStartedFromStopwatch.value = savedWasStartedFromStopwatch

            if (savedIsRunning) {
                isTimerRunning.value = false
                startTimer(context, stopActiveAlarm = false)
            } else {
                isTimerRunning.value = false
                val totalDurationMs = timerDurationMinutes.value * 60 * 1000L
                timerSecondsLeft.value = maxOf(0, ((totalDurationMs - accumulatedSessionTimeMs.value) / 1000).toInt())
            }

            if (savedIsStopwatchActive) {
                isStopwatchActive.value = false
                startStopwatch(context, stopActiveAlarm = false)
            } else {
                isStopwatchActive.value = false
                stopwatchSeconds.value = (accumulatedSessionTimeMs.value / 1000).toInt()
            }

        if (savedAttachedTaskId != -1) {
            scope.launch(Dispatchers.IO) {
                try {
                    val db = AppDatabase.getInstance(context)
                    val task = db.taskDao().getTaskById(savedAttachedTaskId)
                    launch(Dispatchers.Main) {
                        attachedTask.value = task
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        } else {
            attachedTask.value = null
        }
        addSystemLog(context, "State Recovered", "STATE_RESTORE", "TimerRunning=$savedIsRunning, StopwatchActive=$savedIsStopwatchActive, AccumulatedTimeMs=${accumulatedSessionTimeMs.value}, SavedAttachedTaskId=$savedAttachedTaskId")

        // Hourly Google Drive Sync Job
        scope.launch(Dispatchers.IO) {
            while (true) {
                try {
                    // Wait for 1 hour
                    delay(ONE_HOUR_MS)
                    
                    if (GoogleDriveSyncManager.hasDrivePermission(context)) {
                        Log.d("FocusTimerManager", "Starting hourly automatic Google Drive sync...")
                        val (success, msg) = GoogleDriveSyncManager.backupFocusData(context)
                        Log.d("FocusTimerManager", "Hourly Google Drive backup outcome: success=$success, msg=$msg")
                        addSystemLog(context, "Hourly Google Drive Sync", "AUTO_SAVE", "Outcome: success=$success, msg=$msg")
                    } else {
                        Log.d("FocusTimerManager", "Skipping hourly Google Drive sync: Permission not granted yet.")
                    }
                } catch (e: Exception) {
                    Log.e("FocusTimerManager", "Error in hourly Google Drive sync job: ${e.message}", e)
                }
            }
        }
    }
}

    fun setStopwatchBreakDuration(context: Context, mins: Int) {
        init(context)
        stopwatchBreakDurationMinutes.value = mins
        val prefs = context.applicationContext.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        prefs.edit().putInt("stopwatch_break_duration", mins).apply()
    }

    fun setAutoStartStopwatchAfterBreak(context: Context, enabled: Boolean) {
        init(context)
        autoStartStopwatchAfterBreak.value = enabled
        val prefs = context.applicationContext.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("stopwatch_autostart_after_break", enabled).apply()
    }

    fun stopAlarm() {
        alarmJob?.cancel()
        alarmJob = null
    }

    fun playStrongBellSoundWithVibration(context: Context) {
        stopAlarm()
        alarmJob = scope.launch {
            val endTime = System.currentTimeMillis() + 10000L // 10 seconds duration
            
            // Check if any bluetooth devices are connected to keep volume nominal (safe)
            val isBT = isBluetoothAudioConnected(context)
            val volume = if (isBT) 35 else 100 // Nominal volume of 35 when bluetooth connected, else 100
            
            val tg = try {
                android.media.ToneGenerator(android.media.AudioManager.STREAM_ALARM, volume)
            } catch (e: Exception) {
                null
            }
            val vibrator = context.applicationContext.getSystemService(Context.VIBRATOR_SERVICE) as? android.os.Vibrator

            try {
                while (System.currentTimeMillis() < endTime) {
                    try {
                        // Distinct, strong bell-like alarm tone
                        tg?.startTone(android.media.ToneGenerator.TONE_CDMA_HIGH_L, 600)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }

                    try {
                        if (vibrator != null && vibrator.hasVibrator()) {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                vibrator.vibrate(android.os.VibrationEffect.createOneShot(500, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
                            } else {
                                @Suppress("DEPRECATION")
                                vibrator.vibrate(500)
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }

                    delay(1200L) // Wait a bit before repeating
                }
            } finally {
                tg?.release()
            }
        }
    }

    fun playFriendReminderBellSound(context: Context) {
        stopAlarm()
        alarmJob = scope.launch {
            val endTime = System.currentTimeMillis() + 5000L // 5 seconds duration
            
            val isBT = isBluetoothAudioConnected(context)
            val volume = if (isBT) 35 else 100 // Nominal volume of 35 when bluetooth connected, else 100
            
            val tg = try {
                android.media.ToneGenerator(android.media.AudioManager.STREAM_ALARM, volume)
            } catch (e: Exception) {
                null
            }
            val vibrator = context.applicationContext.getSystemService(Context.VIBRATOR_SERVICE) as? android.os.Vibrator

            try {
                while (System.currentTimeMillis() < endTime) {
                    try {
                        // Quick distinct ringing bell tone (TONE_CDMA_ALERT_CALL_GUARD or TONE_CDMA_HIGH_L)
                        tg?.startTone(android.media.ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 400)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }

                    try {
                        if (vibrator != null && vibrator.hasVibrator()) {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                vibrator.vibrate(android.os.VibrationEffect.createOneShot(300, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
                            } else {
                                @Suppress("DEPRECATION")
                                vibrator.vibrate(300)
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }

                    delay(800L) // Wait a bit before repeating
                }
            } finally {
                tg?.release()
            }
        }
    }

    fun playStopwatchBreakEndBellSound(context: Context) {
        stopAlarm()
        alarmJob = scope.launch {
            val endTime = System.currentTimeMillis() + 3000L // 3 seconds duration
            
            val isBT = isBluetoothAudioConnected(context)
            val volume = if (isBT) 35 else 100
            
            val tg = try {
                android.media.ToneGenerator(android.media.AudioManager.STREAM_ALARM, volume)
            } catch (e: Exception) {
                null
            }
            val vibrator = context.applicationContext.getSystemService(Context.VIBRATOR_SERVICE) as? android.os.Vibrator

            try {
                while (System.currentTimeMillis() < endTime) {
                    try {
                        tg?.startTone(android.media.ToneGenerator.TONE_CDMA_HIGH_L, 500)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }

                    try {
                        if (vibrator != null && vibrator.hasVibrator()) {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                vibrator.vibrate(android.os.VibrationEffect.createOneShot(400, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
                            } else {
                                @Suppress("DEPRECATION")
                                vibrator.vibrate(400)
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }

                    delay(1000L) // Repeat every 1 second for 3 seconds
                }
            } finally {
                tg?.release()
            }
        }
    }

    private fun isBluetoothAudioConnected(context: Context): Boolean {
        return try {
            val audioManager = context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                for (i in devices.indices) {
                    val device = devices[i]
                    val type = device.type
                    if (type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                        type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO) {
                        return true
                    }
                }
            }
            @Suppress("DEPRECATION")
            if (audioManager.isBluetoothA2dpOn || audioManager.isBluetoothScoOn) {
                return true
            }
            false
        } catch (e: Exception) {
            false
        }
    }

    fun openAppWithTimerPageInFront(context: Context) {
        try {
            val intent = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: Intent(context, com.example.MainActivity::class.java)
            intent.apply {
                action = Intent.ACTION_MAIN
                addCategory(Intent.CATEGORY_LAUNCHER)
                putExtra("SHOW_TIMER_PAGE", true)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun setSoundEnabled(context: Context, enabled: Boolean) {
        soundEnabled.value = enabled
        val prefs = context.applicationContext.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("timer_sound_enabled", enabled).apply()
    }

    fun setBellSilentModeEnabled(context: Context, enabled: Boolean) {
        init(context)
        isBellSilentModeEnabled.value = enabled
        val prefs = context.applicationContext.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("bell_silent_mode_enabled", enabled).apply()
    }

    fun setAutoStartBreak(context: Context, enabled: Boolean) {
        autoStartBreak.value = enabled
        val prefs = context.applicationContext.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("timer_autostart_break", enabled).apply()
    }

    fun setAutoStartPomo(context: Context, enabled: Boolean) {
        autoStartPomo.value = enabled
        val prefs = context.applicationContext.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("timer_autostart_pomo", enabled).apply()
    }

    fun setTimerDuration(context: Context, mins: Int) {
        init(context)
        timerDurationMinutes.value = mins
        if (!isTimerRunning.value) {
            timerSecondsLeft.value = mins * 60
        }
        val prefs = context.applicationContext.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        prefs.edit().putInt("timer_duration", mins).apply()
    }

    fun attachTaskToTimer(context: Context, task: Task?) {
        init(context)
        attachedTask.value = task
    }

    fun startTimer(context: Context, stopActiveAlarm: Boolean = true) {
        init(context)
        updateLocalInteractionTimestamp()
        if (stopActiveAlarm) {
            stopAlarm()
        }
        if (isTimerRunning.value) return
        isTimerRunning.value = true
        
        val appContext = context.applicationContext

        if (isFocusPhase.value && !wasStartedFromStopwatch.value) {
            // --- POMODORO FOCUS MODE (Timestamp Engine) ---
            recordSessionStart()
            addSystemLog(appContext, "Start Timer", "BUTTON_PRESS", "Duration=${timerDurationMinutes.value}m")
            
            KeepAliveService.start(appContext)

            lastResumeTimeMs.value = System.currentTimeMillis()
            saveActiveSessionState(appContext)
            syncStateToFirebase(appContext)

            timerJob = scope.launch {
                KeepAliveService.updateNotification(appContext)
                updateOverlayVisibility(appContext)

                val totalDurationMs = timerDurationMinutes.value * 60 * 1000L
                var lastRecordedMinutes = ((accumulatedSessionTimeMs.value / 1000) / 60).toInt()

                while (isTimerRunning.value && isFocusPhase.value) {
                    delay(200) // UI refresh rate
                    val currentChunkMs = lastResumeTimeMs.value?.let { System.currentTimeMillis() - it } ?: 0L
                    val totalElapsedMs = accumulatedSessionTimeMs.value + currentChunkMs
                    
                    val remainingMs = totalDurationMs - totalElapsedMs
                    timerSecondsLeft.value = maxOf(0, (remainingMs / 1000).toInt())
                    cumulativeSessionFocusSeconds.value = (totalElapsedMs / 1000).toInt()

                    val currentMinutes = ((totalElapsedMs / 1000) / 60).toInt()
                    val diffMinutes = currentMinutes - lastRecordedMinutes
                    if (diffMinutes > 0) {
                        lastRecordedMinutes = currentMinutes
                        attachedTask.value?.let { task ->
                            val updatedTask = task.copy(actualMinutes = task.actualMinutes + diffMinutes)
                            updateTaskInDatabase(appContext, updatedTask)
                            attachedTask.value = updatedTask
                        }
                    }
                    
                    updateOverlayTextAndState()
                    
                    if (remainingMs <= 0) break // Phase finished
                }

                if (timerSecondsLeft.value <= 0) {
                    handlePhaseCompletion(appContext, completedFocusPhase = true)
                }
            }
        } else {
            // --- BREAK MODE (Simple Countdown) ---
            addSystemLog(appContext, "Start Break", "BUTTON_PRESS", "Left=${timerSecondsLeft.value}s")
            KeepAliveService.start(appContext)
            
            saveActiveSessionState(appContext)
            syncStateToFirebase(appContext) // Will sync as 'break' because isFocusPhase is false

            timerJob = scope.launch {
                KeepAliveService.updateNotification(appContext)
                updateOverlayVisibility(appContext)

                while (isTimerRunning.value && !isFocusPhase.value && timerSecondsLeft.value > 0) {
                    delay(1000) // Simple 1-second tick for breaks
                    timerSecondsLeft.value -= 1
                    updateOverlayTextAndState()
                }

                if (timerSecondsLeft.value <= 0) {
                    handlePhaseCompletion(appContext, completedFocusPhase = false)
                }
            }
        }
    }

    private fun handlePhaseCompletion(context: Context, completedFocusPhase: Boolean) {
        val appContext = context.applicationContext
        isTimerRunning.value = false
        saveActiveSessionState(appContext)

        // Sound prompt alerting phase change (10s ring bell sound with vibration)
        if (soundEnabled.value) {
            playStrongBellSoundWithVibration(appContext)
        }

        if (completedFocusPhase && !wasStartedFromStopwatch.value) {
            val duration = timerDurationMinutes.value

            // Save focus records history item -> Instead of saving directly, we queue a pending review
            val formatter = java.text.SimpleDateFormat("hh:mm:ss a", java.util.Locale.getDefault())
            val startStr = formatter.format(java.util.Date(System.currentTimeMillis() - duration * 60 * 1000L))
            val endStr = formatter.format(java.util.Date())
            val taskName = attachedTask.value?.title ?: "Focus Session"
            val todayStr = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
            
            // Trigger immediate, robust data persistence before resetting the active session tracking values
            val elapsedSecs = if (cumulativeSessionFocusSeconds.value > 0) cumulativeSessionFocusSeconds.value else duration * 60
            persistFocusSession(appContext, elapsedSecs, isTimer = true)

            pendingFocusReview.value = FocusRecord(startStr, endStr, taskName, duration, todayStr, "", duration * 60)
            cumulativeSessionFocusSeconds.value = 0

            // Switch to Break Mode
            isFocusPhase.value = false
            val prefs = appContext.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            val bMins = prefs.getInt("break_duration", 5)
            timerSecondsLeft.value = bMins * 60

            saveActiveSessionState(appContext)
            KeepAliveService.updateNotification(appContext)
            updateOverlayVisibility(appContext)

            // Auto-start break depends on autoStartBreak preference
            if (autoStartBreak.value) {
                startTimer(appContext, stopActiveAlarm = false)
            }
        } else {
            // Break Finished!
            openAppWithTimerPageInFront(appContext)

            if (wasStartedFromStopwatch.value) {
                isFocusPhase.value = true
                wasStartedFromStopwatch.value = false
                isTabFocusTimerSelected.value = false
                val prefs = appContext.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                prefs.edit().putBoolean("was_started_from_stopwatch", false).apply()

                // Reset Timer back to pomo duration
                timerSecondsLeft.value = timerDurationMinutes.value * 60

                saveActiveSessionState(appContext)
                KeepAliveService.updateNotification(appContext)
                updateOverlayVisibility(appContext)

                // Play bell sound for 3 seconds after stopwatch break is over
                if (soundEnabled.value) {
                    playStopwatchBreakEndBellSound(appContext)
                }

                // Auto-start stopwatch if specified in settings
                if (autoStartStopwatchAfterBreak.value) {
                    startStopwatch(appContext, stopActiveAlarm = false)
                } else {
                    pauseStopwatch(appContext, stopActiveAlarm = false)
                }
            } else {
                // Normal Pomo Break End: Reset to Work Phase
                isFocusPhase.value = true
                timerSecondsLeft.value = timerDurationMinutes.value * 60

                saveActiveSessionState(appContext)
                KeepAliveService.updateNotification(appContext)
                updateOverlayVisibility(appContext)

                // Auto-start next focus session depends on autoStartPomo preference
                if (autoStartPomo.value) {
                    startTimer(appContext, stopActiveAlarm = false)
                }
            }
        }
    }

    fun pauseTimer(context: Context) {
        init(context)
        
        // ONLY bank time if we are actively focusing
        if (isFocusPhase.value && !wasStartedFromStopwatch.value) {
            val chunkMs = lastResumeTimeMs.value?.let { System.currentTimeMillis() - it } ?: 0L
            accumulatedSessionTimeMs.value += chunkMs
            cumulativeSessionFocusSeconds.value = (accumulatedSessionTimeMs.value / 1000).toInt()
        }
        lastResumeTimeMs.value = null // Wipes out active live-tracking

        updateLocalInteractionTimestamp()
        stopAlarm()
        timerJob?.cancel()
        isTimerRunning.value = false
        recordSessionPause()
        val appContext = context.applicationContext
        addSystemLog(appContext, "Pause Timer", "BUTTON_PRESS", "SecondsLeft=${timerSecondsLeft.value}s")
        saveActiveSessionState(appContext)
        KeepAliveService.updateNotification(appContext)
        updateOverlayVisibility(appContext)
        syncStateToFirebase(appContext)
    }

    fun persistFocusSession(context: Context, elapsedSecs: Int, isTimer: Boolean) {
        if (elapsedSecs <= 0) return
        
        recordSessionCompleteOrReset(true)
        if (verifiedSessionStartMs.value == null) {
            verifiedSessionStartMs.value = System.currentTimeMillis() - elapsedSecs * 1000L
        }
        
        val finalMinutes = elapsedSecs / 60
        val formatter = java.text.SimpleDateFormat("hh:mm:ss a", java.util.Locale.getDefault())
        val startStr = formatter.format(java.util.Date(System.currentTimeMillis() - elapsedSecs * 1000L))
        val endStr = formatter.format(java.util.Date())
        val taskName = attachedTask.value?.title ?: "Focus Session"
        val tagValue = attachedTag.value
        
        // 1. Save Focus Record locally
        addFocusRecord(context, startStr, endStr, taskName, finalMinutes, "", elapsedSecs, tagValue)

        // 2. Update Stats (Pomos count and total focus minutes)
        val prefs = context.applicationContext.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val focusTimerDurationMins = prefs.getInt("timer_duration", 25)
        if (isTimer && finalMinutes >= focusTimerDurationMins && focusTimerDurationMins > 0) {
            val currentPomos = todayPomosCount.value
            todayPomosCount.value = currentPomos + 1
            prefs.edit().putInt("today_pomos_count", currentPomos + 1).apply()
        }

        val currentMins = totalFocusMinutes.value
        totalFocusMinutes.value = currentMins + finalMinutes
        prefs.edit().putInt("total_focus_minutes", currentMins + finalMinutes).apply()

        // Trigger global verification dialog for background/immediate completion and auto-saves
        globalVerificationFocusedTimeSeconds.value = elapsedSecs
        globalVerificationRevisedTotalMinutes.value = getTodayFocusMinutes()
        globalVerificationRevisedTotalSeconds.value = getTodayFocusSeconds()
        showGlobalVerificationDialog.value = true

        // 3. Update task progress in database
        attachedTask.value?.let { task ->
            val updatedTask = task.copy(actualMinutes = task.actualMinutes + finalMinutes)
            updateTaskInDatabase(context, updatedTask)
            attachedTask.value = updatedTask
        }
        
        // 4. Remote Firebase Synchronization
        val currentUsername = prefs.getString("current_username", null)
        val isLoggedIn = prefs.getBoolean("is_logged_in", false)
        val isAdmin = prefs.getBoolean("is_admin", false)
        if (isLoggedIn && !isAdmin && currentUsername != null) {
            scope.launch(Dispatchers.IO) {
                try {
                    val response = com.example.api.FirebaseClient.api.getUsers()
                    if (response.isSuccessful) {
                        val users = response.body()
                        val baseUser = users?.get(currentUsername)
                        if (baseUser != null) {
                            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
                            val todayStr = sdf.format(java.util.Date())
                            val completedTodaySeconds = focusRecords.value.sumOf { r ->
                                getOverlapSecondsForDate(r, todayStr)
                            }
                            val todayRecords = focusRecords.value.filter { it.dateString == todayStr || it.dateString.isEmpty() }
                            
                            val updatedUser = baseUser.copy(
                                isFocusing = false,
                                accumulatedTimeMs = 0L,
                                lastResumeTimeMs = null,
                                todaysFocusRecords = todayRecords,
                                lastUpdatedTimestamp = System.currentTimeMillis(),
                                focusStatus = "idle"
                            )
                            com.example.api.FirebaseClient.api.putUser(currentUsername, updatedUser)
                            Log.d("FocusTimerManager", "Successfully synced end-event data persistence to Firebase.")
                        }
                    }
                } catch (e: Exception) {
                    Log.e("FocusTimerManager", "Failed to sync end-event data to Firebase", e)
                }
            }
        }

        // 5. Automatic Google Drive Backup
        if (GoogleDriveSyncManager.hasDrivePermission(context)) {
            scope.launch(Dispatchers.IO) {
                try {
                    GoogleDriveSyncManager.backupFocusData(context)
                    Log.d("FocusTimerManager", "Successfully auto-backed up focus records to Google Drive.")
                } catch (e: Exception) {
                    Log.e("FocusTimerManager", "Failed to auto-backup to Google Drive", e)
                }
            }
        }
    }

    fun resetTimer(context: Context, saveSession: Boolean = true) {
        init(context)
        updateLocalInteractionTimestamp()
        stopAlarm()
        timerJob?.cancel()
        isTimerRunning.value = false

        val elapsedSecs = cumulativeSessionFocusSeconds.value
        val appContext = context.applicationContext
        addSystemLog(appContext, "Reset Timer", "BUTTON_PRESS", "SaveSession=$saveSession, ElapsedSecs=${elapsedSecs}s")
        
        if (saveSession && elapsedSecs > 0 && isFocusPhase.value && !wasStartedFromStopwatch.value) {
            persistFocusSession(context, elapsedSecs, isTimer = true)
        }

        isFocusPhase.value = true
        cumulativeSessionFocusSeconds.value = 0
        accumulatedSessionTimeMs.value = 0L
        lastResumeTimeMs.value = null
        wasStartedFromStopwatch.value = false
        val prefs = context.applicationContext.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("was_started_from_stopwatch", false).apply()
        timerSecondsLeft.value = timerDurationMinutes.value * 60
        KeepAliveService.updateNotification(appContext)
        updateOverlayVisibility(appContext)
        syncStateToFirebase(appContext)
    }

    fun takeBreakFromStopwatch(context: Context) {
        init(context)
        updateLocalInteractionTimestamp()
        stopAlarm()
        pauseStopwatch(context)
        
        isFocusPhase.value = false
        wasStartedFromStopwatch.value = true
        val prefs = context.applicationContext.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("was_started_from_stopwatch", true).apply()
        
        timerSecondsLeft.value = stopwatchBreakDurationMinutes.value * 60
        
        KeepAliveService.updateNotification(context)
        startTimer(context)
    }

    fun takeBreakFromPomodoro(context: Context) {
        init(context)
        updateLocalInteractionTimestamp()
        stopAlarm()
        pauseTimer(context)
        
        isFocusPhase.value = false
        wasStartedFromStopwatch.value = false
        val prefs = context.applicationContext.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("was_started_from_stopwatch", false).apply()
        
        val bMins = prefs.getInt("break_duration", 5)
        timerSecondsLeft.value = bMins * 60
        
        KeepAliveService.updateNotification(context)
        startTimer(context)
    }

    fun skipOrEndBreak(context: Context) {
        init(context)
        updateLocalInteractionTimestamp()
        stopAlarm()
        timerJob?.cancel()
        isTimerRunning.value = false

        val appContext = context.applicationContext
        if (wasStartedFromStopwatch.value) {
            isFocusPhase.value = true
            wasStartedFromStopwatch.value = false
            val prefs = appContext.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            prefs.edit().putBoolean("was_started_from_stopwatch", false).apply()

            timerSecondsLeft.value = timerDurationMinutes.value * 60
            KeepAliveService.updateNotification(appContext)
            updateOverlayVisibility(appContext)

            if (autoStartStopwatchAfterBreak.value) {
                startStopwatch(appContext)
            } else {
                pauseStopwatch(appContext)
            }
        } else {
            isFocusPhase.value = true
            timerSecondsLeft.value = timerDurationMinutes.value * 60
            KeepAliveService.updateNotification(appContext)
            updateOverlayVisibility(appContext)

            if (autoStartPomo.value) {
                startTimer(appContext)
            }
        }
    }

    fun startStopwatch(context: Context, stopActiveAlarm: Boolean = true) {
        init(context)
        if (stopActiveAlarm) {
            stopAlarm()
        }
        val appContext = context.applicationContext
        // If we are currently in break mode, stop the break timer and go back to stopwatch mode
        if (!isFocusPhase.value) {
            timerJob?.cancel()
            isTimerRunning.value = false
            isFocusPhase.value = true
            wasStartedFromStopwatch.value = false
            isTabFocusTimerSelected.value = false
            val prefs = appContext.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            prefs.edit().putBoolean("was_started_from_stopwatch", false).apply()
            
            // Reset break timer seconds left back to pomo duration for clean state
            timerSecondsLeft.value = timerDurationMinutes.value * 60
        }

        if (isStopwatchActive.value) return
        updateLocalInteractionTimestamp()
        isStopwatchActive.value = true
        recordSessionStart()
        addSystemLog(appContext, "Start Stopwatch", "BUTTON_PRESS", "Seconds=${stopwatchSeconds.value}s")
        
        KeepAliveService.start(appContext)
        updateOverlayVisibility(appContext)

        lastResumeTimeMs.value = System.currentTimeMillis()
        saveActiveSessionState(appContext)
        syncStateToFirebase(appContext)

        stopwatchJob = scope.launch {
            KeepAliveService.updateNotification(appContext)
            while (isStopwatchActive.value) {
                delay(200) // UI refresh rate
                val currentChunkMs = lastResumeTimeMs.value?.let { System.currentTimeMillis() - it } ?: 0L
                val totalMs = accumulatedSessionTimeMs.value + currentChunkMs
                
                stopwatchSeconds.value = (totalMs / 1000).toInt()
                
                if (stopwatchSeconds.value >= 43200) {
                    pauseStopwatch(appContext, stopActiveAlarm = false)
                    if (soundEnabled.value) playStrongBellSoundWithVibration(appContext)
                    stopwatchLimitReached.value = true
                    break
                }
                updateOverlayTextAndState()
            }
        }
    }

    fun pauseStopwatch(context: Context, stopActiveAlarm: Boolean = true) {
        init(context)
        val chunkMs = lastResumeTimeMs.value?.let { System.currentTimeMillis() - it } ?: 0L
        accumulatedSessionTimeMs.value += chunkMs
        stopwatchSeconds.value = (accumulatedSessionTimeMs.value / 1000).toInt()
        lastResumeTimeMs.value = null // Wipes out active live-tracking

        updateLocalInteractionTimestamp()
        if (stopActiveAlarm) {
            stopAlarm()
        }
        stopwatchJob?.cancel()
        isStopwatchActive.value = false
        recordSessionPause()
        val appContext = context.applicationContext
        addSystemLog(appContext, "Pause Stopwatch", "BUTTON_PRESS", "Seconds=${stopwatchSeconds.value}s")
        saveActiveSessionState(appContext)
        KeepAliveService.updateNotification(appContext)
        updateOverlayVisibility(appContext)
        syncStateToFirebase(appContext)
    }

    fun resetStopwatch(context: Context, saveSession: Boolean = true) {
        init(context)
        updateLocalInteractionTimestamp()
        stopAlarm()
        stopwatchJob?.cancel()
        isStopwatchActive.value = false

        val elapsedSecs = stopwatchSeconds.value
        val appContext = context.applicationContext
        addSystemLog(appContext, "Reset Stopwatch", "BUTTON_PRESS", "SaveSession=$saveSession, Seconds=${elapsedSecs}s")
        
        if (saveSession && elapsedSecs > 0) {
            persistFocusSession(context, elapsedSecs, isTimer = false)
        }

        stopwatchSeconds.value = 0
        accumulatedSessionTimeMs.value = 0L
        lastResumeTimeMs.value = null

        // Reset phase and wasStartedFromStopwatch flags so they don't get stuck in break mode
        isFocusPhase.value = true
        wasStartedFromStopwatch.value = false
        val prefs = appContext.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("was_started_from_stopwatch", false).apply()
        timerSecondsLeft.value = timerDurationMinutes.value * 60

        saveActiveSessionState(appContext)
        KeepAliveService.updateNotification(appContext)
        updateOverlayVisibility(appContext)
        syncStateToFirebase(appContext)
    }

    fun setAppBackgroundedState(context: Context, backgrounded: Boolean) {
        init(context)
        appIsBackgrounded = backgrounded
        updateOverlayVisibility(context.applicationContext)
    }

    fun setTimerScreenActiveState(context: Context, active: Boolean) {
        init(context)
        isTimerScreenActive = active
        updateOverlayVisibility(context.applicationContext)
    }

    private fun updateOverlayVisibility(context: Context) {
        scope.launch(Dispatchers.Main) {
            val prefs = context.applicationContext.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            val showOverlayPref = prefs.getBoolean("show_overlay_on_exit", true)

            val isPromoSessionActive = !isFocusPhase.value || timerSecondsLeft.value < timerDurationMinutes.value * 60
            val isStopwatchSessionActive = stopwatchSeconds.value > 0
            val hasAnySession = isTimerRunning.value || isStopwatchActive.value || isPromoSessionActive || isStopwatchSessionActive

            val shouldShow = hasAnySession && (!isTimerScreenActive || appIsBackgrounded) && showOverlayPref
            if (shouldShow) {
                showOverlay(context)
            } else {
                hideOverlay()
            }
            com.example.widget.WidgetUpdater.updateAllWidgets(context)
        }
    }

    fun recreateOverlayIfExists(context: Context) {
        scope.launch(Dispatchers.Main) {
            if (overlayView != null) {
                hideOverlay()
                showOverlay(context)
            }
        }
    }

    private fun showOverlay(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(context)) {
            Log.d("FocusTimerManager", "Overlay permission not granted")
            return
        }

        if (overlayView != null) {
            updateOverlayTextAndState()
            return
        }

        try {
            val wm = context.applicationContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            windowManager = wm

                val sizePref = context.applicationContext.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                    .getString("floating_timer_size", "large") ?: "large"

                val textSizeVal: Float
                val padH: Float
                val padV: Float
                val fixedWidthDp: Float
                when (sizePref) {
                    "small" -> {
                        textSizeVal = 14f
                        padH = 10f
                        padV = 6f
                        fixedWidthDp = 110f
                    }
                    "medium" -> {
                        textSizeVal = 19f
                        padH = 16f
                        padV = 10f
                        fixedWidthDp = 150f
                    }
                    else -> {
                        textSizeVal = 25f
                        padH = 22f
                        padV = 14f
                        fixedWidthDp = 190f
                    }
                }

                val wmLayoutParams = WindowManager.LayoutParams(
                    dpToPx(context, fixedWidthDp),
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                         WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                    } else {
                         @Suppress("DEPRECATION")
                         WindowManager.LayoutParams.TYPE_PHONE
                    },
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    PixelFormat.TRANSLUCENT
                ).apply {
                    gravity = Gravity.TOP or Gravity.START
                    x = 150
                    y = 150
                }

                val container = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER
                    clipToOutline = true
                    background = android.graphics.drawable.GradientDrawable().apply {
                        setColor(0xFF111111.toInt())
                        cornerRadius = dpToPx(context, 12f).toFloat()
                    }
                }

                val textView = TextView(context).apply {
                    text = formatTime(if (isStopwatchActive.value) stopwatchSeconds.value else timerSecondsLeft.value)
                    setTextColor(android.graphics.Color.WHITE)
                    textSize = textSizeVal
                    gravity = Gravity.CENTER
                    typeface = android.graphics.Typeface.MONOSPACE
                    setPadding(dpToPx(context, padH), dpToPx(context, padV), dpToPx(context, padH), dpToPx(context, padV))
                    this.layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                }
                tvTimerText = textView
                container.addView(textView)

                // Handle collapsed arrow layout
                val arrowText = TextView(context).apply {
                    text = "❯"
                    setTextColor(android.graphics.Color.WHITE)
                    textSize = 18f
                    gravity = Gravity.CENTER
                    visibility = View.GONE
                    typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.BOLD)
                }
                tvCollapsedArrow = arrowText
                container.addView(arrowText)

                val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
                    override fun onDoubleTap(e: MotionEvent): Boolean {
                        val intent = Intent(context, MainActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                            putExtra("SHOW_TIMER_PAGE", true)
                        }
                        context.startActivity(intent)
                        return true
                    }

                    override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                        if (isOverlayCollapsed) {
                            expandOverlay(context)
                            return true
                        }
                        return false
                    }
                })

                var initialX = 0
                var initialY = 0
                var initialTouchX = 0f
                var initialTouchY = 0f

                container.setOnTouchListener { _, event ->
                    if (gestureDetector.onTouchEvent(event)) {
                        return@setOnTouchListener true
                    }
                    when (event.action) {
                        MotionEvent.ACTION_DOWN -> {
                            initialX = wmLayoutParams.x
                            initialY = wmLayoutParams.y
                            initialTouchX = event.rawX
                            initialTouchY = event.rawY
                            true
                        }
                        MotionEvent.ACTION_MOVE -> {
                            wmLayoutParams.x = initialX + (event.rawX - initialTouchX).toInt()
                            wmLayoutParams.y = initialY + (event.rawY - initialTouchY).toInt()
                            try {
                                wm.updateViewLayout(container, wmLayoutParams)
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                            true
                        }
                        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                            // Dock to edge collapse checks
                            val displayMetrics = android.util.DisplayMetrics()
                            @Suppress("DEPRECATION")
                            wm.defaultDisplay.getMetrics(displayMetrics)
                            val screenWidth = displayMetrics.widthPixels
                            val containerWidth = container.width

                            val triggerThreshold = 40 // pixels from edge

                            if (wmLayoutParams.x <= triggerThreshold) {
                                isOverlayCollapsed = true
                                overlayCollapsedSide = "left"
                                wmLayoutParams.x = 0
                                updateCollapsedStateViews(context)
                            } else if (wmLayoutParams.x >= screenWidth - containerWidth - triggerThreshold) {
                                isOverlayCollapsed = true
                                overlayCollapsedSide = "right"
                                wmLayoutParams.x = screenWidth - dpToPx(context, 32f) // keep mini handle visible
                                updateCollapsedStateViews(context)
                            } else {
                                isOverlayCollapsed = false
                                overlayCollapsedSide = "none"
                                updateCollapsedStateViews(context)
                            }

                            try {
                                wm.updateViewLayout(container, wmLayoutParams)
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                            true
                        }
                        else -> true
                    }
                }

                wm.addView(container, wmLayoutParams)
                overlayView = container
                updateOverlayTextAndState()
                updateCollapsedStateViews(context)
            } catch (e: Exception) {
                e.printStackTrace()
            }
    }

    private fun expandOverlay(context: Context) {
        isOverlayCollapsed = false
        overlayCollapsedSide = "none"

        val wm = windowManager ?: return
        val container = overlayView as? LinearLayout ?: return

        val displayMetrics = android.util.DisplayMetrics()
        @Suppress("DEPRECATION")
        wm.defaultDisplay.getMetrics(displayMetrics)
        val screenWidth = displayMetrics.widthPixels

        val lp = container.layoutParams as? WindowManager.LayoutParams ?: return

        if (lp.x < screenWidth / 2) {
            lp.x = 40
        } else {
            lp.x = screenWidth - container.width - 40
        }

        updateCollapsedStateViews(context)

        try {
            wm.updateViewLayout(container, lp)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun updateCollapsedStateViews(context: Context) {
        val timerText = tvTimerText ?: return
        val arrowText = tvCollapsedArrow ?: return
        val container = overlayView as? LinearLayout ?: return
        val lp = container.layoutParams as? WindowManager.LayoutParams ?: return
        val wm = windowManager ?: return

        scope.launch(Dispatchers.Main) {
            if (isOverlayCollapsed) {
                timerText.visibility = View.GONE
                arrowText.visibility = View.VISIBLE
                if (overlayCollapsedSide == "left") {
                    arrowText.text = "❯"
                    arrowText.setPadding(dpToPx(context, 10f), dpToPx(context, 12f), dpToPx(context, 6f), dpToPx(context, 12f))
                } else {
                    arrowText.text = "❮"
                    arrowText.setPadding(dpToPx(context, 6f), dpToPx(context, 12f), dpToPx(context, 10f), dpToPx(context, 12f))
                }
                lp.width = dpToPx(context, 32f)
            } else {
                timerText.visibility = View.VISIBLE
                arrowText.visibility = View.GONE

                val sizePref = context.applicationContext.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                    .getString("floating_timer_size", "large") ?: "large"
                val fixedWidthDp = when (sizePref) {
                    "small" -> 110f
                    "medium" -> 150f
                    "large" -> 190f
                    else -> 190f
                }
                lp.width = dpToPx(context, fixedWidthDp)
            }
            try {
                wm.updateViewLayout(container, lp)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun hideOverlay() {
        try {
            overlayView?.let { view ->
                windowManager?.removeView(view)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            overlayView = null
            tvTimerText = null
            tvCollapsedArrow = null
            windowManager = null
        }
    }

    private fun updateOverlayTextAndState() {
        appContext?.let {
            com.example.widget.WidgetUpdater.updateStopwatchWidget(it)
            com.example.widget.WidgetUpdater.updatePomodoroWidget(it)
        }
        scope.launch(Dispatchers.Main) {
            val displaySeconds = if (!isFocusPhase.value) {
                timerSecondsLeft.value // Show break countdown
            } else if (isTimerRunning.value) {
                timerSecondsLeft.value // Show work countdown
            } else if (isStopwatchActive.value) {
                stopwatchSeconds.value // Show stopwatch active count up
            } else if (wasStartedFromStopwatch.value) {
                timerSecondsLeft.value // Break countdown
            } else {
                // Default to whichever tab is selected or has active seconds
                if (stopwatchSeconds.value > 0 && !isTabFocusTimerSelected.value) stopwatchSeconds.value else timerSecondsLeft.value
            }
            tvTimerText?.let { textView ->
                textView.text = formatTime(displaySeconds)
                val isBreakActive = !isFocusPhase.value
                val hasAnimation = textView.animation != null
                if (isBreakActive) {
                    if (!hasAnimation) {
                        val anim = android.view.animation.AlphaAnimation(1.0f, 0.15f).apply {
                            duration = 600
                            repeatMode = android.view.animation.Animation.REVERSE
                            repeatCount = android.view.animation.Animation.INFINITE
                        }
                        textView.startAnimation(anim)
                    }
                } else {
                    if (hasAnimation) {
                        textView.clearAnimation()
                    }
                }
            }
        }
    }

    private fun updateTaskInDatabase(context: Context, task: Task) {
        scope.launch(Dispatchers.IO) {
            try {
                val db = AppDatabase.getInstance(context)
                db.taskDao().updateTask(task)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun incrementTodayPomos(context: Context) {
        val next = todayPomosCount.value + 1
        todayPomosCount.value = next
        val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        prefs.edit().putInt("today_pomos_count", next).apply()
    }

    fun addFocusMinutes(context: Context, mins: Int) {
        val next = totalFocusMinutes.value + mins
        totalFocusMinutes.value = next
        val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        prefs.edit().putInt("total_focus_minutes", next).apply()
    }

    fun clearPendingFocusReview() {
        pendingFocusReview.value = null
    }

    fun addFocusRecord(context: Context, startTime: String, endTime: String, taskTitle: String, durationMinutes: Int, notes: String = "", durationSeconds: Int = durationMinutes * 60, tag: String = "", id: String = java.util.UUID.randomUUID().toString()): FocusRecord {
        val todayStr = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
        val cappedMinutes = if (durationMinutes > 720) 720 else durationMinutes
        val cappedSeconds = if (durationSeconds > 43200) 43200 else durationSeconds
        val record = FocusRecord(startTime, endTime, taskTitle, cappedMinutes, todayStr, notes, cappedSeconds, tag, id = id)
        
        var updatedList: List<FocusRecord> = emptyList()
        focusRecords.update { current ->
            val currentList = current.toMutableList()
            currentList.add(0, record)
            updatedList = currentList
            currentList
        }
        saveFocusRecords(context, updatedList)
        return record
    }

    fun updateFocusRecordById(context: Context, id: String, updatedRecord: FocusRecord) {
        var updatedList: List<FocusRecord>? = null
        focusRecords.update { current ->
            val currentList = current.toMutableList()
            val index = currentList.indexOfFirst { it.id == id }
            if (index != -1) {
                val cappedMinutes = if (updatedRecord.durationMinutes > 720) 720 else updatedRecord.durationMinutes
                val cappedSeconds = if (updatedRecord.durationSeconds > 43200) 43200 else updatedRecord.durationSeconds
                val record = updatedRecord.copy(durationMinutes = cappedMinutes, durationSeconds = cappedSeconds)
                currentList[index] = record
                updatedList = currentList
                currentList
            } else {
                current
            }
        }
        updatedList?.let {
            saveFocusRecords(context, it)
        }
    }

    fun updateFocusRecord(context: Context, index: Int, updatedRecord: FocusRecord) {
        var updatedList: List<FocusRecord>? = null
        focusRecords.update { current ->
            val currentList = current.toMutableList()
            if (index in currentList.indices) {
                val cappedMinutes = if (updatedRecord.durationMinutes > 720) 720 else updatedRecord.durationMinutes
                val cappedSeconds = if (updatedRecord.durationSeconds > 43200) 43200 else updatedRecord.durationSeconds
                val record = updatedRecord.copy(durationMinutes = cappedMinutes, durationSeconds = cappedSeconds)
                currentList[index] = record
                updatedList = currentList
                currentList
            } else {
                current
            }
        }
        updatedList?.let {
            saveFocusRecords(context, it)
        }
    }

    fun deleteFocusRecord(context: Context, index: Int) {
        var updatedList: List<FocusRecord>? = null
        focusRecords.update { current ->
            val currentList = current.toMutableList()
            if (index in currentList.indices) {
                currentList.removeAt(index)
                updatedList = currentList
                currentList
            } else {
                current
            }
        }
        updatedList?.let {
            saveFocusRecords(context, it)
        }
    }

    fun loadPeerFocusRecords(context: Context, username: String): List<FocusRecord> {
        val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val serialized = prefs.getString("peer_focus_records_$username", null) ?: return emptyList()
        if (serialized.isEmpty()) return emptyList()
        return try {
            serialized.split("\n").mapNotNull { line ->
                if (line.isBlank()) return@mapNotNull null
                val parts = line.split("|")
                if (parts.size >= 4) {
                    val dateValue = if (parts.size >= 5) parts[4] else ""
                    val notesValue = if (parts.size >= 6) {
                        try {
                            String(android.util.Base64.decode(parts[5], android.util.Base64.NO_WRAP))
                        } catch (e: Exception) { "" }
                    } else ""
                    val originalMins = parts[3].toInt()
                    val originalSecs = if (parts.size >= 7) parts[6].toIntOrNull() ?: (originalMins * 60) else (originalMins * 60)
                    val tagValue = if (parts.size >= 8) parts[7] else ""
                    val idValue = if (parts.size >= 9) parts[8] else java.util.UUID.randomUUID().toString()
                    FocusRecord(parts[0], parts[1], parts[2], originalMins, dateValue, notesValue, originalSecs, tagValue, idValue)
                } else null
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun savePeerFocusRecords(context: Context, username: String, list: List<FocusRecord>) {
        val serialized = list.joinToString("\n") { 
            val b64Notes = android.util.Base64.encodeToString(it.notes.toByteArray(), android.util.Base64.NO_WRAP)
            "${it.startTime}|${it.endTime}|${it.taskTitle}|${it.durationMinutes}|${it.dateString}|$b64Notes|${it.durationSeconds}|${it.tag}|${it.id}" 
        }
        val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("peer_focus_records_$username", serialized).apply()
    }

    fun saveFocusRecords(context: Context, list: List<FocusRecord>) {
        synchronized(recordLock) {
            val serialized = list.joinToString("\n") { 
                val b64Notes = android.util.Base64.encodeToString(it.notes.toByteArray(), android.util.Base64.NO_WRAP)
                "${it.startTime}|${it.endTime}|${it.taskTitle}|${it.durationMinutes}|${it.dateString}|$b64Notes|${it.durationSeconds}|${it.tag}|${it.id}" 
            }
            val prefs = context.applicationContext.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            prefs.edit().putString("focus_records_list", serialized).apply()
        }

        // Automatic Google Drive Backup
        if (GoogleDriveSyncManager.hasDrivePermission(context)) {
            scope.launch(Dispatchers.IO) {
                try {
                    GoogleDriveSyncManager.backupFocusData(context)
                    Log.d("FocusTimerManager", "Successfully auto-backed up focus records to Google Drive.")
                } catch (e: Exception) {
                    Log.e("FocusTimerManager", "Failed to auto-backup to Google Drive", e)
                }
            }
        }
    }

    fun loadFocusRecords(context: Context): List<FocusRecord> {
        val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val serialized = prefs.getString("focus_records_list", null) ?: return emptyList()
        if (serialized.isEmpty()) return emptyList()
        return try {
            var diffMins = 0
            val list = serialized.split("\n").mapNotNull { line ->
                if (line.isBlank()) return@mapNotNull null
                val parts = line.split("|")
                if (parts.size >= 4) {
                    val dateValue = if (parts.size >= 5) parts[4] else ""
                    val notesValue = if (parts.size >= 6) {
                        try {
                            String(android.util.Base64.decode(parts[5], android.util.Base64.NO_WRAP))
                        } catch (e: Exception) { "" }
                    } else ""
                    val originalMins = parts[3].toInt()
                    val originalSecs = if (parts.size >= 7) parts[6].toIntOrNull() ?: (originalMins * 60) else (originalMins * 60)
                    
                    if (originalMins > 720) {
                        diffMins += (originalMins - 720)
                    }
                    
                    // Cap at 12 hours (720 mins or 43200 seconds)
                    val durationMins = if (originalMins > 720) 720 else originalMins
                    val durationSecs = if (originalSecs > 43200) 43200 else originalSecs
                    val tagValue = if (parts.size >= 8) parts[7] else ""
                    val idValue = if (parts.size >= 9) parts[8] else java.util.UUID.randomUUID().toString()
                    
                    FocusRecord(parts[0], parts[1], parts[2], durationMins, dateValue, notesValue, durationSecs, tagValue, idValue)
                } else null
            }
            
            val hasChanged = list.any { it.durationMinutes > 720 || it.durationSeconds > 43200 } || diffMins > 0
            if (hasChanged) {
                val cleanedList = list.map {
                    if (it.durationMinutes > 720 || it.durationSeconds > 43200) {
                        it.copy(durationMinutes = 720, durationSeconds = 43200)
                    } else {
                        it
                    }
                }
                saveFocusRecords(context, cleanedList)
                if (diffMins > 0) {
                    val currentTotal = totalFocusMinutes.value
                    val newTotal = maxOf(0, currentTotal - diffMins)
                    totalFocusMinutes.value = newTotal
                    prefs.edit().putInt("total_focus_minutes", newTotal).apply()
                }
                cleanedList
            } else {
                list
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun formatTime(seconds: Int): String {
        val h = seconds / 3600
        val mins = (seconds % 3600) / 60
        val secs = seconds % 60
        return String.format(java.util.Locale.US, "%02d:%02d:%02d", h, mins, secs)
    }

    private fun dpToPx(context: Context, dp: Float): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp,
            context.resources.displayMetrics
        ).toInt()
    }

    fun getOverlapSecondsForDate(record: FocusRecord, targetDateStr: String): Int {
        try {
            val dateStr = if (record.dateString.isNotEmpty()) record.dateString else targetDateStr
            val parser = java.text.SimpleDateFormat("yyyy-MM-dd hh:mm:ss a", java.util.Locale.getDefault())
            val endDate = parser.parse("$dateStr ${record.endTime}") ?: return 0
            val endMs = endDate.time
            val startMs = endMs - (record.durationSeconds * 1000L)
            
            val dateParser = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
            val targetDate = dateParser.parse(targetDateStr) ?: return 0
            val calendar = java.util.Calendar.getInstance()
            calendar.time = targetDate
            calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
            calendar.set(java.util.Calendar.MINUTE, 0)
            calendar.set(java.util.Calendar.SECOND, 0)
            calendar.set(java.util.Calendar.MILLISECOND, 0)
            val targetStartMs = calendar.timeInMillis
            
            calendar.set(java.util.Calendar.HOUR_OF_DAY, 23)
            calendar.set(java.util.Calendar.MINUTE, 59)
            calendar.set(java.util.Calendar.SECOND, 59)
            calendar.set(java.util.Calendar.MILLISECOND, 999)
            val targetEndMs = calendar.timeInMillis
            
            val overlapStart = maxOf(startMs, targetStartMs)
            val overlapEnd = minOf(endMs, targetEndMs)
            
            return if (overlapEnd > overlapStart) {
                ((overlapEnd - overlapStart) / 1000).toInt()
            } else {
                0
            }
        } catch (e: Exception) {
            if (record.dateString == targetDateStr || record.dateString.isEmpty()) {
                return record.durationSeconds
            }
            return 0
        }
    }

    fun getActiveSessionOverlapSeconds(startMs: Long, targetDateStr: String): Int {
        try {
            val endMs = System.currentTimeMillis()
            val dateParser = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
            val targetDate = dateParser.parse(targetDateStr) ?: return 0
            val calendar = java.util.Calendar.getInstance()
            calendar.time = targetDate
            calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
            calendar.set(java.util.Calendar.MINUTE, 0)
            calendar.set(java.util.Calendar.SECOND, 0)
            calendar.set(java.util.Calendar.MILLISECOND, 0)
            val targetStartMs = calendar.timeInMillis
            
            calendar.set(java.util.Calendar.HOUR_OF_DAY, 23)
            calendar.set(java.util.Calendar.MINUTE, 59)
            calendar.set(java.util.Calendar.SECOND, 59)
            calendar.set(java.util.Calendar.MILLISECOND, 999)
            val targetEndMs = calendar.timeInMillis
            
            val overlapStart = maxOf(startMs, targetStartMs)
            val overlapEnd = minOf(endMs, targetEndMs)
            
            return if (overlapEnd > overlapStart) {
                ((overlapEnd - overlapStart) / 1000).toInt()
            } else {
                0
            }
        } catch (e: Exception) {
            return ((System.currentTimeMillis() - startMs) / 1000).toInt()
        }
    }

    fun getTodayFocusMinutes(): Int {
        val todayStr = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
        val completedTodaySeconds = focusRecords.value.sumOf { r ->
            getOverlapSecondsForDate(r, todayStr)
        }
        return (completedTodaySeconds + 30) / 60
    }

    fun getTodayFocusSeconds(): Int {
        val todayStr = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
        return focusRecords.value.sumOf { r ->
            getOverlapSecondsForDate(r, todayStr)
        }
    }

    fun loadFocusTags(context: Context): List<String> {
        val prefs = context.applicationContext.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val tagsString = prefs.getString("focus_tags_list", "")
        return if (tagsString.isNullOrBlank()) {
            listOf("Work", "Study", "Exercise", "Reading", "Relaxation", "Coding")
        } else {
            tagsString.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        }
    }

    fun saveFocusTags(context: Context, tags: List<String>) {
        val prefs = context.applicationContext.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("focus_tags_list", tags.joinToString(",")).apply()
        focusTags.value = tags
    }
}
