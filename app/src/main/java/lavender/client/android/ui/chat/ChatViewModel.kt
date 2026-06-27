package lavender.client.android.ui.chat

import android.content.Context
import androidx.core.content.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import lavender.client.android.data.db.AppDatabase
import lavender.client.android.data.grpc.GrpcClient
import lavender.client.android.data.models.Message
import lavender.client.android.data.proto.UserInfoProto
import lavender.client.android.data.session.SessionManager

class ChatViewModel : ViewModel() {
    val grpcClient = GrpcClient

    var currentRoomId = "general"

    val error: StateFlow<String?> = grpcClient.error
    val messages: StateFlow<List<Message>> = grpcClient.messages
    val users: StateFlow<List<String>> = grpcClient.users

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _pinnedMessageIds = MutableStateFlow<Set<String>>(emptySet())
    val pinnedMessageIds: StateFlow<Set<String>> = _pinnedMessageIds.asStateFlow()

    private val _isAudioUploading = MutableStateFlow(false)
    val isAudioUploading: StateFlow<Boolean> = _isAudioUploading.asStateFlow()

    private val _chatMetadata = MutableStateFlow<ChatMetadata?>(null)
    val chatMetadata: StateFlow<ChatMetadata?> = _chatMetadata.asStateFlow()

    data class ChatMetadata(
        val chatName: String, val isDirect: Boolean, val chatType: String,
        val participantsJson: String, val creator: String,
        val avatarUrl: String, val fullAvatarUrl: String
    )

    fun connect(serverAddress: String, useTls: Boolean = false, port: Int = 50051, context: Context? = null) {
        viewModelScope.launch {
            grpcClient.connect(serverAddress, useTls, port, context)
        }
    }

    fun disconnect() {
        viewModelScope.launch {
            grpcClient.disconnect()
        }
    }

    fun startChatV2(roomId: String, onMessageReceived: (Message) -> Unit) {
        grpcClient.startChatV2(roomId, onMessageReceived)
    }

    fun sendMessage(message: Message) {
        grpcClient.addLocalMessage(message)
        grpcClient.sendMessageV2(message)
        if (currentRoomId.startsWith("favorites_")) grpcClient.markRead(currentRoomId, message.user)
        grpcClient.deleteDraft(currentRoomId)
    }

    fun sendMessageWithE2EE(plainText: String, encryptAndSend: (String, (Boolean) -> Unit) -> Unit, onSuccess: () -> Unit, onError: () -> Unit) {
        encryptAndSend(plainText) { success ->
            if (success) {
                if (currentRoomId.startsWith("favorites_")) grpcClient.markRead(currentRoomId, "")
                grpcClient.deleteDraft(currentRoomId)
                onSuccess()
            } else {
                onError()
            }
        }
    }

    fun uploadAudio(context: Context, file: java.io.File, duration: Int, username: String, onError: (String) -> Unit) {
        _isAudioUploading.value = true
        viewModelScope.launch {
            val result = lavender.client.android.audio.AudioUploader(context).uploadAudio(file, duration)
            _isAudioUploading.value = false
            if (result.success && result.url.isNotEmpty() && !result.url.contains("404")) {
                grpcClient.sendMessageV2(Message(
                    user = username, text = "Voice message", timestamp = System.currentTimeMillis(),
                    roomId = currentRoomId, voiceUrl = result.url, duration = result.duration,
                    userId = grpcClient.getUserId() ?: ""
                ))
            } else {
                onError("Failed to upload audio: ${if (result.url.contains("404")) "Server error 404" else result.error}")
            }
        }
    }

    fun retryMessage(message: Message) {
        viewModelScope.launch {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                grpcClient.loadHistoryV2(currentRoomId) { _, _ ->
                    val updated = grpcClient.messages.value.find { it.id == message.id }
                    if (updated == null || !updated.isSent) {
                        grpcClient.sendMessageV2(message)
                    }
                }
            }
        }
    }

    fun fetchChatMetadata(username: String, roomId: String, isDirect: Boolean, participantsJson: String, chatName: String, onResult: (ChatMetadata) -> Unit) {
        if (roomId.startsWith("favorites_")) return
        if (!isDirect || participantsJson == "[]" || chatName == "Chat") {
            grpcClient.getChats(username) { page ->
                val chat = page.chats.find { it.id == roomId }
                if (chat != null) {
                    val meta = ChatMetadata(
                        chatName = chat.getDisplayName(username), isDirect = chat.type == "direct",
                        chatType = chat.type, participantsJson = chat.participants, creator = chat.creator,
                        avatarUrl = chat.avatarUrl, fullAvatarUrl = chat.fullAvatarUrl
                    )
                    _chatMetadata.value = meta
                    onResult(meta)
                }
            }
        }
    }

    fun loadPinnedMessages(context: Context) {
        viewModelScope.launch {
            try {
                val pinned = GrpcClient.getPinnedMessages(context, currentRoomId)
                _pinnedMessageIds.value = pinned.map { it.id }.toSet()
            } catch (_: Exception) {}
        }
    }

    fun syncChatListIfNeeded(context: Context) {
        val prefs = context.getSharedPreferences("lavender_prefs", Context.MODE_PRIVATE)
        val local = prefs.getLong("chat_list_version", 0L)
        val u = grpcClient.getCurrentUsername() ?: return
        grpcClient.getChatListVersion(u) { server ->
            if (server > local) {
                grpcClient.getChats(u) { _ -> prefs.edit { putLong("chat_list_version", server) } }
            }
        }
    }

    fun ensureUserIdSet(context: Context, onReady: () -> Unit) {
        val prefs = context.getSharedPreferences("lavender_prefs", Context.MODE_PRIVATE)
        val saved = prefs.getString("user_id", null)
        if (saved != null) {
            grpcClient.setUserId(saved)
            onReady()
        } else {
            val username = SessionManager.session.value.username
            if (username.isNotEmpty()) {
                grpcClient.fetchUserId(username) { uid, fetched ->
                    if (fetched && uid != null) {
                        prefs.edit { putString("user_id", uid) }
                        grpcClient.setUserId(uid)
                    }
                    onReady()
                }
            } else {
                onReady()
            }
        }
    }

    fun updateMessage(message: Message) {
        grpcClient.updateMessage(message)
    }

    fun deleteMessage(message: Message) {
        grpcClient.deleteMessageV2(listOf(message.id))
    }

    fun setReaction(messageId: String, username: String, emoji: String) {
        grpcClient.setReactionV2(messageId, username, emoji)
    }

    fun registerToken(username: String, token: String) {
        grpcClient.registerToken(username, token)
    }

    fun clearSystemNotification() {
        grpcClient.clearSystemNotification()
    }

    fun switchRoom(roomId: String) {
        currentRoomId = roomId
        grpcClient.setRoomId(roomId)
        grpcClient.clearMessages()
        loadHistory()
    }

    fun loadHistory() {
        if (_isLoading.value) return
        _isLoading.value = true
        viewModelScope.launch {
            grpcClient.loadHistoryV2(currentRoomId) { _, _ ->
                _isLoading.value = false
            }
        }
    }

    fun markRead(username: String, onCompletion: (() -> Unit)? = null) {
        if (currentRoomId.startsWith("favorites_")) {
            onCompletion?.invoke()
            return
        }
        grpcClient.markRead(currentRoomId, username, onCompletion)
    }

    fun sendTypingSignal(username: String, isTyping: Boolean) {
        grpcClient.sendTypingSignal(username, isTyping)
    }

    fun saveDraft(draftText: String, repliedToMessageId: String = "", repliedToUser: String = "", repliedToText: String = "", callback: (Boolean, String) -> Unit = { _, _ -> }) {
        grpcClient.saveDraft(currentRoomId, draftText, repliedToMessageId, repliedToUser, repliedToText, callback)
    }

    fun getDraft(callback: (String, String, String, String, Boolean) -> Unit) {
        grpcClient.getDraft(currentRoomId, callback)
    }

    fun deleteDraft(callback: (Boolean) -> Unit = {}) {
        grpcClient.deleteDraft(currentRoomId, callback)
    }

    fun clearRoomMessages(context: Context) {
        viewModelScope.launch {
            try {
                AppDatabase.getDatabase(context).messageDao().clearRoom(currentRoomId)
            } catch (_: Exception) {}
        }
    }
}
