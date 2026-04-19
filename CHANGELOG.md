# Changelog

All notable changes to Lavender Messenger (Android client) will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [0.9.7] - 2026-04-19

### Changed
- 📝 **Download Button Text**: Shortened download button text to "update" (EN) and "обновить" (RU) for cleaner UI.
- 🎨 **New App Icon**: Updated launcher icon with new custom image from user.

### Added
- 😀 **Message Reactions**: Added ability to react to messages with emojis (long-press on a message).
- 🆔 **Unique Message IDs**: Switched to UUIDs for reliable message tracking across sessions and reactions.
- 🔔 **Push Notifications**: Integrated Firebase Cloud Messaging (FCM) to receive notifications about new messages even when the app is in the background.
- 🔄 **Smart De-duplication**: Implemented intelligent message matching (ID/Text/User/Time) to prevent duplicate messages when they "echo" back from the server.
- 🛡️ **gRPC Field Synchronization**: Strictly synchronized field indices between Android (manual marshaling) and Go (protoc) to prevent data corruption ("krakozyabry").
- 🔄 **Server-side Message Deletion**: Messages are now deleted from the gRPC server and PostgreSQL database, not just locally.
- 🗄️ **Robust DB Migrations**: Added `COALESCE` handling for legacy database records to ensure stability during version upgrades.
- 🧹 **Persistent Local Deletion**: Deleted messages stay hidden even after app restart (stored in SharedPreferences).
- 🎨 **Modern Chat Interface**: 
    - Moved delete button from FAB to the Toolbar for a cleaner view.
    - Added rounded input field with a modern "card" style.
    - Updated app icon with new lavender branding from user image.
    - Dynamic Toolbar: Theme and language icons hide when messages are selected to focus on deletion.
    - Improved connection status indicator inside the Toolbar.
- 🚀 **Performance**: Replaced deprecated Locale and gRPC methods with modern equivalents.

## [0.9.5] - 2026-04-19

### Added
- 🚀 **Multi-selection** for message deletion: Select multiple own messages to delete at once.
- 🎨 **New App Icon**: Adaptive icon with white logo on lavender background.
- 📡 **gRPC Stability**: 
    - Fixed `INTERNAL (Error in frame handler)` and `UNAVAILABLE` errors on various Android devices.
    - Added automatic retry logic for history loading and chat stream.
    - Increased inbound message size limit to 16MB for large chat histories.
    - Optimized KeepAlive settings for better mobile network compatibility.
- 💬 **New User Welcome**: Added a friendly welcome message for new users when chat history is empty.
- ⚠️ **Error Dialogs**: Detailed, copyable error dialogs replace short toasts for easier troubleshooting.
- 🔘 **Floating Delete Button**: New trash icon FAB in the chat area for easier access.
- 📦 **Download Progress**: Horizontal progress bar shows real-time APK download status on the main screen.
- 🧼 **Modern Chat UI**: 
    - Message grouping: Hides usernames and timestamps for consecutive messages from the same user.
    - Filtered "joined" system messages for a cleaner chat log from both history and live stream.
- ⚠️ **Enhanced Toasts**: Connection errors now appear at the top of the screen with a 5-second duration.
- 🔗 **Share & Copy**: Added buttons to copy the app link to clipboard and share it via external apps.
- 🎨 **Redesigned Main Screen**: New layout with slogan "Secure messaging" and improved information hierarchy.
- 📥 Download latest version button on main screen (now links to: `http://159.195.38.145:8081/lavender.apk`)

### Fixed
- 🛠️ **APK History Loading**: Fixed a bug where history wouldn't load in release builds by enabling `android:usesCleartextTraffic`.

### Technical Details
- Multi-selection tracked in MessageAdapter with `selectedPositions: MutableSet<Int>`
- Delete button visibility controlled by selection state (shown when at least one own message is selected)
- Message grouping logic implemented in `onBindViewHolder` based on time and user proximity
- APK download implemented using `HttpURLConnection` and `lifecycleScope` for progress tracking

## [0.9.4] - 2026-04-18

### Added
- 👥 Ability to view online users in real-time
- 💾 Message history with server-side storage
- 🔄 Server integration for online users tracking
- 🔄 Server integration for message history retrieval
- 📊 Improved message persistence across sessions

### Changed
- 🔧 Updated server integration to support new features

## [0.9.3] - 2026-04-18

### Added
- 🎨 Semantic theme attributes (colorPrimary, colorOnPrimary, colorSecondary, colorOnSecondary, colorSurface, colorOnSurface)
- 🎨 Theme-aware message backgrounds (bg_message_incoming, bg_message_outgoing)
- 🎨 Message bubbles now align left (incoming) or right (outgoing) for better UX
- 🎨 Outgoing messages hide username for cleaner interface
- 🎨 Improved join dialog with better spacing and typography
- 🎨 Join dialog now uses theme-aware colors for all elements

### Changed
- 🔧 Renamed MainActivityMinimal.kt → MainActivity.kt for consistency
- 🔧 Renamed activity_main_simple.xml → activity_main.xml for consistency
- 🎨 Migrated from hardcoded colors to semantic theme attributes throughout the app
- 🎨 Toolbar background now uses colorPrimary instead of deep_purple
- 🎨 Toolbar icons and text now use colorOnPrimary for theme adaptation
- 🎨 Chat backgrounds now use ?android:attr/windowBackground for proper theme support
- 🎨 Dialog elements now use theme attributes (colorSurface, colorPrimary, etc.)
- 🗑️ Removed ChatActivitySafe activity (unused)
- 🗑️ Removed hardcoded message colors from colors.xml
- 🗑️ Removed message_background_rounded.xml (replaced with theme-aware drawables)
- 🗑️ Removed toolbar_background.xml from drawable-night (now uses single theme-aware drawable)
- 🎨 Message time format changed from HH:mm:ss to HH:mm for cleaner display
- 🎨 Message layout restructured with messageContainer for better styling
- 🎨 MessageAdapter now uses theme attributes for text colors (textColorPrimary, textColorPrimaryInverse)
- 🎨 All icons now use ?attr/colorOnPrimary for theme adaptation
- 🎨 Join dialog buttons now use theme-aware colors (colorPrimary, colorSecondary)
- 🎨 Server status refresh button now uses colorPrimary
- 🔧 Removed unused R import from ChatActivity.kt
- 🔧 Updated ChatActivity logout intent to use MainActivity
