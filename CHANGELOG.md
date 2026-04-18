# Changelog

All notable changes to Lavender Messenger (Android client) will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [0.9.2] - Unreleased

### Added
- 🎨 New Lavender Messenger color palette (Deep Purple, Lavender Mist, Soft Lilac, Silver Fog, Dark Slate)
- 🎨 Updated light theme with Silver Fog background and Lavender accents
- 🎨 Updated dark theme with Dark Slate background and Lavender Mist accents
- 🎨 Applied new colors to main screen buttons
- 📝 Server address now displayed in welcome dialog
- 👤 Username is pre-filled in welcome dialog if previously entered
- 📝 Server address selection from predefined list (192.168.1.135:50051, 10.0.2.2:50051, localhost:50051)
- 💾 Server address is saved and restored between sessions
- 🟢 Server status indicator below server address spinner (green = available, red = unavailable)
- 🔘 Join button disabled when server is unavailable
- 🔄 Refresh button to manually recheck server availability

### Changed
- 🎨 Updated dialog buttons to use Lavender Messenger color palette
- 👤 Username now persists after logout (previously was cleared on logout)
- 🚫 Removed auto-login to always show main screen with join dialog
- 🔗 ChatActivity now uses server address from intent instead of hardcoded value
- 🔧 Fixed server address parsing to separate host and port for gRPC connection
- 🔧 Added port parameter to gRPC client functions for flexible port configuration
- 🎨 Improved button text contrast for better visibility in light and dark themes
- 🎨 Updated soft_lilac color to be more contrasting in both themes
- 📝 Simplified Russian app name from "Лаванда Messenger" to "Лаванда"
- 🎨 Theme button now shows current theme (Theme: Light/Dark) like language button
- 🎨 Improved dark theme contrast: buttons now use lighter colors for better visibility
- 🎨 Button text colors now use theme-aware textColorPrimary for automatic adaptation
- 🎨 Replaced simple divider with decorative dots between main action and settings
- 🎨 App title now uses lavender_mist color in both themes
- 📝 Updated Russian app description from "Приложение для безопасного обмена сообщениями" to "Безопасный обмен сообщениями"
- 🎨 Fixed server status indicator to use backgroundTintList instead of setBackgroundColor for proper circle shape
- 📝 Shortened button and hint texts: "Send Message" → "Send", "Type message here" → "Type here"
- 📝 Renamed string resources from lavanda_* to lavender_* for consistency
- 🗑️ Removed unused expert mode feature from menu and code
- 🗑️ Removed test connection from menu (functionality remains in dialog)
- 📝 Localized join message: "username joined the chat" → "username joined" (EN) / "username присоединился" (RU)
- 🎨 Added language and color scheme toggle icons to chat toolbar
- 🎨 Updated toolbar icons: custom sun/moon icons for theme toggle, dynamic EN/RU text for language toggle
- 🎨 Language indicator now clickable directly (EN/RU text)
- 🎨 Theme icon changes from sun to moon when switching to dark theme
- 🎨 Adapted chat screen to Lavender color scheme for light and dark themes
- 🎨 Chat toolbar uses deep_purple background with lavender_mist title
- 🎨 Chat background uses silver_fog (light) / dark_slate (dark)
- 🎨 Message colors adapted to Lavender palette
- 🎨 Send button uses lavender_mist with white text
- 🎨 Added theme-aware backgrounds to RecyclerView, input layout, and EditText
- 🎨 Messages now use different colors for incoming (soft_lilac/deep_purple) and outgoing (lavender_mist) messages
- 🎨 Messages adapt to theme automatically with rounded corners
- 🐛 Fixed message theme preservation - now correctly restores username on theme change
- 🎨 Added rounded corners to toolbar (all four corners)
- 🎨 Messages use GradientDrawable with ContextCompat.getColor for theme support
- ⚠️ Message theme colors update on app restart, not on theme toggle
- 🐛 Fixed reconnection issue on theme change - now only connects on first creation, not on recreate()

## [0.9.1] - 2026-04-17

### Added
- ✅ Working bidirectional gRPC streaming
- ✅ RealGrpcClient with custom MessageProtoMarshaller
- ✅ Manual ClientCall setup for bidirectional streaming
- ✅ Duplicate message detection (prevents echo by checking last 5 messages within 2 seconds)
- ✅ Real-time connection status tracking (Connecting/Connected/Disconnected)
- ✅ Join message system with welcome responses
- ✅ Atomic message list updates with StateFlow
- ✅ Proper cleanup with shutdownNow() on disconnect
- ✅ isChatStarted flag to prevent duplicate stream creation
- ✅ Keep-alive configuration (30s intervals, 5s timeout)
- ✅ Go server integration (localhost:50051 / 10.0.2.2:50051 for emulator)

### Changed
- Updated Target SDK from 36 to 37
- Updated Compile SDK to 37
- Replaced simulation with real gRPC bidirectional streaming
- Improved error handling for connection failures
- Enhanced message processing with duplicate filtering
- 🎨 Renamed app from "Lavanda" to "Lavender Messenger"
- 📝 Updated documentation with new app name and description
- 🌐 Russian translation: "Лаванда"
- 🗑️ Removed Chinese language support (now only English and Russian)
- 🔄 Added language toggle button on main screen
- 💾 Language preference is now saved and restored on app launch
- 🌐 Language is applied consistently across all activities
- 🌐 Complete Russian translation of all UI strings
- 📝 All hardcoded text replaced with string resources for proper localization
- 📝 Project name changed from "MSG Android Client" to "Lavender Messenger (Android client)"
- 📄 Separated README into English (README.md) and Russian (README.ru.md) versions
- 🎨 Reorganized main screen UI - separated Join button from settings buttons
- 🎨 Added color scheme toggle button (Light/Dark only)
- 💾 Color scheme preference is now saved and restored on app launch
- 📱 Removed unnecessary "Ready" toast message that appeared on every activity recreation
- 🔧 Version now stored in single place (build.gradle.kts) and used everywhere via BuildConfig and string resources

### Technical Details
- **gRPC**: Custom marshaller using CodedOutputStream/CodedInputStream
- **Streaming**: Manual ClientCall.newCall() with method descriptor
- **Pattern**: StreamObserver for request/response
- **State Management**: StateFlow for reactive UI updates
- **Concurrency**: Coroutines for async operations
- **Dependencies**: grpc-okhttp, grpc-protobuf-lite, grpc-stub

### Server Integration
- Connects to Go gRPC server at localhost:50051
- Server receives messages via bidirectional stream
- Server broadcasts messages back to all clients
- Join messages trigger welcome responses from server

### Fixed
- Fixed message echo issue with duplicate detection
- Fixed connection cleanup with proper shutdown
- Fixed stream recreation on repeated startChat calls

## [0.9.0] - 2024-04-17

### Added
- 🎉 Initial release of Lavender Messenger (Android client)
- 📱 Basic messaging functionality
- 🔐 User authentication system
- 💾 Local message storage
- 🎨 Modern Material Design UI
- 🔄 Real-time message synchronization
- 📱 ViewBinding implementation
- 🧭 Navigation Component integration
- 🏗️ MVVM architecture setup
- 🧪 Unit and instrumentation test framework

### Technical Details
- **Min SDK**: 29 (Android 10.0)
- **Target SDK**: 37 (Android 14)
- **Compile SDK**: 37
- **Language**: Kotlin
- **Architecture**: MVVM with ViewBinding
- **Dependencies**:
  - AndroidX Core KTX
  - AndroidX AppCompat
  - Material Design Components
  - ConstraintLayout
  - Navigation Component
  - gRPC: grpc-okhttp, grpc-protobuf-lite, grpc-stub

### Configuration
- Gradle build system with Kotlin DSL
- Version code: 10
- Version name: 0.9.2
- Package name: msg.client.android

---

## Version History Guidelines

### Version Format
- **MAJOR**: Breaking changes, major feature additions
- **MINOR**: New features, improvements
- **PATCH**: Bug fixes, small improvements

### Categories
- **Added**: New features
- **Changed**: Changes in existing functionality
- **Deprecated**: Features that will be removed in future versions
- **Removed**: Features removed in this version
- **Fixed**: Bug fixes
- **Security**: Security-related changes

### Release Process
1. Update version in `app/build.gradle.kts`
2. Update version code and version name
3. Add changelog entry
4. Create release tag
5. Build and distribute release APK
