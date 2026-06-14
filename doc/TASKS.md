# Lavender Messenger (Android) — Задачи

**Версия:** v1.1.3.11+ (dev)
**Обновлено:** 2026-06-14
**Ветка:** feat/1.1.3.x

---

## ✅ v1.1.3.11 — Auth widgets + Server switch fix + Chat flickering fix

### Новое: Auth bottom sheet widgets
- ✅ **ServerAuthBottomSheet** — шторка выбора входа (лого + имя сервера + адрес + health индикатор + кнопки Войти/Регистрация)
- ✅ **LoginBottomSheet** — шторка входа (username/password + prefill)
- ✅ **RegisterBottomSheet** — шторка регистрации (username/password/email)
- ✅ Все 3 виджета используются и в ChatListActivity (первый вход) и в ServersActivity (выбор сервера)

### Исправления
- ✅ **Двойной вход при смене сервера** — исправлен
- ✅ **Чаты загружаются с правильного сервера** — clearAll() + ожидание READY
- ✅ **Мерцание чатов** — isLoadingChats флаг, startSync останавливается при смене сервера
- ✅ **Двойной тап на Войти/Регистрация** — isTransitioning флаг
- ✅ **i18n** — server_default_name, app_version_format строки

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

## 📋 Бэклог

### Высокий приоритет
- [ ] **Тестирование JWT auth на dev** — регистрация, вход, refresh token, logout
- [ ] **Token refresh interceptor** — автоматический refresh при 401 от сервера
- [ ] **Подставить Bearer token во все gRPC вызовы** — getChats, getHistory, sendMessage, etc.
- [ ] **Протестировать server switch** — prod ↔ dev, проверить что токены не конфликтуют

### Средний приоритет
- [ ] **Проверить шторку профиля** — нет горизонтальной черты (divider) в bottom_sheet_user_menu
- [ ] **Обновить CHANGELOG.md** — Android

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
| `CredentialStore.kt` | Credentials + last_username |
| `ChatAdapter.kt` | Адаптер чатов с clearAll() |
