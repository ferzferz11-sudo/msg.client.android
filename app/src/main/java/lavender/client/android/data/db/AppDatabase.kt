package lavender.client.android.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [MessageEntity::class, ChatEntity::class, MarketplaceAgentEntity::class, StickerPackEntity::class, StickerEntity::class, DeletedMessageEntity::class], version = 16, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun messageDao(): MessageDao
    abstract fun chatDao(): ChatDao
    abstract fun marketplaceDao(): MarketplaceDao
    abstract fun stickerPackDao(): StickerPackDao
    abstract fun stickerDao(): StickerDao
    abstract fun deletedMessageDao(): DeletedMessageDao

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
                        INSERT INTO messages_new (id, user, text, timestamp, roomId, repliedToMessageId, repliedToUser, repliedToText, read, avatarUrl, imageUrl, '[]', edited, superAdmin, voiceUrl, duration, reactionsJson)
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

        // Migration from version 5 to 6: Add E2EE columns + ensure all previous columns exist
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                try {
                    // Add E2EE columns for secret chats
                    db.execSQL("ALTER TABLE chats ADD COLUMN isSecret INTEGER NOT NULL DEFAULT 0")
                    db.execSQL("ALTER TABLE chats ADD COLUMN peerPublicKey TEXT NOT NULL DEFAULT ''")
                    db.execSQL("ALTER TABLE chats ADD COLUMN e2eeReady INTEGER NOT NULL DEFAULT 0")
                } catch (e: Exception) {
                    android.util.Log.e("AppDatabase", "Migration 5-6 E2EE columns failed", e)
                }
                // Ensure lastMessageHasImage exists (from migration 2-3, may be skipped on some devices)
                try {
                    db.execSQL("ALTER TABLE chats ADD COLUMN lastMessageHasImage INTEGER NOT NULL DEFAULT 0")
                } catch (_: Exception) { /* already exists */ }
                // Ensure lastMessageTime exists
                try {
                    db.execSQL("ALTER TABLE chats ADD COLUMN lastMessageTime INTEGER NOT NULL DEFAULT 0")
                } catch (_: Exception) { /* already exists */ }
            }
        }

        // Migration from version 6 to 7: Schema hash changed due to @ColumnInfo additions
        // No actual schema changes needed, just bumping version for Room identity hash
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // No schema changes — version bump only for Room identity hash
            }
        }

        // Migration from version 7 to 8: Reverted @ColumnInfo, no schema changes
        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // No schema changes — version bump only
            }
        }

        // Migration from version 8 to 9: Add activeAgentId and agentMode to chats table
        // Also ensure all columns from previous migrations exist (defensive)
        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                try { db.execSQL("ALTER TABLE chats ADD COLUMN activeAgentId TEXT NOT NULL DEFAULT ''") } catch (_: Exception) {}
                try { db.execSQL("ALTER TABLE chats ADD COLUMN agentMode TEXT NOT NULL DEFAULT ''") } catch (_: Exception) {}
                try { db.execSQL("ALTER TABLE chats ADD COLUMN isSecret INTEGER NOT NULL DEFAULT 0") } catch (_: Exception) {}
                try { db.execSQL("ALTER TABLE chats ADD COLUMN peerPublicKey TEXT NOT NULL DEFAULT ''") } catch (_: Exception) {}
                try { db.execSQL("ALTER TABLE chats ADD COLUMN e2eeReady INTEGER NOT NULL DEFAULT 0") } catch (_: Exception) {}
                try { db.execSQL("ALTER TABLE chats ADD COLUMN type TEXT NOT NULL DEFAULT ''") } catch (_: Exception) {}
                try { db.execSQL("ALTER TABLE chats ADD COLUMN unreadCount INTEGER NOT NULL DEFAULT 0") } catch (_: Exception) {}
                try { db.execSQL("ALTER TABLE chats ADD COLUMN allowMembersToAdd INTEGER NOT NULL DEFAULT 0") } catch (_: Exception) {}
                try { db.execSQL("ALTER TABLE chats ADD COLUMN lastMessageHasImage INTEGER NOT NULL DEFAULT 0") } catch (_: Exception) {}
                try { db.execSQL("ALTER TABLE chats ADD COLUMN lastMessageTime INTEGER NOT NULL DEFAULT 0") } catch (_: Exception) {}
            }
        }

        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                try { db.execSQL("ALTER TABLE chats ADD COLUMN isPinned INTEGER NOT NULL DEFAULT 0") } catch (_: Exception) {}
                try { db.execSQL("ALTER TABLE chats ADD COLUMN isArchived INTEGER NOT NULL DEFAULT 0") } catch (_: Exception) {}
                try { db.execSQL("ALTER TABLE chats ADD COLUMN pinnedAt INTEGER NOT NULL DEFAULT 0") } catch (_: Exception) {}
            }
        }

        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS marketplace_agents (
                        id TEXT PRIMARY KEY NOT NULL,
                        name TEXT NOT NULL,
                        description TEXT NOT NULL,
                        providerType TEXT NOT NULL,
                        model TEXT NOT NULL,
                        toolsEnabled INTEGER NOT NULL,
                        ragEnabled INTEGER NOT NULL,
                        isPinned INTEGER NOT NULL,
                        isPublic INTEGER NOT NULL,
                        avgRating REAL NOT NULL,
                        installCount INTEGER NOT NULL,
                        cachedAt INTEGER NOT NULL
                    )
                """.trimIndent())
            }
        }

        val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                try { db.execSQL("CREATE INDEX IF NOT EXISTS index_messages_roomId ON messages (roomId)") } catch (_: Exception) {}
            }
        }

        val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                try { db.execSQL("ALTER TABLE chats ADD COLUMN companyId TEXT NOT NULL DEFAULT ''") } catch (_: Exception) {}
                try { db.execSQL("ALTER TABLE chats ADD COLUMN companyChatAccess TEXT NOT NULL DEFAULT ''") } catch (_: Exception) {}
                try { db.execSQL("ALTER TABLE chats ADD COLUMN companyMinPositionLevel INTEGER NOT NULL DEFAULT 0") } catch (_: Exception) {}
            }
        }

        val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                try {
                    db.execSQL("""
                        CREATE TABLE IF NOT EXISTS sticker_packs (
                            id TEXT PRIMARY KEY NOT NULL,
                            title TEXT NOT NULL,
                            name TEXT NOT NULL,
                            creatorUserId TEXT NOT NULL,
                            creatorUsername TEXT NOT NULL,
                            coverStickerId TEXT NOT NULL DEFAULT '',
                            status TEXT NOT NULL DEFAULT 'draft',
                            rejectionReason TEXT NOT NULL DEFAULT '',
                            isFeatured INTEGER NOT NULL DEFAULT 0,
                            createdAt INTEGER NOT NULL DEFAULT 0,
                            updatedAt INTEGER NOT NULL DEFAULT 0
                        )
                    """.trimIndent())
                    db.execSQL("CREATE INDEX IF NOT EXISTS index_sticker_packs_creatorUserId ON sticker_packs (creatorUserId)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS index_sticker_packs_status ON sticker_packs (status)")
                } catch (_: Exception) {}
                try {
                    db.execSQL("""
                        CREATE TABLE IF NOT EXISTS stickers (
                            id TEXT PRIMARY KEY NOT NULL,
                            packId TEXT NOT NULL,
                            lottieUrl TEXT NOT NULL,
                            thumbnailUrl TEXT NOT NULL DEFAULT '',
                            emoji TEXT NOT NULL DEFAULT '',
                            width INTEGER NOT NULL DEFAULT 512,
                            height INTEGER NOT NULL DEFAULT 512,
                            sortOrder INTEGER NOT NULL DEFAULT 0,
                            createdAt INTEGER NOT NULL DEFAULT 0
                        )
                    """.trimIndent())
                    db.execSQL("CREATE INDEX IF NOT EXISTS index_stickers_packId ON stickers (packId)")
                } catch (_: Exception) {}
            }
        }

        val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                try { db.execSQL("CREATE INDEX IF NOT EXISTS index_messages_isSent ON messages (isSent)") } catch (_: Exception) {}
                try { db.execSQL("CREATE INDEX IF NOT EXISTS index_chats_type ON chats (type)") } catch (_: Exception) {}
            }
        }

        val MIGRATION_15_16 = object : Migration(15, 16) {
            override fun migrate(db: SupportSQLiteDatabase) {
                try {
                    db.execSQL("""
                        CREATE TABLE IF NOT EXISTS deleted_messages (
                            id TEXT PRIMARY KEY NOT NULL,
                            deletedAt INTEGER NOT NULL DEFAULT 0
                        )
                    """.trimIndent())
                } catch (_: Exception) {}
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "lavender_cache"
                )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15, MIGRATION_15_16)
                .fallbackToDestructiveMigrationOnDowngrade(dropAllTables = true)
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
