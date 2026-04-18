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
- 💾 Local message history storage
- 🎨 Modern user interface (Material Design)
- 🔄 Duplicate message filtering to prevent echo
- 📊 Real-time connection status tracking

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
- **Server**: Go gRPC server (localhost:50051)

## Requirements

- Android 10.0 (API level 29) or higher
- Minimum 2 GB RAM
- Running Go gRPC server on localhost:50051 (or 10.0.2.2:50051 for emulator)
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
│   │   ├── MainActivityMinimal.kt   # Entry point with username dialog
│   │   ├── ChatActivity.kt          # Main chat UI with RecyclerView
│   │   ├── ChatViewModel.kt         # State management with gRPC
│   │   ├── ui/
│   │   │   ├── MessageAdapter.kt    # RecyclerView adapter for messages
│   │   │   └── MessageViewHolder.kt # ViewHolder for messages
│   │   ├── data/
│   │   │   ├── models/
│   │   │   │   └── Message.kt       # Message model
│   │   │   ├── proto/
│   │   │   │   ├── MessageProto.kt  # Protobuf message
│   │   │   │   └── ProtoUtils.kt    # Protobuf utilities
│   │   │   └── grpc/
│   │   │       ├── GrpcClient.kt    # Wrapper for gRPC client
│   │   │       └── RealGrpcClient.kt # gRPC implementation with custom marshaller
│   │   └── viewmodel/
│   ├── res/                         # Resources
│   └── AndroidManifest.xml          # App manifest
└── build.gradle.kts                 # Build configuration
```

## Configuration

### Server
The application connects to Go gRPC server at:
- **Device**: localhost:50051
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
- **MAJOR.MINOR.PATCH** (e.g., 1.0.0)

Current version: **0.9.3** (versionCode: 11)

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
- 📝 Server address selection from predefined list (192.168.1.135:50051, 10.0.2.2:50051, localhost:50051)
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
