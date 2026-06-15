# Lavender Messenger — Android Документация

**Версия:** v1.1.3.18 (разработка)
**Обновлено:** 2026-06-15 (сессия 18)
**Ветка:** feat/1.1.3.x
**Тег:** v1.1.3.17

---

## Быстрый старт

1. **PROMPT_ANDROID.md** — промпт для новой сессии (читать первым)
2. **TASKS.md** — таск-трекер (бэклог + сделано)
3. **PATTERNS.md** — паттерны и анти-patterns разработки
4. **SESSION_NOTES.md** — заметки всех сессий
5. **CHANGELOG.md** — история изменений
6. **REMOTE_AGENT.md** — документация Remote Agent

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
│   │   ├── ChatListActivityV2.kt    — tabs, toolbar, FABs, navigation, selection mode
│   │   ├── ChatAdapterV2.kt         — адаптер с секциями + selection state
│   │   ├── ChatListViewModelV2.kt   — loadChats, pinChat, setTabFilter
│   │   ├── ChatListSections.kt      — Section enum + SectionItem
│   │   └── ChatListFragmentV2.kt    — фрагмент (не используется, для справки)
│   ├── remote/                  — Remote Agent UI
│   ├── widget/                   — ServerAuthBottomSheet, LoginBottomSheet, RegisterBottomSheet
│   ├── chat/widget/ChatWidget.kt
│   └── adapter/
│       ├── ChatAdapter.kt       ← v1 (НЕ ТРОГАТЬ)
│       └── MessageAdapter.kt    — адаптер сообщений + pinned badge
│
├── data/
│   ├── cache/CacheUtils.kt            — единый утилит очистки кэша
│   ├── grpc/GrpcClient.kt             — facade (pinChat, pinMessage, searchChats, etc.)
│   ├── grpc/RealGrpcClient.kt         — реализация gRPC
│   ├── grpc/ProfileClient.kt          — ProfileService v2 + fetchServerInfo
│   ├── grpc/BearerTokenInterceptor.kt — JWT Bearer token
│   ├── proto/MessengerProto.kt        — proto data classes
│   ├── session/CredentialStore.kt     — credentials + server list + lastUsername
│   ├── session/SessionManager.kt      — loginV2 + loginV1 fallback
│   ├── auth/AuthManager.kt            — JWT token storage
│   └── models/Message.kt              — Message (isPinned), ChatInfo (isPinned, isArchived, pinnedAt)
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
v2 сервер (dev)  → ChatListActivityV2 (новый UI с секциями/табами)
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

### Pin Message flow (v1.1.3.16+)
```
Long press on message → enter selection mode (v1-style)
Select message → pin button in selection toolbar
→ GrpcClient.pinMessage(context, chatId, messageId) → server RPC
→ loadPinnedMessages() updates pinnedMessageIds + adapter
→ Pinned badge shown on pinned messages
Cache: CacheUtils.clearAllSync() on login (silent)
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
- CacheUtils.clearAllWithGlide(context) — полная очистка + Glide из настройки (с Toast)
- Вызывается при входе: SplashActivity, ServersActivity, ChatListActivity

---

## Серверы

| | Dev | Prod |
|--|-----|------|
| Порт gRPC | 50052 | 50051 |
| Порт HTTP | 8083 | 8082 |
| Имя | Lava Germany dev | Lava Germany |
| SSH | lava (13.140.25.249) | same |
| Версия | v1.2.0.1 | v1.1.3.10 |
