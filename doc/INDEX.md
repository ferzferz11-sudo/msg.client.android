# Lavender Messenger — Android Документация

**Версия:** v1.1.3.14
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
│   ├── widget/
│   │   ├── ServerAuthBottomSheet.kt       — шторка выбора входа
│   │   ├── LoginBottomSheet.kt            — шторка входа (prefillUsername)
│   │   └── RegisterBottomSheet.kt         — шторка регистрации
│   ├── chat/widget/ChatWidget.kt          — общий виджет чата
│   └── adapter/ChatAdapter.kt             — адаптер списка чатов (clearAll)
├── data/
│   ├── grpc/GrpcClient.kt                 — facade (signInV2, getChats, profile v2, chatlist v2)
│   ├── grpc/RealGrpcClient.kt             — реализация gRPC клиента
│   ├── grpc/ProfileClient.kt              — ProfileService v2 client + fetchServerInfo
│   ├── grpc/BearerTokenInterceptor.kt     — ClientInterceptor для JWT Bearer token
│   ├── grpc/HermesGrpc.kt                 — Remote Agent gRPC
│   ├── proto/MessengerProto.kt            — proto data classes (Auth, Profile v2, ChatList v2, etc.)
│   ├── session/CredentialStore.kt         — credentials + server list + last_username
│   ├── session/SessionManager.kt          — loginV2 (JWT) + loginV1 (legacy fallback)
│   ├── session/UserSession.kt             — accessToken, refreshToken, authMethod, isJwtAuth
│   ├── auth/AuthManager.kt                — JWT token storage, getBearerToken, getAccessToken
│   ├── models/ErrorHandler.kt              — единый обработчик ошибок
│   └── models/AppLog.kt                   — глобальный логгер
└── theme/ui/
    ├── ThemeApplier.kt                    — применение тем
    └── ThemeUi.kt                         — ThemeUi.bind()
```

---

## Ключевые паттерны

### Auth V2 (JWT) flow (v1.1.3.11+)
```
ServerAuthBottomSheet → LoginBottomSheet → SessionManager.login()
  → try V2 (SignInV2 gRPC)
  → on success: store JWT tokens via AuthManager.storeTokens()
  → on failure: fallback to V1 (Chat stream auth)
  → BearerTokenInterceptor подставляет token во все вызовы
  → Proactive refresh каждые 60с
```

### Profile v2 flow (v1.1.3.13+, dev server only)
```
connect() → fetchServerInfo(/info) → serviceProfileVersion = "2.0"
  → isProfileV2Supported() = true
  → ProfileClient.getProfile() → messenger.ProfileService/GetProfile (JWT)
  → Fallback: legacy ChatService/GetUserProfile via GrpcClient
```

### ChatStream v2 flow (v1.1.3.14+)
```
fetchServerInfo() → serviceChatVersion = "2.0"
  → isChatV2Supported() = true
  → startChat() использует jwt_token вместо password
  → Fallback: password auth если нет JWT токена
```

### ChatList v2 flow (v1.1.3.14+)
```
GrpcClient.pinChat(context, chatId)     — закрепить чат
GrpcClient.searchChats(context, query)  — поиск по чатам
GrpcClient.archiveChat(context, chatId) — архивировать
```
Все методы возвращают `false`/empty на v1 серверах.

### Auth widgets pattern (v1.1.3.11)
Аутентификация вынесена в 3 виджета:
- `ServerAuthBottomSheet` — шторка выбора входа (лого + сервер + статус + login/register)
- `LoginBottomSheet` — шторка входа (username/password + prefill из last_username)
- `RegisterBottomSheet` — шторка регистрации (username/password/email)
- Все наследуют StandardBottomSheet
- Health check через http://host:8082/health
- Используются в: ChatListActivity, ServersActivity
- Drag handle добавлен во все шторки
- Status indicator — только кружок слева от названия, без текста

### Server switch pattern (v1.1.3.11)
При смене сервера через ServersActivity:
- НЕ сохранять `serverAddress` в CredentialStore до успешного входа
- Сохранять `serverAddress` ТОЛЬКО после успешного `SessionManager.login()` в SUCCESS callback
- В `ChatListActivity.serversActivityLauncher` НЕ делать auto-login — пользователь уже вошёл
- Анти-pattern: `CredentialStore.setServerAddress()` до `SessionManager.login()` → двойной вход

### Logout pattern (v1.1.3.11+)
- SessionManager.logout(): очищает password/tokens, сохраняет username в last_username
- LoginBottomSheet.prefillUsername(): предзаполняет username из last_username
- Cancel в login/register sheets: закрывает шторку и возвращает к ServerAuthBottomSheet

### i18n (v1.1.3.9)
- Activity: `getString(R.string.xxx)` — работает напрямую
- Adapter/ViewHolder: `context.getString(R.string.xxx)` или `itemView.context.getString()`
- ViewModel: НЕ использовать обычный ViewModel, только `AndroidViewModel` + `getApplication<Application>().getString()`
- НЕ инициализировать `getString()` в полях класса Activity (до `onCreate()`) — crash!
- Все новые строки ОДНОВРЕМЕННО в values/strings.xml (en) + values-ru/strings.xml

### Темы
- ThemeApplier.apply() до setContentView()
- Цвета устанавливать программно через ThemeUtils.parseSafeColor()
- НЕ использовать ?attr/ в XML для текста на кастомных тёмных темах
- Новые FAB добавлять в ThemeApplier: listOf(R.id.fabAi, R.id.fabAddChat, ...)

### Фильтрация чатов
- ChatAdapter.filter() — dispatchUpdatesTo с offset +1 для Favorites
- НЕ использовать notifyItemRangeChanged
- ChatAdapter.clearAll() — полная очистка с сбросом favoritesItem

### Kotlin 2.3.21 / Coroutines 1.11 (v1.1.3.14)
- `CancellableContinuation.resume()` требует `onCancellation = {}` параметр
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
