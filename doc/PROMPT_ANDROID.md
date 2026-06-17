# Lava Messenger — Android Session Prompt

**Дата:** 2026-06-17 | **Версия:** v1.1.3.33 | **Ветка:** feat/1.1.3.x

---

## СТАТУС

v1.1.3.33 — разработка. Фазы 1-2 завершены. Релиз APK отложен до выполнения всех пунктов плана.

---

## АРХИТЕКТУРА

### ChatList
```
ChatListActivity (~364) — onCreate, setupUI, lifecycle, proxy methods
├── ChatListToolbar (232) — toolbar + settings sheets
├── ChatListTabs (30) — tabs (All/Groups/AI Chats)
├── ChatListActionMode (120) — selection mode
├── ChatListSearch (56) — search
├── ChatListFABs (470) — FABs + action sheets + AI bottom sheet
├── ChatListNavigation (60) — navigateToChat
├── ChatListAuth (212) — auth dialogs
├── ChatListViewModel (295) — ViewModel + error StateFlow
├── ChatListSections (20) — sections
└── UpdateCoordinator (245) — updates
```

### Chat (NewChatActivity)
```
NewChatActivity (~754) — onCreate, lifecycle, observers, wiring
├── ChatToolbarDelegate (341) — toolbar, avatar, subtitle, navigation
├── ChatInputDelegate (567) — input, send, attachments, audio, emoji, mentions
├── ChatSelectionDelegate (236) — selection mode, copy/pin/delete/forward
├── ChatSearchDelegate (135) — in-chat search
├── ChatE2EEDelegate (72) — E2EE key exchange, encrypt/decrypt
└── ChatMessageMenuDelegate (106) — reactions, context menu
```

### gRPC Client
```
GrpcClient (facade, 780 LOC)
    ↓
RealGrpcClient (orchestrator, 882 LOC)
    ├── GrpcConnectionManager (167)
    ├── GrpcAuthClient (232)
    ├── GrpcTypingClient (87)
    ├── GrpcCallClient (125)
    ├── GrpcChatListClient (641)
    ├── GrpcProfileClient (506)
    ├── GrpcDraftClient (86)
    ├── GrpcFavoritesClient (120)
    ├── GrpcMessageClient (344)
    ├── GrpcServerDiscoveryClient (145)
    └── GrpcMarshallers (1395)
```

### Серверы
| | Dev | Prod |
|--|-----|------|
| gRPC | 50052 | 50051 |
| HTTP | 8083 | 8082 |

---

## ПРИОРИТЕТЫ

### ✅ Завершено (v1.1.3.33)
- Фаза 0: Тестирование v1.1.3.32 на реальных чатах ✅
- Фаза 1: NewChatActivity рефакторинг (1473→754 LOC, -49%) ✅
- Фаза 2: Унификация error handling ✅

### 🟡 Следующие (v1.1.3.34-38)
1. **Фаза 3** (v1.1.3.34): Unit-тесты для gRPC клиента — 0 тестов → >20
2. **Фаза 4** (v1.1.3.35): GrpcClient facade оптимизация (780→<400 LOC)
3. **Фаза 5** (v1.1.3.36): AI Chats domain layer (выделение из gRPC слоя)

### 🟢 Отложено
- Pagination для чатов
- Incremental history loading
- Certificate pinning
- Qdrant + CLIP
- Shared element transitions
- ProfileActivity рефакторинг (719 LOC)
- ConferenceLobbyActivity рефакторинг (581 LOC)

**Детальный план:** `doc/PLAN_V1.1.3.33.md`

---

## ПРАВИЛА

1. ⚠️ НЕ компилировать Android на сервере
2. НЕ деплоить на prod без прямого указания ferz
3. Коммитить и пушить после каждого значимого изменения
4. userId (UUID) — всегда как ключ, НЕ username
5. i18n: все новые строки ОДНОВРЕМЕННО в values/strings.xml + values-ru/strings.xml
6. НЕ инициализировать getString() в полях класса Activity
7. Kotlin 2.3.21: cont.resume(value, onCancellation = {})
8. НЕТ forceReconnect — один connect при старте, reconnect только если FAILED
9. Favorites — НЕ секция в списке, а отдельный чат (type="favorites")
10. При выносе кода из Activity — `internal` для полей/методов, прокси-методы в Activity
11. НЕ добавлять новые фичи без прямого запроса
12. НЕ рефакторить работающий код без прямого запроса
13. Все ошибки логировать через `ErrorHandler.handle()` — НЕ через `Log.e` напрямую

---

## КОМАНДЫ

```bash
# Сервер
cd /root/msg && export PATH=$PATH:/usr/local/go/bin:~/go/bin
go build -o /tmp/lavender-server-dev . && systemctl stop lavender-server-dev && cp /tmp/lavender-server-dev /root/LavenderMessenger/run/lavender-server-dev && systemctl start lavender-server-dev

# Android (НЕ компилировать на сервере!)
cd /root/msg.client.android
```

---

## ДОКУМЕНТАЦИЯ

| Файл | Назначение |
|------|-----------|
| `doc/INDEX.md` | Индекс всей документации |
| `doc/SESSION_NOTES.md` | Заметки сессий (42) |
| `doc/PATTERNS.md` | Паттерны и правила разработки |
| `doc/CODE_AUDIT.md` | Аудит кода |
| `doc/PLAN_V1.1.3.33.md` | План реализации v1.1.3.33+ |
| `doc/REMOTE_AGENT.md` | Remote Agent (справочная) |
| `doc/ChatListActivity_v1_REFERENCE.kt` | v1 reference (2802 LOC) |
| `../CHANGELOG.md` | История изменений |

---

## CHANGELOG

### v1.1.3.33 (сессия 42) — NewChatActivity рефакторинг + Error handling
- refactor: NewChatActivity 1473→754 LOC (-49%), 6 новых модулей в ui/chat/message/
- refactor: унификация error handling — все gRPC модули используют ErrorHandler.handle()
- feat: ChatListViewModel.error StateFlow + Snackbar в ChatListActivity
- fix: исправлены ошибки компиляции (импорты Lifecycle, isVisible, toColorInt, edit)

### v1.1.3.32 (сессии 39-41) — ChatList stability + модуляризация
- fix: loadChats() — при timeout НЕ перезаписывать allChats
- fix: read receipts — indexOfFirst проверка перед map
- refactor: ChatListActivity 1085→~600 LOC (-45%), 3 новых модуля (FABs, Navigation, Auth)
- fix: табы переупорядочены: Все → Группы → ИИ чаты
- fix: "AI" → "AI Chats" / "ИИ" → "ИИ чаты"
- fix: исправлена ошибка компиляции в NewChatBottomSheet

### v1.1.3.31 (сессии 37-38) — Read receipts + модуляризация
- feat: read receipts broadcast — readReceiptEvent SharedFlow → ChatListViewModel
- refactor: ChatListActivity 1470→1085 LOC (-26%), 4 новых модуля

### v1.1.3.30 (сессия 36) — FAB + Favorites
- feat: FAB [+] восстановлен — ActionBottomSheet + SearchableListBottomSheet
- fix: Favorites убран из секций, добавлен в шторку профиля

### v1.1.3.28-29 (сессии 33-35) — gRPC модули + UI
- refactor: RealGrpcClient 3810→882 LOC (-77%), 12 модулей
- feat: кастомные темы для AppBarLayout, TabLayout

---

## КЛЮЧЕВЫЕ РЕШЕНИЯ

| Решение | Обоснование |
|---------|-------------|
| v1/v2 разделение | Новые файлы в ui/chatlist/, v1 без изменений |
| Long press = режим выбора | ActionMode toolbar с Pin/Delete/Archive |
| fetchServerInfo strategy | Dev: skip HTTP, assume v2. Prod: try HTTP /info, fallback v1 |
| Optimistic READY | gRPC channel подключается лениво |
| onCancellation = {} | Обязательно в Kotlin 2.3.21 |
| Keepalive 30s/10s | Для мобильных сетей |
| Poll 30s | Уменьшение нагрузки на сервер |
| Gradle wrapper удалён | OOM protection на сервере |
| ErrorHandler единый | Все ошибки через ErrorHandler → AppLog + Log |
| Chat модули | 6 делегатов вместо монолитного NewChatActivity |
