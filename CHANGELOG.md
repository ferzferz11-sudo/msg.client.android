# Lava Messenger — Android Changelog

## [1.2.0.5] - 2026-06-18

### Исправления

**Контакты:**
- При создании чата/секретного чата/конференции показываются только добавленные контакты (был весь список пользователей)
- `getContacts()` — fallback `fetchUserId` если `currentUserId` пуст

**Авторизация:**
- Авто-вход с протухшим JWT: refresh → если не выходит → перелогин по сохранённому паролю
- Chat stream: удалён password fallback, теперь только JWT (deprecated v1)
- `GetChats` v1 → `GetChatsV2` (JWT-based, server uses `GetUserID(ctx)`)
- При холодном запуске с expired токеном — `AUTH_FAILED` вместо застревания

**Навигация:**
- Возврат к родительской шторке (Settings/Additional Settings) после Back из Activity
- Восстановлен `isNavigatingDeeper` + `settingsActivityLauncher`/`editProfileLauncher`

**UI:**
- Диалог "О программе" показывает версию сервера из `GrpcClient.serverVersion`

### Рефакторинг

**gRPC модули:**
- `GrpcChatListClient` (648→255 LOC) → разделён на 3:
  - `GrpcChatClient` (~250 LOC) — getChats, create/delete, participants, settings
  - `GrpcChatListV2Client` (~120 LOC) — pin/unpin, search, archive, pinned messages
  - `GrpcChatAuxClient` (~130 LOC) — users/AI chats/FCM/mute
- `RealGrpcClient`: delegate to new clients, удалён `unaryCallChatListV2` (-40 LOC)
- Удалены дубликаты `getChats`/`getAllChats` из `GrpcChatListClient`

### Документация
- `PROMPT_ANDROID_DEPRECATED.md` — аудит deprecated v1 паттернов, чистка клиента
- Обновлён `PLAN.md`, `PATTERNS.md`, `INDEX.md`

---

## [1.1.3.38] - 2026-06-18

### 🚀 v2 Клиент для v2 Сервера

Новая версия клиента с улучшениями для v2 сервера.

### Улучшения

**Список чатов:**
- Имя собеседника отображается напрямую в личных чатах
- Тулбар с прозрачностью 30% и тенью — тап по заголовку или аватару открывает шторку профиля
- Убраны заголовки секций (All chats, Pinned) — список чатов стал чище
- Предзагрузка пользователей при открытии — создание чата доступно сразу

**Профиль и настройки:**
- Шторка профиля: верхняя секция (аватар + имя) кликабельная → редактирование профиля
- Переключение языка синхронизируется с сервером

**Создание чатов:**
- Секретные чаты, обычные чаты и конференции — показывают всех пользователей, а не только контакты

**Темы:**
- Фон темы корректно применяется к списку чатов (chatListBackground)
- Экран настроек тем больше не перехватывает фон чата

**Typing индикатор:**
- Корректная фильтрация — свой typing не отображается в subtitle

**AI:**
- Исправлен вызов `deployAgentTaskStream` в HermesChatUseCase
- Исправлен scope leak в OwlChatUseCase

### Документация
- Очищена папка doc/ — создан PLAN.md с актуальным анализом
- Обновлены INDEX.md, PATTERNS.md, REMOTE_AGENT.md

---

## [1.1.3.35] - 2026-06-18

### Рефакторинг: GrpcClient Facade Оптимизация
- **GrpcClient: 780 → 106 LOC (-86%)**
- Создан `GrpcClientExtensions.kt` (~600 LOC) с extension functions по доменам:
  - Auth, Chat, Message, Profile, Theme, Draft, Favorite, Call, AI/Hermes, RemoteAgent, SecretChat, Notification
- В GrpcClient.kt оставлено: StateFlow declarations, scope, connect/disconnect, startChat, loadHistory, setRoomId, loadUsers, V2 service detection
- Добавлен `import GrpcClientExtensions.*` в 29 UI файлов
- RealGrpcClient.scope: `private` → `internal`

---

## [1.1.3.34] - 2026-06-17

### Тесты: Unit-тесты для gRPC клиента
- **42 unit-теста** для gRPC модулей (было 0):
  - GrpcAuthClientTest (10) — signIn, signUp, refreshToken, signOut, revokeDevice
  - GrpcChatListClientTest (8) — getChats, pinChat, searchChats, deleteChat
  - GrpcMessageClientTest (8) — sendMessage, addLocalMessage, loadHistory, markRead
  - GrpcConnectionManagerTest (6) — connect, disconnect, reconnect, isConnectedTo
  - GrpcClientFacadeTest (6) — connectionState mapping, StateFlow probing
  - GrpcUnaryCallHelperTest (4) — unaryCall, null channel, error handling
- Добавлены зависимости: mockk 1.13.8, turbine 1.0.0, coroutines-test 1.7.3
- Созданы тестовые утилиты: TestChannelFactory, TestDatabaseFactory, FlowTestExtensions

### Документация
- Оптимизация документации: удалены 9 устаревших файлов, консолидированы актуальные
- Создан ANALYSIS_AND_PLAN.md — анализ + план оптимизации (v1.1.3.35-40)

### Исправления инфраструктуры тестов
- AppLog: try-catch вокруг android.util.Log.d для работы в unit-тестах
- Все тесты: Dispatchers.Main → Dispatchers.Unconfined (Main недоступен без Android)
- Все тесты: переведены с Mockito на MockK
- Все тесты: исправлены runtime ошибки ( smart cast, relaxed mock returning null)
- Proto data classes: newBuilder() заменены на прямые конструкторы

---

## [1.1.3.33] - 2026-06-17

### Рефакторинг: NewChatActivity делегаты
- **NewChatActivity: 1473 → 754 строк (-49%)**
- Вынесено 6 модулей в `ui/chat/message/`:
  - ChatToolbarDelegate (341) — toolbar, avatar, subtitle, navigation
  - ChatInputDelegate (567) — input, send, attachments, audio, emoji, mentions
  - ChatSelectionDelegate (236) — selection mode, copy/pin/delete/forward
  - ChatSearchDelegate (135) — in-chat search
  - ChatE2EEDelegate (72) — E2EE key exchange, encrypt/decrypt
  - ChatMessageMenuDelegate (106) — reactions, context menu

### Рефакторинг: Унификация error handling
- Все gRPC модули используют `ErrorHandler.handle()` (было `Log.e`)
- ChatListViewModel.error StateFlow + Snackbar в ChatListActivity

### Исправлено
- Ошибки компиляции: импорты Lifecycle, isVisible, toColorInt, edit
- Порядок инициализации: setupDelegates после setupRecyclerView

---

## [1.1.3.32] - 2026-06-17

### Рефакторинг: Разбиение ChatListActivity
- **ChatListActivity: 1085 → ~600 строк (-45%)**
- Вынесено 3 модуля в `ui/chatlist/`:
  - ChatListFABs (470) — FABs + action sheets + AI bottom sheet
  - ChatListNavigation (60) — navigateToChat
  - ChatListAuth (212) — auth dialogs

### Исправлено
- loadChats() — при timeout НЕ перезаписывать allChats
- read receipts — indexOfFirst проверка перед map
- Табы переупорядочены: Все → Группы → ИИ чаты
- "AI" → "AI Chats" / "ИИ" → "ИИ чаты"

---

## [1.1.3.31] - 2026-06-17

### Новое: Read receipts broadcast
- readReceiptEvent SharedFlow → ChatListViewModel → clear unread count

### Рефакторинг
- ChatListActivity 1470→1085 строк (-26%), 4 новых модуля

---

## [1.1.3.30] - 2026-06-16

### Новое: FAB + Favorites
- FAB [+] восстановлен — ActionBottomSheet + SearchableListBottomSheet
- Favorites убран из секций, добавлен в шторку профиля

---

## [1.1.3.28-29] - 2026-06-16

### Рефакторинг: gRPC модули
- **RealGrpcClient: 3810 → 882 строк (-77%)**
- 12 модулей вместо God Object
- Кастомные темы для AppBarLayout, TabLayout
