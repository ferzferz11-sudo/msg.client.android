# Lavender Messenger — Android Документация

**Версия:** v1.1.3.20
**Обновлено:** 2026-06-16 (сессия 24)
**Ветка:** feat/1.1.3.x
**Тег:** v1.1.3.20

---

## Быстрый старт

**Порядок чтения для новой сессии:**

1. **PROMPT_ANDROID.md** — полный контекс текущего состояния + правила + приоритеты
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

---

## Архитектура

```
app/src/main/java/lavender/client/android/
├── ChatListActivity.kt          ← v1 (НЕ ТРОГАТЬ — v1.1.3.15 выпущен)
├── ChatAdapter.kt               ← v1 (НЕ ТРОГАТЬ)
│
├── ui/
│   ├── chatlist/                ← v2 НОВАЯ ПАПКА
│   │   ├── ChatListActivityV2.kt    — tabs, toolbar, FABs, navigation, selection mode, search, AI bottom sheet
│   │   ├── ChatAdapterV2.kt         — адаптер с секциями + selection state + DiffUtil
│   │   ├── ChatListViewModelV2.kt   — loadChats, pinChat, setTabFilter, getChats
│   │   ├── ChatListSections.kt      — Section enum + SectionItem
│   ├── adapter/
│   │   ├── ChatAdapter.kt       ← v1 (НЕ ТРОГАТЬ)
│   │   └── MessageAdapter.kt    — адаптер сообщений + pinned badge
│   ├── widget/
│   │   ├── ServerAuthBottomSheet.kt
│   │   ├── LoginBottomSheet.kt
│   │   ├── RegisterBottomSheet.kt
│   │   ├── AIBottomSheet.kt          — шторка выбора AI чата (OWL/Hermes)
│   │   └── CommandBottomSheet.kt
│   ├── hermes/                       — Hermes AI чат
│   │   ├── HermesChatActivity.kt
│   │   └── HermesChatViewModel.kt
│   ├── owl/                          — OWL AI чат
│   │   ├── OwlChatActivity.kt
│   │   ├── OwlChatViewModel.kt
│   │   └── OwlSettingsActivity.kt
│   └── remote/                       — Remote Agent UI
│
├── data/
│   ├── cache/CacheUtils.kt            — единый утилит очистки кэша
│   ├── grpc/
│   │   ├── GrpcClient.kt             — facade (pinChat, pinMessage, searchChats, etc.)
│   │   ├── RealGrpcClient.kt          — оркестратор модулей (~3700 строк, цель: ~200)
│   │   ├── GrpcConnectionManager.kt   — connect/reconnect/disconnect/keepalive (167 строк)
│   │   ├── GrpcAuthClient.kt          — signInV2/signUpV2/refreshToken/signOut (232 строки)
│   │   ├── GrpcCallClient.kt          — startCallSession/sendCallSignal (124 строки)
│   │   ├── GrpcTypingClient.kt        — startTypingStream/sendTypingSignal (87 строк)
│   │   ├── ProfileClient.kt           — ProfileService v2 client + version detection
│   │   ├── BearerTokenInterceptor.kt  — JWT Bearer token
│   │   └── MessengerProto.kt          — proto data classes
│   ├── session/CredentialStore.kt     — credentials + server list + lastUsername
│   ├── session/SessionManager.kt      — loginV2 + loginV1 fallback
│   ├── auth/AuthManager.kt            — JWT token storage
│   └── models/Message.kt              — Message (isPinned), ChatInfo (isPinned, isArchived, pinnedAt), AIChatInfo
│
└── theme/ui/
    ├── ThemeApplier.kt                — применение тем
    └── ThemeUi.kt                     — ThemeUi.bind()
```

---

## Ключевые паттерны

### v1/v2 разделение
```
v1 сервер (prod) → ChatListActivity (v1, без изменений)
v2 сервер (dev)  → ChatListActivityV2 (v2)
Определение: SplashActivity → fetchServerInfo → выбор Activity
```

### Auth V2 (JWT) flow
```
ServerAuthBottomSheet → LoginBottomSheet → SessionManager.login()
  → try V2 (SignInV2 gRPC)
  → on success: store JWT tokens via AuthManager.storeTokens()
  → on failure: fallback to V1 (Chat stream auth)
  → BearerTokenInterceptor подставляет token во все вызовы
  → Proactive refresh каждые 60с
```

### ChatList v2 flow
```
ChatListActivityV2 → fetchServerInfo() → isChatV2Supported()
  → v2: ChatListActivityV2 с секциями (Pinned/Favorites/All)
  → v1: fallback на ChatListActivity
Selection Mode: long press → ActionMode toolbar (Pin/Mute/Archive/Delete)
Search: SearchView в toolbar + debounce 300ms
Pin Chat: selection mode toolbar
Pin Message: selection mode toolbar (кнопка pin/unpin)
```

### AI Chat flow
```
FAB AI → AIBottomSheet → выбор типа (OWL/Hermes)
  → Создание: пустой chatId → HermesChatActivity/OwlChatActivity → сервер создаёт
  → Существующие: список AI чатов из общего списка (фильтр hermes/owl)
  → Настройки: OwlSettingsActivity (isHermes=true для Hermes)
```

### Connection stability
```
connect() → optimistic READY → fetchServerInfo (async)
  → HTTP /info OK → parse versions
  → HTTP /info fail → gRPC port heuristic (50052=v2, 50051=v1)
Keepalive: 30s interval, 10s timeout, idleTimeout 25min
Reconnect: on UNAVAILABLE/UNAUTHENTICATED/INTERNAL, NOT on shutdownNow
Poll: getChats every 30s
```

### RealGrpcClient modular pattern (v1.1.3.20+)
```
RealGrpcClient → оркестратор модулей (цель: ~200 строк)
  ├── GrpcConnectionManager — channel management
  ├── GrpcAuthClient — auth operations
  ├── GrpcChatClient — chat operations (ОЖИДАЕТ)
  ├── GrpcProfileClient — profile operations (ОЖИДАЕТ)
  ├── GrpcCallClient — call signaling
  └── GrpcTypingClient — typing indicator
```

---

## Серверы

| | Dev | Prod |
|--|-----|------|
| Порт gRPC | 50052 | 50051 |
| Порт HTTP | 8083 | 8082 |
| Имя | Lava Germany dev | Lava Germany |
| Версия | v1.2.0.1 | v1.1.3.10 |
