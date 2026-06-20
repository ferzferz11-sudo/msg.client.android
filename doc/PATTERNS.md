# Android — Code Patterns and Rules

**Version:** v1.3.0.5 | **Updated:** 2026-06-20

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
├── GrpcProfileClient — contacts, themes (ChatService)
├── ProfileClient — profile, avatar, settings, delete (ProfileService v2, JWT)
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

### AI v2 Pattern (v1.3.0.2)
```
GrpcAIv2Client — gRPC transport (chatWithAIV2 streaming + agent CRUD + tools + marketplace)
    ├── ChatWithAIV2 streaming with tool calling loop
    ├── Agent CRUD: createAgent, updateAgent, deleteAgent, getAgent, listAgents, cloneAgent
    ├── Tools: listTools
    └── Marketplace: rateAgent, getAgentReviews, listMarketplaceAgents, getAgentStats,
                     shareAgent, installAgent, getUsageStats

AiV2ChatUseCase — orchestrates chat with tool calling loop + marketplace methods
    ├── chat(userId, sessionId, message, agentId, images, scope)
    │   └── executeStream() → if tool_calls → send back → repeat (max 10 iterations)
    ├── Agent CRUD + Tools
    └── Marketplace: listMarketplaceAgents, getAgentStats, getAgentReviews,
                     rateAgent, shareAgent, installAgent, getUsageStats

AiV2ChatManager — shared flows for UI observation
    ├── aiResponses: SharedFlow<AiV2ChatMessage>
    ├── aiTyping: SharedFlow<Boolean>
    ├── agents: StateFlow<List<AiV2Agent>>
    ├── tools: StateFlow<List<AiV2Tool>>
    └── streamState: StateFlow<AiV2StreamState>

UI:
    ├── AiV2ChatActivity + AiV2ChatViewModel — unified AI chat screen + rate limit
    ├── AiV2AgentListActivity + AiV2AgentListViewModel — agent list (5 tabs)
    │   ├── Tab 0: Presets
    │   ├── Tab 1: My Agents
    │   ├── Tab 2: Public
    │   ├── Tab 3: Marketplace (search, sort, filter, pagination, pull-to-refresh)
    │   └── Tab 4: Usage (stats)
    ├── AiV2AgentCreateEditActivity + AiV2AgentCreateEditViewModel — agent create/edit
    ├── MarketplaceViewModel — marketplace catalog with pagination + sort/filter
    ├── AgentDetailViewModel — agent details (stats, reviews, rate/share/install)
    ├── UsageStatsViewModel — usage statistics
    ├── MarketplaceAgentAdapter — marketplace agent cards + skeleton loading
    ├── AgentDetailActivity — agent detail screen
    ├── ReviewAdapter — review list
    ├── RateAgentBottomSheet — rate agent (1-5 stars + review)
    ├── InstallAgentBottomSheet — install agent by share code
    └── UsageStatsAdapter — per-agent usage stats
```
- Server executes all built-in tools (search_messages, web_search, etc.)
- Client only sends tool_calls result back to server
- Agent provider_type: openrouter, local, mimo, webhook, websocket, subprocess, mcp
- 8 preset agents: mimo, assistant, developer, devops, architect, writer, analyst, translator
- Marketplace: search with debounce, infinite scroll, pull-to-refresh, deep link install
- Rate limit: RateLimitCache + countdown + disable input on limit

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

### Marketplace Pattern (v1.3.0.2)
```
AiV2AgentListActivity (Tab 3: Marketplace)
  ├── SearchBar (TextInputLayout + debounce 2+ chars)
  ├── SortFilterBar (Spinner + ChipGroup)
  │   ├── Sort: Rating / Installs / Name
  │   └── Filter: Tools / OpenRouter / MiMo / Local
  ├── SwipeRefreshLayout (pull-to-refresh)
  ├── RecyclerView (MarketplaceAgentAdapter)
  │   ├── TYPE_ITEM: agent cards
  │   ├── TYPE_SKELETON: loading placeholders (6 cards)
  │   └── OnScrollListener → loadMore() (infinite scroll)
  └── EmptyView ("No public agents available yet")

MarketplaceViewModel
  ├── loadAgents(query) → reset offset, fetch first page
  ├── loadMore() → append next page
  ├── setSortOption(option) → client-side sort
  ├── setFilterProvider(provider) → client-side filter
  ├── setFilterToolsEnabled(enabled) → client-side filter
  └── StateFlow: agents, isLoading, isLoadingMore, error, sortOption, filterProvider, filterToolsEnabled

MarketplaceAgentAdapter
  ├── submitList(agents) → show real data
  ├── showSkeleton() → show 6 skeleton cards
  └── Multi-viewType: TYPE_ITEM / TYPE_SKELETON
```

### Rate Limit Pattern (v1.3.0.1)
```
RateLimitCache (client-side)
  ├── Sliding window: 10 requests per 60 seconds per agent_id
  ├── getRemaining(agentId) → check cache
  ├── recordRequest(agentId) → add timestamp
  ├── getTimeUntilReset(agentId) → milliseconds until oldest expires
  └── undoLastRecord(agentId) → rollback on server error

AiV2ChatActivity
  ├── canSendRequest() → rateLimitCache.getRemaining() > 0
  ├── sendMessage() → recordRequest() → chat()
  ├── On error "rate limit" → undoLastRecord() → showRateLimitUI()
  └── showRateLimitUI(waitMs) → disable input + countdown + auto-restore
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

### ProfileService v2 Pattern
```
ProfileClient (object) — JWT-only, no user_id in request
├── getProfile() → GetProfileResponseProto (userId, username, email, avatar, bio, status, locale, isSuperAdmin)
├── updateProfile(username, bio, status, locale) → Boolean
├── updateAvatar(avatarUrl, fullAvatarUrl) → Boolean
├── deleteProfile(password) → Boolean
├── getUserSettings() → GetUserSettingsResponseProto (locale, themeId, pushEnabled)
└── updateUserSettings(locale, themeId, pushEnabled) → Boolean

GrpcProfileClient (class) — ChatService methods (no v2 replacement)
├── updateUsername, updatePassword, adminUpdatePassword
├── requestPasswordReset, resetPassword
├── addContact, removeContact, getContacts
├── getThemes, saveTheme, setCurrentTheme, deleteTheme
├── getUserAvatar (for other users)
└── getDevices, deleteDevice, deleteOtherDevices, getFCMLogs
```
- `user_id` из JWT context — не передавать в запросе
- `DeleteProfile` требует `password`, НЕ `username`

### Unread Count Pattern (v1.3.0.4)
```
Сервер: GetUserChatsV2 CTE
  user_last_read: SELECT room_id, last_read_at FROM user_chat_metadata WHERE user_id=$1
  unread_counts: COUNT(messages WHERE created_at > last_read_at AND username != current_user)

Клиент: ChatListViewModel
  newMessageEvent: unreadCount++ if isFromOther && !isRead
  markAsRead: unreadCount = 0 (local) + MarkRead RPC (server)
  loadChats: merge(server, local) — max(local.unread, server.unread)
  syncChats: allChats (merged) → Room DB (preserve local unread)
```
- `is_read` флаг глобальный — НЕ использовать для подсчёта unread
- `last_read_at` per-user — правильный способ подсчёта

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

### HttpClient Singleton Pattern (v1.3.0.5)
```
network/HttpClient.kt — object HttpClient
  ├── client: OkHttpClient (singleton)
  ├── connectTimeout: 30s
  ├── readTimeout: 30s
  ├── writeTimeout: 30s
  └── connectionPool: 5 connections, 5 min TTL

Usage:
  import lavender.client.android.network.HttpClient
  HttpClient.client.newCall(request).enqueue(callback)
```
- Use `HttpClient.client` everywhere instead of `OkHttpClient()`
- Exception: `LavenderGlideModule` — separate client (60s timeouts, followRedirects, retryOnConnectionFailure)
- Connection pool reuses TCP connections for -40% faster repeated loads

### Logging Pattern (v1.3.0.5)
```
DO:
  Log.e(TAG, "Failed to load chats", e)        — errors always logged
  Log.w(TAG, "loadChats timeout")               — warnings for degraded state
  Log.d(TAG, "Synced 16 chats (123ms)")          — key events with timing

DON'T:
  Log.d(TAG, "MERGE: inhale & ferz local=1...")  — hot-path per-message noise
  Log.d(TAG, "Token refresh triggered")          — periodic task noise
  Log.d(TAG, "Stream reconnection successful")   — retry loop noise
```
- Errors (`Log.e`): always keep
- Warnings (`Log.w`): keep for degraded state (timeout, auth failure, reconnect)
- Debug (`Log.d`): only key events (startup, sync summary with timing, critical transitions)
- Remove: per-message logs, periodic task logs, retry loop iteration logs
- Add timing: `${System.currentTimeMillis() - startTime}ms` for critical operations

### SplashActivity Pattern (v1.3.0.5)
```kotlin
// Use lifecycleScope instead of postDelayed to avoid assignParent to null warning
lifecycleScope.launch {
    delay(400)
    if (!isFinishing && !isDestroyed) {
        navigateToTarget(...)
    }
}
```
- `postDelayed` on views causes `DecorView assignParent to null` when `finish()` is called during delay
- `lifecycleScope` auto-cancels on Activity destruction
- Always check `!isFinishing && !isDestroyed` before navigation

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
16. AI v2 RPC: all methods under `messenger.ChatService/*` (NOT `AIService`)
17. Unread count: based on `user_chat_metadata.last_read_at`, NOT `messages.is_read`
18. ProfileService v2: profile/avatar/delete/settings via `messenger.ProfileService/*` (JWT context)
