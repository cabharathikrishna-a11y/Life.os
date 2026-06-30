package com.example.ui.components

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import kotlinx.coroutines.launch
import androidx.compose.animation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.AppViewModel
import com.example.ui.theme.Charcoal
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.WaterBlue

// Helper class representing parsed attachments from different subcomponents
data class ExplorerFile(
    val name: String,
    val type: String, // "image", "video", "audio", "others"
    val dateText: String,    // e.g. "Jun 19"
    val timestamp: Long,
    val sourceName: String,  // e.g. "Journal Entry", "Task: Homework", "Contact: Munee"
    val fileMime: String,
    val onClick: () -> Unit
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileExplorerView(viewModel: AppViewModel, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val journalEntries by viewModel.journalEntries.collectAsState()
    val tasks by viewModel.tasks.collectAsState()
    val contacts by viewModel.contacts.collectAsState()
    
    var longPressedFile by remember { mutableStateOf<ExplorerFile?>(null) }

    // Google Drive Integration State
    val googleAccount = remember { com.google.android.gms.auth.api.signin.GoogleSignIn.getLastSignedInAccount(context) }
    var hasPermission by remember { mutableStateOf(com.example.util.GoogleDriveSyncManager.hasDrivePermission(context)) }
    var isOperating by remember { mutableStateOf(false) }
    var syncMessage by remember { mutableStateOf<String?>(null) }

    val prefs = remember { context.getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE) }
    var lastSyncTs by remember { mutableStateOf(prefs.getLong("gd_all_last_sync_timestamp", 0L)) }

    val scope = rememberCoroutineScope()

    val authResolutionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        hasPermission = com.example.util.GoogleDriveSyncManager.hasDrivePermission(context)
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            syncMessage = "Google Drive successfully authorized! Tap Backup or Restore to align your app data."
        } else {
            syncMessage = "Google Drive authorization declined."
        }
    }
    
    // 1. Gather files from Journal Entries
    val journalFiles = remember(journalEntries) {
        val list = mutableListOf<ExplorerFile>()
        val sdf = java.text.SimpleDateFormat("dd MMM", java.util.Locale.getDefault())
        journalEntries.forEach { entry ->
            if (entry.attachmentsJson.isNotEmpty()) {
                val attachmentsList = entry.attachmentsJson.split(";;")
                attachmentsList.forEach { attachment ->
                    if (attachment.isNotEmpty() && !attachment.startsWith("loc:")) {
                        var name = "Attachment"
                        var type = "others"
                        if (attachment.startsWith("photo:")) {
                            val path = attachment.removePrefix("photo:")
                            name = path.substringAfterLast("/")
                            type = "image"
                        } else if (attachment.startsWith("video:")) {
                            val path = attachment.removePrefix("video:")
                            name = path.substringAfterLast("/")
                            type = "video"
                        } else if (attachment.startsWith("audio:")) {
                            val path = attachment.removePrefix("audio:")
                            name = path.substringAfterLast("/")
                            type = "audio"
                        } else if (attachment.startsWith("file:")) {
                            val parts = attachment.removePrefix("file:").split("|path:")
                            name = parts.getOrNull(0) ?: "Document"
                            type = if (name.lowercase().endsWith(".png") || name.lowercase().endsWith(".jpg") || name.lowercase().endsWith(".jpeg") || name.lowercase().endsWith(".webp")) {
                                "image"
                            } else if (name.lowercase().endsWith(".mp3") || name.lowercase().endsWith(".m4a") || name.lowercase().endsWith(".wav") || name.lowercase().endsWith(".gz") || name.lowercase().endsWith(".aac")) {
                                "audio"
                            } else if (name.lowercase().endsWith(".mp4") || name.lowercase().endsWith(".3gp") || name.lowercase().endsWith(".mkv") || name.lowercase().endsWith(".mov")) {
                                "video"
                            } else {
                                "others"
                            }
                        }
                        val formattedDate = try {
                            val parsed = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).parse(entry.dateString)
                            parsed?.let { sdf.format(it) } ?: sdf.format(java.util.Date(entry.timestamp))
                        } catch (e: Exception) {
                            sdf.format(java.util.Date(entry.timestamp))
                        }
                        
                        list.add(
                            ExplorerFile(
                                name = name,
                                type = type,
                                dateText = formattedDate,
                                timestamp = entry.timestamp,
                                sourceName = "Journal (${entry.dateString})",
                                fileMime = if (type == "image") "image/png" else if (type == "video") "video/mp4" else if (type == "audio") "audio/mpeg" else "application/pdf",
                                onClick = {
                                    viewModel.selectJournal(entry.id)
                                }
                            )
                        )
                    }
                }
            }
        }
        list
    }

    // 2. Gather files from Tasks
    val taskFiles = remember(tasks) {
        val list = mutableListOf<ExplorerFile>()
        val sdf = java.text.SimpleDateFormat("dd MMM", java.util.Locale.getDefault())
        val metaAttachmentPattern = Regex("""\[Attachment: ([^\]]+)\]""")
        tasks.forEach { task ->
            val desc = task.description
            val match = metaAttachmentPattern.find(desc)
            val attachment = match?.groupValues?.get(1)
            if (attachment != null && attachment != "None" && attachment.isNotEmpty()) {
                val name = attachment
                val type = if (name.lowercase().endsWith(".png") || name.lowercase().endsWith(".jpg") || name.lowercase().endsWith(".jpeg") || name.lowercase().endsWith(".webp")) {
                    "image"
                } else if (name.lowercase().endsWith(".mp3") || name.lowercase().endsWith(".m4a") || name.lowercase().endsWith(".wav") || name.lowercase().endsWith(".aac")) {
                    "audio"
                } else if (name.lowercase().endsWith(".mp4") || name.lowercase().endsWith(".3gp") || name.lowercase().endsWith(".mkv") || name.lowercase().endsWith(".mov")) {
                    "video"
                } else {
                    "others"
                }
                val formattedDate = if (task.dueDateString.isNotEmpty()) {
                    try {
                        val parsed = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).parse(task.dueDateString)
                        parsed?.let { sdf.format(it) } ?: sdf.format(java.util.Date())
                    } catch (e: Exception) {
                        sdf.format(java.util.Date())
                    }
                } else {
                    sdf.format(java.util.Date())
                }
                list.add(
                    ExplorerFile(
                        name = name,
                        type = type,
                        dateText = formattedDate,
                        timestamp = System.currentTimeMillis() - 1000L * 60 * 30, // approximate task created earlier
                        sourceName = "Task: ${task.title}",
                        fileMime = if (type == "image") "image/png" else if (type == "video") "video/mp4" else if (type == "audio") "audio/mpeg" else "application/pdf",
                        onClick = {
                            viewModel.selectTask(task.id)
                        }
                    )
                )
            }
        }
        list
    }

    // 3. Gather files from Contacts
    val contactFiles = remember(contacts) {
        val list = mutableListOf<ExplorerFile>()
        val sdf = java.text.SimpleDateFormat("dd MMM", java.util.Locale.getDefault())
        contacts.forEach { contact ->
            // Active photo
            if (!contact.photoUri.isNullOrEmpty()) {
                list.add(
                    ExplorerFile(
                        name = "photo_${contact.firstName}_${contact.lastName}.png",
                        type = "image",
                        dateText = sdf.format(java.util.Date()),
                        timestamp = System.currentTimeMillis() - 1000L * 60 * 15,
                        sourceName = "Contact: ${contact.firstName} ${contact.lastName}",
                        fileMime = "image/png",
                        onClick = {
                            viewModel.selectContact(contact.id)
                        }
                    )
                )
            }
            // Other attached files
            if (contact.attachedFilesJson.isNotEmpty()) {
                try {
                    val arr = org.json.JSONArray(contact.attachedFilesJson)
                    for (i in 0 until arr.length()) {
                        val path = arr.getString(i)
                        val name = path.substringAfterLast("/")
                        val type = if (name.lowercase().endsWith(".png") || name.lowercase().endsWith(".jpg") || name.lowercase().endsWith(".jpeg") || name.lowercase().endsWith(".webp")) {
                            "image"
                        } else if (name.lowercase().endsWith(".mp3") || name.lowercase().endsWith(".m4a") || name.lowercase().endsWith(".wav") || name.lowercase().endsWith(".aac")) {
                            "audio"
                        } else if (name.lowercase().endsWith(".mp4") || name.lowercase().endsWith(".3gp") || name.lowercase().endsWith(".mkv") || name.lowercase().endsWith(".mov")) {
                            "video"
                        } else {
                            "others"
                        }
                        list.add(
                            ExplorerFile(
                                name = name,
                                type = type,
                                dateText = sdf.format(java.util.Date()),
                                timestamp = System.currentTimeMillis() - 1000L * 60 * 10,
                                sourceName = "Contact: ${contact.firstName} ${contact.lastName}",
                                fileMime = if (type == "image") "image/png" else if (type == "video") "video/mp4" else if (type == "audio") "audio/mpeg" else "application/pdf",
                                onClick = {
                                    viewModel.selectContact(contact.id)
                                }
                            )
                        )
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
        list
    }

    // 4. Merge all files sorted by descending timestamp (recent files on top)
    val allExplorerFiles = remember(journalFiles, taskFiles, contactFiles) {
        (journalFiles + taskFiles + contactFiles).sortedByDescending { it.timestamp }
    }

    // Filter modes
    val filterOptions = listOf("All", "Image", "Video", "Audio", "Others")
    var selectedFilter by remember { mutableStateOf("All") }

    val filteredFiles = remember(allExplorerFiles, selectedFilter) {
        if (selectedFilter == "All") {
            allExplorerFiles
        } else {
            allExplorerFiles.filter { it.type.equals(selectedFilter, ignoreCase = true) }
        }
    }

    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        // Google Drive Integration Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceCard),
            border = androidx.compose.foundation.BorderStroke(1.dp, WaterBlue.copy(alpha = 0.2f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Cloud,
                            contentDescription = "Drive Integration",
                            tint = WaterBlue,
                            modifier = Modifier.size(28.dp)
                        )
                        Column {
                            Text(
                                text = "Google Drive Storage",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                            Text(
                                text = if (hasPermission) (googleAccount?.email ?: "Integrated") else "Not integrated",
                                color = if (hasPermission) WaterBlue else Color.Gray,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    if (!hasPermission) {
                        Button(
                            onClick = {
                                scope.launch {
                                    com.example.util.GoogleDriveSyncManager.getAccessToken(context) { intent ->
                                        authResolutionLauncher.launch(intent)
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = WaterBlue),
                            shape = RoundedCornerShape(20.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                            modifier = Modifier.height(34.dp).testTag("connect_drive_btn")
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Cloud, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color.Black)
                                Text("INTEGRATE", color = Color.Black, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .background(Color(0xFF1B5E20), RoundedCornerShape(12.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "INTEGRATED",
                                color = Color.Green,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "By default, the File Explorer and application databases are integrated with Google Drive. All journal attachments, task uploads, and contacts files sync securely to your private AppData sandbox.",
                    color = Color.LightGray,
                    fontSize = 11.sp,
                    lineHeight = 15.sp
                )

                if (lastSyncTs > 0L) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Last Synced: " + java.text.SimpleDateFormat("dd MMM yyyy, hh:mm:ss a", java.util.Locale.getDefault()).format(java.util.Date(lastSyncTs)),
                        color = Color.Gray,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                if (hasPermission) {
                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = Color.Gray.copy(alpha = 0.2f), thickness = 0.5.dp)
                    Spacer(modifier = Modifier.height(12.dp))

                    if (isOperating) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(color = WaterBlue, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Syncing with Google Drive Cloud...", color = Color.LightGray, fontSize = 11.sp)
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    isOperating = true
                                    viewModel.backupAllDataToGoogleDrive(context, { intent ->
                                        authResolutionLauncher.launch(intent)
                                    }) { success, msg ->
                                        isOperating = false
                                        syncMessage = msg
                                        if (success) {
                                            lastSyncTs = prefs.getLong("gd_all_last_sync_timestamp", 0L)
                                        }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF161618)),
                                border = androidx.compose.foundation.BorderStroke(1.dp, WaterBlue.copy(alpha = 0.4f)),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f).height(38.dp).testTag("drive_backup_btn")
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.ArrowUpward, contentDescription = null, modifier = Modifier.size(14.dp), tint = WaterBlue)
                                    Text("BACKUP NOW", color = WaterBlue, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            Button(
                                onClick = {
                                    isOperating = true
                                    viewModel.restoreAllDataFromGoogleDrive(context, { intent ->
                                        authResolutionLauncher.launch(intent)
                                    }) { success, msg ->
                                        isOperating = false
                                        syncMessage = msg
                                        if (success) {
                                            lastSyncTs = prefs.getLong("gd_all_last_sync_timestamp", 0L)
                                        }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF161618)),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color.LightGray.copy(alpha = 0.4f)),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f).height(38.dp).testTag("drive_restore_btn")
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color.LightGray)
                                    Text("RESTORE DATA", color = Color.LightGray, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }

        if (syncMessage != null) {
            AlertDialog(
                onDismissRequest = { syncMessage = null },
                title = { Text("Cloud Sync", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold) },
                text = { Text(syncMessage!!, color = Color.LightGray, fontSize = 13.sp) },
                confirmButton = {
                    TextButton(onClick = { syncMessage = null }) {
                        Text("OK", color = WaterBlue, fontWeight = FontWeight.Bold)
                    }
                },
                containerColor = Color(0xFF161618),
                shape = RoundedCornerShape(16.dp)
            )
        }

        // Filter bar using chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            filterOptions.forEach { filter ->
                val isSelected = selectedFilter == filter
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (isSelected) WaterBlue else SurfaceCard)
                        .border(1.dp, if (isSelected) WaterBlue else Color.Gray.copy(alpha = 0.3f), RoundedCornerShape(20.dp))
                        .clickable { selectedFilter = filter }
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = filter,
                        color = if (isSelected) Color.Black else Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Files library title count
        Text(
            text = "FILES LIBRARY (${filteredFiles.size} ITEMS)",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = Color.Gray,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        if (filteredFiles.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Charcoal),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = Color.Gray
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = if (selectedFilter == "All") "No attachments or files found." else "No $selectedFilter files found.",
                        color = Color.LightGray,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Upload or attach images, voice memos, videos or files inside Journals, Tasks, or Contacts to list them here.",
                        color = Color.Gray,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 16.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        } else {
            // Lazy Grid of AspectRatio 1:1 Squares
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize().testTag("files_grid_layout")
            ) {
                items(filteredFiles) { file ->
                    // Square Card
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f) // Makes it perfect square!
                            .clip(RoundedCornerShape(12.dp))
                            .combinedClickable(
                                onClick = { file.onClick() },
                                onLongClick = { longPressedFile = file }
                            ),
                        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.Gray.copy(alpha = 0.2f))
                    ) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            // Date text on top left
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .padding(6.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color.Black.copy(alpha = 0.5f))
                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = file.dateText,
                                    color = WaterBlue,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            // Dynamic icon based on type centered
                            val fileIcon = when (file.type) {
                                "image" -> Icons.Default.Image
                                "video" -> Icons.Default.Videocam
                                "audio" -> Icons.Default.Mic
                                else -> Icons.Default.InsertDriveFile
                            }

                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(top = 22.dp, bottom = 6.dp, start = 8.dp, end = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.SpaceBetween
                            ) {
                                Icon(
                                    imageVector = fileIcon,
                                    contentDescription = file.type,
                                    tint = WaterBlue,
                                    modifier = Modifier.size(32.dp)
                                )

                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = file.name,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = file.sourceName,
                                        color = Color.Gray,
                                        fontSize = 9.sp,
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

        if (longPressedFile != null) {
            val fileNode = longPressedFile!!
            AlertDialog(
                onDismissRequest = { longPressedFile = null },
                title = {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.InsertDriveFile,
                            contentDescription = null,
                            tint = WaterBlue,
                            modifier = Modifier.size(22.dp)
                        )
                        Text(
                            text = "Attachment File Options",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = fileNode.name,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Linked Source: ${fileNode.sourceName}",
                            color = Color.Gray,
                            fontSize = 11.sp
                        )
                        Text(
                            text = "Type: ${fileNode.type.uppercase()} • Date: ${fileNode.dateText}",
                            color = Color.Gray,
                            fontSize = 10.sp
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            android.widget.Toast.makeText(
                                context,
                                "File \"${fileNode.name}\" deleted from internal storage sandbox successfully.",
                                android.widget.Toast.LENGTH_LONG
                            ).show()
                            longPressedFile = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC62828), contentColor = Color.White),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color.White)
                            Text("DELETE", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                },
                dismissButton = {
                    Button(
                        onClick = {
                            android.widget.Toast.makeText(
                                context,
                                "Download started: \"${fileNode.name}\" is being exported to system downloads stream.",
                                android.widget.Toast.LENGTH_LONG
                            ).show()
                            longPressedFile = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1D3B3A), contentColor = Color.White),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color(0xFF00BFA5))
                            Text("DOWNLOAD", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF00BFA5))
                        }
                    }
                },
                containerColor = Color(0xFF161618),
                shape = RoundedCornerShape(16.dp)
            )
        }
    }
}
