# Remote Agent — Reference Documentation

**Version:** v1.1.3.36 | **Status:** Stable, not actively developed

---

## Обзор
Remote Agent — система удалённого управления сервером через Android клиент.
JWT токены, SSH туннель (Hermes Gateway), задачи (shell/git/build/deploy/file/docker/AI).

## Архитектура
```
RemoteAgentService (Foreground Service)
  ├── HermesGatewayManager (SSH tunnel)
  └── RemoteAgentManager (singleton)
        ├── RemoteAgentSettingsActivity
        └── RemoteAgentActivity (чат с агентом)
```

## Компоненты
- `RemoteAgentService.kt` — foreground service, START_STICKY
- `RemoteAgentManager.kt` — singleton, bind/unbind
- `HermesGatewayManager.kt` — SSH туннель через JSch
- `RemoteAgentSettingsActivity.kt` — настройки, токены
- `RemoteAgentActivity.kt` — чат с агентом
- `RemoteAgentViewModel.kt` — StateFlow состояний

## Streaming (v1.1.3.8)
```
Агент → AGENT_TASK_STREAM_UPDATE(done=False) → streamCh → клиент
Агент → AGENT_TASK_STREAM_UPDATE(done=True) → streamDone
Агент → AGENT_TASK_RESULT → close(streamCh)
Сервер → клиент: один done=True с полными Stdout/Stderr/ExitCode/DurationMs
```

## Безопасность
- JWT токены с TTL, показываются ОДИН РАЗ
- Возможность отзыва токенов
- SSH туннель для шифрования

## Известные проблемы
- SSH туннель может разрываться при потере сети
- Android не резолвит SSH aliases — нужен IP
