package com.example.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.Task
import com.example.ui.AppViewModel
import com.example.ui.theme.Charcoal
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.WaterBlue
import coil.compose.AsyncImage
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

enum class CalendarViewMode {
    YEAR, MONTH, WEEK, DAY
}

fun getTaskHourPrefix(description: String): String? {
    val regex = Regex("""\[Time: (\d{1,2}):(\d{2})\]""")
    val match = regex.find(description)
    if (match != null) {
        val hr = match.groupValues[1]
        val hourInt = hr.toIntOrNull() ?: return null
        return String.format(Locale.US, "%02d:00", hourInt)
    }
    
    val stringRegex = Regex("""\[Time: ([^\]]+)\]""")
    val stringMatch = stringRegex.find(description)
    if (stringMatch != null) {
        val timeStr = stringMatch.groupValues[1].trim()
        val hourMatch = Regex("""^(\d{1,2})""").find(timeStr)
        if (hourMatch != null) {
            val hourInt = hourMatch.groupValues[1].toIntOrNull()
            if (hourInt != null) {
                var finalHour = hourInt
                if (timeStr.uppercase(Locale.US).contains("PM") && finalHour < 12) {
                    finalHour += 12
                } else if (timeStr.uppercase(Locale.US).contains("AM") && finalHour == 12) {
                    finalHour = 0
                }
                return String.format(Locale.US, "%02d:00", finalHour)
            }
        }
    }
    return null
}

fun parseTaskTime(description: String): Pair<Int, Int>? {
    val amPmRegex = Regex("""\[Time:\s*(\d{1,2}):(\d{2})\s*(AM|PM)\]""", RegexOption.IGNORE_CASE)
    val amPmMatch = amPmRegex.find(description)
    if (amPmMatch != null) {
        var hour = amPmMatch.groupValues[1].toIntOrNull() ?: 0
        val minute = amPmMatch.groupValues[2].toIntOrNull() ?: 0
        val ampm = amPmMatch.groupValues[3].uppercase(Locale.US)
        if (ampm == "PM" && hour < 12) {
            hour += 12
        } else if (ampm == "AM" && hour == 12) {
            hour = 0
        }
        return Pair(hour, minute)
    }

    val stdRegex = Regex("""\[Time:\s*(\d{1,2}):(\d{2})\]""")
    val stdMatch = stdRegex.find(description)
    if (stdMatch != null) {
        val hour = stdMatch.groupValues[1].toIntOrNull() ?: 0
        val minute = stdMatch.groupValues[2].toIntOrNull() ?: 0
        return Pair(hour, minute)
    }

    val stringRegex = Regex("""\[Time:\s*([^\]]+)\]""")
    val stringMatch = stringRegex.find(description)
    if (stringMatch != null) {
        val timeStr = stringMatch.groupValues[1].trim()
        val match = Regex("""^(\d{1,2}):(\d{2})\s*(AM|PM)?""", RegexOption.IGNORE_CASE).find(timeStr)
        if (match != null) {
            var hour = match.groupValues[1].toIntOrNull() ?: 0
            val minute = match.groupValues[2].toIntOrNull() ?: 0
            val ampm = match.groupValues[3].uppercase(Locale.US)
            if (ampm == "PM" && hour < 12) {
                hour += 12
            } else if (ampm == "AM" && hour == 12) {
                hour = 0
            }
            return Pair(hour, minute)
        }
    }
    return null
}

fun parseTaskDuration(description: String): Int {
    val regex = Regex("""\[Duration:\s*([^\]]+)\]""")
    val match = regex.find(description)
    if (match != null) {
        val durationStr = match.groupValues[1].trim()
        val digits = durationStr.filter { it.isDigit() }
        val durationInt = digits.toIntOrNull()
        if (durationInt != null && durationInt > 0) {
            return durationInt
        }
    }
    return 15
}

@Composable
fun CalendarView(viewModel: AppViewModel, modifier: Modifier = Modifier) {
    val tasks by viewModel.tasks.collectAsState()
    val journalEntries by viewModel.journalEntries.collectAsState()

    val calendarViewModeStr by viewModel.calendarViewModeStr.collectAsState()
    val currentViewMode = when (calendarViewModeStr) {
        "Year" -> CalendarViewMode.YEAR
        "Week" -> CalendarViewMode.WEEK
        "Day" -> CalendarViewMode.DAY
        else -> CalendarViewMode.MONTH
    }
    
    var selectedMonthCalendar by remember {
        mutableStateOf(Calendar.getInstance())
    }

    val currentYear = selectedMonthCalendar.get(Calendar.YEAR)
    val currentMonth = selectedMonthCalendar.get(Calendar.MONTH) // 0-indexed

    // Generate dynamic header title based on view mode
    val headerText = remember(selectedMonthCalendar, currentViewMode) {
        when (currentViewMode) {
            CalendarViewMode.YEAR -> {
                val sdf = SimpleDateFormat("yyyy", Locale.getDefault())
                sdf.format(selectedMonthCalendar.time)
            }
            CalendarViewMode.MONTH -> {
                val sdf = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
                sdf.format(selectedMonthCalendar.time)
            }
            CalendarViewMode.WEEK -> {
                val startCal = Calendar.getInstance().apply {
                    time = selectedMonthCalendar.time
                    set(Calendar.DAY_OF_WEEK, Calendar.SUNDAY)
                }
                val endCal = Calendar.getInstance().apply {
                    time = startCal.time
                    add(Calendar.DAY_OF_MONTH, 6)
                }
                val sdfStr = SimpleDateFormat("MMM d", Locale.getDefault())
                val sdfYear = SimpleDateFormat("yyyy", Locale.getDefault())
                "${sdfStr.format(startCal.time)} - ${sdfStr.format(endCal.time)}, ${sdfYear.format(endCal.time)}"
            }
            CalendarViewMode.DAY -> {
                val sdf = SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.getDefault())
                sdf.format(selectedMonthCalendar.time)
            }
        }
    }

    // Helper function for navigating date forward/backward
    fun navigate(delta: Int) {
        val newCal = Calendar.getInstance().apply {
            time = selectedMonthCalendar.time
            when (currentViewMode) {
                CalendarViewMode.YEAR -> add(Calendar.YEAR, delta)
                CalendarViewMode.MONTH -> add(Calendar.MONTH, delta)
                CalendarViewMode.WEEK -> add(Calendar.WEEK_OF_YEAR, delta)
                CalendarViewMode.DAY -> add(Calendar.DAY_OF_YEAR, delta)
            }
        }
        selectedMonthCalendar = newCal
    }

    // Generate month view grid days (42 cells starting from padded previous month)
    val gridDates = remember(currentYear, currentMonth) {
        val cal = Calendar.getInstance().apply {
            set(Calendar.YEAR, currentYear)
            set(Calendar.MONTH, currentMonth)
            set(Calendar.DAY_OF_MONTH, 1)
        }
        val firstDayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
        // Offset for Sunday start: Sunday is 1, so offset = firstDayOfWeek - 1
        val prevMonthPadding = firstDayOfWeek - 1
        
        val gridCal = Calendar.getInstance().apply {
            set(Calendar.YEAR, currentYear)
            set(Calendar.MONTH, currentMonth)
            set(Calendar.DAY_OF_MONTH, 1)
            add(Calendar.DAY_OF_MONTH, -prevMonthPadding)
        }
        
        List(42) {
            val d = gridCal.time
            gridCal.add(Calendar.DAY_OF_MONTH, 1)
            d
        }
    }

    // Generate week view days (7 days Sunday - Saturday)
    val weekDates = remember(selectedMonthCalendar) {
        val cal = Calendar.getInstance().apply {
            time = selectedMonthCalendar.time
            set(Calendar.DAY_OF_WEEK, Calendar.SUNDAY)
        }
        List(7) {
            val d = cal.time
            cal.add(Calendar.DAY_OF_MONTH, 1)
            d
        }
    }

    // Modal dialog state for viewing and managing tasks of a specific clicked day
    var selectedDayForDetail by remember { mutableStateOf<Date?>(null) }

    Column(modifier = modifier.fillMaxSize().padding(start = 12.dp, end = 12.dp, top = 2.dp, bottom = 12.dp)) {
        
        // Navigation Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = headerText,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.weight(1f, fill = false),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            // VIEW DROPDOWN BETWEEN MONTH YEAR AND TODAY
            var expanded by remember { mutableStateOf(false) }
            Box(
                modifier = Modifier.wrapContentSize(Alignment.TopStart)
            ) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF151515))
                        .border(1.dp, Color(0xFF333333), RoundedCornerShape(8.dp))
                        .clickable { expanded = true }
                        .padding(horizontal = 8.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text(
                        text = calendarViewModeStr,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = WaterBlue
                    )
                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = "Select View Mode",
                        tint = WaterBlue,
                        modifier = Modifier.size(14.dp)
                    )
                }

                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                    modifier = Modifier.background(Color(0xFF222225))
                ) {
                    listOf("Year", "Month", "Week", "Day").forEach { mode ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = mode,
                                    color = if (calendarViewModeStr == mode) WaterBlue else Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                            },
                            onClick = {
                                viewModel.setCalendarViewModeStr(mode)
                                expanded = false
                            }
                        )
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = { navigate(-1) },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowLeft,
                        contentDescription = "Previous",
                        tint = WaterBlue,
                        modifier = Modifier.size(20.dp)
                    )
                }
                
                Spacer(modifier = Modifier.width(2.dp))

                Button(
                    onClick = { selectedMonthCalendar = Calendar.getInstance() },
                    colors = ButtonDefaults.buttonColors(containerColor = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.05f)),
                    shape = RoundedCornerShape(6.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.height(28.dp)
                ) {
                    Text("Today", color = WaterBlue, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                }

                Spacer(modifier = Modifier.width(2.dp))

                IconButton(
                    onClick = { navigate(1) },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowRight,
                        contentDescription = "Next",
                        tint = WaterBlue,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        // Google Calendar Live Sync Control Card
        val context = LocalContext.current
        val syncStatus by viewModel.calendarSyncStatus.collectAsState()

        val permissionLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            val readGranted = permissions[android.Manifest.permission.READ_CALENDAR] ?: false
            val writeGranted = permissions[android.Manifest.permission.WRITE_CALENDAR] ?: false
            if (readGranted && writeGranted) {
                viewModel.syncGoogleCalendar(context)
            } else {
                android.widget.Toast.makeText(context, "Calendar permissions are required to sync.", android.widget.Toast.LENGTH_LONG).show()
            }
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 10.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1B1B1E)),
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(1.dp, Color(0xFF333336))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Sync Icon",
                        tint = WaterBlue,
                        modifier = Modifier.size(18.dp)
                    )
                    Column {
                        Text(
                            text = "Google Calendar Sync",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                        Text(
                            text = syncStatus,
                            color = if (syncStatus.startsWith("Sync Complete")) Color(0xFF81C784) else Color.LightGray,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Button(
                    onClick = {
                        val hasRead = androidx.core.content.ContextCompat.checkSelfPermission(
                            context, android.Manifest.permission.READ_CALENDAR
                        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                        val hasWrite = androidx.core.content.ContextCompat.checkSelfPermission(
                            context, android.Manifest.permission.WRITE_CALENDAR
                        ) == android.content.pm.PackageManager.PERMISSION_GRANTED

                        if (hasRead && hasWrite) {
                            viewModel.syncGoogleCalendar(context)
                        } else {
                            permissionLauncher.launch(
                                arrayOf(
                                    android.Manifest.permission.READ_CALENDAR,
                                    android.Manifest.permission.WRITE_CALENDAR
                                )
                            )
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = WaterBlue),
                    shape = RoundedCornerShape(6.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    modifier = Modifier.height(30.dp)
                ) {
                    Text("Sync Now", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                }
            }
        }

        // Main Calendar content container card
        Card(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.05f)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Box(modifier = Modifier.fillMaxSize().padding(12.dp)) {
                when (currentViewMode) {
                    CalendarViewMode.YEAR -> {
                        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                            val isCompact = maxWidth < 480.dp
                            val isLarge = maxWidth >= 840.dp
                            val columnsCount = when {
                                isCompact -> 1
                                isLarge -> 4
                                else -> 3
                            }
                            val rowsCount = (12 + columnsCount - 1) / columnsCount

                            var totalDragX by remember { mutableStateOf(0f) }
                            val scrollState = rememberScrollState()
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(scrollState)
                                    .pointerInput(currentYear) {
                                        detectHorizontalDragGestures(
                                            onDragStart = { totalDragX = 0f },
                                            onDragEnd = {
                                                if (totalDragX > 150) {
                                                    navigate(-1)
                                                } else if (totalDragX < -150) {
                                                    navigate(1)
                                                }
                                            },
                                            onDragCancel = { totalDragX = 0f },
                                            onHorizontalDrag = { change, dragAmount ->
                                                change.consume()
                                                totalDragX += dragAmount
                                            }
                                        )
                                    },
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                for (rowIdx in 0 until rowsCount) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        for (colIdx in 0 until columnsCount) {
                                            val monthIndex = rowIdx * columnsCount + colIdx
                                            if (monthIndex < 12) {
                                                Box(modifier = Modifier.weight(1f)) {
                                                    MiniMonthGrid(
                                                        year = currentYear,
                                                        month = monthIndex,
                                                        tasks = tasks,
                                                        onMonthClick = {
                                                            selectedMonthCalendar = Calendar.getInstance().apply {
                                                                set(Calendar.YEAR, currentYear)
                                                                set(Calendar.MONTH, monthIndex)
                                                                set(Calendar.DAY_OF_MONTH, 1)
                                                            }
                                                            viewModel.setCalendarViewModeStr("Month")
                                                        },
                                                        onDayClick = { day ->
                                                            selectedMonthCalendar = Calendar.getInstance().apply {
                                                                set(Calendar.YEAR, currentYear)
                                                                set(Calendar.MONTH, monthIndex)
                                                                set(Calendar.DAY_OF_MONTH, day)
                                                            }
                                                            viewModel.setCalendarViewModeStr("Day")
                                                        }
                                                    )
                                                }
                                            } else {
                                                Spacer(modifier = Modifier.weight(1f))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    
                    // MONTH VIEW
                    CalendarViewMode.MONTH -> {
                        Column(modifier = Modifier.fillMaxSize()) {
                            // Days of Week Header Indicators
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat").forEach { wd ->
                                    Text(
                                        text = wd,
                                        color = Color.Gray,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }

                            // Grid Weeks (6 rows)
                            val chunkedWeeks = gridDates.chunked(7)
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                chunkedWeeks.forEach { week ->
                                    Row(
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        week.forEach { date ->
                                            val cellCal = Calendar.getInstance().apply { time = date }
                                            val cellYear = cellCal.get(Calendar.YEAR)
                                            val cellMonth = cellCal.get(Calendar.MONTH)
                                            val cellDay = cellCal.get(Calendar.DAY_OF_MONTH)

                                            val isInCurrentMonth = cellMonth == currentMonth && cellYear == currentYear
                                            
                                            val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(date)
                                            val dayTasks = tasks.filter { it.dueDateString == dateStr }

                                            val matchingJournalEntry = journalEntries.find { it.dateString == dateStr }
                                            val photoUrl = matchingJournalEntry?.attachmentsJson
                                                ?.split("|")
                                                ?.find { it.trim().startsWith("photo:") }
                                                ?.replace("photo:", "")
                                                ?.trim()
                                            
                                            val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
                                            val isToday = dateStr == todayStr

                                            val cellBg = when {
                                                isToday -> WaterBlue.copy(alpha = 0.15f)
                                                !isInCurrentMonth -> Color(0xFF141416)
                                                else -> SurfaceCard
                                            }
                                            val borderClr = if (isToday) WaterBlue else Color.Transparent

                                            Box(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .fillMaxHeight()
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .border(1.dp, borderClr, RoundedCornerShape(6.dp))
                                                    .background(cellBg)
                                                    .clickable {
                                                        val targetCal = Calendar.getInstance().apply { time = date }
                                                        selectedMonthCalendar = targetCal
                                                        viewModel.setCalendarViewModeStr("Day")
                                                    }
                                                    .padding(4.dp)
                                            ) {
                                                if (!photoUrl.isNullOrEmpty()) {
                                                    AsyncImage(
                                                        model = photoUrl,
                                                        contentDescription = "Journal Day photo background",
                                                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                                        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(6.dp))
                                                    )
                                                    Box(
                                                        modifier = Modifier
                                                            .fillMaxSize()
                                                            .clip(RoundedCornerShape(6.dp))
                                                            .background(Color.Black.copy(alpha = 0.5f))
                                                    )
                                                }
                                                Column(modifier = Modifier.fillMaxSize()) {
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Text(
                                                            text = cellDay.toString(),
                                                            color = if (isInCurrentMonth) Color.White else Color.DarkGray,
                                                            fontSize = 11.sp,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                        if (dayTasks.isNotEmpty()) {
                                                            Box(
                                                                modifier = Modifier
                                                                    .size(6.dp)
                                                                    .clip(CircleShape)
                                                                    .background(WaterBlue)
                                                            )
                                                        }
                                                    }

                                                    Spacer(modifier = Modifier.height(4.dp))
                                                    Column(
                                                        modifier = Modifier.weight(1f),
                                                        verticalArrangement = Arrangement.spacedBy(2.dp)
                                                    ) {
                                                        val displayedTasks = dayTasks.take(2)
                                                        displayedTasks.forEach { task ->
                                                            val priorityColor = when (task.priority.uppercase()) {
                                                                "HIGH" -> Color(0xFFF44336)
                                                                "LOW" -> Color(0xFF4CAF50)
                                                                else -> WaterBlue
                                                            }
                                                            Box(
                                                                modifier = Modifier
                                                                    .fillMaxWidth()
                                                                    .clip(RoundedCornerShape(3.dp))
                                                                    .background(priorityColor.copy(alpha = 0.1f))
                                                                    .padding(horizontal = 3.dp, vertical = 2.dp)
                                                            ) {
                                                                Text(
                                                                    text = task.title,
                                                                    color = if (task.isCompleted) Color.Gray else Color.White,
                                                                    fontSize = 8.sp,
                                                                    maxLines = 1,
                                                                    overflow = TextOverflow.Ellipsis,
                                                                    style = MaterialTheme.typography.bodySmall.copy(
                                                                        textDecoration = if (task.isCompleted) TextDecoration.LineThrough else null
                                                                    )
                                                                )
                                                            }
                                                        }
                                                        if (dayTasks.size > 2) {
                                                            Text(
                                                                text = "+${dayTasks.size - 2} more",
                                                                color = Color.Gray,
                                                                fontSize = 8.sp,
                                                                fontWeight = FontWeight.Bold,
                                                                modifier = Modifier.padding(horizontal = 2.dp)
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // WEEK VIEW - Scrolling column of days with clean visual schedules
                    CalendarViewMode.WEEK -> {
                        val hourHeight = 60.dp
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color(0xFF0C0C0D))
                        ) {
                            // Header Row containing Sunday to Saturday days
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFF0C0C0D))
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Spacer with GMT indicator
                                Box(
                                    modifier = Modifier.width(70.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "GMT+05:30",
                                        color = Color.Gray,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }

                                // 7 column headers
                                Row(
                                    modifier = Modifier.weight(1f),
                                    horizontalArrangement = Arrangement.SpaceEvenly
                                ) {
                                    weekDates.forEach { date ->
                                        val isToday = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(date) == SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
                                        val dayName = SimpleDateFormat("EEE", Locale.US).format(date).uppercase()
                                        val dayNum = SimpleDateFormat("d", Locale.US).format(date)

                                        Column(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clickable { selectedDayForDetail = date },
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Text(
                                                text = dayName,
                                                color = if (isToday) WaterBlue else Color.Gray,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                softWrap = false
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Box(
                                                modifier = Modifier
                                                    .size(32.dp)
                                                    .clip(CircleShape)
                                                    .background(if (isToday) WaterBlue else Color.Transparent),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = dayNum,
                                                    color = if (isToday) Color.Black else Color.White,
                                                    fontSize = 14.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    maxLines = 1,
                                                    softWrap = false
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            Divider(color = Color(0xFF222225), thickness = 0.5.dp)

                            // Main Scrollable Grid area
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState())
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    // Left hours index column (12 AM to 11 PM or 24 hours)
                                    Column(
                                        modifier = Modifier.width(70.dp)
                                    ) {
                                        (0..23).forEach { hour ->
                                            Box(
                                                modifier = Modifier
                                                    .height(hourHeight)
                                                    .fillMaxWidth()
                                                    .padding(top = 4.dp),
                                                contentAlignment = Alignment.TopCenter
                                            ) {
                                                val label = when {
                                                    hour == 0 -> "12 AM"
                                                    hour == 12 -> "12 PM"
                                                    hour < 12 -> "$hour AM"
                                                    else -> "${hour - 12} PM"
                                                }
                                                Text(
                                                    text = label,
                                                    color = Color.Gray,
                                                    fontSize = 10.sp,
                                                    maxLines = 1,
                                                    softWrap = false
                                                )
                                            }
                                        }
                                    }

                                    // Right side schedule timeline with columns and task cards
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(hourHeight * 24)
                                    ) {
                                        // Hourly grid lines
                                        Column {
                                            (0..23).forEach { _ ->
                                                Box(
                                                    modifier = Modifier
                                                        .height(hourHeight)
                                                        .fillMaxWidth()
                                                ) {
                                                    Divider(
                                                        color = Color(0xFF222225),
                                                        thickness = 0.5.dp,
                                                        modifier = Modifier.align(Alignment.BottomStart)
                                                    )
                                                }
                                            }
                                        }

                                        // Vertical day separator grid lines
                                        Row(modifier = Modifier.fillMaxSize()) {
                                            (0..6).forEach { _ ->
                                                Box(
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .fillMaxHeight()
                                                ) {
                                                    Divider(
                                                        color = Color(0xFF222225),
                                                        thickness = 0.5.dp,
                                                        modifier = Modifier.align(Alignment.CenterEnd)
                                                    )
                                                }
                                            }
                                        }

                                        // Day Tasks mapping onto the grid columns
                                        Row(modifier = Modifier.fillMaxSize()) {
                                            weekDates.forEachIndexed { dayIdx, date ->
                                                val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(date)
                                                val dayTasks = tasks.filter { it.dueDateString == dateStr }

                                                Box(
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .fillMaxHeight()
                                                ) {
                                                    dayTasks.forEach { task ->
                                                        val timeParts = parseTaskTime(task.description)
                                                        if (timeParts != null) {
                                                            val (startHour, startMinute) = timeParts
                                                            val durationMins = parseTaskDuration(task.description)

                                                            val startMinutesOfDay = startHour * 60 + startMinute
                                                            val topOffsetDp = (startMinutesOfDay / 60f) * hourHeight.value
                                                            val heightDp = (durationMins / 60f) * hourHeight.value

                                                            val categoryColor = when (task.priority.uppercase()) {
                                                                "HIGH" -> Color(0xFFD32F2F)
                                                                "LOW" -> Color(0xFF388E3C)
                                                                else -> WaterBlue
                                                            }

                                                            Card(
                                                                modifier = Modifier
                                                                    .fillMaxWidth()
                                                                    .padding(horizontal = 2.dp)
                                                                    .offset(y = topOffsetDp.dp)
                                                                    .height(heightDp.dp)
                                                                    .clickable { selectedDayForDetail = date },
                                                                colors = CardDefaults.cardColors(
                                                                    containerColor = categoryColor.copy(alpha = 0.25f)
                                                                ),
                                                                border = BorderStroke(0.5.dp, categoryColor.copy(alpha = 0.8f)),
                                                                shape = RoundedCornerShape(4.dp)
                                                            ) {
                                                                Column(
                                                                    modifier = Modifier
                                                                        .fillMaxSize()
                                                                        .padding(4.dp),
                                                                    verticalArrangement = Arrangement.Center
                                                                ) {
                                                                    Text(
                                                                        text = task.title,
                                                                        color = Color.White,
                                                                        fontSize = 10.sp,
                                                                        fontWeight = FontWeight.Bold,
                                                                        maxLines = 1,
                                                                        overflow = TextOverflow.Ellipsis
                                                                    )
                                                                    if (heightDp >= 30) {
                                                                        val timeLabel = String.format(Locale.US, "%02d:%02d", if (startHour > 12) startHour - 12 else if (startHour == 0) 12 else startHour, startMinute) + (if (startHour >= 12) " PM" else " AM")
                                                                        Text(
                                                                            text = timeLabel,
                                                                            color = Color.LightGray,
                                                                            fontSize = 8.sp,
                                                                            maxLines = 1,
                                                                            overflow = TextOverflow.Ellipsis
                                                                        )
                                                                    }
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }

                                        // Current Time Line across the active day
                                        val now = Calendar.getInstance()
                                        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(now.time)
                                        val todayIdx = weekDates.indexOfFirst { SimpleDateFormat("yyyy-MM-dd", Locale.US).format(it) == todayStr }
                                        if (todayIdx != -1) {
                                            val currentHour = now.get(Calendar.HOUR_OF_DAY)
                                            val currentMinute = now.get(Calendar.MINUTE)
                                            val nowMinutesOfDay = currentHour * 60 + currentMinute
                                            val indicatorYOffsetDp = (nowMinutesOfDay / 60f) * hourHeight.value

                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .offset(y = indicatorYOffsetDp.dp)
                                            ) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    // Align with today's column precisely
                                                    Spacer(modifier = Modifier.weight(todayIdx.toFloat() + 0.05f))

                                                    Box(
                                                        modifier = Modifier
                                                            .size(6.dp)
                                                            .clip(CircleShape)
                                                            .background(Color(0xFFFF5252))
                                                    )

                                                    Box(
                                                        modifier = Modifier
                                                            .weight(0.9f)
                                                            .height(1.5.dp)
                                                            .background(Color(0xFFFF5252))
                                                    )

                                                    Spacer(modifier = Modifier.weight((6 - todayIdx).toFloat() + 0.05f))
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // DAY VIEW - Detailed schedule timeline with hourly blocks. Interlinked with task creation.
                    CalendarViewMode.DAY -> {
                        val activeDateStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(selectedMonthCalendar.time)
                        val dayTasks = tasks.filter { it.dueDateString == activeDateStr }
                        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
                        val isToday = activeDateStr == todayStr

                        // Day Header info matching screenshot 1 (TUE above, large 16 circle)
                        val dayOfWeekLabel = SimpleDateFormat("EEE", Locale.getDefault()).format(selectedMonthCalendar.time).uppercase()
                        val dayNumLabel = SimpleDateFormat("d", Locale.getDefault()).format(selectedMonthCalendar.time)

                        val scrollState = rememberScrollState()

                        Column(modifier = Modifier.fillMaxSize()) {
                            // Top Day Identifier block
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.padding(start = 12.dp)
                                ) {
                                    Text(
                                        text = dayOfWeekLabel,
                                        color = if (isToday) WaterBlue else Color.Gray,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Box(
                                        modifier = Modifier
                                            .size(44.dp)
                                            .clip(CircleShape)
                                            .background(if (isToday) WaterBlue else SurfaceCard),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = dayNumLabel,
                                            color = if (isToday) Color.Black else Color.White,
                                            fontSize = 18.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(16.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    val readableDate = SimpleDateFormat("EEEE, d MMMM yyyy", Locale.getDefault()).format(selectedMonthCalendar.time)
                                    Text(
                                        text = "DAILY TIMELINE",
                                        color = WaterBlue,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        letterSpacing = 1.2.sp
                                    )
                                    Text(
                                        text = readableDate,
                                        color = Color.White,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }

                            // Horizontal Divider
                            Divider(color = Color.Gray.copy(alpha = 0.15f), thickness = 1.dp)

                            // Calendar list of hours
                            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .verticalScroll(scrollState)
                                ) {
                                    // Header of hour table GMT indicator
                                    val tzOffset = remember {
                                        try {
                                            val tz = java.util.TimeZone.getDefault()
                                            val shortName = tz.getDisplayName(false, java.util.TimeZone.SHORT)
                                            shortName
                                        } catch (e: Exception) {
                                            "GMT-04"
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))

                                    // Generate hours from 00:00 to 23:00
                                    (0..23).forEach { hrInt ->
                                        val hrStr = String.format(Locale.US, "%02d:00", hrInt)
                                        
                                        // Find tasks specifically scheduled or pre-assigned to this hour block
                                        val tasksInThisHour = dayTasks.filter { task ->
                                            val taskHour = getTaskHourPrefix(task.description)
                                            taskHour == hrStr
                                        }

                                        // Hourly row block
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(64.dp)
                                                .clickable {
                                                    viewModel.triggerTaskCreationRedirect(activeDateStr, hrStr, "Inbox")
                                                },
                                            verticalAlignment = Alignment.Top
                                        ) {
                                            // Time Label on Left
                                            Box(
                                                modifier = Modifier
                                                    .width(64.dp)
                                                    .fillMaxHeight()
                                                    .padding(end = 8.dp, top = 4.dp),
                                                contentAlignment = Alignment.TopEnd
                                            ) {
                                                Text(
                                                    text = if (hrInt == 0) tzOffset else hrStr,
                                                    color = Color.Gray,
                                                    fontSize = 11.sp,
                                                    fontWeight = if (hrInt == 0) FontWeight.Bold else FontWeight.Medium
                                                )
                                            }

                                            // Timeline track on Right
                                            Box(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .fillMaxHeight()
                                            ) {
                                                // Horizontal Grid line
                                                Divider(
                                                    color = Color.Gray.copy(alpha = 0.15f),
                                                    thickness = 1.dp,
                                                    modifier = Modifier.align(Alignment.TopStart)
                                                )

                                                // Scheduled tasks list in hour
                                                if (tasksInThisHour.isNotEmpty()) {
                                                    Row(
                                                        modifier = Modifier
                                                            .fillMaxSize()
                                                            .padding(start = 8.dp, end = 16.dp, top = 6.dp, bottom = 4.dp),
                                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                    ) {
                                                        tasksInThisHour.forEach { th ->
                                                            Card(
                                                                modifier = Modifier
                                                                    .weight(1f)
                                                                    .fillMaxHeight()
                                                                    .clickable(enabled = true) {
                                                                        viewModel.navigateTo(com.example.ui.Screen.TASKS)
                                                                    },
                                                                colors = CardDefaults.cardColors(
                                                                    containerColor = if (th.isCompleted) Color(0xFF141416) else SurfaceCard
                                                                ),
                                                                shape = RoundedCornerShape(8.dp),
                                                                border = if (th.isCompleted) null else BorderStroke(0.5.dp, Color.Gray.copy(alpha = 0.3f))
                                                            ) {
                                                                Row(
                                                                    modifier = Modifier
                                                                        .fillMaxSize()
                                                                        .padding(8.dp),
                                                                    verticalAlignment = Alignment.CenterVertically
                                                                ) {
                                                                    Box(
                                                                        modifier = Modifier
                                                                            .size(8.dp)
                                                                            .clip(CircleShape)
                                                                            .background(
                                                                                when (th.priority.uppercase()) {
                                                                                    "HIGH" -> Color(0xFFF44336)
                                                                                    "LOW" -> Color(0xFF4CAF50)
                                                                                    else -> WaterBlue
                                                                                }
                                                                            )
                                                                    )
                                                                    Spacer(modifier = Modifier.width(8.dp))
                                                                    Column(modifier = Modifier.weight(1f)) {
                                                                        Text(
                                                                            text = th.title,
                                                                            color = if (th.isCompleted) Color.Gray else Color.White,
                                                                            fontWeight = FontWeight.Bold,
                                                                            fontSize = 12.sp,
                                                                            maxLines = 1,
                                                                            overflow = TextOverflow.Ellipsis,
                                                                            style = MaterialTheme.typography.bodySmall.copy(
                                                                                textDecoration = if (th.isCompleted) TextDecoration.LineThrough else null
                                                                            )
                                                                        )
                                                                        if (th.description.isNotEmpty()) {
                                                                            Text(
                                                                                text = th.description.replace(Regex("""\[[^\]]+\]"""), "").trim(),
                                                                                color = Color.Gray,
                                                                                fontSize = 10.sp,
                                                                                maxLines = 1,
                                                                                overflow = TextOverflow.Ellipsis
                                                                            )
                                                                        }
                                                                    }
                                                                }
                                                            }
                                                        }
                                                    }
                                                }

                                                // Current relative time pointer red line precisely overlayed if today
                                                if (isToday) {
                                                    val c = Calendar.getInstance()
                                                    val nowHr = c.get(Calendar.HOUR_OF_DAY)
                                                    val nowMin = c.get(Calendar.MINUTE)
                                                    
                                                    if (nowHr == hrInt) {
                                                        val offsetFraction = nowMin / 60f
                                                        Box(
                                                            modifier = Modifier
                                                                .fillMaxWidth()
                                                                .padding(top = (64 * offsetFraction).dp)
                                                        ) {
                                                            Divider(
                                                                color = Color(0xFFEA4335),
                                                                thickness = 1.8.dp,
                                                                modifier = Modifier.fillMaxWidth().align(Alignment.CenterStart)
                                                            )
                                                            Box(
                                                                modifier = Modifier
                                                                    .size(7.dp)
                                                                    .clip(CircleShape)
                                                                    .background(Color(0xFFEA4335))
                                                                    .align(Alignment.CenterStart)
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(24.dp))
                                }
                            }

                            Button(
                                onClick = { viewModel.triggerTaskCreationRedirect(activeDateStr, null, "Inbox") },
                                colors = ButtonDefaults.buttonColors(containerColor = WaterBlue),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, tint = Color.Black)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Add Task for $activeDateStr", color = Color.Black, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }

    // Day Detail Dialog - Opens on clicking day elements
    selectedDayForDetail?.let { date ->
        val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(date)
        val formattedHeader = SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.getDefault()).format(date)
        val dayTasks = tasks.filter { it.dueDateString == dateStr }

        AlertDialog(
            onDismissRequest = { selectedDayForDetail = null },
            title = {
                Column {
                    Text("Day Details", fontSize = 12.sp, color = WaterBlue, fontWeight = FontWeight.Bold)
                    Text(formattedHeader, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            },
            containerColor = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.05f),
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 280.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (dayTasks.isEmpty()) {
                         Box(
                             modifier = Modifier
                                 .fillMaxWidth()
                                 .padding(vertical = 12.dp),
                             contentAlignment = Alignment.Center
                         ) {
                             Text("No tasks scheduled for this day.", color = Color.Gray, fontSize = 13.sp)
                         }
                    } else {
                        dayTasks.forEach { task ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                                border = if (task.isCompleted) null else BorderStroke(0.5.dp, Color.Gray.copy(alpha = 0.2f))
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Checkbox(
                                            checked = task.isCompleted,
                                            onCheckedChange = { checked ->
                                                viewModel.updateTask(task.copy(isCompleted = checked))
                                            },
                                            colors = CheckboxDefaults.colors(
                                                checkedColor = WaterBlue,
                                                uncheckedColor = Color.Gray
                                            )
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column {
                                            Text(
                                                text = task.title,
                                                color = if (task.isCompleted) Color.Gray else Color.White,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                style = MaterialTheme.typography.bodyLarge.copy(
                                                    textDecoration = if (task.isCompleted) TextDecoration.LineThrough else null
                                                )
                                            )
                                            if (task.description.isNotEmpty()) {
                                                Text(
                                                    text = task.description,
                                                    color = Color.Gray,
                                                    fontSize = 11.sp,
                                                    maxLines = 2,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                    }
                                    
                                    val priorityLabel = task.priority.uppercase()
                                    val priorityColor = when (priorityLabel) {
                                        "HIGH" -> Color(0xFFF44336)
                                        "LOW" -> Color(0xFF4CAF50)
                                        else -> WaterBlue
                                    }
                                    Box(
                                         modifier = Modifier
                                             .clip(RoundedCornerShape(4.dp))
                                             .background(priorityColor.copy(alpha = 0.15f))
                                             .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(priorityLabel, color = priorityColor, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // Quick add task button redirecting
                    Button(
                        onClick = {
                            viewModel.triggerTaskCreationRedirect(dateStr, null, "Inbox")
                            selectedDayForDetail = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = WaterBlue),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, tint = Color.Black)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Add Task for This Day", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedDayForDetail = null }) {
                    Text("Close", color = Color.White)
                }
            }
        )
    }
}

@Composable
fun MiniMonthGrid(
    year: Int,
    month: Int,
    tasks: List<Task>,
    onMonthClick: () -> Unit,
    onDayClick: (Int) -> Unit
) {
    val cal = remember(year, month) {
        Calendar.getInstance().apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month)
            set(Calendar.DAY_OF_MONTH, 1)
        }
    }
    
    val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
    val firstDayDayOfWeek = cal.get(Calendar.DAY_OF_WEEK) // 1 = Sunday, 2 = Monday, etc.
    val prevPadding = firstDayDayOfWeek - 1 // Sunday start offset
    
    val totalCells = prevPadding + daysInMonth
    val weeksCount = (totalCells + 6) / 7
    
    val monthName = remember(month) {
        val formatter = SimpleDateFormat("MMMM", Locale.getDefault())
        cal.set(Calendar.MONTH, month)
        formatter.format(cal.time)
    }
    
    val todayCal = remember { Calendar.getInstance() }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onMonthClick() },
        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
        border = BorderStroke(1.dp, Color(0xFF222225)),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier.padding(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Month Heading
            Text(
                text = monthName.uppercase(),
                color = WaterBlue,
                fontWeight = FontWeight.Black,
                fontSize = 11.sp,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            
            // Days of week initials (S M T W T F S)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                listOf("S", "M", "T", "W", "T", "F", "S").forEach { day ->
                    Text(
                        text = day,
                        color = Color.Gray,
                        fontSize = 7.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(2.dp))
            
            // 5 or 6 rows of mini dates
            Column(
                verticalArrangement = Arrangement.spacedBy(1.dp)
            ) {
                for (weekIdx in 0 until weeksCount) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(1.dp)
                    ) {
                        for (dayOfWeekIdx in 0..6) {
                            val cellIndex = weekIdx * 7 + dayOfWeekIdx
                            val dayNumber = cellIndex - prevPadding + 1
                            
                            if (dayNumber in 1..daysInMonth) {
                                val isToday = todayCal.get(Calendar.YEAR) == year &&
                                        todayCal.get(Calendar.MONTH) == month &&
                                        todayCal.get(Calendar.DAY_OF_MONTH) == dayNumber
                                        
                                val cellDateStr = String.format(Locale.US, "%04d-%02d-%02d", year, month + 1, dayNumber)
                                val hasTasks = remember(cellDateStr, tasks) {
                                    tasks.any { it.dueDateString == cellDateStr }
                                }
                                
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .aspectRatio(1f)
                                        .clip(CircleShape)
                                        .background(
                                            when {
                                                isToday -> WaterBlue.copy(alpha = 0.3f)
                                                hasTasks -> WaterBlue.copy(alpha = 0.1f)
                                                else -> Color.Transparent
                                            }
                                        )
                                        .border(
                                            width = if (isToday) 0.5.dp else 0.dp,
                                            color = if (isToday) WaterBlue else Color.Transparent,
                                            shape = CircleShape
                                        )
                                        .clickable { onDayClick(dayNumber) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = dayNumber.toString(),
                                        color = if (isToday) WaterBlue else if (hasTasks) Color.White else Color.LightGray,
                                        fontSize = 7.sp,
                                        fontWeight = if (isToday || hasTasks) FontWeight.Bold else FontWeight.Normal,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            } else {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }
}
