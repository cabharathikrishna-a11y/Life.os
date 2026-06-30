package com.example.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.*
import com.example.ui.AppViewModel
import com.example.ui.theme.AlertRed
import com.example.ui.theme.Charcoal
import com.example.ui.theme.MonospaceNumbers
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.WaterBlue
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

data class CombinedHistoryItem(
    val id: String,
    val timestamp: Long,
    val title: String,
    val type: String,
    val subtitle: String,
    val amount: Double,
    val note: String,
    val isAssetImpact: Boolean,
    val detailString: String
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FinancialLedgerView(viewModel: AppViewModel, modifier: Modifier = Modifier) {
    val coroutineScope = rememberCoroutineScope()
    
    val familyMembers by viewModel.familyMembers.collectAsStateWithLifecycle()
    val accounts by viewModel.financialAccounts.collectAsStateWithLifecycle()
    val logs by viewModel.financialLogs.collectAsStateWithLifecycle()
    val txs by viewModel.financeTransactions.collectAsStateWithLifecycle()
    val categories by viewModel.financeCategories.collectAsStateWithLifecycle()

    // Screen State
    var selectedMemberId by remember { mutableStateOf<Int?>(null) } // null means "All Members"
    
    // Account details expansion states
    var expandedCategory by remember { mutableStateOf<String?>(null) } // "LONG_TERM_ASSETS", "CURRENT_ASSETS", etc.

    // Modal forms expansion states
    var showRecordExpense by remember { mutableStateOf(false) }
    var showRecordIncome by remember { mutableStateOf(false) }
    var showRecordTransfer by remember { mutableStateOf(false) }

    // Manual Adjustments Modals
    var activeAdjustmentAccount by remember { mutableStateOf<FinancialAccount?>(null) }
    var activeAdjustmentType by remember { mutableStateOf("") } // "APPRECIATION", "DEPRECIATION", "INTEREST_ACCRUED", "PAID"
    var showAdjustmentDialog by remember { mutableStateOf(false) }

    // Range Query States
    var queryStartYear by remember { mutableIntStateOf(2026) }
    var queryStartMonth by remember { mutableIntStateOf(1) }
    var queryStartDay by remember { mutableIntStateOf(1) }
    var queryEndYear by remember { mutableIntStateOf(2026) }
    var queryEndMonth by remember { mutableIntStateOf(12) }
    var queryEndDay by remember { mutableIntStateOf(31) }
    var showQueryResults by remember { mutableStateOf(false) }
    var queryTypeRequested by remember { mutableStateOf("") } // "INCOME" or "EXPENSE"

    // AI advisor state
    var aiReportText by remember { mutableStateOf("") }
    var isGeneratingAiReport by remember { mutableStateOf(false) }
    var showTransactionHistory by remember { mutableStateOf(false) }

    // Helper: Compute balance for an individual account
    fun getAccountBalance(a: FinancialAccount): Double {
        val initial = a.openingValue
        val adjustments = logs.filter { it.accountId == a.id }.sumOf { l ->
            when (l.logType) {
                "APPRECIATION", "INTEREST_ACCRUED" -> l.amount
                "DEPRECIATION", "PAID" -> -l.amount
                else -> 0.0
            }
        }
        var txAdjust = 0.0
        txs.forEach { t ->
            if (t.fromAccountId == a.id) {
                if (a.categoryType.contains("ASSET")) {
                    txAdjust -= t.amount
                } else {
                    txAdjust += t.amount
                }
            }
            if (t.toAccountId == a.id) {
                if (a.categoryType.contains("ASSET")) {
                    txAdjust += t.amount
                } else {
                    txAdjust -= t.amount
                }
            }
        }
        return initial + adjustments + txAdjust
    }

    // Filter accounts based on selected family member
    val activeAccounts = remember(accounts, selectedMemberId) {
        if (selectedMemberId == null) {
            accounts // Merged/all
        } else {
            accounts.filter { it.memberId == selectedMemberId }
        }
    }

    // Calculations based on the active set of accounts
    val computedLongTermAssets = remember(activeAccounts, logs, txs) {
        activeAccounts.filter { it.categoryType == "LONG_TERM_ASSETS" }.sumOf { getAccountBalance(it) }
    }
    val computedCurrentAssets = remember(activeAccounts, logs, txs) {
        activeAccounts.filter { it.categoryType == "CURRENT_ASSETS" }.sumOf { getAccountBalance(it) }
    }
    val computedCurrentLiabilities = remember(activeAccounts, logs, txs) {
        activeAccounts.filter { it.categoryType == "CURRENT_LIABILITIES" }.sumOf { getAccountBalance(it) }
    }
    val computedLongTermLiabilities = remember(activeAccounts, logs, txs) {
        activeAccounts.filter { it.categoryType == "LONG_TERM_LIABILITIES" }.sumOf { getAccountBalance(it) }
    }

    val totalAssets = computedLongTermAssets + computedCurrentAssets
    val totalLiabilities = computedCurrentLiabilities + computedLongTermLiabilities
    val netWorth = totalAssets - totalLiabilities

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // --- PART 1: FAMILY MEMBERS TABS ---
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "FAMILY ACCOUNTS radar",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Gray
                )
                
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // "All Members" primary trigger
                    val isAllSelected = selectedMemberId == null
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(if (isAllSelected) WaterBlue else Charcoal)
                            .border(
                                width = 1.dp,
                                color = if (isAllSelected) WaterBlue else Color.Gray.copy(alpha = 0.5f),
                                shape = RoundedCornerShape(20.dp)
                            )
                            .clickable {
                                selectedMemberId = null
                                expandedCategory = null
                            }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Groups,
                                contentDescription = "All",
                                tint = if (isAllSelected) Color.Black else Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                "All Members",
                                color = if (isAllSelected) Color.Black else Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    }

                    // Individual members
                    familyMembers.forEach { member ->
                        val isSelected = selectedMemberId == member.id
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(if (isSelected) WaterBlue else Charcoal)
                                .border(
                                    width = 1.dp,
                                    color = if (isSelected) WaterBlue else Color.Gray.copy(alpha = 0.3f),
                                    shape = RoundedCornerShape(20.dp)
                                )
                                .clickable {
                                    selectedMemberId = member.id
                                    expandedCategory = null
                                }
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = member.name,
                                    tint = if (isSelected) Color.Black else Color.LightGray,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = member.name,
                                    color = if (isSelected) Color.Black else Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                    
                    if (familyMembers.isEmpty()) {
                        Text(
                            "💡 Tip: Go to Settings -> Financial Ledger to add Family Members!",
                            color = WaterBlue,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }
        }

        // --- PART 2: NET WORTH DASHBOARD CARD ---
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.05f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val entityLabel = remember(selectedMemberId, familyMembers) {
                        if (selectedMemberId == null) "FAMILY CONSOLIDATED NET WORTH"
                        else "${familyMembers.find { it.id == selectedMemberId }?.name?.uppercase() ?: "MEMBER"}'S NET WORTH"
                    }
                    Text(
                        text = entityLabel,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.LightGray
                    )
                    
                    Text(
                        text = "₹${String.format("%,.2f", netWorth)}",
                        style = MonospaceNumbers.copy(fontSize = 32.sp, fontWeight = FontWeight.ExtraBold),
                        color = if (netWorth >= 0) WaterBlue else AlertRed,
                        modifier = Modifier.padding(vertical = 6.dp)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("CONSOLIDATED ASSETS", fontSize = 9.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                            Text(
                                text = "+₹${String.format("%,.2f", totalAssets)}",
                                style = MonospaceNumbers.copy(fontSize = 14.sp),
                                color = SuccessGreen
                            )
                        }

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("CONSOLIDATED LIABILITIES", fontSize = 9.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                            Text(
                                text = "-₹${String.format("%,.2f", totalLiabilities)}",
                                style = MonospaceNumbers.copy(fontSize = 14.sp),
                                color = AlertRed
                            )
                        }
                    }
                }
            }
        }

        // --- PART 3: THE 4 SEPARATE CATEGORY CARDS ---
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "ASSET & LIABILITY SEGMENTS",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Gray
                )

                // Category parameters mapping
                val categoriesMap = listOf(
                    Triple("LONG_TERM_ASSETS", "Long Term Assets", computedLongTermAssets),
                    Triple("CURRENT_ASSETS", "Current Assets", computedCurrentAssets),
                    Triple("CURRENT_LIABILITIES", "Current Liability", computedCurrentLiabilities),
                    Triple("LONG_TERM_LIABILITIES", "Long Term Liability", computedLongTermLiabilities)
                )

                categoriesMap.forEach { (typeCode, displayName, totalValue) ->
                    val isExpanded = expandedCategory == typeCode
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.05f))
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            // Header Row clickable
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        expandedCategory = if (isExpanded) null else typeCode
                                    },
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Icon(
                                        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                        contentDescription = "Expand",
                                        tint = WaterBlue
                                    )
                                    Text(
                                        text = displayName,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                                
                                Text(
                                    text = "₹${String.format("%,.2f", totalValue)}",
                                    style = MonospaceNumbers.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold),
                                    color = if (typeCode.contains("ASSET")) SuccessGreen else AlertRed
                                )
                            }

                            // Nested expanded form & account listings
                            AnimatedVisibility(visible = isExpanded) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 12.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    HorizontalDivider(color = Color.Gray.copy(alpha = 0.2f))

                                    // Create account nested section
                                    if (selectedMemberId == null) {
                                        Text(
                                            "⚠️ Select a specific family member tab to add accounts to this category.",
                                            fontSize = 11.sp,
                                            color = WaterBlue
                                        )
                                    } else {
                                        var accountName by remember { mutableStateOf("") }
                                        var openingValText by remember { mutableStateOf("") }

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            TextField(
                                                value = accountName,
                                                onValueChange = { accountName = it },
                                                placeholder = { Text("Account Name", fontSize = 11.sp) },
                                                colors = TextFieldDefaults.colors(
                                                    focusedTextColor = Color.White,
                                                    unfocusedTextColor = Color.LightGray,
                                                    focusedContainerColor = SurfaceCard,
                                                    unfocusedContainerColor = SurfaceCard
                                                ),
                                                modifier = Modifier.weight(1.5f),
                                                singleLine = true
                                            )

                                            TextField(
                                                value = openingValText,
                                                onValueChange = { openingValText = it },
                                                placeholder = { Text("Opening Val", fontSize = 11.sp) },
                                                colors = TextFieldDefaults.colors(
                                                    focusedTextColor = Color.White,
                                                    unfocusedTextColor = Color.LightGray,
                                                    focusedContainerColor = SurfaceCard,
                                                    unfocusedContainerColor = SurfaceCard
                                                ),
                                                modifier = Modifier.weight(1f),
                                                singleLine = true
                                            )

                                            Button(
                                                onClick = {
                                                    val opVal = openingValText.toDoubleOrNull() ?: 0.0
                                                    if (accountName.isNotBlank() && selectedMemberId != null) {
                                                        viewModel.createFinancialAccount(
                                                            memberId = selectedMemberId!!,
                                                            name = accountName.trim(),
                                                            categoryType = typeCode,
                                                            openingValue = opVal
                                                        )
                                                        accountName = ""
                                                        openingValText = ""
                                                    }
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = WaterBlue, contentColor = Color.Black),
                                                shape = RoundedCornerShape(8.dp),
                                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                                            ) {
                                                Text("Add", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }

                                    // List existing accounts
                                    val catAccounts = activeAccounts.filter { it.categoryType == typeCode }
                                    if (catAccounts.isEmpty()) {
                                        Text("No accounts registered under this segment.", fontSize = 12.sp, color = Color.Gray)
                                    } else {
                                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            catAccounts.forEach { account ->
                                                val accountBalance = getAccountBalance(account)
                                                Card(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    colors = CardDefaults.cardColors(containerColor = SurfaceCard)
                                                ) {
                                                    Column(modifier = Modifier.padding(10.dp)) {
                                                        Row(
                                                            modifier = Modifier.fillMaxWidth(),
                                                            horizontalArrangement = Arrangement.SpaceBetween,
                                                            verticalAlignment = Alignment.CenterVertically
                                                        ) {
                                                            val originLabel = remember(selectedMemberId, familyMembers) {
                                                                if (selectedMemberId == null) {
                                                                    val mName = familyMembers.find { it.id == account.memberId }?.name ?: "All"
                                                                    "${account.name} [$mName]"
                                                                } else account.name
                                                            }
                                                            Text(originLabel, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                                            
                                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                                Text(
                                                                    text = "₹${String.format("%,.2f", accountBalance)}",
                                                                    style = MonospaceNumbers.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold),
                                                                    color = if (typeCode.contains("ASSET")) SuccessGreen else AlertRed
                                                                )
                                                                IconButton(
                                                                    onClick = { viewModel.deleteFinancialAccount(account) },
                                                                    modifier = Modifier.size(20.dp)
                                                                ) {
                                                                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Gray, modifier = Modifier.size(12.dp))
                                                                }
                                                            }
                                                        }

                                                        // Modification buttons nested under custom conditions
                                                        if (typeCode == "LONG_TERM_ASSETS") {
                                                            Spacer(modifier = Modifier.height(8.dp))
                                                            Row(
                                                                modifier = Modifier.fillMaxWidth(),
                                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                            ) {
                                                                Button(
                                                                    onClick = {
                                                                        activeAdjustmentAccount = account
                                                                        activeAdjustmentType = "APPRECIATION"
                                                                        showAdjustmentDialog = true
                                                                    },
                                                                    colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen.copy(alpha = 0.2f), contentColor = SuccessGreen),
                                                                    modifier = Modifier.weight(1f).height(32.dp),
                                                                    contentPadding = PaddingValues(0.dp)
                                                                ) {
                                                                    Icon(Icons.Default.TrendingUp, contentDescription = null, modifier = Modifier.size(12.dp))
                                                                    Spacer(modifier = Modifier.width(4.dp))
                                                                    Text("Appreciation", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                                }

                                                                Button(
                                                                    onClick = {
                                                                        activeAdjustmentAccount = account
                                                                        activeAdjustmentType = "DEPRECIATION"
                                                                        showAdjustmentDialog = true
                                                                    },
                                                                    colors = ButtonDefaults.buttonColors(containerColor = AlertRed.copy(alpha = 0.2f), contentColor = AlertRed),
                                                                    modifier = Modifier.weight(1f).height(32.dp),
                                                                    contentPadding = PaddingValues(0.dp)
                                                                ) {
                                                                    Icon(Icons.Default.TrendingDown, contentDescription = null, modifier = Modifier.size(12.dp))
                                                                    Spacer(modifier = Modifier.width(4.dp))
                                                                    Text("Depreciation", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                                }
                                                            }
                                                        }

                                                        if (typeCode == "LONG_TERM_LIABILITIES") {
                                                            Spacer(modifier = Modifier.height(8.dp))
                                                            Row(
                                                                modifier = Modifier.fillMaxWidth(),
                                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                            ) {
                                                                Button(
                                                                    onClick = {
                                                                        activeAdjustmentAccount = account
                                                                        activeAdjustmentType = "INTEREST_ACCRUED"
                                                                        showAdjustmentDialog = true
                                                                    },
                                                                    colors = ButtonDefaults.buttonColors(containerColor = AlertRed.copy(alpha = 0.2f), contentColor = AlertRed),
                                                                    modifier = Modifier.weight(1f).height(32.dp),
                                                                    contentPadding = PaddingValues(0.dp)
                                                                ) {
                                                                    Icon(Icons.Default.AddAlert, contentDescription = null, modifier = Modifier.size(12.dp))
                                                                    Spacer(modifier = Modifier.width(4.dp))
                                                                    Text("Interest Accrued", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                                }

                                                                Button(
                                                                    onClick = {
                                                                        activeAdjustmentAccount = account
                                                                        activeAdjustmentType = "PAID"
                                                                        showAdjustmentDialog = true
                                                                    },
                                                                    colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen.copy(alpha = 0.2f), contentColor = SuccessGreen),
                                                                    modifier = Modifier.weight(1f).height(32.dp),
                                                                    contentPadding = PaddingValues(0.dp)
                                                                ) {
                                                                    Icon(Icons.Default.Payment, contentDescription = null, modifier = Modifier.size(12.dp))
                                                                    Spacer(modifier = Modifier.width(4.dp))
                                                                    Text("Paid", fontSize = 11.sp, fontWeight = FontWeight.Bold)
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
                        }
                    }
                }
            }
        }

        // --- PART 4: THE 3 CORE FINANCIAL TRANSACTION TRIGGERS ---
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "REGISTER BOOK TRANSACTION",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Gray
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { showRecordExpense = true },
                        colors = ButtonDefaults.buttonColors(containerColor = AlertRed.copy(alpha = 0.15f), contentColor = AlertRed),
                        modifier = Modifier.weight(1f).height(44.dp),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Remove, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Expense", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = { showRecordIncome = true },
                        colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen.copy(alpha = 0.15f), contentColor = SuccessGreen),
                        modifier = Modifier.weight(1f).height(44.dp),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Income", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = { showRecordTransfer = true },
                        colors = ButtonDefaults.buttonColors(containerColor = WaterBlue.copy(alpha = 0.15f), contentColor = WaterBlue),
                        modifier = Modifier.weight(1f).height(44.dp),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.SwapHoriz, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Transfer", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // --- PART 4B: TRANSACTION HISTORY TRIGGER ---
        item {
            Button(
                onClick = { showTransactionHistory = true },
                colors = ButtonDefaults.buttonColors(containerColor = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.05f), contentColor = WaterBlue),
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, WaterBlue.copy(alpha = 0.3f))
            ) {
                Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Transaction History", fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }

        // --- PART 5: AI ADVISOR REPORT COMPONENT ---
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.05f))
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Default.Psychology, contentDescription = null, tint = WaterBlue)
                            Text("Deepa AI Ledger Intelligence", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 13.sp)
                        }
                    }
                    Text("Unlock predictive financial models, budget safety recommendations, and liquid asset analysis using secure local AI intelligence.", fontSize = 11.sp, color = Color.LightGray)
                    
                    Button(
                        onClick = {
                            viewModel.runAdvancedAIFinancialAudit()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = WaterBlue, contentColor = Color.Black),
                        modifier = Modifier.fillMaxWidth().height(36.dp)
                    ) {
                        Text("Run Advanced AI Financial Audit", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // --- PART 6: EXPENSE & INCOME RANGE QUERIES ---
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.05f))
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "RANGE CASHFLOW ANALYTICS",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = WaterBlue
                    )
                    Text("Select a custom date range and query cumulative breakdowns instantly with zero mathematical error.", fontSize = 11.sp, color = Color.Gray)

                    // SIMPLE, ELEGANT GRID SELECTORS FOR START AND END DATE
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("START YEAR", fontSize = 9.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                            TextField(
                                value = queryStartYear.toString(),
                                onValueChange = { queryStartYear = it.toIntOrNull() ?: 2026 },
                                colors = TextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.LightGray,
                                    focusedContainerColor = SurfaceCard,
                                    unfocusedContainerColor = SurfaceCard
                                ),
                                singleLine = true
                            )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text("START MONTH (1-12)", fontSize = 9.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                            TextField(
                                value = queryStartMonth.toString(),
                                onValueChange = { queryStartMonth = (it.toIntOrNull() ?: 1).coerceIn(1, 12) },
                                colors = TextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.LightGray,
                                    focusedContainerColor = SurfaceCard,
                                    unfocusedContainerColor = SurfaceCard
                                ),
                                singleLine = true
                            )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text("START DAY (1-31)", fontSize = 9.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                            TextField(
                                value = queryStartDay.toString(),
                                onValueChange = { queryStartDay = (it.toIntOrNull() ?: 1).coerceIn(1, 31) },
                                colors = TextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.LightGray,
                                    focusedContainerColor = SurfaceCard,
                                    unfocusedContainerColor = SurfaceCard
                                ),
                                singleLine = true
                            )
                        }
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("END YEAR", fontSize = 9.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                            TextField(
                                value = queryEndYear.toString(),
                                onValueChange = { queryEndYear = it.toIntOrNull() ?: 2026 },
                                colors = TextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.LightGray,
                                    focusedContainerColor = SurfaceCard,
                                    unfocusedContainerColor = SurfaceCard
                                ),
                                singleLine = true
                            )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text("END MONTH (1-12)", fontSize = 9.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                            TextField(
                                value = queryEndMonth.toString(),
                                onValueChange = { queryEndMonth = (it.toIntOrNull() ?: 12).coerceIn(1, 12) },
                                colors = TextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.LightGray,
                                    focusedContainerColor = SurfaceCard,
                                    unfocusedContainerColor = SurfaceCard
                                ),
                                singleLine = true
                            )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text("END DAY (1-31)", fontSize = 9.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                            TextField(
                                value = queryEndDay.toString(),
                                onValueChange = { queryEndDay = (it.toIntOrNull() ?: 31).coerceIn(1, 31) },
                                colors = TextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.LightGray,
                                    focusedContainerColor = SurfaceCard,
                                    unfocusedContainerColor = SurfaceCard
                                ),
                                singleLine = true
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                queryTypeRequested = "EXPENSE"
                                showQueryResults = true
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = AlertRed, contentColor = Color.White),
                            modifier = Modifier.weight(1f).height(38.dp)
                        ) {
                            Text("Total Expenses", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = {
                                queryTypeRequested = "INCOME"
                                showQueryResults = true
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen, contentColor = Color.White),
                            modifier = Modifier.weight(1f).height(38.dp)
                        ) {
                            Text("Total Incomes", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    if (showQueryResults) {
                        // Extract timestamps
                        val sCal = Calendar.getInstance().apply {
                            set(queryStartYear, queryStartMonth - 1, queryStartDay, 0, 0, 0)
                        }
                        val eCal = Calendar.getInstance().apply {
                            set(queryEndYear, queryEndMonth - 1, queryEndDay, 23, 59, 59)
                        }
                        val startTs = sCal.timeInMillis
                        val endTs = eCal.timeInMillis

                        // Filtered transaction list
                        val inRangeTxs = txs.filter { t ->
                            t.timestamp in startTs..endTs && t.type == queryTypeRequested &&
                            (selectedMemberId == null || t.memberId == selectedMemberId)
                        }

                        // Compute category aggregations
                        val categorySums = remember(inRangeTxs) {
                            val map = mutableMapOf<String, Double>()
                            inRangeTxs.forEach { t ->
                                val categoryName = if (t.type == "EXPENSE") t.toCategory else t.fromCategory
                                if (categoryName != null) {
                                    map[categoryName] = (map[categoryName] ?: 0.0) + t.amount
                                }
                            }
                            map.toList().sortedByDescending { it.second }
                        }

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = SurfaceCard)
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val dateFmt = SimpleDateFormat("yyyy/MM/dd", Locale.getDefault())
                                    Text(
                                        text = "${queryTypeRequested} (${dateFmt.format(sCal.time)} - ${dateFmt.format(eCal.time)})",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        color = Color.LightGray
                                    )
                                    IconButton(onClick = { showQueryResults = false }, modifier = Modifier.size(20.dp)) {
                                        Icon(Icons.Default.Close, contentDescription = "Close query", tint = Color.Gray, modifier = Modifier.size(14.dp))
                                    }
                                }

                                if (categorySums.isEmpty()) {
                                    Text("No entries registered within this custom range.", fontSize = 12.sp, color = Color.Gray)
                                } else {
                                    val totalSum = categorySums.sumOf { it.second }
                                    Text(
                                        text = "Total $queryTypeRequested sum: ₹${String.format("%,.2f", totalSum)}",
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 13.sp,
                                        color = if (queryTypeRequested == "INCOME") SuccessGreen else AlertRed
                                    )

                                    HorizontalDivider(color = Color.Gray.copy(alpha = 0.2f))

                                    categorySums.forEach { (catName, sumAmount) ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(catName, color = Color.White, fontSize = 12.sp)
                                            Text(
                                                text = "₹${String.format("%,.2f", sumAmount)}",
                                                style = MonospaceNumbers.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold),
                                                color = Color.White
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

        // Space at the bottom
        item {
            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    // ====================================================
    // MANUAL PORTFOLIO VALUE ADJUSTMENT DIALOG (Appreciation/Depreciation etc.)
    // ====================================================
    if (showAdjustmentDialog && activeAdjustmentAccount != null) {
        var adjAmountText by remember { mutableStateOf("") }
        val dialogTitle = when (activeAdjustmentType) {
            "APPRECIATION" -> "Log Appreciation (Asset up)"
            "DEPRECIATION" -> "Log Depreciation (Asset down)"
            "INTEREST_ACCRUED" -> "Log Interest Accrued (Liability up)"
            "PAID" -> "Log Cash Paid (Liability down)"
            else -> "Log Adjustment"
        }

        AlertDialog(
            onDismissRequest = { showAdjustmentDialog = false },
            title = { Text(dialogTitle, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold) },
            containerColor = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.05f),
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Account: ${activeAdjustmentAccount!!.name}",
                        color = Color.LightGray,
                        fontSize = 12.sp
                    )
                    TextField(
                        value = adjAmountText,
                        onValueChange = { adjAmountText = it },
                        placeholder = { Text("Enter modification amount (₹)") },
                        colors = TextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.LightGray,
                            focusedContainerColor = SurfaceCard,
                            unfocusedContainerColor = SurfaceCard
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val amt = adjAmountText.toDoubleOrNull() ?: 0.0
                        if (amt > 0.0) {
                            viewModel.logAssetAdjustment(activeAdjustmentAccount!!.id, activeAdjustmentType, amt)
                        }
                        showAdjustmentDialog = false
                        adjAmountText = ""
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = WaterBlue, contentColor = Color.Black)
                ) {
                    Text("Confirm Log")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAdjustmentDialog = false }) {
                    Text("Cancel", color = Color.White)
                }
            }
        )
    }

    // ====================================================
    // RECORD EXPENSE MODAL SUBPANEL
    // ====================================================
    if (showRecordExpense) {
        var expenseMemberId by remember { mutableStateOf<Int?>(selectedMemberId ?: familyMembers.firstOrNull()?.id) }
        var expenseFromAccountId by remember { mutableStateOf<Int?>(null) }
        var expenseCategorySelection by remember { mutableStateOf("") }
        var expenseAmountText by remember { mutableStateOf("") }
        var expenseNoteText by remember { mutableStateOf("") }
        val isSavedState = remember { mutableStateOf(false) }

        LaunchedEffect(Unit) {
            val draft = viewModel.getTransactionDraft()
            if (draft != null && draft.type == "EXPENSE") {
                if (draft.memberId != -1) expenseMemberId = draft.memberId
                expenseCategorySelection = draft.toCategory
                expenseAmountText = if (draft.amount > 0.0) draft.amount.toString() else ""
                expenseNoteText = draft.note
                if (draft.fromAccountId != -1) expenseFromAccountId = draft.fromAccountId
                viewModel.clearTransactionDraft()
            }
        }

        DisposableEffect(Unit) {
            onDispose {
                if (!isSavedState.value && (expenseAmountText.isNotEmpty() || expenseNoteText.isNotEmpty())) {
                    viewModel.saveTransactionDraft(
                        memberId = expenseMemberId ?: -1,
                        type = "EXPENSE",
                        amount = expenseAmountText.toDoubleOrNull() ?: 0.0,
                        note = expenseNoteText,
                        fromCategory = "",
                        toCategory = expenseCategorySelection,
                        fromAccountId = expenseFromAccountId ?: -1,
                        toAccountId = -1
                    )
                }
            }
        }

        // Date selection States
        var expYear by remember { mutableStateOf("2026") }
        var expMonth by remember { mutableStateOf("06") }
        var expDay by remember { mutableStateOf("19") }
        var expHour by remember { mutableStateOf("14") }
        var expMinute by remember { mutableStateOf("00") }

        val memberAccounts = accounts.filter { it.memberId == expenseMemberId && (it.categoryType == "CURRENT_ASSETS" || it.categoryType == "CURRENT_LIABILITIES") }
        val expenseCats = categories.filter { it.type == "EXPENSE" }

        var showUnsavedDialog by remember { mutableStateOf(false) }

        val handleDismissAttempt = {
            if (expenseAmountText.isNotEmpty() || expenseNoteText.isNotEmpty() || expenseCategorySelection.isNotEmpty()) {
                showUnsavedDialog = true
            } else {
                showRecordExpense = false
            }
        }

        if (showUnsavedDialog) {
            AlertDialog(
                onDismissRequest = { showUnsavedDialog = false },
                title = { Text("Unsaved Changes", color = Color.White) },
                text = { Text("You have unsaved changes. Do you want to discard them?", color = Color.LightGray) },
                containerColor = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.05f),
                confirmButton = {
                    TextButton(onClick = {
                        showUnsavedDialog = false
                    }) {
                        Text("Resume Editing", color = WaterBlue)
                    }
                },
                dismissButton = {
                    TextButton(onClick = {
                        viewModel.clearTransactionDraft()
                        showUnsavedDialog = false
                        showRecordExpense = false
                    }) {
                        Text("Discard", color = Color(0xFFF9325D))
                    }
                }
            )
        }

        AlertDialog(
            onDismissRequest = { handleDismissAttempt() },
            title = { Text("Record Custom Expense Bookflow", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp) },
            containerColor = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.05f),
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    // Member dropdown trigger
                    Text("RESPONSIBLE FAMILY MEMBER", fontSize = 9.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        familyMembers.forEach { fm ->
                            val isChosen = expenseMemberId == fm.id
                            FilterChip(
                                selected = isChosen,
                                onClick = {
                                    expenseMemberId = fm.id
                                    expenseFromAccountId = null
                                },
                                label = { Text(fm.name, fontSize = 11.sp) }
                            )
                        }
                    }

                    // Account selections
                    Text("SOURCE ACCOUNT (CURRENT ASSET / CURRENT LIABILITY)", fontSize = 9.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                    if (memberAccounts.isEmpty()) {
                        Text("⚠️ No current assets or current liabilities created for this member yet.", color = AlertRed, fontSize = 11.sp)
                    } else {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            memberAccounts.forEach { acc ->
                                val isChosen = expenseFromAccountId == acc.id
                                FilterChip(
                                    selected = isChosen,
                                    onClick = { expenseFromAccountId = acc.id },
                                    label = { Text("${acc.name} (₹${String.format("%,.2f", getAccountBalance(acc))})", fontSize = 11.sp) }
                                )
                            }
                        }
                    }

                    // Categories dropdown
                    Text("DESTINATION EXPENSE CATEGORY", fontSize = 9.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                    if (expenseCats.isEmpty()) {
                        Text("No custom Expense Categories. Add some in settings first.", color = AlertRed, fontSize = 11.sp)
                    } else {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            expenseCats.forEach { ec ->
                                val isChosen = expenseCategorySelection == ec.name
                                FilterChip(
                                    selected = isChosen,
                                    onClick = { expenseCategorySelection = ec.name },
                                    label = { Text(ec.name, fontSize = 11.sp) }
                                )
                            }
                        }
                    }

                    // Amount Text
                    TextField(
                        value = expenseAmountText,
                        onValueChange = { expenseAmountText = it },
                        placeholder = { Text("Expense Amount (₹)") },
                        colors = TextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.LightGray,
                            focusedContainerColor = SurfaceCard,
                            unfocusedContainerColor = SurfaceCard
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Note Text
                    TextField(
                        value = expenseNoteText,
                        onValueChange = { expenseNoteText = it },
                        placeholder = { Text("Notes / Tags") },
                        colors = TextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.LightGray,
                            focusedContainerColor = SurfaceCard,
                            unfocusedContainerColor = SurfaceCard
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Date & Time configuration fields
                    Text("BOOKFLOW TIME (YEAR-MONTH-DAY HOUR:MINUTE)", fontSize = 9.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextField(value = expYear, onValueChange = { expYear = it }, modifier = Modifier.weight(1.5f), colors = TextFieldDefaults.colors(focusedContainerColor = SurfaceCard, unfocusedContainerColor = SurfaceCard))
                        TextField(value = expMonth, onValueChange = { expMonth = it }, modifier = Modifier.weight(1f), colors = TextFieldDefaults.colors(focusedContainerColor = SurfaceCard, unfocusedContainerColor = SurfaceCard))
                        TextField(value = expDay, onValueChange = { expDay = it }, modifier = Modifier.weight(1f), colors = TextFieldDefaults.colors(focusedContainerColor = SurfaceCard, unfocusedContainerColor = SurfaceCard))
                        TextField(value = expHour, onValueChange = { expHour = it }, modifier = Modifier.weight(1f), colors = TextFieldDefaults.colors(focusedContainerColor = SurfaceCard, unfocusedContainerColor = SurfaceCard))
                        TextField(value = expMinute, onValueChange = { expMinute = it }, modifier = Modifier.weight(1f), colors = TextFieldDefaults.colors(focusedContainerColor = SurfaceCard, unfocusedContainerColor = SurfaceCard))
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        isSavedState.value = true
                        viewModel.clearTransactionDraft()
                        val amt = expenseAmountText.toDoubleOrNull() ?: 0.0
                        if (expenseMemberId != null && expenseFromAccountId != null && expenseCategorySelection.isNotEmpty() && amt > 0.0) {
                            val calendar = Calendar.getInstance()
                            calendar.set(
                                expYear.toIntOrNull() ?: 2026,
                                (expMonth.toIntOrNull() ?: 6) - 1,
                                expDay.toIntOrNull() ?: 19,
                                expHour.toIntOrNull() ?: 14,
                                expMinute.toIntOrNull() ?: 0
                            )
                            viewModel.recordFinanceTransaction(
                                memberId = expenseMemberId!!,
                                type = "EXPENSE",
                                fromAccountId = expenseFromAccountId,
                                fromCategory = null,
                                toAccountId = null,
                                toCategory = expenseCategorySelection,
                                amount = amt,
                                note = expenseNoteText.trim(),
                                timestamp = calendar.timeInMillis
                            )
                        }
                        showRecordExpense = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = WaterBlue, contentColor = Color.Black)
                ) {
                    Text("Commit Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { handleDismissAttempt() }) {
                    Text("Close", color = Color.White)
                }
            }
        )
    }

    // ====================================================
    // RECORD INCOME MODAL SUBPANEL
    // ====================================================
    if (showRecordIncome) {
        var incomeMemberId by remember { mutableStateOf<Int?>(selectedMemberId ?: familyMembers.firstOrNull()?.id) }
        var incomeCategorySelection by remember { mutableStateOf("") }
        var incomeToAccountId by remember { mutableStateOf<Int?>(null) }
        var incomeAmountText by remember { mutableStateOf("") }
        var incomeNoteText by remember { mutableStateOf("") }

        // Date Picker states
        var incYear by remember { mutableStateOf("2026") }
        var incMonth by remember { mutableStateOf("06") }
        var incDay by remember { mutableStateOf("19") }
        var incHour by remember { mutableStateOf("14") }
        var incMinute by remember { mutableStateOf("00") }

        val memberAccounts = accounts.filter { it.memberId == incomeMemberId && (it.categoryType == "CURRENT_ASSETS" || it.categoryType == "CURRENT_LIABILITIES") }
        val incomeCats = categories.filter { it.type == "INCOME" }

        var showUnsavedDialog by remember { mutableStateOf(false) }

        val handleDismissAttempt = {
            if (incomeAmountText.isNotEmpty() || incomeNoteText.isNotEmpty() || incomeCategorySelection.isNotEmpty()) {
                showUnsavedDialog = true
            } else {
                showRecordIncome = false
            }
        }

        if (showUnsavedDialog) {
            AlertDialog(
                onDismissRequest = { showUnsavedDialog = false },
                title = { Text("Unsaved Changes", color = Color.White) },
                text = { Text("You have unsaved changes. Do you want to discard them?", color = Color.LightGray) },
                containerColor = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.05f),
                confirmButton = {
                    TextButton(onClick = {
                        showUnsavedDialog = false
                    }) {
                        Text("Resume Editing", color = WaterBlue)
                    }
                },
                dismissButton = {
                    TextButton(onClick = {
                        showUnsavedDialog = false
                        showRecordIncome = false
                    }) {
                        Text("Discard", color = Color(0xFFF9325D))
                    }
                }
            )
        }

        AlertDialog(
            onDismissRequest = { handleDismissAttempt() },
            title = { Text("Record Custom Income Bookflow", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp) },
            containerColor = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.05f),
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    // Member selection
                    Text("RESPONSIBLE FAMILY MEMBER", fontSize = 9.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        familyMembers.forEach { fm ->
                            val isChosen = incomeMemberId == fm.id
                            FilterChip(
                                selected = isChosen,
                                onClick = {
                                    incomeMemberId = fm.id
                                    incomeToAccountId = null
                                },
                                label = { Text(fm.name, fontSize = 11.sp) }
                            )
                        }
                    }

                    // Category dropdown
                    Text("SOURCE INCOME CATEGORY", fontSize = 9.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                    if (incomeCats.isEmpty()) {
                        Text("No custom Income Categories. Add some in settings first.", color = AlertRed, fontSize = 11.sp)
                    } else {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            incomeCats.forEach { ic ->
                                val isChosen = incomeCategorySelection == ic.name
                                FilterChip(
                                    selected = isChosen,
                                    onClick = { incomeCategorySelection = ic.name },
                                    label = { Text(ic.name, fontSize = 11.sp) }
                                )
                            }
                        }
                    }

                    // Destination account selections
                    Text("DESTINATION ACCOUNT", fontSize = 9.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                    if (memberAccounts.isEmpty()) {
                        Text("No accounts custom configured.", color = AlertRed, fontSize = 11.sp)
                    } else {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            memberAccounts.forEach { acc ->
                                val isChosen = incomeToAccountId == acc.id
                                FilterChip(
                                    selected = isChosen,
                                    onClick = { incomeToAccountId = acc.id },
                                    label = { Text("${acc.name} (₹${String.format("%,.2f", getAccountBalance(acc))})", fontSize = 11.sp) }
                                )
                            }
                        }
                    }

                    // Amount Text
                    TextField(
                        value = incomeAmountText,
                        onValueChange = { incomeAmountText = it },
                        placeholder = { Text("Income Value / Profit (₹)") },
                        colors = TextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.LightGray,
                            focusedContainerColor = SurfaceCard,
                            unfocusedContainerColor = SurfaceCard
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Note Text
                    TextField(
                        value = incomeNoteText,
                        onValueChange = { incomeNoteText = it },
                        placeholder = { Text("Notes / Comments") },
                        colors = TextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.LightGray,
                            focusedContainerColor = SurfaceCard,
                            unfocusedContainerColor = SurfaceCard
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Date Selection Input
                    Text("BOOKFLOW TIME (YEAR-MONTH-DAY HOUR:MINUTE)", fontSize = 9.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextField(value = incYear, onValueChange = { incYear = it }, modifier = Modifier.weight(1.5f), colors = TextFieldDefaults.colors(focusedContainerColor = SurfaceCard, unfocusedContainerColor = SurfaceCard))
                        TextField(value = incMonth, onValueChange = { incMonth = it }, modifier = Modifier.weight(1f), colors = TextFieldDefaults.colors(focusedContainerColor = SurfaceCard, unfocusedContainerColor = SurfaceCard))
                        TextField(value = incDay, onValueChange = { incDay = it }, modifier = Modifier.weight(1f), colors = TextFieldDefaults.colors(focusedContainerColor = SurfaceCard, unfocusedContainerColor = SurfaceCard))
                        TextField(value = incHour, onValueChange = { incHour = it }, modifier = Modifier.weight(1f), colors = TextFieldDefaults.colors(focusedContainerColor = SurfaceCard, unfocusedContainerColor = SurfaceCard))
                        TextField(value = incMinute, onValueChange = { incMinute = it }, modifier = Modifier.weight(1f), colors = TextFieldDefaults.colors(focusedContainerColor = SurfaceCard, unfocusedContainerColor = SurfaceCard))
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val amt = incomeAmountText.toDoubleOrNull() ?: 0.0
                        if (incomeMemberId != null && incomeToAccountId != null && incomeCategorySelection.isNotEmpty() && amt > 0.0) {
                            val calendar = Calendar.getInstance()
                            calendar.set(
                                incYear.toIntOrNull() ?: 2026,
                                (incMonth.toIntOrNull() ?: 6) - 1,
                                incDay.toIntOrNull() ?: 19,
                                incHour.toIntOrNull() ?: 14,
                                incMinute.toIntOrNull() ?: 0
                            )
                            viewModel.recordFinanceTransaction(
                                memberId = incomeMemberId!!,
                                type = "INCOME",
                                fromAccountId = null,
                                fromCategory = incomeCategorySelection,
                                toAccountId = incomeToAccountId,
                                toCategory = null,
                                amount = amt,
                                note = incomeNoteText.trim(),
                                timestamp = calendar.timeInMillis
                            )
                        }
                        showRecordIncome = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = WaterBlue, contentColor = Color.Black)
                ) {
                    Text("Commit Income")
                }
            },
            dismissButton = {
                TextButton(onClick = { handleDismissAttempt() }) {
                    Text("Close", color = Color.White)
                }
            }
        )
    }

    // ====================================================
    // RECORD TRANSFER MODAL SUBPANEL
    // ====================================================
    if (showRecordTransfer) {
        var transferMemberId by remember { mutableStateOf<Int?>(selectedMemberId ?: familyMembers.firstOrNull()?.id) }
        var transferFromAccountId by remember { mutableStateOf<Int?>(null) }
        var transferToAccountId by remember { mutableStateOf<Int?>(null) }
        var transferAmountText by remember { mutableStateOf("") }
        var transferNoteText by remember { mutableStateOf("") }

        // Date selection
        var trsfYear by remember { mutableStateOf("2026") }
        var trsfMonth by remember { mutableStateOf("06") }
        var trsfDay by remember { mutableStateOf("19") }
        var trsfHour by remember { mutableStateOf("14") }
        var trsfMinute by remember { mutableStateOf("00") }

        val memberAccounts = accounts.filter { it.memberId == transferMemberId && (it.categoryType == "CURRENT_ASSETS" || it.categoryType == "CURRENT_LIABILITIES") }

        AlertDialog(
            onDismissRequest = { showRecordTransfer = false },
            title = { Text("Transfer Funds Internally", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp) },
            containerColor = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.05f),
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    // Member choice
                    Text("RESPONSIBLE FAMILY MEMBER", fontSize = 9.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        familyMembers.forEach { fm ->
                            val isChosen = transferMemberId == fm.id
                            FilterChip(
                                selected = isChosen,
                                onClick = {
                                    transferMemberId = fm.id
                                    transferFromAccountId = null
                                    transferToAccountId = null
                                },
                                label = { Text(fm.name, fontSize = 11.sp) }
                            )
                        }
                    }

                    // Source account choices
                    Text("FROM ACCOUNT", fontSize = 9.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                    if (memberAccounts.isEmpty()) {
                        Text("No accounts custom configured.", color = AlertRed, fontSize = 11.sp)
                    } else {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            memberAccounts.forEach { acc ->
                                val isChosen = transferFromAccountId == acc.id
                                FilterChip(
                                    selected = isChosen,
                                    onClick = { transferFromAccountId = acc.id },
                                    label = { Text("${acc.name} (₹${String.format("%,.2f", getAccountBalance(acc))})", fontSize = 11.sp) }
                                )
                            }
                        }
                    }

                    // Destination account choices
                    Text("TO ACCOUNT", fontSize = 9.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                    if (memberAccounts.isEmpty()) {
                        Text("No accounts custom configured.", color = AlertRed, fontSize = 11.sp)
                    } else {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            memberAccounts.forEach { acc ->
                                val isChosen = transferToAccountId == acc.id
                                FilterChip(
                                    selected = isChosen,
                                    onClick = { transferToAccountId = acc.id },
                                    label = { Text("${acc.name} (₹${String.format("%,.2f", getAccountBalance(acc))})", fontSize = 11.sp) }
                                )
                            }
                        }
                    }

                    // Amount Text
                    TextField(
                        value = transferAmountText,
                        onValueChange = { transferAmountText = it },
                        placeholder = { Text("Value to Transfer (₹)") },
                        colors = TextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.LightGray,
                            focusedContainerColor = SurfaceCard,
                            unfocusedContainerColor = SurfaceCard
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Note text
                    TextField(
                        value = transferNoteText,
                        onValueChange = { transferNoteText = it },
                        placeholder = { Text("Transfer details") },
                        colors = TextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.LightGray,
                            focusedContainerColor = SurfaceCard,
                            unfocusedContainerColor = SurfaceCard
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Date select input
                    Text("BOOKFLOW TIME (YEAR-MONTH-DAY HOUR:MINUTE)", fontSize = 9.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextField(value = trsfYear, onValueChange = { trsfYear = it }, modifier = Modifier.weight(1.5f), colors = TextFieldDefaults.colors(focusedContainerColor = SurfaceCard, unfocusedContainerColor = SurfaceCard))
                        TextField(value = trsfMonth, onValueChange = { trsfMonth = it }, modifier = Modifier.weight(1f), colors = TextFieldDefaults.colors(focusedContainerColor = SurfaceCard, unfocusedContainerColor = SurfaceCard))
                        TextField(value = trsfDay, onValueChange = { trsfDay = it }, modifier = Modifier.weight(1f), colors = TextFieldDefaults.colors(focusedContainerColor = SurfaceCard, unfocusedContainerColor = SurfaceCard))
                        TextField(value = trsfHour, onValueChange = { trsfHour = it }, modifier = Modifier.weight(1f), colors = TextFieldDefaults.colors(focusedContainerColor = SurfaceCard, unfocusedContainerColor = SurfaceCard))
                        TextField(value = trsfMinute, onValueChange = { trsfMinute = it }, modifier = Modifier.weight(1f), colors = TextFieldDefaults.colors(focusedContainerColor = SurfaceCard, unfocusedContainerColor = SurfaceCard))
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val amt = transferAmountText.toDoubleOrNull() ?: 0.0
                        if (transferMemberId != null && transferFromAccountId != null && transferToAccountId != null && amt > 0.0) {
                            val calendar = Calendar.getInstance()
                            calendar.set(
                                trsfYear.toIntOrNull() ?: 2026,
                                (trsfMonth.toIntOrNull() ?: 6) - 1,
                                trsfDay.toIntOrNull() ?: 19,
                                trsfHour.toIntOrNull() ?: 14,
                                trsfMinute.toIntOrNull() ?: 0
                            )
                            viewModel.recordFinanceTransaction(
                                memberId = transferMemberId!!,
                                type = "TRANSFER",
                                fromAccountId = transferFromAccountId,
                                fromCategory = null,
                                toAccountId = transferToAccountId,
                                toCategory = null,
                                amount = amt,
                                note = transferNoteText.trim(),
                                timestamp = calendar.timeInMillis
                            )
                        }
                        showRecordTransfer = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = WaterBlue, contentColor = Color.Black)
                ) {
                    Text("Execute Transfer")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRecordTransfer = false }) {
                    Text("Close", color = Color.White)
                }
            }
        )
    }

    if (showTransactionHistory) {
        val combinedList = remember(txs, logs, accounts, familyMembers) {
            val list = mutableListOf<CombinedHistoryItem>()

            // 1. Transactions
            txs.forEach { t ->
                val memberName = familyMembers.find { it.id == t.memberId }?.name ?: "Unknown"
                val fromAccName = accounts.find { it.id == t.fromAccountId }?.name ?: t.fromCategory ?: "None"
                val toAccName = accounts.find { it.id == t.toAccountId }?.name ?: t.toCategory ?: "None"

                val (title, colorHex, isImpact) = when (t.type) {
                    "EXPENSE" -> Triple("Expense: $fromAccName ➔ $toAccName", AlertRed, false)
                    "INCOME" -> Triple("Income: $fromAccName ➔ $toAccName", SuccessGreen, true)
                    else -> Triple("Transfer: $fromAccName ➔ $toAccName", WaterBlue, true)
                }

                list.add(
                    CombinedHistoryItem(
                        id = "tx_${t.id}",
                        timestamp = t.timestamp,
                        title = title,
                        type = t.type,
                        subtitle = "Member: $memberName",
                        amount = t.amount,
                        note = t.note,
                        isAssetImpact = isImpact,
                        detailString = t.type
                    )
                )
            }

            // 2. Logs / Adjustments
            logs.forEach { l ->
                val acc = accounts.find { it.id == l.accountId }
                val accName = acc?.name ?: "Unknown Account"
                val memberName = acc?.let { a -> familyMembers.find { it.id == a.memberId }?.name } ?: "Unknown"

                val (title, colorHex, isImpact) = when (l.logType) {
                    "INITIAL" -> Triple("Account Created: $accName", WaterBlue, true)
                    "APPRECIATION" -> Triple("Adjustment Check-In: $accName (Value Appreciated)", SuccessGreen, true)
                    "DEPRECIATION" -> Triple("Adjustment Check-In: $accName (Value Depreciated)", AlertRed, false)
                    "INTEREST_ACCRUED" -> Triple("Adjustment Check-In: $accName (Interest Accrued)", SuccessGreen, true)
                    "PAID" -> Triple("Adjustment Check-In: $accName (Amortization Payment)", AlertRed, false)
                    else -> Triple("Log: $accName (${l.logType})", Color.Gray, true)
                }

                list.add(
                    CombinedHistoryItem(
                        id = "log_${l.id}",
                        timestamp = l.timestamp,
                        title = title,
                        type = l.logType,
                        subtitle = "Member: $memberName",
                        amount = l.amount,
                        note = "Account manual audit & check-in",
                        isAssetImpact = isImpact,
                        detailString = l.logType
                    )
                )
            }

            list.sortByDescending { it.timestamp }
            list
        }

        AlertDialog(
            onDismissRequest = { showTransactionHistory = false },
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "CORE LEDGER HISTORY",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = WaterBlue
                    )
                    IconButton(onClick = { showTransactionHistory = false }) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                    }
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(450.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Unified log of all cash flows, adjustments, audits, and account creation values in reverse chronological order.",
                        fontSize = 11.sp,
                        color = Color.LightGray
                    )
                    
                    if (combinedList.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("No transactions logged yet.", color = Color.Gray, fontSize = 13.sp)
                        }
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(combinedList, key = { it.id }) { item ->
                                val dateStr = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()).format(Date(item.timestamp))
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = SurfaceCard)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = item.title,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White,
                                                modifier = Modifier.weight(1f)
                                            )
                                            Text(
                                                text = if (item.isAssetImpact) "+$${String.format("%.2f", item.amount)}" else "-$${String.format("%.2f", item.amount)}",
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (item.isAssetImpact) SuccessGreen else AlertRed
                                            )
                                        }
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = item.subtitle,
                                                fontSize = 10.sp,
                                                color = Color.Gray
                                            )
                                            Text(
                                                text = dateStr,
                                                fontSize = 10.sp,
                                                color = Color.Gray
                                            )
                                        }
                                        if (item.note.isNotEmpty()) {
                                            Text(
                                                text = "Note: ${item.note}",
                                                fontSize = 10.sp,
                                                color = Color.LightGray.copy(alpha = 0.8f)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {}
        )
    }
}
