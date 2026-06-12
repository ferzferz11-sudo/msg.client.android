# Remote Agent — Документация

**Версия:** v1.1.3.0
**Обновлено:** 2026-06-12

## Архитектура

```
┌─────────────┐  gRPC          ┌──────────────┐  gRPC           ┌─────────────┐
│  Android    │ ──────────────→ │   Server     │ ←────────────── │   Remote    │
│  Client     │  GenerateToken  │   (Go)       │  Connect        │   Agent     │
│             │  ListTokens     │              │  (streaming)    │   (Python)  │
│             │  DeployTask     │              │                 │             │
└─────────────┘                 └──────────────┘                 └─────────────┘
```

## Компоненты

### 1. Token Management (HermesAgentService)

| RPC | Сервис | Описание |
|-----|--------|----------|
| `GenerateAgentToken` | `hermes_agent.HermesAgentService` | Генерация JWT токена |
| `RevokeAgentToken` | `hermes_agent.HermesAgentService` | Отзыв токена |
| `ListAgentTokens` | `hermes_agent.HermesAgentService` | Список токенов |

**Важно:** Начиная с v1.1.3.0, token RPC доступны любому авторизованному пользователю (не только админам).

### 2. Remote Agent Connection (HermesAgentService)

| RPC | Тип | Описание |
|-----|-----|----------|
| `Connect` | bidirectional streaming | Подключение агента |
| `HealthCheck` | unary | Проверка связи |

### 3. Task Management (ChatService)

| RPC | Сервис | Описание |
|-----|--------|----------|
| `ListRemoteAgents` | `messenger.ChatService` | Список подключённых агентов |
| `DeployAgentTask` | `messenger.ChatService` | Отправка задачи агенту |
| `GetRemoteAgentStatus` | `messenger.ChatService` | Статус агента |

## Android UI

### Экраны

| Activity | Назначение |
|----------|-----------|
| `RemoteAgentActivity` | Чат с агентом, отправка задач |
| `RemoteAgentSettingsActivity` | Управление токенами |
| `AgentListActivity` | Список AI агентов (Hermes) |

### Flow

1. Пользователь открывает "Агенты" из AI шторки
2. `RemoteAgentActivity` загружает список агентов (`ListRemoteAgents`)
3. Пользователь может перейти в настройки (⚙) для управления токенами
4. В настройках: генерация токена → копирование → запуск агента
5. Агент подключается через `Connect` и выполняет задачи

## Токены агентов

### Генерация

1. Открыть "Агенты" → ⚙ → "Сгенерировать токен"
2. Заполнить: имя агента, возможности, TTL
3. Нажать "Сгенерировать"
4. Скопировать токен (показывается один раз!)

### Подключение агента

```bash
python3 hermes_remote_agent.py --server host:port --token <jwt>
```

### Токены в БД

- Хранится SHA-256 хеш токена
- Токен показывается только при генерации
- `RevokeAgentToken` помечает `revoked = TRUE`

## Proto

### hermes_remote.proto

```protobuf
service HermesAgentService {
    rpc Connect(stream AgentMessage) returns (stream OrchestratorMessage);
    rpc HealthCheck(HealthCheckRequest) returns (HealthCheckResponse);
    rpc GenerateAgentToken(GenerateAgentTokenRequest) returns (GenerateAgentTokenResponse);
    rpc RevokeAgentToken(RevokeAgentTokenRequest) returns (RevokeAgentTokenResponse);
    rpc ListAgentTokens(ListAgentTokensRequest) returns (ListAgentTokensResponse);
}

message GenerateAgentTokenRequest {
    string agent_id = 1;
    string agent_name = 2;
    repeated string capabilities = 3;
    int32 ttl_hours = 4;
    string admin_user_id = 5;
}

message GenerateAgentTokenResponse {
    bool success = 1;
    string token = 2;
    string error = 3;
    int64 expires_at = 4;
}
```

### messenger.proto (ChatService)

```protobuf
service ChatService {
    // ... другие методы ...
    rpc ListRemoteAgents(ListRemoteAgentsRequest) returns (ListRemoteAgentsResponse);
    rpc DeployAgentTask(DeployAgentTaskRequest) returns (DeployAgentTaskResponse);
    rpc GetRemoteAgentStatus(GetRemoteAgentStatusRequest) returns (GetRemoteAgentStatusResponse);
}
```

## Известные проблемы (v1.1.3.0)

| Проблема | Статус | Workaround |
|----------|--------|------------|
| Токен не появляется в списке | ⚠️ Исследуется | Проверить логи сервера |
| JobCancellationException при генерации | ✅ Исправлено | — |
| Token RPC на неправильном сервисе | ✅ Исправлено | — |
| writeRawVarint32 deprecated | ✅ Исправлено | — |

## Отладка

### Android логи

```bash
adb logcat -s "RemoteAgentSettings" "HermesGrpc" "RemoteAgentViewModel"
```

Ключевые теги:
- `loadTokens: userId=...` — загрузка токенов
- `generateToken response: success=...` — ответ сервера
- `listRemoteAgents: received N agents` — список агентов

### Сервер логи

```bash
journalctl -u lavender-server-dev -f | grep "HermesAgentService"
```

Ключевые сообщения:
- `GenerateAgentToken: agentId=...` — запрос на генерацию
- `token saved: agentId=... hash=...` — токен сохранён
- `hermesDB is nil, token not persisted!` — проблема с БД
- `ListAgentTokens: adminUser=...` — запрос списка

## Тестирование

### Тест 1: Генерация токена

1. Открыть "Агенты" → ⚙
2. Сгенерировать токен
3. Проверить что токен показан в диалоге
4. Скопировать токен
5. Проверить что токен появился в списке

### Тест 2: Подключение агента

1. Запустить агент: `python3 hermes_remote_agent.py --server localhost:50052 --token <jwt>`
2. Проверить что агент появился в списке (статус "connected")
3. Отправить задачу: `ls -la`
4. Проверить что результат получен

### Тест 3: Отзыв токена

1. Отозвать токен из списка
2. Проверить что агент потерял подключение
