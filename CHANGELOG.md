# Lavender Messenger — Android Changelog

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
