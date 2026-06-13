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
