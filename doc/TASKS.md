# Lavender Messenger (Android) — Задачи

**Версия:** v1.1.3.11 (dev)
**Обновлено:** 2026-06-14
**Ветка:** feat/1.1.3.x

---

## ✅ v1.1.3.11 — Double-login bugfix + Server switch fix

### Исправления
- ✅ **Двойной вход при смене сервера** (ServersActivity → ChatListActivity):
  - Убран преждевременный `CredentialStore.setServerAddress()` из `ServersActivity.showServerLoginSheet` до успешного входа
  - `setServerAddress` теперь вызывается только после успешного `SessionManager.login()`
  - Убран auto-login из `ChatListActivity.serversActivityLauncher` — пользователь уже вошёл через ServersActivity
  - Добавлен флаг `justReturnedFromServersActivity` для предотвращения лишнего reconnect в `onResume()`
  - Лог подтверждал 3 входа подряд: ferz11→dev:50052, ferz→dev:50052, ferz→prod:50051

### Новое: Мультиязычность (i18n)
- ✅ Вынесено 100+ хардкодных русских строк в strings.xml (en + ru)
- ✅ AIBottomSheet, RemoteAgent, ChatList, OWL, Hermes, AgentSettings и др.
- ✅ Добавлено 43 новых строки в values/strings.xml + values-ru/strings.xml
- ⚠️ Осталось: ~15 файлов (NewChatActivity, MessageAdapter, HermesGatewayManager, RemoteAgentManager, SecurityActivity, ThemesActivity, CallActivity, AgentListActivity, RemoteAgentActivity agentCommands)

### Новое: Espresso-тесты
- ✅ ChatListActivityTest — 18 тестов
- ✅ RemoteAgentActivityTest — 12 тестов
- ✅ ChatWidgetTest, EmptyChatTextTest

### Исправления
- ✅ Empty chat text — "Personal storage" только для Favorites
- ✅ RemoteAgentActivity crash — NPE при инициализации taskTypes
- ✅ Форматирование строк — позиционные форматтеры
- ✅ Сборка — getString() в Adapter, BottomSheet, ViewModel

---

## ✅ v1.1.3.8 — Espresso Testing IDs + AI Sheet + Bugfixes
- ✅ Espresso ID naming (snake_case + префиксы)
- ✅ AI Bottom Sheet улучшения
- ✅ ChatAdapter filter() fix
- ✅ Favorites → "Личное хранилище"

---

## ✅ v1.1.3.7 — Streaming + ErrorHandler + P0 Bugfixes
- ✅ DeployAgentTaskStream (server-side streaming)
- ✅ ErrorHandler + AppLog
- ✅ P0 bugfixes

---

## ✅ v1.1.3.5 — Remote Agent: Persistent Connection
- ✅ Foreground Service + Singleton Manager

---

## ✅ v1.1.3.4 — Hermes Gateway (SSH Tunnel)
- ✅ HermesGatewayManager, 40 unit tests

---

## 📋 Бэклог

### Высокий приоритет
- [ ] **Новая единая авторизация (AuthService + JWT)**
  - Сервер: расширить AuthService (device management, refresh tokens, sessions)
  - Android: новый AuthManager с JWT, device registration, token refresh
  - Web/macOS/iOS: аналогичная реализация
  - Старые клиенты продолжают работать через Chat stream auth
  - Миграция: при логине черновый AuthService → выдача JWT → последующие запросы с Bearer

### Низкий приоритет
- [ ] Qdrant + CLIP (production RAG)
- [ ] Prometheus метрики

---

## 🔑 Ключевые решения

| Решение | Обоснование |
|---------|-------------|
| ErrorHandler | Единая точка для логирования всех исключений с контекстом |
| AppLog для Toast | Все Toast-ошибки автоматически попадают в журнал ошибок |
| CancellationException → INFO | Отмена корутины это не ошибка, а нормальное поведение |
| AndroidViewModel для i18n | ViewModel не имеет getString(), нужно AndroidViewModel + getApplication() |
| itemTypes в onCreate | НЕ инициализировать getString() в полях класса Activity — crash до onCreate() |

---

## 📁 Ключевые файлы

| Файл | Назначение |
|------|------------|
| `ErrorHandler.kt` | Единый обработчик ошибок |
| `AppLog.kt` | Глобальный логгер (in-memory, до 500 записей) |
| `GrpcClient.kt` | Единая точка доступа к gRPC (facade) |
| `HermesGrpc.kt` | Hermes/Remote Agent gRPC методы (streaming) |
| `MessengerProto.kt` | Proto data classes (streaming) |
| `RemoteAgentSettingsActivity.kt` | Управление токенами и агентом |
| `RemoteAgentActivity.kt` | Чат с remote agent (streaming) |
| `RemoteAgentService.kt` | Foreground service |
| `RemoteAgentManager.kt` | Singleton manager |
| `HermesGatewayManager.kt` | SSH туннель (JSch) |
| `RemoteAgentViewModel.kt` | ViewModel для Remote Agent (streaming) |
