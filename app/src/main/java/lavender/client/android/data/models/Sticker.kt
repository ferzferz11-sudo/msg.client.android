package lavender.client.android.data.models

data class Sticker(
    val id: String = "",
    val packId: String = "",
    val lottieUrl: String = "",
    val thumbnailUrl: String = "",
    val emoji: String = "",
    val width: Int = 512,
    val height: Int = 512
)

data class StickerPack(
    val id: String = "",
    val title: String = "",
    val name: String = "",
    val creatorUsername: String = "",
    val stickers: List<Sticker> = emptyList(),
    val coverStickerId: String = "",
    val status: String = "draft",
    val rejectionReason: String = "",
    val isFeatured: Boolean = false
)
