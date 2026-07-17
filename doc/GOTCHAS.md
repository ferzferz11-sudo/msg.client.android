# Gotchas & Discovered Knowledge

**Version:** v1.3.2.16 | **Updated:** 2026-07-17

Practical knowledge accumulated across sessions. Things that aren't obvious from reading code.

---

## Debug Commands

- **adb logcat:** `/Users/paveld/Library/Android/sdk/platform-tools/adb logcat`

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

- **Token refresh has two paths:** periodic (60s via `startTokenRefresh`) and async callback (no Main thread blocking)
- **Token expiry buffers differ:** `needsRefresh()` uses 5-min buffer, `isTokenExpiredOrExpiring()` uses 60s buffer
- **`BearerTokenInterceptor`** reads `AuthManager.getBearerToken()` per-call — always latest token, no stale-token bug
- **Token refresh failure fallback:** if both access+refresh tokens expired, `performTokenRefresh()` attempts password re-login with saved credentials; emits logout if no saved creds
- **Chat stream JWT failure:** refresh-first strategy — tries `GrpcClient.refreshToken()` before `AUTH_FAILED` (v2 has no password fallback)
- **`ensureFreshToken()` blocks via CountDownLatch** — callers must use `Dispatchers.IO` to avoid ANR
- **`initFromPrefs()`** now starts periodic token refresh for JWT sessions restored on startup
- **NEVER block Main thread with CountDownLatch** — `waitForConnectionAndReLogin()` used `latch.await(8s)` on Dispatchers.Main, causing splash freeze. Use async callbacks instead

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
- **Splash biometric timeout (15s):** BiometricPrompt can hang without callback on some devices. Always add a timeout fallback that forces navigation. SplashActivity has 15s biometric + 5s general timeout

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
- **Duplicate message race condition:** `sendMessageV2` response handler changes temp ID → server ID, but ChatV2 stream may have already added the message with server ID. Always dedup by ID after ID update in `sendMessageV2` response handler
- **Online status / lastSeenAt:** `ONLINE_USERS_UPDATE` stream provides online usernames (real-time), but `allUsers` (with `lastSeenAt`) is only updated via `loadAllUsers()` unary call. Always call `loadUsers()` during pull-to-refresh and periodically (60s) to keep `lastSeenAt` current. Without this, chat list shows stale "last seen X ago" even when user is online.

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

## Messages V2 Migration (v1.3.1.01)

- **v1 completely removed** — no more `messenger.ChatService/Chat`, `GetHistory`, `SendMessage`, `EditMessage`, `DeleteMessages`, `SetReaction`. Only v2 RPCs work
- **ChatV2 stream** — single source of real-time messages. Auth via `jwt_token` in first message. System signals via `ChatV2System` (type + message fields)
- **Message ID sync** — server returns its own ID in `SendMessageV2Response`. Client MUST update local message + Room DB with server ID, otherwise `loadHistoryV2` creates duplicates (different IDs = different hashes)
- **Favorites duplication** — virtual room `favorites_<username>`. `GetHistoryV2` returns messages from server, `loadHistoryV2` also loads from Room DB cache. Without ID sync, same message appears twice
- **Single reconnection path** — only `ChatKeepAliveService` should trigger reconnection. v2 stream `onError`/`onClose` sets `FAILED` status, ChatKeepAliveService detects and calls `connect()`. Do NOT add retry loops in stream handlers
- **GrpcMessageClient deleted** — all v1 message operations removed. Dead code: `GrpcMessageClient.kt`, v1 marshallers (GetHistory, EditMessage, DeleteMessages, Reaction), v1 proto classes
- **GetFavoritesResponseProto** — uses `List<MessageV2Proto>` (not `List<MessageProto>`). Server returns v2 format after migration
- **SearchMessages** — new RPC `messenger.ChatService/SearchMessages`. Returns `SearchResultProto` with messageId, roomId, username, preview, createdAt

## Favorites

- **Room ID pattern** — `favorites_<username>` (virtual room, not a real chat)
- **Messages stored in messages_v2** — server returns them via `GetHistoryV2` and `GetFavorites`
- **`getFavorites()`** — uses v2 marshallers, resolves `sender_id` UUID → username via `allUsers` cache
- **`addLocalMessage` for favorites** — saves to Room DB with `roomId = "favorites_<username>"`

## Server Soft-Delete (v1.3.1.02)

- **Server does NOT delete messages physically** — sets `content_type = 'deleted'`, clears text/media, returns `"[deleted]"` as text content in proto
- **Client must filter `[deleted]`** — otherwise deleted messages appear as regular messages with "[deleted]" text
- **Three filter points**: GetHistoryV2 response, Room DB cache, ChatV2 stream
- **Favorites also affected** — virtual room messages can be soft-deleted by server

## API Key Visibility (v1.3.1.02)

- **TextInputLayout `endIconMode="password_toggle"`** — eye icon for showing/hiding API key
- **Long-press copies to clipboard** — ClipboardManager + Toast confirmation
- **ProviderConfig parsing**: checks `api_key` (snake_case) first, then `apiKey` (camelCase), then `api_key_source` for server presets

## AuthManager.getBearerToken() Returns "Bearer " Prefix (v1.3.1.03)

- **`getBearerToken()` returns `"Bearer <token>"`** (with prefix) — NOT just the token
- **Do NOT add another "Bearer " prefix** — results in `"Bearer Bearer <token>"` → 401 invalid token
- **Correct usage**: `addHeader("Authorization", AuthManager.getBearerToken(context))`
- **Wrong usage**: `addHeader("Authorization", "Bearer ${AuthManager.getBearerToken(context)}")`
- **AuthInterceptor** handles this automatically — uses `getBearerToken()` directly

## HTTP Upload Auth (v1.3.1.03)

- **`HttpClient` needs AuthInterceptor** — plain OkHttpClient sends no JWT token → 401
- **`HttpClient.init(context)` in SplashActivity** — initializes with AuthInterceptor
- **`HttpClient.client` has default OkHttpClient** — works before init (no lateinit crash)
- **Camera requires runtime permission** — `CAMERA` permission must be requested at runtime
- **`sendMessageV2` sets `isSent = true`** — only after server confirms success

## ChatV2 clientVersion (v1.3.1.04)

- **ChatV2MessageProto field 3 = `clientVersion`** — server proto `ChatV2Message` has `client_version` at field 3. Client was missing it entirely before v1.3.1.04
- **Client sends `BuildConfig.VERSION_NAME`** in first ChatV2 auth message — server uses this to update `users.last_client_version` and `users.last_seen_at`
- **Admin panel shows stale versions** if clientVersion not sent — server cannot update `last_client_version` without it
- **Marshallers must include field 3** — both serialization (stream) and deserialization (parse) for ChatV2MessageMarshaller

## SendMessageV2 missing UpdateLastSeen (v1.3.1.04)

- **Client sends messages via `SendMessageV2` unary RPC** — NOT through ChatV2 stream
- **Server `SendMessageV2` handler was missing `UpdateLastSeen`** — `last_seen_at` never updated on message send
- **Fix (server):** Added `UpdateLastSeen` to `SendMessageV2`, `EditMessageV2`, `DeleteMessageV2`, `SetReactionV2` handlers
- **Root cause:** ChatV2 stream only updates `last_seen_at` on stream auth + received messages. Unary RPCs bypass the stream entirely

## Hermes Agent ACP (v1.3.1.04)

- **Hermes ACP provider** on server: persistent sessions via JSON-RPC 2.0 over stdin/stdout
- **Client needs no proto changes** — Hermes works through existing `ChatWithAIV2` streaming RPC
- **Emoji mapping "hermes" → "🔬"** in 3 files: AIBottomSheet, AiV2AgentListAdapter, AiV2ChatActivity
- **11 preset agents** now (was 10): mimo, assistant, developer, devops, architect, writer, analyst, translator, vision, reve, hermes

## Secret Chat E2EE Status (v1.3.1.05)

- **`updateSubtitle()` overwrites E2EE status** — observer flow (combine users/connectionStatus/typingUsers/allUsers) calls `updateSubtitle()` on every state change. Without `isSecret` early return, it overwrites "🔒 Сквозное шифрование" with participant/online count
- **`ChatE2EEDelegate` must NOT set `toolbarSubtitle` directly** — race condition with observer flow. Use `onKeyExchangeStart`/`onKeyExchangeComplete` callbacks instead
- **`E2EEManager.isE2EEActive()` checks SharedPreferences** — returns true if shared secret is stored for the chat. Use this to determine if key exchange is complete
- **Secret chats have `chatType = "secret"`** — NOT `"direct"`. But `isDirect` must be `true` for toolbar logic (secret chats are always 1-on-1)
- **`IS_SECRET` intent extra is critical** — without it, re-entering secret chat from list → `isSecret = false` → no E2EE init, no E2EE status in toolbar

## Call Button Lost in Refactor (v1.3.1.05)

- **`bae73d5` refactor split NewChatActivity into 6 delegates** — `onCreateOptionsMenu()`/`onPrepareOptionsMenu()`/`onOptionsItemSelected()` were removed but NOT migrated to any delegate
- **`chat_menu.xml` still exists** with `action_video_call`, `action_conference`, `action_search` — but nothing inflated it
- **Call infrastructure intact** — `GrpcCallClient`, `CallManager`, `CallNavigator`, `CallActivity`, `WebRtcClient` all work. Only the UI trigger button was missing
- **Video call only for direct chats** — hidden for secret chats (`!isSecret`), favorites (`!startsWith("favorites_")`), and selection mode
- **`invalidateOptionsMenu()`** called by `ChatSelectionDelegate.onSelectionModeChanged` — menu refreshes when entering/exiting selection mode

## Online Users Count (v1.3.1.05)

- **`ONLINE_USERS_UPDATE` system signal** populates `_users` StateFlow with list of online usernames from server
- **Server may not include current user** in the online users list — "0 online" can appear even when user is connected
- **Secret chats no longer show participant/online count** — they show E2EE status instead (fix for "2 участника, 0 онлайн")

## Reactions Bugs (v1.3.1.07)

- **REACTION_V2 stream drops reactions for unloaded messages** — if a reaction arrives for a message not in `_messages` (e.g., scrolled off, different chat), it was silently discarded. Fixed: saves to Room DB via `updateReactions()` even when message not in memory
- **`setReactionV2` empty reactions ignored** — server response with `success=true` but empty `reactions` bytes kept the optimistic state. Fixed: now clears reactions when server returns empty
- **`updateReactions` DAO method** — new `UPDATE messages SET reactionsJson = :reactionsJson WHERE id = :messageId` for targeted reaction updates without full message rewrite

## Message Dedup (v1.3.1.07)

- **Content-based dedup** — `getContentHash(message)` = `"${userId}:${text}:${timestamp/1000}"`. `deduplicateByContent()` prefers server IDs over temp IDs (`id.startsWith("temp_")`)
- **Applied in `loadHistoryV2`** — both cache load and server response merge now deduplicate by content hash, preventing temp ID + server ID coexistence in Room DB
- **Root cause**: ChatV2 stream can deliver server's copy of a sent message (with server ID) before `sendMessageV2` response handler changes temp ID → server ID. Both get saved to Room DB.

## Chat Search Server-Side (v1.3.1.07)

- **`ChatSearchDelegate` now uses `SearchMessages` RPC** — 300ms debounce, searches server first, falls back to client-side if server returns 0 results
- **Constructor changed** — now requires `CoroutineScope` parameter, plus `roomId` must be set after init
- **`NewChatActivity`** passes `lifecycleScope` and sets `searchDelegate.roomId`

## Notification Sounds (v1.3.1.07)

- **Channel now has default notification sound** — `RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)` with `AudioAttributes` for notification usage
- **Per-chat sound override** — `notification_sounds` SharedPreferences, `setNotificationSound(context, roomId, soundUri)` / `getNotificationSound(context, roomId)`
- **Replaced `DEFAULT_VIBRATE | DEFAULT_SOUND`** — now explicit `setVibrate()` + `setSound()` for reliable sound playback

## Parallel Chat Loading (v1.3.1.07)

- **Regular + AI chats load in parallel** — `supervisorScope` + `CompletableDeferred` on `Dispatchers.IO`
- **Removed standalone `loadAiChats()`** — AI chats now loaded inside `loadChats()` to avoid duplicate calls
- **Kotlin 2.4.0**: `async` is deprecated outside proper coroutine scope — use `supervisorScope` + `launch` + `CompletableDeferred` instead

## AI Chat Deletion (v1.3.1.07)

- **Server doesn't store AI chats in `chats` table** — AI chats are virtual, created by `ListAIV2Chats` RPC. `DeleteChat` server call fails with "not found" for `ai-chat-*` IDs
- **Fix: skip server call for AI chats** — `deleteChat()` checks `chatId.startsWith("ai-chat-")` and only removes locally
- **AI chats reappear after deletion** — `loadChats()` re-fetches from server via `listAIChats()`. Fix: store deleted IDs in SharedPreferences (`deleted_ai_chats`) and filter during merge

## Server Error Handling (v1.3.1.07)

- **Server DB errors return gRPC `INTERNAL`/`UNAVAILABLE`** — when PostgreSQL is down, auth handlers receive status error instead of `success=false`
- **Client showed "Wrong password" for all auth failures** — `AUTH_FAILED` result shown for both wrong credentials and server errors
- **Fix: distinguish error types** — `SessionManager.loginV2()` now checks error message for `connection refused`/`database`/`internal`/`unavailable` → returns `SERVER_ERROR` instead of `AUTH_FAILED`
- **UI handlers**: `ServersActivity`, `ChatListAuth` now handle `SERVER_ERROR` → "Server is temporarily unavailable" and `CONNECTION_FAILED` → "Connection failed"

## Reactions Room DB Race Condition (v1.3.1.07)

- **`messages.value.firstOrNull` after `messages.update` is racy** — another coroutine may update `messages` between the `update` and the `firstOrNull` call, causing the wrong message version to be saved
- **Fix: capture `updatedMsg` inside `messages.update` lambda** — the lambda is atomic, so the captured value is guaranteed to be the correct version

## Thread Safety in RealGrpcClient Singleton (v1.3.1.08)

- **Singleton fields without `@Volatile`** — `currentUsername`, `currentUserId`, `requestObserver`, `chatV2RequestObserver`, `isRetrying`, `lastChatRequest` are read/written from gRPC callback threads and main thread. Without `@Volatile`, JVM may cache stale values in CPU registers
- **Thread-unsafe collections** — `mutableMapOf` and `mutableSetOf` are not thread-safe. `avatarCache`, `fullAvatarCache` written from callback threads, read from main thread → `ConcurrentModificationException` risk. `deletedMessageHashes` and `pendingReads` same issue
- **Fix**: `@Volatile` for simple fields, `ConcurrentHashMap` / `ConcurrentHashMap.newKeySet()` for collections

## runBlocking on Main Thread (v1.3.1.08)

- **`runBlocking(Dispatchers.IO)` in click listeners** — blocks main thread until disk I/O completes. In `ChatListToolbar` cache clear action, this can freeze UI for seconds
- **Fix**: Use `lifecycleScope.launch { withContext(IO) { ... } }` instead

## Handler Lifecycle Leaks (v1.3.1.08)

- **`Handler(Looper.getMainLooper()).postDelayed()` creates leaks** — the Handler holds a reference to the Runnable, which may capture Activity context. If Activity is destroyed before the delay fires, the Runnable accesses destroyed views
- **Fix**: Use `lifecycleScope.launch { delay(); ... }` which auto-cancels on Activity destroy
- **Known sites**: `ChatE2EEDelegate` (3s retry), `AudioRecordingView`, `CallActivity`

## Unmanaged Threads (v1.3.1.08)

- **`Thread { while(...) { Thread.sleep() } }.start()`** — unmanaged thread not bound to lifecycle. If `stop()` races with the thread, `toneGenerator` may be released while the thread calls `startTone()` → NPE
- **Fix**: Use coroutine with `delay()` in a scope that can be cancelled. `CallSoundManager` now uses `CoroutineScope(SupervisorJob() + Dispatchers.Main)` with `destroy()` method

## Coroutine Scope Leaks (v1.3.1.08)

- **`CoroutineScope` created inside method** — if not stored as class property, old scopes are never cancelled when method is called again. `AIBottomSheet.loadPresetAgents()` created new scope each call
- **Fix**: Store scope as class property. Cancel previous job before launching new one

## SimpleDateFormat Performance (v1.3.1.08)

- **`SimpleDateFormat` created per bind** — expensive to construct. `MessageAdapter.onBindViewHolder` created 2 instances per message
- **Fix**: Cache via `ThreadLocal<SimpleDateFormat>` at adapter level

## Favorites Reactions (v1.3.1.09)

- **Favorites messages have different UUIDs** — server `SaveFavoriteMessage` generates new UUID for `messages_v2` row. `GetHistoryV2` returns these new UUIDs
- **`SetReactionV2` returns success=false** — server can't find Favorites message by UUID from `GetHistoryV2`. Server-side fix: `SetReactionV2` must find messages by room_id+content or `SaveFavoriteMessage` must use consistent UUIDs
- **Content hash fallback merge** — `loadHistoryV2` merge now falls back to `getContentHash()` (user:text:timestamp) when `getMessageHash()` (by ID) doesn't match. Fixes reaction loss when server returns messages with different UUIDs than cache

## Admin Panel Data (v1.3.1.09)

- **`last_seen_at` only updates on send/login** — `UpdateLastSeen` called on SendMessageV2, EditMessageV2, DeleteMessageV2, SetReactionV2, ChatV2 stream connect, and login. NOT on receive. Server heartbeat (60s) fixes this
- **`last_client_version` in `users` table is stale** — only updated when ChatV2 stream receives `clientVersion`. Use `user_devices` table instead (updated on every connection). Server-side fix via `PROMPT_ADMIN_VERSION_FIX.md`
- **"unknown" IP in sessions** — server sends `ipAddress="unknown"` when IP can't be determined. Client now filters this out

## Chat List Online Status (v1.3.1.09)

- **Online status from `GrpcClient.users`** — `ONLINE_USERS_UPDATE` system signal populates StateFlow of online usernames. NOT from `users` table
- **Last seen from `GrpcClient.allUsers`** — `GetAllUsers` RPC loads all users with `lastSeenAt`. Called via `GrpcClient.loadUsers()` in `ChatListActivity.onResume()`
- **Only for direct chats** — online dot and last seen hidden for groups, secrets, favorites, AI chats

## Call Signaling UUID Fix (v1.3.1.11)

- **`callStreams` stores UUID** — server `hub.go` maps `callStreams[stream] = currentUserId` (UUID from stream identification)
- **`getOtherParticipant()` returns USERNAME** — `participantsJson` stores usernames, not UUIDs
- **`initiateCall(username)` → `delivered: false`** — UUID != username in `BroadcastCall` comparison
- **Fix: `initiateCall()` resolves username → UUID via `allUsers`** — `resolveUserId(username)` helper
- **Conference methods also used `getCurrentUsername()`** — 7 methods fixed to use `getUserId()`
- **FCM push `sender_id` must be UUID** — server `sendCallPushNotification` now sends `msg.SenderId` (UUID) instead of `senderUsername`

## Call Disconnect Asymmetry (v1.3.1.11)

- **INITIATE echo corrupts `receiverId`** — server echoes INITIATE back with `receiverId` swapped to sender's own ID. `CallManager.handleIncomingSignal` overwrites `_currentCall` with this echo, corrupting `receiverId`. After hangup + stream reconnect, `acceptCall()` uses corrupted state → sends ACCEPT to wrong party
- **Fix: INITIATE echo only updates `callId`** — `existing.copy(callId = signal.callId)` preserves original `receiverId` from `initiateCall()`
- **HANGUP not delivered when callee stream offline** — `BroadcastCall` at line 669 can't find callee in `callStreams` (FCM-initiated call, not stream-initiated). Server logs warning but no push sent
- **Fix: server sends `CALL_ENDED` FCM push** — `sendCallEndedPushNotification()` called when `!delivered && (HANGUP || REJECT)`
- **Client handles `CALL_ENDED` push** — `LavenderMessagingService` dismisses notification, `CallManager.handleCallEndedPush()` emits synthetic HANGUP to close open CallActivity

## Token Resilience (v1.3.1.11)

- **`INTERNAL`/`NOT_CONNECTED` are NOT auth errors** — server availability errors should NOT trigger force logout. Only `UNAUTHENTICATED`/`PERMISSION_DENIED` are real auth failures
- **Force logout condition narrowed** — `ChatListViewModel.loadChats()` only force logouts on `UNAUTHENTICATED`/`PERMISSION_DENIED` with empty chat list
- **Token refresh on resume** — `ChatListActivity.onResume()` calls `ensureFreshToken()` before loading chats. Handles long idle (overnight, doze mode) when periodic 60s refresh coroutine was suspended
- **`startTokenRefresh` runs every 60s** — checks `needsRefresh()` (5-min buffer before expiry). In doze mode, coroutine is suspended. On wake, `onResume` refresh compensates

## CallActionService (v1.3.1.15)

- **IntentService deprecated since API 26** — Android kills its process after `onHandleIntent` returns. Notification dismissal races with process kill. Use `Service` + `stopSelf(startId)` instead
- **CallActionService handles DECLINE action** — FCM notification adds decline button. `CallManager.rejectCall()` + `NotificationManager.cancel()`
- **Registered in AndroidManifest** with intent filters for `DECLINE` action

## SessionManager Resilience (v1.3.1.15)

- **`ensureFreshToken()` MUST wait for READY gRPC channel** — refreshing before channel is ready causes UNAUTHENTICATED errors. Now waits for `connectionStatus == READY` before attempting refresh
- **`isRefreshing` guard prevents parallel refresh** — multiple coroutines calling `ensureFreshToken()` would race. CountDownLatch ensures only one refresh runs, others wait
- **ChatListActivity connection observer** — on READY status, triggers `loadChats()`. Handles wake from doze mode where periodic refresh was suspended
- **`loadChats()` not blocked by `isLoading`** — `refreshChats()` resets `_isLoading` first to allow pull-to-refresh during periodic sync

## Stale APK Cleanup (v1.3.1.15)

- **Downloaded APK version tracked** — `UpdateManager` stores `downloadedVersion` in SharedPreferences. When a new version is downloaded, old APK is deleted automatically
- **APK validation before marking as downloaded** — Content-Type (reject text/html), ZIP header (PK magic bytes), minimum size (>100KB). Prevents "невозможно установить пакет"

## Call Notification Fixes (v1.3.1.15)

- **Call notification shows UUID instead of name** — FCM `sender_id` was UUID. Fix: use `sender_name` field from FCM data for notification title
- **Call notification has ringtone + vibration** — now uses `RingtoneManager.getDefaultUri(TYPE_NOTIFICATION)` + `Vibrator` for incoming calls
- **Decline button in notification** — `CallActionService` handles `DECLINE` action, rejects call and dismisses notification

## Content Hash Race Condition (v1.3.1.16)

- **`getContentHash` used `message.user` (username)** — when `allUsers()` was empty (before `loadAllUsers` completed), `resolveUsername(senderId)` returned `""`. Content hash became `":Hello:1719900000"` instead of `"alice:Hello:1719900000"`
- **Same message had different hashes at different times** — once with `user=""` (when allUsers empty), once with `user="alice"` (after allUsers loaded). Merge/dedup logic failed to match them
- **Fix: use `message.userId` (UUID) instead of `message.user`** — UUID is always available from proto, never depends on `allUsers` loading order
- **Impact**: messages disappearing from chat view while visible in chat list last message. Server correctly stores message, but client's dedup logic creates two entries with different content hashes

## Thread Safety Audit (v1.3.1.16)

- **Fields without `@Volatile`** — `appContext`, `currentRoomId`, `markReadJob`, `pendingMarkReadRoom/User`, `database` in RealGrpcClient; `channel`, `currentServerAddress`, `currentServerPort`, `reconnectDelayMs`, `appContext` in GrpcConnectionManager; `callRequestObserver` in GrpcCallClient; `typingRequestObserver` in GrpcTypingClient; `database` in GrpcMessageV2Client — all accessed from multiple threads
- **Fix: added `@Volatile`** to all cross-thread fields
- **`allChats` in ChatListViewModel** — was written on `Dispatchers.IO` (init block) but read/written on Main thread. Fix: moved assignment to Main thread via `withContext(Dispatchers.IO)` for DB call only

## Memory Leak Fixes (v1.3.1.16)

- **CallController not cancelled in onDestroy** — `CallActivity.onDestroy()` did not call `callController?.cancel()`. Coroutine scope kept running, holding Activity via `context` field. Fix: added `callController?.cancel()` to `onDestroy()`
- **GrpcConnectionManager capturing Activity in reconnect lambda** — `scheduleReconnect` received raw `context` (could be Activity) and captured it in coroutine lambda with up to 60s delay. Fix: extract `context?.applicationContext` before launching coroutine
- **AIBottomSheet.agentScope never cancelled** — `CoroutineScope` created but never cancelled on dismiss. Fix: `dialog?.setOnDismissListener { agentScope.cancel() }`
- **CallActivity.fetchTurnCredentials unmanaged Thread** — `Thread { ... }.start()` with `runOnUiThread` callback kept Activity alive. Fix: replaced with `lifecycleScope.launch(Dispatchers.IO)` + `isFinishing/isDestroyed` guard

## gRPC Resilience Fixes (v1.3.1.16)

- **Typing stream retry loop** — was unconditional `delay(5000)` with no backoff, no max retries, no connection check. On UNAUTHENTICATED/channel shutdown, retried forever. Fix: exponential backoff (1s→30s), max 10 retries, check channel state before retry, reset count on success
- **Call stream retry loop** — same anti-pattern as typing. Fix: same backoff + max retries + connection check
- **gRPC silent error swallows** — `editMessageV2`, `deleteMessageV2`, `setReactionV2` `onClose` callbacks silently discarded errors. Fix: added `ErrorHandler.handle()` to all three

## Room DB Index (v1.3.1.16)

- **No index on `messages.roomId`** — every message query did full table scan. Most queried column in the database. Fix: added `@ColumnInfo(index = true)` on `MessageEntity.roomId` + migration 11→12 with `CREATE INDEX`

## Message History Race Condition (v1.3.1.18)

- **`loadHistoryV2` cache+server race** — Cache phase (IO thread) and server phase (gRPC callback) run concurrently, both call `messages.update`. If server completes first, cache phase overwrites with stale DB data. Fix: `loadHistoryServerCompleted` flag — cache phase skips merge if server already completed
- **`addLocalMessage` DB write races with `sendMessageV2`** — Both save to Room DB independently on IO. If `addLocalMessage` completes after `sendMessageV2`'s delete+insert, stale UUID record persists. Fix: removed DB save from `addLocalMessage` — only `sendMessageV2` response handler saves
- **Server timestamp differs from client** — `getContentHash` uses `timestamp/1000`. Server assigns its own timestamp, client uses `System.currentTimeMillis()`. Clock skew >1s defeats content-hash dedup. Fix: `getContentHash` already uses `userId` (UUID) which is clock-independent

## Swipe Refresh (v1.3.1.18)

- **Pull-to-refresh wiped Room DB** — `clearRoomMessages()` deleted all cached messages, then `switchRoom()` cleared in-memory state. If server was slow, messages disappeared. Fix: removed `clearRoomMessages`, use `forceLoadHistory()` instead
- **`_isLoading` guard blocked `switchRoom()` history load** — If combine collector fired `loadHistory()` first, `switchRoom()`'s call was silently dropped. Fix: added `forceLoadHistory()` that bypasses the guard

## Group Chat Deletion (v1.3.1.18)

- **Server rejected non-creator delete but client ignored error** — `deleteChat` callback discarded the error message (`{ success, _ -> }`). Fix: callback now passes error message to UI, shows Toast
- **Admins couldn't delete group chats** — Server only allowed creator to delete groups. Fix: server now checks `IsSuperAdmin()` — admins can delete any group
- **No confirmation dialog for group deletion** — Long-press delete showed no warning. Fix: added AlertDialog with creator name for group chats

## ShareReceiverActivity (v1.3.1.18)

- **Crash on browser share** — Activity didn't call `GrpcClient.connect()`, message had no `userId`, no error handling. Fix: added gRPC connection init, `userId` in message, try-catch around `onCreate`

## Call Accept Race Condition (v1.3.1.19)

- **`acceptCall()` before `CallController` subscribes** — `btnAccept.setOnClickListener` called `initWebRtc()` (async) then `CallManager.acceptCall()` (immediate). `acceptCall()` sent ACCEPT before `CallController` was created (created in `setupController()` → `setupWebRtcListeners()` → `fetchTurnCredentials` callback). Caller receives ACCEPT → creates OFFER → sends back. OFFER arrives at `incomingSignals` SharedFlow but nobody is collecting yet. `incomingSignals` has `extraBufferCapacity=64` but `replay=0` — new subscribers don't get buffered values. OFFER lost → call stuck on "Подключение..."
- **Fix:** `initWebRtc(onReady)` callback — `acceptCall()` called after `setupController()` completes
- **Why SharedFlow extraBufferCapacity doesn't help:** With `replay=0`, extra buffer is only for slow EXISTING subscribers. New subscribers start from scratch — they only see values emitted AFTER they subscribe.

## FCM Incoming Call Not Opening CallActivity (v1.3.1.19)

- **`handleIncomingCall()` only showed notification** — `setFullScreenIntent()` only works when screen is off/locked. When app is in foreground, Android shows heads-up notification instead. User may miss it → CallActivity never opens
- **Fix:** `handleIncomingCall()` now always calls `startActivity(CallActivity)` directly in addition to showing notification
- **Notification still needed:** ringtone, decline button, visible when screen off
- **`SENDER_NAME` missing from notification intent** — CallActivity showed UUID instead of caller name when opened from notification tap. Fix: added `SENDER_NAME` to notification intent

## Push Notifications Not Dismissed / Splash on Tap (v1.3.2.14)

- **FCM service runs before session init** — `SessionManager.session.value.username` is empty when `showNotification()` is called from `LavenderMessagingService.onMessageReceived()` before `SessionManager.initFromPrefs()`. Intent targets `SplashActivity` instead of `NewChatActivity` → splash screen on tap
- **`showNotificationFromStream()` hardcoded `USERNAME=""`** — PendingIntent extras had empty username → if NewChatActivity not on stack, new instance loads with empty data
- **Fix:** Both `showNotification()` and `showNotificationFromStream()` now always target `NewChatActivity` with fallback username from SharedPreferences. `loadDataFromIntent()` handles empty username via session/prefs fallback
- **`onNewIntent()` returned early without dismissing** — when same room notification tapped, `if (newRoomId == roomId) return` skipped `dismissNotificationsForRoom()` → notification persisted
- **Fix:** `dismissNotificationsForRoom(newRoomId)` called before the early return check
- **`markRead` callback delayed notification dismiss** — `dismissNotificationsForRoom` was only called via gRPC `markRead` callback, which had network latency
- **Fix:** Immediate `dismissNotificationsForRoom(roomId)` in `NewChatActivity.onCreate()` after `switchRoom`

## Swipe-to-Refresh Stuck Spinner (v1.3.1.22)

- **`listAIChats()` had no timeout** — gRPC call could hang indefinitely when offline. `supervisorScope` in `loadChats()` waited forever, `finally` block never ran, `_isLoading` stayed `true` permanently → spinner kept spinning
- **Fix:** added `withTimeoutOrNull(10.seconds)` for `aiDeferred` completion, matching `pageDeferred`

## Force Logout on Token Expiry (v1.3.1.22)

- **`ensureFreshToken()` gives up after 5s** — if gRPC channel isn't READY (client was offline, token expired), refresh is skipped. `getChats()` then gets UNAUTHENTICATED → force logout even though user just had no internet
- **Fix:** on UNAUTHENTICATED, `forceTokenRefresh()` is attempted + `getChats()` retried. Force logout only if retry also fails with auth error

## Call Signal Missing senderName (v1.3.1.22)

- **`initiateCall()` didn't set `senderName`** in `CallMessageProto` — server forwarded INITIATE signal with empty `senderName`, receiver fell back to `senderId` (UUID)
- **Fix:** added `senderName = getCurrentUsername()` in `initiateCall()`

## Duration API (v1.3.1.22)

- **Kotlin 2.x prefers `Duration` over `Long`** for `withTimeoutOrNull`, `delay`, etc. Converted all legacy Long overloads in ChatListViewModel, SessionManager, ChatListActivity, NewChatActivity
- **`CountDownLatch.await(Long, TimeUnit)`** is Java API — no Duration overload, left as-is

## Company System (v1.3.2.0)

- **CompanyService is separate gRPC service** — not part of ChatService. All RPCs under `messenger.CompanyService/*`
- **Default positions created by server** — Owner (3), Top Manager (2), Manager (1), Employee (0). Client should not create these
- **Access control is client-side** — `buildSections()` filters company chats by positionLevel. Server does NOT enforce visibility for company chats
- **UserSession stores positionLevel** — updated by `fetchAdminStatus()` after profile load. If profile not loaded, positionLevel defaults to 0 (Employee)
- **Company badge shown for all company chats** — even if user has no access (filtered by buildSections, not by adapter)
- **GetUserInfo returns company info** — used in ProfileActivity to show company section for other users
- **Marshallers for nested messages** — use `writeByteArray` (not `writeBytes`) for nested proto messages. `writeBytes` expects ByteString, not ByteArray
- **Room DB migration 12→13** — adds companyId, companyChatAccess, companyMinPositionLevel columns to chats table
- **ChatInfo.companyId populated from server** — server sets this field in GetChatsV2 response for company chats

## Media Preview Localization (v1.3.2.0)

- **Server sends hardcoded "Image" / "Voice message"** — stored in `chats.last_message_text` column. Client must translate for non-English locales
- **Translation in 3 places** — ChatListViewModel (real-time), ChatAdapter (display), SuperAdminAdapter (admin panel)
- **Content hash uses userId (UUID)** — not username, to avoid hash mismatch when allUsers is empty

## Missing Layout ID Fatal (v1.3.2.1)

- **`findViewById` returns null when view ID doesn't exist in layout** — even if the ID is defined in another layout file (R.id exists in generated R class). Kotlin treats the return as platform type `T!`, so `.setOnClickListener` compiles fine but throws NPE at runtime
- **`btnDeleteProfile` in `activity_edit_profile.xml`** — button had no `android:id`, code called `findViewById<Button>(R.id.btnDeleteProfile)` → null → NPE on `setOnClickListener`
- **Always verify:** every `findViewById` call must match a view in the CURRENT layout, not just any layout in the project

## Company Logo Upload (v1.3.2.1)

- **`CompanyProto.avatarUrl`** — company logo stored as `avatarUrl` field in `CompanyProto`
- **`UpdateCompanyRequestProto`** — field 3 = `avatarUrl`, sent via `GrpcCompanyClient.updateCompany()`
- **Upload flow:** HTTP `/upload-avatar` → get URL → `updateCompany(companyId, avatarUrl=url)`
- **Display:** `Glide.with().load(logoUrl).placeholder(R.drawable.ic_default_avatar).into(ivCompanyLogo)`
- **Owner-only:** logo change button only visible/functional for company owner

## CompanyProfileActivity Theme (v1.3.2.1)

- **`ThemeUi.bind()` must be called in `onCreate()`** after `setContentView()` — collects `ThemeStore.theme` StateFlow
- **`applyThemeToViews()` in `onResume()`** — applies theme colors to views not handled by `ThemeApplier`
- **`companyCard` added to `ThemeApplier`** — automatically themed with `surfaceColor` background

## Company Position Localization (v1.3.2.2)

- **Server returns English position titles** — "Owner", "Manager", etc. regardless of client locale
- **`formatCompanyPosition()`** maps level numbers to localized names: 0→Employee, 1→Manager, 2→Top Manager, 3→Owner
- **If positionTitle matches English name** — show only localized version (e.g., "Владелец" not "Owner (Владелец)")
- **If positionTitle is custom** — show both: "Директор (Владелец)"
- **Used in both ProfileActivity and EditProfileActivity** — same logic duplicated

## Company Logo in Profile (v1.3.2.2)

- **`ivProfileCompanyLogo` in `activity_profile.xml`** — CircleImageView next to company name
- **Loaded via `GrpcCompanyClient.getCompany(companyId)`** — async, shows logo if `avatarUrl` is not empty
- **Same pattern in EditProfileActivity** — logo loaded from company response

## Owner Protection in Members List (v1.3.2.2)

- **Owner cannot remove themselves as sole member** — `CompanyListFragment.showMemberOptions()` checks `memberCount <= 1` and `position.level == 3`
- **Shows Toast** with `R.string.owner_cannot_remove_self` instead of options dialog
- **If multiple members** — owner sees only "Change Position" (no "Remove")
- **`memberCount` tracked** from `loadMembers()` response

## Company Card Theme Fix (v1.3.2.2)

- **`?attr/colorSurface` in XML doesn't resolve for custom themes** — must set card background programmatically
- **Fix:** `companyInfoCard.setCardBackgroundColor(surfaceColor)` in `applyThemeToViews()`
- **Same pattern as chat list** — `ChatAdapter` sets `cardView.setCardBackgroundColor(bgColor)` programmatically

## Rename Company Dialog (v1.3.2.2)

- **`btnEditCompanyName`** — ImageButton with `ic_edit`, visible only for owner
- **Uses `StandardBottomSheet` with `dialog_edit_username` layout** — same as username change
- **`GrpcCompanyClient.updateCompany(companyId, name=newName)`** — server call
- **`inputLayout?.startIconDrawable = null`** — hides the person icon from username dialog

## Position Localization Pattern (v1.3.2.2)

- **Server returns English position titles** — "Owner", "Manager" regardless of locale
- **`formatPosition()` / `formatCompanyPosition()`** maps level → localized name
- **Used in:** `CompanyMemberAdapter`, `CompanyListFragment`, `ProfileActivity`, `EditProfileActivity`
- **Logic:** if title matches English name → show only localized; if custom → show "Custom (Localized)"

## Toolbar Color Standardization (v1.3.2.4)

- **`setSupportActionBar()` overrides `navigationIconTint`** — ActionBar takes control of navigation icon tint via its own theme (`textColorPrimary`), ignoring `app:navigationIconTint` from XML and ThemeApplier. When `setHomeAsUpIndicator()` creates a new drawable, it has no tint
- **Fix for activities without menu:** remove `setSupportActionBar()`, manage toolbar directly with `toolbar.setNavigationIcon()` + manual tint
- **Fix for activities with menu:** keep `setSupportActionBar()`, but after each `setHomeAsUpIndicator()` call re-apply `toolbar.navigationIcon?.setTint(getColorOnPrimary())`
- **`getColorOnPrimary()` helper:** `ThemeUtils.parseSafeColor(theme.onPrimaryColor, Color.WHITE)` — reads from ThemeStore, not Android theme attrs
- **Affected activities:** ContactsActivity (removed setSupportActionBar), ThemesActivity (removed), SuperAdminActivity (kept, manual tint)
- **Unified toolbar standard:** all toolbars use `toolbar_background` drawable + `@dimen/custom_toolbar_height` + `app:navigationIconTint="?attr/colorOnPrimary"` + `app:titleTextColor="?attr/colorOnPrimary"`

## Contacts Add with Group Chat (v1.3.2.4)

- **`showAddContactDialog()` creates group when 2+ selected** — previously only created direct chat for `selected.size == 1`, ignored multi-select with "create chat" checkbox
- **Fix:** `if (createChat) { if (selected.size == 1) createDirectChat else createGroupChat }`
- **String key renamed:** `create_direct_chat_after` → `create_chat_after` (EN/RU)
- **Empty participant list in group:** `showAddParticipantSheet()` filters out existing participants + contacts not in user's contact list. When result is empty, show `setEmptyState(true, getString(R.string.all_contacts_already_in_group))`

## AuthResponseV2 User Field Mapping (v1.3.2.5)

- **Server User proto fields:** 1=id, 2=username, 3=email, 4=avatar_url, 5=bio, 6=status, 7=created_at(Timestamp), 8=last_seen_at(Timestamp)
- **Client marshaller was missing field 4** — avatarUrl parsed from field 5 (bio), bio from field 6 (status), status from field 7 (created_at as String — type mismatch!)
- **Fix:** Added field 4 → avatarUrl, shifted 5→bio, 6→status, removed field 7/8 parsing (Timestamp not needed in auth response)

## GetPinnedMessagesRequest Swapped Fields (v1.3.2.5)

- **Server proto:** field 1=user_id, field 2=chat_id
- **Client marshaller had them reversed:** field 1=chatId, field 2=userId
- **Fix:** Swapped to match server: field 1=userId, field 2=chatId
- **Added limit/offset:** fields 3/4 added to data class and marshaller

## GetFavoritesResponse v1-v2 Type Mismatch (v1.3.2.5)

- **Server proto:** `repeated Message messages = 1;` — uses v1 `Message` type
- **Client was parsing as `MessageV2Proto`** — completely different field numbers (v1: user=2, text=3; v2: roomId=2, senderId=3, text=4)
- **Fix:** Parse as `MessageProto` (v1), then convert via `v1ToV2()` helper
- **v1→v2 mapping:** user→senderId, imageUrl+voiceUrl+duration→media(MessageMediaProto), reactions list→reactions JSON bytes

## RealGrpcClient.currentUsername Never Assigned (v1.3.2.5)

- **`currentUsername` was declared but never set** — `setUserId()` existed but no `setUsername()`
- **Impact:** All operations using `getUsername = { currentUsername }` callback received null: typing signals, call auto-start, markRead, FORCE_DISCONNECT matching
- **Fix:** Added `setUsername()` to RealGrpcClient + GrpcClient facade. `SessionManager.updateSession()` now calls `GrpcClient.setUsername(it)` alongside `setUserId()`

## markRead Double Callback (v1.3.2.5)

- **gRPC unary call:** `onMessage` fires first, then `onClose` always fires after
- **Client called `onComp?.invoke()` in both** — callback executed twice
- **Fix:** Removed `onComp?.invoke()` from `onMessage`, kept only in `onClose`

## ChatListViewModel Thread Safety (v1.3.2.5)

- **gRPC callbacks run on IO threads** — `toggleMute` and `deleteChat` callbacks mutated `allChats` (a plain `var`) from gRPC thread
- **Fix:** Wrapped callback bodies in `viewModelScope.launch(Dispatchers.Main)` for all gRPC callbacks that mutate shared state

## ChatAdapter notifyDataSetChanged (not blocking, medium priority)

- **5 places use `notifyDataSetChanged()`:** setSelectionMode, toggleSelection, clearSelection, updateOnlineUsers, updateAllUsers
- **Main data path correctly uses DiffUtil** — `setSections()` with `DiffUtil.calculateDiff()`
- **updateOnlineUsers/updateAllUsers** called on every presence change (60s + real-time) — full rebind of all visible items
- **Fix (next session):** Use `notifyItemChanged()` for affected items only

## deletedMessageHashes Unbounded Growth (not blocking, low priority)

- **ConcurrentHashMap.newKeySet()** grows with every deleted message, loaded from SharedPreferences on startup
- **Growth rate:** human-limited (~10-50 deletions per day, ~40 bytes each)
- **Fix (next session):** Add LRU cap at 10000 entries

## TURN Credentials Auth (v1.3.2.9)

- **`fetchTurnCredentials()` used raw `java.net.URL`** — no Authorization header → server returns 401 → client falls back to STUN-only (Google STUN) → no TURN relay → behind CGNAT devices can't establish P2P
- **Fix:** Added `AuthManager.getBearerToken()` to Authorization header
- **Symptom:** Calls connect at signaling level (ICE candidates exchanged) but media never flows. Works on same-network devices (STUN sufficient), fails on mobile carriers (CGNAT requires TURN relay)

## Typing Room Switch (v1.3.2.9, server fix)

- **Server didn't update `currentRoom` on room switch** — client sends `ChatV2MessageProto(roomId = newRoom)` without payload, server's switch statement falls through, `currentRoom` stays at auth-time room
- **Typing broadcast went to wrong room** — `BroadcastToRoom(currentRoom, ...)` used stale room
- **Fix (server):** Added `msg.RoomId != currentRoom` check before switch, updates `currentRoom` + `hub.SetV2Room()`
- **Requires server restart** to deploy

## JWT Refresh Token Missing Username (v1.3.3.7)

- **Root cause of empty `connectedUser`/`room` in all ChatV2 connections** — `authClaims` for refresh token did NOT include `Username` field. Only `UserID` and `DeviceID` were set.
- **Flow:** User logs in → access token has username ✓ → token refreshes → new access token has `Username: ""` (from refresh token claims) → all ChatV2 connections show `connectedUser = ""` → typing broadcasts to room "" → `BroadcastCall` can't match receiver
- **Symptoms:** `[ChatV2]  connected to room ` (empty user AND room), typing doesn't work, ACCEPT not delivered to caller
- **Fix (server):** Added `Username: username` to `refreshClaims` in `auth_jwt.go`
- **After fix:** All users must re-login (or wait for token expiry + refresh) to get new JWT with username
- **Also fixed:** `BroadcastCall` receiver resolution — if `ReceiverId` is username (not UUID), resolve via `GetUserIDByUsername()` before matching against `callStreams` map

## setDecorFitsSystemWindows Before super.onCreate (v1.3.2.11)

- **`NewChatActivity` was the ONLY activity** calling `setDecorFitsSystemWindows(window, false)` BEFORE `super.onCreate()`
- On API 31+ (Android 12+), this is a real system call that modifies window decor behavior
- Calling before `super.onCreate()` means `AppCompatActivity` base initialization runs AFTER the flag is set → decor view in inconsistent state → crash during `setContentView()`
- On API 29 (Android 10), this is a no-op → no crash
- On API 31 (Android 12), this is a real system call → crash
- On API 34 (Android 14), may not crash but ordering is still wrong
- **Fix:** Moved `setDecorFitsSystemWindows` after `super.onCreate()`

## OutOfMemoryError in uploadFile (v1.3.2.11)

- **`ShareReceiverActivity.uploadFile()`** used `stream.readBytes()` which reads entire file into a single byte array
- Large images (20MB+) → `OutOfMemoryError` (extends `Error`, NOT `Exception`)
- `catch (e: Exception)` does NOT catch `Error` subclasses → app crashes
- **Fix:** Added separate `catch (e: OutOfMemoryError)` with user-friendly Toast
- **Same risk exists in:** `AudioUploader.kt`, `ChatInputDelegate.kt` — any place using `readBytes()` on large files

## Call Notification Not Dismissed (v1.3.2.10)

- **`CallActivity.onDestroy()`** did not dismiss the call notification — only `CALL_ENDED` FCM push or `CallActionService` DECLINE removed it
- **Fix (client):** Added `dismissCallNotification()` in `CallActivity.onDestroy()`, `CallManager.hangup()`, `CallManager.rejectCall()`, `CallManager.clearCurrentCall()`, `CallManager.handleCallEndedPush()`
- **Push when already in call:** `LavenderMessagingService.handleIncomingCall()` now checks `CallManager.currentCall.value != null` before showing notification

## ThemeApplier Crashes on Some Devices (v1.3.2.12)

- **`ThemeApplier.apply()` was the #1 uncaught crash source for chat entry** — ~50 view operations without any try-catch. Called via `ThemeUi.bind()` → `repeatOnLifecycle(STARTED)`, so any exception in `apply()` crashed the Activity
- **Device-specific:** Crashed on some manufacturer + API level combinations (e.g. `WindowInsetsControllerCompat` failed on certain MIUI/OneUI builds, `DrawableCompat.wrap(bg.mutate())` on others). Working on most devices
- **Fix:** Split `apply()` into 5 try-catch sections: WindowInsets, background decorView, toolbar, widgets/tabLayout, panels/forms. Each section logs to Logcat and continues
- **`ThemeUi.bind()`** — outer try-catch around `ThemeApplier.apply()` prevents coroutine scope crash
- **`ChatToolbarDelegate.setup()`** — `setSupportActionBar()` + `ThemeStore.currentTheme().toColorInt()` could crash on invalid theme colors. Now wrapped in try-catch with fallback to basic UI
- **Rule:** Always wrap `ThemeApplier.apply()` and any code that calls `ThemeStore.currentTheme()` + `.toColorInt()` in try-catch. Custom themes can have invalid color strings

## Dead Code in ThemeApplier (v1.3.2.12)

- **`R.id.tvToolbarTitle` / `R.id.tvToolbarSubtitle`** — ThemeApplier searched for these IDs inside the toolbar, but they don't exist in any layout (the actual IDs are `toolbarTitle` / `toolbarSubtitle`). Safe-call `?.` prevented crash but code was dead
- **Removed** — no functional change

## Defensive Error Handling Pattern (v1.3.2.12)

- **`NewChatActivity.onCreate()`** — all init calls wrapped in try-catch:
  - `initDelegates()` / `initSharedViews()` → try-catch + `finish()` + `return`
  - `setupDelegates()` → try-catch (non-fatal, chat opens without full delegate setup)
  - `combine` flow collector → try-catch (prevents coroutine scope crash)
  - `fetchChatMetadata` callback → try-catch
  - `setupTheme()` → try-catch
  - `setDecorFitsSystemWindows` → try-catch
- **Purpose:** Prevent crashes on devices where we can't get logcat. Error messages go to Logcat for future diagnosis

## Token Refresh Race Condition (v1.3.2.13)

- **Three paths call `GrpcClient.refreshToken()` independently:** `performTokenRefresh()` (periodic 60s, Main thread), `ensureFreshToken()` (before gRPC calls, IO thread), `forceTokenRefresh()` (pull-to-refresh, IO thread)
- **Server implements refresh token rotation with reuse detection:** each successful refresh rotates the JTI (JWT ID). Submitting a previously-used JTI triggers `RevokeDevice()` — the entire device session is killed (`is_active = FALSE`)
- **Root cause:** old `isRefreshing` flag only guarded `ensureFreshToken()`. `performTokenRefresh()` and `forceTokenRefresh()` had no mutual exclusion. When the 60s periodic timer fired simultaneously with a pull-to-refresh or `loadChats()`, both read the same old refresh token and sent it to the server. Server processed the first → rotated. Server processed the second → reuse detected → device revoked → user forced to re-login
- **Fix:** replaced `isRefreshing` with `refreshGuard: AtomicBoolean`. All three paths use `compareAndSet(false, true)` to acquire the guard. `waitForRefreshComplete()` helper polls until guard is released. Each path re-checks token freshness after waiting — if another refresh already completed, skips redundant refresh
- **Server confirmation:** `auth_jwt.go:103-116` `ValidateToken` checks JWT expiry. `db_auth_devices.go:188-204` `ValidateRefreshToken` checks DB `is_active` AND JTI match. `auth_service_v2.go:355-360` on reuse: `RevokeDevice()` + log `refresh_reuse_detected`
- **Impact:** intermittent forced re-login despite auto-refresh working. Most likely triggered when app returns from background (Doze) and both periodic timer + loadChats fire simultaneously

## Edited Message Not Updating (v1.3.2.15)

- **`editMessageV2()` only handled errors** — on success, the edited message text was not updated in `_messages` StateFlow or Room DB. The `ListAdapter`DiffUtil` didn't detect the change because the `Message` object was stale
- **Fix:** `RealGrpcClient.editMessageV2()` now updates `_messages` via `update { current.map { if (id == it.id) it.copy(text, edited=true) else it } }` and Room DB via `messageDao().updateMessageText(id, text, edited=true)` immediately on success
- **ChatV2 stream does NOT broadcast edits** — unlike DELETE_MESSAGE, there is no EDIT_MESSAGE system message type. Edited messages only appear when `loadHistoryV2` is called (app restart or pull-to-refresh)
- **`MessageDao.updateMessageText()`** — new Room DAO method: `UPDATE messages SET text = :text, edited = :edited WHERE id = :messageId`

## Gallery Thumbnails in FullScreenImageActivity (v1.3.2.15)

- **Previous UX:** single image with swipe + +x overlay for galleries — users didn't realize they could swipe or tap +x
- **New UX:** bottom bar with horizontal RecyclerView of 56x56dp thumbnails, counter text ("1 / 5"), current thumbnail highlighted with white border (3dp stroke), others at 50% alpha. Smooth scroll to current on swipe. Tap thumbnail → jump to that image
- **`item_thumbnail.xml`** — FrameLayout with ImageView (56dp, centerCrop) + selectedBorder View (60dp, GradientDrawable white stroke)
- **`ThumbnailAdapter`** — inner class in FullScreenImageActivity, handles click → callback to update currentIndex + loadImage + updateThumbnailHighlight
- **Layout change:** root changed from FrameLayout to vertical LinearLayout (image in FrameLayout with weight=1, bottomBar below)

## ChatAdapter Theme Cache (v1.3.2.16)

- **`ChatAdapter.colorsInitialized` never reset** — `initColors()` short-circuits after first call. Theme changes don't affect card backgrounds until adapter is recreated
- **Fix:** Added `updateTheme()` method (sets `colorsInitialized=false` + `notifyItemRangeChanged`). Called from `ChatListActivity.onResume()` after `ThemeApplier.apply()`
- **Pattern:** Same as `UserAdapter.updateTheme()` (line 63-66) — all adapters with cached theme colors need this method

## ProfileActivity setSupportActionBar (v1.3.2.16)

- **`setSupportActionBar(toolbar)` overrides navigation icon tint** — ActionBar takes control, replaces XML-defined icon+tint with its own. Arrow invisible but still clickable
- **Fix:** Remove `setSupportActionBar()`, manage toolbar directly: `setNavigationIcon()` + `setTint(getColorOnPrimary())`
- **Same fix applied to:** ContactsActivity, ThemesActivity, SuperAdminActivity in v1.3.2.4

## Chat Gallery Thumbnails (v1.3.2.16)

- **Replaced `tvGalleryCount` (+N overlay) with `rvGalleryThumbnails`** — horizontal RecyclerView showing up to 4 clickable thumbnails for multi-image messages
- **`ThumbnailGridAdapter`** — inner class in `MessageAdapter`, uses `item_thumbnail.xml` (reused from FullScreenImageActivity)
- **Single images:** still use `ivMessageImage` (unchanged behavior)
- **`CredentialStore.getHttpServerUrl(ctx)`** — required for relative URL resolution in thumbnails

## Inline Username in EditProfile (v1.3.2.16)

- **Replaced `btnChangeUsername` button with `tvInlineUsername`** — shows `@username` below avatar, tappable to open same `showChangeUsernameDialog()`
- **Layout:** `activity_edit_profile.xml` — `tvInlineUsername` added after avatar card, before bio card
- **Removed:** `btnChangeUsername` button and divider from settings card
