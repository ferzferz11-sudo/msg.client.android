package lavender.client.android.data.ai

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class AiChatManagerTest {

    @Test
    fun `hermesAgents initial value is empty`() = runTest {
        val agents = AiChatManager.hermesAgents.first()
        assertTrue("Hermes agents should be empty initially", agents.isEmpty())
    }

    @Test
    fun `owlSettings initial value is null`() = runTest {
        val settings = AiChatManager.owlSettings.first()
        assertNull("OWL settings should be null initially", settings)
    }

    @Test
    fun `hermesSettings initial value is null`() = runTest {
        val settings = AiChatManager.hermesSettings.first()
        assertNull("Hermes settings should be null initially", settings)
    }

    @Test
    fun `remoteAgents initial value is empty`() = runTest {
        val agents = AiChatManager.remoteAgents.first()
        assertTrue("Remote agents should be empty initially", agents.isEmpty())
    }

    @Test
    fun `AiChatSession default values`() {
        val session = AiChatSession()
        assertEquals("", session.id)
        assertEquals("", session.userId)
        assertEquals(AiSource.OWL, session.source)
        assertEquals("", session.name)
        assertTrue("createdAt should be > 0", session.createdAt > 0)
        assertEquals("", session.activeAgentId)
        assertEquals("single", session.mode)
        assertFalse("isUsingCustomKey should be false", session.isUsingCustomKey)
        assertEquals("", session.model)
    }

    @Test
    fun `AiChatSession with values`() {
        val session = AiChatSession(
            id = "session-1",
            userId = "user-1",
            source = AiSource.HERMES,
            name = "Test Session",
            activeAgentId = "agent-1",
            mode = "parallel"
        )
        assertEquals("session-1", session.id)
        assertEquals("user-1", session.userId)
        assertEquals(AiSource.HERMES, session.source)
        assertEquals("Test Session", session.name)
        assertEquals("agent-1", session.activeAgentId)
        assertEquals("parallel", session.mode)
    }

    @Test
    fun `AiChatMessage default values`() {
        val message = AiChatMessage()
        assertTrue("id should not be empty", message.id.isNotEmpty())
        assertEquals("", message.sessionId)
        assertEquals("", message.role)
        assertEquals("", message.content)
        assertEquals(AiSource.OWL, message.source)
        assertFalse("isStreaming should be false", message.isStreaming)
    }

    @Test
    fun `AiChatMessage with values`() {
        val message = AiChatMessage(
            id = "msg-1",
            sessionId = "session-1",
            role = "assistant",
            content = "Hello from AI",
            agentId = "agent-1",
            agentName = "Hermes",
            source = AiSource.HERMES,
            isStreaming = true
        )
        assertEquals("msg-1", message.id)
        assertEquals("session-1", message.sessionId)
        assertEquals("assistant", message.role)
        assertEquals("Hello from AI", message.content)
        assertEquals("agent-1", message.agentId)
        assertEquals("Hermes", message.agentName)
        assertEquals(AiSource.HERMES, message.source)
        assertTrue("isStreaming should be true", message.isStreaming)
    }

    @Test
    fun `AiChatSettings default values`() {
        val settings = AiChatSettings()
        assertEquals("", settings.sessionId)
        assertEquals("", settings.userId)
        assertEquals(AiSource.OWL, settings.source)
        assertEquals("", settings.apiKey)
        assertEquals("", settings.model)
        assertFalse("isUsingCustomKey should be false", settings.isUsingCustomKey)
        assertEquals(0, settings.remaining)
        assertEquals(0, settings.limit)
        assertEquals(0, settings.windowSeconds)
    }

    @Test
    fun `AiStreamState default values`() {
        val state = AiStreamState()
        assertFalse("isStreaming should be false", state.isStreaming)
        assertFalse("isTyping should be false", state.isTyping)
        assertTrue("tokens should be empty", state.tokens.isEmpty())
        assertNull("error should be null", state.error)
        assertFalse("finished should be false", state.finished)
        assertEquals("", state.agentId)
        assertEquals("", state.agentName)
    }

    @Test
    fun `AiStreamState streaming`() {
        val state = AiStreamState(
            isStreaming = true,
            isTyping = true,
            tokens = listOf("Hello", " ", "World"),
            finished = false,
            agentId = "agent-1",
            agentName = "Hermes"
        )
        assertTrue("isStreaming should be true", state.isStreaming)
        assertTrue("isTyping should be true", state.isTyping)
        assertEquals(3, state.tokens.size)
        assertFalse("finished should be false", state.finished)
    }

    @Test
    fun `AiStreamState with error`() {
        val state = AiStreamState(
            isStreaming = false,
            isTyping = false,
            error = "Connection failed",
            finished = true
        )
        assertEquals("Connection failed", state.error)
        assertTrue("finished should be true", state.finished)
    }

    @Test
    fun `AiSource enum values`() {
        val sources = AiSource.values()
        assertEquals(2, sources.size)
        assertTrue("Should contain OWL", sources.contains(AiSource.OWL))
        assertTrue("Should contain HERMES", sources.contains(AiSource.HERMES))
    }
}
