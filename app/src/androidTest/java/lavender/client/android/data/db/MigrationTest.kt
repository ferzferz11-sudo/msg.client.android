package lavender.client.android.data.db

import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MigrationTest {

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun createV15Database(name: String): SQLiteDatabase {
        context.deleteDatabase(name)
        val db = context.openOrCreateDatabase(name, 0, null)
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS messages (
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
                userId TEXT NOT NULL DEFAULT '',
                isSent INTEGER NOT NULL DEFAULT 1,
                reactionsJson TEXT NOT NULL
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS index_messages_roomId ON messages (roomId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_messages_isSent ON messages (isSent)")
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS chats (
                id TEXT PRIMARY KEY NOT NULL,
                name TEXT NOT NULL,
                type TEXT NOT NULL,
                participants TEXT NOT NULL,
                createdAt INTEGER NOT NULL,
                unreadCount INTEGER NOT NULL,
                lastMessageTime INTEGER NOT NULL,
                creator TEXT NOT NULL,
                lastMessageText TEXT NOT NULL,
                avatarUrl TEXT NOT NULL,
                fullAvatarUrl TEXT NOT NULL,
                lastMessageUsername TEXT NOT NULL,
                muted INTEGER NOT NULL,
                lastMessageHasImage INTEGER NOT NULL DEFAULT 0,
                allowMembersToAdd INTEGER NOT NULL DEFAULT 0,
                isSecret INTEGER NOT NULL DEFAULT 0,
                peerPublicKey TEXT NOT NULL DEFAULT '',
                e2eeReady INTEGER NOT NULL DEFAULT 0,
                activeAgentId TEXT NOT NULL DEFAULT '',
                agentMode TEXT NOT NULL DEFAULT '',
                isPinned INTEGER NOT NULL DEFAULT 0,
                isArchived INTEGER NOT NULL DEFAULT 0,
                pinnedAt INTEGER NOT NULL DEFAULT 0,
                companyId TEXT NOT NULL DEFAULT '',
                companyChatAccess TEXT NOT NULL DEFAULT '',
                companyMinPositionLevel INTEGER NOT NULL DEFAULT 0
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS index_chats_type ON chats (type)")
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
        db.execSQL("PRAGMA user_version = 15")
        return db
    }

    private fun queryDb(db: AppDatabase, sql: String): Cursor {
        return db.openHelper.readableDatabase.query(SimpleSQLiteQuery(sql))
    }

    @Test
    fun migrate15to17_deletedMessagesSchemaCorrect() {
        val dbName = "migration_test_15_17"
        val rawDb = createV15Database(dbName)

        rawDb.execSQL("""
            INSERT INTO messages (id, user, text, timestamp, roomId, repliedToMessageId, repliedToUser, repliedToText, read, avatarUrl, imageUrl, imageUrlsJson, edited, superAdmin, voiceUrl, duration, userId, isSent, reactionsJson)
            VALUES ('msg1', 'user1', 'hello', 1000, 'room1', '', '', '', 0, '', '', '[]', 0, 0, '', 0, '', 1, '[]')
        """.trimIndent())
        rawDb.execSQL("""
            INSERT INTO chats (id, name, type, participants, createdAt, unreadCount, lastMessageTime, creator, lastMessageText, avatarUrl, fullAvatarUrl, lastMessageUsername, muted)
            VALUES ('room1', 'Test Chat', 'direct', '[]', 1000, 0, 1000, 'user1', 'hello', '', '', 'user1', 0)
        """.trimIndent())
        rawDb.close()

        val migratedDb = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
            .addMigrations(AppDatabase.MIGRATION_15_16, AppDatabase.MIGRATION_16_17, AppDatabase.MIGRATION_17_18)
            .build()

        // Force Room to verify schema
        migratedDb.openHelper.writableDatabase

        // Verify deleted_messages table schema
        val cursor = queryDb(migratedDb, "PRAGMA table_info(deleted_messages)")
        val columns = mutableMapOf<String, String?>()
        while (cursor.moveToNext()) {
            val name = cursor.getString(cursor.getColumnIndexOrThrow("name"))
            val defaultVal = cursor.getString(cursor.getColumnIndexOrThrow("dflt_value"))
            columns[name] = defaultVal
        }
        cursor.close()

        assertTrue("deleted_messages should have 'id' column", columns.containsKey("id"))
        assertTrue("deleted_messages should have 'deletedAt' column", columns.containsKey("deletedAt"))
        assertEquals("deletedAt DEFAULT should be 0", "0", columns["deletedAt"])

        // Verify index exists
        val indexCursor = queryDb(migratedDb,
            "SELECT name FROM sqlite_master WHERE type='index' AND name='index_deleted_messages_deletedAt'"
        )
        assertTrue("Index on deletedAt should exist", indexCursor.moveToFirst())
        indexCursor.close()

        // Verify data survived
        val msgCursor = queryDb(migratedDb, "SELECT COUNT(*) FROM messages")
        msgCursor.moveToFirst()
        assertEquals("Messages should survive migration", 1, msgCursor.getInt(0))
        msgCursor.close()

        // Verify we can insert into deleted_messages (confirms schema is valid)
        migratedDb.openHelper.writableDatabase.execSQL("INSERT INTO deleted_messages (id, deletedAt) VALUES ('dm1', 12345)")
        val dmCursor = queryDb(migratedDb, "SELECT id, deletedAt FROM deleted_messages WHERE id='dm1'")
        assertTrue(dmCursor.moveToFirst())
        assertEquals("dm1", dmCursor.getString(0))
        assertEquals(12345L, dmCursor.getLong(1))
        dmCursor.close()

        migratedDb.close()
        context.deleteDatabase(dbName)
    }

    @Test
    fun freshInstall_v17_allTablesExist() {
        val dbName = "fresh_install_test"
        context.deleteDatabase(dbName)

        val db = Room.databaseBuilder(context, AppDatabase::class.java, dbName).build()
        val conn = db.openHelper.writableDatabase

        // Verify all expected tables
        val expectedTables = listOf("messages", "chats", "marketplace_agents", "sticker_packs", "stickers", "deleted_messages")
        for (table in expectedTables) {
            val cursor = queryDb(db, "SELECT name FROM sqlite_master WHERE type='table' AND name='$table'")
            assertTrue("Table '$table' should exist", cursor.moveToFirst())
            cursor.close()
        }

        // Verify deleted_messages schema
        val cursor = queryDb(db, "PRAGMA table_info(deleted_messages)")
        val columns = mutableMapOf<String, String?>()
        while (cursor.moveToNext()) {
            val name = cursor.getString(cursor.getColumnIndexOrThrow("name"))
            val defaultVal = cursor.getString(cursor.getColumnIndexOrThrow("dflt_value"))
            columns[name] = defaultVal
        }
        cursor.close()

        assertEquals("deletedAt DEFAULT should be 0", "0", columns["deletedAt"])

        db.close()
        context.deleteDatabase(dbName)
    }

    @Test
    fun migrate15to17_insertAfterMigration_works() {
        val dbName = "migration_insert_test"
        val rawDb = createV15Database(dbName)
        rawDb.close()

        val migratedDb = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
            .addMigrations(AppDatabase.MIGRATION_15_16, AppDatabase.MIGRATION_16_17, AppDatabase.MIGRATION_17_18)
            .build()

        val thread = Thread {
            try {
                val writable = migratedDb.openHelper.writableDatabase
                writable.execSQL("INSERT INTO deleted_messages (id, deletedAt) VALUES ('test1', 999)")
                writable.execSQL("INSERT INTO deleted_messages (id, deletedAt) VALUES ('test2', 0)")

                val cursor = queryDb(migratedDb, "SELECT COUNT(*) FROM deleted_messages")
                cursor.moveToFirst()
                val count = cursor.getInt(0)
                cursor.close()
                assertEquals("Should insert 2 deleted messages", 2, count)
            } finally {
                migratedDb.close()
                context.deleteDatabase(dbName)
            }
        }
        thread.start()
        thread.join(5000)
    }

    @Test
    fun migrate17to18_marketplaceAgentsColumnRename() {
        val dbName = "migration_17_18_test"
        context.deleteDatabase(dbName)
        val rawDb = context.openOrCreateDatabase(dbName, 0, null)

        // Create v17 schema with the WRONG column name (isPinned instead of isPreset)
        rawDb.execSQL("""
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
        rawDb.execSQL("INSERT INTO marketplace_agents (id, name, description, providerType, model, toolsEnabled, ragEnabled, isPinned, isPublic, avgRating, installCount, cachedAt) VALUES ('a1', 'Test', 'desc', 'openrouter', 'gpt-4', 1, 0, 1, 1, 4.5, 100, 1000)")
        rawDb.execSQL("PRAGMA user_version = 17")
        rawDb.close()

        val migratedDb = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
            .addMigrations(AppDatabase.MIGRATION_17_18)
            .build()

        // Force Room to verify schema
        migratedDb.openHelper.writableDatabase

        // Verify data survived the rename
        val cursor = queryDb(migratedDb, "SELECT id, isPreset FROM marketplace_agents WHERE id='a1'")
        assertTrue(cursor.moveToFirst())
        assertEquals("a1", cursor.getString(0))
        assertEquals(1, cursor.getInt(1))
        cursor.close()

        migratedDb.close()
        context.deleteDatabase(dbName)
    }
}
