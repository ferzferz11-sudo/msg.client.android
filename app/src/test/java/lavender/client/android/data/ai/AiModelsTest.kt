package lavender.client.android.data.ai

import org.junit.Assert.*
import org.junit.Test

class AiModelsTest {

    // ===== AiChatSession =====

    @Test
    fun `AiChatSession default values`() {
        val session = AiChatSession()
        assertEquals("Default id should be empty", "", session.id)
        assertEquals("Default userId should be empty", "", session.userId)
        assertEquals("Default source should be OWL", AiSource.OWL, session.source)
        assertEquals("Default name should be empty", "", session.name)
        assertEquals("Default activeAgentId should be empty", "", session.activeAgentId)
        assertEquals("Default mode should be single", "single", session.mode)
        assertFalse("Default isUsingCustomKey should be false", session.isUsingCustomKey)
        assertEquals("Default model should be empty", "", session.model)
    }

    @Test
    fun `AiChatSession with custom values`() {
        val session = AiChatSession(
            id = "session-1",
            userId = "user-1",
            source = AiSource.HERMES,
            name = "Test Session",
            activeAgentId = "agent-1",
            mode = "parallel",
            isUsingCustomKey = true,
            model = "gpt-4"
        )
        assertEquals("session-1", session.id)
        assertEquals("user-1", session.userId)
        assertEquals(AiSource.HERMES, session.source)
        assertEquals("Test Session", session.name)
        assertEquals("agent-1", session.activeAgentId)
        assertEquals("parallel", session.mode)
        assertTrue("isUsingCustomKey should be true", session.isUsingCustomKey)
        assertEquals("gpt-4", session.model)
    }

    @Test
    fun `AiChatSession copy preserves fields`() {
        val original = AiChatSession(id = "s1", userId = "u1", name = "Original")
        val copied = original.copy(name = "Updated")
        assertEquals("s1", copied.id)
        assertEquals("u1", copied.userId)
        assertEquals("Updated", copied.name)
    }

    @Test
    fun `AiChatSession OWL source`() {
        val session = AiChatSession(source = AiSource.OWL)
        assertEquals(AiSource.OWL, session.source)
    }

    @Test
    fun `AiChatSession HERMES source`() {
        val session = AiChatSession(source = AiSource.HERMES)
        assertEquals(AiSource.HERMES, session.source)
    }

    // ===== AiChatMessage =====

    @Test
    fun `AiChatMessage default values`() {
        val message = AiChatMessage()
        assertEquals("Default sessionId should be empty", "", message.sessionId)
        assertEquals("Default role should be empty", "", message.role)
        assertEquals("Default content should be empty", "", message.content)
        assertEquals("Default agentId should be empty", "", message.agentId)
        assertEquals("Default agentName should be empty", "", message.agentName)
        assertEquals("Default source should be OWL", AiSource.OWL, message.source)
        assertFalse("Default isStreaming should be false", message.isStreaming)
    }

    @Test
    fun `AiChatMessage with custom values`() {
        val message = AiChatMessage(
            id = "msg-1",
            sessionId = "session-1",
            role = "assistant",
            content = "Hello!",
            agentId = "agent-1",
            agentName = "Helper",
            source = AiSource.HERMES,
            isStreaming = true
        )
        assertEquals("msg-1", message.id)
        assertEquals("session-1", message.sessionId)
        assertEquals("assistant", message.role)
        assertEquals("Hello!", message.content)
        assertEquals("agent-1", message.agentId)
        assertEquals("Helper", message.agentName)
        assertEquals(AiSource.HERMES, message.source)
        assertTrue("isStreaming should be true", message.isStreaming)
    }

    @Test
    fun `AiChatMessage copy preserves fields`() {
        val original = AiChatMessage(id = "m1", content = "Original")
        val copied = original.copy(content = "Updated")
        assertEquals("m1", copied.id)
        assertEquals("Updated", copied.content)
    }

    @Test
    fun `AiChatMessage user role`() {
        val message = AiChatMessage(role = "user", content = "Hello")
        assertEquals("user", message.role)
        assertEquals("Hello", message.content)
    }

    @Test
    fun `AiChatMessage system role`() {
        val message = AiChatMessage(role = "system", content = "System prompt")
        assertEquals("system", message.role)
    }

    // ===== AiChatSettings =====

    @Test
    fun `AiChatSettings default values`() {
        val settings = AiChatSettings()
        assertEquals("Default sessionId should be empty", "", settings.sessionId)
        assertEquals("Default userId should be empty", "", settings.userId)
        assertEquals("Default source should be OWL", AiSource.OWL, settings.source)
        assertEquals("Default apiKey should be empty", "", settings.apiKey)
        assertEquals("Default model should be empty", "", settings.model)
        assertFalse("Default isUsingCustomKey should be false", settings.isUsingCustomKey)
        assertEquals("Default remaining should be 0", 0, settings.remaining)
        assertEquals("Default limit should be 0", 0, settings.limit)
        assertEquals("Default windowSeconds should be 0", 0, settings.windowSeconds)
    }

    @Test
    fun `AiChatSettings with custom values`() {
        val settings = AiChatSettings(
            sessionId = "s1",
            userId = "u1",
            source = AiSource.HERMES,
            apiKey = "key-123",
            model = "gpt-4",
            isUsingCustomKey = true,
            remaining = 100,
            limit = 1000,
            windowSeconds = 3600
        )
        assertEquals("s1", settings.sessionId)
        assertEquals("u1", settings.userId)
        assertEquals(AiSource.HERMES, settings.source)
        assertEquals("key-123", settings.apiKey)
        assertEquals("gpt-4", settings.model)
        assertTrue("isUsingCustomKey should be true", settings.isUsingCustomKey)
        assertEquals(100, settings.remaining)
        assertEquals(1000, settings.limit)
        assertEquals(3600, settings.windowSeconds)
    }

    @Test
    fun `AiChatSettings copy preserves fields`() {
        val original = AiChatSettings(sessionId = "s1", remaining = 50)
        val copied = original.copy(remaining = 25)
        assertEquals("s1", copied.sessionId)
        assertEquals(25, copied.remaining)
    }

    // ===== AiStreamState =====

    @Test
    fun `AiStreamState default values`() {
        val state = AiStreamState()
        assertFalse("Default isStreaming should be false", state.isStreaming)
        assertFalse("Default isTyping should be false", state.isTyping)
        assertTrue("Default tokens should be empty", state.tokens.isEmpty())
        assertNull("Default error should be null", state.error)
        assertFalse("Default finished should be false", state.finished)
        assertEquals("Default agentId should be empty", "", state.agentId)
        assertEquals("Default agentName should be empty", "", state.agentName)
    }

    @Test
    fun `AiStreamState streaming active`() {
        val state = AiStreamState(
            isStreaming = true,
            isTyping = true,
            tokens = listOf("Hello", " world"),
            agentId = "agent-1",
            agentName = "Helper"
        )
        assertTrue("isStreaming should be true", state.isStreaming)
        assertTrue("isTyping should be true", state.isTyping)
        assertEquals(2, state.tokens.size)
        assertEquals("Hello", state.tokens[0])
        assertEquals(" world", state.tokens[1])
        assertEquals("agent-1", state.agentId)
        assertEquals("Helper", state.agentName)
    }

    @Test
    fun `AiStreamState with error`() {
        val state = AiStreamState(
            isStreaming = false,
            error = "Connection failed",
            finished = true
        )
        assertFalse("isStreaming should be false", state.isStreaming)
        assertEquals("Connection failed", state.error)
        assertTrue("finished should be true", state.finished)
    }

    @Test
    fun `AiStreamState finished without error`() {
        val state = AiStreamState(
            isStreaming = false,
            finished = true,
            tokens = listOf("Complete response")
        )
        assertTrue("finished should be true", state.finished)
        assertNull("error should be null", state.error)
        assertEquals(1, state.tokens.size)
    }

    @Test
    fun `AiStreamState copy preserves fields`() {
        val original = AiStreamState(isStreaming = true, tokens = listOf("a"))
        val copied = original.copy(tokens = listOf("a", "b"))
        assertTrue("isStreaming should be preserved", copied.isStreaming)
        assertEquals(2, copied.tokens.size)
    }

    // ===== AiSource enum =====

    @Test
    fun `AiSource has OWL and HERMES values`() {
        val values = AiSource.values()
        assertEquals("Should have 2 values", 2, values.size)
        assertTrue("Should contain OWL", values.contains(AiSource.OWL))
        assertTrue("Should contain HERMES", values.contains(AiSource.HERMES))
    }

    @Test
    fun `AiSource OWL ordinal`() {
        assertEquals(0, AiSource.OWL.ordinal)
    }

    @Test
    fun `AiSource HERMES ordinal`() {
        assertEquals(1, AiSource.HERMES.ordinal)
    }
}
