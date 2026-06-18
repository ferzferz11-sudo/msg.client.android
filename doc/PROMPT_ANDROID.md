# Lava Messenger — Android Session Prompt

**Дата:** 2026-06-18 | **Версия:** v1.1.3.35 | **Ветка:** feat/1.1.3.x

---

## СТАТУС

v1.1.3.35 — Фаза 4 завершена. GrpcClient 780→~400 LOC. Компиляция проходит.
Фаза 5 (v1.1.3.36): AI Chats domain layer.

---

## АРХИТЕКТУРА

### gRPC Client
```
GrpcClient (~400 LOC) — StateFlow facade + inline domain delegates
    ↓
RealGrpcClient (883 LOC) — orchestrator
    ├── GrpcConnectionManager (167), GrpcAuthClient (232), GrpcTypingClient (87)
    ├── GrpcCallClient (125), GrpcChatListClient (641), GrpcProfileClient (506)
    ├── GrpcDraftClient (86), GrpcFavoritesClient (120), GrpcMessageClient (344)
    ├── GrpcServerDiscoveryClient (145), GrpcMarshallers (1395)
    ├── HermesGrpc (1876), OwlGrpc (1145)
    └── AiChatGrpc, SecretChatGrpc, ProfileClient
```

### ChatList
```
ChatListActivity (~364) → ChatListToolbar (232), ChatListTabs (30), ChatListActionMode (120)
├── ChatListSearch (56), ChatListFABs (470), ChatListNavigation (60)
├── ChatListAuth (212), ChatListViewModel (295), ChatListSections (20)
└── UpdateCoordinator (245)
```

### Chat
```
NewChatActivity (~754) → ChatToolbarDelegate (341), ChatInputDelegate (567)
├── ChatSelectionDelegate (236), ChatSearchDelegate (135)
├── ChatE2EEDelegate (72), ChatMessageMenuDelegate (106)
```

### Серверы
| | Dev | Prod |
|--|-----|------|
| gRPC | 50052 | 50051 |
| HTTP | 8083 | 8082 |

---

## ПРИОРИТЕТЫ

### ✅ Завершено
- Фаза 0-2: Тестирование, NewChatActivity рефакторинг, Error handling ✅
- Фаза 3: Unit-тесты для gRPC клиента (42 теста) ✅ v1.1.3.34
- Фаза 4: GrpcClient facade оптимизация (780→~400 LOC) ✅ v1.1.3.35

### 🟡 Текущая (v1.1.3.36)
1. **Фаза 5: AI Chats domain layer** (HermesGrpc 1876 + OwlGrpc 1145 → domain)
   - Выделить AI логику в отдельный domain слой
   - Подробный план: `doc/ANALYSIS_AND_PLAN.md`

### 🟢 Следующие
2. **Фаза 6** (v1.1.3.37): NewChatActivity финальный рефакторинг (754→<400 LOC)
3. **Фаза 7** (v1.1.3.38): ProfileActivity рефакторинг (719→<300 LOC)
4. **Фаза 8** (v1.1.3.39): GrpcChatListClient разделение (642→3x200)
5. **Фаза 9** (v1.1.3.40): MessageAdapter разделение (870→<300 LOC)

### 📦 Отложено
- Pagination для чатов, Incremental history loading, Certificate pinning
- Qdrant + CLIP, Shared element transitions, ConferenceLobbyActivity рефакторинг

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
| `doc/SESSION_NOTES.md` | Заметки сессий (42-44) |
| `doc/PATTERNS.md` | Паттерны и правила разработки |
| `doc/CODE_AUDIT.md` | Аудит кода |
| `doc/ANALYSIS_AND_PLAN.md` | Анализ + план оптимизации (v1.1.3.35-40) |
| `doc/REMOTE_AGENT.md` | Remote Agent (справочная) |
| `doc/ChatListActivity_v1_REFERENCE.kt` | v1 reference (2802 LOC) |
| `../CHANGELOG.md` | История изменений |

---

## CHANGELOG

### v1.1.3.35 (сессия 44) — GrpcClient Facade Оптимизация
- refactor: GrpcClient 780→~400 LOC (-49%) — inline delegates вместо extension functions
- refactor: удалён FCMLogsActivity (дублировал LogViewerActivity)
- refactor: удалён пункт "Журнал ошибок" из шторки дополнительных настроек
- refactor: SuperAdminActivity меню "Logs" → LogViewerActivity
- ⚠️ Extension functions через star import не работают в Kotlin — все методы вернулись в GrpcClient.kt

### v1.1.3.34 (сессия 43) — Unit-тесты для gRPC клиента
- test: 42 unit-теста для gRPC модулей (Auth, ChatList, Message, ConnectionManager, Facade, UnaryCallHelper)
- test: добавлены mockk, turbine, coroutines-test зависимости
- test: созданы тестовые утилиты (TestChannelFactory, FlowTestExtensions)
- docs: оптимизация документации (удалены 9 устаревших файлов)

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
| ErrorHandler единый | Все ошибки через ErrorHandler → AppLog + Log |
| Chat модули | 6 делегатов вместо монолитного NewChatActivity |
| MockK для тестов | Не Mockito — не добавлен в deps |
| GrpcClient inline delegates | Extension functions не работают через star import в Kotlin |
