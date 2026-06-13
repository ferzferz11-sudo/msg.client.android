# Lavender Messenger — Android Changelog

## [1.1.3.9] - 2026-06-13

### Новое: Espresso-тесты
- **ChatListActivityTest** — 18 тестов: toolbar, FABs, search, navigation
- **RemoteAgentActivityTest** — 12 тестов: toolbar, status bar, task types, agent controls
- **ChatWidgetTest** — структура виджета, input/send behavior
- **EmptyChatTextTest** — проверка текста пустых чатов

### Новое: Мультиязычность (i18n)
- Вынесено 100+ хардкодных русских строк в strings.xml (en + ru)
- AI Bottom Sheet: секции, кнопки, popup menu — полностью локализованы
- RemoteAgentActivity: task types, status, sender names, hints
- RemoteAgentSettingsActivity: connect, token management, status
- RemoteAgentService: notification строки
- ChatListActivity: session, rename, password reset, system notification
- ConferenceLobbyActivity: participants count
- OwlSettingsActivity: model options, key info
- OwlChatActivity: commands, errors, typing indicator
- LogViewerActivity: title, copy/clear notifications
- HermesChatActivity: agent switching, help, status
- AgentSettingsActivity/BottomSheet: CRUD operations, delete dialog
- AgentListActivity: delete dialog
- ChatMessageAdapter: typing indicator
- CommandBottomSheet: header
- Добавлено 43 новых строки в values/strings.xml + values-ru/strings.xml

### Исправления
- **Empty chat text** — `favorites_description` показывался для ВСЕХ пустых чатов
  - Теперь: Favorites → "Personal storage" / "Личное хранилище"
  - Обычные пустые чаты → "No messages" / "Нет сообщений"
- **RemoteAgentActivity crash** — NPE при инициализации taskTypes до создания Activity
- **Форматирование строк** — исправлены непозиционные форматтеры (%s/%d → %1$s/%2$d) в 8 строках
- **Сборка** — исправлены ошибки компиляции getString() в Adapter, BottomSheet, ViewModel

---

## [1.1.3.8] - 2026-06-13

### Espresso Testing — подготовка ID
- Все XML ID переименованы в snake_case с префиксами (btn_, et_, tv_, iv_, rv_, srl_, fl_, ll_, pb_, fab_, cv_, til_, actv_, barrier_)
- Динамические View в Kotlin используют `View.generateViewId()`
- Код обновлён для совместимости с новыми ID (ChatAdapter, MessageAdapter, ChatMessageAdapter, ChatWidget, ThemeApplier, ForwardChatAdapter, SuperAdminAdapter, AIBottomSheet, OwlSettingsActivity, TokenDialog, EditProfileActivity)
- Все строки на двух языках: values/strings.xml (en) + values-ru/strings.xml (ru), 560 строк каждое
- PATTERNS.md содержит таблицу префиксов и примеры Espresso-тестов

### AI Bottom Sheet улучшения
- Названия новых чатов: "Лава ИИ #N" / "Агент OWL #N" с автоматической нумерацией
- Удаление чата на ИИ шторке — удаление на фоне с перестройкой UI после закрытия popup
- Заголовок шторки: "AI Сервисы (в разработке)"
- Убран typeLabel из элементов чата (тип чата не нужен, чаты разбиты по секциям)

### Исправления
- **Favorites** — "Нет сообщений" → "Личное хранилище" (R.string.favorites_description)
- **EditProfileActivity** — исправлен фатал `avatarImageView` → `ivProfileAvatar`
- **AIBottomSheet** — исправлены ID `tvChatName` → `chatName`, `tvChatType` → `chatTypeLabel`
- **ChatListActivity** — исправлены ID `swipeRefreshLayout` → `srlChatList`, `actionSettings` → `ivActionSettings`, `toolbarSubtitle` → `tvToolbarSubtitle`

### Известные проблемы
- Камера: Toast "Could not open camera" при вызове из шторки (возможно отсутствие камеры на устройстве)

---

## [1.1.3.8] - 2026-06-13 (предыдущая секция — Remote Agent UI/UX)

**Тулбар в чате и настройках агента:**
- Единый стиль тулбара с закругленными нижними углами (`toolbar_background`)
- Фон тулбара из primary color темы, текст и иконки из `colorOnPrimary`
- Высота `custom_toolbar_height` — единообразно с другими экранами
- `ThemeUi.bind()` + `ThemeApplier` полностью стилизуют тулбар

**Статус-бар в чате агента:**
- Заменён `ConstraintLayout` на `LinearLayout` — кнопки Start/Stop больше не уезжают за экран
- Фон статус-бара из `surfaceColor` темы
- Индикатор подключения — программный `GradientDrawable` (надёжнее `setTint`)
- Текст статуса из `textPrimaryColor` темы

**Поля ввода в настройках (Шлюз):**
- Все поля ввода (`etSshHost`, `etSshPort`, `etSshUser`, `etSshPassword`, `etServerHost`, `etServerPort`, `etLocalPort`) теперь получают цвета из темы
- Текст и hint из `textPrimaryColor` / `hintColor`
- Лейблы статуса (`tvGatewayStatus`, `tvTunnelAddress`) из `textPrimaryColor`

**Исправления:**
- `ChatAdapter.filter()` — `notifyItemRangeChanged` заменён на `diffResult.dispatchUpdatesTo` с offset +1 для Favorites → больше не крашится при фильтрации
- `EditProfileActivity` — кнопка «Сохранить» появляется даже если профиль не загрузился с сервера
- Убран ручной `setNavigationIconTint` из `RemoteAgentActivity.setupToolbar()` — `ThemeApplier` делает это автоматически

### Streaming fix
- **RemoteAgentViewModel** — при финальном `done=True` использует полные буферы из `update.stdout`/`update.stderr` (из TaskResult на сервере), fallback на накопленные чанки
- Сервер теперь отправляет **один** `done=True` с полными данными (исправлен двойной done=True)

### Сервер v1.1.3.8
- `server_remote.go` — исправлен `DeployAgentTaskStream`: done=True отправляется ровно один раз
- `hermes_remote_manager.go` — StreamDone flag + ожидание TaskResult через close(streamCh)
- `server_remote_test.go` — 6 unit-тестов для streaming логики

---

## [1.1.3.7] - 2026-06-13
### Streaming результатов задач агентом
- **DeployAgentTaskStream** — server-side streaming для real-time stdout/stderr/progress
  - `HermesGrpc.kt`: `deployAgentTaskStream()` → `callbackFlow` с `ClientCall.Listener`
  - `GrpcClient.kt`: фасад `deployAgentTaskStream()`
  - `MessengerProto.kt`: `DeployAgentTaskStreamResponseProto` (поля 1-11)
  - `RemoteAgentViewModel.kt`: `sendMessageStreaming()` — Flow.collect → real-time обновление чата
  - `RemoteAgentActivity.kt`: переключение на streaming
- **ErrorHandler** — единый обработчик ошибок
  - `CancellationException` → `AppLog.info()` (не ERROR — это нормальное поведение)
  - `gRPC StatusRuntimeException` → `AppLog.error()` с кодом статуса
  - Network errors, SecurityException → `AppLog.error()`
- **AppLog** добавлен во все catch-блоки с Toast ошибками
- **Bugfix**: "Job was cancelled" тост больше не появляется (CancellationException обрабатывается отдельно)

### P0 Bugfixes
- **"Агент не выбран" исправлен** — `ensureAgentSelected()` в `RemoteAgentViewModel`
  - Автоматическая загрузка агентов с сервера при отправке сообщения
  - Fallback: создание локального агента с именем из настроек шлюза (`sshHost`)
  - Работает для обоих режимов (шлюз + токен)
  - Убрана рекурсия в `sendMessageStreaming()`
- **Status bar в RemoteAgentActivity**
  - `ConstraintLayout` вместо `LinearLayout` — кнопки Start/Stop фиксированы 48dp
  - Текст статуса использует `?android:textColorPrimary` (контрастный на любых темах)
  - Убрана установка цвета текста из темы (был bug: невидимый текст на кастомных темах)
- **"Job was cancelled" тост подавлен**
  - `loadAgents()` не пишет в `_error` (только `AppLog.info`)
  - Убраны дублирующие `refreshAgentStatus()` из `onCreate` и `onStateChanged()`
- **Сборка**: ./gradlew compileDebugKotlin — OK

### Server Changes
- `server_remote.go` — все Remote Agent RPC вынесены из `server_ai.go`
  - `ListRemoteAgents`, `GetRemoteAgentStatus`, `DeployAgentTask`, `DeployAgentTaskStream`
  - `ensureRemoteManager()` — единая проверка зависимостей
  - Graceful degradation: пустой список вместо ошибки если менеджер недоступен
  - Stale detection для агентов (heartbeat > 120с → status="stale")
  - Проверка существования агента перед отправкой задачи
- **Prod сервер обновлён** (v1.1.3.7-stable)

### UI/UX Changes
- **Убран выбор сервера из шторок логина/регистрации** — сервер всегда берётся из CredentialStore (по умолчанию prod)
- **Переключение сервера** — только через ServersActivity ( eingeben server → login sheet на выбранный сервер)
- **ServersActivity** — при выборе сервера открывается шторка логина напрямую на этот сервер
- **Скрыт dragHandle** в шторках логина/регистрации (белая полоска убрана)
- **Fallback на prod** (`13.140.25.249:50051`) если CredentialStore пуст

### Bugfixes
- **Room DB migration 8→9** — добавлены все недостающие столбцы с `try/catch` для совместимости со старыми БД
- **androidViewGroup parent** — исправлен `instanceof` → Kotlin `is` каст для скрытия контейнера spinner'а

## [1.1.3.6] - 2026-06-13
### Remote Agent — New Settings UI + Chat Improvements
- **Tab-based Settings** — вкладки «Шлюз» (SSH туннель) и «Токен» (JWT + start/stop)
- **Инструкции** для обоих режимов подключения
- **Статус подключения** на тулбаре чата (шлюз IP / токен)
- **Команды агента** — быстрые команды (help, status, logs, deploy, restart, git, docker, ps, df, uptime)
- **Авто-прокрутка чата** при новых сообщениях

## [1.1.3.5] - 2026-06-13
### Remote Agent — Persistent Background Connection
- **Foreground Service** — `RemoteAgentService.kt` с SSH туннелем + gRPC
- **Singleton Manager** — `RemoteAgentManager.kt` для привязки UI к сервису
- **START_STICKY** — перезапускается системой, notification со статусом
- **AndroidManifest.xml** — RemoteAgentService + FOREGROUND_SERVICE_CONNECTED_DEVICE

## [1.1.3.4] - 2026-06-13
### Remote Agent — Hermes Gateway (SSH Tunnel)
- **HermesGatewayManager.kt** — управление SSH туннелем через JSch
- **UI** — секция "Подключение через шлюз" в настройках
- **MessengerProto.kt** — tunnel_mode поля (6-13)
- **JSch зависимость** — `com.jcraft:jsch:0.1.55`

## [1.1.3.3] - 2026-06-12
### Remote Agent — Task Results + Script Path Fix
- **Task results** — stdout/stderr/exitCode/durationMs в чате
- **Script path** — `/root/msg.remote.agent/` (было `/root/msg/hermes-agent/`)
- **Proto** — `DeployAgentTaskResponseProto` расширен

## [1.1.3.2] - 2026-06-12
### Remote Agent — Token Management + UI
- **Генерация JWT токенов** через `hermes_agent.HermesAgentService/GenerateAgentToken`
- **Список токенов** — отображается сразу после генерации
- **Копирование токена/команды**, отзыв токена
- **Start/Stop агента** через RPC

## [1.1.3.1] - 2026-06-12
### AI шторка — исправления
- **Новый чат с оркестратором отображается сразу**
- **Удаление чата не ломает список**
- **Убран автоскролл** на последнее сообщение
