# Lavender Messenger (Android client)

A secure messaging application

**Author:** Pavel Davydov (ferz)

A real-time secure messaging application with gRPC server and multiple client implementations.

---

## Project Description

Lavender Messenger (Android client) is a native Android application designed for messaging through the Lavender Messenger system. The application is developed in Kotlin using modern Android practices and architectural approaches.

## Key Features

- 📱 Send and receive messages in real-time via gRPC
- 🔌 Bidirectional streaming with Go server
- 👤 Username system for chat
- 👥 View online users in real-time
- 💾 Message history with server-side storage
- 💾 Local message history storage
- 🎨 Modern user interface (Material Design)
- 🔄 Duplicate message filtering to prevent echo
- 📊 Real-time connection status tracking
- 🔔 Push notifications with room navigation
- 🚀 Auto-navigation on app launch (if credentials saved)

## Tech Stack

- **Programming Language**: Kotlin
- **Min SDK**: 29 (Android 10.0)
- **Target SDK**: 37 (Android 14)
- **Compile SDK**: 37
- **Architecture**: MVVM
- **UI Framework**: Android Jetpack (ViewBinding)
- **Asynchronous**: Kotlin Coroutines + StateFlow
- **Network Protocol**: gRPC (bidirectional streaming)
- **Protocol**: Protobuf (protobuf-lite)
- **Server**: Go gRPC server (159.195.38.145:50051)

## Requirements

- Android 10.0 (API level 29) or higher
- Minimum 2 GB RAM
- Running Go gRPC server on 159.195.38.145:50051 (or 10.0.2.2:50051 for emulator)
- Internet connection for server communication

## Installation

### From Source Code

1. Clone the repository:
```bash
git clone <repository-url>
cd msg/client/android
```

2. Open the project in Android Studio or use command line:

```bash
# Build project
./gradlew build

# Install on device
./gradlew installDebug
```

## Project Structure

```
app/
├── src/main/
│   ├── java/msg/client/android/
│   │   ├── MainActivity.kt           # Entry point with username dialog
│   │   ├── ChatActivity.kt          # Main chat UI with RecyclerView
│   │   ├── ui/
│   │   │   ├── adapter/
│   │   │   │   └── MessageAdapter.kt    # RecyclerView adapter for messages
│   │   │   └── chat/
│   │   │       └── ChatViewModel.kt     # State management with gRPC
│   │   └── data/
│   │       ├── models/
│   │       │   ├── Message.kt          # Message model
│   │       │   └── GetHistoryResponse.kt # History response model
│   │       ├── proto/
│   │       │   ├── ChatMessage.kt      # Protobuf message wrapper
│   │       │   ├── MessengerProto.kt   # Protobuf definitions
│   │       │   └── ProtoUtils.kt        # Protobuf utilities
│   │       └── grpc/
│   │           ├── GrpcClient.kt        # Wrapper for gRPC client
│   │           ├── RealGrpcClient.kt    # gRPC implementation with custom marshaller
│   │           └── ServerConnectivityTest.kt # Server connectivity checker
│   ├── proto/
│   │   └── messenger.proto           # Protobuf schema definition
│   ├── res/
│   │   ├── drawable/
│   │   │   ├── bg_message_incoming.xml    # Incoming message background
│   │   │   ├── bg_message_outgoing.xml    # Outgoing message background
│   │   │   ├── circle_button.xml          # Circular button shape
│   │   │   ├── circle_indicator.xml       # Circular status indicator
│   │   │   ├── ic_back_arrow.xml          # Back navigation icon
│   │   │   ├── ic_theme_dark.xml          # Moon icon for dark theme
│   │   │   ├── ic_theme_toggle.xml        # Sun icon for light theme
│   │   │   ├── toolbar_background.xml     # Toolbar background with rounded corners
│   │   │   ├── ic_launcher_background.xml # Launcher icon background
│   │   │   └── ic_launcher_foreground.xml # Launcher icon foreground
│   │   ├── layout/
│   │   │   ├── activity_main.xml          # Main screen layout
│   │   │   ├── activity_chat.xml          # Chat screen layout
│   │   │   ├── dialog_join_chat.xml       # Join dialog layout
│   │   │   ├── item_message.xml           # Message item layout
│   │   │   └── language_indicator.xml     # Language toggle indicator
│   │   ├── menu/
│   │   │   ├── main_menu.xml              # Chat toolbar menu
│   │   │   └── menu_main.xml              # Main screen menu
│   │   ├── values/
│   │   │   ├── colors.xml                # Color definitions (light theme)
│   │   │   ├── strings.xml                # String resources (English)
│   │   │   ├── themes.xml                 # Theme definitions
│   │   │   └── dimens.xml                # Dimension values
│   │   ├── values-night/
│   │   │   └── colors.xml                # Color definitions (dark theme)
│   │   ├── values-ru/
│   │   │   └── strings.xml                # String resources (Russian)
│   │   └── xml/
│   │       ├── network_security_config.xml # Network security configuration
│   │       └── file_paths.xml               # File provider paths
│   └── AndroidManifest.xml          # App manifest
├── build.gradle.kts                 # App build configuration
└── proguard-rules.pro               # ProGuard rules
build.gradle.kts                     # Project build configuration
gradle/
├── libs.versions.toml               # Dependency versions (Version Catalog)
└── wrapper/
settings.gradle.kts                 # Gradle settings
```

## Configuration

### Server
The application connects to Go gRPC server at:
- **Device**: 159.195.38.145:50051
- **Android Emulator**: 10.0.2.2:50051

### gRPC Settings
- **Protocol**: Bidirectional streaming
- **Keep-alive**: 30 seconds interval, 5 seconds timeout
- **Marshaller**: Custom MessageProtoMarshaller
- **Method**: messenger.ChatService/Chat

## Build and Testing

### Build Debug Version
```bash
./gradlew assembleDebug
```

### Build Release Version
```bash
./gradlew assembleRelease
```

### Run Tests
```bash
# Unit tests
./gradlew test

# Instrumented tests
./gradlew connectedAndroidTest
```

## Versioning

The project follows semantic versioning (SemVer):
- **MAJOR.MINOR.PATCH.BUILD** (e.g., 1.0.1.22)

Current version: **1.0.1.45**

### Version 1.0.1.45 - Android 15 Support and UI Overhaul
- 📱 Added full Edge-to-Edge support with WindowInsets handling for Android 15 compatibility
- 🚪 Replaced back arrow with "Exit" icon in ChatListActivity for better navigation
- ➕ Added new "Add Chat" icon in the Toolbar for easier chat creation
- 👤 Redesigned Edit Profile screen with Material cards and full theme support
- 🌓 Optimized Light theme with white background and Dark theme with improved contrast
- 🛠️ Updated to compileSdk 37 and optimized dependencies (Glide 5.0.7, etc.)
- ⚡ Optimized update check to run only once per session
- 🖼️ Fixed immediate avatar synchronization in chat list
- 🔔 Fixed notification auto-navigation flow with pre-authentication

### Version 1.0.1.43 - Navigation and Update Improvements
- 🔄 Back button in ChatListActivity now navigates to MainActivity instead of closing app
- 📥 Added "Update" menu item to ChatListActivity with progress bar and cancel button
- 🔔 Update check now runs in background on first app launch with toolbar icon indicator
- ✅ Fixed messages not being marked as read in UI (history reload after markRead)
- 🔔 Added onNewIntent handler for notification clicks when app is running
- 🚫 Fixed update dialog showing when coming from notification

### Version 1.0.1.42 - Push Notifications and Auto-Navigation
- 🔔 Push notifications now include room_id for direct chat navigation
- 🚀 Auto-navigation on app launch if credentials are saved
- 💬 Fixed messages not updating automatically while in chat

### Version 1.0.1.41 - Chat Sorting and Theme Fixes
- 📊 Chats now sorted by time of last message instead of creation time
- 🎨 Fixed theme application in NewChatActivity
- 🎨 Updated message bubble colors for better theme consistency
- 🎨 Removed hardcoded chat background image

### Version 1.0.1.40 - UI Improvements and New Icons
- 🎨 Added custom icons for settings and account
- 🌐 Language button now shows current language
- 👋 Added welcome message for new users
- 📝 Added preview and edit buttons for profile
- 🎨 Redesigned chat list layout

### Version 1.0.1.39 - File Attachments and Notification History
- 📁 Added support for file attachments (PDF, archives)
- 🔔 Added notification history dialog with FCM token view
- 📊 Added notification tracking system
- 🎨 Improved file message display with custom icons

### Version 1.0.1.37 - Dialog Fix
- 🐛 Fixed dialog inflation issues in ChatListActivity

### Version 1.0.1.36 - Edit Profile and Unit Tests
- 👤 Added dedicated Edit Profile activity
- 🔐 Added password change dialog
- 🧪 Added comprehensive unit tests for ProtoUtils

### Version 1.0.1.35 - Reactions, Replies, and Profile
- 😀 Added emoji reactions to messages
- ↩️ Added reply to messages with swipe gesture
- 👤 Added dedicated Profile activity
- 📎 Added attachment picker dialog
- 📍 Added location sharing
- 🗑️ Added delete messages dialog
- 🎨 Redesigned message and chat layouts

### Version 1.0.1.34 - Material Design 3 Color System
- 🎨 Restructured color scheme following Material Design 3 guidelines
- 🎨 Added colorPrimaryContainer, colorSecondaryContainer, colorSurfaceContainer, colorSurfaceVariant
- 🎨 Light theme: pale_lilac background, white dialogs, deep_purple text
- 🎨 Dark theme: deep_purple background, lavender_mist dialogs, lavender_mist/white text
- 🔧 Changed default theme in AndroidManifest from dark to light for proper theme switching
- 🐛 Removed conflicting windowBackground override in values-night/themes.xml
- 📱 Fixed hardcoded background in landscape layout (layout-land/activity_main.xml)
- 🗑️ Completely removed person icon from chat list toolbar
- 👤 Changed profile icon to standard edit icon (pencil) with theme-aware tinting
- 🎨 Fixed text visibility in welcome dialog for dark theme
- 🔧 Replaced getColor() calls with theme.resolveAttribute() for Material Design colors
- 💬 Updated message background colors in light theme to prevent blending with toolbar
- 🎨 Fixed dialog text visibility in light theme by using white background and dark text
- 💬 Unified message background colors in light theme to match chat cards
- 🎨 Created PrimaryButton style for consistent button design across the app
- 🎨 Changed PrimaryButton background to match main background in dark theme
- 🎨 Created PrimaryButtonCompact style for landscape layouts
- 🎨 Reverted button text to uppercase first letter (Join/Войти)
- 🎨 Added dark_background color (#04052E) from logo edge for dark theme
- 🎨 Disabled textAllCaps in all button styles to display text exactly as defined
- 🎨 Fixed dialog text visibility in dark theme (white text on lavender_mist background)
- 🎨 Fixed server address spinner visibility in dark theme (white text)
- 🎨 Added explicit dark theme for ChatActivity and ChatListActivity in AndroidManifest
- 🎨 Fixed message backgrounds in light theme (pale_lilac to match chat cards)
- 🎨 Removed avatars from messages and disabled toolbar animation
- 📥 Added download progress display in megabytes with button blocking during update
- 🎨 Changed update button to PrimaryButton style to match Join button
- 🐛 Fixed crash when clicking Update button with null checks and error handling
- 🔄 Prevented download interruption on screen rotation
- 📝 Unified font size across all buttons on main screen
- 🎨 Fixed ChatActivity and ChatListActivity to properly apply user's theme preference
- 🔄 Added theme synchronization across all activities (changes in chat sync with main screen)
- 🐛 Fixed theme not saving when toggled in ChatActivity (changed default from light to dark)
- 🎨 Unified theme toggle icon and functionality between ChatActivity and ChatListActivity
- 🐛 Fixed light theme not applying correctly in ChatActivity

### Version 1.0.1.33 - UI Theme Improvements
- 🎨 Updated dark theme background to deep_purple for chat list and main screen
- 🎨 Added lavender_mist background for dialogs in dark theme
- 🎨 Updated button styles with rounded corners and strokes in dark theme
- 🎨 Improved text contrast for better visibility on dark backgrounds
- 🎨 Removed person icon from chat list and chat activities
- 🎨 Updated dialog styling for consistency across the app
- 🎨 Fixed button visibility issues in dark theme dialogs

### Version 1.0.1.28 - Avatar Display Fix
- 🐛 Fixed avatar caching issue - avatar URLs are now properly saved for users with avatars
- 💾 All avatar URLs (including empty strings) are now correctly saved to cache
- 🚫 Removed fade-in animation for avatars in chat list for better performance
- ⏳ Chat list now displays only after all participant avatars are loaded
- ⏱️ Added 5-second timeout for avatar loading
- 🔄 Same fixes applied to startPollingChats and onResume for consistency
- 📊 Avatars now correctly display in chat list (small icons next to chat names)
- 👥 Avatars correctly display in user list dialog
- ✨ No more flickering or missing avatars

### Version 1.0.1.27 - Image Attachments and Chat List UI
- 🖼️ Added image attachments - users can now attach images to messages
- 📸 Image picker with progress indicator
- 🎬 Animated GIF support - GIFs can be attached without resizing
- 📐 Automatic image resize to 1024x1024px before upload
- 📐 Automatic avatar resize to 256x256px before upload
- 🗑️ Image files physically deleted from server when message is deleted
- 💬 Send button enabled when image is selected, even without text
- 🎨 Chat list UI with participant avatars on the right side
- 📍 Unread count moved to left side of chat name with fixed indentation
- 📐 Larger avatars (96dp) for better visibility
- ✨ Smooth fade-in animation (300ms) for avatar loading
- 🔄 Auto avatar loading when chat list opens
- 🎭 Default avatars for participants without custom avatars
- 👥 All participant avatars shown in direct chats
- 📱 Avatar cache integration for efficient loading
- ⚡ Increased max upload size from 5MB to 10MB

### Version 1.0.1.26 - Avatar Support
- 👤 Added avatar support - users can now upload and display profile pictures
- 🖼️ Avatar displayed in chat messages next to sender's name
- 📸 Avatar selection in profile dialog with progress indicator
- 🌐 Server-side avatar storage and serving via HTTP
- 🎨 Default avatar icon for users without custom avatars
- 🔄 Added Glide library for efficient image loading

### Version 1.0.1.25 - Landscape Layout Support
- 📱 Added landscape layout support for main screen (activity_main.xml)
- 📱 Added landscape layout support for join chat dialog (dialog_join_chat.xml)
- 📱 Added landscape layout support for profile dialog (dialog_profile.xml)
- 🔄 Improved UI adaptation for different screen orientations

### Version 1.0.1.24 - Unread Count Fix
- 🔔 Fixed unread count calculation - now uses is_read flag instead of last_read_at comparison
- 🔄 Added DiffUtil to ChatAdapter for efficient UI updates
- ⚡ Reduced polling interval from 5 to 3 seconds for faster unread count updates
- 🔄 Added onResume() to refresh chat list when returning from chat
- 🐛 Fixed issue where unread count was always 0 due to incorrect time comparison

### Version 1.0.1.23 - Chat List UI Improvements
- 🎨 Updated chat list to use MaterialCardView with improved styling
- 🔔 Added unread count badges for chats
- 📨 Added message read status indicators (sent/read icons)
- 🛠️ Modernized toolbar to MaterialToolbar
- 🔧 Downgraded coreKtx dependency from 1.18.0 to 1.15.0 for stability

### Version 1.0.1 - Chat List and Profile
- 📱 Added dedicated chat list activity
- 👤 Added profile dialog
- 🎨 Updated app icon and logo
- 🎨 Refactored chat and main activities

### Version 0.9.7 - Message Reactions and Push Notifications
- 😀 Added ability to react to messages with emojis (long-press on a message)
- 🆔 Switched to UUIDs for reliable message tracking across sessions and reactions
- 🔔 Integrated Firebase Cloud Messaging (FCM) to receive notifications about new messages even when the app is in the background
- 🔄 Implemented intelligent message matching (ID/Text/User/Time) to prevent duplicate messages when they "echo" back from the server
- 🛡️ Strictly synchronized field indices between Android (manual marshaling) and Go (protoc) to prevent data corruption
- 🔄 Server-side message deletion: messages are now deleted from the gRPC server and PostgreSQL database, not just locally
- 🗄️ Added COALESCE handling for legacy database records to ensure stability during version upgrades
- 🧹 Deleted messages stay hidden even after app restart (stored in SharedPreferences)
- 🎨 Modern chat interface with dynamic toolbar, rounded input field, and improved connection status indicator
- 🚀 Replaced deprecated Locale and gRPC methods with modern equivalents

### Version 0.9.4 - Online Users and Message History
- 👥 Added ability to view online users in real-time
- 💾 Added message history with server-side storage
- 🔄 Server integration for online users tracking
- 🔄 Server integration for message history retrieval
- 📊 Improved message persistence across sessions

### Version 0.9.3 - Theme System Refactor
- 🎨 Migrated to Material 3 semantic theme attributes (colorPrimary, colorOnPrimary, colorSecondary, etc.)
- 🎨 Theme-aware message backgrounds with left/right alignment (incoming/outgoing)
- 🎨 Outgoing messages hide username for cleaner interface
- 🎨 All UI elements now use theme attributes for proper theme switching
- 🎨 Improved join dialog with better spacing and theme-aware colors
- 🔧 Renamed MainActivityMinimal.kt → MainActivity.kt for consistency
- 🔧 Renamed activity_main_simple.xml → activity_main.xml for consistency
- 🗑️ Removed unused ChatActivitySafe activity
- 🗑️ Removed hardcoded message colors, now using theme attributes
- 🎨 Message time format changed to HH:mm for cleaner display
- 🏗️ Improved theming structure for better maintainability

### Version 0.9.2 - Lavender Color Palette
- 🎨 New Lavender Messenger color palette (Deep Purple, Lavender Mist, Soft Lilac, Silver Fog, Dark Slate)
- 🎨 Updated light and dark themes with new colors
- 📝 Server address selection from predefined list (159.195.38.145:50051, 10.0.2.2:50051, localhost:50051)
- 👤 Username pre-filled in welcome dialog if previously entered
- 🟢 Server status indicator below server address spinner
- 🔘 Join button disabled when server is unavailable
- 🔄 Refresh button to manually recheck server availability
- 🎨 Improved UI: theme button shows current value (Theme: Light/Dark)
- 🎨 Replaced divider with decorative dots between main action and settings
- 🎨 App title color uses lavender_mist in both themes
- 🎨 Fixed server status indicator - now circular shape
- 📝 Shortened button and hint texts for better visibility
- 📝 Localized join message
- 🗑️ Removed unused expert mode feature
- 🗑️ Removed test connection from menu (functionality remains in dialog)
- 🎨 Added language and color scheme toggle icons to chat toolbar
- 🎨 Updated toolbar icons: custom sun/moon icons for theme toggle, dynamic EN/RU text for language toggle
- 🎨 Language indicator now clickable directly (EN/RU text)
- 🎨 Theme icon changes from sun to moon when switching to dark theme
- 🎨 Adapted chat screen to Lavender color scheme for light and dark themes
- 🎨 Added theme-aware backgrounds to RecyclerView, input layout, and EditText
- 🎨 Messages now use different colors for incoming and outgoing messages
- 🎨 Messages adapt to theme automatically with rounded corners
- 🐛 Fixed message theme preservation - now correctly restores username on theme change
- 🎨 Added rounded corners to toolbar (all four corners)
- 🎨 Messages use GradientDrawable with ContextCompat.getColor for theme support
- ⚠️ Message theme colors update on app restart, not on theme toggle
- 🐛 Fixed reconnection issue on theme change - no longer reconnects when switching themes

### Version 0.9.1 - Working Bidirectional Streaming
- ✅ Working bidirectional gRPC streaming
- ✅ Server receives messages and broadcasts back
- ✅ Custom protobuf marshaller
- ✅ Duplicate message filtering
- ✅ Proper connection error handling

## License

[Add license information]

## Contacts

- Developer: Pavel Davydov (ferz)
- Email: [your-email@example.com]
- GitHub: [your-github-username]

## Contributing

1. Fork the project
2. Create a feature branch (`git checkout -b feature/AmazingFeature`)
3. Commit your changes (`git commit -m 'Add some AmazingFeature'`)
4. Push to the branch (`git push origin feature/AmazingFeature`)
5. Open a Pull Request

---

## Russian Documentation

For Russian documentation, see [README.ru.md](README.ru.md)
