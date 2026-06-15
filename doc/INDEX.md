# Lavender Messenger — Android Документация

**Версия:** v1.1.3.16
**Обновлено:** 2026-06-16
**Ветка:** feat/1.1.3.x

---

## Быстрый старт

1. **PROMPT_ANDROID.md** — промпт для новой сессии (читать первым)
2. **TASKS.md** — таск-трекер (бэклог + сделано)
3. **PATTERNS.md** — паттерны и анти-patterns разработки
4. **REMOTE_AGENT.md** — документация Remote Agent (архитектура, протокол, streaming)
5. **SESSION_NOTES.md** — заметки последней сессии
6. **CHANGELOG.md** — история изменений
7. **PLAN_CHATLIST_V2.md** — план ChatList v2 UI + разделение v1/v2

---

## Индекс документации

### Текущая работа
| Файл | Назначение | Когда читать |
|------|-----------|-------------|
| `PROMPT_ANDROID.md` | Промпт для новой сессии | **Всегда в начале** |
| `TASKS.md` | Таск-трекер | В начале сессии |
| `PATTERNS.md` | Паттерны и анти-patterns | Перед написанием кода |
| `PLAN_CHATLIST_V2.md` | План ChatList v2 UI + v1/v2 разделение | При работе над ChatList v2 |

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
├── ChatListActivity.kt          ← v1 (НЕ ТРОГАТЬ — v1.1.3.15 выпущен)
├── ChatAdapter.kt               ← v1 (НЕ ТРОГАТЬ)
│
├── ui/
│   ├── chatlist/                ← v2 НОВАЯ ПАПКА
│   │   ├── ChatListActivityV2.kt    — определение версии + fallback на v1
│   │   ├── ChatListFragmentV2.kt    — SwipeRefresh + RecyclerView
│   │   ├── ChatAdapterV2.kt         — адаптер с секциями
│   │   ├── ChatListViewModelV2.kt   — ViewModel
│   │   └── ChatListSections.kt      — Section enum + SectionItem
│   ├── remote/                  — Remote Agent UI
│   ├── widget/                   — ServerAuthBottomSheet, LoginBottomSheet, RegisterBottomSheet
│   ├── chat/widget/ChatWidget.kt
│   └── adapter/ChatAdapter.kt   ← v1 (НЕ ТРОГАТЬ)
│
├── data/
│   ├── grpc/GrpcClient.kt                 — facade (pinChat, searchChats, etc.)
│   ├── grpc/RealGrpcClient.kt             — реализация gRPC
│   ├── grpc/ProfileClient.kt              — ProfileService v2 + fetchServerInfo
│   ├── grpc/BearerTokenInterceptor.kt     — JWT Bearer token
│   ├── proto/MessengerProto.kt            — proto data classes
│   ├── session/CredentialStore.kt         — credentials + server list
│   ├── session/SessionManager.kt          — loginV2 + loginV1 fallback
│   ├── auth/AuthManager.kt                — JWT token storage
│   └── models/Message.kt                  — ChatInfo (isPinned, isArchived, pinnedAt)
│
└── theme/ui/
    ├── ThemeApplier.kt                    — применение тем
    └── ThemeUi.kt                         — ThemeUi.bind()
```

---

## Ключевые паттерны

### v1/v2 разделение (v1.1.3.16+)
```
v1 сервер (prod) → ChatListActivity (v1, без изменений)
v2 сервер (dev)  → ChatListActivityV2 (новый UI с секциями/табами)
Определение: fetchServerInfo() → isChatV2Supported() → выбор Activity
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
  → v2: ChatListFragmentV2 с секциями (Pinned/Favorites/All)
  → v1: fallback на ChatListActivity
Pin Chat: context menu списка (long press), НЕ toolbar
Pin Message: в меню сообщения (long press), нужны новые серверные RPC
Favorites = Archive: существующий чат "Личное хранилище"
```

### i18n (v1.1.3.9)
- Activity: `getString(R.string.xxx)` — работает напрямую
- Adapter/ViewHolder: `context.getString(R.string.xxx)` или `itemView.context.getString()`
- ViewModel: НЕ использовать обычный ViewModel, только `AndroidViewModel` + `getApplication<Application>().getString()`
- НЕ инициализировать `getString()` в полях класса Activity (до `onCreate()`) — crash!
- Все новые строки ОДНОВРЕМЕННО в values/strings.xml (en) + values-ru/strings.xml

### Темы
- ThemeApplier.apply(activity, theme) — ДО setContentView()
- Цвета программно через ThemeUtils.parseSafeColor(colorStr, defaultColor)
- НЕ использовать ?attr/ в XML для текста на кастомных тёмных темах
- Новые FAB добавлять в ThemeApplier: listOf(R.id.fabAi, R.id.fabAddChat, ...)

### Kotlin 2.3.21 / Coroutines 1.11 (v1.1.3.14)
- `CancellableContinuation.resume(value, onCancellation = {})` — всегда передавать onCancellation
- `import kotlinx.coroutines.suspendCancellableCoroutine` (не `kotlin.coroutines`)
- data class с `repeated` proto полем использует `List<T>` напрямую (не `getXxxList()`)

---

## Серверы

| | Dev | Prod |
|--|-----|------|
| Порт gRPC | 50052 | 50051 |
| Порт HTTP | 8083 | 8082 |
| Имя | Lava Germany dev | Lava Germany |
| SSH | lava (13.140.25.249) | same |
