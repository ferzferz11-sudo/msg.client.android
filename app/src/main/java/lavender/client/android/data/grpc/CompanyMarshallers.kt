package lavender.client.android.data.grpc

import lavender.client.android.data.proto.*

// ===== Helper: parse CompanyProto from CodedInputStream =====
private fun parseCompany(cis: com.google.protobuf.CodedInputStream): CompanyProto {
    var id = ""; var name = ""; var ownerId = ""; var avatarUrl = ""; var createdAt = ""; var memberCount = 0
    while (!cis.isAtEnd) { val tag = cis.readTag(); if (tag == 0) break
        when (com.google.protobuf.WireFormat.getTagFieldNumber(tag)) {
            1 -> id = cis.readString(); 2 -> name = cis.readString(); 3 -> ownerId = cis.readString()
            4 -> avatarUrl = cis.readString(); 5 -> createdAt = cis.readString(); 6 -> memberCount = cis.readInt32()
            else -> cis.skipField(tag)
        }
    }
    return CompanyProto(id, name, ownerId, avatarUrl, createdAt, memberCount)
}

private fun writeCompany(cos: com.google.protobuf.CodedOutputStream, field: Int, v: CompanyProto) {
    val baos = java.io.ByteArrayOutputStream(); val inner = com.google.protobuf.CodedOutputStream.newInstance(baos)
    if (v.id.isNotEmpty()) inner.writeString(1, v.id); if (v.name.isNotEmpty()) inner.writeString(2, v.name)
    if (v.ownerId.isNotEmpty()) inner.writeString(3, v.ownerId); if (v.avatarUrl.isNotEmpty()) inner.writeString(4, v.avatarUrl)
    if (v.createdAt.isNotEmpty()) inner.writeString(5, v.createdAt); if (v.memberCount != 0) inner.writeInt32(6, v.memberCount)
    inner.flush(); cos.writeByteArray(field, baos.toByteArray())
}

// ===== Helper: parse CompanyPositionProto =====
private fun parsePosition(cis: com.google.protobuf.CodedInputStream): CompanyPositionProto {
    var id = ""; var companyId = ""; var title = ""; var level = 0; var chatAccess = ""
    while (!cis.isAtEnd) { val tag = cis.readTag(); if (tag == 0) break
        when (com.google.protobuf.WireFormat.getTagFieldNumber(tag)) {
            1 -> id = cis.readString(); 2 -> companyId = cis.readString(); 3 -> title = cis.readString()
            4 -> level = cis.readInt32(); 5 -> chatAccess = cis.readString()
            else -> cis.skipField(tag)
        }
    }
    return CompanyPositionProto(id, companyId, title, level, chatAccess)
}

private fun writePosition(cos: com.google.protobuf.CodedOutputStream, field: Int, v: CompanyPositionProto) {
    val baos = java.io.ByteArrayOutputStream(); val inner = com.google.protobuf.CodedOutputStream.newInstance(baos)
    if (v.id.isNotEmpty()) inner.writeString(1, v.id); if (v.companyId.isNotEmpty()) inner.writeString(2, v.companyId)
    if (v.title.isNotEmpty()) inner.writeString(3, v.title); if (v.level != 0) inner.writeInt32(4, v.level)
    if (v.chatAccess.isNotEmpty()) inner.writeString(5, v.chatAccess)
    inner.flush(); cos.writeByteArray(field, baos.toByteArray())
}

// ===== Helper: parse CompanyMemberProto =====
private fun parseMember(cis: com.google.protobuf.CodedInputStream): CompanyMemberProto {
    var id = ""; var companyId = ""; var userId = ""; var username = ""; var avatarUrl = ""
    var position: CompanyPositionProto? = null; var joinedAt = ""
    while (!cis.isAtEnd) { val tag = cis.readTag(); if (tag == 0) break
        when (com.google.protobuf.WireFormat.getTagFieldNumber(tag)) {
            1 -> id = cis.readString(); 2 -> companyId = cis.readString(); 3 -> userId = cis.readString()
            4 -> username = cis.readString(); 5 -> avatarUrl = cis.readString()
            6 -> { val len = cis.readUInt32(); position = parsePosition(com.google.protobuf.CodedInputStream.newInstance(cis.readRawBytes(len))) }
            7 -> joinedAt = cis.readString()
            else -> cis.skipField(tag)
        }
    }
    return CompanyMemberProto(id, companyId, userId, username, avatarUrl, position, joinedAt)
}

private fun writeMember(cos: com.google.protobuf.CodedOutputStream, field: Int, v: CompanyMemberProto) {
    val baos = java.io.ByteArrayOutputStream(); val inner = com.google.protobuf.CodedOutputStream.newInstance(baos)
    if (v.id.isNotEmpty()) inner.writeString(1, v.id); if (v.companyId.isNotEmpty()) inner.writeString(2, v.companyId)
    if (v.userId.isNotEmpty()) inner.writeString(3, v.userId); if (v.username.isNotEmpty()) inner.writeString(4, v.username)
    if (v.avatarUrl.isNotEmpty()) inner.writeString(5, v.avatarUrl)
    if (v.position != null) { val pbaos = java.io.ByteArrayOutputStream(); val pinner = com.google.protobuf.CodedOutputStream.newInstance(pbaos)
        val pos = v.position!!; if (pos.id.isNotEmpty()) pinner.writeString(1, pos.id); if (pos.companyId.isNotEmpty()) pinner.writeString(2, pos.companyId)
        if (pos.title.isNotEmpty()) pinner.writeString(3, pos.title); if (pos.level != 0) pinner.writeInt32(4, pos.level)
        if (pos.chatAccess.isNotEmpty()) pinner.writeString(5, pos.chatAccess); pinner.flush(); inner.writeByteArray(6, pbaos.toByteArray()) }
    if (v.joinedAt.isNotEmpty()) inner.writeString(7, v.joinedAt)
    inner.flush(); cos.writeByteArray(field, baos.toByteArray())
}

// ===== Helper: parse CompanyChatInfoProto =====
private fun parseCompanyChatInfo(cis: com.google.protobuf.CodedInputStream): CompanyChatInfoProto {
    var chatId = ""; var companyId = ""; var accessLevel = ""; var minPositionLevel = 0
    while (!cis.isAtEnd) { val tag = cis.readTag(); if (tag == 0) break
        when (com.google.protobuf.WireFormat.getTagFieldNumber(tag)) {
            1 -> chatId = cis.readString(); 2 -> companyId = cis.readString()
            3 -> accessLevel = cis.readString(); 4 -> minPositionLevel = cis.readInt32()
            else -> cis.skipField(tag)
        }
    }
    return CompanyChatInfoProto(chatId, companyId, accessLevel, minPositionLevel)
}

// ===== Helper: parse UserPublicInfoProto =====
private fun parseUserPublicInfo(cis: com.google.protobuf.CodedInputStream): UserPublicInfoProto {
    var userId = ""; var username = ""; var avatarUrl = ""; var fullAvatarUrl = ""; var bio = ""; var status = ""
    var isOnline = false; var lastSeenAt = ""; var companyId = ""; var companyName = ""
    var positionTitle = ""; var positionLevel = 0
    while (!cis.isAtEnd) { val tag = cis.readTag(); if (tag == 0) break
        when (com.google.protobuf.WireFormat.getTagFieldNumber(tag)) {
            1 -> userId = cis.readString(); 2 -> username = cis.readString(); 3 -> avatarUrl = cis.readString()
            4 -> fullAvatarUrl = cis.readString(); 5 -> bio = cis.readString(); 6 -> status = cis.readString()
            7 -> isOnline = cis.readBool(); 8 -> lastSeenAt = cis.readString()
            9 -> companyId = cis.readString(); 10 -> companyName = cis.readString()
            11 -> positionTitle = cis.readString(); 12 -> positionLevel = cis.readInt32()
            else -> cis.skipField(tag)
        }
    }
    return UserPublicInfoProto(userId, username, avatarUrl, fullAvatarUrl, bio, status, isOnline, lastSeenAt, companyId, companyName, positionTitle, positionLevel)
}

// ===== Company CRUD Marshallers =====

class CreateCompanyRequestMarshaller : io.grpc.MethodDescriptor.Marshaller<CreateCompanyRequestProto> {
    override fun stream(v: CreateCompanyRequestProto): java.io.InputStream {
        val baos = java.io.ByteArrayOutputStream(); val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        if (v.name.isNotEmpty()) cos.writeString(1, v.name)
        cos.flush(); return java.io.ByteArrayInputStream(baos.toByteArray())
    }
    override fun parse(s: java.io.InputStream): CreateCompanyRequestProto = CreateCompanyRequestProto()
}

class CreateCompanyResponseMarshaller : io.grpc.MethodDescriptor.Marshaller<CreateCompanyResponseProto> {
    override fun stream(v: CreateCompanyResponseProto): java.io.InputStream = java.io.ByteArrayInputStream(byteArrayOf())
    override fun parse(s: java.io.InputStream): CreateCompanyResponseProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(s)
        var success = false; var company: CompanyProto? = null
        while (!cis.isAtEnd) { val tag = cis.readTag(); if (tag == 0) break
            when (com.google.protobuf.WireFormat.getTagFieldNumber(tag)) {
                1 -> success = cis.readBool()
                2 -> { val len = cis.readUInt32(); company = parseCompany(com.google.protobuf.CodedInputStream.newInstance(cis.readRawBytes(len))) }
                else -> cis.skipField(tag)
            }
        }
        return CreateCompanyResponseProto(success, company)
    }
}

class GetCompanyRequestMarshaller : io.grpc.MethodDescriptor.Marshaller<GetCompanyRequestProto> {
    override fun stream(v: GetCompanyRequestProto): java.io.InputStream {
        val baos = java.io.ByteArrayOutputStream(); val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        if (v.companyId.isNotEmpty()) cos.writeString(1, v.companyId)
        cos.flush(); return java.io.ByteArrayInputStream(baos.toByteArray())
    }
    override fun parse(s: java.io.InputStream): GetCompanyRequestProto = GetCompanyRequestProto()
}

class GetCompanyResponseMarshaller : io.grpc.MethodDescriptor.Marshaller<GetCompanyResponseProto> {
    override fun stream(v: GetCompanyResponseProto): java.io.InputStream = java.io.ByteArrayInputStream(byteArrayOf())
    override fun parse(s: java.io.InputStream): GetCompanyResponseProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(s)
        var company: CompanyProto? = null; val positions = mutableListOf<CompanyPositionProto>(); var memberCount = 0
        while (!cis.isAtEnd) { val tag = cis.readTag(); if (tag == 0) break
            when (com.google.protobuf.WireFormat.getTagFieldNumber(tag)) {
                1 -> { val len = cis.readUInt32(); company = parseCompany(com.google.protobuf.CodedInputStream.newInstance(cis.readRawBytes(len))) }
                2 -> { val len = cis.readUInt32(); positions.add(parsePosition(com.google.protobuf.CodedInputStream.newInstance(cis.readRawBytes(len)))) }
                3 -> memberCount = cis.readInt32()
                else -> cis.skipField(tag)
            }
        }
        return GetCompanyResponseProto(company, positions, memberCount)
    }
}

class UpdateCompanyRequestMarshaller : io.grpc.MethodDescriptor.Marshaller<UpdateCompanyRequestProto> {
    override fun stream(v: UpdateCompanyRequestProto): java.io.InputStream {
        val baos = java.io.ByteArrayOutputStream(); val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        if (v.companyId.isNotEmpty()) cos.writeString(1, v.companyId); if (v.name.isNotEmpty()) cos.writeString(2, v.name)
        if (v.avatarUrl.isNotEmpty()) cos.writeString(3, v.avatarUrl)
        cos.flush(); return java.io.ByteArrayInputStream(baos.toByteArray())
    }
    override fun parse(s: java.io.InputStream): UpdateCompanyRequestProto = UpdateCompanyRequestProto()
}

class UpdateCompanyResponseMarshaller : io.grpc.MethodDescriptor.Marshaller<UpdateCompanyResponseProto> {
    override fun stream(v: UpdateCompanyResponseProto): java.io.InputStream = java.io.ByteArrayInputStream(byteArrayOf())
    override fun parse(s: java.io.InputStream): UpdateCompanyResponseProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(s)
        var success = false; var company: CompanyProto? = null
        while (!cis.isAtEnd) { val tag = cis.readTag(); if (tag == 0) break
            when (com.google.protobuf.WireFormat.getTagFieldNumber(tag)) {
                1 -> success = cis.readBool()
                2 -> { val len = cis.readUInt32(); company = parseCompany(com.google.protobuf.CodedInputStream.newInstance(cis.readRawBytes(len))) }
                else -> cis.skipField(tag)
            }
        }
        return UpdateCompanyResponseProto(success, company)
    }
}

class DeleteCompanyRequestMarshaller : io.grpc.MethodDescriptor.Marshaller<DeleteCompanyRequestProto> {
    override fun stream(v: DeleteCompanyRequestProto): java.io.InputStream {
        val baos = java.io.ByteArrayOutputStream(); val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        if (v.companyId.isNotEmpty()) cos.writeString(1, v.companyId)
        cos.flush(); return java.io.ByteArrayInputStream(baos.toByteArray())
    }
    override fun parse(s: java.io.InputStream): DeleteCompanyRequestProto = DeleteCompanyRequestProto()
}

class DeleteCompanyResponseMarshaller : io.grpc.MethodDescriptor.Marshaller<DeleteCompanyResponseProto> {
    override fun stream(v: DeleteCompanyResponseProto): java.io.InputStream = java.io.ByteArrayInputStream(byteArrayOf())
    override fun parse(s: java.io.InputStream): DeleteCompanyResponseProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(s)
        var success = false; var message = ""
        while (!cis.isAtEnd) { val tag = cis.readTag(); if (tag == 0) break
            when (com.google.protobuf.WireFormat.getTagFieldNumber(tag)) {
                1 -> success = cis.readBool(); 2 -> message = cis.readString()
                else -> cis.skipField(tag)
            }
        }
        return DeleteCompanyResponseProto(success, message)
    }
}

class ListCompaniesRequestMarshaller : io.grpc.MethodDescriptor.Marshaller<ListCompaniesRequestProto> {
    override fun stream(v: ListCompaniesRequestProto): java.io.InputStream {
        val baos = java.io.ByteArrayOutputStream(); val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        if (v.userId.isNotEmpty()) cos.writeString(1, v.userId)
        cos.flush(); return java.io.ByteArrayInputStream(baos.toByteArray())
    }
    override fun parse(s: java.io.InputStream): ListCompaniesRequestProto = ListCompaniesRequestProto()
}

class ListCompaniesResponseMarshaller : io.grpc.MethodDescriptor.Marshaller<ListCompaniesResponseProto> {
    override fun stream(v: ListCompaniesResponseProto): java.io.InputStream = java.io.ByteArrayInputStream(byteArrayOf())
    override fun parse(s: java.io.InputStream): ListCompaniesResponseProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(s)
        val companies = mutableListOf<CompanyProto>()
        while (!cis.isAtEnd) { val tag = cis.readTag(); if (tag == 0) break
            when (com.google.protobuf.WireFormat.getTagFieldNumber(tag)) {
                1 -> { val len = cis.readUInt32(); companies.add(parseCompany(com.google.protobuf.CodedInputStream.newInstance(cis.readRawBytes(len)))) }
                else -> cis.skipField(tag)
            }
        }
        return ListCompaniesResponseProto(companies)
    }
}

// ===== Position Marshallers =====

class CreatePositionRequestMarshaller : io.grpc.MethodDescriptor.Marshaller<CreatePositionRequestProto> {
    override fun stream(v: CreatePositionRequestProto): java.io.InputStream {
        val baos = java.io.ByteArrayOutputStream(); val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        if (v.companyId.isNotEmpty()) cos.writeString(1, v.companyId); if (v.title.isNotEmpty()) cos.writeString(2, v.title)
        if (v.level != 0) cos.writeInt32(3, v.level); if (v.chatAccess.isNotEmpty()) cos.writeString(4, v.chatAccess)
        cos.flush(); return java.io.ByteArrayInputStream(baos.toByteArray())
    }
    override fun parse(s: java.io.InputStream): CreatePositionRequestProto = CreatePositionRequestProto()
}

class CreatePositionResponseMarshaller : io.grpc.MethodDescriptor.Marshaller<CreatePositionResponseProto> {
    override fun stream(v: CreatePositionResponseProto): java.io.InputStream = java.io.ByteArrayInputStream(byteArrayOf())
    override fun parse(s: java.io.InputStream): CreatePositionResponseProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(s)
        var success = false; var position: CompanyPositionProto? = null
        while (!cis.isAtEnd) { val tag = cis.readTag(); if (tag == 0) break
            when (com.google.protobuf.WireFormat.getTagFieldNumber(tag)) {
                1 -> success = cis.readBool()
                2 -> { val len = cis.readUInt32(); position = parsePosition(com.google.protobuf.CodedInputStream.newInstance(cis.readRawBytes(len))) }
                else -> cis.skipField(tag)
            }
        }
        return CreatePositionResponseProto(success, position)
    }
}

class UpdatePositionRequestMarshaller : io.grpc.MethodDescriptor.Marshaller<UpdatePositionRequestProto> {
    override fun stream(v: UpdatePositionRequestProto): java.io.InputStream {
        val baos = java.io.ByteArrayOutputStream(); val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        if (v.positionId.isNotEmpty()) cos.writeString(1, v.positionId); if (v.title.isNotEmpty()) cos.writeString(2, v.title)
        if (v.level != 0) cos.writeInt32(3, v.level); if (v.chatAccess.isNotEmpty()) cos.writeString(4, v.chatAccess)
        cos.flush(); return java.io.ByteArrayInputStream(baos.toByteArray())
    }
    override fun parse(s: java.io.InputStream): UpdatePositionRequestProto = UpdatePositionRequestProto()
}

class UpdatePositionResponseMarshaller : io.grpc.MethodDescriptor.Marshaller<UpdatePositionResponseProto> {
    override fun stream(v: UpdatePositionResponseProto): java.io.InputStream = java.io.ByteArrayInputStream(byteArrayOf())
    override fun parse(s: java.io.InputStream): UpdatePositionResponseProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(s)
        var success = false; var position: CompanyPositionProto? = null
        while (!cis.isAtEnd) { val tag = cis.readTag(); if (tag == 0) break
            when (com.google.protobuf.WireFormat.getTagFieldNumber(tag)) {
                1 -> success = cis.readBool()
                2 -> { val len = cis.readUInt32(); position = parsePosition(com.google.protobuf.CodedInputStream.newInstance(cis.readRawBytes(len))) }
                else -> cis.skipField(tag)
            }
        }
        return UpdatePositionResponseProto(success, position)
    }
}

class DeletePositionRequestMarshaller : io.grpc.MethodDescriptor.Marshaller<DeletePositionRequestProto> {
    override fun stream(v: DeletePositionRequestProto): java.io.InputStream {
        val baos = java.io.ByteArrayOutputStream(); val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        if (v.positionId.isNotEmpty()) cos.writeString(1, v.positionId)
        cos.flush(); return java.io.ByteArrayInputStream(baos.toByteArray())
    }
    override fun parse(s: java.io.InputStream): DeletePositionRequestProto = DeletePositionRequestProto()
}

class DeletePositionResponseMarshaller : io.grpc.MethodDescriptor.Marshaller<DeletePositionResponseProto> {
    override fun stream(v: DeletePositionResponseProto): java.io.InputStream = java.io.ByteArrayInputStream(byteArrayOf())
    override fun parse(s: java.io.InputStream): DeletePositionResponseProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(s)
        var success = false; var message = ""
        while (!cis.isAtEnd) { val tag = cis.readTag(); if (tag == 0) break
            when (com.google.protobuf.WireFormat.getTagFieldNumber(tag)) {
                1 -> success = cis.readBool(); 2 -> message = cis.readString()
                else -> cis.skipField(tag)
            }
        }
        return DeletePositionResponseProto(success, message)
    }
}

class ListPositionsRequestMarshaller : io.grpc.MethodDescriptor.Marshaller<ListPositionsRequestProto> {
    override fun stream(v: ListPositionsRequestProto): java.io.InputStream {
        val baos = java.io.ByteArrayOutputStream(); val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        if (v.companyId.isNotEmpty()) cos.writeString(1, v.companyId)
        cos.flush(); return java.io.ByteArrayInputStream(baos.toByteArray())
    }
    override fun parse(s: java.io.InputStream): ListPositionsRequestProto = ListPositionsRequestProto()
}

class ListPositionsResponseMarshaller : io.grpc.MethodDescriptor.Marshaller<ListPositionsResponseProto> {
    override fun stream(v: ListPositionsResponseProto): java.io.InputStream = java.io.ByteArrayInputStream(byteArrayOf())
    override fun parse(s: java.io.InputStream): ListPositionsResponseProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(s)
        val positions = mutableListOf<CompanyPositionProto>()
        while (!cis.isAtEnd) { val tag = cis.readTag(); if (tag == 0) break
            when (com.google.protobuf.WireFormat.getTagFieldNumber(tag)) {
                1 -> { val len = cis.readUInt32(); positions.add(parsePosition(com.google.protobuf.CodedInputStream.newInstance(cis.readRawBytes(len)))) }
                else -> cis.skipField(tag)
            }
        }
        return ListPositionsResponseProto(positions)
    }
}

// ===== Member Marshallers =====

class AddMemberRequestMarshaller : io.grpc.MethodDescriptor.Marshaller<AddMemberRequestProto> {
    override fun stream(v: AddMemberRequestProto): java.io.InputStream {
        val baos = java.io.ByteArrayOutputStream(); val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        if (v.companyId.isNotEmpty()) cos.writeString(1, v.companyId); if (v.userId.isNotEmpty()) cos.writeString(2, v.userId)
        if (v.positionId.isNotEmpty()) cos.writeString(3, v.positionId)
        cos.flush(); return java.io.ByteArrayInputStream(baos.toByteArray())
    }
    override fun parse(s: java.io.InputStream): AddMemberRequestProto = AddMemberRequestProto()
}

class AddMemberResponseMarshaller : io.grpc.MethodDescriptor.Marshaller<AddMemberResponseProto> {
    override fun stream(v: AddMemberResponseProto): java.io.InputStream = java.io.ByteArrayInputStream(byteArrayOf())
    override fun parse(s: java.io.InputStream): AddMemberResponseProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(s)
        var success = false; var member: CompanyMemberProto? = null
        while (!cis.isAtEnd) { val tag = cis.readTag(); if (tag == 0) break
            when (com.google.protobuf.WireFormat.getTagFieldNumber(tag)) {
                1 -> success = cis.readBool()
                2 -> { val len = cis.readUInt32(); member = parseMember(com.google.protobuf.CodedInputStream.newInstance(cis.readRawBytes(len))) }
                else -> cis.skipField(tag)
            }
        }
        return AddMemberResponseProto(success, member)
    }
}

class RemoveMemberRequestMarshaller : io.grpc.MethodDescriptor.Marshaller<RemoveMemberRequestProto> {
    override fun stream(v: RemoveMemberRequestProto): java.io.InputStream {
        val baos = java.io.ByteArrayOutputStream(); val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        if (v.companyId.isNotEmpty()) cos.writeString(1, v.companyId); if (v.userId.isNotEmpty()) cos.writeString(2, v.userId)
        cos.flush(); return java.io.ByteArrayInputStream(baos.toByteArray())
    }
    override fun parse(s: java.io.InputStream): RemoveMemberRequestProto = RemoveMemberRequestProto()
}

class RemoveMemberResponseMarshaller : io.grpc.MethodDescriptor.Marshaller<RemoveMemberResponseProto> {
    override fun stream(v: RemoveMemberResponseProto): java.io.InputStream = java.io.ByteArrayInputStream(byteArrayOf())
    override fun parse(s: java.io.InputStream): RemoveMemberResponseProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(s)
        var success = false; var message = ""
        while (!cis.isAtEnd) { val tag = cis.readTag(); if (tag == 0) break
            when (com.google.protobuf.WireFormat.getTagFieldNumber(tag)) {
                1 -> success = cis.readBool(); 2 -> message = cis.readString()
                else -> cis.skipField(tag)
            }
        }
        return RemoveMemberResponseProto(success, message)
    }
}

class UpdateMemberPositionRequestMarshaller : io.grpc.MethodDescriptor.Marshaller<UpdateMemberPositionRequestProto> {
    override fun stream(v: UpdateMemberPositionRequestProto): java.io.InputStream {
        val baos = java.io.ByteArrayOutputStream(); val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        if (v.companyId.isNotEmpty()) cos.writeString(1, v.companyId); if (v.userId.isNotEmpty()) cos.writeString(2, v.userId)
        if (v.positionId.isNotEmpty()) cos.writeString(3, v.positionId)
        cos.flush(); return java.io.ByteArrayInputStream(baos.toByteArray())
    }
    override fun parse(s: java.io.InputStream): UpdateMemberPositionRequestProto = UpdateMemberPositionRequestProto()
}

class UpdateMemberPositionResponseMarshaller : io.grpc.MethodDescriptor.Marshaller<UpdateMemberPositionResponseProto> {
    override fun stream(v: UpdateMemberPositionResponseProto): java.io.InputStream = java.io.ByteArrayInputStream(byteArrayOf())
    override fun parse(s: java.io.InputStream): UpdateMemberPositionResponseProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(s)
        var success = false; var member: CompanyMemberProto? = null
        while (!cis.isAtEnd) { val tag = cis.readTag(); if (tag == 0) break
            when (com.google.protobuf.WireFormat.getTagFieldNumber(tag)) {
                1 -> success = cis.readBool()
                2 -> { val len = cis.readUInt32(); member = parseMember(com.google.protobuf.CodedInputStream.newInstance(cis.readRawBytes(len))) }
                else -> cis.skipField(tag)
            }
        }
        return UpdateMemberPositionResponseProto(success, member)
    }
}

class ListMembersRequestMarshaller : io.grpc.MethodDescriptor.Marshaller<ListMembersRequestProto> {
    override fun stream(v: ListMembersRequestProto): java.io.InputStream {
        val baos = java.io.ByteArrayOutputStream(); val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        if (v.companyId.isNotEmpty()) cos.writeString(1, v.companyId); if (v.cursor.isNotEmpty()) cos.writeString(2, v.cursor)
        if (v.limit != 50) cos.writeInt32(3, v.limit)
        cos.flush(); return java.io.ByteArrayInputStream(baos.toByteArray())
    }
    override fun parse(s: java.io.InputStream): ListMembersRequestProto = ListMembersRequestProto()
}

class ListMembersResponseMarshaller : io.grpc.MethodDescriptor.Marshaller<ListMembersResponseProto> {
    override fun stream(v: ListMembersResponseProto): java.io.InputStream = java.io.ByteArrayInputStream(byteArrayOf())
    override fun parse(s: java.io.InputStream): ListMembersResponseProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(s)
        val members = mutableListOf<CompanyMemberProto>(); var nextCursor = ""; var hasMore = false
        while (!cis.isAtEnd) { val tag = cis.readTag(); if (tag == 0) break
            when (com.google.protobuf.WireFormat.getTagFieldNumber(tag)) {
                1 -> { val len = cis.readUInt32(); members.add(parseMember(com.google.protobuf.CodedInputStream.newInstance(cis.readRawBytes(len)))) }
                2 -> nextCursor = cis.readString(); 3 -> hasMore = cis.readBool()
                else -> cis.skipField(tag)
            }
        }
        return ListMembersResponseProto(members, nextCursor, hasMore)
    }
}

// ===== Company Chat Marshallers =====

class CreateCompanyChatRequestMarshaller : io.grpc.MethodDescriptor.Marshaller<CreateCompanyChatRequestProto> {
    override fun stream(v: CreateCompanyChatRequestProto): java.io.InputStream {
        val baos = java.io.ByteArrayOutputStream(); val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        if (v.companyId.isNotEmpty()) cos.writeString(1, v.companyId); if (v.name.isNotEmpty()) cos.writeString(2, v.name)
        if (v.accessLevel.isNotEmpty()) cos.writeString(3, v.accessLevel); if (v.minPositionLevel != 0) cos.writeInt32(4, v.minPositionLevel)
        for (id in v.participantIds) { cos.writeString(5, id) }
        cos.flush(); return java.io.ByteArrayInputStream(baos.toByteArray())
    }
    override fun parse(s: java.io.InputStream): CreateCompanyChatRequestProto = CreateCompanyChatRequestProto()
}

class CreateCompanyChatResponseMarshaller : io.grpc.MethodDescriptor.Marshaller<CreateCompanyChatResponseProto> {
    override fun stream(v: CreateCompanyChatResponseProto): java.io.InputStream = java.io.ByteArrayInputStream(byteArrayOf())
    override fun parse(s: java.io.InputStream): CreateCompanyChatResponseProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(s)
        var success = false; var chatId = ""
        while (!cis.isAtEnd) { val tag = cis.readTag(); if (tag == 0) break
            when (com.google.protobuf.WireFormat.getTagFieldNumber(tag)) {
                1 -> success = cis.readBool(); 2 -> chatId = cis.readString()
                else -> cis.skipField(tag)
            }
        }
        return CreateCompanyChatResponseProto(success, chatId)
    }
}

class SetCompanyChatAccessRequestMarshaller : io.grpc.MethodDescriptor.Marshaller<SetCompanyChatAccessRequestProto> {
    override fun stream(v: SetCompanyChatAccessRequestProto): java.io.InputStream {
        val baos = java.io.ByteArrayOutputStream(); val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        if (v.chatId.isNotEmpty()) cos.writeString(1, v.chatId); if (v.accessLevel.isNotEmpty()) cos.writeString(2, v.accessLevel)
        if (v.minPositionLevel != 0) cos.writeInt32(3, v.minPositionLevel)
        cos.flush(); return java.io.ByteArrayInputStream(baos.toByteArray())
    }
    override fun parse(s: java.io.InputStream): SetCompanyChatAccessRequestProto = SetCompanyChatAccessRequestProto()
}

class SetCompanyChatAccessResponseMarshaller : io.grpc.MethodDescriptor.Marshaller<SetCompanyChatAccessResponseProto> {
    override fun stream(v: SetCompanyChatAccessResponseProto): java.io.InputStream = java.io.ByteArrayInputStream(byteArrayOf())
    override fun parse(s: java.io.InputStream): SetCompanyChatAccessResponseProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(s)
        var success = false
        while (!cis.isAtEnd) { val tag = cis.readTag(); if (tag == 0) break
            when (com.google.protobuf.WireFormat.getTagFieldNumber(tag)) {
                1 -> success = cis.readBool(); else -> cis.skipField(tag)
            }
        }
        return SetCompanyChatAccessResponseProto(success)
    }
}

class GetCompanyChatsRequestMarshaller : io.grpc.MethodDescriptor.Marshaller<GetCompanyChatsRequestProto> {
    override fun stream(v: GetCompanyChatsRequestProto): java.io.InputStream {
        val baos = java.io.ByteArrayOutputStream(); val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        if (v.companyId.isNotEmpty()) cos.writeString(1, v.companyId)
        cos.flush(); return java.io.ByteArrayInputStream(baos.toByteArray())
    }
    override fun parse(s: java.io.InputStream): GetCompanyChatsRequestProto = GetCompanyChatsRequestProto()
}

class GetCompanyChatsResponseMarshaller : io.grpc.MethodDescriptor.Marshaller<GetCompanyChatsResponseProto> {
    override fun stream(v: GetCompanyChatsResponseProto): java.io.InputStream = java.io.ByteArrayInputStream(byteArrayOf())
    override fun parse(s: java.io.InputStream): GetCompanyChatsResponseProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(s)
        val chats = mutableListOf<CompanyChatInfoProto>()
        while (!cis.isAtEnd) { val tag = cis.readTag(); if (tag == 0) break
            when (com.google.protobuf.WireFormat.getTagFieldNumber(tag)) {
                1 -> { val len = cis.readUInt32(); chats.add(parseCompanyChatInfo(com.google.protobuf.CodedInputStream.newInstance(cis.readRawBytes(len)))) }
                else -> cis.skipField(tag)
            }
        }
        return GetCompanyChatsResponseProto(chats)
    }
}

// ===== Join / Leave Marshallers =====

class JoinCompanyRequestMarshaller : io.grpc.MethodDescriptor.Marshaller<JoinCompanyRequestProto> {
    override fun stream(v: JoinCompanyRequestProto): java.io.InputStream {
        val baos = java.io.ByteArrayOutputStream(); val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        if (v.companyId.isNotEmpty()) cos.writeString(1, v.companyId); if (v.inviteCode.isNotEmpty()) cos.writeString(2, v.inviteCode)
        cos.flush(); return java.io.ByteArrayInputStream(baos.toByteArray())
    }
    override fun parse(s: java.io.InputStream): JoinCompanyRequestProto = JoinCompanyRequestProto()
}

class JoinCompanyResponseMarshaller : io.grpc.MethodDescriptor.Marshaller<JoinCompanyResponseProto> {
    override fun stream(v: JoinCompanyResponseProto): java.io.InputStream = java.io.ByteArrayInputStream(byteArrayOf())
    override fun parse(s: java.io.InputStream): JoinCompanyResponseProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(s)
        var success = false; var member: CompanyMemberProto? = null
        while (!cis.isAtEnd) { val tag = cis.readTag(); if (tag == 0) break
            when (com.google.protobuf.WireFormat.getTagFieldNumber(tag)) {
                1 -> success = cis.readBool()
                2 -> { val len = cis.readUInt32(); member = parseMember(com.google.protobuf.CodedInputStream.newInstance(cis.readRawBytes(len))) }
                else -> cis.skipField(tag)
            }
        }
        return JoinCompanyResponseProto(success, member)
    }
}

class LeaveCompanyRequestMarshaller : io.grpc.MethodDescriptor.Marshaller<LeaveCompanyRequestProto> {
    override fun stream(v: LeaveCompanyRequestProto): java.io.InputStream {
        val baos = java.io.ByteArrayOutputStream(); val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        if (v.companyId.isNotEmpty()) cos.writeString(1, v.companyId)
        cos.flush(); return java.io.ByteArrayInputStream(baos.toByteArray())
    }
    override fun parse(s: java.io.InputStream): LeaveCompanyRequestProto = LeaveCompanyRequestProto()
}

class LeaveCompanyResponseMarshaller : io.grpc.MethodDescriptor.Marshaller<LeaveCompanyResponseProto> {
    override fun stream(v: LeaveCompanyResponseProto): java.io.InputStream = java.io.ByteArrayInputStream(byteArrayOf())
    override fun parse(s: java.io.InputStream): LeaveCompanyResponseProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(s)
        var success = false; var message = ""
        while (!cis.isAtEnd) { val tag = cis.readTag(); if (tag == 0) break
            when (com.google.protobuf.WireFormat.getTagFieldNumber(tag)) {
                1 -> success = cis.readBool(); 2 -> message = cis.readString()
                else -> cis.skipField(tag)
            }
        }
        return LeaveCompanyResponseProto(success, message)
    }
}

// ===== User Info Marshallers =====

class GetUserInfoRequestMarshaller : io.grpc.MethodDescriptor.Marshaller<GetUserInfoRequestProto> {
    override fun stream(v: GetUserInfoRequestProto): java.io.InputStream {
        val baos = java.io.ByteArrayOutputStream(); val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        if (v.userId.isNotEmpty()) cos.writeString(1, v.userId)
        cos.flush(); return java.io.ByteArrayInputStream(baos.toByteArray())
    }
    override fun parse(s: java.io.InputStream): GetUserInfoRequestProto = GetUserInfoRequestProto()
}

class GetUserInfoResponseMarshaller : io.grpc.MethodDescriptor.Marshaller<GetUserInfoResponseProto> {
    override fun stream(v: GetUserInfoResponseProto): java.io.InputStream = java.io.ByteArrayInputStream(byteArrayOf())
    override fun parse(s: java.io.InputStream): GetUserInfoResponseProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(s)
        var info: UserPublicInfoProto? = null
        while (!cis.isAtEnd) { val tag = cis.readTag(); if (tag == 0) break
            when (com.google.protobuf.WireFormat.getTagFieldNumber(tag)) {
                1 -> { val len = cis.readUInt32(); info = parseUserPublicInfo(com.google.protobuf.CodedInputStream.newInstance(cis.readRawBytes(len))) }
                else -> cis.skipField(tag)
            }
        }
        return GetUserInfoResponseProto(info)
    }
}

class GetCompanyByUserRequestMarshaller : io.grpc.MethodDescriptor.Marshaller<GetCompanyByUserRequestProto> {
    override fun stream(v: GetCompanyByUserRequestProto): java.io.InputStream {
        val baos = java.io.ByteArrayOutputStream(); val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        if (v.userId.isNotEmpty()) cos.writeString(1, v.userId)
        cos.flush(); return java.io.ByteArrayInputStream(baos.toByteArray())
    }
    override fun parse(s: java.io.InputStream): GetCompanyByUserRequestProto = GetCompanyByUserRequestProto()
}

class GetCompanyByUserResponseMarshaller : io.grpc.MethodDescriptor.Marshaller<GetCompanyByUserResponseProto> {
    override fun stream(v: GetCompanyByUserResponseProto): java.io.InputStream = java.io.ByteArrayInputStream(byteArrayOf())
    override fun parse(s: java.io.InputStream): GetCompanyByUserResponseProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(s)
        var company: CompanyProto? = null; var member: CompanyMemberProto? = null
        while (!cis.isAtEnd) { val tag = cis.readTag(); if (tag == 0) break
            when (com.google.protobuf.WireFormat.getTagFieldNumber(tag)) {
                1 -> { val len = cis.readUInt32(); company = parseCompany(com.google.protobuf.CodedInputStream.newInstance(cis.readRawBytes(len))) }
                2 -> { val len = cis.readUInt32(); member = parseMember(com.google.protobuf.CodedInputStream.newInstance(cis.readRawBytes(len))) }
                else -> cis.skipField(tag)
            }
        }
        return GetCompanyByUserResponseProto(company, member)
    }
}

// ===== Multi-Company Support Marshallers =====

class GetUserCompaniesRequestMarshaller : io.grpc.MethodDescriptor.Marshaller<GetUserCompaniesRequestProto> {
    override fun stream(v: GetUserCompaniesRequestProto): java.io.InputStream = java.io.ByteArrayInputStream(byteArrayOf())
    override fun parse(s: java.io.InputStream): GetUserCompaniesRequestProto = GetUserCompaniesRequestProto()
}

class GetUserCompaniesResponseMarshaller : io.grpc.MethodDescriptor.Marshaller<GetUserCompaniesResponseProto> {
    override fun stream(v: GetUserCompaniesResponseProto): java.io.InputStream = java.io.ByteArrayInputStream(byteArrayOf())
    override fun parse(s: java.io.InputStream): GetUserCompaniesResponseProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(s)
        val companies = mutableListOf<CompanyCompanyMemberProto>()
        while (!cis.isAtEnd) { val tag = cis.readTag(); if (tag == 0) break
            when (com.google.protobuf.WireFormat.getTagFieldNumber(tag)) {
                1 -> {
                    val len = cis.readUInt32()
                    val inner = com.google.protobuf.CodedInputStream.newInstance(cis.readRawBytes(len))
                    var company: CompanyProto? = null; var member: CompanyMemberProto? = null; var isPrimary = false
                    while (!inner.isAtEnd) { val t2 = inner.readTag(); if (t2 == 0) break
                        when (com.google.protobuf.WireFormat.getTagFieldNumber(t2)) {
                            1 -> { val l = inner.readUInt32(); company = parseCompany(com.google.protobuf.CodedInputStream.newInstance(inner.readRawBytes(l))) }
                            2 -> { val l = inner.readUInt32(); member = parseMember(com.google.protobuf.CodedInputStream.newInstance(inner.readRawBytes(l))) }
                            3 -> isPrimary = inner.readBool()
                            else -> inner.skipField(t2)
                        }
                    }
                    companies.add(CompanyCompanyMemberProto(company, member, isPrimary))
                }
                else -> cis.skipField(tag)
            }
        }
        return GetUserCompaniesResponseProto(companies)
    }
}

class SetPrimaryCompanyRequestMarshaller : io.grpc.MethodDescriptor.Marshaller<SetPrimaryCompanyRequestProto> {
    override fun stream(v: SetPrimaryCompanyRequestProto): java.io.InputStream {
        val baos = java.io.ByteArrayOutputStream(); val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        if (v.companyId.isNotEmpty()) cos.writeString(1, v.companyId)
        cos.flush(); return java.io.ByteArrayInputStream(baos.toByteArray())
    }
    override fun parse(s: java.io.InputStream): SetPrimaryCompanyRequestProto = SetPrimaryCompanyRequestProto()
}

class SetPrimaryCompanyResponseMarshaller : io.grpc.MethodDescriptor.Marshaller<SetPrimaryCompanyResponseProto> {
    override fun stream(v: SetPrimaryCompanyResponseProto): java.io.InputStream = java.io.ByteArrayInputStream(byteArrayOf())
    override fun parse(s: java.io.InputStream): SetPrimaryCompanyResponseProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(s)
        var success = false; var message = ""
        while (!cis.isAtEnd) { val tag = cis.readTag(); if (tag == 0) break
            when (com.google.protobuf.WireFormat.getTagFieldNumber(tag)) {
                1 -> success = cis.readBool(); 2 -> message = cis.readString()
                else -> cis.skipField(tag)
            }
        }
        return SetPrimaryCompanyResponseProto(success, message)
    }
}

// ===== Company Settings =====

private fun parseCompanySettings(cis: com.google.protobuf.CodedInputStream): CompanySettingsProto {
    var companyId = ""; var inviteOnly = false; var defaultPositionId = ""
    var allowMemberInvite = false; var chatAccess = "member"; var requireApproval = false
    while (!cis.isAtEnd) { val tag = cis.readTag(); if (tag == 0) break
        when (com.google.protobuf.WireFormat.getTagFieldNumber(tag)) {
            1 -> companyId = cis.readString(); 2 -> inviteOnly = cis.readBool()
            3 -> defaultPositionId = cis.readString(); 4 -> allowMemberInvite = cis.readBool()
            5 -> chatAccess = cis.readString(); 6 -> requireApproval = cis.readBool()
            else -> cis.skipField(tag)
        }
    }
    return CompanySettingsProto(companyId, inviteOnly, defaultPositionId, allowMemberInvite, chatAccess, requireApproval)
}

private fun writeCompanySettings(cos: com.google.protobuf.CodedOutputStream, field: Int, v: CompanySettingsProto) {
    val baos = java.io.ByteArrayOutputStream(); val inner = com.google.protobuf.CodedOutputStream.newInstance(baos)
    if (v.companyId.isNotEmpty()) inner.writeString(1, v.companyId)
    if (v.inviteOnly) inner.writeBool(2, v.inviteOnly)
    if (v.defaultPositionId.isNotEmpty()) inner.writeString(3, v.defaultPositionId)
    if (v.allowMemberInvite) inner.writeBool(4, v.allowMemberInvite)
    if (v.chatAccess != "member") inner.writeString(5, v.chatAccess)
    if (v.requireApproval) inner.writeBool(6, v.requireApproval)
    inner.flush(); cos.writeByteArray(field, baos.toByteArray())
}

class GetCompanySettingsRequestMarshaller : io.grpc.MethodDescriptor.Marshaller<GetCompanySettingsRequestProto> {
    override fun stream(v: GetCompanySettingsRequestProto): java.io.InputStream {
        val baos = java.io.ByteArrayOutputStream(); val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        if (v.companyId.isNotEmpty()) cos.writeString(1, v.companyId)
        cos.flush(); return java.io.ByteArrayInputStream(baos.toByteArray())
    }
    override fun parse(s: java.io.InputStream): GetCompanySettingsRequestProto = GetCompanySettingsRequestProto()
}

class GetCompanySettingsResponseMarshaller : io.grpc.MethodDescriptor.Marshaller<GetCompanySettingsResponseProto> {
    override fun stream(v: GetCompanySettingsResponseProto): java.io.InputStream = java.io.ByteArrayInputStream(byteArrayOf())
    override fun parse(s: java.io.InputStream): GetCompanySettingsResponseProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(s)
        var settings: CompanySettingsProto? = null
        while (!cis.isAtEnd) { val tag = cis.readTag(); if (tag == 0) break
            when (com.google.protobuf.WireFormat.getTagFieldNumber(tag)) {
                1 -> { val len = cis.readUInt32(); settings = parseCompanySettings(com.google.protobuf.CodedInputStream.newInstance(cis.readRawBytes(len))) }
                else -> cis.skipField(tag)
            }
        }
        return GetCompanySettingsResponseProto(settings)
    }
}

class UpdateCompanySettingsRequestMarshaller : io.grpc.MethodDescriptor.Marshaller<UpdateCompanySettingsRequestProto> {
    override fun stream(v: UpdateCompanySettingsRequestProto): java.io.InputStream {
        val baos = java.io.ByteArrayOutputStream(); val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        if (v.companyId.isNotEmpty()) cos.writeString(1, v.companyId)
        v.settings?.let { writeCompanySettings(cos, 2, it) }
        cos.flush(); return java.io.ByteArrayInputStream(baos.toByteArray())
    }
    override fun parse(s: java.io.InputStream): UpdateCompanySettingsRequestProto = UpdateCompanySettingsRequestProto()
}

class UpdateCompanySettingsResponseMarshaller : io.grpc.MethodDescriptor.Marshaller<UpdateCompanySettingsResponseProto> {
    override fun stream(v: UpdateCompanySettingsResponseProto): java.io.InputStream = java.io.ByteArrayInputStream(byteArrayOf())
    override fun parse(s: java.io.InputStream): UpdateCompanySettingsResponseProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(s)
        var success = false
        while (!cis.isAtEnd) { val tag = cis.readTag(); if (tag == 0) break
            when (com.google.protobuf.WireFormat.getTagFieldNumber(tag)) {
                1 -> success = cis.readBool()
                else -> cis.skipField(tag)
            }
        }
        return UpdateCompanySettingsResponseProto(success)
    }
}

// ===== Invite Codes =====

private fun parseInviteCodeInfo(cis: com.google.protobuf.CodedInputStream): InviteCodeInfoProto {
    var id = ""; var code = ""; var companyId = ""; var createdBy = ""; var createdAt = ""
    var expiresAt = ""; var maxUses = 1; var useCount = 0; var isActive = true
    while (!cis.isAtEnd) { val tag = cis.readTag(); if (tag == 0) break
        when (com.google.protobuf.WireFormat.getTagFieldNumber(tag)) {
            1 -> id = cis.readString(); 2 -> code = cis.readString(); 3 -> companyId = cis.readString()
            4 -> createdBy = cis.readString(); 5 -> createdAt = cis.readString(); 6 -> expiresAt = cis.readString()
            7 -> maxUses = cis.readInt32(); 8 -> useCount = cis.readInt32(); 9 -> isActive = cis.readBool()
            else -> cis.skipField(tag)
        }
    }
    return InviteCodeInfoProto(id, code, companyId, createdBy, createdAt, expiresAt, maxUses, useCount, isActive)
}

class GenerateInviteCodeRequestMarshaller : io.grpc.MethodDescriptor.Marshaller<GenerateInviteCodeRequestProto> {
    override fun stream(v: GenerateInviteCodeRequestProto): java.io.InputStream {
        val baos = java.io.ByteArrayOutputStream(); val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        if (v.companyId.isNotEmpty()) cos.writeString(1, v.companyId)
        if (v.expiresHours != 0) cos.writeInt32(2, v.expiresHours)
        if (v.maxUses != 1) cos.writeInt32(3, v.maxUses)
        cos.flush(); return java.io.ByteArrayInputStream(baos.toByteArray())
    }
    override fun parse(s: java.io.InputStream): GenerateInviteCodeRequestProto = GenerateInviteCodeRequestProto()
}

class GenerateInviteCodeResponseMarshaller : io.grpc.MethodDescriptor.Marshaller<GenerateInviteCodeResponseProto> {
    override fun stream(v: GenerateInviteCodeResponseProto): java.io.InputStream = java.io.ByteArrayInputStream(byteArrayOf())
    override fun parse(s: java.io.InputStream): GenerateInviteCodeResponseProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(s)
        var success = false; var code: InviteCodeInfoProto? = null
        while (!cis.isAtEnd) { val tag = cis.readTag(); if (tag == 0) break
            when (com.google.protobuf.WireFormat.getTagFieldNumber(tag)) {
                1 -> success = cis.readBool()
                2 -> { val len = cis.readUInt32(); code = parseInviteCodeInfo(com.google.protobuf.CodedInputStream.newInstance(cis.readRawBytes(len))) }
                else -> cis.skipField(tag)
            }
        }
        return GenerateInviteCodeResponseProto(success, code)
    }
}

class JoinByInviteCodeRequestMarshaller : io.grpc.MethodDescriptor.Marshaller<JoinByInviteCodeRequestProto> {
    override fun stream(v: JoinByInviteCodeRequestProto): java.io.InputStream {
        val baos = java.io.ByteArrayOutputStream(); val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        if (v.code.isNotEmpty()) cos.writeString(1, v.code)
        cos.flush(); return java.io.ByteArrayInputStream(baos.toByteArray())
    }
    override fun parse(s: java.io.InputStream): JoinByInviteCodeRequestProto = JoinByInviteCodeRequestProto()
}

class JoinByInviteCodeResponseMarshaller : io.grpc.MethodDescriptor.Marshaller<JoinByInviteCodeResponseProto> {
    override fun stream(v: JoinByInviteCodeResponseProto): java.io.InputStream = java.io.ByteArrayInputStream(byteArrayOf())
    override fun parse(s: java.io.InputStream): JoinByInviteCodeResponseProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(s)
        var success = false; var companyId = ""; var member: CompanyMemberProto? = null
        while (!cis.isAtEnd) { val tag = cis.readTag(); if (tag == 0) break
            when (com.google.protobuf.WireFormat.getTagFieldNumber(tag)) {
                1 -> success = cis.readBool(); 2 -> companyId = cis.readString()
                3 -> { val len = cis.readUInt32(); member = parseMember(com.google.protobuf.CodedInputStream.newInstance(cis.readRawBytes(len))) }
                else -> cis.skipField(tag)
            }
        }
        return JoinByInviteCodeResponseProto(success, companyId, member)
    }
}

class RevokeInviteCodeRequestMarshaller : io.grpc.MethodDescriptor.Marshaller<RevokeInviteCodeRequestProto> {
    override fun stream(v: RevokeInviteCodeRequestProto): java.io.InputStream {
        val baos = java.io.ByteArrayOutputStream(); val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        if (v.codeId.isNotEmpty()) cos.writeString(1, v.codeId)
        cos.flush(); return java.io.ByteArrayInputStream(baos.toByteArray())
    }
    override fun parse(s: java.io.InputStream): RevokeInviteCodeRequestProto = RevokeInviteCodeRequestProto()
}

class RevokeInviteCodeResponseMarshaller : io.grpc.MethodDescriptor.Marshaller<RevokeInviteCodeResponseProto> {
    override fun stream(v: RevokeInviteCodeResponseProto): java.io.InputStream = java.io.ByteArrayInputStream(byteArrayOf())
    override fun parse(s: java.io.InputStream): RevokeInviteCodeResponseProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(s)
        var success = false
        while (!cis.isAtEnd) { val tag = cis.readTag(); if (tag == 0) break
            when (com.google.protobuf.WireFormat.getTagFieldNumber(tag)) {
                1 -> success = cis.readBool()
                else -> cis.skipField(tag)
            }
        }
        return RevokeInviteCodeResponseProto(success)
    }
}

class ListInviteCodesRequestMarshaller : io.grpc.MethodDescriptor.Marshaller<ListInviteCodesRequestProto> {
    override fun stream(v: ListInviteCodesRequestProto): java.io.InputStream {
        val baos = java.io.ByteArrayOutputStream(); val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        if (v.companyId.isNotEmpty()) cos.writeString(1, v.companyId)
        cos.flush(); return java.io.ByteArrayInputStream(baos.toByteArray())
    }
    override fun parse(s: java.io.InputStream): ListInviteCodesRequestProto = ListInviteCodesRequestProto()
}

class ListInviteCodesResponseMarshaller : io.grpc.MethodDescriptor.Marshaller<ListInviteCodesResponseProto> {
    override fun stream(v: ListInviteCodesResponseProto): java.io.InputStream = java.io.ByteArrayInputStream(byteArrayOf())
    override fun parse(s: java.io.InputStream): ListInviteCodesResponseProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(s)
        val codes = mutableListOf<InviteCodeInfoProto>()
        while (!cis.isAtEnd) { val tag = cis.readTag(); if (tag == 0) break
            when (com.google.protobuf.WireFormat.getTagFieldNumber(tag)) {
                1 -> { val len = cis.readUInt32(); codes.add(parseInviteCodeInfo(com.google.protobuf.CodedInputStream.newInstance(cis.readRawBytes(len)))) }
                else -> cis.skipField(tag)
            }
        }
        return ListInviteCodesResponseProto(codes)
    }
}

// ===== Company Notifications =====

class SendCompanyNotificationRequestMarshaller : io.grpc.MethodDescriptor.Marshaller<SendCompanyNotificationRequestProto> {
    override fun stream(v: SendCompanyNotificationRequestProto): java.io.InputStream {
        val baos = java.io.ByteArrayOutputStream(); val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        if (v.companyId.isNotEmpty()) cos.writeString(1, v.companyId)
        if (v.eventType != 0) cos.writeInt32(2, v.eventType)
        if (v.actorUsername.isNotEmpty()) cos.writeString(3, v.actorUsername)
        if (v.targetUsername.isNotEmpty()) cos.writeString(4, v.targetUsername)
        if (v.positionName.isNotEmpty()) cos.writeString(5, v.positionName)
        cos.flush(); return java.io.ByteArrayInputStream(baos.toByteArray())
    }
    override fun parse(s: java.io.InputStream): SendCompanyNotificationRequestProto = SendCompanyNotificationRequestProto()
}

class SendCompanyNotificationResponseMarshaller : io.grpc.MethodDescriptor.Marshaller<SendCompanyNotificationResponseProto> {
    override fun stream(v: SendCompanyNotificationResponseProto): java.io.InputStream = java.io.ByteArrayInputStream(byteArrayOf())
    override fun parse(s: java.io.InputStream): SendCompanyNotificationResponseProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(s)
        var success = false
        while (!cis.isAtEnd) { val tag = cis.readTag(); if (tag == 0) break
            when (com.google.protobuf.WireFormat.getTagFieldNumber(tag)) {
                1 -> success = cis.readBool()
                else -> cis.skipField(tag)
            }
        }
        return SendCompanyNotificationResponseProto(success)
    }
}
