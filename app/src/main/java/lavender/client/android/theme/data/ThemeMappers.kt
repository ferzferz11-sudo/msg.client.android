package lavender.client.android.theme.data

import lavender.client.android.data.proto.CustomThemeProto
import lavender.client.android.theme.BuiltInThemes
import lavender.client.android.theme.Theme

object ThemeMappers {
    fun fromProto(proto: CustomThemeProto?): Theme {
        if (proto == null) return BuiltInThemes.dark
        
        return Theme(
            id = proto.id.ifEmpty { "unknown" },
            name = proto.name.ifEmpty { "Custom Theme" },
            primaryColor = proto.primaryColor.takeIf { it.isNotEmpty() && it.startsWith("#") } ?: "#5F9EA0",
            onPrimaryColor = proto.onPrimaryColor.takeIf { it.isNotEmpty() && it.startsWith("#") } ?: "#FFFFFF",
            surfaceColor = proto.surfaceColor.takeIf { it.isNotEmpty() && it.startsWith("#") } ?: "#2D2D2D",
            onSurfaceColor = proto.onSurfaceColor.takeIf { it.isNotEmpty() && it.startsWith("#") } ?: "#B8B8B8",
            backgroundColor = proto.backgroundColor.takeIf { it.isNotEmpty() && it.startsWith("#") } ?: "#1E1E1E",
            textPrimaryColor = proto.textPrimaryColor.takeIf { it.isNotEmpty() && it.startsWith("#") } ?: "#E8E8E8",
            textSecondaryColor = proto.textSecondaryColor.takeIf { it.isNotEmpty() && it.startsWith("#") } ?: "#909090",
            surfaceContainer = proto.surfaceContainer.takeIf { it.isNotEmpty() && it.startsWith("#") } ?: "#252525",
            bottomPanelColor = proto.bottomPanelColor.takeIf { it.isNotEmpty() && it.startsWith("#") } ?: "#2D2D2D",
            onBottomPanelColor = proto.onBottomPanelColor.takeIf { it.isNotEmpty() && it.startsWith("#") } ?: "#5F9EA0",
            outgoingBubbleColor = proto.outgoingBubbleColor.takeIf { it.isNotEmpty() && it.startsWith("#") } ?: "#3D6B6C",
            incomingBubbleColor = proto.incomingBubbleColor.takeIf { it.isNotEmpty() && it.startsWith("#") } ?: "#363636",
            outgoingTextColor = if (proto.outgoingTextColor.isNotEmpty() && proto.outgoingTextColor.startsWith("#")) proto.outgoingTextColor 
                                else BuiltInThemes.getContrastTextColor(proto.outgoingBubbleColor.ifEmpty { "#3D6B6C" }),
            incomingTextColor = if (proto.incomingTextColor.isNotEmpty() && proto.incomingTextColor.startsWith("#")) proto.incomingTextColor
                                else BuiltInThemes.getContrastTextColor(proto.incomingBubbleColor.ifEmpty { "#363636" }),
            chatListBackgroundImageUrl = proto.chatListBackgroundImageUrl,
            chatBackgroundImageUrl = proto.chatBackgroundImageUrl,
        )
    }

    fun toProto(theme: Theme): CustomThemeProto =
        CustomThemeProto(
            id = theme.id,
            name = theme.name,
            primaryColor = theme.primaryColor,
            onPrimaryColor = theme.onPrimaryColor,
            surfaceColor = theme.surfaceColor,
            onSurfaceColor = theme.onSurfaceColor,
            backgroundColor = theme.backgroundColor,
            textPrimaryColor = theme.textPrimaryColor,
            textSecondaryColor = theme.textSecondaryColor,
            chatListBackgroundImageUrl = theme.chatListBackgroundImageUrl,
            chatBackgroundImageUrl = theme.chatBackgroundImageUrl,
            bottomPanelColor = theme.bottomPanelColor,
            onBottomPanelColor = theme.onBottomPanelColor,
            surfaceContainer = theme.surfaceContainer,
            outgoingBubbleColor = theme.outgoingBubbleColor,
            incomingBubbleColor = theme.incomingBubbleColor,
            outgoingTextColor = theme.outgoingTextColor,
            incomingTextColor = theme.incomingTextColor,
        )
}

