# Android — Code Patterns and Rules

**Version:** v1.3.0.0 | **Updated:** 2026-06-20

---

## Patterns

### GrpcClient Facade Pattern
```
GrpcClient (facade) — StateFlow declarations + inline domain delegates
    ├── StateFlow/SharedFlow declarations (15+)
    ├── Mutable state properties (4)
    ├── Core lifecycle: connect, disconnect, startChat, loadHistory
    └── Domain methods: signInV2, getChats, sendMessage, etc. (inline delegates)

RealGrpcClient (orchestrator) delegates to:
├── GrpcConnectionManager — connect/reconnect/health check
├── GrpcAuthClient — JWT auth (v2 only)
├── GrpcTypingClient — typing stream
├── GrpcCallClient — calls
├── GrpcChatClient (~250) — getChats, create/delete, participants
├── GrpcChatListV2Client (~120) — pin/unpin, search, archive
├── GrpcChatAuxClient (~130) — users, FCM, mute
├── GrpcChatListClient (~255) — chat list version, create/delete
├── GrpcProfileClient — profile, avatar, contacts, themes
├── GrpcDraftClient, GrpcFavoritesClient, GrpcMessageClient
├── GrpcServerDiscoveryClient — server discovery
├── GrpcAIv2Client — AI v2 (ChatWithAIV2, Agent CRUD, Tools, Marketplace)
├── SecretChatGrpc, ProfileClient
├── NotificationsGrpc — notifications (subscribe, history, read, unread)
└── RemoteAgentGrpc — Remote Agent (list, deploy, tokens, process)
```
- Each module: separate class with clear responsibility
- DI via constructor (no framework)
- RealGrpcClient: StateFlow declarations → module init → chat stream → proxy methods
- **CRITICAL:** StateFlow declared BEFORE modules (Kotlin object top-to-bottom init)
- GrpcClient: extension functions don't work via star import — all methods inline

### AI v2 Pattern (v1.3.0.0)
```
GrpcAIv2Client — gRPC transport (chatWithAIV2 streaming + agent CRUD + tools + marketplace)
    ├── ChatWithAIV2 streaming with tool calling loop
    ├── Agent CRUD: createAgent, updateAgent, deleteAgent, getAgent, listAgents, cloneAgent
    ├── Tools: listTools
    └── Marketplace: rateAgent, getAgentReviews, listMarketplaceAgents, getAgentStats,
                     shareAgent, installAgent, getUsageStats

AiV2ChatUseCase — orchestrates chat with tool calling loop
    ├── chat(userId, sessionId, message, agentId, images, scope)
    │   └── executeStream() → if tool_calls → send back → repeat (max 10 iterations)
    └── Agent CRUD + Tools

AiV2ChatManager — shared flows for UI observation
    ├── aiResponses: SharedFlow<AiV2ChatMessage>
    ├── aiTyping: SharedFlow<Boolean>
    ├── agents: StateFlow<List<AiV2Agent>>
    ├── tools: StateFlow<List<AiV2Tool>>
    └── streamState: StateFlow<AiV2StreamState>

UI:
    ├── AiV2ChatActivity + AiV2ChatViewModel — unified AI chat screen
    ├── AiV2AgentListActivity + AiV2AgentListViewModel — agent list (tabs: Presets/My/Public)
    └── AiV2AgentCreateEditActivity + AiV2AgentCreateEditViewModel — agent create/edit
```
- Server executes all built-in tools (search_messages, web_search, etc.)
- Client only sends tool_calls result back to server
- Agent provider_type: openrouter, local, mimo, webhook, websocket, subprocess, mcp
- 8 preset agents: mimo, assistant, developer, devops, architect, writer, analyst, translator

### Graceful Shutdown Pattern (v1.3.0.0)
```
Server sends SERVER_SHUTTINGDOWN via Chat stream
  → RealGrpcClient._serverShuttingDown = true
  → connectionStatus = RECONNECTING
  → ChatListActivity shows "Server restarting…"

On UNAVAILABLE error:
  → Retry loop with health check (GET /health)
  → If 503 (shutting_down) → exponential backoff (max 30s)
  → If 200 → reconnect
  → _serverShuttingDown = false on READY
```

### ChatListActivity Modular Pattern
```
ChatListActivity — onCreate, setupUI, lifecycle, proxy methods
├── ChatListToolbar — toolbar + settings sheets
├── ChatListTabs — tabs (All/Groups/AI Chats)
├── ChatListActionMode — selection mode
├── ChatListSearch — search
├── ChatListFABs — FABs + action sheets + AI bottom sheet
├── ChatListNavigation — navigateToChat
├── ChatListAuth — auth dialogs
├── ChatListViewModel — ViewModel with StateFlow
├── ChatListSections — sections
└── UpdateCoordinator — updates
```

### Chat Delegates Pattern (NewChatActivity)
```
NewChatActivity → 6 delegates:
├── ChatToolbarDelegate — toolbar, avatar, subtitle, navigation
├── ChatInputDelegate — text input, send, attachments, audio, emoji, mentions
├── ChatSelectionDelegate — selection mode, copy/pin/delete/forward
├── ChatSearchDelegate — in-chat search
├── ChatE2EEDelegate — end-to-end encryption for secret chats
├── ChatMessageMenuDelegate — reactions, context menu
```

### StandardBottomSheet Pattern
All sheets extend `StandardBottomSheet`: ServerAuth, Login, Register, AI, NewChat.
Drag handle + title automatically via `widget_standard_bottom_sheet.xml`.

### Bearer Token Interceptor Pattern
- Attaches JWT to all gRPC calls (except AuthService)
- v2 only — no v1 password fallback
- Proactive refresh every 60s, 5 min before expiry
- Per-server validation: tokens bound to server
- **ensureFreshToken()** — sync refresh before Chat stream

### JWT Auth Pattern (v2 Only)
```
AuthManager: storeTokens, getAccessToken, isTokenExpiredOrExpiring
BearerTokenInterceptor: attach JWT to all calls except AuthService
SessionManager: ensureFreshToken() sync refresh before chat stream
Token refresh: proactive every 60s + sync before chat stream
```

### Toolbar Pattern
```xml
<MaterialToolbar
    android:layout_height="@dimen/custom_toolbar_height"
    android:background="@drawable/toolbar_background"
    android:elevation="0dp"
    app:navigationIcon="@drawable/ic_back_arrow"
    app:navigationIconTint="?attr/colorOnPrimary" />
```
- Fixed height, not wrap_content
- Elevation 0dp (handled by toolbar_background drawable)
- `setDecorFitsSystemWindows(window, false)` required in Activity.onCreate

### Marshallers Pattern
- Custom marshallers for each proto type (not using protobuf-java reflection)
- Request marshallers: serialize all fields
- Response marshallers: parse by field number, skip unknown fields
- v2 fields (isPinned, isMuted, etc.) must be included in parser

### Sheet Navigation Pattern
- `isNavigatingDeeper` flag prevents `onBack` callback when navigating to child sheet/activity
- `settingsActivityLauncher`/`editProfileLauncher` — `ActivityResultContracts` for lifecycle-aware launching
- `setOnDismissListener` on each sheet: if `!isNavigatingDeeper` → call `onBack` to reopen parent

---

## Rules

1. Do NOT compile Android on server (OOM)
2. Do NOT deploy to prod without explicit instruction
3. userId (UUID) — always as key, NOT username
4. i18n: all new strings simultaneously in values/strings.xml + values-ru/strings.xml
5. Do NOT initialize getString() in Activity class fields
6. Kotlin 2.3.21: cont.resume(value, onCancellation = {})
7. All errors via `ErrorHandler.handle()` — NOT direct `Log.e`
8. v2 server only — no v1 legacy fallbacks
9. Chat toolbars: fixed `@dimen/custom_toolbar_height`, elevation 0dp
10. All chat activities: `setDecorFitsSystemWindows(window, false)` in onCreate
11. Marshallers: always include v2 proto fields
12. JWT freshness: `ensureFreshToken()` before Chat stream
13. Run `./gradlew assembleDebug` before committing
14. Do NOT bump version — only user bumps version
15. Marshallers field order: server proto defines field numbers
