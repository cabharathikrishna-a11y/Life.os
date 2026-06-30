package com.example.data

import kotlinx.coroutines.flow.Flow

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

    // Custom List Operations
    val allLists: Flow<List<CustomList>> = customListDao.getAllLists()

    suspend fun insertList(list: CustomList): Long {
        return customListDao.insertList(list)
    }

    suspend fun updateList(list: CustomList) {
        customListDao.updateList(list)
    }

    suspend fun deleteList(list: CustomList) {
        customListDao.deleteList(list)
    }

    // Task Operations
    val allTasks: Flow<List<Task>> = taskDao.getAllTasks()
    
    suspend fun insertTask(task: Task): Long {
        return taskDao.insertTask(task)
    }

    suspend fun updateTask(task: Task) {
        taskDao.updateTask(task)
    }

    suspend fun deleteTask(task: Task) {
        taskDao.deleteTask(task)
        // Also delete subtasks if it's a parent
        taskDao.deleteSubtasks(task.id)
    }

    // Habit Operations
    val allHabits: Flow<List<Habit>> = habitDao.getAllHabits()
    val allCompletions: Flow<List<HabitCompletion>> = habitDao.getAllCompletions()

    suspend fun insertHabit(habit: Habit): Long {
        return habitDao.insertHabit(habit)
    }

    suspend fun updateHabit(habit: Habit) {
        habitDao.updateHabit(habit)
    }

    suspend fun deleteHabit(habit: Habit) {
        habitDao.deleteHabit(habit)
    }

    suspend fun insertHabitCompletion(habitId: Int, dateString: String) {
        habitDao.insertCompletion(HabitCompletion(habitId = habitId, dateString = dateString))
    }

    suspend fun deleteHabitCompletion(habitId: Int, dateString: String) {
        habitDao.deleteCompletion(habitId, dateString)
    }

    // Journal Operations
    val allJournalEntries: Flow<List<JournalEntry>> = journalDao.getAllJournalEntries()

    fun searchJournal(query: String): Flow<List<JournalEntry>> {
        return journalDao.searchJournalEntries("%$query%")
    }

    suspend fun insertJournal(entry: JournalEntry): Long {
        return journalDao.insertJournalEntry(entry)
    }

    suspend fun deleteJournal(entry: JournalEntry) {
        journalDao.deleteJournalEntry(entry)
    }

    // Financial Operations
    val allLedgerEntries: Flow<List<LedgerEntry>> = ledgerDao.getAllLedgerEntries()

    suspend fun insertLedger(entry: LedgerEntry) {
        ledgerDao.insertLedgerEntry(entry)
    }

    suspend fun deleteLedger(entry: LedgerEntry) {
        ledgerDao.deleteLedgerEntry(entry)
    }

    // Deadline Operations
    val allDeadlines: Flow<List<Deadline>> = deadlineDao.getAllDeadlines()

    suspend fun insertDeadline(deadline: Deadline): Long {
        return deadlineDao.insertDeadline(deadline)
    }

    suspend fun updateDeadline(deadline: Deadline) {
        deadlineDao.updateDeadline(deadline)
    }

    suspend fun deleteDeadline(deadline: Deadline) {
        deadlineDao.deleteDeadline(deadline)
    }

    // Financial Goal Operations
    val allFinancialGoals: Flow<List<FinancialGoal>> = financialGoalDao.getAllFinancialGoals()

    suspend fun insertFinancialGoal(goal: FinancialGoal): Long {
        return financialGoalDao.insertFinancialGoal(goal)
    }

    suspend fun updateFinancialGoal(goal: FinancialGoal) {
        financialGoalDao.updateFinancialGoal(goal)
    }

    suspend fun deleteFinancialGoal(goal: FinancialGoal) {
        financialGoalDao.deleteFinancialGoal(goal)
    }

    // Contact Operations
    val allContacts: Flow<List<Contact>> = contactDao.getAllContacts()

    suspend fun insertContact(contact: Contact): Long {
        return contactDao.insertContact(contact)
    }

    suspend fun updateContact(contact: Contact) {
        contactDao.updateContact(contact)
    }

    suspend fun deleteContact(contact: Contact) {
        contactDao.deleteContact(contact)
    }

    // File Operations
    val allFiles: Flow<List<AppFile>> = appFileDao.getAllFiles()

    suspend fun insertFile(file: AppFile): Long {
        return appFileDao.insertFile(file)
    }

    suspend fun deleteFile(file: AppFile) {
        appFileDao.deleteFile(file)
    }

    // Family Ledger Operations
    val allFamilyMembers: Flow<List<FamilyMember>> = familyMemberDao.getAllMembers()
    val allFinancialAccounts: Flow<List<FinancialAccount>> = financialAccountDao.getAllAccounts()
    val allFinancialLogs: Flow<List<FinancialLog>> = financialLogDao.getAllLogs()
    val allFinanceTransactions: Flow<List<FinanceTransaction>> = financeTransactionDao.getAllTransactions()
    val allFinanceCategories: Flow<List<FinanceCategory>> = financeCategoryDao.getAllCategories()

    suspend fun insertFamilyMember(member: FamilyMember): Long {
        return familyMemberDao.insertMember(member)
    }

    suspend fun deleteFamilyMember(member: FamilyMember) {
        familyMemberDao.deleteMember(member)
    }

    suspend fun insertFinancialAccount(account: FinancialAccount): Long {
        return financialAccountDao.insertAccount(account)
    }

    suspend fun deleteFinancialAccount(account: FinancialAccount) {
        financialAccountDao.deleteAccount(account)
    }

    suspend fun insertFinancialLog(log: FinancialLog): Long {
        return financialLogDao.insertLog(log)
    }

    suspend fun deleteFinancialLog(log: FinancialLog) {
        financialLogDao.deleteLog(log)
    }

    suspend fun insertFinanceTransaction(transaction: FinanceTransaction): Long {
        return financeTransactionDao.insertTransaction(transaction)
    }

    suspend fun deleteFinanceTransaction(transaction: FinanceTransaction) {
        financeTransactionDao.deleteTransaction(transaction)
    }

    suspend fun insertFinanceCategory(category: FinanceCategory): Long {
        return financeCategoryDao.insertCategory(category)
    }

    suspend fun deleteFinanceCategory(category: FinanceCategory) {
        financeCategoryDao.deleteCategory(category)
    }
}
