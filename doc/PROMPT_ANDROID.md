# Промпт для новой сессии — v1.1.3.x (Android)

**Дата:** 2026-06-12
**Версия:** v1.1.3.4
**Ветка:** feat/1.1.3.x
**Текущая версия APK:** 1.1.3.4 (выпущен)
**GitHub релиз:** https://github.com/ferzferz11-sudo/msg.client.android/releases/tag/v1.1.3.4

---

## СТАТУС

- Remote Agent UI реализован и работает
- Token Management (генерация, список, отзыв, копирование) — работает
- HermesGrpc — все методы реализованы
- **Hermes Gateway** — SSH туннель работает (JSch)
- **tunnel_mode** — передаётся в DeployAgentTask
- APK v1.1.3.2 собран и залит
- Сервер v1.1.3.2 — выпущен

---

## ЧТО СДЕЛАНО В v1.1.3.2

- Генерация JWT токенов через `hermes_agent.HermesAgentService/GenerateAgentToken`
- Список токенов отображается сразу после генерации
- Копирование токена/команды в каждом элементе списка
- Отзыв токена с подтверждением
- Запуск/остановка агента через StartAgent/StopAgent RPC
- Зелёный индикатор при запущенном агенте
- Персистентность выбранного агента (SharedPreferences)
- Ошибки сервера переведены на русский

---

## ЗАДАЧИ ДЛЯ НОВОЙ СЕССИИ

### P1 — Исправить hermes_remote_agent.py (сервер)
- Агент завершается сразу после запуска — падает в `connect()` при отправке `AgentMessage`
- Root cause: protobuf marshaling в Python скрипте
- Нужно исправить `/root/msg/hermes-agent/hermes_remote_agent.py`

### P2 — Фильтрация токенов по пользователю (сервер)
- `ListAgentTokens` возвращает все токены из БД
- Нужно добавить фильтр по `created_by = adminUserId`

### P3 — Streaming результатов задач
- Агент должен отправлять результаты выполнения задач обратно клиенту
- Через bidirectional gRPC stream `Connect`

---

## КРИТИЧЕСКИЕ ФАЙЛЫ

| Файл | Назначение |
|------|-----------|
| `data/grpc/HermesGrpc.kt` | gRPC методы (token RPC, agent management) |
| `ui/remote/RemoteAgentSettingsActivity.kt` | Управление токенами и агентом |
| `ui/remote/RemoteAgentActivity.kt` | Чат с агентом |
| `ui/remote/TokenDialog.kt` | Диалог генерации токена |
| `hermes-agent/hermes_remote_agent.py` | Python агент (сервер) |

---

## АРХИТЕКТУРА

```
┌──────────────────────────────────────────────────────────────┐
│ AIBottomSheet                                                │
│  └── "🖥 Агенты" → RemoteAgentActivity                       │
│       ├── loadAgents() → listRemoteAgents() gRPC             │
│       ├── sendMessage() → deployAgentTask() gRPC             │
│       └── ⚙ → RemoteAgentSettingsActivity                    │
│            ├── generateToken() → GenerateAgentToken gRPC     │
│            ├── loadTokens() → ListAgentTokens gRPC            │
│            ├── revokeToken() → RevokeAgentToken gRPC          │
│            ├── startAgent() → StartAgent gRPC                │
│            └── stopAgent() → StopAgent gRPC                   │
└──────────────────────────────────────────────────────────────┘
```

---

## ТЕСТОВЫЕ ДАННЫЕ

**User (dev server):**
- userId: `ea577733-3f2c-4752-ac0e-1b2a88a6836b`
- username: `ferz11`

**Сервер:**
- Dev: `localhost:50052`
- Prod: `13.140.25.249:50051`
