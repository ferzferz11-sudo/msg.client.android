package lavender.client.android.theme.data

import lavender.client.android.data.proto.CustomThemeProto
import lavender.client.android.theme.BuiltInThemes
import lavender.client.android.theme.Theme

object ThemeMappers {
    fun fromProto(proto: CustomThemeProto): Theme {
        return Theme(
            id = proto.id,
            name = proto.name,
            primaryColor = proto.primaryColor,
            onPrimaryColor = proto.onPrimaryColor,
            surfaceColor = proto.surfaceColor,
            onSurfaceColor = proto.onSurfaceColor,
            backgroundColor = proto.backgroundColor,
            textPrimaryColor = proto.textPrimaryColor,
            textSecondaryColor = proto.textSecondaryColor,
            surfaceContainer = proto.surfaceContainer,
            bottomPanelColor = proto.bottomPanelColor,
            onBottomPanelColor = proto.onBottomPanelColor,
            outgoingBubbleColor = proto.outgoingBubbleColor,
            incomingBubbleColor = proto.incomingBubbleColor,
            outgoingTextColor = proto.outgoingTextColor.ifEmpty { 
                BuiltInThemes.getContrastTextColor(proto.outgoingBubbleColor) 
            },
            incomingTextColor = proto.incomingTextColor.ifEmpty { 
                BuiltInThemes.getContrastTextColor(proto.incomingBubbleColor) 
            },
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

