# Заметки сессии 8 — 2026-06-14

## Что сделано

### BearerTokenInterceptor (Android)
- Создан `data/grpc/BearerTokenInterceptor.kt`
- Автоматически подставляет JWT Bearer token во все gRPC вызовы
- Пропускает AuthService (нет токена), Chat stream (legacy auth), вызовы без JWT (v1)
- Подключён в `RealGrpcClient.connect()` через `builder.intercept()`

### Proactive Token Refresh (Android)
- `startTokenRefresh()` — проверка каждые 60с
- `performTokenRefresh()` — синхронный refresh через suspendCancellableCoroutine
- Остановка при logout / FORCE_LOGOUT

### Per-server token validation
- `CredentialStore.setJwtServerAddress()` / `getJwtServerAddress()` / `clearJwtServerAddress()`
- `initFromPrefs()` — проверка совпадения сервера
- `login()` — clearTokens() перед новым логином
- `clearTokens()` — также очищает jwt_server_address

### Совместимость
- Полная совместимость с prod сервером (v1, без JWT)
- Интерцептор является no-op если нет JWT токена
- Legacy flow (Chat stream с password) не затронут

## Коммиты
- (pending)

## Известные проблемы
- Нет

## Следующие шаги
1. Тестирование на prod (v1 legacy) — убедиться что ничего не сломалось
2. Тестирование на dev (v2 JWT) — полный цикл: регистрация, вход, refresh, logout
3. Тест server switch — prod ↔ dev
4. Редеплой prod сервера (после тестирования)
