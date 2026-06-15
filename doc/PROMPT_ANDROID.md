# Промпт для новой сессии — Android v1.1.3.13

**Дата:** 2026-06-15
**Версия:** 1.1.3.13
**Ветка:** feat/1.1.3.x

---

## СТАТУС: v1.1.3.13 — готов к релизу

Сервер dev: v1.2.1.0 (ProfileService v2 активен).
Сервер prod: v1.1.3.10 (legacy, без ProfileService v2).
Android: ProfileClient + Typing/CallSession compat реализованы, протестированы на dev и prod.

---

## АРХИТЕКТУРА

### Сервер (/root/msg)
```
main.go                    — Entry point, gRPC server, graceful shutdown
server.go                  — ServerVersion = "1.2.1.0", service version constants
auth_service.go            — AuthService v1 (deprecated)
auth_service_v2.go         — AuthService v2 (JWT, основной)
auth_interceptor.go        — gRPC Bearer token interceptor (unary + streaming)
auth_jwt.go                — JWT генерация/валидация
db_auth_devices.go         — CRUD для user_devices + device_auth_log
db_auth_migrations.go      — миграция таблиц (включая user_settings)
server_profile_v2.go       — ProfileService v2 (JWT, dev only)
server_remote.go           — Remote Agent RPC
hermes_remote_manager.go   — HandleTaskStream
ai_chat_manager.go         — AI чаты
owl.go                     — OWL AI
hermes_orchestrator.go     — Hermes Orchestrator
http_server.go             — HTTP (/health, /info)
messenger.proto            — ChatService, AuthService, ProfileService, AI Chat, Remote Agent RPC
```

### Android (/root/msg.client.android)
```
ui/
├── widget/
│   ├── ServerAuthBottomSheet.kt    — шторка выбора входа
│   ├── LoginBottomSheet.kt         — шторка входа (prefillUsername)
│   └── RegisterBottomSheet.kt      — шторка регистрации
├── ServersActivity.kt              — управление списком серверов
├── remote/                         — Remote Agent UI
├── chat/widget/ChatWidget.kt       — общий виджет чата
└── adapter/ChatAdapter.kt          — адаптер чатов (clearAll)

data/
├── grpc/BearerTokenInterceptor.kt  — ClientInterceptor для JWT Bearer token
├── grpc/GrpcClient.kt              — фасад
├── grpc/RealGrpcClient.kt          — реализация gRPC
├── grpc/ProfileClient.kt           — ProfileService v2 client (JWT, dev only)
├── auth/AuthManager.kt             — JWT token storage, getBearerToken, needsRefresh
├── session/CredentialStore.kt      — credentials, jwt_server_address, last_username
├── session/SessionManager.kt       — loginV2 + loginV1 fallback, startTokenRefresh
├── session/UserSession.kt          — accessToken, refreshToken, authMethod
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

### Profile v2 flow (dev server only)
```
connect() → fetchServerInfo(/info) → serviceProfileVersion = "2.0"
  → isProfileV2Supported() = true
  → ProfileClient.getProfile() → messenger.ProfileService/GetProfile (JWT)
  → Fallback: legacy ChatService/GetUserProfile via GrpcClient
```

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

1. НЕ компилировать на сервере (OOM kill) — это касается и Go и Android (./gradlew убивает всё по памяти, а на сервере крутится prod)
2. НЕ деплоить новую версию на prod без прямого указания ферзя
3. Коммитить и пушить после каждого значимого изменения
4. Версия сервера в server.go:33, версия Android в version.txt
5. userId (UUID) — всегда как ключ, НЕ username
6. changelog.txt БОЛЬШЕ НЕ ИСПОЛЬЗУЕТСЯ
7. JWT секрет: минимум 32 байта, НЕ коммитить
8. Темы: цвета программно через ThemeUtils.parseSafeColor()
9. i18n: все новые строки ОДНОВРЕМЕННО в values/strings.xml + values-ru/strings.xml
10. НЕ инициализировать getString() в полях класса Activity
11. Форматирование строк: позиционные форматтеры (%1$s, %2$d)
12. Серверы: ServersActivity остаётся для управления списком серверов
13. **Ветка Android: 1.1.3.x** до релиза, после релиза переход на 1.2.0.x
14. Вся разработка на dev сервере, проверка обратной совместимости на prod

---

## ИЗВЕСТНЫЕ ПРОБЛЕМЫ

### 42P10 на prod БД (сервер)
- `Failed to register device ... pq: there is no unique or exclusion constraint`
- UNIQUE constraint на user_devices в prod БД уже есть (добавлен ранее)
- Ошибка возникает только на старом бинарнике (v1.1.3.10)
- Исправится после редеплоя prod на v1.2.1.0

### Первый вход на prod — только Favorites
- Проблема в локальном кеше Android — после очистки всё ОК
- Не является багом нового кода

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
| Версия | v1.2.1.0 | v1.1.3.10 |

---

## ДОКУМЕНТАЦИЯ

- Индекс: `/root/msg.client.android/doc/INDEX.md`
- Паттерны: `/root/msg.client.android/doc/PATTERNS.md`
- Remote Agent: `/root/msg.client.android/doc/REMOTE_AGENT.md`
- Сервер: `/root/msg/doc/INTEGRATION_SESSION.md`, `/root/msg/doc/TASKS.md`
- Подводные камни: `/root/msg/doc/PITFALLS.md`
- CHANGELOG: `/root/msg.client.android/CHANGELOG.md`
