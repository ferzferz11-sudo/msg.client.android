# Android клиент — Промпт для новой сессии

Текущая версия: v1.1.3.0 (dev)
Ветка: feat/1.1.3.x

---

## КТО ТЫ

Ты — Senior Android/Kotlin разработчик проекта Lavender Messenger.
gRPC-мессенджер с E2EE шифрованием, кастомными темами, AI чатами (OWL + Hermes).

---

## СТРУКТУРА ПРОЕКТА

Сервер:       /root/msg/          (Go, gRPC + HTTP hub, PostgreSQL)
Android:      /root/msg.client.android/  (Kotlin + ViewBinding)
Агент:        /root/msg/hermes-agent/    (Python, gRPC Connect)
Prod сервер:  13.140.25.249 (prod порт 50051, dev порт 50052)

Ветка: feat/1.1.3.x

---

## КЛЮЧЕВЫЕ ФАЙЛЫ ANDROID

```
app/src/main/java/lavender/client/android/
├── ChatListActivity.kt          — Главный экран + AI шторка
├── NewChatActivity.kt           — Обычный чат
├── HermesChatActivity.kt        — Чат с Hermes
├── HermesChatViewModel.kt       — ViewModel Hermes + Room DB
├── OwlChatActivity.kt           — Чат с OWL
├── OwlSettingsActivity.kt       — Настройки OWL
├── SplashActivity.kt            — Сплеш
├── ui/
│   ├── widget/AIBottomSheet.kt  — Шторка AI (Hermes/OWL/Агенты секции)
│   ├── remote/                  — Remote Agent
│   │   ├── RemoteAgentActivity.kt       — Чат с агентом
│   │   ├── RemoteAgentViewModel.kt      — ViewModel (sendMessage, loadAgents)
│   │   ├── RemoteAgentSettingsActivity.kt — Управление токенами
│   │   └── TokenDialog.kt               — Диалог генерации токена
│   └── adapter/ChatAdapter.kt   — Адаптер чатов
├── data/
│   ├── proto/MessengerProto.kt  — Все proto data classes (ручные)
│   ├── grpc/GrpcClient.kt       — Facade к gRPC
│   ├── grpc/HermesGrpc.kt       — Hermes/Remote Agent методы
│   ├── grpc/OwlGrpc.kt          — OWL методы
│   └── theme/                   — ThemeStore, ThemeUtils, ThemeApplier
└── scripts/release.sh           — Скрипт релиза
```

---

## ДОКУМЕНТАЦИЯ

Все ключевые знания в doc/:
- `doc/INDEX.md` — навигация
- `doc/TASKS.md` — таск-трекер, бэклог
- `doc/STRUCTURE.md` — справочник структуры кода
- `doc/REMOTE_AGENT.md` — проект Remote Agent (ЧИТАТЬ ПЕРВЫМ)
- `doc/REMOTE_AGENT_PLAN.md` — план работ по интеграции (ЧИТАТЬ ВТОРЫМ)
- `doc/PROMPT_ANDROID.md` — этот файл

---

## ТЕКУЩАЯ ЗАДАЧА: Remote Agent v1.1.3 — Интеграция с реальным бэкендом

### Архитектура

```
┌─────────────┐  gRPC          ┌──────────────┐  gRPC           ┌─────────────┐
│  Android    │ ──────────────→ │   Server     │ ←────────────── │   Hermes    │
│  Client     │  DeployAgent    │   ChatService│  Connect        │   Agent     │
│             │  Task           │              │  (streaming)    │   (Python)  │
└─────────────┘                 └──────────────┘                 └─────────────┘
                                       │
                                       │ маршрутизация
                                       ▼
                                ┌──────────────┐
                                │  Orchestrator│
                                │  (server_ai) │
                                └──────────────┘
```

### Что уже сделано

**Android клиент:**
- ✅ RemoteAgentActivity (чат, статус, toolbar с выбором агента)
- ✅ RemoteAgentSettingsActivity (управление токенами)
- ✅ TokenDialog (генерация токена с "Выбрать все")
- ✅ AIBottomSheet секция "🖥 Агенты"
- ✅ Task type selector (ChipGroup: shell, git, build, deploy, docker, ai)
- ✅ Кастомные темы через ThemeUi.bind
- ✅ Отправка задач через GrpcClient.deployAgentTask()
- ✅ Выбор агента из списка (Spinner в toolbar)

**Сервер (Go):**
- ✅ hermes_remote.proto — Connect, RegistrationInfo, Task, TaskResult
- ✅ messenger.proto — GenerateAgentToken, DeployAgentTask, ListRemoteAgents
- ✅ hermes_agent_service.go — Connect streaming, регистрация агентов
- ✅ server_ai.go — DeployAgentTask, GenerateAgentToken, GetRemoteAgentStatus
- ✅ AgentID передаётся в RemoteTask (баг исправлен)

**Агент (Python):**
- ✅ hermes-agent/hermes_remote_agent.py — gRPC Connect, выполнение задач
- ✅ hermes-agent/hermes_remote_pb2.py — сгенерированные proto
- ✅ JWT токен сгенерирован

### Что нужно сделать

**Приоритет 1 — Завершить интеграцию:**
1. Запустить сервер (`./run/lavender-server` на порту 50051)
2. Сгенерировать токен через Go: `go run /tmp/gen_token.go`
3. Записать токен в конфиг агента
4. Запустить агент: `python3 hermes_remote_agent.py --server localhost:50051 --token <jwt>`
5. Проверить что агент появляется в ListRemoteAgents
6. Отправить задачу из Android приложения
7. Получить результат в чате

**Приоритет 2 — Улучшить UI:**
- Показывать stdout/stderr задачи в чате
- Показывать exit_code и duration
- Показывать лог выполнения задач

**Приоритет 3 — Тестирование:**
- Тест всех типов задач (shell, git, build, file, docker)
- Тест отключения/подключения агента
- Тест нескольких агентов одновременно

### Критические файлы для чтения

Перед началом работы прочитай:
1. `doc/REMOTE_AGENT.md` — полная документация проекта
2. `doc/REMOTE_AGENT_PLAN.md` — план работ с текущим статусом
3. `ui/remote/RemoteAgentViewModel.kt` — текущая реализация sendMessage()
4. `data/grpc/HermesGrpc.kt` — deployAgentTask(), getRemoteAgentStatus()
5. `/root/msg/hermes-agent/hermes_remote_agent.py` — агент Python
6. `/root/msg/server_ai.go` — DeployAgentTask, GenerateAgentToken
7. `/root/msg/hermes_agent_service.go` — Connect streaming

### Важные детали

- Сервер запускается на порту 50051 (prod) и 50052 (dev)
- Агент подключается к серверу через `Connect` (bidirectional streaming)
- JWT токен генерируется через `GenerateAgentToken` на сервере
- Токен передаётся агенту через `RegistrationInfo.auth_token`
- Сервер валидирует токен в `validateToken()`
- Задачи отправляются через `DeployAgentTask` → `SendTaskToAgent()`
- Результаты отправляются через `AGENT_TASK_RESULT` в стриме

---

## ПРАВИЛА

- Стиль кода как в существующих файлах
- ViewBinding (не findViewById)
- Корутины + lifecycleScope
- DiffUtil для адаптера чата
- Тема через ThemeStore (программно, не XML ?attr)
- **НЕ запускать assembleRelease на сервере** (OOM, нужно 2GB+)
- **НЕ запускать compileDebugKotlin без крайней необходимости** — сначала `free -h`, если < 2GB free → НЕ запускать
- **НЕ запускать никакие ./gradlew задачи** если память < 2GB free
- Если нужно проверить синтаксис — делай это через чтение файлов и анализ кода, а не через компиляцию на сервере
- Коммитить и пушить после каждого значимого изменения

---

## ВАЖНО

- Package: `lavender.client.android` (НЕ ru.lavender.messenger)
- Proto: ручные data classes в MessengerProto.kt (НЕ генерируются)
- version.txt обновлять ДО release.sh
- Серверный код в `/root/msg/` — не изменять без необходимости
- Агент в `/root/msg/hermes-agent/` — Python, gRPC Connect
