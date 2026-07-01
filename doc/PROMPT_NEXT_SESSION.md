# Prompt: Android Client — Next Session

**Версия:** v1.3.1.11 | **Ветка:** feat/1.3.1.x | **Дата:** 2026-06-29

---

## Быстрый старт

1. `doc/PATTERNS.md` — паттерны кода, правила, архитектура
2. `doc/PROMPT_NEXT_SESSION.md` — текущий план и бэклог
3. Этот файл — контекст сессии

Проект: `/Users/paveld/LavenderMessenger-Android`
Сборка: `./gradlew assembleDebug` (запускать локально на Mac)

---

## Текущая архитектура

```
GrpcClient (facade)
  └── RealGrpcClient — orchestrator
        ├── GrpcConnectionManager — connect/reconnect/health check
        ├── GrpcAuthClient — JWT auth (v2 only)
        ├── GrpcTypingClient — typing stream
        ├── GrpcCallClient — calls
        ├── GrpcChatClient (~250) — getChats (cursor pagination), create/delete, participants, settings
        ├── GrpcChatListV2Client (~120) — pin/unpin, search, archive
        ├── GrpcChatAuxClient (~130) — users, FCM, mute, getAdminUserList, getAdminUserSessions
        ├── GrpcProfileClient — contacts, themes, devices, passwords (ChatService)
        ├── ProfileClient — profile, avatar, settings, delete (ProfileService v2, JWT)
        ├── GrpcDraftClient, GrpcFavoritesClient (+ parseReactions)
        ├── GrpcMessageV2Client — messages v2 only (no v1 fallback), parseReactions (internal)
        ├── GrpcServerDiscoveryClient — server discovery
        ├── GrpcAIv2Client — AI v2 (ChatWithAIV2, Agent CRUD, Tools, Marketplace, Chat History)
        ├── SecretChatGrpc, ProfileClient
        ├── NotificationsGrpc — notifications (subscribe, history, read, unread)
        ├── RemoteAgentGrpc — Remote Agent (list, deploy, tokens, process)
        └── ChatKeepAliveService — foreground service, keep-alive connection

network/HttpClient.kt — singleton OkHttpClient (connection pool 5/5min, timeouts 30s)
network/AuthInterceptor.kt — JWT auth for HTTP requests

ChatListActivity → 10 modules (toolbar, tabs, FABs, auth, etc.)
NewChatActivity → 6 delegates + ChatViewModel (v2 only)
  └── ChatToolbarDelegate — toolbar, avatar, subtitle, navigation, E2EE status
  └── ChatInputDelegate — text input, send, attachments, audio, emoji, mentions
  └── ChatSelectionDelegate — selection mode, copy/pin/delete/forward
  └── ChatSearchDelegate — in-chat search
  └── ChatE2EEDelegate — end-to-end encryption for secret chats
  └── ChatMessageMenuDelegate — reactions, context menu
AiV2ChatActivity → unified AI chat + commands + rate limit + image/file support + multi-agent + errors as chat messages
AiV2AgentListActivity → unified agent management (5 tabs: Presets/My Agents/Discover/Remote Agent/Usage)
  └── Tab 3 Remote Agent → RemoteAgentSettingsFragment (inline Gateway + Token)
  └── Tab 4 Usage → UsageStatsFragment (summary cards + per-agent list, auto-refresh 30s)
AiAgentSetupActivity → create/edit all agent types (API key, temperature, max tokens)
AIBottomSheet → agent selection with user agents only + loading/empty states + fixed footer
SuperAdminActivity → admin panel with GetAdminUserList (cursor pagination, search, sort) + GetAdminUserSessions (expandable device sessions)

Auth: JWT only (v2), AuthManager + BearerTokenInterceptor + AuthInterceptor (HTTP)
SplashScreen: SplashActivity → animateAndNavigate() → navigateToTarget() → biometric prompt (15s timeout) + 5s safety timeout
Session: SessionManager (ensureFreshToken BEFORE loadChats + forceTokenRefresh on pull-to-refresh, async token refresh — no blocking Main thread)
Chat Stream: ChatV2 bidirectional stream (messenger.ChatService/ChatV2) — JWT auth + clientVersion, system signals, typing
Messages: v2 only — GetHistoryV2, SendMessageV2, EditMessageV2, DeleteMessageV2, SetReactionV2
Reactions: optimistic UI → Room DB save → server response → in-memory + Room DB update → REACTION_V2 stream → Room DB save
Unread: markAsRead optimistic clear + locallyReadChats tracking → gRPC MarkRead to server → server updates last_read_at
Real-time: ChatV2 stream messages added to _messages StateFlow + Room DB, auto markAsRead for active chat
AI v2: ChatWithAIV2 streaming + tool calling loop + 9 provider types (openrouter, local, mimo, webhook, websocket, subprocess, mcp, reve, hermes_acp) + image/file support
AI Chat History: GetAIV2ChatHistory + ListAIV2Chats (server-side)
AI Chat Settings: per-session API key + model override
AI Chat Commands: /new, /clear, /history, /settings, /model, /system, /tools
AI Chats in Chat List: AI chats merged into main chat list via ListAIV2Chats
AI Errors: shown as agent chat bubbles (⚠️ + текст), НЕ Toast
AI Marketplace: cache in Room DB (marketplace_agents table), Favorites (SharedPreferences)
Biometric: BiometricPrompt after splash screen when enabled (error → continue, not crash)
Chat List: Cursor-based pagination (infinite scroll), Unread highlight
Graceful Shutdown: SERVER_SHUTTINGDOWN + health check + backoff
Notifications in Remote Agent: server notifications shown as system messages in chat
Calls: WebRTC video calls via CallManager + CallNavigator + CallActivity
Secret Chats: E2EE via E2EEManager (ECDH + AES-256-GCM), key exchange with retry
```

---

## Итог сессии v1.3.1.07 (завершена)

### Выполнено

**1. Reactions fix:**
- `REACTION_V2` stream handler — reactions теперь сохраняются в Room DB даже если сообщение не в `_messages` (была primary причина бага)
- `setReactionV2` response — обрабатывает пустые реакции (optimistic state больше не залипает)
- Room DB save через захваченный `updatedMsg` вместо `messages.value.firstOrNull` (исправлена race condition в Избранном)
- +`updateReactions` DAO method для обновления реакций без полной перезаписи сообщения

**2. Message dedup:**
- Добавлен `getContentHash` (user + text + timestamp/1000) и `deduplicateByContent`
- Применён в `loadHistoryV2` — cache load и server response merge теперь дедуплицируют по content, предотвращая дубликаты temp ID vs server ID

**3. Chat list scroll position:**
- Scroll position теперь сохраняется при pull-to-refresh если пользователь прокрутил вниз (wasNearTop = false → scrollToPosition(firstVisible))

**4. Server-side message search:**
- `ChatSearchDelegate` теперь использует `SearchMessages` RPC с 300ms debounce
- Fallback на клиентский поиск если сервер вернул 0 результатов
- Добавлен `scope` и `roomId` параметры

**5. Notification sounds:**
- Notification channel теперь имеет default notification sound
- Per-chat sound override через `notification_sounds` SharedPreferences
- `setNotificationSound`/`getNotificationSound` companion methods

**6. Parallel chat loading:**
- Regular chats и AI chats загружаются одновременно через `supervisorScope` + `CompletableDeferred`
- Удалён отдельный `loadAiChats()` — AI chats теперь загружаются в `loadChats()`

**7. AI chat deletion:**
- `deleteChat()` пропускает серверный вызов для `ai-chat-*` ID (сервер не хранит AI chats в таблице `chats`)
- Удалённые AI chat ID сохраняются в SharedPreferences (`deleted_ai_chats`) и фильтруются при загрузке

**8. Server error handling:**
- gRPC `INTERNAL`/`UNAVAILABLE` ошибки теперь распознаются как `SERVER_ERROR`
- UI показывает "Сервер временно недоступен" вместо "Неверное имя или пароль"
- Обработаны в `ServersActivity`, `ChatListAuth`, `SessionManager`

### Изменённые файлы (клиент)

| Файл | Изменение |
|------|-----------|
| `RealGrpcClient.kt` | REACTION_V2 handler: +Room DB save via updateReactions() for missing messages |
| `GrpcMessageV2Client.kt` | +setReactionV2 empty reactions, captured updatedMsg for Room DB save, +getContentHash, +deduplicateByContent |
| `Daos.kt` | +updateReactions(messageId, reactionsJson) |
| `ChatListActivity.kt` | Scroll position preservation |
| `ChatSearchDelegate.kt` | Server-side search via SearchMessages RPC |
| `NewChatActivity.kt` | ChatSearchDelegate constructor + roomId wiring |
| `LavenderMessagingService.kt` | Channel sound, per-chat sound override |
| `ChatListViewModel.kt` | Parallel chat + AI loading, AI deletion locally only, deleted_ai_chats filter |
| `SessionManager.kt` | Login error handling: SERVER_ERROR for DB/server errors |
| `ServersActivity.kt` | SERVER_ERROR + CONNECTION_FAILED handling |
| `ChatListAuth.kt` | SERVER_ERROR + CONNECTION_FAILED handling |
| `strings.xml` (EN + RU) | +server_error |

---

## Итог сессии v1.3.1.09 (завершена)

### Выполнено

**1. Admin panel — lastSeenAt fix:**
- `SuperAdminAdapter.bindAdmin()`: `user.lastMessageTime` → `user.lastSeenAt` на главной плашке пользователя
- `SuperAdminActivity.loadData()`: `adapter.clearExpanded()` — pull-to-refresh очищает раскрытые сессии
- `SuperAdminAdapter.clearExpanded()`: очищает `expandedUsers` + `userSessions`
- Скрытие "unknown" IP в сессиях: `ipAddress != "unknown"` проверка

**2. Chat list — online status + last seen:**
- `item_chat.xml`: FrameLayout wrapper с `statusIndicator` (online dot) + `tvLastSeen` рядом с именем
- `ChatAdapter`: `onlineUsers`/`allUsers` параметры, bind logic для direct-чатов (online dot 🟢/⚪ + last seen time)
- `ChatListActivity`: подписка на `GrpcClient.users` + `GrpcClient.allUsers` для обновления статусов

**3. Admin panel — version from user_devices:**
- `PROMPT_ADMIN_VERSION_FIX.md`: SQL запрос `GetAdminUserList` берёт `last_client_version` из `user_devices` вместо `users`

**4. Server heartbeat:**
- `PROMPT_LAST_SEEN_FIX.md`: heartbeat 60s в ChatV2 stream + фикс `UpdateLastSeen` при `clientVersion != ""`

**5. Favorites reactions — диагностика + фикс:**
- `GrpcMessageV2Client.loadHistoryV2()`: fallback merge по `getContentHash` (user:text:timestamp) когда ID не совпадают
- `GrpcMessageV2Client.setReactionV2()`: логирование server response + onClose
- `PROMPT_FAVORITES_REACTION_FIX.md`: сервер возвращает `success=false` на `SetReactionV2` для Favorites UUID
- Корень: `SaveFavoriteMessage` генерирует другой UUID чем `req.Id` → `SetReactionV2` не находит сообщение

### Изменённые файлы (клиент)

| Файл | Изменение |
|------|-----------|
| `SuperAdminAdapter.kt` | `lastSeenAt` вместо `lastMessageTime`, `clearExpanded()`, "unknown" IP filter |
| `SuperAdminActivity.kt` | `adapter.clearExpanded()` при pull-to-refresh |
| `item_chat.xml` | FrameLayout wrapper + status dot + tvLastSeen |
| `ChatAdapter.kt` | onlineUsers/allUsers params, bind logic for direct chats |
| `ChatListActivity.kt` | observers for GrpcClient.users + allUsers |
| `GrpcMessageV2Client.kt` | contentHash fallback merge, setReactionV2 logging |

### Серверные задачи (выполнены)

- `PROMPT_LAST_SEEN_FIX.md` — heartbeat 60s + UpdateLastSeen fix ✅
- `PROMPT_ADMIN_VERSION_FIX.md` — last_client_version из user_devices ✅
- `PROMPT_FAVORITES_REACTION_FIX.md` — SetReactionV2 для Favorites ✅

---

## Итог сессии v1.3.1.11 (завершена)

### Выполнено

**1. Call signaling — UUID vs username fix:**
- `CallManager.initiateCall()` — резолвит username → UUID через `allUsers` перед отправкой
- Добавлен `resolveUserId(username)` helper
- 7 conference методов: `getCurrentUsername()` → `getUserId() ?: getCurrentUsername()` (initiateConference, joinConference, leaveConference, inviteToConference, removeFromConference, updateConferenceMetadata, endConference)
- `NewChatActivity` — `CallNavigator.startCall` получает UUID вместо username

**2. Server-side call push fix:**
- `server_chat.go:551` — `msg.SenderId` (UUID) вместо `msg.SenderName` (username) в FCM push
- `server_push.go:524-546` — `sender_id` = UUID, убран `resolveDisplayName`

### Изменённые файлы

| Файл | Изменение |
|------|-----------|
| `CallManager.kt` | `initiateCall()` резолвит username→UUID; `resolveUserId()` helper; 7 conference методов: `getUserId()` |
| `NewChatActivity.kt` | Резолвинг UUID через `allUsers` перед `CallNavigator.startCall` |
| `CHANGELOG.md` | Запись v1.3.1.11 |

### Серверные задачи (выполнены)

- `PROMPT_CALL_UUID_FIX.md` — FCM push sender_id = UUID ✅

---

## Итог сессии v1.3.1.10 (завершена)

### Выполнено

**1. Group info fix:**
- `ProfileViewModel.loadGroupData()` — теперь принимает intent extras (participants, creator, avatarUrl, fullAvatarUrl, name) как fallback при пагинации `getChats()`
- `ProfileActivity` — читает extras из intent и передаёт в `loadGroupData()`
- `ChatToolbarDelegate.openProfile()` + `SuperAdminActivity` — добавлен `chat_name` extra

**2. Call fix — WebRTC signaling:**
- `CallManager.handleIncomingSignal()` — `RECEIVER_ID` = `signal.senderId` (UUID), `SENDER_NAME` = display name. Ранее `displayName` передавался как `receiverId` → WebRTC сигналы не доходили
- `CallActivity` — `isCameraEnabled = true` по умолчанию; Accept включает камеру; `SENDER_NAME` для отображения имени

**3. FCM VOIP_CALL fix:**
- `LavenderMessagingService.handleIncomingCall()` — опрос `connectionStatus == READY` до 5 сек перед `startCallSession()`

**4. BadTokenException fix:**
- `ChatInputDelegate.showAttachmentSheet()` — `isFinishing/isDestroyed` guard + убрано кеширование `WidgetManager.getOrCreate`

### Изменённые файлы

| Файл | Изменение |
|------|-----------|
| `ProfileViewModel.kt` | `loadGroupData()` с intent extras fallback |
| `ProfileActivity.kt` | Чтение extras + передача в loadGroupData |
| `ChatToolbarDelegate.kt` | `chat_name` extra |
| `SuperAdminActivity.kt` | `chat_name` extra |
| `CallManager.kt` | `RECEIVER_ID` = UUID, `SENDER_NAME` = display name |
| `CallActivity.kt` | `isCameraEnabled = true`, `SENDER_NAME`, Accept включает камеру |
| `LavenderMessagingService.kt` | Ждёт `connectionStatus == READY` перед startCallSession |
| `ChatInputDelegate.kt` | `isFinishing/isDestroyed` guard, убрано WidgetManager кеширование |

---

## Задачи — Следующая сессия (v1.3.1.11)

### P0: Звонки — сигналы не доставляются (UUID vs username) ✅ ВЫПОЛНЕНО

**Проблема:** Звонки не доходят — `BroadcastCall` на сервере не находит получателя. Сервер хранит `callStreams[stream] = UUID`, а клиент отправляет `username` как `receiverId`.

**Корень:**
1. `getOtherParticipant()` возвращает **username** из `participantsJson` (сервер хранит usernames)
2. `initiateCall(username)` → `CallMessageProto(receiverId="ferz")`
3. Сервер: `BroadcastCall` сравнивает UUID == username → **не совпадает** → `delivered: false`
4. FCM push отправляется с `sender_id = senderUsername` (username) → `handleIncomingCall(senderId)` → `CallActivity` получает username → WebRTC сигналы тоже не доставлялись

**Исправление (клиент):**
- `CallManager.initiateCall()` — резолвит username → UUID через `allUsers` перед отправкой
- Добавлен `resolveUserId(username)` helper
- 7 conference методов: `getCurrentUsername()` → `getUserId() ?: getCurrentUsername()`
- `NewChatActivity` — `CallNavigator.startCall` получает UUID вместо username

**Исправление (сервер):**
- `server_chat.go:551` — `msg.SenderId` (UUID) вместо `msg.SenderName` (username) в FCM push
- `server_push.go:524-546` — `sender_id` = UUID, убран `resolveDisplayName`

**Серверный промпт:** `PROMPT_CALL_UUID_FIX.md` ✅

### P1: Token resilience — разлогин при недоступности сервера ✅ ВЫПОЛНЕНО

**Проблема:** Сервер временно недоступен → `INTERNAL`/`NOT_CONNECTED` ошибки → force logout. Также токен протухает за ночь и не обновляется при пробуждении.

**Исправление:**
- `ChatListViewModel.kt` — force logout только для `UNAUTHENTICATED`/`PERMISSION_DENIED` (убраны `INTERNAL`/`NOT_CONNECTED`)
- `ChatListActivity.onResume()` — `ensureFreshToken` перед загрузкой чатов (обрабатывает пробуждение после doze)

---

### Полный аудит: UUID vs Username в клиенте

| # | Файл:строка | Поле | Тип | Нужен UUID? | Проблема |
|---|-------------|------|-----|-------------|----------|
| 1 | `CallManager.kt:109` | `initiateCall(receiverId)` | username | ✅ Да | `getOtherParticipant()` возвращает username из `participantsJson` |
| 2 | `CallManager.kt:206` | `initiateConference(senderId)` | username | ✅ Да | `getCurrentUsername()` вместо `getUserId()` |
| 3 | `CallManager.kt:218` | `joinConference(senderId)` | username | ✅ Да | `getCurrentUsername()` вместо `getUserId()` |
| 4 | `CallManager.kt:232` | `leaveConference(senderId)` | username | ✅ Да | `getCurrentUsername()` вместо `getUserId()` |
| 5 | `CallManager.kt:242` | `inviteToConference(senderId)` | username | ✅ Да | `getCurrentUsername()` вместо `getUserId()` |
| 6 | `CallManager.kt:254` | `removeFromConference(senderId)` | username | ✅ Да | `getCurrentUsername()` вместо `getUserId()` |
| 7 | `CallManager.kt:265` | `updateConferenceMetadata(senderId)` | username | ✅ Да | `getCurrentUsername()` вместо `getUserId()` |
| 8 | `CallManager.kt:281` | `endConference(senderId)` | username | ✅ Да | `getCurrentUsername()` вместо `getUserId()` |
| 9 | `server_push.go:548` | `sendCallPushNotification` → `sender_id` | username | ✅ Да | Сервер отправляет `senderUsername` вместо `senderId` (UUID) |
| 10 | `NewChatActivity.kt:560` | `getOtherParticipant()` | username | ✅ Да | Корень проблемы — `participantsJson` хранит usernames |

### Исправление

**Клиент (CallManager.kt):**
- Все 7 методов conference: заменить `getCurrentUsername()` → `getUserId() ?: getCurrentUsername()` (line 206, 218, 232, 242, 254, 265, 281)
- `initiateCall`: резолвить username → UUID через `allUsers` перед отправкой
- `NewChatActivity`: `getOtherParticipant()` должен возвращать UUID, не username

**Сервер (server_push.go:548):**
- `sender_id` в FCM push должен быть `msg.SenderId` (UUID), а не `senderUsername`

**Правило:** Все gRPC/routing вызовы должны использовать UUID, НЕ username. Username — только для отображения в UI.

---

## Правила (обязательно к прочтению)

1. **НЕ компилировать Android на сервере** (OOM kill) — assembleRelease ТОЛЬКО локально
2. **НЕ деплоить на prod** без явного указания
3. userId (UUID) — всегда как ключ, НЕ username
4. Все новые строки ОДНОВРЕМЕННО в `values/strings.xml` + `values-ru/strings.xml`
5. getString() НЕ в полях Activity — только в методах
6. Kotlin 2.4.0: `cont.resume(value, onCancellation = {})`
7. Все ошибки через `ErrorHandler.handle()` — НЕ `Log.e`
8. v2 server only — никаких v1 fallbacks
9. Chat toolbar: фиксированная высота `@dimen/custom_toolbar_height`, elevation 0dp
10. Все chat activities: `setDecorFitsSystemWindows(window, false)` в onCreate
11. Marshallers: всегда включать v2 proto поля, сверять field numbers с серверным proto
12. JWT freshness: `ensureFreshToken()` перед gRPC вызовами (loadChats, Chat stream)
13. **Перед коммитом всегда запускать `./gradlew assembleDebug`**
14. **НЕ bump'ать версию — bump делает только пользователь**
15. **Marshallers field order:** server proto определяет field numbers
16. **AI v2 RPC:** все методы в `messenger.ChatService/*` (НЕ `AIService`)
17. **Unread count:** считается по `user_chat_metadata.last_read_at`, НЕ по `messages.is_read`
18. **ProfileService v2:** profile/avatar/delete/settings — через `messenger.ProfileService/*` (JWT context)
19. **CHANGELOG:** не включать документационные изменения — только код
20. **AI ошибки:** показывать как chat bubble (⚠️ + текст), НЕ Toast
21. **AuthManager.getBearerToken()** возвращает "Bearer <token>" — НЕ добавлять ещё один "Bearer "
22. **ВСЕГДА сверять с сервером:** перед любым gRPC/marshaller изменением проверять серверный код
23. **Reaction flow:** optimistic UI → Room DB save → server response (incl. empty) → in-memory + Room DB update → REACTION_V2 stream → Room DB save (even if message not in _messages)
24. **UUID ALWAYS for routing, username ONLY for display:** callStreams, participants, gRPC routing, FCM payloads, intent extras — all use UUID. Username CAN change. `participantsJson` stores usernames → resolve via `allUsers` before sending to server.

---

## Сервер

| | Dev | Prod |
|--|-----|------|
| gRPC | 50052 | 50051 |
| HTTP | 8083 | 8082 |
| Сервис | lavender-server-dev | lavender-server |
| Сайт | http://13.140.25.249 |

**Деплой сервера:** НЕ делать — другой агент управляет сервером.

---

## Полезные ссылки

- Документация клиента: `doc/INDEX.md`, `doc/PATTERNS.md`
- Документация AI v2: `doc/AI_V2_TESTING.md`
- Клиентский план Hermes: `doc/PROMPT_HERMES_ACP_CLIENT.md`
- Changelog: `CHANGELOG.md`
