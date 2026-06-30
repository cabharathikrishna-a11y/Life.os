package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

// ==========================================
// 1. Entities
// ==========================================

@Entity(tableName = "tasks")
data class Task(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val description: String = "",
    val estimatedMinutes: Int = 30,
    val actualMinutes: Int = 0,
    val isCompleted: Boolean = false,
    val parentTaskId: Int? = null,
    val listCategory: String = "Inbox",
    val timeBlockTimestamp: Long? = null,
    val nagModeEnabled: Boolean = false,
    val nagIntervalMinutes: Int = 5,
    val priority: String = "MEDIUM", // "HIGH", "MEDIUM", "LOW"
    val dueDateString: String = "", // "YYYY-MM-DD" style execution date target
    val orderIndex: Int = 0
)

@Entity(tableName = "habits")
data class Habit(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val streakCount: Int = 0,
    val lastCompletedTimestamp: Long? = null,
    val listCategory: String = "Health & Vigor",
    val timeOfDay: String = "Morning",
    val targetCount: Int = 1,
    val frequency: String = "DAILY", // "DAILY", "WEEKLY", "MONTHLY", "MONTHLY_ONCE"
    val weeklyDay: Int = 2, // 2 = Calendar.MONDAY
    val monthlyStartDate: Int = 1,
    val monthlyEndDate: Int = 30,
    val orderIndex: Int = 0,
    val scheduledTime: String = "08:00",
    val isReminderEnabled: Boolean = false
)

@Entity(tableName = "habit_completions",
        foreignKeys = [
            ForeignKey(
                entity = Habit::class,
                parentColumns = ["id"],
                childColumns = ["habitId"],
                onDelete = ForeignKey.CASCADE
            )
        ],
        indices = [Index(value = ["habitId"])])
data class HabitCompletion(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val habitId: Int,
    val dateString: String // "YYYY-MM-DD"
)

@Entity(tableName = "journal_entries")
data class JournalEntry(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val text: String,
    val dateString: String, // "YYYY-MM-DD"
    val timestamp: Long = System.currentTimeMillis(),
    val attachmentsJson: String = "" // Serialized as a JSON list of attachment details
)

@Entity(tableName = "ledger_entries")
data class LedgerEntry(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val type: String, // "INCOME" or "EXPENSE"
    val amount: Double,
    val categoryTag: String,
    val note: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "deadlines")
data class Deadline(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val targetTimestamp: Long,
    val isCompleted: Boolean = false
)

@Entity(tableName = "financial_goals")
data class FinancialGoal(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val targetAmount: Double,
    val type: String = "SAVINGS", // "SAVINGS" or "BUDGET"
    val categoryTag: String = "General"
)

@Entity(tableName = "contacts")
data class Contact(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val firstName: String,
    val middleName: String = "",
    val lastName: String,
    val jobTitle: String = "",
    val email: String = "",
    val address: String = "",
    val phone: String = "",
    val dobString: String = "", // "YYYY-MM-DD" or "MM-DD"
    val photoUri: String? = null,
    val anniversaryString: String = "",
    val additionalFieldsJson: String = "",
    val additionalDatesJson: String = "",
    val folder: String = "All",
    val attachedFilesJson: String = "",
    val systemContactId: Long? = null,
    val googleContactId: String? = null
)

@Entity(tableName = "app_files")
data class AppFile(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val path: String, // e.g. "/docs" or "/media"
    val size: Long,
    val mimeType: String,
    val uriString: String,
    val timestamp: Long = System.currentTimeMillis()
)

// ==========================================
// 2. DAOs
// ==========================================

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks ORDER BY id DESC")
    fun getAllTasks(): Flow<List<Task>>

    @Query("SELECT * FROM tasks ORDER BY id DESC")
    suspend fun getAllTasksDirect(): List<Task>

    @Query("SELECT * FROM tasks WHERE parentTaskId = :parentId")
    fun getSubtasks(parentId: Int): Flow<List<Task>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: Task): Long

    @Update
    suspend fun updateTask(task: Task)

    @Query("SELECT * FROM tasks WHERE id = :id LIMIT 1")
    suspend fun getTaskById(id: Int): Task?

    @Delete
    suspend fun deleteTask(task: Task)

    @Query("DELETE FROM tasks WHERE parentTaskId = :parentId")
    suspend fun deleteSubtasks(parentId: Int)
}

@Dao
interface HabitDao {
    @Query("SELECT * FROM habits ORDER BY orderIndex ASC, id ASC")
    fun getAllHabits(): Flow<List<Habit>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHabit(habit: Habit): Long

    @Update
    suspend fun updateHabit(habit: Habit)

    @Delete
    suspend fun deleteHabit(habit: Habit)

    // Completions
    @Query("SELECT * FROM habit_completions WHERE habitId = :habitId")
    fun getCompletionsForHabit(habitId: Int): Flow<List<HabitCompletion>>

    @Query("SELECT * FROM habit_completions")
    fun getAllCompletions(): Flow<List<HabitCompletion>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCompletion(completion: HabitCompletion)

    @Query("DELETE FROM habit_completions WHERE habitId = :habitId AND dateString = :dateString")
    suspend fun deleteCompletion(habitId: Int, dateString: String)
}

@Dao
interface JournalDao {
    @Query("SELECT * FROM journal_entries ORDER BY timestamp DESC")
    fun getAllJournalEntries(): Flow<List<JournalEntry>>

    @Query("SELECT * FROM journal_entries WHERE title LIKE :query OR text LIKE :query")
    fun searchJournalEntries(query: String): Flow<List<JournalEntry>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertJournalEntry(entry: JournalEntry): Long

    @Delete
    suspend fun deleteJournalEntry(entry: JournalEntry)
}

@Dao
interface LedgerDao {
    @Query("SELECT * FROM ledger_entries ORDER BY timestamp DESC")
    fun getAllLedgerEntries(): Flow<List<LedgerEntry>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLedgerEntry(entry: LedgerEntry)

    @Update
    suspend fun updateLedgerEntry(entry: LedgerEntry)

    @Delete
    suspend fun deleteLedgerEntry(entry: LedgerEntry)
}

@Dao
interface DeadlineDao {
    @Query("SELECT * FROM deadlines ORDER BY isCompleted ASC, targetTimestamp ASC")
    fun getAllDeadlines(): Flow<List<Deadline>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDeadline(deadline: Deadline): Long

    @Update
    suspend fun updateDeadline(deadline: Deadline)

    @Delete
    suspend fun deleteDeadline(deadline: Deadline)
}

@Dao
interface FinancialGoalDao {
    @Query("SELECT * FROM financial_goals ORDER BY id DESC")
    fun getAllFinancialGoals(): Flow<List<FinancialGoal>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFinancialGoal(goal: FinancialGoal): Long

    @Update
    suspend fun updateFinancialGoal(goal: FinancialGoal)

    @Delete
    suspend fun deleteFinancialGoal(goal: FinancialGoal)
}

@Dao
interface ContactDao {
    @Query("SELECT * FROM contacts ORDER BY firstName ASC, lastName ASC")
    fun getAllContacts(): Flow<List<Contact>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertContact(contact: Contact): Long

    @Update
    suspend fun updateContact(contact: Contact)

    @Delete
    suspend fun deleteContact(contact: Contact)
}

@Entity(tableName = "custom_lists")
data class CustomList(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val colorHex: String = "#2196F3",
    val viewType: String = "List", // "List", "Kanban", "Timeline"
    val parentListName: String? = null // Sublist relationship: null if primary list
)

@Dao
interface AppFileDao {
    @Query("SELECT * FROM app_files ORDER BY timestamp DESC")
    fun getAllFiles(): Flow<List<AppFile>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFile(file: AppFile): Long

    @Delete
    suspend fun deleteFile(file: AppFile)
}

@Dao
interface CustomListDao {
    @Query("SELECT * FROM custom_lists ORDER BY name ASC")
    fun getAllLists(): Flow<List<CustomList>>

    @Query("SELECT * FROM custom_lists ORDER BY name ASC")
    suspend fun getAllListsDirect(): List<CustomList>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertList(list: CustomList): Long

    @Update
    suspend fun updateList(list: CustomList)

    @Delete
    suspend fun deleteList(list: CustomList)
}

// ==========================================
// Family Ledger Entities
// ==========================================

@Entity(tableName = "family_members")
data class FamilyMember(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String
)

@Entity(tableName = "financial_accounts")
data class FinancialAccount(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val memberId: Int, // id of FamilyMember
    val name: String,
    val categoryType: String, // "LONG_TERM_ASSETS", "CURRENT_ASSETS", "LONG_TERM_LIABILITIES", "CURRENT_LIABILITIES"
    val openingValue: Double
)

@Entity(tableName = "financial_logs")
data class FinancialLog(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val accountId: Int,
    val logType: String, // "APPRECIATION", "DEPRECIATION", "INTEREST_ACCRUED", "PAID", "INITIAL"
    val amount: Double,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "finance_transactions")
data class FinanceTransaction(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val memberId: Int,
    val type: String, // "EXPENSE", "INCOME", "TRANSFER"
    val fromAccountId: Int? = null,
    val fromCategory: String? = null,
    val toAccountId: Int? = null,
    val toCategory: String? = null,
    val amount: Double,
    val timestamp: Long = System.currentTimeMillis(),
    val note: String = ""
)

@Entity(tableName = "finance_categories")
data class FinanceCategory(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val type: String // "INCOME" or "EXPENSE"
)

// ==========================================
// Family Ledger DAOs
// ==========================================

@Dao
interface FamilyMemberDao {
    @Query("SELECT * FROM family_members ORDER BY name ASC")
    fun getAllMembers(): Flow<List<FamilyMember>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMember(member: FamilyMember): Long

    @Delete
    suspend fun deleteMember(member: FamilyMember)
}

@Dao
interface FinancialAccountDao {
    @Query("SELECT * FROM financial_accounts")
    fun getAllAccounts(): Flow<List<FinancialAccount>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAccount(account: FinancialAccount): Long

    @Delete
    suspend fun deleteAccount(account: FinancialAccount)
}

@Dao
interface FinancialLogDao {
    @Query("SELECT * FROM financial_logs ORDER BY timestamp ASC")
    fun getAllLogs(): Flow<List<FinancialLog>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: FinancialLog): Long

    @Delete
    suspend fun deleteLog(log: FinancialLog)
}

@Dao
interface FinanceTransactionDao {
    @Query("SELECT * FROM finance_transactions ORDER BY timestamp DESC")
    fun getAllTransactions(): Flow<List<FinanceTransaction>>

    @Query("SELECT * FROM finance_transactions ORDER BY timestamp DESC")
    suspend fun getAllTransactionsDirect(): List<FinanceTransaction>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(transaction: FinanceTransaction): Long

    @Delete
    suspend fun deleteTransaction(transaction: FinanceTransaction)
}

@Dao
interface FinanceCategoryDao {
    @Query("SELECT * FROM finance_categories ORDER BY name ASC")
    fun getAllCategories(): Flow<List<FinanceCategory>>

    @Query("SELECT * FROM finance_categories ORDER BY name ASC")
    suspend fun getAllCategoriesDirect(): List<FinanceCategory>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategory(category: FinanceCategory): Long

    @Delete
    suspend fun deleteCategory(category: FinanceCategory)
}

// ==========================================
// 3. Database
// ==========================================

@Database(
    entities = [
        Task::class,
        Habit::class,
        HabitCompletion::class,
        JournalEntry::class,
        LedgerEntry::class,
        Deadline::class,
        FinancialGoal::class,
        Contact::class,
        AppFile::class,
        CustomList::class,
        FamilyMember::class,
        FinancialAccount::class,
        FinancialLog::class,
        FinanceTransaction::class,
        FinanceCategory::class
    ],
    version = 11,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao
    abstract fun habitDao(): HabitDao
    abstract fun journalDao(): JournalDao
    abstract fun ledgerDao(): LedgerDao
    abstract fun deadlineDao(): DeadlineDao
    abstract fun financialGoalDao(): FinancialGoalDao
    abstract fun contactDao(): ContactDao
    abstract fun appFileDao(): AppFileDao
    abstract fun customListDao(): CustomListDao
    abstract fun familyMemberDao(): FamilyMemberDao
    abstract fun financialAccountDao(): FinancialAccountDao
    abstract fun financialLogDao(): FinancialLogDao
    abstract fun financeTransactionDao(): FinanceTransactionDao
    abstract fun financeCategoryDao(): FinanceCategoryDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: android.content.Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = androidx.room.Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java, "life_os_database"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}
