# Lavender Messenger — Android Changelog

## [1.1.1.5] - 2026-07-18
### OWL Settings
- **OwlSettingsActivity:** экран настроек OWL AI (API key input, model selector dropdown)
- **activity_owl_settings.xml:** layout с MaterialToolbar, TextInput, AutoCompleteTextView, save button
- **MessengerProto.kt:** добавлены `UpdateOwlSettingsRequestProto`, `UpdateOwlSettingsResponseProto`, `GetOwlSettingsRequestProto`, `GetOwlSettingsResponseProto`
- **НЕ ДОДЕЛАНО:** getOwlSettings()/updateOwlSettings() в OwlGrpc.kt, регистрация в AndroidManifest, подключение из AIBottomSheet → будет в v1.1.1.6

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
