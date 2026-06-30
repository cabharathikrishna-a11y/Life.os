package com.example.util

import android.content.Context
import android.net.Uri
import android.util.Log
import com.example.data.*
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter

object DatabaseBackupHelper {
    private const val TAG = "DatabaseBackupHelper"

    suspend fun exportData(context: Context, database: AppDatabase, uri: Uri): Boolean {
        return try {
            context.contentResolver.openOutputStream(uri)?.use { fos ->
                exportDataToStream(context, database, fos)
            } ?: false
        } catch (e: Exception) {
            Log.e(TAG, "Failed to export data to Uri", e)
            false
        }
    }

    suspend fun exportDataToStream(context: Context, database: AppDatabase, outputStream: java.io.OutputStream): Boolean {
        return try {
            val root = JSONObject()
            root.put("version", 5) // current schema version
            root.put("files_dir_path_placeholder", com.example.util.StorageHelper.getAppFilesDir(context).absolutePath)

            // 1. SharedPreferences backup
            val prefs = context.applicationContext.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            val settingsJson = JSONObject()
            val allPrefs = prefs.all
            allPrefs.forEach { (key, value) ->
                when (value) {
                    is Boolean -> settingsJson.put(key, value)
                    is Int -> settingsJson.put(key, value)
                    is Long -> settingsJson.put(key, value)
                    is Float -> settingsJson.put(key, value.toDouble())
                    is String -> settingsJson.put(key, value)
                }
            }
            root.put("shared_preferences", settingsJson)

            // 2. Tasks
            val tasks = database.taskDao().getAllTasks().first()
            val tasksArray = JSONArray()
            tasks.forEach {
                val obj = JSONObject()
                obj.put("title", it.title)
                obj.put("description", it.description)
                obj.put("estimatedMinutes", it.estimatedMinutes)
                obj.put("actualMinutes", it.actualMinutes)
                obj.put("isCompleted", it.isCompleted)
                obj.put("parentTaskId", it.parentTaskId ?: -1)
                obj.put("listCategory", it.listCategory)
                obj.put("timeBlockTimestamp", it.timeBlockTimestamp ?: -1L)
                obj.put("nagModeEnabled", it.nagModeEnabled)
                obj.put("nagIntervalMinutes", it.nagIntervalMinutes)
                obj.put("priority", it.priority)
                obj.put("dueDateString", it.dueDateString)
                obj.put("orderIndex", it.orderIndex)
                tasksArray.put(obj)
            }
            root.put("tasks", tasksArray)

            // 3. Habits
            val habits = database.habitDao().getAllHabits().first()
            val habitsArray = JSONArray()
            habits.forEach {
                val obj = JSONObject()
                obj.put("id", it.id) // keep id so completions match if possible
                obj.put("name", it.name)
                obj.put("streakCount", it.streakCount)
                obj.put("lastCompletedTimestamp", it.lastCompletedTimestamp ?: -1L)
                habitsArray.put(obj)
            }
            root.put("habits", habitsArray)

            // 4. Habit Completions
            val completions = database.habitDao().getAllCompletions().first()
            val completionsArray = JSONArray()
            completions.forEach {
                val obj = JSONObject()
                obj.put("habitId", it.habitId)
                obj.put("dateString", it.dateString)
                completionsArray.put(obj)
            }
            root.put("habit_completions", completionsArray)

            // 5. Journal Entries
            val journal = database.journalDao().getAllJournalEntries().first()
            val journalArray = JSONArray()
            journal.forEach {
                val obj = JSONObject()
                obj.put("title", it.title)
                obj.put("text", it.text)
                obj.put("dateString", it.dateString)
                obj.put("timestamp", it.timestamp)
                obj.put("attachmentsJson", it.attachmentsJson)
                journalArray.put(obj)
            }
            root.put("journal_entries", journalArray)

            // 6. Ledger Entries
            val ledger = database.ledgerDao().getAllLedgerEntries().first()
            val ledgerArray = JSONArray()
            ledger.forEach {
                val obj = JSONObject()
                obj.put("type", it.type)
                obj.put("amount", it.amount)
                obj.put("categoryTag", it.categoryTag)
                obj.put("note", it.note)
                obj.put("timestamp", it.timestamp)
                ledgerArray.put(obj)
            }
            root.put("ledger_entries", ledgerArray)

            // 7. Deadlines
            val deadlines = database.deadlineDao().getAllDeadlines().first()
            val deadlinesArray = JSONArray()
            deadlines.forEach {
                val obj = JSONObject()
                obj.put("name", it.name)
                obj.put("targetTimestamp", it.targetTimestamp)
                obj.put("isCompleted", it.isCompleted)
                deadlinesArray.put(obj)
            }
            root.put("deadlines", deadlinesArray)

            // 8. Financial Goals
            val fg = database.financialGoalDao().getAllFinancialGoals().first()
            val fgArray = JSONArray()
            fg.forEach {
                val obj = JSONObject()
                obj.put("name", it.name)
                obj.put("targetAmount", it.targetAmount)
                obj.put("type", it.type)
                obj.put("categoryTag", it.categoryTag)
                fgArray.put(obj)
            }
            root.put("financial_goals", fgArray)

            // 9. Contacts
            val contacts = database.contactDao().getAllContacts().first()
            val contactsArray = JSONArray()
            contacts.forEach {
                val obj = JSONObject()
                obj.put("firstName", it.firstName)
                obj.put("middleName", it.middleName)
                obj.put("lastName", it.lastName)
                obj.put("jobTitle", it.jobTitle)
                obj.put("email", it.email)
                obj.put("address", it.address)
                obj.put("phone", it.phone)
                obj.put("dobString", it.dobString)
                obj.put("photoUri", it.photoUri ?: "")
                obj.put("anniversaryString", it.anniversaryString)
                obj.put("additionalFieldsJson", it.additionalFieldsJson)
                obj.put("additionalDatesJson", it.additionalDatesJson)
                obj.put("folder", it.folder)
                obj.put("attachedFilesJson", it.attachedFilesJson)
                contactsArray.put(obj)
            }
            root.put("contacts", contactsArray)

            // 10. App Files
            val files = database.appFileDao().getAllFiles().first()
            val filesArray = JSONArray()
            files.forEach {
                val obj = JSONObject()
                obj.put("name", it.name)
                obj.put("path", it.path)
                obj.put("size", it.size)
                obj.put("mimeType", it.mimeType)
                obj.put("uriString", it.uriString)
                obj.put("timestamp", it.timestamp)
                filesArray.put(obj)
            }
            root.put("app_files", filesArray)

            // 11. Custom Lists
            val lists = database.customListDao().getAllLists().first()
            val listsArray = JSONArray()
            lists.forEach {
                val obj = JSONObject()
                obj.put("name", it.name)
                obj.put("colorHex", it.colorHex)
                obj.put("viewType", it.viewType)
                obj.put("parentListName", it.parentListName ?: "")
                listsArray.put(obj)
            }
            root.put("custom_lists", listsArray)

            // 12. Family Members
            val members = database.familyMemberDao().getAllMembers().first()
            val membersArray = JSONArray()
            members.forEach {
                val obj = JSONObject()
                obj.put("id", it.id)
                obj.put("name", it.name)
                membersArray.put(obj)
            }
            root.put("family_members", membersArray)

            // 13. Financial Accounts
            val accounts = database.financialAccountDao().getAllAccounts().first()
            val accountsArray = JSONArray()
            accounts.forEach {
                val obj = JSONObject()
                obj.put("id", it.id)
                obj.put("memberId", it.memberId)
                obj.put("name", it.name)
                obj.put("categoryType", it.categoryType)
                obj.put("openingValue", it.openingValue)
                accountsArray.put(obj)
            }
            root.put("financial_accounts", accountsArray)

            // 14. Financial Logs
            val logs = database.financialLogDao().getAllLogs().first()
            val logsArray = JSONArray()
            logs.forEach {
                val obj = JSONObject()
                obj.put("id", it.id)
                obj.put("accountId", it.accountId)
                obj.put("logType", it.logType)
                obj.put("amount", it.amount)
                obj.put("timestamp", it.timestamp)
                logsArray.put(obj)
            }
            root.put("financial_logs", logsArray)

            // 15. Finance Transactions
            val transactions = database.financeTransactionDao().getAllTransactions().first()
            val transactionsArray = JSONArray()
            transactions.forEach {
                val obj = JSONObject()
                obj.put("id", it.id)
                obj.put("memberId", it.memberId)
                obj.put("type", it.type)
                obj.put("fromAccountId", it.fromAccountId ?: -1)
                obj.put("fromCategory", it.fromCategory ?: "")
                obj.put("toAccountId", it.toAccountId ?: -1)
                obj.put("toCategory", it.toCategory ?: "")
                obj.put("amount", it.amount)
                obj.put("timestamp", it.timestamp)
                obj.put("note", it.note)
                transactionsArray.put(obj)
            }
            root.put("finance_transactions", transactionsArray)

            // 16. Finance Categories
            val categories = database.financeCategoryDao().getAllCategories().first()
            val categoriesArray = JSONArray()
            categories.forEach {
                val obj = JSONObject()
                obj.put("id", it.id)
                obj.put("name", it.name)
                obj.put("type", it.type)
                categoriesArray.put(obj)
            }
            root.put("finance_categories", categoriesArray)

            val jsonString = root.toString()

            // Calculate stats for backup_summary.txt
            val filesDir = com.example.util.StorageHelper.getAppFilesDir(context)
            val filesList = filesDir.listFiles() ?: emptyArray()

            var imageCount = 0
            var videoCount = 0
            var otherCount = 0

            filesList.forEach { file ->
                if (file.isFile) {
                    val nameLower = file.name.lowercase()
                    if (nameLower.endsWith(".jpg") || nameLower.endsWith(".jpeg") || nameLower.endsWith(".png") || nameLower.endsWith(".webp")) {
                        imageCount++
                    } else if (nameLower.endsWith(".mp4") || nameLower.endsWith(".3gp") || nameLower.endsWith(".mkv") || nameLower.endsWith(".webm") || nameLower.endsWith(".avi")) {
                        videoCount++
                    } else {
                        if (file.name != "backup_summary.txt" && file.name != "backup_data.json") {
                            otherCount++
                        }
                    }
                }
            }

            val journalsCount = journal.size
            val tasksCount = tasks.size
            val habitsCount = habits.size
            val ledgerCount = ledger.size

            val serializedFocus = prefs.getString("focus_records_list", null) ?: ""
            val focusRecordsLines = if (serializedFocus.isBlank()) emptyList() else serializedFocus.split("\n")
            val focusRecordsCount = focusRecordsLines.filter { it.isNotBlank() }.size
            var totalFocusMins = 0
            focusRecordsLines.forEach { line ->
                if (line.isNotBlank()) {
                    val parts = line.split("|")
                    if (parts.size >= 4) {
                        val mins = parts[3].toIntOrNull() ?: 0
                        totalFocusMins += mins
                    }
                }
            }

            val summaryText = """
                --- BACKUP SUMMARY MANIFEST ---
                Images Count: $imageCount
                Videos Count: $videoCount
                Other Files Count: $otherCount
                Journals Count: $journalsCount
                Tasks Count: $tasksCount
                Habits Count: $habitsCount
                History (Ledger) Entries Count: $ledgerCount
                Focused Session Record Count: $focusRecordsCount
                Total Focused Session Duration (minutes): $totalFocusMins
                -------------------------------
            """.trimIndent()
            
            // Export inside a UNIFIED zip archive
            java.util.zip.ZipOutputStream(outputStream).use { zipOut ->
                // 1. Write the backup JSON database dump
                val jsonEntry = java.util.zip.ZipEntry("backup_data.json")
                zipOut.putNextEntry(jsonEntry)
                val jsonWriter = java.io.BufferedWriter(java.io.OutputStreamWriter(zipOut, Charsets.UTF_8))
                jsonWriter.write(jsonString)
                jsonWriter.flush()
                zipOut.closeEntry()

                // 1b. Write the backup summary manifest
                val summaryEntry = java.util.zip.ZipEntry("backup_summary.txt")
                zipOut.putNextEntry(summaryEntry)
                val summaryWriter = java.io.BufferedWriter(java.io.OutputStreamWriter(zipOut, Charsets.UTF_8))
                summaryWriter.write(summaryText)
                summaryWriter.flush()
                zipOut.closeEntry()

                // 2. Write all physical files (journal photos, recordings, local files)
                filesList.forEach { file ->
                    if (file.isFile) {
                        val entryName = "media/${file.name}"
                        val fileEntry = java.util.zip.ZipEntry(entryName)
                        zipOut.putNextEntry(fileEntry)
                        file.inputStream().use { input ->
                            FileChunkHelper.copyStreamSecure(input, zipOut, bufferSize = 8192)
                        }
                        zipOut.closeEntry()
                    }
                }
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to export data", e)
            false
        }
    }

    suspend fun importData(context: Context, database: AppDatabase, uri: Uri): Boolean {
        return try {
            context.contentResolver.openInputStream(uri)?.use { rawIn ->
                importDataFromStream(context, database, rawIn)
            } ?: false
        } catch (e: Exception) {
            Log.e(TAG, "Failed to import data from Uri", e)
            false
        }
    }

    suspend fun importDataFromStream(context: Context, database: AppDatabase, rawIn: java.io.InputStream): Boolean {
        return try {
            val filesDir = com.example.util.StorageHelper.getAppFilesDir(context)
            var backupSummaryString: String? = null
            var tempJsonFile: java.io.File? = null

            val bufferedIn = java.io.BufferedInputStream(rawIn)
            bufferedIn.mark(4)
            val header = ByteArray(4)
            val read = bufferedIn.read(header)
            if (read == -1) return false
            bufferedIn.reset()

            val isZip = read == 4 &&
                    header[0] == 0x50.toByte() &&
                    header[1] == 0x4B.toByte() &&
                    header[2] == 0x03.toByte() &&
                    header[3] == 0x04.toByte()

            if (!isZip) {
                val contentStr = bufferedIn.bufferedReader(Charsets.UTF_8).readText()
                return parseAndRestoreDb(context, database, contentStr)
            } else {
                val zipIn = java.util.zip.ZipInputStream(bufferedIn)
                var entry = zipIn.nextEntry
                while (entry != null) {
                    if (entry.name == "backup_summary.txt") {
                        val bos = java.io.ByteArrayOutputStream()
                        FileChunkHelper.copyStreamSecure(zipIn, bos, bufferSize = 8192)
                        backupSummaryString = bos.toString("UTF-8")
                    } else if (entry.name == "backup_data.json") {
                        val tempFile = java.io.File(context.cacheDir, "temp_backup_data.json")
                        tempFile.outputStream().use { output ->
                            FileChunkHelper.copyStreamSecure(zipIn, output, bufferSize = 8192)
                        }
                        tempJsonFile = tempFile
                    } else if (entry.name.startsWith("media/")) {
                        val fileName = entry.name.substringAfter("media/")
                        if (fileName.isNotEmpty()) {
                            val destFile = java.io.File(filesDir, fileName)
                            destFile.parentFile?.mkdirs()
                            destFile.outputStream().use { output ->
                                FileChunkHelper.copyStreamSecure(zipIn, output, bufferSize = 8192)
                            }
                        }
                    }
                    zipIn.closeEntry()
                    entry = zipIn.nextEntry
                }
                zipIn.close()

                if (tempJsonFile != null && tempJsonFile.exists()) {
                    val jsonContent = tempJsonFile.readText(Charsets.UTF_8)
                    val restoreResult = parseAndRestoreDb(context, database, jsonContent)
                    tempJsonFile.delete() // Clean up disk cache immediately
                    
                    if (restoreResult) {
                        if (backupSummaryString != null) {
                            verifyImportCounts(context, database, backupSummaryString)
                        }
                        return true
                    }
                }
            }
            false
        } catch (e: Exception) {
            Log.e(TAG, "Failed to import data from Stream", e)
            false
        }
    }

    private suspend fun parseAndRestoreDb(context: Context, database: AppDatabase, contentStr: String): Boolean {
        return try {
            val rootObj = JSONObject(contentStr)
            val originalFilesDir = rootObj.optString("files_dir_path_placeholder", "")
            val currentFilesDir = com.example.util.StorageHelper.getAppFilesDir(context).absolutePath

            // Perform automatic filesystem translation to match new installation details seamlessly
            var finalContentStr = contentStr
            if (originalFilesDir.isNotEmpty() && originalFilesDir != currentFilesDir) {
                finalContentStr = finalContentStr.replace(originalFilesDir, currentFilesDir)
            }

            val root = JSONObject(finalContentStr)

            // Transact-clear existing databases & tables to prevent ID collisions, clones or leaks
            database.runInTransaction {
                database.clearAllTables()
            }

            // Restore SharedPreferences if present
            val settingsJson = root.optJSONObject("shared_preferences")
            if (settingsJson != null) {
                val prefs = context.applicationContext.getSharedPreferences("app_prefs", Context.MODE_PRIVATE).edit()
                prefs.clear() // Remove local keys to overwrite with backup
                val keys = settingsJson.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val value = settingsJson.get(key)
                    when (value) {
                        is Boolean -> prefs.putBoolean(key, value)
                        is Int -> prefs.putInt(key, value)
                        is Long -> prefs.putLong(key, value)
                        is Double -> prefs.putFloat(key, value.toFloat())
                        is String -> prefs.putString(key, value)
                    }
                }
                prefs.apply()
            }

            // 1. Tasks
            val tasksArray = root.optJSONArray("tasks")
            if (tasksArray != null) {
                for (i in 0 until tasksArray.length()) {
                    val obj = tasksArray.getJSONObject(i)
                    val parentIdVal = obj.optInt("parentTaskId", -1)
                    val parentTaskId: Int? = if (parentIdVal == -1) null else parentIdVal
                    
                    val blockTimeVal = obj.optLong("timeBlockTimestamp", -1L)
                    val timeBlockTimestamp: Long? = if (blockTimeVal == -1L) null else blockTimeVal

                    val task = Task(
                        title = obj.optString("title", "Untitled Task"),
                        description = obj.optString("description", ""),
                        estimatedMinutes = obj.optInt("estimatedMinutes", 30),
                        actualMinutes = obj.optInt("actualMinutes", 0),
                        isCompleted = obj.optBoolean("isCompleted", false),
                        parentTaskId = parentTaskId,
                        listCategory = obj.optString("listCategory", "Inbox"),
                        timeBlockTimestamp = timeBlockTimestamp,
                        nagModeEnabled = obj.optBoolean("nagModeEnabled", false),
                        nagIntervalMinutes = obj.optInt("nagIntervalMinutes", 5),
                        priority = obj.optString("priority", "MEDIUM"),
                        dueDateString = obj.optString("dueDateString", ""),
                        orderIndex = obj.optInt("orderIndex", 0)
                    )
                    database.taskDao().insertTask(task)
                }
            }

            // 2. Habits
            val habitsArray = root.optJSONArray("habits")
            val idMapping = mutableMapOf<Int, Int>() // maps old habit id to newly generated habit id
            if (habitsArray != null) {
                for (i in 0 until habitsArray.length()) {
                    val obj = habitsArray.getJSONObject(i)
                    val oldId = obj.optInt("id", -1)
                    val lastCompletedVal = obj.optLong("lastCompletedTimestamp", -1L)
                    val lastCompletedTimestamp: Long? = if (lastCompletedVal == -1L) null else lastCompletedVal

                    val habit = Habit(
                        name = obj.optString("name", "Untitled Habit"),
                        streakCount = obj.optInt("streakCount", 0),
                        lastCompletedTimestamp = lastCompletedTimestamp
                    )
                    val newId = database.habitDao().insertHabit(habit).toInt()
                    if (oldId != -1) {
                        idMapping[oldId] = newId
                    }
                }
            }

            // 3. Habit Completions
            val completionsArray = root.optJSONArray("habit_completions")
            if (completionsArray != null) {
                for (i in 0 until completionsArray.length()) {
                    val obj = completionsArray.getJSONObject(i)
                    val oldHabitId = obj.optInt("habitId", -1)
                    val newHabitId = idMapping[oldHabitId] ?: oldHabitId
                    if (newHabitId != -1) {
                        val completion = HabitCompletion(
                            habitId = newHabitId,
                            dateString = obj.optString("dateString", "")
                        )
                        database.habitDao().insertCompletion(completion)
                    }
                }
            }

            // 4. Journal Entries
            val journalArray = root.optJSONArray("journal_entries")
            if (journalArray != null) {
                for (i in 0 until journalArray.length()) {
                    val obj = journalArray.getJSONObject(i)
                    val entry = JournalEntry(
                        title = obj.optString("title", "Untitled Entry"),
                        text = obj.optString("text", ""),
                        dateString = obj.optString("dateString", ""),
                        timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                        attachmentsJson = obj.optString("attachmentsJson", "")
                    )
                    database.journalDao().insertJournalEntry(entry)
                }
            }

            // 5. Ledger Entries
            val ledgerArray = root.optJSONArray("ledger_entries")
            if (ledgerArray != null) {
                for (i in 0 until ledgerArray.length()) {
                    val obj = ledgerArray.getJSONObject(i)
                    val entry = LedgerEntry(
                        type = obj.optString("type", "EXPENSE"),
                        amount = obj.optDouble("amount", 0.0),
                        categoryTag = obj.optString("categoryTag", "General"),
                        note = obj.optString("note", ""),
                        timestamp = obj.optLong("timestamp", System.currentTimeMillis())
                    )
                    database.ledgerDao().insertLedgerEntry(entry)
                }
            }

            // 6. Deadlines
            val deadlinesArray = root.optJSONArray("deadlines")
            if (deadlinesArray != null) {
                for (i in 0 until deadlinesArray.length()) {
                    val obj = deadlinesArray.getJSONObject(i)
                    val deadline = Deadline(
                        name = obj.optString("name", "Untitled Deadline"),
                        targetTimestamp = obj.optLong("targetTimestamp", System.currentTimeMillis()),
                        isCompleted = obj.optBoolean("isCompleted", false)
                    )
                    database.deadlineDao().insertDeadline(deadline)
                }
            }

            // 7. Financial Goals
            val fgArray = root.optJSONArray("financial_goals")
            if (fgArray != null) {
                for (i in 0 until fgArray.length()) {
                    val obj = fgArray.getJSONObject(i)
                    val goal = FinancialGoal(
                        name = obj.optString("name", "Untitled Goal"),
                        targetAmount = obj.optDouble("targetAmount", 0.0),
                        type = obj.optString("type", "SAVINGS"),
                        categoryTag = obj.optString("categoryTag", "General")
                    )
                    database.financialGoalDao().insertFinancialGoal(goal)
                }
            }

            // 8. Contacts
            val contactsArray = root.optJSONArray("contacts")
            if (contactsArray != null) {
                for (i in 0 until contactsArray.length()) {
                    val obj = contactsArray.getJSONObject(i)
                    val photoUriVal = obj.optString("photoUri", "")
                    val photoUri = photoUriVal.ifEmpty { null }
                    
                    val contact = Contact(
                        firstName = obj.optString("firstName", ""),
                        middleName = obj.optString("middleName", ""),
                        lastName = obj.optString("lastName", ""),
                        jobTitle = obj.optString("jobTitle", ""),
                        email = obj.optString("email", ""),
                        address = obj.optString("address", ""),
                        phone = obj.optString("phone", ""),
                        dobString = obj.optString("dobString", ""),
                        photoUri = photoUri,
                        anniversaryString = obj.optString("anniversaryString", ""),
                        additionalFieldsJson = obj.optString("additionalFieldsJson", ""),
                        additionalDatesJson = obj.optString("additionalDatesJson", ""),
                        folder = obj.optString("folder", "All"),
                        attachedFilesJson = obj.optString("attachedFilesJson", "")
                    )
                    database.contactDao().insertContact(contact)
                }
            }

            // 9. App Files
            val filesArray = root.optJSONArray("app_files")
            if (filesArray != null) {
                for (i in 0 until filesArray.length()) {
                    val obj = filesArray.getJSONObject(i)
                    val file = AppFile(
                        name = obj.optString("name", ""),
                        path = obj.optString("path", ""),
                        size = obj.optLong("size", 0L),
                        mimeType = obj.optString("mimeType", ""),
                        uriString = obj.optString("uriString", ""),
                        timestamp = obj.optLong("timestamp", System.currentTimeMillis())
                    )
                    database.appFileDao().insertFile(file)
                }
            }

            // 11. Custom Lists
            val listsArray = root.optJSONArray("custom_lists")
            if (listsArray != null) {
                for (i in 0 until listsArray.length()) {
                    val obj = listsArray.getJSONObject(i)
                    val parentVal = obj.optString("parentListName", "")
                    val parentListName = parentVal.ifEmpty { null }

                    val customList = CustomList(
                        name = obj.optString("name", "Inbox"),
                        colorHex = obj.optString("colorHex", "#2196F3"),
                        viewType = obj.optString("viewType", "List"),
                        parentListName = parentListName
                    )
                    database.customListDao().insertList(customList)
                }
            }

            // 12. Family Members
            val membersArray = root.optJSONArray("family_members")
            if (membersArray != null) {
                for (i in 0 until membersArray.length()) {
                    val obj = membersArray.getJSONObject(i)
                    val mem = FamilyMember(
                        id = obj.optInt("id"),
                        name = obj.optString("name", "Unknown")
                    )
                    database.familyMemberDao().insertMember(mem)
                }
            }

            // 13. Financial Accounts
            val accountsArray = root.optJSONArray("financial_accounts")
            if (accountsArray != null) {
                for (i in 0 until accountsArray.length()) {
                    val obj = accountsArray.getJSONObject(i)
                    val acc = FinancialAccount(
                        id = obj.optInt("id"),
                        memberId = obj.optInt("memberId"),
                        name = obj.optString("name", "Account"),
                        categoryType = obj.optString("categoryType", "CURRENT_ASSETS"),
                        openingValue = obj.optDouble("openingValue", 0.0)
                    )
                    database.financialAccountDao().insertAccount(acc)
                }
            }

            // 14. Financial Logs
            val logsArray = root.optJSONArray("financial_logs")
            if (logsArray != null) {
                for (i in 0 until logsArray.length()) {
                    val obj = logsArray.getJSONObject(i)
                    val logEntry = FinancialLog(
                        id = obj.optInt("id"),
                        accountId = obj.optInt("accountId"),
                        logType = obj.optString("logType", "INITIAL"),
                        amount = obj.optDouble("amount", 0.0),
                        timestamp = obj.optLong("timestamp", System.currentTimeMillis())
                    )
                    database.financialLogDao().insertLog(logEntry)
                }
            }

            // 15. Finance Transactions
            val transactionsArray = root.optJSONArray("finance_transactions")
            if (transactionsArray != null) {
                for (i in 0 until transactionsArray.length()) {
                    val obj = transactionsArray.getJSONObject(i)
                    val fromAccIdVal = obj.optInt("fromAccountId", -1)
                    val fromAccountId: Int? = if (fromAccIdVal == -1) null else fromAccIdVal
                    val fromCategoryVal = obj.optString("fromCategory", "")
                    val fromCategory: String? = if (fromCategoryVal.isEmpty()) null else fromCategoryVal
                    val toAccIdVal = obj.optInt("toAccountId", -1)
                    val toAccountId: Int? = if (toAccIdVal == -1) null else toAccIdVal
                    val toCategoryVal = obj.optString("toCategory", "")
                    val toCategory: String? = if (toCategoryVal.isEmpty()) null else toCategoryVal

                    val tx = FinanceTransaction(
                        id = obj.optInt("id"),
                        memberId = obj.optInt("memberId"),
                        type = obj.optString("type", "EXPENSE"),
                        fromAccountId = fromAccountId,
                        fromCategory = fromCategory,
                        toAccountId = toAccountId,
                        toCategory = toCategory,
                        amount = obj.optDouble("amount", 0.0),
                        timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                        note = obj.optString("note", "")
                    )
                    database.financeTransactionDao().insertTransaction(tx)
                }
            }

            // 16. Finance Categories
            val categoriesArray = root.optJSONArray("finance_categories")
            if (categoriesArray != null) {
                for (i in 0 until categoriesArray.length()) {
                    val obj = categoriesArray.getJSONObject(i)
                    val cat = FinanceCategory(
                        id = obj.optInt("id"),
                        name = obj.optString("name", ""),
                        type = obj.optString("type", "EXPENSE")
                    )
                    database.financeCategoryDao().insertCategory(cat)
                }
            }

            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed parseAndRestoreDb", e)
            false
        }
    }

    private suspend fun verifyImportCounts(context: Context, database: AppDatabase, summaryStr: String): Boolean {
        return try {
            Log.d(TAG, "Verifying imported database counts against backup_summary.txt...")
            
            var expectedImages = -1
            var expectedVideos = -1
            var expectedOtherFiles = -1
            var expectedJournals = -1
            var expectedTasks = -1
            var expectedHabits = -1
            var expectedLedgerCount = -1
            var expectedFocusRecords = -1
            var expectedFocusMinutes = -1

            summaryStr.split("\n").forEach { line ->
                val trimmed = line.trim()
                when {
                    trimmed.startsWith("Images Count:") -> expectedImages = trimmed.substringAfter("Images Count:").trim().toIntOrNull() ?: -1
                    trimmed.startsWith("Videos Count:") -> expectedVideos = trimmed.substringAfter("Videos Count:").trim().toIntOrNull() ?: -1
                    trimmed.startsWith("Other Files Count:") -> expectedOtherFiles = trimmed.substringAfter("Other Files Count:").trim().toIntOrNull() ?: -1
                    trimmed.startsWith("Journals Count:") -> expectedJournals = trimmed.substringAfter("Journals Count:").trim().toIntOrNull() ?: -1
                    trimmed.startsWith("Tasks Count:") -> expectedTasks = trimmed.substringAfter("Tasks Count:").trim().toIntOrNull() ?: -1
                    trimmed.startsWith("Habits Count:") -> expectedHabits = trimmed.substringAfter("Habits Count:").trim().toIntOrNull() ?: -1
                    trimmed.startsWith("History (Ledger) Entries Count:") -> expectedLedgerCount = trimmed.substringAfter("History (Ledger) Entries Count:").trim().toIntOrNull() ?: -1
                    trimmed.startsWith("Focused Session Record Count:") -> expectedFocusRecords = trimmed.substringAfter("Focused Session Record Count:").trim().toIntOrNull() ?: -1
                    trimmed.startsWith("Total Focused Session Duration (minutes):") -> expectedFocusMinutes = trimmed.substringAfter("Total Focused Session Duration (minutes):").trim().toIntOrNull() ?: -1
                }
            }

            // Count actuals in filesystem
            val filesDir = com.example.util.StorageHelper.getAppFilesDir(context)
            val filesList = filesDir.listFiles() ?: emptyArray()
            
            var actualImages = 0
            var actualVideos = 0
            var actualOtherFiles = 0
            
            filesList.forEach { file ->
                if (file.isFile) {
                    val nameLower = file.name.lowercase()
                    if (nameLower.endsWith(".jpg") || nameLower.endsWith(".jpeg") || nameLower.endsWith(".png") || nameLower.endsWith(".webp")) {
                        actualImages++
                    } else if (nameLower.endsWith(".mp4") || nameLower.endsWith(".3gp") || nameLower.endsWith(".mkv") || nameLower.endsWith(".webm") || nameLower.endsWith(".avi")) {
                        actualVideos++
                    } else {
                        if (file.name != "backup_summary.txt" && file.name != "backup_data.json") {
                            actualOtherFiles++
                        }
                    }
                }
            }

            // Count actuals in database
            val actualJournals = database.journalDao().getAllJournalEntries().first().size
            val actualTasks = database.taskDao().getAllTasks().first().size
            val actualHabits = database.habitDao().getAllHabits().first().size
            val actualLedger = database.ledgerDao().getAllLedgerEntries().first().size

            // Count focus from new shared preferences
            val prefs = context.applicationContext.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            val serializedFocus = prefs.getString("focus_records_list", null) ?: ""
            val focusRecordsLines = if (serializedFocus.isBlank()) emptyList() else serializedFocus.split("\n")
            val actualFocusRecords = focusRecordsLines.filter { it.isNotBlank() }.size
            var actualFocusMins = 0
            focusRecordsLines.forEach { line ->
                if (line.isNotBlank()) {
                    val parts = line.split("|")
                    if (parts.size >= 4) {
                        val mins = parts[3].toIntOrNull() ?: 0
                        actualFocusMins += mins
                    }
                }
            }

            Log.d(TAG, "VERIFICATION COMPARISON RESULTS:")
            Log.d(TAG, "Images: Expected: $expectedImages, Actual: $actualImages")
            Log.d(TAG, "Videos: Expected: $expectedVideos, Actual: $actualVideos")
            Log.d(TAG, "Other Files: Expected: $expectedOtherFiles, Actual: $actualOtherFiles")
            Log.d(TAG, "Journals: Expected: $expectedJournals, Actual: $actualJournals")
            Log.d(TAG, "Tasks: Expected: $expectedTasks, Actual: $actualTasks")
            Log.d(TAG, "Habits: Expected: $expectedHabits, Actual: $actualHabits")
            Log.d(TAG, "Ledger Entries: Expected: $expectedLedgerCount, Actual: $actualLedger")
            Log.d(TAG, "Focus Records: Expected: $expectedFocusRecords, Actual: $actualFocusRecords")
            Log.d(TAG, "Focus Minutes: Expected: $expectedFocusMinutes, Actual: $actualFocusMins")

            var match = true
            if (expectedImages != -1 && expectedImages != actualImages) match = false
            if (expectedVideos != -1 && expectedVideos != actualVideos) match = false
            if (expectedOtherFiles != -1 && expectedOtherFiles != actualOtherFiles) match = false
            if (expectedJournals != -1 && expectedJournals != actualJournals) match = false
            if (expectedTasks != -1 && expectedTasks != actualTasks) match = false
            if (expectedHabits != -1 && expectedHabits != actualHabits) match = false
            if (expectedLedgerCount != -1 && expectedLedgerCount != actualLedger) match = false
            if (expectedFocusRecords != -1 && expectedFocusRecords != actualFocusRecords) match = false
            if (expectedFocusMinutes != -1 && expectedFocusMinutes != actualFocusMins) match = false

            if (!match) {
                Log.w(TAG, "Import completed but counts did not match perfectly!")
            } else {
                Log.d(TAG, "Import counts matched database statistics perfectly!")
            }
            match
        } catch (e: Exception) {
            Log.e(TAG, "Error in verifyImportCounts", e)
            false
        }
    }

    fun getBackupLocations(context: Context): List<java.io.File> {
        val locations = mutableListOf<java.io.File>()
        
        // 1. Standard public Downloads & Documents folders on primary shared storage
        val primaryDownload = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
        val primaryDocument = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOCUMENTS)
        
        if (primaryDownload != null) {
            locations.add(java.io.File(primaryDownload, "LifeOS_Backup"))
        }
        if (primaryDocument != null) {
            locations.add(java.io.File(primaryDocument, "LifeOS_Backup"))
        }
        
        // Add generic path fallbacks
        locations.add(java.io.File("/sdcard/Download/LifeOS_Backup"))
        locations.add(java.io.File("/sdcard/Documents/LifeOS_Backup"))
        locations.add(java.io.File("/storage/emulated/0/Download/LifeOS_Backup"))
        locations.add(java.io.File("/storage/emulated/0/Documents/LifeOS_Backup"))
        
        // 2. Secondary external storages (connected SD cards, USB OTG, etc.) from system /storage/
        try {
            val storageDir = java.io.File("/storage")
            if (storageDir.exists() && storageDir.isDirectory) {
                val volumes = storageDir.listFiles()
                if (volumes != null) {
                    for (volume in volumes) {
                        try {
                            if (volume.isDirectory && volume.canRead()) {
                                val name = volume.name
                                if (name != "self" && name != "emulated") {
                                    // This is likely an external SD card or USB drive!
                                    locations.add(java.io.File(volume, "Download/LifeOS_Backup"))
                                    locations.add(java.io.File(volume, "Documents/LifeOS_Backup"))
                                    locations.add(java.io.File(volume, "LifeOS_Backup"))
                                }
                            }
                        } catch (ex: Exception) {
                            // Suppress per-volume permission errors safely
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error scanning external storage volumes", e)
        }

        // Context external files dirs
        try {
            context.getExternalFilesDirs(null).forEach { dir ->
                if (dir != null) {
                    locations.add(java.io.File(dir, "LifeOS_Backup"))
                }
            }
        } catch (ex: Exception) {
            // Ignored
        }

        // Return unique existing/creatable directories
        return locations.distinct()
    }

    suspend fun autoBackup(context: Context, database: AppDatabase): Boolean {
        var success = false
        val locations = getBackupLocations(context)
        for (dir in locations) {
            try {
                if (!dir.exists()) {
                    dir.mkdirs()
                }
                if (dir.exists() && dir.canWrite()) {
                    val backupFile = java.io.File(dir, "lifeos_backup.zip")
                    val tempFile = java.io.File(context.cacheDir, "lifeos_auto_backup_temp.zip")
                    if (tempFile.exists()) tempFile.delete()
                    
                    val tempUri = Uri.fromFile(tempFile)
                    val exported = exportData(context, database, tempUri)
                    if (exported && tempFile.exists() && tempFile.length() > 0) {
                        tempFile.copyTo(backupFile, overwrite = true)
                        Log.d(TAG, "Auto-backup succeeded to: ${backupFile.absolutePath}")
                        success = true
                    }
                    if (tempFile.exists()) tempFile.delete()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Auto-backup failed to directory: ${dir.absolutePath}", e)
            }
        }
        return success
    }

    suspend fun autoRestoreIfNeeded(context: Context, database: AppDatabase): Boolean {
        val prefs = context.applicationContext.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val previouslyInitialized = prefs.getBoolean("previously_initialized", false)
        if (previouslyInitialized) {
            // App was already initialized and run before. No auto-restore needed.
            return false
        }

        // Set preference immediately so we don't end up in an infinite auto-restore loop
        prefs.edit().putBoolean("previously_initialized", true).apply()

        // Now, scan all potential backup locations for lifeos_backup.zip
        val locations = getBackupLocations(context)
        for (dir in locations) {
            val backupFile = java.io.File(dir, "lifeos_backup.zip")
            if (backupFile.exists() && backupFile.isFile) {
                try {
                    Log.d(TAG, "Found previously exported backup at: ${backupFile.absolutePath}. Attempting auto-restore.")
                    val uri = Uri.fromFile(backupFile)
                    val imported = importData(context, database, uri)
                    if (imported) {
                        Log.d(TAG, "Auto-restore successful from: ${backupFile.absolutePath}")
                        return true
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Auto-restore failed from path: ${backupFile.absolutePath}", e)
                }
            }
        }
        return false
    }
}
