package lavender.client.android.data.ai

import lavender.client.android.data.proto.AgentInfoV2Proto
import lavender.client.android.data.proto.AgentReviewProto
import lavender.client.android.data.proto.UsageStatEntryProto
import org.junit.Assert.*
import org.junit.Test

class MarketplaceMappersTest {

    @Test
    fun agentInfoV2Proto_toMarketplaceAgent() {
        val proto = AgentInfoV2Proto(
            id = "agent-123",
            name = "Developer",
            description = "Coding assistant",
            providerType = "openrouter",
            model = "anthropic/claude-sonnet-4",
            toolsEnabled = true,
            ragEnabled = false,
            isPreset = true,
            isPublic = true
        )
        val agent = proto.toMarketplaceAgent()
        assertEquals("agent-123", agent.id)
        assertEquals("Developer", agent.name)
        assertEquals("Coding assistant", agent.description)
        assertEquals(AiProviderType.OPENROUTER, agent.providerType)
        assertEquals("anthropic/claude-sonnet-4", agent.model)
        assertTrue(agent.toolsEnabled)
        assertFalse(agent.ragEnabled)
        assertTrue(agent.isPreset)
        assertTrue(agent.isPublic)
    }

    @Test
    fun agentInfoV2Proto_toMarketplaceAgent_unknownProvider() {
        val proto = AgentInfoV2Proto(
            id = "agent-456",
            name = "Custom Agent",
            providerType = "unknown_provider"
        )
        val agent = proto.toMarketplaceAgent()
        assertEquals(AiProviderType.OPENROUTER, agent.providerType)
    }

    @Test
    fun agentReviewProto_toDomain() {
        val proto = AgentReviewProto(
            userId = "user-123",
            rating = 5,
            review = "Great agent!",
            createdAt = "2026-06-20"
        )
        val review = proto.toDomain()
        assertEquals("user-123", review.userId)
        assertEquals(5, review.rating)
        assertEquals("Great agent!", review.review)
        assertEquals("2026-06-20", review.createdAt)
    }

    @Test
    fun agentReviewProto_toDomain_empty() {
        val proto = AgentReviewProto()
        val review = proto.toDomain()
        assertEquals("", review.userId)
        assertEquals(0, review.rating)
        assertEquals("", review.review)
        assertEquals("", review.createdAt)
    }

    @Test
    fun usageStatEntryProto_toDomain() {
        val proto = UsageStatEntryProto(
            agentId = "agent-123",
            agentName = "Developer",
            totalTokens = 1000000,
            requestCount = 500,
            periodStart = "2026-06-01"
        )
        val stat = proto.toDomain()
        assertEquals("agent-123", stat.agentId)
        assertEquals("Developer", stat.agentName)
        assertEquals(1000000, stat.totalTokens)
        assertEquals(500, stat.requestCount)
        assertEquals("2026-06-01", stat.periodStart)
    }

    @Test
    fun usageStatEntryProto_toDomain_empty() {
        val proto = UsageStatEntryProto()
        val stat = proto.toDomain()
        assertEquals("", stat.agentId)
        assertEquals("", stat.agentName)
        assertEquals(0, stat.totalTokens)
        assertEquals(0, stat.requestCount)
        assertEquals("", stat.periodStart)
    }

    @Test
    fun marketplaceAgent_providerTypes() {
        val providerTypes = listOf(
            "openrouter" to AiProviderType.OPENROUTER,
            "local" to AiProviderType.LOCAL,
            "mimo" to AiProviderType.MIMO,
            "webhook" to AiProviderType.WEBHOOK,
            "websocket" to AiProviderType.WEBSOCKET,
            "subprocess" to AiProviderType.SUBPROCESS,
            "mcp" to AiProviderType.MCP
        )
        providerTypes.forEach { (input, expected) ->
            val proto = AgentInfoV2Proto(providerType = input)
            val agent = proto.toMarketplaceAgent()
            assertEquals(expected, agent.providerType)
        }
    }
}
