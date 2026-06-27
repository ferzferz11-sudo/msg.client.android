# Gotchas & Discovered Knowledge

**Version:** v1.3.1.01 | **Updated:** 2026-06-27

Practical knowledge accumulated across sessions. Things that aren't obvious from reading code.

---

## Build & Compilation

- **Do NOT compile Android on server** — OOM kill. `assembleRelease` ONLY locally on Mac
- **`compileReleaseKotlin` may show warnings not in `compileDebugKotlin`** — always check both
- **`testOptions.unitTests.isReturnDefaultValues = true`** needed in `app/build.gradle.kts` for Android SDK stubs in unit tests (e.g. `Log.e`, `Dispatchers.Main`)
- **`./gradlew assembleDebug`** succeeds in ~10s on Mac. Run before every commit

## gRPC & Marshallers

- **Marshallers field order:** server proto defines field numbers — do NOT reorder
- **Always include v2 proto fields** in marshallers (isPinned, isMuted, etc.)
- **`GetHistoryV2RequestProto()` defaults `limit=50`** — marshaller produces non-empty bytes even with "empty" constructor. For empty-bytes tests, use `limit = 0` explicitly
- **`GrpcChatClient.getChats` returns empty on ANY failure** — no retry. Callers must handle empty list gracefully
- **Protobuf default values in marshallers:** default constructor values get serialized (e.g. `limit=20` in `ListMarketplaceAgentsRequestProto`). Tests that assert `bytes.size == 0` for default constructors are wrong

## Auth & Token Management

- **Token refresh has two paths:** periodic (60s via `startTokenRefresh`) and sync (`ensureFreshToken` before chat stream with 5s latch)
- **Token expiry buffers differ:** `needsRefresh()` uses 5-min buffer, `isTokenExpiredOrExpiring()` uses 60s buffer
- **`BearerTokenInterceptor`** reads `AuthManager.getBearerToken()` per-call — always latest token, no stale-token bug
- **Token refresh failure fallback:** if both access+refresh tokens expired, `performTokenRefresh()` attempts password re-login with saved credentials; emits logout if no saved creds
- **Chat stream JWT failure:** refresh-first strategy — tries `GrpcClient.refreshToken()` before `AUTH_FAILED` (v2 has no password fallback)
- **`ensureFreshToken()` blocks via CountDownLatch** — callers must use `Dispatchers.IO` to avoid ANR
- **`initFromPrefs()`** now starts periodic token refresh for JWT sessions restored on startup

## Firebase & Push

- **`FirebaseMessaging.getInstance().token` is deprecated** — no replacement. Use `@Suppress("DEPRECATION")`. Also `onNewToken` override needs `@Suppress("DEPRECATION")`
- **Token sync** is handled by `SessionManager.syncFcmToken()`, called from `LavenderMessagingService.onNewToken()`

## UI & Android

- **All chat activities must call `WindowCompat.setDecorFitsSystemWindows(window, false)`** in `onCreate`
- **Chat toolbars** must use fixed `@dimen/custom_toolbar_height`, elevation 0dp
- **`postDelayed` on Views causes `assignParent to null` warning** — when Activity finishes while a Runnable is pending, DecorView detaches. Fix: use `lifecycleScope.launch { delay() }` with `isFinishing/isDestroyed` guard. Known sites: AiV2ChatActivity, ChatE2EEDelegate, AudioRecordingView, CallActivity
- **`WidgetManager.getOrCreate` caches Activity-scoped BottomSheets** — `ConcurrentHashMap.getOrPut` caches by key. If Activity is recreated, cached sheet holds stale Context. No invalidation mechanism. Always create fresh instances for Activity-scoped dialogs
- **`ListBottomSheet` is Dialog-based** — extends `StandardBottomSheet` which extends `Dialog`. `RecyclerView` created in `init` bound to construction Context. Reusing after Activity recreation causes stale-window bugs
- **`ThemeStore.currentTheme()` may throw** — use `theme.resolveAttribute(colorOnPrimary)` for reliable theme color access
- **`compileReleaseKotlin` vs debug:** deprecation warnings (Firebase, security-crypto) may only appear in release compilation
- **`@file:Suppress("DEPRECATION")`** for multi-import deprecation — when a file imports multiple deprecated symbols from same library (e.g. `EncryptedSharedPreferences` + `MasterKey`)

## Database & Caching

- **Room DB caching:** Messages via `MessageDao` (INSERT REPLACE), chats via `ChatDao.syncChats()` transaction. `loadHistory()` loads from cache first
- **`ChatDao.syncChats()`** (Daos.kt:55-66) is a transaction: deletes local chats not in server list, then inserts/updates server chats
- **`ChatDao.getAllChats()`** orders by `lastMessageTime DESC` — matches chat list sort order
- **`deleteChat()` must also delete from Room DB** (`db.chatDao().deleteChat(chatId)`) or deleted chats reappear on restart
- **`chatDeletedEvent`** is `MutableStateFlow<String?>` — consumed by ChatListViewModel for real-time removal + Room DB cleanup
- **Offline-first:** `GrpcMessageClient.loadHistory()` returns cached messages when no channel, calls `onCompletion()`

## Chat & Messaging

- **`newMessageEvent`** emits `Message` (was `Pair<String,String>`) — only ChatListViewModel consumes it
- **`newMessageEvent` unread guard:** must check `message.user != currentUsername` to avoid self-unread increment
- **`getChats()` in GrpcChatClient.kt** always ignores `skipCache` parameter — always makes gRPC call
- **`loadChats()` guard** `if (_isLoading.value) return` blocks pull-to-refresh when periodic sync is running — `refreshChats()` must reset `_isLoading` first
- **`deleteSelectedChats()`** must `suspendCancellableCoroutine`-await each server delete before calling `loadChats()` — otherwise deleted chats reappear
- **Auto-scroll threshold:** `lastCompletelyVisible >= itemCount - 3` to determine "near bottom"
- **`"Поделиться в чате"` forward flow:** `ChatSelectionDelegate.forwardSelectedMessages()` → `grpcClient.getChats()` → `ListBottomSheet` + `ForwardChatAdapter`. NOT in `ChatMessageMenuDelegate`. Selection mode entered via long-press on message

## Unread Count

- **Unread count** is based on `user_chat_metadata.last_read_at`, NOT `messages.is_read`
- **`is_read` flag is global** — do NOT use for unread counting
- **Server CTE fix (v1.3.0.3):** `GetUserChatsV2` now includes `unread_counts` CTE
- **`incrementUnreadCount()`** in ChatListViewModel is live code via `newMessageEvent` subscription

## Secret Chat / E2EE

- **Secret chat marshallers field order (fixed v1.2.0.18):** `CreateSecretChat`: 1=target_username, 2=target_user_id, 3=public_key, 4=client_version. `ExchangeSecretKey`: 1=chat_id, 2=public_key. `GetSecretChatKey`: 1=chat_id
- **`getDisplayName()`** — secret chat check moved to top level (was inside `type != "direct"` check, but secret chats have `type = "direct"`)
- **E2EE key exchange:** limit 10 retries, 3s interval

## AI v2

- **AI v2 RPC:** all methods in `messenger.ChatService/*` (NOT `AIService`)
- **`org.json` not available in JVM unit tests** — must add `testImplementation("org.json:json:20230227")` when tests use `org.json.JSONObject`
- **Kotlin `assertTrue` lambda scoping:** `assertTrue { it.user == "x" }` — `it` resolves to Boolean (assertTrue parameter), not collection element. Use explicit parameter: `assertTrue { r -> r.user == "x" }`
- **10 preset agents:** mimo, assistant, developer, devops, architect, writer, analyst, translator, vision, reve
- **Capability negotiation:** GET `/info` returns `{ "services": { "auth": "2.0", "chat": "2.0", "profile": "2.0", "ai": "2.0" } }`
- **`adminUserId` stores UUID, not username** — `createDirectChat()` expects usernames. Must resolve UUID→username via `loadAllUsers()` first

## Server Integration

- **Server dev ports:** gRPC 50052, HTTP 8083; prod: gRPC 50051, HTTP 8082
- **Do NOT modify server code directly** — write prompts for server agent instead
- **Server docs:** `/Users/paveld/LavenderMessenger-server/doc/CLIENT_INTEGRATION.md`
- **`ONLINE_USERS_UPDATE` server sends null** — client must null-check before JSONArray parse
- **Admin panel `UserInfoProto` data is server-managed** — `lastClientVersion` and `lastSeenAt` come from server. Client sends `BuildConfig.VERSION_NAME` in chat stream, but server must record it

## Networking

- **Singleton OkHttpClient (v1.3.0.5):** `network/HttpClient.kt` — `object HttpClient { val client }` with ConnectionPool(5, 5, MINUTES), 30s timeouts. All HTTP calls use this singleton EXCEPT `LavenderGlideModule` (60s timeouts, followRedirects, retryOnConnectionFailure)
- **Always wrap HTTP/networking in `withContext(Dispatchers.IO)`** when called from Main dispatcher scope
- **`adb` full path:** `/Users/paveld/Library/Android/sdk/platform-tools/adb`

## Deprecation Handling

- **Firebase token:** no replacement API, use `@Suppress("DEPRECATION")`
- **security-crypto 1.1.0:** `EncryptedSharedPreferences`/`MasterKey` deprecated but API still works
- **User prefers IDE dependency updates** — agent verifies compilation + tests after, documents in CHANGELOG

## Git & Releases

- **Do NOT bump version numbers between sessions** — only user bumps version
- **Do NOT deploy to prod** without explicit instruction
- **Commit after each significant change**

## Biometric

- **BiometricPrompt `onAuthenticationError` with `finish()`** — closes the app when biometric is enabled but fails (hardware unavailable, no enrolled fingerprints). Fix: only `USER_CANCELED`/`NEGATIVE_BUTTON` → `finish()`, other errors → continue to ChatListActivity
- **`biometric_enabled_$username`** stored in SharedPreferences, defaults to `false`. Only toggled in SecurityActivity
- **BiometricService Status 7** on Xiaomi — system checks sensor availability before showing prompt. Not an error

## AI Chats in Chat List

- **Server `GetUserChatsV2` excludes `ai/owl/hermes`** from regular chat query (`WHERE c.type NOT IN ('ai', 'owl', 'hermes')`). Client must load AI chats separately via `ListAIV2Chats` and merge
- **`loadAiChats()` must NOT be inside `loadChats()`** — can block startup if gRPC channel not ready. Call separately after connection is established
- **AI chats use type `hermes`** in ChatInfo for navigation to AiV2ChatActivity
- **`activeAgentId`** field in ChatInfo stores the agent ID for AI chats

## Token Refresh & Background

- **`ensureFreshToken()` MUST be called BEFORE any gRPC call in ViewModel** — not in Activity.onResume async. The old pattern launched `ensureFreshToken` on IO dispatcher while `loadChats()` ran immediately on Main, causing UNAUTHENTICATED errors when app returns from background
- **Token refresh race condition:** `loadChats()` in ViewModel called before async `ensureFreshToken()` completed. Fix: call `ensureFreshToken` synchronously at the start of `loadChats()` coroutine
- **`onResume` token refresh is redundant** if ViewModel already calls `ensureFreshToken` — remove duplicate to avoid confusion

## Remote Agent Inline Settings

- **`RemoteAgentSettingsFragment`** replaces `RemoteAgentSettingsActivity` for Tab 3 click — Gateway + Token UI shown inline in `AiV2AgentListActivity`
- **Fragment implements `RemoteAgentManager.RemoteAgentStateListener`** — `bind(this)` / `unbind(this)` in `onResume` / `onPause`
- **`onBackPressed` override** needed to return from fragment to agent list — check `showingRemoteSettings` flag

## Server ListAIAgents Empty UUID

- **Server `ListAIAgents` may return `INTERNAL` with `pq: invalid input syntax for type uuid: ""`** if JWT auth interceptor doesn't set `userID` in context properly
- **Check deployed binary date vs source** — `stat --format='%y' /root/LavenderMessenger/run/lavender-server` should match source build date
- **Prompt for fix:** `/Users/paveld/LavenderMessenger-server/doc/PROMPT_LISTAIAGENTS_FIX.md`

## AIBottomSheet Loading State

- **`isLoadingAgents` flag** needed to distinguish "still loading" from "loaded empty" — without it, "Загрузка агентов…" shown forever when server returns 0 agents
- **`buildAndShow()` must set `isLoadingAgents = true`** before first `buildContent()` to show loading state immediately
- **`loadPresetAgents()` sets `isLoadingAgents = false`** after gRPC completes, then calls `buildContent()` to update UI

## ChatKeepAliveService

- **`ChatKeepAliveService` is START_STICKY** — system restarts it after process kill. Monitors `connectionStatus` flow and auto-reconnects on FAILED/DISCONNECTED
- **Start lifecycle:** started in `SessionManager.initFromPrefs()` and `loginV2()`, stopped in `logout()`
- **Notification channel:** `chat_keepalive_channel` (IMPORTANCE_LOW), shows connection status
- **Does NOT use bindService** — only started/stopped via companion `start()`/`stop()` methods
- **`isRunning` flag** is static companion property — lost on process death (service restarts fresh)

## Persist lastChatRequest

- **`lastChatRequest` is saved to SharedPreferences** (`chat_keepalive`) on every `startChat()` call — survives process death
- **Restored in `onAutoResumeChat`** callback when `lastChatRequest == null` after reconnect
- **Password comes from `CredentialStore`**, not from prefs — security: never persist passwords in plain SharedPreferences
- **Cleared on logout** via `GrpcClient.clearLastChatRequestPrefs()`
- **After process death, callback is `{}` (empty)** — messages for other rooms still processed (DB + newMessageEvent), but current room messages have no UI callback until user opens chat

## APK Update Validation

- **Downloaded APK is validated** before marking as "downloaded" — prevents "невозможно установить пакет"
- **Three checks:** Content-Type (reject text/html), ZIP header (PK magic bytes), minimum size (>100KB)
- **`UpdateManager.isValidApk(file)`** reads first 4 bytes to verify ZIP signature
- **If validation fails:** file deleted, download marked as failed, user can retry

## minSdk 29 (Android 10)

- **minSdk was raised to 33 in v1.3.0.17**, causing "версия пакета на 31 версию SDK" error on Android 12
- **Reverted to 29 in v1.3.0.18** — all API usage verified compatible with Android 10+
- **README.md and doc/INDEX.md** both reference minSdk 29

## AI Agent Setup Form

- **`providerConfig` field 22 IS returned by server** — `AgentInfoV2` proto includes `string provider_config = 22`. Server `agentToProto()` marshals `ProviderConfig` as JSON. For preset agents: `{"api_key_source": "server", ...}` (no `api_key`). For user agents: `{"api_key": "sk-...", ...}`
- **`AiAgentSetupActivity` checks `api_key_source`** — shows "Server key" placeholder for preset agents, masked key for user agents with keys
- **Temperature slider** uses `addOnChangeListener` — fires during programmatic `setValue()` in `observeState()`. Use `isLoaded` flag to suppress change tracking during initial load
- **Save button as floating overlay** — added dynamically to `FrameLayout` (root content) with `Gravity.BOTTOM | CENTER_HORIZONTAL`. WindowInsetsListener adjusts `bottomMargin` for keyboard/nav bar

## AI Chat Error Handling

- **Errors shown as chat messages** — `AiV2ChatMessage.error` field propagates server errors to chat bubble (⚠️ prefix). No Toast
- **Rate limit** uses separate `rateLimitEvent` StateFlow — not mixed with error flow
- **`providerConfig` in AiV2Agent** — added to domain model but not in `AgentInfoV2Proto`. Used only for Create/Update requests

## AIBottomSheet

- **CheckBox replaced with ImageView toggle** — CheckBox's internal button drawable has intrinsic size that resists `minimumWidth/Height = 0`. ImageView gives exact 22dp control
- **ScrollView needs `layout_weight=1`** — `wrap_content` + programmatic maxHeight broke touch events. Use weight-based layout in parent LinearLayout
- **`selectedAgents` must NOT be cleared in `buildContent()`** — called from `loadPresetAgents()` callback. Clear only in `buildAndShow()`/`rebuildContent()`, restore checkbox states at end of `buildContent()`
- **Presets removed from bottom sheet (v1.3.0.22)** — only user agents shown. Presets accessible via AiV2AgentListActivity. Loading/empty states added.
