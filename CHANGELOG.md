# Changelog

All notable changes to Lavender Messenger (Android client) will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

1.0.2.32
переработана система подключений, чтобы избежать разрывов интернета
переработана система состояний сети

1.0.2.31
исправлена система обновления статуса прочитанности сообщения
исправлено отображение поиска в чате для кастомных тем
сделан прозрачным задний фон для кастомных тем в записи голосовых сообщений

1.0.2.30
исправлены проблемы переподключения к серверу при разрыве соединения или уходе в сон

1.0.2.29
showImagePreview() for takePhotoLauncher
logout() fix MainActivity

1.0.2.28
добавили обработку drag-панирования в onTouchEvent после время pinch-to-zoom

1.0.2.27
Исправлена проблема с потерей сообщений при загрузке изображений. Когда пользователь отключается во время выбора фото из галереи.

Фотографии больше не перезагружаются при перемотке в чате
Первая загрузка изображения показывает прелоадер
Последующие отображения того же изображения загружаются мгновенно из кэша

1.0.2.26
Добавлена функция pinch-to-zoom для просмотра фотографий на весь экран в FullScreenImageActivity.kt.

1.0.2.25
уборка темной темы с MainActivity

1.0.2.24
добавлены новые темы

1.0.2.23
Исправлена критическая ошибка в MessageAdapter.kt с переиспользованием ViewHolder'ов при загрузке изображений.
Исправлена контрастность текста в активити ThemesActivity
Теперь ContactsActivity будет использовать стили кастомной темы так же, как ChatListActivity и ThemesActivity, обеспечивая единообразный внешний вид приложения.

1.0.2.22
Светлая тема полностью удалена из приложения
✅ Все пользователи, у которых была включена светлая тема, автоматически переключатся на тёмную при следующем запуске приложения
✅ Тёмная тема установлена по умолчанию
✅ Кастомные темы и пользователи, которые их используют, остаются без изменений
✅ Встроенные светлые темы (зелёная, синяя, фиолетовая, оранжевая) остаются доступными как кастомные темы
Fixed search bar background color in NewChatActivity.kt. The showSearchBar() method now applies custom theme colors:

Background: customTheme.textPrimaryColor.toColorInt()
Text color: customTheme.backgroundColor.toColorInt()
Hint text color: customTheme.backgroundColor.toColorInt()
Results count text: customTheme.backgroundColor.toColorInt()
When a custom theme is active, the search bar will display with the correct background and text colors. Falls back to default theme colors if no custom theme is set

1.0.2.21
Встроенная светлая тема: светлый фон с тёмным текстом + закруглённые углы
✅ Встроенная тёмная тема: тёмный фон с светлым текстом + закруглённые углы
✅ Кастомные темы: используют сохранённые на сервере цвета + закруглённые углы
✅ Стиль bubble: закруглённые углы (18dp) как у входящих сообщений
✅ Нет артефактов: прозрачный фон AlertDialog
✅ Кнопка удаления: красный цвет #FF3B30 для всех тем
✅ Полная адаптация: диалоги автоматически подстраиваются под любую тему с правильными цветами и закруглениями

1.0.2.20
1. Легковесный прелоадер для загрузки изображений
   Создан файл app/src/main/res/drawable/loading_spinner.xml с анимированным спиннером, который отображается во время загрузки изображения вместо черного фона.

2. Обновлен layout сообщений
   В файле app/src/main/res/layout/item_message.xml:

Обернул messageImageView в FrameLayout для наложения прелоадера
Добавил imageLoadingSpinner (48dp × 48dp) с центрированием
3. Обновлена логика MessageAdapter
   В файле app/src/main/java/lavender/client/android/ui/adapter/MessageAdapter.kt:

Добавлена ссылка на imageLoadingSpinner в MessageViewHolder
При загрузке изображения прелоадер становится видимым
После успешной загрузки прелоадер скрывается
При ошибке загрузки прелоадер также скрывается
Добавлена проверка на файлы: файлы больше не отображаются как изображения (!isFile)
Файлы отображаются с иконкой и именем:
PDF файлы → ic_file_pdf
ZIP/RAR/7Z архивы → ic_file_archive
Остальные файлы → ic_file

## [1.0.2.19] - 2026-04-29
Все исправления завершены:

Синхронизация звука в плеере - Заменен Handler на coroutine для постоянного обновления позиции воспроизведения в AudioPlayerManager.kt

Цвет панели выбора в кастомной теме - Добавлено применение цвета из кастомной темы при входе в режим выбора сообщений в NewChatActivity.kt:544-562

Цвет иконки навигации в режиме выбора - Обновлена логика установки цвета иконки с учетом кастомной темы в NewChatActivity.kt:545-562

Видимость меню супер админа - Добавлена проверка флага isSuperAdmin при отображении меню в ChatListActivity.kt:556

Сохранение настроек уведомлений - Исправлена регистрация токена при запуске приложения с передачей сохраненных флагов push_send_enabled и push_receive_enabled в ChatListActivity.kt:247-258


## [1.0.2.12] - 2026-04-27

### Fixed
- 🛠️ Fixed missing `logout()` functionality and unresolved references in `ChatListActivity`.
- 🐛 Resolved systemic string syntax errors across the application.
- 🛡️ Improved code stability and cleaned up IDE warnings/errors.
- 🎨 Final unified styling for toolbars and avatars.

### Added
- ⌨️ Fully implemented listeners for message input actions (send, attach, audio).

## [1.0.2.11] - 2026-04-27

### Fixed
- 🛠️ Fixed crash when opening chats with no messages.
- 🎨 Minor UI refinements in chat list.

## [1.0.2.10] - 2026-04-27

### Added
- 👤 **Mention System**: Added user mention system using the `@` symbol in group chats.
- 🎤 **Voice Messages**: Implemented voice message recording and sending with waveform visualization.
- 🖼️ **Avatar Viewer**: Added full-screen avatar viewing from the profile menu.
- 🌈 **Edge-to-Edge**: Improved support for Edge-to-Edge display on Android 12-14.

### Changed
- 🎨 **UI Unification**: Unified incoming message bubble and chat card colors.
- 🎨 **Modern Toolbars**: Increased height (up to 100dp) and added rounded corners (24dp) to all toolbars.
- 📱 **Optimized Layouts**: Custom toolbar height for direct chats (84dp) to maximize screen space.

### Fixed
- ⌨️ Improved keyboard and system bar handling for Android 12 and 14.
- 🔄 Fixed update system: forced check on "Update" click and on every startup.
- 🔝 Adjusted FAB position to avoid overlapping with navigation bar.

## [1.0.2.1] - 2026-04-26

### Added
- 🔔 **Customizable Notification Styles**:
  - Choice between multiple system notification styles in the Notification Center.
  - **Standard**: Classic title and body preview.
  - **Messaging (Telegram style)**: Uses Android `MessagingStyle` for better conversation grouping.
  - **Expanded Text**: Uses `BigTextStyle` for full preview of long messages.
  - **Real-time Preview**: Visual preview of styles directly in settings with localized (EN/RU) examples.
- 📜 **Enhanced Notification Management**:
  - Separated **Notification Settings** and **Notification Log** into dedicated screens.
  - Fixed issues where incoming/outgoing events were missing from history.
- 🛠️ **FCM Server Logs (Super Admin only)**:
  - New dedicated screen for super admins to monitor real-time server push logs.
  - Tracks token registrations, delivery success, and skip reasons (e.g., "User disabled push").
- 🎨 **Powerful Theme Editor Updates**:
  - **Multi-Backgrounds**: Support for separate background images for **Chat List** and **Chat** screens.
  - **Bottom Panel Styling**: Full control over background and icon colors for the chat input panel.
  - **Smart Saving**: The "Save" button now only appears when actual changes are detected.
  - **Localized Templates**: Built-in themes (Forest Green, Ocean Blue, etc.) are now fully localized and optimized for bottom panel colors.
- 🖼️ **Personalized Theme Preview**:
  - Previews now display **your real username and avatar**, making theme setup truly personal.

### Fixed
- 🛠️ **UI Tinting & Consistency**:
  - **Toolbar Perfected**: Titles and all icons (Search, Contacts, More) now strictly follow theme colors.
  - **Avatar Visibility**: Fixed "white circle" bug where avatars were accidentally tinted over by theme colors.
  - **Message Bubbles**: Bubbles and **quoted messages** now correctly apply custom theme colors with high contrast.
  - **Quote Visibility**: Fixed invisible quote text in standard light themes.
  - **FAB Tinting**: The "+" button in the chat list now follows the Primary theme color.
- 🛡️ **Theming Stability**:
  - Clean transitions when switching between custom and standard system themes (no "color leaking").
  - Corrected "Message edited" toast to "Theme saved" when updating themes.
- 🔌 **Server & Protocol**:
  - Updated messenger.proto and database to persist new theme fields (bottom panel, chat list background).
  - Improved FCM registration logs with detailed "Push for me" and "Push from me" statuses.

## [1.0.2.0] - 2026-04-26
### Added
- 🌈 **Personalized Theme Editor**: Creation and management of custom themes with Primary, Background, and Surface color controls.
- 🖼️ **Chat Backgrounds**: Ability to upload custom background images for chat screens.
- 📡 **Server-side Theme Storage**: Themes are now synced to your account and available on all devices.
- 🌗 **Auto-Theming**: System bars automatically adapt their icon brightness based on your primary theme color.

### Fixed
- Optimized image loading for high-resolution background textures.
- Improved gRPC reconnection logic during theme synchronization.
- General UI cleanup for the account settings screen.
