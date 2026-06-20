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
    fun chatWithAIV2ResponseMarshaller_imageUrl() {
        val baos = java.io.ByteArrayOutputStream()
        val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        cos.writeString(10, "https://api.reve.com/v1/image/abc123.png")
        cos.flush()
        val parsed = ChatWithAIV2ResponseMarshaller().parse(java.io.ByteArrayInputStream(baos.toByteArray()))
        assertEquals("https://api.reve.com/v1/image/abc123.png", parsed.imageUrl)
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
    fun listAIAgentsRequestMarshaller_serializes_includePublic() {
        val req = ListAIAgentsRequestProto(includePublic = true)
        val bytes = ListAIAgentsRequestMarshaller().stream(req).readBytes()
        assertTrue(bytes.isNotEmpty())
    }

    @Test
    fun listAIAgentsRequestMarshaller_empty_when_false() {
        val req = ListAIAgentsRequestProto(includePublic = false)
        val bytes = ListAIAgentsRequestMarshaller().stream(req).readBytes()
        assertEquals(0, bytes.size)
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

    // ======= Marketplace =======

    @Test
    fun rateAIAgentRequestMarshaller_serializes() {
        val req = RateAIAgentRequestProto(agentId = "agent-1", rating = 5, review = "Great!")
        val bytes = RateAIAgentRequestMarshaller().stream(req).readBytes()
        assertTrue(bytes.isNotEmpty())
    }

    @Test
    fun rateAIAgentRequestMarshaller_empty() {
        val req = RateAIAgentRequestProto()
        val bytes = RateAIAgentRequestMarshaller().stream(req).readBytes()
        assertEquals(0, bytes.size)
    }

    @Test
    fun rateAIAgentResponseMarshaller_empty() {
        val parsed = RateAIAgentResponseMarshaller().parse(java.io.ByteArrayInputStream(byteArrayOf()))
        assertFalse(parsed.success)
        assertEquals("", parsed.error)
        assertEquals(0f, parsed.avgRating, 0.001f)
        assertEquals(0, parsed.reviewCount)
    }

    @Test
    fun rateAIAgentResponseMarshaller_allFields() {
        val baos = java.io.ByteArrayOutputStream()
        val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        cos.writeBool(1, true)
        cos.writeString(2, "already rated")
        cos.writeFloat(3, 4.5f)
        cos.writeInt32(4, 12)
        cos.flush()
        val parsed = RateAIAgentResponseMarshaller().parse(java.io.ByteArrayInputStream(baos.toByteArray()))
        assertTrue(parsed.success)
        assertEquals("already rated", parsed.error)
        assertEquals(4.5f, parsed.avgRating, 0.001f)
        assertEquals(12, parsed.reviewCount)
    }

    @Test
    fun getAIAgentReviewsRequestMarshaller_serializes() {
        val req = GetAIAgentReviewsRequestProto(agentId = "agent-1", limit = 10)
        val bytes = GetAIAgentReviewsRequestMarshaller().stream(req).readBytes()
        assertTrue(bytes.isNotEmpty())
    }

    @Test
    fun getAIAgentReviewsRequestMarshaller_defaults() {
        val req = GetAIAgentReviewsRequestProto()
        val bytes = GetAIAgentReviewsRequestMarshaller().stream(req).readBytes()
        assertEquals(0, bytes.size)
    }

    @Test
    fun getAIAgentReviewsResponseMarshaller_empty() {
        val parsed = GetAIAgentReviewsResponseMarshaller().parse(java.io.ByteArrayInputStream(byteArrayOf()))
        assertTrue(parsed.reviews.isEmpty())
        assertEquals(0f, parsed.avgRating, 0.001f)
        assertEquals(0, parsed.reviewCount)
    }

    @Test
    fun getAIAgentReviewsResponseMarshaller_withReviews() {
        fun writeReview(userId: String, rating: Int, review: String, createdAt: String): ByteArray {
            val baos = java.io.ByteArrayOutputStream()
            val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
            cos.writeString(1, userId)
            cos.writeInt32(2, rating)
            cos.writeString(3, review)
            cos.writeString(4, createdAt)
            cos.flush()
            return baos.toByteArray()
        }

        val baos = java.io.ByteArrayOutputStream()
        val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        val review1 = writeReview("u1", 5, "Awesome!", "2026-01-01")
        val review2 = writeReview("u2", 4, "Good", "2026-01-02")
        cos.writeTag(1, com.google.protobuf.WireFormat.WIRETYPE_LENGTH_DELIMITED)
        cos.writeUInt32NoTag(review1.size)
        cos.writeRawBytes(review1)
        cos.writeTag(1, com.google.protobuf.WireFormat.WIRETYPE_LENGTH_DELIMITED)
        cos.writeUInt32NoTag(review2.size)
        cos.writeRawBytes(review2)
        cos.writeFloat(2, 4.5f)
        cos.writeInt32(3, 2)
        cos.flush()

        val parsed = GetAIAgentReviewsResponseMarshaller().parse(java.io.ByteArrayInputStream(baos.toByteArray()))
        assertEquals(2, parsed.reviews.size)
        assertEquals("u1", parsed.reviews[0].userId)
        assertEquals(5, parsed.reviews[0].rating)
        assertEquals("Awesome!", parsed.reviews[0].review)
        assertEquals("2026-01-01", parsed.reviews[0].createdAt)
        assertEquals("u2", parsed.reviews[1].userId)
        assertEquals(4, parsed.reviews[1].rating)
        assertEquals(4.5f, parsed.avgRating, 0.001f)
        assertEquals(2, parsed.reviewCount)
    }

    @Test
    fun listMarketplaceAgentsRequestMarshaller_serializes() {
        val req = ListMarketplaceAgentsRequestProto(query = "test", limit = 10, offset = 5)
        val bytes = ListMarketplaceAgentsRequestMarshaller().stream(req).readBytes()
        assertTrue(bytes.isNotEmpty())
    }

    @Test
    fun listMarketplaceAgentsRequestMarshaller_empty() {
        val req = ListMarketplaceAgentsRequestProto()
        val bytes = ListMarketplaceAgentsRequestMarshaller().stream(req).readBytes()
        assertEquals(0, bytes.size)
    }

    @Test
    fun listMarketplaceAgentsResponseMarshaller_empty() {
        val parsed = ListMarketplaceAgentsResponseMarshaller().parse(java.io.ByteArrayInputStream(byteArrayOf()))
        assertTrue(parsed.agents.isEmpty())
        assertEquals(0, parsed.total)
    }

    @Test
    fun listMarketplaceAgentsResponseMarshaller_withAgents() {
        fun writeAgent(id: String, name: String): ByteArray {
            val baos = java.io.ByteArrayOutputStream()
            val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
            cos.writeString(1, id)
            cos.writeString(2, name)
            cos.writeString(3, "desc")
            cos.writeString(4, "openrouter")
            cos.writeString(5, "claude-sonnet-4")
            cos.flush()
            return baos.toByteArray()
        }

        val baos = java.io.ByteArrayOutputStream()
        val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        val agent1 = writeAgent("a1", "Agent 1")
        val agent2 = writeAgent("a2", "Agent 2")
        cos.writeTag(1, com.google.protobuf.WireFormat.WIRETYPE_LENGTH_DELIMITED)
        cos.writeUInt32NoTag(agent1.size)
        cos.writeRawBytes(agent1)
        cos.writeTag(1, com.google.protobuf.WireFormat.WIRETYPE_LENGTH_DELIMITED)
        cos.writeUInt32NoTag(agent2.size)
        cos.writeRawBytes(agent2)
        cos.writeInt32(2, 50)
        cos.flush()

        val parsed = ListMarketplaceAgentsResponseMarshaller().parse(java.io.ByteArrayInputStream(baos.toByteArray()))
        assertEquals(2, parsed.agents.size)
        assertEquals("a1", parsed.agents[0].id)
        assertEquals("Agent 1", parsed.agents[0].name)
        assertEquals("a2", parsed.agents[1].id)
        assertEquals(50, parsed.total)
    }

    @Test
    fun getAIAgentStatsRequestMarshaller_serializes() {
        val req = GetAIAgentStatsRequestProto(agentId = "agent-1")
        val bytes = GetAIAgentStatsRequestMarshaller().stream(req).readBytes()
        assertTrue(bytes.isNotEmpty())
    }

    @Test
    fun getAIAgentStatsResponseMarshaller_empty() {
        val parsed = GetAIAgentStatsResponseMarshaller().parse(java.io.ByteArrayInputStream(byteArrayOf()))
        assertEquals(0, parsed.installCount)
        assertEquals(0f, parsed.avgRating, 0.001f)
        assertEquals(0, parsed.reviewCount)
    }

    @Test
    fun getAIAgentStatsResponseMarshaller_allFields() {
        val baos = java.io.ByteArrayOutputStream()
        val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        cos.writeInt32(1, 100)
        cos.writeFloat(2, 4.2f)
        cos.writeInt32(3, 25)
        cos.flush()
        val parsed = GetAIAgentStatsResponseMarshaller().parse(java.io.ByteArrayInputStream(baos.toByteArray()))
        assertEquals(100, parsed.installCount)
        assertEquals(4.2f, parsed.avgRating, 0.001f)
        assertEquals(25, parsed.reviewCount)
    }

    @Test
    fun shareAIAgentRequestMarshaller_serializes() {
        val req = ShareAIAgentRequestProto(agentId = "agent-1")
        val bytes = ShareAIAgentRequestMarshaller().stream(req).readBytes()
        assertTrue(bytes.isNotEmpty())
    }

    @Test
    fun shareAIAgentResponseMarshaller_empty() {
        val parsed = ShareAIAgentResponseMarshaller().parse(java.io.ByteArrayInputStream(byteArrayOf()))
        assertFalse(parsed.success)
        assertEquals("", parsed.shareCode)
    }

    @Test
    fun shareAIAgentResponseMarshaller_allFields() {
        val baos = java.io.ByteArrayOutputStream()
        val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        cos.writeBool(1, true)
        cos.writeString(2, "abc123")
        cos.flush()
        val parsed = ShareAIAgentResponseMarshaller().parse(java.io.ByteArrayInputStream(baos.toByteArray()))
        assertTrue(parsed.success)
        assertEquals("abc123", parsed.shareCode)
    }

    @Test
    fun installAIAgentRequestMarshaller_serializes() {
        val req = InstallAIAgentRequestProto(shareCode = "abc123", newName = "My Agent")
        val bytes = InstallAIAgentRequestMarshaller().stream(req).readBytes()
        assertTrue(bytes.isNotEmpty())
    }

    @Test
    fun installAIAgentRequestMarshaller_empty() {
        val req = InstallAIAgentRequestProto()
        val bytes = InstallAIAgentRequestMarshaller().stream(req).readBytes()
        assertEquals(0, bytes.size)
    }

    @Test
    fun installAIAgentResponseMarshaller_empty() {
        val parsed = InstallAIAgentResponseMarshaller().parse(java.io.ByteArrayInputStream(byteArrayOf()))
        assertFalse(parsed.success)
        assertEquals("", parsed.agentId)
        assertEquals("", parsed.error)
    }

    @Test
    fun installAIAgentResponseMarshaller_allFields() {
        val baos = java.io.ByteArrayOutputStream()
        val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        cos.writeBool(1, true)
        cos.writeString(2, "new-agent-id")
        cos.flush()
        val parsed = InstallAIAgentResponseMarshaller().parse(java.io.ByteArrayInputStream(baos.toByteArray()))
        assertTrue(parsed.success)
        assertEquals("new-agent-id", parsed.agentId)
    }

    @Test
    fun installAIAgentResponseMarshaller_error() {
        val baos = java.io.ByteArrayOutputStream()
        val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        cos.writeBool(1, false)
        cos.writeString(3, "Invalid share code")
        cos.flush()
        val parsed = InstallAIAgentResponseMarshaller().parse(java.io.ByteArrayInputStream(baos.toByteArray()))
        assertFalse(parsed.success)
        assertEquals("Invalid share code", parsed.error)
    }

    @Test
    fun getAIUsageStatsRequestMarshaller_empty() {
        val parsed = GetAIUsageStatsRequestMarshaller().parse(java.io.ByteArrayInputStream(byteArrayOf()))
        assertFalse(parsed.dummy)
    }

    @Test
    fun getAIUsageStatsResponseMarshaller_empty() {
        val parsed = GetAIUsageStatsResponseMarshaller().parse(java.io.ByteArrayInputStream(byteArrayOf()))
        assertTrue(parsed.stats.isEmpty())
        assertEquals(0, parsed.totalTokens)
        assertEquals(0, parsed.totalRequests)
    }

    @Test
    fun getAIUsageStatsResponseMarshaller_withStats() {
        fun writeStatEntry(agentId: String, totalTokens: Int, requests: Int, period: String, agentName: String): ByteArray {
            val baos = java.io.ByteArrayOutputStream()
            val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
            cos.writeString(1, agentId)
            cos.writeInt32(2, totalTokens)
            cos.writeInt32(3, requests)
            cos.writeString(4, period)
            cos.writeString(5, agentName)
            cos.flush()
            return baos.toByteArray()
        }

        val baos = java.io.ByteArrayOutputStream()
        val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        val stat1 = writeStatEntry("a1", 1500, 30, "2026-01", "Agent 1")
        val stat2 = writeStatEntry("a2", 2500, 50, "2026-01", "Agent 2")
        cos.writeTag(1, com.google.protobuf.WireFormat.WIRETYPE_LENGTH_DELIMITED)
        cos.writeUInt32NoTag(stat1.size)
        cos.writeRawBytes(stat1)
        cos.writeTag(1, com.google.protobuf.WireFormat.WIRETYPE_LENGTH_DELIMITED)
        cos.writeUInt32NoTag(stat2.size)
        cos.writeRawBytes(stat2)
        cos.writeInt32(2, 4000)
        cos.writeInt32(3, 80)
        cos.flush()

        val parsed = GetAIUsageStatsResponseMarshaller().parse(java.io.ByteArrayInputStream(baos.toByteArray()))
        assertEquals(2, parsed.stats.size)
        assertEquals("a1", parsed.stats[0].agentId)
        assertEquals("Agent 1", parsed.stats[0].agentName)
        assertEquals(1500, parsed.stats[0].totalTokens)
        assertEquals(30, parsed.stats[0].requestCount)
        assertEquals("2026-01", parsed.stats[0].periodStart)
        assertEquals("a2", parsed.stats[1].agentId)
        assertEquals(2500, parsed.stats[1].totalTokens)
        assertEquals(4000, parsed.totalTokens)
        assertEquals(80, parsed.totalRequests)
    }

    @Test
    fun rateAIAgentResponseMarshaller_unknownFieldsSkipped() {
        val baos = java.io.ByteArrayOutputStream()
        val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        cos.writeBool(1, true)
        cos.writeString(2, "error msg")
        cos.writeFloat(3, 3.0f)
        cos.writeString(99, "unknown")
        cos.flush()
        val parsed = RateAIAgentResponseMarshaller().parse(java.io.ByteArrayInputStream(baos.toByteArray()))
        assertTrue(parsed.success)
        assertEquals("error msg", parsed.error)
        assertEquals(3.0f, parsed.avgRating, 0.001f)
    }

    @Test
    fun listMarketplaceAgentsResponseMarshaller_unknownFieldsSkipped() {
        val baos = java.io.ByteArrayOutputStream()
        val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        cos.writeInt32(2, 10)
        cos.writeString(99, "unknown")
        cos.flush()
        val parsed = ListMarketplaceAgentsResponseMarshaller().parse(java.io.ByteArrayInputStream(baos.toByteArray()))
        assertEquals(10, parsed.total)
    }

    // ======= AI Chat Settings =======

    @Test
    fun getAIChatSettingsRequestMarshaller_serializes() {
        val req = GetAIChatSettingsRequestProto(sessionId = "sess-abc")
        val bytes = GetAIChatSettingsRequestMarshaller().stream(req).readBytes()
        assertTrue(bytes.isNotEmpty())
    }

    @Test
    fun getAIChatSettingsRequestMarshaller_empty() {
        val req = GetAIChatSettingsRequestProto()
        val bytes = GetAIChatSettingsRequestMarshaller().stream(req).readBytes()
        assertEquals(0, bytes.size)
    }

    @Test
    fun aiChatSettingsResponseMarshaller_empty() {
        val parsed = AIChatSettingsResponseMarshaller().parse(java.io.ByteArrayInputStream(byteArrayOf()))
        assertEquals("", parsed.sessionId)
        assertEquals("", parsed.userApiKey)
        assertEquals("", parsed.model)
        assertFalse(parsed.isUsingCustomKey)
        assertEquals(0, parsed.remaining)
        assertEquals(0, parsed.limit)
        assertEquals(0, parsed.windowSeconds)
    }

    @Test
    fun aiChatSettingsResponseMarshaller_allFields() {
        val baos = java.io.ByteArrayOutputStream()
        val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        cos.writeString(1, "sess-123")
        cos.writeString(2, "sk-or-v1-xxx")
        cos.writeString(3, "anthropic/claude-sonnet-4")
        cos.writeBool(4, true)
        cos.writeInt32(5, 8)
        cos.writeInt32(6, 10)
        cos.writeInt32(7, 60)
        cos.flush()
        val parsed = AIChatSettingsResponseMarshaller().parse(java.io.ByteArrayInputStream(baos.toByteArray()))
        assertEquals("sess-123", parsed.sessionId)
        assertEquals("sk-or-v1-xxx", parsed.userApiKey)
        assertEquals("anthropic/claude-sonnet-4", parsed.model)
        assertTrue(parsed.isUsingCustomKey)
        assertEquals(8, parsed.remaining)
        assertEquals(10, parsed.limit)
        assertEquals(60, parsed.windowSeconds)
    }

    @Test
    fun updateAIChatSettingsRequestMarshaller_serializes() {
        val req = UpdateAIChatSettingsRequestProto(sessionId = "s1", apiKey = "sk-or-v1-xxx", model = "gpt-4o")
        val bytes = UpdateAIChatSettingsRequestMarshaller().stream(req).readBytes()
        assertTrue(bytes.isNotEmpty())
    }

    @Test
    fun updateAIChatSettingsRequestMarshaller_empty() {
        val req = UpdateAIChatSettingsRequestProto()
        val bytes = UpdateAIChatSettingsRequestMarshaller().stream(req).readBytes()
        assertEquals(0, bytes.size)
    }

    @Test
    fun updateAIChatSettingsResponseMarshaller_empty() {
        val parsed = UpdateAIChatSettingsResponseMarshaller().parse(java.io.ByteArrayInputStream(byteArrayOf()))
        assertFalse(parsed.success)
        assertEquals("", parsed.message)
    }

    @Test
    fun updateAIChatSettingsResponseMarshaller_success() {
        val baos = java.io.ByteArrayOutputStream()
        val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        cos.writeBool(1, true)
        cos.writeString(2, "Settings updated")
        cos.flush()
        val parsed = UpdateAIChatSettingsResponseMarshaller().parse(java.io.ByteArrayInputStream(baos.toByteArray()))
        assertTrue(parsed.success)
        assertEquals("Settings updated", parsed.message)
    }
}
