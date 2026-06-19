package lavender.client.android.data.grpc

import android.content.Context
import android.util.Log
import io.grpc.CallOptions
import io.grpc.ClientCall
import io.grpc.Metadata
import io.grpc.MethodDescriptor
import io.grpc.Status
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import lavender.client.android.data.db.*
import lavender.client.android.data.models.Message
import lavender.client.android.data.models.Reaction
import lavender.client.android.data.models.ErrorHandler
import lavender.client.android.data.proto.*
import lavender.client.android.data.fcm.LavenderMessagingService

/**
 * Handles message operations: send, load history, edit, delete, reactions, mark read.
 *
 * Owns all message-related RPC calls to ChatService.
 * Does NOT own channel management — uses channel from GrpcConnectionManager.
 *
 * Extracted from RealGrpcClient v1.1.3.27 to continue modular decomposition.
 */
class GrpcMessageClient(
    private val getChannel: () -> io.grpc.ManagedChannel?,
    private val getUserId: () -> String?,
    private val getUsername: () -> String?,
    private val messages: MutableStateFlow<List<Message>>,
    private val deletedMessageHashes: MutableSet<String>,
    private val pendingReads: MutableSet<String>,
    private val scope: CoroutineScope,
    private val appContext: () -> Context?,
    private val onReadReceipt: ((String, String) -> Unit)? = null
) {
    companion object {
        private const val TAG = "GrpcMessageClient"
    }

    private var database: AppDatabase? = null
    private fun db() = database ?: appContext()?.let {
        val d = AppDatabase.getDatabase(it)
        database = d
        d
    }

    // ======= Send Message =======

    fun sendMessage(message: Message, requestObserver: io.grpc.stub.StreamObserver<MessageProto>?) {
        if (requestObserver == null) {
            Log.e(TAG, "Cannot send message: requestObserver is null. Message is already saved locally and will be resent on reconnection.")
            return
        }
        try {
            val proto = ProtoUtils.createMessageProto(message)
            requestObserver.onNext(proto)
            Log.d(TAG, "Message sent via stream: ${message.text.take(20)}...")
        } catch (e: Exception) {
            ErrorHandler.handle("GrpcMessageClient.sendMessage", e)
        }
    }

    // ======= Local Message (optimistic UI) =======

    fun addLocalMessage(message: Message) {
        // Persist local message so it's not lost on app restart
        scope.launch(Dispatchers.IO) {
            db()?.messageDao()?.insertMessages(listOf(message.toEntity()))
        }

        messages.update { current ->
            val list = current.toMutableList()
            val existingIndex = list.indexOfFirst { getMessageHash(it) == getMessageHash(message) }
            if (existingIndex != -1) {
                val existing = list[existingIndex]
                if (existing.timestamp != message.timestamp) {
                    list.removeAt(existingIndex)
                    val insertIndex = list.indexOfFirst { it.timestamp > message.timestamp }
                    if (insertIndex == -1) list.add(message)
                    else list.add(insertIndex, message)
                } else {
                    list[existingIndex] = message
                }
            } else {
                val insertIndex = list.indexOfFirst { it.timestamp > message.timestamp }
                if (insertIndex == -1) list.add(message)
                else list.add(insertIndex, message)
            }
            list
        }
    }

    // ======= Resend Pending Messages =======

    fun resendPendingMessages(getRequestObserver: () -> io.grpc.stub.StreamObserver<MessageProto>?) {
        scope.launch(Dispatchers.IO) {
            val pending = db()?.messageDao()?.getPendingMessages() ?: emptyList()
            if (pending.isNotEmpty()) {
                Log.d(TAG, "Resending ${pending.size} pending messages")
                pending.forEach { entity ->
                    val msg = entity.toDomain()
                    scope.launch(Dispatchers.Main) {
                        getRequestObserver()?.onNext(ProtoUtils.createMessageProto(msg))
                    }
                }
            }
        }
    }

    // ======= Load History =======

    fun loadHistory(roomId: String, onCompletion: () -> Unit = {}) {
        val context = appContext()

        // First, always load from cache
        scope.launch(Dispatchers.IO) {
            val cached = db()?.messageDao()?.getMessagesForRoom(roomId)?.map { it.toDomain() } ?: emptyList()
            if (cached.isNotEmpty() && messages.value.isEmpty()) {
                val decrypted = decryptE2EEMessages(roomId, cached, context)
                messages.update { decrypted }
                Log.d(TAG, "Loaded ${decrypted.size} messages from cache for $roomId")
            }
        }

        val currentChannel = getChannel()
        if (currentChannel == null) {
            Log.d(TAG, "loadHistory: no channel (offline), using cache only for $roomId")
            scope.launch { onCompletion() }
            return
        }
        val methodDescriptor = MethodDescriptor.newBuilder<GetHistoryRequestProto, GetHistoryResponseProto>()
            .setType(MethodDescriptor.MethodType.UNARY)
            .setFullMethodName("messenger.ChatService/GetHistory")
            .setRequestMarshaller(GetHistoryRequestMarshaller())
            .setResponseMarshaller(GetHistoryResponseMarshaller())
            .build()

        val call = currentChannel.newCall(methodDescriptor, CallOptions.DEFAULT)
        call.start(object : ClientCall.Listener<GetHistoryResponseProto>() {
            override fun onMessage(message: GetHistoryResponseProto) {
                val history = message.messages.map { ProtoUtils.createMessageFromProto(it) }
                    .filterNot { deletedMessageHashes.contains(getMessageHash(it)) }

                val decryptedHistory = decryptE2EEMessages(roomId, history, context)

                messages.update { current ->
                    val currentMap = current.associateBy { getMessageHash(it) }
                    val mergedHistory = decryptedHistory.map { serverMsg ->
                        val localMsg = currentMap[getMessageHash(serverMsg)]
                        if (localMsg != null) {
                            serverMsg.copy(isRead = localMsg.isRead || serverMsg.isRead)
                        } else serverMsg
                    }
                    val historyHashes = mergedHistory.map { getMessageHash(it) }.toSet()
                    val optimisticOnly = current.filterNot { getMessageHash(it) in historyHashes }
                    (mergedHistory + optimisticOnly).sortedBy { it.timestamp }
                }

                // Save to cache
                scope.launch(Dispatchers.IO) {
                    val toCache = messages.value.filter { it.roomId == roomId || (roomId.startsWith("favorites_") && it.roomId.startsWith("favorites_")) }
                    if (toCache.isNotEmpty()) {
                        db()?.messageDao()?.insertMessages(toCache.map { it.toEntity() })
                    }
                }
            }
            override fun onClose(status: Status, trailers: Metadata) { onCompletion() }
        }, Metadata())
        call.sendMessage(GetHistoryRequestProto(limit = 100, room = roomId))
        call.halfClose()
        call.request(1)
    }

    private fun decryptE2EEMessages(roomId: String, msgs: List<Message>, context: Context?): List<Message> {
        if (context == null) return msgs
        return msgs.map { msg ->
            if (msg.isE2EE && msg.e2eePayload.isNotEmpty()) {
                val decrypted = lavender.client.android.data.crypto.E2EEManager.decryptMessage(context, roomId, msg.e2eePayload)
                if (decrypted != null) msg.copy(text = decrypted, isE2EE = false, e2eePayload = "")
                else msg.copy(text = "\uD83D\uDD12 Encrypted message", isE2EE = false, e2eePayload = "")
            } else msg
        }
    }

    // ======= Edit Message =======

    fun editMessage(id: String, text: String, cb: (Boolean, String) -> Unit) {
        val currentChannel = getChannel() ?: return
        val call = currentChannel.newCall(
            MethodDescriptor.newBuilder<EditMessageRequestProto, EditMessageResponseProto>()
                .setType(MethodDescriptor.MethodType.UNARY)
                .setFullMethodName("messenger.ChatService/EditMessage")
                .setRequestMarshaller(EditMessageRequestMarshaller())
                .setResponseMarshaller(EditMessageResponseMarshaller())
                .build(),
            CallOptions.DEFAULT
        )
        call.start(object : ClientCall.Listener<EditMessageResponseProto>() {
            override fun onMessage(msg: EditMessageResponseProto) { cb(msg.success, msg.message) }
            override fun onClose(status: Status, trailers: Metadata) {
                if (!status.isOk) cb(false, status.description ?: "Error")
            }
        }, Metadata())
        call.sendMessage(EditMessageRequestProto(id, text))
        call.halfClose()
        call.request(1)
    }

    // ======= Delete Message =======

    fun deleteMessage(m: Message, currentUsername: String?) {
        // Optimistic UI: remove locally first
        deletedMessageHashes.add(getMessageHash(m))
        messages.update { current -> current.filterNot { it.id == m.id } }
        scope.launch(Dispatchers.IO) { db()?.messageDao()?.deleteMessage(m.id) }

        val currentChannel = getChannel() ?: return
        val call = currentChannel.newCall(
            MethodDescriptor.newBuilder<DeleteMessagesRequestProto, DeleteMessagesResponseProto>()
                .setType(MethodDescriptor.MethodType.UNARY)
                .setFullMethodName("messenger.ChatService/DeleteMessages")
                .setRequestMarshaller(DeleteMessagesRequestMarshaller())
                .setResponseMarshaller(DeleteMessagesResponseMarshaller())
                .build(),
            CallOptions.DEFAULT
        )
        call.start(object : ClientCall.Listener<DeleteMessagesResponseProto>() {}, Metadata())
        call.sendMessage(DeleteMessagesRequestProto(listOf(ProtoUtils.createMessageProto(m)), currentUsername ?: ""))
        call.halfClose()
        call.request(1)
    }

    // ======= Set Reaction =======

    fun setReaction(messageId: String, username: String, emoji: String) {
        val currentChannel = getChannel() ?: return

        // Optimistic UI: update locally first
        messages.update { current ->
            val list = current.toMutableList()
            val index = list.indexOfFirst { it.id == messageId }
            if (index != -1) {
                val msg = list[index]
                val newReactions = msg.reactions.toMutableList()
                newReactions.removeAll { it.user == username }
                newReactions.add(Reaction(username, emoji))
                val newMsg = msg.copy(reactions = newReactions)
                list[index] = newMsg

                // Save optimistic update to local cache
                scope.launch(Dispatchers.IO) {
                    db()?.messageDao()?.insertMessages(listOf(newMsg.toEntity()))
                }
                list
            } else current
        }

        val methodDescriptor = MethodDescriptor.newBuilder<ReactionRequestProto, ReactionResponseProto>()
            .setType(MethodDescriptor.MethodType.UNARY)
            .setFullMethodName("messenger.ChatService/SetReaction")
            .setRequestMarshaller(ReactionRequestMarshaller())
            .setResponseMarshaller(ReactionResponseMarshaller())
            .build()
        val call = currentChannel.newCall(methodDescriptor, CallOptions.DEFAULT)
        call.start(object : ClientCall.Listener<ReactionResponseProto>() {}, Metadata())
        call.sendMessage(ReactionRequestProto(messageId, ReactionProto(username, emoji)))
        call.halfClose()
        call.request(1)
    }

    // ======= Mark Read =======

    fun markRead(rid: String, u: String, connectionStatus: ConnectionStatus, onComp: (() -> Unit)?) {
        appContext()?.let { LavenderMessagingService.dismissNotificationsForRoom(it, rid) }
        val currentChannel = getChannel()
        if (currentChannel == null || connectionStatus != ConnectionStatus.READY) {
            Log.d(TAG, "Queueing markRead for $rid because channel is not ready")
            pendingReads.add(rid)
            onComp?.invoke()
            return
        }
        val call = currentChannel.newCall(
            MethodDescriptor.newBuilder<MarkReadRequestProto, MarkReadResponseProto>()
                .setType(MethodDescriptor.MethodType.UNARY)
                .setFullMethodName("messenger.ChatService/MarkRead")
                .setRequestMarshaller(MarkReadRequestMarshaller())
                .setResponseMarshaller(MarkReadResponseMarshaller())
                .build(),
            CallOptions.DEFAULT
        )
        call.start(object : ClientCall.Listener<MarkReadResponseProto>() {
            override fun onClose(status: Status, trailers: Metadata) {
                if (status.isOk) pendingReads.remove(rid) else pendingReads.add(rid)
                onComp?.invoke()
            }
        }, Metadata())
        call.sendMessage(MarkReadRequestProto(rid, u, getUserId() ?: ""))
        call.halfClose()
        call.request(1)
    }

    // ======= Resend Pending Reads =======

    fun resendPendingReads(username: String, connectionStatus: ConnectionStatus) {
        val rooms = pendingReads.toList()
        if (rooms.isEmpty()) return

        Log.d(TAG, "Resending ${rooms.size} pending read signals")
        rooms.forEach { rid ->
            markRead(rid, username, connectionStatus, null)
        }
    }

    // ======= Delete Message from Server Signal =======

    fun handleDeleteMessageSignal(deletedId: String) {
        deletedMessageHashes.add("id:$deletedId")
        scope.launch(Dispatchers.IO) {
            db()?.messageDao()?.deleteMessage(deletedId)
        }
    }

    // ======= Read All Signal =======

    fun handleReadAllSignal(reader: String, targetRoomId: String, currentRoomId: String) {
        Log.d(TAG, "Received READ_ALL signal from $reader for room $targetRoomId (current: $currentRoomId)")

        if (targetRoomId == currentRoomId) {
            messages.update { current ->
                if (current.all { it.isRead }) current
                else current.map { it.copy(isRead = true) }
            }
        }

        if (targetRoomId.isNotEmpty()) {
            scope.launch(Dispatchers.IO) {
                db()?.messageDao()?.markRoomAsRead(targetRoomId)
            }
            // Broadcast read receipt to update chat list unread count
            onReadReceipt?.invoke(targetRoomId, reader)
        }
    }

    // ======= Clear Cache Signal =======

    fun handleClearCacheSignal(chatId: String, currentRoomId: String) {
        scope.launch(Dispatchers.IO) {
            db()?.messageDao()?.clearRoom(chatId)
            db()?.chatDao()?.deleteChat(chatId)
            if (chatId == currentRoomId) {
                messages.update { emptyList() }
            }
        }
    }

    // ======= Helpers =======

    private fun getMessageHash(message: Message): String =
        if (message.id.isNotEmpty()) "id:${message.id}"
        else "${message.user}:${message.text}:${message.timestamp / 1000}"
}
