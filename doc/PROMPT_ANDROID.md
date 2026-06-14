# Промпт для новой сессии — Android v1.1.3.11+

**Дата:** 2026-06-14
**Версия:** 1.1.3.11+
**Ветка:** feat/1.1.3.x

---

## СТАТУС: v1.1.3.11+ — DEV

Сервер: v1.2.0.1 на dev и prod. AuthService v2 (JWT) основной, v1 deprecated.
Android: 3 auth виджета, server switch исправлен, AuthV2 интегрирован (loginV2 + fallback на v1).

**Текущая задача:** Тестирование JWT auth на dev + token refresh interceptor + Bearer token во все gRPC вызовы.

---

## АРХИТЕКТУРА

### Сервер (/root/msg)
```
main.go                    — Entry point, gRPC server
server.go                  — ServerVersion = "1.2.0.1"
auth_service.go            — AuthService v1 (deprecated)
auth_service_v2.go         — AuthService v2 (JWT, основной)
auth_interceptor.go        — gRPC Bearer token interceptor
http_server.go             — HTTP (/health, /info)
messenger.proto            — ChatService, AuthService, AI Chat, Remote Agent RPC
```

### Android (/root/msg.client.android)
```
ui/
├── widget/
│   ├── ServerAuthBottomSheet.kt    — шторка выбора входа
│   ├── LoginBottomSheet.kt         — шторка входа (prefillUsername)
│   └── RegisterBottomSheet.kt      — шторка регистрации
├── remote/                         — Remote Agent UI
├── chat/widget/ChatWidget.kt       — общий виджет чата
└── adapter/ChatAdapter.kt          — адаптер чатов

data/
├── grpc/GrpcClient.kt              — facade (signInV2, signUpV2, refreshToken)
├── grpc/RealGrpcClient.kt          — реализация gRPC
├── session/CredentialStore.kt      — credentials + last_username
├── session/SessionManager.kt       — loginV2 (JWT) + loginV1 (legacy fallback)
├── session/UserSession.kt          — accessToken, refreshToken, authMethod
├── auth/AuthManager.kt             — JWT token storage, getBearerToken
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
```

### Auth widgets
- 3 виджета: ServerAuthBottomSheet, LoginBottomSheet, RegisterBottomSheet
- Все наследуют StandardBottomSheet
- Health check через http://host:8082/health
- Используются в: ChatListActivity, ServersActivity

### Server switch
- CredentialStore.setServerAddress() только после успешного входа
- justReturnedFromServersActivity флаг для пропуска reconnect в onResume
- isLoadingChats предотвращает двойную загрузку

### Logout
- SessionManager.logout(): очищает password/tokens, сохраняет username в last_username
- LoginBottomSheet.prefillUsername(): предзаполняет username из last_username
- Cancel в login/register sheets: закрывает шторку и возвращает к auth choice

### i18n
- Все строки в values/strings.xml (en) + values-ru/strings.xml
- server_default_name, app_version_format, wrong_password строки

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

---

## ИЗВЕСТНЫЕ ПРОБЛЕМЫ

- **Шторка профиля** (bottom_sheet_user_menu) — нет горизонтальной черты (divider), отличается от других шторок

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

# Тесты
go test ./...

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
- CHANGELOG: `/root/msg.client.android/CHANGELOG.md` (Android), `/root/msg/CHANGELOG.md` (сервер)
