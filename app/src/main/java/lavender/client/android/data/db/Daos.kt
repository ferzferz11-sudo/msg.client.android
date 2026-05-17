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

    @Query("SELECT * FROM messages WHERE roomId = :favoritesRoomId ORDER BY timestamp ASC")
    suspend fun getFavorites(favoritesRoomId: String): List<MessageEntity>

    @Query("UPDATE messages SET read = 1 WHERE roomId = :roomId")
    suspend fun markRoomAsRead(roomId: String)

    @Query("SELECT * FROM messages WHERE isSent = 0")
    suspend fun getPendingMessages(): List<MessageEntity>

    @Query("DELETE FROM messages")
    suspend fun clearAll()
}

@Dao
interface ChatDao {
    @Query("SELECT * FROM chats ORDER BY lastMessageTime DESC")
    suspend fun getAllChats(): List<ChatEntity>

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