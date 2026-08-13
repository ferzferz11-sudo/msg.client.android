package lavender.client.android.data.grpc

import com.google.protobuf.CodedInputStream
import com.google.protobuf.WireFormat
import lavender.client.android.data.proto.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for critical marshallers in GrpcMarshallers.kt.
 * Focus: field order correctness (field numbers must match server proto),
 * serialization non-empty, and parse-empty for response marshallers.
 *
 * Historical context: AddContact/RemoveContact/GetContacts/PinChat/PinMessage
 * marshallers had field order mismatches (alphabetical vs server-defined order).
 */
class GrpcMarshallersTest {

    // Helper: read all field numbers from serialized bytes
    private fun readFieldNumbers(bytes: ByteArray): List<Int> {
        val fields = mutableListOf<Int>()
        val cis = CodedInputStream.newInstance(bytes)
        while (!cis.isAtEnd) {
            val tag = cis.readTag()
            if (tag == 0) break
            fields.add(WireFormat.getTagFieldNumber(tag))
            // Skip the field value
            when (WireFormat.getTagWireType(tag)) {
                WireFormat.WIRETYPE_VARINT -> cis.readInt64()
                WireFormat.WIRETYPE_FIXED64 -> cis.readFixed64()
                WireFormat.WIRETYPE_LENGTH_DELIMITED -> cis.readBytes()
                WireFormat.WIRETYPE_FIXED32 -> cis.readFixed32()
                else -> break
            }
        }
        return fields
    }

    // ======= AddContact =======
    // Server proto: username=1, contact_username=2, user_id=3

    @Test
    fun addContactRequest_stream_fieldOrder() {
        val req = AddContactRequestProto(username = "alice", contactUsername = "bob", userId = "uuid-123")
        val bytes = AddContactRequestMarshaller().stream(req).readBytes()
        val fields = readFieldNumbers(bytes)
        assertEquals(listOf(1, 2, 3), fields)
    }

    @Test
    fun addContactRequest_stream_nonEmpty() {
        val req = AddContactRequestProto(username = "alice", contactUsername = "bob", userId = "uuid-123")
        val bytes = AddContactRequestMarshaller().stream(req).readBytes()
        assertTrue(bytes.isNotEmpty())
    }

    @Test
    fun addContactResponse_parseEmpty() {
        val parsed = AddContactResponseMarshaller().parse(java.io.ByteArrayInputStream(byteArrayOf()))
        assertFalse(parsed.success)
        assertEquals("", parsed.message)
    }

    // ======= RemoveContact =======
    // Server proto: username=1, contact_username=2, user_id=3

    @Test
    fun removeContactRequest_stream_fieldOrder() {
        val req = RemoveContactRequestProto(username = "alice", contactUsername = "bob", userId = "uuid-123")
        val bytes = RemoveContactRequestMarshaller().stream(req).readBytes()
        val fields = readFieldNumbers(bytes)
        assertEquals(listOf(1, 2, 3), fields)
    }

    @Test
    fun removeContactResponse_parseEmpty() {
        val parsed = RemoveContactResponseMarshaller().parse(java.io.ByteArrayInputStream(byteArrayOf()))
        assertFalse(parsed.success)
        assertEquals("", parsed.message)
    }

    // ======= GetContacts =======
    // Server proto: username=1, user_id=2

    @Test
    fun getContactsRequest_stream_fieldOrder() {
        val req = GetContactsRequestProto(username = "alice", userId = "uuid-123")
        val bytes = GetContactsRequestMarshaller().stream(req).readBytes()
        val fields = readFieldNumbers(bytes)
        assertEquals(listOf(1, 2), fields)
    }

    @Test
    fun getContactsResponse_parseEmpty() {
        val parsed = GetContactsResponseMarshaller().parse(java.io.ByteArrayInputStream(byteArrayOf()))
        assertTrue(parsed.contacts.isEmpty())
    }

    // ======= PinChat / UnPinChat =======
    // Server proto: user_id=1, chat_id=2

    @Test
    fun pinChatRequest_stream_fieldOrder() {
        val req = PinChatRequestProto(userId = "uuid-123", chatId = "chat-456")
        val bytes = PinChatRequestMarshaller().stream(req).readBytes()
        val fields = readFieldNumbers(bytes)
        assertEquals(listOf(1, 2), fields)
    }

    @Test
    fun unpinChatRequest_stream_fieldOrder() {
        val req = UnPinChatRequestProto(userId = "uuid-123", chatId = "chat-456")
        val bytes = UnPinChatRequestMarshaller().stream(req).readBytes()
        val fields = readFieldNumbers(bytes)
        assertEquals(listOf(1, 2), fields)
    }

    // ======= ArchiveChat / UnarchiveChat =======
    // Server proto: user_id=1, chat_id=2

    @Test
    fun archiveChatRequest_stream_fieldOrder() {
        val req = ArchiveChatRequestProto(userId = "uuid-123", chatId = "chat-456")
        val bytes = ArchiveChatRequestMarshaller().stream(req).readBytes()
        val fields = readFieldNumbers(bytes)
        assertEquals(listOf(1, 2), fields)
    }

    @Test
    fun unarchiveChatRequest_stream_fieldOrder() {
        val req = UnarchiveChatRequestProto(userId = "uuid-123", chatId = "chat-456")
        val bytes = UnarchiveChatRequestMarshaller().stream(req).readBytes()
        val fields = readFieldNumbers(bytes)
        assertEquals(listOf(1, 2), fields)
    }

    // ======= PinMessage / UnPinMessage =======
    // Server proto: user_id=1, chat_id=2, message_id=3

    @Test
    fun pinMessageRequest_stream_fieldOrder() {
        val req = PinMessageRequestProto(userId = "uuid-123", chatId = "chat-456", messageId = "msg-789")
        val bytes = PinMessageRequestMarshaller().stream(req).readBytes()
        val fields = readFieldNumbers(bytes)
        assertEquals(listOf(1, 2, 3), fields)
    }

    @Test
    fun unpinMessageRequest_stream_fieldOrder() {
        val req = UnPinMessageRequestProto(userId = "uuid-123", chatId = "chat-456", messageId = "msg-789")
        val bytes = UnPinMessageRequestMarshaller().stream(req).readBytes()
        val fields = readFieldNumbers(bytes)
        assertEquals(listOf(1, 2, 3), fields)
    }

    // ======= Proto defaults =======

    @Test
    fun addContactRequestProto_defaults() {
        val req = AddContactRequestProto()
        assertEquals("", req.username)
        assertEquals("", req.contactUsername)
        assertEquals("", req.userId)
    }

    @Test
    fun pinChatRequestProto_defaults() {
        val req = PinChatRequestProto()
        assertEquals("", req.userId)
        assertEquals("", req.chatId)
    }

    @Test
    fun pinMessageRequestProto_defaults() {
        val req = PinMessageRequestProto()
        assertEquals("", req.userId)
        assertEquals("", req.chatId)
        assertEquals("", req.messageId)
    }

    // ======= Empty field serialization =======

    @Test
    fun addContactRequest_stream_emptyFields_producesEmptyBytes() {
        val req = AddContactRequestProto()
        val bytes = AddContactRequestMarshaller().stream(req).readBytes()
        assertTrue(bytes.isEmpty())
    }

    @Test
    fun pinChatRequest_stream_emptyFields_producesEmptyBytes() {
        val req = PinChatRequestProto()
        val bytes = PinChatRequestMarshaller().stream(req).readBytes()
        assertTrue(bytes.isEmpty())
    }

    @Test
    fun pinMessageRequest_stream_emptyFields_producesEmptyBytes() {
        val req = PinMessageRequestProto()
        val bytes = PinMessageRequestMarshaller().stream(req).readBytes()
        assertTrue(bytes.isEmpty())
    }

    // ======= ArchiveChat/UnarchiveChat response parse =======

    @Test
    fun archiveChatResponse_parseEmpty() {
        val parsed = ArchiveChatResponseMarshaller().parse(java.io.ByteArrayInputStream(byteArrayOf()))
        assertFalse(parsed.success)
    }

    @Test
    fun unarchiveChatResponse_parseEmpty() {
        val parsed = UnarchiveChatResponseMarshaller().parse(java.io.ByteArrayInputStream(byteArrayOf()))
        assertFalse(parsed.success)
    }

    @Test
    fun pinChatResponse_parseEmpty() {
        val parsed = PinChatResponseMarshaller().parse(java.io.ByteArrayInputStream(byteArrayOf()))
        assertFalse(parsed.success)
    }

    @Test
    fun pinMessageResponse_parseEmpty() {
        val parsed = PinMessageResponseMarshaller().parse(java.io.ByteArrayInputStream(byteArrayOf()))
        assertFalse(parsed.success)
    }

    // ======= SearchChats =======
    // Server proto: user_id=1, query=2, limit=3, offset=4

    @Test
    fun searchChatsRequest_stream_fieldOrder() {
        val req = SearchChatsRequestProto(userId = "uuid-123", query = "hello", limit = 50, offset = 10)
        val bytes = SearchChatsRequestMarshaller().stream(req).readBytes()
        val fields = readFieldNumbers(bytes)
        assertEquals(listOf(1, 2, 3, 4), fields)
    }

    // ======= MarkRead =======
    // Server proto: room_id=1, username=2, user_id=3

    @Test
    fun markReadRequest_stream_fieldOrder() {
        val req = MarkReadRequestProto(roomId = "room-456", username = "alice", userId = "uuid-123")
        val bytes = MarkReadRequestMarshaller().stream(req).readBytes()
        val fields = readFieldNumbers(bytes)
        assertEquals(listOf(1, 2, 3), fields)
    }

    // ======= SetMutedChat =======
    // Server proto: user_id=1, room_id=2, muted=3

    @Test
    fun setMutedChatRequest_stream_fieldOrder() {
        val req = SetMutedChatRequestProto(userId = "uuid-123", roomId = "room-456", muted = true)
        val bytes = SetMutedChatRequestMarshaller().stream(req).readBytes()
        val fields = readFieldNumbers(bytes)
        assertEquals(listOf(1, 2, 3), fields)
    }

    // ======= DeleteChat =======
    // Server proto: chat_id=1, requester_username=2, requester_user_id=3

    @Test
    fun deleteChatRequest_stream_fieldOrder() {
        val req = DeleteChatRequestProto(chatId = "chat-456", requesterUsername = "alice", requesterUserId = "uuid-123")
        val bytes = DeleteChatRequestMarshaller().stream(req).readBytes()
        val fields = readFieldNumbers(bytes)
        assertEquals(listOf(1, 2, 3), fields)
    }
}
