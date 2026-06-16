# Промпт для новой сессии — Android v1.1.3.19+

**Дата:** 2026-06-16
**Версия:** 1.1.3.19 (разработка)
**Ветка:** feat/1.1.3.x
**Тег:** v1.1.3.18

---

## СТАТУС: v1.1.3.19 — JWT auth, reconnect stability, DiffUtil, unread badges

v1.1.3.19 — JWT auth исправлен, reconnect оптимизирован, DiffUtil добавлен, unread badges работают.
Сервер dev: v1.2.0.1 (ProfileService v2, ChatStream v2, ChatList v2, Pin Message).
Сервер prod: v1.1.3.10 (legacy, без v2).
Android: ChatListActivityV2 с табами, selection mode, поиском, Pin Message, FAB AI.

**Архитектурный принцип:** Полное разделение v1 и v2 архитектуры.
- v1 сервер (prod) → ChatListActivity (v1, без изменений)
- v2 сервер (dev) → ChatListActivityV2 (v2)
- Оба клиента (v1 и v2) поддерживают обратную совместимость с v1 сервером
- Версия сервера определяется через HTTP /info + fallback по gRPC порту

**КРИТИЧЕСКИЕ ПИТФОЛЫ (изучены в сессии 22):**
- `getBearerToken()` возвращает `"Bearer <token>"` — для JWT в ChatStream использовать `getAccessToken()` (чистый токен)
- Auth failure (`UNKNOWN` + `"authentication failed"`) — ловить явно, ставить FAILED, НЕ retry
- Reconnect — единственный источник onError, НЕ onClose/getChats
- НЕ переписывать работающий код с нуля — только добавлять недостающее
- RealGrpcClient 4070 строк — главная проблема архитектуры (план: разделить на модули)

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
│   ├── ChatAdapterV2.kt         — адаптер с секциями + selection state
│   ├── ChatListViewModelV2.kt   — loadChats, pinChat, setTabFilter, getChats
│   ├── ChatListSections.kt      — Section enum + SectionItem
│   └── ChatListFragmentV2.kt    — фрагмент (не используется, для справки)
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
├── grpc/GrpcClient.kt             — facade (pinChat, pinMessage, searchChats, etc.)
├── grpc/RealGrpcClient.kt         — реализация gRPC (JWT auth, ChatList v2, Pin Message RPC, optimistic READY, reconnect)
├── grpc/ProfileClient.kt          — ProfileService v2 client + version detection (HTTP /info + gRPC port heuristic)
├── grpc/BearerTokenInterceptor.kt — JWT Bearer token
├── proto/MessengerProto.kt        — proto data classes (ChatList v2, Pin Message, jwt_token)
├── session/CredentialStore.kt     — credentials + server list + lastUsername
├── session/SessionManager.kt      — loginV2 (JWT) + loginV1 (legacy fallback)
├── auth/AuthManager.kt            — JWT token storage
└── models/Message.kt              — Message (isPinned), ChatInfo (isPinned, isArchived, pinnedAt), AIChatInfo

res/
├── layout/
│   ├── activity_chat_list_v2.xml       — v2 layout: SwipeRefresh+RecyclerView, TabLayout, FABs
│   ├── item_chat.xml                   — элемент чата + checkbox для selection
│   └── item_message.xml                — сообщение + pinned badge
├── menu/
│   ├── chat_list_action_mode.xml       — меню ActionMode (Pin/Mute/Archive/Delete)
│   ├── chat_list_search.xml            — меню поиска
│   └── chat_list_context_menu_v2.xml   — v2 контекстное меню (Pin/Mute/Delete)
├── values/strings.xml                  — connection status, selection, pin message строки
└── values-ru/strings.xml               — connection status, selection, pin message строки
```

---

## КЛЮЧЕВЫЕ РЕШЕНИЯ

### v1.1.3.18 (текущая)
- **Optimistic READY** — gRPC channel подключается лениво, READY сразу после builder.build()
- **Reconnect on transport errors** — UNAVAILABLE/UNAUTHENTICATED/INTERNAL → reconnect, но НЕ при shutdownNow
- **Keepalive 30s/10s** — для мобильных сетей, idleTimeout 25min
- **gRPC port heuristic** — если HTTP /info недоступен: 50052 → v2, 50051 → v1
- **Poll 30s** — getChats каждые 30 секунд вместо 5

### v1.1.3.17
- **FAB AI** — AIBottomSheet подключён к ChatListActivityV2
- **AI навигация** — Hermes/OWL чаты создаются с пустым chatId → сервер создаёт

### i18n
- Все строки в values/strings.xml (en) + values-ru/strings.xml
- app_version_format: "Lava: app Android %s" / "Lava: приложение Android %s"

---

## ПРАВИЛА

1. НЕ компилировать на сервере (OOM kill)
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
| Версия | v1.2.0.1 | v1.1.3.10 |
| ProfileService | v2 (JWT) | v1 (legacy ChatService) |
| ChatStream | v2 (JWT + password) | v1 (password only) |
| ChatList | v2 (Pin/Search/Archive) | v1 (basic) |

---

## ДОКУМЕНТАЦИЯ

- Индекс: `/root/msg.client.android/doc/INDEX.md`
- Паттерны: `/root/msg.client.android/doc/PATTERNS.md`
- Remote Agent: `/root/msg.client.android/doc/REMOTE_AGENT.md`
- Сервер: `/root/msg/doc/INTEGRATION_SESSION.md`, `/root/msg/doc/TASKS.md`
- CHANGELOG: `/root/msg.client.android/CHANGELOG.md`
- Заметки сессий: `/root/msg.client.android/doc/SESSION_NOTES.md`
- Архитектурный анализ: `/root/msg.client.android/doc/ARCH_ANALYSIS_V2_V1.md`

---

## ПРИОРИТЕТЫ СЛЕДУЮЩЕЙ СЕССИИ (v1.1.3.20)

### Высокий приоритет
1. **Рефакторинг RealGrpcClient** — разделить на модули (план: `doc/PLAN_REFACTOR_GRPC.md`)
   - GrpcConnectionManager, GrpcChatClient, GrpcAuthClient, GrpcProfileClient, GrpcCallClient, GrpcTypingClient
   - RealGrpcClient → тонкая обёртка ~200 строк
   - Тестировать на dev после каждого шага

### Средний приоритет
2. **Убрать мёртвый код** — ChatListFragmentV2 (не используется, 144 строки)
3. **ProfileService v2** — проверить работу на dev сервере

### Отложено
- Qdrant + CLIP (production RAG)
- Shared element transitions
- Infinite scroll + pagination

### Выполнено в v1.1.3.19
- ✅ Unread badges — цвета по теме, mark-as-read, реал-тайм обновление
- ✅ JWT auth fix — getAccessToken() вместо getBearerToken()
- ✅ Reconnect stability — единый источник onError, auth failure detection
- ✅ DiffUtil в ChatAdapterV2 — анимированные обновления списка
- ✅ Stream stability — убраны дубли reconnect, shutdownNow suppression
- ✅ HTTP /info fix — dev сервер определяется мгновенно
- ✅ ARCH_ANALYSIS_V2_V1.md — анализ архитектуры
- ✅ PLAN_REFACTOR_GRPC.md — план рефакторинга
