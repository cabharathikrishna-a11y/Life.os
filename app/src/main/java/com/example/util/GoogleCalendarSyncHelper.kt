package com.example.util

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.CalendarContract
import android.util.Log
import com.example.data.Task
import java.text.SimpleDateFormat
import java.util.*

object GoogleCalendarSyncHelper {

    private const val TAG = "GoogleCalendarSync"

    // Helper to check and get a calendar ID (preferring Google account calendars)
    fun getOrCreateCalendarId(context: Context): Long? {
        val resolver = context.contentResolver
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.ACCOUNT_NAME,
            CalendarContract.Calendars.ACCOUNT_TYPE,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME
        )

        var cursor: Cursor? = null
        try {
            cursor = resolver.query(
                CalendarContract.Calendars.CONTENT_URI,
                projection,
                null,
                null,
                null
            )
            
            var fallbackId: Long? = null
            cursor?.use {
                while (it.moveToNext()) {
                    val id = it.getLong(0)
                    val accountType = it.getString(2)
                    
                    // Prefer Google account calendars
                    if (accountType == "com.google") {
                        Log.d(TAG, "Found Google Calendar ID: $id")
                        return id
                    }
                    if (fallbackId == null) {
                        fallbackId = id
                    }
                }
            }
            if (fallbackId != null) {
                Log.d(TAG, "No Google Calendar found, falling back to calendar ID: $fallbackId")
                return fallbackId
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Permission missing for querying calendars: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "Error querying calendars: ${e.message}", e)
        }

        // If no calendars exist, we can try to create a local one or return null
        return null
    }

    // Bidirectional sync
    suspend fun syncGoogleCalendar(
        context: Context,
        localTasks: List<Task>,
        onImportTask: suspend (String, String, Int, String) -> Long,
        onUpdateTaskDescription: suspend (Task, String) -> Unit
    ): String {
        val calendarId = getOrCreateCalendarId(context)
            ?: return "No calendar found on device. Please set up a Google account first."

        var importedCount = 0
        var exportedCount = 0

        val resolver = context.contentResolver
        val timeZone = TimeZone.getDefault().id

        // 1. IMPORT FROM GOOGLE CALENDAR
        val sdfDate = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        
        // Define query window: from 30 days ago to 60 days in the future
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.DAY_OF_YEAR, -30)
        val startMillis = calendar.timeInMillis
        calendar.add(Calendar.DAY_OF_YEAR, 90)
        val endMillis = calendar.timeInMillis

        val selection = "(${CalendarContract.Events.CALENDAR_ID} = ?) AND (${CalendarContract.Events.DTSTART} >= ?) AND (${CalendarContract.Events.DTSTART} <= ?) AND (deleted != 1)"
        val selectionArgs = arrayOf(calendarId.toString(), startMillis.toString(), endMillis.toString())

        val projection = arrayOf(
            CalendarContract.Events._ID,
            CalendarContract.Events.TITLE,
            CalendarContract.Events.DESCRIPTION,
            CalendarContract.Events.DTSTART,
            CalendarContract.Events.DTEND
        )

        var eventCursor: Cursor? = null
        try {
            eventCursor = resolver.query(
                CalendarContract.Events.CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                null
            )

            eventCursor?.use { cursor ->
                while (cursor.moveToNext()) {
                    val eventId = cursor.getLong(0)
                    val title = cursor.getString(1) ?: "Google Event"
                    val description = cursor.getString(2) ?: ""
                    val dtStart = cursor.getLong(3)
                    val dtEnd = cursor.getLong(4)

                    val eventDateStr = sdfDate.format(Date(dtStart))

                    // Check if we already have this synced locally
                    val alreadySynced = localTasks.any { task ->
                        task.description.contains("[GCalEventId: $eventId]") ||
                        (task.title.trim().equals(title.trim(), ignoreCase = true) && task.dueDateString == eventDateStr)
                    }

                    if (!alreadySynced && !description.contains("[AppTaskId:")) {
                        // Estimate duration
                        val estMinutes = if (dtEnd > dtStart) {
                            ((dtEnd - dtStart) / 60000).toInt().coerceAtLeast(15)
                        } else {
                            30
                        }

                        val hourFormatter = SimpleDateFormat("hh:mm a", Locale.US)
                        val timeStr = hourFormatter.format(Date(dtStart))
                        val cleanDesc = if (description.isEmpty()) {
                            "[Time: $timeStr] [Duration: ${estMinutes}m]\n\n[GCalEventId: $eventId]"
                        } else {
                            "$description\n[Time: $timeStr] [Duration: ${estMinutes}m]\n\n[GCalEventId: $eventId]"
                        }

                        onImportTask(title, cleanDesc, estMinutes, eventDateStr)
                        importedCount++
                    }
                }
            }
        } catch (e: SecurityException) {
            return "Calendar permissions are required to sync Google Calendar."
        } catch (e: Exception) {
            Log.e(TAG, "Failed importing from Google Calendar: ${e.message}", e)
            return "Sync failed: ${e.message}"
        }

        // 2. EXPORT TO GOOGLE CALENDAR
        for (task in localTasks) {
            // Check if task is scheduled for a date, and is not already synced
            if (task.dueDateString.isNotEmpty() && !task.description.contains("[GCalEventId:")) {
                try {
                    val dateParts = task.dueDateString.split("-")
                    if (dateParts.size == 3) {
                        val year = dateParts[0].toIntOrNull() ?: continue
                        val month = (dateParts[1].toIntOrNull() ?: continue) - 1
                        val day = dateParts[2].toIntOrNull() ?: continue

                        // Try parsing [Time: hh:mm AM/PM] or standard time from task description
                        var startHour = 9
                        var startMinute = 0
                        val parsedTime = parseTaskTime(task.description)
                        if (parsedTime != null) {
                            startHour = parsedTime.first
                            startMinute = parsedTime.second
                        }

                        val startCal = Calendar.getInstance().apply {
                            set(Calendar.YEAR, year)
                            set(Calendar.MONTH, month)
                            set(Calendar.DAY_OF_MONTH, day)
                            set(Calendar.HOUR_OF_DAY, startHour)
                            set(Calendar.MINUTE, startMinute)
                            set(Calendar.SECOND, 0)
                            set(Calendar.MILLISECOND, 0)
                        }

                        val durationMin = task.estimatedMinutes.coerceAtLeast(15)
                        val endCal = Calendar.getInstance().apply {
                            timeInMillis = startCal.timeInMillis + (durationMin * 60 * 1000L)
                        }

                        val values = ContentValues().apply {
                            put(CalendarContract.Events.CALENDAR_ID, calendarId)
                            put(CalendarContract.Events.TITLE, task.title)
                            put(CalendarContract.Events.DESCRIPTION, "${task.description}\n\n[AppTaskId: ${task.id}]")
                            put(CalendarContract.Events.DTSTART, startCal.timeInMillis)
                            put(CalendarContract.Events.DTEND, endCal.timeInMillis)
                            put(CalendarContract.Events.EVENT_TIMEZONE, timeZone)
                        }

                        val uri: Uri? = resolver.insert(CalendarContract.Events.CONTENT_URI, values)
                        if (uri != null) {
                            val newEventId = ContentUris.parseId(uri)
                            // Update our local task description to reflect GCal event id
                            val updatedDesc = if (task.description.isEmpty()) {
                                "[GCalEventId: $newEventId]"
                            } else {
                                "${task.description}\n\n[GCalEventId: $newEventId]"
                            }
                            onUpdateTaskDescription(task, updatedDesc)
                            exportedCount++
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed exporting task '${task.title}': ${e.message}", e)
                }
            }
        }

        return "Sync Complete! Imported $importedCount new events, Exported $exportedCount tasks."
    }

    // Helper to parse time from description
    private fun parseTaskTime(description: String): Pair<Int, Int>? {
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
        return null
    }
}
