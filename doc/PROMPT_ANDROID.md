# Lava Messenger — Android Session Prompt

**Дата:** 2026-06-17 | **Версия:** v1.1.3.35 | **Ветка:** feat/1.1.3.x

---

## СТАТУС

v1.1.3.35 — планирование. Фаза 3 завершена (42 unit-теста). Фаза 4: GrpcClient facade оптимизация.

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

### ✅ Завершено
- Фаза 0: Тестирование v1.1.3.32 ✅
- Фаза 1: NewChatActivity рефакторинг (1473→754 LOC, -49%) ✅
- Фаза 2: Унификация error handling ✅
- Фаза 3: Unit-тесты для gRPC клиента (42 теста) ✅ v1.1.3.34

### 🟡 Текущая (v1.1.3.35)
1. **Фаза 4: GrpcClient facade оптимизация** (780→<400 LOC)
   - Заменить proxy-методы на extension functions
   - Группировать по доменам
   - Подробный план: `doc/ANALYSIS_AND_PLAN.md`

### 🟢 Следующие
2. **Фаза 5** (v1.1.3.36): AI Chats domain layer (HermesGrpc 1876 + OwlGrpc 1145 → domain)
3. **Фаза 6** (v1.1.3.37): NewChatActivity финальный рефакторинг (754→<400 LOC)
4. **Фаза 7** (v1.1.3.38): ProfileActivity рефакторинг (719→<300 LOC)
5. **Фаза 8** (v1.1.3.39): GrpcChatListClient разделение (642→3x200)
6. **Фаза 9** (v1.1.3.40): MessageAdapter разделение (870→<300 LOC)

### 📦 Отложено
- Pagination для чатов
- Incremental history loading
- Certificate pinning
- Qdrant + CLIP
- Shared element transitions
- ConferenceLobbyActivity рефакторинг (581 LOC)

---

## ПЛАН v1.1.3.35 — ФАЗА 4: GrpcClient FACADE ОПТИМИЗАЦИЯ

### Проблема
GrpcClient — 780 facade-методов, каждый из которых просто делегирует вызов в `realGrpcClient`. 

### Решение
Заменить proxy-методы на extension functions + группировку по доменам.

### Шаги

1. Создать `GrpcClientExtensions.kt` с extension functions:
```kotlin
// Auth domain
fun GrpcClient.signInV2(...) = realGrpcClient.signInV2(...)
fun GrpcClient.signUpV2(...) = realGrpcClient.signUpV2(...)
fun GrpcClient.refreshToken(...) = realGrpcClient.refreshToken(...)

// Chat domain  
fun GrpcClient.getChats(...) = realGrpcClient.getChats(...)
fun GrpcClient.getAllChats(...) = realGrpcClient.getAllChats(...)

// Message domain
fun GrpcClient.sendMessage(...) = realGrpcClient.sendMessage(...)
fun GrpcClient.loadHistory(...) = realGrpcClient.loadHistory(...)
```

2. Убрать proxy-методы из `GrpcClient.kt`
3. Оставить только: StateFlow declarations, scope, connect/disconnect, version checks
4. Цель: GrpcClient < 300 LOC

### Критерии приёмки
- [ ] GrpcClient < 300 LOC
- [ ] Все вызовы из UI работают через extension functions
- [ ] Тесты проходят
- [ ] Нет изменений в поведении

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
| `doc/SESSION_NOTES.md` | Заметки сессий (42-43) |
| `doc/PATTERNS.md` | Паттерны и правила разработки |
| `doc/CODE_AUDIT.md` | Аудит кода |
| `doc/ANALYSIS_AND_PLAN.md` | Анализ + план оптимизации (v1.1.3.35-40) |
| `doc/REMOTE_AGENT.md` | Remote Agent (справочная) |
| `doc/ChatListActivity_v1_REFERENCE.kt` | v1 reference (2802 LOC) |
| `../CHANGELOG.md` | История изменений |

---

## CHANGELOG

### v1.1.3.35 (следующая) — GrpcClient facade оптимизация
- refactor: GrpcClient 780→<400 LOC через extension functions
- refactor: группировка proxy-методов по доменам

### v1.1.3.34 (сессия 43) — Unit-тесты для gRPC клиента
- test: 42 unit-теста для gRPC модулей (Auth, ChatList, Message, ConnectionManager, Facade, UnaryCallHelper)
- test: добавлены mockk, turbine, coroutines-test зависимости
- test: созданы тестовые утилиты (TestChannelFactory, FlowTestExtensions)
- docs: оптимизация документации (удалены 9 устаревших файлов)
- docs: создан ANALYSIS_AND_PLAN.md

### v1.1.3.33 (сессия 42) — NewChatActivity рефакторинг + Error handling
- refactor: NewChatActivity 1473→754 LOC (-49%), 6 новых модулей в ui/chat/message/
- refactor: унификация error handling — все gRPC модули используют ErrorHandler.handle()

### v1.1.3.32 (сессии 39-41) — ChatList stability + модуляризация
- fix: loadChats() — при timeout НЕ перезаписывать allChats
- refactor: ChatListActivity 1085→~600 LOC (-45%), 3 новых модуля

### v1.1.3.31 (сессии 37-38) — Read receipts + модуляризация
- feat: read receipts broadcast — readReceiptEvent SharedFlow → ChatListViewModel
- refactor: ChatListActivity 1470→1085 LOC (-26%), 4 новых модуля

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
| MockK для тестов | Не Mockito — не добавлен в deps |
