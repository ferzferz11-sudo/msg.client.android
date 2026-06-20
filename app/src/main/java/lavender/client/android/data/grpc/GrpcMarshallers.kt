package lavender.client.android.data.grpc

import com.google.protobuf.Timestamp
import io.grpc.MethodDescriptor
import lavender.client.android.data.proto.*
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream

// ======= MARSHALLERS =======
// Extracted from RealGrpcClient v1.1.3.27 — all gRPC MethodDescriptor.Marshaller implementations.
// Total: 90+ marshaller classes, ~1380 LOC.
// These are in the same package as RealGrpcClient, so they're accessible without explicit imports.

class MessageProtoMarshaller : io.grpc.MethodDescriptor.Marshaller<MessageProto> {
    override fun stream(value: MessageProto): java.io.InputStream {
        val baos = java.io.ByteArrayOutputStream(); val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        if (value.id.isNotEmpty()) cos.writeString(1, value.id)
        if (value.user.isNotEmpty()) cos.writeString(2, value.user)
        if (value.text.isNotEmpty()) cos.writeString(3, value.text)
        value.createdAt?.let { cos.writeTag(4, com.google.protobuf.WireFormat.WIRETYPE_LENGTH_DELIMITED); val b = it.toByteArray(); cos.writeUInt32NoTag(b.size); cos.writeRawBytes(b) }

        // Serialize reactions if they exist
        if (value.reactions.isNotEmpty()) {
            val rbaos = java.io.ByteArrayOutputStream()
            val rcos = com.google.protobuf.CodedOutputStream.newInstance(rbaos)
            for (reaction in value.reactions) {
                val singleRbaos = java.io.ByteArrayOutputStream()
                val singleRcos = com.google.protobuf.CodedOutputStream.newInstance(singleRbaos)
                if (reaction.user.isNotEmpty()) singleRcos.writeString(1, reaction.user)
                if (reaction.emoji.isNotEmpty()) singleRcos.writeString(2, reaction.emoji)
                singleRcos.flush()
                val rb = singleRbaos.toByteArray()
                cos.writeTag(5, com.google.protobuf.WireFormat.WIRETYPE_LENGTH_DELIMITED)
                cos.writeUInt32NoTag(rb.size)
                cos.writeRawBytes(rb)
            }
        }

        if (value.password.isNotEmpty()) cos.writeString(6, value.password)
        if (value.repliedToMessageId.isNotEmpty()) cos.writeString(7, value.repliedToMessageId)
        if (value.repliedToUser.isNotEmpty()) cos.writeString(8, value.repliedToUser)
        if (value.repliedToText.isNotEmpty()) cos.writeString(9, value.repliedToText)
        if (value.roomId.isNotEmpty()) cos.writeString(10, value.roomId)
        if (value.isRead) cos.writeBool(11, value.isRead)
        if (value.avatarUrl.isNotEmpty()) cos.writeString(12, value.avatarUrl)
        if (value.imageUrl.isNotEmpty()) cos.writeString(13, value.imageUrl)
        if (value.edited) cos.writeBool(14, value.edited)
        if (value.clientVersion.isNotEmpty()) cos.writeString(15, value.clientVersion)
        if (value.isSuperAdmin) cos.writeBool(16, value.isSuperAdmin)
        if (value.voiceUrl.isNotEmpty()) cos.writeString(17, value.voiceUrl)
        if (value.duration != 0) cos.writeInt32(18, value.duration)
        if (value.register) cos.writeBool(19, value.register)
        // Serialize imageUrls for gallery support (field 20)
        if (value.imageUrls.isNotEmpty()) {
            for (imageUrl in value.imageUrls) {
                cos.writeString(20, imageUrl)
            }
        }
        if (value.deviceId.isNotEmpty()) cos.writeString(21, value.deviceId)
        if (value.deviceName.isNotEmpty()) cos.writeString(22, value.deviceName)
        if (value.userId.isNotEmpty()) cos.writeString(23, value.userId)
        if (value.isE2Ee) cos.writeBool(24, value.isE2Ee)
        if (value.e2EePayload.isNotEmpty()) cos.writeString(25, value.e2EePayload)
        if (value.jwtToken.isNotEmpty()) cos.writeString(26, value.jwtToken)
        cos.flush(); return java.io.ByteArrayInputStream(baos.toByteArray())
    }
    override fun parse(stream: java.io.InputStream): MessageProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(stream); val builder = MessageProto.newBuilder()
        while (!cis.isAtEnd) {
            val tag = cis.readTag(); if (tag == 0) break
            when (com.google.protobuf.WireFormat.getTagFieldNumber(tag)) {
                1 -> builder.setId(cis.readString()); 2 -> builder.setUser(cis.readString()); 3 -> builder.setText(cis.readString())
                4 -> { val l = cis.readUInt32(); builder.setCreatedAt(Timestamp.parseFrom(cis.readRawBytes(l))) }
                5 -> {
                    val l = cis.readUInt32()
                    val reactionCis = com.google.protobuf.CodedInputStream.newInstance(cis.readRawBytes(l))
                    var rUser = ""
                    var rEmoji = ""
                    while (!reactionCis.isAtEnd) {
                        val rTag = reactionCis.readTag()
                        if (rTag == 0) break
                        when (com.google.protobuf.WireFormat.getTagFieldNumber(rTag)) {
                            1 -> rUser = reactionCis.readString()
                            2 -> rEmoji = reactionCis.readString()
                            else -> reactionCis.skipField(rTag)
                        }
                    }
                    if (rUser.isNotEmpty() && rEmoji.isNotEmpty()) {
                        builder.addReaction(ReactionProto(rUser, rEmoji))
                    }
                }
                6 -> builder.setPassword(cis.readString()); 7 -> builder.setRepliedToMessageId(cis.readString()); 8 -> builder.setRepliedToUser(cis.readString()); 9 -> builder.setRepliedToText(cis.readString())
                10 -> builder.setRoomId(cis.readString()); 11 -> builder.setIsRead(cis.readBool()); 12 -> builder.setAvatarUrl(cis.readString()); 13 -> builder.setImageUrl(cis.readString())
                14 -> builder.setEdited(cis.readBool()); 15 -> builder.setClientVersion(cis.readString()); 16 -> builder.setIsSuperAdmin(cis.readBool()); 17 -> builder.setVoiceUrl(cis.readString()); 18 -> builder.setDuration(cis.readInt32())
                19 -> builder.setRegister(cis.readBool())
                20 -> builder.addImageUrls(cis.readString()) // Parse imageUrls for gallery support
                21 -> builder.setDeviceId(cis.readString())
                22 -> builder.setDeviceName(cis.readString())
                23 -> builder.setUserId(cis.readString())
                24 -> builder.setIsE2Ee(cis.readBool())
                25 -> builder.setE2EePayload(cis.readString())
                26 -> builder.setJwtToken(cis.readString())
                else -> cis.skipField(tag)
            }
        }
        return builder.build()
    }
}

class TypingRequestMarshaller : io.grpc.MethodDescriptor.Marshaller<TypingRequestProto> {
    override fun stream(v: TypingRequestProto): java.io.InputStream {
        val baos = java.io.ByteArrayOutputStream(); val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        if (v.roomId.isNotEmpty()) cos.writeString(1, v.roomId); if (v.username.isNotEmpty()) cos.writeString(2, v.username); if (v.isTyping) cos.writeBool(3, v.isTyping)
        if (v.userId.isNotEmpty()) cos.writeString(4, v.userId)
        cos.flush(); return java.io.ByteArrayInputStream(baos.toByteArray())
    }
    override fun parse(s: java.io.InputStream): TypingRequestProto = TypingRequestProto()
}

class TypingSignalMarshaller : io.grpc.MethodDescriptor.Marshaller<TypingSignalProto> {
    override fun stream(v: TypingSignalProto): java.io.InputStream = java.io.ByteArrayInputStream(byteArrayOf())
    override fun parse(s: java.io.InputStream): TypingSignalProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(s); var rid = ""; var u = ""; var it = false; var uid = ""
        while (!cis.isAtEnd) { val tag = cis.readTag(); if (tag == 0) break; when (com.google.protobuf.WireFormat.getTagFieldNumber(tag)) { 1 -> rid = cis.readString(); 2 -> u = cis.readString(); 3 -> it = cis.readBool(); 4 -> uid = cis.readString(); else -> cis.skipField(tag) } }
        return TypingSignalProto(rid, u, it, uid)
    }
}

class GetHistoryRequestMarshaller : io.grpc.MethodDescriptor.Marshaller<GetHistoryRequestProto> {
    override fun stream(v: GetHistoryRequestProto): java.io.InputStream {
        val baos = java.io.ByteArrayOutputStream(); val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        if (v.limit != 0) cos.writeInt32(1, v.limit); if (v.room.isNotEmpty()) cos.writeString(2, v.room)
        cos.flush(); return java.io.ByteArrayInputStream(baos.toByteArray())
    }
    override fun parse(s: java.io.InputStream): GetHistoryRequestProto = GetHistoryRequestProto()
}

class GetHistoryResponseMarshaller : io.grpc.MethodDescriptor.Marshaller<GetHistoryResponseProto> {
    override fun stream(v: GetHistoryResponseProto): java.io.InputStream = java.io.ByteArrayInputStream(byteArrayOf())
    override fun parse(s: java.io.InputStream): GetHistoryResponseProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(s); val msgs = mutableListOf<MessageProto>(); val mm = MessageProtoMarshaller()
        while (!cis.isAtEnd) { val tag = cis.readTag(); if (tag == 0) break; if (com.google.protobuf.WireFormat.getTagFieldNumber(tag) == 1) { val len = cis.readUInt32(); msgs.add(mm.parse(java.io.ByteArrayInputStream(cis.readRawBytes(len)))) } else cis.skipField(tag) }
        return GetHistoryResponseProto(msgs)
    }
}

class GetChatsRequestMarshaller : io.grpc.MethodDescriptor.Marshaller<GetChatsRequestProto> {
    override fun stream(v: GetChatsRequestProto): java.io.InputStream {
        val baos = java.io.ByteArrayOutputStream(); val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        if (v.username.isNotEmpty()) cos.writeString(1, v.username); if (v.userId.isNotEmpty()) cos.writeString(2, v.userId)
        if (v.limit != 0) cos.writeInt32(3, v.limit); if (v.offset != 0) cos.writeInt32(4, v.offset)
        cos.flush(); return java.io.ByteArrayInputStream(baos.toByteArray())
    }
    override fun parse(s: java.io.InputStream): GetChatsRequestProto = GetChatsRequestProto()
}

class GetChatsResponseMarshaller : io.grpc.MethodDescriptor.Marshaller<GetChatsResponseProto> {
    override fun stream(v: GetChatsResponseProto): java.io.InputStream = java.io.ByteArrayInputStream(byteArrayOf())
    override fun parse(s: java.io.InputStream): GetChatsResponseProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(s); val chats = mutableListOf<ChatInfoProto>()
        while (!cis.isAtEnd) { val tag = cis.readTag(); if (tag == 0) break;             if (com.google.protobuf.WireFormat.getTagFieldNumber(tag) == 1) {
                val len = cis.readUInt32(); val b = cis.readRawBytes(len); val cisis = com.google.protobuf.CodedInputStream.newInstance(b)
                var id = ""; var n = ""; var t = ""; var p = ""; var ca: Timestamp? = null; var uc = 0; var lmt: Timestamp? = null; var cr = ""; var lmtxt = ""; var au = ""; var fau = ""; var lmu = ""; var lmhi = false; var amta = false; var cst: Timestamp? = null; var isSecret = false; var peerKey = ""; var e2eeReady = false; var activeAgentId = ""; var agentMode = ""; var isPinned = false; var isMuted = false; var isArchived = false; var pinnedAt = 0L
                while (!cisis.isAtEnd) { val t2 = cisis.readTag(); if (t2 == 0) break; when (com.google.protobuf.WireFormat.getTagFieldNumber(t2)) { 1 -> id = cisis.readString(); 2 -> n = cisis.readString(); 3 -> t = cisis.readString(); 4 -> p = cisis.readString(); 5 -> { val l = cisis.readUInt32(); ca = Timestamp.parseFrom(cisis.readRawBytes(l)) }; 6 -> uc = cisis.readInt32(); 7 -> { val l = cisis.readUInt32(); lmt = Timestamp.parseFrom(cisis.readRawBytes(l)) }; 8 -> cr = cisis.readString(); 9 -> lmtxt = cisis.readString(); 10 -> au = cisis.readString(); 11 -> fau = cisis.readString(); 12 -> lmu = cisis.readString(); 13 -> lmhi = cisis.readBool(); 14 -> amta = cisis.readBool(); 15 -> isSecret = cisis.readBool(); 16 -> peerKey = cisis.readString(); 17 -> e2eeReady = cisis.readBool(); 20 -> activeAgentId = cisis.readString(); 21 -> agentMode = cisis.readString(); 22 -> isPinned = cisis.readBool(); 23 -> isMuted = cisis.readBool(); 24 -> isArchived = cisis.readBool(); 25 -> pinnedAt = cisis.readInt64(); else -> cisis.skipField(t2) } }
                chats.add(ChatInfoProto(id, n, t, p, ca, uc, lmt, cr, lmtxt, au, fau, lmu, lmhi, amta, cst, isSecret, peerKey, e2eeReady, activeAgentId, agentMode, isPinned, isMuted, isArchived, pinnedAt))
            } else cis.skipField(tag)
        }
        return GetChatsResponseProto(chats)
    }
}

class TokenRequestMarshaller : io.grpc.MethodDescriptor.Marshaller<TokenRequestProto> {
    override fun stream(v: TokenRequestProto): java.io.InputStream {
        val baos = java.io.ByteArrayOutputStream(); val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        if (v.user.isNotEmpty()) cos.writeString(1, v.user); if (v.token.isNotEmpty()) cos.writeString(2, v.token); if (v.pushEnabled) cos.writeBool(3, v.pushEnabled)
        if (v.userId.isNotEmpty()) cos.writeString(4, v.userId)
        cos.flush(); return java.io.ByteArrayInputStream(baos.toByteArray())
    }
    override fun parse(s: java.io.InputStream): TokenRequestProto = TokenRequestProto()
}

class TokenResponseMarshaller : io.grpc.MethodDescriptor.Marshaller<TokenResponseProto> {
    override fun stream(v: TokenResponseProto): java.io.InputStream = java.io.ByteArrayInputStream(byteArrayOf())
    override fun parse(s: java.io.InputStream): TokenResponseProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(s); var ok = false
        while (!cis.isAtEnd) { val tag = cis.readTag(); if (tag == 0) break; if (com.google.protobuf.WireFormat.getTagFieldNumber(tag) == 1) ok = cis.readBool() else cis.skipField(tag) }
        return TokenResponseProto(ok)
    }
}

class SaveDraftRequestMarshaller : io.grpc.MethodDescriptor.Marshaller<SaveDraftRequestProto> {
    override fun stream(v: SaveDraftRequestProto): java.io.InputStream {
        val baos = java.io.ByteArrayOutputStream(); val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        cos.writeString(1, v.userId)
        cos.writeString(2, v.roomId)
        cos.writeString(3, v.draftText)
        cos.writeString(4, v.repliedToMessageId)
        cos.writeString(5, v.repliedToUser)
        cos.writeString(6, v.repliedToText)
        cos.flush(); return java.io.ByteArrayInputStream(baos.toByteArray())
    }
    override fun parse(s: java.io.InputStream): SaveDraftRequestProto = SaveDraftRequestProto()
}

class SaveDraftResponseMarshaller : io.grpc.MethodDescriptor.Marshaller<SaveDraftResponseProto> {
    override fun stream(v: SaveDraftResponseProto): java.io.InputStream = java.io.ByteArrayInputStream(byteArrayOf())
    override fun parse(s: java.io.InputStream): SaveDraftResponseProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(s); var ok = false; var msg = ""
        while (!cis.isAtEnd) { val tag = cis.readTag(); if (tag == 0) break; when (com.google.protobuf.WireFormat.getTagFieldNumber(tag)) { 1 -> ok = cis.readBool(); 2 -> msg = cis.readString(); else -> cis.skipField(tag) } }
        return SaveDraftResponseProto(ok, msg)
    }
}

class GetDraftRequestMarshaller : io.grpc.MethodDescriptor.Marshaller<GetDraftRequestProto> {
    override fun stream(v: GetDraftRequestProto): java.io.InputStream {
        val baos = java.io.ByteArrayOutputStream(); val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        cos.writeString(1, v.userId)
        cos.writeString(2, v.roomId)
        cos.flush(); return java.io.ByteArrayInputStream(baos.toByteArray())
    }
    override fun parse(s: java.io.InputStream): GetDraftRequestProto = GetDraftRequestProto()
}

class GetDraftResponseMarshaller : io.grpc.MethodDescriptor.Marshaller<GetDraftResponseProto> {
    override fun stream(v: GetDraftResponseProto): java.io.InputStream = java.io.ByteArrayInputStream(byteArrayOf())
    override fun parse(s: java.io.InputStream): GetDraftResponseProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(s); var dt = ""; var rmid = ""; var ru = ""; var rt = ""; var hd = false
        while (!cis.isAtEnd) { val tag = cis.readTag(); if (tag == 0) break; when (com.google.protobuf.WireFormat.getTagFieldNumber(tag)) { 1 -> dt = cis.readString(); 2 -> rmid = cis.readString(); 3 -> ru = cis.readString(); 4 -> rt = cis.readString(); 5 -> hd = cis.readBool(); else -> cis.skipField(tag) } }
        return GetDraftResponseProto(dt, rmid, ru, rt, hd)
    }
}

class DeleteDraftRequestMarshaller : io.grpc.MethodDescriptor.Marshaller<DeleteDraftRequestProto> {
    override fun stream(v: DeleteDraftRequestProto): java.io.InputStream {
        val baos = java.io.ByteArrayOutputStream(); val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        cos.writeString(1, v.userId)
        cos.writeString(2, v.roomId)
        cos.flush(); return java.io.ByteArrayInputStream(baos.toByteArray())
    }
    override fun parse(s: java.io.InputStream): DeleteDraftRequestProto = DeleteDraftRequestProto()
}

class DeleteDraftResponseMarshaller : io.grpc.MethodDescriptor.Marshaller<DeleteDraftResponseProto> {
    override fun stream(v: DeleteDraftResponseProto): java.io.InputStream = java.io.ByteArrayInputStream(byteArrayOf())
    override fun parse(s: java.io.InputStream): DeleteDraftResponseProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(s); var ok = false
        while (!cis.isAtEnd) { val tag = cis.readTag(); if (tag == 0) break; if (com.google.protobuf.WireFormat.getTagFieldNumber(tag) == 1) ok = cis.readBool() else cis.skipField(tag) }
        return DeleteDraftResponseProto(ok)
    }
}

class GetMutedChatsRequestMarshaller : io.grpc.MethodDescriptor.Marshaller<GetMutedChatsRequestProto> {
    override fun stream(v: GetMutedChatsRequestProto): java.io.InputStream {
        val baos = java.io.ByteArrayOutputStream(); val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        if (v.userId.isNotEmpty()) cos.writeString(1, v.userId)
        cos.flush(); return java.io.ByteArrayInputStream(baos.toByteArray())
    }
    override fun parse(s: java.io.InputStream): GetMutedChatsRequestProto = GetMutedChatsRequestProto()
}

class GetMutedChatsResponseMarshaller : io.grpc.MethodDescriptor.Marshaller<GetMutedChatsResponseProto> {
    override fun stream(v: GetMutedChatsResponseProto): java.io.InputStream = java.io.ByteArrayInputStream(byteArrayOf())
    override fun parse(s: java.io.InputStream): GetMutedChatsResponseProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(s); val ids = mutableListOf<String>()
        while (!cis.isAtEnd) { val tag = cis.readTag(); if (tag == 0) break; if (com.google.protobuf.WireFormat.getTagFieldNumber(tag) == 1) ids.add(cis.readString()) else cis.skipField(tag) }
        return GetMutedChatsResponseProto(ids)
    }
}

class SetMutedChatRequestMarshaller : io.grpc.MethodDescriptor.Marshaller<SetMutedChatRequestProto> {
    override fun stream(v: SetMutedChatRequestProto): java.io.InputStream {
        val baos = java.io.ByteArrayOutputStream(); val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        if (v.userId.isNotEmpty()) cos.writeString(1, v.userId); if (v.roomId.isNotEmpty()) cos.writeString(2, v.roomId); if (v.muted) cos.writeBool(3, v.muted)
        cos.flush(); return java.io.ByteArrayInputStream(baos.toByteArray())
    }
    override fun parse(s: java.io.InputStream): SetMutedChatRequestProto = SetMutedChatRequestProto()
}

class SetMutedChatResponseMarshaller : io.grpc.MethodDescriptor.Marshaller<SetMutedChatResponseProto> {
    override fun stream(v: SetMutedChatResponseProto): java.io.InputStream = java.io.ByteArrayInputStream(byteArrayOf())
    override fun parse(s: java.io.InputStream): SetMutedChatResponseProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(s); var ok = false
        while (!cis.isAtEnd) { val tag = cis.readTag(); if (tag == 0) break; if (com.google.protobuf.WireFormat.getTagFieldNumber(tag) == 1) ok = cis.readBool() else cis.skipField(tag) }
        return SetMutedChatResponseProto(ok)
    }
}

class GetUserIdRequestMarshaller : io.grpc.MethodDescriptor.Marshaller<GetUserIdRequestProto> {
    override fun stream(v: GetUserIdRequestProto): java.io.InputStream {
        val baos = java.io.ByteArrayOutputStream(); val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        if (v.username.isNotEmpty()) cos.writeString(1, v.username)
        cos.flush(); return java.io.ByteArrayInputStream(baos.toByteArray())
    }
    override fun parse(s: java.io.InputStream): GetUserIdRequestProto = GetUserIdRequestProto()
}

class GetUserIdResponseMarshaller : io.grpc.MethodDescriptor.Marshaller<GetUserIdResponseProto> {
    override fun stream(v: GetUserIdResponseProto): java.io.InputStream = java.io.ByteArrayInputStream(byteArrayOf())
    override fun parse(s: java.io.InputStream): GetUserIdResponseProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(s); var uid = ""; var f = false
        while (!cis.isAtEnd) { val tag = cis.readTag(); if (tag == 0) break; when (com.google.protobuf.WireFormat.getTagFieldNumber(tag)) { 1 -> uid = cis.readString(); 2 -> f = cis.readBool(); else -> cis.skipField(tag) } }
        return GetUserIdResponseProto(uid, f)
    }
}

class AddFavoriteRequestMarshaller : io.grpc.MethodDescriptor.Marshaller<AddFavoriteRequestProto> {
    override fun stream(v: AddFavoriteRequestProto): java.io.InputStream {
        val baos = java.io.ByteArrayOutputStream(); val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        if (v.userId.isNotEmpty()) cos.writeString(1, v.userId); if (v.messageId.isNotEmpty()) cos.writeString(2, v.messageId)
        cos.flush(); return java.io.ByteArrayInputStream(baos.toByteArray())
    }
    override fun parse(s: java.io.InputStream): AddFavoriteRequestProto = AddFavoriteRequestProto()
}

class AddFavoriteResponseMarshaller : io.grpc.MethodDescriptor.Marshaller<AddFavoriteResponseProto> {
    override fun stream(v: AddFavoriteResponseProto): java.io.InputStream = java.io.ByteArrayInputStream(byteArrayOf())
    override fun parse(s: java.io.InputStream): AddFavoriteResponseProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(s); var ok = false; var msg = ""
        while (!cis.isAtEnd) { val tag = cis.readTag(); if (tag == 0) break; when (com.google.protobuf.WireFormat.getTagFieldNumber(tag)) { 1 -> ok = cis.readBool(); 2 -> msg = cis.readString(); else -> cis.skipField(tag) } }
        return AddFavoriteResponseProto(ok, msg)
    }
}

class RemoveFavoriteRequestMarshaller : io.grpc.MethodDescriptor.Marshaller<RemoveFavoriteRequestProto> {
    override fun stream(v: RemoveFavoriteRequestProto): java.io.InputStream {
        val baos = java.io.ByteArrayOutputStream(); val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        if (v.userId.isNotEmpty()) cos.writeString(1, v.userId); if (v.messageId.isNotEmpty()) cos.writeString(2, v.messageId)
        cos.flush(); return java.io.ByteArrayInputStream(baos.toByteArray())
    }
    override fun parse(s: java.io.InputStream): RemoveFavoriteRequestProto = RemoveFavoriteRequestProto()
}

class RemoveFavoriteResponseMarshaller : io.grpc.MethodDescriptor.Marshaller<RemoveFavoriteResponseProto> {
    override fun stream(v: RemoveFavoriteResponseProto): java.io.InputStream = java.io.ByteArrayInputStream(byteArrayOf())
    override fun parse(s: java.io.InputStream): RemoveFavoriteResponseProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(s); var ok = false
        while (!cis.isAtEnd) { val tag = cis.readTag(); if (tag == 0) break; if (com.google.protobuf.WireFormat.getTagFieldNumber(tag) == 1) ok = cis.readBool() else cis.skipField(tag) }
        return RemoveFavoriteResponseProto(ok)
    }
}

class GetFavoritesRequestMarshaller : io.grpc.MethodDescriptor.Marshaller<GetFavoritesRequestProto> {
    override fun stream(v: GetFavoritesRequestProto): java.io.InputStream {
        val baos = java.io.ByteArrayOutputStream(); val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        if (v.userId.isNotEmpty()) cos.writeString(1, v.userId)
        cos.flush(); return java.io.ByteArrayInputStream(baos.toByteArray())
    }
    override fun parse(s: java.io.InputStream): GetFavoritesRequestProto = GetFavoritesRequestProto()
}

class GetFavoritesResponseMarshaller : io.grpc.MethodDescriptor.Marshaller<GetFavoritesResponseProto> {
    override fun stream(v: GetFavoritesResponseProto): java.io.InputStream = java.io.ByteArrayInputStream(byteArrayOf())
    override fun parse(s: java.io.InputStream): GetFavoritesResponseProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(s); val msgs = mutableListOf<MessageProto>(); val mm = MessageProtoMarshaller()
        while (!cis.isAtEnd) { val tag = cis.readTag(); if (tag == 0) break; if (com.google.protobuf.WireFormat.getTagFieldNumber(tag) == 1) { val len = cis.readUInt32(); msgs.add(mm.parse(java.io.ByteArrayInputStream(cis.readRawBytes(len)))) } else cis.skipField(tag) }
        return GetFavoritesResponseProto(msgs)
    }
}

class EditMessageRequestMarshaller : io.grpc.MethodDescriptor.Marshaller<EditMessageRequestProto> {
    override fun stream(v: EditMessageRequestProto): java.io.InputStream {
        val baos = java.io.ByteArrayOutputStream(); val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        if (v.messageId.isNotEmpty()) cos.writeString(1, v.messageId); if (v.text.isNotEmpty()) cos.writeString(2, v.text)
        cos.flush(); return java.io.ByteArrayInputStream(baos.toByteArray())
    }
    override fun parse(s: java.io.InputStream): EditMessageRequestProto = EditMessageRequestProto()
}

class EditMessageResponseMarshaller : io.grpc.MethodDescriptor.Marshaller<EditMessageResponseProto> {
    override fun stream(v: EditMessageResponseProto): java.io.InputStream = java.io.ByteArrayInputStream(byteArrayOf())
    override fun parse(s: java.io.InputStream): EditMessageResponseProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(s); var ok = false; var msg = ""
        while (!cis.isAtEnd) { val tag = cis.readTag(); if (tag == 0) break; when (com.google.protobuf.WireFormat.getTagFieldNumber(tag)) { 1 -> ok = cis.readBool(); 2 -> msg = cis.readString(); else -> cis.skipField(tag) } }
        return EditMessageResponseProto(ok, msg)
    }
}

class MarkReadRequestMarshaller : io.grpc.MethodDescriptor.Marshaller<MarkReadRequestProto> {
    override fun stream(v: MarkReadRequestProto): java.io.InputStream {
        val baos = java.io.ByteArrayOutputStream(); val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        if (v.roomId.isNotEmpty()) cos.writeString(1, v.roomId); if (v.username.isNotEmpty()) cos.writeString(2, v.username); if (v.userId.isNotEmpty()) cos.writeString(3, v.userId)
        cos.flush(); return java.io.ByteArrayInputStream(baos.toByteArray())
    }
    override fun parse(s: java.io.InputStream): MarkReadRequestProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(s); var rid = ""; var u = ""; var uid = ""
        while (!cis.isAtEnd) { val tag = cis.readTag(); if (tag == 0) break; when (com.google.protobuf.WireFormat.getTagFieldNumber(tag)) { 1 -> rid = cis.readString(); 2 -> u = cis.readString(); 3 -> uid = cis.readString(); else -> cis.skipField(tag) } }
        return MarkReadRequestProto(rid, u, uid)
    }
}

class MarkReadResponseMarshaller : io.grpc.MethodDescriptor.Marshaller<MarkReadResponseProto> {
    override fun stream(v: MarkReadResponseProto): java.io.InputStream = java.io.ByteArrayInputStream(byteArrayOf())
    override fun parse(s: java.io.InputStream): MarkReadResponseProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(s); var ok = false
        while (!cis.isAtEnd) { val tag = cis.readTag(); if (tag == 0) break; if (com.google.protobuf.WireFormat.getTagFieldNumber(tag) == 1) ok = cis.readBool() else cis.skipField(tag) }
        return MarkReadResponseProto(ok)
    }
}

class DeleteChatRequestMarshaller : io.grpc.MethodDescriptor.Marshaller<DeleteChatRequestProto> {
    override fun stream(v: DeleteChatRequestProto): java.io.InputStream {
        val baos = java.io.ByteArrayOutputStream(); val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        if (v.chatId.isNotEmpty()) cos.writeString(1, v.chatId)
        if (v.requesterUsername.isNotEmpty()) cos.writeString(2, v.requesterUsername)
        if (v.requesterUserId.isNotEmpty()) cos.writeString(3, v.requesterUserId)
        cos.flush(); return java.io.ByteArrayInputStream(baos.toByteArray())
    }
    override fun parse(s: java.io.InputStream): DeleteChatRequestProto = DeleteChatRequestProto()
}

class DeleteChatResponseMarshaller : io.grpc.MethodDescriptor.Marshaller<DeleteChatResponseProto> {
    override fun stream(v: DeleteChatResponseProto): java.io.InputStream = java.io.ByteArrayInputStream(byteArrayOf())
    override fun parse(s: java.io.InputStream): DeleteChatResponseProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(s); var ok = false; var msg = ""
        while (!cis.isAtEnd) { val tag = cis.readTag(); if (tag == 0) break; when (com.google.protobuf.WireFormat.getTagFieldNumber(tag)) { 1 -> ok = cis.readBool(); 2 -> msg = cis.readString(); else -> cis.skipField(tag) } }
        return DeleteChatResponseProto(ok, msg)
    }
}

class UpdateAvatarRequestMarshaller : io.grpc.MethodDescriptor.Marshaller<UpdateAvatarRequestProto> {
    override fun stream(v: UpdateAvatarRequestProto): java.io.InputStream {
        val baos = java.io.ByteArrayOutputStream(); val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        if (v.username.isNotEmpty()) cos.writeString(1, v.username); if (v.avatarUrl.isNotEmpty()) cos.writeString(2, v.avatarUrl); if (v.fullAvatarUrl.isNotEmpty()) cos.writeString(3, v.fullAvatarUrl); if (v.userId.isNotEmpty()) cos.writeString(4, v.userId)
        cos.flush(); return java.io.ByteArrayInputStream(baos.toByteArray())
    }
    override fun parse(s: java.io.InputStream): UpdateAvatarRequestProto = UpdateAvatarRequestProto()
}

class UpdateAvatarResponseMarshaller : io.grpc.MethodDescriptor.Marshaller<UpdateAvatarResponseProto> {
    override fun stream(v: UpdateAvatarResponseProto): java.io.InputStream = java.io.ByteArrayInputStream(byteArrayOf())
    override fun parse(s: java.io.InputStream): UpdateAvatarResponseProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(s); var ok = false; var msg = ""
        while (!cis.isAtEnd) { val tag = cis.readTag(); if (tag == 0) break; when (com.google.protobuf.WireFormat.getTagFieldNumber(tag)) { 1 -> ok = cis.readBool(); 2 -> msg = cis.readString(); else -> cis.skipField(tag) } }
        return UpdateAvatarResponseProto(ok, msg)
    }
}

class GetUserAvatarRequestMarshaller : io.grpc.MethodDescriptor.Marshaller<GetUserAvatarRequestProto> {
    override fun stream(v: GetUserAvatarRequestProto): java.io.InputStream {
        val baos = java.io.ByteArrayOutputStream(); val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        if (v.username.isNotEmpty()) cos.writeString(1, v.username)
        if (v.userId.isNotEmpty()) cos.writeString(2, v.userId)
        cos.flush(); return java.io.ByteArrayInputStream(baos.toByteArray())
    }
    override fun parse(s: java.io.InputStream): GetUserAvatarRequestProto = GetUserAvatarRequestProto()
}

class GetUserAvatarResponseMarshaller : io.grpc.MethodDescriptor.Marshaller<GetUserAvatarResponseProto> {
    override fun stream(v: GetUserAvatarResponseProto): java.io.InputStream = java.io.ByteArrayInputStream(byteArrayOf())
    override fun parse(s: java.io.InputStream): GetUserAvatarResponseProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(s); var au = ""; var fau = ""
        while (!cis.isAtEnd) { val tag = cis.readTag(); if (tag == 0) break; when (com.google.protobuf.WireFormat.getTagFieldNumber(tag)) { 1 -> au = cis.readString(); 2 -> fau = cis.readString(); else -> cis.skipField(tag) } }
        return GetUserAvatarResponseProto(au, fau)
    }
}

class UpdateProfileRequestMarshaller : io.grpc.MethodDescriptor.Marshaller<UpdateProfileRequestProto> {
    override fun stream(v: UpdateProfileRequestProto): java.io.InputStream {
        val baos = java.io.ByteArrayOutputStream(); val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        if (v.username.isNotEmpty()) cos.writeString(1, v.username); if (v.bio.isNotEmpty()) cos.writeString(2, v.bio); if (v.status.isNotEmpty()) cos.writeString(3, v.status); if (v.userId.isNotEmpty()) cos.writeString(4, v.userId)
        cos.flush(); return java.io.ByteArrayInputStream(baos.toByteArray())
    }
    override fun parse(s: java.io.InputStream): UpdateProfileRequestProto = UpdateProfileRequestProto()
}

class UpdateProfileResponseMarshaller : io.grpc.MethodDescriptor.Marshaller<UpdateProfileResponseProto> {
    override fun stream(v: UpdateProfileResponseProto): java.io.InputStream = java.io.ByteArrayInputStream(byteArrayOf())
    override fun parse(s: java.io.InputStream): UpdateProfileResponseProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(s); var ok = false; var msg = ""
        while (!cis.isAtEnd) { val tag = cis.readTag(); if (tag == 0) break; when (com.google.protobuf.WireFormat.getTagFieldNumber(tag)) { 1 -> ok = cis.readBool(); 2 -> msg = cis.readString(); else -> cis.skipField(tag) } }
        return UpdateProfileResponseProto(ok, msg)
    }
}

class GetUserProfileRequestMarshaller : io.grpc.MethodDescriptor.Marshaller<GetUserProfileRequestProto> {
    override fun stream(v: GetUserProfileRequestProto): java.io.InputStream {
        val baos = java.io.ByteArrayOutputStream(); val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        if (v.userId.isNotEmpty()) cos.writeString(2, v.userId)
        cos.flush(); return java.io.ByteArrayInputStream(baos.toByteArray())
    }
    override fun parse(s: java.io.InputStream): GetUserProfileRequestProto = GetUserProfileRequestProto()
}

class GetUserProfileResponseMarshaller : io.grpc.MethodDescriptor.Marshaller<GetUserProfileResponseProto> {
    override fun stream(v: GetUserProfileResponseProto): java.io.InputStream = java.io.ByteArrayInputStream(byteArrayOf())
    override fun parse(s: java.io.InputStream): GetUserProfileResponseProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(s); var u = ""; var b = ""; var st = ""; var au = ""; var ls: Timestamp? = null; var fau = ""
        while (!cis.isAtEnd) { val tag = cis.readTag(); if (tag == 0) break; when (com.google.protobuf.WireFormat.getTagFieldNumber(tag)) { 1 -> u = cis.readString(); 2 -> b = cis.readString(); 3 -> st = cis.readString(); 4 -> au = cis.readString(); 5 -> { val len = cis.readUInt32(); ls = ProtoUtils.parseTimestampFromProto(java.io.ByteArrayInputStream(cis.readRawBytes(len))) }; 6 -> fau = cis.readString(); else -> cis.skipField(tag) } }
        return GetUserProfileResponseProto(u, b, st, au, ls, fau)
    }
}

class DeleteMessagesRequestMarshaller : io.grpc.MethodDescriptor.Marshaller<DeleteMessagesRequestProto> {
    override fun stream(v: DeleteMessagesRequestProto): java.io.InputStream {
        val baos = java.io.ByteArrayOutputStream(); val cos = com.google.protobuf.CodedOutputStream.newInstance(baos); val mm = MessageProtoMarshaller()
        for (m in v.messages) { val b = mm.stream(m).readBytes(); cos.writeTag(1, com.google.protobuf.WireFormat.WIRETYPE_LENGTH_DELIMITED); cos.writeUInt32NoTag(b.size); cos.writeRawBytes(b) }
        if (v.requesterUsername.isNotEmpty()) cos.writeString(2, v.requesterUsername)
        cos.flush(); return java.io.ByteArrayInputStream(baos.toByteArray())
    }
    override fun parse(s: java.io.InputStream): DeleteMessagesRequestProto = DeleteMessagesRequestProto()
}

class DeleteMessagesResponseMarshaller : io.grpc.MethodDescriptor.Marshaller<DeleteMessagesResponseProto> {
    override fun stream(v: DeleteMessagesResponseProto): java.io.InputStream = java.io.ByteArrayInputStream(byteArrayOf())
    override fun parse(s: java.io.InputStream): DeleteMessagesResponseProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(s); var ok = false
        while (!cis.isAtEnd) { val tag = cis.readTag(); if (tag == 0) break; if (com.google.protobuf.WireFormat.getTagFieldNumber(tag) == 1) ok = cis.readBool() else cis.skipField(tag) }
        return DeleteMessagesResponseProto(ok)
    }
}

class UpdateUsernameRequestMarshaller : io.grpc.MethodDescriptor.Marshaller<UpdateUsernameRequestProto> {
    override fun stream(v: UpdateUsernameRequestProto): java.io.InputStream {
        val baos = java.io.ByteArrayOutputStream(); val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        if (v.oldUsername.isNotEmpty()) cos.writeString(1, v.oldUsername); if (v.newUsername.isNotEmpty()) cos.writeString(2, v.newUsername)
        if (v.userId.isNotEmpty()) cos.writeString(3, v.userId)
        cos.flush(); return java.io.ByteArrayInputStream(baos.toByteArray())
    }
    override fun parse(s: java.io.InputStream): UpdateUsernameRequestProto = UpdateUsernameRequestProto()
}

class UpdateUsernameResponseMarshaller : io.grpc.MethodDescriptor.Marshaller<UpdateUsernameResponseProto> {
    override fun stream(v: UpdateUsernameResponseProto): java.io.InputStream = java.io.ByteArrayInputStream(byteArrayOf())
    override fun parse(s: java.io.InputStream): UpdateUsernameResponseProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(s); var ok = false; var msg = ""
        while (!cis.isAtEnd) { val tag = cis.readTag(); if (tag == 0) break; when (com.google.protobuf.WireFormat.getTagFieldNumber(tag)) { 1 -> ok = cis.readBool(); 2 -> msg = cis.readString(); else -> cis.skipField(tag) } }
        return UpdateUsernameResponseProto(ok, msg)
    }
}

class UpdatePasswordRequestMarshaller : io.grpc.MethodDescriptor.Marshaller<UpdatePasswordRequestProto> {
    override fun stream(v: UpdatePasswordRequestProto): java.io.InputStream {
        val baos = java.io.ByteArrayOutputStream(); val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        if (v.username.isNotEmpty()) cos.writeString(1, v.username); if (v.oldPassword.isNotEmpty()) cos.writeString(2, v.oldPassword); if (v.newPassword.isNotEmpty()) cos.writeString(3, v.newPassword)
        if (v.userId.isNotEmpty()) cos.writeString(4, v.userId)
        cos.flush(); return java.io.ByteArrayInputStream(baos.toByteArray())
    }
    override fun parse(s: java.io.InputStream): UpdatePasswordRequestProto = UpdatePasswordRequestProto()
}

class UpdatePasswordResponseMarshaller : io.grpc.MethodDescriptor.Marshaller<UpdatePasswordResponseProto> {
    override fun stream(v: UpdatePasswordResponseProto): java.io.InputStream = java.io.ByteArrayInputStream(byteArrayOf())
    override fun parse(s: java.io.InputStream): UpdatePasswordResponseProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(s); var ok = false; var msg = ""
        while (!cis.isAtEnd) { val tag = cis.readTag(); if (tag == 0) break; when (com.google.protobuf.WireFormat.getTagFieldNumber(tag)) { 1 -> ok = cis.readBool(); 2 -> msg = cis.readString(); else -> cis.skipField(tag) } }
        return UpdatePasswordResponseProto(ok, msg)
    }
}

class CreateDirectChatRequestMarshaller : io.grpc.MethodDescriptor.Marshaller<CreateDirectChatRequestProto> {
    override fun stream(v: CreateDirectChatRequestProto): java.io.InputStream {
        val baos = java.io.ByteArrayOutputStream(); val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        if (v.user1.isNotEmpty()) cos.writeString(1, v.user1); if (v.user2.isNotEmpty()) cos.writeString(2, v.user2)
        if (v.user1Id.isNotEmpty()) cos.writeString(3, v.user1Id); if (v.user2Id.isNotEmpty()) cos.writeString(4, v.user2Id)
        cos.flush(); return java.io.ByteArrayInputStream(baos.toByteArray())
    }
    override fun parse(s: java.io.InputStream): CreateDirectChatRequestProto = CreateDirectChatRequestProto()
}

class CreateDirectChatResponseMarshaller : io.grpc.MethodDescriptor.Marshaller<CreateDirectChatResponseProto> {
    override fun stream(v: CreateDirectChatResponseProto): java.io.InputStream = java.io.ByteArrayInputStream(byteArrayOf())
    override fun parse(s: java.io.InputStream): CreateDirectChatResponseProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(s); var cid = ""; var ok = false
        while (!cis.isAtEnd) { val tag = cis.readTag(); if (tag == 0) break; when (com.google.protobuf.WireFormat.getTagFieldNumber(tag)) { 1 -> cid = cis.readString(); 2 -> ok = cis.readBool(); else -> cis.skipField(tag) } }
        return CreateDirectChatResponseProto(cid, ok)
    }
}

class CreateGroupChatRequestMarshaller : io.grpc.MethodDescriptor.Marshaller<CreateGroupChatRequestProto> {
    override fun stream(v: CreateGroupChatRequestProto): java.io.InputStream {
        val baos = java.io.ByteArrayOutputStream(); val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        if (v.name.isNotEmpty()) cos.writeString(1, v.name); for (p in v.participants) cos.writeString(2, p); if (v.creator.isNotEmpty()) cos.writeString(3, v.creator)
        if (v.creatorId.isNotEmpty()) cos.writeString(4, v.creatorId); for (pid in v.participantIds) cos.writeString(5, pid)
        if (v.type.isNotEmpty()) cos.writeString(6, v.type)
        cos.flush(); return java.io.ByteArrayInputStream(baos.toByteArray())
    }
    override fun parse(s: java.io.InputStream): CreateGroupChatRequestProto = CreateGroupChatRequestProto()
}

class CreateGroupChatResponseMarshaller : io.grpc.MethodDescriptor.Marshaller<CreateGroupChatResponseProto> {
    override fun stream(v: CreateGroupChatResponseProto): java.io.InputStream = java.io.ByteArrayInputStream(byteArrayOf())
    override fun parse(s: java.io.InputStream): CreateGroupChatResponseProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(s); var cid = ""; var ok = false
        while (!cis.isAtEnd) { val tag = cis.readTag(); if (tag == 0) break; when (com.google.protobuf.WireFormat.getTagFieldNumber(tag)) { 1 -> cid = cis.readString(); 2 -> ok = cis.readBool(); else -> cis.skipField(tag) } }
        return CreateGroupChatResponseProto(cid, ok)
    }
}

class GetAllUsersRequestMarshaller : io.grpc.MethodDescriptor.Marshaller<GetAllUsersRequestProto> {
    override fun stream(v: GetAllUsersRequestProto): java.io.InputStream = java.io.ByteArrayInputStream(byteArrayOf())
    override fun parse(s: java.io.InputStream): GetAllUsersRequestProto = GetAllUsersRequestProto()
}

class UserInfoProtoMarshaller : io.grpc.MethodDescriptor.Marshaller<UserInfoProto> {
    override fun stream(v: UserInfoProto): java.io.InputStream {
        val baos = java.io.ByteArrayOutputStream(); val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        if (v.username.isNotEmpty()) cos.writeString(1, v.username)
        if (v.avatarUrl.isNotEmpty()) cos.writeString(2, v.avatarUrl)
        if (v.lastClientVersion.isNotEmpty()) cos.writeString(3, v.lastClientVersion)
        v.lastSeenAt?.let { cos.writeTag(4, com.google.protobuf.WireFormat.WIRETYPE_LENGTH_DELIMITED); val b = it.toByteArray(); cos.writeUInt32NoTag(b.size); cos.writeRawBytes(b) }
        if (v.email.isNotEmpty()) cos.writeString(5, v.email)
        if (v.userId.isNotEmpty()) cos.writeString(6, v.userId)
        if (v.isSuperAdmin) cos.writeBool(7, v.isSuperAdmin)
        cos.flush(); return java.io.ByteArrayInputStream(baos.toByteArray())
    }
    override fun parse(s: java.io.InputStream): UserInfoProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(s); var u = ""; var a = ""; var v = ""; var ls: Timestamp? = null; var e = ""; var uid = ""; var sa = false
        while (!cis.isAtEnd) {
            val tag = cis.readTag(); if (tag == 0) break
            when (com.google.protobuf.WireFormat.getTagFieldNumber(tag)) {
                1 -> u = cis.readString(); 2 -> a = cis.readString(); 3 -> v = cis.readString()
                4 -> { val l = cis.readUInt32(); ls = Timestamp.parseFrom(cis.readRawBytes(l)) }
                5 -> e = cis.readString()
                6 -> uid = cis.readString()
                7 -> sa = cis.readBool()
                else -> cis.skipField(tag)
            }
        }
        return UserInfoProto(u, a, v, ls, e, uid, sa)
    }
}

class GetAllUsersResponseMarshaller : io.grpc.MethodDescriptor.Marshaller<GetAllUsersResponseProto> {
    override fun stream(v: GetAllUsersResponseProto): java.io.InputStream = java.io.ByteArrayInputStream(byteArrayOf())
    override fun parse(s: java.io.InputStream): GetAllUsersResponseProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(s); val users = mutableListOf<UserInfoProto>(); val um = UserInfoProtoMarshaller(); var serverTime: Timestamp? = null
        while (!cis.isAtEnd) { val tag = cis.readTag(); if (tag == 0) break; when (com.google.protobuf.WireFormat.getTagFieldNumber(tag)) { 1 -> { val len = cis.readUInt32(); users.add(um.parse(java.io.ByteArrayInputStream(cis.readRawBytes(len)))) } 2 -> { val len = cis.readUInt32(); serverTime = ProtoUtils.parseTimestampFromProto(java.io.ByteArrayInputStream(cis.readRawBytes(len))) } else -> cis.skipField(tag) } }
        return GetAllUsersResponseProto(users, serverTime)
    }
}

class UpdateChatAvatarRequestMarshaller : io.grpc.MethodDescriptor.Marshaller<UpdateChatAvatarRequestProto> {
    override fun stream(v: UpdateChatAvatarRequestProto): java.io.InputStream {
        val baos = java.io.ByteArrayOutputStream(); val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        if (v.chatId.isNotEmpty()) cos.writeString(1, v.chatId); if (v.avatarUrl.isNotEmpty()) cos.writeString(2, v.avatarUrl); if (v.username.isNotEmpty()) cos.writeString(3, v.username); if (v.fullAvatarUrl.isNotEmpty()) cos.writeString(4, v.fullAvatarUrl)
        if (v.userId.isNotEmpty()) cos.writeString(5, v.userId)
        cos.flush(); return java.io.ByteArrayInputStream(baos.toByteArray())
    }
    override fun parse(s: java.io.InputStream): UpdateChatAvatarRequestProto = UpdateChatAvatarRequestProto()
}

class UpdateChatAvatarResponseMarshaller : io.grpc.MethodDescriptor.Marshaller<UpdateChatAvatarResponseProto> {
    override fun stream(v: UpdateChatAvatarResponseProto): java.io.InputStream = java.io.ByteArrayInputStream(byteArrayOf())
    override fun parse(s: java.io.InputStream): UpdateChatAvatarResponseProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(s); var ok = false; var msg = ""
        while (!cis.isAtEnd) { val tag = cis.readTag(); if (tag == 0) break; when (com.google.protobuf.WireFormat.getTagFieldNumber(tag)) { 1 -> ok = cis.readBool(); 2 -> msg = cis.readString(); else -> cis.skipField(tag) } }
        return UpdateChatAvatarResponseProto(ok, msg)
    }
}

class AddParticipantRequestMarshaller : io.grpc.MethodDescriptor.Marshaller<AddParticipantRequestProto> {
    override fun stream(v: AddParticipantRequestProto): java.io.InputStream {
        val baos = java.io.ByteArrayOutputStream(); val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        if (v.chatId.isNotEmpty()) cos.writeString(1, v.chatId); if (v.username.isNotEmpty()) cos.writeString(2, v.username)
        if (v.userId.isNotEmpty()) cos.writeString(3, v.userId)
        cos.flush(); return java.io.ByteArrayInputStream(baos.toByteArray())
    }
    override fun parse(s: java.io.InputStream): AddParticipantRequestProto = AddParticipantRequestProto()
}

class AddParticipantResponseMarshaller : io.grpc.MethodDescriptor.Marshaller<AddParticipantResponseProto> {
    override fun stream(v: AddParticipantResponseProto): java.io.InputStream = java.io.ByteArrayInputStream(byteArrayOf())
    override fun parse(s: java.io.InputStream): AddParticipantResponseProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(s); var ok = false; var msg = ""
        while (!cis.isAtEnd) { val tag = cis.readTag(); if (tag == 0) break; when (com.google.protobuf.WireFormat.getTagFieldNumber(tag)) { 1 -> ok = cis.readBool(); 2 -> msg = cis.readString(); else -> cis.skipField(tag) } }
        return AddParticipantResponseProto(ok, msg)
    }
}

class RemoveParticipantRequestMarshaller : io.grpc.MethodDescriptor.Marshaller<RemoveParticipantRequestProto> {
    override fun stream(v: RemoveParticipantRequestProto): java.io.InputStream {
        val baos = java.io.ByteArrayOutputStream(); val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        if (v.chatId.isNotEmpty()) cos.writeString(1, v.chatId); if (v.username.isNotEmpty()) cos.writeString(2, v.username)
        if (v.userId.isNotEmpty()) cos.writeString(3, v.userId)
        cos.flush(); return java.io.ByteArrayInputStream(baos.toByteArray())
    }
    override fun parse(s: java.io.InputStream): RemoveParticipantRequestProto = RemoveParticipantRequestProto()
}

class RemoveParticipantResponseMarshaller : io.grpc.MethodDescriptor.Marshaller<RemoveParticipantResponseProto> {
    override fun stream(v: RemoveParticipantResponseProto): java.io.InputStream = java.io.ByteArrayInputStream(byteArrayOf())
    override fun parse(s: java.io.InputStream): RemoveParticipantResponseProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(s); var ok = false; var msg = ""
        while (!cis.isAtEnd) { val tag = cis.readTag(); if (tag == 0) break; when (com.google.protobuf.WireFormat.getTagFieldNumber(tag)) { 1 -> ok = cis.readBool(); 2 -> msg = cis.readString(); else -> cis.skipField(tag) } }
        return RemoveParticipantResponseProto(ok, msg)
    }
}

class AddContactRequestMarshaller : io.grpc.MethodDescriptor.Marshaller<AddContactRequestProto> {
    override fun stream(v: AddContactRequestProto): java.io.InputStream {
        val baos = java.io.ByteArrayOutputStream(); val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        if (v.userId.isNotEmpty()) cos.writeString(1, v.userId); if (v.contactUsername.isNotEmpty()) cos.writeString(2, v.contactUsername); if (v.username.isNotEmpty()) cos.writeString(3, v.username)
        cos.flush(); return java.io.ByteArrayInputStream(baos.toByteArray())
    }
    override fun parse(s: java.io.InputStream): AddContactRequestProto = AddContactRequestProto()
}

class AddContactResponseMarshaller : io.grpc.MethodDescriptor.Marshaller<AddContactResponseProto> {
    override fun stream(v: AddContactResponseProto): java.io.InputStream = java.io.ByteArrayInputStream(byteArrayOf())
    override fun parse(s: java.io.InputStream): AddContactResponseProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(s); var ok = false; var msg = ""
        while (!cis.isAtEnd) { val tag = cis.readTag(); if (tag == 0) break; when (com.google.protobuf.WireFormat.getTagFieldNumber(tag)) { 1 -> ok = cis.readBool(); 2 -> msg = cis.readString(); else -> cis.skipField(tag) } }
        return AddContactResponseProto(ok, msg)
    }
}

class RemoveContactRequestMarshaller : io.grpc.MethodDescriptor.Marshaller<RemoveContactRequestProto> {
    override fun stream(v: RemoveContactRequestProto): java.io.InputStream {
        val baos = java.io.ByteArrayOutputStream(); val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        if (v.userId.isNotEmpty()) cos.writeString(1, v.userId); if (v.contactUsername.isNotEmpty()) cos.writeString(2, v.contactUsername); if (v.username.isNotEmpty()) cos.writeString(3, v.username)
        cos.flush(); return java.io.ByteArrayInputStream(baos.toByteArray())
    }
    override fun parse(s: java.io.InputStream): RemoveContactRequestProto = RemoveContactRequestProto()
}

class RemoveContactResponseMarshaller : io.grpc.MethodDescriptor.Marshaller<RemoveContactResponseProto> {
    override fun stream(v: RemoveContactResponseProto): java.io.InputStream = java.io.ByteArrayInputStream(byteArrayOf())
    override fun parse(s: java.io.InputStream): RemoveContactResponseProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(s); var ok = false; var msg = ""
        while (!cis.isAtEnd) { val tag = cis.readTag(); if (tag == 0) break; when (com.google.protobuf.WireFormat.getTagFieldNumber(tag)) { 1 -> ok = cis.readBool(); 2 -> msg = cis.readString(); else -> cis.skipField(tag) } }
        return RemoveContactResponseProto(ok, msg)
    }
}

class GetContactsRequestMarshaller : io.grpc.MethodDescriptor.Marshaller<GetContactsRequestProto> {
    override fun stream(v: GetContactsRequestProto): java.io.InputStream {
        val baos = java.io.ByteArrayOutputStream(); val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        if (v.userId.isNotEmpty()) cos.writeString(1, v.userId); if (v.username.isNotEmpty()) cos.writeString(2, v.username)
        cos.flush(); return java.io.ByteArrayInputStream(baos.toByteArray())
    }
    override fun parse(s: java.io.InputStream): GetContactsRequestProto = GetContactsRequestProto()
}

class GetContactsResponseMarshaller : io.grpc.MethodDescriptor.Marshaller<GetContactsResponseProto> {
    override fun stream(v: GetContactsResponseProto): java.io.InputStream = java.io.ByteArrayInputStream(byteArrayOf())
    override fun parse(s: java.io.InputStream): GetContactsResponseProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(s); val contacts = mutableListOf<String>()
        while (!cis.isAtEnd) { val tag = cis.readTag(); if (tag == 0) break; if (com.google.protobuf.WireFormat.getTagFieldNumber(tag) == 1) contacts.add(cis.readString()) else cis.skipField(tag) }
        return GetContactsResponseProto(contacts)
    }
}

class GetAllChatsRequestMarshaller : io.grpc.MethodDescriptor.Marshaller<GetAllChatsRequestProto> {
    override fun stream(v: GetAllChatsRequestProto): java.io.InputStream = java.io.ByteArrayInputStream(byteArrayOf())
    override fun parse(s: java.io.InputStream): GetAllChatsRequestProto = GetAllChatsRequestProto()
}

class GetAllChatsResponseMarshaller : io.grpc.MethodDescriptor.Marshaller<GetAllChatsResponseProto> {
    override fun stream(v: GetAllChatsResponseProto): java.io.InputStream = java.io.ByteArrayInputStream(byteArrayOf())
    override fun parse(s: java.io.InputStream): GetAllChatsResponseProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(s); val chats = mutableListOf<ChatInfoProto>()
        while (!cis.isAtEnd) { val tag = cis.readTag(); if (tag == 0) break; if (com.google.protobuf.WireFormat.getTagFieldNumber(tag) == 1) {
                val len = cis.readUInt32(); val b = cis.readRawBytes(len); val cisis = com.google.protobuf.CodedInputStream.newInstance(b)
                var id = ""; var n = ""; var t = ""; var p = ""; var ca: Timestamp? = null; var uc = 0; var lmt: Timestamp? = null; var cr = ""; var lmtxt = ""; var au = ""; var fau = ""; var lmu = ""; var lmhi = false; var amta = false; var cst: Timestamp? = null; var isSecret = false; var peerKey = ""; var e2eeReady = false; var activeAgentId = ""; var agentMode = ""; var isPinned = false; var isMuted = false; var isArchived = false; var pinnedAt = 0L
                while (!cisis.isAtEnd) { val t2 = cisis.readTag(); if (t2 == 0) break; when (com.google.protobuf.WireFormat.getTagFieldNumber(t2)) { 1 -> id = cisis.readString(); 2 -> n = cisis.readString(); 3 -> t = cisis.readString(); 4 -> p = cisis.readString(); 5 -> { val l = cisis.readUInt32(); ca = Timestamp.parseFrom(cisis.readRawBytes(l)) }; 6 -> uc = cisis.readInt32(); 7 -> { val l = cisis.readUInt32(); lmt = Timestamp.parseFrom(cisis.readRawBytes(l)) }; 8 -> cr = cisis.readString(); 9 -> lmtxt = cisis.readString(); 10 -> au = cisis.readString(); 11 -> fau = cisis.readString(); 12 -> lmu = cisis.readString(); 13 -> lmhi = cisis.readBool(); 14 -> amta = cisis.readBool(); 15 -> isSecret = cisis.readBool(); 16 -> peerKey = cisis.readString(); 17 -> e2eeReady = cisis.readBool(); 20 -> activeAgentId = cisis.readString(); 21 -> agentMode = cisis.readString(); 22 -> isPinned = cisis.readBool(); 23 -> isMuted = cisis.readBool(); 24 -> isArchived = cisis.readBool(); 25 -> pinnedAt = cisis.readInt64(); else -> cisis.skipField(t2) } }
                chats.add(ChatInfoProto(id, n, t, p, ca, uc, lmt, cr, lmtxt, au, fau, lmu, lmhi, amta, cst, isSecret, peerKey, e2eeReady, activeAgentId, agentMode, isPinned, isMuted, isArchived, pinnedAt))
            } else cis.skipField(tag)
        }
        return GetAllChatsResponseProto(chats)
    }
}

class GetChatListVersionRequestMarshaller : io.grpc.MethodDescriptor.Marshaller<GetChatListVersionRequestProto> {
    override fun stream(v: GetChatListVersionRequestProto): java.io.InputStream {
        val baos = java.io.ByteArrayOutputStream(); val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        if (v.username.isNotEmpty()) cos.writeString(1, v.username); if (v.userId.isNotEmpty()) cos.writeString(2, v.userId)
        cos.flush(); return java.io.ByteArrayInputStream(baos.toByteArray())
    }
    override fun parse(s: java.io.InputStream): GetChatListVersionRequestProto = GetChatListVersionRequestProto()
}

class GetChatListVersionResponseMarshaller : io.grpc.MethodDescriptor.Marshaller<GetChatListVersionResponseProto> {
    override fun stream(v: GetChatListVersionResponseProto): java.io.InputStream = java.io.ByteArrayInputStream(byteArrayOf())
    override fun parse(s: java.io.InputStream): GetChatListVersionResponseProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(s); var v = 0L
        while (!cis.isAtEnd) { val tag = cis.readTag(); if (tag == 0) break; if (com.google.protobuf.WireFormat.getTagFieldNumber(tag) == 1) v = cis.readInt64() else cis.skipField(tag) }
        return GetChatListVersionResponseProto(v)
    }
}

class GetThemesRequestMarshaller : io.grpc.MethodDescriptor.Marshaller<GetThemesRequestProto> {
    override fun stream(v: GetThemesRequestProto): java.io.InputStream {
        val baos = java.io.ByteArrayOutputStream(); val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        if (v.username.isNotEmpty()) cos.writeString(1, v.username); if (v.userId.isNotEmpty()) cos.writeString(2, v.userId)
        cos.flush(); return java.io.ByteArrayInputStream(baos.toByteArray())
    }
    override fun parse(s: java.io.InputStream): GetThemesRequestProto = GetThemesRequestProto()
}

class GetThemesResponseMarshaller : io.grpc.MethodDescriptor.Marshaller<GetThemesResponseProto> {
    override fun stream(v: GetThemesResponseProto): java.io.InputStream = java.io.ByteArrayInputStream(byteArrayOf())
    override fun parse(s: java.io.InputStream): GetThemesResponseProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(s); var tid = ""; val themes = mutableListOf<CustomThemeProto>()
        while (!cis.isAtEnd) { val tag = cis.readTag(); if (tag == 0) break; when (com.google.protobuf.WireFormat.getTagFieldNumber(tag)) {
                1 -> tid = cis.readString()
                2 -> {
                    val len = cis.readUInt32(); val b = cis.readRawBytes(len); val cisis = com.google.protobuf.CodedInputStream.newInstance(b)
                    var id = ""; var name = ""; var pc = ""; var opc = ""; var sc = ""; var osc = ""; var bc = ""; var tpc = ""; var tsc = ""; var clbu = ""; var cbu = ""; var bpc = ""; var obpc = ""; var sctr = ""; var obc = ""; var ibc = ""; var idark = false
                    while (!cisis.isAtEnd) { val t2 = cisis.readTag(); if (t2 == 0) break; when (com.google.protobuf.WireFormat.getTagFieldNumber(t2)) { 1 -> id = cisis.readString(); 2 -> name = cisis.readString(); 3 -> pc = cisis.readString(); 4 -> opc = cisis.readString(); 5 -> sc = cisis.readString(); 6 -> osc = cisis.readString(); 7 -> bc = cisis.readString(); 8 -> tpc = cisis.readString(); 9 -> tsc = cisis.readString(); 10 -> idark = cisis.readBool(); 11 -> cbu = cisis.readString(); 12 -> clbu = cisis.readString(); 13 -> bpc = cisis.readString(); 14 -> obpc = cisis.readString(); 15 -> sctr = cisis.readString(); 16 -> obc = cisis.readString(); 17 -> ibc = cisis.readString(); else -> cisis.skipField(t2) } }
                    themes.add(CustomThemeProto(id, name, pc, opc, sc, osc, bc, tpc, tsc, clbu, cbu, bpc, obpc, sctr, obc, ibc))
                }
                else -> cis.skipField(tag)
            } }
        return GetThemesResponseProto(tid, themes)
    }
}

class SaveThemeRequestMarshaller : io.grpc.MethodDescriptor.Marshaller<SaveThemeRequestProto> {
    override fun stream(v: SaveThemeRequestProto): java.io.InputStream {
        val baos = java.io.ByteArrayOutputStream(); val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        if (v.username.isNotEmpty()) cos.writeString(1, v.username)
        cos.writeTag(2, com.google.protobuf.WireFormat.WIRETYPE_LENGTH_DELIMITED); val tbaos = java.io.ByteArrayOutputStream(); val tcos = com.google.protobuf.CodedOutputStream.newInstance(tbaos); val th = v.theme
        if (th.id.isNotEmpty()) tcos.writeString(1, th.id); if (th.name.isNotEmpty()) tcos.writeString(2, th.name); if (th.primaryColor.isNotEmpty()) tcos.writeString(3, th.primaryColor); if (th.onPrimaryColor.isNotEmpty()) tcos.writeString(4, th.onPrimaryColor); if (th.surfaceColor.isNotEmpty()) tcos.writeString(5, th.surfaceColor); if (th.onSurfaceColor.isNotEmpty()) tcos.writeString(6, th.onSurfaceColor); if (th.backgroundColor.isNotEmpty()) tcos.writeString(7, th.backgroundColor); if (th.textPrimaryColor.isNotEmpty()) tcos.writeString(8, th.textPrimaryColor); if (th.textSecondaryColor.isNotEmpty()) tcos.writeString(9, th.textSecondaryColor); if (th.chatBackgroundImageUrl.isNotEmpty()) tcos.writeString(11, th.chatBackgroundImageUrl); if (th.chatListBackgroundImageUrl.isNotEmpty()) tcos.writeString(12, th.chatListBackgroundImageUrl); if (th.bottomPanelColor.isNotEmpty()) tcos.writeString(13, th.bottomPanelColor); if (th.onBottomPanelColor.isNotEmpty()) tcos.writeString(14, th.onBottomPanelColor); if (th.surfaceContainer.isNotEmpty()) tcos.writeString(15, th.surfaceContainer); if (th.outgoingBubbleColor.isNotEmpty()) tcos.writeString(16, th.outgoingBubbleColor); if (th.incomingBubbleColor.isNotEmpty()) tcos.writeString(17, th.incomingBubbleColor)
        tcos.flush(); val tb = tbaos.toByteArray(); cos.writeUInt32NoTag(tb.size); cos.writeRawBytes(tb)
        if (v.userId.isNotEmpty()) cos.writeString(3, v.userId)
        cos.flush(); return java.io.ByteArrayInputStream(baos.toByteArray())
    }
    override fun parse(s: java.io.InputStream): SaveThemeRequestProto = SaveThemeRequestProto()
}

class SaveThemeResponseMarshaller : io.grpc.MethodDescriptor.Marshaller<SaveThemeResponseProto> {
    override fun stream(v: SaveThemeResponseProto): java.io.InputStream = java.io.ByteArrayInputStream(byteArrayOf())
    override fun parse(s: java.io.InputStream): SaveThemeResponseProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(s); var ok = false; var msg = ""
        while (!cis.isAtEnd) { val tag = cis.readTag(); if (tag == 0) break; when (com.google.protobuf.WireFormat.getTagFieldNumber(tag)) { 1 -> ok = cis.readBool(); 2 -> msg = cis.readString(); else -> cis.skipField(tag) } }
        return SaveThemeResponseProto(ok, msg)
    }
}

class SetCurrentThemeRequestMarshaller : io.grpc.MethodDescriptor.Marshaller<SetCurrentThemeRequestProto> {
    override fun stream(v: SetCurrentThemeRequestProto): java.io.InputStream {
        val baos = java.io.ByteArrayOutputStream(); val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        if (v.username.isNotEmpty()) cos.writeString(1, v.username); if (v.themeId.isNotEmpty()) cos.writeString(2, v.themeId); if (v.userId.isNotEmpty()) cos.writeString(3, v.userId)
        cos.flush(); return java.io.ByteArrayInputStream(baos.toByteArray())
    }
    override fun parse(s: java.io.InputStream): SetCurrentThemeRequestProto = SetCurrentThemeRequestProto()
}

class SetCurrentThemeResponseMarshaller : io.grpc.MethodDescriptor.Marshaller<SetCurrentThemeResponseProto> {
    override fun stream(v: SetCurrentThemeResponseProto): java.io.InputStream = java.io.ByteArrayInputStream(byteArrayOf())
    override fun parse(s: java.io.InputStream): SetCurrentThemeResponseProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(s); var ok = false
        while (!cis.isAtEnd) { val tag = cis.readTag(); if (tag == 0) break; if (com.google.protobuf.WireFormat.getTagFieldNumber(tag) == 1) ok = cis.readBool() else cis.skipField(tag) }
        return SetCurrentThemeResponseProto(ok)
    }
}

class DeleteThemeRequestMarshaller : io.grpc.MethodDescriptor.Marshaller<DeleteThemeRequestProto> {
    override fun stream(v: DeleteThemeRequestProto): java.io.InputStream {
        val baos = java.io.ByteArrayOutputStream(); val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        if (v.username.isNotEmpty()) cos.writeString(1, v.username); if (v.themeId.isNotEmpty()) cos.writeString(2, v.themeId); if (v.userId.isNotEmpty()) cos.writeString(3, v.userId)
        cos.flush(); return java.io.ByteArrayInputStream(baos.toByteArray())
    }
    override fun parse(s: java.io.InputStream): DeleteThemeRequestProto = DeleteThemeRequestProto()
}

class DeleteThemeResponseMarshaller : io.grpc.MethodDescriptor.Marshaller<DeleteThemeResponseProto> {
    override fun stream(v: DeleteThemeResponseProto): java.io.InputStream = java.io.ByteArrayInputStream(byteArrayOf())
    override fun parse(s: java.io.InputStream): DeleteThemeResponseProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(s); var ok = false
        while (!cis.isAtEnd) { val tag = cis.readTag(); if (tag == 0) break; if (com.google.protobuf.WireFormat.getTagFieldNumber(tag) == 1) ok = cis.readBool() else cis.skipField(tag) }
        return DeleteThemeResponseProto(ok)
    }
}

class GetFCMLogsRequestMarshaller : io.grpc.MethodDescriptor.Marshaller<GetFCMLogsRequestProto> {
    override fun stream(v: GetFCMLogsRequestProto): java.io.InputStream = java.io.ByteArrayInputStream(byteArrayOf())
    override fun parse(s: java.io.InputStream): GetFCMLogsRequestProto = GetFCMLogsRequestProto()
}

class GetFCMLogsResponseMarshaller : io.grpc.MethodDescriptor.Marshaller<GetFCMLogsResponseProto> {
    override fun stream(v: GetFCMLogsResponseProto): java.io.InputStream = java.io.ByteArrayInputStream(byteArrayOf())
    override fun parse(s: java.io.InputStream): GetFCMLogsResponseProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(s); val logs = mutableListOf<FCMLogEntryProto>()
        while (!cis.isAtEnd) { val tag = cis.readTag(); if (tag == 0) break; if (com.google.protobuf.WireFormat.getTagFieldNumber(tag) == 1) {
                val len = cis.readUInt32(); val b = cis.readRawBytes(len); val cisis = com.google.protobuf.CodedInputStream.newInstance(b)
                var ts = ""; var lvl = ""; var msg = ""
                while (!cisis.isAtEnd) { val t2 = cisis.readTag(); if (t2 == 0) break; when (com.google.protobuf.WireFormat.getTagFieldNumber(t2)) { 1 -> ts = cisis.readString(); 2 -> lvl = cisis.readString(); 3 -> msg = cisis.readString(); else -> cisis.skipField(t2) } }
                logs.add(FCMLogEntryProto(ts, lvl, msg))
            } else cis.skipField(tag) }
        return GetFCMLogsResponseProto(logs)
    }
}

class ReactionRequestMarshaller : io.grpc.MethodDescriptor.Marshaller<ReactionRequestProto> {
    override fun stream(v: ReactionRequestProto): java.io.InputStream {
        val baos = java.io.ByteArrayOutputStream(); val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        if (v.messageId.isNotEmpty()) cos.writeString(1, v.messageId)
        cos.writeTag(2, com.google.protobuf.WireFormat.WIRETYPE_LENGTH_DELIMITED); val rbaos = java.io.ByteArrayOutputStream(); val rcos = com.google.protobuf.CodedOutputStream.newInstance(rbaos)
        if (v.reaction.user.isNotEmpty()) rcos.writeString(1, v.reaction.user); if (v.reaction.emoji.isNotEmpty()) rcos.writeString(2, v.reaction.emoji)
        rcos.flush(); val rb = rbaos.toByteArray(); cos.writeUInt32NoTag(rb.size); cos.writeRawBytes(rb)
        cos.flush(); return java.io.ByteArrayInputStream(baos.toByteArray())
    }
    override fun parse(s: java.io.InputStream): ReactionRequestProto = ReactionRequestProto()
}

class ReactionResponseMarshaller : io.grpc.MethodDescriptor.Marshaller<ReactionResponseProto> {
    override fun stream(v: ReactionResponseProto): java.io.InputStream = java.io.ByteArrayInputStream(byteArrayOf())
    override fun parse(s: java.io.InputStream): ReactionResponseProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(s); var ok = false
        while (!cis.isAtEnd) { val tag = cis.readTag(); if (tag == 0) break; if (com.google.protobuf.WireFormat.getTagFieldNumber(tag) == 1) ok = cis.readBool() else cis.skipField(tag) }
        return ReactionResponseProto(ok)
    }
}

class DeleteProfileRequestMarshaller : io.grpc.MethodDescriptor.Marshaller<DeleteProfileRequestProto> {
    override fun stream(v: DeleteProfileRequestProto): java.io.InputStream {
        val baos = java.io.ByteArrayOutputStream(); val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        if (v.username.isNotEmpty()) cos.writeString(1, v.username); if (v.userId.isNotEmpty()) cos.writeString(2, v.userId)
        cos.flush(); return java.io.ByteArrayInputStream(baos.toByteArray())
    }
    override fun parse(s: java.io.InputStream): DeleteProfileRequestProto = DeleteProfileRequestProto()
}

class DeleteProfileResponseMarshaller : io.grpc.MethodDescriptor.Marshaller<DeleteProfileResponseProto> {
    override fun stream(v: DeleteProfileResponseProto): java.io.InputStream = java.io.ByteArrayInputStream(byteArrayOf())
    override fun parse(s: java.io.InputStream): DeleteProfileResponseProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(s); var ok = false; var msg = ""
        while (!cis.isAtEnd) { val tag = cis.readTag(); if (tag == 0) break; when (com.google.protobuf.WireFormat.getTagFieldNumber(tag)) { 1 -> ok = cis.readBool(); 2 -> msg = cis.readString(); else -> cis.skipField(tag) } }
        return DeleteProfileResponseProto(ok, msg)
    }
}

class UpdateChatSettingsRequestMarshaller : io.grpc.MethodDescriptor.Marshaller<UpdateChatSettingsRequestProto> {
    override fun stream(v: UpdateChatSettingsRequestProto): java.io.InputStream {
        val baos = java.io.ByteArrayOutputStream(); val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        if (v.chatId.isNotEmpty()) cos.writeString(1, v.chatId)
        cos.writeBool(2, v.allowMembersToAdd)
        if (v.userId.isNotEmpty()) cos.writeString(3, v.userId)
        cos.flush(); return java.io.ByteArrayInputStream(baos.toByteArray())
    }
    override fun parse(s: java.io.InputStream): UpdateChatSettingsRequestProto = UpdateChatSettingsRequestProto()
}

class UpdateChatSettingsResponseMarshaller : io.grpc.MethodDescriptor.Marshaller<UpdateChatSettingsResponseProto> {
    override fun stream(v: UpdateChatSettingsResponseProto): java.io.InputStream = java.io.ByteArrayInputStream(byteArrayOf())
    override fun parse(s: java.io.InputStream): UpdateChatSettingsResponseProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(s); var ok = false; var msg = ""
        while (!cis.isAtEnd) { val tag = cis.readTag(); if (tag == 0) break; when (com.google.protobuf.WireFormat.getTagFieldNumber(tag)) { 1 -> ok = cis.readBool(); 2 -> msg = cis.readString(); else -> cis.skipField(tag) } }
        return UpdateChatSettingsResponseProto(ok, msg)
    }
}

class UpdateChatNameRequestMarshaller : io.grpc.MethodDescriptor.Marshaller<UpdateChatNameRequestProto> {
    override fun stream(v: UpdateChatNameRequestProto): java.io.InputStream {
        val baos = java.io.ByteArrayOutputStream(); val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        if (v.chatId.isNotEmpty()) cos.writeString(1, v.chatId); if (v.newName.isNotEmpty()) cos.writeString(2, v.newName)
        cos.flush(); return java.io.ByteArrayInputStream(baos.toByteArray())
    }
    override fun parse(s: java.io.InputStream): UpdateChatNameRequestProto = UpdateChatNameRequestProto()
}

class UpdateChatNameResponseMarshaller : io.grpc.MethodDescriptor.Marshaller<UpdateChatNameResponseProto> {
    override fun stream(v: UpdateChatNameResponseProto): java.io.InputStream = java.io.ByteArrayInputStream(byteArrayOf())
    override fun parse(s: java.io.InputStream): UpdateChatNameResponseProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(s); var ok = false; var msg = ""
        while (!cis.isAtEnd) { val tag = cis.readTag(); if (tag == 0) break; when (com.google.protobuf.WireFormat.getTagFieldNumber(tag)) { 1 -> ok = cis.readBool(); 2 -> msg = cis.readString(); else -> cis.skipField(tag) } }
        return UpdateChatNameResponseProto(ok, msg)
    }
}

class RequestPasswordResetRequestMarshaller : io.grpc.MethodDescriptor.Marshaller<RequestPasswordResetRequestProto> {
    override fun stream(v: RequestPasswordResetRequestProto): java.io.InputStream {
        val baos = java.io.ByteArrayOutputStream(); val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        if (v.email.isNotEmpty()) cos.writeString(1, v.email)
        cos.flush(); return java.io.ByteArrayInputStream(baos.toByteArray())
    }
    override fun parse(s: java.io.InputStream): RequestPasswordResetRequestProto = RequestPasswordResetRequestProto()
}

class RequestPasswordResetResponseMarshaller : io.grpc.MethodDescriptor.Marshaller<RequestPasswordResetResponseProto> {
    override fun stream(v: RequestPasswordResetResponseProto): java.io.InputStream = java.io.ByteArrayInputStream(byteArrayOf())
    override fun parse(s: java.io.InputStream): RequestPasswordResetResponseProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(s); var ok = false; var msg = ""
        while (!cis.isAtEnd) { val tag = cis.readTag(); if (tag == 0) break; when (com.google.protobuf.WireFormat.getTagFieldNumber(tag)) { 1 -> ok = cis.readBool(); 2 -> msg = cis.readString(); else -> cis.skipField(tag) } }
        return RequestPasswordResetResponseProto(ok, msg)
    }
}

class ResetPasswordRequestMarshaller : io.grpc.MethodDescriptor.Marshaller<ResetPasswordRequestProto> {
    override fun stream(v: ResetPasswordRequestProto): java.io.InputStream {
        val baos = java.io.ByteArrayOutputStream(); val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        if (v.token.isNotEmpty()) cos.writeString(1, v.token)
        if (v.newPassword.isNotEmpty()) cos.writeString(2, v.newPassword)
        cos.flush(); return java.io.ByteArrayInputStream(baos.toByteArray())
    }
    override fun parse(s: java.io.InputStream): ResetPasswordRequestProto = ResetPasswordRequestProto()
}

class GetDevicesRequestMarshaller : io.grpc.MethodDescriptor.Marshaller<GetDevicesRequestProto> {
    override fun stream(v: GetDevicesRequestProto): java.io.InputStream {
        val baos = java.io.ByteArrayOutputStream(); val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        if (v.userId.isNotEmpty()) cos.writeString(1, v.userId)
        cos.flush(); return java.io.ByteArrayInputStream(baos.toByteArray())
    }
    override fun parse(s: java.io.InputStream): GetDevicesRequestProto = GetDevicesRequestProto()
}

class GetDevicesResponseMarshaller : io.grpc.MethodDescriptor.Marshaller<GetDevicesResponseProto> {
    override fun stream(v: GetDevicesResponseProto): java.io.InputStream = java.io.ByteArrayInputStream(byteArrayOf())
    override fun parse(s: java.io.InputStream): GetDevicesResponseProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(s); val devices = mutableListOf<DeviceInfoProto>()
        while (!cis.isAtEnd) {
            val tag = cis.readTag(); if (tag == 0) break
            if (com.google.protobuf.WireFormat.getTagFieldNumber(tag) == 1) {
                val len = cis.readUInt32(); val b = cis.readRawBytes(len); val cisis = com.google.protobuf.CodedInputStream.newInstance(b)
                var id = ""; var name = ""; var cv = ""; var ts: Timestamp? = null; var ip = ""
                while (!cisis.isAtEnd) {
                    val t2 = cisis.readTag(); if (t2 == 0) break
                    when (com.google.protobuf.WireFormat.getTagFieldNumber(t2)) {
                        1 -> id = cisis.readString(); 2 -> name = cisis.readString(); 3 -> cv = cisis.readString()
                        4 -> { val l2 = cisis.readUInt32(); ts = Timestamp.parseFrom(cisis.readRawBytes(l2)) }
                        5 -> ip = cisis.readString(); else -> cisis.skipField(t2)
                    }
                }
                devices.add(DeviceInfoProto(id, name, cv, ts, ip))
            } else cis.skipField(tag)
        }
        return GetDevicesResponseProto(devices)
    }
}

class DeleteDeviceRequestMarshaller : io.grpc.MethodDescriptor.Marshaller<DeleteDeviceRequestProto> {
    override fun stream(v: DeleteDeviceRequestProto): java.io.InputStream {
        val baos = java.io.ByteArrayOutputStream(); val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        if (v.userId.isNotEmpty()) cos.writeString(1, v.userId); if (v.deviceId.isNotEmpty()) cos.writeString(2, v.deviceId)
        cos.flush(); return java.io.ByteArrayInputStream(baos.toByteArray())
    }
    override fun parse(s: java.io.InputStream): DeleteDeviceRequestProto = DeleteDeviceRequestProto()
}

class DeleteDeviceResponseMarshaller : io.grpc.MethodDescriptor.Marshaller<DeleteDeviceResponseProto> {
    override fun stream(v: DeleteDeviceResponseProto): java.io.InputStream = java.io.ByteArrayInputStream(byteArrayOf())
    override fun parse(s: java.io.InputStream): DeleteDeviceResponseProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(s); var ok = false; var msg = ""
        while (!cis.isAtEnd) {
            val tag = cis.readTag(); if (tag == 0) break
            when (com.google.protobuf.WireFormat.getTagFieldNumber(tag)) {
                1 -> ok = cis.readBool(); 2 -> msg = cis.readString(); else -> cis.skipField(tag)
            }
        }
        return DeleteDeviceResponseProto(ok, msg)
    }
}

class ResetPasswordResponseMarshaller : io.grpc.MethodDescriptor.Marshaller<ResetPasswordResponseProto> {
    override fun stream(v: ResetPasswordResponseProto): java.io.InputStream = java.io.ByteArrayInputStream(byteArrayOf())
    override fun parse(s: java.io.InputStream): ResetPasswordResponseProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(s); var ok = false; var msg = ""
        while (!cis.isAtEnd) { val tag = cis.readTag(); if (tag == 0) break; when (com.google.protobuf.WireFormat.getTagFieldNumber(tag)) { 1 -> ok = cis.readBool(); 2 -> msg = cis.readString(); else -> cis.skipField(tag) } }
        return ResetPasswordResponseProto(ok, msg)
    }
}

class AdminUpdatePasswordRequestMarshaller : io.grpc.MethodDescriptor.Marshaller<AdminUpdatePasswordRequestProto> {
    override fun stream(v: AdminUpdatePasswordRequestProto): java.io.InputStream {
        val baos = java.io.ByteArrayOutputStream(); val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        if (v.targetUsername.isNotEmpty()) cos.writeString(1, v.targetUsername)
        if (v.newPassword.isNotEmpty()) cos.writeString(2, v.newPassword)
        if (v.adminUsername.isNotEmpty()) cos.writeString(3, v.adminUsername)
        if (v.adminUserId.isNotEmpty()) cos.writeString(4, v.adminUserId)
        cos.flush(); return java.io.ByteArrayInputStream(baos.toByteArray())
    }
    override fun parse(s: java.io.InputStream): AdminUpdatePasswordRequestProto = AdminUpdatePasswordRequestProto()
}

class AdminUpdatePasswordResponseMarshaller : io.grpc.MethodDescriptor.Marshaller<AdminUpdatePasswordResponseProto> {
    override fun stream(v: AdminUpdatePasswordResponseProto): java.io.InputStream = java.io.ByteArrayInputStream(byteArrayOf())
    override fun parse(s: java.io.InputStream): AdminUpdatePasswordResponseProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(s); var ok = false; var msg = ""
        while (!cis.isAtEnd) { val tag = cis.readTag(); if (tag == 0) break; when (com.google.protobuf.WireFormat.getTagFieldNumber(tag)) { 1 -> ok = cis.readBool(); 2 -> msg = cis.readString(); else -> cis.skipField(tag) } }
        return AdminUpdatePasswordResponseProto(ok, msg)
    }
}

class CallMessageProtoMarshaller : io.grpc.MethodDescriptor.Marshaller<CallMessageProto> {
    override fun stream(v: CallMessageProto): java.io.InputStream {
        val baos = java.io.ByteArrayOutputStream(); val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        if (v.callId.isNotEmpty()) cos.writeString(1, v.callId)
        if (v.senderId.isNotEmpty()) cos.writeString(2, v.senderId)
        if (v.receiverId.isNotEmpty()) cos.writeString(3, v.receiverId)
        cos.writeEnum(4, v.type.value)
        if (v.payload.isNotEmpty()) cos.writeString(5, v.payload)
        if (v.senderName.isNotEmpty()) cos.writeString(6, v.senderName)
        if (v.receiverName.isNotEmpty()) cos.writeString(7, v.receiverName)
        if (v.roomId.isNotEmpty()) cos.writeString(8, v.roomId)
        cos.flush(); return java.io.ByteArrayInputStream(baos.toByteArray())
    }
    override fun parse(s: java.io.InputStream): CallMessageProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(s)
        var cid = ""; var sid = ""; var rid = ""; var t = 0; var p = ""; var sn = ""; var rn = ""; var rm = ""
        while (!cis.isAtEnd) {
            val tag = cis.readTag(); if (tag == 0) break
            when (com.google.protobuf.WireFormat.getTagFieldNumber(tag)) {
                1 -> cid = cis.readString()
                2 -> sid = cis.readString()
                3 -> rid = cis.readString()
                4 -> t = cis.readEnum()
                5 -> p = cis.readString()
                6 -> sn = cis.readString()
                7 -> rn = cis.readString()
                8 -> rm = cis.readString()
                else -> cis.skipField(tag)
            }
        }
        val result = CallMessageProto(cid, sid, rid, CallMessageProto.Type.fromInt(t), p, sn, rn, rm)
        return result
    }

    // Flag to control E2EE support based on client version
    var isE2EEMessageEnabled: Boolean = true
}

// ======= Auth V2 Marshallers =======

class SignInRequestV2Marshaller : io.grpc.MethodDescriptor.Marshaller<SignInRequestV2Proto> {
    override fun stream(v: SignInRequestV2Proto): java.io.InputStream {
        val baos = java.io.ByteArrayOutputStream()
        val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        if (v.username.isNotEmpty()) cos.writeString(1, v.username)
        if (v.password.isNotEmpty()) cos.writeString(2, v.password)
        if (v.deviceId.isNotEmpty() || v.deviceName.isNotEmpty()) {
            val deviceBytes = java.io.ByteArrayOutputStream()
            val deviceCos = com.google.protobuf.CodedOutputStream.newInstance(deviceBytes)
            if (v.deviceId.isNotEmpty()) deviceCos.writeString(1, v.deviceId)
            if (v.deviceName.isNotEmpty()) deviceCos.writeString(2, v.deviceName)
            deviceCos.flush()
            cos.writeByteArray(3, deviceBytes.toByteArray())
        }
        if (v.clientVersion.isNotEmpty()) cos.writeString(4, v.clientVersion)
        cos.flush()
        return java.io.ByteArrayInputStream(baos.toByteArray())
    }
    override fun parse(s: java.io.InputStream): SignInRequestV2Proto = SignInRequestV2Proto()
}

class AuthResponseV2Marshaller : io.grpc.MethodDescriptor.Marshaller<AuthResponseV2Proto> {
    override fun stream(v: AuthResponseV2Proto): java.io.InputStream = java.io.ByteArrayInputStream(byteArrayOf())
    override fun parse(s: java.io.InputStream): AuthResponseV2Proto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(s)
        var success = false
        var message = ""
        var accessToken = ""
        var refreshToken = ""
        var accessExpiresAt = 0L
        var refreshExpiresAt = 0L
        var userId = ""
        var username = ""
        var email = ""
        var avatarUrl = ""
        var bio = ""
        var status = ""
        while (!cis.isAtEnd) {
            val tag = cis.readTag()
            if (tag == 0) break
            when (com.google.protobuf.WireFormat.getTagFieldNumber(tag)) {
                1 -> success = cis.readBool()
                2 -> message = cis.readString()
                3 -> accessToken = cis.readString()
                4 -> refreshToken = cis.readString()
                5 -> accessExpiresAt = cis.readInt64()
                6 -> refreshExpiresAt = cis.readInt64()
                7 -> {
                    val userLen = cis.readRawVarint32()
                    val userBytes = cis.readRawBytes(userLen)
                    val userCis = com.google.protobuf.CodedInputStream.newInstance(userBytes)
                    while (!userCis.isAtEnd) {
                        val utag = userCis.readTag()
                        if (utag == 0) break
                        when (com.google.protobuf.WireFormat.getTagFieldNumber(utag)) {
                            1 -> userId = userCis.readString()
                            2 -> username = userCis.readString()
                            3 -> email = userCis.readString()
                            5 -> avatarUrl = userCis.readString()
                            6 -> bio = userCis.readString()
                            7 -> status = userCis.readString()
                            else -> userCis.skipField(utag)
                        }
                    }
                }
                else -> cis.skipField(tag)
            }
        }
        return AuthResponseV2Proto(
            success = success, message = message,
            accessToken = accessToken, refreshToken = refreshToken,
            accessExpiresAt = accessExpiresAt, refreshExpiresAt = refreshExpiresAt,
            userId = userId, username = username, email = email,
            avatarUrl = avatarUrl, bio = bio, status = status
        )
    }
}

class SignUpRequestV2Marshaller : io.grpc.MethodDescriptor.Marshaller<SignUpRequestV2Proto> {
    override fun stream(v: SignUpRequestV2Proto): java.io.InputStream {
        val baos = java.io.ByteArrayOutputStream()
        val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        if (v.username.isNotEmpty()) cos.writeString(1, v.username)
        if (v.password.isNotEmpty()) cos.writeString(2, v.password)
        if (v.email.isNotEmpty()) cos.writeString(3, v.email)
        if (v.deviceId.isNotEmpty() || v.deviceName.isNotEmpty()) {
            val deviceBytes = java.io.ByteArrayOutputStream()
            val deviceCos = com.google.protobuf.CodedOutputStream.newInstance(deviceBytes)
            if (v.deviceId.isNotEmpty()) deviceCos.writeString(1, v.deviceId)
            if (v.deviceName.isNotEmpty()) deviceCos.writeString(2, v.deviceName)
            deviceCos.flush()
            cos.writeByteArray(4, deviceBytes.toByteArray())
        }
        if (v.clientVersion.isNotEmpty()) cos.writeString(5, v.clientVersion)
        cos.flush()
        return java.io.ByteArrayInputStream(baos.toByteArray())
    }
    override fun parse(s: java.io.InputStream): SignUpRequestV2Proto = SignUpRequestV2Proto()
}

class RefreshTokenRequestMarshaller : io.grpc.MethodDescriptor.Marshaller<RefreshTokenRequestProto> {
    override fun stream(v: RefreshTokenRequestProto): java.io.InputStream {
        val baos = java.io.ByteArrayOutputStream()
        val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        if (v.refreshToken.isNotEmpty()) cos.writeString(1, v.refreshToken)
        cos.flush()
        return java.io.ByteArrayInputStream(baos.toByteArray())
    }
    override fun parse(s: java.io.InputStream): RefreshTokenRequestProto = RefreshTokenRequestProto()
}

class RefreshTokenResponseMarshaller : io.grpc.MethodDescriptor.Marshaller<RefreshTokenResponseProto> {
    override fun stream(v: RefreshTokenResponseProto): java.io.InputStream = java.io.ByteArrayInputStream(byteArrayOf())
    override fun parse(s: java.io.InputStream): RefreshTokenResponseProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(s)
        var accessToken = ""
        var refreshToken = ""
        var accessExpiresAt = 0L
        var refreshExpiresAt = 0L
        while (!cis.isAtEnd) {
            val tag = cis.readTag()
            if (tag == 0) break
            when (com.google.protobuf.WireFormat.getTagFieldNumber(tag)) {
                1 -> accessToken = cis.readString()
                2 -> refreshToken = cis.readString()
                3 -> accessExpiresAt = cis.readInt64()
                4 -> refreshExpiresAt = cis.readInt64()
                else -> cis.skipField(tag)
            }
        }
        return RefreshTokenResponseProto(
            accessToken = accessToken, refreshToken = refreshToken,
            accessExpiresAt = accessExpiresAt, refreshExpiresAt = refreshExpiresAt
        )
    }
}

class SignOutRequestMarshaller : io.grpc.MethodDescriptor.Marshaller<SignOutRequestProto> {
    override fun stream(v: SignOutRequestProto): java.io.InputStream {
        val baos = java.io.ByteArrayOutputStream()
        val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        if (v.refreshToken.isNotEmpty()) cos.writeString(1, v.refreshToken)
        if (v.allDevices) cos.writeBool(2, v.allDevices)
        cos.flush()
        return java.io.ByteArrayInputStream(baos.toByteArray())
    }
    override fun parse(s: java.io.InputStream): SignOutRequestProto = SignOutRequestProto()
}

class SimpleAuthResponseMarshaller : io.grpc.MethodDescriptor.Marshaller<SimpleAuthResponseProto> {
    override fun stream(v: SimpleAuthResponseProto): java.io.InputStream = java.io.ByteArrayInputStream(byteArrayOf())
    override fun parse(s: java.io.InputStream): SimpleAuthResponseProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(s)
        var success = false
        var message = ""
        while (!cis.isAtEnd) {
            val tag = cis.readTag()
            if (tag == 0) break
            when (com.google.protobuf.WireFormat.getTagFieldNumber(tag)) {
                1 -> success = cis.readBool()
                2 -> message = cis.readString()
                else -> cis.skipField(tag)
            }
        }
        return SimpleAuthResponseProto(success = success, message = message)
    }
}

class RevokeDeviceRequestMarshaller : io.grpc.MethodDescriptor.Marshaller<RevokeDeviceRequestProto> {
    override fun stream(v: RevokeDeviceRequestProto): java.io.InputStream {
        val baos = java.io.ByteArrayOutputStream()
        val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        if (v.deviceId.isNotEmpty()) cos.writeString(1, v.deviceId)
        cos.flush()
        return java.io.ByteArrayInputStream(baos.toByteArray())
    }
    override fun parse(s: java.io.InputStream): RevokeDeviceRequestProto = RevokeDeviceRequestProto()
}

// ======= ChatList v2 marshallers =======

class PinChatRequestMarshaller : io.grpc.MethodDescriptor.Marshaller<PinChatRequestProto> {
    override fun stream(v: PinChatRequestProto): java.io.InputStream {
        val baos = java.io.ByteArrayOutputStream(); val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        if (v.chatId.isNotEmpty()) cos.writeString(1, v.chatId); if (v.userId.isNotEmpty()) cos.writeString(2, v.userId)
        cos.flush(); return java.io.ByteArrayInputStream(baos.toByteArray())
    }
    override fun parse(s: java.io.InputStream): PinChatRequestProto = PinChatRequestProto()
}

class UnPinChatRequestMarshaller : io.grpc.MethodDescriptor.Marshaller<UnPinChatRequestProto> {
    override fun stream(v: UnPinChatRequestProto): java.io.InputStream {
        val baos = java.io.ByteArrayOutputStream(); val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        if (v.chatId.isNotEmpty()) cos.writeString(1, v.chatId); if (v.userId.isNotEmpty()) cos.writeString(2, v.userId)
        cos.flush(); return java.io.ByteArrayInputStream(baos.toByteArray())
    }
    override fun parse(s: java.io.InputStream): UnPinChatRequestProto = UnPinChatRequestProto()
}

class SearchChatsRequestMarshaller : io.grpc.MethodDescriptor.Marshaller<SearchChatsRequestProto> {
    override fun stream(v: SearchChatsRequestProto): java.io.InputStream {
        val baos = java.io.ByteArrayOutputStream(); val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        if (v.userId.isNotEmpty()) cos.writeString(1, v.userId); if (v.query.isNotEmpty()) cos.writeString(2, v.query)
        if (v.limit != 20) cos.writeInt32(3, v.limit); if (v.offset != 0) cos.writeInt32(4, v.offset)
        cos.flush(); return java.io.ByteArrayInputStream(baos.toByteArray())
    }
    override fun parse(s: java.io.InputStream): SearchChatsRequestProto = SearchChatsRequestProto()
}

class SearchChatsResponseMarshaller : io.grpc.MethodDescriptor.Marshaller<SearchChatsResponseProto> {
    override fun stream(v: SearchChatsResponseProto): java.io.InputStream = java.io.ByteArrayInputStream(byteArrayOf())
    override fun parse(s: java.io.InputStream): SearchChatsResponseProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(s); val chats = mutableListOf<ChatInfoProto>()
        while (!cis.isAtEnd) { val tag = cis.readTag(); if (tag == 0) break; if (com.google.protobuf.WireFormat.getTagFieldNumber(tag) == 1) {
            val len = cis.readUInt32(); val b = cis.readRawBytes(len); val cisis = com.google.protobuf.CodedInputStream.newInstance(b)
            var id = ""; var n = ""; var t = ""; var p = ""; var ca: Timestamp? = null; var uc = 0; var lmt: Timestamp? = null; var cr = ""; var lmtxt = ""; var au = ""; var fau = ""; var lmu = ""; var lmhi = false; var amta = false; var cst: Timestamp? = null; var isSecret = false; var peerKey = ""; var e2eeReady = false; var activeAgentId = ""; var agentMode = ""; var isPinned = false; var isMuted = false; var isArchived = false; var pinnedAt = 0L
            while (!cisis.isAtEnd) { val t2 = cisis.readTag(); if (t2 == 0) break; when (com.google.protobuf.WireFormat.getTagFieldNumber(t2)) { 1 -> id = cisis.readString(); 2 -> n = cisis.readString(); 3 -> t = cisis.readString(); 4 -> p = cisis.readString(); 5 -> { val l = cisis.readUInt32(); ca = Timestamp.parseFrom(cisis.readRawBytes(l)) }; 6 -> uc = cisis.readInt32(); 7 -> { val l = cisis.readUInt32(); lmt = Timestamp.parseFrom(cisis.readRawBytes(l)) }; 8 -> cr = cisis.readString(); 9 -> lmtxt = cisis.readString(); 10 -> au = cisis.readString(); 11 -> fau = cisis.readString(); 12 -> lmu = cisis.readString(); 13 -> lmhi = cisis.readBool(); 14 -> amta = cisis.readBool(); 15 -> isSecret = cisis.readBool(); 16 -> peerKey = cisis.readString(); 17 -> e2eeReady = cisis.readBool(); 20 -> activeAgentId = cisis.readString(); 21 -> agentMode = cisis.readString(); 22 -> isPinned = cisis.readBool(); 23 -> isMuted = cisis.readBool(); 24 -> isArchived = cisis.readBool(); 25 -> pinnedAt = cisis.readInt64(); else -> cisis.skipField(t2) } }
            chats.add(ChatInfoProto(id, n, t, p, ca, uc, lmt, cr, lmtxt, au, fau, lmu, lmhi, amta, cst, isSecret, peerKey, e2eeReady, activeAgentId, agentMode, isPinned, isMuted, isArchived, pinnedAt))
        } else cis.skipField(tag) }
        return SearchChatsResponseProto(chats)
    }
}

class ArchiveChatRequestMarshaller : io.grpc.MethodDescriptor.Marshaller<ArchiveChatRequestProto> {
    override fun stream(v: ArchiveChatRequestProto): java.io.InputStream {
        val baos = java.io.ByteArrayOutputStream(); val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        if (v.chatId.isNotEmpty()) cos.writeString(1, v.chatId); if (v.userId.isNotEmpty()) cos.writeString(2, v.userId)
        cos.flush(); return java.io.ByteArrayInputStream(baos.toByteArray())
    }
    override fun parse(s: java.io.InputStream): ArchiveChatRequestProto = ArchiveChatRequestProto()
}

class UnarchiveChatRequestMarshaller : io.grpc.MethodDescriptor.Marshaller<UnarchiveChatRequestProto> {
    override fun stream(v: UnarchiveChatRequestProto): java.io.InputStream {
        val baos = java.io.ByteArrayOutputStream(); val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        if (v.chatId.isNotEmpty()) cos.writeString(1, v.chatId); if (v.userId.isNotEmpty()) cos.writeString(2, v.userId)
        cos.flush(); return java.io.ByteArrayInputStream(baos.toByteArray())
    }
    override fun parse(s: java.io.InputStream): UnarchiveChatRequestProto = UnarchiveChatRequestProto()
}

class PinMessageRequestMarshaller : io.grpc.MethodDescriptor.Marshaller<PinMessageRequestProto> {
    override fun stream(v: PinMessageRequestProto): java.io.InputStream {
        val baos = java.io.ByteArrayOutputStream(); val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        if (v.messageId.isNotEmpty()) cos.writeString(1, v.messageId); if (v.chatId.isNotEmpty()) cos.writeString(2, v.chatId); if (v.userId.isNotEmpty()) cos.writeString(3, v.userId)
        cos.flush(); return java.io.ByteArrayInputStream(baos.toByteArray())
    }
    override fun parse(s: java.io.InputStream): PinMessageRequestProto = PinMessageRequestProto()
}

class UnPinMessageRequestMarshaller : io.grpc.MethodDescriptor.Marshaller<UnPinMessageRequestProto> {
    override fun stream(v: UnPinMessageRequestProto): java.io.InputStream {
        val baos = java.io.ByteArrayOutputStream(); val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        if (v.messageId.isNotEmpty()) cos.writeString(1, v.messageId); if (v.chatId.isNotEmpty()) cos.writeString(2, v.chatId); if (v.userId.isNotEmpty()) cos.writeString(3, v.userId)
        cos.flush(); return java.io.ByteArrayInputStream(baos.toByteArray())
    }
    override fun parse(s: java.io.InputStream): UnPinMessageRequestProto = UnPinMessageRequestProto()
}

class GetPinnedMessagesRequestMarshaller : io.grpc.MethodDescriptor.Marshaller<GetPinnedMessagesRequestProto> {
    override fun stream(v: GetPinnedMessagesRequestProto): java.io.InputStream {
        val baos = java.io.ByteArrayOutputStream(); val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        if (v.chatId.isNotEmpty()) cos.writeString(1, v.chatId); if (v.userId.isNotEmpty()) cos.writeString(2, v.userId)
        cos.flush(); return java.io.ByteArrayInputStream(baos.toByteArray())
    }
    override fun parse(s: java.io.InputStream): GetPinnedMessagesRequestProto = GetPinnedMessagesRequestProto()
}

class BoolResponseMarshaller : io.grpc.MethodDescriptor.Marshaller<PinChatResponseProto> {
    override fun stream(v: PinChatResponseProto): java.io.InputStream = java.io.ByteArrayInputStream(byteArrayOf())
    override fun parse(s: java.io.InputStream): PinChatResponseProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(s); var ok = false
        while (!cis.isAtEnd) { val tag = cis.readTag(); if (tag == 0) break; if (com.google.protobuf.WireFormat.getTagFieldNumber(tag) == 1) ok = cis.readBool() else cis.skipField(tag) }
        return PinChatResponseProto(ok)
    }
}

// ======= ProfileService V2 Marshallers =======

class GetProfileRequestMarshaller : io.grpc.MethodDescriptor.Marshaller<GetProfileRequestProto> {
    override fun stream(v: GetProfileRequestProto): java.io.InputStream = java.io.ByteArrayInputStream(byteArrayOf())
    override fun parse(s: java.io.InputStream): GetProfileRequestProto = GetProfileRequestProto()
}

class GetProfileResponseMarshaller : io.grpc.MethodDescriptor.Marshaller<GetProfileResponseProto> {
    override fun stream(v: GetProfileResponseProto): java.io.InputStream = java.io.ByteArrayInputStream(byteArrayOf())
    override fun parse(s: java.io.InputStream): GetProfileResponseProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(s)
        var userId = ""; var username = ""; var email = ""; var avatarUrl = ""; var fullAvatarUrl = ""
        var bio = ""; var status = ""; var locale = "en"; var isSuperAdmin = false; var createdAt = ""; var lastSeenAt = ""
        while (!cis.isAtEnd) {
            val tag = cis.readTag(); if (tag == 0) break
            when (com.google.protobuf.WireFormat.getTagFieldNumber(tag)) {
                1 -> userId = cis.readString(); 2 -> username = cis.readString(); 3 -> email = cis.readString()
                4 -> avatarUrl = cis.readString(); 5 -> fullAvatarUrl = cis.readString()
                6 -> bio = cis.readString(); 7 -> status = cis.readString(); 8 -> locale = cis.readString()
                9 -> isSuperAdmin = cis.readBool(); 10 -> createdAt = cis.readString(); 11 -> lastSeenAt = cis.readString()
                else -> cis.skipField(tag)
            }
        }
        return GetProfileResponseProto(userId, username, email, avatarUrl, fullAvatarUrl, bio, status, locale, isSuperAdmin, createdAt, lastSeenAt)
    }
}

class UpdateProfileV2RequestMarshaller : io.grpc.MethodDescriptor.Marshaller<UpdateProfileV2RequestProto> {
    override fun stream(v: UpdateProfileV2RequestProto): java.io.InputStream {
        val baos = java.io.ByteArrayOutputStream(); val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        if (v.username.isNotEmpty()) cos.writeString(1, v.username)
        if (v.bio.isNotEmpty()) cos.writeString(2, v.bio)
        if (v.status.isNotEmpty()) cos.writeString(3, v.status)
        if (v.locale.isNotEmpty()) cos.writeString(4, v.locale)
        cos.flush(); return java.io.ByteArrayInputStream(baos.toByteArray())
    }
    override fun parse(s: java.io.InputStream): UpdateProfileV2RequestProto = UpdateProfileV2RequestProto()
}

class UpdateProfileV2ResponseMarshaller : io.grpc.MethodDescriptor.Marshaller<UpdateProfileV2ResponseProto> {
    override fun stream(v: UpdateProfileV2ResponseProto): java.io.InputStream = java.io.ByteArrayInputStream(byteArrayOf())
    override fun parse(s: java.io.InputStream): UpdateProfileV2ResponseProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(s)
        var success = false; var message = ""; var profile: GetProfileResponseProto? = null
        while (!cis.isAtEnd) {
            val tag = cis.readTag(); if (tag == 0) break
            when (com.google.protobuf.WireFormat.getTagFieldNumber(tag)) {
                1 -> success = cis.readBool(); 2 -> message = cis.readString()
                3 -> { val len = cis.readUInt32(); profile = GetProfileResponseMarshaller().parse(java.io.ByteArrayInputStream(cis.readRawBytes(len))) }
                else -> cis.skipField(tag)
            }
        }
        return UpdateProfileV2ResponseProto(success, message, profile)
    }
}

class UpdateAvatarV2RequestMarshaller : io.grpc.MethodDescriptor.Marshaller<UpdateAvatarV2RequestProto> {
    override fun stream(v: UpdateAvatarV2RequestProto): java.io.InputStream {
        val baos = java.io.ByteArrayOutputStream(); val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        if (v.avatarUrl.isNotEmpty()) cos.writeString(1, v.avatarUrl)
        if (v.fullAvatarUrl.isNotEmpty()) cos.writeString(2, v.fullAvatarUrl)
        cos.flush(); return java.io.ByteArrayInputStream(baos.toByteArray())
    }
    override fun parse(s: java.io.InputStream): UpdateAvatarV2RequestProto = UpdateAvatarV2RequestProto()
}

class UpdateAvatarV2ResponseMarshaller : io.grpc.MethodDescriptor.Marshaller<UpdateAvatarV2ResponseProto> {
    override fun stream(v: UpdateAvatarV2ResponseProto): java.io.InputStream = java.io.ByteArrayInputStream(byteArrayOf())
    override fun parse(s: java.io.InputStream): UpdateAvatarV2ResponseProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(s)
        var success = false; var message = ""; var avatarUrl = ""; var fullAvatarUrl = ""
        while (!cis.isAtEnd) {
            val tag = cis.readTag(); if (tag == 0) break
            when (com.google.protobuf.WireFormat.getTagFieldNumber(tag)) {
                1 -> success = cis.readBool(); 2 -> message = cis.readString()
                3 -> avatarUrl = cis.readString(); 4 -> fullAvatarUrl = cis.readString()
                else -> cis.skipField(tag)
            }
        }
        return UpdateAvatarV2ResponseProto(success, message, avatarUrl, fullAvatarUrl)
    }
}

class GetUserSettingsRequestMarshaller : io.grpc.MethodDescriptor.Marshaller<GetUserSettingsRequestProto> {
    override fun stream(v: GetUserSettingsRequestProto): java.io.InputStream = java.io.ByteArrayInputStream(byteArrayOf())
    override fun parse(s: java.io.InputStream): GetUserSettingsRequestProto = GetUserSettingsRequestProto()
}

class GetUserSettingsResponseMarshaller : io.grpc.MethodDescriptor.Marshaller<GetUserSettingsResponseProto> {
    override fun stream(v: GetUserSettingsResponseProto): java.io.InputStream = java.io.ByteArrayInputStream(byteArrayOf())
    override fun parse(s: java.io.InputStream): GetUserSettingsResponseProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(s)
        var locale = "en"; var themeId = ""; var pushEnabled = true; val custom = mutableMapOf<String, String>()
        while (!cis.isAtEnd) {
            val tag = cis.readTag(); if (tag == 0) break
            when (com.google.protobuf.WireFormat.getTagFieldNumber(tag)) {
                1 -> locale = cis.readString(); 2 -> themeId = cis.readString(); 3 -> pushEnabled = cis.readBool()
                4 -> { val len = cis.readUInt32(); val b = cis.readRawBytes(len); val mcis = com.google.protobuf.CodedInputStream.newInstance(b)
                    var mk = ""; var mv = ""
                    while (!mcis.isAtEnd) { val mtag = mcis.readTag(); if (mtag == 0) break; when (com.google.protobuf.WireFormat.getTagFieldNumber(mtag)) { 1 -> mk = mcis.readString(); 2 -> mv = mcis.readString(); else -> mcis.skipField(mtag) } }
                    if (mk.isNotEmpty()) custom[mk] = mv }
                else -> cis.skipField(tag)
            }
        }
        return GetUserSettingsResponseProto(locale, themeId, pushEnabled, custom)
    }
}

class UpdateUserSettingsRequestMarshaller : io.grpc.MethodDescriptor.Marshaller<UpdateUserSettingsRequestProto> {
    override fun stream(v: UpdateUserSettingsRequestProto): java.io.InputStream {
        val baos = java.io.ByteArrayOutputStream(); val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        if (v.locale.isNotEmpty()) cos.writeString(1, v.locale)
        if (v.themeId.isNotEmpty()) cos.writeString(2, v.themeId)
        v.pushEnabled?.let { cos.writeBool(3, it) }
        cos.flush(); return java.io.ByteArrayInputStream(baos.toByteArray())
    }
    override fun parse(s: java.io.InputStream): UpdateUserSettingsRequestProto = UpdateUserSettingsRequestProto()
}

class UpdateUserSettingsResponseMarshaller : io.grpc.MethodDescriptor.Marshaller<UpdateUserSettingsResponseProto> {
    override fun stream(v: UpdateUserSettingsResponseProto): java.io.InputStream = java.io.ByteArrayInputStream(byteArrayOf())
    override fun parse(s: java.io.InputStream): UpdateUserSettingsResponseProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(s); var success = false; var message = ""
        while (!cis.isAtEnd) { val tag = cis.readTag(); if (tag == 0) break; when (com.google.protobuf.WireFormat.getTagFieldNumber(tag)) { 1 -> success = cis.readBool(); 2 -> message = cis.readString(); else -> cis.skipField(tag) } }
        return UpdateUserSettingsResponseProto(success, message)
    }
}

class DeleteProfileV2RequestMarshaller : io.grpc.MethodDescriptor.Marshaller<DeleteProfileV2RequestProto> {
    override fun stream(v: DeleteProfileV2RequestProto): java.io.InputStream {
        val baos = java.io.ByteArrayOutputStream(); val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        if (v.password.isNotEmpty()) cos.writeString(1, v.password)
        cos.flush(); return java.io.ByteArrayInputStream(baos.toByteArray())
    }
    override fun parse(s: java.io.InputStream): DeleteProfileV2RequestProto = DeleteProfileV2RequestProto()
}

class DeleteProfileV2ResponseMarshaller : io.grpc.MethodDescriptor.Marshaller<DeleteProfileV2ResponseProto> {
    override fun stream(v: DeleteProfileV2ResponseProto): java.io.InputStream = java.io.ByteArrayInputStream(byteArrayOf())
    override fun parse(s: java.io.InputStream): DeleteProfileV2ResponseProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(s); var success = false; var message = ""
        while (!cis.isAtEnd) { val tag = cis.readTag(); if (tag == 0) break; when (com.google.protobuf.WireFormat.getTagFieldNumber(tag)) { 1 -> success = cis.readBool(); 2 -> message = cis.readString(); else -> cis.skipField(tag) } }
        return DeleteProfileV2ResponseProto(success, message)
    }
}

// ======= ChatList V2 Boolean Response Marshallers =======

class PinChatResponseMarshaller : io.grpc.MethodDescriptor.Marshaller<PinChatResponseProto> {
    override fun stream(v: PinChatResponseProto): java.io.InputStream = java.io.ByteArrayInputStream(byteArrayOf())
    override fun parse(s: java.io.InputStream): PinChatResponseProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(s); var ok = false
        while (!cis.isAtEnd) { val tag = cis.readTag(); if (tag == 0) break; if (com.google.protobuf.WireFormat.getTagFieldNumber(tag) == 1) ok = cis.readBool() else cis.skipField(tag) }
        return PinChatResponseProto(ok)
    }
}

class UnPinChatResponseMarshaller : io.grpc.MethodDescriptor.Marshaller<UnPinChatResponseProto> {
    override fun stream(v: UnPinChatResponseProto): java.io.InputStream = java.io.ByteArrayInputStream(byteArrayOf())
    override fun parse(s: java.io.InputStream): UnPinChatResponseProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(s); var ok = false
        while (!cis.isAtEnd) { val tag = cis.readTag(); if (tag == 0) break; if (com.google.protobuf.WireFormat.getTagFieldNumber(tag) == 1) ok = cis.readBool() else cis.skipField(tag) }
        return UnPinChatResponseProto(ok)
    }
}

class ArchiveChatResponseMarshaller : io.grpc.MethodDescriptor.Marshaller<ArchiveChatResponseProto> {
    override fun stream(v: ArchiveChatResponseProto): java.io.InputStream = java.io.ByteArrayInputStream(byteArrayOf())
    override fun parse(s: java.io.InputStream): ArchiveChatResponseProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(s); var ok = false
        while (!cis.isAtEnd) { val tag = cis.readTag(); if (tag == 0) break; if (com.google.protobuf.WireFormat.getTagFieldNumber(tag) == 1) ok = cis.readBool() else cis.skipField(tag) }
        return ArchiveChatResponseProto(ok)
    }
}

class UnarchiveChatResponseMarshaller : io.grpc.MethodDescriptor.Marshaller<UnarchiveChatResponseProto> {
    override fun stream(v: UnarchiveChatResponseProto): java.io.InputStream = java.io.ByteArrayInputStream(byteArrayOf())
    override fun parse(s: java.io.InputStream): UnarchiveChatResponseProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(s); var ok = false
        while (!cis.isAtEnd) { val tag = cis.readTag(); if (tag == 0) break; if (com.google.protobuf.WireFormat.getTagFieldNumber(tag) == 1) ok = cis.readBool() else cis.skipField(tag) }
        return UnarchiveChatResponseProto(ok)
    }
}

class PinMessageResponseMarshaller : io.grpc.MethodDescriptor.Marshaller<PinMessageResponseProto> {
    override fun stream(v: PinMessageResponseProto): java.io.InputStream = java.io.ByteArrayInputStream(byteArrayOf())
    override fun parse(s: java.io.InputStream): PinMessageResponseProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(s); var ok = false
        while (!cis.isAtEnd) { val tag = cis.readTag(); if (tag == 0) break; if (com.google.protobuf.WireFormat.getTagFieldNumber(tag) == 1) ok = cis.readBool() else cis.skipField(tag) }
        return PinMessageResponseProto(ok)
    }
}

class UnPinMessageResponseMarshaller : io.grpc.MethodDescriptor.Marshaller<UnPinMessageResponseProto> {
    override fun stream(v: UnPinMessageResponseProto): java.io.InputStream = java.io.ByteArrayInputStream(byteArrayOf())
    override fun parse(s: java.io.InputStream): UnPinMessageResponseProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(s); var ok = false
        while (!cis.isAtEnd) { val tag = cis.readTag(); if (tag == 0) break; if (com.google.protobuf.WireFormat.getTagFieldNumber(tag) == 1) ok = cis.readBool() else cis.skipField(tag) }
        return UnPinMessageResponseProto(ok)
    }
}

class GetPinnedMessagesResponseMarshaller : io.grpc.MethodDescriptor.Marshaller<GetPinnedMessagesResponseProto> {
    override fun stream(v: GetPinnedMessagesResponseProto): java.io.InputStream = java.io.ByteArrayInputStream(byteArrayOf())
    override fun parse(s: java.io.InputStream): GetPinnedMessagesResponseProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(s); val msgs = mutableListOf<MessageProto>(); val mm = MessageProtoMarshaller()
        while (!cis.isAtEnd) { val tag = cis.readTag(); if (tag == 0) break; if (com.google.protobuf.WireFormat.getTagFieldNumber(tag) == 1) { val len = cis.readUInt32(); msgs.add(mm.parse(java.io.ByteArrayInputStream(cis.readRawBytes(len)))) } else cis.skipField(tag) }
        return GetPinnedMessagesResponseProto(msgs)
    }
}
