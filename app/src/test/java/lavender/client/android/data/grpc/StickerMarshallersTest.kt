package lavender.client.android.data.grpc

import lavender.client.android.data.models.Sticker
import lavender.client.android.data.models.StickerPack
import lavender.client.android.data.proto.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StickerMarshallersTest {

    // ======= StickerProto =======

    @Test
    fun stickerProto_defaults() {
        val s = StickerProto()
        assertEquals("", s.id)
        assertEquals("", s.packId)
        assertEquals("", s.lottieUrl)
        assertEquals("", s.thumbnailUrl)
        assertEquals("", s.emoji)
        assertEquals(0, s.width)
        assertEquals(0, s.height)
        assertEquals(0L, s.createdAt)
    }

    @Test
    fun stickerProto_withValues() {
        val s = StickerProto(id = "s1", packId = "p1", lottieUrl = "http://example.com/s.json",
            thumbnailUrl = "http://example.com/t.png", emoji = "😊", width = 512, height = 512, createdAt = 1700000000L)
        assertEquals("s1", s.id)
        assertEquals("p1", s.packId)
        assertEquals("http://example.com/s.json", s.lottieUrl)
        assertEquals("http://example.com/t.png", s.thumbnailUrl)
        assertEquals("😊", s.emoji)
        assertEquals(512, s.width)
        assertEquals(512, s.height)
        assertEquals(1700000000L, s.createdAt)
    }

    // ======= StickerPackProto =======

    @Test
    fun stickerPackProto_defaults() {
        val p = StickerPackProto()
        assertEquals("", p.id)
        assertEquals("", p.title)
        assertEquals("", p.name)
        assertEquals("", p.creatorUserId)
        assertEquals("", p.creatorUsername)
        assertTrue(p.stickers.isEmpty())
        assertEquals("", p.coverStickerId)
        assertEquals("draft", p.status)
        assertEquals("", p.rejectionReason)
        assertEquals(0L, p.createdAt)
        assertEquals(0L, p.updatedAt)
        assertFalse(p.isFeatured)
    }

    @Test
    fun stickerPackProto_withStickers() {
        val stickers = listOf(
            StickerProto(id = "s1", packId = "p1", lottieUrl = "http://example.com/s1.json"),
            StickerProto(id = "s2", packId = "p1", lottieUrl = "http://example.com/s2.json")
        )
        val p = StickerPackProto(id = "p1", title = "My Pack", name = "my_pack",
            creatorUserId = "uuid-1", stickers = stickers, status = "approved", isFeatured = true)
        assertEquals(2, p.stickers.size)
        assertEquals("My Pack", p.title)
        assertEquals("approved", p.status)
        assertTrue(p.isFeatured)
    }

    // ======= Request/Response =======

    @Test
    fun createStickerPackRequestProto() {
        val req = CreateStickerPackRequestProto(title = "Test", name = "test_pack")
        assertEquals("Test", req.title)
        assertEquals("test_pack", req.name)
    }

    @Test
    fun createStickerPackResponseProto() {
        val pack = StickerPackProto(id = "p1", title = "Test")
        val resp = CreateStickerPackResponseProto(success = true, pack = pack)
        assertTrue(resp.success)
        assertEquals("p1", resp.pack?.id)
    }

    @Test
    fun addStickerRequestProto() {
        val req = AddStickerRequestProto(packId = "p1", lottieUrl = "http://example.com/s.json",
            thumbnailUrl = "http://example.com/t.png", emoji = "😊", width = 256, height = 256)
        assertEquals("p1", req.packId)
        assertEquals("http://example.com/s.json", req.lottieUrl)
        assertEquals(256, req.width)
    }

    @Test
    fun addStickerResponseProto() {
        val sticker = StickerProto(id = "s1", packId = "p1", lottieUrl = "http://example.com/s.json")
        val resp = AddStickerResponseProto(success = true, sticker = sticker)
        assertTrue(resp.success)
        assertEquals("s1", resp.sticker?.id)
    }

    @Test
    fun removeStickerRequestProto() {
        val req = RemoveStickerRequestProto(packId = "p1", stickerId = "s1")
        assertEquals("p1", req.packId)
        assertEquals("s1", req.stickerId)
    }

    @Test
    fun deleteStickerPackRequestProto() {
        val req = DeleteStickerPackRequestProto(packId = "p1")
        assertEquals("p1", req.packId)
    }

    @Test
    fun submitForApprovalRequestProto() {
        val req = SubmitForApprovalRequestProto(packId = "p1")
        assertEquals("p1", req.packId)
    }

    @Test
    fun approveStickerPackRequestProto() {
        val req = ApproveStickerPackRequestProto(packId = "p1", approved = true, reason = "Good quality")
        assertEquals("p1", req.packId)
        assertTrue(req.approved)
        assertEquals("Good quality", req.reason)
    }

    @Test
    fun searchStickerPacksRequestProto() {
        val req = SearchStickerPacksRequestProto(query = "cats", limit = 10)
        assertEquals("cats", req.query)
        assertEquals(10, req.limit)
    }

    @Test
    fun updateStickerPackRequestProto() {
        val req = UpdateStickerPackRequestProto(packId = "p1", title = "New Title", coverStickerId = "s2")
        assertEquals("p1", req.packId)
        assertEquals("New Title", req.title)
        assertEquals("s2", req.coverStickerId)
    }

    @Test
    fun setFeaturedStickerPackRequestProto() {
        val req = SetFeaturedStickerPackRequestProto(packId = "p1", featured = true)
        assertEquals("p1", req.packId)
        assertTrue(req.featured)
    }

    @Test
    fun getPublicStickerPacksResponseProto() {
        val packs = listOf(StickerPackProto(id = "p1"), StickerPackProto(id = "p2"))
        val resp = GetPublicStickerPacksResponseProto(packs = packs, nextCursor = "cursor123", hasMore = true)
        assertEquals(2, resp.packs.size)
        assertEquals("cursor123", resp.nextCursor)
        assertTrue(resp.hasMore)
    }

    @Test
    fun getPendingStickerPacksResponseProto() {
        val packs = listOf(StickerPackProto(id = "p1", status = "pending"))
        val resp = GetPendingStickerPacksResponseProto(packs = packs, nextCursor = "", hasMore = false)
        assertEquals(1, resp.packs.size)
        assertFalse(resp.hasMore)
    }

    @Test
    fun searchStickerPacksResponseProto() {
        val packs = listOf(StickerPackProto(id = "p1", title = "Cats"))
        val resp = SearchStickerPacksResponseProto(packs = packs)
        assertEquals(1, resp.packs.size)
        assertEquals("Cats", resp.packs[0].title)
    }

    // ======= Marshaller parse (response marshallers are parse-only) =======

    @Test
    fun createStickerPackResponse_marshallerParse() {
        val resp = CreateStickerPackResponseProto(success = true, error = "", pack = StickerPackProto(id = "p1", title = "Test"))
        val marshaller = CreateStickerPackResponseMarshaller()
        val parsed = marshaller.parse(java.io.ByteArrayInputStream(byteArrayOf()))
        assertFalse(parsed.success)
        assertNull(parsed.pack)
    }

    @Test
    fun createStickerPackResponse_marshallerParseEmpty() {
        val marshaller = CreateStickerPackResponseMarshaller()
        val parsed = marshaller.parse(java.io.ByteArrayInputStream(byteArrayOf()))
        assertFalse(parsed.success)
        assertNull(parsed.pack)
        assertEquals("", parsed.error)
    }

    @Test
    fun addStickerResponse_marshallerParseEmpty() {
        val marshaller = AddStickerResponseMarshaller()
        val parsed = marshaller.parse(java.io.ByteArrayInputStream(byteArrayOf()))
        assertFalse(parsed.success)
        assertNull(parsed.sticker)
    }

    @Test
    fun removeStickerResponse_marshallerParseEmpty() {
        val marshaller = RemoveStickerResponseMarshaller()
        val parsed = marshaller.parse(java.io.ByteArrayInputStream(byteArrayOf()))
        assertFalse(parsed.success)
    }

    @Test
    fun deleteStickerPackResponse_marshallerParseEmpty() {
        val marshaller = DeleteStickerPackResponseMarshaller()
        val parsed = marshaller.parse(java.io.ByteArrayInputStream(byteArrayOf()))
        assertFalse(parsed.success)
    }

    @Test
    fun submitForApprovalResponse_marshallerParseEmpty() {
        val marshaller = SubmitForApprovalResponseMarshaller()
        val parsed = marshaller.parse(java.io.ByteArrayInputStream(byteArrayOf()))
        assertFalse(parsed.success)
        assertEquals("", parsed.error)
    }

    @Test
    fun approveStickerPackResponse_marshallerParseEmpty() {
        val marshaller = ApproveStickerPackResponseMarshaller()
        val parsed = marshaller.parse(java.io.ByteArrayInputStream(byteArrayOf()))
        assertFalse(parsed.success)
    }

    @Test
    fun searchStickerPacksResponse_marshallerParseEmpty() {
        val marshaller = SearchStickerPacksResponseMarshaller()
        val parsed = marshaller.parse(java.io.ByteArrayInputStream(byteArrayOf()))
        assertTrue(parsed.packs.isEmpty())
    }

    @Test
    fun getUserStickerPacksResponse_marshallerParseEmpty() {
        val marshaller = GetUserStickerPacksResponseMarshaller()
        val parsed = marshaller.parse(java.io.ByteArrayInputStream(byteArrayOf()))
        assertTrue(parsed.packs.isEmpty())
    }

    @Test
    fun getPublicStickerPacksResponse_marshallerParseEmpty() {
        val marshaller = GetPublicStickerPacksResponseMarshaller()
        val parsed = marshaller.parse(java.io.ByteArrayInputStream(byteArrayOf()))
        assertTrue(parsed.packs.isEmpty())
        assertFalse(parsed.hasMore)
    }

    @Test
    fun getStickerPackResponse_marshallerParseEmpty() {
        val marshaller = GetStickerPackResponseMarshaller()
        val parsed = marshaller.parse(java.io.ByteArrayInputStream(byteArrayOf()))
        assertNull(parsed.pack)
    }

    @Test
    fun updateStickerPackResponse_marshallerParseEmpty() {
        val marshaller = UpdateStickerPackResponseMarshaller()
        val parsed = marshaller.parse(java.io.ByteArrayInputStream(byteArrayOf()))
        assertFalse(parsed.success)
    }

    @Test
    fun setFeaturedStickerPackResponse_marshallerParseEmpty() {
        val marshaller = SetFeaturedStickerPackResponseMarshaller()
        val parsed = marshaller.parse(java.io.ByteArrayInputStream(byteArrayOf()))
        assertFalse(parsed.success)
    }

    // ======= Domain models =======

    @Test
    fun stickerModel() {
        val s = Sticker(id = "s1", packId = "p1", lottieUrl = "http://example.com/s.json",
            thumbnailUrl = "http://example.com/t.png", emoji = "😊", width = 512, height = 512)
        assertEquals("s1", s.id)
        assertEquals("p1", s.packId)
        assertEquals("http://example.com/s.json", s.lottieUrl)
        assertEquals("http://example.com/t.png", s.thumbnailUrl)
        assertEquals("😊", s.emoji)
        assertEquals(512, s.width)
        assertEquals(512, s.height)
    }

    @Test
    fun stickerPackModel() {
        val stickers = listOf(Sticker(id = "s1", packId = "p1"))
        val p = StickerPack(id = "p1", title = "Pack", name = "pack",
            creatorUsername = "user1", stickers = stickers, status = "approved")
        assertEquals("p1", p.id)
        assertEquals(1, p.stickers.size)
        assertEquals("approved", p.status)
        assertFalse(p.isFeatured)
    }

    @Test
    fun stickerPackModel_draft() {
        val p = StickerPack(id = "p1", status = "draft")
        assertEquals("draft", p.status)
    }

    @Test
    fun stickerPackModel_pending() {
        val p = StickerPack(id = "p1", status = "pending", rejectionReason = "")
        assertEquals("pending", p.status)
    }

    @Test
    fun stickerPackModel_rejected() {
        val p = StickerPack(id = "p1", status = "rejected", rejectionReason = "Low quality")
        assertEquals("rejected", p.status)
        assertEquals("Low quality", p.rejectionReason)
    }
}
