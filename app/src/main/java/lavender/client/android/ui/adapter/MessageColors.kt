package lavender.client.android.ui.adapter

import android.graphics.Color
import androidx.core.graphics.toColorInt

data class MessageColors(
    val incomingBg: Int,
    val incomingText: Int,
    val outgoingBg: Int,
    val outgoingText: Int
)

fun getMessageColorsFromTheme(theme: lavender.client.android.theme.Theme): MessageColors {
    val defaultIncomingBg = "#16173A".toColorInt()
    val defaultOutgoingBg = "#2A2C6D".toColorInt()
    val defaultText = Color.WHITE
    return MessageColors(
        incomingBg = parseSafeColor(theme.incomingBubbleColor, defaultIncomingBg),
        incomingText = parseSafeColor(theme.incomingTextColor, defaultText),
        outgoingBg = parseSafeColor(theme.outgoingBubbleColor, defaultOutgoingBg),
        outgoingText = parseSafeColor(theme.outgoingTextColor, defaultText)
    )
}

fun parseSafeColor(colorStr: String, defaultColor: Int): Int {
    return try {
        colorStr.toColorInt()
    } catch (_: Exception) {
        defaultColor
    }
}
