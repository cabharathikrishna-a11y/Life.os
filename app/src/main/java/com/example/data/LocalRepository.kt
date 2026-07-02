package com.example.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.NonCancellable

class LocalRepository(val db: AppDatabase) {

    private val taskDao = db.taskDao()
    private val habitDao = db.habitDao()
    private val journalDao = db.journalDao()
    private val ledgerDao = db.ledgerDao()
    private val deadlineDao = db.deadlineDao()
    private val financialGoalDao = db.financialGoalDao()
    private val contactDao = db.contactDao()
    private val appFileDao = db.appFileDao()
    private val customListDao = db.customListDao()
    private val familyMemberDao = db.familyMemberDao()
    private val financialAccountDao = db.financialAccountDao()
    private val financialLogDao = db.financialLogDao()
    private val financeTransactionDao = db.financeTransactionDao()
    private val financeCategoryDao = db.financeCategoryDao()
    private val focusRecordDao = db.focusRecordDao()

    // Custom List Operations
    val allLists: Flow<List<CustomList>> = customListDao.getAllLists()

    suspend fun insertList(list: CustomList): Long = withContext(NonCancellable) {
        customListDao.insertList(list)
    }

    suspend fun updateList(list: CustomList) = withContext(NonCancellable) {
        customListDao.updateList(list)
    }

    suspend fun deleteList(list: CustomList) = withContext(NonCancellable) {
        customListDao.deleteList(list)
    }

    // Task Operations
    val allTasks: Flow<List<Task>> = taskDao.getAllTasks()
    
    suspend fun insertTask(task: Task): Long = withContext(NonCancellable) {
        taskDao.insertTask(task)
    }

    suspend fun updateTask(task: Task) = withContext(NonCancellable) {
        taskDao.updateTask(task)
    }

    suspend fun deleteTask(task: Task) = withContext(NonCancellable) {
        taskDao.deleteTask(task)
        // Also delete subtasks if it's a parent
        taskDao.deleteSubtasks(task.id)
    }

    // Habit Operations
    val allHabits: Flow<List<Habit>> = habitDao.getAllHabits()
    val allCompletions: Flow<List<HabitCompletion>> = habitDao.getAllCompletions()

    suspend fun insertHabit(habit: Habit): Long = withContext(NonCancellable) {
        habitDao.insertHabit(habit)
    }

    suspend fun updateHabit(habit: Habit) = withContext(NonCancellable) {
        habitDao.updateHabit(habit)
    }

    suspend fun deleteHabit(habit: Habit) = withContext(NonCancellable) {
        habitDao.deleteHabit(habit)
    }

    suspend fun insertHabitCompletion(habitId: Int, dateString: String) = withContext(NonCancellable) {
        habitDao.insertCompletion(HabitCompletion(habitId = habitId, dateString = dateString))
    }

    suspend fun deleteHabitCompletion(habitId: Int, dateString: String) = withContext(NonCancellable) {
        habitDao.deleteCompletion(habitId, dateString)
    }

    // Journal Operations
    val allJournalEntries: Flow<List<JournalEntry>> = journalDao.getAllJournalEntries()

    fun searchJournal(query: String): Flow<List<JournalEntry>> {
        return journalDao.searchJournalEntries("%$query%")
    }

    suspend fun insertJournal(entry: JournalEntry): Long = withContext(NonCancellable) {
        journalDao.insertJournalEntry(entry)
    }

    suspend fun deleteJournal(entry: JournalEntry) = withContext(NonCancellable) {
        journalDao.deleteJournalEntry(entry)
    }

    // Financial Operations
    val allLedgerEntries: Flow<List<LedgerEntry>> = ledgerDao.getAllLedgerEntries()

    suspend fun insertLedger(entry: LedgerEntry) = withContext(NonCancellable) {
        ledgerDao.insertLedgerEntry(entry)
    }

    suspend fun deleteLedger(entry: LedgerEntry) = withContext(NonCancellable) {
        ledgerDao.deleteLedgerEntry(entry)
    }

    // Deadline Operations
    val allDeadlines: Flow<List<Deadline>> = deadlineDao.getAllDeadlines()

    suspend fun insertDeadline(deadline: Deadline): Long = withContext(NonCancellable) {
        deadlineDao.insertDeadline(deadline)
    }

    suspend fun updateDeadline(deadline: Deadline) = withContext(NonCancellable) {
        deadlineDao.updateDeadline(deadline)
    }

    suspend fun deleteDeadline(deadline: Deadline) = withContext(NonCancellable) {
        deadlineDao.deleteDeadline(deadline)
    }

    // Financial Goal Operations
    val allFinancialGoals: Flow<List<FinancialGoal>> = financialGoalDao.getAllFinancialGoals()

    suspend fun insertFinancialGoal(goal: FinancialGoal): Long = withContext(NonCancellable) {
        financialGoalDao.insertFinancialGoal(goal)
    }

    suspend fun updateFinancialGoal(goal: FinancialGoal) = withContext(NonCancellable) {
        financialGoalDao.updateFinancialGoal(goal)
    }

    suspend fun deleteFinancialGoal(goal: FinancialGoal) = withContext(NonCancellable) {
        financialGoalDao.deleteFinancialGoal(goal)
    }

    // Contact Operations
    val allContacts: Flow<List<Contact>> = contactDao.getAllContacts()

    suspend fun insertContact(contact: Contact): Long = withContext(NonCancellable) {
        contactDao.insertContact(contact)
    }

    suspend fun updateContact(contact: Contact) = withContext(NonCancellable) {
        contactDao.updateContact(contact)
    }

    suspend fun deleteContact(contact: Contact) = withContext(NonCancellable) {
        contactDao.deleteContact(contact)
    }

    // File Operations
    val allFiles: Flow<List<AppFile>> = appFileDao.getAllFiles()

    suspend fun insertFile(file: AppFile): Long = withContext(NonCancellable) {
        appFileDao.insertFile(file)
    }

    suspend fun deleteFile(file: AppFile) = withContext(NonCancellable) {
        appFileDao.deleteFile(file)
    }

    // Family Ledger Operations
    val allFamilyMembers: Flow<List<FamilyMember>> = familyMemberDao.getAllMembers()
    val allFinancialAccounts: Flow<List<FinancialAccount>> = financialAccountDao.getAllAccounts()
    val allFinancialLogs: Flow<List<FinancialLog>> = financialLogDao.getAllLogs()
    val allFinanceTransactions: Flow<List<FinanceTransaction>> = financeTransactionDao.getAllTransactions()
    val allFinanceCategories: Flow<List<FinanceCategory>> = financeCategoryDao.getAllCategories()

    suspend fun insertFamilyMember(member: FamilyMember): Long = withContext(NonCancellable) {
        familyMemberDao.insertMember(member)
    }

    suspend fun deleteFamilyMember(member: FamilyMember) = withContext(NonCancellable) {
        familyMemberDao.deleteMember(member)
    }

    suspend fun insertFinancialAccount(account: FinancialAccount): Long = withContext(NonCancellable) {
        financialAccountDao.insertAccount(account)
    }

    suspend fun deleteFinancialAccount(account: FinancialAccount) = withContext(NonCancellable) {
        financialAccountDao.deleteAccount(account)
    }

    suspend fun insertFinancialLog(log: FinancialLog): Long = withContext(NonCancellable) {
        financialLogDao.insertLog(log)
    }

    suspend fun deleteFinancialLog(log: FinancialLog) = withContext(NonCancellable) {
        financialLogDao.deleteLog(log)
    }

    suspend fun insertFinanceTransaction(transaction: FinanceTransaction): Long = withContext(NonCancellable) {
        financeTransactionDao.insertTransaction(transaction)
    }

    suspend fun deleteFinanceTransaction(transaction: FinanceTransaction) = withContext(NonCancellable) {
        financeTransactionDao.deleteTransaction(transaction)
    }

    suspend fun insertFinanceCategory(category: FinanceCategory): Long = withContext(NonCancellable) {
        financeCategoryDao.insertCategory(category)
    }

    suspend fun deleteFinanceCategory(category: FinanceCategory) = withContext(NonCancellable) {
        financeCategoryDao.deleteCategory(category)
    }

    // Focus Record Operations
    val allFocusRecords: Flow<List<FocusRecordEntity>> = focusRecordDao.getAllRecords()

    suspend fun insertFocusRecord(record: FocusRecordEntity): Long = withContext(NonCancellable) {
        focusRecordDao.insertRecord(record)
    }

    suspend fun updateFocusRecord(record: FocusRecordEntity) = withContext(NonCancellable) {
        focusRecordDao.updateRecord(record)
    }

    suspend fun deleteFocusRecord(record: FocusRecordEntity) = withContext(NonCancellable) {
        focusRecordDao.deleteRecord(record)
    }

    suspend fun getFocusRecordsForDate(dateStr: String): List<FocusRecordEntity> {
        return focusRecordDao.getRecordsForDate(dateStr)
    }
}
