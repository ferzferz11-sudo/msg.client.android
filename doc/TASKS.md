# Lavender Messenger (Android) — Задачи

**Версия:** v1.1.3.12 (dev)
**Обновлено:** 2026-06-14 (сессия 7)
**Ветка:** feat/1.1.3.x

---

## ✅ v1.1.3.12 — Auth bottom sheets cosmetics + code cleanup

### UI cosmetics
- ✅ **app_version_format** — "client" → "app" (EN), "клиент" → "приложение" (RU)
- ✅ **ServerAuthBottomSheet status indicator** — только кружок (без текста), зелёный/красный, слева от названия сервера
- ✅ **Drag handle** — добавлен во все шторки входа (server auth, login, register)
- ✅ **Убраны горизонтальные dividers** из шторок входа
- ✅ **Divider в шторке профиля** — уже был, оставлен как есть

### Code cleanup
- ✅ **showAuthChoiceDialog()** — убран getDefaultServer(), захардожен дефолтный сервер
- ✅ **onResume()** — убран justReturnedFromServersActivity guard
- ✅ **Profile menu** — скрыта кнопка actionServers
- ✅ **AppDatabase** — fallbackToDestructiveMigration → fallbackToDestructiveMigrationOnDowngrade(dropAllTables = true)
- ✅ **Серверы** — ServersActivity остаётся для управления списком серверов

### Коммиты
- `c64856b` — cosmetics: auth bottom sheets UI fixes
- `13d6045` — fix: restore TextView import in ServerAuthBottomSheet
- `36cb2a6` — fix: replace deprecated fallbackToDestructiveMigration
- `689796e` — fix: auth bottom sheets - drag handle, status indicator position, remove dividers
- `bcf8cf2` — fix: replace deprecated fallbackToDestructiveMigrationOnDowngrade with dropAllTables param

---

## ✅ v1.1.3.11+ — AuthV2 integration + UI fixes

### Новое: AuthV2 (JWT)
- ✅ **SessionManager.loginV2()** — SignInV2/SignUpV2 с fallback на v1
- ✅ **JWT token storage** — AuthManager.storeTokens(), getAccessToken(), getRefreshToken(), getBearerToken()
- ✅ **UserSession** — accessToken, refreshToken, authMethod, isJwtAuth
- ✅ **Logout сохраняет username** — last_username в legacy prefs
- ✅ **Предзаполнение username** — LoginBottomSheet.prefillUsername()

### Исправления UI
- ✅ **Toolbar flickering** — единый поток загрузки, isConnecting flag
- ✅ **Убран диалог "Предложить регистрацию"** — Toast с реальной ошибкой
- ✅ **Cancel в login/register sheets** — закрывает шторку и возвращает к auth choice
- ✅ **Подавлены DEPRECATION warnings** — @Suppress("DEPRECATION") на loginV1 fallback

---

## ✅ v1.1.3.11 — Auth widgets + Server switch fix + Chat flickering fix

### Новое: Auth bottom sheet widgets
- ✅ **ServerAuthBottomSheet** — шторка выбора входа (лого + имя сервера + адрес + health индикатор + кнопки Войти/Регистрация)
- ✅ **LoginBottomSheet** — шторка входа (username/password + prefill)
- ✅ **RegisterBottomSheet** — шторка регистрации (username/password/email)

### Исправления
- ✅ **Двойной вход при смене сервера** — исправлен
- ✅ **Чаты загружаются с правильного сервера** — clearAll() + ожидание READY
- ✅ **Мерцание чатов** — isLoadingChats флаг, startSync останавливается при смене сервера
- ✅ **Двойной тап на Войти/Регистрация** — isTransitioning флаг
- ✅ **i18n** — server_default_name, app_version_format строки

---

## 📋 Бэклог

### Высокий приоритет
- [ ] **Bearer token interceptor** — подставить Bearer token во все gRPC вызовы (getChats, getHistory, sendMessage, etc.)
- [ ] **Token refresh interceptor** — автоматический refresh при 401 от сервера
- [ ] **Тестирование JWT auth на dev** — регистрация, вход, refresh token, logout
- [ ] **Протестировать server switch** — prod ↔ dev, проверить что токены не конфликтуют

### Средний приоритет
- [ ] **Обновить CHANGELOG.md** — Android
- [ ] **ON CONFLICT на prod БД** — UNIQUE constraint есть, но ошибка 42P10 была в логах. Нужно пересобрать и деплоить prod сервер.

### Низкий приоритет
- [ ] Qdrant + CLIP (production RAG) — на стороне сервера

---

## 🔑 Ключевые решения

| Решение | Обоснование |
|---------|-------------|
| Auth widgets | 3 виджета (ServerAuth, Login, Register) — единый стиль входа |
| Health check | HTTP /health для индикатора доступности сервера |
| isLoadingChats | Предотвращает двойную загрузку из launcher + onResume |
| isTransitioning | Предотвращает повторный showAuthChoiceDialog при переходе |
| loginV2 + fallback | JWT приоритет, fallback на v1 для совместимости со старыми серверами |
| last_username | Сохранение username для предзаполнения после logout |
| ServersActivity | Остаётся для управления списком серверов (добавление/удаление/выбор) |
| getAuthMetadata() | Определён в RealGrpcClient, но нигде не вызывается — нужно подключить |

---

## 📁 Ключевые файлы

| Файл | Назначение |
|------|------------|
| `ServerAuthBottomSheet.kt` | Шторка выбора входа (лого + сервер + статус) |
| `LoginBottomSheet.kt` | Шторка входа (prefillUsername) |
| `RegisterBottomSheet.kt` | Шторка регистрации |
| `AuthManager.kt` | JWT token storage |
| `SessionManager.kt` | loginV2 + loginV1 fallback |
| `UserSession.kt` | accessToken, refreshToken, authMethod |
| `CredentialStore.kt` | Credentials + last_username + server list |
| `ChatAdapter.kt` | Адаптер чатов с clearAll() |
| `ServersActivity.kt` | Управление списком серверов (добавление/удаление/выбор) |
