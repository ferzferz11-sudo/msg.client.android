package lavender.client.android.data.proto

// ===== Sticker Models =====

data class StickerProto(
    val id: String = "",
    val packId: String = "",
    val lottieUrl: String = "",
    val thumbnailUrl: String = "",
    val emoji: String = "",
    val width: Int = 0,
    val height: Int = 0,
    val createdAt: Long = 0
)

data class StickerPackProto(
    val id: String = "",
    val title: String = "",
    val name: String = "",
    val creatorUserId: String = "",
    val creatorUsername: String = "",
    val stickers: List<StickerProto> = emptyList(),
    val coverStickerId: String = "",
    val status: String = "draft",
    val rejectionReason: String = "",
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
    val isFeatured: Boolean = false
)

// ===== Sticker CRUD =====

data class CreateStickerPackRequestProto(
    val title: String = "",
    val name: String = ""
)

data class CreateStickerPackResponseProto(
    val success: Boolean = false,
    val error: String = "",
    val pack: StickerPackProto? = null
)

data class AddStickerRequestProto(
    val packId: String = "",
    val lottieUrl: String = "",
    val thumbnailUrl: String = "",
    val emoji: String = "",
    val width: Int = 0,
    val height: Int = 0
)

data class AddStickerResponseProto(
    val success: Boolean = false,
    val error: String = "",
    val sticker: StickerProto? = null
)

data class RemoveStickerRequestProto(
    val packId: String = "",
    val stickerId: String = ""
)

data class RemoveStickerResponseProto(
    val success: Boolean = false
)

data class DeleteStickerPackRequestProto(
    val packId: String = ""
)

data class DeleteStickerPackResponseProto(
    val success: Boolean = false
)

class GetUserStickerPacksRequestProto

data class GetUserStickerPacksResponseProto(
    val packs: List<StickerPackProto> = emptyList()
)

data class GetPublicStickerPacksRequestProto(
    val cursor: String = "",
    val limit: Int = 30
)

data class GetPublicStickerPacksResponseProto(
    val packs: List<StickerPackProto> = emptyList(),
    val nextCursor: String = "",
    val hasMore: Boolean = false
)

data class GetStickerPackRequestProto(
    val packId: String = ""
)

data class GetStickerPackResponseProto(
    val pack: StickerPackProto? = null
)

data class SubmitForApprovalRequestProto(
    val packId: String = ""
)

data class SubmitForApprovalResponseProto(
    val success: Boolean = false,
    val error: String = ""
)

data class ApproveStickerPackRequestProto(
    val packId: String = "",
    val approved: Boolean = false,
    val reason: String = ""
)

data class ApproveStickerPackResponseProto(
    val success: Boolean = false
)

data class GetPendingStickerPacksRequestProto(
    val cursor: String = "",
    val limit: Int = 30
)

data class GetPendingStickerPacksResponseProto(
    val packs: List<StickerPackProto> = emptyList(),
    val nextCursor: String = "",
    val hasMore: Boolean = false
)

data class SearchStickerPacksRequestProto(
    val query: String = "",
    val limit: Int = 20
)

data class SearchStickerPacksResponseProto(
    val packs: List<StickerPackProto> = emptyList()
)

data class UpdateStickerPackRequestProto(
    val packId: String = "",
    val title: String = "",
    val coverStickerId: String = ""
)

data class UpdateStickerPackResponseProto(
    val success: Boolean = false
)

data class SetFeaturedStickerPackRequestProto(
    val packId: String = "",
    val featured: Boolean = false
)

data class SetFeaturedStickerPackResponseProto(
    val success: Boolean = false
)
