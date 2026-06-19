# Lava Messenger — Android Changelog

## [1.2.0.14] - 2026-06-19

### Исправления

**Chat subtitle last seen:**
- В direct-чатах вместо "офлайн" показывается время последнего входа ("был(а) в сети X мин/ч/дн назад")
- `allUsers` добавлен в combine flow в NewChatActivity — subtitle обновляется при загрузке данных

**Deleted chat persistence fix:**
- `deleteChat()` теперь удаляет чат из Room DB — удалённые чаты больше не появляются после перезапуска
- `chatDeletedEvent` подписан в `ChatListViewModel` — чаты удаляются из списка в реальном времени

**Chat list sync:**
- `newMessageEvent` подписан в `ChatListViewModel` — чат-лист обновляется в реальном времени когда сообщения приходят в другие комнаты
- Добавлен periodic polling каждые 30с для обновления чат-листа
- `ChatDao` кэширование: чаты загружаются из кэша при старте (мгновенное отображение), синхронизируются с сервером в фоне
- `ChatEntity` расширен: `isPinned`, `isArchived`, `pinnedAt` (миграция 9→10)
- `SplashActivity` больше не стирает Room кэш при каждом запуске — кэш очищается только при logout
- `markAsRead` вызывается при тапе на чат с непрочитанными сообщениями

### UI

**Action mode toolbar:**
- Все 4 иконки action mode (pin/mute/archive/delete) `showAsAction="always"` — не уезжают в overflow меню

## [1.2.0.13] - 2026-06-19

### Исправления

**Admin menu + Feedback:**
- `isSuperAdmin` сбрасывался в false при каждом `connect()` — race condition с async `fetchAdminStatus()`. Теперь: сброс только при `forceReconnect`
- `adminUserId` сохраняется в SharedPreferences при обнаружении (из chat stream или profile). Восстанавливается при старте — feedback работает сразу после перезапуска
- `fetchAdminStatus()` теперь сохраняет `adminUserId` из профиля (profile.isSuperAdmin + profile.userId)

**Admin discovery for non-admin users:**
- `UserInfoProto` добавлены `userId` (field 6) и `isSuperAdmin` (field 7) — серверный `GetAllUsers` теперь возвращает эти поля
- `loadUsers()` сканирует `allUsers` на `isSuperAdmin` и устанавливает `adminUserId` — feedback чат работает для ЛЮБОГО пользователя
- `openFeedbackChat()` retry: если `adminUserId` пуст → `loadUsers()` + retry через 1.5с
- `connect()` восстанавливает `isSuperAdmin` из SharedPreferences при старте
- `fetchAdminStatus()` вызывается при READY (не в `connect()` — канал ещё не готов)
- `logout()` очищает `is_super_admin` и `admin_user_id` из SharedPreferences

## [1.2.0.12] - 2026-06-18

### Исправления

**Диалог "О программе":**
- Текст приложения: "Лава: платформа защищенных бизнес-коммуникаций" вместо "Lavender Messenger"
- Версия клиента убрана — показывается только версия сервера
- **Поделиться:** текст "Лава: платформа..." + ссылка http://13.140.25.249
- **Отзыв:** открывается личный чат с админом (вместо email). Админ определяется динамически через `adminUserId` из chat stream (не хардкод username)
- Добавлен `adminUserId` StateFlow в GrpcClient/RealGrpcClient — отслеживает userId админа из сообщений с `isSuperAdmin = true`

**i18n:**
- Добавлены строки: `about_description`, `admin_not_found` (EN + RU)

## [1.2.0.11] - 2026-06-18

### Рефакторинг

**ProfileActivity → ProfileViewModel:**
- Бизнес-логика перенесена в ProfileViewModel: loadUserProfile, loadGroupData, updateChatName, updateChatSettings, removeParticipant, addParticipants, uploadGroupAvatar, resizeImage
- ProfileActivity: 719 → ~400 строк

**MessageAdapter split:**
- bind() 600 строк → 12 выделенных методов: bindAlignment, bindBubbleStyle, bindCallMessage, bindReadStatus, bindAudioContent, bindTextContent, bindImageContent, bindReactions, bindReplyQuote, bindSelectionIndicator, bindContainerClicks, bindPinnedBadge
- MessageAdapter: 870 → 324 строки (-63%)

## [1.2.0.10] - 2026-06-18

### Исправления

**Диалог "О программе":**
- Кнопки "Что нового", "Отзыв", "Поделиться" не работали — отсутствовали click listeners (только "Закрыть" был привязан)
- Добавлены: What's New → ChangelogActivity, Feedback → email intent, Share → shareApp()
- **Drag handle отсутствовал** — dialog_about.xml не использовал стандартный wrapper (MaterialCardView + dragHandle). Обновлён layout: добавлен dragHandle, contentContainer, MaterialCardView

**i18n:**
- Добавлена строка `no_email_client` (EN + RU)

## [1.2.0.9] - 2026-06-18

### Исправления

**Токен/Сессия (критический):**
- **startTokenRefresh не вызывался после перезапуска приложения** — initFromPrefs() восстанавливал JWT сессию, но не запускал периодический refresh. Токен протухал молча.
- **waitForConnectionAndReLogin не запускал refresh** — после успешного обновления токена при старте, периодический refresh не начинался.
- **Chat stream retry: мёртвая петля** — при ошибке JWT, код очищал токены и пытался использовать пароль (v1), но v2 не поддерживает пароль → AUTH_FAILED. Теперь: сначала пытается refresh, потом — AUTH_FAILED.
- **performTokenRefresh: fallback при истечении refresh_token** — если refresh_token тоже истёк, автоматический re-login по сохранённому паролю.
- **onResume: валидация токена** — ChatListActivity и NewChatActivity проверяют свежесть токена при возвращении в foreground.

**Новые токены хранятся и обновляются корректно, рефреш запускается при каждом входе и восстановлении сессии.**

### Рефакторинг

**NewChatActivity → ChatViewModel:**
- Бизнес-логика перенесена в ChatViewModel: sendMessage, uploadAudio, retryMessage, fetchChatMetadata, loadPinnedMessages, syncChatListIfNeeded, ensureUserIdSet
- NewChatActivity: 759 → ~450 строк
- ViewModel обогащён StateFlow: chatMetadata, pinnedMessageIds, isAudioUploading

### Исправления тестов
- GrpcChatListClientTest: getChats() тесты обновлены для GrpcChatClient (после split в v1.2.0.5)

## [1.2.0.8] - 2026-06-18

### Исправления

**ChatList V2 — Response Marshallers (критический):**
- Pin/Unpin Chat — response marshaller отсутствовал, `unaryCallWithClass` использовал рефлексию и всегда возвращал `success=false`
- Archive/Unarchive Chat — аналогичный баг
- Pin/Unpin Message — аналогичный баг
- Search Chats — response marshaller отсутствовал, поиск всегда возвращал пустой список
- GetPinned Messages — response marshaller отсутствовал
- Созданы: `PinChatResponseMarshaller`, `UnPinChatResponseMarshaller`, `ArchiveChatResponseMarshaller`, `UnarchiveChatResponseMarshaller`, `PinMessageResponseMarshaller`, `UnPinMessageResponseMarshaller`, `GetPinnedMessagesResponseMarshaller`
- Все V2 boolean-response методы теперь корректно десериализуют ответ сервера

**Примечание:** ChatList V2 фичи (pin, archive, mute) работали ранее только благодаря `loadChats()` в `ChatListActionMode` — полный re-fetch после каждого действия. Теперь optimistic update в ViewModel тоже работает корректно.

## [1.2.0.7] - 2026-06-18

### Исправления

**SuperAdmin (критический):**
- Исправлен баг: кнопка "Admin" не отображалась ни для кого
- `ProfileClient.unaryCall()` использовал рефлексию для response marshaller — всегда возвращал дефолтный объект с `isSuperAdmin = false`
- Созданы `GetProfileResponseMarshaller` и `GetProfileRequestMarshaller` — корректная десериализация 11 полей ответа
- Созданы marshallers для всех ProfileService v2 методов (UpdateProfile, UpdateAvatar, GetUserSettings, UpdateUserSettings)

**ProfileService marshallers:**
- `GetProfileResponseMarshaller` — десериализует userId, username, email, avatarUrl, fullAvatarUrl, bio, status, locale, isSuperAdmin, createdAt, lastSeenAt
- `UpdateProfileV2ResponseMarshaller` — десериализует success, message, вложенный profile
- `UpdateAvatarV2ResponseMarshaller` — десериализует success, message, avatarUrl, fullAvatarUrl
- `GetUserSettingsResponseMarshaller` — десериализует locale, themeId, pushEnabled, custom map
- Все request marshallers — корректная сериализация полей

## [1.2.0.6] - 2026-06-18

### Исправления

**Список чатов:**
- Исправлена загрузка списка чатов — GetChatsV2 RPC добавлен в proto (сервер возвращал UNIMPLEMENTED)
- Исправлен SQL-запрос GetUserChatsV2 — participants содержат usernames, а не UUID

**Админ-панель:**
- Кнопка "Admin" в Additional Settings теперь отображается для всех администраторов (ранее — только для `ferz`)
- Флаг `isSuperAdmin` загружается из профиля сервера вместо хардкода по username

---

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
