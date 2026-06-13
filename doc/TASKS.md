# Lavender Messenger (Android) — Задачи

**Версия:** 1.1.3.7
**Обновлено:** 2026-06-13
**Ветка:** feat/1.1.3.x

---

## ✅ v1.1.3.7 — Streaming + ErrorHandler + P0 Bugfixes

### Server-side streaming: DeployAgentTaskStream
  - Полный stdout/stderr + exit_code + duration_ms при done=true
- ✅ **Сервер**: `server_ai.go` — `DeployAgentTaskStream` handler
  - Отправляет задачу через `SendTask` → подписывается на `onStream` callback
  - Стримит промежуточные stdout/stderr/progress через gRPC stream
  - Финальный результат с done=true при завершении
- ✅ **Сервер**: `hermes_remote_manager.go` — `HandleTaskStream` + `RemoteTaskStreamUpdate`
  - Callback `onStream` для промежуточных обновлений от агента
- ✅ **Android**: `MessengerProto.kt` — `DeployAgentTaskStreamResponseProto`
- ✅ **Android**: `HermesGrpc.kt` — `deployAgentTaskStream()` возвращает `Flow`
  - `callbackFlow` с `ClientCall.Listener` для server-side streaming
  - `awaitClose` для корректной отмены
- ✅ **Android**: `GrpcClient.kt` — фасад `deployAgentTaskStream()`
- ✅ **Android**: `RemoteAgentViewModel.kt` — `sendMessageStreaming()`
  - Использует `Flow.collect` для real-time обновления сообщений
  - Буферизует stdout/stderr чанки, показывает progress
  - Обновляет placeholder-сообщение по мере поступления данных
- ✅ **Android**: `RemoteAgentActivity.kt` — переключение на `sendMessageStreaming`
- ✅ **Сервер**: деплой на dev

### Новая система логирования ошибок
- ✅ `ErrorHandler.kt` — единый обработчик ошибок с автоматическим добавлением в AppLog
  - Поддержка CancellationException (INFO, не ERROR — это не ошибка)
  - Поддержка gRPC StatusRuntimeException (уровень кода)
  - Поддержка Network errors (UnknownHostException, ConnectException, SocketTimeoutException)
  - Поддержка SecurityException
  - Методы: handle(), log(), warn()

### Исправления
- ✅ **Bugfix: "Job was cancelled" тост** — CancellationException в RemoteAgentViewModel.sendMessage больше не показывает тост
  - Добавлен отдельный catch для CancellationException
  - Логируется как INFO в AppLog (не ERROR)
  - re-throw для structured concurrency
- ✅ **AppLog.error() добавлен во все catch-блоки** где показываются Toast ошибки:
  - RemoteAgentViewModel: loadAgents, generateToken, revokeToken, sendMessage, sendMessageStreaming
  - RemoteAgentService: createTunnel, sendTask
  - RemoteAgentActivity: error collector
  - ChatListActivity: session terminated

### Архитектура streaming
```
Client → DeployAgentTaskStream → Server → SendTask → Agent
                                                         │
                              onStream callback ←────────┘
                                    │
Client ← stream.Send(update) ←──────┘
  │
  ├── stdout_chunk → append to buffer → update chat message
  ├── stderr_chunk → append to buffer → update chat message
  ├── progress → show status → update chat message
  └── done=true → finalize message with full stdout/stderr
```

### Архитектура логирования
```
Exception → ErrorHandler.handle(source, throwable)
  ├── CancellationException → AppLog.info()
  ├── Network error → AppLog.error()
  ├── gRPC error → AppLog.error() [with status code]
  ├── Permission error → AppLog.error()
  └── Other → AppLog.error()
```

### P0 Bugfixes
- ✅ **"Агент не выбран"** — `ensureAgentSelected()` в `RemoteAgentViewModel`
  - Автоматическая загрузка агентов с сервера при отправке
  - Fallback: создание локального агента с именем из настроек шлюза (`sshHost`)
  - Работает для обоих режимов (шлюз + токен)
  - Убрана рекурсия в `sendMessageStreaming()`
- ✅ **Status bar** — `ConstraintLayout` + фиксированные 48dp кнопки + `?android:textColorPrimary`
  - Убрана установка цвета текста из `ThemeStore.textSecondaryColor` (был bug: невидимый текст)
- ✅ **"Job was cancelled" тост** — `loadAgents()` не пишет в `_error`, только `AppLog.info()`
  - Убраны дублирующие `refreshAgentStatus()` из `onCreate` и `onStateChanged()`
- ✅ **Сервер** — Remote Agent RPC вынесен в `server_remote.go`
  - Единый `ensureRemoteManager()`, graceful degradation, stale detection
  - Проверка существования агента перед отправкой задачи

---

### Foreground Service + Singleton Manager
- ✅ `RemoteAgentService.kt` — foreground service с SSH туннелем + gRPC
- ✅ `RemoteAgentManager.kt` — singleton для bind/unbind UI к сервису
- ✅ `RemoteAgentSettingsActivity.kt` — ServiceConnection + RemoteAgentStateListener
- ✅ `RemoteAgentActivity.kt` — ServiceConnection + RemoteAgentStateListener
- ✅ `AndroidManifest.xml` — RemoteAgentService + FOREGROUND_SERVICE_CONNECTED_DEVICE
- ✅ Notification показывает статус подключения
- ✅ START_STICKY — перезапускается системой

---

## ✅ v1.1.3.5 — Remote Agent: UI исправления (commit ee5e115)

### Исправления чата с агентом
- ✅ TextWatcher для send button — показывается только при наличии текста
- ✅ CommandButton с CommandBottomSheet — 12 команд агента (help, status, logs, deploy, restart, git, docker, ps, df, uptime)
- ✅ Авто-прокрутка чата при новых сообщениях
- ✅ Исправлен баг: сообщения не отправлялись после ввода текста
- ✅ Исправлен баг: иконка команд была без обработчика

---

## ✅ v1.1.3.4 — Hermes Gateway (SSH туннель)
- ✅ `HermesGatewayManager.kt` — класс для управления SSH туннелем (JSch)
- ✅ `RemoteAgentSettingsActivity.kt` — UI секция "Подключение через шлюз"
- ✅ `activity_remote_agent_settings.xml` — layout с полями SSH хоста, портов, кнопками
- ✅ JSch зависимость `com.jcraft:jsch:0.1.55` в build.gradle.kts
- ✅ Сохранение настроек туннеля в SharedPreferences
- ✅ Команды агента используют туннельный адрес при активном туннеле

---

## ✅ v1.1.3.2 — Remote Agent Token Management

### Android
- **Генерация JWT токенов** — работает через `hermes_agent.HermesAgentService/GenerateAgentToken`
- **Список токенов** — отображается сразу после генерации (локальный кэш)
- **Копирование токена/команды** — кнопки в каждом элементе списка
- **Отзыв токена** — кнопка "Отозвать" с подтверждением
- **Запуск/остановка агента** — StartAgent/StopAgent RPC
- **UI статуса** — зелёный индикатор при запущенном агенте
- **Персистентность** — выбранный агент сохраняется в SharedPreferences

---

## ✅ v1.1.3.1 — Мелкие исправления и полировка

### UI/UX
- Убран Toast "Вход выполнен" после авторизации
- Авто-прокрутка вниз при отправке сообщения
- Версия приложения на SplashActivity (BuildConfig.VERSION_NAME)

### Code quality
- Debug логи обёрнуты в BuildConfig.DEBUG

---

## 📋 Бэклог

### Высокий приоритет
- [x] Streaming результатов задач агентом обратно клиенту ✅ v1.1.3.7
- [x] Favorites мерцание при обновлении списка чатов ✅ v1.1.2.8 (подтверждено v1.1.3.7)

### Средний приоритет
- [ ] Модульные тесты для OWL streaming (owl_test.go — сервер)
- [ ] Кэширование запросов чатов

### Низкий приоритет
- [ ] Qdrant + CLIP (production RAG)
- [ ] Structured logging (zap/logrus)
- [ ] Prometheus метрики

---

## 🔑 Ключевые решения

| Решение | Обоснование |
|---------|-------------|
| ErrorHandler | Единая точка для логирования всех исключений с контекстом |
| AppLog для Toast | Все Toast-ошибки автоматически попадают в журнал ошибок |
| CancellationException → INFO | Отмена корутины это не ошибка, а нормальное поведение |
| activityScope | Независимый CoroutineScope, переживает пересоздание Activity |
| RemoteAgentService + RemoteAgentManager | Foreground service + singleton для persistent connection (v1.1.3.5) |

---

## 📁 Ключевые файлы

| Файл | Назначение |
|------|------------|
| `ErrorHandler.kt` | Единый обработчик ошибок (NEW v1.1.3.7) |
| `AppLog.kt` | Глобальный логгер (in-memory, до 500 записей) |
| `GrpcClient.kt` | Единая точка доступа к gRPC (facade) |
| `HermesGrpc.kt` | Hermes/Remote Agent gRPC методы (streaming v1.1.3.7) |
| `MessengerProto.kt` | Proto data classes (streaming v1.1.3.7) |
| `RemoteAgentSettingsActivity.kt` | Управление токенами и агентом |
| `RemoteAgentActivity.kt` | Чат с remote agent (streaming v1.1.3.7) |
| `RemoteAgentService.kt` | Foreground service (v1.1.3.5) |
| `RemoteAgentManager.kt` | Singleton manager (v1.1.3.5) |
| `HermesGatewayManager.kt` | SSH туннель (JSch) |
| `RemoteAgentViewModel.kt` | ViewModel для Remote Agent (streaming v1.1.3.7) |
