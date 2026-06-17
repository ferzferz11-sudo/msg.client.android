# Промпт для новой сессии — Android v1.1.3.29+

**Дата:** 2026-06-17
**Версия:** 1.1.3.28 (разработка)
**Ветка:** feat/1.1.3.x

---

## СТАТУС: v1.1.3.28 — Рефакторинг gRPC завершён

**Ключевые изменения:**
- GrpcMessageClient (341 LOC) — все message operations вынесены
- GrpcServerDiscoveryClient (145 LOC) — server discovery вынесен
- RealGrpcClient: 3810 → 874 строк (-77% от оригинала)
- 12 модулей выделено, рефакторинг gRPC завершён

Сервер dev: v1.2.0.2
Сервер prod: v1.1.3.10

---

## АРХИТЕКТУРА

### Сервер (/root/msg)
```
main.go                    — Entry point, gRPC server, graceful shutdown
server.go                  — ServerVersion = "1.2.0.2", service version constants
auth_service.go            — AuthService v1 (deprecated)
auth_service_v2.go         — AuthService v2 (JWT, основной)
auth_interceptor.go        — gRPC Bearer token interceptor (unary + streaming)
auth_jwt.go                — JWT генерация/валидация
db_auth_devices.go         — CRUD для user_devices + device_auth_log
db_auth_migrations.go      — миграция таблиц (включая user_settings)
db_chatlist_v2.go          — ChatList v2 DB methods + Pin Message DB methods
server_profile_v2.go       — ProfileService v2 (JWT, dev only)
server_chatlist_v2.go      — ChatList v2 RPC + Pin Message RPC handlers
server_chat.go             — Chat stream v2 (JWT + password)
server_remote.go           — Remote Agent RPC
hermes_remote_manager.go   — HandleTaskStream
ai_chat_manager.go         — AI чаты
owl.go                     — OWL AI
hermes_orchestrator.go     — Hermes Orchestrator
http_server.go             — HTTP (/health, /info)
messenger.proto            — ChatService v2, AuthService v2, ProfileService v2, Pin Message
```

### Android (/root/msg.client.android)
```
ui/
├── chatlist/
│   ├── ChatListActivity.kt         — ЕДИНЫЙ Activity (1104 LOC)
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
│   └── NewChatBottomSheet.kt       — шторка создания чата
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

### gRPC Client Architecture (v1.1.3.28)
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
| Версия | v1.2.0.2 | v1.1.3.10 |

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

### v1.1.3.28 — Финальная модуляризация gRPC
- GrpcMessageClient (341 LOC) — sendMessage, addLocalMessage, loadHistory, editMessage, deleteMessage, setReaction, markRead
- GrpcServerDiscoveryClient (145 LOC) — fetchServersList, parseServerList, proto parsing
- RealGrpcClient: 3810 → 874 строк (-77%), 12 модулей
- Компиляция: ✅ проходит

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
