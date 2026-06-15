# Промпт для новой сессии — Android v1.1.3.16

**Дата:** 2026-06-16
**Версия:** 1.1.3.16 (v1.1.3.15 выпущен, v1.1.3.16 в разработке)
**Ветка:** feat/1.1.3.x

---

## СТАТУС: v1.1.3.16 — в разработке

v1.1.3.15 — последняя стабильная v1 (prod сервер) — **выпущен ферзём**.
v1.1.3.16 — ChatList v2 UI + разделение v1/v2 Activity.

Сервер dev: v1.2.0.1 (ProfileService v2, ChatStream v2, ChatList v2).
Сервер prod: v1.1.3.10 (legacy, без v2).
Android: ChatList v2 UI scaffold создан, тестируется на dev и prod.

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
db_chatlist_v2.go          — ChatList v2 DB methods
server_profile_v2.go       — ProfileService v2 (JWT, dev only)
server_chatlist_v2.go      — ChatList v2 RPC
server_chat.go             — Chat stream v2 (JWT + password)
server_remote.go           — Remote Agent RPC
hermes_remote_manager.go   — HandleTaskStream
ai_chat_manager.go         — AI чаты
owl.go                     — OWL AI
hermes_orchestrator.go     — Hermes Orchestrator
http_server.go             — HTTP (/health, /info)
messenger.proto            — ChatService v2, AuthService v2, ProfileService v2
```

### Android v2 (/root/msg.client.android) — НОВАЯ ПАПКА
```
ui/
├── chatlist/                ← v2 НОВАЯ ПАПКА
│   ├── ChatListActivityV2.kt    — определение версии сервера + fallback на v1
│   ├── ChatListFragmentV2.kt    — SwipeRefresh + RecyclerView
│   ├── ChatAdapterV2.kt         — адаптер с секциями
│   ├── ChatListViewModelV2.kt   — ViewModel
│   └── ChatListSections.kt      — Section enum + SectionItem
├── adapter/
│   └── ChatAdapter.kt       ← v1 (НЕ ТРОГАТЬ)
├── widget/
│   ├── ServerAuthBottomSheet.kt
│   ├── LoginBottomSheet.kt
│   └── RegisterBottomSheet.kt
└── ...

res/
├── layout/
│   ├── activity_chat_list_v2.xml       — v2 layout с TabLayout
│   ├── fragment_chat_list_v2.xml       — SwipeRefresh + RecyclerView
│   └── item_chat_section_header.xml    — заголовок секции
├── menu/
│   └── chat_list_context_menu_v2.xml   — v2 контекстное меню (Pin/Mute/Delete)
├── values/strings.xml                  — 17 новых строк
└── values-ru/strings.xml               — 17 новых строк

data/
├── grpc/
│   ├── GrpcClient.kt              — facade (pinChat, searchChats, archiveChat, etc.)
│   ├── RealGrpcClient.kt          — реализация gRPC
│   ├── ProfileClient.kt           — ProfileService v2 client + fetchServerInfo
│   └── BearerTokenInterceptor.kt  — JWT Bearer token
├── models/
│   └── Message.kt                 — ChatInfo (isPinned, isArchived, pinnedAt)
└── session/
    ├── SessionManager.kt          — loginV2 (JWT) + loginV1 (legacy fallback)
    └── CredentialStore.kt         — credentials + server list
```

---

## КЛЮЧЕВЫЕ РЕШЕНИЯ

### v1.1.3.15 (выпущен)
- Последняя версия с полной поддержкой v1 (prod сервер)
- Полная обратная совместимость

### v1.1.3.16 (в разработке)
- **Разделение v1/v2 Activity**: ChatListActivityV2 определяет версию сервера через fetchServerInfo()
  - v2 сервер → ChatListActivityV2 (новый UI)
  - v1 сервер → fallback на ChatListActivity (v1, без изменений)
- **Pin Chat** — в context menu списка (long press), НЕ в toolbar
- **Pin Message** — в меню сообщения (long press), нужны новые серверные RPC
- **Favorites** = Archive — существующий чат "Личное хранилище"
- **Секции списка**: Pinned / Favorites / All Chats
- **Табы**: All / AI / Groups

### i18n
- Все строки в values/strings.xml (en) + values-ru/strings.xml
- app_version_format: "Lava: app Android %s" / "Lava: приложение Android %s"

---

## ПРАВИЛА

1. НЕ компилировать на сервере (OOM kill) — это касается и Go и Android (./gradlew убивает всё по памяти)
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
13. **fetchServerInfo** — всегда использовать для определения версии сервера
14. **Kotlin 2.3.21:** `cont.resume(value, onCancellation = {})` — всегда передавать onCancellation
15. **НЕ ТРОГАТЬ v1 файлы**: ChatListActivity.kt, ChatAdapter.kt — v1.1.3.15 уже выпущен
16. **Pin Chat НЕ в toolbar** — в context menu списка (long press), как в Telegram

---

## ИЗВЕСТНЫЕ ПРОБЛЕМЫ

### dataBindingGenBaseClasses NPE
- `@++id/` → `@+id/` (двойной плюс невалиден)
- НЕ использовать `app:layout_constraint*` в CoordinatorLayout — использовать `android:layout_gravity`
- НЕ ссылаться на несуществующие стили (TextAppearance.MaterialComponents.Caption)

### 42P10 на prod БД (сервер)
- `Failed to register device ... pq: there is no unique or exclusion constraint`
- UNIQUE constraint на user_devices в prod БД уже есть
- Исправится после редеплоя prod на v1.2.0.1

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
- Подводные камни: `/root/msg/doc/PITFALLS.md`
- CHANGELOG: `/root/msg.client.android/CHANGELOG.md`
- План ChatList v2: `/root/msg.client.android/doc/PLAN_CHATLIST_V2.md`
- Заметки сессии: `/root/msg/client.android/doc/SESSION_NOTES.md`
