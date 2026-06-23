# Prompt: Android Client — Next Session

**Версия:** v1.3.0.19 | **Ветка:** feat/1.3.0.x | **Дата:** 2026-06-23

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
AiV2ChatActivity → unified AI chat (simple/agent/pipeline) + rate limit + image support + multi-agent
AiV2AgentListActivity → unified agent management (4 tabs: Presets/My Agents/Discover/Remote Agent)
  └── Tab 3 Remote Agent → RemoteAgentSettingsFragment (inline Gateway + Token)
AiAgentSetupActivity → create/edit all agent types
AIBottomSheet → agent selection with checkboxes + AI Agents button

Auth: JWT only (v2), AuthManager + BearerTokenInterceptor
Session: SessionManager (ensureFreshToken BEFORE loadChats)
AI v2: ChatWithAIV2 streaming + tool calling loop + 8 provider types + image support
AI Chat History: GetAIV2ChatHistory + ListAIV2Chats (server-side)
AI Chat Settings: per-session API key + model override
AI Chats in Chat List: AI chats merged into main chat list via ListAIV2Chats
Biometric: BiometricPrompt after splash screen when enabled (error → continue, not crash)
Chat List: Cursor-based pagination (infinite scroll), Unread highlight
Graceful Shutdown: SERVER_SHUTTINGDOWN + health check + backoff
Notifications in Remote Agent: server notifications shown as system messages in chat
```

---

## Итог сессии v1.3.0.19

### Выполнено

**Системные уведомления — вибрация и экран:**
1. Создан новый канал `lavender_messages_v2` с `IMPORTANCE_HIGH` (старый канал нельзя изменить)
2. Удалён старый канал `lavender_messages` с неправильными настройками
3. Вибрация `[0, 300, 200, 300]` + включение экрана (`setFullScreenIntent`)
4. `setDefaults(DEFAULT_VIBRATE or DEFAULT_SOUND)` для fallback

**AI Пресеты — добавление в мои агенты:**
1. Тап по пресету клонирует его в "Мои агенты" через `cloneAgent`
2. Долгий тап по пресету открывает настройки агента
3. Шторка ИИ чатов показывает "Мои агенты" с чекбоксами для быстрого начала чата
4. Мульти-агентный чат через чекбоксы в шторке

**Список чатов — мгновенное обновление:**
1. Чат с новым сообщением сразу перемещается наверх списка
2. Если чата нет в списке — автоматическая `loadChats(silent=true)`
3. Unread badge обновляется в реальном времени

### Изменённые файлы

| Файл | Изменение |
|------|-----------|
| `data/fcm/LavenderMessagingService.kt` | Новый канал `lavender_messages_v2`, удаление старого, вибрация, setFullScreenIntent |
| `ui/ai/AiV2AgentListAdapter.kt` | `onItemLongClick` + `onAddClick`, динамический tint для кнопок |
| `ui/ai/AiV2AgentListActivity.kt` | Tap → clone, long press → settings, `clonePresetAgent()` |
| `ui/widget/AIBottomSheet.kt` | Секция "Мои агенты" с чекбоксами для quick chat |
| `ui/chatlist/ChatListViewModel.kt` | Перемещение чата наверх при новом сообщении, auto-reload |
| `res/values/strings.xml` | `ai_my_agents`, `ai_preset_added` |
| `res/values-ru/strings.xml` | `ai_my_agents`, `ai_preset_added` |
| `version.txt` | 1.3.0.18 → 1.3.0.19 |

---

## Бэклог — Следующая сессия (v1.3.0.20+)

### Приоритет 1: Серверная интеграция
| Задача | Статус |
|--------|--------|
| Серверный фикс ListAIAgents (empty UUID) | ✅ (промпт написан, ожидаем деплой) |
| Финальный прогон AI v2 тестов | 🔲 |
| Better error messages для AI v2 | ✅ (timestamp parsing fixed) |

### Приоритет 2: UX улучшения
| Задача | Статус |
|--------|--------|
| UI для AI Chat Settings (API key, model) | ✅ AiChatSettingsActivity + menu |
| Кэширование Marketplace в Room DB | 🔲 |
| Автообновление статистики Usage | 🔲 |

### Приоритет 3: Новые фичи
| Задача | Статус |
|--------|--------|
| Уведомления о новых отзывах на агентов | 🔲 |
| Избранное в Marketplace | 🔲 |

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
- Серверный промпт (ListAIAgents fix): `/Users/paveld/LavenderMessenger-server/doc/PROMPT_LISTAIAGENTS_FIX.md`
- Changelog: `CHANGELOG.md`
