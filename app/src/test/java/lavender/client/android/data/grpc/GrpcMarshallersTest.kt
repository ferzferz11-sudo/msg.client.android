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

    // ======= Auth V2 =======
    // SignInRequestV2: username=1, password=2, device=3, client_version=4

    @Test
    fun signInRequestV2_stream_fieldOrder() {
        val req = SignInRequestV2Proto(username = "alice", password = "secret", deviceId = "dev-1", deviceName = "Pixel", clientVersion = "1.4.0.4")
        val bytes = SignInRequestV2Marshaller().stream(req).readBytes()
        val fields = readFieldNumbers(bytes)
        assertEquals(listOf(1, 2, 3, 4), fields)
    }

    @Test
    fun signInRequestV2_stream_nonEmpty() {
        val req = SignInRequestV2Proto(username = "alice", password = "secret")
        val bytes = SignInRequestV2Marshaller().stream(req).readBytes()
        assertTrue(bytes.isNotEmpty())
    }

    @Test
    fun signInRequestV2_stream_emptyFields_producesEmptyBytes() {
        val req = SignInRequestV2Proto()
        val bytes = SignInRequestV2Marshaller().stream(req).readBytes()
        assertTrue(bytes.isEmpty())
    }

    // SignUpRequestV2: username=1, password=2, email=3, device=4, client_version=5

    @Test
    fun signUpRequestV2_stream_fieldOrder() {
        val req = SignUpRequestV2Proto(username = "alice", password = "secret", email = "a@b.com", deviceId = "dev-1", deviceName = "Pixel", clientVersion = "1.4.0.4")
        val bytes = SignUpRequestV2Marshaller().stream(req).readBytes()
        val fields = readFieldNumbers(bytes)
        assertEquals(listOf(1, 2, 3, 4, 5), fields)
    }

    @Test
    fun signUpRequestV2_stream_emptyFields_producesEmptyBytes() {
        val req = SignUpRequestV2Proto()
        val bytes = SignUpRequestV2Marshaller().stream(req).readBytes()
        assertTrue(bytes.isEmpty())
    }

    // RefreshTokenRequest: refresh_token=1

    @Test
    fun refreshTokenRequest_stream_fieldOrder() {
        val req = RefreshTokenRequestProto(refreshToken = "refresh-abc")
        val bytes = RefreshTokenRequestMarshaller().stream(req).readBytes()
        val fields = readFieldNumbers(bytes)
        assertEquals(listOf(1), fields)
    }

    @Test
    fun refreshTokenRequest_stream_emptyFields_producesEmptyBytes() {
        val req = RefreshTokenRequestProto()
        val bytes = RefreshTokenRequestMarshaller().stream(req).readBytes()
        assertTrue(bytes.isEmpty())
    }

    // SignOutRequest: refresh_token=1, all_devices=2

    @Test
    fun signOutRequest_stream_fieldOrder() {
        val req = SignOutRequestProto(refreshToken = "refresh-abc", allDevices = true)
        val bytes = SignOutRequestMarshaller().stream(req).readBytes()
        val fields = readFieldNumbers(bytes)
        assertEquals(listOf(1, 2), fields)
    }

    @Test
    fun signOutRequest_stream_emptyFields_producesEmptyBytes() {
        val req = SignOutRequestProto()
        val bytes = SignOutRequestMarshaller().stream(req).readBytes()
        assertTrue(bytes.isEmpty())
    }

    // AuthResponseV2: parse empty

    @Test
    fun authResponseV2_parseEmpty() {
        val parsed = AuthResponseV2Marshaller().parse(java.io.ByteArrayInputStream(byteArrayOf()))
        assertFalse(parsed.success)
        assertEquals("", parsed.message)
        assertEquals("", parsed.accessToken)
        assertEquals("", parsed.refreshToken)
        assertEquals(0L, parsed.accessExpiresAt)
        assertEquals(0L, parsed.refreshExpiresAt)
        assertEquals("", parsed.userId)
        assertEquals("", parsed.username)
    }

    // RefreshTokenResponse: parse empty

    @Test
    fun refreshTokenResponse_parseEmpty() {
        val parsed = RefreshTokenResponseMarshaller().parse(java.io.ByteArrayInputStream(byteArrayOf()))
        assertEquals("", parsed.accessToken)
        assertEquals("", parsed.refreshToken)
        assertEquals(0L, parsed.accessExpiresAt)
        assertEquals(0L, parsed.refreshExpiresAt)
    }

    // SimpleAuthResponse: parse empty

    @Test
    fun simpleAuthResponse_parseEmpty() {
        val parsed = SimpleAuthResponseMarshaller().parse(java.io.ByteArrayInputStream(byteArrayOf()))
        assertFalse(parsed.success)
        assertEquals("", parsed.message)
    }

    // Auth V2 Proto defaults

    @Test
    fun signInRequestV2Proto_defaults() {
        val req = SignInRequestV2Proto()
        assertEquals("", req.username)
        assertEquals("", req.password)
        assertEquals("", req.deviceId)
        assertEquals("", req.deviceName)
        assertEquals("android", req.deviceType)
        assertEquals("", req.clientVersion)
    }

    @Test
    fun authResponseV2Proto_defaults() {
        val resp = AuthResponseV2Proto()
        assertFalse(resp.success)
        assertEquals("", resp.accessToken)
        assertEquals("", resp.refreshToken)
        assertEquals("", resp.userId)
        assertEquals("", resp.username)
    }

    // ======= Saved Messages =======
    // AddFavoriteRequest: user_id=1, message_id=2

    @Test
    fun addSavedMessageRequest_stream_fieldOrder() {
        val req = AddSavedMessageRequestProto(userId = "uuid-123", messageId = "msg-789")
        val bytes = AddSavedMessageRequestMarshaller().stream(req).readBytes()
        val fields = readFieldNumbers(bytes)
        assertEquals(listOf(1, 2), fields)
    }

    @Test
    fun addSavedMessageRequest_stream_nonEmpty() {
        val req = AddSavedMessageRequestProto(userId = "uuid-123", messageId = "msg-789")
        val bytes = AddSavedMessageRequestMarshaller().stream(req).readBytes()
        assertTrue(bytes.isNotEmpty())
    }

    @Test
    fun addSavedMessageRequest_stream_emptyFields_producesEmptyBytes() {
        val req = AddSavedMessageRequestProto()
        val bytes = AddSavedMessageRequestMarshaller().stream(req).readBytes()
        assertTrue(bytes.isEmpty())
    }

    // RemoveFavoriteRequest: user_id=1, message_id=2

    @Test
    fun removeSavedMessageRequest_stream_fieldOrder() {
        val req = RemoveSavedMessageRequestProto(userId = "uuid-123", messageId = "msg-789")
        val bytes = RemoveSavedMessageRequestMarshaller().stream(req).readBytes()
        val fields = readFieldNumbers(bytes)
        assertEquals(listOf(1, 2), fields)
    }

    @Test
    fun removeSavedMessageRequest_stream_emptyFields_producesEmptyBytes() {
        val req = RemoveSavedMessageRequestProto()
        val bytes = RemoveSavedMessageRequestMarshaller().stream(req).readBytes()
        assertTrue(bytes.isEmpty())
    }

    // GetFavoritesRequest: user_id=1

    @Test
    fun getSavedMessagesRequest_stream_fieldOrder() {
        val req = GetSavedMessagesRequestProto(userId = "uuid-123")
        val bytes = GetSavedMessagesRequestMarshaller().stream(req).readBytes()
        val fields = readFieldNumbers(bytes)
        assertEquals(listOf(1), fields)
    }

    @Test
    fun getSavedMessagesRequest_stream_emptyFields_producesEmptyBytes() {
        val req = GetSavedMessagesRequestProto()
        val bytes = GetSavedMessagesRequestMarshaller().stream(req).readBytes()
        assertTrue(bytes.isEmpty())
    }

    // Favorites response parse empty

    @Test
    fun addSavedMessageResponse_parseEmpty() {
        val parsed = AddSavedMessageResponseMarshaller().parse(java.io.ByteArrayInputStream(byteArrayOf()))
        assertFalse(parsed.success)
        assertEquals("", parsed.message)
    }

    @Test
    fun removeSavedMessageResponse_parseEmpty() {
        val parsed = RemoveSavedMessageResponseMarshaller().parse(java.io.ByteArrayInputStream(byteArrayOf()))
        assertFalse(parsed.success)
    }

    @Test
    fun getSavedMessagesResponse_parseEmpty() {
        val parsed = GetSavedMessagesResponseMarshaller().parse(java.io.ByteArrayInputStream(byteArrayOf()))
        assertTrue(parsed.messages.isEmpty())
    }

    // Favorites Proto defaults

    @Test
    fun addSavedMessageRequestProto_defaults() {
        val req = AddSavedMessageRequestProto()
        assertEquals("", req.userId)
        assertEquals("", req.messageId)
    }

    @Test
    fun removeSavedMessageRequestProto_defaults() {
        val req = RemoveSavedMessageRequestProto()
        assertEquals("", req.userId)
        assertEquals("", req.messageId)
    }

    @Test
    fun getSavedMessagesRequestProto_defaults() {
        val req = GetSavedMessagesRequestProto()
        assertEquals("", req.userId)
    }

    // ======= Send Message V2 (saved_messages roomId) =======

    @Test
    fun sendMessageV2Request_savedMessagesRoomId_fieldOrder() {
        val req = SendMessageV2RequestProto(roomId = "saved_messages_testuser", text = "Hello saved")
        val bytes = SendMessageV2RequestMarshaller().stream(req).readBytes()
        val fields = readFieldNumbers(bytes)
        // roomId=1, text=2
        assertEquals(listOf(1, 2), fields)
    }

    @Test
    fun sendMessageV2Request_savedMessagesRoomId_nonEmpty() {
        val req = SendMessageV2RequestProto(roomId = "saved_messages_testuser", text = "Hello saved")
        val bytes = SendMessageV2RequestMarshaller().stream(req).readBytes()
        assertTrue(bytes.isNotEmpty())
    }

    @Test
    fun sendMessageV2Request_savedMessagesRoomId_preservesRoomId() {
        val roomId = "saved_messages_testuser"
        val req = SendMessageV2RequestProto(roomId = roomId, text = "test")
        assertEquals(roomId, req.roomId)
    }

    @Test
    fun sendMessageV2Request_savedMessages_fullSerialization_roundtrip() {
        // Simulate what domainToSendRequest produces for a saved messages text message
        val req = SendMessageV2RequestProto(
            roomId = "saved_messages_pavel",
            text = "Hello from saved messages"
        )
        val bytes = SendMessageV2RequestMarshaller().stream(req).readBytes()
        assertTrue("Serialized bytes should not be empty", bytes.isNotEmpty())

        // Verify field numbers: roomId=1, text=2
        val fields = readFieldNumbers(bytes)
        assertEquals(listOf(1, 2), fields)

        // Verify roomId string is present in serialized bytes
        val serialized = String(bytes, Charsets.UTF_8)
        assertTrue("RoomId should be in serialized bytes", serialized.contains("saved_messages_pavel"))
        assertTrue("Text should be in serialized bytes", serialized.contains("Hello from saved messages"))
    }

    @Test
    fun sendMessageV2Request_savedMessages_withReply() {
        val req = SendMessageV2RequestProto(
            roomId = "saved_messages_pavel",
            text = "Reply text",
            replyToId = "msg-123"
        )
        val bytes = SendMessageV2RequestMarshaller().stream(req).readBytes()
        val fields = readFieldNumbers(bytes)
        // roomId=1, text=2, replyToId=4
        assertEquals(listOf(1, 2, 4), fields)
    }

    // ======= Profile V2 =======
    // UpdateProfileV2Request: username=1, bio=2, status=3, locale=4

    @Test
    fun updateProfileV2Request_stream_fieldOrder() {
        val req = UpdateProfileV2RequestProto(username = "alice", bio = "Hello", status = "Online", locale = "ru")
        val bytes = UpdateProfileV2RequestMarshaller().stream(req).readBytes()
        val fields = readFieldNumbers(bytes)
        assertEquals(listOf(1, 2, 3, 4), fields)
    }

    @Test
    fun updateProfileV2Request_stream_nonEmpty() {
        val req = UpdateProfileV2RequestProto(bio = "Hello")
        val bytes = UpdateProfileV2RequestMarshaller().stream(req).readBytes()
        assertTrue(bytes.isNotEmpty())
    }

    @Test
    fun updateProfileV2Request_stream_emptyFields_producesEmptyBytes() {
        val req = UpdateProfileV2RequestProto()
        val bytes = UpdateProfileV2RequestMarshaller().stream(req).readBytes()
        assertTrue(bytes.isEmpty())
    }

    // UpdateAvatarV2Request: avatar_url=1, full_avatar_url=2

    @Test
    fun updateAvatarV2Request_stream_fieldOrder() {
        val req = UpdateAvatarV2RequestProto(avatarUrl = "https://cdn/avatar.jpg", fullAvatarUrl = "https://cdn/avatar_full.jpg")
        val bytes = UpdateAvatarV2RequestMarshaller().stream(req).readBytes()
        val fields = readFieldNumbers(bytes)
        assertEquals(listOf(1, 2), fields)
    }

    @Test
    fun updateAvatarV2Request_stream_emptyFields_producesEmptyBytes() {
        val req = UpdateAvatarV2RequestProto()
        val bytes = UpdateAvatarV2RequestMarshaller().stream(req).readBytes()
        assertTrue(bytes.isEmpty())
    }

    // DeleteProfileV2Request: password=1

    @Test
    fun deleteProfileV2Request_stream_fieldOrder() {
        val req = DeleteProfileV2RequestProto(password = "secret123")
        val bytes = DeleteProfileV2RequestMarshaller().stream(req).readBytes()
        val fields = readFieldNumbers(bytes)
        assertEquals(listOf(1), fields)
    }

    @Test
    fun deleteProfileV2Request_stream_emptyFields_producesEmptyBytes() {
        val req = DeleteProfileV2RequestProto()
        val bytes = DeleteProfileV2RequestMarshaller().stream(req).readBytes()
        assertTrue(bytes.isEmpty())
    }

    // Profile V2 response parse empty

    @Test
    fun getProfileResponse_parseEmpty() {
        val parsed = GetProfileResponseMarshaller().parse(java.io.ByteArrayInputStream(byteArrayOf()))
        assertEquals("", parsed.userId)
        assertEquals("", parsed.username)
        assertEquals("", parsed.email)
        assertEquals("", parsed.avatarUrl)
        assertEquals("en", parsed.locale)
        assertFalse(parsed.isSuperAdmin)
    }

    @Test
    fun updateProfileV2Response_parseEmpty() {
        val parsed = UpdateProfileV2ResponseMarshaller().parse(java.io.ByteArrayInputStream(byteArrayOf()))
        assertFalse(parsed.success)
        assertEquals("", parsed.message)
    }

    @Test
    fun updateAvatarV2Response_parseEmpty() {
        val parsed = UpdateAvatarV2ResponseMarshaller().parse(java.io.ByteArrayInputStream(byteArrayOf()))
        assertFalse(parsed.success)
        assertEquals("", parsed.message)
        assertEquals("", parsed.avatarUrl)
        assertEquals("", parsed.fullAvatarUrl)
    }

    @Test
    fun deleteProfileV2Response_parseEmpty() {
        val parsed = DeleteProfileV2ResponseMarshaller().parse(java.io.ByteArrayInputStream(byteArrayOf()))
        assertFalse(parsed.success)
        assertEquals("", parsed.message)
    }

    // Profile V2 Proto defaults

    @Test
    fun getProfileResponseProto_defaults() {
        val resp = GetProfileResponseProto()
        assertEquals("", resp.userId)
        assertEquals("", resp.username)
        assertEquals("", resp.email)
        assertEquals("en", resp.locale)
        assertFalse(resp.isSuperAdmin)
        assertEquals(0, resp.positionLevel)
    }

    @Test
    fun updateProfileV2RequestProto_defaults() {
        val req = UpdateProfileV2RequestProto()
        assertEquals("", req.username)
        assertEquals("", req.bio)
        assertEquals("", req.status)
        assertEquals("", req.locale)
    }

    @Test
    fun deleteProfileV2RequestProto_defaults() {
        val req = DeleteProfileV2RequestProto()
        assertEquals("", req.password)
    }

    // ======= GetUserSettings =======
    // GetUserSettingsResponse: locale=1, theme_id=2, push_enabled=3, custom=4

    @Test
    fun getUserSettingsResponse_parseEmpty() {
        val parsed = GetUserSettingsResponseMarshaller().parse(java.io.ByteArrayInputStream(byteArrayOf()))
        assertEquals("en", parsed.locale)
        assertEquals("", parsed.themeId)
        assertTrue(parsed.pushEnabled)
        assertTrue(parsed.custom.isEmpty())
    }

    // UpdateUserSettingsRequest: locale=1, theme_id=2, push_enabled=3

    @Test
    fun updateUserSettingsRequest_stream_fieldOrder() {
        val req = UpdateUserSettingsRequestProto(locale = "ru", themeId = "dark-1", pushEnabled = false)
        val bytes = UpdateUserSettingsRequestMarshaller().stream(req).readBytes()
        val fields = readFieldNumbers(bytes)
        assertEquals(listOf(1, 2, 3), fields)
    }

    @Test
    fun updateUserSettingsRequest_stream_emptyFields_producesEmptyBytes() {
        val req = UpdateUserSettingsRequestProto()
        val bytes = UpdateUserSettingsRequestMarshaller().stream(req).readBytes()
        assertTrue(bytes.isEmpty())
    }

    @Test
    fun updateUserSettingsRequest_stream_withCustomMap_fieldOrder() {
        val req = UpdateUserSettingsRequestProto(custom = mapOf("chat_list_mode" to "fast"))
        val bytes = UpdateUserSettingsRequestMarshaller().stream(req).readBytes()
        val fields = readFieldNumbers(bytes)
        // field 4 (custom map entry)
        assertTrue("Should contain field 4", 4 in fields)
    }

    @Test
    fun updateUserSettingsRequest_stream_withCustomMap_containsKeyValue() {
        val req = UpdateUserSettingsRequestProto(custom = mapOf("chat_list_mode" to "fast"))
        val bytes = UpdateUserSettingsRequestMarshaller().stream(req).readBytes()
        assertTrue("Serialized bytes should not be empty", bytes.isNotEmpty())
        val serialized = String(bytes, Charsets.UTF_8)
        assertTrue("Should contain key", serialized.contains("chat_list_mode"))
        assertTrue("Should contain value", serialized.contains("fast"))
    }

    @Test
    fun updateUserSettingsResponse_parseEmpty() {
        val parsed = UpdateUserSettingsResponseMarshaller().parse(java.io.ByteArrayInputStream(byteArrayOf()))
        assertFalse(parsed.success)
        assertEquals("", parsed.message)
    }
}
