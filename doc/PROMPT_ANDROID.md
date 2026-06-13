# Промпт для новой сессии — v1.1.3.7 (stable)

**Дата:** 2026-06-13
**Версия:** 1.1.3.7
**Ветка:** feat/1.1.3.x

---

## СТАТУС: v1.1.3.7 — СТАБИЛЬНАЯ ВЕРСИЯ

Прод и dev серверы обновлены, Android клиент протестирован.

---

## АРХИТЕКТУРА

### Сервер (/root/msg)
```
main.go                    — Entry point, gRPC server
server.go                  — Структура server, общие методы (ServerVersion = "1.1.3.7")
server_*.go                — Методы по доменам (chat, users, chats, messages, profile, push, contacts, themes, drafts, muted, favorites, ai)
server_ai.go               — AI Chat + Hermes Orchestrator RPC
server_remote.go           — Remote Agent RPC (ListRemoteAgents, GetRemoteAgentStatus, DeployAgentTask, DeployAgentTaskStream)
ai_chat_manager.go         — Единый менеджер AI чатов
owl.go                     — OWL AI: streaming через OpenRouter
hermes_orchestrator.go     — Hermes: оркестрация агентов
hermes_agent_service.go   — HermesAgentService: Connect, tokens
hermes_remote_manager.go  — RemoteAgentManager: Register, SendTask, HandleTaskResult
http_server.go             — HTTP сервер (файлы, аватары, /health)
db.go / db_hermes.go       — Database layer
auth_service.go            — AuthService (SignIn, SignUp)
jwt.go                     — JWT генерация/валидация
messenger.proto            — ChatService, AuthService, AI Chat, Remote Agent RPC
hermes_remote.proto        — HermesAgentService
```

### Android (/root.msg.client.android)
```
data/
├── proto/MessengerProto.kt       — Все proto data classes
├── grpc/GrpcClient.kt            — Единая точка доступа (facade)
├── grpc/HermesGrpc.kt            — Hermes/Remote Agent gRPC (unary + streaming)
├── grpc/OwlGrpc.kt               — OWL gRPC (streaming)
├── grpc/RealGrpcClient.kt        — Реализация gRPC клиента
├── db/AppDatabase.kt             — Room DB (version 9)
├── db/Entities.kt                — ChatEntity, MessageEntity
├── models/ErrorHandler.kt         — Единый обработчик ошибок
├── models/AppLog.kt              — Глобальный логгер
├── models/HermesModel.kt         — RemoteAgentInfo, AgentInfo, HermesSession
├── session/CredentialStore.kt     — Credentials + Server list
├── session/SessionManager.kt      — Управление сессией
├── theme/ThemeStore.kt            — Темы
└── updates/UpdateManager.kt       — Обновления

ui/
├── remote/
│   ├── RemoteAgentActivity.kt     — Чат с агентом (streaming)
│   ├── RemoteAgentViewModel.kt    — ViewModel (sendMessageStreaming)
│   ├── RemoteAgentSettingsActivity.kt — Токены + SSH туннель
│   ├── RemoteAgentService.kt      — Foreground service
│   ├── RemoteAgentManager.kt      — Singleton manager
│   └── HermesGatewayManager.kt    — SSH туннель (JSch)
├── ServersActivity.kt             — Список серверов + логин на выбранный
├── ChatListActivity.kt            — Главный экран + авторизация
├── owl/OwlChatActivity.kt         — OWL AI чат
├── hermes/HermesChatActivity.kt   — Hermes чат
└── LogViewerActivity.kt           — Журнал ошибок (AppLog)
```

---

## КЛЮЧЕВЫЕ РЕШЕНИЯ

### Сервер
- **server_remote.go** — все Remote Agent RPC в отдельном файле (не в server_ai.go)
- **ensureRemoteManager()** — единая проверка зависимостей для RPC
- **Graceful degradation** — пустой список вместо ошибки если менеджер недоступен
- **Stale detection** — heartbeat > 120с → status="stale"

### Android
- **Нет выбора сервера в логине** — сервер всегда из CredentialStore (по умолчанию prod)
- **Переключение сервера** — только через ServersActivity (сервер → login sheet)
- **Fallback на prod** — `CredentialStore.getServerAddress().ifEmpty { "13.140.25.249:50051" }`
- **Room DB migration 8→9** — defensive column addition для совместимости
- **ErrorHandler.kt** — единый обработчик ошибок с AppLog
- **ensureAgentSelected()** — авто-выбор агента с fallback

---

## ПРАВИЛА

1. НЕ компилировать на сервере (OOM kill)
2. Коммитить и пушить после каждого значимого изменения
3. Версия сервера в `server.go:34`, версия Android в `version.txt`
4. Разделение архитектуры — каждый домен в своём server_*.go файле
5. userId (UUID) — всегда как ключ, НЕ username
6. changelog.txt БОЛЬШЕ НЕ ИСПОЛЬЗУЕТСЯ — использовать bundled changelog в APK

---

## КОМАНДЫ

```bash
# === СЕРВЕР ===
cd /root/msg
export PATH=$PATH:/usr/local/go/bin:~/go/bin

# Сборка и деплой на dev
go build -o /tmp/lavender-server-dev .
systemctl stop lavender-server-dev
cp /tmp/lavender-server-dev /root/LavenderMessenger/run/lavender-server-dev
systemctl start lavender-server-dev

# Сборка и деплой на prod
go build -o /tmp/lavender-server .
systemctl stop lavender-server
cp /tmp/lavender-server /root/LavenderMessenger/run/lavender-server
systemctl start lavender-server

# Тесты
go test ./...

# === ANDROID ===
cd /root/msg.client.android
# НЕ запускать assembleRelease на сервере (OOM)!

# === Remote Agent ===
cd /root/msg.remote.agent
python3 hermes_remote_agent.py --server host:port --token <jwt>
```

---

## DEV vs PROD

| Характеристика | Dev | Prod |
|----------------|-----|------|
| Порт | 50052 | 50051 |
| Сервис | lavender-server-dev | lavender-server |
| Конфиг | .env.dev | .env |
| DB | chat_db_dev | chat_db |

---

## ИЗВЕСТНЫЕ ПРОБЛЕМЫ

- Агент (hermes_remote_agent.py) ещё НЕ отправляет streaming updates — сервер готов, клиент готов, агент нужно обновить
- Server migration warnings: `role "lavender" does not exist` (не критично)
- Favorites мерцание при обновлении списка чатов (DiffUtil)
