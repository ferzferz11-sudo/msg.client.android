# Lavender Messenger — Android Changelog

## [1.1.3.5] - 2026-06-13
### Remote Agent — Persistent Background Connection
- **Foreground Service** — `RemoteAgentService.kt` для фонового подключения
  - Управляет SSH туннелем через HermesGatewayManager
  - Держит gRPC подключение при переходе между Activity
  - Notification со статусом (подключено/отключено)
  - START_STICKY — перезапускается системой после убийства
  - Кнопка "Отключить" в notification
- **Singleton Manager** — `RemoteAgentManager.kt` для привязки UI к сервису
  - bindService()/unbindService() из Activity
  - RemoteAgentStateListener — callback для изменения состояния
  - isConnected()/isTunnelActive()/getTunnelAddress()
  - sendTask() — отправка задач через сервис
- **Activity binding** — RemoteAgentSettingsActivity и RemoteAgentActivity
  - Привязка к сервису через ServiceConnection
  - onResume → bind, onPause → unbind (сервис продолжает работать)
  - Статус подключения обновляется через RemoteAgentStateListener
- **AndroidManifest.xml** — добавлен RemoteAgentService + FOREGROUND_SERVICE_CONNECTED_DEVICE
- **Исправлено**: удалён невалидный `import HermesGrpc` из RemoteAgentService.kt
- **Исправлено**: варнинг `stopForeground(true)` deprecated — добавлен @Suppress("DEPRECATION")

## [1.1.3.4] - 2026-06-13
### Remote Agent — Hermes Gateway (SSH Tunnel)
- **HermesGatewayManager.kt** — управление SSH туннелем через JSch
- **RemoteAgentSettingsActivity.kt** — UI секция "Подключение через шлюз"
- **activity_remote_agent_settings.xml** — layout с полями SSH хоста, портов, кнопками
- **MessengerProto.kt** — tunnel_mode поля в DeployAgentTaskRequestProto
- **HermesGrpc.kt** — сериализация tunnel_mode (поля 6-13)
- **GrpcClient.kt** — обёртка с tunnel параметрами
- **RemoteAgentViewModel.kt** — передача tunnel_mode при отправке задачи
- **JSch зависимость** — `com.jcraft:jsch:0.1.55`
- **SharedPreferences** — сохранение настроек туннеля
- **Понятные ошибки** — SSH alias vs IP, auth failed, timeout, port in use

## [1.1.3.3] - 2026-06-12
### Remote Agent — Task Results + Script Path Fix
- **Task results in chat** — `DeployAgentTask` now returns stdout/stderr/exitCode/durationMs
  - Android displays task output directly in RemoteAgent chat
  - Error messages show exit code
- **Script path updated** — agent script path changed from `/root/msg/hermes-agent/` to `/root/msg.remote.agent/`
  - Configurable via `PREF_AGENT_SCRIPT_PATH` in lavender_prefs
- **Proto updated** — `DeployAgentTaskResponseProto` extended with stdout, stderr, exitCode fields

## [1.1.3.2] - 2026-06-12
### Remote Agent — Token Management + UI
- **Генерация JWT токенов** — работает через `hermes_agent.HermesAgentService/GenerateAgentToken`
- **Список токенов** — отображается сразу после генерации, без задержки
- **Копирование токена/команды** — кнопки в каждом элементе списка токенов
- **Отзыв токена** — кнопка "Отозвать" с подтверждением
- **Запуск/остановка агента** — через `StartAgent`/`StopAgent` RPC
- **Статус подключения** — зелёный индикатор при запущенном агенте, белый текст для остальных статусов
- **Персистентность** — выбранный агент сохраняется между сессиями
- **Исправлено**: диалог токена не закрывается при копировании в буфер
- **Исправлено**: ошибки сервера переведены на русский ("Агент не найден", "Агент уже остановлен")

## [1.1.3.1] - 2026-06-12
### AI шторка — исправления
- **Новый чат с оркестратором отображается сразу** — не нужно переоткрывать шторку. Root cause: `refreshAiChats()` был асинхронным, данные не успевали прийти. Fix: `refreshAiChatsAwait()` через `suspendCancellableCoroutine`.
- **Удаление чата не ломает список** — при удалении чата оркестратора чат агента больше не исчезал. Root cause: `refreshAiChats()` в `onDeleteChat` очищал список до ответа сервера. Fix: локальное удаление без сетевого запроса + `AIBottomSheet.removeChat()`.

### Автоскролл — удалён
- **Убран автоскролл на последнее сообщение** во всех чатах (Hermes, OWL, обычные). Позиция скролла сохраняется при возврате в чат — больше никаких неожиданных перескоков при действиях меню, typing indicator и новых сообщениях.
- Удалён метод `ChatWidget.scrollToBottom()`.

## [1.1.2.9] - 2026-06-11
### Исправления
- **OWL: сообщения пользователя не отображались до ответа агента** — typing indicator мутировал adapter.currentList напрямую, ломая DiffUtil. Теперь typing placeholder — часть единого списка сообщений в ViewModel.
- **Hermes: история чата не сохранялась в локальную БД** — сообщения хранились только в памяти и терялись при перезапуске. Теперь сохраняются в Room DB (пользовательские сообщения при отправке, ответы агентов при завершении стрима, история при загрузке с сервера).
- **Hermes: история загружается из локальной БД сначала** — быстрый показ при открытии, затем обновление с сервера.

## [1.1.2.8] - 2026-06-11
### AI Чаты — улучшения
- **Убран прелоадер** во время ожидания ответа агента — достаточно typing indicator
- **Таймаут стрима 120 сек** с сбросом при каждом сообщении — показывает ошибку на русском при таймауте
- **Шторка AI реорганизована**: чаты разделены по типам — Hermes чаты в секции "Лава ИИ", OWL чаты в секции "OWL агент"

### Favorites — исправлено
- **Favorites отображается сразу при входе** — не нужно создавать чат чтобы увидеть Избранное
- Показывается даже при недоступном сервере (offline-first)
- Добавлен fallback при ошибке загрузки чатов

### Changelog
- **Цвета текста** из ThemeStore вместо resolveColorAttr — читаемый текст на кастомных тёмных темах
- **Порядок загрузки**: сначала GitHub API, fallback только через 3с при отсутствии сети

### Мелкие исправления
- Убран deprecated `overridePendingTransition` в SplashActivity
- Убран дебаг-логгинг из production кода

## [1.1.2.7] - 2026-06-11
### SplashActivity — улучшения
- **Расстояние логотип→текст**: увеличено с 60px до 90dp
- **SplashLoadingActivity**: новый оверлей загрузки (логотип + анимация) для логина/регистрации
- **Login/Register**: показывается SplashLoadingActivity во время запросов авторизации

### Удаление онбординга
- Удалён экран приветствия (welcomeContainer) с логотипом и описанием
- Удалены подсказки профиля и FAB (onboardingProfileBubble, onboardingFabBubble)
- Удалена установка `first_login_`/`onboarding_completed_` prefs
- Удалена темизация онбординга из ThemeApplier
- Удалены строки welcome_new_user_title/description, onboarding_profile_hint/fab_hint (en + ru)
- Список чатов теперь показывается сразу при входе

### Чекбокс "Создать чат" при добавлении контакта
- Добавлен CheckBox "Сразу создать личный чат" в шторку добавления контакта
- Чекбокс включён по умолчанию
- При включении: создаётся прямой чат → переход в NewChatActivity через SplashLoadingActivity
- Работает и в ChatListActivity и в ContactsActivity

### Исправления
- **Crash при выборе чатов с пустым списком**: `selectedPositions.clear()` в `setChats()`, `getSelectedChats()` защищён от IndexOutOfBoundsException
- **getSelectedChats offset**: исправлен маппинг позиций с учётом Favorites на позиции 0
- **loadingContainer удалён**: прелоадер "Загрузка" в центре экрана больше не блокирует интерфейс
- **statusBarColor deprecation**: убран deprecated вызов в ThemeApplier

### Известные проблемы
- **Favorites при пустом списке**: при входе после очистки памяти Favorites может не отображаться если нет созданных чатов (см. PITFALLS.md)

## [1.1.2.6] - 2026-06-10
### ChangelogActivity — Bundled Changelog + GitHub Links
- **Bundled changelog**: добавлен `app/src/main/assets/changelog_bundled.txt` — встроенный ченджлог показывается мгновенно без сети
- **Новая логика загрузки**: bundled (мгновенно) → GitHub API → server fallback
- **Ссылки на полные CHANGELOG.md**: кнопки «Ченджлог сервера» и «Ченджлог клиента» ведут на GitHub
- **changelog.txt удалён** из проекта и из деплоя на сервер
- **strings.xml**: добавлены `changelog_server_full`, `changelog_client_full`, `changelog_full_description`, `changelog_loading_from_cache`
- **activity_changelog.xml**: секция с ссылками на GitHub в fallback-виде
- **ru strings**: локализация новых строк
- **Исправление цветов**: fallback теперь использует цвета из `ThemeStore` программно (не XML-атрибуты)
- **deploy_android.sh обновлён**: убрана загрузка changelog.txt, добавлен комментарий про bundled
- **Документация обновлена**: INDEX.md, PITFALLS.md, TASKS.md
- compileDebugKotlin ✅

## [1.1.2.5] - 2026-06-10
### ChangelogActivity — Theme Fix
- **Белый экран**: добавлен ThemeUi.bind(this, "") в onCreate — кастомные темы применяются корректно
- **Splash**: логотип + «Лава» с анимацией пока данные грузятся
- **Fallback**: если GitHub API не ответил — загружает changelog.txt с сервера
- ChangelogActivity: ThemeApplier.apply вызывается синхронно до setContentView

## [1.1.2.3] - 2026-06-09
### AI Chat Refactor — Unified AI Chat
- **Новые proto классы:** AIChatRequestProto, AIChatResponseProto, AIChatMessageProto, AIChatSettingsProto + request/response wrappers
- **AiChatGrpc.kt:** новый файл — chatWithAI (streaming), getAIChatHistory, getAIChatSettings, updateAIChatSettings
- **GrpcClient.kt:** добавлены aiChatResponses, aiChatTyping SharedFlows + facade методы
- **Unified streaming:** один chatWithAI RPC вместо отдельных chatWithOwl/chatWithOrchestrator

## [1.1.1.15] - 2026-06-09
### Free Models + Custom Model Input
- **OwlSettingsActivity:** бесплатные модели загружаются с сервера (GetFreeModels RPC) вместо хардкода
- **OwlSettingsActivity:** без своего ключа — только выпадающий список бесплатных моделей, OWL Alpha первая
- **OwlSettingsActivity:** с ключом — бесплатные модели + опция «Своя модель (ввести вручную)»
- **OwlSettingsActivity:** поле ввода собственной модели скрыто без ключа, показывается подсказка «Доступно только с заполненным полем API OpenRouter»
- **OwlSettingsActivity:** если модель не из бесплатного списка — автоматически выбирается «Своя модель»
- **OwlGrpc.kt:** парсер GetOwlSettingsResponse теперь читает free_models (field 4)
- **OwlGrpc.kt:** новая функция getFreeModels() — unary RPC
- **MessengerProto.kt:** добавлены FreeModelInfoProto, GetFreeModelsRequestProto, GetFreeModelsResponseProto
- **activity_owl_settings.xml:** добавлены modelCustomInput (текстовое поле) и modelCustomHint (подсказка)

### Favorites Flickering Fix
- **ChatAdapter:** updateAvatarCache() теперь использует offset +1 при наличии Favorites (не перерисовывает position 0)
- **ChatListActivity:** startSync() всегда включает Favorites в список, передаваемый в setChats()
- **ChatListActivity:** добавлен метод hasFavorites() в ChatAdapter для безопасной проверки

## [1.1.1.9] - 2026-06-08
### Graceful Reconnect
- **ConnectionStatus:** добавлено новое состояние `RECONNECTING` — промежуточный статус между READY и FAILED
- **RealGrpcClient.connect():** при повторном подключении после разрыва показывает RECONNECTING вместо CONNECTING
- **RealGrpcClient:** экспоненциальный backoff (5s → 60s) для reconnect с автоматическим сбросом при успехе
- **RealGrpcClient:** keepAliveTime уменьшен до 10s, keepAliveTimeout до 5s — быстрее обнаруживаем разрыв
- **subscribeNotifications:** автоматический retry с exponential backoff (3s → 30s) — уведомления больше не теряются при разрыве
- **chatWithOwl:** автоматический retry (до 10 попыток, 3s → 30s backoff) — OWL стрим переживает кратковременные разрывы
- **chatWithOrchestrator:** автоматический retry (до 10 попыток, 3s → 30s backoff) — Hermes стрим переживает кратковременные разрывы
- **onError stream:** FAILED заменён на RECONNECTING, меньше retry (50 вместо 100), быстрый backoff (3s → 30s)
- **disconnect():** отменяет reconnect job и сбрасывает backoff

## [1.1.1.8] - 2026-06-08
### AI Chats Separation
- **AIBottomSheet:** добавлен selection mode (долгий тап → режим выбора с тулбаром удаления/переименования)
- **ChatListActivity:** refreshAiChats() теперь загружает через GetAIChats RPC вместо фильтрации из getAllChats
- **ChatListActivity:** showAIActionSheet() — подключены кнопки удаления и переименования AI-чатов
- **GrpcClient:** добавлены getAIChats(), renameAIChat(), deleteChat() overload с userId
- **RealGrpcClient:** новые RPC методы + marshallers для GetAIChats, RenameAIChat
- **AIChatInfo:** новый data class для доменной модели AI-чатов
- **MessengerProto.kt:** добавлены GetAIChatsRequest/Response, AIChatInfo, RenameAIChatRequest/Response proto классы
- **widget_ai_bottom_sheet.xml:** добавлен selection toolbar с кнопками удаления и переименования

## [1.1.1.7] - 2026-07-18
### Notification Badge
- **ServerNotificationProto:** добавлено поле isRead: Boolean
- **OwlGrpc.kt:** getUnreadCount() — новый unary RPC для получения количества непрочитанных
- **OwlGrpc.kt:** парсеры getNotificationHistory и subscribeNotifications теперь читают isRead (field 7)
- **GrpcClient.kt:** getUnreadCount() — новый метод
- **NotificationAdapter:** bold title + accent background для непрочитанных; клик → mark as read
- **NotificationActivity:** отмечает все загруженные уведомления как прочитанные при открытии
- **SheetAction:** добавлено поле badge: Int для отображения счётчика
- **AIBottomSheet + ActionBottomSheet:** показывают badge (красный кружок с числом)
- **ChatListActivity:** unreadNotifCount + refreshUnreadCount() + badge на пункте "Уведомления"
- **Layouts:** widget_action_item.xml — добавлен actionBadge TextView
- **Drawables:** badge_background.xml — красный круг для badge
- **Colors:** notification_unread_bg — полупрозрачный accent для непрочитанных

## [1.1.1.5] - 2026-07-18
### OWL Settings
- **OwlSettingsActivity:** экран настроек OWL AI (API key input, model selector dropdown)
- **activity_owl_settings.xml:** layout с MaterialToolbar, TextInput, AutoCompleteTextView, save button
- **MessengerProto.kt:** добавлены proto классы для OWL settings
- **OwlGrpc.kt:** getOwlSettings() и updateOwlSettings() unary RPC методы
- **AndroidManifest.xml:** регистрация OwlSettingsActivity
- **ChatListActivity.kt:** AIBottomSheet → OwlSettingsActivity (вместо Toast)

## [1.1.1.4] - 2026-07-17
### [AI] кнопка в списке чатов
- **[AI] FAB:** добавлена кнопка [AI] рядом с [+] в ChatListActivity
- **AIBottomSheet:** новая шторка с группировкой AI-сервисов (Оркестратор / OWL) и разделителем
- **Группа Оркестратор:** Lava AI → HermesChatActivity, Агенты → AgentListActivity, Уведомления → NotificationActivity
- **Группа OWL:** OWL AI → OwlChatActivity, Настройки OWL → (TODO)
- **AI-пункты перенесены** из [+] шторки в [AI] шторку
- **Новые файлы:** AIBottomSheet.kt, widget_ai_bottom_sheet.xml, widget_section_header.xml, widget_section_divider.xml
- **HermesChatActivity:** использует userId (UUID) из сессии вместо username

## [1.1.1.3] - 2026-07-17
### Server Notifications UI
- **NotificationActivity:** экран просмотра серверных уведомлений с историей и real-time подпиской
- **NotificationAdapter:** адаптер с иконками по типу (🚀 deploy, ✅ deploy_done, ❌ deploy_error, 🔄 restart, ⚠️ warning, ℹ️ info)
- **OwlGrpc.kt:** subscribeNotifications (server streaming), getNotificationHistory, markNotificationsRead
- **OwlGrpc.kt:** serverNotifications SharedFlow для real-time уведомлений
- **ChatListActivity:** кнопка "Уведомления" в ActionBottomSheet
- **Layouts:** activity_notification.xml, item_notification.xml
- **Drawables:** ic_notifications.xml, ic_arrow_back.xml, circle_background.xml

## [1.1.1.2] - 2026-07-17
### OWL Bot — исправления и разделение архитектуры
- **Полное разделение OWL/Hermes:** создан отдельный `OwlGrpc.kt` — все OWL-методы вынесены из `HermesGrpc.kt`
- **OwlGrpc.kt:** `processBotCommand`, `getBotCommands`, `getOWLStatus`, `chatWithOwl` — все в отдельном файле
- **OwlGrpc.kt:** `OwlRequestMarshaller`/`OwlResponseMarshaller`, `OwlRequestProto`/`OwlResponseProto`
- **HermesGrpc.kt:** очищен от OWL-кода — только orchestrator-методы и agent management
- **Отдельный OWL streaming:** `owlTyping`/`owlResponses` SharedFlows — изолированы от Hermes orchestrator
- **ChatWithOWL gRPC:** реальный вызов серверного streaming RPC вместо пустого stub
- **Streaming chunks:** OwlChatViewModel аккумулирует стриминговые чанки в полный ответ

## [1.1.1.1] - 2026-07-17
### OWL Bot — AI чат с бот-командами
- **OwlChatActivity:** новый экран чата с OWL AI ассистентом
- **OwlChatViewModel:** ViewModel для управления состоянием OWL чата
- **OWL AI кнопка:** добавлена в bottom sheet меню ChatListActivity
- **Slash command detection:** при вводе `/` в поле ввода показываются подсказки команд
- **Bot Commands UI:** диалог со списком доступных команд (/status, /deploy, /logs, /restart, /ai, /help, /version)
- **gRPC:** добавлены `processBotCommand`, `getBotCommands`, `getOWLStatus` методы
- **Proto:** добавлены `BotCommandRequest/Response/Info`, `OWLStatusRequest/Response`, `ServerNotification` классы
- **Server Notifications:** добавлены `SubscribeNotifications`, `GetNotificationHistory`, `MarkNotificationsRead` proto классы
- **OwlMessage:** новая data class для сообщений OWL чата

## [1.1.0.16] - 2026-06-07
### Favorites — исправления
- **Favorites flicker:** Favorites вынесен из RecyclerView в статический view выше списка
- **Favorites styling:** ImageView вместо ShapeableImageView, AppCompatImageView с srcCompat
- **Favorites margins:** стилизация выровнена с элементами списка чатов
- **Duplicate Favorites:** удалён дубликат client-side placeholder (сервер возвращает)

## [1.1.0.15] - 2026-06-05
### Исправления
- **Force reconnect убивал стримы:** `connect(force=true)` при живом канале (READY) вызывал `shutdownNow()`. Теперь если канал живой и адрес совпадает — `force` не убивает канал.
- **Регистрация выбрасывала на рабочий стол:** `startActivity+finish` → `recreate()` (focus race)
- **Очистка кэша при выходе:** убрана очистка при login, добавлена в `logout()` и `deleteProfile()`
- **Logout показывал старые чаты:** `FLAG_ACTIVITY_NEW_TASK|CLEAR_TASK` + синхронная очистка Room DB

## [1.1.0.14] - 2026-06-05
### Hermes сессии в списке чатов
- **Hermes сессии в чатах:** чаты с Hermes AI появляются в списке как `type="hermes"`
- **Room DB:** версия 8→9, миграция с `activeAgentId/agentMode`
- **Навигация:** тап по hermes чату → HermesChatActivity
- **Refresh:** `loadChats(skipCache=true)` при возврате из активити

## [1.1.0.13] - 2026-06-05
### ChatWidget рефакторинг + Mention system
- **HermesChatActivity** переписан на ChatWidget
- **Mention system:** `@` в поле ввода → popup со списком агентов
- **Agent chips:** активный агент подсвечивается primary color
- **ProgressBar** для loading state
- **Typing indicator** с именем агента
- **Два MentionAdapter:** agents/emojis и users/avatars — не объединять

## [1.1.0.12] - 2026-06-04
### Unified Chat Widget
- **widget_chat.xml:** единый layout для группового чата и Hermes
- **ChatMessageAdapter:** единый адаптер с DiffUtil
- **ChatWidget.kt:** ViewBinding обёртка с общим API
- **OWL полностью удалён:** -2425 строк
- **Bottom Sheet:** "Hermes AI" + "Агенты" вместо "Чат с AI"

## [1.1.0.11] - 2026-06-04
### Hermes Orchestrator
- Оркестратор отвечает приветственным сообщением на Android
- CreateHermesSession — создание сессии работает
- ChatWithOrchestrator — стриминг ответов работает
- Proto mismatch fix в CreateHermesSession response

## [1.1.0.10] - 2026-06-04
### Changelog — исправления
- **Белый экран:** убран ThemeUi.bind из ChangelogActivity
- **Таймаут:** уменьшен с 10с до 3с
- **Навигация назад:** при возврате из ChangelogActivity шторка «О программе» открывается снова

## [1.1.0.9] и ранее
- Предыдущие версии...
