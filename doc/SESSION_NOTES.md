# Заметки сессии 7 — 2026-06-14

## Что сделано

### Dev server
- Был inactive dead, запущен на порту 50052 (gRPC), 8083 (HTTP)
- Systemd unit упрощён: только `Environment=APP_ENV=dev`
- Логи доступны: http://13.140.25.249/server-logs-dev

### Android auth cosmetics
- `app_version_format`: "client" → "app" (EN), "клиент" → "приложение" (RU)
- Status indicator — только кружок слева от названия сервера (без текста)
- Drag handle добавлен во все шторки входа
- Убраны горизонтальные dividers из шторок входа

### Android code cleanup
- `showAuthChoiceDialog()` — убран `getDefaultServer()`, захардожен дефолт
- `onResume()` — убран `justReturnedFromServersActivity` guard
- Profile menu — скрыта кнопка `actionServers`
- `AppDatabase` — `fallbackToDestructiveMigrationOnDowngrade(dropAllTables = true)`
- `ServersActivity` оставлена для управления списком серверов

### БД prod
- UNIQUE constraint на `user_devices(user_id, device_id)` существует
- Дубликатов нет
- Ошибка 42P10 была из-за старого бинарника prod сервера

## Коммиты
- `c64856b` — cosmetics: auth bottom sheets UI fixes
- `13d6045` — fix: restore TextView import in ServerAuthBottomSheet
- `36cb2a6` — fix: replace deprecated fallbackToDestructiveMigration
- `689796e` — fix: auth bottom sheets - drag handle, status indicator, remove dividers
- `bcf8cf2` — fix: replace deprecated fallbackToDestructiveMigrationOnDowngrade with dropAllTables param

## Известные проблемы
- Bearer token не подставляется в gRPC вызовы (Android) — нужен ClientInterceptor
- Нет token refresh (Android) — нужен интерцептор для авто-refresh при 401
- ON CONFLICT 42P10 на prod — нужен редеплой prod сервера

## Следующие шаги
1. Bearer token interceptor (Android)
2. Token refresh interceptor (Android)
3. Тестирование JWT auth на dev
4. Редеплой prod сервера
