package lavender.client.android.data.grpc

import lavender.client.android.data.proto.*
import org.junit.Assert.*
import org.junit.Test

class AiV2MarshallersTest {

    // ======= ChatWithAIV2 =======

    @Test
    fun chatWithAIV2RequestMarshaller_serializes() {
        val req = ChatWithAIV2RequestProto(
            sessionId = "sess-abc",
            message = "Hello AI",
            agentId = "developer"
        )
        val marshaller = ChatWithAIV2RequestMarshaller()
        val bytes = marshaller.stream(req).readBytes()
        assertTrue(bytes.isNotEmpty())
    }

    @Test
    fun chatWithAIV2RequestMarshaller_empty() {
        val req = ChatWithAIV2RequestProto()
        val marshaller = ChatWithAIV2RequestMarshaller()
        val bytes = marshaller.stream(req).readBytes()
        assertEquals(0, bytes.size)
    }

    @Test
    fun chatWithAIV2RequestMarshaller_withToolCalls() {
        val tc = ToolCallV2Proto(id = "c1", name = "web_search", arguments = """{"q":"test"}""", result = "ok")
        val req = ChatWithAIV2RequestProto(sessionId = "s1", message = "hi", agentId = "a1", toolCalls = listOf(tc))
        val marshaller = ChatWithAIV2RequestMarshaller()
        val bytes = marshaller.stream(req).readBytes()
        assertTrue(bytes.size > 10)
    }

    @Test
    fun chatWithAIV2ResponseMarshaller_emptyBytes() {
        val marshaller = ChatWithAIV2ResponseMarshaller()
        val parsed = marshaller.parse(java.io.ByteArrayInputStream(byteArrayOf()))
        assertEquals("", parsed.token)
        assertFalse(parsed.finished)
        assertEquals("", parsed.error)
        assertEquals("", parsed.agentId)
        assertEquals("", parsed.agentName)
        assertTrue(parsed.toolCalls.isEmpty())
        assertFalse(parsed.hasRagContext)
        assertEquals("", parsed.modelUsed)
        assertEquals(0, parsed.tokenCount)
    }

    @Test
    fun chatWithAIV2ResponseMarshaller_token() {
        val baos = java.io.ByteArrayOutputStream()
        val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        cos.writeString(1, "Hello")
        cos.flush()
        val parsed = ChatWithAIV2ResponseMarshaller().parse(java.io.ByteArrayInputStream(baos.toByteArray()))
        assertEquals("Hello", parsed.token)
        assertFalse(parsed.finished)
    }

    @Test
    fun chatWithAIV2ResponseMarshaller_finished() {
        val baos = java.io.ByteArrayOutputStream()
        val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        cos.writeBool(2, true)
        cos.flush()
        val parsed = ChatWithAIV2ResponseMarshaller().parse(java.io.ByteArrayInputStream(baos.toByteArray()))
        assertTrue(parsed.finished)
    }

    @Test
    fun chatWithAIV2ResponseMarshaller_error() {
        val baos = java.io.ByteArrayOutputStream()
        val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        cos.writeString(3, "Something went wrong")
        cos.flush()
        val parsed = ChatWithAIV2ResponseMarshaller().parse(java.io.ByteArrayInputStream(baos.toByteArray()))
        assertEquals("Something went wrong", parsed.error)
    }

    @Test
    fun chatWithAIV2ResponseMarshaller_agentInfo() {
        val baos = java.io.ByteArrayOutputStream()
        val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        cos.writeString(4, "agent-123")
        cos.writeString(5, "Developer")
        cos.flush()
        val parsed = ChatWithAIV2ResponseMarshaller().parse(java.io.ByteArrayInputStream(baos.toByteArray()))
        assertEquals("agent-123", parsed.agentId)
        assertEquals("Developer", parsed.agentName)
    }

    @Test
    fun chatWithAIV2ResponseMarshaller_allFields() {
        val toolCallBaos = java.io.ByteArrayOutputStream()
        val toolCallCos = com.google.protobuf.CodedOutputStream.newInstance(toolCallBaos)
        toolCallCos.writeString(1, "call_1")
        toolCallCos.writeString(2, "web_search")
        toolCallCos.writeString(3, """{"q":"test"}""")
        toolCallCos.flush()

        val baos = java.io.ByteArrayOutputStream()
        val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        cos.writeString(1, "result")
        cos.writeBool(2, true)
        cos.writeString(3, "err")
        cos.writeString(4, "a1")
        cos.writeString(5, "Agent1")
        cos.writeTag(6, com.google.protobuf.WireFormat.WIRETYPE_LENGTH_DELIMITED)
        cos.writeUInt32NoTag(toolCallBaos.toByteArray().size)
        cos.writeRawBytes(toolCallBaos.toByteArray())
        cos.writeBool(7, true)
        cos.writeString(8, "claude-sonnet-4")
        cos.writeInt32(9, 42)
        cos.flush()

        val parsed = ChatWithAIV2ResponseMarshaller().parse(java.io.ByteArrayInputStream(baos.toByteArray()))
        assertEquals("result", parsed.token)
        assertTrue(parsed.finished)
        assertEquals("err", parsed.error)
        assertEquals("a1", parsed.agentId)
        assertEquals("Agent1", parsed.agentName)
        assertEquals(1, parsed.toolCalls.size)
        assertEquals("call_1", parsed.toolCalls[0].id)
        assertEquals("web_search", parsed.toolCalls[0].name)
        assertEquals("""{"q":"test"}""", parsed.toolCalls[0].arguments)
        assertTrue(parsed.hasRagContext)
        assertEquals("claude-sonnet-4", parsed.modelUsed)
        assertEquals(42, parsed.tokenCount)
    }

    @Test
    fun chatWithAIV2ResponseMarshaller_unknownFieldsSkipped() {
        val baos = java.io.ByteArrayOutputStream()
        val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        cos.writeString(1, "ok")
        cos.writeBool(2, true)
        cos.writeString(99, "unknown_field")
        cos.flush()
        val parsed = ChatWithAIV2ResponseMarshaller().parse(java.io.ByteArrayInputStream(baos.toByteArray()))
        assertEquals("ok", parsed.token)
        assertTrue(parsed.finished)
    }

    // ======= Agent CRUD request serialization =======

    @Test
    fun createAIAgentRequestMarshaller_serializes() {
        val req = CreateAIAgentRequestProto(
            name = "Test Agent", description = "A test",
            providerType = "openrouter", model = "claude-sonnet-4",
            toolsEnabled = true, toolWhitelist = listOf("web_search", "web_fetch")
        )
        val bytes = CreateAIAgentRequestMarshaller().stream(req).readBytes()
        assertTrue(bytes.isNotEmpty())
    }

    @Test
    fun createAIAgentResponseMarshaller_success() {
        val baos = java.io.ByteArrayOutputStream()
        val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        cos.writeBool(1, true)
        cos.writeString(2, "agent-new")
        cos.flush()
        val parsed = CreateAIAgentResponseMarshaller().parse(java.io.ByteArrayInputStream(baos.toByteArray()))
        assertTrue(parsed.success)
        assertEquals("agent-new", parsed.agentId)
    }

    @Test
    fun createAIAgentResponseMarshaller_error() {
        val baos = java.io.ByteArrayOutputStream()
        val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        cos.writeBool(1, false)
        cos.writeString(3, "name taken")
        cos.flush()
        val parsed = CreateAIAgentResponseMarshaller().parse(java.io.ByteArrayInputStream(baos.toByteArray()))
        assertFalse(parsed.success)
        assertEquals("name taken", parsed.error)
    }

    @Test
    fun updateAIAgentRequestMarshaller_serializes() {
        val req = UpdateAIAgentRequestProto(agentId = "a1", name = "Updated", model = "gpt-4o")
        val bytes = UpdateAIAgentRequestMarshaller().stream(req).readBytes()
        assertTrue(bytes.isNotEmpty())
    }

    @Test
    fun updateAIAgentResponseMarshaller_empty() {
        val parsed = UpdateAIAgentResponseMarshaller().parse(java.io.ByteArrayInputStream(byteArrayOf()))
        assertFalse(parsed.success)
        assertEquals("", parsed.error)
    }

    @Test
    fun deleteAIAgentRequestMarshaller_serializes() {
        val req = DeleteAIAgentRequestProto(agentId = "agent-del")
        val bytes = DeleteAIAgentRequestMarshaller().stream(req).readBytes()
        assertTrue(bytes.isNotEmpty())
    }

    @Test
    fun deleteAIAgentResponseMarshaller_success() {
        val baos = java.io.ByteArrayOutputStream()
        val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        cos.writeBool(1, true)
        cos.flush()
        val parsed = DeleteAIAgentResponseMarshaller().parse(java.io.ByteArrayInputStream(baos.toByteArray()))
        assertTrue(parsed.success)
    }

    @Test
    fun getAIAgentRequestMarshaller_serializes() {
        val req = GetAIAgentRequestProto(agentId = "get-me")
        val bytes = GetAIAgentRequestMarshaller().stream(req).readBytes()
        assertTrue(bytes.isNotEmpty())
    }

    @Test
    fun listAIAgentsRequestMarshaller_empty() {
        val parsed = ListAIAgentsRequestMarshaller().parse(java.io.ByteArrayInputStream(byteArrayOf()))
        assertFalse(parsed.includePublic)
    }

    @Test
    fun listAIAgentsResponseMarshaller_empty() {
        val parsed = ListAIAgentsResponseMarshaller().parse(java.io.ByteArrayInputStream(byteArrayOf()))
        assertTrue(parsed.agents.isEmpty())
    }

    @Test
    fun listAIAgentsResponseMarshaller_withAgent() {
        val agentBaos = java.io.ByteArrayOutputStream()
        val agentCos = com.google.protobuf.CodedOutputStream.newInstance(agentBaos)
        agentCos.writeString(1, "id-1")
        agentCos.writeString(2, "Developer")
        agentCos.writeString(3, "Code helper")
        agentCos.writeString(4, "openrouter")
        agentCos.writeString(5, "claude-sonnet-4")
        agentCos.writeString(6, "You code")
        agentCos.writeBool(7, true)
        agentCos.writeBool(8, false)
        agentCos.writeBool(9, true)
        agentCos.flush()

        val baos = java.io.ByteArrayOutputStream()
        val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        cos.writeTag(1, com.google.protobuf.WireFormat.WIRETYPE_LENGTH_DELIMITED)
        cos.writeUInt32NoTag(agentBaos.toByteArray().size)
        cos.writeRawBytes(agentBaos.toByteArray())
        cos.flush()

        val parsed = ListAIAgentsResponseMarshaller().parse(java.io.ByteArrayInputStream(baos.toByteArray()))
        assertEquals(1, parsed.agents.size)
        assertEquals("id-1", parsed.agents[0].id)
        assertEquals("Developer", parsed.agents[0].name)
        assertEquals("Code helper", parsed.agents[0].description)
        assertEquals("openrouter", parsed.agents[0].providerType)
        assertEquals("claude-sonnet-4", parsed.agents[0].model)
        assertTrue(parsed.agents[0].toolsEnabled)
        assertTrue(parsed.agents[0].isPreset)
    }

    @Test
    fun listAIAgentsResponseMarshaller_withCapabilities() {
        val agentBaos = java.io.ByteArrayOutputStream()
        val agentCos = com.google.protobuf.CodedOutputStream.newInstance(agentBaos)
        agentCos.writeString(1, "a1")
        agentCos.writeString(2, "A")
        agentCos.writeString(3, "D")
        agentCos.writeString(4, "mimo")
        agentCos.writeString(5, "m")
        agentCos.writeString(6, "sp")
        agentCos.writeBool(7, false)
        agentCos.writeBool(8, true)
        agentCos.writeBool(9, false)
        agentCos.writeInt32(11, 4096)
        agentCos.writeFloat(12, 0.5f)
        agentCos.writeString(13, "user1")
        val capsBaos = java.io.ByteArrayOutputStream()
        val capsCos = com.google.protobuf.CodedOutputStream.newInstance(capsBaos)
        capsCos.writeBool(1, true)
        capsCos.writeBool(2, true)
        capsCos.writeBool(3, false)
        capsCos.writeInt32(4, 128000)
        capsCos.flush()
        agentCos.writeTag(14, com.google.protobuf.WireFormat.WIRETYPE_LENGTH_DELIMITED)
        agentCos.writeUInt32NoTag(capsBaos.toByteArray().size)
        agentCos.writeRawBytes(capsBaos.toByteArray())
        agentCos.flush()

        val baos = java.io.ByteArrayOutputStream()
        val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        cos.writeTag(1, com.google.protobuf.WireFormat.WIRETYPE_LENGTH_DELIMITED)
        cos.writeUInt32NoTag(agentBaos.toByteArray().size)
        cos.writeRawBytes(agentBaos.toByteArray())
        cos.flush()

        val parsed = ListAIAgentsResponseMarshaller().parse(java.io.ByteArrayInputStream(baos.toByteArray()))
        assertEquals(1, parsed.agents.size)
        val agent = parsed.agents[0]
        assertEquals(4096, agent.maxTokens)
        assertEquals(0.5f, agent.temperature, 0.001f)
        assertEquals("user1", agent.createdBy)
        assertTrue(agent.capabilities.supportsImages)
        assertTrue(agent.capabilities.supportsTools)
        assertFalse(agent.capabilities.supportsStreaming)
        assertEquals(128000, agent.capabilities.maxTokens)
    }

    @Test
    fun cloneAIAgentRequestMarshaller_serializes() {
        val req = CloneAIAgentRequestProto(agentId = "src", newName = "Clone")
        val bytes = CloneAIAgentRequestMarshaller().stream(req).readBytes()
        assertTrue(bytes.isNotEmpty())
    }

    @Test
    fun cloneAIAgentResponseMarshaller_success() {
        val baos = java.io.ByteArrayOutputStream()
        val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        cos.writeBool(1, true)
        cos.writeString(2, "new-id")
        cos.flush()
        val parsed = CloneAIAgentResponseMarshaller().parse(java.io.ByteArrayInputStream(baos.toByteArray()))
        assertTrue(parsed.success)
        assertEquals("new-id", parsed.agentId)
    }

    // ======= Tools =======

    @Test
    fun listAIToolsResponseMarshaller_empty() {
        val parsed = ListAIToolsResponseMarshaller().parse(java.io.ByteArrayInputStream(byteArrayOf()))
        assertTrue(parsed.tools.isEmpty())
    }

    @Test
    fun listAIToolsResponseMarshaller_withTools() {
        val toolBaos = java.io.ByteArrayOutputStream()
        val toolCos = com.google.protobuf.CodedOutputStream.newInstance(toolBaos)
        toolCos.writeString(1, "web_search")
        toolCos.writeString(2, "Search the web")
        toolCos.writeString(3, """{"type":"object"}""")
        toolCos.writeString(4, "user")
        toolCos.flush()

        val baos = java.io.ByteArrayOutputStream()
        val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        cos.writeTag(1, com.google.protobuf.WireFormat.WIRETYPE_LENGTH_DELIMITED)
        cos.writeUInt32NoTag(toolBaos.toByteArray().size)
        cos.writeRawBytes(toolBaos.toByteArray())
        cos.flush()

        val parsed = ListAIToolsResponseMarshaller().parse(java.io.ByteArrayInputStream(baos.toByteArray()))
        assertEquals(1, parsed.tools.size)
        assertEquals("web_search", parsed.tools[0].name)
        assertEquals("Search the web", parsed.tools[0].description)
        assertEquals("user", parsed.tools[0].requiredRole)
    }

    @Test
    fun listAIToolsResponseMarshaller_multipleTools() {
        fun writeTool(name: String): ByteArray {
            val baos = java.io.ByteArrayOutputStream()
            val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
            cos.writeString(1, name)
            cos.writeString(2, "desc for $name")
            cos.flush()
            return baos.toByteArray()
        }

        val baos = java.io.ByteArrayOutputStream()
        val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        for (toolName in listOf("web_search", "web_fetch", "search_messages")) {
            val toolBytes = writeTool(toolName)
            cos.writeTag(1, com.google.protobuf.WireFormat.WIRETYPE_LENGTH_DELIMITED)
            cos.writeUInt32NoTag(toolBytes.size)
            cos.writeRawBytes(toolBytes)
        }
        cos.flush()

        val parsed = ListAIToolsResponseMarshaller().parse(java.io.ByteArrayInputStream(baos.toByteArray()))
        assertEquals(3, parsed.tools.size)
        assertEquals("web_search", parsed.tools[0].name)
        assertEquals("web_fetch", parsed.tools[1].name)
        assertEquals("search_messages", parsed.tools[2].name)
    }
}
