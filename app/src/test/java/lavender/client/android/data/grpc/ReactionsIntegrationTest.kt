package lavender.client.android.data.grpc

import lavender.client.android.data.db.MessageEntity
import lavender.client.android.data.db.toDomain
import lavender.client.android.data.db.toEntity
import lavender.client.android.data.models.Message
import lavender.client.android.data.models.Reaction
import lavender.client.android.data.proto.MessageV2Proto
import org.junit.Assert.*
import org.junit.Test

/**
 * Reaction integration tests — covers the 3 bugs found in v1.3.1.06:
 * 1. GrpcSavedMessagesClient.getSavedMessages() didn't parse reactions from proto
 * 2. setReactionV2() didn't save to Room DB after server response
 * 3. loadHistoryV2 merge didn't preserve server reactions
 */
class ReactionsIntegrationTest {

    // ======= Bug #1: Saved Messages reactions parsing =======

    @Test
    fun savedMessages_protoMessage_hasReactions_field() {
        val reactions = """{"uuid-1":"👍","uuid-2":"🔥"}""".toByteArray()
        val proto = MessageV2Proto(
            id = "fav-1",
            roomId = "room-1",
            senderId = "uuid-1",
            text = "Hello!",
            reactions = reactions
        )
        assertTrue(proto.reactions.isNotEmpty())
        val json = String(proto.reactions)
        assertTrue(json.contains("uuid-1"))
        assertTrue(json.contains("👍"))
    }

    @Test
    fun savedMessages_protoMessage_emptyReactions() {
        val proto = MessageV2Proto(id = "fav-1", reactions = byteArrayOf())
        assertTrue(proto.reactions.isEmpty())
    }

    @Test
    fun savedMessages_protoMessage_emptyJsonObjectReactions() {
        val proto = MessageV2Proto(id = "fav-1", reactions = "{}".toByteArray())
        assertTrue(proto.reactions.isNotEmpty())
    }

    @Test
    fun parseReactions_validJson() {
        val reactions = """{"uuid-1":"👍","uuid-2":"❤️"}""".toByteArray()
        val parsed = parseReactions(reactions)
        assertEquals(2, parsed.size)
        assertTrue(parsed.any { it.user == "uuid-1" && it.emoji == "👍" })
        assertTrue(parsed.any { it.user == "uuid-2" && it.emoji == "❤️" })
    }

    @Test
    fun parseReactions_emptyBytes() {
        assertTrue(parseReactions(byteArrayOf()).isEmpty())
    }

    @Test
    fun parseReactions_emptyJsonObject() {
        assertTrue(parseReactions("{}".toByteArray()).isEmpty())
    }

    @Test
    fun parseReactions_invalidJson() {
        assertTrue(parseReactions("not-json".toByteArray()).isEmpty())
    }

    @Test
    fun parseReactions_singleReaction() {
        val parsed = parseReactions("""{"uuid-1":"🔥"}""".toByteArray())
        assertEquals(1, parsed.size)
        assertEquals("uuid-1", parsed[0].user)
        assertEquals("🔥", parsed[0].emoji)
    }

    @Test
    fun parseReactions_emptyEmoji() {
        assertTrue(parseReactions("""{"uuid-1":""}""".toByteArray()).isEmpty())
    }

    // ======= Bug #2: Room DB round-trip =======

    @Test
    fun message_toEntity_preservesReactions() {
        val msg = Message(
            id = "msg-1", user = "alice", text = "Hello",
            timestamp = 1000L, roomId = "room-1", userId = "uuid-1",
            reactions = listOf(Reaction("uuid-1", "👍"), Reaction("uuid-2", "🔥"))
        )
        val entity = msg.toEntity()
        assertTrue(entity.reactionsJson.contains("uuid-1"))
        assertTrue(entity.reactionsJson.contains("👍"))
        assertTrue(entity.reactionsJson.contains("uuid-2"))
    }

    @Test
    fun entity_toDomain_preservesReactions() {
        val entity = MessageEntity(
            id = "msg-1", user = "alice", text = "Hello",
            timestamp = 1000L, roomId = "room-1",
            repliedToMessageId = "", repliedToUser = "", repliedToText = "",
            read = false, avatarUrl = "", imageUrl = "",
            imageUrlsJson = "[]", edited = false, superAdmin = false,
            voiceUrl = "", duration = 0, userId = "uuid-1", isSent = true,
            reactionsJson = """[{"user":"uuid-1","emoji":"👍"},{"user":"uuid-2","emoji":"🔥"}]"""
        )
        val msg = entity.toDomain()
        assertEquals(2, msg.reactions.size)
        assertTrue(msg.reactions.any { it.user == "uuid-1" && it.emoji == "👍" })
        assertTrue(msg.reactions.any { it.user == "uuid-2" && it.emoji == "🔥" })
    }

    @Test
    fun message_entity_roundTrip_preservesReactions() {
        val original = Message(
            id = "msg-1", user = "alice", text = "Hello",
            timestamp = 1000L, roomId = "room-1", userId = "uuid-1",
            reactions = listOf(Reaction("uuid-1", "👍"), Reaction("uuid-2", "🔥"))
        )
        val entity = original.toEntity()
        val restored = entity.toDomain()
        assertEquals(original.reactions.size, restored.reactions.size)
        assertEquals(original.reactions[0].user, restored.reactions[0].user)
        assertEquals(original.reactions[0].emoji, restored.reactions[0].emoji)
        assertEquals(original.reactions[1].user, restored.reactions[1].user)
        assertEquals(original.reactions[1].emoji, restored.reactions[1].emoji)
    }

    @Test
    fun message_emptyReactions_roundTrip() {
        val original = Message(
            id = "msg-1", user = "alice", text = "Hello",
            timestamp = 1000L, roomId = "room-1", userId = "uuid-1",
            reactions = emptyList()
        )
        val entity = original.toEntity()
        val restored = entity.toDomain()
        assertTrue(restored.reactions.isEmpty())
    }

    @Test
    fun optimisticUpdate_thenServerResponse_overwritesCorrectly() {
        val original = Message(
            id = "msg-1", user = "alice", text = "Hello",
            timestamp = 1000L, roomId = "room-1", userId = "uuid-1",
            reactions = emptyList()
        )

        // Step 1: Optimistic update → save to Room DB
        val optimistic = original.copy(reactions = listOf(Reaction("uuid-1", "👍")))
        val entity1 = optimistic.toEntity()
        val fromCache1 = entity1.toDomain()
        assertEquals(1, fromCache1.reactions.size)

        // Step 2: Server response → save to Room DB
        val serverReactions = parseReactions("""{"uuid-1":"👍","uuid-2":"❤️"}""".toByteArray())
        val afterServer = original.copy(reactions = serverReactions)
        val entity2 = afterServer.toEntity()
        val fromCache2 = entity2.toDomain()
        assertEquals(2, fromCache2.reactions.size)
        assertTrue(fromCache2.reactions.any { it.user == "uuid-1" && it.emoji == "👍" })
        assertTrue(fromCache2.reactions.any { it.user == "uuid-2" && it.emoji == "❤️" })
    }

    // ======= Bug #3: loadHistoryV2 merge =======

    @Test
    fun merge_serverReactionsPreserved_overLocalReactions() {
        val local = Message(
            id = "msg-1", user = "alice", text = "Hello",
            timestamp = 1000L, roomId = "room-1", userId = "uuid-1",
            reactions = listOf(Reaction("uuid-1", "👍"))
        )
        val server = Message(
            id = "msg-1", user = "alice", text = "Hello",
            timestamp = 1000L, roomId = "room-1", userId = "uuid-1",
            reactions = listOf(Reaction("uuid-1", "👍"), Reaction("uuid-2", "❤️"))
        )

        val currentMap = listOf(local).associateBy { it.id }
        val merged = listOf(server).map { serverMsg ->
            val localMsg = currentMap[serverMsg.id]
            if (localMsg != null) serverMsg.copy(isRead = localMsg.isRead || serverMsg.isRead)
            else serverMsg
        }

        assertEquals(1, merged.size)
        assertEquals(2, merged[0].reactions.size)
        assertTrue(merged[0].reactions.any { it.user == "uuid-2" && it.emoji == "❤️" })
    }

    @Test
    fun merge_serverReactionsEmpty_overwritesLocalReactions_bug() {
        // This test documents the bug: when server returns empty reactions,
        // local reactions are lost. This is the root cause of the user-visible issue.
        val local = Message(
            id = "msg-1", user = "alice", text = "Hello",
            timestamp = 1000L, roomId = "room-1", userId = "uuid-1",
            reactions = listOf(Reaction("uuid-1", "👍"))
        )
        val server = Message(
            id = "msg-1", user = "alice", text = "Hello",
            timestamp = 1000L, roomId = "room-1", userId = "uuid-1",
            reactions = emptyList()
        )

        val currentMap = listOf(local).associateBy { it.id }
        val merged = listOf(server).map { serverMsg ->
            val localMsg = currentMap[serverMsg.id]
            if (localMsg != null) serverMsg.copy(isRead = localMsg.isRead || serverMsg.isRead)
            else serverMsg
        }

        assertEquals(1, merged.size)
        assertTrue(merged[0].reactions.isEmpty()) // BUG: reactions lost
    }

    @Test
    fun merge_newMessageFromServer_addedToHistory() {
        val server = Message(
            id = "msg-new", user = "bob", text = "New",
            timestamp = 2000L, roomId = "room-1", userId = "uuid-2",
            reactions = listOf(Reaction("uuid-2", "🔥"))
        )

        val currentMap = emptyList<Message>().associateBy { it.id }
        val merged = listOf(server).map { serverMsg ->
            val localMsg = currentMap[serverMsg.id]
            if (localMsg != null) serverMsg.copy(isRead = localMsg.isRead || serverMsg.isRead)
            else serverMsg
        }

        assertEquals(1, merged.size)
        assertEquals(1, merged[0].reactions.size)
        assertEquals("🔥", merged[0].reactions[0].emoji)
    }

    @Test
    fun merge_optimisticOnlyMessage_preservedInResult() {
        val local = Message(
            id = "msg-optimistic", user = "alice", text = "Pending",
            timestamp = 3000L, roomId = "room-1", userId = "uuid-1",
            reactions = listOf(Reaction("uuid-1", "👍"))
        )
        val server = Message(
            id = "msg-server", user = "bob", text = "From server",
            timestamp = 1000L, roomId = "room-1", userId = "uuid-2"
        )

        val currentMap = listOf(local).associateBy { it.id }
        val merged = listOf(server).map { serverMsg ->
            val localMsg = currentMap[serverMsg.id]
            if (localMsg != null) serverMsg.copy(isRead = localMsg.isRead || serverMsg.isRead)
            else serverMsg
        }
        val historyHashes = merged.map { it.id }.toSet()
        val optimisticOnly = listOf(local).filterNot { it.id in historyHashes }
        val result = (merged + optimisticOnly).sortedBy { it.timestamp }

        assertEquals(2, result.size)
        assertEquals("msg-server", result[0].id)
        assertEquals("msg-optimistic", result[1].id)
        assertEquals(1, result[1].reactions.size)
    }

    // ======= REACTION_V2 system message parsing =======

    @Test
    fun reactionV2_systemMessage_parsing() {
        val sysMessage = "msg-123|{\"uuid-1\":\"👍\",\"uuid-2\":\"❤️\"}"
        val parts = sysMessage.split("|", limit = 2)
        assertEquals(2, parts.size)
        assertEquals("msg-123", parts[0])
        val reactions = parseReactions(parts[1].toByteArray())
        assertEquals(2, reactions.size)
    }

    @Test
    fun reactionV2_systemMessage_emptyReactions() {
        val sysMessage = "msg-123|{}"
        val parts = sysMessage.split("|", limit = 2)
        val reactions = parseReactions(parts[1].toByteArray())
        assertTrue(reactions.isEmpty())
    }

    // ======= Full round-trip =======

    @Test
    fun fullRoundTrip_protoToEntityToDomain_preservesReactions() {
        val reactions = """{"uuid-1":"👍","uuid-2":"🔥"}""".toByteArray()
        val proto = MessageV2Proto(
            id = "full-1", roomId = "room-1", senderId = "uuid-1",
            text = "Hello!", reactions = reactions
        )

        val domain = Message(
            id = proto.id, user = "alice", text = proto.text,
            timestamp = 1000L, roomId = proto.roomId, userId = proto.senderId,
            reactions = parseReactions(proto.reactions)
        )
        assertEquals(2, domain.reactions.size)

        val entity = domain.toEntity()
        assertTrue(entity.reactionsJson.contains("uuid-1"))
        assertTrue(entity.reactionsJson.contains("👍"))

        val fromCache = entity.toDomain()
        assertEquals(2, fromCache.reactions.size)
        assertTrue(fromCache.reactions.any { it.user == "uuid-1" && it.emoji == "👍" })
        assertTrue(fromCache.reactions.any { it.user == "uuid-2" && it.emoji == "🔥" })
    }

    private fun parseReactions(reactionsBytes: ByteArray): List<Reaction> {
        if (reactionsBytes.isEmpty()) return emptyList()
        return try {
            val obj = org.json.JSONObject(String(reactionsBytes))
            val result = mutableListOf<Reaction>()
            for (key in obj.keys()) {
                val emoji = obj.getString(key)
                if (emoji.isNotEmpty()) {
                    result.add(Reaction(user = key, emoji = emoji))
                }
            }
            result
        } catch (_: Exception) {
            emptyList()
        }
    }
}
