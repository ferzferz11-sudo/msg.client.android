# Lavender Messenger — Android Client

**Author:** Pavel Davydov (ferz)
**Version:** 1.1.1.16
**Language:** Kotlin

Native Android client for Lavender Messenger with gRPC bidirectional streaming, E2EE, Material Design 3, and AI chat integration (OWL + Hermes).

## Repository

This is the **Android client** repository.
Server lives in a separate repo: `ferzferz11-sudo/msg`

## Requirements

- Android 10.0 (API 29) or higher
- Running Go gRPC server (13.140.25.249:50051 or 10.0.2.2:50051 for emulator)

## Build

```bash
./gradlew assembleDebug       # Debug build
./gradlew assembleRelease     # Release build (signed)
./gradlew clean assembleRelease  # Clean release
```

**Note:** ProGuard is disabled (`isMinifyEnabled = false`).

## Project Structure

```
msg.client.android/                  # Android client repo root
├── app/
│   ├── build.gradle.kts             # App module build config
│   ├── google-services.json         # Firebase config — NOT committed
│   ├── release.keystore             # Signing keystore — NOT committed
│   ├── proguard-rules.pro
│   └── src/
│       ├── main/
│       │   ├── java/lavender/client/android/
│       │   │   ├── SplashActivity.kt           # Splash screen
│       │   │   ├── ChatListActivity.kt         # Chat list with search
│       │   │   ├── NewChatActivity.kt           # New chat creation
│       │   │   ├── ContactsActivity.kt          # Contacts management
│       │   │   ├── EditProfileActivity.kt      # Profile editing
│       │   │   ├── ProfileActivity.kt          # View profile
│       │   │   ├── SuperAdminActivity.kt       # Admin tools
│       │   │   ├── NotificationActivity.kt     # Notifications
│       │   │   ├── NotificationLogActivity.kt  # Notification history
│       │   │   ├── ChangelogActivity.kt        # App changelog viewer
│       │   │   ├── FavoritesActivity.kt        # Favorite chats
│       │   │   ├── ServersActivity.kt          # Server selection
│       │   │   ├── SecurityActivity.kt         # Security settings
│       │   │   ├── ThemesActivity.kt           # Theme management
│       │   │   ├── ThemePaletteActivity.kt     # Theme editor
│       │   │   ├── BackgroundsFragment.kt      # Chat backgrounds
│       │   │   ├── PaletteFragment.kt          # Color palette
│       │   │   ├── CallActivity.kt             # Voice/video calls
│       │   │   ├── ConferenceLobbyActivity.kt  # Conference lobby
│       │   │   ├── MapPickerActivity.kt        # Location sharing
│       │   │   ├── FullScreenImageActivity.kt  # Image viewer
│       │   │   ├── VideoPlayerActivity.kt      # Video player
│       │   │   ├── ShareReceiverActivity.kt    # Share intent handler
│       │   │   ├── FCMLogsActivity.kt          # FCM debug logs
│       │   │   ├── data/
│       │   │   │   ├── grpc/
│       │   │   │   │   ├── GrpcClient.kt          # Facade
│       │   │   │   │   ├── RealGrpcClient.kt      # gRPC implementation (singleton)
│       │   │   │   │   ├── OwlGrpc.kt             # OWL AI gRPC
│       │   │   │   │   ├── HermesGrpc.kt          # Hermes gRPC
│       │   │   │   │   ├── SecretChatGrpc.kt      # Secret chat gRPC
│       │   │   │   │   └── ServerConnectivityTest.kt
│       │   │   │   ├── session/
│       │   │   │   │   ├── CredentialStore.kt     # EncryptedSharedPreferences
│       │   │   │   │   ├── SessionManager.kt      # StateFlow<UserSession>
│       │   │   │   │   └── UserSession.kt
│       │   │   │   ├── crypto/
│       │   │   │   │   └── E2EEManager.kt         # ECDH + AES-256-GCM
│       │   │   │   ├── db/                       # Room database
│       │   │   │   │   ├── AppDatabase.kt
│       │   │   │   │   ├── Daos.kt
│       │   │   │   │   └── Entities.kt
│       │   │   │   ├── fcm/
│       │   │   │   │   ├── LavenderMessagingService.kt
│       │   │   │   │   └── NotificationHistory.kt
│       │   │   │   ├── models/                  # DTOs, proto models
│       │   │   │   ├── changelog/               # Changelog parsing/rendering
│       │   │   │   ├── updates/                 # APK update manager
│       │   │   │   └── proto/                   # Protobuf manual parsing
│       │   │   ├── ui/
│       │   │   │   ├── chat/
│       │   │   │   │   ├── ChatViewModel.kt
│       │   │   │   │   ├── ChatViewModelFactory.kt
│       │   │   │   │   └── widget/              # Unified chat widget
│       │   │   │   │       ├── ChatMessageAdapter.kt
│       │   │   │   │       ├── ChatWidget.kt
│       │   │   │   │       └── MentionAdapter.kt
│       │   │   │   ├── owl/                     # OWL AI chat
│       │   │   │   │   ├── OwlChatActivity.kt
│       │   │   │   │   ├── OwlChatViewModel.kt
│       │   │   │   │   └── OwlSettingsActivity.kt
│       │   │   │   ├── hermes/                  # Hermes AI chat
│       │   │   │   │   ├── HermesChatActivity.kt
│       │   │   │   │   ├── HermesChatViewModel.kt
│       │   │   │   │   ├── HermesChatAdapter.kt
│       │   │   │   │   ├── HermesCommandAdapter.kt
│       │   │   │   │   ├── AgentListActivity.kt
│       │   │   │   │   ├── AgentListViewModel.kt
│       │   │   │   │   ├── AgentListAdapter.kt
│       │   │   │   │   ├── AgentSettingsActivity.kt
│       │   │   │   │   └── AgentSettingsBottomSheet.kt
│       │   │   │   ├── notification/
│       │   │   │   ├── viewmodel/
│       │   │   │   │   └── ChatListViewModel.kt
│       │   │   │   ├── adapter/                 # RecyclerView adapters
│       │   │   │   ├── audio/                   # Audio player/recorder views
│       │   │   │   ├── calls/                   # Call ViewModel
│       │   │   │   └── widget/                  # Bottom sheets, FAB
│       │   │   │       ├── AIBottomSheet.kt
│       │   │   │       ├── CommandBottomSheet.kt
│       │   │   │       └── LavenderFab.kt
│       │   │   ├── theme/                       # Theme system
│       │   │   │   ├── Theme.kt
│       │   │   │   ├── ThemeStore.kt
│       │   │   │   ├── ThemeUtils.kt
│       │   │   │   ├── BuiltInThemes.kt
│       │   │   │   ├── data/                   # Theme data layer
│       │   │   │   └── ui/                     # ThemeApplier, ThemeUi
│       │   │   └── LogViewerActivity.kt
│       │   ├── proto/
│       │   │   └── messenger.proto           # Protobuf schema (manual parsing)
│       │   └── res/
│       │       ├── layout/                   # Activity & item layouts
│       │       ├── drawable/                 # Message backgrounds, icons
│       │       ├── values/                   # colors, strings, themes (EN)
│       │       ├── values-ru/               # Russian strings
│       │       ├── values-night/            # Dark theme colors
│       │       └── xml/                      # Security config, file paths
│       ├── androidTest/                     # Instrumented tests
│       └── test/                            # Unit tests
├── build.gradle.kts            # Project build config
├── settings.gradle.kts
├── version.txt                 # Version: "MAJOR.MINOR.PATCH.BUILD"
├── CHANGELOG.md
├── README.md
├── deploy_android.sh           # Build + rsync to server
├── doc/                        # Documentation (INDEX.md, TASKS.md)
├── scripts/                    # Build/deploy scripts
├── releases/                   # Release artifacts
└── gradle/
    ├── libs.versions.toml      # Version catalog
    └── wrapper/
```

## Tech Stack

| Component          | Technology                                    |
|--------------------|-----------------------------------------------|
| Language           | Kotlin                                        |
| Architecture       | MVVM                                          |
| Async              | Kotlin Coroutines + StateFlow                 |
| Network            | gRPC (bidirectional streaming)                |
| Serialization      | Protobuf (protobuf-lite, manual)              |
| Database           | Room (SQLite)                                 |
| Security           | EncryptedSharedPreferences, ECDH, AES-256-GCM |
| Push               | Firebase Cloud Messaging                      |
| UI                 | Material Design 3, ViewBinding                |
| Min SDK            | 29 (Android 10)                               |
| Compile/Target SDK | 37/35                                         |

## Versioning

Format: `MAJOR.MINOR.PATCH.BUILD` (e.g., `1.1.1.16`)
Stored in `version.txt`.
`versionCode = major*1000000 + minor*10000 + patch*100 + build`

## Key Features

- Real-time messaging via gRPC bidirectional streaming
- E2EE secret chats (ECDH key exchange)
- AI chat: OWL (OpenRouter models, free model list) + Hermes Orchestrator (multi-agent, RAG, tool calling)
- Custom theme system (light/dark + user-created themes)
- Push notifications with chat navigation
- Voice messages with waveform
- File/image attachments
- Message reactions & replies
- Voice/video calls (WebRTC)
- Server discovery via gRPC ListServers (multi-server support)
- Background APK updates via WorkManager
- Localization (RU/English, RU default)
- Changelog viewer with bundled + GitHub fallback

## Signing

- Keystore: `release.keystore` (password: `lavender123`, alias: `lavender`)
- **NOT committed** — stays on dev machine only

## Keys & Credentials

- `.env`, `release.keystore`, `google-services.json` — NOT in git
- APK download: `https://lavender-messenger.com/download`
