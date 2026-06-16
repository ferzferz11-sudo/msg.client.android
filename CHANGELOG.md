# Lavender Messenger — Android Changelog

## [1.1.3.19] - 2026-06-16

### Исправлено: JWT auth для ChatStream v2
- **JWT token malformed** — `getBearerToken()` возвращал `"Bearer <token>"` с префиксом, а `setJwtToken()` ожидал чистый токен
- Исправлено: используем `getAccessToken()` для JWT в ChatStream
- **Бесконечный reconnect loop при auth failure** — `UNKNOWN - authentication failed` не ловился как auth error
- Добавлена проверка `authentication failed` / `JWT validation failed` / `token is malformed` → FAILED status, без retry

### Исправлено: Дублированный reconnect logic
- **3 независимых источника reconnect** (onClose, onError, getChats onClose) конфликтовали
- onClose больше не вызывает reconnect, только делегирует в onError
- getChats() onClose не трогает connection status
- onError — единственный источник reconnect с isRetrying guard

### Новое: Unread badges
- Бейдж стилизуется по теме (primary color bg, adaptive text color)
- Кап 99+ для больших чисел
- MarkAsRead при клике на чат (clear badge + server MarkAsRead)
- Реал-тайм обновление через newMessageEvent SharedFlow

### Новое: DiffUtil в ChatAdapterV2
- Заменён notifyDataSetChanged на DiffUtil.calculateDiff() + dispatchUpdatesTo()
- Анимации добавления/удаления элементов, нет мерцания

### Документация
- Создан `doc/ARCH_ANALYSIS_V2_V1.md` — полный анализ архитектуры v2 vs v1

### Коммиты
- `9726929` — fix: JWT auth and infinite reconnect on auth failure
- `63ed73f` — fix: eliminate duplicate reconnect logic
- `959a79f` — feat: add DiffUtil to ChatAdapterV2
- `e029aa7` — feat: unread badges — theme colors, mark-as-read, real-time update
- `583bf3f` — docs: add ARCH_ANALYSIS_V2_V1.md

---

## [1.1.3.18] - 2026-06-16

### Исправлено: Баг загрузки чатов (сессия 19)
- **Корневая причина**: `connect()` ставил READY сразу после `builder.build()`, до установления TCP
- **Корневая причина**: Двойной `loadChats()` в ChatListActivityV2 (Activity + ViewModel)
- **Корневая причина**: Cache-first логика в `getChats()` вызывала `callback(emptyList())`
- Убран HTTP health check, используется optimistic READY
- Убрана cache-first логика; callback всегда вызываетс
- Добавлен reconnect при transport errors
- onResume() safety nets для v1 и v2

### Исправлено: Стабильность соединения (сессия 20)
- **HTTP /info недоступен на dev** — fallback по gRPC порту (50052→v2, 50051→v1)
- **Keepalive failures** — увеличены таймауты (30s/10s), добавлен idleTimeout 25min
- **Множественные reconnect** — подавлен reconnect при shutdownNow
- **Poll interval** — увеличен 5s → 30s
- Сохранение currentServerPort для правильного reconnect

---

## [1.1.3.17] - 2026-06-15

### Новое: FAB AI — создание AI чата из ChatListActivityV2
- **AIBottomSheet** подключён к FAB AI в ChatListActivityV2
- Создание нового Hermes чата → HermesChatActivity (пустой chatId = создание на сервере)
- Создание нового OWL чата → OwlChatActivity (пустой chatId = создание на сервере)
- Существующие AI чаты отображаются в списке (Hermes + OWL)
- Удаление AI чатов через контекстное меню
- Настройки AI чатов → OwlSettingsActivity (для OWL и Hermes)
- Навигация: таб AI фильтрует hermes/owl типы

### Рефакторинг
- ChatListViewModelV2: добавлен публичный метод `getChats()` для доступа к списку чатов
- ChatListActivityV2: импорты обновлены (AIChatInfo, AIBottomSheet)

---

## [1.1.3.16] - 2026-06-16

### Новое: Selection Mode (множественный выбор чатов)
- **Long press** на чате → ActionMode toolbar с действиями (Pin/Unpin, Mute, Archive, Delete)
- **Тап** в режиме выбора → toggle selection с checkbox
- **Визуальная подсветка** выбранных элементов (primary color с alpha)
- **Back press** → выход из selection mode (OnBackPressedDispatcher)
- Массовые действия над выбранными чатами

### Новое: Поиск чатов
- **SearchView** в toolbar через inflateMenu
- **Debounce 300ms** через coroutine Job
- Локальная фильтрация allChats (работает на v1 и v2)

### Новое: Pin Message
- **PinMessage/UnPinMessage/GetPinnedMessages** RPC в ChatService
- **Selection toolbar** — кнопка pin/unpin при выборе 1 сообщения
- **Pinned badge** в MessageAdapter для закреплённых сообщений
- Graceful fallback на v1 серверы

### Новое: ServersActivity improvements
- **Prefill** последнего логина в login bottom sheet
- **Splash** после успешного входа/регистрации
- Все серверы (включая dev) доступны всем пользователям

### Новое: Очистка кэша при входе
- **CacheUtils** — единый утилитный метод очистки кэша
- Синхронная очистка БД (messages, chats) при входе (без Toast)
- Полная очистка + Glide из настроек (с Toast)

### Рефакторинг
- Удалён дублирующийся код очистки кэша из всех Activity
- Использование CacheUtils.clearAllSync() и CacheUtils.clearAllWithGlide()

### Новое: ChatList v2 UI — полная реализация (сессия 13)
- **ChatListActivityV2** — полная переработка: RecyclerView+SwipeRefresh напрямую в Activity (без фрагмента)
- **TabLayout** — табы All / AI / Groups с фильтрацией через ViewModel (setTabFilter)
- **Toolbar** — avatar→ProfileActivity, title→ServersActivity, search/settings icons
- **FABs** — fabAi (TODO AI chat), fabAddChat→NewChatActivity
- **Навигация** — favorites→NewChat, hermes→HermesChat, owl→OwlChat, other→NewChat
- **Connection status** — subtitle с connecting/online/offline
- **SplashActivity** — маршрутизация v1/v2 по наличию server host
- **ChatAdapterV2** — исправлено дублирование cachedColors (единый кэш в адаптере)
- **AndroidManifest** — регистрация ChatListActivityV2, удалены дубликаты activity
- **strings.xml** — добавлены connection status строки (en+ru)
- **activity_chat_list_v2.xml** — SwipeRefresh+RecyclerView вместо FragmentContainer, убран XML tint с FAB
- Backward compatible: v1 пользователи не получают изменений

### Новое: ChatList v2 UI scaffold (сессия 12)
- **ChatListActivityV2** — новый Activity с определением версии сервера (v1/v2)
- **ChatListFragmentV2** — фрагмент с SwipeRefresh + RecyclerView
- **ChatAdapterV2** — адаптер с секциями (Pinned/Favorites/All Chats)
- **ChatListViewModelV2** — ViewModel: loadChats, pinChat, archiveChat, searchChats
- **ChatListSections.kt** — управление секциями
- **TabLayout** — табы All / AI / Groups (заглушка)
- **v2 context menu** — Pin/Mute/Delete в списке чатов (long press)
- **Fallback на v1** — при подключении к prod серверу автоматически запускается ChatListActivity v1
- **i18n** — 17 новых строк (en + ru)

---

## [1.1.3.15] - 2026-06-16

### Последняя версия с полной поддержкой v1 (prod сервер)
- **Стабильная версия** для пользователей на prod сервере (v1.1.3.10)
- Все v1 API работают без изменений
- Полная обратная совместимость

---

## [1.1.3.14] - 2026-06-16

### Новое: ChatStream v2 (JWT auth в Chat stream)
- **BearerTokenInterceptor** — теперь прикрепляет JWT token к Chat stream на v2 серверах
- **RealGrpcClient.startChat()** — использует JWT token для v2, password для v1
- **ProfileClient.fetchServerInfo()** — парсит все версии сервисов (chat/auth/profile/ai)
- Добавлены `isChatV2Supported()`, `isAuthV2Supported()` helpers
- Backward compatible: v1 серверы работают без изменений

### Новое: ChatList v2
- **ProfileClient.fetchServerInfo()** — проверка `chat >= "2.0"` для ChatList v2 API
- **GrpcClient** — добавлены `pinChat()`, `unpinChat()`, `searchChats()`, `archiveChat()`, `unarchiveChat()`
- **RealGrpcClient** — низкоуровневый `unaryCallChatListV2()` для новых RPC методов
- **ChatInfo** — добавлены `isPinned`, `isArchived`, `pinnedAt` поля
- Все v2 методы возвращают `false`/empty на v1 серверах — не требуют explicit проверки версии

### Proto updates
- **MessengerProto.kt** — добавлены ChatList v2 proto classes (PinChatRequestProto, SearchChatsResponseProto, etc.)
- **MessageProto** — добавлены `jwtToken`, `isE2Ee`, `e2EePayload` + Builder методы
- **GetChatsRequestProto** — добавлены `limit`, `offset`, `filter` для пагинации

### Исправлено
- MessageProtoMarshaller — сериализация/deserialization jwt_token (field 26), isE2Ee, e2EePayload
- Обратная совместимость: v1 клиенты работают с новым сервером без изменений

---

## [1.1.3.13] - 2026-06-14

### Новое: ProfileService v2 client
- **ProfileClient** — клиент для ProfileService v2 с JWT Bearer auth
- Автоопределение версии сервера через /info endpoint (profile >= "2.0")
- Fallback на legacy ChatService методы для prod сервера
- Методы: getProfile, updateProfile, updateAvatar, getUserSettings, updateUserSettings
- fetchServerInfo() вызывается автоматически при connect()

### Исправлено: Typing/CallSession compat
- v1 клиенты теперь могут вызывать Typing и CallSession без JWT (server-side fix)

---

## [1.1.3.12] - 2026-06-14

### Новое: Bearer Token Interceptor
- **BearerTokenInterceptor** — автоматически подставляет JWT Bearer token во все gRPC вызовы (кроме AuthService и Chat stream)
- Работает только при JWT v2 аутентификации — для legacy v1 (prod сервер) является no-op
- Полная совместимость с серверами v1 (без JWT)

### Новое: Proactive Token Refresh
- Автоматическая проверка истечения access token каждые 60 секунд
- Тихий refresh через `AuthService/RefreshToken` за 5 минут до истечения
- Корректная остановка при logout / FORCE_LOGOUT

### Новое: Per-server token validation
- Токены привязаны к серверу, который их выдал (`jwt_server_address`)
- При смене сервера через ServersActivity — старые токены автоматически очищаются
- При восстановлении сессии из prefs — проверка совпадения сервера

### Исправлено
- `SessionManager.login()` — очистка старых JWT токенов перед новым логином (предотвращает конфликты при смене сервера)
- `AuthManager.clearTokens()` — также очищает `jwt_server_address`

---

## [1.1.3.11] - 2026-06-14

### Исправлено
- **Двойной вход при смене сервера** — исправлен баг с тремя последовательными входами при переключении между prod/dev серверами
  - `ServersActivity`: `setServerAddress` вызывается только после успешного входа, а до него
  - `ChatListActivity`: убран auto-login из `serversActivityLauncher` — пользователь уже вошёл через ServersActivity
  - `ChatListActivity.onResume`: добавлен флаг `justReturnedFromServersActivity` для предотвращения лишнего reconnect

---

## [1.1.3.10] - 2026-06-14

### Новое: Полная локализация (i18n)
- Все user-facing строки вынесены в `values/strings.xml` (en) + `values-ru/strings.xml`
- Поддержка двух языков: английский и русский
- Локализованы: ошибки, уведомления, подписи кнопок, статусы звонков, SSH-ошибки, команды агента, онлайн-статусы

### Новое: Unit-тесты
- **ErrorHandlerTest** — 11 тестов маршрутизации ошибок
- **ChatAdapterTest** — 15 тестов фильтрации и отображения чатов

### Исправлено
- Онлайн-статус пользователей теперь корректно обновляется (очистка истекших grace period)
- Исправлены краши при запуске OwlSettingsActivity

---

## [1.1.3.9] - 2026-06-13

### Новое: Espresso-тесты
- **ChatListActivityTest** — 18 тестов
- **RemoteAgentActivityTest** — 12 тестов
- **ChatWidgetTest**, **EmptyChatTextTest**

### Новое: Мультиязычность (i18n)
- Вынесено 100+ строк в strings.xml (en + ru)

### Исправлено
- Empty chat text для Favorites vs обычных чатов
- RemoteActivity crash (NPE при инициализации taskTypes)
