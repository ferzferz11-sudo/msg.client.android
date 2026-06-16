# Промпт для новой сессии — Android v1.1.3.20+

**Дата:** 2026-06-16
**Версия:** 1.1.3.20 (разработка)
**Ветка:** feat/1.1.3.x
**Тег:** v1.1.3.20

---

## СТАТУС: v1.1.3.20 — Модуляризация RealGrpcClient, следующий шаг: GrpcChatClient

RealGrpcClient частично модуляризирован: 4 из 6 модулей выделены.
Осталось ~3700 строк в RealGrpcClient, целевой размер ~200 строк (facade).

Сервер dev: v1.2.0.1 (ProfileService v2, ChatStream v2, ChatList v2, Pin Message).
Сервер prod: v1.1.3.10 (legacy, без v2).
Android: ChatListActivityV2 с табами, selection mode, поиском, Pin Message, FAB AI.

**Архитектурный принцип:** Полное разделение v1 и v2 архитектуры.
- v1 сервер (prod) → ChatListActivity (v1, без изменений)
- v2 сервер (dev) → ChatListActivityV2 (v2)
- Оба клиента (v1 и v2) поддерживают обратную совместимость с v1 сервером
- Версия сервера определяется через HTTP /info + fallback по gRPC порту

**КРИТИЧЕСКИЕ ПИТФОЛЫ (изучены в сессиях 22-24):**
- `getBearerToken()` возвращает `"Bearer <token>"` — для JWT в ChatStream использовать `getAccessToken()` (чистый токен)
- Auth failure (`UNKNOWN` + `"authentication failed"`) — ловить явно, ставить FAILED, НЕ retry
- Reconnect — единственный источник onError, НЕ onClose/getChats
- НЕ переписывать работающий код с нуля — только добавлять недостающее
- RealGrpcClient 3739 строк (было 4081) — разделить на модули: сделано 4/6, осталось GrpcChatClient + GrpcProfileClient
- НЕ компилировать Android на сервере — Gradle wrapper удалён (OOM kill), собирать ТОЛЬКО локально
- Kotlin object init order: StateFlow объявления ДО инициализации модулей (top-to-bottom)

---

## АРХИТЕКТУРА

### Сервер (/root/msg)
```
main.go                    — Entry point, gRPC server, graceful shutdown
server.go                  — ServerVersion = "1.2.0.1", service version constants
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

### Android v2 (/root/msg.client.android)
```
ui/
├── chatlist/                ← v2 НОВАЯ ПАПКА
│   ├── ChatListActivityV2.kt    — tabs, toolbar, FABs, navigation, selection mode, search, AI bottom sheet
│   ├── ChatAdapterV2.kt         — адаптер с секциями + selection state + DiffUtil
│   ├── ChatListViewModelV2.kt   — loadChats, pinChat, setTabFilter, getChats
│   ├── ChatListSections.kt      — Section enum + SectionItem
├── adapter/
│   ├── ChatAdapter.kt       ← v1 (НЕ ТРОГАТЬ)
│   └── MessageAdapter.kt    — адаптер сообщений + pinned badge
├── widget/
│   ├── ServerAuthBottomSheet.kt
│   ├── LoginBottomSheet.kt
│   ├── RegisterBottomSheet.kt
│   ├── AIBottomSheet.kt          — шторка выбора AI чата (OWL/Hermes)
│   └── CommandBottomSheet.kt     — шторка команд
├── hermes/                       — Hermes AI чат
│   ├── HermesChatActivity.kt
│   └── HermesChatViewModel.kt
├── owl/                          — OWL AI чат
│   ├── OwlChatActivity.kt
│   ├── OwlChatViewModel.kt
│   └── OwlSettingsActivity.kt
└── remote/                       — Remote Agent UI

data/
├── cache/CacheUtils.kt            — единый утилит очистки кэша
├── grpc/
│   ├── GrpcClient.kt             — facade (pinChat, pinMessage, searchChats, etc.)
│   ├── RealGrpcClient.kt          — оркестратор модулей (~3700 строк, цель: ~200)
│   ├── GrpcConnectionManager.kt   — connect/reconnect/disconnect/keepalive (167 строк)
│   ├── GrpcAuthClient.kt          — signInV2/signUpV2/refreshToken/signOut (232 строки)
│   ├── GrpcCallClient.kt          — startCallSession/sendCallSignal (124 строки)
│   ├── GrpcTypingClient.kt        — startTypingStream/sendTypingSignal (87 строк)
│   ├── ProfileClient.kt           — ProfileService v2 client + version detection
│   ├── BearerTokenInterceptor.kt  — JWT Bearer token
│   └── MessengerProto.kt          — proto data classes
├── session/CredentialStore.kt     — credentials + server list + lastUsername
├── session/SessionManager.kt      — loginV2 (JWT) + loginV1 (legacy fallback)
├── auth/AuthManager.kt            — JWT token storage
└── models/Message.kt              — Message (isPinned), ChatInfo (isPinned, isArchived, pinnedAt), AIChatInfo
```

---

## КЛЮЧЕВЫЕ РЕШЕНИЯ

### v1.1.3.20 (текущая)
- **Gradle wrapper удалён с сервера** — OOM protection, Android собирать ТОЛЬКО локально
- **RealGrpcClient модуляризирован** — 4 модуля выделены, ~3700 строк осталось

### v1.1.3.19
- **JWT auth** — getAccessToken() для ChatStream (чистый токен, без "Bearer ")
- **Reconnect** — единый источник onError, auth failure detection
- **DiffUtil** — ChatAdapterV2 использует DiffUtil
- **Unread badges** — цвета по теме, mark-as-read, реал-тайм

### v1.1.3.18
- **Optimistic READY** — gRPC channel подключается лениво, READY сразу после builder.build()
- **Reconnect on transport errors** — UNAVAILABLE/UNAUTHENTICATED/INTERNAL → reconnect, НЕ при shutdownNow
- **Keepalive 30s/10s** — для мобильных сетей, idleTimeout 25min
- **gRPC port heuristic** — если HTTP /info недоступен: 50052 → v2, 50051 → v1
- **Poll 30s** — getChats каждые 30 секунд

### v1.1.3.17
- **FAB AI** — AIBottomSheet подключён к ChatListActivityV2
- **AI навигация** — Hermes/OWL чаты создаются с пустым chatId → сервер создаёт

### i18n
- Все строки в values/strings.xml (en) + values-ru/strings.xml
- app_version_format: "Lava: app Android %s" / "Lava: приложение Android %s"

---

## ПРАВИЛА

1. ⚠️ **НЕ компилировать Android на сервере** — только `go build` для сервера, Gradle wrapper удалён
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
15. НЕ ТРОГАТЬ v1 файлы: ChatListActivity.kt, ChatAdapter.kt
16. ChatListActivityV2 — БЕЗ фрагмента, RecyclerView+SwipeRefresh напрямую
17. Очистка кэша — использовать CacheUtils, не дублировать код
18. Pin Message — только через selection toolbar (v1-style), НЕ PopupMenu
19. getChats() — всегда вызывать callback, даже при ошибке
20. loadChats() — единственная точка входа: ViewModel.init collector, НЕ дублировать из Activity
21. Kotlin object: StateFlow объявления ДО инициализации модулей (top-to-bottom)

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

# Proto gen (обязательно после изменений в messenger.proto!)
protoc --go_out=gen --go_opt=paths=source_relative --go-grpc_out=gen --go-grpc_opt=paths=source_relative messenger.proto

# Тесты
go test ./...

# Логи
journalctl -u lavender-server-dev -f
journalctl -u lavender-server -f

# === ANDROID ===
cd /root/msg.client.android
# assembleRelease ТОЛЬКО локально! Gradle wrapper удалён с сервера.
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
| Версия | v1.2.0.1 | v1.1.3.10 |
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
| `doc/ARCH_ANALYSIS_V2_V1.md` | Анализ архитектуры v2 vs v1 | При планировании рефакторинга |
| `doc/REMOTE_AGENT.md` | Remote Agent: архитектура, протокол, streaming | При работе с Remote Agent |
| `doc/PLAN_REFACTOR_GRPC.md` | План рефакторинга RealGrpcClient | При продолжении модуляризации |
| `/root/msg/doc/INTEGRATION_SESSION.md` | Интеграционная сессия | При работе с сервером |
| `/root/msg/doc/AI_SERVICES.md` | AI-сервисы: OWL, Hermes | При работе с AI чатами |

---

## ПРИОРИТЕТЫ СЛЕДУЮЩЕЙ СЕССИИ (v1.1.3.21)

### Высокий приоритет
1. **Выделить GrpcChatClient** — из оставшихся ~3700 строк RealGrpcClient
   - Методы: getChats, sendMessage, loadHistory, pinChat, searchChats, archiveChat, draft, favorites, reactions, profile, chat management
   - ~2000 строк — самый большой оставшийся кусок
   - Тестировать на dev после завершения
2. **Push notifications** — FCM интеграция

### Средний приоритет
3. **ProfileService v2** — проверить работу на dev сервере
4. **Read receipts** — MarkAsRead

### Отложено
- Qdrant + CLIP (production RAG)
- Shared element transitions
- Infinite scroll + pagination
