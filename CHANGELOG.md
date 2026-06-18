# Lava Messenger — Android Changelog

## [1.1.3.38] - 2026-06-18

### Исправления
- **Имя чата в списке**: для личных чатов теперь отображается имя собеседника вместо "Я & собеседник"
- **Тулбар списка чатов**: прозрачность 30% + тень 6dp, заголовок "Чаты" открывает шторку профиля
- **Серверы**: убран переход на серверы из тулбара (доступны через шторку → доп. настройки)
- **Язык**: переключение языка синхронизируется с сервером; добавлен `attachBaseContext` в ChatListActivity
- **Typing индикатор**: фильтрация своего typing по username И userId (исправлена проблема с UUID в subtitle)
- **Фон темы**: исправлены ID — `chatListBackground` для списка чатов, `themePreviewBackground` для экрана тем
- **Секции**: убраны заголовки секций (All chats, Pinned) из списка чатов
- **Контакты**: предзагрузка пользователей при открытии списка чатов
- **Шторка профиля**: верхняя секция (аватар + имя) кликабельная → редактирование профиля
- **Компиляция**: исправлены `deployTaskStream` → `deployAgentTaskStream`, scope leak `retryDelay`

### Документация
- Очищена папка doc/ — удалены устаревшие планы, создан PLAN.md
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
