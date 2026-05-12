package lavender.client.android.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [MessageEntity::class, ChatEntity::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun messageDao(): MessageDao
    abstract fun chatDao(): ChatDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        // Migration from version 1 to 2: Add imageUrlsJson column
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                try {
                    // Create new table with imageUrlsJson column
                    database.execSQL("""
                        CREATE TABLE messages_new (
                            id TEXT PRIMARY KEY NOT NULL,
                            user TEXT NOT NULL,
                            text TEXT NOT NULL,
                            timestamp INTEGER NOT NULL,
                            roomId TEXT NOT NULL,
                            repliedToMessageId TEXT NOT NULL,
                            repliedToUser TEXT NOT NULL,
                            repliedToText TEXT NOT NULL,
                            read INTEGER NOT NULL,
                            avatarUrl TEXT NOT NULL,
                            imageUrl TEXT NOT NULL,
                            imageUrlsJson TEXT NOT NULL,
                            edited INTEGER NOT NULL,
                            superAdmin INTEGER NOT NULL,
                            voiceUrl TEXT NOT NULL,
                            duration INTEGER NOT NULL,
                            reactionsJson TEXT NOT NULL
                        )
                    """.trimIndent())
                    
                    // Copy data from old table to new table
                    database.execSQL("""
                        INSERT INTO messages_new (id, user, text, timestamp, roomId, repliedToMessageId, repliedToUser, repliedToText, read, avatarUrl, imageUrl, imageUrlsJson, edited, superAdmin, voiceUrl, duration, reactionsJson)
                        SELECT id, user, text, timestamp, roomId, repliedToMessageId, repliedToUser, repliedToText, read, avatarUrl, imageUrl, '[]', edited, superAdmin, voiceUrl, duration, reactionsJson
                        FROM messages
                    """.trimIndent())
                    
                    // Drop old table
                    database.execSQL("DROP TABLE messages")
                    
                    // Rename new table to old table name
                    database.execSQL("ALTER TABLE messages_new RENAME TO messages")
                } catch (e: Exception) {
                    android.util.Log.e("AppDatabase", "Migration failed", e)
                }
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "lavender_cache"
                )
                .addMigrations(MIGRATION_1_2)
                .fallbackToDestructiveMigration() // For development - will clear DB on migration failure
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
