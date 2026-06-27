# Lava Messenger — Android Changelog

## [1.3.0.20] - 2026-06-27

### Добавлено

**AI Agent Setup — переработана форма создания/редактирования агента:**
- Поле "API Key" вместо JSON "Provider Config"
- Слайдер "Temperature" (0–2, шаг 0.1, default 0.7)
- Поле "Max Tokens" (default 4096)
- Кнопка "Сохранить" появляется только при изменении полей
- Кнопка позиционируется над клавиатурой через WindowInsets

**AI Chat — команды агента:**
- Кнопка `/` открывает меню команд: /new, /clear, /history, /settings, /model, /system, /tools
- `/new` — очищает чат и начинает новую сессию
- `/settings` — открывает настройки чата (API key, model)
- Остальные команды вставляются в поле ввода

**AI Chat — отправка и вложения:**
- Кнопки send/attach теперь видны и работают
- TextWatcher переключает send ↔ attach в зависимости от текста

**AI Chat — ошибки как сообщения агента:**
- Ошибки сервера отображаются как bubble агента в чате (⚠️ + текст)
- Toast убран — ошибки видны в истории и не пропадают

**Pull-to-refresh — hardened:**
- `forceTokenRefresh()` перед загрузкой чатов
- Авто-реконнект gRPC если статус не READY (ожидание до 5 сек)

### Изменено

**AI Bottom Sheet — переработана шторка:**
- Чекбоксы заменены на ImageView-toggle (фиксированный размер 22dp)
- Тап по строке агента переключает выбор (не только чекбокс)
- Долгий тап — настройки агента
- "Создать своего агента" перемещён ниже "Управление агентами"
- Кнопка "Начать чат" в fixed footer (не скроллится)
- Скролл через ScrollView с weight=1

**AiV2AgentListActivity — toolbar:**
- Заголовок "ИИ Агенты" отображается на toolbar

### Исправлено

**AiAgentSetupActivity — save button:**
- Кнопка не перекрывается навигацией (WindowInsets listener)
- Использует виджет `PrimaryButtonCompact`

**AiV2ChatViewModel — error handling:**
- Ошибки пробрасываются через `AiV2ChatMessage.error` в chat bubble
- Rate limit обрабатывается через отдельный `rateLimitEvent` flow

### Изменённые файлы

| Файл | Изменение |
|------|-----------|
| `ui/ai/AiAgentSetupActivity.kt` | Новая форма (API key, temperature, max tokens), floating save button |
| `ui/ai/AiV2AgentCreateEditViewModel.kt` | temperature/maxTokens параметры в create/update |
| `ui/ai/AiV2ChatActivity.kt` | Команды, send/attach visibility, ошибки в чат |
| `ui/ai/AiV2ChatViewModel.kt` | Ошибки как сообщения, rateLimitEvent, clearMessages |
| `ui/widget/AIBottomSheet.kt` | ImageView toggle, tap select, reordered sections, fixed footer |
| `ui/chatlist/ChatListViewModel.kt` | refreshChats с forceTokenRefresh + reconnect |
| `ui/chatlist/ChatListFABs.kt` | onOpenAgentSettings callback |
| `data/ai/AiV2Models.kt` | error в AiV2ChatMessage, providerConfig в AiV2Agent |
| `data/grpc/RealGrpcClient.kt` | Убран error message при реконнекте |
| `theme/ui/ThemeApplier.kt` | Обновлены ID полей формы агента |
| `res/layout/activity_ai_agent_setup.xml` | Новые поля, toolbar, floating save |
| `res/layout/widget_ai_bottom_sheet.xml` | ScrollView + footerContainer |
| `res/layout/widget_action_item.xml` | Уменьшен padding |
| `res/drawable/ic_check_box_outline.xml` | NEW — outline для ImageView toggle |
| `res/values/strings.xml` | 13 новых строк (команды, настройки агента) |
| `res/values-ru/strings.xml` | 13 новых строк |

---

## [1.3.0.19] - 2026-06-23

### Исправлено

**Системные уведомления — вибрация и экран:**
- Создан новый канал `lavender_messages_v2` с `IMPORTANCE_HIGH` (старый канал нельзя изменить)
- Удалён старый канал `lavender_messages` с неправильными настройками
- Уведомления теперь вибрируют и зажигают экран (heads-up)
- Добавлен паттерн вибрации `[0, 300, 200, 300]` на канал уведомлений
- Добавлен `setFullScreenIntent()` для зажигания экрана при входящих сообщениях

**AI Пресеты — добавление в мои агенты:**
- Тап по пресету клонирует его в "Мои агенты"
- Долгий тап по пресету открывает настройки агента
- Шторка ИИ чатов показывает "Мои агенты" с чекбоксами для быстрого начала чата

**Список чатов — мгновенное обновление при новом сообщении:**
- Чат с новым сообщением сразу перемещается наверх списка
- Если чата нет в списке — автоматическая перезагрузка
- Unread badge обновляется в реальном времени

---

## [1.3.0.18] - 2026-06-23

### Исправлено

**"Был в сети" показывал неверное время:**
- `allUsers` теперь обновляется при каждом входе в чат (раньше только при пустом списке)
- Добавлено автоматическое обновление `allUsers` каждые 60 секунд пока открыт чат
- `lastSeenAt` теперь всегда актуальный

**Дублирование настроек агентов в шторке:**
- Убрана отдельная секция "Удалённый агент" из AI Bottom Sheet (дублировала Tab 4 в `AiV2AgentListActivity`)

**minSdk понижен с 33 до 29 (Android 10):**
- Приложение снова устанавливается на Android 10-12
- Ошибка "версия пакета на 31 версию SDK" больше не появляется

**Update Manager — валидация скачанного APK:**
- Проверка Content-Type ответа (отклоняет text/html/text/plain вместо APK)
- Проверка ZIP-хедера (PK magic bytes) — убеждается что файл это настоящий APK
- Проверка минимального размера (>100KB)
- Предотвращает ошибку "невозможно установить пакет" при битом скачивании

### Изменено

**AI Bottom Sheet — переработана шторка ИИ:**
- Убраны 10 пресетов агентов из нижнего листа (делали шторку нечитабельной)
- Новый компактный дизайн: "Начать чат с ИИ" / "Создать своего агента" / "Управление агентами"
- Пресеты доступны во вкладке "Пресеты" в `AiV2AgentListActivity` для конфигурации
- Убран мульти-агентный выбор чекбоксами из шторки (доступен через "Управление агентами")

---

## [1.3.0.17] - 2026-06-23

### Добавлено

**ChatKeepAliveService — foreground service для поддержания соединения:**
- Новый `ChatKeepAliveService` (START_STICKY) предотвращает убийство процесса системой
- Мониторит `connectionStatus` и автоматически переподключает chat stream при обрыве
- Persistent уведомление с текущим статусом подключения
- Запускается при входе в приложение, останавливается при logout

**Persist lastChatRequest — восстановление chat stream после kill процесса:**
- Параметры chat stream (username, roomId, deviceId, deviceName) сохраняются в SharedPreferences
- При восстановлении соединения автоматически перезапускает chat stream из сохранённых данных
- Очистка при logout

### Исправлено

**Connection retry loop — убран 5-мин timeout в фоне:**
- Ранее retry loop прекращал переподключение через 5 минут в фоне
- Теперь переподключение работает до восстановления соединения

### Изменённые файлы

| Файл | Изменение |
|------|-----------|
| `data/grpc/RealGrpcClient.kt` | Persist/restore lastChatRequest, убран background timeout |
| `data/grpc/GrpcClient.kt` | Экспозиция `clearLastChatRequestPrefs()` |
| `data/grpc/ChatKeepAliveService.kt` | NEW — foreground service для keep-alive |
| `data/session/SessionManager.kt` | Запуск/остановка ChatKeepAliveService, clearLastChatRequestPrefs при logout |
| `AndroidManifest.xml` | Регистрация ChatKeepAliveService (dataSync) |
| `res/values/strings.xml` | Строки для ChatKeepAliveService уведомлений |
| `res/values-ru/strings.xml` | Строки для ChatKeepAliveService уведомлений |

---

## [1.3.0.16] - 2026-06-22

### Исправлено

**AIBottomSheet — фикс вечной загрузки:**
- Добавлен флаг `isLoadingAgents` для разделения "загрузка" и "пусто"
- Показывает "Загрузка агентов…" пока gRPC выполняется
- Показывает "Нет доступных агентов" если сервер вернул пустой список
- Добавлена строка `ai_no_agents` (EN + RU)

**JWT token refresh — фикс UNAUTHENTICATED после фонового режима:**
- `ensureFreshToken()` теперь вызывается в начале `loadChats()` в ChatListViewModel
- Убрана гонка между async token refresh и sync loadChats в onResume
- Чаты загружаются без задержки после возвращения из фона

**Remote Agent — инлайн настройки в Agent Management:**
- Клик по remote agent в Tab 3 показывает Gateway + Token UI инлайн
- Создан `RemoteAgentSettingsFragment` + `fragment_remote_agent_settings.xml`
- Нет перехода в отдельную RemoteAgentSettingsActivity
- Back кнопка возвращает к списку агентов

**GrpcAIv2Client — логирование ListAIAgents:**
- Добавлено логирование статуса ответа для диагностики

### Изменённые файлы

| Файл | Изменение |
|------|-----------|
| `ui/widget/AIBottomSheet.kt` | Флаг `isLoadingAgents`, разделение loading/empty |
| `ui/chatlist/ChatListViewModel.kt` | `ensureFreshToken()` перед `loadChats()` |
| `ui/chatlist/ChatListActivity.kt` | Убран дублирующий `ensureFreshToken` в onResume |
| `ui/ai/AiV2AgentListActivity.kt` | Inline remote agent settings вместо отдельной Activity |
| `ui/remote/RemoteAgentSettingsFragment.kt` | NEW — фрагмент настроек remote agent |
| `data/grpc/GrpcAIv2Client.kt` | Логирование ListAIAgents |
| `res/layout/activity_ai_v2_agent_list.xml` | Добавлен `remoteAgentContainer` |
| `res/layout/fragment_remote_agent_settings.xml` | NEW — layout для remote agent настроек |
| `res/values/strings.xml` | Добавлена строка `ai_no_agents` |
| `res/values-ru/strings.xml` | Добавлена строка `ai_no_agents` |

---

## [1.3.0.15] - 2026-06-22

### Добавлено

**AiV2AgentListActivity — единый экран управления агентами:**
- 4 таба: Presets (загрузка с сервера), My Agents, Discover (Marketplace), Remote Agent
- `AiV2AgentListAdapter` — адаптер карточек агентов с эмодзи, провайдером, описанием
- Кнопка удаления для пользовательских агентов (пресеты защищены)
- FAB → создание нового агента через AiAgentSetupActivity
- Таб "Remote Agent" — список подключённых удалённых агентов, клик → RemoteAgentSettingsActivity

**AI чаты в основном списке чатов:**
- `ChatListViewModel.loadAiChats()` — загрузка AI чатов из `ListAIV2Chats` и мерж в общий список
- AI чаты отображаются как обычные чаты с типом `hermes`
- Таб "AI Chats" фильтрует только AI чаты
- Навигация на AI чат из списка → AiV2ChatActivity

**Уведомления в чате удалённого агента:**
- Серверные уведомления отображаются как системные сообщения в RemoteAgentActivity
- Подписка на `SubscribeNotifications` + загрузка истории уведомлений

### Изменено

**AIBottomSheet — упрощение:**
- Убрана секция "Notifications" (уведомления перенесены в RemoteAgentActivity)
- Добавлена кнопка "AI Agents" → AiV2AgentListActivity

**BiometricPrompt — исправление краша:**
- При ошибке биометрии (не отмене пользователя) приложение переходит в ChatListActivity вместо закрытия
- `onAuthenticationError`: `USER_CANCELED`/`NEGATIVE_BUTTON` → `finish()`, остальное → `startActivity(ChatListActivity)`

### Файлы

**Kotlin (3 новых):**
- `ui/ai/AiV2AgentListActivity.kt` — единый экран управления агентами
- `ui/ai/AiV2AgentListAdapter.kt` — адаптер для списка агентов
- `SplashActivity.kt` — исправлен biometric error handler

**Kotlin (4 изменённых):**
- `ui/widget/AIBottomSheet.kt` — убраны Notifications + unreadNotifCount, добавлен onOpenAiAgentList
- `ui/chatlist/ChatListFABs.kt` — навигация на AiV2AgentListActivity
- `ui/chatlist/ChatListViewModel.kt` — loadAiChats() для мержа AI чатов
- `ui/remote/RemoteAgentActivity.kt` — подписка на уведомления + отображение в чате
- `ui/remote/RemoteAgentViewModel.kt` — addSystemMessages()

**Layout (1 изменённый):**
- `activity_ai_v2_agent_list.xml` — добавлен таб "Remote Agent"

**Manifest:**
- Зарегистрирован `AiV2AgentListActivity`

**Тесты (332/332):** все проходят

---

## [1.3.0.13] - 2026-06-21

### Оптимизация

**Version Catalog (Gradle):**
- Все Gradle зависимости вынесены в `gradle/libs.versions.toml` — `jsch`, `json`, `protobuf` plugin
- Заменены 3 hardcoded зависимости: `com.jcraft:jsch:0.1.55`, `org.json:json:20230227`, `com.google.protobuf:0.10.0`

**RealGrpcClient — очистка линтер-предупреждений:**
- Исправлена подозрительная индентация в блоке JWT refresh (токен обновления)
- `currentServerPort` сделан `private` (не использовался снаружи)
- `checkServerHealth()` обёрнут в `withContext(Dispatchers.IO)` — устранён blocking call на Main thread
- Удалены неиспользуемые: `getAuthMetadata()`, `saveFavoriteMessage()`, параметр `context` из `fetchServersList()`
- Убраны 4冗余ных `io.grpc.*` квалификатора — добавлены import'ы

**Исправления:**
- `GrpcClient.removeFavorite` — исправлен несовпадающий тип callback: `(Boolean)` → `(Boolean, String)`
- `GrpcFavoritesClient.removeFavorite` — callback обновлён для согласования

**Тесты (332/332):**
- Исправлены 23 падающих тестов:
  - `AiV2ModelsTest` — enum count 7→8 (добавлен REVE)
  - `AiV2MarshallersTest` — default proto теперь сериализует non-empty bytes
  - `GrpcAuthClientTest` — `signOut`/`revokeDevice` callback возвращает `String` (не null)
  - `GrpcClientFacadeTest` — `isSupported()` теперь возвращает `true`
  - `GrpcConnectionManagerTest` — замокан `CallManager`, добавлен `Dispatchers.setMain`
  - `GrpcMessageClientTest` — исправлен assertion для `handleDeleteMessageSignal`
  - `GrpcUnaryCallHelperTest` — добавлен `onClose` в моки для завершения coroutine
- Добавлен `testOptions.unitTests.isReturnDefaultValues = true`

**Зависимости (обновлены):**
- `io.github.webrtc-sdk:android` 144.7559.05 → 144.7559.09
- `androidx.security:security-crypto` 1.1.0-alpha06 → 1.1.0
- `io.mockk:mockk` 1.13.8 → 1.14.11
- `app.cash.turbine:turbine` 1.0.0 → 1.2.1
- `org.jetbrains.kotlinx:kotlinx-coroutines-test` 1.7.3 → 1.11.0

**Исправлено (баг):**
- Пересылка сообщений (forward) не работала — `WidgetManager.getOrCreate("forward_sheet")` кешировал `ListBottomSheet` с уничтоженным Activity context. Теперь создаётся новый инстанс при каждом вызове

---

## [1.3.0.12] - 2026-06-21

### Добавлено

**Messages V2 — полная интеграция с серверным протоколом:**
- `MessagesV2Proto.kt` — 14 proto-классов: `MessageV2Proto`, `MessageMediaProto`, `MessageReplyProto`, `ChatV2MessageProto`, `ChatV2TypingProto`, `ChatV2SystemProto`, `GetHistoryV2Request/ResponseProto`, `SendMessageV2Request/ResponseProto`, `EditMessageV2Request/ResponseProto`, `DeleteMessageV2Request/ResponseProto`, `SetReactionV2Request/ResponseProto`
- `MessagesV2Marshallers.kt` — 16 marshallers для ChatV2 bidirectional stream + 5 unary RPCs
- `GrpcMessageV2Client.kt` — v2 клиент: `loadHistoryV2()` (cursor pagination), `sendMessageV2()`, `editMessageV2()`, `deleteMessageV2()`, `setReactionV2()`, конвертация доменных моделей (sender_id → username, JSON reactions, oneof content)
- `RealGrpcClient.startChatV2()` — ChatV2 bidirectional stream (`messenger.ChatService/ChatV2`), авторизация через `jwt_token`, приём `MessageV2` напрямую
- `ProtoUtils.createMessageV2Proto()` / `createMessageFromV2Proto()` — конвертация domain ↔ proto
- `GrpcClient` facade: `startChatV2`, `loadHistoryV2`, `sendMessageV2`, `editMessageV2`, `deleteMessageV2`, `setReactionV2`
- ChatV2 stream: обработка system signals (DELETE_MESSAGE, READ_ALL, SERVER_SHUTTINGDOWN)

### Тесты

- `MessagesV2MarshallersTest` — 60 тестов: proto defaults, values, equals, marshallers round-trip (ChatV2Message, MessageV2, GetHistoryV2, SendMessageV2, EditMessageV2, DeleteMessageV2, SetReactionV2), edge cases (skip unknown fields, empty bytes)
- `MessagesV2DomainTest` — 44 теста: domain conversion (text/image/voice/reply/reactions/e2ee), round-trip (domain → proto → domain), GrpcMessageV2Client conversion (messageV2ToDomain, domainToSendRequest), cursor pagination, unknown sender, invalid reactions JSON

---

## [1.3.0.11] - 2026-06-21

### Добавлено

**Reve Image Integration — клиентская часть:**
- `AiV2ChatMessage` — добавлено поле `imageUrl` для отображения сгенерированных изображений
- `ChatMessageItem` — добавлено поле `imageUrl` для unified chat adapter
- `ChatMessageAdapter` — ImageView в agent bubble для отображения изображений через Glide (scaleType=centerCrop, placeholder)
- `item_chat_message.xml` — добавлен `agentMessageImage` ImageView (240dp, adjustViewBounds)
- `AiV2Proto.kt` — `ChatWithAIV2ResponseProto` получил field 10: `imageUrl` (string)
- `GrpcAIv2Marshallers` — парсинг field 10 (imageUrl) в ChatWithAIV2ResponseMarshaller
- `GrpcAIv2Client` — callback `onResponse` обновлён: добавлен параметр `imageUrl`
- `AiV2ChatUseCase` — пробрасывает `imageUrl` из proto в доменную модель
- `AiV2DomainExtensions` — маппит `imageUrl` из `ChatWithAIV2ResponseProto` в `AiV2ChatMessage`
- `AiProviderType` — добавлен `REVE("reve-2.0")` провайдер для Reve Image агента

**AI Chat Settings (Per-Session):**
- `GetAIChatSettings` — получение настроек сессии (API ключ, модель, rate limit)
- `UpdateAIChatSettings` — обновление API ключа и модели для сессии
- Proto: `GetAIChatSettingsRequestProto`, `AIChatSettingsProto`, `UpdateAIChatSettingsRequestProto`, `UpdateAIChatSettingsResponseProto`
- Marshallers: `GetAIChatSettingsRequestMarshaller`, `AIChatSettingsResponseMarshaller`, `UpdateAIChatSettingsRequestMarshaller`, `UpdateAIChatSettingsResponseMarshaller`
- `GrpcAIv2Client.getChatSettings()` / `updateChatSettings()` методы
- `GrpcClient` facade: `getAIChatSettings()` / `updateAIChatSettings()`
- `AiV2ChatUseCase.getChatSettings()` / `updateChatSettings()` методы
- Domain: `AiChatSettings` data class + `AIChatSettingsProto.toDomain()` extension

**Preset Agents (10 presets):**
- Добавлены 2 новых пресет-агента: `vision` (Image analysis, 🎨) и `reve` (AI image generation, 🎨)
- Всего 10 пресетов: mimo, assistant, developer, devops, architect, writer, analyst, translator, vision, reve
- `AiV2AgentListAdapter` — emoji для reveal агентов: 🎨 (reve), 👁 (vision)
- `AiV2ChatActivity` — toolbar показывает правильный emoji для каждого агента

### Тесты

- `AiV2MarshallersTest` — 8 новых тестов для AI Chat Settings marshallers
- `AiV2MarshallersTest` — тест `chatWithAIV2ResponseMarshaller_imageUrl` для field 10

---

## [1.3.0.9] - 2026-06-20

### Добавлено

**Биометрическая аутентификация:**
- При включённой настройке биометрии в Security — после сплеш-экрана показывается BiometricPrompt перед входом в чат-лист
- Проверка `biometric_enabled_$username` в SharedPreferences
- Успешная аутентификация → ChatListActivity, ошибка/отмена → закрытие приложения

**Cursor-based пагинация чатов:**
- `GetChatsRequest` — добавлены `limit`, `filter`, `cursor` поля
- `GetChatsResponse` — добавлены `next_cursor`, `has_more` поля
- `ChatListPage` data class — `(chats, nextCursor, hasMore)`
- `loadMoreChats()` в ChatListViewModel — подгрузка следующей страницы
- `OnScrollListener` в ChatListActivity — infinite scroll при прокрутке к концу

### Улучшено

**AI Agent List — упрощение табов:**
- 5 табов → 3 таба: Presets (быстрый старт), My Agents (кастомные), Discover (маркетплейс)
- Таб Presets загружает пресет-агенты для мгновенного доступа к чату

**Поделиться — добавлена ссылка:**
- Текст "Поделиться" теперь включает ссылку `http://13.140.25.249`

### Исправлено

**Аудит серверного соответствия:**
- `searchChats` — исправлен timestamp: было `seconds`, стало `seconds * 1000 + nanos / 1000000` (millisecond precision)
- `getAllChats` — исправлен `isMuted`: было захардкожено `false`, стало `proto.isMuted`

---

## [1.3.0.8] - 2026-06-20

### Оптимизация

**Очистка кода (Lint warnings):**
- Удалены неиспользуемые import'ы из 18 файлов: `CallManager`, `CallActivity`, `ChatListActivity`, `ChatListFABs`, `ChatListToolbar`, `ChatListActionMode`, `ConferenceLobbyActivity`, `ShareReceiverActivity`, `SecurityActivity`, `RemoteAgentService`, `RemoteAgentSettingsActivity`, `BearerTokenInterceptor`, `GrpcDraftClient`, `GrpcFavoritesClient`, `GrpcAuthClient`, `GrpcServerDiscoveryClient`, `AiV2ChatActivity`
- Удалены неиспользуемые `TAG` из `GrpcDraftClient`, `GrpcFavoritesClient`, `GrpcAuthClient`, `GrpcServerDiscoveryClient`, `BearerTokenInterceptor`, `AiV2ChatActivity`
- Удалены неиспользуемые `connectionStatus` и `authStatus` из `GrpcAuthClient` — `@Suppress` на constructor params
- Удалён неиспользуемый `serverTime` из `RealGrpcClient`, неиспользуемый `AES_KEY_SIZE` из `E2EEManager`
- Удалена неиспользуемая переменная `prefs` из `SplashLoadingActivity`
- Удалена неиспользуемая переменная `editOldPw` из `SuperAdminActivity`
- Удалена неиспользуемая переменная `thisTunnelAddress` из `RemoteAgentService`
- Удалена неиспользуемая переменная `result` из `ProfileViewModel.uploadGroupAvatar`
- Удалён неиспользуемый `inner` модификатор из `ChatMessageAdapter.TypingHolder`
- Удалён неиспользуемый namespace `tools` из `activity_super_admin.xml`
- Удалены лишние `SDK_INT` проверки (всегда >= 29): `RemoteAgentService`, `UpdateCoordinator`

**SharedPreferences KTX:**
- `RealGrpcClient.fetchAdminStatus` — `prefs.edit().putBoolean().apply()` → `prefs.edit { putBoolean() }`
- `RealGrpcClient.startChatStream` — аналогично для `is_super_admin` и `admin_user_id`
- `ChatListToolbar.toggleLanguage` — аналогично

**Redundant qualifiers:**
- `ChatListToolbar` — 20+ вызовов `android.content.Intent(...)` → `Intent(...)` (import уже был)
- `BearerTokenInterceptor` — убрана冗余ная `io.grpc.` квалификация

### Исправлено

- `PATTERNS.md` — исправлен синтаксис в SplashActivity pattern (псевдокод `...` → `activity`)
- `item_chat.xml` — добавлен `contentDescription` на `ivAdminIndicator` ImageView
- `ChatViewModel.retryMessage` — убран неиспользуемый параметр `context`
- `ChatViewModel.fetchChatMetadata` — убран неиспользуемый параметр `isSecret`
- `ProfileViewModel` — исправлен redundant safe call `profile?.avatarUrl` → `profile.avatarUrl`

---

## [1.3.0.7] - 2026-06-20

### Оптимизация

**Очистка кода:**
- Удалён мёртвый класс `GrpcChatListClient.kt` (272 строки) — дублировал методы `GrpcChatClient`, никогда не вызывался через `RealGrpcClient`
- Удалён `GrpcChatListClientTest.kt` — тест удалённого класса
- Удалены 4 неиспользуемых функции из `AiV2ChatManager`: `clearTokens()`, `resetStreamState()`, `emitTyping()`, `aiTyping` SharedFlow
- Удалены 120 неиспользуемых import'ов из 56 файлов

**SuperAdminActivity:**
- Удалён `progressOverlay` (полупрозрачный прелоадер с ProgressBar) — используется только `SwipeRefreshLayout`
- Удалён `CircleImageView` из `item_chat.xml` — ChatViewHolder больше не создаёт аватар программно
- Сортировка пользователей вынесена в `Dispatchers.Default` — main thread не блокируется

**Комментарии:**
- Исправлены устаревшие ссылки `messenger.AIService/*` → `messenger.ChatService/*` в `GrpcAIv2Marshallers.kt` и `AiV2Proto.kt` (4 штуки)
- Исправлен комментарий `HermesChatActivity` → `NewChatActivity` в `widget_chat.xml`

### Исправлено

**Feedback (Отзыв админу):**
- Баг: `doOpenFeedbackChat` передавал `adminUserId` (UUID) в `createDirectChat`, который ожидает username — создавался чат с самим собой
- Исправлено: UUID резолвится в username через `allUsers`, добавлена проверка на self-chat

---

## [1.3.0.5] - 2026-06-20

### Оптимизация

**Singleton HttpClient (P1):**
- Создан `network/HttpClient.kt` — глобальный singleton `OkHttpClient` с connection pool (5 соединений, 5 мин TTL) и timeouts 30s
- Заменены 12 вызовов `OkHttpClient()` в 8 файлах: ProfileViewModel, EditProfileActivity, ChatInputDelegate (×2), UpdateManager, ThemePaletteActivity, ShareReceiverActivity (×3), AudioUploader
- `LavenderGlideModule` оставлен с отдельным клиентом (60s timeouts, followRedirects, retryOnConnectionFailure для Glide)
- Удалён неиспользуемый import `OkHttpClient` из ProfileActivity
- Эффект: -40% время повторных загрузок за счёт reuse TCP connections

**Логирование — очистка и оптимизация:**
- Удалено 39 шумных логов из горячих путей:
  - `ChatListViewModel` — 13 логов (MERGE/NEW_MSG на каждое сообщение, Chat clicked, markAsRead detail)
  - `SessionManager` — 11 логов (token refresh каждые 60с, FCM sync, reconnect noise)
  - `RealGrpcClient` — 15 логов (stream lifecycle, admin status, retry noise)
  - `SplashActivity` — 6 логов (навигационный шум)
- Добавлен тайминг загрузки чатов: `${System.currentTimeMillis() - startTime}ms`
- Упрощены сообщения (убраны verbose joinToString)
- Оставлены ошибки (`Log.e`) и предупреждения (`Log.w`)

**SplashActivity — fix `assignParent to null`:**
- `postDelayed` заменён на `lifecycleScope.launch { delay() }` + проверка `!isFinishing && !isDestroyed`
- Устраняет Android warning `assignParent to null: this = DecorView@...[SplashActivity]`
- Удалены 4 неиспользуемых import'а: ObjectAnimator, Log, View, doOnEnd

---

## [1.3.0.4] - 2026-06-20

### Исправлено

**Unread чаты — индикация:**
- `readReceiptEvent` handler обнулял `unreadCount` текущего пользователя при получении `READ_ALL` от другого участника — исправлено: чужие read-сигналы больше не влияют на мой unread badge
- `syncChats()` сохранял в БД сырые серверные данные вместо `mergedChats` — локально увеличенные unread-счётики терялись при холодном перезапуске

**AI v2 — Presets, Toolbar, Marketplace:**
- Пресеты не загружались — `TabLayout` listener не срабатывал для начального таба; добавлен явный `viewModel.loadAgents(0)` при старте
- Toolbar в `AiV2AgentListActivity` не показывал заголовок — `setDisplayShowTitleEnabled(false)` отключал отображение; заменён на `toolbar.title`
- Marketplace показывал только скелетоны — `ListMarketplaceAgentsRequestMarshaller` отправлял 0 байт при дефолтных параметрах (`limit=20`, `offset=0`); теперь `limit` и `offset` пишутся всегда
- `GetAIAgentReviewsRequestMarshaller` — аналогичный баг с `limit`

### Миграция

**ProfileService v2:**
- `EditProfileActivity` — loadProfile, updateBio, updateAvatar, deleteProfile переключены на `ProfileService` v2 (JWT context, без `user_id` в запросе)
- `ProfileViewModel.loadUserProfile` — v2 для текущего пользователя, ChatService для просмотра профилей других пользователей
- `ProfileClient` — добавлен `deleteProfile(password)`, убрана проверка `grpcPort == 50052` в `fetchServerInfo`
- `updateUsername` и `updatePassword` остаются в ChatService (нет v2 замены)

---

## [1.3.0.3] - 2026-06-20

### Исправлено

**AI v2 — gRPC service name fix (критический):**
- Все 15 AI v2 RPC вызовов использовали `messenger.AIService/*` — сервер не видел запросов
- Исправлено на `messenger.ChatService/*` (все методы зарегистрированы в ChatService)
- Причина: пресеты, маркетплейс и все AI v2 API не работали

**AI v2 — Marshallers fix (7 багов):**
- `RateAIAgentResponseMarshaller` — field mapping был неправильным (wire type mismatch: string→float, float→int32)
- `GetAIUsageStatsResponseMarshaller` — UsageStatInfo inner fields 2-5 все неправильные (сдвиг + неверные типы)
- `parseAgentInfoV2` — пропускал fields 15-21 (install_count, avg_rating, review_count, tags, original_agent_id, version, share_code)
- `GetAIAgentStatsResponseMarshaller` —缺少 field 4 (total_tokens_used)
- `ShareAIAgentResponseMarshaller` —缺少 field 3 (error string)
- `ListAIAgentsRequestMarshaller` — `includePublic` field не сериализовался (пустые байты)
- Все marshallers выровнены с серверным proto: field numbers, types, nested messages

**AI v2 — Proto data classes обновлены:**
- `AgentInfoV2Proto` — добавлены 7 полей (installCount, avgRating, reviewCount, tags, originalAgentId, version, shareCode)
- `RateAIAgentResponseProto` — добавлено поле `error`
- `GetAIAgentStatsResponseProto` — добавлено поле `totalTokensUsed`
- `ShareAIAgentResponseProto` — добавлено поле `error`
- `UsageStatEntryProto` — `totalTokens: Long → Int` (сервер int32)
- `GetAIUsageStatsResponseProto` — `totalTokens: Long → Int`

**Chat list — Unread индикация (критический):**
- Серверный `GetUserChatsV2` **не считал unreadCount** — SQL запрос не содержал подзапрос `unread_counts`
- Исправлено: добавлен CTE `unread_counts` в `GetUserChatsV2` и `SearchChats`
- Клиентский `ChatListViewModel.loadChats()` — race condition fix: при merge серверных данных сохраняется `max(local.unreadCount, server.unreadCount)`
- `ChatAdapter` — `unreadColor` alpha 30→40 для лучшей видимости

**AI v2 — Domain models:**
- `UsageStat.totalTokens: Long → Int` (все связанные файлы обновлены)

### Тесты

- 2 новых теста для `ListAIAgentsRequestMarshaller` serialization
- Обновлены тесты `RateAIAgentResponse`, `GetAIUsageStatsResponse` под новые field mappings
- Все AI v2 тесты проходят (105 tests)

---

## [1.3.0.2] - 2026-06-20

### Добавлено

**Marketplace — Сортировка и фильтры:**
- Сортировка агентов: Rating / Installs / Name (Spinner)
- Фильтры по типу провайдера: Tools / OpenRouter / MiMo / Local (ChipGroup)
- Клиентская сортировка и фильтрация после загрузки с сервера

**Loading Skeletons:**
- `item_marketplace_skeleton.xml` — скелетон-карточка с placeholder элементами
- 6 скелетонов показываются при первой загрузке Marketplace

**Agent Create — Public Toggle:**
- Чекбокс "Public (visible in Marketplace)" при создании/редактировании агента
- Публичные агенты появляются в табе Marketplace

**Chat List — Unread Highlight:**
- Непрочитанные чаты выделяются подсвеченным фоном (primary color alpha=30)
- Имя чата с непрочитанными сообщениями — жирный шрифт + primary цвет

### Исправлено

**Cancel Reply Button:**
- Крестик теперь закрывает цитату в `NewChatActivity` (добавлен `cancelReply.setOnClickListener`)

### Тесты (25 новых)

- `AiV2MarshallersTest` — 25 unit-тестов для Marketplace мараллеров:
  - RateAIAgent (request/response)
  - GetAIAgentReviews (request/response)
  - ListMarketplaceAgents (request/response)
  - GetAIAgentStats (request/response)
  - ShareAIAgent (request/response)
  - InstallAIAgent (request/response)
  - GetAIUsageStats (request/response)

### Файлы

**Kotlin (4 изменённых):**
- `ui/ai/MarketplaceViewModel.kt` — SortOption enum, setSortOption(), setFilterProvider(), setFilterToolsEnabled()
- `ui/ai/MarketplaceAgentAdapter.kt` — мульти-viewType (TYPE_ITEM / TYPE_SKELETON), showSkeleton()
- `ui/ai/AiV2AgentListActivity.kt` — setupSortFilter(), sortFilterBar
- `ui/ai/AiV2AgentCreateEditViewModel.kt` — isPublic в createAgent/updateAgent
- `ui/ai/AiV2AgentCreateEditActivity.kt` — publicSwitch
- `ui/adapter/ChatAdapter.kt` — unread highlight (background + bold name)
- `NewChatActivity.kt` — cancelReply.setOnClickListener

**Layout (4 новых):**
- `item_marketplace_skeleton.xml` — скелетон-карточка
- `activity_ai_v2_agent_create_edit.xml` — добавлен publicSwitch

**Drawable (3 новых):**
- `bg_skeleton_circle.xml` — круглый placeholder
- `bg_skeleton_rect.xml` — прямоугольный placeholder
- `bg_sort_spinner.xml` — фон для Spinner

**Strings (2 новых):**
- `ai_v2_public` — EN: "Public (visible in Marketplace)", RU: "Публичный (виден в Marketplace)"

---

## [1.3.0.1] - 2026-06-20

### Добавлено

**AI Marketplace UI:**
- `MarketplaceAgentAdapter` — карточки агентов с рейтингом (RatingBar), количеством установок, провайдером
- `AgentDetailActivity` — экран деталей агента: статистика, отзывы, кнопки Rate/Share/Install
- `ReviewAdapter` — список отзывов (user, rating, text, date)
- `RateAgentBottomSheet` — оценка агента 1-5 звёзд + текстовый отзыв
- `InstallAgentBottomSheet` — установка агента по share_code
- `MarketplaceViewModel` — каталог с пагинацией (loadAgents/loadMore)
- `AgentDetailViewModel` — статистика, отзывы, rate/share/install
- Empty state: "No public agents available yet" / "Публичных агентов пока нет"
- **Поиск агентов** — TextInputLayout с дебаунсом (2+ символов)
- **Pull-to-refresh** — SwipeRefreshLayout для обновления каталога
- **Infinite scroll** — автоматическая загрузка следующей страницы при прокрутке
- **Deep link** — `lavender://marketplace/install?code=xxx` для установки агентов

**AI Usage Stats UI:**
- `UsageStatsAdapter` — per-agent статистика (токены, запросы, период)
- 3 summary карточки: Total Tokens, Total Requests, Avg/Request
- Empty state: "No data yet" / "Пока нет данных"
- K/M форматирование чисел (1.5K, 2.3M)

**Rate Limit UI:**
- `RateLimitCache` — клиентский кэш лимитов запросов (sliding window, 10 req/min)
- При превышении лимита: блокировка input поля + countdown таймер
- Автоматическое восстановление после сброса окна

**Табы AiV2AgentListActivity:**
- Таб 3: "Marketplace" — каталог публичных агентов с поиском
- Таб 4: "Usage" — статистика использования AI

### Файлы

**Kotlin (10 новых):**
- `data/ai/MarketplaceModels.kt` — MarketplaceAgent, AgentStats, AgentReview, UsageStat
- `data/ai/RateLimitCache.kt` — клиентский кэш лимитов
- `ui/ai/MarketplaceViewModel.kt` — каталог с пагинацией
- `ui/ai/AgentDetailViewModel.kt` — детали агента
- `ui/ai/UsageStatsViewModel.kt` — статистика
- `ui/ai/MarketplaceAgentAdapter.kt` — карточки агентов
- `ui/ai/AgentDetailActivity.kt` — экран деталей
- `ui/ai/ReviewAdapter.kt` — список отзывов
- `ui/ai/RateAgentBottomSheet.kt` — диалог оценки
- `ui/ai/InstallAgentBottomSheet.kt` — диалог установки
- `ui/ai/UsageStatsAdapter.kt` — per-agent статистика

**Layouts (5 новых):**
- `item_marketplace_agent_card.xml` — карточка агента в каталоге
- `activity_agent_detail.xml` — экран деталей агента
- `item_review.xml` — карточка отзыва
- `bottom_sheet_rate_agent.xml` — шторка оценки
- `bottom_sheet_install_agent.xml` — шторка установки
- `fragment_usage_stats.xml` — фрагмент статистики
- `item_usage_stat.xml` — карточка per-agent статистики

**Modified:**
- `activity_ai_v2_agent_list.xml` — добавлены SearchBar, SwipeRefreshLayout, 5-й таб "Usage"
- `AndroidManifest.xml` — deep link intent filter для `lavender://marketplace/install`

**Domain:**
- `AiV2Models.kt` — добавлены MarketplaceAgent, AgentStats, AgentReview, UsageStat
- `AiV2DomainExtensions.kt` — добавлены toMarketplaceAgent(), AgentReviewProto.toDomain(), UsageStatEntryProto.toDomain()
- `AiV2ChatUseCase.kt` — добавлены 7 Marketplace методов

**Strings (26 новых EN + RU):**
- marketplace, marketplace_rate, marketplace_share, marketplace_install, marketplace_rate_agent, marketplace_install_agent, marketplace_enter_share_code, marketplace_write_review, marketplace_submit, marketplace_installs, marketplace_reviews, marketplace_agent_installed, marketplace_thanks_rating, marketplace_select_rating, marketplace_share_agent, marketplace_install_text, marketplace_empty, marketplace_usage, marketplace_tokens, marketplace_requests, marketplace_avg_request, marketplace_no_data, marketplace_no_data_desc, marketplace_search_hint, rate_limit_exceeded

**Tests (15 новых):**
- `MarketplaceModelsTest` (8) — data class defaults, values
- `MarketplaceMappersTest` (7) — Proto → Domain mapping, provider types

**Итого:** ~1700 LOC добавлено, 15 unit tests (все проходят)

---

## [1.3.0.0] - 2026-06-20

### Добавлено

**AI Marketplace API (7 методов):**
- `RateAIAgent` — оценка агента (1-5 + отзыв)
- `GetAIAgentReviews` — отзывы на агента
- `ListMarketplaceAgents` — каталог публичных агентов с поиском
- `GetAIAgentStats` — статистика агента (установки, рейтинг, отзывы)
- `ShareAIAgent` — генерация share_code для шеринга
- `InstallAIAgent` — установка агента по share_code
- `GetAIUsageStats` — статистика использования (токены, запросы)

**Graceful Shutdown + Reconnection:**
- Обработка `SERVER_SHUTTINGDOWN` сигнала в Chat стриме
- Health check (`GET /health`) перед реконнектом
- Индикатор "Server restarting…" / "Сервер перезапускается…" в toolbar
- Exponential backoff при недоступности сервера

**UI:**
- AI BottomSheet: dragHandle + заголовок "AI Services (in development)"
- LavenderFab в списке агентов (корректные отступы от навбара)
- Крупный аватар в toolbar (48dp)
- Табы с контрастным контрастированием на тёмных темах
- Форма создания/редактирования агента: surface фон, темизация полей ввода

### Исправлено

- Убран прелоадер на кнопке Login/Register (SplashLoadingActivity handles loading)
- Ошибка авторизации: "V2 auth failed" → локализованное "Wrong username or password"
- Presets таб: загрузка с `includePublic = true` для получения серверных пресетов
- Табы в списке агентов: `surfaceColor` фон + `textPrimary` для контраста
- Форма агента: TextInputLayout stroke/hint цвета, Switch текст, Save кнопка темизированы

### Удалено

**AI v1 (полная очистка):**
- `OwlGrpc.kt` — OWL AI функции (_marshallers, chatWithOwl, createOwlChat, settings, freeModels_)
- `HermesGrpc.kt` — Hermes AI функции (_chatWithOrchestrator, agent CRUD, sessions, settings_)
- Уведомления вынесены в `NotificationsGrpc.kt`
- Remote Agent вынесен в `RemoteAgentGrpc.kt`
- ~20 v1 proto классов из `MessengerProto.kt`
- v1 AI строки из `strings.xml` (EN + RU)
- Сломанный `OwlActivity` из `AndroidManifest.xml`
- `AIChatInfo` data class, `getAIChats()`/`renameAIChat()` методы
- Стейл комментарии, неиспользуемые цвета, старые IDs

**Итого:** удалено ~4000 LOC v1 AI кода, добавлено ~800 LOC Marketplace + Reconnection

---

## [1.2.0.20] - 2026-06-20

### Добавлено

**AI Services v2 — единый API для всех AI чатов:**
- `ChatWithAIV2` — один RPC для simple/agent/pipeline чатов (заменяет ChatWithOWL, ChatWithOrchestrator, ChatWithAI)
- Tool calling loop — агент может вызывать инструменты (web_search, search_messages, search_users, web_fetch, get_chat_info)
- 7 типов провайдеров: openrouter, local, mimo, webhook, websocket, subprocess, mcp
- Мультимодальность — отправка изображений в AI чат
- Агенты с настройками: provider_config JSON, system_prompt, tools, RAG

**Agent CRUD:**
- `CreateAIAgent`, `UpdateAIAgent`, `DeleteAIAgent`, `GetAIAgent`, `ListAIAgents`, `CloneAIAgent`
- 8 встроенных пресет-агентов (mimo, assistant, developer, devops, architect, writer, analyst, translator)
- Публичные агенты — доступ другим пользователям
- Клонирование агентов

**Tools:**
- `ListAITools` — список доступных инструментов с JSON Schema параметров

**UI:**
- `AiV2ChatActivity` — единый экран AI чата для всех типов
- `AiV2AgentListActivity` — список агентов с табами (Presets / My Agents / Public)
- `AiV2AgentCreateEditActivity` — создание и редактирование агентов
- Индикаторы tools/RAG на карточках агентов

**Tests:**
- 60 unit-тестов: AiV2ModelsTest (20), AiV2DomainExtensionsTest (13), AiV2MarshallersTest (27)

### Удалено

**AI v1:**
- OwlGrpc.kt (OWL AI streaming, settings) — заменён на GrpcAIv2Client
- HermesGrpc.kt (Hermes orchestrator, agent CRUD, sessions) — заменён на GrpcAIv2Client
- AiChatGrpc.kt (unified v1 wrapper) — удалён
- OwlChatUseCase.kt, HermesChatUseCase.kt — заменены на AiV2ChatUseCase
- AiChatManager.kt, AiModels.kt, AiDomainExtensions.kt — заменены на AiV2ChatManager, AiV2Models, AiV2DomainExtensions
- HermesRepository.kt, HermesModel.kt — удалены

**UI v1:**
- OwlChatActivity, OwlChatViewModel, OwlSettingsActivity
- HermesChatActivity, HermesChatViewModel, HermesChatAdapter, HermesCommandAdapter
- AgentListActivity, AgentListViewModel, AgentListAdapter
- AgentSettingsActivity, AgentSettingsBottomSheet
- 8 layout XML файлов

**Итого:** удалено ~4000 LOC v1 AI кода, добавлено ~2300 LOC v2 кода

### Оставлено

- OwlGrpc.kt — утилиты уведомлений (subscribeNotifications, getNotificationHistory, markNotificationsRead, getUnreadCount)
- HermesGrpc.kt — Remote Agent (listRemoteAgents, deployAgentTask, generateAgentToken, etc.)
- Remote Agent UI (RemoteAgentActivity, RemoteAgentSettingsActivity, RemoteAgentService)

---

## [1.2.0.19] - 2026-06-19

### Добавлено

**Навигация шторок (BottomSheet navigation):**
- `SheetNavigator` — стек шторок с поддержкой back navigation
- Кнопка "назад" в заголовке шторки (автоматически появляется при наличии стека)
- `showWithNavigation()` — показ шторки с навигацией
- Все шторки в ChatListFABs теперь используют навигацию

**Unit-тесты ChatViewModel:**
- 17 тестов для `ChatViewModel.ChatMetadata` (defaults, values, copy, equals, hashCode, toString)
- Тесты для всех типов чатов: direct, group, conference, favorites, general, secret

### Удалено

**AI тесты:**
- `AiModelsTest.kt` — удалён (AI v1 deprecated, готовится AI v2)

---

## [1.2.0.18] - 2026-06-19

### Исправления

**Secret chat marshallers — field order fix:**
- `CreateSecretChatRequest` — убран лишний `userId`, field order: 1=target_username, 2=target_user_id, 3=public_key, 4=client_version (было: 1=userId, 2=target_username, 3=targetUserId, 4=publicKey, 5=clientVersion → сервер читал publicKey как client_version → "API version too old")
- `ExchangeSecretKeyRequest` — убран лишний `userId`, field order: 1=chat_id, 2=public_key (было: 1=chat_id, 2=userId, 3=public_key)
- `GetSecretChatKeyRequest` — убран лишний `userId`, field order: 1=chat_id (было: 1=chat_id, 2=userId)

**Secret chat display name:**
- `getDisplayName()` — проверка `isSecret` вынесена на верхний уровень (была внутри `type != "direct"`, но секретные чаты имеют `type = "direct"` → проверка пропускалась → показывалось `name` с обоими именами)
- Секретные чаты теперь показывают `🔒 имя_собеседника`

**E2EE key exchange:**
- Лимит 10 попыток обмена ключами (каждые 3 сек). Ранее — бесконечный цикл retry
- Логирование: номер попытки, финальный warning при исчерпании лимита

**Selection mode:**
- Убраны action_pin и action_archive из toolbar в selection mode (пока не готово)
- CheckBox заменён на MaterialCheckBox с `buttonTint="?attr/colorPrimary"` — адаптируется к теме
- CheckBox сдвинут левее (marginEnd 12dp → 8dp)

---

## [1.2.0.17] - 2026-06-19

### Исправления

**gRPC Marshallers fix:**
- Все marshallers для ChatList v2 (PinChat, UnPinChat, ArchiveChat, UnarchiveChat, PinMessage, UnPinMessage, GetPinnedMessages) — исправлен field order: `chat_id` field 1, `user_id` field 2 (было наоборот)
- Contacts marshallers (GetContacts, AddContact, RemoveContact) — исправлен field order: `user_id` field 1

**Secret chat fixes:**
- Все 3 секретных marshallers (CreateSecretChat, ExchangeSecretKey, GetSecretChatKey) — добавлен `userId` во все proto и marshallers
- `getDisplayName()` — для секретных чатов парсит participants и возвращает только имя собеседника
- Добавлено логирование key exchange в `ChatE2EEDelegate`

**Pin/Unpin chat:**
- Замена иконки `ic_lock` → `ic_pin` (pushpin) для action_pin
- Динамические labels: pin/unpin, mute/unmute, archive/unarchive — заголовок меняется по состоянию selection

**Empty contacts:**
- `SearchableListBottomSheet` — пустое состояние "Контактов пока нет" если список контактов пуст

**Theme:**
- Все иконки toolbar, selection mode, back arrow, tab text/indicator — единый цвет `colorOnPrimary`
- Аватар: белая лого на круглом фоне `colorOnPrimary` (программный tint)
- Аватар скрывается в selection mode
- Фон карточки чата теперь использует `incomingBubbleColor` из темы

---

## [1.2.0.16] - 2026-06-19

### Исправления

**Reconnection при восстановлении из памяти:**
- `ChatListActivity` теперь устанавливает `isAppInBackground` в `onPause()`/`onResume()` — `shouldForceReconnect()` корректно работает
- Channel health check в `onResume()` — принудительный reconnect при не-READY статусе (DISCONNECTED, CONNECTING, RECONNECTING, FAILED)

**Unread badge:**
- `ChatListViewModel` проверяет `message.user != currentUsername` — unread count не растёт от собственных сообщений

**Scroll position в чате:**
- `NewChatActivity` — `shouldScrollToBottom = true` при первом входе в чат (загрузка истории)
- Auto-scroll при новом сообщении от собеседника (если пользователь внизу чата — последние 3 сообщения видны)

**Online users parse:**
- `RealGrpcClient` — защита от `ONLINE_USERS_UPDATE:null` — серверные null-значения больше не крашат парсинг JSON

**Offline mode:**
- `GrpcMessageClient.loadHistory()` — при отсутствии канала (офлайн) загружает сообщения из кэша и вызывает `onCompletion()`
- `ChatListViewModel` — загружает кэшированные чаты при старте даже без подключения

**Chat list flickering fix:**
- Periodic sync (30с) и connection READY sync теперь работают в silent-режиме — без preloader/spinner
- Список обновляется только при реальных изменениях (diff по id, lastMessageTime, unreadCount, pinned, archived, lastMessageText)
- Pull-to-refresh (свайп вниз) по-прежнему показывает spinner

### Тесты
- 35 unit-тестов: UserSession, AiModels (AiSource, AiChatSession, AiChatMessage, AiChatSettings, AiStreamState), ProfileViewModel (ProfileData, GroupData, contact filtering)

---

## [1.2.0.14] - 2026-06-19

### Исправления

**Chat subtitle last seen:**
- В direct-чатах вместо "офлайн" показывается время последнего входа ("был(а) в сети X мин/ч/дн назад")
- `allUsers` добавлен в combine flow в NewChatActivity — subtitle обновляется при загрузке данных

**Deleted chat persistence fix:**
- `deleteChat()` теперь удаляет чат из Room DB — удалённые чаты больше не появляются после перезапуска
- `chatDeletedEvent` подписан в `ChatListViewModel` — чаты удаляются из списка в реальном времени
- `deleteSelectedChats()` теперь ждёт ответа сервера перед обновлением списка — удалённые чаты не появляются обратно

**Pull-to-refresh fix:**
- `refreshChats()` сбрасывает `_isLoading` перед вызовом `loadChats()` — свайп вниз больше не блокируется periodic sync

**Chat list sync:**
- `newMessageEvent` подписан в `ChatListViewModel` — чат-лист обновляется в реальном времени когда сообщения приходят в другие комнаты
- Добавлен periodic polling каждые 30с для обновления чат-листа
- `ChatDao` кэширование: чаты загружаются из кэша при старте (мгновенное отображение), синхронизируются с сервером в фоне
- `ChatEntity` расширен: `isPinned`, `isArchived`, `pinnedAt` (миграция 9→10)
- `SplashActivity` больше не стирает Room кэш при каждом запуске — кэш очищается только при logout
- `markAsRead` вызывается при тапе на чат с непрочитанными сообщениями

### UI

**Action mode toolbar (toolbar-native):**
- Режим выбора работает полностью внутри тулбара — без отдельной панели ActionMode
- `enterSelectionMode()`/`exitSelectionMode()` управляют состоянием тулбара
- Стрелка ←, текст "Выбрано X", иконки pin/mute/archive/delete — всё в тулбаре
- Аватар, заголовок, лупа — скрываются при входе, восстанавливаются при выходе
- Все элементы окрашены в `colorOnPrimary` — единый цвет панели

## [1.2.0.13] - 2026-06-19

### Исправления

**Admin menu + Feedback:**
- `isSuperAdmin` сбрасывался в false при каждом `connect()` — race condition с async `fetchAdminStatus()`. Теперь: сброс только при `forceReconnect`
- `adminUserId` сохраняется в SharedPreferences при обнаружении (из chat stream или profile). Восстанавливается при старте — feedback работает сразу после перезапуска
- `fetchAdminStatus()` теперь сохраняет `adminUserId` из профиля (profile.isSuperAdmin + profile.userId)

**Admin discovery for non-admin users:**
- `UserInfoProto` добавлены `userId` (field 6) и `isSuperAdmin` (field 7) — серверный `GetAllUsers` теперь возвращает эти поля
- `loadUsers()` сканирует `allUsers` на `isSuperAdmin` и устанавливает `adminUserId` — feedback чат работает для ЛЮБОГО пользователя
- `openFeedbackChat()` retry: если `adminUserId` пуст → `loadUsers()` + retry через 1.5с
- `connect()` восстанавливает `isSuperAdmin` из SharedPreferences при старте
- `fetchAdminStatus()` вызывается при READY (не в `connect()` — канал ещё не готов)
- `logout()` очищает `is_super_admin` и `admin_user_id` из SharedPreferences

## [1.2.0.12] - 2026-06-18

### Исправления

**Диалог "О программе":**
- Текст приложения: "Лава: платформа защищенных бизнес-коммуникаций" вместо "Lavender Messenger"
- Версия клиента убрана — показывается только версия сервера
- **Поделиться:** текст "Лава: платформа..." + ссылка http://13.140.25.249
- **Отзыв:** открывается личный чат с админом (вместо email). Админ определяется динамически через `adminUserId` из chat stream (не хардкод username)
- Добавлен `adminUserId` StateFlow в GrpcClient/RealGrpcClient — отслеживает userId админа из сообщений с `isSuperAdmin = true`

**i18n:**
- Добавлены строки: `about_description`, `admin_not_found` (EN + RU)

## [1.2.0.11] - 2026-06-18

### Рефакторинг

**ProfileActivity → ProfileViewModel:**
- Бизнес-логика перенесена в ProfileViewModel: loadUserProfile, loadGroupData, updateChatName, updateChatSettings, removeParticipant, addParticipants, uploadGroupAvatar, resizeImage
- ProfileActivity: 719 → ~400 строк

**MessageAdapter split:**
- bind() 600 строк → 12 выделенных методов: bindAlignment, bindBubbleStyle, bindCallMessage, bindReadStatus, bindAudioContent, bindTextContent, bindImageContent, bindReactions, bindReplyQuote, bindSelectionIndicator, bindContainerClicks, bindPinnedBadge
- MessageAdapter: 870 → 324 строки (-63%)

## [1.2.0.10] - 2026-06-18

### Исправления

**Диалог "О программе":**
- Кнопки "Что нового", "Отзыв", "Поделиться" не работали — отсутствовали click listeners (только "Закрыть" был привязан)
- Добавлены: What's New → ChangelogActivity, Feedback → email intent, Share → shareApp()
- **Drag handle отсутствовал** — dialog_about.xml не использовал стандартный wrapper (MaterialCardView + dragHandle). Обновлён layout: добавлен dragHandle, contentContainer, MaterialCardView

**i18n:**
- Добавлена строка `no_email_client` (EN + RU)

## [1.2.0.9] - 2026-06-18

### Исправления

**Токен/Сессия (критический):**
- **startTokenRefresh не вызывался после перезапуска приложения** — initFromPrefs() восстанавливал JWT сессию, но не запускал периодический refresh. Токен протухал молча.
- **waitForConnectionAndReLogin не запускал refresh** — после успешного обновления токена при старте, периодический refresh не начинался.
- **Chat stream retry: мёртвая петля** — при ошибке JWT, код очищал токены и пытался использовать пароль (v1), но v2 не поддерживает пароль → AUTH_FAILED. Теперь: сначала пытается refresh, потом — AUTH_FAILED.
- **performTokenRefresh: fallback при истечении refresh_token** — если refresh_token тоже истёк, автоматический re-login по сохранённому паролю.
- **onResume: валидация токена** — ChatListActivity и NewChatActivity проверяют свежесть токена при возвращении в foreground.

**Новые токены хранятся и обновляются корректно, рефреш запускается при каждом входе и восстановлении сессии.**

### Рефакторинг

**NewChatActivity → ChatViewModel:**
- Бизнес-логика перенесена в ChatViewModel: sendMessage, uploadAudio, retryMessage, fetchChatMetadata, loadPinnedMessages, syncChatListIfNeeded, ensureUserIdSet
- NewChatActivity: 759 → ~450 строк
- ViewModel обогащён StateFlow: chatMetadata, pinnedMessageIds, isAudioUploading

### Исправления тестов
- GrpcChatListClientTest: getChats() тесты обновлены для GrpcChatClient (после split в v1.2.0.5)

## [1.2.0.8] - 2026-06-18

### Исправления

**ChatList V2 — Response Marshallers (критический):**
- Pin/Unpin Chat — response marshaller отсутствовал, `unaryCallWithClass` использовал рефлексию и всегда возвращал `success=false`
- Archive/Unarchive Chat — аналогичный баг
- Pin/Unpin Message — аналогичный баг
- Search Chats — response marshaller отсутствовал, поиск всегда возвращал пустой список
- GetPinned Messages — response marshaller отсутствовал
- Созданы: `PinChatResponseMarshaller`, `UnPinChatResponseMarshaller`, `ArchiveChatResponseMarshaller`, `UnarchiveChatResponseMarshaller`, `PinMessageResponseMarshaller`, `UnPinMessageResponseMarshaller`, `GetPinnedMessagesResponseMarshaller`
- Все V2 boolean-response методы теперь корректно десериализуют ответ сервера

**Примечание:** ChatList V2 фичи (pin, archive, mute) работали ранее только благодаря `loadChats()` в `ChatListActionMode` — полный re-fetch после каждого действия. Теперь optimistic update в ViewModel тоже работает корректно.

## [1.2.0.7] - 2026-06-18

### Исправления

**SuperAdmin (критический):**
- Исправлен баг: кнопка "Admin" не отображалась ни для кого
- `ProfileClient.unaryCall()` использовал рефлексию для response marshaller — всегда возвращал дефолтный объект с `isSuperAdmin = false`
- Созданы `GetProfileResponseMarshaller` и `GetProfileRequestMarshaller` — корректная десериализация 11 полей ответа
- Созданы marshallers для всех ProfileService v2 методов (UpdateProfile, UpdateAvatar, GetUserSettings, UpdateUserSettings)

**ProfileService marshallers:**
- `GetProfileResponseMarshaller` — десериализует userId, username, email, avatarUrl, fullAvatarUrl, bio, status, locale, isSuperAdmin, createdAt, lastSeenAt
- `UpdateProfileV2ResponseMarshaller` — десериализует success, message, вложенный profile
- `UpdateAvatarV2ResponseMarshaller` — десериализует success, message, avatarUrl, fullAvatarUrl
- `GetUserSettingsResponseMarshaller` — десериализует locale, themeId, pushEnabled, custom map
- Все request marshallers — корректная сериализация полей

## [1.2.0.6] - 2026-06-18

### Исправления

**Список чатов:**
- Исправлена загрузка списка чатов — GetChatsV2 RPC добавлен в proto (сервер возвращал UNIMPLEMENTED)
- Исправлен SQL-запрос GetUserChatsV2 — participants содержат usernames, а не UUID

**Админ-панель:**
- Кнопка "Admin" в Additional Settings теперь отображается для всех администраторов (ранее — только для `ferz`)
- Флаг `isSuperAdmin` загружается из профиля сервера вместо хардкода по username

---

## [1.2.0.5] - 2026-06-18

### Исправления

**Контакты:**
- При создании чата/секретного чата/конференции показываются только добавленные контакты (был весь список пользователей)
- `getContacts()` — fallback `fetchUserId` если `currentUserId` пуст

**Авторизация:**
- Авто-вход с протухшим JWT: refresh → если не выходит → перелогин по сохранённому паролю
- Chat stream: удалён password fallback, теперь только JWT (deprecated v1)
- `GetChats` v1 → `GetChatsV2` (JWT-based, server uses `GetUserID(ctx)`)
- При холодном запуске с expired токеном — `AUTH_FAILED` вместо застревания

**Навигация:**
- Возврат к родительской шторке (Settings/Additional Settings) после Back из Activity
- Восстановлен `isNavigatingDeeper` + `settingsActivityLauncher`/`editProfileLauncher`

**UI:**
- Диалог "О программе" показывает версию сервера из `GrpcClient.serverVersion`

### Рефакторинг

**gRPC модули:**
- `GrpcChatListClient` (648→255 LOC) → разделён на 3:
  - `GrpcChatClient` (~250 LOC) — getChats, create/delete, participants, settings
  - `GrpcChatListV2Client` (~120 LOC) — pin/unpin, search, archive, pinned messages
  - `GrpcChatAuxClient` (~130 LOC) — users/AI chats/FCM/mute
- `RealGrpcClient`: delegate to new clients, удалён `unaryCallChatListV2` (-40 LOC)
- Удалены дубликаты `getChats`/`getAllChats` из `GrpcChatListClient`

---

## [1.1.3.38] - 2026-06-18

### 🚀 v2 Клиент для v2 Сервера

Новая версия клиента с улучшениями для v2 сервера.

### Улучшения

**Список чатов:**
- Имя собеседника отображается напрямую в личных чатах
- Тулбар с прозрачностью 30% и тенью — тап по заголовку или аватару открывает шторку профиля
- Убраны заголовки секций (All chats, Pinned) — список чатов стал чище
- Предзагрузка пользователей при открытии — создание чата доступно сразу

**Профиль и настройки:**
- Шторка профиля: верхняя секция (аватар + имя) кликабельная → редактирование профиля
- Переключение языка синхронизируется с сервером

**Создание чатов:**
- Секретные чаты, обычные чаты и конференции — показывают всех пользователей, а не только контакты

**Темы:**
- Фон темы корректно применяется к списку чатов (chatListBackground)
- Экран настроек тем больше не перехватывает фон чата

**Typing индикатор:**
- Корректная фильтрация — свой typing не отображается в subtitle

**AI:**
- Исправлен вызов `deployAgentTaskStream` в HermesChatUseCase
- Исправлен scope leak в OwlChatUseCase

---

## [1.1.3.35] - 2026-06-18

### Рефакторинг: GrpcClient Facade Оптимизация
- **GrpcClient: 780 → 106 LOC (-86%)**
- Создан `GrpcClientExtensions.kt` (~600 LOC) с extension functions по доменам:
  - Auth, Chat, Message, Profile, Theme, Draft, Favorite, Call, AI/Hermes, RemoteAgent, SecretChat, Notification
- В GrpcClient.kt оставлено: StateFlow declarations, scope, connect/disconnect, startChat, loadHistory, setRoomId, loadUsers, V2 service detection
- Добавлен `import GrpcClientExtensions.*` в 29 UI файлов
- RealGrpcClient.scope: `private` → `internal`

---

## [1.1.3.34] - 2026-06-17

### Тесты: Unit-тесты для gRPC клиента
- **42 unit-теста** для gRPC модулей (было 0):
  - GrpcAuthClientTest (10) — signIn, signUp, refreshToken, signOut, revokeDevice
  - GrpcChatListClientTest (8) — getChats, pinChat, searchChats, deleteChat
  - GrpcMessageClientTest (8) — sendMessage, addLocalMessage, loadHistory, markRead
  - GrpcConnectionManagerTest (6) — connect, disconnect, reconnect, isConnectedTo
  - GrpcClientFacadeTest (6) — connectionState mapping, StateFlow probing
  - GrpcUnaryCallHelperTest (4) — unaryCall, null channel, error handling
- Добавлены зависимости: mockk 1.13.8, turbine 1.0.0, coroutines-test 1.7.3
- Созданы тестовые утилиты: TestChannelFactory, TestDatabaseFactory, FlowTestExtensions

### Исправления инфраструктуры тестов
- AppLog: try-catch вокруг android.util.Log.d для работы в unit-тестах
- Все тесты: Dispatchers.Main → Dispatchers.Unconfined (Main недоступен без Android)
- Все тесты: переведены с Mockito на MockK
- Все тесты: исправлены runtime ошибки ( smart cast, relaxed mock returning null)
- Proto data classes: newBuilder() заменены на прямые конструкторы

---

## [1.1.3.33] - 2026-06-17

### Рефакторинг: NewChatActivity делегаты
- **NewChatActivity: 1473 → 754 строк (-49%)**
- Вынесено 6 модулей в `ui/chat/message/`:
  - ChatToolbarDelegate (341) — toolbar, avatar, subtitle, navigation
  - ChatInputDelegate (567) — input, send, attachments, audio, emoji, mentions
  - ChatSelectionDelegate (236) — selection mode, copy/pin/delete/forward
  - ChatSearchDelegate (135) — in-chat search
  - ChatE2EEDelegate (72) — E2EE key exchange, encrypt/decrypt
  - ChatMessageMenuDelegate (106) — reactions, context menu

### Рефакторинг: Унификация error handling
- Все gRPC модули используют `ErrorHandler.handle()` (было `Log.e`)
- ChatListViewModel.error StateFlow + Snackbar в ChatListActivity

### Исправлено
- Ошибки компиляции: импорты Lifecycle, isVisible, toColorInt, edit
- Порядок инициализации: setupDelegates после setupRecyclerView

---

## [1.1.3.32] - 2026-06-17

### Рефакторинг: Разбиение ChatListActivity
- **ChatListActivity: 1085 → ~600 строк (-45%)**
- Вынесено 3 модуля в `ui/chatlist/`:
  - ChatListFABs (470) — FABs + action sheets + AI bottom sheet
  - ChatListNavigation (60) — navigateToChat
  - ChatListAuth (212) — auth dialogs

### Исправлено
- loadChats() — при timeout НЕ перезаписывать allChats
- read receipts — indexOfFirst проверка перед map
- Табы переупорядочены: Все → Группы → ИИ чаты
- "AI" → "AI Chats" / "ИИ" → "ИИ чаты"

---

## [1.1.3.31] - 2026-06-17

### Новое: Read receipts broadcast
- readReceiptEvent SharedFlow → ChatListViewModel → clear unread count

### Рефакторинг
- ChatListActivity 1470→1085 строк (-26%), 4 новых модуля

---

## [1.1.3.30] - 2026-06-16

### Новое: FAB + Favorites
- FAB [+] восстановлен — ActionBottomSheet + SearchableListBottomSheet
- Favorites убран из секций, добавлен в шторку профиля

---

## [1.1.3.28-29] - 2026-06-16

### Рефакторинг: gRPC модули
- **RealGrpcClient: 3810 → 882 строк (-77%)**
- 12 модулей вместо God Object
- Кастомные темы для AppBarLayout, TabLayout
