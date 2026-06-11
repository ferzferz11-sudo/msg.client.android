# Android Client — Справочник структуры

## Навигация по коду

### Пакеты
```
lavender.client.android/           — Главный пакет (ChatListActivity, SplashActivity и т.д.)
├── ui/
│   ├── chat/                      — Новый чат (NewChatActivity)
│   ├── chat/widget/               — ChatWidget — общий виджет чата
│   ├── hermes/                    — Hermes чат (HermesChatActivity, HermesChatViewModel)
│   ├── owl/                       — OWL чат (OwlChatActivity, OwlChatViewModel, OwlSettingsActivity)
│   ├── notification/              — Уведомления (NotificationActivity)
│   ├── widget/                    — Виджеты (AIBottomSheet, StandardBottomSheet, ActionBottomSheet)
│   ├── remote/                    — Remote Agent (RemoteAgentActivity — TODO)
│   └── adapter/                   — Адаптеры (ChatAdapter, UserAdapter)
├── data/
│   ├── proto/                     — Proto data classes (MessengerProto.kt)
│   ├── grpc/                      — gRPC клиент (GrpcClient.kt, HermesGrpc.kt, OwlGrpc.kt, RealGrpcClient.kt)
│   ├── db/                        — Room DB (AppDatabase.kt, Entities.kt, DAOs)
│   ├── models/                    — Доменные модели (ChatInfo, AIChatInfo, HermesModel)
│   ├── session/                   — Сессии (SessionManager, CredentialStore)
│   ├── repository/                — Репозитории (HermesRepository)
│   └── theme/                     — Темы (ThemeStore, ThemeUtils, ThemeApplier)
├── theme/                         — Кастомные темы (BuiltInThemes, Theme)
└── scripts/                       — Скрипты (release.sh, deploy_android.sh)
```

### Ключевые файлы

| Файл | Назначение |
|------|------------|
| `ChatListActivity.kt` | Главный экран — список чатов, AI шторка, Favorites |
| `AIBottomSheet.kt` | Шторка AI — разделы Hermes/OWL, создание чатов, удаление |
| `ChatWidget.kt` | Общий виджет чата — RecyclerView, emoji, attach, reply |
| `NewChatActivity.kt` | Обычный чат (группы/личные) |
| `HermesChatActivity.kt` | Чат с Hermes агентом |
| `HermesChatViewModel.kt` | ViewModel Hermes + локальная БД |
| `OwlChatActivity.kt` | Чат с OWL AI |
| `OwlSettingsActivity.kt` | Настройки OWL (API key, model, удалённый агент) |
| `GrpcClient.kt` | Единая точка доступа к gRPC (facade) |
| `HermesGrpc.kt` | Hermes/Remote Agent gRPC методы |
| `OwlGrpc.kt` | OWL gRPC методы |
| `RealGrpcClient.kt` | Реализация gRPC клиента (channel, keepalive) |
| `MessengerProto.kt` | Все proto data classes |
| `ThemeStore.kt` | Хранилище текущей темы |
| `ThemeUtils.kt` | Утилиты цветов |
| `ThemeApplier.kt` | Применение тем к UI |
| `Entities.kt` | Room Entity + mapping |
| `SessionManager.kt` | Управление сессией |

### gRPC методы (что есть)

#### ChatService (messenger.proto)
- Auth: SignIn, SignUp
- Chat: SendMessage, GetHistory, DeleteChat, MarkRead
- Hermes: CreateHermesSession, ChatWithOrchestrator, GetHermesHistory
- OWL: ChatWithOWL, GetOwlHistory, GetOwlSettings, UpdateOwlSettings
- AI Chat: ChatWithAI, GetAIChatHistory, Get/UpdateAIChatSettings
- AI Management: GetAIChats, RenameAIChat
- Remote Agents: ListRemoteAgents, GetRemoteAgentStatus, DeployAgentTask
- **Agent Tokens: GenerateAgentToken, RevokeAgentToken, ListAgentTokens** (NEW)

#### HermesAgentService (hermes_remote.proto)
- Connect (bidirectional streaming) — подключение удалённого агента
- HealthCheck
- GenerateAgentToken, RevokeAgentToken, ListAgentTokens (admin)

### Proto → Kotlin mapping

Proto генерируются вручную как data classes в MessengerProto.kt:
- `message XxxRequest` → `data class XxxRequestProto`
- `message XxxResponse` → `data class XxxResponseProto`
- Поля сохраняются как val параметры

### gRPC паттерн (HermesGrpc.kt)

Каждый метод:
1. Создаёт `MethodDescriptor` с маршаллерами
2. Использует `suspendCancellableCoroutine` для suspend-вызова
3. Кодирует запрос через `CodedOutputStream`
4. Декодирует ответ через `CodedInputStream`
5. Timeout через `withTimeoutOrNull`

### Темы (ThemeStore)

- Кастомные темы хранятся в SharedPreferences
- Цвета применяются программно через `ThemeUtils.parseSafeColor()`
- **НЕ использовать** `?attr/` в XML для текста на кастомных тёмных темах
- `ThemeApplier.apply()` ДО `setContentView()`

### Changelog

- `CHANGELOG.md` — dev-facing, подробный
- `app/src/main/assets/changelog_bundled.txt` — user-facing, показывается в приложении
- При релизе: обновлять bundled + CHANGELOG.md

### Сборка

- `compileDebugKotlin` — OK на сервере (~1GB)
- `assembleRelease` — **НЕ** на сервере (OOM, нужно 2GB+)
- APK собирать локально, затем загружать на сервер через SCP
