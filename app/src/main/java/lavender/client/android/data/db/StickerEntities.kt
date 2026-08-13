package lavender.client.android.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import lavender.client.android.data.models.Sticker
import lavender.client.android.data.models.StickerPack

@Entity(
    tableName = "sticker_packs",
    indices = [Index("creatorUserId"), Index("status")]
)
data class StickerPackEntity(
    @PrimaryKey val id: String,
    val title: String,
    val name: String,
    val creatorUserId: String,
    val creatorUsername: String,
    val coverStickerId: String,
    val status: String,
    val rejectionReason: String,
    val isFeatured: Boolean,
    val createdAt: Long,
    val updatedAt: Long
)

@Entity(
    tableName = "stickers",
    indices = [Index("packId")]
)
data class StickerEntity(
    @PrimaryKey val id: String,
    val packId: String,
    val lottieUrl: String,
    val thumbnailUrl: String,
    val emoji: String,
    val width: Int,
    val height: Int,
    val sortOrder: Int,
    val createdAt: Long
)

fun StickerPackEntity.toDomain(stickers: List<Sticker>): StickerPack = StickerPack(
    id = id,
    title = title,
    name = name,
    creatorUsername = creatorUsername,
    stickers = stickers,
    coverStickerId = coverStickerId,
    status = status,
    rejectionReason = rejectionReason,
    isFeatured = isFeatured
)

fun StickerEntity.toDomain(): Sticker = Sticker(
    id = id,
    packId = packId,
    lottieUrl = lottieUrl,
    thumbnailUrl = thumbnailUrl,
    emoji = emoji,
    width = width,
    height = height
)
