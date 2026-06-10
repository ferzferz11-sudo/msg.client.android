# Lavender Messenger — Android Changelog

## [1.1.2.3] - 2026-06-09
### AI Chat Refactor — Unified AI Chat
- **Новые proto классы:** AIChatRequestProto, AIChatResponseProto, AIChatMessageProto, AIChatSettingsProto + request/response wrappers
- **AiChatGrpc.kt:** новый файл — chatWithAI (streaming), getAIChatHistory, getAIChatSettings, updateAIChatSettings
- **GrpcClient.kt:** добавлены aiChatResponses, aiChatTyping SharedFlows + facade методы
- **Unified streaming:** один chatWithAI RPC вместо отдельных chatWithOwl/chatWithOrchestrator
- **version.txt:** 1.1.2.3
- **compileDebugKotlin:** BUILD SUCCESSFUL
- **Известные проблемы:** Hermes история не загружается, счётчик запросов off-by-one

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
