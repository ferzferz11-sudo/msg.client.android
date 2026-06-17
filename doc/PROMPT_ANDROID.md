# Промпт для новой сессии — Android v1.1.3.30+

**Дата:** 2026-06-17
**Версия:** 1.1.3.29 (разработка)
**Ветка:** feat/1.1.3.x

---

## СТАТУС: v1.1.3.29 — UI улучшения завершены

**Ключевые изменения:**
- NewChatBottomSheet — 7 пунктов меню (Add Contact, Start Chat, Group, Secret Chat, Conference, Hermes AI, OWL AI)
- Favorites — добавлен в табы (All/AI/Groups/Favorites)
- Тулбар и Activity — полная адаптация к кастомным темам (AppBarLayout, TabLayout, toolbar title/subtitle)
- ThemeStore.init() вызывается в applyTheme() для загрузки кастомной темы из кэша

Сервер dev: v1.1.3.0 (порт 50052)
Сервер prod: v1.1.3.0 (порт 50051)

---

## АРХИТЕКТУРА

### Сервер (/root/msg)
```
main.go                    — Entry point, gRPC server, graceful shutdown
server.go                  — ServerVersion = "1.1.3.0", service version constants
server_chat.go             — Chat, Typing, CallSession, GetClients
server_users.go            — GetAllUsers, UpdateProfile, GetUserProfile, GetUserAvatar
server_chats.go            — GetAllChats, GetChats, CreateDirectChat, CreateGroupChat, DeleteChat
server_messages.go         — GetHistory, SetReaction, DeleteMessages, EditMessage
server_profile.go          — UpdateUsername, UpdatePassword, AdminUpdatePassword, MarkRead, UpdateAvatar, DeleteProfile
server_push.go             — RegisterToken, sendPushNotification, broadcastOnlineUsers
server_contacts.go         — AddContact, RemoveContact, GetContacts, GetChatListVersion
server_themes.go           — GetThemes, SaveTheme, SetCurrentTheme, DeleteTheme
server_drafts.go           — GetFCMLogs, SaveDraft, GetDraft, DeleteDraft
server_muted.go            — GetMutedChats, SetMutedChat
server_favorites.go        — GetUserId, AddFavorite, RemoveFavorite, GetFavorites
server_ai.go               — ChatWithOWL, ChatWithAI, ChatWithOrchestrator, Hermes sessions
server_management.go       — ServerServiceServer
auth_service.go            — AuthService: SignIn, SignUp
owl.go                     — OWL AI: ChatWithOWL streaming, сессии, история
bot_commands.go            — Bot Commands: /status, /deploy, /logs, /restart, /ai, /help, /version
hermes_orchestrator.go     — Hermes: оркестратор, маршрутизация агентов
hermes_agent_service.go    — Hermes: управление агентами
ai_chat_manager.go         — AI чаты (единый менеджер для OWL + Hermes)
db.go                      — Database layer
messenger.proto            — ChatService v2, AuthService v2, ProfileService v2, Pin Message
```

### Android (/root/msg.client.android)
```
ui/
├── chatlist/
│   ├── ChatListActivity.kt         — ЕДИНЫЙ Activity (~1113 LOC)
│   ├── ChatListViewModel.kt        — loadChats, pinChat, setTabFilter
│   ├── ChatListSections.kt         — Section enum + SectionItem
│   └── UpdateCoordinator.kt        — update system UI logic
├── adapter/
│   ├── ChatAdapter.kt              — адаптер с секциями + selection state + DiffUtil
│   └── MessageAdapter.kt           — адаптер сообщений + pinned badge
├── widget/
│   ├── ServerAuthBottomSheet.kt    — шторка выбора входа
│   ├── LoginBottomSheet.kt         — шторка входа
│   ├── RegisterBottomSheet.kt      — шторка регистрации
│   ├── AIBottomSheet.kt            — шторка выбора AI чата
│   └── NewChatBottomSheet.kt       — шторка создания чата (7 пунктов)
├── hermes/                         — Hermes AI чат
├── owl/                            — OWL AI чат
└── remote/                         — Remote Agent UI

data/
├── cache/CacheUtils.kt             — единый утилит очистки кэша
├── grpc/
│   ├── GrpcClient.kt              — facade (779 LOC)
│   ├── RealGrpcClient.kt           — orchestrator (874 LOC)
│   ├── GrpcMessageClient.kt        — messages, history, reactions (341 LOC)
│   ├── GrpcServerDiscoveryClient.kt — server discovery (145 LOC)
│   ├── GrpcMarshallers.kt          — 111 marshaller classes (1394 LOC)
│   ├── GrpcUnaryCallHelper.kt      — universal unary call (111 LOC)
│   ├── GrpcConnectionManager.kt    — connect/reconnect (167 LOC)
│   ├── GrpcAuthClient.kt           — JWT auth (232 LOC)
│   ├── GrpcCallClient.kt           — call session (125 LOC)
│   ├── GrpcTypingClient.kt         — typing stream (87 LOC)
│   ├── GrpcChatListClient.kt       — chat list CRUD (638 LOC)
│   ├── GrpcProfileClient.kt        — profile/avatar/themes (506 LOC)
│   ├── GrpcDraftClient.kt          — drafts (86 LOC)
│   ├── GrpcFavoritesClient.kt      — favorites (120 LOC)
│   ├── ProfileClient.kt            — ProfileService v2
│   └── BearerTokenInterceptor.kt
├── session/CredentialStore.kt
├── session/SessionManager.kt
├── auth/AuthManager.kt
└── models/Message.kt
```

### gRPC Client Architecture (v1.1.3.29)
```
GrpcClient (facade, 779 LOC)
    ↓
RealGrpcClient (orchestrator, 874 LOC)
    ├── GrpcConnectionManager (167)
    ├── GrpcAuthClient (232)
    ├── GrpcTypingClient (87)
    ├── GrpcCallClient (125)
    ├── GrpcChatListClient (638)
    ├── GrpcProfileClient (506)
    ├── GrpcDraftClient (86)
    ├── GrpcFavoritesClient (120)
    ├── GrpcMessageClient (341)
    ├── GrpcServerDiscoveryClient (145)
    └── GrpcMarshallers (1394)
```

---

## КЛЮЧЕВЫЕ РЕШЕНИЯ

- **Единый ChatListActivity** — убрано разделение v1/v2, один Activity работает на обоих серверах
- **JWT auth fallback** — при JWT ошибке: clear tokens → retry с password
- **gRPC модуляризация** — 12 модулей, RealGrpcClient -77%, God Object устранён
- **Settings Sheet** — showSettingsSheet() + showAdditionalSettingsSheet()
- **enableOnBackInvokedCallback** — добавлен в манифест
- **i18n** — "Lava" (en) / "Лава" (ru)
- **fetchServerInfo** — Dev (50052): skip HTTP, assume v2. Prod (50051): try HTTP /info, fallback v1
- **ThemeStore.init()** — вызывается в applyTheme() для загрузки кастомной темы из кэша
- **AppBarLayout tinting** — красится программно в customPrimary через ThemeApplier
- **TabLayout transparent** — прозрачный фон, цвета текста через customOnPrimary

---

## ПРАВИЛА

1. ⚠️ **НЕ компилировать Android на сервере** — только `go build` для сервера
2. НЕ деплоить на prod без прямого указания ферзя
3. Коммитить и пушить после каждого значимого изменения
4. userId (UUID) — всегда как ключ, НЕ username
5. JWT секрет: минимум 32 байта, НЕ коммитить
6. Темы: цвета программно через ThemeUtils.parseSafeColor()
7. i18n: все новые строки ОДНОВРЕМЕННО в values/strings.xml + values-ru/strings.xml
8. НЕ инициализировать getString() в полях класса Activity
9. Kotlin 2.3.21: cont.resume(value, onCancellation = {})
10. НЕ деплоить на prod без тестирования на dev
11. Kotlin object: StateFlow объявления ДО инициализации модулей
12. **НЕТ forceReconnect** — один connect при старте, reconnect только если FAILED

---

## КОМАНДЫ

```bash
# === СЕРВЕР ===
cd /root/msg && export PATH=$PATH:/usr/local/go/bin:~/go/bin

# Сборка и деплой на dev
go build -o /tmp/lavender-server-dev .
systemctl stop lavender-server-dev
cp /tmp/lavender-server-dev /root/LavenderMessenger/run/lavender-server-dev
systemctl start lavender-server-dev

# Сборка и деплой на prod (НЕ делать без тестирования на dev!)
go build -o /tmp/lavender-server .
systemctl stop lavender-server
cp /tmp/lavender-server /root/LavenderMessenger/run/lavender-server
systemctl start lavender-server

# Proto gen
protoc --go_out=gen --go_opt=paths=source_relative --go-grpc_out=gen --go-grpc_opt=paths=source_relative messenger.proto

# Логи
journalctl -u lavender-server-dev -f
journalctl -u lavender-server -f

# === ANDROID ===
cd /root/msg.client.android
# assembleRelease ТОЛЬКО локально!
```

---

## DEV vs PROD

| Характеристика | Dev | Prod |
|----------------|-----|------|
| Порт gRPC | 50052 | 50051 |
| Порт HTTP | 8083 | 8082 |
| Имя | Lava Germany dev | Lava Germany |
| Версия | v1.1.3.0 | v1.1.3.0 |

---

## ДОКУМЕНТАЦИЯ

| Файл | Назначение | Когда читать |
|------|-----------|-------------|
| `doc/INDEX.md` | Индекс всей документации | **Всегда в начале** |
| `doc/TASKS.md` | Таск-трекер | В начале сессии |
| `doc/PROMPT_ANDROID.md` | Этот файл — промпт | **Всегда в начале** |
| `doc/PATTERNS.md` | Паттерны и анти-patterns | Перед написанием кода |
| `doc/SESSION_NOTES.md` | Заметки всех сессий | В начале сессии |
| `doc/CHANGELOG.md` | История изменений | Для понимания что сделано |

---

## CHANGELOG (последние версии)

### v1.1.3.29 — UI улучшения
- NewChatBottomSheet — 7 пунктов меню (Add Contact, Start Chat, Group, Secret Chat, Conference, Hermes AI, OWL AI)
- Favorites — добавлен в табы (All/AI/Groups/Favorites)
- Тулбар — полная адаптация к кастомным темам (AppBarLayout, TabLayout, title/subtitle)
- ThemeStore.init() в applyTheme() для загрузки кастомной темы из кэша
- ivActionSettings — всегда виден после авторизации

### v1.1.3.28 — Финальная модуляризация gRPC
- GrpcMessageClient (341 LOC) — sendMessage, addLocalMessage, loadHistory, editMessage, deleteMessage, setReaction, markRead
- GrpcServerDiscoveryClient (145 LOC) — fetchServersList, parseServerList, proto parsing
- RealGrpcClient: 3810 → 874 строк (-77%), 12 модулей

### v1.1.3.27 — Извлечение GrpcMarshallers
- GrpcMarshallers (1394 LOC) — 111 marshaller classes
- RealGrpcClient: 2992 → 1611 LOC (-46%)

### v1.1.3.26 — Продолжение модуляризации
- GrpcChatListClient, GrpcProfileClient, GrpcDraftClient, GrpcFavoritesClient, GrpcUnaryCallHelper
- RealGrpcClient: 3810 → 2992 LOC

---

## ПРИОРИТЕТЫ СЛЕДУЮЩЕЙ СЕССИИ (v1.1.3.30)

### Средний приоритет
1. **ProfileService v2** — проверить работу на dev сервере
2. **Read receipts** — MarkAsRead с broadcast

### Отложено (пока не делаем)
- NewChatActivity рефакторинг (1473 строки → ViewModel) — отложено по решению ферзя
- ChatListActivity разбиение (ToolbarManager, TabManager)
- Qdrant + CLIP (production RAG)
- Shared element transitions
- Infinite scroll + pagination
