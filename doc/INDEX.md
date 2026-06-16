# Lavender Messenger — Android Документация

**Версия:** v1.1.3.20
**Обновлено:** 2026-06-16 (сессия 23)
**Ветка:** feat/1.1.3.x
**Тег:** v1.1.3.20

---

## Быстрый старт

1. **PROMPT_ANDROID.md** — промпт для новой сессии (читать первым)
2. **TASKS.md** — таск-трекер (бэклог + сделано)
3. **PATTERNS.md** — паттерны и анти-patterns разработки
4. **SESSION_NOTES.md** — заметки всех сессий
5. **CHANGELOG.md** — история изменений
6. **REMOTE_AGENT.md** — документация Remote Agent
7. **ARCH_ANALYSIS_V2_V1.md** — анализ архитектуры v2 vs v1

---

## Индекс документации

### Текущая работа
| Файл | Назначение | Когда читать |
|------|-----------|-------------|
| `PROMPT_ANDROID.md` | Промпт для новой сессии | **Всегда в начале** |
| `TASKS.md` | Таск-трекер | В начале сессии |
| `PATTERNS.md` | Паттерны и анти-patterns | Перед написанием кода |
| `SESSION_NOTES.md` | Заметки всех сессий | В начале сессии |

### Архитектура и дизайн
| Файл | Назначение | Когда читать |
|------|-----------|-------------|
| `ARCH_ANALYSIS_V2_V1.md` | Анализ архитектуры v2 vs v1, метрики, рекомендации | При планировании рефакторинга |
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
| `/root/msg/doc/PROMPT_SERVER.md` | Промпт для серверных сессий |

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
│   │   ├── ChatAdapterV2.kt         — адаптер с секциями + selection state
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
│   │   ├── RealGrpcClient.kt          — оркестратор модулей (3739 строк)
│   │   ├── GrpcConnectionManager.kt   — connect/reconnect/disconnect/keepalive (167 строк)
│   │   ├── GrpcAuthClient.kt          — signInV2/signUpV2/refreshToken/signOut (232 строки)
│   │   ├── GrpcCallClient.kt          — startCallSession/sendCallSignal (124 строки)
│   │   ├── GrpcTypingClient.kt        — startTypingStream/sendTypingSignal (87 строк)
│   │   ├── ProfileClient.kt           — ProfileService v2 client + version detection
│   │   ├── BearerTokenInterceptor.kt — JWT Bearer token
│   │   └── MessengerProto.kt        — proto data classes (ChatList v2, Pin Message, jwt_token)
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

### v1/v2 разделение (v1.1.3.16+)
```
v1 сервер (prod) → ChatListActivity (v1, без изменений)
v2 сервер (dev)  → ChatListActivityV2 (v2)
Определение: SplashActivity → fetchServerInfo → выбор Activity
```

### Auth V2 (JWT) flow (v1.1.3.11+)
```
ServerAuthBottomSheet → LoginBottomSheet → SessionManager.login()
  → try V2 (SignInV2 gRPC)
  → on success: store JWT tokens via AuthManager.storeTokens()
  → on failure: fallback to V1 (Chat stream auth)
  → BearerTokenInterceptor подставляет token во все вызовы
  → Proactive refresh каждые 60с
```

### ChatList v2 flow (v1.1.3.16+)
```
ChatListActivityV2 → fetchServerInfo() → isChatV2Supported()
  → v2: ChatListActivityV2 с секциями (Pinned/Favorites/All)
  → v1: fallback на ChatListActivity
Selection Mode: long press → ActionMode toolbar (Pin/Mute/Archive/Delete)
Search: SearchView в toolbar + debounce 300ms
Pin Chat: selection mode toolbar
Pin Message: selection mode toolbar (кнопка pin/unpin)
```

### AI Chat flow (v1.1.3.17+)
```
FAB AI → AIBottomSheet → выбор типа (OWL/Hermes)
  → Создание: пустой chatId → HermesChatActivity/OwlChatActivity → сервер создаёт
  → Существующие: список AI чатов из общего списка (фильтр hermes/owl)
  → Настройки: OwlSettingsActivity (isHermes=true для Hermes)
```

### Connection stability (v1.1.3.18+)
```
connect() → optimistic READY → fetchServerInfo (async)
  → HTTP /info OK → parse versions
  → HTTP /info fail → gRPC port heuristic (50052=v2, 50051=v1)
Keepalive: 30s interval, 10s timeout, idleTimeout 25min
Reconnect: on UNAVAILABLE/UNAUTHENTICATED/INTERNAL, NOT on shutdownNow
Poll: getChats every 30s
```

### i18n (v1.1.3.9)
- Activity: getString(R.string.xxx) — работает напрямую
- Adapter/ViewHolder: context.getString(R.string.xxx) или itemView.context.getString()
- ViewModel: НЕ использовать обычный ViewModel, только AndroidViewModel
- НЕ инициализировать getString() в полях класса Activity (до onCreate()) — crash!
- Все новые строки ОДНОВРЕМЕННО в values/strings.xml (en) + values-ru/strings.xml

### Темы
- ThemeApplier.apply(activity, theme) — ДО setContentView()
- Цвета программно через ThemeUtils.parseSafeColor(colorStr, defaultColor)
- НЕ использовать ?attr/ в XML для текста на кастомных тёмных темах
- Новые FAB добавлять в ThemeApplier: listOf(R.id.fabAi, R.id.fabAddChat, ...)

### Kotlin 2.3.21 / Coroutines 1.11 (v1.1.3.14)
- CancellableContinuation.resume(value, onCancellation = {}) — всегда передавать onCancellation
- import kotlinx.coroutines.suspendCancellableCoroutine (не kotlin.coroutines)
- OnBackPressedDispatcher вместо deprecated onBackPressed()

### Cache clearing (v1.1.3.16+)
- CacheUtils.clearAllSync(context) — синхронная очистка БД при входе (без Toast)
- CacheUtils.clearAllWithGlide(context) — полная очистка + Glide из настроек (с Toast)
- Вызывается при входе: SplashActivity, ServersActivity, ChatListActivity

---

## Серверы

| | Dev | Prod |
|--|-----|------|
| Порт gRPC | 50052 | 50051 |
| Порт HTTP | 8083 | 8082 |
| Имя | Lava Germany dev | Lava Germany |
| Версия | v1.2.0.1 | v1.1.3.10 |
