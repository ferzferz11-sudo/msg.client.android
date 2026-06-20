# Prompt: Android Client — Next Session

**Версия:** v1.2.0.20 (релиз) | **Ветка:** feat/1.2.0.x | **Дата:** 2026-06-20

---

## Быстрый старт

1. `doc/PATTERNS.md` — паттерны кода, правила, архитектура
2. `doc/PLAN.md` — текущий план и бэклог
3. Этот файл — контекст сессии

Проект: `/Users/paveld/LavenderMessenger-Android`
Сборка: `./gradlew assembleDebug` (запускать локально на Mac)

---

## Что сделано (v1.2.0.19 → v1.2.0.20)

### AI Services v2 — единый API для всех AI чатов

**gRPC транспорт:**
- `GrpcAIv2Client.kt` — chatWithAIV2 streaming + Agent CRUD + Tools
- `GrpcAIv2Marshallers.kt` — все marshallers для v2 proto
- `AiV2Proto.kt` — все v2 proto data classes

**Domain layer:**
- `AiV2Models.kt` — AiV2Agent, AiV2ToolCall, AiV2StreamState, AiV2Tool, AiV2ChatMessage
- `AiV2DomainExtensions.kt` — proto → domain mapping
- `AiV2ChatUseCase.kt` — chat с tool calling loop (max 10 итераций)
- `AiV2ChatManager.kt` — unified SharedFlow/StateFlow

**UI:**
- `AiV2ChatActivity.kt` + ViewModel — единый AI чат для всех типов
- `AiV2AgentListActivity.kt` + ViewModel + Adapter — список агентов (tabs: Presets/My/Public)
- `AiV2AgentCreateEditActivity.kt` + ViewModel — создание/редактирование агентов

**Tests:**
- 60 unit-тестов: AiV2ModelsTest (20), AiV2DomainExtensionsTest (13), AiV2MarshallersTest (27)

**Cleanup v1:**
- Удалено: OwlChatUseCase, HermesChatUseCase, AiChatManager, AiModels, AiDomainExtensions, HermesRepository, HermesModel, AiChatGrpc
- Удалено: OwlChatActivity, OwlChatViewModel, OwlSettingsActivity, HermesChatActivity, HermesChatViewModel, HermesChatAdapter, HermesCommandAdapter, AgentListActivity, AgentListViewModel, AgentListAdapter, AgentSettingsActivity, AgentSettingsBottomSheet
- Удалено: 8 layout XML файлов, 3 directories

**Оставлено:**
- `OwlGrpc.kt` — утилиты уведомлений (subscribeNotifications, getNotificationHistory, markNotificationsRead, getUnreadCount)
- `HermesGrpc.kt` — Remote Agent (listRemoteAgents, deployAgentTask, generateAgentToken, etc.)
- Remote Agent UI (RemoteAgentActivity, RemoteAgentSettingsActivity, RemoteAgentService)

---

## Ключевые решения

| Решение | Обоснование |
|---------|-------------|
| Единый ChatWithAIV2 | Заменяет ChatWithOWL, ChatWithOrchestrator, ChatWithAI |
| Server executes tools | Клиент только отправляет tool_calls результат обратно |
| Single AiV2ChatActivity | Один экран для всех типов AI чатов (simple/agent/pipeline) |
| Remote Agent интегрирован в v2 | Remote Agent — тип провайдера в v2 agent system |
| Чистый старт | Без миграции OWL/Hermes чатов |

---

## Текущая архитектура

```
GrpcClient (facade)
  └── RealGrpcClient — orchestrator
        ├── GrpcConnectionManager — connect/reconnect/disconnect
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
        ├── GrpcAIv2Client — AI v2 (ChatWithAIV2, Agent CRUD, Tools)
        ├── SecretChatGrpc, ProfileClient
        └── OwlGrpc (notifications), HermesGrpc (remote agents)

ChatListActivity → 10 modules (toolbar, tabs, FABs, auth, etc.)
NewChatActivity → 6 delegates + ChatViewModel
AiV2ChatActivity → unified AI chat (simple/agent/pipeline)
AiV2AgentListActivity → agent list (tabs: Presets/My/Public)
AiV2AgentCreateEditActivity → agent create/edit

Auth: JWT only (v2), AuthManager + BearerTokenInterceptor
Session: SessionManager (token refresh EVERY entry point)
AI v2: ChatWithAIV2 streaming + tool calling loop + 7 provider types
```

---

## Бэклог — Следующая сессия (v1.2.0.21)

### Приоритет 1: AI v2 — интеграция с сервером
- Тестирование ChatWithAIV2 на реальном сервере
- Тестирование Agent CRUD (CreateAIAgent, ListAIAgents, etc.)
- Тестирование Tool Calling loop
- Настройка встроенных пресет-агентов на сервере

### Приоритет 2: Тесты
| Задача | Статус |
|--------|--------|
| Unit-тесты AI v2 (models, marshallers, extensions) | ✅ Done (60 tests) |
| Unit-тесты для ChatViewModel | ✅ Done (v1.2.0.19) |
| Unit-тесты для ProfileViewModel | ✅ Done (v1.2.0.16) |
| Unit-тесты для SessionManager | ✅ Done (v1.2.0.16) |
| Интеграционные тесты AI v2 с сервером | 🔲 |

### Приоритет 3: UX
| Задача | Статус |
|--------|--------|
| Offline mode | ✅ Done (v1.2.0.16) |
| Push notification deep link | ✅ Done (v1.2.0.16) |
| Sheet navigation | ✅ Done (v1.2.0.19) |

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
