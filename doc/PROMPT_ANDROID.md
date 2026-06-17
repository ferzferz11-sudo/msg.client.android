# Lava Messenger — Android Session Prompt

**Дата:** 2026-06-17 | **Версия:** v1.1.3.34 | **Ветка:** feat/1.1.3.x

---

## СТАТУС

v1.1.3.34 — в разработке. Фаза 3: Unit-тесты для gRPC клиента.

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

### 🟡 Текущая (v1.1.3.34)
1. **Фаза 3: Unit-тесты для gRPC клиента** — 0 тестов → >20
   - Подробный план см. ниже

### 🟢 Следующие (v1.1.3.35-36)
2. **Фаза 4** (v1.1.3.35): GrpcClient facade оптимизация (780→<400 LOC)
3. **Фаза 5** (v1.1.3.36): AI Chats domain layer (выделение из gRPC слоя)

### 📦 Отложено
- Pagination для чатов
- Incremental history loading
- Certificate pinning
- Qdrant + CLIP
- Shared element transitions
- ProfileActivity рефакторинг (719 LOC)
- ConferenceLobbyActivity рефакторинг (581 LOC)

---

## ПЛАН РЕАЛИЗАЦИИ v1.1.3.34 — ФАЗА 3: UNIT-ТЕСТЫ ДЛЯ gRPC КЛИЕНТА

### Проблема
0 unit-тестов для gRPC клиента. Код работает, но нет защиты от регрессий.

### Стратегия тестирования

**Инструменты:**
- JUnit 4 (уже используется в ErrorHandlerTest, ChatAdapterTest)
- MockK для мокирования gRPC stubs
- `grpc-inprocess` для in-process сервера (без реального сервера)
- Turbine для тестирования Flow/StateFlow

**Структура тестов:**
```
app/src/test/java/lavender/client/android/data/grpc/
├── GrpcAuthClientTest.kt      — 10 тестов
├── GrpcChatListClientTest.kt  — 8 тестов
├── GrpcMessageClientTest.kt   — 8 тестов
├── GrpcConnectionManagerTest.kt — 6 тестов
├── GrpcClientFacadeTest.kt    — 6 тестов
└── GrpcUnaryCallHelperTest.kt — 4 теста
```

### Детальный план по модулям

#### 1. GrpcAuthClientTest (10 тестов)
Файл: `app/src/test/java/lavender/client/android/data/grpc/GrpcAuthClientTest.kt`

```
Тесты:
- testSignInV2_Success — валидные credentials → success=true, token не пустой
- testSignInV2_WrongPassword — неправильный пароль → success=false
- testSignInV2_UserNotFound — несуществующий пользователь → success=false
- testSignInV2_EmptyUsername — пустой username → success=false
- testSignInV2_EmptyPassword — пустой password → success=false
- testSignUpV2_Success — новый пользователь → success=true
- testSignUpV2_DuplicateUsername — существующий username → success=false
- testRefreshToken_Success — валидный refresh token → новая пара токенов
- testSignOut_Success — валидный sign out → success=true
- testRevokeDevice_Success — revoke device → success=true

Мокирование:
- ManagedChannel → mock, возвращает mock stub
- Проверять callback-и через CountDownLatch
```

#### 2. GrpcChatListClientTest (8 тестов)
Файл: `app/src/test/java/lavender/client/android/data/grpc/GrpcChatListClientTest.kt`

```
Тесты:
- testGetChats_Success — сервер возвращает список чатов → callback с чатами
- testGetChats_EmptyList — сервер возвращает пустой список → callback с emptyList
- testGetChats_NullChannel — channel = null → callback с emptyList, без crash
- testPinChat_V2Supported — v2 сервер → pin вызывается
- testPinChat_V1Fallback — v1 сервер → возвращает false
- testSearchChats_V2Supported — v2 сервер → search возвращает результаты
- testSearchChats_V1Fallback — v1 сервер → возвращает emptyList
- testDeleteChat_Success — delete chat → callback с success

Мокирование:
- ProfileClient.isChatV2Supported() → контролируем через mock
- ManagedChannel → mock
```

#### 3. GrpcMessageClientTest (8 тестов)
Файл: `app/src/test/java/lavender/client/android/data/grpc/GrpcMessageClientTest.kt`

```
Тесты:
- testSendMessage_ValidMessage — message отправляется через requestObserver
- testSendMessage_NullObserver — observer = null → нет crash, лог ошибки
- testAddLocalMessage — message добавляется в StateFlow
- testLoadHistory_Success — история загружается → messages StateFlow обновляется
- testLoadHistory_CacheFirst — кэш загружается первым
- testMarkRead_Ready — connection READY → markRead вызывается
- testMarkRead_NotReady — connection не READY → pendingReads добавляется
- testResendPendingReads — pending reads отправляются при reconnect

Мокирование:
- AppDatabase → in-memory Room database (androidTest)
- StateFlow → проверяем значения через Turbine
```

#### 4. GrpcConnectionManagerTest (6 тестов)
Файл: `app/src/test/java/lavender/client/android/data/grpc/GrpcConnectionManagerTest.kt`

```
Тесты:
- testConnect_Success — валидный адрес → connectionStatus = READY
- testConnect_AlreadyConnected — тот же адрес → без переподключения
- testDisconnect — disconnect → connectionStatus = DISCONNECTED
- testReconnect — reconnect → вызывается connect с forceReconnect
- testIsConnectedTo_True — совпадение адреса + READY → true
- testIsConnectedTo_False — другой адрес → false

Мокирование:
- OkHttpChannelBuilder → нельзя мокать напрямую, используем in-process канал
- Или: тестируем через реальный in-process gRPC канал
```

#### 5. GrpcClientFacadeTest (6 тестов)
Файл: `app/src/test/java/lavender/client/android/data/grpc/GrpcClientFacadeTest.kt`

```
Тесты:
- testConnectionState_MapsReady — connectionStatus = READY → connectionState = true
- testConnectionState_MapsDisconnected — connectionStatus = DISCONNECTED → connectionState = false
- testStateFlow_ConnectionStatus — проброс connectionStatus из RealGrpcClient
- testStateFlow_Messages — проброс messages из RealGrpcClient
- testStateFlow_Error — проброс error из RealGrpcClient
- testChatV2Supported_Delegates — isChatV2Supported делегирует в ProfileClient
```

#### 6. GrpcUnaryCallHelperTest (4 теста)
Файл: `app/src/test/java/lavender/client/android/data/grpc/GrpcUnaryCallHelperTest.kt`

```
Тесты:
- testUnaryCall_Success — валидный запрос → возвращает response
- testUnaryCall_NullChannel — channel = null → возвращает null
- testUnaryCall_Error — сервер возвращает error → возвращает null
- testUnaryCallWithClass_Success — class-based variant → возвращает response
```

### Итого: ~42 теста

### Подготовка инфраструктуры тестов

1. **Добавить зависимости в build.gradle:**
   ```
   testImplementation "io.mockk:mockk:1.13.8"
   testImplementation "app.cash.turbine:turbine:1.0.0"
   testImplementation "org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3"
   ```

2. **Создать тестовые утилиты:**
   - `TestChannelFactory` — создаёт mock ManagedChannel
   - `TestDatabaseFactory` — создаёт in-memory Room database
   - `FlowTestExtensions` — extension для тестирования StateFlow/SharedFlow

3. **Порядок реализации:**
   - Шаг 1: Добавить зависимости
   - Шаг 2: Создать тестовые утилиты
   - Шаг 3: GrpcAuthClientTest (самый независимый модуль)
   - Шаг 4: GrpcUnaryCallHelperTest
   - Шаг 5: GrpcChatListClientTest
   - Шаг 6: GrpcMessageClientTest
   - Шаг 7: GrpcConnectionManagerTest
   - Шаг 8: GrpcClientFacadeTest
   - Шаг 9: Исправление ошибок, финализация

### Критерии приёмки
- [ ] Все 42 теста проходят (`./gradlew testDebugUnitTest`)
- [ ] Покрытие gRPC модулей > 70%
- [ ] Тесты изолированы (не зависят от порядка выполнения)
- [ ] Тесты не требуют реального сервера
- [ ] CI-совместимость (можно запускать в GitHub Actions)

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
| `doc/REMOTE_AGENT.md` | Remote Agent (справочная) |
| `doc/ChatListActivity_v1_REFERENCE.kt` | v1 reference (2802 LOC) |
| `../CHANGELOG.md` | История изменений |

---

## CHANGELOG

### v1.1.3.34 (сессия 43) — Unit-тесты для gRPC клиента
- test: 42 unit-теста для gRPC модулей (Auth, ChatList, Message, ConnectionManager, Facade, UnaryCallHelper)
- test: добавлены mockk, turbine, coroutines-test зависимости
- test: созданы тестовые утилиты (TestChannelFactory, TestDatabaseFactory)

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
