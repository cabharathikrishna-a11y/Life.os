package com.example.ui.components

import android.content.Context
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FreeBreakfast
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.AppViewModel
import com.example.ui.theme.PremiumEffects.bouncyClick
import com.example.ui.theme.PremiumEffects.glassmorphicCard
import com.example.ui.theme.WaterBlue
import kotlinx.coroutines.delay

@Composable
fun TimerImmersiveContent(
    viewModel: AppViewModel,
    focusTimerDurationMins: Int,
    onShowFriendsDetails: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val isFocusPhase by viewModel.isFocusPhase.collectAsState()
    val isTimerActive by viewModel.isTimerRunning.collectAsState()
    val timerSecondsRemaining by viewModel.timerSecondsLeft.collectAsState()
    val stopwatchSeconds by viewModel.stopwatchSeconds.collectAsState()
    val isStopwatchActive by viewModel.isStopwatchActive.collectAsState()
    val isTabFocusTimerSelected by viewModel.isTabFocusTimerSelected.collectAsState()
    val wasStartedFromStopwatch by viewModel.wasStartedFromStopwatch.collectAsState()
    val cumulativeSessionFocusSeconds by viewModel.cumulativeSessionFocusSeconds.collectAsState()
    val selectedTask by viewModel.attachedTask.collectAsState()

    val motivationalQuoteEnabled by viewModel.focusMotivationalQuoteEnabled.collectAsState()
    val quoteIntervalMins by viewModel.focusMotivationalQuoteIntervalMins.collectAsState()
    val currentQuote by viewModel.currentQuote.collectAsState()

    var areControlsVisible by remember { mutableStateOf(true) }
    var isAntiBurnCenteredByTap by remember { mutableStateOf(false) }
    var interactionCounter by remember { mutableStateOf(0) }

    val minutesElapsedTotal = (System.currentTimeMillis() / 60000).toInt()
    val periodIndex = (minutesElapsedTotal / 5) % 4

    LaunchedEffect(viewModel, motivationalQuoteEnabled, quoteIntervalMins) {
        if (motivationalQuoteEnabled) {
            if (viewModel.currentQuote.value.isEmpty()) {
                viewModel.triggerNextMotivationalQuote()
            }
            while (true) {
                delay(quoteIntervalMins * 60 * 1000L)
                viewModel.triggerNextMotivationalQuote()
            }
        }
    }

    LaunchedEffect(periodIndex) {
        isAntiBurnCenteredByTap = false
    }

    LaunchedEffect(areControlsVisible, interactionCounter) {
        if (areControlsVisible) {
            delay(10000) // 10 seconds auto-hide
            areControlsVisible = false
        }
    }

    LaunchedEffect(isFocusPhase, isTimerActive, isStopwatchActive) {
        areControlsVisible = true
        interactionCounter++
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                areControlsVisible = !areControlsVisible
                isAntiBurnCenteredByTap = true
                interactionCounter++
            }
            .padding(24.dp)
    ) {
        // Upper block: Show the focusing people emoji bubble and potential quote below it
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth(0.85f)
                .padding(top = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            FriendsFocusPill(
                viewModel = viewModel,
                onClick = onShowFriendsDetails
            )

            if (motivationalQuoteEnabled && currentQuote.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Crossfade(
                    targetState = currentQuote,
                    animationSpec = androidx.compose.animation.core.tween(1500),
                    label = "quote_crossfade"
                ) { targetQuote ->
                    Text(
                        text = "\"$targetQuote\"",
                        color = Color(0xFFFFEB3B).copy(alpha = 0.85f),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                        textAlign = TextAlign.Center,
                        lineHeight = 20.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
            }
        }

        // Close button fixed at top right corner only
        if (areControlsVisible) {
            IconButton(
                onClick = { viewModel.setTimerImmersive(false) },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .testTag("exit_immersive_btn")
                    .padding(8.dp)
            ) {
                Icon(Icons.Default.Close, contentDescription = "Exit Immersive", tint = Color.White)
            }
        }

        // Exactly Centered Timer Display
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Task Name (visible only when controls are visible)
            if (areControlsVisible) {
                val displayName = selectedTask?.title ?: "GENERAL FOCUS SPHERE"
                Text(
                    text = displayName.uppercase(),
                    color = Color.LightGray,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            if (!isFocusPhase) {
                RenderDigitalDigits(
                    viewModel = viewModel,
                    seconds = timerSecondsRemaining,
                    isImmersive = true,
                    isAntiBurnCenteredByTap = isAntiBurnCenteredByTap,
                    isBlinking = true
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text("now u r in a break", color = Color(0xFF81C784), fontSize = 14.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
            } else if (isTabFocusTimerSelected) {
                RenderDigitalDigits(
                    viewModel = viewModel,
                    seconds = timerSecondsRemaining,
                    isImmersive = true,
                    isAntiBurnCenteredByTap = isAntiBurnCenteredByTap,
                    isBlinking = !isFocusPhase
                )
            } else {
                RenderDigitalDigits(
                    viewModel = viewModel,
                    seconds = stopwatchSeconds,
                    isImmersive = true,
                    isAntiBurnCenteredByTap = isAntiBurnCenteredByTap,
                    isBlinking = false
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Control buttons row (visible only when controls are visible)
            if (areControlsVisible) {
                if (isTabFocusTimerSelected) {
                    if (isFocusPhase) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Start a Break Button
                                Box(
                                    modifier = Modifier
                                        .width(130.dp)
                                        .height(44.dp)
                                        .bouncyClick {
                                            viewModel.pauseTimer()
                                            viewModel.takeBreakFromPomodoro()
                                        }
                                        .glassmorphicCard(
                                            shape = RoundedCornerShape(10.dp),
                                            borderWidth = 0.5.dp,
                                            borderColor = Color(0x5581C784),
                                            backgroundColor = Color(0x334CAF50)
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.FreeBreakfast, contentDescription = null, tint = Color(0xFF81C784), modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Start Break", color = Color(0xFF81C784), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }

                                // Configure Button (Task Linker)
                                Box(
                                    modifier = Modifier
                                        .width(130.dp)
                                        .height(44.dp)
                                        .bouncyClick {
                                            viewModel.setShowTaskSelectionDialog(true)
                                        }
                                        .glassmorphicCard(
                                            shape = RoundedCornerShape(10.dp),
                                            borderWidth = 0.5.dp,
                                            borderColor = Color(0x44FFFFFF),
                                            backgroundColor = Color(0x22FFFFFF)
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Settings, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Configure", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                    }
                                }
                            }

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (isTimerActive) {
                                    // Pause Button
                                    Box(
                                        modifier = Modifier
                                            .width(130.dp)
                                            .height(48.dp)
                                            .bouncyClick { viewModel.pauseTimer() }
                                            .glassmorphicCard(
                                                shape = RoundedCornerShape(12.dp),
                                                borderWidth = 0.5.dp,
                                                borderColor = Color(0x33FFFFFF),
                                                backgroundColor = Color(0x40222222)
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("Pause", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                    }
                                } else {
                                    // Resume Button
                                    Box(
                                        modifier = Modifier
                                            .width(130.dp)
                                            .height(48.dp)
                                            .bouncyClick { viewModel.startTimer() }
                                            .glassmorphicCard(
                                                shape = RoundedCornerShape(12.dp),
                                                borderWidth = 0.5.dp,
                                                borderColor = WaterBlue.copy(alpha = 0.5f),
                                                backgroundColor = WaterBlue.copy(alpha = 0.3f)
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("Resume", color = WaterBlue, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    }
                                }

                                // End Button
                                Box(
                                    modifier = Modifier
                                        .width(130.dp)
                                        .height(48.dp)
                                        .bouncyClick {
                                            viewModel.pauseTimer()
                                            viewModel.prepareAndShowEndSessionDialog("timer", cumulativeSessionFocusSeconds)
                                        }
                                        .glassmorphicCard(
                                            shape = RoundedCornerShape(12.dp),
                                            borderWidth = 0.5.dp,
                                            borderColor = Color(0x15F9325D),
                                            backgroundColor = Color(0x40C62828)
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("End", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }
                            }
                        }
                    } else {
                        // BREAK PHASE
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Pause/Resume Break Button
                                val isBreakActive = isTimerActive
                                Box(
                                    modifier = Modifier
                                        .width(130.dp)
                                        .height(44.dp)
                                        .bouncyClick {
                                            if (isBreakActive) {
                                                viewModel.pauseTimer()
                                            } else {
                                                viewModel.startTimer()
                                            }
                                        }
                                        .glassmorphicCard(
                                            shape = RoundedCornerShape(10.dp),
                                            borderWidth = 0.5.dp,
                                            borderColor = if (isBreakActive) Color(0x33FFFFFF) else WaterBlue.copy(alpha = 0.5f),
                                            backgroundColor = if (isBreakActive) Color(0x40222222) else WaterBlue.copy(alpha = 0.3f)
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = if (isBreakActive) "Pause Break" else "Resume Break",
                                        color = if (isBreakActive) Color.White else WaterBlue,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                }

                                // Start Pomo Button
                                Box(
                                    modifier = Modifier
                                        .width(130.dp)
                                        .height(44.dp)
                                        .bouncyClick {
                                            viewModel.pauseTimer()
                                            if (wasStartedFromStopwatch) {
                                                viewModel.switchToFocusPhaseFromStopwatch()
                                                viewModel.startStopwatch()
                                            } else {
                                                viewModel.resetWorkPhaseTimer(focusTimerDurationMins)
                                                viewModel.startTimer()
                                            }
                                            viewModel.setTimerImmersive(true)
                                        }
                                        .glassmorphicCard(
                                            shape = RoundedCornerShape(10.dp),
                                            borderWidth = 0.5.dp,
                                            borderColor = WaterBlue.copy(alpha = 0.6f),
                                            backgroundColor = WaterBlue.copy(alpha = 0.35f)
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = if (wasStartedFromStopwatch) "Start Stopw." else "Start Pomo",
                                        color = WaterBlue,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                }
                            }

                            // End Break Button
                            Box(
                                modifier = Modifier
                                    .width(272.dp)
                                    .height(44.dp)
                                    .bouncyClick {
                                        viewModel.skipOrEndBreak()
                                    }
                                    .glassmorphicCard(
                                        shape = RoundedCornerShape(10.dp),
                                        borderWidth = 0.5.dp,
                                        borderColor = Color(0x33C62828),
                                        backgroundColor = Color(0x22C62828)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("End Break", color = Color(0xFFEF5350), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    }
                } else {
                    // Stopwatch controls
                    if (isFocusPhase) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Take Break Button
                                Box(
                                    modifier = Modifier
                                        .width(130.dp)
                                        .height(44.dp)
                                        .bouncyClick {
                                            viewModel.pauseStopwatch()
                                            viewModel.takeBreakFromStopwatch()
                                        }
                                        .glassmorphicCard(
                                            shape = RoundedCornerShape(10.dp),
                                            borderWidth = 0.5.dp,
                                            borderColor = Color(0x5581C784),
                                            backgroundColor = Color(0x334CAF50)
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.FreeBreakfast, contentDescription = null, tint = Color(0xFF81C784), modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Take Break", color = Color(0xFF81C784), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }

                                // Configure Button (Task Linker)
                                Box(
                                    modifier = Modifier
                                        .width(130.dp)
                                        .height(44.dp)
                                        .bouncyClick {
                                            viewModel.setShowTaskSelectionDialog(true)
                                        }
                                        .glassmorphicCard(
                                            shape = RoundedCornerShape(10.dp),
                                            borderWidth = 0.5.dp,
                                            borderColor = Color(0x44FFFFFF),
                                            backgroundColor = Color(0x22FFFFFF)
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Settings, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Configure", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                    }
                                }
                            }

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (isStopwatchActive) {
                                    // Pause Button
                                    Box(
                                        modifier = Modifier
                                            .width(130.dp)
                                            .height(48.dp)
                                            .bouncyClick { viewModel.pauseStopwatch() }
                                            .glassmorphicCard(
                                                shape = RoundedCornerShape(12.dp),
                                                borderWidth = 0.5.dp,
                                                borderColor = Color(0x33FFFFFF),
                                                backgroundColor = Color(0x40222222)
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("Pause", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                    }
                                } else {
                                    // Resume Button
                                    Box(
                                        modifier = Modifier
                                            .width(130.dp)
                                            .height(48.dp)
                                            .bouncyClick { viewModel.startStopwatch() }
                                            .glassmorphicCard(
                                                shape = RoundedCornerShape(12.dp),
                                                borderWidth = 0.5.dp,
                                                borderColor = WaterBlue.copy(alpha = 0.5f),
                                                backgroundColor = WaterBlue.copy(alpha = 0.3f)
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("Resume", color = WaterBlue, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    }
                                }

                                // End Button
                                Box(
                                    modifier = Modifier
                                        .width(130.dp)
                                        .height(48.dp)
                                        .bouncyClick {
                                            viewModel.pauseStopwatch()
                                            viewModel.prepareAndShowEndSessionDialog("stopwatch", stopwatchSeconds)
                                        }
                                        .glassmorphicCard(
                                            shape = RoundedCornerShape(12.dp),
                                            borderWidth = 0.5.dp,
                                            borderColor = Color(0x15F9325D),
                                            backgroundColor = Color(0x40C62828)
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("End", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }
                            }
                        }
                    } else {
                        // BREAK PHASE (from Stopwatch context)
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Pause/Resume Break Button
                                val isBreakActive = isTimerActive
                                Box(
                                    modifier = Modifier
                                        .width(130.dp)
                                        .height(44.dp)
                                        .bouncyClick {
                                            if (isBreakActive) {
                                                viewModel.pauseTimer()
                                            } else {
                                                viewModel.startTimer()
                                            }
                                        }
                                        .glassmorphicCard(
                                            shape = RoundedCornerShape(10.dp),
                                            borderWidth = 0.5.dp,
                                            borderColor = if (isBreakActive) Color(0x33FFFFFF) else WaterBlue.copy(alpha = 0.5f),
                                            backgroundColor = if (isBreakActive) Color(0x40222222) else WaterBlue.copy(alpha = 0.3f)
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = if (isBreakActive) "Pause Break" else "Resume Break",
                                        color = if (isBreakActive) Color.White else WaterBlue,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                }

                                // Start Stopwatch Button
                                Box(
                                    modifier = Modifier
                                        .width(130.dp)
                                        .height(44.dp)
                                        .bouncyClick {
                                            viewModel.pauseTimer()
                                            viewModel.switchToFocusPhase()
                                            viewModel.startStopwatch()
                                        }
                                        .glassmorphicCard(
                                            shape = RoundedCornerShape(10.dp),
                                            borderWidth = 0.5.dp,
                                            borderColor = WaterBlue.copy(alpha = 0.6f),
                                            backgroundColor = WaterBlue.copy(alpha = 0.35f)
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("Start Stopw.", color = WaterBlue, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            }

                            // End Break Button
                            Box(
                                modifier = Modifier
                                    .width(272.dp)
                                    .height(44.dp)
                                    .bouncyClick {
                                        viewModel.pauseTimer()
                                        viewModel.prepareAndShowEndSessionDialog("stopwatch", stopwatchSeconds)
                                    }
                                    .glassmorphicCard(
                                        shape = RoundedCornerShape(10.dp),
                                        borderWidth = 0.5.dp,
                                        borderColor = Color(0x33C62828),
                                        backgroundColor = Color(0x22C62828)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("End Break", color = Color(0xFFEF5350), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}
