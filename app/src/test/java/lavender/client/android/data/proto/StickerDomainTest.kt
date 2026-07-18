package lavender.client.android.data.proto

import lavender.client.android.data.models.Sticker
import lavender.client.android.data.models.StickerPack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StickerDomainTest {

    // ======= Sticker ======

    @Test
    fun sticker_defaults() {
        val s = Sticker()
        assertEquals("", s.id)
        assertEquals("", s.packId)
        assertEquals("", s.lottieUrl)
        assertEquals("", s.thumbnailUrl)
        assertEquals("", s.emoji)
        assertEquals(512, s.width)
        assertEquals(512, s.height)
    }

    @Test
    fun sticker_equality() {
        val s1 = Sticker(id = "s1", packId = "p1", lottieUrl = "http://example.com/s.json")
        val s2 = Sticker(id = "s1", packId = "p1", lottieUrl = "http://example.com/s.json")
        assertEquals(s1, s2)
    }

    @Test
    fun sticker_inequality() {
        val s1 = Sticker(id = "s1", lottieUrl = "http://example.com/s1.json")
        val s2 = Sticker(id = "s2", lottieUrl = "http://example.com/s2.json")
        assertNotEquals(s1, s2)
    }

    @Test
    fun sticker_copy() {
        val s1 = Sticker(id = "s1", packId = "p1", lottieUrl = "http://example.com/s.json")
        val s2 = s1.copy(id = "s2")
        assertEquals("s2", s2.id)
        assertEquals("p1", s2.packId)
    }

    // ======= StickerPack ======

    @Test
    fun stickerPack_defaults() {
        val p = StickerPack()
        assertEquals("", p.id)
        assertEquals("", p.title)
        assertEquals("", p.name)
        assertEquals("", p.creatorUsername)
        assertTrue(p.stickers.isEmpty())
        assertEquals("", p.coverStickerId)
        assertEquals("draft", p.status)
        assertEquals("", p.rejectionReason)
        assertFalse(p.isFeatured)
    }

    @Test
    fun stickerPack_withStickers() {
        val stickers = listOf(
            Sticker(id = "s1", packId = "p1"),
            Sticker(id = "s2", packId = "p1"),
            Sticker(id = "s3", packId = "p1")
        )
        val p = StickerPack(id = "p1", title = "My Pack", stickers = stickers)
        assertEquals(3, p.stickers.size)
    }

    @Test
    fun stickerPack_coverSticker() {
        val stickers = listOf(
            Sticker(id = "s1", packId = "p1"),
            Sticker(id = "s2", packId = "p1")
        )
        val p = StickerPack(id = "p1", stickers = stickers, coverStickerId = "s2")
        assertEquals("s2", p.coverStickerId)
    }

    @Test
    fun stickerPack_featured() {
        val p = StickerPack(id = "p1", isFeatured = true)
        assertTrue(p.isFeatured)
    }

    @Test
    fun stickerPack_notFeatured() {
        val p = StickerPack(id = "p1", isFeatured = false)
        assertFalse(p.isFeatured)
    }

    @Test
    fun stickerPack_statusTransitions() {
        val draft = StickerPack(id = "p1", status = "draft")
        val pending = StickerPack(id = "p1", status = "pending")
        val approved = StickerPack(id = "p1", status = "approved")
        val rejected = StickerPack(id = "p1", status = "rejected", rejectionReason = "Too simple")

        assertEquals("draft", draft.status)
        assertEquals("pending", pending.status)
        assertEquals("approved", approved.status)
        assertEquals("rejected", rejected.status)
        assertEquals("Too simple", rejected.rejectionReason)
    }

    // ======= Proto to Domain ======

    @Test
    fun protoToSticker() {
        val proto = StickerProto(id = "s1", packId = "p1", lottieUrl = "http://example.com/s.json",
            thumbnailUrl = "http://example.com/t.png", emoji = "😊", width = 256, height = 256)
        val domain = Sticker(proto.id, proto.packId, proto.lottieUrl, proto.thumbnailUrl, proto.emoji, proto.width, proto.height)
        assertEquals(proto.id, domain.id)
        assertEquals(proto.lottieUrl, domain.lottieUrl)
    }

    @Test
    fun protoToStickerPack() {
        val stickers = listOf(StickerProto(id = "s1"))
        val proto = StickerPackProto(id = "p1", title = "Test", stickers = stickers, status = "approved")
        val domain = StickerPack(proto.id, proto.title, proto.name, proto.creatorUsername,
            proto.stickers.map { Sticker(it.id, it.packId, it.lottieUrl, it.thumbnailUrl, it.emoji, it.width, it.height) },
            proto.coverStickerId, proto.status, proto.rejectionReason, proto.isFeatured)
        assertEquals("p1", domain.id)
        assertEquals("Test", domain.title)
        assertEquals("approved", domain.status)
        assertEquals(1, domain.stickers.size)
    }
}
