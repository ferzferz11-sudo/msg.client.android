package lavender.client.android.ui.adapter

import lavender.client.android.data.models.Message
import lavender.client.android.data.models.Reaction
import lavender.client.android.data.models.Sticker
import lavender.client.android.ui.sticker.StickerGridAdapter
import lavender.client.android.ui.adapter.MessageAdapter.MessageDiffCallback
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiffCallbackTest {

    // ======= MessageDiffCallback ======

    private val msgDiff = MessageDiffCallback()

    @Test
    fun messageDiff_sameId_areItemsSame() {
        val m1 = Message(id = "msg1", user = "alice", text = "hello", timestamp = 100)
        val m2 = Message(id = "msg1", user = "bob", text = "world", timestamp = 200)
        assertTrue(msgDiff.areItemsTheSame(m1, m2))
    }

    @Test
    fun messageDiff_differentId_areItemsDifferent() {
        val m1 = Message(id = "msg1", user = "alice", text = "hello", timestamp = 100)
        val m2 = Message(id = "msg2", user = "alice", text = "hello", timestamp = 100)
        assertFalse(msgDiff.areItemsTheSame(m1, m2))
    }

    @Test
    fun messageDiff_sameContent_areContentsSame() {
        val m1 = Message(id = "msg1", user = "alice", text = "hello", timestamp = 100)
        val m2 = Message(id = "msg1", user = "alice", text = "hello", timestamp = 100)
        assertTrue(msgDiff.areContentsTheSame(m1, m2))
    }

    @Test
    fun messageDiff_differentText_areContentsDifferent() {
        val m1 = Message(id = "msg1", user = "alice", text = "hello", timestamp = 100)
        val m2 = Message(id = "msg1", user = "alice", text = "edited", timestamp = 100)
        assertFalse(msgDiff.areContentsTheSame(m1, m2))
    }

    @Test
    fun messageDiff_differentTimestamp_areContentsDifferent() {
        val m1 = Message(id = "msg1", user = "alice", text = "hello", timestamp = 100)
        val m2 = Message(id = "msg1", user = "alice", text = "hello", timestamp = 200)
        assertFalse(msgDiff.areContentsTheSame(m1, m2))
    }

    @Test
    fun messageDiff_differentReactions_areContentsDifferent() {
        val m1 = Message(id = "msg1", user = "alice", text = "hello", timestamp = 100, reactions = emptyList())
        val m2 = Message(id = "msg1", user = "alice", text = "hello", timestamp = 100, reactions = listOf(Reaction("bob", "\uD83D\uDC4D")))
        assertFalse(msgDiff.areContentsTheSame(m1, m2))
    }

    @Test
    fun messageDiff_readStatusChange_areContentsDifferent() {
        val m1 = Message(id = "msg1", user = "alice", text = "hello", timestamp = 100, isSent = false)
        val m2 = Message(id = "msg1", user = "alice", text = "hello", timestamp = 100, isSent = true)
        assertFalse(msgDiff.areContentsTheSame(m1, m2))
    }

    @Test
    fun messageDiff_stickerMessage_sameContent() {
        val m1 = Message(id = "msg1", user = "alice", text = "", timestamp = 100, stickerUrl = "http://example.com/s.json")
        val m2 = Message(id = "msg1", user = "alice", text = "", timestamp = 100, stickerUrl = "http://example.com/s.json")
        assertTrue(msgDiff.areContentsTheSame(m1, m2))
    }

    @Test
    fun messageDiff_stickerMessage_differentUrl() {
        val m1 = Message(id = "msg1", user = "alice", text = "", timestamp = 100, stickerUrl = "http://example.com/s1.json")
        val m2 = Message(id = "msg1", user = "alice", text = "", timestamp = 100, stickerUrl = "http://example.com/s2.json")
        assertFalse(msgDiff.areContentsTheSame(m1, m2))
    }

    // ======= StickerDiffCallback ======

    private val stickerDiff = StickerGridAdapter.StickerDiffCallback()

    @Test
    fun stickerDiff_sameId_areItemsSame() {
        val s1 = Sticker(id = "s1", packId = "p1", lottieUrl = "http://a.json")
        val s2 = Sticker(id = "s1", packId = "p2", lottieUrl = "http://b.json")
        assertTrue(stickerDiff.areItemsTheSame(s1, s2))
    }

    @Test
    fun stickerDiff_differentId_areItemsDifferent() {
        val s1 = Sticker(id = "s1", packId = "p1", lottieUrl = "http://a.json")
        val s2 = Sticker(id = "s2", packId = "p1", lottieUrl = "http://a.json")
        assertFalse(stickerDiff.areItemsTheSame(s1, s2))
    }

    @Test
    fun stickerDiff_sameContent_areContentsSame() {
        val s1 = Sticker(id = "s1", packId = "p1", lottieUrl = "http://a.json", emoji = "\uD83C\uDFB5")
        val s2 = Sticker(id = "s1", packId = "p1", lottieUrl = "http://a.json", emoji = "\uD83C\uDFB5")
        assertTrue(stickerDiff.areContentsTheSame(s1, s2))
    }

    @Test
    fun stickerDiff_differentUrl_areContentsDifferent() {
        val s1 = Sticker(id = "s1", packId = "p1", lottieUrl = "http://a.json")
        val s2 = Sticker(id = "s1", packId = "p1", lottieUrl = "http://b.json")
        assertFalse(stickerDiff.areContentsTheSame(s1, s2))
    }

    @Test
    fun stickerDiff_differentEmoji_areContentsDifferent() {
        val s1 = Sticker(id = "s1", packId = "p1", lottieUrl = "http://a.json", emoji = "\uD83C\uDFB5")
        val s2 = Sticker(id = "s1", packId = "p1", lottieUrl = "http://a.json", emoji = "\uD83C\uDFB6")
        assertFalse(stickerDiff.areContentsTheSame(s1, s2))
    }
}
