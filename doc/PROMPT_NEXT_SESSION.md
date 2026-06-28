# Prompt: Android Client — Next Session

**Версия:** v1.3.1.07 | **Ветка:** feat/1.3.1.x | **Дата:** 2026-06-29

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

## Итог сессии v1.3.1.06 (завершена)

### Выполнено

**1. Splash screen freeze fix:**
- `SessionManager.waitForConnectionAndReLogin()` — убран `latch.await(8, SECONDS)`, блокировавший Main thread. Callback обрабатывает результат асинхронно
- `SplashActivity` — добавлен 15s biometric timeout (принудительный переход если biometric завис)
- `SplashActivity` — добавлен 5s safety timeout (принудительный переход если анимация сломалась)

**2. Duplicate message fix:**
- `GrpcMessageV2Client.sendMessageV2()` — race condition: temp ID → server ID в response handler, но ChatV2 stream уже добавил сообщение с server ID. Добавлен dedup фильтр после обновления ID

**3. Push notification deep link fix:**
- `NewChatActivity.onNewIntent()` — если пользователь уже в чате и нажимает уведомление на другой чат — теперь корректно переключается (startChatV2 + markRead + toolbar update)

**4. Backlog check:**
- Offline mode — ✅ уже реализовано (Room DB кеш для чатов и сообщений)
- Push notification deep link — ✅ уже реализовано (+ исправлен onNewIntent)

### Изменённые файлы (клиент)

| Файл | Изменение |
|------|-----------|
| `SessionManager.kt` | убран `CountDownLatch` + `latch.await(8s)` |
| `SplashActivity.kt` | +Log import, +15s biometric timeout, +5s splash timeout |
| `GrpcMessageV2Client.kt` | dedup фильтр в `sendMessageV2` response handler |
| `NewChatActivity.kt` | `onNewIntent()` +startChatV2, +markRead, +toolbar update |

---

## Задачи — Следующая сессия (v1.3.1.07)

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
23. **Reaction flow:** optimistic UI → Room DB save → server response → in-memory + Room DB update → REACTION_V2 stream

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
- Серверный промпт (Hermes ACP): `/Users/paveld/LavenderMessenger-server/doc/PROMPT_HERMES_ACP.md`
- Клиентский план Hermes: `doc/PROMPT_HERMES_ACP_CLIENT.md`
- Серверный промпт (Admin User List): `/Users/paveld/LavenderMessenger-server/doc/PROMPT_ADMIN_USER_LIST.md`
- Серверный промпт (Reactions Fix): `/Users/paveld/LavenderMessenger-server/doc/PROMPT_REACTIONS_FIX.md`
- Серверный промпт (Admin Sessions): `/Users/paveld/LavenderMessenger-server/doc/PROMPT_ADMIN_SESSIONS.md`
- Changelog: `CHANGELOG.md`
