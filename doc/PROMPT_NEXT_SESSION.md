# Prompt: Android Client — Next Session

**Версия:** v1.3.0.15 | **Ветка:** feat/1.3.0.x | **Дата:** 2026-06-22

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
        ├── GrpcAIv2Client — AI v2 (ChatWithAIV2, Agent CRUD, Tools, Marketplace, Chat History)
        ├── SecretChatGrpc, ProfileClient
        ├── NotificationsGrpc — notifications (subscribe, history, read, unread)
        └── RemoteAgentGrpc — Remote Agent (list, deploy, tokens, process)

ChatListActivity → 10 modules (toolbar, tabs, FABs, auth, etc.)
NewChatActivity → 6 delegates + ChatViewModel
AiV2ChatActivity → unified AI chat (simple/agent/pipeline) + rate limit + image support + multi-agent
AIBottomSheet → agent selection with checkboxes → create AI chat
AiAgentSetupActivity → create/edit all agent types

Auth: JWT only (v2), AuthManager + BearerTokenInterceptor
Session: SessionManager (token refresh EVERY entry point)
AI v2: ChatWithAIV2 streaming + tool calling loop + 8 provider types + image support
AI Marketplace: Rate, Reviews, Stats, Share, Install, Usage + Search + Pagination + Sort + Filter
AI Chat History: GetAIV2ChatHistory + ListAIV2Chats (server-side)
AI Chat Settings: per-session API key + model override
Biometric: BiometricPrompt after splash screen when enabled
Chat List: Cursor-based pagination (infinite scroll), Unread highlight
Graceful Shutdown: SERVER_SHUTTINGDOWN + health check + backoff
Reve Image: image generation via Reve API (image_url in ChatWithAIV2Response)
Version Catalog: all dependencies in gradle/libs.versions.toml
```

---

## Итог сессии v1.3.0.15

### Выполнено

**AI Services — полная переработка:**
1. **AIBottomSheet redesigned** — новый порядок: пресеты с чекбоксами → создать чат → удалённые агенты → уведомления (внизу)
2. **AiAgentSetupActivity** — единый экран создания/редактирования агентов всех типов
3. **AiV2ChatActivity** — мультиагентные чаты + кнопка скрепки (галерея/камера)
4. **Серверная интеграция** — GetAIV2ChatHistory + ListAIV2Chats RPC с marshaller'ами
5. **Proto марshallеры** — новые типы AIV2ChatMessage, AIV2ChatInfo, запросы/ответы

**Серверные доработки (AI_MULTI_AGENT_PROMPT.md):**
- StreamFn теперь передаёт agent_id/agent_name в каждом токене
- GetAIV2ChatHistory — загрузка истории сообщений AI чата
- ListAIV2Chats — список всех AI чатов пользователя
- Proto перегенерирован

### Изменённые файлы (клиент)

| Файл | Изменение |
|------|-----------|
| `AIBottomSheet.kt` | Полная переработка: чекбоксы агентов, create chat flow |
| `AiV2ChatActivity.kt` | Multi-agent support + image picker |
| `AiV2ChatViewModel.kt` | imageUri + loadHistory() |
| `AiV2ChatUseCase.kt` | imageUri support + getChatHistory() + listAIChats() |
| `AiAgentSetupActivity.kt` | NEW — unified agent setup |
| `activity_ai_agent_setup.xml` | NEW — agent setup layout |
| `item_ai_agent_selectable.xml` | NEW — agent checkbox item |
| `ChatListFABs.kt` | New callbacks for AIBottomSheet |
| `RealGrpcClient.kt` | appContext → internal |
| `AiV2Proto.kt` | New proto types for history/list |
| `GrpcAIv2Marshallers.kt` | Marshallers for GetAIV2ChatHistory + ListAIV2Chats |
| `GrpcAIv2Client.kt` | getAIV2ChatHistory() + listAIV2Chats() |
| `AndroidManifest.xml` | AiAgentSetupActivity added, old activities removed |
| `strings.xml` (EN + RU) | New strings for AI integration |

---

## Бэклог — Следующая сессия (v1.3.0.16+)

### Приоритет 1: Тестирование AI v2 интеграции
| Задача | Статус |
|--------|--------|
| Тест GetAIV2ChatHistory на реальном сервере | 🔲 Нужен live-тест |
| Тест ListAIV2Chats на реальном сервере | 🔲 Нужен live-тест |
| Тест мультиагентных чатов (клиентская маршрутизация) | 🔲 Нужен live-тест |
| Тест отправки изображений в AI чат | 🔲 Нужен live-тест |
| Тест AiAgentSetupActivity (создание/редактирование агентов) | 🔲 Нужен live-тест |

### Приоритет 2: Серверные исправления
| Задача | Статус |
|--------|--------|
| Обновлять `user_chat_metadata.last_seen_at` при каждом сообщении через chat stream | 🔲 Серверный баг |
| Обновлять `user_chat_metadata.last_client_version` при подключении с новой версией | 🔲 Серверный баг |

### Приоритет 3: UX улучшения
| Задача | Статус |
|--------|--------|
| Кэширование Marketplace в Room DB | 🔲 |
| Автообновление статистики Usage | 🔲 |
| Better error messages для AI v2 (показывать server error из response) | 🔲 |
| UI для AI Chat Settings (API key input, model selector в AiV2ChatActivity) | 🔲 |
| Удаление старых файлов: AiV2AgentListActivity.kt, AgentDetailActivity.kt | 🔲 |

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
6. Kotlin 2.4.0: `cont.resume(value, onCancellation = {})`
7. Все ошибки через `ErrorHandler.handle()` — НЕ `Log.e`
8. v2 server only — никаких v1 fallbacks
9. Chat toolbar: фиксированная высота `@dimen/custom_toolbar_height`, elevation 0dp
10. Все chat activities: `setDecorFitsSystemWindows(window, false)` в onCreate
11. Marshallers: всегда включать v2 proto поля, сверять field numbers с серверным proto
12. JWT freshness: `ensureFreshToken()` перед Chat stream
13. **Перед коммитом всегда запускать `./gradlew assembleDebug`**
14. **НЕ bump'ать версию — bump делает только пользователь**
15. **Marshallers field order:** server proto определяет field numbers
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

## Полезные ссылки

- Документация клиента: `doc/INDEX.md`, `doc/PATTERNS.md`
- Документация AI v2: `doc/AI_V2_TESTING.md`
- Серверный промпт: `/Users/paveld/LavenderMessenger-server/doc/AI_MULTI_AGENT_PROMPT.md`
- Changelog: `CHANGELOG.md`
