# Prompt: Android Client — Next Session

**Версия:** v1.3.0.20 | **Ветка:** feat/1.3.0.x | **Дата:** 2026-06-27

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
AiV2ChatActivity → unified AI chat + commands + rate limit + image support + multi-agent + errors as chat messages
AiV2AgentListActivity → unified agent management (4 tabs: Presets/My Agents/Discover/Remote Agent)
  └── Tab 3 Remote Agent → RemoteAgentSettingsFragment (inline Gateway + Token)
AiAgentSetupActivity → create/edit all agent types (API key, temperature, max tokens)
AIBottomSheet → agent selection with ImageView toggles + fixed footer + scrollable content

Auth: JWT only (v2), AuthManager + BearerTokenInterceptor
Session: SessionManager (ensureFreshToken BEFORE loadChats + forceTokenRefresh on pull-to-refresh)
AI v2: ChatWithAIV2 streaming + tool calling loop + 8 provider types + image support
AI Chat History: GetAIV2ChatHistory + ListAIV2Chats (server-side)
AI Chat Settings: per-session API key + model override
AI Chat Commands: /new, /clear, /history, /settings, /model, /system, /tools
AI Chats in Chat List: AI chats merged into main chat list via ListAIV2Chats
AI Errors: shown as agent chat bubbles (⚠️ prefix), not Toast
Biometric: BiometricPrompt after splash screen when enabled (error → continue, not crash)
Chat List: Cursor-based pagination (infinite scroll), Unread highlight
Graceful Shutdown: SERVER_SHUTTINGDOWN + health check + backoff
Notifications in Remote Agent: server notifications shown as system messages in chat
```

---

## Итог сессии v1.3.0.20

### Выполнено

**AI Agent Setup — переработана форма:**
1. Поле "API Key" (textPassword) вместо JSON "Provider Config"
2. Слайдер "Temperature" (0–2, шаг 0.1, default 0.7)
3. Поле "Max Tokens" (number, default 4096)
4. Кнопка "Сохранить" — floating overlay над клавиатурой, появляется только при изменениях
5. Toolbar с заголовком "Создать агента" / "Редактировать агента"

**AI Chat — команды и ввод:**
1. Кнопка `/` → CommandBottomSheet с 7 командами
2. `/new` — очищает чат, `/settings` — открывает настройки
3. Кнопки send/attach работают (TextWatcher переключает)
4. Ошибки сервера отображаются как bubble агента (⚠️ + текст)

**AI Bottom Sheet — переработка:**
1. CheckBox заменён на ImageView-toggle (фиксированный 22dp)
2. Тап по строке переключает выбор
3. Долгий тап — настройки агента
4. "Создать своего агента" перемещён ниже "Управление агентами"
5. Кнопка "Начать чат" в fixed footer, ScrollView для списка

**Pull-to-refresh hardened:**
1. `forceTokenRefresh()` перед загрузкой
2. Авто-реконнект gRPC если не READY (wait до 5 сек)

**Исправления:**
1. AiV2AgentListActivity — toolbar показывает "ИИ Агенты"
2. AiAgentSetupActivity — save button не перекрывается навигацией
3. Чекбокс не залазит на текст (ImageView + marginEnd)

### Изменённые файлы

| Файл | Изменение |
|------|-----------|
| `ui/ai/AiAgentSetupActivity.kt` | Новая форма, floating save button, change tracking |
| `ui/ai/AiV2AgentCreateEditViewModel.kt` | temperature/maxTokens параметры |
| `ui/ai/AiV2ChatActivity.kt` | Команды, send/attach, ошибки в чат |
| `ui/ai/AiV2ChatViewModel.kt` | Ошибки как сообщения, rateLimitEvent |
| `ui/widget/AIBottomSheet.kt` | ImageView toggle, tap select, reordered, fixed footer |
| `ui/chatlist/ChatListViewModel.kt` | refreshChats с forceTokenRefresh + reconnect |
| `ui/chatlist/ChatListFABs.kt` | onOpenAgentSettings callback |
| `data/ai/AiV2Models.kt` | error в AiV2ChatMessage, providerConfig в AiV2Agent |
| `data/grpc/RealGrpcClient.kt` | Убран error message при реконнекте |
| `theme/ui/ThemeApplier.kt` | Обновлены ID полей формы |
| `res/layout/activity_ai_agent_setup.xml` | Новые поля, toolbar, floating save |
| `res/layout/widget_ai_bottom_sheet.xml` | ScrollView + footerContainer |
| `res/layout/widget_action_item.xml` | Уменьшен padding |
| `res/drawable/ic_check_box_outline.xml` | NEW — outline для toggle |
| `res/values/strings.xml` | 13 новых строк |
| `res/values-ru/strings.xml` | 13 новых строк |

---

## Бэклог — Следующая сессия (v1.3.0.21+)

### Приоритет 1: Серверная интеграция
| Задача | Статус |
|--------|--------|
| Серверный `AgentInfoV2.provider_config` (field 22) | 🔲 нужен серверный промпт |
| Финальный прогон AI v2 тестов | 🔲 |

### Приоритет 2: UX улучшения
| Задача | Статус |
|--------|--------|
| Кэширование Marketplace в Room DB | 🔲 |
| Автообновление статистики Usage | 🔲 |
| AI Chat Settings — показ текущего API key в toolbar | 🔲 |

### Приоритет 3: Новые фичи
| Задача | Статус |
|--------|--------|
| Уведомления о новых отзывах на агентов | 🔲 |
| Избранное в Marketplace | 🔲 |
| File attachments для AI агентов | 🔲 |

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
21. **AgentInfoV2 proto:** не содержит `provider_config` — ключ не возвращается при Get/List

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
