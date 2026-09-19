package org.cssnr.zipline.log

import android.content.Context
import android.content.SharedPreferences
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

enum class LogLevel { DEBUG, INFO, WARNING, ERROR }

@Entity
data class LogEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val level: LogLevel,
    val message: String,
    val timestamp: Long = System.currentTimeMillis(),
)

@Dao
interface LogDao {
    @Query("SELECT * FROM logentry ORDER BY id DESC")
    fun getAll(): Flow<List<LogEntry>>

    @Query("SELECT * FROM logentry ORDER BY id DESC")
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

    private fun database(context: Context): LogDatabase = LogDatabase.getInstance(context)

    @Volatile
    private var enabled = true

    @Volatile
    private var prefsInitialized = false

    private val preferenceListener =
        SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
            if (key == ENABLED_KEY) enabled = prefs.getBoolean(ENABLED_KEY, true)
        }

    private fun isEnabled(context: Context): Boolean {
        if (!prefsInitialized) {
            synchronized(this) {
                if (!prefsInitialized) {
                    val preferences = PreferenceManager.getDefaultSharedPreferences(context)
                    enabled = preferences.getBoolean(ENABLED_KEY, true)
                    preferences.registerOnSharedPreferenceChangeListener(preferenceListener)
                    prefsInitialized = true
                }
            }
        }
        return enabled
    }

    private val fireForgetScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun log(context: Context, level: LogLevel, message: String) {
        if (!isEnabled(context)) return
        val appContext = context.applicationContext
        fireForgetScope.launch {
            try {
                purgeIfNeeded(appContext)
                database(appContext).logDao().insert(
                    LogEntry(level = level, message = message)
                )
            } catch (e: Exception) {
                Log.e(LOG_TAG, "Failed to write log entry", e)
            }
        }
    }

    fun d(context: Context, message: String) = log(context, LogLevel.DEBUG, message)

    fun i(context: Context, message: String) = log(context, LogLevel.INFO, message)

    fun w(context: Context, message: String) = log(context, LogLevel.WARNING, message)

    fun e(context: Context, message: String) = log(context, LogLevel.ERROR, message)

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
                            "$time ${entry.level.name}: ${entry.message}"
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
