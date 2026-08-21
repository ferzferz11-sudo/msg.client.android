package lavender.client.android.data.db

import androidx.room.*

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE roomId = :roomId ORDER BY timestamp ASC")
    suspend fun getMessagesForRoom(roomId: String): List<MessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessages(messages: List<MessageEntity>)

    @Query("DELETE FROM messages WHERE id = :messageId")
    suspend fun deleteMessage(messageId: String)

    @Query("DELETE FROM messages WHERE roomId = :roomId")
    suspend fun clearRoom(roomId: String)

    @Query("SELECT * FROM messages WHERE roomId = :roomId ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLastMessage(roomId: String): MessageEntity?

    @Query("SELECT imageUrl FROM messages WHERE roomId = :roomId ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLastMessageImageUrl(roomId: String): String?

    @Query("SELECT * FROM messages WHERE roomId = :savedMessagesRoomId ORDER BY timestamp ASC")
    suspend fun getSavedMessages(savedMessagesRoomId: String): List<MessageEntity>

    @Query("UPDATE messages SET read = 1 WHERE roomId = :roomId")
    suspend fun markRoomAsRead(roomId: String)

    @Query("SELECT * FROM messages WHERE isSent = 0")
    suspend fun getPendingMessages(): List<MessageEntity>

    @Query("UPDATE messages SET reactionsJson = :reactionsJson WHERE id = :messageId")
    suspend fun updateReactions(messageId: String, reactionsJson: String)

    @Query("UPDATE messages SET text = :text, edited = :edited WHERE id = :messageId")
    suspend fun updateMessageText(messageId: String, text: String, edited: Boolean)

    @Query("DELETE FROM messages")
    suspend fun clearAll()
}

@Dao
interface ChatDao {
    @Query("SELECT * FROM chats ORDER BY lastMessageTime DESC")
    suspend fun getAllChats(): List<ChatEntity>

    @Query("SELECT SUM(unreadCount) FROM chats")
    suspend fun getTotalUnreadCount(): Int?

    @Query("SELECT * FROM chats WHERE id = :chatId")
    suspend fun getChat(chatId: String): ChatEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChats(chats: List<ChatEntity>)

    @Query("DELETE FROM chats WHERE id = :chatId")
    suspend fun deleteChat(chatId: String)

    @Query("DELETE FROM chats")
    suspend fun clearAll()

    @Transaction
    suspend fun syncChats(serverChats: List<ChatEntity>) {
        val serverIds = serverChats.map { it.id }
        // Delete local chats that are not in the server response
        if (serverIds.isNotEmpty()) {
            deleteChatsNotInList(serverIds)
        } else {
            clearAll()
        }
        // Insert or update server chats
        insertChats(serverChats)
    }

    @Query("DELETE FROM chats WHERE id NOT IN (:chatIds)")
    suspend fun deleteChatsNotInList(chatIds: List<String>)
}

@Dao
interface MarketplaceDao {
    @Query("SELECT * FROM marketplace_agents ORDER BY avgRating DESC")
    suspend fun getAll(): List<MarketplaceAgentEntity>

    @Query("SELECT * FROM marketplace_agents WHERE name LIKE '%' || :query || '%' OR description LIKE '%' || :query || '%' ORDER BY avgRating DESC")
    suspend fun search(query: String): List<MarketplaceAgentEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(agents: List<MarketplaceAgentEntity>)

    @Query("DELETE FROM marketplace_agents")
    suspend fun clearAll()

    @Transaction
    suspend fun sync(agents: List<MarketplaceAgentEntity>) {
        clearAll()
        insertAll(agents)
    }
}

@Dao
interface StickerPackDao {
    @Query("SELECT * FROM sticker_packs WHERE creatorUserId = :userId")
    suspend fun getPacksByUser(userId: String): List<StickerPackEntity>

    @Query("SELECT * FROM sticker_packs WHERE status = 'approved' ORDER BY isFeatured DESC, updatedAt DESC")
    suspend fun getPublicPacks(): List<StickerPackEntity>

    @Query("SELECT * FROM sticker_packs WHERE status = 'pending'")
    suspend fun getPendingPacks(): List<StickerPackEntity>

    @Query("SELECT * FROM sticker_packs WHERE id = :packId")
    suspend fun getPack(packId: String): StickerPackEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPack(pack: StickerPackEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPacks(packs: List<StickerPackEntity>)

    @Query("DELETE FROM sticker_packs WHERE id = :packId")
    suspend fun deletePack(packId: String)

    @Query("DELETE FROM sticker_packs")
    suspend fun clearAll()

    @Transaction
    suspend fun syncPacks(packs: List<StickerPackEntity>) {
        clearAll()
        insertPacks(packs)
    }
}

@Dao
interface StickerDao {
    @Query("SELECT * FROM stickers WHERE packId = :packId ORDER BY sortOrder ASC")
    suspend fun getStickersForPack(packId: String): List<StickerEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStickers(stickers: List<StickerEntity>)

    @Query("DELETE FROM stickers WHERE packId = :packId")
    suspend fun deleteStickersForPack(packId: String)

    @Query("DELETE FROM stickers WHERE id = :stickerId")
    suspend fun deleteSticker(stickerId: String)

    @Query("DELETE FROM stickers")
    suspend fun clearAll()
}

@Dao
interface DeletedMessageDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: DeletedMessageEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<DeletedMessageEntity>)

    @Query("SELECT id FROM deleted_messages")
    suspend fun getAllIds(): List<String>

    @Query("DELETE FROM deleted_messages WHERE deletedAt < :before")
    suspend fun cleanupOlderThan(before: Long)
}