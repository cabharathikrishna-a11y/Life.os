package com.example.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.data.AppDatabase
import com.example.util.AlarmScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        Log.d("BootReceiver", "Received broadcast: $action")
        
        if (action == Intent.ACTION_BOOT_COMPLETED || action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            // Start the persistent foreground service if enabled to prevent sleep/termination
            val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            if (prefs.getBoolean("keep_notification_enabled", true)) {
                com.example.service.KeepAliveService.start(context)
            }

            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val db = AppDatabase.getInstance(context)
                    // Retrieve all current active tasks (first snapshot of database flow)
                    val tasks = db.taskDao().getAllTasks().first()
                    
                    Log.d("BootReceiver", "Rescheduling reminders for ${tasks.size} tasks on device initialization")
                    tasks.forEach { task ->
                        if (!task.isCompleted && task.dueDateString.isNotEmpty()) {
                            AlarmScheduler.scheduleReminder(context, task)
                        }
                    }
                    AlarmScheduler.scheduleAllDayNotification(context)
                } catch (e: Exception) {
                    Log.e("BootReceiver", "Could not complete reschedule: ${e.message}")
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}
