package lavender.client.android.data.proto

import com.google.protobuf.Timestamp
import lavender.client.android.data.models.Message
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StickerMessageTest {

    private fun resolveUsername(id: String): String = when (id) {
        "uuid-1" -> "user1"
        else -> "unknown"
    }

    @Test
    fun createMessageV2Proto_stickerMedia() {
        val msg = Message(
            id = "msg-1", user = "user1", text = "", timestamp = 1700000000000L,
            roomId = "room-1", userId = "uuid-1",
            stickerUrl = "http://example.com/sticker.json",
            stickerThumbnailUrl = "http://example.com/thumb.png"
        )
        val proto = ProtoUtils.createMessageV2Proto(msg)
        assertNotNull(proto.media)
        assertEquals("sticker", proto.media?.type)
        assertEquals("http://example.com/sticker.json", proto.media?.url)
        assertEquals("http://example.com/thumb.png", proto.media?.urls?.firstOrNull())
    }

    @Test
    fun createMessageFromV2Proto_stickerMedia() {
        val media = MessageMediaProto(type = "sticker", url = "http://example.com/sticker.json",
            urls = listOf("http://example.com/thumb.png"))
        val ts = Timestamp.newBuilder().setSeconds(1700000000).build()
        val proto = MessageV2Proto(
            id = "msg-1", roomId = "room-1", senderId = "uuid-1",
            text = "", media = media, createdAt = ts
        )
        val msg = ProtoUtils.createMessageFromV2Proto(proto, ::resolveUsername)
        assertEquals("http://example.com/sticker.json", msg.stickerUrl)
        assertEquals("http://example.com/thumb.png", msg.stickerThumbnailUrl)
        assertEquals("", msg.text)
        assertEquals("user1", msg.user)
    }

    @Test
    fun createMessageFromV2Proto_stickerEmptyThumbnail() {
        val media = MessageMediaProto(type = "sticker", url = "http://example.com/sticker.json", urls = emptyList())
        val proto = MessageV2Proto(id = "msg-1", roomId = "room-1", senderId = "uuid-1", media = media)
        val msg = ProtoUtils.createMessageFromV2Proto(proto, ::resolveUsername)
        assertEquals("http://example.com/sticker.json", msg.stickerUrl)
        assertEquals("", msg.stickerThumbnailUrl)
    }

    @Test
    fun message_stickerFields() {
        val msg = Message(
            id = "m1", user = "u1", text = "", timestamp = 1700000000000L,
            stickerUrl = "http://example.com/s.json",
            stickerThumbnailUrl = "http://example.com/t.png"
        )
        assertEquals("http://example.com/s.json", msg.stickerUrl)
        assertEquals("http://example.com/t.png", msg.stickerThumbnailUrl)
        assertEquals("", msg.text)
    }

    @Test
    fun message_stickerAndText() {
        val msg = Message(
            id = "m1", user = "u1", text = "Check this sticker!", timestamp = 1700000000000L,
            stickerUrl = "http://example.com/s.json"
        )
        assertEquals("Check this sticker!", msg.text)
        assertEquals("http://example.com/s.json", msg.stickerUrl)
    }

    @Test
    fun message_stickerEquality() {
        val m1 = Message(id = "m1", user = "u1", text = "", timestamp = 1700000000000L,
            stickerUrl = "http://example.com/s.json")
        val m2 = Message(id = "m1", user = "u1", text = "", timestamp = 1700000000000L,
            stickerUrl = "http://example.com/s.json")
        assertEquals(m1.stickerUrl, m2.stickerUrl)
        assertEquals(m1.stickerThumbnailUrl, m2.stickerThumbnailUrl)
    }
}
