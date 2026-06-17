# Lava Messenger — Android Документация

**Версия:** v1.1.3.31
**Обновлено:** 2026-06-17 (сессия 38)
**Ветка:** feat/1.1.3.x
**Тег:** v1.1.3.31 (TODO)

---

## Быстрый старт

**Порядок чтения для новой сессии:**

1. **PROMPT_ANDROID.md** — полный контекст текущего состояния + правила + приоритеты
2. **TASKS.md** — что сделано, что осталось
3. **SESSION_NOTES.md** — история сессий
4. **PATTERNS.md** — паттерны и анти-patterns перед написанием кода
5. **CHANGELOG.md** — история изменений по версиям
6. **INDEX.md** — этот файл, для навигации

---

## Индекс документации

### Текущая работа
| Файл | Назначение | Когда читать |
|------|-----------|-------------|
| `PROMPT_ANDROID.md` | Промпт для новой сессии | **Всегда в начале** |
| `TASKS.md` | Таск-трекер (бэклог + сделано) | В начале сессии |
| `SESSION_NOTES.md` | Заметки сессий (35-38) | В начале сессии |
| `SESSION_NOTES_ARCHIVE.md` | Архив сессий (23-34) | Справочно |
| `../CHANGELOG.md` | История изменений по версиям | Для понимания что сделано |

### Архитектура и паттерны
| Файл | Назначение | Когда читать |
|------|-----------|-------------|
| `PATTERNS.md` | Паттерны и анти-patterns разработки | Перед написанием кода |
| `ARCH_ANALYSIS_V2_V1.md` | Анализ архитектуры v2 vs v1 | При планировании рефакторинга |
| `PLAN_REFACTOR_GRPC.md` | План рефакторинга RealGrpcClient (ЗАВЕРШЁН) | Справочно |
| `CODE_AUDIT.md` | Аудит кода — сильные/слабые места | При планировании оптимизаций |

### Компоненты
| Файл | Назначение | Когда читать |
|------|-----------|-------------|
| `REMOTE_AGENT.md` | Remote Agent: архитектура, протокол, streaming | При работе с Remote Agent |
| `ChatListActivity_v1_REFERENCE.kt` | Копия удалённого v1 Activity (2802 строки) | Для переноса кода из v1 |

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
├── ui/
│   ├── chatlist/
│   │   ├── ChatListActivity.kt         — ЕДИНЫЙ Activity (~1113 LOC)
│   │   ├── ChatListViewModel.kt        — loadChats, pinChat, setTabFilter
│   │   ├── ChatListSections.kt         — Section enum + SectionItem
│   │   └── UpdateCoordinator.kt        — update system UI logic
│   ├── adapter/
│   │   ├── ChatAdapter.kt              — адаптер с секциями + selection state + DiffUtil
│   │   ├── MessageAdapter.kt           — адаптер сообщений + pinned badge
│   │   └── UserAdapter.kt              — адаптер пользователей с выбором + поиск
│   ├── widget/
│   │   ├── ServerAuthBottomSheet.kt    — шторка выбора входа
│   │   ├── LoginBottomSheet.kt         — шторка входа
│   │   ├── RegisterBottomSheet.kt      — шторка регистрации
│   │   ├── AIBottomSheet.kt            — шторка выбора AI чата
│   │   ├── ActionBottomSheet.kt        — шторка действий (v1 reference)
│   │   ├── SearchableListBottomSheet.kt — шторка с поиском и списком
│   │   └── StandardBottomSheet.kt      — базовая шторка
│   ├── hermes/                         — Hermes AI чат
│   ├── owl/                            — OWL AI чат
│   └── remote/                         — Remote Agent UI
├── data/
│   ├── cache/CacheUtils.kt             — единый утилит очистки кэша
│   ├── grpc/
│   │   ├── GrpcClient.kt              — facade (779 LOC)
│   │   ├── RealGrpcClient.kt           — orchestrator (874 LOC)
│   │   ├── GrpcMarshallers.kt          — 111 marshaller classes (1394 LOC)
│   │   ├── GrpcUnaryCallHelper.kt      — universal unary call (111 LOC)
│   │   ├── GrpcConnectionManager.kt    — connect/reconnect (167 LOC)
│   │   ├── GrpcAuthClient.kt           — JWT auth (232 LOC)
│   │   ├── GrpcCallClient.kt           — call session (125 LOC)
│   │   ├── GrpcTypingClient.kt         — typing stream (87 LOC)
│   │   ├── GrpcChatListClient.kt       — chat list CRUD (638 LOC)
│   │   ├── GrpcProfileClient.kt        — profile/avatar/themes (506 LOC)
│   │   ├── GrpcDraftClient.kt          — drafts (86 LOC)
│   │   ├── GrpcFavoritesClient.kt      — favorites (120 LOC)
│   │   ├── GrpcMessageClient.kt        — messages/history/reactions (341 LOC)
│   │   ├── GrpcServerDiscoveryClient.kt — server discovery (145 LOC)
│   │   ├── ProfileClient.kt            — ProfileService v2
│   │   └── BearerTokenInterceptor.kt
│   ├── session/
│   ├── auth/
│   └── models/
└── theme/ui/
```

### gRPC Client Architecture (v1.1.3.30)
```
GrpcClient (facade, 779 LOC)
    ↓
RealGrpcClient (orchestrator, 874 LOC)
    ├── GrpcConnectionManager (167) — channel lifecycle
    ├── GrpcAuthClient (232) — JWT auth
    ├── GrpcTypingClient (87) — typing stream
    ├── GrpcCallClient (125) — calls
    ├── GrpcChatListClient (638) — chat list, pin/search/archive, management
    ├── GrpcProfileClient (506) — profile, avatar, contacts, themes, devices
    ├── GrpcDraftClient (86) — drafts
    ├── GrpcFavoritesClient (120) — favorites
    ├── GrpcMessageClient (341) — messages, history, reactions, mark read
    ├── GrpcServerDiscoveryClient (145) — server discovery, proto parsing
    └── GrpcMarshallers (1394) — all marshaller classes (separate file)
```

---

## Серверы

| | Dev | Prod |
|--|-----|------|
| Порт gRPC | 50052 | 50051 |
| Порт HTTP | 8083 | 8082 |
| Имя | Lava Germany dev | Lava Germany |
| Версия | v1.1.3.0 | v1.1.3.0 |
