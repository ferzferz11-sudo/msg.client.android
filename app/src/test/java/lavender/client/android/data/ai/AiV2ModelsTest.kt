package lavender.client.android.data.ai

import org.junit.Assert.*
import org.junit.Test

class AiV2ModelsTest {

    @Test
    fun aiV2Agent_defaults() {
        val agent = AiV2Agent()
        assertEquals("", agent.id)
        assertEquals("", agent.name)
        assertEquals("", agent.description)
        assertEquals(AiProviderType.OPENROUTER, agent.providerType)
        assertEquals("", agent.model)
        assertEquals("", agent.systemPrompt)
        assertFalse(agent.toolsEnabled)
        assertFalse(agent.ragEnabled)
        assertFalse(agent.isPreset)
        assertFalse(agent.isPublic)
        assertEquals(4096, agent.maxTokens)
        assertEquals(0.7f, agent.temperature, 0.001f)
        assertEquals("", agent.createdBy)
    }

    @Test
    fun aiV2Agent_withValues() {
        val agent = AiV2Agent(
            id = "agent-123",
            name = "Developer",
            description = "Coding assistant",
            providerType = AiProviderType.OPENROUTER,
            model = "anthropic/claude-sonnet-4",
            systemPrompt = "You are a developer",
            toolsEnabled = true,
            ragEnabled = false,
            isPreset = true,
            maxTokens = 8192,
            temperature = 0.3f
        )
        assertEquals("agent-123", agent.id)
        assertEquals("Developer", agent.name)
        assertEquals("Coding assistant", agent.description)
        assertEquals(AiProviderType.OPENROUTER, agent.providerType)
        assertEquals("anthropic/claude-sonnet-4", agent.model)
        assertEquals("You are a developer", agent.systemPrompt)
        assertTrue(agent.toolsEnabled)
        assertFalse(agent.ragEnabled)
        assertTrue(agent.isPreset)
        assertEquals(8192, agent.maxTokens)
        assertEquals(0.3f, agent.temperature, 0.001f)
    }

    @Test
    fun aiV2Agent_copy() {
        val original = AiV2Agent(id = "a1", name = "Old")
        val updated = original.copy(name = "New", toolsEnabled = true)
        assertEquals("New", updated.name)
        assertTrue(updated.toolsEnabled)
        assertEquals("a1", updated.id)
    }

    @Test
    fun aiProviderType_fromString() {
        assertEquals(AiProviderType.OPENROUTER, AiProviderType.fromString("openrouter"))
        assertEquals(AiProviderType.MIMO, AiProviderType.fromString("mimo"))
        assertEquals(AiProviderType.WEBHOOK, AiProviderType.fromString("webhook"))
        assertEquals(AiProviderType.WEBSOCKET, AiProviderType.fromString("websocket"))
        assertEquals(AiProviderType.SUBPROCESS, AiProviderType.fromString("subprocess"))
        assertEquals(AiProviderType.MCP, AiProviderType.fromString("mcp"))
        assertEquals(AiProviderType.LOCAL, AiProviderType.fromString("local"))
        assertEquals(AiProviderType.OPENROUTER, AiProviderType.fromString("unknown"))
    }

    @Test
    fun aiProviderType_values() {
        val types = AiProviderType.entries
        assertEquals(7, types.size)
        assertEquals("openrouter", AiProviderType.OPENROUTER.value)
        assertEquals("mimo", AiProviderType.MIMO.value)
        assertEquals("local", AiProviderType.LOCAL.value)
    }

    @Test
    fun aiV2ToolCall_defaults() {
        val tc = AiV2ToolCall()
        assertEquals("", tc.id)
        assertEquals("", tc.name)
        assertEquals("", tc.arguments)
        assertEquals("", tc.result)
    }

    @Test
    fun aiV2ToolCall_withValues() {
        val tc = AiV2ToolCall(
            id = "call_123",
            name = "web_search",
            arguments = """{"query":"Go concurrency"}""",
            result = "Go goroutines are lightweight threads"
        )
        assertEquals("call_123", tc.id)
        assertEquals("web_search", tc.name)
        assertEquals("""{"query":"Go concurrency"}""", tc.arguments)
        assertEquals("Go goroutines are lightweight threads", tc.result)
    }

    @Test
    fun aiV2ToolCall_mutableResult() {
        val tc = AiV2ToolCall(id = "c1", name = "test")
        assertEquals("", tc.result)
        tc.result = "executed"
        assertEquals("executed", tc.result)
    }

    @Test
    fun aiV2StreamState_defaults() {
        val state = AiV2StreamState()
        assertFalse(state.isStreaming)
        assertFalse(state.isTyping)
        assertTrue(state.tokens.isEmpty())
        assertNull(state.error)
        assertFalse(state.finished)
        assertEquals("", state.agentId)
        assertEquals("", state.agentName)
        assertTrue(state.toolCalls.isEmpty())
        assertFalse(state.hasRagContext)
        assertEquals("", state.modelUsed)
        assertEquals(0, state.tokenCount)
    }

    @Test
    fun aiV2StreamState_streaming() {
        val state = AiV2StreamState(
            isStreaming = true,
            isTyping = true,
            tokens = listOf("Hello"),
            agentId = "a1",
            agentName = "Assistant"
        )
        assertTrue(state.isStreaming)
        assertTrue(state.isTyping)
        assertEquals(listOf("Hello"), state.tokens)
        assertEquals("a1", state.agentId)
        assertEquals("Assistant", state.agentName)
    }

    @Test
    fun aiV2StreamState_withToolCalls() {
        val toolCalls = listOf(
            AiV2ToolCall(id = "c1", name = "web_search", arguments = "{}")
        )
        val state = AiV2StreamState(
            isStreaming = true,
            toolCalls = toolCalls,
            hasRagContext = true,
            modelUsed = "claude-sonnet-4",
            tokenCount = 150
        )
        assertEquals(1, state.toolCalls.size)
        assertEquals("web_search", state.toolCalls[0].name)
        assertTrue(state.hasRagContext)
        assertEquals("claude-sonnet-4", state.modelUsed)
        assertEquals(150, state.tokenCount)
    }

    @Test
    fun aiV2Tool_defaults() {
        val tool = AiV2Tool()
        assertEquals("", tool.name)
        assertEquals("", tool.description)
        assertEquals("", tool.parametersSchema)
        assertEquals("", tool.requiredRole)
    }

    @Test
    fun aiV2Tool_withValues() {
        val tool = AiV2Tool(
            name = "web_search",
            description = "Search the web",
            parametersSchema = """{"type":"object","properties":{"query":{"type":"string"}}}""",
            requiredRole = "user"
        )
        assertEquals("web_search", tool.name)
        assertEquals("Search the web", tool.description)
        assertTrue(tool.parametersSchema.contains("query"))
        assertEquals("user", tool.requiredRole)
    }

    @Test
    fun aiV2ChatMessage_defaults() {
        val msg = AiV2ChatMessage()
        assertEquals("", msg.sessionId)
        assertEquals("", msg.role)
        assertEquals("", msg.content)
        assertEquals("", msg.agentId)
        assertEquals("", msg.agentName)
        assertFalse(msg.isStreaming)
        assertTrue(msg.toolCalls.isEmpty())
        assertFalse(msg.hasRagContext)
        assertEquals("", msg.modelUsed)
        assertEquals(0, msg.tokenCount)
    }

    @Test
    fun aiV2ChatMessage_userMessage() {
        val msg = AiV2ChatMessage(
            sessionId = "s1",
            role = "user",
            content = "Hello!"
        )
        assertEquals("s1", msg.sessionId)
        assertEquals("user", msg.role)
        assertEquals("Hello!", msg.content)
        assertFalse(msg.isStreaming)
    }

    @Test
    fun aiV2ChatMessage_assistantStreaming() {
        val msg = AiV2ChatMessage(
            sessionId = "s1",
            role = "assistant",
            content = "Hell",
            isStreaming = true,
            agentId = "a1",
            agentName = "Assistant",
            modelUsed = "claude-sonnet-4"
        )
        assertTrue(msg.isStreaming)
        assertEquals("a1", msg.agentId)
        assertEquals("claude-sonnet-4", msg.modelUsed)
    }

    @Test
    fun aiV2ChatSession_defaults() {
        val session = AiV2ChatSession()
        assertEquals("", session.id)
        assertEquals("", session.agentId)
        assertEquals("", session.agentName)
    }

    @Test
    fun aiV2ChatSession_withValues() {
        val session = AiV2ChatSession(
            id = "sess-123",
            agentId = "agent-456",
            agentName = "Developer"
        )
        assertEquals("sess-123", session.id)
        assertEquals("agent-456", session.agentId)
        assertEquals("Developer", session.agentName)
    }

    @Test
    fun aiAgentCapabilities_defaults() {
        val caps = AiAgentCapabilities()
        assertFalse(caps.supportsImages)
        assertFalse(caps.supportsTools)
        assertFalse(caps.supportsStreaming)
        assertEquals(0, caps.maxTokens)
    }

    @Test
    fun aiAgentCapabilities_withValues() {
        val caps = AiAgentCapabilities(
            supportsImages = true,
            supportsTools = true,
            supportsStreaming = true,
            maxTokens = 128000
        )
        assertTrue(caps.supportsImages)
        assertTrue(caps.supportsTools)
        assertTrue(caps.supportsStreaming)
        assertEquals(128000, caps.maxTokens)
    }
}
