# Android — Code Patterns and Rules

**Version:** v1.3.2.17 | **Updated:** 2026-07-17

---

## Patterns

### GrpcClient Facade Pattern
```
GrpcClient (facade) — StateFlow declarations + inline domain delegates
    ├── StateFlow/SharedFlow declarations (15+)
    ├── Mutable state properties (4)
    ├── Core lifecycle: connect, disconnect, startChatV2, loadHistoryV2
    └── Domain methods: signInV2, getChats, sendMessageV2, etc. (inline delegates)

RealGrpcClient (orchestrator) delegates to:
├── GrpcConnectionManager — connect/reconnect/health check
├── GrpcAuthClient — JWT auth (v2 only)
├── GrpcTypingClient — typing stream
├── GrpcCallClient — calls
        ├── GrpcChatClient (~250) — getChats, create/delete, participants, settings
        ├── GrpcChatListV2Client (~120) — pin/unpin, search, archive
        ├── GrpcChatAuxClient (~130) — users, FCM, mute
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

### AI v2 Pattern (v1.3.0.15)
```
GrpcAIv2Client — gRPC transport (chatWithAIV2 streaming + agent CRUD + tools + marketplace + history)
    ├── ChatWithAIV2 streaming with tool calling loop
    ├── Agent CRUD: createAgent, updateAgent, deleteAgent, getAgent, listAgents, cloneAgent
    ├── Tools: listTools
    ├── Marketplace: rateAgent, getAgentReviews, listMarketplaceAgents, getAgentStats,
    │                shareAgent, installAgent, getUsageStats
    ├── Chat History: getAIV2ChatHistory, listAIV2Chats
    └── Chat Settings: getChatSettings, updateChatSettings

AiV2ChatUseCase — orchestrates chat with tool calling loop + marketplace methods
    ├── chat(userId, sessionId, message, agentId, images, imageUri, scope)
    │   └── executeStream() → if tool_calls → send back → repeat (max 10 iterations)
    ├── Agent CRUD + Tools
    ├── Marketplace: listMarketplaceAgents, getAgentStats, getAgentReviews,
    │                rateAgent, shareAgent, installAgent, getUsageStats
    ├── Chat History: getChatHistory(sessionId, limit)
    └── Chat List: listAIChats()

AiV2ChatManager — shared flows for UI observation
    ├── aiResponses: SharedFlow<AiV2ChatMessage>
    ├── aiTyping: SharedFlow<Boolean>
    ├── agents: StateFlow<List<AiV2Agent>>
    ├── tools: StateFlow<List<AiV2Tool>>
    └── streamState: StateFlow<AiV2StreamState>

UI:
    ├── AiV2ChatActivity + AiV2ChatViewModel — unified AI chat + rate limit + image support + multi-agent
    ├── AIBottomSheet — agent selection with checkboxes → create AI chat
    ├── AiAgentSetupActivity — create/edit all agent types
    ├── MarketplaceViewModel — marketplace catalog with pagination + sort/filter
    ├── AgentDetailViewModel — agent details (stats, reviews, rate/share/install)
    ├── MarketplaceAgentAdapter — marketplace agent cards + skeleton loading
    ├── ReviewAdapter — review list
    ├── RateAgentBottomSheet — rate agent (1-5 stars + review)
    └── InstallAgentBottomSheet — install agent by share code
```
- Server executes all built-in tools (search_messages, web_search, etc.)
- Client only sends tool_calls result back to server
- Agent provider_type: openrouter, local, mimo, webhook, websocket, subprocess, mcp, reve, hermes_acp
- 11 preset agents: mimo, assistant, developer, devops, architect, writer, analyst, translator, vision, reve, hermes
- Marketplace: search with debounce, infinite scroll, pull-to-refresh, deep link install
- Rate limit: RateLimitCache + countdown + disable input on limit
- Reve Image: image generation via `reve` agent, `image_url` in ChatWithAIV2Response field 10
- Multi-agent chats: client-side routing (send to multiple agents, aggregate responses)
- Image support: gallery picker + camera via ActivityResultContracts
- Chat History: GetAIV2ChatHistory + ListAIV2Chats RPCs with marshallers

### AiV2AgentListActivity Pattern (v1.3.0.16)
```
AiV2AgentListActivity — unified agent management screen
    ├── Tab 0: Presets — load via listAgents(includePublic=true), filter isPreset
    ├── Tab 1: My Agents — user's custom agents
    ├── Tab 2: Discover — Marketplace (search + sort/filter)
    ├── Tab 3: Remote Agent — list connected remote agents
    │   └── Click → RemoteAgentSettingsFragment (inline Gateway + Token UI)
    ├── AiV2AgentListAdapter — card adapter with emoji, provider, description
    ├── FAB → AiAgentSetupActivity (create new agent)
    └── Click on agent → AiAgentSetupActivity (edit) or RemoteAgentSettingsFragment (remote tab, inline)
```
- Agent emoji mapping duplicated in AIBottomSheet, AiV2ChatActivity, AiV2AgentListAdapter
- Emoji mapping: mimo=🤖, assistant=🧠, developer=💻, devops=⚙️, architect=🏗️, writer=✍️, analyst=📊, translator=🌐, vision=👁, reve=🎨, hermes=🔬
- Presets loaded from server via ListAIAgents(includePublic=true), filtered by isPreset
- Remote agents listed via GrpcClient.listRemoteAgents()
- Tab 3 click shows RemoteAgentSettingsFragment inline — Gateway + Token UI embedded in container
- `showingRemoteSettings` flag tracks fragment state, `onBackPressed` returns to list

### AI Chats in Chat List Pattern (v1.3.0.15)
```
ChatListViewModel
    ├── loadChats() — loads regular chats from server (GetChatsV2)
    ├── loadAiChats() — loads AI chats from ListAIV2Chats, merges into allChats
    ├── buildSections() — filters by tab: "ai" → type == "hermes" || type == "owl"
    └── Navigation: hermes/owl type → AiV2ChatActivity

Server: GetUserChatsV2 excludes ai/owl/hermes types from regular chat query
Client: merges AI chats separately via ListAIV2Chats RPC
```
- AI chats appear as regular ChatInfo with type="hermes"
- activeAgentId field stores the agent for navigation to AiV2ChatActivity
- Tab "AI Chats" filters to only hermes/owl type chats

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
AiV2AgentListActivity (Tab 2: Discover)
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

### AI Chat Settings Pattern (v1.3.0.11)
```
GetAIChatSettings(session_id) → AIChatSettingsProto
  ├── sessionId, userApiKey, model, isUsingCustomKey
  └── remaining, limit, windowSeconds (rate limit info)

UpdateAIChatSettings(session_id, api_key, model) → success/message
  ├── api_key: "" = remove user key, use server key
  ├── model: "" = remove override, use agent default
  └── Per-session API key and model override

AiV2ChatUseCase
  ├── getChatSettings(sessionId) → AIChatSettingsProto
  └── updateChatSettings(sessionId, apiKey, model) → UpdateAIChatSettingsResponseProto
```

### Messages V2 Pattern (v1.3.0.12)
```
ChatV2 bidirectional stream (messenger.ChatService/ChatV2)
  ├── Request/Response: ChatV2Message (oneof payload: message/typing/system)
  ├── Auth: first message with jwt_token + room_id + clientVersion (field 3)
  ├── MessageV2: sender_id (UUID), oneof content (text/media/reply), JSON reactions
  └── System: type + message (DELETE_MESSAGE, READ_ALL, SERVER_SHUTTINGDOWN)

Unary RPCs:
  ├── GetHistoryV2(room_id, limit, cursor) → messages, next_cursor, has_more
  ├── SendMessageV2(room_id, text/media, reply_to_id, e2ee) → message, success
  ├── EditMessageV2(message_id, text) → success, message
  ├── DeleteMessageV2(message_ids, requester_user_id) → success
  └── SetReactionV2(message_id, emoji) → success, reactions (JSON bytes)

Domain mapping:
  MessageV2Proto → Message (sender_id → username via allUsers lookup)
  reactions: JSON bytes {"uuid":"emoji",...} → List<Reaction>
  media: {type: "image"|"voice", url, urls, duration}
  reply: {message_id, preview}

Files:
  ├── MessagesV2Proto.kt — proto data classes (MessageV2Proto, ChatV2MessageProto, etc.)
  ├── MessagesV2Marshallers.kt — wire format marshallers
  ├── GrpcMessageV2Client.kt — unary RPCs + domain conversion + search
  ├── ProtoUtils.kt — createMessageV2Proto(), createMessageFromV2Proto()
  ├── RealGrpcClient.kt — startChatV2(), loadHistoryV2(), sendMessageV2(), etc.
  └── GrpcClient.kt — facade methods for v2 operations

Search Messages:
  ├── GrpcMessageV2Client.searchMessages(roomId, query, limit)
  ├── SearchMessagesRequestProto(roomId, query, limit) → SearchMessagesResponseProto(messages)
  └── SearchResultProto(messageId, roomId, username, preview, createdAt)
```
- ChatV2MessageProto includes `clientVersion` (field 3) — sent in first auth message
- Server uses clientVersion to update `users.last_client_version` and `users.last_seen_at`

### Cursor Pagination Pattern (v1.3.0.9)
```
ChatListViewModel
  ├── loadChats() → reset cursor, fetch first page
  ├── loadMoreChats() → fetch next page by cursor, append to list
  ├── nextCursor: String → cursor from last response
  ├── hasMore: Boolean → false when no more pages
  └── isLoadingMore: Boolean → prevents concurrent loads

GrpcChatClient.getChats(username, skipCache, limit, cursor) → ChatListPage
  ├── ChatListPage(chats, nextCursor, hasMore)
  └── Server: GetChatsRequest { user_id, limit, cursor } → GetChatsResponse { chats, next_cursor, has_more }

ChatListActivity
  └── OnScrollListener → if lastVisible >= total - 5 → viewModel.loadMoreChats()
```

### Biometric Pattern (v1.3.0.9)
```
SplashActivity
  ├── Check biometric_enabled_$username in SharedPreferences
  ├── If enabled + device supports → showBiometricPrompt()
  ├── On success → navigate to ChatListActivity
  └── On error/cancel → finish() (exit app)

SecurityActivity
  └── switchBiometric → toggle biometric_enabled_$username
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
├── ChatListViewModel — ViewModel with StateFlow + cursor pagination
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
    app:navigationIconTint="?attr/colorOnPrimary"
    app:titleTextColor="?attr/colorOnPrimary" />
```
- Fixed height (`@dimen/custom_toolbar_height`), not `?attr/actionBarSize`
- Background: `@drawable/toolbar_background` (primary color with rounded bottom corners), NOT `?attr/colorPrimary`
- Elevation 0dp (handled by toolbar_background drawable)
- `setDecorFitsSystemWindows(window, false)` required in Activity.onCreate
- **NEVER use `setSupportActionBar()` if you need to change navigation icon at runtime** — ActionBar overrides tint. Manage toolbar directly

### Toolbar Navigation Icon Pattern (v1.3.2.4)
```
Activities WITHOUT options menu:
  — Remove setSupportActionBar(), manage toolbar directly
  — toolbar.setNavigationIcon(R.drawable.ic_back_arrow)
  — toolbar.navigationIcon?.setTint(getColorOnPrimary())
  — Change icon: toolbar.navigationIcon = drawable.apply { setTint(getColorOnPrimary()) }

Activities WITH options menu:
  — Keep setSupportActionBar() for menu inflation
  — After supportActionBar?.setHomeAsUpIndicator(): toolbar.navigationIcon?.setTint(getColorOnPrimary())
  — getColorOnPrimary() = ThemeUtils.parseSafeColor(theme.onPrimaryColor, Color.WHITE)
```

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
        navigateToTarget(activity)
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

### ChatKeepAliveService Pattern (v1.3.0.18)
```
ChatKeepAliveService — foreground service (START_STICKY)
  ├── Monitors GrpcClient.connectionStatus flow
  ├── Auto-reconnect on FAILED/DISCONNECTED (5s delay)
  ├── Persistent notification: "Подключено — получение сообщений"
  └── Companion: start(context) / stop(context) / isRunning()

Lifecycle:
  ├── SessionManager.initFromPrefs() → start()
  ├── SessionManager.loginV2() → start()
  └── SessionManager.logout() → stop() + clearLastChatRequestPrefs()

RealGrpcClient — persist lastChatRequest to SharedPreferences
  ├── saveLastChatRequestPrefs() — called in startChat()
  ├── restoreLastChatRequest() — called in onAutoResumeChat when null
  └── clearLastChatRequestPrefs() — called in logout()
```

### AI Agent Setup Pattern (v1.3.0.20)
```
AiAgentSetupActivity — create/edit all agent types
  ├── Form: name, description, provider type, model, system prompt
  ├── API Key (TextInputEditText, textPassword)
  ├── Temperature (Slider 0–2, step 0.1, default 0.7)
  ├── Max Tokens (TextInputEditText, number, default 4096)
  ├── Switches: tools, RAG, public
  ├── Save button: floating overlay (Gravity.BOTTOM | CENTER_HORIZONTAL)
  │   └── WindowInsetsListener adjusts bottomMargin for keyboard
  └── Change tracking: TextWatcher + OnCheckedChangeListener + OnChangeListener
      └── saveButton appears only when isLoaded && hasChanges

Provider config: JSON {"apiKey": "..."} sent in CreateAIAgentRequestProto
Server AgentInfoV2 proto does NOT return providerConfig (field 22 missing)
```

### API Key Visibility Pattern (v1.3.1.02)
```
AiAgentSetupActivity — API key field
  ├── TextInputLayout: endIconMode="password_toggle" (eye icon)
  ├── TextInputEditText: inputType="textPassword" (masked by default)
  ├── Long-press → copy full key to clipboard + Toast confirmation
  └── ProviderConfig parsing: api_key_source → "Server key" hint, real key → masked display
```

### Deleted Messages Filter Pattern (v1.3.1.02)
```
Server soft-delete: content_type = 'deleted', text = "[deleted]"
Client filters [deleted] in 3 places:
  ├── GrpcMessageV2Client.loadHistoryV2() — server response filter
  ├── GrpcMessageV2Client.loadHistoryV2() — Room DB cache filter
  └── RealGrpcClient.chatV2Stream — real-time stream filter
```

### Agent Status Pattern (v1.3.1.03)
```
AgentStatus enum: AVAILABLE / SERVER_KEY / NEEDS_KEY
  ├── AVAILABLE: api_key present in providerConfig
  ├── SERVER_KEY: api_key_source == "server" (no user key)
  └── NEEDS_KEY: no key, no server source

UI display:
  ├── AiV2ChatActivity toolbar: toolbarInfo with colored text
  ├── AIBottomSheet: emoji dots (🟢🟡🔴) next to agent names
  └── Color: GREEN=#4CAF50, YELLOW=#FFC107, RED=#F44336
```

### HTTP Auth Interceptor Pattern (v1.3.1.03)
```
AuthInterceptor (okhttp3.Interceptor)
  ├── Reads AuthManager.getBearerToken(context) per request
  ├── Skips /info and /health endpoints
  └── Adds Authorization header to all HTTP requests

HttpClient.init(context) called in SplashActivity.onCreate()
  └── Replaces default OkHttpClient with one that has AuthInterceptor

CRITICAL: AuthManager.getBearerToken() returns "Bearer <token>" (with prefix)
  └── Do NOT add another "Bearer " prefix — results in double "Bearer Bearer <token>"
```

### AI Chat Commands Pattern (v1.3.0.20)
```
AiV2ChatActivity
  ├── Command button (ic_hermes) → CommandBottomSheet
  ├── Commands: /new, /clear, /history, /settings, /model, /system, /tools
  ├── /new → clearMessages() + reset sessionId
  ├── /settings → open AiChatSettingsActivity
  └── Other → insert into messageInput for user to send

Error handling:
  ├── AiV2ChatMessage.error field → chat bubble (⚠️ prefix)
  ├── AiV2ChatViewModel.updateStreamingMessage() checks error
  ├── Rate limit → separate rateLimitEvent StateFlow → handleRateLimit()
  └── No Toast — errors visible in chat history
```

### AIBottomSheet Redesign Pattern (v1.3.0.22)
```
AIBottomSheet — agent selection for AI chats
  ├── ScrollView (layout_weight=1, fillViewport) — scrollable agent list
  ├── FooterContainer (fixed, outside ScrollView)
  │   ├── summaryText (selected agent count)
  │   ├── longPressHint
  │   └── startSelectedBtn (always visible, disabled until selection)
  ├── Sections:
  │   ├── Loading indicator (when isLoadingAgents)
  │   ├── Empty state "No agents" (when myAgents empty)
  │   ├── My Agents (header + rows with ImageView toggle)
  │   ├── Manage agents button → AiV2AgentListActivity
  │   └── Create custom agent button → AiAgentSetupActivity
  └── Tap row → toggle checkView (ImageView), long press → settings

ImageView toggle: 22dp fixed, ic_check_box_outline / ic_checked_small
buildContent() does NOT clear selectedAgents — only buildAndShow() does
Restore states at end of buildContent(): if (agent in selectedAgents) restoreCheckState()
Presets removed — accessible via AiV2AgentListActivity only
```

---

## Rules

1. Do NOT compile Android on server (OOM)
2. Do NOT deploy to prod without explicit instruction
3. userId (UUID) — always as key, NOT username
4. i18n: all new strings simultaneously in values/strings.xml + values-ru/strings.xml
5. Do NOT initialize getString() in Activity class fields
6. Kotlin 2.4.0: cont.resume(value, onCancellation = {})
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
19. **ALWAYS verify against server code**: Before any gRPC/marshaller change, check `/Users/paveld/LavenderMessenger-server/doc/CLIENT_INTEGRATION.md` AND the actual server source code at `/Users/paveld/LavenderMessenger-server/`.
20. CHANGELOG: do NOT list documentation changes (README, doc/, comments) — only code changes

---

## v1.3.1.05

### Secret Chat E2EE Status Pattern (v1.3.1.05)
```
ChatToolbarDelegate.updateSubtitle()
  ├── isSecret check FIRST (early return)
  │   ├── !isConnected → "Подключение..."
  │   ├── isE2eeInProgress → "🔒 Обмен ключами..."
  │   ├── E2EEManager.isE2EEActive() → "🔒 Сквозное шифрование" (green)
  │   └── else → "🔒 E2EE"
  └── Regular chats: typists → isDirect → group

ChatE2EEDelegate
  ├── onKeyExchangeStart callback → toolbarDelegate.isE2eeInProgress = true
  ├── onKeyExchangeComplete callback → toolbarDelegate.isE2eeInProgress = false
  └── NO direct toolbarSubtitle manipulation (caused race condition with observer flow)
```
- Secret chats ALWAYS show E2EE status, never participant/online count
- Observer flow (combine users/connectionStatus/typingUsers/allUsers) calls updateSubtitle() — must not overwrite E2EE status
- `isE2eeInProgress` flag ensures "Обмен ключами..." shows during retry loop

### Call Button in Chat Toolbar Pattern (v1.3.1.05)
```
NewChatActivity
  ├── onCreateOptionsMenu → inflate chat_menu.xml (action_video_call, action_search)
  ├── onPrepareOptionsMenu
  │   ├── action_video_call: visible if !inSelection && isDirect && !startsWith("favorites_") && !isSecret
  │   ├── action_search: visible if !inSelection
  │   └── action_conference: always hidden
  └── onOptionsItemSelected
      ├── action_video_call → CallManager.initiateCall(otherUser) + CallNavigator.startCall()
      └── action_search → searchDelegate.show()

ChatToolbarDelegate.getOtherParticipant() → resolves other username from participantsJson
```
- Call button lost during bae73d5 refactor (NewChatActivity split into 6 delegates)
- None of the 6 delegates absorbed menu inflation logic
- Video calls only for direct chats, not secret/favorites
- `invalidateOptionsMenu()` called by ChatSelectionDelegate.onSelectionModeChanged

### Secret Chat Navigation Pattern (v1.3.1.05)
```
ChatListNavigation
  └── else branch → putExtra("IS_SECRET", chat.isSecret.toString())
                    putExtra("IS_DIRECT", chat.type == "direct" || chat.isSecret)

NewChatActivity.loadDataFromIntent()
  ├── isSecret = intent.getStringExtra("IS_SECRET") == "true"
  ├── if (isSecret) chatType = "secret"
  └── IS_DIRECT must be true for secret chats (they are always 1-on-1)
```
- Without IS_SECRET, re-entering secret chat from list → isSecret=false → no E2EE init
- `chat.isSecret` from ChatInfo model (server-populated field)

---

## v1.3.1.07

### Reactions Fix Pattern (v1.3.1.07)
```
REACTION_V2 stream handler (RealGrpcClient.kt)
  ├── If message found in _messages → update reactions + save to Room DB
  └── If message NOT found → save to Room DB via updateReactions() (previously silently dropped)

setReactionV2 response (GrpcMessageV2Client.kt)
  ├── response.success && reactions not empty → overwrite with server reactions
  ├── response.success && reactions empty → clear reactions (previously ignored)
  └── Always save to Room DB after update

updateReactions DAO (Daos.kt)
  └── UPDATE messages SET reactionsJson = :reactionsJson WHERE id = :messageId
```

### Message Dedup Pattern (v1.3.1.07)
```
getContentHash(message) = "${user}:${text}:${timestamp / 1000}"
  — Content-based key independent of message ID

deduplicateByContent(messages)
  — Groups by content hash, prefers server IDs over temp IDs (id.startsWith("temp_"))

Applied in loadHistoryV2:
  — Cache load: dedupedCache = deduplicateByContent(cached)
  — Server merge: filterNot by contentHash, then deduplicateByContent(result)
```

### Server-Side Search Pattern (v1.3.1.07)
```
ChatSearchDelegate
  ├── searchDebounced(query) — 300ms delay via coroutine Job cancel
  ├── performServerSearch(query) — GrpcClient.searchMessages(roomId, query, limit=50)
  │   ├── Find message positions in adapter by ID
  │   └── If positions found → scroll to results
  └── Fallback: performClientSearch(query) — filter adapter.currentList by text contains

Constructor: ChatSearchDelegate(activity, scope)
  + var roomId: String — set after init in NewChatActivity.setupDelegates()
```

### Parallel Chat Loading Pattern (v1.3.1.07)
```
loadChats()
  ├── Cache load (if allChats empty) — Room DB → instant display
  ├── supervisorScope {
  │   ├── launch(IO) { fetchedPage = getChats() }
  │   └── launch(IO) { aiSessions = listAIChats() }
  │   — Both run concurrently
  ├── Process regular chats (merge, unread tracking)
  ├── Merge AI chats (ChatInfo with type="hermes")
  ├── buildSections(allChats)
  └── Sync to Room DB
```
- Removed standalone `loadAiChats()` — AI chats loaded inside `loadChats()` for parallelism
- Kotlin 2.4.0: `async` deprecated outside scope → use `supervisorScope` + `launch` + `CompletableDeferred`

### Notification Sound Pattern (v1.3.1.07)
```
LavenderMessagingService
  ├── Channel: IMPORTANCE_HIGH + RingtoneManager.getDefaultUri(TYPE_NOTIFICATION)
  │   └── AudioAttributes(USAGE_NOTIFICATION, CONTENT_TYPE_SONIFICATION)
  ├── Per-chat override: notification_sounds SharedPreferences
  │   ├── setNotificationSound(context, roomId, soundUri)
  │   └── getNotificationSound(context, roomId) → Uri?
  └── Builder: setSound(customUri) or setSound(defaultUri)
```

### AI Chat Deletion Pattern (v1.3.1.07)
```
deleteChat(chatId)
  ├── if chatId.startsWith("ai-chat-")
  │   ├── Remove from allChats + buildSections
  │   ├── Delete from Room DB
  │   ├── Save to SharedPreferences: deleted_ai_chats Set
  │   └── Skip server DeleteChat call
  └── else: normal server DeleteChat

loadChats() — AI chat merge
  ├── Load deleted_ai_chats from SharedPreferences
  ├── Filter aiSessions by deletedAiChats
  └── Only merge non-deleted AI chats
```

### Server Error Handling Pattern (v1.3.1.07)
```
SessionManager.loginV2()
  ├── Auth callback: Pair<AuthResponseV2Proto?, String?> (response, error)
  ├── If error contains "connection refused" | "database" | "internal" | "unavailable"
  │   └── onComplete("SERVER_ERROR")
  └── Else → onComplete("AUTH_FAILED")

UI handling:
  ├── "SUCCESS" → navigate to chat list
  ├── "AUTH_FAILED" → "Wrong username or password"
  ├── "SERVER_ERROR" → "Server is temporarily unavailable"
  ├── "CONNECTION_FAILED" → "Connection failed"
  └── "USER_NOT_FOUND" → "User not found"
```

---

## v1.3.1.08

### Thread Safety Pattern (v1.3.1.08)
```
Singleton fields accessed from multiple threads:
  ├── Simple fields: @Volatile annotation (JVM memory visibility guarantee)
  │   ├── currentUsername, currentUserId — written from gRPC callbacks
  │   ├── requestObserver, chatV2RequestObserver — written from main, read from gRPC
  │   └── isRetrying, lastChatRequest — written from coroutines
  └── Collections: ConcurrentHashMap / ConcurrentHashMap.newKeySet()
      ├── avatarCache, fullAvatarCache — ConcurrentHashMap<String, String>
      ├── deletedMessageHashes, pendingReads — ConcurrentHashMap.newKeySet()
      └── locallyReadChats (ChatListViewModel) — ConcurrentHashMap.newKeySet()
```

### Coroutine over Thread Pattern (v1.3.1.08)
```
BAD:  Thread { while(running) { doWork(); Thread.sleep(3000) } }.start()
GOOD: scope.launch { while(isActive) { doWork(); delay(3000) } }

Benefits:
  — Auto-cancels when scope is cancelled (lifecycle-aware)
  — No Thread.sleep blocking
  — Structured concurrency: parent scope tracks child jobs
  — No unmanaged thread leaks

Known sites fixed:
  — CallSoundManager: dial tone loop
  — ChatE2EEDelegate: E2EE retry delay
```

### Lifecycle-Safe Delay Pattern (v1.3.1.08)
```
BAD:  Handler(Looper.getMainLooper()).postDelayed({ doWork() }, 3000)
GOOD: lifecycleScope.launch {
          delay(3000)
          if (!activity.isFinishing && !activity.isDestroyed) doWork()
      }

Benefits:
  — Auto-cancels on Activity destroy (no leak)
  — No Handler/Runnable object allocation
  — isFinishing/isDestroyed guard for extra safety
```

### Debounced State Update Pattern (v1.3.1.08)
```
ChatListViewModel
  ├── scheduleBuildSections()
  │   ├── Cancel previous job: buildSectionsJob?.cancel()
  │   ├── Launch new job with 50ms delay
  │   └── Only the last update actually runs
  └── scheduleMarkRead(roomId, username)
      ├── Cancel previous job: markReadJob?.cancel()
      ├── Store pending values
      ├── Launch new job with 1s delay
      └── Coalesces rapid markRead calls into one gRPC request

Pattern:
  private var job: Job? = null
  fun schedule() {
      job?.cancel()
      job = scope.launch { delay(DEBOUNCE_MS); doWork() }
  }
```

### Targeted RecyclerView Notification Pattern (v1.3.1.08)
```
BAD:  notifyItemRangeChanged(0, itemCount)  // rebinds ALL visible items
GOOD: for (i in 0 until itemCount) {
          if (itemChanged(i)) notifyItemChanged(i)
      }

Used in MessageAdapter:
  — setSearchHighlight: only rebinds items matching old/new query
  — updatePinnedMessages: only rebinds items whose pin status changed
```

---

## v1.3.1.09

### Chat List Online Status + Last Seen Pattern (v1.3.1.09)
```
ChatAdapter — direct chats only
  ├── onlineUsers: List<String> — from GrpcClient.users (ONLINE_USERS_UPDATE stream)
  ├── allUsers: List<UserInfoProto> — from GrpcClient.allUsers (GetAllUsers RPC)
  ├── Bind logic:
  │   ├── Get other participant username from chat.participants JSON
  │   ├── Status dot: onlineUsers.contains(otherUser) → green/gray dot
  │   └── Last seen: allUsers.firstOrNull{username}?.lastSeenAt → getTimeAgo()
  └── Layout: FrameLayout wrapper around participantAvatars with status dot overlay

ChatListActivity observers:
  ├── GrpcClient.users.collectLatest → chatAdapter.updateOnlineUsers()
  └── GrpcClient.allUsers.collectLatest → chatAdapter.updateAllUsers()
```
- Online dot: `status_online_dot` / `status_offline_dot` drawables (12dp, elevation 4dp)
- Last seen: shown only when offline, format: "just now", "5 min", "3h", "2d"
- Direct chats only: hidden for groups, secrets, favorites, AI chats

### Admin Panel Last Seen Fix Pattern (v1.3.1.09)
```
SuperAdminAdapter.bindAdmin()
  ├── versionText: user.lastClientVersion (from users table — stale, PROMPT_ADMIN_VERSION_FIX)
  ├── timeAgoText: user.lastSeenAt (was lastMessageTime — BUG, fixed)
  └── statusDot: user.isOnline (from hub)

SuperAdminAdapter.clearExpanded()
  ├── expandedUsers.clear()
  └── userSessions.clear()
  — Called by SuperAdminActivity.loadData() on pull-to-refresh
```

### Message Dedup Content Hash Fallback (v1.3.1.09)
```
loadHistoryV2 merge logic:
  currentMap = current.associateBy { getMessageHash(it) }  // by ID
  currentByContent = current.associateBy { getContentHash(it) }  // by content

  localMsg = currentMap[getMessageHash(serverMsg)]
           ?: currentByContent[getContentHash(serverMsg)]  // fallback

  — Fixes Favorites reaction loss: server generates new UUID for favorites copies,
    content hash matches even when IDs differ
```

---

## v1.3.1.15

### CallActionService Pattern (v1.3.1.15)
```
CallActionService (Service, NOT IntentService)
  ├── Replaces deprecated IntentService (API 26+)
  ├── Handles FCM call notification actions (DECLINE)
  ├── onStartCommand → action + callId from Intent extras
  ├── CallManager.rejectCall() for DECLINE action
  ├── NotificationManager.cancel(callId.hashCode()) to dismiss
  └── stopSelf(startId) + START_NOT_STICKY
```
- IntentService deprecated since API 26 — Android kills its process after `onHandleIntent` returns, causing notification dismissal race
- Service stays alive until `stopSelf()` — reliable for quick actions

### SessionManager Resilience Pattern (v1.3.1.15)
```
SessionManager
  ├── ensureFreshToken()
  │   ├── Wait for GrpcClient.connectionStatus == READY before refresh
  │   ├── isRefreshing guard: concurrent calls block on CountDownLatch
  │   └── Falls back to password re-login if both tokens expired
  ├── isRefreshing: Boolean — prevents parallel refresh races
  └── Connection observer in ChatListActivity
      └── On READY → loadChats() (handles wake from doze)
```
- `ensureFreshToken()` MUST wait for READY gRPC channel — refreshing before channel is ready causes UNAUTHENTICATED
- `isRefreshing` flag with CountDownLatch ensures only one refresh at a time
- ChatListActivity connection observer triggers `loadChats()` on READY — handles app wake from background/doze

### Stale APK Cleanup Pattern (v1.3.1.15)
```
UpdateManager
  ├── downloadedVersion: String? — tracks which version was downloaded
  ├── On new version download: if downloadedVersion != current download → delete old APK
  └── isValidApk(file): Content-Type + ZIP header + minimum size validation
```

---

## v1.3.1.16

### gRPC Retry Backoff Pattern (v1.3.1.16)
```
GrpcTypingClient / GrpcCallClient
  ├── @Volatile retryCount = 0
  ├── MAX_RETRIES = 10
  ├── onError:
  │   ├── if retryCount >= MAX_RETRIES → return (stop retrying)
  │   ├── if channel null/shutdown/terminated → return
  │   ├── retryCount++
  │   └── delay((1000L * (1 shl minOf(retryCount, 5))).coerceAtMost(30_000L))
  └── On successful stream start: retryCount = 0

Backoff sequence: 2s → 4s → 8s → 16s → 30s → 30s → ... (max 10 retries)
```
- Exponential backoff prevents thundering herd on server restart
- Connection check prevents retries when channel is dead
- Reset on success ensures fresh backoff for new failures

### Content Hash UUID Pattern (v1.3.1.16)
```
getContentHash(message) = "${message.userId}:${message.text}:${message.timestamp / 1000}"
  — Uses userId (UUID) instead of user (username)
  — UUID always available from proto, never depends on allUsers loading order
  — Prevents content hash mismatch when allUsers() is empty
```

### isAuthFailure Guard Pattern (v1.3.1.16)
```
GrpcAuthClient
  ├── onMessage: if !success → setAuthFailure(true)
  └── onClose: if !isOk → setAuthFailure(true)

GrpcConnectionManager.scheduleReconnect()
  └── if isAuthFailure → skip reconnect (return early)
```

### Incoming Call Accept Pattern (v1.3.1.19)
```
CallActivity
  ├── btnAccept.click
  │   └── initWebRtc(onReady = { CallManager.acceptCall() })
  │
  ├── initWebRtc(onReady)
  │   ├── Create WebRtcClient
  │   ├── fetchTurnCredentials (async)
  │   │   └── callback:
  │   │       ├── initPeerConnection(iceServers)
  │   │       ├── setupWebRtcListeners()
  │   │       │   └── setupController() → CallController subscribes to incomingSignals
  │   │       └── onReady?.invoke() → acceptCall()
  │   └── CRITICAL: acceptCall() AFTER CallController subscribes
  │
  └── Why: SharedFlow(extraBufferCapacity=64, replay=0)
      — OFFER signal buffered but NOT replayed to late subscribers
      — acceptCall() before subscribe → OFFER lost → stuck on "Подключение..."

CallController
  └── observeSignals() collects CallManager.incomingSignals
      ├── ACCEPT → createOffer() (outgoing) or onCallAccepted (incoming)
      ├── OFFER → setRemoteDescription + createAnswer
      ├── ANSWER → setRemoteDescription
      └── ICE_CANDIDATE → addIceCandidate
```
- `initWebRtc(onReady)` — callback runs after `setupController()` completes
- For outgoing calls: `initWebRtc()` without callback (no acceptCall needed)
- For incoming calls: `initWebRtc(onReady = { CallManager.acceptCall() })`
- For conference: `initWebRtc()` without callback (joinConference called separately)

### FCM Incoming Call Auto-Launch Pattern (v1.3.1.19)
```
LavenderMessagingService.handleIncomingCall()
  ├── CallManager.init() — start collecting callSignals
  ├── GrpcClient.connect() — ensure gRPC connection
  ├── Poll for READY (10 × 500ms) → startCallSession()
  ├── showCallNotification() — ringtone + decline button + full-screen intent
  └── startActivity(CallActivity) — ALWAYS launch directly
      ├── FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_SINGLE_TOP
      ├── CALL_ID, RECEIVER_ID, SENDER_NAME, IS_INCOMING=true
      └── If CallActivity already open → onNewIntent()

Why both notification + direct launch:
  — Notification: ringtone, decline button, visible when screen off
  — Direct launch: ensures CallActivity opens when app is in foreground
  — setFullScreenIntent only works when screen off/locked
  — Without direct launch: user sees heads-up notification, may miss it
```

### Auth Retry Pattern (v1.3.1.22)
```
loadChats()
  ├── ensureFreshToken() — may fail if gRPC not READY (5s timeout)
  ├── getChats() → UNAUTHENTICATED with empty chats
  │   ├── forceTokenRefresh() — force refresh token
  │   ├── Retry getChats() once with refreshed token
  │   └── Only force logout if retry also fails with UNAUTHENTICATED/PERMISSION_DENIED
  └── Normal flow proceeds with retried result
```

### Call senderName Pattern (v1.3.1.22)
```
initiateCall(receiverUsername)
  ├── senderId = getUserId() (UUID)
  ├── receiverId = resolveUserId(username) (UUID)
  ├── senderName = getCurrentUsername() (display name)
  └── CallMessageProto(senderId, receiverId, senderName, INITIATE)
      └── Server broadcasts → receiver shows senderName, not senderId
```

### Duration API Pattern (v1.3.1.22)
```
DO:
  withTimeoutOrNull(10.seconds) { ... }
  delay(60.seconds)
  delay(200.milliseconds)

DON'T:
  withTimeoutOrNull(10000L) { ... }
  delay(60_000)
  delay(200)

Exception: CountDownLatch.await(Long, TimeUnit) — Java API, no Duration overload
```

---

## v1.3.2.0

### Company System Pattern (v1.3.2.0)
```
GrpcCompanyClient (object) — CompanyService RPCs
  ├── Company CRUD: createCompany, getCompany, updateCompany, deleteCompany, listCompanies
  ├── Positions: createPosition, updatePosition, deletePosition, listPositions
  ├── Members: addMember, removeMember, updateMemberPosition, listMembers
  ├── Company Chats: createCompanyChat, setCompanyChatAccess, getCompanyChats
  ├── Join/Leave: joinCompany, leaveCompany
  └── User Info: getUserInfo, getCompanyByUser

CompanyProfileActivity
  ├── TabLayout + ViewPager2 (3 tabs)
  │   ├── Tab 0: Members (CompanyListFragment TYPE_MEMBERS)
  │   ├── Tab 1: Positions (CompanyListFragment TYPE_POSITIONS)
  │   └── Tab 2: Company Chats (CompanyListFragment TYPE_CHATS)
  ├── btnAddMember → AddMemberActivity
  ├── btnCreateChat → CreateCompanyChatDialog
  └── btnLeaveCompany / btnDeleteCompany

AddMemberActivity
  ├── ContactAdapter — list of all users
  ├── Click → showSelectPositionDialog (listPositions)
  └── addMember(companyId, userId, positionId)

CompanyListFragment
  ├── TYPE_MEMBERS: CompanyMemberAdapter + onMoreClick → changePosition / removeMember
  ├── TYPE_POSITIONS: CompanyPositionAdapter + onMoreClick → editPosition / deletePosition
  └── TYPE_CHATS: CompanyChatAdapter

Access Control (buildSections):
  ├── companyId.isEmpty() → show (no restriction)
  ├── companyMinPositionLevel > 0 → userPositionLevel >= min
  ├── companyChatAccess == "management" → userPositionLevel >= 1
  ├── companyChatAccess == "owner_only" → userPositionLevel >= 3
  └── companyChatAccess == "member" → all employees

UserSession: companyId, companyName, positionTitle, positionLevel
  └── Updated by fetchAdminStatus() after profile load
```

### Media Preview Localization Pattern (v1.3.2.0)
```
Server sends hardcoded: "Image" / "Voice message" in last_message_text
Client translates in 3 places:
  ├── ChatListViewModel.newMessageEvent — real-time preview uses R.string.chat_preview_image/voice
  ├── ChatAdapter.bind() — translateMediaPreview() for server strings
  └── SuperAdminAdapter.bindAdmin() — translateMediaPreview() for admin panel

String resources:
  ├── chat_preview_image: "Image" / "Изображение"
  └── chat_preview_voice: "Voice message" / "Голосовое сообщение"
```

---

## v1.3.2.5

### gRPC Callback Thread Safety Pattern (v1.3.2.5)
```
gRPC callbacks run on IO threads, NOT Main thread:
  ├── toggleMute callback → viewModelScope.launch(Dispatchers.Main) { mutate allChats }
  ├── deleteChat callback → viewModelScope.launch(Dispatchers.Main) { mutate allChats }
  └── Any callback that mutates UI state → wrap in Dispatchers.Main

Pattern:
  GrpcClient.someMethod(arg) { success ->
      viewModelScope.launch(Dispatchers.Main) {
          if (success) { /* mutate state, update UI */ }
      }
  }
```

### AuthResponseV2 User Proto Mapping (v1.3.2.5)
```
Server User proto fields:
  1=id, 2=username, 3=email, 4=avatar_url, 5=bio, 6=status
  7=created_at(Timestamp), 8=last_seen_at(Timestamp)

Client marshaller: field 4→avatarUrl, 5→bio, 6→status
  — Fields 7-8 NOT parsed (Timestamp not needed in auth response)
  — Fields 1-3: id, username, email (correct)
```

### Favorites v1-v2 Conversion Pattern (v1.3.2.5)
```
GetFavoritesResponse: server sends v1 Message, client needs v2 MessageV2Proto
  — Parse with MessageProtoMarshaller (v1)
  — Convert via v1ToV2() helper:
    ├── user → senderId
    ├── imageUrl + voiceUrl + duration → media: MessageMediaProto
    ├── reactions: List<ReactionProto> → reactions: ByteArray (JSON)
    ├── repliedTo* → reply: MessageReplyProto
    └── isE2Ee/e2EePayload → isE2EE/e2eePayload (naming only)
```

### RealGrpcClient.setUsername Pattern (v1.3.2.5)
```
currentUsername MUST be set after login:
  ├── SessionManager.updateSession() → GrpcClient.setUsername(it)
  ├── Used by: typing signals, call auto-start, markRead, FORCE_DISCONNECT
  └── Without it, all getUsername = { currentUsername } callbacks return null
```

---

## v1.3.2.13

### Token Refresh Guard Pattern (v1.3.2.13)
```
SessionManager — unified refresh guard
  ├── refreshGuard: AtomicBoolean(false) — single guard for all refresh paths
  ├── waitForRefreshComplete() — polls until refreshGuard is released
  │
  ├── performTokenRefresh (periodic 60s, suspend/Main):
  │   └── compareAndSet(false, true) → skip if false (another refresh in progress)
  │
  ├── ensureFreshToken (sync, blocking/IO):
  │   ├── waitForRefreshComplete() → re-check expiry → skip if fresh
  │   ├── compareAndSet(false, true) → wait + bail if false
  │   └── try/finally { refreshGuard.set(false) }
  │
  └── forceTokenRefresh (pull-to-refresh, blocking/IO):
      ├── waitForRefreshComplete() → re-check expiry → skip if fresh
      ├── compareAndSet(false, true) → wait + bail if false
      └── try/finally { refreshGuard.set(false) }

Server refresh token rotation:
  ├── Each RefreshToken call → new JTI, old JTI invalidated
  ├── Reuse detection: JTI mismatch → RevokeDevice(user_id, device_id)
  └── RevokeDevice → is_active = FALSE → all subsequent refresh attempts fail

Why AtomicBoolean (not Mutex/synchronized):
  ├── performTokenRefresh is suspend — can't hold JVM lock across suspension
  ├── ensureFreshToken/forceTokenRefresh are blocking — can't use coroutine Mutex
  ├── AtomicBoolean.compareAndSet is thread-safe, non-blocking
  └── try/finally guarantees guard release on any exit path
```

---

## v1.3.2.16

### ChatAdapter Theme Reset Pattern (v1.3.2.16)
```
ChatAdapter — cached theme colors
  ├── colorsInitialized: Boolean — set true on first bind
  ├── initColors(view) — reads ThemeStore.currentTheme() once, caches colors
  └── updateTheme() — resets colorsInitialized=false + notifyItemRangeChanged

ChatListActivity.onResume()
  ├── ThemeStore.init(this)
  ├── ThemeApplier.apply(this, ThemeStore.currentTheme())
  └── chatAdapter.updateTheme() — forces re-read of theme colors

Pattern: ALL adapters with cached theme colors need updateTheme()
  — ChatAdapter: card backgrounds, text colors
  — UserAdapter: already has updateTheme() (v1.3.1.08)
```

### Inline Username Pattern (v1.3.2.16)
```
EditProfileActivity — inline @username
  ├── tvInlineUsername: TextView — shows "@username" below avatar
  ├── Click → showChangeUsernameDialog() (existing bottom sheet)
  ├── Set in setupUI(): tvInlineUsername.text = "@$username"
  └── Updated after profile load: tvInlineUsername.text = "@${profile.username}"

Removed: btnChangeUsername button + divider from settings card
  — Redundant with inline display
```

### Chat Gallery Thumbnail Pattern (v1.3.2.16)
```
item_message.xml
  ├── flImageContainer (FrameLayout)
  │   ├── ivMessageImage — single image (unchanged)
  │   └── rvGalleryThumbnails — horizontal RecyclerView for galleries

MessageAdapter.bindImageContent()
  ├── Single image: show ivMessageImage, hide rvGalleryThumbnails
  └── Gallery (2+): hide ivMessageImage, show rvGalleryThumbnails
      ├── ThumbnailGridAdapter — inner class, max 4 items
      ├── item_thumbnail.xml — reused from FullScreenImageActivity
      └── Click → FullScreenImageActivity with all URLs + index
```
