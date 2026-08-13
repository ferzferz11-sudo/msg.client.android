package lavender.client.android.data.calls

/**
 * Language-agnostic call message detection.
 * Server sends call messages with emoji prefixes (📹📞↗️📞↘️) + text.
 * We detect by emoji first (universal), then by keywords (RU + EN).
 */
object CallMessageHelper {

    private val CALL_EMOJIS = listOf("📹", "📞")
    private val CALL_KEYWORDS = listOf(
        "звонок", "звонка", "вызов", "вызова", "видеозвонок",
        "call", "calling", "video call"
    )
    private val CONFERENCE_KEYWORDS = listOf(
        "конференция", "конференции",
        "conference"
    )
    private val ENDED_KEYWORDS = listOf(
        "завершен", "завершена", "завершено",
        "удалена", "удален", "удалено",
        "ended", "deleted", "completed"
    )
    private val MISSED_KEYWORDS = listOf(
        "пропущенный", "пропущена", "пропущен",
        "не принят", "не принята",
        "отклонён", "отклонена", "отклонен",
        "missed", "rejected", "declined"
    )

    private fun String.containsAny(keywords: List<String>): Boolean {
        val lower = this.lowercase()
        return keywords.any { lower.contains(it) }
    }

    /** Message is a call (video or audio) — not a conference */
    fun isCallMessage(text: String): Boolean {
        if (text.containsAny(CALL_EMOJIS)) return true
        if (text.containsAny(CALL_KEYWORDS)) return true
        return false
    }

    /** Message is a conference call */
    fun isConferenceMessage(text: String): Boolean {
        return text.containsAny(CONFERENCE_KEYWORDS)
    }

    /** Message is any call-related (call or conference) */
    fun isCallOrConference(text: String): Boolean {
        return isCallMessage(text) || isConferenceMessage(text)
    }

    /** Call/conference has ended */
    fun isCallEnded(text: String): Boolean {
        return text.containsAny(ENDED_KEYWORDS)
    }

    /** Call was missed/rejected */
    fun isCallMissed(text: String): Boolean {
        return text.containsAny(MISSED_KEYWORDS)
    }
}
