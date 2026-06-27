# Prompt: Android Client — Next Session

**Версия:** v1.3.1.04 | **Ветка:** feat/1.3.1.x | **Дата:** 2026-06-28

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
        ├── GrpcChatAuxClient (~130) — users, FCM, mute
        ├── GrpcProfileClient — contacts, themes, devices, passwords (ChatService)
        ├── ProfileClient — profile, avatar, settings, delete (ProfileService v2, JWT)
        ├── GrpcDraftClient, GrpcFavoritesClient
        ├── GrpcMessageV2Client — messages v2 only (no v1 fallback)
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
AiV2ChatActivity → unified AI chat + commands + rate limit + image/file support + multi-agent + errors as chat messages
AiV2AgentListActivity → unified agent management (5 tabs: Presets/My Agents/Discover/Remote Agent/Usage)
  └── Tab 3 Remote Agent → RemoteAgentSettingsFragment (inline Gateway + Token)
  └── Tab 4 Usage → UsageStatsFragment (summary cards + per-agent list, auto-refresh 30s)
AiAgentSetupActivity → create/edit all agent types (API key, temperature, max tokens)
AIBottomSheet → agent selection with user agents only + loading/empty states + fixed footer

Auth: JWT only (v2), AuthManager + BearerTokenInterceptor + AuthInterceptor (HTTP)
Session: SessionManager (ensureFreshToken BEFORE loadChats + forceTokenRefresh on pull-to-refresh)
Chat Stream: ChatV2 bidirectional stream (messenger.ChatService/ChatV2) — JWT auth + clientVersion, system signals, typing
Messages: v2 only — GetHistoryV2, SendMessageV2, EditMessageV2, DeleteMessageV2, SetReactionV2
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
```

---

## Итог сессии v1.3.1.04

### Выполнено

**ChatV2 clientVersion (для админ-панели):**
1. `ChatV2MessageProto` добавлено поле `clientVersion` (field 3)
2. Маршаллеры: сериализация/dесериализация field 3
3. `RealGrpcClient`: первый ChatV2 сообщение отправляет `BuildConfig.VERSION_NAME`
4. Сервер обновляет `users.last_client_version` и `users.last_seen_at`

**Hermes Agent ACP (клиент):**
1. Emoji mapping "hermes" → "🔬" добавлен в 3 файла: AIBottomSheet, AiV2AgentListAdapter, AiV2ChatActivity
2. Промпт для серверной реализации: `/Users/paveld/LavenderMessenger-server/doc/PROMPT_HERMES_ACP.md`
3. Клиентский план: `doc/PROMPT_HERMES_ACP_CLIENT.md`

**Документация:**
1. Полное обновление `doc/INDEX.md` — актуальная статистика и архитектура
2. Обновление `doc/PATTERNS.md` — новые паттерны (clientVersion, AuthInterceptor)
3. Обновление `doc/GOTCHAS.md` — новые 발견ения
4. `doc/PROMPT_NEXT_SESSION.md` — обновлён для следующей сессии
5. `doc/MEMORY.md` — обновлена память проекта

### Изменённые файлы

| Файл | Изменение |
|------|-----------|
| `MessagesV2Proto.kt` | +`clientVersion` в `ChatV2MessageProto` |
| `MessagesV2Marshallers.kt` | +сериализация/dесериализация field 3 |
| `RealGrpcClient.kt` | +`BuildConfig.VERSION_NAME` в первом ChatV2 сообщении |
| `AIBottomSheet.kt` | +"hermes" → "🔬" |
| `AiV2AgentListAdapter.kt` | +"hermes" → "🔬" |
| `AiV2ChatActivity.kt` | +"hermes" → "🔬" |
| `doc/INDEX.md` | Полное обновление |
| `doc/PATTERNS.md` | +новые паттерны |
| `doc/GOTCHAS.md` | +новые discoveries |
| `doc/PROMPT_NEXT_SESSION.md` | Обновлён |
| `doc/PROMPT_HERMES_ACP_CLIENT.md` | NEW — клиентский план |
| `doc/MEMORY.md` | Обновлена память проекта |

---

## Бэклог — Следующая сессия (v1.3.1.x)

### Приоритет 1: Hermes Agent ACP (клиент готов, ждём сервер)
| Задача | Статус |
|--------|--------|
| Emoji mapping для hermes | ✅ |
| Серверная реализация ACP | ✅ (сделано серверным агентом) |
| Тестирование Hermes в продакшене | 🔲 |
| Добавить Hermes в AI v2 тесты | 🔲 |

### Приоритет 2: Reve Image
| Задача | Статус |
|--------|--------|
| Reve 402 обработка (красивый текст в чате) | ✅ |

### Приоритет 3: Тестирование
| Задача | Статус |
|--------|--------|
| Финальный прогон AI v2 тестов | 🔲 |
| Тестирование на prod сервере | 🔲 |

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
- Changelog: `CHANGELOG.md`
