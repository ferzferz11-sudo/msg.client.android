package lavender.client.android.data.ai

import lavender.client.android.data.proto.*
import org.junit.Assert.*
import org.junit.Test

class AiV2DomainExtensionsTest {

    // ======= AgentInfoV2Proto.toDomain =======

    @Test
    fun agentInfoV2Proto_toDomain() {
        val proto = AgentInfoV2Proto(
            id = "a1",
            name = "Developer",
            description = "Code helper",
            providerType = "openrouter",
            model = "claude-sonnet-4",
            systemPrompt = "You code",
            toolsEnabled = true,
            ragEnabled = false,
            isPreset = true,
            isPublic = false,
            maxTokens = 8192,
            temperature = 0.3f,
            createdBy = "user1",
            capabilities = AgentCapabilitiesV2Proto(
                supportsImages = true,
                supportsTools = true,
                supportsStreaming = true,
                maxTokens = 128000
            )
        )
        val domain = proto.toDomain()
        assertEquals("a1", domain.id)
        assertEquals("Developer", domain.name)
        assertEquals("Code helper", domain.description)
        assertEquals(AiProviderType.OPENROUTER, domain.providerType)
        assertEquals("claude-sonnet-4", domain.model)
        assertEquals("You code", domain.systemPrompt)
        assertTrue(domain.toolsEnabled)
        assertFalse(domain.ragEnabled)
        assertTrue(domain.isPreset)
        assertFalse(domain.isPublic)
        assertEquals(8192, domain.maxTokens)
        assertEquals(0.3f, domain.temperature, 0.001f)
        assertEquals("user1", domain.createdBy)
        assertTrue(domain.capabilities.supportsImages)
        assertTrue(domain.capabilities.supportsTools)
        assertTrue(domain.capabilities.supportsStreaming)
        assertEquals(128000, domain.capabilities.maxTokens)
    }

    @Test
    fun agentInfoV2Proto_toDomain_unknownProvider() {
        val proto = AgentInfoV2Proto(providerType = "custom_type")
        val domain = proto.toDomain()
        assertEquals(AiProviderType.OPENROUTER, domain.providerType)
    }

    // ======= AgentCapabilitiesV2Proto.toDomain =======

    @Test
    fun agentCapabilitiesV2Proto_toDomain() {
        val proto = AgentCapabilitiesV2Proto(
            supportsImages = false,
            supportsTools = true,
            supportsStreaming = false,
            maxTokens = 4096
        )
        val domain = proto.toDomain()
        assertFalse(domain.supportsImages)
        assertTrue(domain.supportsTools)
        assertFalse(domain.supportsStreaming)
        assertEquals(4096, domain.maxTokens)
    }

    // ======= ToolInfoV2Proto.toDomain =======

    @Test
    fun toolInfoV2Proto_toDomain() {
        val proto = ToolInfoV2Proto(
            name = "web_search",
            description = "Search the web",
            parametersSchema = """{"type":"object"}""",
            requiredRole = "admin"
        )
        val domain = proto.toDomain()
        assertEquals("web_search", domain.name)
        assertEquals("Search the web", domain.description)
        assertEquals("""{"type":"object"}""", domain.parametersSchema)
        assertEquals("admin", domain.requiredRole)
    }

    // ======= ToolCallRequestV2Proto.toDomain =======

    @Test
    fun toolCallRequestV2Proto_toDomain() {
        val proto = ToolCallRequestV2Proto(
            id = "call_1",
            name = "web_search",
            arguments = """{"query":"test"}"""
        )
        val domain = proto.toDomain()
        assertEquals("call_1", domain.id)
        assertEquals("web_search", domain.name)
        assertEquals("""{"query":"test"}""", domain.arguments)
        assertEquals("", domain.result)
    }

    // ======= AiV2ToolCall.toProto =======

    @Test
    fun aiV2ToolCall_toProto() {
        val domain = AiV2ToolCall(
            id = "call_1",
            name = "web_search",
            arguments = """{"query":"test"}""",
            result = "some results"
        )
        val proto = domain.toProto()
        assertEquals("call_1", proto.id)
        assertEquals("web_search", proto.name)
        assertEquals("""{"query":"test"}""", proto.arguments)
        assertEquals("some results", proto.result)
    }

    // ======= ChatWithAIV2ResponseProto.toStreamState =======

    @Test
    fun chatWithAIV2ResponseProto_toStreamState_token() {
        val proto = ChatWithAIV2ResponseProto(
            token = "Hello",
            finished = false,
            agentId = "a1",
            agentName = "Assistant"
        )
        val state = proto.toStreamState()
        assertTrue(state.isStreaming)
        assertEquals(listOf("Hello"), state.tokens)
        assertEquals("a1", state.agentId)
        assertEquals("Assistant", state.agentName)
    }

    @Test
    fun chatWithAIV2ResponseProto_toStreamState_finished() {
        val proto = ChatWithAIV2ResponseProto(
            token = "",
            finished = true,
            error = "",
            agentId = "a1",
            tokenCount = 100
        )
        val state = proto.toStreamState()
        assertFalse(state.isStreaming)
        assertFalse(state.isTyping)
        assertEquals("a1", state.agentId)
        assertEquals(100, state.tokenCount)
    }

    @Test
    fun chatWithAIV2ResponseProto_toStreamState_error() {
        val proto = ChatWithAIV2ResponseProto(
            finished = true,
            error = "Something failed"
        )
        val state = proto.toStreamState()
        assertEquals("Something failed", state.error)
        assertFalse(state.isStreaming)
    }

    @Test
    fun chatWithAIV2ResponseProto_toStreamState_withToolCalls() {
        val proto = ChatWithAIV2ResponseProto(
            finished = true,
            toolCalls = listOf(
                ToolCallRequestV2Proto(id = "c1", name = "web_search", arguments = "{}")
            )
        )
        val state = proto.toStreamState()
        assertEquals(1, state.toolCalls.size)
        assertEquals("web_search", state.toolCalls[0].name)
    }

    @Test
    fun chatWithAIV2ResponseProto_toStreamState_ragAndModel() {
        val proto = ChatWithAIV2ResponseProto(
            finished = true,
            hasRagContext = true,
            modelUsed = "claude-sonnet-4",
            tokenCount = 200
        )
        val state = proto.toStreamState()
        assertTrue(state.hasRagContext)
        assertEquals("claude-sonnet-4", state.modelUsed)
        assertEquals(200, state.tokenCount)
    }

    // ======= ChatWithAIV2ResponseProto.toChatMessage =======

    @Test
    fun chatWithAIV2ResponseProto_toChatMessage() {
        val proto = ChatWithAIV2ResponseProto(
            token = "Hi!",
            finished = false,
            agentId = "a1",
            agentName = "Assistant",
            modelUsed = "gpt-4o"
        )
        val msg = proto.toChatMessage("session-1")
        assertEquals("session-1", msg.sessionId)
        assertEquals("assistant", msg.role)
        assertEquals("Hi!", msg.content)
        assertEquals("a1", msg.agentId)
        assertEquals("Assistant", msg.agentName)
        assertTrue(msg.isStreaming)
        assertEquals("gpt-4o", msg.modelUsed)
    }

    @Test
    fun chatWithAIV2ResponseProto_toChatMessage_finished() {
        val proto = ChatWithAIV2ResponseProto(
            token = "Done",
            finished = true,
            agentId = "a1"
        )
        val msg = proto.toChatMessage("s1")
        assertEquals("Done", msg.content)
        assertFalse(msg.isStreaming)
    }
}
