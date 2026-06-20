# Prompt: Android Client — Next Session

**Версия:** v1.3.0.9 | **Ветка:** feat/1.3.0.x | **Дата:** 2026-06-20

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
        ├── GrpcDraftClient, GrpcFavoritesClient, GrpcMessageClient
        ├── GrpcServerDiscoveryClient — server discovery
        ├── GrpcAIv2Client — AI v2 (ChatWithAIV2, Agent CRUD, Tools, Marketplace)
        ├── SecretChatGrpc, ProfileClient
        ├── NotificationsGrpc — notifications (subscribe, history, read, unread)
        └── RemoteAgentGrpc — Remote Agent (list, deploy, tokens, process)

ChatListActivity → 10 modules (toolbar, tabs, FABs, auth, etc.)
NewChatActivity → 6 delegates + ChatViewModel
AiV2ChatActivity → unified AI chat (simple/agent/pipeline) + rate limit
AiV2AgentListActivity → 3 tabs (Presets/My Agents/Discover)
AiV2AgentCreateEditActivity → agent create/edit

Auth: JWT only (v2), AuthManager + BearerTokenInterceptor
Session: SessionManager (token refresh EVERY entry point)
AI v2: ChatWithAIV2 streaming + tool calling loop + 7 provider types
AI Marketplace: Rate, Reviews, Stats, Share, Install, Usage + Search + Pagination + Sort + Filter
Biometric: BiometricPrompt after splash screen when enabled
Chat List: Cursor-based pagination (infinite scroll), Unread highlight
Graceful Shutdown: SERVER_SHUTTINGDOWN + health check + backoff
```

---

## Итог сессии v1.3.0.9

### Выполнено

- **Биометрия** — BiometricPrompt после сплеш-экрана при включённой настройке в Security
- **Cursor pagination** — `GetChatsV2` с cursor-based пагинацией (`next_cursor`, `has_more`), `ChatListPage`, `loadMoreChats()`, infinite scroll в ChatListActivity
- **AI Agent List** — 5 табов → 3 (Presets/My Agents/Discover), Presets для быстрого доступа
- **Share** — добавлена ссылка `http://13.140.25.249` в текст "Поделиться"
- **Аудит сервера** — исправлены `searchChats` timestamp (seconds → milliseconds) и `getAllChats` isMuted (hardcoded false → proto.isMuted)

---

## Бэклог — Следующая сессия (v1.3.0.10)

### Приоритет 1: Серверные исправления
| Задача | Статус |
|--------|--------|
| Обновлять `user_chat_metadata.last_seen_at` при каждом сообщении через chat stream | 🔲 Серверный баг |
| Обновлять `user_chat_metadata.last_client_version` при подключении с новой версией | 🔲 Серверный баг |

### Приоритет 2: End-to-end тестирование AI v2
| Задача | Статус |
|--------|--------|
| Тест ChatWithAIV2 на реальном сервере (стриминг + tool calling) | 🔲 Нужен live-тест |
| Тест Agent CRUD (create/update/delete/clone) | 🔲 Нужен live-тест |
| Тест Marketplace API (каталог, rate, reviews, install, share) | 🔲 Нужен live-тест |
| Тест Rate Limit (10 req/min, countdown, auto-restore) | 🔲 Нужен live-тест |
| Тест Graceful Shutdown (SERVER_SHUTTINGDOWN + backoff) | 🔲 Нужен live-тест |

### Приоритет 3: UX улучшения
| Задача | Статус |
|--------|--------|
| Кэширование Marketplace в Room DB | 🔲 |
| Автообновление статистики Usage | 🔲 |
| Better error messages для AI v2 (показывать server error из response) | 🔲 |
| Fix 22 падающих unit-тестов (pre-existing: GrpcConnectionManager, GrpcMessageClient, GrpcUnaryCallHelper, AiV2Marshallers) | 🔲 |

### Приоритет 4: Новые фичи
| Задача | Статус |
|--------|--------|
| Уведомления о новых отзывах на агентов | 🔲 |
| Избранное в Marketplace (сохранять понравившихся агентов) | 🔲 |

---

## Правила (обязательно к прочтению)

1. **НЕ компилировать Android на сервере** (OOM kill) — assembleRelease ТОЛЬКО локально
2. **НЕ деплоить на prod** без явного указания
3. userId (UUID) — всегда как ключ, НЕ username
4. Все новые строки ОДНОВРЕМЕННО в `values/strings.xml` + `values-ru/strings.xml`
5. getString() НЕ в полях Activity — только в методах
6. Kotlin 2.3.21: `cont.resume(value, onCancellation = {})`
7. Все ошибки через `ErrorHandler.handle()` — НЕ `Log.e`
8. v2 server only — никаких v1 fallbacks
9. Chat toolbar: фиксированная высота `@dimen/custom_toolbar_height`, elevation 0dp
10. Все chat activities: `setDecorFitsSystemWindows(window, false)` в onCreate
11. Marshallers: всегда включать v2 proto поля, сверять field numbers с серверным proto
12. JWT freshness: `ensureFreshToken()` перед Chat stream
13. **Перед коммитом всегда запускать `./gradlew assembleDebug`**
14. **НЕ bump'ать версию — bump делает только пользователь**
15. **Marshallers field order:** server proto определяет field numbers. `chat_id` всегда field 1, `user_id` field 2
16. **AI v2 RPC:** все методы в `messenger.ChatService/*` (НЕ `AIService`)
17. **Unread count:** считается по `user_chat_metadata.last_read_at`, НЕ по `messages.is_read`
18. **ProfileService v2:** profile/avatar/delete/settings — через `messenger.ProfileService/*` (JWT context)

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

## Серверная документация

| Файл | Назначение |
|------|------------|
| `/Users/paveld/LavenderMessenger-server/doc/CLIENT_INTEGRATION.md` | Полный гайд интеграции клиента |
| `/Users/paveld/LavenderMessenger-server/doc/ANDROID_AI_BILLING_INTEGRATION.md` | UsageStats UI (реализовано) |
| `/Users/paveld/LavenderMessenger-server/doc/ANDROID_RATE_LIMIT_PROMPT.md` | Rate limit UI (реализовано) |

---

## Полезные ссылки

- Документация клиента: `doc/INDEX.md`, `doc/PATTERNS.md`
- Документация AI v2: `doc/AI_V2_TESTING.md`
- Changelog: `CHANGELOG.md`
