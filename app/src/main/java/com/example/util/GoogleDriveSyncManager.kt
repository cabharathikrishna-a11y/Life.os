package com.example.util

import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.UserRecoverableAuthException
import com.google.android.gms.auth.api.signin.GoogleSignIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.Locale

object GoogleDriveSyncManager {
    private const val TAG = "GoogleDriveSync"
    private const val DRIVE_SCOPE = "oauth2:https://www.googleapis.com/auth/drive.appdata"
    private const val BACKUP_FILE_NAME = "focus_backup.json"
    private const val ALL_DATA_BACKUP_FILE_NAME = "app_data_backup.zip"

    private val client = OkHttpClient()
    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

    /**
     * Obtains the OAuth2 access token for the signed-in Google account.
     * If authentication resolution is required (e.g. user needs to approve permission),
     * [onAuthResolutionRequired] is invoked with the required Intent.
     */
    suspend fun getAccessToken(
        context: Context,
        onAuthResolutionRequired: (Intent) -> Unit = {}
    ): String? = withContext(Dispatchers.IO) {
        try {
            val account = GoogleSignIn.getLastSignedInAccount(context)
            if (account == null) {
                Log.w(TAG, "No Google account signed in.")
                return@withContext null
            }
            val email = account.email
            if (email.isNullOrBlank()) {
                Log.w(TAG, "Google account has no email address.")
                return@withContext null
            }
            GoogleAuthUtil.getToken(context, email, DRIVE_SCOPE)
        } catch (recoverable: UserRecoverableAuthException) {
            Log.w(TAG, "User recoverable auth exception encountered.", recoverable)
            recoverable.intent?.let { onAuthResolutionRequired(it) }
            null
        } catch (e: Exception) {
            Log.e(TAG, "Error obtaining Google OAuth2 token: ${e.message}", e)
            null
        }
    }

    /**
     * Checks whether the user has signed in and granted the Drive AppData scope.
     */
    fun hasDrivePermission(context: Context): Boolean {
        val driveScope = com.google.android.gms.common.api.Scope("https://www.googleapis.com/auth/drive.appdata")
        val account = GoogleSignIn.getLastSignedInAccount(context)
        return account != null && GoogleSignIn.hasPermissions(account, driveScope)
    }

    /**
     * Performs a backup of focus-related data (focus records list, total focus minutes, today's pomos count)
     * to the user's hidden Google Drive AppData folder.
     */
    suspend fun backupFocusData(
        context: Context,
        onAuthResolutionRequired: (Intent) -> Unit = {}
    ): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val token = getAccessToken(context, onAuthResolutionRequired)
            ?: return@withContext Pair(false, "Authorization required. Please connect your Google Drive.")

        try {
            // 1. Load localized focus data from SharedPreferences
            val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            val focusRecordsSerialized = prefs.getString("focus_records_list", "") ?: ""
            val totalMinutes = prefs.getInt("total_focus_minutes", 0)
            val pomosCount = prefs.getInt("today_pomos_count", 0)

            // 2. Build beautiful focus backup JSON payload
            val backupJson = JSONObject().apply {
                put("focus_records_list", focusRecordsSerialized)
                put("total_focus_minutes", totalMinutes)
                put("today_pomos_count", pomosCount)
                put("last_sync_timestamp", System.currentTimeMillis())
                put("device_model", android.os.Build.MODEL)
            }
            val contentStr = backupJson.toString()

            // 3. Find if the file already exists in AppData
            var fileId = findBackupFileId(token)
            if (fileId == null) {
                // Not found, create new file metadata first
                Log.i(TAG, "Backup file not found in Google Drive. Creating a new one...")
                fileId = createBackupFileMetadata(token)
                if (fileId == null) {
                    return@withContext Pair(false, "Failed to initialize backup space in Google Drive.")
                }
            }

            // 4. Upload/Patch the content to Google Drive
            val uploadSuccess = uploadBackupFileContent(token, fileId, contentStr)
            if (uploadSuccess) {
                // Save last synced timestamp locally
                prefs.edit().putLong("gd_focus_last_sync_timestamp", System.currentTimeMillis()).apply()
                Pair(true, "Successfully backed up focus history to Google Drive.")
            } else {
                Pair(false, "Failed to upload focus data to Google Drive.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error backing up focus data: ${e.message}", e)
            Pair(false, "Sync Error: ${e.localizedMessage ?: "Unknown error"}")
        }
    }

    /**
     * Retrieves focus-related data from Google Drive AppData folder,
     * reconciles and merges it with current local focus history (avoiding duplicates),
     * and restores the state.
     */
    suspend fun restoreFocusData(
        context: Context,
        onAuthResolutionRequired: (Intent) -> Unit = {}
    ): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val token = getAccessToken(context, onAuthResolutionRequired)
            ?: return@withContext Pair(false, "Authorization required. Please connect your Google Drive.")

        try {
            // 1. Find the backup file in AppData folder
            val fileId = findBackupFileId(token)
                ?: return@withContext Pair(false, "No backup file found on your Google Drive. Save a backup first.")

            // 2. Download backup content
            val contentStr = downloadBackupFileContent(token, fileId)
                ?: return@withContext Pair(false, "Failed to read backup from Google Drive.")

            val backupJson = JSONObject(contentStr)
            val remoteSerializedRecords = backupJson.optString("focus_records_list", "")
            val remoteTotalMinutes = backupJson.optInt("total_focus_minutes", 0)
            val remotePomosCount = backupJson.optInt("today_pomos_count", 0)

            // 3. Load local focus records
            val localRecords = FocusTimerManager.loadFocusRecords(context)
            
            // Parse remote records from the serialized string
            val remoteRecords = parseSerializedFocusRecords(remoteSerializedRecords)

            // 4. Reconciliation: Merge lists and keep unique records (using unique key combination)
            val mergedRecords = (localRecords + remoteRecords).distinctBy { record ->
                "${record.startTime}_${record.endTime}_${record.taskTitle}_${record.durationSeconds}"
            }

            // 5. Update local storage
            FocusTimerManager.saveFocusRecords(context, mergedRecords)
            
            // Overwrite total stats with max values or merged values to preserve progress
            val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            val finalTotalMinutes = maxOf(prefs.getInt("total_focus_minutes", 0), remoteTotalMinutes, mergedRecords.sumOf { it.durationMinutes })
            val finalPomosCount = maxOf(prefs.getInt("today_pomos_count", 0), remotePomosCount)

            prefs.edit().apply {
                putInt("total_focus_minutes", finalTotalMinutes)
                putInt("today_pomos_count", finalPomosCount)
                putLong("gd_focus_last_sync_timestamp", System.currentTimeMillis())
                apply()
            }

            // 6. Update FocusTimerManager live states on main thread
            withContext(Dispatchers.Main) {
                FocusTimerManager.focusRecords.value = mergedRecords
                FocusTimerManager.totalFocusMinutes.value = finalTotalMinutes
                FocusTimerManager.todayPomosCount.value = finalPomosCount
            }

            Pair(true, "Successfully restored and merged ${remoteRecords.size} focus records from Google Drive!")
        } catch (e: Exception) {
            Log.e(TAG, "Error restoring focus data: ${e.message}", e)
            Pair(false, "Restore Error: ${e.localizedMessage ?: "Unknown error"}")
        }
    }

    /**
     * Performs a backup of the entire app database and attachment files (ZIP package)
     * to the user's hidden Google Drive AppData folder.
     */
    suspend fun backupAllAppData(
        context: Context,
        database: com.example.data.AppDatabase,
        onAuthResolutionRequired: (Intent) -> Unit = {}
    ): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val token = getAccessToken(context, onAuthResolutionRequired)
            ?: return@withContext Pair(false, "Authorization required. Please connect your Google Drive.")

        try {
            // 1. Create a temp file to hold our zip backup
            val tempFile = java.io.File(context.cacheDir, "temp_app_data_backup.zip")
            if (tempFile.exists()) tempFile.delete()

            // 2. Export database and files to the temp zip file
            val exportSuccess = tempFile.outputStream().use { fos ->
                DatabaseBackupHelper.exportDataToStream(context, database, fos)
            }

            if (!exportSuccess) {
                if (tempFile.exists()) tempFile.delete()
                return@withContext Pair(false, "Failed to compile backup package locally.")
            }

            // 3. Find if the file already exists in AppData
            var fileId = findFileId(token, ALL_DATA_BACKUP_FILE_NAME)
            if (fileId == null) {
                fileId = createFileMetadata(token, ALL_DATA_BACKUP_FILE_NAME)
                if (fileId == null) {
                    tempFile.delete()
                    return@withContext Pair(false, "Failed to initialize backup slot in Google Drive.")
                }
            }

            // 4. Upload the zip binary
            val bytes = tempFile.readBytes()
            val requestBody = bytes.toRequestBody("application/zip".toMediaType())
            val url = "https://www.googleapis.com/upload/drive/v3/files/$fileId?uploadType=media"
            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $token")
                .addHeader("Content-Type", "application/zip")
                .patch(requestBody)
                .build()

            var uploadSuccess = false
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    uploadSuccess = true
                } else {
                    Log.e(TAG, "Error uploading zip: code=${response.code} body=${response.body?.string()}")
                }
            }

            // Clean up
            tempFile.delete()

            if (uploadSuccess) {
                val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                prefs.edit().putLong("gd_all_last_sync_timestamp", System.currentTimeMillis()).apply()
                Pair(true, "Successfully backed up all app data and files to Google Drive.")
            } else {
                Pair(false, "Failed to upload backup package to Google Drive.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error backing up all app data", e)
            Pair(false, "Backup Error: ${e.localizedMessage ?: "Unknown error"}")
        }
    }

    /**
     * Downloads and restores the entire app database and attachment files (ZIP package)
     * from the user's hidden Google Drive AppData folder.
     */
    suspend fun restoreAllAppData(
        context: Context,
        database: com.example.data.AppDatabase,
        onAuthResolutionRequired: (Intent) -> Unit = {}
    ): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val token = getAccessToken(context, onAuthResolutionRequired)
            ?: return@withContext Pair(false, "Authorization required. Please connect your Google Drive.")

        try {
            // 1. Find the file ID in Google Drive
            val fileId = findFileId(token, ALL_DATA_BACKUP_FILE_NAME)
                ?: return@withContext Pair(false, "No full app data backup found on Google Drive. Save a backup first.")

            // 2. Download zip content
            val url = "https://www.googleapis.com/drive/v3/files/$fileId?alt=media"
            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $token")
                .get()
                .build()

            val tempFile = java.io.File(context.cacheDir, "temp_app_data_restore.zip")
            if (tempFile.exists()) tempFile.delete()

            var downloadSuccess = false
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    response.body?.byteStream()?.use { input ->
                        tempFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    downloadSuccess = true
                } else {
                    Log.e(TAG, "Failed downloading zip backup: code=${response.code}")
                }
            }

            if (!downloadSuccess) {
                tempFile.delete()
                return@withContext Pair(false, "Failed to download backup package from Google Drive.")
            }

            // 3. Import data from temp zip file
            val importSuccess = tempFile.inputStream().use { fis ->
                DatabaseBackupHelper.importDataFromStream(context, database, fis)
            }

            tempFile.delete()

            if (importSuccess) {
                val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                prefs.edit().putLong("gd_all_last_sync_timestamp", System.currentTimeMillis()).apply()
                Pair(true, "Successfully restored all app data and files from Google Drive!")
            } else {
                Pair(false, "Failed to restore downloaded backup package.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error restoring all app data", e)
            Pair(false, "Restore Error: ${e.localizedMessage ?: "Unknown error"}")
        }
    }

    /**
     * Searches for 'focus_backup.json' in the AppData folder.
     * Returns its fileId or null if not found.
     */
    private fun findBackupFileId(accessToken: String): String? {
        return findFileId(accessToken, BACKUP_FILE_NAME)
    }

    /**
     * Creates empty file metadata for 'focus_backup.json' in 'appDataFolder'.
     * Returns the created fileId or null.
     */
    private fun createBackupFileMetadata(accessToken: String): String? {
        return createFileMetadata(accessToken, BACKUP_FILE_NAME)
    }

    /**
     * Generic file finder inside Google Drive appDataFolder.
     */
    private fun findFileId(accessToken: String, fileName: String): String? {
        val query = "name = '$fileName'"
        val encodedQuery = try {
            java.net.URLEncoder.encode(query, "UTF-8")
        } catch (e: Exception) {
            Log.e(TAG, "URLEncode failed", e)
            return null
        }
        val url = "https://www.googleapis.com/drive/v3/files?spaces=appDataFolder&q=$encodedQuery&fields=files(id,name)"
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $accessToken")
            .addHeader("Accept", "application/json")
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            val bodyStr = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                Log.e(TAG, "Error listing files: code=${response.code} body=$bodyStr")
                throw Exception("Google Drive API Error (HTTP ${response.code}): $bodyStr")
            }
            val filesArray = JSONObject(bodyStr).getJSONArray("files")
            if (filesArray.length() > 0) {
                return filesArray.getJSONObject(0).getString("id")
            }
        }
        return null
    }

    /**
     * Generic file metadata creator inside Google Drive appDataFolder.
     */
    private fun createFileMetadata(accessToken: String, fileName: String): String? {
        val url = "https://www.googleapis.com/drive/v3/files"
        val bodyJson = JSONObject().apply {
            put("name", fileName)
            val parentsArray = org.json.JSONArray().apply {
                put("appDataFolder")
            }
            put("parents", parentsArray)
        }
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $accessToken")
            .addHeader("Content-Type", "application/json")
            .post(bodyJson.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()

        client.newCall(request).execute().use { response ->
            val bodyStr = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                Log.e(TAG, "Error creating metadata: code=${response.code} body=$bodyStr")
                throw Exception("Google Drive Creation Error (HTTP ${response.code}): $bodyStr")
            }
            return JSONObject(bodyStr).getString("id")
        }
    }

    /**
     * Uploads/Overwrites the file content using PATCH.
     */
    private fun uploadBackupFileContent(accessToken: String, fileId: String, content: String): Boolean {
        val url = "https://www.googleapis.com/upload/drive/v3/files/$fileId?uploadType=media"
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $accessToken")
            .addHeader("Content-Type", "application/json")
            .patch(content.toRequestBody(JSON_MEDIA_TYPE))
            .build()

        client.newCall(request).execute().use { response ->
            val bodyStr = response.body?.string() ?: ""
            if (response.isSuccessful) {
                return true
            }
            Log.e(TAG, "Error uploading content: code=${response.code} body=$bodyStr")
            throw Exception("Google Drive Upload Error (HTTP ${response.code}): $bodyStr")
        }
    }

    /**
     * Downloads file content from Google Drive.
     */
    private fun downloadBackupFileContent(accessToken: String, fileId: String): String? {
        val url = "https://www.googleapis.com/drive/v3/files/$fileId?alt=media"
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $accessToken")
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            val bodyStr = response.body?.string() ?: ""
            if (response.isSuccessful) {
                return bodyStr
            }
            Log.e(TAG, "Error downloading content: code=${response.code} body=$bodyStr")
            throw Exception("Google Drive Download Error (HTTP ${response.code}): $bodyStr")
        }
    }

    /**
     * Parsed serialized string back to FocusRecord list.
     */
    private fun parseSerializedFocusRecords(serialized: String): List<com.example.ui.FocusRecord> {
        if (serialized.isBlank()) return emptyList()
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
                    
                    val durationMins = if (originalMins > 720) 720 else originalMins
                    val durationSecs = if (originalSecs > 43200) 43200 else originalSecs

                    com.example.ui.FocusRecord(parts[0], parts[1], parts[2], durationMins, dateValue, notesValue, durationSecs)
                } else null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing serialized focus records: ${e.message}")
            emptyList()
        }
    }
}
