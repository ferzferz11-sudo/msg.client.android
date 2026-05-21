package lavender.client.android.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [MessageEntity::class, ChatEntity::class], version = 5, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun messageDao(): MessageDao
    abstract fun chatDao(): ChatDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        // Migration from version 1 to 2: Add imageUrlsJson column
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                try {
                    // Create new table with imageUrlsJson column
                    db.execSQL("""
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
                    db.execSQL("""
                        INSERT INTO messages_new (id, user, text, timestamp, roomId, repliedToMessageId, repliedToUser, repliedToText, read, avatarUrl, imageUrl, imageUrlsJson, edited, superAdmin, voiceUrl, duration, reactionsJson)
                        SELECT id, user, text, timestamp, roomId, repliedToMessageId, repliedToUser, repliedToText, read, avatarUrl, imageUrl, '[]', edited, superAdmin, voiceUrl, duration, reactionsJson
                        FROM messages
                    """.trimIndent())

                    // Drop old table
                    db.execSQL("DROP TABLE messages")

                    // Rename new table to old table name
                    db.execSQL("ALTER TABLE messages_new RENAME TO messages")
                } catch (e: Exception) {
                    android.util.Log.e("AppDatabase", "Migration failed", e)
                }
            }
        }

        // Migration from version 2 to 3: Add lastMessageHasImage column to chats table
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                try {
                    db.execSQL("ALTER TABLE chats ADD COLUMN lastMessageHasImage INTEGER NOT NULL DEFAULT 0")
                } catch (e: Exception) {
                    android.util.Log.e("AppDatabase", "Migration 2-3 failed", e)
                }
            }
        }

        // Migration from version 3 to 4: Add isSent column to messages table
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                try {
                    db.execSQL("ALTER TABLE messages ADD COLUMN isSent INTEGER NOT NULL DEFAULT 1")
                } catch (e: Exception) {
                    android.util.Log.e("AppDatabase", "Migration 3-4 failed", e)
                }
            }
        }

        // Migration from version 4 to 5: Add userId column to messages table
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                try {
                    db.execSQL("ALTER TABLE messages ADD COLUMN userId TEXT NOT NULL DEFAULT ''")
                } catch (e: Exception) {
                    android.util.Log.e("AppDatabase", "Migration 4-5 failed", e)
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
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                .fallbackToDestructiveMigration(true)
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
