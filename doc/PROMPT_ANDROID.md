# Промпт для новой сессии — Android v1.1.3.25+

**Дата:** 2026-06-17
**Версия:** 1.1.3.25 (разработка)
**Ветка:** feat/1.1.3.x
**Тег:** v1.1.3.25 (не выпущен)

---

## СТАТУС: v1.1.3.25 — Update System восстановление

**Ключевые изменения:**
- UpdateManager интегрирован в ChatListActivity: silent check, manual check, progress dialog
- Update indicator в toolbar (llUpdateContainer) с состояниями: available/downloading/downloaded
- APK install через FileProvider после скачивания
- Созданы drawable: ic_loading_renew, deployed_code_update_24, ic_checked

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
│   ├── ChatListActivity.kt         — ЕДИНЫЙ Activity: tabs, toolbar, FABs, navigation, selection mode, search, AI bottom sheet, settings sheets
│   ├── ChatListViewModel.kt        — loadChats, pinChat, setTabFilter, getChats
│   ├── ChatListSections.kt         — Section enum + SectionItem
├── adapter/
│   ├── ChatAdapter.kt              — адаптер с секциями + selection state + DiffUtil
│   └── MessageAdapter.kt           — адаптер сообщений + pinned badge
├── widget/
│   ├── ServerAuthBottomSheet.kt    — шторка выбора входа (httpPort auto-detect)
│   ├── LoginBottomSheet.kt         — шторка входа (prefillUsername)
│   ├── RegisterBottomSheet.kt      — шторка регистрации
│   ├── AIBottomSheet.kt            — шторка выбора AI чата (OWL/Hermes)
│   └── NewChatBottomSheet.kt       — шторка создания чата
├── hermes/                         — Hermes AI чат
├── owl/                            — OWL AI чат
└── remote/                         — Remote Agent UI

data/
├── cache/CacheUtils.kt             — единый утилит очистки кэша
├── grpc/
│   ├── GrpcClient.kt              — facade (pinChat, pinMessage, searchChats, etc.)
│   ├── RealGrpcClient.kt           — оркестратор модулей (~3700 строк, цель: ~200)
│   ├── GrpcConnectionManager.kt    — connect/reconnect/disconnect/keepalive (167 строк)
│   ├── GrpcAuthClient.kt           — signInV2/signUpV2/refreshToken/signOut (232 строки)
│   ├── GrpcCallClient.kt           — startCallSession/sendCallSignal (124 строки)
│   ├── GrpcTypingClient.kt         — startTypingStream/sendTypingSignal (87 строк)
│   ├── ProfileClient.kt            — ProfileService v2 client + version detection
│   ├── BearerTokenInterceptor.kt   — JWT Bearer token
│   └── MessengerProto.kt           — proto data classes
├── session/CredentialStore.kt      — credentials + server list + lastUsername
├── session/SessionManager.kt       — loginV2 (JWT) + loginV1 (legacy fallback)
├── auth/AuthManager.kt             — JWT token storage
└── models/Message.kt               — Message (isPinned), ChatInfo (isPinned, isArchived, pinnedAt), AIChatInfo
```

---

## КЛЮЧЕВЫЕ РЕШЕНИЯ

### v1.1.3.24 (текущая)
- **Единый ChatListActivity** — убрано разделение v1/v2, один Activity работает на обоих серверах
- **НЕТ fallbackToV1()** — если сервер v1, Activity просто не показывает v2-only фичи
- **JWT auth fallback** — при JWT ошибке: clear tokens → retry с password
- **getChats retry** — при shutdownNow через 1.5с вместо emptyList
- **Backup chat restart** — при shutdownNow race condition через 2с
- **Аватар в тулбаре** — Glide + avatarCacheFlow
- **Статус соединения** — RECONNECTING и FAILED отображаются в тулбаре
- **Settings Sheet** — showSettingsSheet() + showAdditionalSettingsSheet() в ChatListActivity
- **enableOnBackInvokedCallback** — добавлен в манифест

### i18n
- Все строки в values/strings.xml (en) + values-ru/strings.xml
- Приложение называется "Lava" (en) / "Лава" (ru)

---

## ПРАВИЛА

1. ⚠️ **НЕ компилировать Android на сервере** — только `go build` для сервера
2. НЕ деплоить новую версию на prod без прямого указания ферзя
3. Коммитить и пушить после каждого значимого изменения
4. Версия сервера в server.go:33, версия Android в version.txt
5. userId (UUID) — всегда как ключ, НЕ username
6. changelog.txt БОЛЬШЕ НЕ ИСПОЛЬЗУЕТСЯ
7. JWT секрет: минимум 32 байта, НЕ коммитить
8. Темы: цвета программно через ThemeUtils.parseSafeColor()
9. i18n: все новые строки ОДНОВРЕМЕННО в values/strings.xml + values-ru/strings.xml
10. НЕ инициализировать getString() в полях класса Activity
11. Форматирование строк: позиционные форматтеры (%1$s, %2$d)
12. НЕ деплоить на prod без тестирования на dev
13. fetchServerInfo — всегда использовать для определения версии сервера
14. Kotlin 2.3.21: cont.resume(value, onCancellation = {}) — всегда передавать onCancellation
15. Очистка кэша — использовать CacheUtils, не дублировать код
16. Pin Message — только через selection toolbar (v1-style), НЕ PopupMenu
17. getChats() — всегда вызывать callback, даже при ошибке
18. loadChats() — единственная точка входа: ViewModel.init collector, НЕ дублировать из Activity
19. Kotlin object: StateFlow объявления ДО инициализации модулей (top-to-bottom)
20. **НЕТ forceReconnect** — один connect при старте, reconnect только если FAILED
21. **НЕТ disconnect/connect при resume** — только reconnect если статус FAILED

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

# Тесты
go test ./...

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
| Сервис | lavender-server-dev | lavender-server |
| Конфиг | .env.dev | .env |
| DB | chat_db_dev | chat_db |
| Версия | v1.2.0.2 | v1.1.3.10 |
| ProfileService | v2 (JWT) | v1 (legacy ChatService) |
| ChatStream | v2 (JWT + password) | v1 (password only) |
| ChatList | v2 (Pin/Search/Archive) | v1 (basic) |

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
| `doc/REMOTE_AGENT.md` | Remote Agent: архитектура, протокол, streaming | При работе с Remote Agent |
| `/root/msg/doc/INTEGRATION_SESSION.md` | Интеграционная сессия | При работе с сервером |
| `/root/msg/doc/AI_SERVICES.md` | AI-сервисы: OWL, Hermes | При работе с AI чатами |

---

## ПРИОРИТЕТЫ СЛЕДУЮЩЕЙ СЕССИИ (v1.1.3.27)

### Высокий приоритет
1. **Выделить GrpcMessageClient** — из оставшихся ~1611 строк RealGrpcClient
   - Методы: sendMessage, addLocalMessage, loadHistory, editMessage, deleteMessage, setReaction, markRead
   - ~800 строк — самый большой оставшийся кусок
   - После этого RealGrpcClient станет ~800 строк (orchestrator only)

2. **Выделить GrpcServerDiscoveryClient** — server discovery, raw protobuf parsing (~150 строк)

3. **Финальный рефакторинг RealGrpcClient** — до ~200 строк (только orchestrator)

### Средний приоритет
4. **ProfileService v2** — проверить работу на dev сервере
5. **Read receipts** — MarkAsRead с broadcast
6. **NewChatActivity рефакторинг** — 1473 строки, выделить ViewModel

### Отложено
- Qdrant + CLIP (production RAG)
- Shared element transitions
- Infinite scroll + pagination
- ChatListActivity разбиение (ToolbarManager, TabManager)
