# Lavender Messenger — Android Client

**Author:** Pavel Davydov (ferz)  
**Version:** 1.0.7.1  
**Language:** Kotlin  

Native Android client for Lavender Messenger with gRPC bidirectional streaming, E2EE, and Material Design 3.

## Repository

This is the **Android client** repository.  
Server lives in a separate repo: `ferzferz11-sudo/msg`

## Requirements

- Android 10.0 (API 29) or higher
- Running Go gRPC server (159.195.38.145:50051 or 10.0.2.2:50051 for emulator)

## Build

```bash
./gradlew assembleDebug       # Debug build
./gradlew assembleRelease     # Release build (signed)
./gradlew clean assembleRelease  # Clean release
```

**Note:** ProGuard is disabled (`isMinifyEnabled = false`).

## Project Structure

```
msg.client.android/           # Android client repo root
├── app/
│   ├── build.gradle.kts      # App module build config
│   ├── google-services.json  # Firebase config — NOT committed
│   ├── release.keystore      # Signing keystore — NOT committed
│   ├── proguard-rules.pro
│   └── src/
│       ├── main/
│       │   ├── java/msg/client/android/
│       │   │   ├── MainActivity.kt           # Entry, server selection, username
│       │   │   ├── ChatListActivity.kt       # Chat list with search
│       │   │   ├── ChatActivity.kt           # Chat screen
│       │   │   ├── EditProfileActivity.kt    # Profile editing
│       │   │   ├── SuperAdminActivity.kt     # Admin tools
│       │   │   ├── NotificationActivity.kt   # Notification history
│       │   │   ├── data/
│       │   │   │   ├── grpc/
│       │   │   │   │   ├── GrpcClient.kt         # Facade
│       │   │   │   │   └── RealGrpcClient.kt     # gRPC implementation (singleton)
│       │   │   │   ├── session/
│       │   │   │   │   ├── CredentialStore.kt    # EncryptedSharedPreferences
│       │   │   │   │   └── SessionManager.kt     # StateFlow<UserSession>
│       │   │   │   ├── crypto/
│       │   │   │   │   └── E2EEManager.kt        # ECDH + AES-256-GCM
│       │   │   │   ├── db/                      # Room database
│       │   │   │   └── models/
│       │   │   ├── ui/
│       │   │   │   ├── chat/
│       │   │   │   │   ├── ChatViewModel.kt      # Chat state management
│       │   │   │   │   └── ChatViewModelFactory.kt
│       │   │   │   ├── viewmodel/
│       │   │   │   │   └── ChatListViewModel.kt  # Chat list state
│       │   │   │   ├── adapter/
│       │   │   │   │   └── MessageAdapter.kt
│       │   │   │   └── theme/                   # Theme system
│       │   │   └── theme/
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
├── README.ru.md
├── deploy_android.sh           # Build + rsync to server
└── gradle/
    ├── libs.versions.toml      # Version catalog
    └── wrapper/
```

## Tech Stack

| Component | Technology |
|-----------|-----------|
| Language | Kotlin |
| Architecture | MVVM |
| Async | Kotlin Coroutines + StateFlow |
| Network | gRPC (bidirectional streaming) |
| Serialization | Protobuf (protobuf-lite, manual) |
| Database | Room (SQLite) |
| Security | EncryptedSharedPreferences, ECDH, AES-256-GCM |
| Push | Firebase Cloud Messaging |
| UI | Material Design 3, ViewBinding |
| Min SDK | 29 (Android 10) |
| Compile/Target SDK | 34/35 |

## Versioning

Format: `MAJOR.MINOR.PATCH.BUILD` (e.g., `1.0.7.1`)  
Stored in `version.txt`.  
`versionCode = major*1000000 + minor*10000 + patch*100 + build`

## Key Features

- Real-time messaging via gRPC bidirectional streaming
- E2EE secret chats (ECDH key exchange)
- Custom theme system (light/dark + user-created themes)
- Push notifications with chat navigation
- Voice messages with waveform
- File/image attachments
- Message reactions & replies
- Server management (multi-server support)
- Background APK updates via WorkManager
- Localization (RU/English, RU default)

## Signing

- Keystore: `release.keystore` (password: `lavender123`, alias: `lavender`)
- **NOT committed** — stays on dev machine only

## Keys & Credentials

- `.env`, `release.keystore`, `google-services.json` — NOT in git
- APK download: `http://159.195.38.145:8081/lavender.apk`
