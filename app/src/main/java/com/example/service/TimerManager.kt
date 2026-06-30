package com.example.service

import kotlinx.coroutines.flow.MutableStateFlow

object TimerManager {
    // Basic settings & states
    val focusTimerDurationMins = MutableStateFlow(25)
    val breakDurationMins = MutableStateFlow(5)
    
    val isTimerActive = MutableStateFlow(false)
    val isInBreakMode = MutableStateFlow(false)
    val timerSecondsRemaining = MutableStateFlow(25 * 60)
    
    // Stopwatch
    val isStopwatchActive = MutableStateFlow(false)
    val stopwatchSeconds = MutableStateFlow(0)
    val isTabFocusTimerSelected = MutableStateFlow(true) // true: Focus, false: Stopwatch
    
    // Auto start
    val autoStartBreak = MutableStateFlow(false)
    val autoStartNextPomo = MutableStateFlow(false)
    
    // Custom sound selection
    val playSoundFocusEnd = MutableStateFlow(true)
    val playSoundBreakEnd = MutableStateFlow(true)
    val soundFocusEndSelection = MutableStateFlow("CDMA Pip")
    val soundBreakEndSelection = MutableStateFlow("CDMA Confirm")
    
    // Limits & Tracking
    var activeTimerTotalTickedSeconds = 0
    var activeStopwatchTotalTickedSeconds = 0
    
    // Selected task linked with timer
    val selectedTaskName = MutableStateFlow<String?>(null)
    val selectedTaskId = MutableStateFlow<Int?>(null)
    
    // Total stats
    val todayPomosCount = MutableStateFlow(0)
    val totalFocusMinutes = MutableStateFlow(0)
}
