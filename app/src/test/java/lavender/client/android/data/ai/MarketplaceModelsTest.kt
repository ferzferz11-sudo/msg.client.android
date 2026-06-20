package lavender.client.android.data.ai

import org.junit.Assert.*
import org.junit.Test

class MarketplaceModelsTest {

    @Test
    fun marketplaceAgent_defaults() {
        val agent = MarketplaceAgent()
        assertEquals("", agent.id)
        assertEquals("", agent.name)
        assertEquals("", agent.description)
        assertEquals(AiProviderType.OPENROUTER, agent.providerType)
        assertEquals("", agent.model)
        assertFalse(agent.toolsEnabled)
        assertFalse(agent.ragEnabled)
        assertFalse(agent.isPreset)
        assertFalse(agent.isPublic)
        assertEquals(0f, agent.avgRating, 0.001f)
        assertEquals(0, agent.installCount)
    }

    @Test
    fun marketplaceAgent_withValues() {
        val agent = MarketplaceAgent(
            id = "agent-123",
            name = "Developer",
            description = "Coding assistant",
            providerType = AiProviderType.OPENROUTER,
            model = "anthropic/claude-sonnet-4",
            toolsEnabled = true,
            ragEnabled = false,
            isPreset = true,
            isPublic = true,
            avgRating = 4.5f,
            installCount = 100
        )
        assertEquals("agent-123", agent.id)
        assertEquals("Developer", agent.name)
        assertEquals("Coding assistant", agent.description)
        assertEquals(AiProviderType.OPENROUTER, agent.providerType)
        assertEquals("anthropic/claude-sonnet-4", agent.model)
        assertTrue(agent.toolsEnabled)
        assertFalse(agent.ragEnabled)
        assertTrue(agent.isPreset)
        assertTrue(agent.isPublic)
        assertEquals(4.5f, agent.avgRating, 0.001f)
        assertEquals(100, agent.installCount)
    }

    @Test
    fun agentStats_defaults() {
        val stats = AgentStats()
        assertEquals(0, stats.installCount)
        assertEquals(0f, stats.avgRating, 0.001f)
        assertEquals(0, stats.reviewCount)
    }

    @Test
    fun agentStats_withValues() {
        val stats = AgentStats(
            installCount = 50,
            avgRating = 4.2f,
            reviewCount = 25
        )
        assertEquals(50, stats.installCount)
        assertEquals(4.2f, stats.avgRating, 0.001f)
        assertEquals(25, stats.reviewCount)
    }

    @Test
    fun agentReview_defaults() {
        val review = AgentReview()
        assertEquals("", review.userId)
        assertEquals(0, review.rating)
        assertEquals("", review.review)
        assertEquals("", review.createdAt)
    }

    @Test
    fun agentReview_withValues() {
        val review = AgentReview(
            userId = "user-123",
            rating = 5,
            review = "Great agent!",
            createdAt = "2026-06-20"
        )
        assertEquals("user-123", review.userId)
        assertEquals(5, review.rating)
        assertEquals("Great agent!", review.review)
        assertEquals("2026-06-20", review.createdAt)
    }

    @Test
    fun usageStat_defaults() {
        val stat = UsageStat()
        assertEquals("", stat.agentId)
        assertEquals("", stat.agentName)
        assertEquals(0, stat.totalTokens)
        assertEquals(0, stat.requestCount)
        assertEquals("", stat.periodStart)
    }

    @Test
    fun usageStat_withValues() {
        val stat = UsageStat(
            agentId = "agent-123",
            agentName = "Developer",
            totalTokens = 1000000,
            requestCount = 500,
            periodStart = "2026-06-01"
        )
        assertEquals("agent-123", stat.agentId)
        assertEquals("Developer", stat.agentName)
        assertEquals(1000000, stat.totalTokens)
        assertEquals(500, stat.requestCount)
        assertEquals("2026-06-01", stat.periodStart)
    }
}
