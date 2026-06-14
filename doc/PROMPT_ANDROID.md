# Промпт для новой сессии — Android v1.1.3.12

**Дата:** 2026-06-14
**Версия:** 1.1.3.12
**Ветка:** feat/1.1.3.x

---

## СТАТУС: v1.1.3.12 — DEV (готов к релизу)

Сервер: v1.2.0.1 на dev (порт 50052, HTTP 8083) и prod (порт 50051, HTTP 8082).
Android: BearerTokenInterceptor + proactive refresh + per-server validation реализованы.
Тестирование на prod пройдено — чаты загружаются, после очистки кеша всё ОК.

---

## АРХИТЕКТУРА

### Сервер (/root/msg)
```
main.go                    — Entry point, gRPC server, graceful shutdown
server.go                  — ServerVersion = "1.2.0.1", service version constants
auth_service.go            — AuthService v1 (deprecated)
auth_service_v2.go         — AuthService v2 (JWT, основной)
auth_interceptor.go        — gRPC Bearer token interceptor (unary + streaming)
auth_jwt.go                — JWT генерация/валидация
db_auth_devices.go         — CRUD для user_devices + device_auth_log
db_auth_migrations.go      — миграция таблиц
http_server.go             — HTTP (/health, /info)
messenger.proto            — ChatService, AuthService, AI Chat, Remote Agent RPC
```

### Android (/root/msg.client.android)
```
ui/
├── widget/
│   ├── ServerAuthBottomSheet.kt    — шторка выбора входа (лого + сервер + статус)
│   ├── LoginBottomSheet.kt         — шторка входа (prefillUsername)
│   └── RegisterBottomSheet.kt      — шторка регистрации
├── ServersActivity.kt              — управление списком серверов
├── remote/                         — Remote Agent UI
├── chat/widget/ChatWidget.kt       — общий виджет чата
└── adapter/ChatAdapter.kt          — адаптер чатов (clearAll)

data/
├── grpc/BearerTokenInterceptor.kt  — ClientInterceptor для JWT Bearer token
├── grpc/GrpcClient.kt              — фасад
├── grpc/RealGrpcClient.kt          — реализация gRPC (connect, getChats, signInV2, refreshToken)
├── auth/AuthManager.kt             — JWT token storage, getBearerToken, needsRefresh, clearTokens
├── session/CredentialStore.kt      — credentials, jwt_server_address, last_username, server list
├── session/SessionManager.kt       — loginV2 + loginV1 fallback, startTokenRefresh, per-server validation
├── session/UserSession.kt          — accessToken, refreshToken, authMethod, isJwtAuth
└── models/ErrorHandler.kt          — единый обработчик ошибок
```

---

## КЛЮЧЕВЫЕ РЕШЕНИЯ

### Auth V2 (JWT) flow
```
ServerAuthBottomSheet → LoginBottomSheet → SessionManager.login()
  → try V2 (SignInV2 gRPC)
  → on success: store JWT tokens via AuthManager.storeTokens()
  → on failure: fallback to V1 (Chat stream auth)
  → BearerTokenInterceptor подставляет token во все вызовы
  → Proactive refresh каждые 60с
```

### BearerTokenInterceptor
- Пропускает AuthService (нет токена), Chat stream (legacy auth)
- No-op если AuthManager.getBearerToken() == null (совместимость с v1)
- Полная совместимость с prod сервером (v1 без JWT)

### Per-server token validation
- CredentialStore.setJwtServerAddress() / getJwtServerAddress()
- initFromPrefs() проверяет совпадение сервера при восстановлении
- login() вызывает clearTokens() перед новым логином

### Auth widgets
- 3 виджета: ServerAuthBottomSheet, LoginBottomSheet, RegisterBottomSheet
- Health check через http://host:8082/health
- Drag handle во всех шторках
- Status indicator — только кружок слева от названия

### Server management
- ServersActivity — отдельный экран для управления списком серверов
- CredentialStore.getServerList() / saveServerList()
- При смене сервера → clearTokens() + reconnect

### Logout
- SessionManager.logout(): очищает password/tokens, сохраняет username в last_username
- LoginBottomSheet.prefillUsername(): предзаполняет username из last_username
- Cancel в login/register sheets: закрывает шторку и возвращает к auth choice

### i18n
- Все строки в values/strings.xml (en) + values-ru/strings.xml
- app_version_format: "Lava: app Android %s" / "Lava: приложение Android %s"

---

## ПРАВИЛА

1. НЕ компилировать на сервере (OOM kill)
2. Коммитить и пушить после каждого значимого изменения
3. Версия сервера в server.go:33, версия Android в version.txt
4. userId (UUID) — всегда как ключ, НЕ username
5. changelog.txt БОЛЬШЕ НЕ ИСПОЛЬЗУЕТСЯ
6. JWT секрет: минимум 32 байта, НЕ коммитить
7. Темы: цвета программно через ThemeUtils.parseSafeColor()
8. i18n: все новые строки ОДНОВРЕМЕННО в values/strings.xml + values-ru/strings.xml
9. НЕ инициализировать getString() в полях класса Activity
10. Форматирование строк: позиционные форматтеры (%1$s, %2$d)
11. Серверы: ServersActivity остаётся для управления списком серверов

---

## ИЗВЕСТНЫЕ ПРОБЛЕМЫ

### 42P10 на prod БД (сервер)
- `Failed to register device ... pq: there is no unique or exclusion constraint`
- Нужно вручную выполнить ALTER TABLE на prod БД
- Не критично — аутентификация работает

### Первый вход на prod — только Favorites
- Проблема в локальном кеше Android — после очистки всё ОК
- Не является багом сервера или нового кода

---

## КОМАНДЫ

```bash
# === СЕРВЕР ===
cd /root/msg && export PATH=$PATH:/usr/local/go/bin:~/go/bin

# Сборка и деплой на dev
go build -o /tmp/lavender-server-dev .
systemctl stop lavender-server-dev
cp /tmp/lavender-server-dev /root/LavenderMessenger/run/lavender-server-dev
systemctl start lavender-server-dev

# Сборка и деплой на prod (НЕ делать без тестирования на dev!)
go build -o /tmp/lavender-server .
systemctl stop lavender-server
cp /tmp/lavender-server /root/LavenderMessenger/run/lavender-server
systemctl start lavender-server

# Тесты
go test ./...

# Логи
journalctl -u lavender-server-dev -f
journalctl -u lavender-server -f

# === ANDROID ===
cd /root/msg.client.android
# assembleRelease ТОЛЬКО локально!
```

---

## DEV vs PROD

| Характеристика | Dev | Prod |
|----------------|-----|------|
| Порт gRPC | 50052 | 50051 |
| Порт HTTP | 8083 | 8082 |
| Имя | Lava Germany dev | Lava Germany |
| Сервис | lavender-server-dev | lavender-server |
| Конфиг | .env.dev | .env |
| DB | chat_db_dev | chat_db |

---

## ДОКУМЕНТАЦИЯ

- Индекс: `/root/msg.client.android/doc/INDEX.md`
- Паттерны: `/root/msg.client.android/doc/PATTERNS.md`
- Remote Agent: `/root/msg.client.android/doc/REMOTE_AGENT.md`
- Сервер: `/root/msg/doc/INTEGRATION_SESSION.md`, `/root/msg/doc/TASKS.md`
- Подводные камни: `/root/msg/doc/PITFALLS.md`
- CHANGELOG: `/root/msg.client.android/CHANGELOG.md`
