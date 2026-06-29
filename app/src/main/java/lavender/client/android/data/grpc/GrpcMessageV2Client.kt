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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import lavender.client.android.data.db.AppDatabase
import lavender.client.android.data.db.toDomain
import lavender.client.android.data.db.toEntity
import lavender.client.android.data.models.Message
import lavender.client.android.data.models.ErrorHandler
import lavender.client.android.data.models.Reaction
import lavender.client.android.data.proto.*
import org.json.JSONObject

/**
 * Handles Messages V2 operations: getHistoryV2, sendMessageV2, editMessageV2, deleteMessageV2, setReactionV2.
 *
 * Uses v2 proto types (MessageV2Proto) with cursor-based pagination.
 * Resolves sender_id (UUID) to username via allUsers lookup.
 */
class GrpcMessageV2Client(
    private val getChannel: () -> io.grpc.ManagedChannel?,
    private val getUserId: () -> String?,
    private val getUsername: () -> String?,
    private val messages: MutableStateFlow<List<Message>>,
    private val allUsers: () -> List<UserInfoProto>,
    private val deletedMessageHashes: MutableSet<String>,
    private val scope: CoroutineScope,
    private val appContext: () -> Context?,
    private val onReadReceipt: ((String, String) -> Unit)? = null
) {
    companion object {
        private const val TAG = "GrpcMsgV2"
        private const val MAX_HISTORY_LIMIT = 100

        private val METHOD_GET_HISTORY_V2 = MethodDescriptor.newBuilder<GetHistoryV2RequestProto, GetHistoryV2ResponseProto>()
            .setType(MethodDescriptor.MethodType.UNARY)
            .setFullMethodName("messenger.ChatService/GetHistoryV2")
            .setRequestMarshaller(GetHistoryV2RequestMarshaller())
            .setResponseMarshaller(GetHistoryV2ResponseMarshaller())
            .build()

        private val METHOD_SEND_MESSAGE_V2 = MethodDescriptor.newBuilder<SendMessageV2RequestProto, SendMessageV2ResponseProto>()
            .setType(MethodDescriptor.MethodType.UNARY)
            .setFullMethodName("messenger.ChatService/SendMessageV2")
            .setRequestMarshaller(SendMessageV2RequestMarshaller())
            .setResponseMarshaller(SendMessageV2ResponseMarshaller())
            .build()

        private val METHOD_EDIT_MESSAGE_V2 = MethodDescriptor.newBuilder<EditMessageV2RequestProto, EditMessageV2ResponseProto>()
            .setType(MethodDescriptor.MethodType.UNARY)
            .setFullMethodName("messenger.ChatService/EditMessageV2")
            .setRequestMarshaller(EditMessageV2RequestMarshaller())
            .setResponseMarshaller(EditMessageV2ResponseMarshaller())
            .build()

        private val METHOD_DELETE_MESSAGE_V2 = MethodDescriptor.newBuilder<DeleteMessageV2RequestProto, DeleteMessageV2ResponseProto>()
            .setType(MethodDescriptor.MethodType.UNARY)
            .setFullMethodName("messenger.ChatService/DeleteMessageV2")
            .setRequestMarshaller(DeleteMessageV2RequestMarshaller())
            .setResponseMarshaller(DeleteMessageV2ResponseMarshaller())
            .build()

        private val METHOD_SET_REACTION_V2 = MethodDescriptor.newBuilder<SetReactionV2RequestProto, SetReactionV2ResponseProto>()
            .setType(MethodDescriptor.MethodType.UNARY)
            .setFullMethodName("messenger.ChatService/SetReactionV2")
            .setRequestMarshaller(SetReactionV2RequestMarshaller())
            .setResponseMarshaller(SetReactionV2ResponseMarshaller())
            .build()

        private val METHOD_SEARCH_MESSAGES = MethodDescriptor.newBuilder<SearchMessagesRequestProto, SearchMessagesResponseProto>()
            .setType(MethodDescriptor.MethodType.UNARY)
            .setFullMethodName("messenger.ChatService/SearchMessages")
            .setRequestMarshaller(SearchMessagesRequestMarshaller())
            .setResponseMarshaller(SearchMessagesResponseMarshaller())
            .build()
    }

    private var database: AppDatabase? = null
    private fun db() = database ?: appContext()?.let {
        val d = AppDatabase.getDatabase(it)
        database = d
        d
    }

    // ====== Resolve sender UUID → username ======

    private fun resolveUsername(senderId: String): String {
        if (senderId.isEmpty()) return ""
        return allUsers().firstOrNull { it.userId == senderId }?.username ?: ""
    }

    // ====== Parse reactions JSON bytes → List<Reaction> ======

    internal fun parseReactions(reactionsBytes: ByteArray): List<Reaction> {
        if (reactionsBytes.isEmpty()) return emptyList()
        return try {
            val obj = JSONObject(String(reactionsBytes))
            val result = mutableListOf<Reaction>()
            for (key in obj.keys()) {
                val emoji = obj.getString(key)
                if (emoji.isNotEmpty()) {
                    result.add(Reaction(user = key, emoji = emoji))
                }
            }
            Log.d(TAG, "parseReactions: ${reactionsBytes.size} bytes → ${result.size} reactions: $result")
            result
        } catch (e: Exception) {
            Log.e(TAG, "parseReactions error: ${String(reactionsBytes)}", e)
            ErrorHandler.handle("$TAG.parseReactions", e)
            emptyList()
        }
    }

    // ====== Convert MessageV2Proto → domain Message ======

    fun messageV2ToDomain(proto: MessageV2Proto): Message {
        val timestamp = proto.createdAt?.let {
            it.seconds * 1000 + (it.nanos / 1000000)
        } ?: System.currentTimeMillis()

        val username = resolveUsername(proto.senderId)

        var imageUrl = ""; var imageUrls = emptyList<String>()
        var voiceUrl = ""; var duration = 0
        var repliedToMessageId = ""; var repliedToText = ""

        when {
            proto.media != null -> {
                when (proto.media.type) {
                    "image" -> {
                        imageUrl = proto.media.url
                        imageUrls = proto.media.urls.ifEmpty { listOf(proto.media.url).filter { it.isNotEmpty() } }
                    }
                    "voice" -> {
                        voiceUrl = proto.media.url
                        duration = proto.media.duration
                    }
                }
            }
            proto.reply != null -> {
                repliedToMessageId = proto.reply.messageId
                repliedToText = proto.reply.preview
            }
        }

        return Message(
            id = proto.id,
            user = username,
            text = proto.text,
            timestamp = timestamp,
            reactions = parseReactions(proto.reactions),
            repliedToMessageId = repliedToMessageId,
            repliedToText = repliedToText,
            roomId = proto.roomId,
            isRead = proto.isRead,
            imageUrl = imageUrl,
            imageUrls = imageUrls,
            edited = proto.edited,
            voiceUrl = voiceUrl,
            duration = duration,
            userId = proto.senderId,
            isE2EE = proto.isE2EE,
            e2eePayload = proto.e2eePayload
        )
    }

    // ====== Convert domain Message → SendMessageV2RequestProto ======

    fun domainToSendRequest(message: Message): SendMessageV2RequestProto {
        val media = when {
            message.voiceUrl.isNotEmpty() -> MessageMediaProto(type = "voice", url = message.voiceUrl, duration = message.duration)
            message.imageUrl.isNotEmpty() -> MessageMediaProto(
                type = "image",
                url = message.imageUrl,
                urls = message.imageUrls.ifEmpty { listOf(message.imageUrl).filter { it.isNotEmpty() } }
            )
            else -> null
        }

        return SendMessageV2RequestProto(
            roomId = message.roomId,
            text = message.text,
            media = media,
            replyToId = message.repliedToMessageId,
            isE2EE = message.isE2EE,
            e2eePayload = message.e2eePayload
        )
    }

    // ====== Get History V2 (cursor-based pagination) ======

    fun loadHistoryV2(roomId: String, cursor: String = "", limit: Int = MAX_HISTORY_LIMIT, onCompletion: (String, Boolean) -> Unit = { _, _ -> }) {
        // Always load from cache first (offline-first)
        scope.launch(Dispatchers.IO) {
            val cached = db()?.messageDao()?.getMessagesForRoom(roomId)?.map { it.toDomain() }
                ?: emptyList()
            val dedupedCache = deduplicateByContent(cached)
            Log.d(TAG, "loadHistoryV2 cache: loaded ${cached.size} msgs (${dedupedCache.size} after dedup) from Room DB, ${dedupedCache.count { it.reactions.isNotEmpty() }} with reactions")
            if (dedupedCache.isNotEmpty()) {
                messages.update { current ->
                    if (current.isEmpty()) dedupedCache
                    else {
                        val currentMap = current.associateBy { getMessageHash(it) }
                        dedupedCache.map { cachedMsg ->
                            val localMsg = currentMap[getMessageHash(cachedMsg)]
                            if (localMsg != null) {
                                val mergedReactions = if (cachedMsg.reactions.isEmpty() && localMsg.reactions.isNotEmpty()) {
                                    localMsg.reactions
                                } else if (cachedMsg.reactions.isNotEmpty() && localMsg.reactions.isNotEmpty()) {
                                    val cachedUserIds = cachedMsg.reactions.map { it.user }.toSet()
                                    cachedMsg.reactions + localMsg.reactions.filter { it.user !in cachedUserIds }
                                } else {
                                    cachedMsg.reactions
                                }
                                cachedMsg.copy(isRead = localMsg.isRead || cachedMsg.isRead, reactions = mergedReactions)
                            } else cachedMsg
                        } + current.filterNot { msg -> dedupedCache.any { getMessageHash(it) == getMessageHash(msg) } }
                    }
                }
            }
        }

        val currentChannel = getChannel()
        if (currentChannel == null) {
            scope.launch { onCompletion("", false) }
            return
        }

        val call = currentChannel.newCall(METHOD_GET_HISTORY_V2, CallOptions.DEFAULT)
        call.start(object : ClientCall.Listener<GetHistoryV2ResponseProto>() {
            override fun onMessage(message: GetHistoryV2ResponseProto) {
                val history = message.messages
                    .map { messageV2ToDomain(it) }
                    .filterNot { deletedMessageHashes.contains(getMessageHash(it)) }

                Log.d(TAG, "loadHistoryV2: server returned ${message.messages.size} msgs, ${history.count { it.reactions.isNotEmpty() }} with reactions, room=$roomId")

                messages.update { current ->
                    val currentMap = current.associateBy { getMessageHash(it) }
                    val currentByContent = current.associateBy { getContentHash(it) }
                    val mergedHistory = history.map { serverMsg ->
                        val localMsg = currentMap[getMessageHash(serverMsg)]
                            ?: currentByContent[getContentHash(serverMsg)]
                        if (localMsg != null) {
                            val mergedReactions = if (serverMsg.reactions.isEmpty() && localMsg.reactions.isNotEmpty()) {
                                localMsg.reactions
                            } else if (serverMsg.reactions.isNotEmpty() && localMsg.reactions.isNotEmpty()) {
                                val serverUserIds = serverMsg.reactions.map { it.user }.toSet()
                                serverMsg.reactions + localMsg.reactions.filter { it.user !in serverUserIds }
                            } else {
                                serverMsg.reactions
                            }
                            serverMsg.copy(isRead = localMsg.isRead || serverMsg.isRead, reactions = mergedReactions)
                        } else serverMsg
                    }
                    val historyContentHashes = mergedHistory.map { getContentHash(it) }.toSet()
                    val historyHashes = mergedHistory.map { getMessageHash(it) }.toSet()
                    val optimisticOnly = current.filterNot { msg ->
                        getMessageHash(msg) in historyHashes || getContentHash(msg) in historyContentHashes
                    }
                    val result = deduplicateByContent(mergedHistory + optimisticOnly).sortedBy { it.timestamp }
                    Log.d(TAG, "loadHistoryV2: merged ${result.size} msgs, ${result.count { it.reactions.isNotEmpty() }} with reactions")
                    result
                }

                scope.launch(Dispatchers.IO) {
                    val toCache = messages.value.filter { it.roomId == roomId }
                    if (toCache.isNotEmpty()) {
                        db()?.messageDao()?.insertMessages(toCache.map { it.toEntity() })
                        Log.d(TAG, "loadHistoryV2: saved ${toCache.size} msgs to cache, ${toCache.count { it.reactions.isNotEmpty() }} with reactions")
                    }
                }

                onCompletion(message.nextCursor, message.hasMore)
            }
            override fun onClose(status: Status, trailers: Metadata) {
                if (!status.isOk) {
                    ErrorHandler.handle("$TAG.loadHistoryV2", StatusRuntimeException(status))
                    onCompletion("", false)
                }
            }
        }, Metadata())
        call.sendMessage(GetHistoryV2RequestProto(roomId = roomId, limit = limit, cursor = cursor))
        call.halfClose()
        call.request(1)
    }

    // ====== Send Message V2 ======

    fun sendMessageV2(message: Message, onResult: ((MessageV2Proto?) -> Unit)? = null) {
        val currentChannel = getChannel()
        if (currentChannel == null) {
            ErrorHandler.handle("$TAG.sendMessageV2", Exception("No channel available"))
            onResult?.invoke(null)
            return
        }

        val call = currentChannel.newCall(METHOD_SEND_MESSAGE_V2, CallOptions.DEFAULT)
        call.start(object : ClientCall.Listener<SendMessageV2ResponseProto>() {
            override fun onMessage(response: SendMessageV2ResponseProto) {
                if (response.success && response.message != null) {
                    val serverMsg = response.message
                    val serverId = serverMsg.id
                    messages.update { current ->
                        val updated = current.map {
                            if (it.id == message.id) {
                                val newId = if (serverId.isNotEmpty() && serverId != message.id) serverId else it.id
                                it.copy(id = newId, isSent = true)
                            } else it
                        }
                        val seen = mutableSetOf<String>()
                        updated.filter { msg -> msg.id !in seen && seen.add(msg.id) }
                    }
                    if (serverId.isNotEmpty() && serverId != message.id) {
                        scope.launch(Dispatchers.IO) {
                            db()?.messageDao()?.deleteMessage(message.id)
                            val updated = message.copy(id = serverId, isSent = true)
                            db()?.messageDao()?.insertMessages(listOf(updated.toEntity()))
                        }
                    } else {
                        scope.launch(Dispatchers.IO) {
                            db()?.messageDao()?.insertMessages(listOf(message.copy(isSent = true).toEntity()))
                        }
                    }
                    onResult?.invoke(serverMsg)
                } else {
                    ErrorHandler.handle("$TAG.sendMessageV2", Exception(response.error.ifEmpty { "Server returned success=false" }))
                    onResult?.invoke(null)
                }
            }
            override fun onClose(status: Status, trailers: Metadata) {
                if (!status.isOk) {
                    ErrorHandler.handle("$TAG.sendMessageV2", StatusRuntimeException(status))
                    onResult?.invoke(null)
                }
            }
        }, Metadata())
        call.sendMessage(domainToSendRequest(message))
        call.halfClose()
        call.request(1)
    }

    // ====== Edit Message V2 ======

    fun editMessageV2(messageId: String, text: String, cb: (Boolean, String) -> Unit) {
        val currentChannel = getChannel() ?: return
        val call = currentChannel.newCall(METHOD_EDIT_MESSAGE_V2, CallOptions.DEFAULT)
        call.start(object : ClientCall.Listener<EditMessageV2ResponseProto>() {
            override fun onMessage(msg: EditMessageV2ResponseProto) { cb(msg.success, msg.message) }
            override fun onClose(status: Status, trailers: Metadata) {
                if (!status.isOk) cb(false, status.description ?: "Error")
            }
        }, Metadata())
        call.sendMessage(EditMessageV2RequestProto(messageId, text))
        call.halfClose()
        call.request(1)
    }

    // ====== Delete Message V2 (with optimistic UI) ======

    fun deleteMessageV2(messageIds: List<String>, cb: (Boolean) -> Unit = {}) {
        // Optimistic UI: remove from local list before server confirms
        messageIds.forEach { id ->
            deletedMessageHashes.add("id:$id")
        }
        messages.update { current -> current.filterNot { it.id in messageIds } }
        scope.launch(Dispatchers.IO) {
            messageIds.forEach { id -> db()?.messageDao()?.deleteMessage(id) }
        }

        val currentChannel = getChannel() ?: return
        val call = currentChannel.newCall(METHOD_DELETE_MESSAGE_V2, CallOptions.DEFAULT)
        call.start(object : ClientCall.Listener<DeleteMessageV2ResponseProto>() {
            override fun onMessage(msg: DeleteMessageV2ResponseProto) { cb(msg.success) }
            override fun onClose(status: Status, trailers: Metadata) {
                if (!status.isOk) cb(false)
            }
        }, Metadata())
        call.sendMessage(DeleteMessageV2RequestProto(messageIds, getUserId() ?: ""))
        call.halfClose()
        call.request(1)
    }

    // ====== Set Reaction V2 ======

    fun setReactionV2(messageId: String, username: String, emoji: String) {
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
                list[index] = msg.copy(reactions = newReactions)
                Log.d(TAG, "setReactionV2 optimistic: msg $messageId now has ${newReactions.size} reactions")
                val msgToSave = list[index]
                scope.launch(Dispatchers.IO) {
                    db()?.messageDao()?.insertMessages(listOf(msgToSave.toEntity()))
                    Log.d(TAG, "setReactionV2: saved optimistic reaction to Room DB")
                }
            }
            list
        }

        val call = currentChannel.newCall(METHOD_SET_REACTION_V2, CallOptions.DEFAULT)
        call.start(object : ClientCall.Listener<SetReactionV2ResponseProto>() {
            override fun onMessage(response: SetReactionV2ResponseProto) {
                if (response.success) {
                    val serverReactions = if (response.reactions.isNotEmpty()) {
                        parseReactions(response.reactions)
                    } else {
                        emptyList()
                    }
                    var updatedMsg: Message? = null
                    messages.update { current ->
                        val list = current.toMutableList()
                        val idx = list.indexOfFirst { it.id == messageId }
                        if (idx != -1) {
                            list[idx] = list[idx].copy(reactions = serverReactions)
                            updatedMsg = list[idx]
                        }
                        list
                    }
                    if (updatedMsg != null) {
                        val msgToSave = updatedMsg!!
                        scope.launch(Dispatchers.IO) {
                            db()?.messageDao()?.insertMessages(listOf(msgToSave.toEntity()))
                        }
                    }
                }
            }
            override fun onClose(status: Status, trailers: Metadata) {}
        }, Metadata())
        call.sendMessage(SetReactionV2RequestProto(messageId, emoji))
        call.halfClose()
        call.request(1)
    }

    // ====== Search Messages ======

    suspend fun searchMessages(roomId: String = "", query: String, limit: Int = 20): List<SearchResultProto> {
        val channel = getChannel() ?: return emptyList()
        return kotlinx.coroutines.suspendCancellableCoroutine { cont ->
            val call = channel.newCall(METHOD_SEARCH_MESSAGES, CallOptions.DEFAULT)
            call.start(object : ClientCall.Listener<SearchMessagesResponseProto>() {
                override fun onMessage(message: SearchMessagesResponseProto) {
                    if (cont.isActive) cont.resumeWith(Result.success(message.messages))
                }
                override fun onClose(status: Status, trailers: Metadata) {
                    if (!status.isOk) {
                        ErrorHandler.handle("$TAG.searchMessages", StatusRuntimeException(status))
                        if (cont.isActive) cont.resumeWith(Result.success(emptyList()))
                    }
                }
            }, Metadata())
            call.sendMessage(SearchMessagesRequestProto(roomId = roomId, query = query, limit = limit))
            call.halfClose()
            call.request(1)
        }
    }

    // ====== Helpers ======

    private fun getMessageHash(message: Message): String =
        if (message.id.isNotEmpty()) "id:${message.id}"
        else "${message.user}:${message.text}:${message.timestamp / 1000}"

    private fun getContentHash(message: Message): String =
        "${message.user}:${message.text}:${message.timestamp / 1000}"

    private fun deduplicateByContent(messages: List<Message>): List<Message> {
        val seen = mutableMapOf<String, Message>()
        for (msg in messages) {
            val key = getContentHash(msg)
            val existing = seen[key]
            if (existing == null) {
                seen[key] = msg
            } else {
                val existingIsTemp = existing.id.startsWith("temp_")
                val currentIsTemp = msg.id.startsWith("temp_")
                if (existingIsTemp && !currentIsTemp) {
                    seen[key] = msg
                }
            }
        }
        return seen.values.toList()
    }

    private class StatusRuntimeException(status: Status) :
        Exception("gRPC error: ${status.code} - ${status.description}")
}
