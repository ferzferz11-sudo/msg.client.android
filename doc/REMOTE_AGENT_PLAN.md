# План работ: Интеграция Hermes Remote Agent

## Общая цель
Сделать полноценное подключение Android клиента к серверу через агент,
используя gRPC Connect (bidirectional streaming) из hermes_remote.proto.

Архитектура:
```
┌─────────────┐  gRPC          ┌──────────────┐  gRPC           ┌─────────────┐
│  Android    │ ──────────────→ │   Server     │ ←────────────── │   Hermes    │
│  Client     │  DeployAgent    │   ChatService│  Connect        │   Agent     │
│             │  Task           │              │  (streaming)    │   (daemon)  │
└─────────────┘                 └──────────────┘                 └─────────────┘
                                       │
                                       │ маршрутизация
                                       ▼
                                ┌──────────────┐
                                │  Orchestrator│
                                └──────────────┘
```

---

## Этап 1: Серверная часть (Go)

### 1.1 Исправить баг в DeployAgentTask
**Файл:** `server_ai.go`
- Добавить `AgentID: req.AgentId` в создание `RemoteTask`
- Без этого агент никогда не получит задачу

### 1.2 Исправить баг в GetRemoteAgentStatus
**Файл:** `hermes_agent_service.go` / `server_ai.go`
- Убедиться что `GetRemoteAgentStatus` возвращает правильный статус агента
- Проверить что `activeTasks` и `lastHeartbeat` заполняются корректно

### 1.3 Протестировать сервер
- Запустить сервер: `go run main.go`
- Проверить что `ListRemoteAgents` возвращает список
- Проверить что `GenerateAgentToken` работает
- Проверить что `RevokeAgentToken` работает

**Критерий готовности:** Сервер запущен, RPC работают, баги исправлены

---

## Этап 2: Агент на Python (hermes_remote_agent.py)

### 2.1 Сгенерировать Python proto файлы
- Установить `grpcio` и `grpcio-tools`
- Сгенерировать `hermes_remote_pb2.py` и `hermes_remote_pb2_grpc.py` из `hermes_remote.proto`

### 2.2 Реализовать агента
**Файл:** `hermes-agent/hermes_remote_agent.py`

Методы:
- `connect()` — подключение к серверу через gRPC bidirectional stream, отправка RegistrationInfo
- `run()` — основной цикл получения задач
- `handle_task(task)` — маршрутизация задач по типу
- `exec_shell(params)` — выполнение shell команд
- `exec_git(params)` — git операции
- `exec_build(params, working_dir)` — сборка проектов
- `read_file(params)` — чтение файлов
- `write_file(params)` — запись файлов
- `exec_docker(params)` — docker команды
- `send_result(task_id, status, stdout, stderr, exit_code)` — отправка результата
- `heartbeat_loop()` — отправка heartbeat каждые 30 секунд

### 2.3 Конфигурация агента
**Файл:** `hermes-agent/config.json`
```json
{
    "server_addr": "localhost:50052",
    "agent_id": "hermes-agent-1",
    "agent_name": "Hermes Agent",
    "auth_token": "<jwt-token-from-app>",
    "capabilities": ["shell", "git", "build", "file", "docker", "ai"]
}
```

### 2.4 Тестирование агента
- Запустить агент: `python3 hermes_remote_agent.py --config config.json`
- Проверить что агент подключается к серверу
- Проверить что `ListRemoteAgents` показывает агента как "connected"
- Проверить heartbeat

**Критерий готовности:** Агент подключается к серверу, появляется в списке агентов

---

## Этап 3: Android клиент (Kotlin)

### 3.1 Исправить отправку задач
**Файл:** `RemoteAgentViewModel.kt`
- `sendMessage()` должен вызывать `GrpcClient.deployAgentTask()`
- Передавать `taskType` из UI (chip selector)
- Передавать `params` в правильном формате (protobuf map)

### 3.2 Исправить получение результатов
**Файл:** `RemoteAgentViewModel.kt`
- Реализовать streaming получение результатов от агента
- ИЛИ реализовать polling через `GetRemoteAgentStatus`
- Показывать результаты в чате

### 3.3 UI улучшения
**Файл:** `RemoteAgentActivity.kt`
- Показывать статус подключения агента (connected/disconnected)
- Показывать имя выбранного агента
- Показывать лог выполнения задач (stdout, stderr, exit code)

**Критерий готовности:** Android клиент отправляет задачи, получает результаты

---

## Этап 4: Интеграционное тестирование

### 4.1 Полный цикл
1. Сгенерировать токен в приложении (Агенты → ⚙ → Сгенерировать токен)
2. Скопировать токен
3. Запустить агент с этим токеном
4. Отправить задачу из приложения (например, "ls -la")
5. Получить результат в чате

### 4.2 Тест по типам задач
- Shell: `ls -la`, `pwd`, `whoami`
- Git: `status`, `log --oneline -5`
- File read: `cat /etc/hostname`
- File write: `echo "hello" > /tmp/test.txt`
- Build: `go build ./...`

### 4.3 Тест отключения/подключения
- Отключить агента → статус должен стать "disconnected"
- Подключить снова → статус должен стать "connected"

**Критерий готовности:** Полный цикл работает стабильно

---

## Этап 5: Финализация

### 5.1 Коммит и пуш
- Все изменения в сервере (Go)
- Все изменения в клиенте (Kotlin)
- Агент (Python)

### 5.2 Обновить документацию
- Обновить REMOTE_AGENT.md
- Обновить TASKS.md

### 5.3 Релиз
- Собрать APK локально
- Протестировать на устройстве

---

## Текущий статус

**Сделано:**
- ✅ Proto классы на клиенте
- ✅ gRPC методы (generate/revoke/list tokens)
- ✅ gRPC методы (deployAgentTask, getRemoteAgentStatus)
- ✅ RemoteActivity + layout
- ✅ TokenDialog
- ✅ AIBottomSheet секция "Агенты"
- ✅ Серверная часть (отчасти)
- ⚠️ Hermes Agent daemon (частично, не работает)

**Не сделано:**
- ❌ Исправить баг DeployAgentTask (AgentID)
- ❌ Сгенерировать Python proto файлы
- ❌ Доделать агента (handle_task, heartbeat)
- ❌ Streaming/polling результатов на клиенте
- ❌ UI для лога выполнения задач
- ❌ Интеграционное тестирование

## Приоритет выполнения
1. Этап 1 (сервер) — 1 час
2. Этап 2 (агент) — 2 часа
3. Этап 3 (клиент) — 1 час
4. Этап 4 (тестирование) — 1 час
5. Этап 5 (финализация) — 30 минут

**Итого:** ~5.5 часов
