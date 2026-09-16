package org.cssnr.zipline.log

import android.content.Context
import android.util.Log
import androidx.preference.PreferenceManager
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

enum class LogLevel { DEBUG, INFO, WARNING, ERROR }

@Entity
data class LogEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val level: Int,
    val message: String,
    val timestamp: Long = System.currentTimeMillis(),
) {
    val levelEnum: LogLevel get() = LogLevel.entries[level]
}

@Dao
interface LogDao {
    @Query("SELECT * FROM logentry ORDER BY timestamp DESC")
    fun getAll(): Flow<List<LogEntry>>

    @Query("SELECT * FROM logentry ORDER BY timestamp DESC")
    suspend fun getAllNow(): List<LogEntry>

    @Insert
    suspend fun insert(entry: LogEntry)

    @Query("DELETE FROM logentry")
    suspend fun clearAll()

    @Query("DELETE FROM logentry WHERE timestamp < :before")
    suspend fun deleteOlderThan(before: Long)
}

@Database(entities = [LogEntry::class], version = 1, exportSchema = false)
abstract class LogDatabase : RoomDatabase() {
    abstract fun logDao(): LogDao

    companion object {
        @Volatile
        private var instance: LogDatabase? = null

        fun getInstance(context: Context): LogDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    LogDatabase::class.java,
                    "log-database"
                ).build().also { instance = it }
            }
    }
}

sealed interface LogExportResult {
    data class Success(val text: String) : LogExportResult
    data object Empty : LogExportResult
    data object Error : LogExportResult
}

object AppLogs {

    private const val LOG_TAG = "AppLogs"
    private const val PURGE_DAYS = 7L
    private const val ENABLED_KEY = "enable_debug_logs"

    @Volatile
    private var purged = false

    @Volatile
    private var instance: LogDatabase? = null

    private fun database(context: Context): LogDatabase =
        instance ?: synchronized(this) {
            instance ?: LogDatabase.getInstance(context).also { instance = it }
        }

    private fun isEnabled(context: Context): Boolean =
        PreferenceManager.getDefaultSharedPreferences(context)
            .getBoolean(ENABLED_KEY, true)

    suspend fun log(context: Context, level: LogLevel, message: String) {
        if (!isEnabled(context)) return
        try {
            purgeIfNeeded(context)
            withContext(Dispatchers.IO) {
                database(context).logDao().insert(
                    LogEntry(level = level.ordinal, message = message)
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(LOG_TAG, "Failed to write log entry", e)
        }
    }

    suspend fun d(context: Context, message: String) = log(context, LogLevel.DEBUG, message)

    suspend fun i(context: Context, message: String) = log(context, LogLevel.INFO, message)

    suspend fun w(context: Context, message: String) = log(context, LogLevel.WARNING, message)

    suspend fun e(context: Context, message: String) = log(context, LogLevel.ERROR, message)

    fun getLogs(context: Context): Flow<List<LogEntry>> =
        database(context).logDao().getAll()

    suspend fun clear(context: Context) {
        try {
            withContext(Dispatchers.IO) { database(context).logDao().clearAll() }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(LOG_TAG, "Failed to clear logs", e)
        }
    }

    suspend fun exportAsText(context: Context): LogExportResult {
        return try {
            withContext(Dispatchers.IO) {
                val logs = database(context).logDao().getAllNow()
                if (logs.isEmpty()) {
                    LogExportResult.Empty
                } else {
                    val formatter = DateTimeFormatter.ofPattern("MM-dd HH:mm:ss", Locale.US)
                    LogExportResult.Success(
                        logs.joinToString("\n") { entry ->
                            val time = Instant.ofEpochMilli(entry.timestamp)
                                .atZone(ZoneId.systemDefault())
                                .format(formatter)
                            "$time ${entry.levelEnum.name}: ${entry.message}"
                        }
                    )
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(LOG_TAG, "Failed to export logs", e)
            LogExportResult.Error
        }
    }

    private suspend fun purgeIfNeeded(context: Context) {
        if (purged) return
        try {
            withContext(Dispatchers.IO) {
                val cutoff = System.currentTimeMillis() - PURGE_DAYS * 24 * 60 * 60 * 1000L
                database(context).logDao().deleteOlderThan(cutoff)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(LOG_TAG, "Failed to purge old logs", e)
        }
        purged = true
    }
}