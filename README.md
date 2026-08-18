# Lavender Messenger — Android Client

**Author:** Pavel Davydov (ferz)
**Version:** 1.4.0.16
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
./gradlew testDebugUnitTest   # Unit tests (625+)
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
│   │   ├── GrpcConnectionManager.kt               # Channel lifecycle + reconnect
│   │   ├── GrpcReconnectStrategy.kt               # Exponential backoff
│   │   ├── GrpcAuthClient.kt                       # JWT auth
│   │   ├── GrpcChatClient.kt                       # Chat CRUD
│   │   ├── GrpcChatListV2Client.kt                 # Pin/archive/search
│   │   ├── GrpcChatAuxClient.kt                    # Users/FCM/mute
│   │   ├── GrpcMessageV2Client.kt                  # Messages v2 + history
│   │   ├── GrpcSavedMessagesClient.kt              # Saved Messages (ex-Favorites)
│   │   ├── GrpcAIv2Client.kt                       # AI v2 (streaming + CRUD + marketplace)
│   │   ├── GrpcProfileClient.kt                    # Contacts/themes
│   │   ├── ProfileClient.kt                        # Profile v2 (JWT)
│   │   ├── GrpcTypingClient.kt                     # Typing indicators
│   │   ├── GrpcDraftClient.kt                      # Draft messages
│   │   ├── GrpcCompanyClient.kt                    # Company access control
│   │   ├── GrpcStickerClient.kt                    # Sticker CRUD (13 RPCs)
│   │   ├── GrpcServerDiscoveryClient.kt            # Server discovery
│   │   ├── GrpcMarshallers.kt                      # Custom proto marshallers
│   │   ├── GrpcAIv2Marshallers.kt                  # AI v2 marshallers
│   │   ├── SecretChatGrpc.kt                       # E2EE chats
│   │   ├── NotificationsGrpc.kt                    # Notifications
│   │   ├── RemoteAgentGrpc.kt                      # Remote Agent
│   │   ├── ChatKeepAliveService.kt                 # Foreground service for stream
│   │   └── BearerTokenInterceptor.kt               # JWT interceptor
│   ├── ai/                                         # AI v2 domain
│   │   ├── AiV2ChatUseCase.kt                      # Chat + tool calling loop
│   │   ├── AiV2ChatManager.kt                      # Shared flows
│   │   ├── AiV2Models.kt                           # Domain models
│   │   ├── AiV2DomainExtensions.kt                 # Proto → Domain
│   │   └── RateLimitCache.kt                       # Client rate limit
│   ├── auth/AuthManager.kt                         # Token storage
│   ├── session/SessionManager.kt                   # Session lifecycle + token refresh
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
- Reve Image Generation: generate/edit/remix images via Reve 2.0 API
- Rate limit: client-side cache with countdown timer
- Graceful shutdown: SERVER_SHUTTINGDOWN signal + health check + backoff
- Remote Agent: SSH tunnel + shell/git/build/deploy/file/docker/AI tasks
- Custom theme system (light/dark + user-created themes)
- Push notifications with chat navigation
- Voice messages with waveform
- File/image attachments
- Message reactions, replies, and edits
- Self-destruct timer per chat (auto-delete messages)
- Voice/video calls (WebRTC)
- Background APK updates via WorkManager
- Localization (RU/English, RU default)
- Fast Mode: toggle avatars/animations off for performance
- Saved Messages: private notes synced across devices
- System message filtering (timer/call messages hidden from chat list)
- Company access control with per-company position lookup

## Connection & Auth Architecture

### gRPC Channel Lifecycle
- `GrpcConnectionManager` — owns channel, reconnect scheduling, keepalive
- `GrpcReconnectStrategy` — exponential backoff (5s → 30s cap)
- `ChatKeepAliveService` — foreground service monitoring connection status
- `BearerTokenInterceptor` — attaches JWT to every non-auth RPC

### Token Refresh
- `SessionManager.ensureFreshToken()` — synchronous refresh before gRPC calls
- `SessionManager.forceTokenRefresh()` — forced refresh (ignores expiry check)
- `SessionManager.performTokenRefresh()` — periodic (60s) background refresh
- Fallback: re-login with saved password when refresh token expired
- UNAUTHENTICATED retry on unary calls: markRead, loadHistoryV2, getSavedMessages, getAdminUserList, etc.

### Connection Resilience
- ChatV2 stream auto-reconnect on UNAVAILABLE/DEADLINE_EXCEEDED
- UNAUTHENTICATED → token refresh + retry (not permanent failure)
- Status debounce (1s) prevents UI flapping
- `isAuthFailure` flag never blocks reconnection — always allows channel rebuild

## Versioning

Format: `MAJOR.MINOR.PATCH.BUILD` (e.g., `1.4.0.16`)
Stored in `version.txt`.
`versionCode = major*1000000 + minor*10000 + patch*100 + build`

## Signing

- Keystore: `release.keystore` (password: `lavender123`, alias: `lavender`)
- **NOT committed** — stays on dev machine only

## Documentation

- `.mimocode/doc/INDEX.md` — project overview
- `.mimocode/doc/PATTERNS.md` — code patterns and rules
- `.mimocode/doc/PROMPT_NEXT_SESSION.md` — current plan and backlog
- `.mimocode/doc/GOTCHAS.md` — discovered knowledge, gotchas, edge cases
- `CHANGELOG.md` — full version history

## Tests

- **625+ unit tests** across 36 test files
- Run: `./gradlew testDebugUnitTest`
- Coverage: gRPC marshallers, session management, connection logic, chat adapters, system message filtering
