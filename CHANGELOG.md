# Lavender Messenger — Android Changelog

## [1.1.3.16] - 2026-06-16

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
