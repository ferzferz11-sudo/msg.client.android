package lavender.client.android.data.grpc

import io.grpc.MethodDescriptor
import lavender.client.android.data.proto.*
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

// ======= AI Services v2 Marshallers =======
// Server proto: messenger.ChatService/*
// Field order from AI_V2_CLIENT_INTEGRATION.md

// ======= ChatWithAIV2 =======

class ChatWithAIV2RequestMarshaller : MethodDescriptor.Marshaller<ChatWithAIV2RequestProto> {
    override fun stream(v: ChatWithAIV2RequestProto): java.io.InputStream {
        val baos = ByteArrayOutputStream(); val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        if (v.sessionId.isNotEmpty()) cos.writeString(1, v.sessionId)
        if (v.message.isNotEmpty()) cos.writeString(2, v.message)
        for (image in v.images) {
            cos.writeByteArray(3, image)
        }
        if (v.agentId.isNotEmpty()) cos.writeString(4, v.agentId)
        for (toolCall in v.toolCalls) {
            val tcBaos = ByteArrayOutputStream(); val tcCos = com.google.protobuf.CodedOutputStream.newInstance(tcBaos)
            if (toolCall.id.isNotEmpty()) tcCos.writeString(1, toolCall.id)
            if (toolCall.name.isNotEmpty()) tcCos.writeString(2, toolCall.name)
            if (toolCall.arguments.isNotEmpty()) tcCos.writeString(3, toolCall.arguments)
            if (toolCall.result.isNotEmpty()) tcCos.writeString(4, toolCall.result)
            tcCos.flush()
            cos.writeTag(5, com.google.protobuf.WireFormat.WIRETYPE_LENGTH_DELIMITED)
            cos.writeUInt32NoTag(tcBaos.toByteArray().size)
            cos.writeRawBytes(tcBaos.toByteArray())
        }
        cos.flush(); return ByteArrayInputStream(baos.toByteArray())
    }
    override fun parse(s: java.io.InputStream): ChatWithAIV2RequestProto = ChatWithAIV2RequestProto()
}

class ChatWithAIV2ResponseMarshaller : MethodDescriptor.Marshaller<ChatWithAIV2ResponseProto> {
    override fun stream(v: ChatWithAIV2ResponseProto): java.io.InputStream = ByteArrayInputStream(byteArrayOf())
    override fun parse(s: java.io.InputStream): ChatWithAIV2ResponseProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(s)
        var token = ""; var finished = false; var error = ""; var imageUrl = ""; var agentId = ""; var agentName = ""
        var hasRagContext = false; var modelUsed = ""; var tokenCount = 0
        val toolCalls = mutableListOf<ToolCallRequestV2Proto>()
        while (!cis.isAtEnd) {
            val tag = cis.readTag(); if (tag == 0) break
            when (com.google.protobuf.WireFormat.getTagFieldNumber(tag)) {
                1 -> token = cis.readString()
                2 -> finished = cis.readBool()
                3 -> error = cis.readString()
                4 -> agentId = cis.readString()
                5 -> agentName = cis.readString()
                6 -> {
                    val len = cis.readRawVarint32()
                    val msgBytes = cis.readRawBytes(len)
                    if (msgBytes.isNotEmpty()) {
                        try {
                            val inner = com.google.protobuf.CodedInputStream.newInstance(msgBytes)
                            var tcId = ""; var tcName = ""; var tcArgs = ""
                            while (!inner.isAtEnd) {
                                val innerTag = inner.readTag()
                                if (innerTag == 0) break
                                when (com.google.protobuf.WireFormat.getTagFieldNumber(innerTag)) {
                                    1 -> tcId = inner.readString()
                                    2 -> tcName = inner.readString()
                                    3 -> tcArgs = inner.readString()
                                    else -> inner.skipField(innerTag)
                                }
                            }
                            toolCalls.add(ToolCallRequestV2Proto(tcId, tcName, tcArgs))
                        } catch (_: Exception) {}
                    }
                }
                7 -> hasRagContext = cis.readBool()
                8 -> modelUsed = cis.readString()
                9 -> tokenCount = cis.readInt32()
                10 -> imageUrl = cis.readString()
                else -> cis.skipField(tag)
            }
        }
        return ChatWithAIV2ResponseProto(token, finished, error, imageUrl, agentId, agentName, toolCalls, hasRagContext, modelUsed, tokenCount)
    }
}

// ======= Agent CRUD =======

class CreateAIAgentRequestMarshaller : MethodDescriptor.Marshaller<CreateAIAgentRequestProto> {
    override fun stream(v: CreateAIAgentRequestProto): java.io.InputStream {
        val baos = ByteArrayOutputStream(); val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        if (v.name.isNotEmpty()) cos.writeString(1, v.name)
        if (v.description.isNotEmpty()) cos.writeString(2, v.description)
        if (v.providerType.isNotEmpty()) cos.writeString(3, v.providerType)
        if (v.providerConfig.isNotEmpty()) cos.writeString(4, v.providerConfig)
        if (v.systemPrompt.isNotEmpty()) cos.writeString(5, v.systemPrompt)
        if (v.model.isNotEmpty()) cos.writeString(6, v.model)
        if (v.maxTokens != 0) cos.writeInt32(7, v.maxTokens)
        if (v.temperature != 0f) cos.writeFloat(8, v.temperature)
        if (v.toolsEnabled) cos.writeBool(9, v.toolsEnabled)
        for (tool in v.toolWhitelist) { cos.writeString(10, tool) }
        if (v.ragEnabled) cos.writeBool(11, v.ragEnabled)
        if (v.ragConfig.isNotEmpty()) cos.writeString(12, v.ragConfig)
        if (v.rateLimit != 0) cos.writeInt32(13, v.rateLimit)
        if (v.isPublic) cos.writeBool(14, v.isPublic)
        cos.flush(); return ByteArrayInputStream(baos.toByteArray())
    }
    override fun parse(s: java.io.InputStream): CreateAIAgentRequestProto = CreateAIAgentRequestProto()
}

class CreateAIAgentResponseMarshaller : MethodDescriptor.Marshaller<CreateAIAgentResponseProto> {
    override fun stream(v: CreateAIAgentResponseProto): java.io.InputStream = ByteArrayInputStream(byteArrayOf())
    override fun parse(s: java.io.InputStream): CreateAIAgentResponseProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(s)
        var success = false; var agentId = ""; var error = ""
        while (!cis.isAtEnd) {
            val tag = cis.readTag(); if (tag == 0) break
            when (com.google.protobuf.WireFormat.getTagFieldNumber(tag)) {
                1 -> success = cis.readBool()
                2 -> agentId = cis.readString()
                3 -> error = cis.readString()
                else -> cis.skipField(tag)
            }
        }
        return CreateAIAgentResponseProto(success, agentId, error)
    }
}

class UpdateAIAgentRequestMarshaller : MethodDescriptor.Marshaller<UpdateAIAgentRequestProto> {
    override fun stream(v: UpdateAIAgentRequestProto): java.io.InputStream {
        val baos = ByteArrayOutputStream(); val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        if (v.agentId.isNotEmpty()) cos.writeString(1, v.agentId)
        if (v.name.isNotEmpty()) cos.writeString(2, v.name)
        if (v.description.isNotEmpty()) cos.writeString(3, v.description)
        if (v.providerConfig.isNotEmpty()) cos.writeString(4, v.providerConfig)
        if (v.systemPrompt.isNotEmpty()) cos.writeString(5, v.systemPrompt)
        if (v.model.isNotEmpty()) cos.writeString(6, v.model)
        if (v.maxTokens != 0) cos.writeInt32(7, v.maxTokens)
        if (v.temperature != 0f) cos.writeFloat(8, v.temperature)
        if (v.toolsEnabled) cos.writeBool(9, v.toolsEnabled)
        for (tool in v.toolWhitelist) { cos.writeString(10, tool) }
        if (v.ragEnabled) cos.writeBool(11, v.ragEnabled)
        if (v.ragConfig.isNotEmpty()) cos.writeString(12, v.ragConfig)
        if (v.rateLimit != 0) cos.writeInt32(13, v.rateLimit)
        if (v.isPublic) cos.writeBool(14, v.isPublic)
        cos.flush(); return ByteArrayInputStream(baos.toByteArray())
    }
    override fun parse(s: java.io.InputStream): UpdateAIAgentRequestProto = UpdateAIAgentRequestProto()
}

class UpdateAIAgentResponseMarshaller : MethodDescriptor.Marshaller<UpdateAIAgentResponseProto> {
    override fun stream(v: UpdateAIAgentResponseProto): java.io.InputStream = ByteArrayInputStream(byteArrayOf())
    override fun parse(s: java.io.InputStream): UpdateAIAgentResponseProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(s)
        var success = false; var error = ""
        while (!cis.isAtEnd) {
            val tag = cis.readTag(); if (tag == 0) break
            when (com.google.protobuf.WireFormat.getTagFieldNumber(tag)) {
                1 -> success = cis.readBool()
                2 -> error = cis.readString()
                else -> cis.skipField(tag)
            }
        }
        return UpdateAIAgentResponseProto(success, error)
    }
}

class DeleteAIAgentRequestMarshaller : MethodDescriptor.Marshaller<DeleteAIAgentRequestProto> {
    override fun stream(v: DeleteAIAgentRequestProto): java.io.InputStream {
        val baos = ByteArrayOutputStream(); val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        if (v.agentId.isNotEmpty()) cos.writeString(1, v.agentId)
        cos.flush(); return ByteArrayInputStream(baos.toByteArray())
    }
    override fun parse(s: java.io.InputStream): DeleteAIAgentRequestProto = DeleteAIAgentRequestProto()
}

class DeleteAIAgentResponseMarshaller : MethodDescriptor.Marshaller<DeleteAIAgentResponseProto> {
    override fun stream(v: DeleteAIAgentResponseProto): java.io.InputStream = ByteArrayInputStream(byteArrayOf())
    override fun parse(s: java.io.InputStream): DeleteAIAgentResponseProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(s)
        var success = false; var error = ""
        while (!cis.isAtEnd) {
            val tag = cis.readTag(); if (tag == 0) break
            when (com.google.protobuf.WireFormat.getTagFieldNumber(tag)) {
                1 -> success = cis.readBool()
                2 -> error = cis.readString()
                else -> cis.skipField(tag)
            }
        }
        return DeleteAIAgentResponseProto(success, error)
    }
}

class GetAIAgentRequestMarshaller : MethodDescriptor.Marshaller<GetAIAgentRequestProto> {
    override fun stream(v: GetAIAgentRequestProto): java.io.InputStream {
        val baos = ByteArrayOutputStream(); val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        if (v.agentId.isNotEmpty()) cos.writeString(1, v.agentId)
        cos.flush(); return ByteArrayInputStream(baos.toByteArray())
    }
    override fun parse(s: java.io.InputStream): GetAIAgentRequestProto = GetAIAgentRequestProto()
}

class GetAIAgentResponseMarshaller : MethodDescriptor.Marshaller<GetAIAgentResponseProto> {
    override fun stream(v: GetAIAgentResponseProto): java.io.InputStream = ByteArrayInputStream(byteArrayOf())
    override fun parse(s: java.io.InputStream): GetAIAgentResponseProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(s)
        var agent: AgentInfoV2Proto? = null
        while (!cis.isAtEnd) {
            val tag = cis.readTag(); if (tag == 0) break
            when (com.google.protobuf.WireFormat.getTagFieldNumber(tag)) {
                1 -> {
                    val len = cis.readRawVarint32()
                    val msgBytes = cis.readRawBytes(len)
                    if (msgBytes.isNotEmpty()) {
                        agent = parseAgentInfoV2(msgBytes)
                    }
                }
                else -> cis.skipField(tag)
            }
        }
        return GetAIAgentResponseProto(agent ?: AgentInfoV2Proto())
    }
}

class ListAIAgentsRequestMarshaller : MethodDescriptor.Marshaller<ListAIAgentsRequestProto> {
    override fun stream(v: ListAIAgentsRequestProto): java.io.InputStream {
        val baos = ByteArrayOutputStream(); val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        if (v.includePublic) cos.writeBool(1, true)
        cos.flush(); return ByteArrayInputStream(baos.toByteArray())
    }
    override fun parse(s: java.io.InputStream): ListAIAgentsRequestProto = ListAIAgentsRequestProto()
}

class ListAIAgentsResponseMarshaller : MethodDescriptor.Marshaller<ListAIAgentsResponseProto> {
    override fun stream(v: ListAIAgentsResponseProto): java.io.InputStream = ByteArrayInputStream(byteArrayOf())
    override fun parse(s: java.io.InputStream): ListAIAgentsResponseProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(s)
        val agents = mutableListOf<AgentInfoV2Proto>()
        while (!cis.isAtEnd) {
            val tag = cis.readTag(); if (tag == 0) break
            when (com.google.protobuf.WireFormat.getTagFieldNumber(tag)) {
                1 -> {
                    val len = cis.readRawVarint32()
                    val msgBytes = cis.readRawBytes(len)
                    if (msgBytes.isNotEmpty()) {
                        val agent = parseAgentInfoV2(msgBytes)
                        if (agent != null) agents.add(agent)
                    }
                }
                else -> cis.skipField(tag)
            }
        }
        return ListAIAgentsResponseProto(agents)
    }
}

class CloneAIAgentRequestMarshaller : MethodDescriptor.Marshaller<CloneAIAgentRequestProto> {
    override fun stream(v: CloneAIAgentRequestProto): java.io.InputStream {
        val baos = ByteArrayOutputStream(); val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        if (v.agentId.isNotEmpty()) cos.writeString(1, v.agentId)
        if (v.newName.isNotEmpty()) cos.writeString(2, v.newName)
        cos.flush(); return ByteArrayInputStream(baos.toByteArray())
    }
    override fun parse(s: java.io.InputStream): CloneAIAgentRequestProto = CloneAIAgentRequestProto()
}

class CloneAIAgentResponseMarshaller : MethodDescriptor.Marshaller<CloneAIAgentResponseProto> {
    override fun stream(v: CloneAIAgentResponseProto): java.io.InputStream = ByteArrayInputStream(byteArrayOf())
    override fun parse(s: java.io.InputStream): CloneAIAgentResponseProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(s)
        var success = false; var agentId = ""; var error = ""
        while (!cis.isAtEnd) {
            val tag = cis.readTag(); if (tag == 0) break
            when (com.google.protobuf.WireFormat.getTagFieldNumber(tag)) {
                1 -> success = cis.readBool()
                2 -> agentId = cis.readString()
                3 -> error = cis.readString()
                else -> cis.skipField(tag)
            }
        }
        return CloneAIAgentResponseProto(success, agentId, error)
    }
}

// ======= Tools =======

class ListAIToolsRequestMarshaller : MethodDescriptor.Marshaller<ListAIToolsRequestProto> {
    override fun stream(v: ListAIToolsRequestProto): java.io.InputStream = ByteArrayInputStream(byteArrayOf())
    override fun parse(s: java.io.InputStream): ListAIToolsRequestProto = ListAIToolsRequestProto()
}

class ListAIToolsResponseMarshaller : MethodDescriptor.Marshaller<ListAIToolsResponseProto> {
    override fun stream(v: ListAIToolsResponseProto): java.io.InputStream = ByteArrayInputStream(byteArrayOf())
    override fun parse(s: java.io.InputStream): ListAIToolsResponseProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(s)
        val tools = mutableListOf<ToolInfoV2Proto>()
        while (!cis.isAtEnd) {
            val tag = cis.readTag(); if (tag == 0) break
            when (com.google.protobuf.WireFormat.getTagFieldNumber(tag)) {
                1 -> {
                    val len = cis.readRawVarint32()
                    val msgBytes = cis.readRawBytes(len)
                    if (msgBytes.isNotEmpty()) {
                        try {
                            val inner = com.google.protobuf.CodedInputStream.newInstance(msgBytes)
                            var name = ""; var desc = ""; var schema = ""; var role = ""
                            while (!inner.isAtEnd) {
                                val innerTag = inner.readTag()
                                if (innerTag == 0) break
                                when (com.google.protobuf.WireFormat.getTagFieldNumber(innerTag)) {
                                    1 -> name = inner.readString()
                                    2 -> desc = inner.readString()
                                    3 -> schema = inner.readString()
                                    4 -> role = inner.readString()
                                    else -> inner.skipField(innerTag)
                                }
                            }
                            tools.add(ToolInfoV2Proto(name, desc, schema, role))
                        } catch (_: Exception) {}
                    }
                }
                else -> cis.skipField(tag)
            }
        }
        return ListAIToolsResponseProto(tools)
    }
}

// ======= AI Chat Settings =======

class GetAIChatSettingsRequestMarshaller : MethodDescriptor.Marshaller<GetAIChatSettingsRequestProto> {
    override fun stream(v: GetAIChatSettingsRequestProto): java.io.InputStream {
        val baos = ByteArrayOutputStream(); val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        if (v.sessionId.isNotEmpty()) cos.writeString(1, v.sessionId)
        cos.flush(); return ByteArrayInputStream(baos.toByteArray())
    }
    override fun parse(s: java.io.InputStream): GetAIChatSettingsRequestProto = GetAIChatSettingsRequestProto()
}

class AIChatSettingsResponseMarshaller : MethodDescriptor.Marshaller<AIChatSettingsProto> {
    override fun stream(v: AIChatSettingsProto): java.io.InputStream = ByteArrayInputStream(byteArrayOf())
    override fun parse(s: java.io.InputStream): AIChatSettingsProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(s)
        var sessionId = ""; var userApiKey = ""; var model = ""; var isUsingCustomKey = false
        var remaining = 0; var limit = 0; var windowSeconds = 0
        while (!cis.isAtEnd) {
            val tag = cis.readTag(); if (tag == 0) break
            when (com.google.protobuf.WireFormat.getTagFieldNumber(tag)) {
                1 -> sessionId = cis.readString()
                2 -> userApiKey = cis.readString()
                3 -> model = cis.readString()
                4 -> isUsingCustomKey = cis.readBool()
                5 -> remaining = cis.readInt32()
                6 -> limit = cis.readInt32()
                7 -> windowSeconds = cis.readInt32()
                else -> cis.skipField(tag)
            }
        }
        return AIChatSettingsProto(sessionId, userApiKey, model, isUsingCustomKey, remaining, limit, windowSeconds)
    }
}

class UpdateAIChatSettingsRequestMarshaller : MethodDescriptor.Marshaller<UpdateAIChatSettingsRequestProto> {
    override fun stream(v: UpdateAIChatSettingsRequestProto): java.io.InputStream {
        val baos = ByteArrayOutputStream(); val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        if (v.sessionId.isNotEmpty()) cos.writeString(1, v.sessionId)
        if (v.apiKey.isNotEmpty()) cos.writeString(2, v.apiKey)
        if (v.model.isNotEmpty()) cos.writeString(3, v.model)
        cos.flush(); return ByteArrayInputStream(baos.toByteArray())
    }
    override fun parse(s: java.io.InputStream): UpdateAIChatSettingsRequestProto = UpdateAIChatSettingsRequestProto()
}

class UpdateAIChatSettingsResponseMarshaller : MethodDescriptor.Marshaller<UpdateAIChatSettingsResponseProto> {
    override fun stream(v: UpdateAIChatSettingsResponseProto): java.io.InputStream = ByteArrayInputStream(byteArrayOf())
    override fun parse(s: java.io.InputStream): UpdateAIChatSettingsResponseProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(s)
        var success = false; var message = ""
        while (!cis.isAtEnd) {
            val tag = cis.readTag(); if (tag == 0) break
            when (com.google.protobuf.WireFormat.getTagFieldNumber(tag)) {
                1 -> success = cis.readBool()
                2 -> message = cis.readString()
                else -> cis.skipField(tag)
            }
        }
        return UpdateAIChatSettingsResponseProto(success, message)
    }
}

// ======= Marketplace =======

class RateAIAgentRequestMarshaller : MethodDescriptor.Marshaller<RateAIAgentRequestProto> {
    override fun stream(v: RateAIAgentRequestProto): java.io.InputStream {
        val baos = ByteArrayOutputStream(); val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        if (v.agentId.isNotEmpty()) cos.writeString(1, v.agentId)
        if (v.rating != 0) cos.writeInt32(2, v.rating)
        if (v.review.isNotEmpty()) cos.writeString(3, v.review)
        cos.flush(); return ByteArrayInputStream(baos.toByteArray())
    }
    override fun parse(s: java.io.InputStream): RateAIAgentRequestProto = RateAIAgentRequestProto()
}

class RateAIAgentResponseMarshaller : MethodDescriptor.Marshaller<RateAIAgentResponseProto> {
    override fun stream(v: RateAIAgentResponseProto): java.io.InputStream = ByteArrayInputStream(byteArrayOf())
    override fun parse(s: java.io.InputStream): RateAIAgentResponseProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(s)
        var success = false; var error = ""; var avgRating = 0f; var reviewCount = 0
        while (!cis.isAtEnd) {
            val tag = cis.readTag(); if (tag == 0) break
            when (com.google.protobuf.WireFormat.getTagFieldNumber(tag)) {
                1 -> success = cis.readBool()
                2 -> error = cis.readString()
                3 -> avgRating = cis.readFloat()
                4 -> reviewCount = cis.readInt32()
                else -> cis.skipField(tag)
            }
        }
        return RateAIAgentResponseProto(success, error, avgRating, reviewCount)
    }
}

class GetAIAgentReviewsRequestMarshaller : MethodDescriptor.Marshaller<GetAIAgentReviewsRequestProto> {
    override fun stream(v: GetAIAgentReviewsRequestProto): java.io.InputStream {
        val baos = ByteArrayOutputStream(); val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        if (v.agentId.isNotEmpty()) cos.writeString(1, v.agentId)
        cos.writeInt32(2, v.limit)
        cos.flush(); return ByteArrayInputStream(baos.toByteArray())
    }
    override fun parse(s: java.io.InputStream): GetAIAgentReviewsRequestProto = GetAIAgentReviewsRequestProto()
}

class GetAIAgentReviewsResponseMarshaller : MethodDescriptor.Marshaller<GetAIAgentReviewsResponseProto> {
    override fun stream(v: GetAIAgentReviewsResponseProto): java.io.InputStream = ByteArrayInputStream(byteArrayOf())
    override fun parse(s: java.io.InputStream): GetAIAgentReviewsResponseProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(s)
        val reviews = mutableListOf<AgentReviewProto>()
        var avgRating = 0f; var reviewCount = 0
        while (!cis.isAtEnd) {
            val tag = cis.readTag(); if (tag == 0) break
            when (com.google.protobuf.WireFormat.getTagFieldNumber(tag)) {
                1 -> {
                    val len = cis.readRawVarint32()
                    val msgBytes = cis.readRawBytes(len)
                    if (msgBytes.isNotEmpty()) {
                        try {
                            val inner = com.google.protobuf.CodedInputStream.newInstance(msgBytes)
                            var userId = ""; var rating = 0; var review = ""; var createdAt = ""
                            while (!inner.isAtEnd) {
                                val innerTag = inner.readTag()
                                if (innerTag == 0) break
                                when (com.google.protobuf.WireFormat.getTagFieldNumber(innerTag)) {
                                    1 -> userId = inner.readString()
                                    2 -> rating = inner.readInt32()
                                    3 -> review = inner.readString()
                                    4 -> createdAt = inner.readString()
                                    else -> inner.skipField(innerTag)
                                }
                            }
                            reviews.add(AgentReviewProto(userId, rating, review, createdAt))
                        } catch (_: Exception) {}
                    }
                }
                2 -> avgRating = cis.readFloat()
                3 -> reviewCount = cis.readInt32()
                else -> cis.skipField(tag)
            }
        }
        return GetAIAgentReviewsResponseProto(reviews, avgRating, reviewCount)
    }
}

class ListMarketplaceAgentsRequestMarshaller : MethodDescriptor.Marshaller<ListMarketplaceAgentsRequestProto> {
    override fun stream(v: ListMarketplaceAgentsRequestProto): java.io.InputStream {
        val baos = ByteArrayOutputStream(); val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        if (v.query.isNotEmpty()) cos.writeString(1, v.query)
        cos.writeInt32(2, v.limit)
        cos.writeInt32(3, v.offset)
        cos.flush(); return ByteArrayInputStream(baos.toByteArray())
    }
    override fun parse(s: java.io.InputStream): ListMarketplaceAgentsRequestProto = ListMarketplaceAgentsRequestProto()
}

class ListMarketplaceAgentsResponseMarshaller : MethodDescriptor.Marshaller<ListMarketplaceAgentsResponseProto> {
    override fun stream(v: ListMarketplaceAgentsResponseProto): java.io.InputStream = ByteArrayInputStream(byteArrayOf())
    override fun parse(s: java.io.InputStream): ListMarketplaceAgentsResponseProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(s)
        val agents = mutableListOf<AgentInfoV2Proto>()
        var total = 0
        while (!cis.isAtEnd) {
            val tag = cis.readTag(); if (tag == 0) break
            when (com.google.protobuf.WireFormat.getTagFieldNumber(tag)) {
                1 -> {
                    val len = cis.readRawVarint32()
                    val msgBytes = cis.readRawBytes(len)
                    if (msgBytes.isNotEmpty()) {
                        val agent = parseAgentInfoV2(msgBytes)
                        if (agent != null) agents.add(agent)
                    }
                }
                2 -> total = cis.readInt32()
                else -> cis.skipField(tag)
            }
        }
        return ListMarketplaceAgentsResponseProto(agents, total)
    }
}

class GetAIAgentStatsRequestMarshaller : MethodDescriptor.Marshaller<GetAIAgentStatsRequestProto> {
    override fun stream(v: GetAIAgentStatsRequestProto): java.io.InputStream {
        val baos = ByteArrayOutputStream(); val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        if (v.agentId.isNotEmpty()) cos.writeString(1, v.agentId)
        cos.flush(); return ByteArrayInputStream(baos.toByteArray())
    }
    override fun parse(s: java.io.InputStream): GetAIAgentStatsRequestProto = GetAIAgentStatsRequestProto()
}

class GetAIAgentStatsResponseMarshaller : MethodDescriptor.Marshaller<GetAIAgentStatsResponseProto> {
    override fun stream(v: GetAIAgentStatsResponseProto): java.io.InputStream = ByteArrayInputStream(byteArrayOf())
    override fun parse(s: java.io.InputStream): GetAIAgentStatsResponseProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(s)
        var installCount = 0; var avgRating = 0f; var reviewCount = 0; var totalTokensUsed = 0
        while (!cis.isAtEnd) {
            val tag = cis.readTag(); if (tag == 0) break
            when (com.google.protobuf.WireFormat.getTagFieldNumber(tag)) {
                1 -> installCount = cis.readInt32()
                2 -> avgRating = cis.readFloat()
                3 -> reviewCount = cis.readInt32()
                4 -> totalTokensUsed = cis.readInt32()
                else -> cis.skipField(tag)
            }
        }
        return GetAIAgentStatsResponseProto(installCount, avgRating, reviewCount, totalTokensUsed)
    }
}

class ShareAIAgentRequestMarshaller : MethodDescriptor.Marshaller<ShareAIAgentRequestProto> {
    override fun stream(v: ShareAIAgentRequestProto): java.io.InputStream {
        val baos = ByteArrayOutputStream(); val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        if (v.agentId.isNotEmpty()) cos.writeString(1, v.agentId)
        cos.flush(); return ByteArrayInputStream(baos.toByteArray())
    }
    override fun parse(s: java.io.InputStream): ShareAIAgentRequestProto = ShareAIAgentRequestProto()
}

class ShareAIAgentResponseMarshaller : MethodDescriptor.Marshaller<ShareAIAgentResponseProto> {
    override fun stream(v: ShareAIAgentResponseProto): java.io.InputStream = ByteArrayInputStream(byteArrayOf())
    override fun parse(s: java.io.InputStream): ShareAIAgentResponseProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(s)
        var success = false; var shareCode = ""; var error = ""
        while (!cis.isAtEnd) {
            val tag = cis.readTag(); if (tag == 0) break
            when (com.google.protobuf.WireFormat.getTagFieldNumber(tag)) {
                1 -> success = cis.readBool()
                2 -> shareCode = cis.readString()
                3 -> error = cis.readString()
                else -> cis.skipField(tag)
            }
        }
        return ShareAIAgentResponseProto(success, shareCode, error)
    }
}

class InstallAIAgentRequestMarshaller : MethodDescriptor.Marshaller<InstallAIAgentRequestProto> {
    override fun stream(v: InstallAIAgentRequestProto): java.io.InputStream {
        val baos = ByteArrayOutputStream(); val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        if (v.shareCode.isNotEmpty()) cos.writeString(1, v.shareCode)
        if (v.newName.isNotEmpty()) cos.writeString(2, v.newName)
        cos.flush(); return ByteArrayInputStream(baos.toByteArray())
    }
    override fun parse(s: java.io.InputStream): InstallAIAgentRequestProto = InstallAIAgentRequestProto()
}

class InstallAIAgentResponseMarshaller : MethodDescriptor.Marshaller<InstallAIAgentResponseProto> {
    override fun stream(v: InstallAIAgentResponseProto): java.io.InputStream = ByteArrayInputStream(byteArrayOf())
    override fun parse(s: java.io.InputStream): InstallAIAgentResponseProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(s)
        var success = false; var agentId = ""; var error = ""
        while (!cis.isAtEnd) {
            val tag = cis.readTag(); if (tag == 0) break
            when (com.google.protobuf.WireFormat.getTagFieldNumber(tag)) {
                1 -> success = cis.readBool()
                2 -> agentId = cis.readString()
                3 -> error = cis.readString()
                else -> cis.skipField(tag)
            }
        }
        return InstallAIAgentResponseProto(success, agentId, error)
    }
}

class GetAIUsageStatsRequestMarshaller : MethodDescriptor.Marshaller<GetAIUsageStatsRequestProto> {
    override fun stream(v: GetAIUsageStatsRequestProto): java.io.InputStream = ByteArrayInputStream(byteArrayOf())
    override fun parse(s: java.io.InputStream): GetAIUsageStatsRequestProto = GetAIUsageStatsRequestProto()
}

class GetAIUsageStatsResponseMarshaller : MethodDescriptor.Marshaller<GetAIUsageStatsResponseProto> {
    override fun stream(v: GetAIUsageStatsResponseProto): java.io.InputStream = ByteArrayInputStream(byteArrayOf())
    override fun parse(s: java.io.InputStream): GetAIUsageStatsResponseProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(s)
        val stats = mutableListOf<UsageStatEntryProto>()
        var totalTokens = 0; var totalRequests = 0
        while (!cis.isAtEnd) {
            val tag = cis.readTag(); if (tag == 0) break
            when (com.google.protobuf.WireFormat.getTagFieldNumber(tag)) {
                1 -> {
                    val len = cis.readRawVarint32()
                    val msgBytes = cis.readRawBytes(len)
                    if (msgBytes.isNotEmpty()) {
                        try {
                            val inner = com.google.protobuf.CodedInputStream.newInstance(msgBytes)
                            var agentId = ""; var totalT = 0; var reqCount = 0; var periodStart = ""; var agentName = ""
                            while (!inner.isAtEnd) {
                                val innerTag = inner.readTag()
                                if (innerTag == 0) break
                                when (com.google.protobuf.WireFormat.getTagFieldNumber(innerTag)) {
                                    1 -> agentId = inner.readString()
                                    2 -> totalT = inner.readInt32()
                                    3 -> reqCount = inner.readInt32()
                                    4 -> periodStart = inner.readString()
                                    5 -> agentName = inner.readString()
                                    else -> inner.skipField(innerTag)
                                }
                            }
                            stats.add(UsageStatEntryProto(agentId, totalT, reqCount, periodStart, agentName))
                        } catch (_: Exception) {}
                    }
                }
                2 -> totalTokens = cis.readInt32()
                3 -> totalRequests = cis.readInt32()
                else -> cis.skipField(tag)
            }
        }
        return GetAIUsageStatsResponseProto(stats, totalTokens, totalRequests)
    }
}

// ======= Helpers =======

private fun parseAgentInfoV2(bytes: ByteArray): AgentInfoV2Proto? {
    return try {
        val inner = com.google.protobuf.CodedInputStream.newInstance(bytes)
        var id = ""; var name = ""; var desc = ""; var providerType = ""; var model = ""
        var sysPrompt = ""; var toolsEnabled = false; var ragEnabled = false
        var isPreset = false; var isPublic = false; var maxTokens = 0; var temperature = 0.7f
        var createdBy = ""
        var capsImages = false; var capsTools = false; var capsStreaming = false; var capsMaxTokens = 0
        var installCount = 0; var avgRating = 0f; var reviewCount = 0
        val tags = mutableListOf<String>()
        var originalAgentId = ""; var version = 0; var shareCode = ""
        while (!inner.isAtEnd) {
            val innerTag = inner.readTag()
            if (innerTag == 0) break
            when (com.google.protobuf.WireFormat.getTagFieldNumber(innerTag)) {
                1 -> id = inner.readString()
                2 -> name = inner.readString()
                3 -> desc = inner.readString()
                4 -> providerType = inner.readString()
                5 -> model = inner.readString()
                6 -> sysPrompt = inner.readString()
                7 -> toolsEnabled = inner.readBool()
                8 -> ragEnabled = inner.readBool()
                9 -> isPreset = inner.readBool()
                10 -> isPublic = inner.readBool()
                11 -> maxTokens = inner.readInt32()
                12 -> temperature = inner.readFloat()
                13 -> createdBy = inner.readString()
                14 -> {
                    val capsLen = inner.readRawVarint32()
                    val capsBytes = inner.readRawBytes(capsLen)
                    if (capsBytes.isNotEmpty()) {
                        val capsInner = com.google.protobuf.CodedInputStream.newInstance(capsBytes)
                        while (!capsInner.isAtEnd) {
                            val capsTag = capsInner.readTag()
                            if (capsTag == 0) break
                            when (com.google.protobuf.WireFormat.getTagFieldNumber(capsTag)) {
                                1 -> capsImages = capsInner.readBool()
                                2 -> capsTools = capsInner.readBool()
                                3 -> capsStreaming = capsInner.readBool()
                                4 -> capsMaxTokens = capsInner.readInt32()
                                else -> capsInner.skipField(capsTag)
                            }
                        }
                    }
                }
                15 -> installCount = inner.readInt32()
                16 -> avgRating = inner.readFloat()
                17 -> reviewCount = inner.readInt32()
                18 -> tags.add(inner.readString())
                19 -> originalAgentId = inner.readString()
                20 -> version = inner.readInt32()
                21 -> shareCode = inner.readString()
                else -> inner.skipField(innerTag)
            }
        }
        AgentInfoV2Proto(id, name, desc, providerType, model, sysPrompt, toolsEnabled, ragEnabled,
            isPreset, isPublic, maxTokens, temperature, createdBy,
            AgentCapabilitiesV2Proto(capsImages, capsTools, capsStreaming, capsMaxTokens),
            installCount, avgRating, reviewCount, tags, originalAgentId, version, shareCode)
    } catch (_: Exception) {
        null
    }
}

// ======= GetAIV2ChatHistory =======
// Server proto: messenger.ChatService/GetAIV2ChatHistory

class GetAIV2ChatHistoryRequestMarshaller : MethodDescriptor.Marshaller<GetAIV2ChatHistoryRequestProto> {
    override fun stream(v: GetAIV2ChatHistoryRequestProto): java.io.InputStream {
        val baos = ByteArrayOutputStream(); val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        if (v.sessionId.isNotEmpty()) cos.writeString(1, v.sessionId)
        if (v.limit != 0) cos.writeInt32(2, v.limit)
        cos.flush(); return ByteArrayInputStream(baos.toByteArray())
    }
    override fun parse(s: java.io.InputStream): GetAIV2ChatHistoryRequestProto = GetAIV2ChatHistoryRequestProto()
}

class GetAIV2ChatHistoryResponseMarshaller : MethodDescriptor.Marshaller<GetAIV2ChatHistoryResponseProto> {
    override fun stream(v: GetAIV2ChatHistoryResponseProto): java.io.InputStream = ByteArrayInputStream(byteArrayOf())
    override fun parse(s: java.io.InputStream): GetAIV2ChatHistoryResponseProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(s)
        val messages = mutableListOf<AIV2ChatMessageProto>()
        while (!cis.isAtEnd) {
            val tag = cis.readTag(); if (tag == 0) break
            when (com.google.protobuf.WireFormat.getTagFieldNumber(tag)) {
                1 -> {
                    val len = cis.readRawVarint32()
                    val msgBytes = cis.readRawBytes(len)
                    if (msgBytes.isNotEmpty()) {
                        try {
                            val inner = com.google.protobuf.CodedInputStream.newInstance(msgBytes)
                            var id = 0L; var chatId = ""; var role = ""; var content = ""
                            var agentId = ""; var tokenCount = 0; var modelUsed = ""; var createdAt = ""
                            while (!inner.isAtEnd) {
                                val innerTag = inner.readTag()
                                if (innerTag == 0) break
                                when (com.google.protobuf.WireFormat.getTagFieldNumber(innerTag)) {
                                    1 -> id = inner.readInt64()
                                    2 -> chatId = inner.readString()
                                    3 -> role = inner.readString()
                                    4 -> content = inner.readString()
                                    5 -> agentId = inner.readString()
                                    6 -> tokenCount = inner.readInt32()
                                    7 -> modelUsed = inner.readString()
                                    8 -> createdAt = inner.readString()
                                    else -> inner.skipField(innerTag)
                                }
                            }
                            messages.add(AIV2ChatMessageProto(id, chatId, role, content, agentId, tokenCount, modelUsed, createdAt))
                        } catch (_: Exception) {}
                    }
                }
                else -> cis.skipField(tag)
            }
        }
        return GetAIV2ChatHistoryResponseProto(messages)
    }
}

// ======= ListAIV2Chats =======
// Server proto: messenger.ChatService/ListAIV2Chats

class ListAIV2ChatsRequestMarshaller : MethodDescriptor.Marshaller<ListAIV2ChatsRequestProto> {
    override fun stream(v: ListAIV2ChatsRequestProto): java.io.InputStream = ByteArrayInputStream(byteArrayOf())
    override fun parse(s: java.io.InputStream): ListAIV2ChatsRequestProto = ListAIV2ChatsRequestProto()
}

class ListAIV2ChatsResponseMarshaller : MethodDescriptor.Marshaller<ListAIV2ChatsResponseProto> {
    override fun stream(v: ListAIV2ChatsResponseProto): java.io.InputStream = ByteArrayInputStream(byteArrayOf())
    override fun parse(s: java.io.InputStream): ListAIV2ChatsResponseProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(s)
        val chats = mutableListOf<AIV2ChatInfoProto>()
        while (!cis.isAtEnd) {
            val tag = cis.readTag(); if (tag == 0) break
            when (com.google.protobuf.WireFormat.getTagFieldNumber(tag)) {
                1 -> {
                    val len = cis.readRawVarint32()
                    val msgBytes = cis.readRawBytes(len)
                    if (msgBytes.isNotEmpty()) {
                        try {
                            val inner = com.google.protobuf.CodedInputStream.newInstance(msgBytes)
                            var id = ""; var name = ""; var chatType = ""; var agentId = ""
                            var createdAt = ""; var updatedAt = ""
                            while (!inner.isAtEnd) {
                                val innerTag = inner.readTag()
                                if (innerTag == 0) break
                                when (com.google.protobuf.WireFormat.getTagFieldNumber(innerTag)) {
                                    1 -> id = inner.readString()
                                    2 -> name = inner.readString()
                                    3 -> chatType = inner.readString()
                                    4 -> agentId = inner.readString()
                                    5 -> createdAt = inner.readString()
                                    6 -> updatedAt = inner.readString()
                                    else -> inner.skipField(innerTag)
                                }
                            }
                            chats.add(AIV2ChatInfoProto(id, name, chatType, agentId, createdAt, updatedAt))
                        } catch (_: Exception) {}
                    }
                }
                else -> cis.skipField(tag)
            }
        }
        return ListAIV2ChatsResponseProto(chats)
    }
}
