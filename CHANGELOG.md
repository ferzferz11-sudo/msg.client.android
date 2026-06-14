# Lavender Messenger — Android Changelog

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
