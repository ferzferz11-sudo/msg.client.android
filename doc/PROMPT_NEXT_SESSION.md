# Prompt: Android Client — Next Session

**Версия:** v1.3.1.16 | **Ветка:** feat/1.3.1.x | **Дата:** 2026-07-02

---

## Следующая сессия: Оптимизация завершена

**Фокус:** все 8 областей аудита пройдены. Следующий шаг — тестирование и релиз.

### Области анализа

1. **Thread safety** — ✅ 15 `@Volatile` + allChats race fix
2. **Memory leaks** — ✅ 4 фикса (CallController, GrpcConnectionManager, AIBottomSheet, CallActivity)
3. **Deprecated API** — ✅ IntentService → CallActionService
4. **Error handling** — ✅ 23 `Log.e`/`Log.w` → ErrorHandler + 3 gRPC silent swallows fixed
5. **Dead code** — ✅ Задокументировано (5 unused params, clearSelection)
6. **UI consistency** — ✅ 2 Activity + setDecorFitsSystemWindows
7. **gRPC resilience** — ✅ Retry loops + backoff + isAuthFailure + connection checks
8. **Room DB** — ✅ Index на roomId (migration 11→12)

### Ключевые файлы для аудита

| Файл | Описание |
|------|----------|
| `RealGrpcClient.kt` | Main orchestrator — thread safety, state management |
| `SessionManager.kt` | Token lifecycle — refresh, race conditions |
| `ChatListViewModel.kt` | Chat list — concurrent updates, memory |
| `NewChatActivity.kt` | Chat screen — delegates lifecycle |
| `CallManager.kt` | Calls — WebRTC lifecycle, coroutine scopes |
| `GrpcMessageV2Client.kt` | Messages — dedup, Room DB, reactions |
| `LavenderMessagingService.kt` | FCM — notification lifecycle |
| `CallController.kt` | Calls — WebRTC state machine, ACCEPT handling |
| `CallActionService.kt` | FCM call actions — Service vs IntentService |
| `UpdateManager.kt` | Downloads — coroutine cancellation, stale APK cleanup |

---

## Быстрый старт

- Проект: `/Users/paveld/LavenderMessenger-Android`
- Сборка: `./gradlew assembleDebug`
- Сервер: `/Users/paveld/LavenderMessenger-server/`
- Сервер docs: `/Users/paveld/LavenderMessenger-server/doc/CLIENT_INTEGRATION.md`

---

## Текущая архитектура

```
GrpcClient (facade)
  └── RealGrpcClient — orchestrator (~1200 lines)
        ├── GrpcConnectionManager — connect/reconnect/health check
        ├── GrpcAuthClient — JWT auth (v2 only)
        ├── GrpcTypingClient — typing stream
        ├── GrpcCallClient — calls
        ├── GrpcChatClient (~250) — getChats, create/delete, participants, settings
        ├── GrpcChatListV2Client (~120) — pin/unpin, search, archive
        ├── GrpcChatAuxClient (~130) — users, FCM, mute, admin
        ├── GrpcProfileClient — contacts, themes, devices, passwords
        ├── ProfileClient — profile, avatar, settings, delete (ProfileService v2)
        ├── GrpcDraftClient, GrpcFavoritesClient
        ├── GrpcMessageV2Client — messages v2 only
        ├── GrpcServerDiscoveryClient — server discovery
        ├── GrpcAIv2Client — AI v2 (ChatWithAIV2, Agent CRUD, Tools, Marketplace)
        ├── NotificationsGrpc — notifications
        ├── RemoteAgentGrpc — Remote Agent
        └── ChatKeepAliveService — foreground service

network/HttpClient.kt — singleton OkHttpClient
network/AuthInterceptor.kt — JWT auth for HTTP

ChatListActivity → 10 modules (toolbar, tabs, FABs, auth, update, etc.)
NewChatActivity → 6 delegates + ChatViewModel
  ├── ChatToolbarDelegate — toolbar, avatar, subtitle, E2EE status
  ├── ChatInputDelegate — text, send, attachments, audio, emoji
  ├── ChatSelectionDelegate — selection mode, copy/delete/forward
  ├── ChatSearchDelegate — in-chat search (server + client fallback)
  ├── ChatE2EEDelegate — E2EE for secret chats
  └── ChatMessageMenuDelegate — reactions, context menu
AiV2ChatActivity → AI chat + commands + streaming + tool calling
AiV2AgentListActivity → 5 tabs: Presets/My Agents/Discover/Remote/Usage
SuperAdminActivity → admin panel with user sessions

Auth: JWT v2 only, AuthManager + BearerTokenInterceptor + AuthInterceptor
Session: SessionManager (ensureFreshToken + periodic refresh + isRefreshing guard)
Chat Stream: ChatV2 bidirectional — JWT + clientVersion + system signals + typing
Messages: v2 only — GetHistoryV2, SendMessageV2, EditMessageV2, DeleteMessageV2, SetReactionV2
Calls: WebRTC via CallManager + CallNavigator + CallActivity + CallActionService
Secret Chats: E2EE via E2EEManager (ECDH + AES-256-GCM)
Update: UpdateManager + UpdateCoordinator + downloaded_version tracking
```

---

## Правила

1. **НЕ компилировать Android на сервере** (OOM kill)
2. **НЕ деплоить на prod** без явного указания
3. UUID ALWAYS for routing, username ONLY for display
4. Все новые строки ОДНОВРЕМЕННО в EN + RU
5. getString() НЕ в полях Activity — только в методах
6. Все ошибки через `ErrorHandler.handle()`
7. v2 server only — никаких v1 fallbacks
8. Marshallers: всегда включать v2 proto поля
9. `ensureFreshToken()` перед gRPC вызовами
10. Перед коммитом: `./gradlew assembleDebug`
11. НЕ bump'ать версию — bump делает только пользователь
12. AI v2 RPC: все методы в `messenger.ChatService/*`
13. CHANGELOG: только код, не документация

---

## Сервер

| | Dev | Prod |
|--|-----|------|
| gRPC | 50052 | 50051 |
| HTTP | 8083 | 8082 |
| Сервис | lavender-server-dev | lavender-server |

**Деплой сервера:** НЕ делать.

---

## Полезные ссылки

- `doc/PATTERNS.md` — паттерны кода
- `doc/GOTCHAS.md` — known gotchas (400+ entries)
- `doc/INDEX.md` — project overview
- `doc/AI_V2_TESTING.md` — AI v2 testing
- `CHANGELOG.md` — version history
