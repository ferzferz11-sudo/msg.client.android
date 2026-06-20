# Prompt: Android Client — Next Session

**Версия:** v1.3.0.1 (релиз) | **Ветка:** feat/1.3.0.x | **Дата:** 2026-06-20

---

## Быстрый старт

1. `doc/PATTERNS.md` — паттерны кода, правила, архитектура
2. `doc/PLAN.md` — текущий план и бэклог
3. Этот файл — контекст сессии

Проект: `/Users/paveld/LavenderMessenger-Android`
Сборка: `./gradlew assembleDebug` (запускать локально на Mac)

---

## Что сделано (v1.3.0.0 → v1.3.0.1)

### AI Marketplace UI

**Domain Layer:**
- `MarketplaceModels.kt` — 4 модели: MarketplaceAgent, AgentStats, AgentReview, UsageStat
- `AiV2DomainExtensions.kt` — 3 маппера: toMarketplaceAgent(), AgentReviewProto.toDomain(), UsageStatEntryProto.toDomain()
- `AiV2ChatUseCase.kt` — 7 Marketplace методов (listMarketplace, stats, reviews, rate, share, install, usage)
- `RateLimitCache.kt` — клиентский кэш лимитов запросов (sliding window, 10 req/min)

**ViewModel Layer:**
- `MarketplaceViewModel.kt` — каталог с пагинацией (loadAgents/loadMore/search)
- `AgentDetailViewModel.kt` — статистика, отзывы, rate/share/install
- `UsageStatsViewModel.kt` — статистика использования

**UI Layer:**
- `MarketplaceAgentAdapter.kt` — карточки агентов с рейтингом (RatingBar), install count, провайдер
- `AgentDetailActivity.kt` — экран деталей агента: статистика, отзывы, кнопки Rate/Share/Install
- `ReviewAdapter.kt` — список отзывов (user, rating, text, date)
- `RateAgentBottomSheet.kt` — оценка агента 1-5 звёзд + текстовый отзыв
- `InstallAgentBottomSheet.kt` — установка агента по share_code
- `UsageStatsAdapter.kt` — per-agent статистика (токены, запросы, период)

**Табы AiV2AgentListActivity:**
| # | Таб | Описание |
|---|-----|----------|
| 0 | Presets | Пресет-агенты |
| 1 | My Agents | Пользовательские |
| 2 | Public | Публичные |
| 3 | **Marketplace** | Каталог с поиском, pull-to-refresh, infinite scroll |
| 4 | **Usage** | Статистика (токены/запросы) |

**Фичи:**
- Поиск агентов — TextInputLayout с дебаунсом (2+ символов)
- Pull-to-refresh — SwipeRefreshLayout для обновления каталога
- Infinite scroll — автоматическая загрузка следующей страницы
- Deep link — `lavender://marketplace/install?code=xxx`
- Empty state — "No public agents available yet" / "Публичных агентов пока нет"
- Rate limit UI — блокировка input + countdown при превышении лимита

**Layouts (7 новых):**
- `item_marketplace_agent_card.xml`
- `activity_agent_detail.xml`
- `item_review.xml`
- `bottom_sheet_rate_agent.xml`
- `bottom_sheet_install_agent.xml`
- `fragment_usage_stats.xml`
- `item_usage_stat.xml`

**Strings (26 EN + 26 RU):**
- marketplace, marketplace_rate, marketplace_share, marketplace_install, marketplace_rate_agent, marketplace_install_agent, marketplace_enter_share_code, marketplace_write_review, marketplace_submit, marketplace_installs, marketplace_reviews, marketplace_agent_installed, marketplace_thanks_rating, marketplace_select_rating, marketplace_share_agent, marketplace_install_text, marketplace_empty, marketplace_usage, marketplace_tokens, marketplace_requests, marketplace_avg_request, marketplace_no_data, marketplace_no_data_desc, marketplace_search_hint, rate_limit_exceeded

**Tests (15 новых):**
- `MarketplaceModelsTest` (8) — data class defaults, values
- `MarketplaceMappersTest` (7) — Proto → Domain mapping, provider types

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
AiV2ChatActivity → unified AI chat (simple/agent/pipeline) + rate limit
AiV2AgentListActivity → 5 tabs (Presets/My/Public/Marketplace/Usage)
AiV2AgentCreateEditActivity → agent create/edit
AgentDetailActivity → agent detail (stats, reviews, rate/share/install)

Auth: JWT only (v2), AuthManager + BearerTokenInterceptor
Session: SessionManager (token refresh EVERY entry point)
AI v2: ChatWithAIV2 streaming + tool calling loop + 7 provider types
AI Marketplace: Rate, Reviews, Stats, Share, Install, Usage + Search + Pagination
Rate Limit: RateLimitCache + countdown + disable input
Graceful Shutdown: SERVER_SHUTTINGDOWN + health check + backoff
```

---

## Ключевые решения

| Решение | Обоснование |
|---------|-------------|
| 5 табов в AgentListActivity | Marketplace и Usage — отдельные табы для удобства навигации |
| SearchBar в табе Marketplace | API поддерживает query параметр для фильтрации |
| SwipeRefreshLayout | Стандартный Android паттерн для pull-to-refresh |
| Infinite scroll через OnScrollListener | Автоматическая пагинация при приближении к концу списка |
| Deep link lavender://marketplace/install | Удобная установка агентов по ссылке |
| RateLimitCache клиентский | Серверный rate limit, клиентский кэш только для UX |

---

## Бэклог — Следующая сессия (v1.3.0.2)

### Приоритет 1: Тестирование AI v2 с сервером
| Задача | Статус |
|--------|--------|
| Тестирование ChatWithAIV2 на реальном сервере | 🔲 |
| Тестирование Agent CRUD | 🔲 |
| Тестирование Tool Calling loop | 🔲 |
| Тестирование Marketplace API (каталог, отзывы, оценки) | 🔲 |
| Тестирование Graceful Shutdown | 🔲 |
| Тестирование Rate Limit | 🔲 |

### Приоритет 2: Тесты
| Задача | Статус |
|--------|--------|
| Unit-тесты AI v2 (models, marshallers, extensions) | ✅ Done (60 tests) |
| Unit-тесты Marketplace (models, mappers) | ✅ Done (15 tests) |
| Unit-тесты Marketplace marshallers | 🔲 |
| Unit-тесты для ChatViewModel | ✅ Done (v1.2.0.19) |
| Unit-тесты для ProfileViewModel | ✅ Done (v1.2.0.16) |
| Unit-тесты для SessionManager | ✅ Done (v1.2.0.16) |
| Интеграционные тесты AI v2 с сервером | 🔲 |

### Приоритет 3: UX улучшения
| Задача | Статус |
|--------|--------|
| Offline mode | ✅ Done (v1.2.0.16) |
| Push notification deep link | ✅ Done (v1.2.0.16) |
| Sheet navigation | ✅ Done (v1.2.0.19) |
| Graceful Shutdown UI | ✅ Done (v1.3.0.0) |
| Agent form dark theme | ✅ Done (v1.3.0.0) |
| Marketplace empty state | ✅ Done (v1.3.0.1) |
| Rate limit UI | ✅ Done (v1.3.0.1) |
| Loading skeletons для Marketplace | 🔲 |
| Кэширование Marketplace в Room DB | 🔲 |

### Приоритет 4: Новые фичи
| Задача | Статус |
|--------|--------|
| Уведомления о новых отзывах на агентов | 🔲 |
| Сортировка агентов в Marketplace (rating, installs, newest) | 🔲 |
| Фильтры в Marketplace (provider type, tools enabled) | 🔲 |
| Избранное в Marketplace (сохранять понравившихся агентов) | 🔲 |
| Автообновление статистики Usage | 🔲 |

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

## Серверная документация

| Файл | Назначение |
|------|------------|
| `/Users/paveld/LavenderMessenger-server/doc/CLIENT_INTEGRATION.md` | Полный гайд интеграции клиента |
| `/Users/paveld/LavenderMessenger-server/doc/ANDROID_AI_BILLING_INTEGRATION.md` | UsageStats UI (реализовано) |
| `/Users/paveld/LavenderMessenger-server/doc/ANDROID_RATE_LIMIT_PROMPT.md` | Rate limit UI (реализовано) |

---

## Полезные ссылки

- Документация клиента: `doc/INDEX.md`, `doc/PATTERNS.md`, `doc/PLAN.md`
- Документация AI v2: `doc/AI_V2_TESTING.md`
- Changelog: `CHANGELOG.md`
