package lavender.client.android.data.ai

class RateLimitCache {
    data class RateLimitInfo(
        val agentId: String,
        val limit: Int = 10,
        val windowMs: Long = 60_000L,
        val timestamps: MutableList<Long> = mutableListOf()
    )

    private val cache = mutableMapOf<String, RateLimitInfo>()

    fun getRemaining(agentId: String, limit: Int = 10): Int {
        val info = cache.getOrPut(agentId) { RateLimitInfo(agentId, limit) }
        val cutoff = System.currentTimeMillis() - info.windowMs
        info.timestamps.removeAll { it < cutoff }
        return (info.limit - info.timestamps.size).coerceAtLeast(0)
    }

    fun recordRequest(agentId: String) {
        val info = cache.getOrPut(agentId) { RateLimitInfo(agentId, 10) }
        info.timestamps.add(System.currentTimeMillis())
    }

    fun getTimeUntilReset(agentId: String): Long {
        val info = cache[agentId] ?: return 0
        if (info.timestamps.isEmpty()) return 0
        val oldest = info.timestamps.minOrNull() ?: return 0
        return (oldest + info.windowMs) - System.currentTimeMillis()
    }

    fun undoLastRecord(agentId: String) {
        val info = cache[agentId] ?: return
        if (info.timestamps.isNotEmpty()) {
            info.timestamps.removeAt(info.timestamps.size - 1)
        }
    }

    fun setLimit(agentId: String, limit: Int) {
        val info = cache.getOrPut(agentId) { RateLimitInfo(agentId, limit) }
        cache[agentId] = info.copy(limit = limit)
    }
}
