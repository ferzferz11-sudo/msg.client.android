package lavender.client.android.data.ai

import org.junit.Assert.*
import org.junit.Test

class AiModelsTest {

    @Test
    fun aiSource_values() {
        assertEquals(2, AiSource.values().size)
        assertEquals(AiSource.OWL, AiSource.valueOf("OWL"))
        assertEquals(AiSource.HERMES, AiSource.valueOf("HERMES"))
    }

    @Test
    fun aiChatSession_defaults() {
        val session = AiChatSession()
        assertEquals("", session.id)
        assertEquals("", session.userId)
        assertEquals(AiSource.OWL, session.source)
        assertEquals("", session.name)
        assertEquals("", session.activeAgentId)
        assertEquals("single", session.mode)
        assertFalse(session.isUsingCustomKey)
        assertEquals("", session.model)
    }

    @Test
    fun aiChatSession_hermesFields() {
        val session = AiChatSession(
            source = AiSource.HERMES,
            activeAgentId = "agent-123",
            mode = "parallel"
        )
        assertEquals(AiSource.HERMES, session.source)
        assertEquals("agent-123", session.activeAgentId)
        assertEquals("parallel", session.mode)
    }

    @Test
    fun aiChatSession_owlFields() {
        val session = AiChatSession(
            source = AiSource.OWL,
            isUsingCustomKey = true,
            model = "openai/gpt-4"
        )
        assertEquals(AiSource.OWL, session.source)
        assertTrue(session.isUsingCustomKey)
        assertEquals("openai/gpt-4", session.model)
    }

    @Test
    fun aiChatMessage_defaults() {
        val msg = AiChatMessage()
        assertEquals("", msg.sessionId)
        assertEquals("", msg.role)
        assertEquals("", msg.content)
        assertEquals("", msg.agentId)
        assertEquals("", msg.agentName)
        assertEquals(AiSource.OWL, msg.source)
        assertFalse(msg.isStreaming)
        assertTrue(msg.id.isNotEmpty())
    }

    @Test
    fun aiChatMessage_userMessage() {
        val msg = AiChatMessage(
            sessionId = "sess-1",
            role = "user",
            content = "Hello AI"
        )
        assertEquals("sess-1", msg.sessionId)
        assertEquals("user", msg.role)
        assertEquals("Hello AI", msg.content)
    }

    @Test
    fun aiChatMessage_assistantStreaming() {
        val msg = AiChatMessage(
            role = "assistant",
            content = "Partial response...",
            isStreaming = true
        )
        assertTrue(msg.isStreaming)
        assertEquals("assistant", msg.role)
    }

    @Test
    fun aiChatMessage_hermesAgent() {
        val msg = AiChatMessage(
            role = "agent",
            content = "Agent response",
            agentId = "agent-456",
            agentName = "Research Bot",
            source = AiSource.HERMES
        )
        assertEquals("agent-456", msg.agentId)
        assertEquals("Research Bot", msg.agentName)
        assertEquals(AiSource.HERMES, msg.source)
    }

    @Test
    fun aiChatSettings_defaults() {
        val settings = AiChatSettings()
        assertEquals("", settings.sessionId)
        assertEquals("", settings.userId)
        assertEquals(AiSource.OWL, settings.source)
        assertEquals("", settings.apiKey)
        assertEquals("", settings.model)
        assertFalse(settings.isUsingCustomKey)
        assertEquals(0, settings.remaining)
        assertEquals(0, settings.limit)
        assertEquals(0, settings.windowSeconds)
    }

    @Test
    fun aiChatSettings_owlWithApiKey() {
        val settings = AiChatSettings(
            source = AiSource.OWL,
            apiKey = "sk-or-12345",
            model = "openai/gpt-4",
            isUsingCustomKey = true,
            remaining = 100,
            limit = 200,
            windowSeconds = 3600
        )
        assertEquals("sk-or-12345", settings.apiKey)
        assertEquals("openai/gpt-4", settings.model)
        assertTrue(settings.isUsingCustomKey)
        assertEquals(100, settings.remaining)
        assertEquals(200, settings.limit)
        assertEquals(3600, settings.windowSeconds)
    }

    @Test
    fun aiStreamState_defaults() {
        val state = AiStreamState()
        assertFalse(state.isStreaming)
        assertFalse(state.isTyping)
        assertTrue(state.tokens.isEmpty())
        assertNull(state.error)
        assertFalse(state.finished)
        assertEquals("", state.agentId)
        assertEquals("", state.agentName)
    }

    @Test
    fun aiStreamState_streaming() {
        val state = AiStreamState(
            isStreaming = true,
            tokens = listOf("Hello", " world"),
            finished = false
        )
        assertTrue(state.isStreaming)
        assertEquals(2, state.tokens.size)
        assertEquals("Hello", state.tokens[0])
        assertEquals(" world", state.tokens[1])
    }

    @Test
    fun aiStreamState_error() {
        val state = AiStreamState(error = "Rate limit exceeded")
        assertEquals("Rate limit exceeded", state.error)
    }

    @Test
    fun aiStreamState_finished() {
        val state = AiStreamState(finished = true, isStreaming = false)
        assertTrue(state.finished)
        assertFalse(state.isStreaming)
    }
}
