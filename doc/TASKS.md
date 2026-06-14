# Lavender Messenger (Android) — Задачи

**Версия:** v1.1.3.11 (dev)
**Обновлено:** 2026-06-14
**Ветка:** feat/1.1.3.x

---

## ✅ v1.1.3.11 — Auth widgets + Server switch fix + Chat flickering fix

### Новое: Auth bottom sheet widgets
- ✅ **ServerAuthBottomSheet** — шторка выбора входа (лого + имя сервера + адрес + health индикатор + кнопки Войти/Регистрация)
- ✅ **LoginBottomSheet** — шторка входа (username/password + кнопки)
- ✅ **RegisterBottomSheet** — шторка регистрации (username/password/email + кнопки)
- ✅ Все 3 виджета используются и в ChatListActivity (первый вход) и в ServersActivity (выбор сервера)
- ✅ Имена серверов: "Lava Germany" (prod), "Lava Germany dev" (dev)
- ✅ Health check через `http://host:8082/health` (зелёный/серый индикатор)
- ✅ Версия приложения: "Lava: app Android v1.1.3.11" / "Лава: приложение Android v1.1.3.11"

### Исправления
- ✅ **Двойной вход при смене сервера** — исправлен
- ✅ **Чаты загружаются с правильного сервера** — clearAll() + ожидание READY
- ✅ **Мерцание чатов** — isLoadingChats флаг, startSync останавливается при смене сервера
- ✅ **Двойной тап на Войти/Регистрация** — isTransitioning флаг
- ✅ **i18n** — server_default_name, app_version_format строки

---

## ✅ v1.1.3.10 — i18n completion + Stability

### Android
- ✅ i18n завершён — все user-facing строки вынесены (~50 строк)
- ✅ Unit-тесты — ErrorHandlerTest (11), ChatAdapterTest (15)
- ✅ Crash fixes — OwlSettingsActivity, RemoteAgentActivity

---

## ✅ v1.1.3.9 — Espresso Tests + Bugfixes

- ✅ Espresso-тесты — 4 тест-класса (42 теста)
- ✅ Empty chat text fix
- ✅ RemoteAgentActivity crash fix

---

## ✅ v1.1.3.8 — DeployAgentTaskStream fix + Remote Agent UI

- ✅ ChatAdapter filter() fix
- ✅ RemoteAgentViewModel fix

---

## ✅ v1.1.3.7 — Streaming + ErrorHandler

- ✅ DeployAgentTaskStream
- ✅ ErrorHandler + AppLog

---

## 📋 Бэклог

### Высокий приоритет
- [ ] **AuthService v2 интеграция в Android**
  - Клиент должен поддерживать оба метода входа (v2 приоритет, fallback на v1)
  - При получении deprecated warning от v1 — показать уведомление
  - JWT token refresh, device management

### Средний приоритет
- [ ] **Мерцание тулбара** — "не может подключиться" + кружок перезагрузки после входа через серверы
  - Проблема: onResume() и serversActivityLauncher конфликтуют
  - Нужно: единый поток загрузки чатов, не дублировать startSync()

### Низкий приоритет
- [ ] Qdrant + CLIP (production RAG)
- [ ] Prometheus метрики

---

## 🔑 Ключевые решения

| Решение | Обоснование |
|---------|-------------|
| Auth widgets | 3 виджета (ServerAuth, Login, Register) — единый стиль входа |
| Health check | HTTP /health для индикатора доступности сервера |
| isLoadingChats | Предотвращает двойную загрузку из launcher + onResume |
| isTransitioning | Предвращает повторный showAuthChoiceDialog при переходе |

---

## 📁 Ключевые файлы

| Файл | Назначение |
|------|------------|
| `ServerAuthBottomSheet.kt` | Шторка выбора входа (лого + сервер + статус) |
| `LoginBottomSheet.kt` | Шторка входа |
| `RegisterBottomSheet.kt` | Шторка регистрации |
| `ChatAdapter.kt` | Адаптер чатов с clearAll() |
| `CredentialStore.kt` | Хранилище credentials + список серверов |
| `SessionManager.kt` | Управление сессией |
