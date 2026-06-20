# Prompt: Android Client — Next Session

**Версия:** v1.3.0.0 (релиз) | **Ветка:** feat/1.3.0.x | **Дата:** 2026-06-20

---

## Быстрый старт

1. `doc/PATTERNS.md` — паттерны кода, правила, архитектура
2. `doc/PLAN.md` — текущий план и бэклог
3. Этот файл — контекст сессии

Проект: `/Users/paveld/LavenderMessenger-Android`
Сборка: `./gradlew assembleDebug` (запускать локально на Mac)

---

## Что сделано (v1.2.0.20 → v1.3.0.0)

### AI Marketplace API

**gRPC методы (7 шт):**
- `RateAIAgent` — оценка агента (1-5 + отзыв)
- `GetAIAgentReviews` — отзывы на агента
- `ListMarketplaceAgents` — каталог публичных агентов с поиском
- `GetAIAgentStats` — статистика агента (установки, рейтинг)
- `ShareAIAgent` — генерация share_code
- `InstallAIAgent` — установка по share_code
- `GetAIUsageStats` — статистика использования (токены, запросы)

**Файлы:**
- `AiV2Proto.kt` — 15 новых proto классов
- `GrpcAIv2Marshallers.kt` — 14 новых marshallers
- `GrpcAIv2Client.kt` — 7 новых методов
- `GrpcClient.kt` — 7 facade методов

### Graceful Shutdown + Reconnection

- `SERVER_SHUTTINGDOWN` сигнал в Chat стриме → `_serverShuttingDown` StateFlow
- Health check (`GET /health`) перед каждым реконнектом
- Экспоненциальный backoff при недоступности сервера
- "Server restarting…" индикатор в toolbar
- `NotificationsGrpc.kt` — уведомления вынесены из OwlGrpc.kt
- `RemoteAgentGrpc.kt` — Remote Agent вынесен из HermesGrpc.kt

### v1 AI Cleanup

- Удалены `OwlGrpc.kt`, `HermesGrpc.kt` (~4000 LOC)
- ~20 v1 proto классов удалены из `MessengerProto.kt`
- Удалены v1 AI строки, стейл комментарии, неиспользуемые цвета/IDs
- Сломанный `OwlActivity` удалён из `AndroidManifest.xml`

### UI Fixes

- AI BottomSheet: dragHandle + заголовок "AI Services (in development)"
- LavenderFab в списке агентов (отступы от навбара)
- Аватар в toolbar: 42dp → 48dp
- Табы: контрастное контрастирование на тёмных темах
- Форма агента: surface фон, темизация полей ввода, Save кнопка
- Login: убран прелоадер на кнопке, локализованная ошибка
- Presets таб: `includePublic = true` для серверных пресетов

---

## Ключевые решения

| Решение | Обоснование |
|---------|-------------|
| NotificationsGrpc + RemoteAgentGrpc | Разделение OwlGrpc/HermesGrpc на domain-специфичные файлы |
| LavenderFab для agent list | Автоматические отступы от system bars |
| includePublic=true для Presets | Серверные пресеты доступны только через includePublic |
| surfaceColor для форм | Контрастный фон на тёмных темах |
| textPrimary для табов | Лучшая видимость чем colorOnSurface на тёмных темах |

---

## Текущая архитектура

```
GrpcClient (facade)
  └── RealGrpcClient — orchestrator
        ├── GrpcConnectionManager — connect/reconnect/health check
        ├── GrpcAuthClient — JWT auth (v2 only)
        ├── GrpcTypingClient — typing stream
        ├── GrpcCallClient — calls
        ├── GrpcChatClient (~250) — getChats, create/delete, participants
        ├── GrpcChatListV2Client (~120) — pin/unpin, search, archive
        ├── GrpcChatAuxClient (~130) — users, FCM, mute
        ├── GrpcChatListClient (~255) — chat list version, create/delete
        ├── GrpcProfileClient — profile, avatar, contacts, themes
        ├── GrpcDraftClient, GrpcFavoritesClient, GrpcMessageClient
        ├── GrpcServerDiscoveryClient — server discovery
        ├── GrpcAIv2Client — AI v2 (ChatWithAIV2, Agent CRUD, Tools, Marketplace)
        ├── SecretChatGrpc, ProfileClient
        ├── NotificationsGrpc — notifications (subscribe, history, read, unread)
        └── RemoteAgentGrpc — Remote Agent (list, deploy, tokens, process)

ChatListActivity → 10 modules (toolbar, tabs, FABs, auth, etc.)
NewChatActivity → 6 delegates + ChatViewModel
AiV2ChatActivity → unified AI chat (simple/agent/pipeline)
AiV2AgentListActivity → agent list (tabs: Presets/My/Public)
AiV2AgentCreateEditActivity → agent create/edit

Auth: JWT only (v2), AuthManager + BearerTokenInterceptor
Session: SessionManager (token refresh EVERY entry point)
AI v2: ChatWithAIV2 streaming + tool calling loop + 7 provider types
AI Marketplace: Rate, Reviews, Stats, Share, Install, Usage
Graceful Shutdown: SERVER_SHUTTINGDOWN + health check + backoff
```

---

## Бэклог — Следующая сессия (v1.3.0.1)

### Приоритет 1: Marketplace UI
| Задача | Статус |
|--------|--------|
| Marketplace экран (каталог публичных агентов) | 🔲 |
| Экран отзывов на агента | 🔲 |
| Шеринг агента (generate share_code) | 🔲 |
| Установка по share_code | 🔲 |
| Статистика использования (токены, запросы) | 🔲 |
| Оценка агента (1-5 звёзд + отзыв) | 🔲 |

### Приоритет 2: AI v2 — интеграция с сервером
| Задача | Статус |
|--------|--------|
| Тестирование ChatWithAIV2 на реальном сервере | 🔲 |
| Тестирование Agent CRUD | 🔲 |
| Тестирование Tool Calling loop | 🔲 |
| Тестирование Marketplace API | 🔲 |
| Тестирование Graceful Shutdown | 🔲 |

### Приоритет 3: Тесты
| Задача | Статус |
|--------|--------|
| Unit-тесты AI v2 (models, marshallers, extensions) | ✅ Done (60 tests) |
| Unit-тесты Marketplace marshallers | 🔲 |
| Unit-тесты для ChatViewModel | ✅ Done (v1.2.0.19) |
| Unit-тесты для ProfileViewModel | ✅ Done (v1.2.0.16) |
| Unit-тесты для SessionManager | ✅ Done (v1.2.0.16) |
| Интеграционные тесты AI v2 с сервером | 🔲 |

### Приоритет 4: UX
| Задача | Статус |
|--------|--------|
| Offline mode | ✅ Done (v1.2.0.16) |
| Push notification deep link | ✅ Done (v1.2.0.16) |
| Sheet navigation | ✅ Done (v1.2.0.19) |
| Graceful Shutdown UI | ✅ Done (v1.3.0.0) |
| Agent form dark theme | ✅ Done (v1.3.0.0) |

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
11. Marshallers: всегда включать v2 proto поля
12. JWT freshness: `ensureFreshToken()` перед Chat stream
13. **Перед коммитом всегда запускать `./gradlew assembleDebug`**
14. **НЕ bump'ать версию — bump делает только пользователь**
15. **Marshallers field order:** server proto определяет field numbers. `chat_id` всегда field 1, `user_id` field 2

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

- Документация клиента: `doc/INDEX.md`, `doc/PATTERNS.md`, `doc/PLAN.md`
- Документация AI v2: `doc/AI_V2_CLIENT_PLAN.md`, `doc/AI_V2_TESTING.md`
- Документация сервера: `/Users/paveld/LavenderMessenger-server/doc/AI_V2_CLIENT_INTEGRATION.md`
- Changelog: `CHANGELOG.md`
