package lavender.client.android.data.grpc

import lavender.client.android.data.proto.*

// ===== Helper: parse StickerProto from CodedInputStream =====
private fun parseSticker(cis: com.google.protobuf.CodedInputStream): StickerProto {
    var id = ""; var packId = ""; var lottieUrl = ""; var thumbnailUrl = ""
    var emoji = ""; var width = 0; var height = 0; var createdAt = 0L
    while (!cis.isAtEnd) { val tag = cis.readTag(); if (tag == 0) break
        when (com.google.protobuf.WireFormat.getTagFieldNumber(tag)) {
            1 -> id = cis.readString(); 2 -> packId = cis.readString()
            3 -> lottieUrl = cis.readString(); 4 -> thumbnailUrl = cis.readString()
            5 -> emoji = cis.readString(); 6 -> width = cis.readInt32()
            7 -> height = cis.readInt32(); 8 -> createdAt = cis.readInt64()
            else -> cis.skipField(tag)
        }
    }
    return StickerProto(id, packId, lottieUrl, thumbnailUrl, emoji, width, height, createdAt)
}

private fun writeSticker(cos: com.google.protobuf.CodedOutputStream, field: Int, v: StickerProto) {
    val baos = java.io.ByteArrayOutputStream(); val inner = com.google.protobuf.CodedOutputStream.newInstance(baos)
    if (v.id.isNotEmpty()) inner.writeString(1, v.id); if (v.packId.isNotEmpty()) inner.writeString(2, v.packId)
    if (v.lottieUrl.isNotEmpty()) inner.writeString(3, v.lottieUrl); if (v.thumbnailUrl.isNotEmpty()) inner.writeString(4, v.thumbnailUrl)
    if (v.emoji.isNotEmpty()) inner.writeString(5, v.emoji); if (v.width != 0) inner.writeInt32(6, v.width)
    if (v.height != 0) inner.writeInt32(7, v.height); if (v.createdAt != 0L) inner.writeInt64(8, v.createdAt)
    inner.flush(); cos.writeByteArray(field, baos.toByteArray())
}

// ===== Helper: parse StickerPackProto from CodedInputStream =====
private fun parseStickerPack(cis: com.google.protobuf.CodedInputStream): StickerPackProto {
    var id = ""; var title = ""; var name = ""; var creatorUserId = ""; var creatorUsername = ""
    val stickers = mutableListOf<StickerProto>(); var coverStickerId = ""; var status = ""
    var rejectionReason = ""; var createdAt = 0L; var updatedAt = 0L; var isFeatured = false
    while (!cis.isAtEnd) { val tag = cis.readTag(); if (tag == 0) break
        when (com.google.protobuf.WireFormat.getTagFieldNumber(tag)) {
            1 -> id = cis.readString(); 2 -> title = cis.readString(); 3 -> name = cis.readString()
            4 -> creatorUserId = cis.readString(); 5 -> creatorUsername = cis.readString()
            6 -> { val len = cis.readUInt32(); stickers.add(parseSticker(com.google.protobuf.CodedInputStream.newInstance(cis.readRawBytes(len)))) }
            7 -> coverStickerId = cis.readString(); 8 -> status = cis.readString()
            9 -> rejectionReason = cis.readString(); 10 -> createdAt = cis.readInt64()
            11 -> updatedAt = cis.readInt64(); 12 -> isFeatured = cis.readBool()
            else -> cis.skipField(tag)
        }
    }
    return StickerPackProto(id, title, name, creatorUserId, creatorUsername, stickers, coverStickerId, status, rejectionReason, createdAt, updatedAt, isFeatured)
}

private fun writeStickerPack(cos: com.google.protobuf.CodedOutputStream, field: Int, v: StickerPackProto) {
    val baos = java.io.ByteArrayOutputStream(); val inner = com.google.protobuf.CodedOutputStream.newInstance(baos)
    if (v.id.isNotEmpty()) inner.writeString(1, v.id); if (v.title.isNotEmpty()) inner.writeString(2, v.title)
    if (v.name.isNotEmpty()) inner.writeString(3, v.name); if (v.creatorUserId.isNotEmpty()) inner.writeString(4, v.creatorUserId)
    if (v.creatorUsername.isNotEmpty()) inner.writeString(5, v.creatorUsername)
    for (s in v.stickers) { writeSticker(inner, 6, s) }
    if (v.coverStickerId.isNotEmpty()) inner.writeString(7, v.coverStickerId)
    if (v.status.isNotEmpty()) inner.writeString(8, v.status); if (v.rejectionReason.isNotEmpty()) inner.writeString(9, v.rejectionReason)
    if (v.createdAt != 0L) inner.writeInt64(10, v.createdAt); if (v.updatedAt != 0L) inner.writeInt64(11, v.updatedAt)
    if (v.isFeatured) inner.writeBool(12, v.isFeatured)
    inner.flush(); cos.writeByteArray(field, baos.toByteArray())
}

// ===== CreateStickerPack =====

class CreateStickerPackRequestMarshaller : io.grpc.MethodDescriptor.Marshaller<CreateStickerPackRequestProto> {
    override fun stream(v: CreateStickerPackRequestProto): java.io.InputStream {
        val baos = java.io.ByteArrayOutputStream(); val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        if (v.title.isNotEmpty()) cos.writeString(1, v.title); if (v.name.isNotEmpty()) cos.writeString(2, v.name)
        cos.flush(); return java.io.ByteArrayInputStream(baos.toByteArray())
    }
    override fun parse(s: java.io.InputStream): CreateStickerPackRequestProto = CreateStickerPackRequestProto()
}

class CreateStickerPackResponseMarshaller : io.grpc.MethodDescriptor.Marshaller<CreateStickerPackResponseProto> {
    override fun stream(v: CreateStickerPackResponseProto): java.io.InputStream = java.io.ByteArrayInputStream(byteArrayOf())
    override fun parse(s: java.io.InputStream): CreateStickerPackResponseProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(s)
        var success = false; var error = ""; var pack: StickerPackProto? = null
        while (!cis.isAtEnd) { val tag = cis.readTag(); if (tag == 0) break
            when (com.google.protobuf.WireFormat.getTagFieldNumber(tag)) {
                1 -> success = cis.readBool(); 2 -> error = cis.readString()
                3 -> { val len = cis.readUInt32(); pack = parseStickerPack(com.google.protobuf.CodedInputStream.newInstance(cis.readRawBytes(len))) }
                else -> cis.skipField(tag)
            }
        }
        return CreateStickerPackResponseProto(success, error, pack)
    }
}

// ===== AddSticker =====

class AddStickerRequestMarshaller : io.grpc.MethodDescriptor.Marshaller<AddStickerRequestProto> {
    override fun stream(v: AddStickerRequestProto): java.io.InputStream {
        val baos = java.io.ByteArrayOutputStream(); val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        if (v.packId.isNotEmpty()) cos.writeString(1, v.packId); if (v.lottieUrl.isNotEmpty()) cos.writeString(2, v.lottieUrl)
        if (v.thumbnailUrl.isNotEmpty()) cos.writeString(3, v.thumbnailUrl); if (v.emoji.isNotEmpty()) cos.writeString(4, v.emoji)
        if (v.width != 0) cos.writeInt32(5, v.width); if (v.height != 0) cos.writeInt32(6, v.height)
        cos.flush(); return java.io.ByteArrayInputStream(baos.toByteArray())
    }
    override fun parse(s: java.io.InputStream): AddStickerRequestProto = AddStickerRequestProto()
}

class AddStickerResponseMarshaller : io.grpc.MethodDescriptor.Marshaller<AddStickerResponseProto> {
    override fun stream(v: AddStickerResponseProto): java.io.InputStream = java.io.ByteArrayInputStream(byteArrayOf())
    override fun parse(s: java.io.InputStream): AddStickerResponseProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(s)
        var success = false; var error = ""; var sticker: StickerProto? = null
        while (!cis.isAtEnd) { val tag = cis.readTag(); if (tag == 0) break
            when (com.google.protobuf.WireFormat.getTagFieldNumber(tag)) {
                1 -> success = cis.readBool(); 2 -> error = cis.readString()
                3 -> { val len = cis.readUInt32(); sticker = parseSticker(com.google.protobuf.CodedInputStream.newInstance(cis.readRawBytes(len))) }
                else -> cis.skipField(tag)
            }
        }
        return AddStickerResponseProto(success, error, sticker)
    }
}

// ===== RemoveSticker =====

class RemoveStickerRequestMarshaller : io.grpc.MethodDescriptor.Marshaller<RemoveStickerRequestProto> {
    override fun stream(v: RemoveStickerRequestProto): java.io.InputStream {
        val baos = java.io.ByteArrayOutputStream(); val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        if (v.packId.isNotEmpty()) cos.writeString(1, v.packId); if (v.stickerId.isNotEmpty()) cos.writeString(2, v.stickerId)
        cos.flush(); return java.io.ByteArrayInputStream(baos.toByteArray())
    }
    override fun parse(s: java.io.InputStream): RemoveStickerRequestProto = RemoveStickerRequestProto()
}

class RemoveStickerResponseMarshaller : io.grpc.MethodDescriptor.Marshaller<RemoveStickerResponseProto> {
    override fun stream(v: RemoveStickerResponseProto): java.io.InputStream = java.io.ByteArrayInputStream(byteArrayOf())
    override fun parse(s: java.io.InputStream): RemoveStickerResponseProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(s)
        var success = false
        while (!cis.isAtEnd) { val tag = cis.readTag(); if (tag == 0) break
            when (com.google.protobuf.WireFormat.getTagFieldNumber(tag)) {
                1 -> success = cis.readBool(); else -> cis.skipField(tag)
            }
        }
        return RemoveStickerResponseProto(success)
    }
}

// ===== DeleteStickerPack =====

class DeleteStickerPackRequestMarshaller : io.grpc.MethodDescriptor.Marshaller<DeleteStickerPackRequestProto> {
    override fun stream(v: DeleteStickerPackRequestProto): java.io.InputStream {
        val baos = java.io.ByteArrayOutputStream(); val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        if (v.packId.isNotEmpty()) cos.writeString(1, v.packId)
        cos.flush(); return java.io.ByteArrayInputStream(baos.toByteArray())
    }
    override fun parse(s: java.io.InputStream): DeleteStickerPackRequestProto = DeleteStickerPackRequestProto()
}

class DeleteStickerPackResponseMarshaller : io.grpc.MethodDescriptor.Marshaller<DeleteStickerPackResponseProto> {
    override fun stream(v: DeleteStickerPackResponseProto): java.io.InputStream = java.io.ByteArrayInputStream(byteArrayOf())
    override fun parse(s: java.io.InputStream): DeleteStickerPackResponseProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(s)
        var success = false
        while (!cis.isAtEnd) { val tag = cis.readTag(); if (tag == 0) break
            when (com.google.protobuf.WireFormat.getTagFieldNumber(tag)) {
                1 -> success = cis.readBool(); else -> cis.skipField(tag)
            }
        }
        return DeleteStickerPackResponseProto(success)
    }
}

// ===== GetUserStickerPacks =====

class GetUserStickerPacksRequestMarshaller : io.grpc.MethodDescriptor.Marshaller<GetUserStickerPacksRequestProto> {
    override fun stream(v: GetUserStickerPacksRequestProto): java.io.InputStream = java.io.ByteArrayInputStream(byteArrayOf())
    override fun parse(s: java.io.InputStream): GetUserStickerPacksRequestProto = GetUserStickerPacksRequestProto()
}

class GetUserStickerPacksResponseMarshaller : io.grpc.MethodDescriptor.Marshaller<GetUserStickerPacksResponseProto> {
    override fun stream(v: GetUserStickerPacksResponseProto): java.io.InputStream = java.io.ByteArrayInputStream(byteArrayOf())
    override fun parse(s: java.io.InputStream): GetUserStickerPacksResponseProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(s)
        val packs = mutableListOf<StickerPackProto>()
        while (!cis.isAtEnd) { val tag = cis.readTag(); if (tag == 0) break
            when (com.google.protobuf.WireFormat.getTagFieldNumber(tag)) {
                1 -> { val len = cis.readUInt32(); packs.add(parseStickerPack(com.google.protobuf.CodedInputStream.newInstance(cis.readRawBytes(len)))) }
                else -> cis.skipField(tag)
            }
        }
        return GetUserStickerPacksResponseProto(packs)
    }
}

// ===== GetPublicStickerPacks =====

class GetPublicStickerPacksRequestMarshaller : io.grpc.MethodDescriptor.Marshaller<GetPublicStickerPacksRequestProto> {
    override fun stream(v: GetPublicStickerPacksRequestProto): java.io.InputStream {
        val baos = java.io.ByteArrayOutputStream(); val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        if (v.cursor.isNotEmpty()) cos.writeString(1, v.cursor); if (v.limit != 30) cos.writeInt32(2, v.limit)
        cos.flush(); return java.io.ByteArrayInputStream(baos.toByteArray())
    }
    override fun parse(s: java.io.InputStream): GetPublicStickerPacksRequestProto = GetPublicStickerPacksRequestProto()
}

class GetPublicStickerPacksResponseMarshaller : io.grpc.MethodDescriptor.Marshaller<GetPublicStickerPacksResponseProto> {
    override fun stream(v: GetPublicStickerPacksResponseProto): java.io.InputStream = java.io.ByteArrayInputStream(byteArrayOf())
    override fun parse(s: java.io.InputStream): GetPublicStickerPacksResponseProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(s)
        val packs = mutableListOf<StickerPackProto>(); var nextCursor = ""; var hasMore = false
        while (!cis.isAtEnd) { val tag = cis.readTag(); if (tag == 0) break
            when (com.google.protobuf.WireFormat.getTagFieldNumber(tag)) {
                1 -> { val len = cis.readUInt32(); packs.add(parseStickerPack(com.google.protobuf.CodedInputStream.newInstance(cis.readRawBytes(len)))) }
                2 -> nextCursor = cis.readString(); 3 -> hasMore = cis.readBool()
                else -> cis.skipField(tag)
            }
        }
        return GetPublicStickerPacksResponseProto(packs, nextCursor, hasMore)
    }
}

// ===== GetStickerPack =====

class GetStickerPackRequestMarshaller : io.grpc.MethodDescriptor.Marshaller<GetStickerPackRequestProto> {
    override fun stream(v: GetStickerPackRequestProto): java.io.InputStream {
        val baos = java.io.ByteArrayOutputStream(); val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        if (v.packId.isNotEmpty()) cos.writeString(1, v.packId)
        cos.flush(); return java.io.ByteArrayInputStream(baos.toByteArray())
    }
    override fun parse(s: java.io.InputStream): GetStickerPackRequestProto = GetStickerPackRequestProto()
}

class GetStickerPackResponseMarshaller : io.grpc.MethodDescriptor.Marshaller<GetStickerPackResponseProto> {
    override fun stream(v: GetStickerPackResponseProto): java.io.InputStream = java.io.ByteArrayInputStream(byteArrayOf())
    override fun parse(s: java.io.InputStream): GetStickerPackResponseProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(s)
        var pack: StickerPackProto? = null
        while (!cis.isAtEnd) { val tag = cis.readTag(); if (tag == 0) break
            when (com.google.protobuf.WireFormat.getTagFieldNumber(tag)) {
                1 -> { val len = cis.readUInt32(); pack = parseStickerPack(com.google.protobuf.CodedInputStream.newInstance(cis.readRawBytes(len))) }
                else -> cis.skipField(tag)
            }
        }
        return GetStickerPackResponseProto(pack)
    }
}

// ===== SubmitForApproval =====

class SubmitForApprovalRequestMarshaller : io.grpc.MethodDescriptor.Marshaller<SubmitForApprovalRequestProto> {
    override fun stream(v: SubmitForApprovalRequestProto): java.io.InputStream {
        val baos = java.io.ByteArrayOutputStream(); val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        if (v.packId.isNotEmpty()) cos.writeString(1, v.packId)
        cos.flush(); return java.io.ByteArrayInputStream(baos.toByteArray())
    }
    override fun parse(s: java.io.InputStream): SubmitForApprovalRequestProto = SubmitForApprovalRequestProto()
}

class SubmitForApprovalResponseMarshaller : io.grpc.MethodDescriptor.Marshaller<SubmitForApprovalResponseProto> {
    override fun stream(v: SubmitForApprovalResponseProto): java.io.InputStream = java.io.ByteArrayInputStream(byteArrayOf())
    override fun parse(s: java.io.InputStream): SubmitForApprovalResponseProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(s)
        var success = false; var error = ""
        while (!cis.isAtEnd) { val tag = cis.readTag(); if (tag == 0) break
            when (com.google.protobuf.WireFormat.getTagFieldNumber(tag)) {
                1 -> success = cis.readBool(); 2 -> error = cis.readString()
                else -> cis.skipField(tag)
            }
        }
        return SubmitForApprovalResponseProto(success, error)
    }
}

// ===== ApproveStickerPack =====

class ApproveStickerPackRequestMarshaller : io.grpc.MethodDescriptor.Marshaller<ApproveStickerPackRequestProto> {
    override fun stream(v: ApproveStickerPackRequestProto): java.io.InputStream {
        val baos = java.io.ByteArrayOutputStream(); val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        if (v.packId.isNotEmpty()) cos.writeString(1, v.packId); cos.writeBool(2, v.approved)
        if (v.reason.isNotEmpty()) cos.writeString(3, v.reason)
        cos.flush(); return java.io.ByteArrayInputStream(baos.toByteArray())
    }
    override fun parse(s: java.io.InputStream): ApproveStickerPackRequestProto = ApproveStickerPackRequestProto()
}

class ApproveStickerPackResponseMarshaller : io.grpc.MethodDescriptor.Marshaller<ApproveStickerPackResponseProto> {
    override fun stream(v: ApproveStickerPackResponseProto): java.io.InputStream = java.io.ByteArrayInputStream(byteArrayOf())
    override fun parse(s: java.io.InputStream): ApproveStickerPackResponseProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(s)
        var success = false
        while (!cis.isAtEnd) { val tag = cis.readTag(); if (tag == 0) break
            when (com.google.protobuf.WireFormat.getTagFieldNumber(tag)) {
                1 -> success = cis.readBool(); else -> cis.skipField(tag)
            }
        }
        return ApproveStickerPackResponseProto(success)
    }
}

// ===== GetPendingStickerPacks =====

class GetPendingStickerPacksRequestMarshaller : io.grpc.MethodDescriptor.Marshaller<GetPendingStickerPacksRequestProto> {
    override fun stream(v: GetPendingStickerPacksRequestProto): java.io.InputStream {
        val baos = java.io.ByteArrayOutputStream(); val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        if (v.cursor.isNotEmpty()) cos.writeString(1, v.cursor); if (v.limit != 30) cos.writeInt32(2, v.limit)
        cos.flush(); return java.io.ByteArrayInputStream(baos.toByteArray())
    }
    override fun parse(s: java.io.InputStream): GetPendingStickerPacksRequestProto = GetPendingStickerPacksRequestProto()
}

class GetPendingStickerPacksResponseMarshaller : io.grpc.MethodDescriptor.Marshaller<GetPendingStickerPacksResponseProto> {
    override fun stream(v: GetPendingStickerPacksResponseProto): java.io.InputStream = java.io.ByteArrayInputStream(byteArrayOf())
    override fun parse(s: java.io.InputStream): GetPendingStickerPacksResponseProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(s)
        val packs = mutableListOf<StickerPackProto>(); var nextCursor = ""; var hasMore = false
        while (!cis.isAtEnd) { val tag = cis.readTag(); if (tag == 0) break
            when (com.google.protobuf.WireFormat.getTagFieldNumber(tag)) {
                1 -> { val len = cis.readUInt32(); packs.add(parseStickerPack(com.google.protobuf.CodedInputStream.newInstance(cis.readRawBytes(len)))) }
                2 -> nextCursor = cis.readString(); 3 -> hasMore = cis.readBool()
                else -> cis.skipField(tag)
            }
        }
        return GetPendingStickerPacksResponseProto(packs, nextCursor, hasMore)
    }
}

// ===== SearchStickerPacks =====

class SearchStickerPacksRequestMarshaller : io.grpc.MethodDescriptor.Marshaller<SearchStickerPacksRequestProto> {
    override fun stream(v: SearchStickerPacksRequestProto): java.io.InputStream {
        val baos = java.io.ByteArrayOutputStream(); val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        if (v.query.isNotEmpty()) cos.writeString(1, v.query); if (v.limit != 20) cos.writeInt32(2, v.limit)
        cos.flush(); return java.io.ByteArrayInputStream(baos.toByteArray())
    }
    override fun parse(s: java.io.InputStream): SearchStickerPacksRequestProto = SearchStickerPacksRequestProto()
}

class SearchStickerPacksResponseMarshaller : io.grpc.MethodDescriptor.Marshaller<SearchStickerPacksResponseProto> {
    override fun stream(v: SearchStickerPacksResponseProto): java.io.InputStream = java.io.ByteArrayInputStream(byteArrayOf())
    override fun parse(s: java.io.InputStream): SearchStickerPacksResponseProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(s)
        val packs = mutableListOf<StickerPackProto>()
        while (!cis.isAtEnd) { val tag = cis.readTag(); if (tag == 0) break
            when (com.google.protobuf.WireFormat.getTagFieldNumber(tag)) {
                1 -> { val len = cis.readUInt32(); packs.add(parseStickerPack(com.google.protobuf.CodedInputStream.newInstance(cis.readRawBytes(len)))) }
                else -> cis.skipField(tag)
            }
        }
        return SearchStickerPacksResponseProto(packs)
    }
}

// ===== UpdateStickerPack =====

class UpdateStickerPackRequestMarshaller : io.grpc.MethodDescriptor.Marshaller<UpdateStickerPackRequestProto> {
    override fun stream(v: UpdateStickerPackRequestProto): java.io.InputStream {
        val baos = java.io.ByteArrayOutputStream(); val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        if (v.packId.isNotEmpty()) cos.writeString(1, v.packId); if (v.title.isNotEmpty()) cos.writeString(2, v.title)
        if (v.coverStickerId.isNotEmpty()) cos.writeString(3, v.coverStickerId)
        cos.flush(); return java.io.ByteArrayInputStream(baos.toByteArray())
    }
    override fun parse(s: java.io.InputStream): UpdateStickerPackRequestProto = UpdateStickerPackRequestProto()
}

class UpdateStickerPackResponseMarshaller : io.grpc.MethodDescriptor.Marshaller<UpdateStickerPackResponseProto> {
    override fun stream(v: UpdateStickerPackResponseProto): java.io.InputStream = java.io.ByteArrayInputStream(byteArrayOf())
    override fun parse(s: java.io.InputStream): UpdateStickerPackResponseProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(s)
        var success = false
        while (!cis.isAtEnd) { val tag = cis.readTag(); if (tag == 0) break
            when (com.google.protobuf.WireFormat.getTagFieldNumber(tag)) {
                1 -> success = cis.readBool(); else -> cis.skipField(tag)
            }
        }
        return UpdateStickerPackResponseProto(success)
    }
}

// ===== SetFeaturedStickerPack =====

class SetFeaturedStickerPackRequestMarshaller : io.grpc.MethodDescriptor.Marshaller<SetFeaturedStickerPackRequestProto> {
    override fun stream(v: SetFeaturedStickerPackRequestProto): java.io.InputStream {
        val baos = java.io.ByteArrayOutputStream(); val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        if (v.packId.isNotEmpty()) cos.writeString(1, v.packId); cos.writeBool(2, v.featured)
        cos.flush(); return java.io.ByteArrayInputStream(baos.toByteArray())
    }
    override fun parse(s: java.io.InputStream): SetFeaturedStickerPackRequestProto = SetFeaturedStickerPackRequestProto()
}

class SetFeaturedStickerPackResponseMarshaller : io.grpc.MethodDescriptor.Marshaller<SetFeaturedStickerPackResponseProto> {
    override fun stream(v: SetFeaturedStickerPackResponseProto): java.io.InputStream = java.io.ByteArrayInputStream(byteArrayOf())
    override fun parse(s: java.io.InputStream): SetFeaturedStickerPackResponseProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(s)
        var success = false
        while (!cis.isAtEnd) { val tag = cis.readTag(); if (tag == 0) break
            when (com.google.protobuf.WireFormat.getTagFieldNumber(tag)) {
                1 -> success = cis.readBool(); else -> cis.skipField(tag)
            }
        }
        return SetFeaturedStickerPackResponseProto(success)
    }
}
