# Lava Messenger — Android Документация

**Версия:** v1.1.3.23
**Обновлено:** 2026-06-16 (сессия 28)
**Ветка:** feat/1.1.3.x
**Тег:** v1.1.3.22 (не выпущен)

---

## Быстрый старт

**Порядок чтения для новой сессии:**

1. **PROMPT_ANDROID.md** — полный контекст текущего состояния + правила + приоритеты
2. **TASKS.md** — что сделано, что осталось
3. **SESSION_NOTES.md** — история сессий
4. **PATTERNS.md** — паттерны и анти-patterns перед написанием кода
5. **CHANGELOG.md** — история изменений по версиям
6. **INDEX.md** — этот файл, для навигации по остальной документации

---

## Индекс документации

### Текущая работа
| Файл | Назначение | Когда читать |
|------|-----------|-------------|
| `PROMPT_ANDROID.md` | Промпт для новой сессии | **Всегда в начале** |
| `TASKS.md` | Таск-трекер (бэклог + сделано) | В начале сессии |
| `SESSION_NOTES.md` | Заметки всех сессий | В начале сессии |
| `CHANGELOG.md` | История изменений по версиям | Для понимания что сделано |

### Архитектура и паттерны
| Файл | Назначение | Когда читать |
|------|-----------|-------------|
| `PATTERNS.md` | Паттерны и анти-patterns разработки | Перед написанием кода |
| `ARCH_ANALYSIS_V2_V1.md` | Анализ архитектуры v2 vs v1 | При планировании рефакторинга |
| `PLAN_REFACTOR_GRPC.md` | План рефакторинга RealGrpcClient | При продолжении модуляризации |

### Компоненты
| Файл | Назначение | Когда читать |
|------|-----------|-------------|
| `REMOTE_AGENT.md` | Remote Agent: архитектура, протокол, streaming | При работе с Remote Agent |

### Сервер
| Файл | Назначение |
|------|-----------|
| `/root/msg/doc/INDEX.md` | Индекс серверной документации |
| `/root/msg/doc/TASKS.md` | Серверный таск-трекер |
| `/root/msg/doc/PROMPT_SERVER.md` | Промпт для серверных сессий |
| `/root/msg/doc/INTEGRATION_SESSION.md` | Интеграционная сессия: версии, архитектура |
| `/root/msg/doc/AI_SERVICES.md` | AI-сервисы: OWL, Hermes |
| `/root/msg/doc/PITFALLS.md` | Подводные камни |

---

## Архитектура

```
app/src/main/java/lavender/client/android/
├── ChatListActivity.kt          ← ЕДИНЫЙ Activity (v1+v2)
├── ui/
│   ├── chatlist/
│   │   ├── ChatListActivity.kt
│   │   ├── ChatListViewModel.kt
│   │   ├── ChatListSections.kt
│   ├── adapter/
│   │   ├── ChatAdapter.kt
│   │   └── MessageAdapter.kt
│   ├── widget/
│   │   ├── ServerAuthBottomSheet.kt
│   │   ├── LoginBottomSheet.kt
│   │   ├── RegisterBottomSheet.kt
│   │   ├── AIBottomSheet.kt
│   │   └── CommandBottomSheet.kt
│   ├── hermes/
│   ├── owl/
│   └── remote/
├── data/
│   ├── cache/CacheUtils.kt
│   ├── grpc/
│   │   ├── GrpcClient.kt
│   │   ├── RealGrpcClient.kt
│   │   ├── GrpcConnectionManager.kt
│   │   ├── GrpcAuthClient.kt
│   │   ├── GrpcCallClient.kt
│   │   ├── GrpcTypingClient.kt
│   │   ├── ProfileClient.kt
│   │   ├── BearerTokenInterceptor.kt
│   │   └── MessengerProto.kt
│   ├── session/
│   ├── auth/
│   └── models/
└── theme/ui/
```

---

## Серверы

| | Dev | Prod |
|--|-----|------|
| Порт gRPC | 50052 | 50051 |
| Порт HTTP | 8083 | 8082 |
| Имя | Lava Germany dev | Lava Germany |
| Версия | v1.2.0.2 | v1.1.3.10 |
