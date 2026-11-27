package com.example.data.local

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import android.content.Context
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "active_focus_session")
data class FocusSessionEntity(
    @PrimaryKey
    val id: Int = 1,
    val subject: String,
    val targetSeconds: Long,
    val remainingSeconds: Long,
    val isPaused: Boolean,
    val isActive: Boolean,
    val startTimeMs: Long,
    val lastTickTimeMs: Long,
    val blockedPackages: String // Comma separated packages
)

@Entity(tableName = "blocked_apps")
data class BlockedAppEntity(
    @PrimaryKey
    val packageName: String,
    val appName: String,
    val category: String,
    val isBlocked: Boolean
)

@Dao
interface FocusDao {
    @Query("SELECT * FROM active_focus_session WHERE id = 1 LIMIT 1")
    fun getActiveSessionFlow(): Flow<FocusSessionEntity?>

    @Query("SELECT * FROM active_focus_session WHERE id = 1 LIMIT 1")
    suspend fun getActiveSession(): FocusSessionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveActiveSession(session: FocusSessionEntity)

    @Query("UPDATE active_focus_session SET remainingSeconds = :remainingSeconds, lastTickTimeMs = :lastTickTimeMs WHERE id = 1")
    suspend fun updateRemainingTime(remainingSeconds: Long, lastTickTimeMs: Long)

    @Query("UPDATE active_focus_session SET isPaused = :isPaused WHERE id = 1")
    suspend fun setPaused(isPaused: Boolean)

    @Query("DELETE FROM active_focus_session")
    suspend fun clearActiveSession()

    @Query("SELECT * FROM blocked_apps ORDER BY appName ASC")
    fun getBlockedAppsFlow(): Flow<List<BlockedAppEntity>>

    @Query("SELECT * FROM blocked_apps WHERE isBlocked = 1")
    suspend fun getActiveBlockedApps(): List<BlockedAppEntity>

    @Query("SELECT * FROM blocked_apps")
    suspend fun getAllBlockedApps(): List<BlockedAppEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertDefaultApps(apps: List<BlockedAppEntity>)

    @Query("UPDATE blocked_apps SET isBlocked = :isBlocked WHERE packageName = :packageName")
    suspend fun setAppBlocked(packageName: String, isBlocked: Boolean)
}

@Database(entities = [FocusSessionEntity::class, BlockedAppEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun focusDao(): FocusDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "focus_study_local.db"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
