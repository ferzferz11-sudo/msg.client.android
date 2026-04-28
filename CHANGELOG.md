# Changelog

All notable changes to Lavender Messenger (Android client) will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

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
