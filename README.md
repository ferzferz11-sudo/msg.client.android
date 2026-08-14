# Lavender Messenger — Android Client

**Author:** Pavel Davydov (ferz)
**Version:** 1.4.0.11
**Language:** Kotlin 2.4.0

Native Android client for Lavender Messenger with gRPC bidirectional streaming, E2EE, Material Design 3, AI v2 chat integration, Marketplace, Reve Image Generation, and Remote Agent.

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
app/src/main/java/lavender/client/android/
├── SplashActivity.kt, SplashLoadingActivity.kt   # Splash screens
├── ChatListActivity (ui/chatlist/)                # Chat list (10 modules)
├── NewChatActivity                                # Chat (6 delegates)
├── ProfileActivity, EditProfileActivity           # Profiles
├── ContactsActivity                               # Contacts
├── ThemesActivity, ThemePaletteActivity           # Theme system
├── SuperAdminActivity                             # Admin tools
├── SecurityActivity, ServersActivity              # Settings
├── ChangelogActivity                              # Version history
├── CallActivity, ConferenceLobbyActivity          # Voice/video calls
├── FullScreenImageActivity, VideoPlayerActivity   # Media
├── ShareReceiverActivity                          # Share intents
├── LogViewerActivity                              # Debug logs
│
├── data/
│   ├── grpc/                                      # gRPC clients
│   │   ├── GrpcClient.kt                          # Facade
│   │   ├── RealGrpcClient.kt                      # Orchestrator
│   │   ├── GrpcAuthClient.kt                       # JWT auth
│   │   ├── GrpcChatClient.kt                       # Chat CRUD
│   │   ├── GrpcChatListV2Client.kt                 # Pin/archive/search
│   │   ├── GrpcChatAuxClient.kt                    # Users/FCM/mute
│   │   ├── GrpcAIv2Client.kt                       # AI v2 (streaming + CRUD + marketplace)
│   │   ├── GrpcProfileClient.kt                    # Contacts/themes
│   │   ├── ProfileClient.kt                        # Profile v2 (JWT)
│   │   ├── GrpcMarshallers.kt                      # Custom proto marshallers
│   │   ├── GrpcAIv2Marshallers.kt                  # AI v2 marshallers
│   │   ├── SecretChatGrpc.kt                       # E2EE chats
│   │   ├── NotificationsGrpc.kt                    # Notifications
│   │   ├── RemoteAgentGrpc.kt                      # Remote Agent
│   │   └── BearerTokenInterceptor.kt               # JWT interceptor
│   ├── ai/                                         # AI v2 domain
│   │   ├── AiV2ChatUseCase.kt                      # Chat + tool calling loop
│   │   ├── AiV2ChatManager.kt                      # Shared flows
│   │   ├── AiV2Models.kt                           # Domain models
│   │   ├── AiV2DomainExtensions.kt                 # Proto → Domain
│   │   └── RateLimitCache.kt                       # Client rate limit
│   ├── auth/AuthManager.kt                         # Token storage
│   ├── session/SessionManager.kt                   # Session lifecycle
│   ├── db/                                         # Room database
│   ├── fcm/                                        # Firebase messaging
│   ├── calls/                                      # WebRTC calls
│   ├── cache/                                      # Cache utilities
│   └── models/                                     # DTOs, proto models
│
├── ui/
│   ├── ai/                                         # AI v2 screens
│   │   ├── AiV2ChatActivity.kt                     # Unified AI chat
│   │   ├── AiV2AgentListActivity.kt                # 5 tabs (Presets/My/Public/Marketplace/Usage)
│   │   ├── AiV2AgentCreateEditActivity.kt          # Agent create/edit
│   │   ├── AgentDetailActivity.kt                  # Agent details + reviews
│   │   ├── MarketplaceViewModel.kt                 # Marketplace catalog
│   │   ├── AgentDetailViewModel.kt                 # Agent stats/reviews
│   │   ├── UsageStatsViewModel.kt                  # Usage statistics
│   │   └── adapters + bottom sheets
│   ├── chat/                                       # Chat widgets
│   ├── chatlist/                                   # Chat list modules
│   ├── remote/                                     # Remote Agent
│   │   ├── RemoteAgentActivity.kt                  # Agent chat
│   │   ├── RemoteAgentSettingsActivity.kt          # Settings
│   │   ├── RemoteAgentService.kt                   # Foreground service
│   │   └── HermesGatewayManager.kt                 # SSH tunnel
│   ├── profile/                                    # Profile ViewModel
│   ├── adapter/                                    # RecyclerView adapters
│   └── widget/                                     # FAB, bottom sheets
│
├── network/HttpClient.kt                           # Singleton OkHttpClient
├── theme/                                          # Theme system
└── LogViewerActivity.kt
```

## Tech Stack

| Component          | Technology                                    |
|--------------------|-----------------------------------------------|
| Language           | Kotlin 2.4.0                                 |
| Architecture       | MVVM                                          |
| Async              | Kotlin Coroutines + StateFlow                 |
| Network            | gRPC (bidirectional streaming)                |
| Serialization      | Protobuf (protobuf-lite, manual marshallers)  |
| Database           | Room (SQLite)                                 |
| Security           | EncryptedSharedPreferences, ECDH, AES-256-GCM |
| Push               | Firebase Cloud Messaging                      |
| UI                 | Material Design 3, ViewBinding                |
| Min SDK            | 29 (Android 10)                               |
| Compile/Target SDK | 37/35                                         |

## Key Features

- Real-time messaging via gRPC bidirectional streaming
- E2EE secret chats (ECDH key exchange)
- AI v2 chat: streaming + tool calling + 7 provider types (openrouter, local, mimo, webhook, websocket, subprocess, mcp)
- AI Marketplace: rate, review, install, share agents + search/pagination/sort/filter
- **Reve Image Generation:** generate/edit/remix images via Reve 2.0 API (image_url in chat responses)
- Rate limit: client-side cache with countdown timer
- Graceful shutdown: SERVER_SHUTTINGDOWN signal + health check + backoff
- Remote Agent: SSH tunnel + shell/git/build/deploy/file/docker/AI tasks
- Custom theme system (light/dark + user-created themes)
- Push notifications with chat navigation
- Voice messages with waveform
- File/image attachments
- Message reactions & replies
- Server-side history clear (ClearRoomHistory RPC)
- Self-destruct timer per chat (auto-delete messages)
- Voice/video calls (WebRTC)
- Background APK updates via WorkManager
- Localization (RU/English, RU default)

## Versioning

Format: `MAJOR.MINOR.PATCH.BUILD` (e.g., `1.3.0.5`)
Stored in `version.txt`.
`versionCode = major*1000000 + minor*10000 + patch*100 + build`

## Signing

- Keystore: `release.keystore` (password: `lavender123`, alias: `lavender`)
- **NOT committed** — stays on dev machine only

## Documentation

- `doc/INDEX.md` — project overview
- `doc/PATTERNS.md` — code patterns and rules
- `doc/PROMPT_NEXT_SESSION.md` — current plan and backlog
- `doc/AI_V2_TESTING.md` — AI v2 test scenarios
- `doc/REMOTE_AGENT.md` — Remote Agent reference
- `doc/CODE_AUDIT.md` — unused code and import audit
