# Prompt: Android Client — Next Session

**Версия:** v1.3.0.21 | **Ветка:** feat/1.3.0.x | **Дата:** 2026-06-27

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
        ├── RemoteAgentGrpc — Remote Agent (list, deploy, tokens, process)
        └── ChatKeepAliveService — foreground service, keep-alive connection

network/HttpClient.kt — singleton OkHttpClient (connection pool 5/5min, timeouts 30s)

ChatListActivity → 10 modules (toolbar, tabs, FABs, auth, etc.)
NewChatActivity → 6 delegates + ChatViewModel
AiV2ChatActivity → unified AI chat + commands + rate limit + image/file support + multi-agent + errors as chat messages
AiV2AgentListActivity → unified agent management (5 tabs: Presets/My Agents/Discover/Remote Agent/Usage)
  └── Tab 3 Remote Agent → RemoteAgentSettingsFragment (inline Gateway + Token)
  └── Tab 4 Usage → UsageStatsFragment (summary cards + per-agent list, auto-refresh 30s)
AiAgentSetupActivity → create/edit all agent types (API key, temperature, max tokens)
AIBottomSheet → agent selection with ImageView toggles + fixed footer + scrollable content

Auth: JWT only (v2), AuthManager + BearerTokenInterceptor
Session: SessionManager (ensureFreshToken BEFORE loadChats + forceTokenRefresh on pull-to-refresh)
AI v2: ChatWithAIV2 streaming + tool calling loop + 8 provider types + image/file support
AI Chat History: GetAIV2ChatHistory + ListAIV2Chats (server-side)
AI Chat Settings: per-session API key + model override
AI Chat Commands: /new, /clear, /history, /settings, /model, /system, /tools
AI Chats in Chat List: AI chats merged into main chat list via ListAIV2Chats
AI Errors: shown as agent chat bubbles (⚠️ prefix), not Toast
AI Marketplace: cache in Room DB (marketplace_agents table), Favorites (SharedPreferences)
Biometric: BiometricPrompt after splash screen when enabled (error → continue, not crash)
Chat List: Cursor-based pagination (infinite scroll), Unread highlight
Graceful Shutdown: SERVER_SHUTTINGDOWN + health check + backoff
Notifications in Remote Agent: server notifications shown as system messages in chat
```

---

## Итог сессии v1.3.0.22

### Выполнено

**Provider Config fix — API key отображается корректно:**
1. Проблема: для пресетов (Reve и др.) `provider_config` = `{"api_key_source": "server", ...}` — нет `api_key` в JSON
2. `AiAgentSetupActivity` теперь проверяет `api_key_source: "server"` → показывает "Server key" как placeholder
3. Для пользовательских агентов с ключом — показывает замаскированный ключ
4. `AiV2AgentCreateEditViewModel.loadAgent()` — fallback на `ai_chat_settings` работает для всех агентов без пользовательского ключа

**AIBottomSheet — убраны пресеты:**
1. Шторка ИИ показывает только собственные агенты пользователя
2. Пресеты доступны в настройках агентов (AiV2AgentListActivity)
3. Добавлены loading/empty состояния для шторки
4. `listAgents(includePublic = false)` — загружает только пользовательских агентов

### Изменённые файлы

| Файл | Изменение |
|------|-----------|
| `ui/ai/AiAgentSetupActivity.kt` | providerConfig: проверка `api_key_source`, placeholder "Server key", маскировка ключа |
| `ui/ai/AiV2AgentCreateEditViewModel.kt` | loadAgent(): fallback на ai_chat_settings для всех агентов без user key |
| `ui/widget/AIBottomSheet.kt` | Убраны пресеты, только пользовательские агенты, loading/empty состояния |

---

## Итог сессии v1.3.0.21

### Выполнено

**Provider Config (сервер + клиент):**
1. Сервер: `string provider_config = 22` в `AgentInfoV2` proto
2. Сервер: `agentToProto()` маршалит `ProviderConfig` в JSON
3. Клиент: `providerConfig` добавлен в `AgentInfoV2Proto` (field 22)
4. Клиент: `parseAgentInfoV2()` парсит field 22
5. Клиент: `toDomain()` маппит `providerConfig` → `AiV2Agent.providerConfig`
6. Клиент: `AiAgentSetupActivity` предзаполняет API key (из providerConfig + fallback из ai_chat_settings)

**Usage Stats — автообновление:**
1. `UsageStatsFragment` — новый фрагмент с summary cards + RecyclerView
2. 5-й таб "Статистика" в `AiV2AgentListActivity`
3. Автообновление каждые 30 секунд при активном экране
4. Summary: Total Tokens, Total Requests, Avg/Request
5. Полная локализация (EN + RU)

**File Attachments для AI агентов:**
1. Диалог "Прикрепить" теперь: Gallery / Camera / **File**
2. HTTP upload файла через `upload-file` endpoint
3. URL файла вставляется в сообщение: `"File: name\nhttps://..."`
4. AI агент получает URL и может обработать файл

**Marketplace Cache:**
1. `marketplace_agents` table в Room DB (version 11 migration)
2. Fallback на кэш при ошибке сети
3. Автообновление при успешном запросе

**AI Chat Settings Toolbar:**
1. Subtitle показывает статус API key: masked key / "Server key" / "No key"

**Favorites в Marketplace:**
1. `FavoriteAgentsManager` — хранит избранные агенты в SharedPreferences
2. ⭐ кнопка на каждой карточке агента в Marketplace
3. Фильтр "Favorites" (chip) — показывает только избранных
4. Иконки: `ic_star_outline.xml` / `ic_star_filled.xml`

**Image sending fix:**
1. Байты изображения теперь читаются в Activity через contentResolver (не через RealGrpcClient.appContext)
2. Прогресс-бар показывается при отправке изображения

**Navigation fix:**
1. При выходе из управления агентами / настроек / удалённых агентов — AI шторка переоткрывается автоматически

### Изменённые файлы

| Файл | Изменение |
|------|-----------|
| `data/proto/AiV2Proto.kt` | `providerConfig` в `AgentInfoV2Proto` |
| `data/grpc/GrpcAIv2Marshallers.kt` | field 22 парсинг в `parseAgentInfoV2()` |
| `data/ai/AiV2DomainExtensions.kt` | `providerConfig` в `toDomain()` |
| `data/ai/AiV2ChatUseCase.kt` | Marketplace cache fallback |
| `data/ai/FavoriteAgentsManager.kt` | NEW — Favorites storage (SharedPreferences) |
| `data/db/AppDatabase.kt` | version 11, `MarketplaceAgentEntity` |
| `data/db/Entities.kt` | `MarketplaceAgentEntity` + конвертеры |
| `data/db/Daos.kt` | `MarketplaceDao` |
| `ui/ai/AiV2ChatActivity.kt` | File picker + upload + image fix |
| `ui/ai/AiV2ChatViewModel.kt` | `images` parameter |
| `ui/ai/AiV2AgentListActivity.kt` | 5-й таб Usage + Favorites filter |
| `ui/ai/AiV2AgentListAdapter.kt` | Favorites star button |
| `ui/ai/AiV2AgentCreateEditViewModel.kt` | API key fallback из ai_chat_settings |
| `ui/ai/MarketplaceViewModel.kt` | Favorites filter |
| `ui/ai/UsageStatsFragment.kt` | NEW — Usage stats UI (полная локализация) |
| `ui/ai/UsageStatsAdapter.kt` | Локализованные строки |
| `ui/ai/AiChatSettingsActivity.kt` | Toolbar subtitle |
| `ui/chatlist/ChatListActivity.kt` | shouldReopenAIBottomSheet flag |
| `ui/chatlist/ChatListFABs.kt` | shouldReopenAIBottomSheet при навигации |
| `res/layout/activity_ai_v2_agent_list.xml` | Usage tab + Favorites chip |
| `res/layout/item_ai_v2_agent_card.xml` | Favorite star button |
| `res/layout/fragment_usage_stats.xml` | String resources вместо хардкода |
| `res/drawable/ic_star_outline.xml` | NEW — outline star |
| `res/drawable/ic_star_filled.xml` | NEW — filled star |
| `res/values/strings.xml` | 14 новых строк |
| `res/values-ru/strings.xml` | 14 новых строк |

---

## Бэклог — Следующая сессия (v1.3.0.23+)

### Приоритет 1: Статусы агентов в чате
| Задача | Статус |
|--------|--------|
| Добавить статусы агентов: доступен / требует настройки / нет ключа | 🔲 |
| Показывать статус в toolbar чата | 🔲 |
| Показывать статус в AI шторке (AIBottomSheet) | 🔲 |
| Цветовые индикаторы (зелёный/жёлтый/красный) | 🔲 |

### Приоритет 2: Остальное
| Задача | Статус |
|--------|--------|
| Уведомления о новых отзывах на агентов | 🔲 |
| Финальный прогон AI v2 тестов | 🔲 |

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
19. **CHANGELOG:** не включать документационные изменения (README, doc/, комментарии) — только код
20. **AI ошибки:** показывать как chat bubble (⚠️ + текст), НЕ Toast
21. **AgentInfoV2 proto field 22:** `provider_config` — JSON string. Для пресетов: `{"api_key_source": "server", "default_model": "..."}` — нет `api_key`. Для user-агентов: `{"api_key": "sk-...", ...}`
22. **ВСЕГДА сверять с сервером:** перед любым gRPC/marshaller изменением проверять `/Users/paveld/LavenderMessenger-server/doc/CLIENT_INTEGRATION.md` И актуальный код сервера `/Users/paveld/LavenderMessenger-server/`

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
- Серверный промпт (Provider Config): `/Users/paveld/LavenderMessenger-server/doc/PROMPT_PROVIDER_CONFIG.md`
- Changelog: `CHANGELOG.md`
