# Remote Agent — Документация

**Версия:** v1.1.3.10
**Дата:** 2026-06-13
**Ветка:** feat/1.1.3.x

---

## Обзор

Remote Agent — система удалённого управления сервером через Android клиент. Позволяет:
- Генерировать JWT токены для аутентификации агентов
- Запускать/останавливать агентов на сервере
- Отправлять задачи (shell, git, build, deploy, file, docker, AI)
- Получать результаты выполнения задач в реальном времени
- Подключаться через SSH туннель (Hermes Gateway) для безопасного доступа

---

## Архитектура

```
┌─────────────────────────────────────────────────────────────┐
│                    RemoteAgentService                        │
│                    (Foreground Service)                      │
│                                                             │
│  ┌─────────────────┐  ┌──────────────────┐                 │
│  │ HermesGateway   │  │ GrpcClient       │                 │
│  │ Manager         │  │ (persistent)     │                 │
│  │ (SSH tunnel)    │  │                  │                 │
│  └────────┬────────┘  └────────┬─────────┘                 │
│           │                    │                            │
│  ┌────────┴────────────────────┴─────────┐                 │
│  │         RemoteAgentManager            │                 │
│  │         (singleton, binds to App)      │                 │
│  └───────────────────────────────────────┘                 │
└─────────────────────────────────────────────────────────────┘
           │                              │
           │ ServiceConnection            │ ServiceConnection
           ▼                              ▼
┌──────────────────────┐    ┌──────────────────────────┐
│ RemoteAgentSettings  │    │ RemoteAgentActivity      │
│ Activity             │    │ (чат с агентом)          │
│ (настройки туннеля)  │    │                          │
└──────────────────────┘    └──────────────────────────┘
           │                              │
           ▼                              ▼
┌─────────────────────────────────────────────────────────────┐
│                      Server (Go)                             │
│                                                             │
│  ┌─────────────────┐  ┌──────────────────┐                 │
│  │ ChatService     │  │ HermesAgent      │                 │
│  │ (gRPC)          │  │ Service (gRPC)   │                 │
│  │                 │  │                  │                 │
│  │ DeployAgentTask │  │ GenerateToken    │                 │
│  │ ListAgents      │  │ RevokeToken      │                 │
│  │ GetAgentStatus  │  │ ListTokens       │                 │
│  └────────┬────────┘  └────────┬─────────┘                 │
│           │                    │                            │
│  ┌────────┴────────────────────┴─────────┐                 │
│  │         RemoteAgentManager            │                 │
│  │         (server-side, Go)              │                 │
│  └───────────────────────────────────────┘                 │
└─────────────────────────────────────────────────────────────┘
           │
           ▼
┌─────────────────────────────────────────────────────────────┐
│                   Remote Agent (Python)                      │
│                                                             │
│  ┌─────────────────┐  ┌──────────────────┐                 │
│  │ hermes_remote   │  │ adapter.py       │                 │
│  │ _agent.py       │  │ (Platform        │                 │
│  │                 │  │  Adapter)        │                 │
│  │ Task execution  │  │                  │                 │
│  │ Retry/reconnect │  │ Shell/Git/Build  │                 │
│  └─────────────────┘  └──────────────────┘                 │
└─────────────────────────────────────────────────────────────┘
```

---

## Компоненты

### 1. RemoteAgentService (Foreground Service)

**Файл:** `ui/remote/RemoteAgentService.kt`

Фоновый сервис для поддержания подключения к Remote Agent при переходе между Activity.

**Ключевые особенности:**
- `START_STICKY` — перезапускается системой после убийства
- Управляет SSH туннелем через `HermesGatewayManager`
- Уведомление с статусом подключения
- Кнопка "Отключить" в уведомлении

**Жизненный цикл:**
1. `startService()` → `onCreate()` → `startForeground()`
2. `bindService()` из Activity → `onBind()` → возврат `IBinder`
3. `unbindService()` из Activity → `onUnbind()` (сервис продолжает работать)
4. `stopService()` / `stopSelf()` → `onDestroy()`

**Методы:**
- `createTunnel(...)` — создание SSH туннеля
- `closeTunnel()` — закрытие туннеля
- `sendTask(...)` — отправка задачи агенту
- `isConnected()` — проверка подключения
- `getStatusText()` — текстовый статус

### 2. RemoteAgentManager (Singleton)

**Файл:** `ui/remote/RemoteAgentManager.kt`

Singleton для привязки UI к RemoteAgentService.

**Ключевые особенности:**
- `init(context)` — инициализация (вызвать один раз)
- `bind(listener)` — привязка к сервису + запуск foreground
- `unbind(listener)` — отвязка (сервис продолжает работать)
- `stopService()` — полная остановка сервиса
- `RemoteAgentStateListener` — callback для изменения состояния

**Состояния:**
```kotlin
data class AgentConnectionState(
    val isConnected: Boolean,
    val isTunnelActive: Boolean,
    val tunnelAddress: String,
    val statusText: String
)
```

### 3. HermesGatewayManager (SSH Tunnel)

**Файл:** `ui/remote/HermesGatewayManager.kt`

Управление SSH туннелем через JSch.

**Ключевые особенности:**
- Создание SSH туннеля: `localhost:localPort → serverHost:serverPort via sshHost:sshPort`
- Типизированные ошибки: `UNKNOWN_HOST`, `CONNECTION_REFUSED`, `AUTH_FAILED`, `TIMEOUT`, `PORT_IN_USE`
- Сохранение настроек в SharedPreferences
- Авто-проверка активности туннеля

**Настройки:**
```kotlin
data class GatewaySettings(
    val sshHost: String,
    val sshPort: Int = 22,
    val sshUser: String,
    val sshPassword: String,
    val serverHost: String = "localhost",
    val serverPort: Int = 50051,
    val localPort: Int = 50052,
    val autoConnect: Boolean = false
)
```

### 4. RemoteAgentSettingsActivity

**Файл:** `ui/remote/RemoteAgentSettingsActivity.kt`

Экран настроек Remote Agent.

**Функции:**
- Генерация JWT токенов
- Список токенов с возможностью отзыва
- Копирование токена/команды
- Запуск/остановка агента на сервере
- Настройка SSH туннеля (Hermes Gateway)
- Индикатор состояния подключения

**Привязка к сервису:**
- `onResume()` → `RemoteAgentManager.bind(this)`
- `onPause()` → `RemoteAgentManager.unbind(this)`
- Реализует `RemoteAgentStateListener`

### 5. RemoteAgentActivity

**Файл:** `ui/remote/RemoteAgentActivity.kt`

Чат с Remote Agent.

**Функции:**
- Отправка задач (shell, git, build, deploy, file, docker, AI)
- Получение результатов в реальном времени
- Выбор типа задачи (ChipGroup)
- Индикатор подключения
- Авто-рефреш статуса каждые 30 сек

**Привязка к сервису:**
- Аналогично RemoteAgentSettingsActivity

### 6. RemoteAgentViewModel

**Файл:** `ui/remote/RemoteAgentViewModel.kt`

ViewModel для управления состоянием Remote Agent.

**State:**
- `agents: StateFlow<List<RemoteAgentInfo>>` — список агентов
- `selectedAgent: StateFlow<RemoteAgentInfo?>` — выбранный агент
- `isConnected: StateFlow<Boolean>` — статус подключения
- `messages: StateFlow<List<RemoteAgentMessage>>` — сообщения чата
- `isTyping: StateFlow<Boolean>` — индикатор выполнения
- `error: StateFlow<String?>` — ошибки

**Методы:**
- `loadAgents()` — загрузка списка агентов
- `selectAgent(agent)` — выбор агента
- `sendMessage(text, userId, taskType)` — отправка задачи
- `generateToken(...)` — генерация токена
- `revokeToken(agentId, adminUserId)` — отзыв токена
- `refreshAgentStatus()` — обновление статуса

---

## Протокол взаимодействия

### 1. Генерация токена
```
Android → Server: GenerateAgentToken(agentId, agentName, capabilities, ttlHours, adminUserId)
Server → Android: GenerateAgentTokenResponse(success, token, expiresAt)
```

### 2. Запуск агента
```
Android → Server: StartAgentOnServer(agentId, agentName, token, serverAddress, adminUserId)
Server → Android: StartAgentResponse(success, pid, error)
```

### 3. Отправка задачи
```
Android → Server: DeployAgentTask(agentId, taskType, params, tunnelMode, tunnelHost, ...)
Server → Agent: Connect + TaskRequest (streaming)
Agent → Server: TaskResponse (streaming)
Server → Android: DeployAgentTaskResponse(success, stdout, stderr, exitCode, durationMs)
```

### 4. SSH туннель (Hermes Gateway)
```
Android → SSH Server: JSch createTunnel(localPort, serverHost, serverPort)
Android → localhost:localPort → SSH Server → serverHost:serverPort
```

---

## Типы задач

| Тип | Описание |
|-----|----------|
| shell | Выполнение shell команд |
| git | Git операции |
| build | Сборка проекта |
| deploy | Деплой |
| file | Операции с файлами |
| docker | Docker операции |
| AI | AI задачи |

---

## Безопасность

- JWT токены с TTL
- Токены показываются ОДИН РАЗ при генерации
- Возможность отзыва токенов
- SSH туннель для шифрования трафика
- Проверка `created_by` для фильтрации токенов

---

## Известные проблемы

- При повороте экрана Activity пересоздаётся, но сервис продолжает работать
- SSH туннель может разрываться при потере сети (авто-реконнект через START_STICKY)
- Android не резолвит SSH aliases — нужно использовать IP адрес

---

## Streaming (v1.1.3.8)

### Протокол
```
Агент → AGENT_TASK_STREAM_UPDATE(done=False) → onStream → streamCh → клиент (stdout_chunk)
Агент → AGENT_TASK_STREAM_UPDATE(done=True)  → onStream → streamDone flag, continue
Агент → AGENT_TASK_RESULT                    → onResult → close(streamCh)
Сервер → клиент: один done=True с полными Stdout/Stderr/ExitCode/DurationMs
```

### Ключевые изменения (v1.1.3.8)
- Сервер отправляет **один** `done=True` с полными данными из TaskResult
- Раньше: два `done=True` (первый пустой из stream update, второй полный из TaskResult)
- Android: при `done=True` использует `update.stdout`/`update.stderr` (полные буферы), fallback на чанки

### Компоненты
- `server_remote.go:DeployAgentTaskStream` — серверный handler
- `hermes_remote_manager.go:HandleTaskStream` — callback для stream updates
- `hermes_agent_service.go:handleTaskStreamUpdate` → `HandleTaskStream`
- `RemoteAgentViewModel.kt:sendMessageStreaming` — клиентский Flow collector
- `HermesGrpc.kt:deployAgentTaskStream` → callbackFlow с ClientCall.Listener

---

## Логи

Все компоненты Remote Agent логируют с тегом `RemoteAgent*`:
- `RemoteAgentService` — сервис
- `RemoteAgentManager` — менеджер
- `HermesGatewayManager` — SSH туннель
- `RemoteAgentViewModel` — ViewModel

---

## Тестирование

### Модульные тесты
- `/root/msg.remote.agent/tests/` — 40 unit tests для hermes_remote_agent.py

### Интеграционные тесты
1. Генерация токена → появление в списке
2. Запуск агента → статус "подключён"
3. Отправка задачи → получение результата
4. SSH туннель → подключение через шлюз
5. Отзыв токена → исчезновение из списка

---

## Деплой

### Android
```bash
# ferz локально:
git pull && ./gradlew assembleRelease
```

### Сервер
```bash
# С сервера (ssh lava):
./scripts/release.sh 1.1.3.5 --deploy

# С Mac (удалённо):
./scripts/release.sh 1.1.3.5 --deploy --remote
```

---

## История версий

| Версия | Дата | Описание |
|--------|------|----------|
| v1.1.3.8 | 2026-06-13 | Streaming fix: single done=True with full TaskResult data. ChatAdapter filter() fix. |
| v1.1.3.7 | 2026-06-13 | Streaming: DeployAgentTaskStream, sendMessageStreaming, HermesGrpc callbackFlow |
| v1.1.3.5 | 2026-06-13 | Foreground service + singleton manager для persistent connection |
| v1.1.3.4 | 2026-06-13 | Hermes Gateway (SSH туннель) |
| v1.1.3.3 | 2026-06-12 | Task results + script path fix |
| v1.1.3.2 | 2026-06-12 | Token management + UI |
| v1.1.3.1 | 2026-06-12 | Мелкие исправления |
| v1.1.3.0 | 2026-06-12 | Remote Agent UI + Token Management |
