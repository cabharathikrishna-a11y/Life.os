package com.example.util

import android.content.Context
import android.content.SharedPreferences
import android.content.Intent
import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object AppBlockHelper {
    private const val PREFS_NAME = "app_blocks_preferences"
    private const val KEY_BLOCKED_APPS = "blocked_apps"
    private const val KEY_LAST_USAGE_DATE = "last_usage_date"
    private const val PREFIX_DAILY_LIMIT = "daily_limit_"
    private const val PREFIX_DAILY_USAGE = "daily_usage_"
    private const val PREFIX_SESSION_EXPIRY = "session_expiry_"

    // Default social apps
    val DEFAULT_BLOCKED_APPS = setOf("com.instagram.android", "com.snapchat.android")

    /**
     * Checks if an app is installed on the device.
     */
    fun isAppInstalled(context: Context, packageName: String): Boolean {
        return try {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Initializes default strict apps on install/first load of settings if not already done.
     */
    fun initializeStrictAppsIfNeeded(context: Context) {
        val strictPrefs = context.getSharedPreferences("strict_mode_prefs", Context.MODE_PRIVATE)
        if (!strictPrefs.contains("strict_mode_enabled")) {
            strictPrefs.edit().putBoolean("strict_mode_enabled", true).apply()
        }
        if (!strictPrefs.getBoolean("strict_apps_initialized_v2", false)) {
            val defaultStrict = mutableSetOf<String>()
            if (isAppInstalled(context, "com.instagram.android")) {
                defaultStrict.add("com.instagram.android")
            }
            if (isAppInstalled(context, "com.snapchat.android")) {
                defaultStrict.add("com.snapchat.android")
            }
            if (defaultStrict.isNotEmpty()) {
                val currentSet = strictPrefs.getStringSet("blocked_packages", emptySet()) ?: emptySet()
                val resultSet = currentSet + defaultStrict
                strictPrefs.edit()
                    .putStringSet("blocked_packages", resultSet)
                    .putBoolean("strict_apps_initialized_v2", true)
                    .apply()
            } else {
                // Mark initialized even if none installed to avoid repeating checks
                strictPrefs.edit().putBoolean("strict_apps_initialized_v2", true).apply()
            }
        }
    }

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * Checks if Usage Stats permission is robustly granted.
     */
    fun hasUsageStatsPermission(context: Context): Boolean {
        return try {
            val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? android.app.AppOpsManager
            val mode = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                appOps?.unsafeCheckOpNoThrow(android.app.AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), context.packageName)
            } else {
                @Suppress("DEPRECATION")
                appOps?.checkOpNoThrow(android.app.AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), context.packageName)
            }
            if (mode == android.app.AppOpsManager.MODE_ALLOWED) {
                true
            } else {
                val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? android.app.usage.UsageStatsManager
                if (usageStatsManager != null) {
                    val now = System.currentTimeMillis()
                    val stats = usageStatsManager.queryUsageStats(android.app.usage.UsageStatsManager.INTERVAL_DAILY, now - 60000, now)
                    !stats.isNullOrEmpty()
                } else {
                    false
                }
            }
        } catch (e: Exception) {
            false
        }
    }

    private fun getTodayDateString(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        return sdf.format(Date())
    }

    /**
     * Resets or updates daily stats if the date has changed.
     */
    fun checkAndResetDailyUsageIfNeeded(context: Context) {
        val prefs = getPrefs(context)
        val today = getTodayDateString()
        val savedDate = prefs.getString(KEY_LAST_USAGE_DATE, "")
        
        if (savedDate != today) {
            val editor = prefs.edit()
            editor.putString(KEY_LAST_USAGE_DATE, today)
            
            // Clear all accumulated daily usage seconds
            val allKeys = prefs.all
            for (key in allKeys.keys) {
                if (key.startsWith(PREFIX_DAILY_USAGE)) {
                    editor.putInt(key, 0)
                }
            }
            editor.apply()
            Log.d("AppBlockHelper", "Daily screen-time usage stats reset for a new day: $today")
        }
    }

    /**
     * Gets the set of monitored apps (including standard ones and user-added ones).
     */
    fun getBlockedApps(context: Context): Set<String> {
        val prefs = getPrefs(context)
        val saved = prefs.getStringSet(KEY_BLOCKED_APPS, null)
        if (saved == null) {
            // First time initialization
            prefs.edit().putStringSet(KEY_BLOCKED_APPS, DEFAULT_BLOCKED_APPS).apply()
            return DEFAULT_BLOCKED_APPS
        }
        return saved
    }

    /**
     * Updates the monitored apps set.
     */
    fun setBlockedApps(context: Context, apps: Set<String>) {
        getPrefs(context).edit().putStringSet(KEY_BLOCKED_APPS, apps).apply()
        
        // Also add any of these apps to strict mode blocked packages
        val strictPrefs = context.getSharedPreferences("strict_mode_prefs", Context.MODE_PRIVATE)
        val strictSet = strictPrefs.getStringSet("blocked_packages", emptySet())?.toMutableSet() ?: mutableSetOf()
        strictSet.addAll(apps)
        strictPrefs.edit().putStringSet("blocked_packages", strictSet).apply()
    }

    /**
     * Adds an app to the monitored apps list.
     */
    fun addBlockedApp(context: Context, packageName: String) {
        val current = getBlockedApps(context).toMutableSet()
        current.add(packageName)
        setBlockedApps(context, current)
    }

    /**
     * Removes an app from the monitored apps list.
     */
    fun removeBlockedApp(context: Context, packageName: String) {
        val current = getBlockedApps(context).toMutableSet()
        current.remove(packageName)
        setBlockedApps(context, current)
    }

    /**
     * Gets the configured daily limit in minutes for an app. Defaults to 30 mins.
     */
    fun getDailyLimitMinutes(context: Context, packageName: String): Int {
        return getPrefs(context).getInt(PREFIX_DAILY_LIMIT + packageName, 30)
    }

    /**
     * Sets the configured daily limit in minutes for an app.
     */
    fun setDailyLimitMinutes(context: Context, packageName: String, minutes: Int) {
        getPrefs(context).edit().putInt(PREFIX_DAILY_LIMIT + packageName, minutes).apply()
    }

    /**
     * Gets today's usage in seconds for an app.
     */
    fun getDailyUsageSeconds(context: Context, packageName: String): Int {
        checkAndResetDailyUsageIfNeeded(context)
        return getPrefs(context).getInt(PREFIX_DAILY_USAGE + packageName, 0)
    }

    /**
     * Increments today's usage by a specified amount of seconds (defaults to 1).
     */
    fun incrementDailyUsageSeconds(context: Context, packageName: String, amount: Int = 1) {
        checkAndResetDailyUsageIfNeeded(context)
        val current = getDailyUsageSeconds(context, packageName)
        getPrefs(context).edit().putInt(PREFIX_DAILY_USAGE + packageName, current + amount).apply()
    }

    /**
     * Checks if today's usage for an app has exceeded its daily limit.
     */
    fun isDailyLimitExceeded(context: Context, packageName: String): Boolean {
        val limitMinutes = getDailyLimitMinutes(context, packageName)
        val usageSeconds = getDailyUsageSeconds(context, packageName)
        return usageSeconds >= (limitMinutes * 60)
    }

    /**
     * Starts a temporary usage session (e.g., 5, 10, 15, 20 minutes) for an app.
     */
    fun startTemporarySession(context: Context, packageName: String, durationMinutes: Int) {
        val expiryTime = System.currentTimeMillis() + (durationMinutes * 60 * 1000L)
        getPrefs(context).edit().putLong(PREFIX_SESSION_EXPIRY + packageName, expiryTime).apply()
        Log.d("AppBlockHelper", "Started temporary session for $packageName: duration = $durationMinutes minutes")
    }

    /**
     * Checks if an active temporary session is currently running and hasn't expired.
     */
    fun isSessionActive(context: Context, packageName: String): Boolean {
        val expiryTime = getPrefs(context).getLong(PREFIX_SESSION_EXPIRY + packageName, 0L)
        val active = System.currentTimeMillis() < expiryTime
        if (!active && expiryTime > 0L) {
            // Clean up expired session
            getPrefs(context).edit().putLong(PREFIX_SESSION_EXPIRY + packageName, 0L).apply()
        }
        return active
    }

    /**
     * Checks if we need to show the temporary duration select popup for this app.
     * This occurs if the app is opened, daily limit is NOT exceeded, and there is no active session.
     */
    fun shouldShowSessionSelector(context: Context, packageName: String): Boolean {
        val isBlocked = getBlockedApps(context).contains(packageName)
        if (!isBlocked) return false
        
        val limitOver = isDailyLimitExceeded(context, packageName)
        if (limitOver) return false
        
        val sessionActive = isSessionActive(context, packageName)
        return !sessionActive
    }

    /**
     * Clear all sessions for manual resets.
     */
    fun clearSessions(context: Context) {
        val prefs = getPrefs(context)
        val editor = prefs.edit()
        val allKeys = prefs.all
        for (key in allKeys.keys) {
            if (key.startsWith(PREFIX_SESSION_EXPIRY)) {
                editor.putLong(key, 0L)
            }
        }
        editor.apply()
    }

    fun isPackageBlockedInStrictMode(context: Context, packageName: String): Boolean {
        // 1. Exclude our own app
        if (packageName == context.packageName) return false
        
        // 2. Exclude Google Gemini, WhatsApp, Chrome
        val lowerPkg = packageName.lowercase()
        if (lowerPkg.contains("gemini") || 
            lowerPkg.contains("bard") || 
            lowerPkg.contains("whatsapp") || 
            lowerPkg.contains("chrome") ||
            packageName == "com.google.android.apps.bard" ||
            packageName == "com.whatsapp" ||
            packageName == "com.android.chrome") {
            return false
        }
        
        // 3. Exclude essential apps: Phone, Messages, Contacts
        if (lowerPkg.contains("dialer") || 
            lowerPkg.contains("phone") || 
            lowerPkg.contains("messaging") || 
            lowerPkg.contains("message") ||
            lowerPkg.contains("contacts") ||
            lowerPkg.contains("contacts.android") ||
            packageName == "com.google.android.dialer" ||
            packageName == "com.android.phone" ||
            packageName == "com.google.android.apps.messaging" ||
            packageName == "com.android.messaging" ||
            packageName == "com.google.android.contacts" ||
            packageName == "com.android.contacts") {
            return false
        }
        
        // 4. Exclude system launchers / UI (Android OS components)
        if (lowerPkg.contains("launcher") || 
            lowerPkg.contains("systemui") || 
            packageName == "android" || 
            packageName == "com.android.systemui") {
            return false
        }
        
        // 5. Exclude system apps (unless it's an updated system app)
        try {
            val pm = context.packageManager
            val appInfo = pm.getApplicationInfo(packageName, 0)
            val isSystem = (appInfo.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0
            val isUpdatedSystem = (appInfo.flags and android.content.pm.ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
            if (isSystem && !isUpdatedSystem) {
                return false
            }
        } catch (e: Exception) {
            return false
        }
        
        return true
    }

    fun checkForegroundAppAndBlockIfNeeded(context: Context) {
        val isFocusing = (FocusTimerManager.isTimerRunning.value || FocusTimerManager.isStopwatchActive.value) && FocusTimerManager.isFocusPhase.value
        if (!isFocusing) return

        val strictPrefs = context.getSharedPreferences("strict_mode_prefs", Context.MODE_PRIVATE)
        val strictEnabled = strictPrefs.getBoolean("strict_mode_enabled", true)
        
        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? android.app.usage.UsageStatsManager ?: return
        val time = System.currentTimeMillis()
        val usageEvents = usageStatsManager.queryEvents(time - 2000, time)
        
        var currentForegroundApp: String? = null
        val event = android.app.usage.UsageEvents.Event()
        
        while (usageEvents != null && usageEvents.hasNextEvent()) {
            usageEvents.getNextEvent(event)
            if (event.eventType == android.app.usage.UsageEvents.Event.ACTIVITY_RESUMED) {
                currentForegroundApp = event.packageName
            }
        }

        if (currentForegroundApp != null) {
            val isBlocked = if (strictEnabled) {
                isPackageBlockedInStrictMode(context, currentForegroundApp)
            } else {
                val blockedApps = strictPrefs.getStringSet("blocked_packages", emptySet()) ?: emptySet()
                blockedApps.contains(currentForegroundApp)
            }

            if (isBlocked) {
                Log.w("AppBlocker", "Intercepted blocked app: $currentForegroundApp")
                
                val blockIntent = Intent(context, com.example.ui.AppBlockInterceptActivity::class.java).apply {
                    putExtra("INTERCEPTED_PACKAGE", currentForegroundApp)
                    putExtra("IS_LIMIT_BLOCK", false)
                    putExtra("IS_STRICT_MODE_INTERCEPT", strictEnabled)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                }
                context.startActivity(blockIntent)
            }
        }
    }
}
