package org.cssnr.zipline.db

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Upsert

// NOTE: Currently this is only used for stats
@Entity(tableName = "servers")
data class ServerEntity(
    @PrimaryKey val url: String,
    val token: String = "",
    val active: Boolean = false,
    val filesUploaded: Int? = null,
    val favoriteFiles: Int? = null,
    val views: Int? = null,
    val avgViews: Double? = null,
    val storageUsed: Long? = null,
    val avgStorageUsed: Double? = null,
    val urlsCreated: Int? = null,
    val urlViews: Int,
    val updatedAt: Long,
)

@Dao
interface ServerDao {
    @Upsert
    fun upsert(stats: ServerEntity)

    @Query("SELECT * FROM servers WHERE url = :url LIMIT 1")
    fun get(url: String): ServerEntity?

    //@Query("SELECT * FROM servers ORDER BY ROWID")
    //fun getAll(): List<ServerEntity>

    //@Query("UPDATE servers SET token = :token WHERE url = :url")
    //fun setToken(url: String, token: String)

    //@Query("UPDATE servers SET active = 1 WHERE url = :url")
    //fun activate(url: String)

    //@Delete
    //fun delete(server: ServerEntity)
}


@Database(entities = [ServerEntity::class], version = 2)
abstract class ServerDatabase : RoomDatabase() {
    abstract fun serverDao(): ServerDao

    companion object {
        @Volatile
        private var instance: ServerDatabase? = null

        fun getInstance(context: Context): ServerDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    ServerDatabase::class.java,
                    "server-database"
                )
                    .fallbackToDestructiveMigration(true)
                    .build().also { instance = it }
            }
        }
    }
}
