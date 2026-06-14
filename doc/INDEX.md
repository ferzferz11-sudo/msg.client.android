# Lavender Messenger — Android Документация

**Версия:** v1.1.3.11
**Обновлено:** 2026-06-14
**Ветка:** feat/1.1.3.x

---

## Быстрый старт

1. **PROMPT_ANDROID.md** — промпт для новой сессии (читать первым)
2. **TASKS.md** — таск-трекер (бэклог + сделано)
3. **PATTERNS.md** — паттерны и анти-patterns разработки
4. **REMOTE_AGENT.md** — документация Remote Agent (архитектура, протокол, streaming)
5. **CHANGELOG.md** — история изменений

---

## Индекс документации

### Текущая работа
| Файл | Назначение | Когда читать |
|------|-----------|-------------|
| `PROMPT_ANDROID.md` | Промпт для новой сессии | **Всегда в начале** |
| `TASKS.md` | Таск-трекер | В начале сессии |
| `PATTERNS.md` | Паттерны и анти-patterns | Перед написанием кода |

### Архитектура и дизайн
| Файл | Назначение | Когда читать |
|------|-----------|-------------|
| `REMOTE_AGENT.md` | Remote Agent: архитектура, протокол, streaming | При работе с Remote Agent |
| `/root/msg/doc/INTEGRATION_SESSION.md` | Интеграционная сессия: версии, архитектура | При работе с сервером |
| `/root/msg/doc/AI_SERVICES.md` | AI-сервисы: OWL, Hermes | При работе с AI чатами |

### Справочники
| Файл | Назначение | Когда читать |
|------|-----------|-------------|
| `/root/msg/doc/PITFALLS.md` | Подводные камни | **Перед началом работы** |
| `/root/msg/doc/LOG_MONITOR.md` | Log Monitor | При проблемах с логами |
| `/root/msg/doc/TESTING.md` | Модульные тесты | При работе с тестами |

### Сервер
| Файл | Назначение |
|------|-----------|
| `/root/msg/doc/INDEX.md` | Индекс серверной документации |
| `/root/msg/doc/TASKS.md` | Серверный таск-трекер |
| `/root/msg/doc/PROMPT.md` | Промпт для серверных сессий |

---

## Архитектура

```
app/src/main/java/lavender/client/android/
├── ui/
│   ├── remote/
│   │   ├── RemoteAgentActivity.kt         — чат с агентом
│   │   ├── RemoteAgentSettingsActivity.kt — настройки (шлюз + токен)
│   │   ├── RemoteAgentViewModel.kt        — ViewModel (AndroidViewModel)
│   │   ├── RemoteAgentService.kt          — foreground service
│   │   ├── RemoteAgentManager.kt          — singleton manager
│   │   └── HermesGatewayManager.kt        — SSH туннель
│   ├── chat/widget/ChatWidget.kt          — общий виджет чата
│   ├── adapter/ChatAdapter.kt             — адаптер списка чатов (clearAll)
│   └── widget/
│       ├── ServerAuthBottomSheet.kt       — шторка выбора входа
│       ├── LoginBottomSheet.kt            — шторка входа
│       └── RegisterBottomSheet.kt         — шторка регистрации
├── data/
│   ├── grpc/GrpcClient.kt                 — facade
│   ├── grpc/HermesGrpc.kt                 — Remote Agent gRPC
│   ├── proto/MessengerProto.kt            — proto data classes
│   ├── models/ErrorHandler.kt              — единый обработчик ошибок
│   ├── models/AppLog.kt                   — глобальный логгер
│   └── session/
│       ├── CredentialStore.kt             — credentials + server list
│       └── SessionManager.kt             — управление сессией
└── theme/ui/
    ├── ThemeApplier.kt                    — применение тем
    └── ThemeUi.kt                         — ThemeUi.bind()
```

---

## Ключевые паттерны

### Auth widgets pattern (v1.1.3.11)
Аутентификация вынесена в отдельные виджеты:
- `ServerAuthBottomSheet` — шторка выбора (лого + сервер + статус + login/register)
- `LoginBottomSheet` — шторка входа (username/password)
- `RegisterBottomSheet` — шторка регистрации (username/password/email)
- Оба наследуют `StandardBottomSheet`
- Health check через `http://host:8082/health`
- Используются в: `ChatListActivity`, `ServersActivity`

### Server switch pattern (v1.1.3.11)
При смене сервера через ServersActivity:
- НЕ сохранять `serverAddress` до успешного входа
- Сохранять `serverAddress` ТОЛЬКО после успешного `SessionManager.login()`
- Использовать флаг `justReturnedFromServersActivity` для пропуска reconnect в onResume()
- `isLoadingChats` предотвращает двойную загрузку
- `startSync()` останавливается при смене сервера

### i18n (v1.1.3.9)
- Activity: `getString(R.string.xxx)`
- Adapter: `context.getString(R.string.xxx)`
- ViewModel: `AndroidViewModel` + `getApplication<Application>().getString()`
- НЕ инициализировать getString() в полях класса Activity
- Несколько подстановок: позиционные форматтеры (%1$s, %2$d)
- Все новые строки ОДНОВРЕМЕННО в values/strings.xml (en) + values-ru/strings.xml

### Темы
- `ThemeApplier.apply()` до `setContentView()`
- Цвета программно через `ThemeUtils.parseSafeColor()`
- НЕ использовать `?attr/` в XML для текста на кастомных темах

### Фильтрация чатов
- `ChatAdapter.filter()` — `dispatchUpdatesTo` с offset +1 для Favorites
- НЕ использовать `notifyItemRangeChanged`
- `ChatAdapter.clearAll()` — полная очистка с сбросом favoritesItem

---

## Команды

```bash
# Сборка
./gradlew assembleRelease    # ТОЛЬКО локально (OOM на сервере)

# Релиз
./scripts/release.sh 1.1.3.11

# SSH к серверу
ssh lava
```

---

## Серверы

| | Dev | Prod |
|--|-----|------|
| Порт | 50052 | 50051 |
| Имя | Lava Germany dev | Lava Germany |
| SSH | lava (13.140.25.249) | same |
