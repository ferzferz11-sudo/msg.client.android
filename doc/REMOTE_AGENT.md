# Remote Agent — Проект реализации

## Цель

Позволить любому пользователю подключить своего удалённого агента (Hermes daemon или другой) к Лаве через токен аутентификации. Агент получает задачи от оркестратора и может выполнять их (shell, git, docker, AI).

## Концепция

- **Удалённый агент** — программа (Hermes daemon), запущенная на другой машине, которая подключается к серверу Лавы через gRPC с JWT токеном
- **Токен** — генерируется в мобильном приложении, передаётся агенту (показывается один раз)
- **Оркестратор** — серверный компонент, который маршрутизирует задачи на подключённых агентов
- **Типы задач**: shell, file_read, file_write, git, build, deploy, docker, custom, ai

## Архитектура

```
┌─────────────┐     gRPC      ┌──────────────┐     gRPC      ┌─────────────┐
│  Android    │ ───────────→  │   Server     │ ←───────────  │  Remote     │
│  Client     │  Generate     │   ChatService│  Connect      │  Agent      │
│             │  AgentToken   │              │  (streaming)  │  (daemon)   │
└─────────────┘               └──────────────┘               └─────────────┘
                                     │
                                     │ маршрутизация
                                     ▼
                              ┌──────────────┐
                              │  Orchestrator│
                              │  (Hermes)    │
                              └──────────────┘
```

## Компоненты

### 1. Отдельная активити — `RemoteAgentActivity`

- Не загружает список чатов
- Содержит: чат с агентом, настройки, управление токенами
- Доступна из шторки AI как пункт "Удалённые агенты"

### 2. Управление токенами

- **Генерация токена**: `GenerateAgentToken(agentId, agentName, capabilities, ttlHours)`
- **Отзыв токена**: `RevokeAgentToken(agentId)`
- **Список токенов**: `ListAgentTokens()` — показывает все токены пользователя
- Токен показывается ОДИН РАЗ после генерации

### 3. Чат с агентом

- Отправка сообщений агенту (задачи)
- Получение результатов в реальном времени (streaming)
- Поддержка типов задач: shell, file, git, docker, ai

### 4. Настройки агента

- Agent ID, Agent Name
- Capabilities (выбор из списка: shell, git, build, deploy, file, docker, ai)
- TTL токена (часы)
- Статус подключения

## Протокол (hermes_remote.proto)

### Агент подключается:
1. `Connect(stream AgentMessage) → stream OrchestratorMessage`
2. При подключении отправляет `RegistrationInfo` с `auth_token`
3. Сервер валидирует JWT токен

### Задачи от оркестратора:
- `ORCHESTRATOR_TASK` → `Task` с типом (TASK_SHELL, TASK_AI, ...)
- Агент выполняет и отправляет `TaskResult`

### Типы задач:
- `TASK_SHELL` — shell команда
- `TASK_FILE_READ` / `TASK_FILE_WRITE` — файлы
- `TASK_GIT` — git операции
- `TASK_BUILD` — сборка
- `TASK_DEPLOY` — деплой
- `TASK_DOCKER` — docker
- `TASK_CUSTOM` — пользовательский скрипт
- `TASK_AI` — AI-ответ (агент сам решает)

## UI/UX

### Шторка AI (AIBottomSheet)
- Добавить пункт "🖥 Удалённые агенты" → открывает `RemoteAgentActivity`

### RemoteAgentActivity
- Toolbar: название агента + статус (подключён/отключён)
- Чат: сообщения пользователя + ответы агента
- Настройки: токен, capabilities, TTL
- Кнопки: "Сгенерировать токен", "Отозвать токен", "Подключить"

### Диалог генерации токена
- Поля: Agent Name, Capabilities (мультивыбор), TTL
- Результат: токен (копировать в буфер)

## Файлы для изменения

### Новые файлы:
- `ui/remote/RemoteAgentActivity.kt` — основной экран
- `ui/remote/RemoteAgentViewModel.kt` — ViewModel
- `ui/remote/RemoteAgentAdapter.kt` — адаптер чата
- `ui/remote/TokenDialog.kt` — диалог генерации токена
- `layout/activity_remote_agent.xml` — layout
- `layout/dialog_token_generate.xml` — layout диалога
- `layout/item_remote_agent_message.xml` — layout сообщения

### Существующие файлы (изменения):
- `data/proto/MessengerProto.kt` — добавлены proto классы для токенов ✅
- `data/grpc/HermesGrpc.kt` — добавлены методы generate/revoke/list ✅
- `data/grpc/GrpcClient.kt` — добавлены методы ✅
- `ui/widget/AIBottomSheet.kt` — добавить пункт "Удалённые агенты"
- `ChatListActivity.kt` — обработка нажатия на пункт

### Сервер (уже сделано):
- `hermes_remote.proto` — добавлен TASK_AI ✅
- `messenger.proto` — добавлены RPC для токенов ✅
- `server_ai.go` — реализация Generate/Revoke/List ✅

## Этапы реализации

1. ✅ Proto классы на клиенте
2. ✅ gRPC методы на клиенте
3. ✅ Серверная часть
4. ✅ RemoteActivity + layout
5. ✅ ViewModel + чат
6. ✅ TokenDialog
7. ✅ Интеграция с AIBottomSheet
8. ⬜ Тестирование

## Будущие расширения

- Поддержка других типов агентов (не только Hermes daemon)
- Мониторинг статуса агента (heartbeat, метрики)
- Управление задачами (отмена, приоритеты)
- Логирование выполнения задач
