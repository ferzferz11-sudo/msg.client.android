# Промпт для новой сессии — Android v1.1.3.18+

**Дата:** 2026-06-15
**Версия:** 1.1.3.18 (разработка)
**Ветка:** feat/1.1.3.x
**Тег:** v1.1.3.17

---

## СТАТУС: v1.1.3.18 — Тестирование v1.1.3.17, план следующей сессии

v1.1.3.17 — FAB AI подключён, AIBottomSheet интегрирован, protoc сгенерирован.
v1.1.3.16 — все фичи реализованы (ChatListV2, Selection Mode, Search, Pin Message, CacheUtils).

Сервер dev: v1.2.0.1 (ProfileService v2, ChatStream v2, ChatList v2, Pin Message).
Сервер prod: v1.1.3.10 (legacy, без v2).
Android: ChatListActivityV2 с табами, selection mode, поиском, Pin Message, FAB AI.

**Архитектурный принцип:** Полное разделение v1 и v2 архитектуры.
- v1 сервер (prod) → ChatListActivity (v1, без изменений)
- v2 сервер (dev) → ChatListActivityV2 (v2)
- Оба клиента (v1 и v2) поддерживают обратную совместимость с v1 сервером
- fetchServerInfo всегда определяет версию сервера

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

### Android v2 (/root/msg.client.android) — НОВАЯ ПАПКА
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
│   ├── HermesChatViewModel.kt
│   └── HermesSettingsActivity.kt (не существует, используется OwlSettingsActivity с isHermes=true)
├── owl/                          — OWL AI чат
│   ├── OwlChatActivity.kt
│   ├── OwlChatViewModel.kt
│   └── OwlSettingsActivity.kt
└── remote/                       — Remote Agent UI

data/
├── cache/CacheUtils.kt            — единый утилит очистки кэша
├── grpc/GrpcClient.kt             — facade (pinChat, pinMessage, searchChats, etc.)
├── grpc/RealGrpcClient.kt         — реализация gRPC (JWT auth, ChatList v2, Pin Message RPC)
├── grpc/ProfileClient.kt          — ProfileService v2 client + fetchServerInfo
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

### v1.1.3.17 (текущая)
- **FAB AI** — AIBottomSheet подключён к ChatListActivityV2
- **AI навигация** — Hermes/OWL чаты создаются с пустым chatId → сервер создаёт
- **AI настройки** — Hermes использует OwlSettingsActivity с isHermes=true
- **getChats()** — публичный метод в ChatListViewModelV2

### v1.1.3.16 (предыдущая)
- **ChatListActivityV2 без фрагмента** — RecyclerView+SwipeRefresh напрямую в Activity
- **TabLayout** — табы All/AI/Groups с фильтрацией через ViewModel.setTabFilter
- **Selection Mode** — long press = ActionMode toolbar, тап = toggle selection
- **Поиск** — SearchView в toolbar + debounce 300ms
- **Pin Message** — selection toolbar кнопка pin/unpin (v1-style), pinned badge
- **CacheUtils** — единый утилит очистки кэша

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

---

## ДОКУМЕНТАЦИЯ

- Индекс: `/root/msg.client.android/doc/INDEX.md`
- Паттерны: `/root/msg.client.android/doc/PATTERNS.md`
- Remote Agent: `/root/msg.client.android/doc/REMOTE_AGENT.md`
- Сервер: `/root/msg/doc/INTEGRATION_SESSION.md`, `/root/msg/doc/TASKS.md`
- CHANGELOG: `/root/msg.client.android/CHANGELOG.md`
- Заметки сессий: `/root/msg.client.android/doc/SESSION_NOTES.md`

---

## ПРИОРИТЕТЫ СЛЕДУЮЩЕЙ СЕССИИ (v1.1.3.18)

### Критический баг (ПЕРВЫЙ ПРИОРИТЕТ)
1. **Баг: Чаты не загружаются при входе на новый сервер** — подробное исследование в `doc/BUG_LOAD_CHATS_RESEARCH.md`
   - Симптом: при входе виден только "Избранное", чаты не появляются
   - SwipeRefresh помогает, но не всегда (при восстановлении сеанса — не помогает)
   - Корневая причина: race condition между connect/loadChats + проблемы с кэшем в getChats()
   - Нужно: исправить логику загрузки чатов в ChatListActivity (v1) и ChatListActivityV2 (v2)

### Средний приоритет
2. **Unread badges улучшение** — счётчик непрочитанных в списке чатов
3. **Push notifications** — интеграция с FCM для v2

### Отложено (не в этой сессии)
- Qdrant + CLIP (production RAG)
- Shared element transitions
- Infinite scroll + pagination
- Read receipts (MarkAsRead)
- Редеплой prod сервера — только после выхода Android клиента
- Выпуск Android — делается ферзем лично
