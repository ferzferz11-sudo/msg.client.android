# Android — Code Patterns and Rules

**Version:** v1.2.0.4 | **Updated:** 2026-06-18

---

## Patterns

### GrpcClient Facade Pattern
```
GrpcClient (facade, ~700 LOC) — StateFlow declarations + inline domain delegates
    ├── StateFlow/SharedFlow declarations (15)
    ├── Mutable state properties (4)
    ├── Core lifecycle: connect, disconnect, startChat, loadHistory
    └── Domain methods: signInV2, getChats, sendMessage, etc. (inline delegates)

RealGrpcClient (orchestrator, ~880 LOC) delegates to:
├── GrpcConnectionManager (167) — connect/reconnect/disconnect
├── GrpcAuthClient (232) — JWT auth (v2 only)
├── GrpcTypingClient (87) — typing stream
├── GrpcCallClient (125) — calls
├── GrpcChatListClient (647) — chat list, pin/search/archive
├── GrpcProfileClient (506) — profile, avatar, contacts, themes
├── GrpcDraftClient (86) — drafts
├── GrpcFavoritesClient (120) — favorites
├── GrpcMessageClient (345) — messages, history, reactions, mark read
├── GrpcServerDiscoveryClient (145) — server discovery
└── GrpcMarshallers (~1500) — all marshaller classes
```
- Each module: separate class with clear responsibility
- DI via constructor (no framework)
- RealGrpcClient: StateFlow declarations → module init → chat stream → proxy methods
- **CRITICAL:** StateFlow declared BEFORE modules (Kotlin object top-to-bottom init)
- GrpcClient: extension functions don't work via star import — all methods inline

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
- При выносе: `internal` для полей/методов, прокси-методы в Activity
- Top-level internal fun файлы в том же пакете — нужны явные импорты

### UpdateCoordinator Pattern
Activity → Coordinator → Manager → Utils. Activity только создаёт, наблюдает StateFlow, делегирует UI.

### StandardBottomSheet Pattern
Все шторки наследуют `StandardBottomSheet`: ServerAuth, Login, Register, AI, NewChat.
Drag handle автоматически. Dismiss listener через `setOnDismissListener`.

### Bearer Token Interceptor Pattern
- Подставляет JWT во все gRPC вызовы (кроме AuthService)
- v2 only — нет fallback на v1
- Proactive refresh каждые 60с, за 5 мин до истечения
- Per-server validation: токены привязаны к серверу
- **ensureFreshToken()** — синхронный refresh перед Chat stream

### JWT Auth Pattern (v2 Only)
```
AuthManager: storeTokens, getAccessToken, isTokenExpiredOrExpiring
BearerTokenInterceptor: attach JWT to all calls except AuthService
SessionManager: ensureFreshToken() sync refresh before chat stream
Token refresh: proactive every 60s + sync before chat stream
```

### Server Switch Pattern
- `serverAddress` сохраняется ТОЛЬКО после успешного `SessionManager.login()`
- НЕ сохранять до входа — двойной вход

### ChatStream Auth Pattern (v2 Only)
```kotlin
SessionManager.ensureFreshToken(context) // sync refresh if needed
val accessToken = AuthManager.getAccessToken(context)
if (!accessToken.isNullOrEmpty()) {
    firstMessageBuilder.setJwtToken(accessToken)
} else {
    firstMessageBuilder.setPassword(password) // fallback
}
```

### Connection Readiness Pattern
- Optimistic READY сразу после `builder.build()`
- Keepalive: 30s interval, 10s timeout, idleTimeout 25min
- Reconnect только при FAILED, не при shutdownNow

### Toolbar Pattern (Contacts Style)
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

### getChats() Callback Pattern
- Всегда вызывать callback (success/error/timeout)
- Poll interval: 30s
- При timeout: НЕ перезаписывать allChats, логировать warning

### Read Receipts Broadcast Pattern
```
Server: MarkRead → Broadcast("READ_ALL:username") → Hub → All clients
  → RealGrpcClient.chatStream → handleReadAllSignal()
  → GrpcMessageClient.onReadReceipt(targetRoomId, reader)
  → RealGrpcClient._readReceiptEvent.emit(Pair(roomId, reader))
```

### Marshallers Pattern
- Custom marshallers for each proto type (not using protobuf-java reflection)
- Request marshallers: serialize all fields
- Response marshallers: parse by field number, skip unknown fields
- v2 fields (isPinned, isMuted, etc.) must be included in parser

---

## Rules

1. Do NOT compile Android on server (OOM)
2. Do NOT deploy to prod without explicit instruction
3. Commit after each significant change
4. userId (UUID) — always as key, NOT username
5. i18n: all new strings simultaneously in values/strings.xml + values-ru/strings.xml
6. Do NOT initialize getString() in Activity class fields
7. Kotlin 2.3.21: cont.resume(value, onCancellation = {})
8. No forceReconnect — one connect at start, reconnect only on FAILED
9. Favorites — not a section in list, but a separate chat (type="favorites")
10. When extracting code from Activity — `internal` for fields/methods, proxy methods in Activity
11. Do not add new features without explicit request
12. Do not refactor working code without explicit request
13. All errors via `ErrorHandler.handle()` — NOT direct `Log.e`
14. v2 server only — no v1 legacy fallbacks
15. All chat activities must call `setDecorFitsSystemWindows(window, false)`
16. Chat toolbars: fixed `@dimen/custom_toolbar_height`, elevation 0dp
17. Always include v2 proto fields in marshallers
18. Ensure JWT token freshness before Chat stream via `ensureFreshToken()`
