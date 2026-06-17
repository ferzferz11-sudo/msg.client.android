# Lava Messenger — Android Session Prompt

**Дата:** 2026-06-17 | **Версия:** v1.1.3.32 | **Ветка:** feat/1.1.3.x

---

## СТАТУС

v1.1.3.32 — завершена. Тег выпущен. Релиз APK отложен до тестирования на реальных чатах.

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
├── ChatListViewModel (290) — ViewModel
├── ChatListSections (20) — sections
└── UpdateCoordinator (245) — updates
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

### 🔴 Высокий (v1.1.3.33)
- Тестирование v1.1.3.32 на реальных чатах (ferz локально)
- Найти и исправить баги при тестировании

### 🟡 Средний (v1.1.3.34-38)
1. NewChatActivity рефакторинг (1473 → <400 LOC) — v1.1.3.34
2. Унификация error handling — v1.1.3.35
3. Тесты для gRPC клиента — v1.1.3.36
4. GrpcClient facade оптимизация (780 → <400 LOC) — v1.1.3.37
5. AI Chats domain layer — v1.1.3.38

### 🟢 Отложено
- Pagination для чатов
- Incremental history loading
- Certificate pinning
- Qdrant + CLIP
- Shared element transitions

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
| `doc/SESSION_NOTES.md` | Заметки сессий |
| `doc/PATTERNS.md` | Паттерны и правила разработки |
| `doc/CODE_AUDIT.md` | Аудит кода |
| `doc/PLAN_V1.1.3.33.md` | План реализации v1.1.3.33+ |
| `doc/REMOTE_AGENT.md` | Remote Agent (справочная) |
| `doc/ChatListActivity_v1_REFERENCE.kt` | v1 reference (2802 LOC) |
| `../CHANGELOG.md` | История изменений |

---

## CHANGELOG

### v1.1.3.32 — ChatList stability + модуляризация
- fix: loadChats() — при timeout НЕ перезаписывать allChats
- fix: read receipts — indexOfFirst проверка перед map
- refactor: ChatListActivity 1085→~600 LOC (-45%), 3 новых модуля (FABs, Navigation, Auth)
- fix: табы переупорядочены: Все → Группы → ИИ чаты
- fix: "AI" → "AI Chats" / "ИИ" → "ИИ чаты"
- fix: исправлена ошибка компиляции в NewChatBottomSheet
- docs: объединены TASKS.md + PROMPT_ANDROID.md

### v1.1.3.31 — Read receipts + модуляризация
- feat: read receipts broadcast — readReceiptEvent SharedFlow → ChatListViewModel
- refactor: ChatListActivity 1470→1085 LOC (-26%), 4 новых модуля

### v1.1.3.30 — FAB + Favorites
- feat: FAB [+] восстановлен — ActionBottomSheet + SearchableListBottomSheet
- fix: Favorites убран из секций, добавлен в шторку профиля

### v1.1.3.28-29 — gRPC модули + UI
- refactor: RealGrpcClient 3810→882 LOC (-77%), 12 модулей
- feat: кастомные темы для AppBarLayout, TabLayout

### v1.1.3.24-25 — Auth + Updates
- feat: полный auth flow — LoginBottomSheet + RegisterBottomSheet
- feat: UpdateManager — silent check, manual check, progress dialog

### v1.1.3.23 — Единый Activity
- Удалён v1 ChatListActivity (2802 LOC)
- ChatListActivityV2 → ChatListActivity (единый)

### v1.1.3.22 — Rename
- Lavender → Lava

### v1.1.3.21 — Push
- FCM push notifications

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
