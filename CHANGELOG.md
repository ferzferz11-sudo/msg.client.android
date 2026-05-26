# Changelog

All notable changes to Lavender Messenger (Android client) will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## 1.0.6.32
### Update Management (Current Release)
- 📥 **Interactive Update Progress**: Tap on the update icon in the toolbar to manage ongoing downloads.
- 🚫 **Cancel Updates**: Added the ability to cancel an in-progress update download directly from the UI.
- 🏗️ **Core UI Foundations**: Extended `UpdateManager` with cancellation support and integrated it with the themed widget system.

## 1.0.6.31
### 🧩 Unified Widget System (Current Release)
- 🏗️ **Core UI Engine**: Fully implemented `WidgetSystem.kt` with a suite of themed base components:
    - `StandardBottomSheet`: Base for all interactive modal dialogs.
    - `ActionBottomSheet`: Dynamic icon-based menus (Attachments, Chat Actions).
    - `ListBottomSheet`: High-performance RecyclerView integration for themed lists.
    - `SearchableListBottomSheet`: Advanced component with real-time filtering, loading states, and action buttons.
- 🔐 **Themed Auth Flow**: Entire authentication system (Login, Registration, Forgot Password, Auth Choice) now uses unified widgets, removing 300+ lines of redundant UI boilerplate.
- ⚙️ **Themed Settings**: User Profile menu and Additional Settings fully migrated to the widget system with automatic tinting for icons and destructive actions (Logout/Delete).
- 🎨 **Deep Theme Integration**:
    - **Smart Inputs**: All text fields, hints, and cursors automatically adapt to the primary theme color.
    - **Interactive States**: Loading indicators (`ProgressBar`) and text selection (`highlightColor`) now follow theme accents.
    - **Cursor Support**: Modernized cursor tinting for a polished user experience.
- ⚡ **UX Refinements**:
    - **Loading States**: Introduced `setLoading()` for searchable lists to provide visual feedback during gRPC data fetching.
    - **Persistent Search**: Improved adapter logic (`UserAdapter`, `SelectableUserAdapter`) to maintain search filters during background data updates.
- 🔧 **Code Cleanup**: Removed deprecated theme application methods and unified dialog logic across all activities.

## 1.0.6.30
### Calls & Core Refinement
- 📞 **Call Stability**: Fixed a critical bug where users with both a UUID and a Username could not correctly hang up on calls.
- 🔔 **Call Priority**: Increased notification priority to `MAX` and enabled full-screen intent for incoming calls.
- 🏗️ **Widget Foundations**: Began migration to the new component system for chat creation and contact management.

## 1.0.6.29
### UI Improvements & Reactions
- 🛡️ **Admin Panel**: TabLayout background is now transparent, and text uses the primary theme color. Optimized list rendering using RecyclerView and DiffUtil to eliminate lag during multi-selection.
- 😀 **Emoji Picker**: Replaced the dialog-based emoji picker with a themed BottomSheet for better accessibility and style consistency.
- 🔥 **Reactions**: Added "100%" (💯) reaction and reordered existing reactions (👍, 💯, 🔥, ✅, ❤️, 😂, 😮, 😢, 🙏).
- 📞 **Conferences**: Prevented re-entry into ended or deleted conferences. Tapping on such messages in chat now opens the standard context menu instead of trying to join.
- 🔐 **Authentication**: Fixed login notifications to correctly distinguish between new registrations and existing user logins.
- 🧹 **Cache Management**: Added automatic cache clearing after app updates and successful logins to ensure data consistency.
- 🎨 **Theme Fixes**: Fixed non-themed buttons in the authentication choice dialog shown after logout.

## 1.0.6.28
### Visual Improvements
- 😀 **Emoji Selection**: Moved to a themed bottom sheet for better UX and consistency.
- 🔥 **Reaction Update**: Added new reactions and optimized their display order.
- 📞 **Conference Logic**: Restricted re-entry into finalized conference rooms.

## 1.0.6.27
### Conference Lobby & Setup
- 🏠 **Lobby Screen**: Introduced a pre-call setup screen with video/audio toggles.
- ⚙️ **Advanced Setup**: Users can now set conference topics and scheduled start times.
- 🔔 **Invite Control**: Manual trigger for participant invitations from the lobby.
- 🔊 **Audio Experience**: Added dial tones for outgoing and distinctive ringtones for incoming calls.
- ⌚ **Time Synchronization**: Fixed timezone-related display bugs for message timestamps.

## 1.0.6.26
### Stability & Core Fixes
- 🛠️ **Modularization**: Refactored call logic into independent modules for better crash resistance.
- 🔄 **Rotation Support**: Improved preservation of call state and timers during screen orientation changes.
- 📱 **MIUI Optimization**: Fixed specific crashes triggered when returning to the app on Xiaomi devices.

## 1.0.6.25
### Group Conferences & Call UI
- ⚡ **Seamless Entry**: Enabled instant joining of active conferences.
- 🖱️ **Deep Linking**: Direct entry into calls via chat message notifications.
- ⏱️ **Call Tracking**: Integrated a real-time call duration timer into the call UI.
- 💡 **Display Management**: Implemented screen wake-lock to prevent dimming during video calls.

## 1.0.6.24
### Interface Optimization
- 📱 **UI Density**: Compact chat list mode where previews are limited to a single line.
- 🧠 **Dynamic UI**: Action buttons now dynamically appear only when changes require saving.
- 🚀 **Performance**: Optimized scrolling performance and search indexing for large contact lists.

## 1.0.6.23
### Performance Improvements
- ⚡ **Instant Loading**: Implemented theme caching to allow the UI to load immediately from memory.
- 🛠️ **Startup Polish**: Eliminated visual flickering during the application boot sequence.

## 1.0.6.21
### Rebranding
- 🐦 **Lava Branding**: Officially transitioned branding and assets to "Lava Messenger".

## 1.0.6.16
### Group Conference Mode
- 👥 **Group Video**: Launched group video meetings with integrated chat history.
- 🎨 **Redesigned Call UI**: Modern interface for managing active calls.
- 📊 **Auto-Logging**: Automated capture of call durations and participant metrics.

## 1.0.6.6
### Background Updates & Reliability Fixes
- 📥 **Background APK Downloading**: Updates are now downloaded in the background using `WorkManager`. A persistent notification shows real-time progress.
- 🔔 **Install Notifications**: Once the download is complete, a notification allows for one-click installation.
- 🛡️ **gRPC Reliability**: Fixed a critical race condition where messages sent while offline might not sync correctly upon reconnection.
- ⚡ **Auto-Reconnection**: Improved gRPC stream recovery with up to 100 retry attempts and better state management.
- 🛠️ **Modern Build System**: Migrated to AGP 9.0 with built-in Kotlin support and enabled KSP2 for faster builds.
- 📱 **Android 14+ Support**: Added necessary permissions and foreground service declarations (`dataSync`) for seamless background tasks.

## 1.0.3.17
### Theme System Enhancements
- 🎨 **NotificationActivity Theme Adaptation**: Added edge-to-edge support with WindowInsets handling and ThemeManager integration
- 🎨 **SuperAdminActivity Theme Adaptation**: Added edge-to-edge support with WindowInsets handling and ThemeManager integration
- 🖼️ **Background Images Support**: Added chatListBackground ImageView to NotificationActivity and SuperAdminActivity for custom theme backgrounds
- 🎨 **Welcome Text Contrast**: ChatListActivity welcome screen now uses theme colors (textPrimaryColor, onSurfaceColor) for better readability on custom backgrounds
- ➕ **FAB Theme Colors**: Floating Action Buttons (addChatFab, addContactFab, addThemeFab) now use primaryColor for background and onPrimaryColor for icons

## 1.0.3.10
### Language Default Update
- 🌐 **Russian as Default**: Russian language is now set as default for all first-time app installations
- 🔄 **Language Persistence**: Selected language preference is saved locally and restored on app restart
- 📝 **Server Logging**: Language changes are logged to server for analytics and user preference tracking
- 🎯 **Consistent Implementation**: All activities now use unified default language logic ("ru" instead of "en")
- 🔧 **Clean Implementation**: Removed local Android logging for language preference errors
- **Activities Updated**: MainActivity, ChatListActivity, ProfileActivity, NewChatActivity, and all other activities now default to Russian
- **Language Toggle**: Enhanced language switching in MainActivity and ChatListActivity with proper server logging

## 1.0.3.5
### Drafts & Mutes Migration to User ID
- Migrated draft messages and muted chats from username-based to user ID (UUID) based storage.
- **GrpcClient**: Added `setUserId()`, `getUserId()`, and `fetchUserId()` methods.
- **Proto**: Updated `SaveDraftRequest`, `GetDraftRequest`, `DeleteDraftRequest`, `GetMutedChatsRequest`, `SetMutedChatRequest` to use `userId` field.
- **Activities**: `ChatListActivity` and `NewChatActivity` now ensure `userId` is set before draft/mute operations.
- **Caching**: User ID is retrieved from server once and cached in SharedPreferences.
- **Server Sync**: Uses new `GetUserId` RPC to resolve UUID from username.
- **Benefit**: Drafts and muted chats now persist correctly after username changes.

## 1.0.3.2
### Theming Improvements
- Unified bottom sheet styling across all activities (ChatList, Chat, Notifications)
- Default theme: bottom sheets now use `colorSurfaceContainer` for background and `colorPrimary` for icons/top handle
- Custom themes: consistent `backgroundColor` and `primaryColor` for all bottom sheets
- Outgoing message bubbles now use `colorSecondary` for default theme
- Added `dragHandle` ID to bottom sheet layouts for proper theming
- Fixed background color handling for NotificationActivity with custom themes

1.0.3.1
### Full-Size Group Avatars
- Extended full-size avatar functionality to group chats (both thumbnail 512x512 and full 1920x1920)
- Added `full_avatar_url` field to `UpdateChatAvatarRequest` protobuf message
- Server: Added `full_avatar_url` column to `chats` table with migration
- Server: Updated `UpdateChatAvatar` handler to store both thumbnail and full URLs
- Android: Added `fullAvatarUrl` field to `ChatInfo`, `ChatInfoProto`, and `ProfileActivity`
- Android: Updated all places where `ProfileActivity` is opened to pass `full_avatar_url` (ChatListActivity, NewChatActivity, SuperAdminActivity)
- Android: Group avatar click now opens full-size image when available

### Server Improvements
- Reduced avatar upload logging verbosity (now shows compact message with filenames)
- Replaced hardcoded IP addresses with `PUBLIC_IP` environment variable in `http_server.go`
- All file upload handlers now use `os.Getenv("PUBLIC_IP")` with `localhost` fallback
- Server configuration is now fully driven by `.env` file

1.0.3.0
### Message Drafts (Unsent Message Persistence)
- Draft messages are now automatically saved when leaving a chat.
- Draft text and reply context are restored when re-entering the chat.
- Draft is automatically cleared after successful message send.
- Works across all chats with server-side storage for multi-device support.
- New gRPC methods: SaveDraft, GetDraft, DeleteDraft.
- Full reply preservation: message ID, username, and text are saved with the draft.

### Server Health Monitoring
- Added `monitor.sh` script for automated server health checks.
- Runs every 30 minutes via cron to verify server availability.
- Auto-restarts server if down, with detailed logging to `logs.txt`.
- Integrated into deployment process for automatic setup.

1.0.2.33
масштабный рефакторинг системы тем

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
