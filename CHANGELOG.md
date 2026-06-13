# Lavender Messenger — Android Changelog

## [1.1.3.7] - 2026-06-13
### Streaming результатов задач агентом
- **DeployAgentTaskStream** — server-side streaming для real-time stdout/stderr/progress
  - `HermesGrpc.kt`: `deployAgentTaskStream()` → `callbackFlow` с `ClientCall.Listener`
  - `GrpcClient.kt`: фасад `deployAgentTaskStream()`
  - `MessengerProto.kt`: `DeployAgentTaskStreamResponseProto` (поля 1-11)
  - `RemoteAgentViewModel.kt`: `sendMessageStreaming()` — Flow.collect → real-time обновление чата
  - `RemoteAgentActivity.kt`: переключение на streaming
- **ErrorHandler** — единый обработчик ошибок
  - `CancellationException` → `AppLog.info()` (не ERROR — это нормальное поведение)
  - `gRPC StatusRuntimeException` → `AppLog.error()` с кодом статуса
  - Network errors, SecurityException → `AppLog.error()`
- **AppLog** добавлен во все catch-блоки с Toast ошибками
- **Bugfix**: "Job was cancelled" тост больше не появляется (CancellationException обрабатывается отдельно)

### P0 Bugfixes
- **"Агент не выбран" исправлен** — `ensureAgentSelected()` в `RemoteAgentViewModel`
  - Автоматическая загрузка агентов с сервера при отправке сообщения
  - Fallback: создание локального агента с именем из настроек шлюза (`sshHost`)
  - Работает для обоих режимов (шлюз + токен)
  - Убрана рекурсия в `sendMessageStreaming()`
- **Status bar в RemoteAgentActivity**
  - `ConstraintLayout` вместо `LinearLayout` — кнопки Start/Stop фиксированы 48dp
  - Текст статуса использует `?android:textColorPrimary` (контрастный на любых темах)
  - Убрана установка цвета текста из темы (был bug: невидимый текст на кастомных темах)
- **"Job was cancelled" тост подавлен**
  - `loadAgents()` не пишет в `_error` (только `AppLog.info`)
  - Убраны дублирующие `refreshAgentStatus()` из `onCreate` и `onStateChanged()`
- **Сборка**: ./gradlew compileDebugKotlin — OK

### Server Changes
- `server_remote.go` — все Remote Agent RPC вынесены из `server_ai.go`
  - `ListRemoteAgents`, `GetRemoteAgentStatus`, `DeployAgentTask`, `DeployAgentTaskStream`
  - `ensureRemoteManager()` — единая проверка зависимостей
  - Graceful degradation: пустой список вместо ошибки если менеджер недоступен
  - Stale detection для агентов (heartbeat > 120с → status="stale")
  - Проверка существования агента перед отправкой задачи
- **Prod сервер обновлён** (v1.1.3.7-stable)

### UI/UX Changes
- **Убран выбор сервера из шторок логина/регистрации** — сервер всегда берётся из CredentialStore (по умолчанию prod)
- **Переключение сервера** — только через ServersActivity ( eingeben server → login sheet на выбранный сервер)
- **ServersActivity** — при выборе сервера открывается шторка логина напрямую на этот сервер
- **Скрыт dragHandle** в шторках логина/регистрации (белая полоска убрана)
- **Fallback на prod** (`13.140.25.249:50051`) если CredentialStore пуст

### Bugfixes
- **Room DB migration 8→9** — добавлены все недостающие столбцы с `try/catch` для совместимости со старыми БД
- **androidViewGroup parent** — исправлен `instanceof` → Kotlin `is` каст для скрытия контейнера spinner'а

## [1.1.3.6] - 2026-06-13
### Remote Agent — New Settings UI + Chat Improvements
- **Tab-based Settings** — вкладки «Шлюз» (SSH туннель) и «Токен» (JWT + start/stop)
- **Инструкции** для обоих режимов подключения
- **Статус подключения** на тулбаре чата (шлюз IP / токен)
- **Команды агента** — быстрые команды (help, status, logs, deploy, restart, git, docker, ps, df, uptime)
- **Авто-прокрутка чата** при новых сообщениях

## [1.1.3.5] - 2026-06-13
### Remote Agent — Persistent Background Connection
- **Foreground Service** — `RemoteAgentService.kt` с SSH туннелем + gRPC
- **Singleton Manager** — `RemoteAgentManager.kt` для привязки UI к сервису
- **START_STICKY** — перезапускается системой, notification со статусом
- **AndroidManifest.xml** — RemoteAgentService + FOREGROUND_SERVICE_CONNECTED_DEVICE

## [1.1.3.4] - 2026-06-13
### Remote Agent — Hermes Gateway (SSH Tunnel)
- **HermesGatewayManager.kt** — управление SSH туннелем через JSch
- **UI** — секция "Подключение через шлюз" в настройках
- **MessengerProto.kt** — tunnel_mode поля (6-13)
- **JSch зависимость** — `com.jcraft:jsch:0.1.55`

## [1.1.3.3] - 2026-06-12
### Remote Agent — Task Results + Script Path Fix
- **Task results** — stdout/stderr/exitCode/durationMs в чате
- **Script path** — `/root/msg.remote.agent/` (было `/root/msg/hermes-agent/`)
- **Proto** — `DeployAgentTaskResponseProto` расширен

## [1.1.3.2] - 2026-06-12
### Remote Agent — Token Management + UI
- **Генерация JWT токенов** через `hermes_agent.HermesAgentService/GenerateAgentToken`
- **Список токенов** — отображается сразу после генерации
- **Копирование токена/команды**, отзыв токена
- **Start/Stop агента** через RPC

## [1.1.3.1] - 2026-06-12
### AI шторка — исправления
- **Новый чат с оркестратором отображается сразу**
- **Удаление чата не ломает список**
- **Убран автоскролл** на последнее сообщение
