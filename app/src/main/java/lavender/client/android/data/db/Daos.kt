package lavender.client.android.data.db

import androidx.room.*

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE roomId = :roomId ORDER BY timestamp ASC")
    suspend fun getMessagesForRoom(roomId: String): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE roomId LIKE 'favorites_%' OR roomId = :favoritesRoomId ORDER BY timestamp ASC")
    suspend fun getFavorites(favoritesRoomId: String): List<MessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessages(messages: List<MessageEntity>)

    @Query("DELETE FROM messages WHERE id = :messageId")
    suspend fun deleteMessage(messageId: String)

    @Query("DELETE FROM messages WHERE roomId = :roomId")
    suspend fun clearRoom(roomId: String)
}

@Dao
interface ChatDao {
    @Query("SELECT * FROM chats ORDER BY lastMessageTime DESC")
    suspend fun getAllChats(): List<ChatEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChats(chats: List<ChatEntity>)

    @Query("DELETE FROM chats WHERE id = :chatId")
    suspend fun deleteChat(chatId: String)
}
