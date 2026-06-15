# Lavender Messenger (Android) — Задачи

**Версия:** v1.1.3.12
**Обновлено:** 2026-06-14 (сессия 8)
**Ветка:** feat/1.1.3.x

---

## ✅ v1.1.3.12 — Bearer Token Interceptor + Token Refresh + Per-server validation

### Новое: Bearer Token Interceptor
- ✅ **BearerTokenInterceptor** — ClientInterceptor для автоматической подстановки JWT Bearer token
- ✅ Пропускает AuthService и Chat stream (legacy auth)
- ✅ No-op при отсутствии токена (совместимость с v1 серверами)

### Новое: Proactive Token Refresh
- ✅ **startTokenRefresh()** — периодическая проверка каждые 60с
- ✅ **performTokenRefresh()** — синхронный refresh через suspendCancellableCoroutine
- ✅ Остановка при logout / FORCE_LOGOUT

### Новое: Per-server token validation
- ✅ **CredentialStore.setJwtServerAddress()** / **getJwtServerAddress()** / **clearJwtServerAddress()**
- ✅ **initFromPrefs()** — проверка совпадения сервера при восстановлении сессии
- ✅ **login()** — clearTokens() перед новым логином
- ✅ **clearTokens()** — также очищает jwt_server_address

### Коммиты
- (pending)

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
- [ ] **ChatList v2** — новая версия списка чатов с улучшенным UI/UX
- [ ] **Выпуск Android v1.1.3.13** — готов к релизу

### Средний приоритет
- [ ] **Тесты для ProfileService v2** — unit-тесты для ProfileClient
- [ ] **Bearer token в Chat stream** — вместо password в первом сообщении (v1.2.2.x, отложено)

### Отложено
- [ ] Qdrant + CLIP (production RAG) — см. AI_SERVICES.md

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
|| BearerTokenInterceptor | Автоматическая подстановка JWT Bearer token, no-op для v1 ||
|| Proactive refresh | Проверка каждые 60с, refresh за 5 минут до истечения ||
|| Per-server validation | Токены привязаны к серверу, очистка при смене ||
|| getChats timeout | withTimeoutOrNull(10с) предотвращает зависание ||
| getChats error callback | callback(emptyList()) при onClose ошибке |
| ProfileClient | ProfileService v2 client (JWT, dev only), fallback на v1 |
| fetchServerInfo | Автоопределение версии сервера через /info при connect() |

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
| `ProfileClient.kt` | ProfileService v2 client (JWT, dev only) |
| `BearerTokenInterceptor.kt` | ClientInterceptor для JWT Bearer token |
