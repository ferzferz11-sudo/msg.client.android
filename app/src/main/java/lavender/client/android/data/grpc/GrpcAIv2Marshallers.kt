package lavender.client.android.data.grpc

import io.grpc.MethodDescriptor
import lavender.client.android.data.proto.*
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

// ======= AI Services v2 Marshallers =======
// Server proto: messenger.AIService/*
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
        var token = ""; var finished = false; var error = ""; var agentId = ""; var agentName = ""
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
                else -> cis.skipField(tag)
            }
        }
        return ChatWithAIV2ResponseProto(token, finished, error, agentId, agentName, toolCalls, hasRagContext, modelUsed, tokenCount)
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
    override fun stream(v: ListAIAgentsRequestProto): java.io.InputStream = ByteArrayInputStream(byteArrayOf())
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

// ======= Helpers =======

private fun parseAgentInfoV2(bytes: ByteArray): AgentInfoV2Proto? {
    return try {
        val inner = com.google.protobuf.CodedInputStream.newInstance(bytes)
        var id = ""; var name = ""; var desc = ""; var providerType = ""; var model = ""
        var sysPrompt = ""; var toolsEnabled = false; var ragEnabled = false
        var isPreset = false; var isPublic = false; var maxTokens = 0; var temperature = 0.7f
        var createdBy = ""
        var capsImages = false; var capsTools = false; var capsStreaming = false; var capsMaxTokens = 0
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
                else -> inner.skipField(innerTag)
            }
        }
        AgentInfoV2Proto(id, name, desc, providerType, model, sysPrompt, toolsEnabled, ragEnabled,
            isPreset, isPublic, maxTokens, temperature, createdBy,
            AgentCapabilitiesV2Proto(capsImages, capsTools, capsStreaming, capsMaxTokens))
    } catch (_: Exception) {
        null
    }
}
