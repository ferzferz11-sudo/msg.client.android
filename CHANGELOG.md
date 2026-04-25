# Changelog

All notable changes to Lavender Messenger (Android client) will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.0.1.51] - 2026-04-25

### Added
- 💬 **Telegram-style Swipe**: Changed message swipe direction to **left** for replying, matching industry standards.
- 🎨 **Theme System Polish**:
  - Moved theme toggle (light/dark) from the main chat list to the dedicated **Themes** screen for a cleaner UI.
  - Added theme templates (Green, Blue, Purple, Sunset) for instant customization.
  - Live color preview in the theme editor.
- 🔗 **Guaranteed Chat Transition**: Optimized navigation to ensure users are always immediately redirected to a newly created chat.

### Fixed
- 🛑 **Photo Duplication**: Resolved issue where some gallery apps caused images to be uploaded and sent twice.
- 🎨 **Theme Sync**: Fixed RadioButton synchronization in Theme settings when toggling modes via the toolbar.
- 📏 **Compact Bubbles**: Perfected the sizing and alignment of message bubbles for better readability.

## [1.0.1.49] - 2026-04-25

### Added
- 🎨 **Dynamic Custom Themes**:
  - Full support for creating, editing, and deleting custom color schemes.
  - Live color preview in the theme editor.
  - Theme templates (Green, Blue, Purple, Sunset) for quick customization.
  - Automatic synchronization of chosen themes with the server.
- 🚀 **Android Deployment Script**:
  - Added `deploy_android.sh` to automate APK and version uploads to the production server.
- 🔄 **Pull-to-Refresh**: Added ability to manually refresh the chat list by swiping down.
- ⚡ **Optimized Synchronization**: Implemented chat list versioning to avoid unnecessary data fetching and improve performance.
- 🖼️ **New Chat List Design**:
  - Avatars are now located on the left side of the list item.
  - **Direct Chats**: Displays a larger avatar of the other person only. The chat name now shows only the contact's name.
  - **Group Chats**: Displays small overlapping avatars of participants on the left.
- 💾 **State Preservation**: Injected `ChatListViewModel` to maintain chat data and scroll position across orientation changes.

## [1.0.1.48] - 2026-04-25

### Added
- 👥 **Contact Management**:
  - New Contacts screen with the ability to add and remove contacts.
  - Search functionality to find existing users on the server.
  - Option to create a direct chat immediately when adding a new contact.
- 🔄 **Smart Chat Creation**:
  - The "Add Chat" list now only displays users from your contacts for a more private experience.
  - Automatic prompt to add your first contact if the list is empty.
- 🆔 **User Identity**:
  - Transitioned to a UUID-based architecture (`user_id`) to support safe username changes.
- 🌍 **Localization**:
  - Added "Create private chat" and other contact-related strings in both English and Russian.

### Changed
- 🛠️ **UI Refinement**: Replaced generic "Chat with %" text with "Create private chat" in the contact options menu.

### Fixed
- 🔗 **Data Integrity**: Improved synchronization between client usernames and server-side UUIDs.
- 🧹 **Internal API**: Updated gRPC client to support new contact-related RPC methods.

## [1.0.1.46] - 2026-04-25

### Added
- 📍 **Interactive Location Sharing**:
  - New Map Picker screen using OpenStreetMap and Leaflet.
  - Ability to select a specific point on the map by tapping.
  - Built-in map viewer for received location messages.
- 💬 **Modern Message UI**:
  - Compact message bubbles that wrap around text content (Telegram style).
  - Proper alignment: Outgoing messages on the right, incoming on the left.
  - Outgoing message backgrounds now match the chat list card colors in both themes.
- 🎨 **Enhanced UI Consistency**:
  - Unified background colors for outgoing messages with `colorSurfaceContainer`.
  - Improved layout logic for messages during selection mode.

### Fixed
- 📏 **Bubble Sizing**: Fixed messages taking full width; they now shrink to fit the content.
- 🌓 **Theme Synchronization**: Removed redundant night-mode drawable overrides to ensure theme-aware colors work correctly.

## [1.0.1.45] - 2026-04-24

### Added
- 🚪 **Exit Navigation**: Replaced back arrow with "Exit to App" icon in ChatListActivity for clearer navigation to main screen.
- ➕ **Add Chat Icon**: Added new "Add Chat" icon in the Toolbar for quicker access to chat creation.
- 👤 **Improved Profile Editing**: Redesigned Edit Profile screen with Material cards and improved typography.
- 🌒 **Enhanced Theming**:
  - Full Light/Dark theme support for all profile and settings screens.
  - Light theme now uses pure white background with pale lilac cards for better clarity.
  - Dark theme cards updated to deep purple for better contrast.
  - Added `windowLightStatusBar` support for Light theme.
- 📱 **Android 15 Compatibility**:
  - Implemented full Edge-to-Edge support with WindowInsets handling.
  - Fixed UI overlapping with status bar (clocks/icons) and navigation bars on all main activities.
- 🛠️ **Build System**: Updated to `compileSdk 37` and refined dependency versions (Glide 5.0.7, Google Services 4.4.4, etc.).

### Changed
- ⚡ **Optimized Update Check**: Update check now runs only once per session (background task in ChatListActivity after initial load) to save resources.
- 🔄 **Skip Auto-Login**: Added logic to skip automatic login when returning from ChatListActivity to allow server switching.

### Fixed
- 🖼️ **Avatar Synchronization**: Fixed issue where profile avatar didn't update immediately in the chat list by force-refreshing the cache and adapter.
- 🔔 **Notification Flow**: Fixed auto-navigation from notifications by ensuring proper gRPC authentication before loading chat data.
- 🐛 **UI Cleanup**: Removed unused imports, redundant qualifiers, and fixed various lint warnings across the project.
- 🧹 **General Chat Removal**: Completely removed all legacy logic related to the "general" chat room from both client and server.

## [1.0.1.43] - 2026-04-24

### Added
- 🔄 **Back Button Navigation**: Back button in ChatListActivity now navigates to MainActivity instead of closing app
  - Provides consistent navigation flow for users
  - FLAG_ACTIVITY_CLEAR_TOP ensures proper back stack management

### Added
- 📥 **Update Menu Item in Chat List**: Added "Update" menu item to ChatListActivity
  - Horizontal progress bar with download status (MB/MB)
  - Cancel button to stop download process
  - Rounded background card for better UI
  - Download progress displayed in real-time

### Added
- 🔔 **Background Update Check**: Update check now runs in background on first app launch
  - MainActivity saves update availability to SharedPreferences
  - Update icon displayed on ChatListActivity toolbar when update available
  - Clicking update icon navigates to MainActivity for download
  - No automatic update check when launching chat list

### Fixed
- ✅ **Message Read Receipts**: Fixed messages not being marked as read in UI
  - Added history reload after marking messages as read
  - onCompletion callback for UI refresh after history reload
  - Applied to all markRead calls in NewChatActivity

### Fixed
- 🔔 **Notification Navigation**: Added onNewIntent handler for notification clicks when app is running
  - Handles notification clicks when MainActivity is already in back stack
  - Properly navigates to specific chat room from notification
  - setIntent() called to update current intent

### Fixed
- 🚫 **Update Dialog Interference**: Fixed update dialog showing when coming from notification
  - Prevented periodic update check from starting when from_notification=true
  - Ensures smooth navigation to chat without interruption

## [1.0.1.42] - 2026-04-24

### Added
- 🔔 **Push Notification Room Navigation**: Notifications now include room_id for direct chat navigation
  - Updated LavenderMessagingService to extract room_id from notification data
  - Notification intent now includes room_id and from_notification flags
  - Clicking notification opens the specific chat room where message was sent

### Added
- 🚀 **Auto-Navigation on App Launch**: App now auto-navigates to chat list if credentials are saved
  - MainActivity checks for saved username, password, and server address
  - If credentials exist, automatically navigates to ChatListActivity
  - If coming from notification, navigates directly to the specific chat room
  - Improved user experience by skipping login screen when already authenticated

### Fixed
- 💬 **Message Auto-Update in Chat**: Fixed messages not updating automatically while in chat
  - Added room_id filtering in NewChatActivity message observer
  - Messages now properly filtered by current room_id
  - Real-time message updates now work correctly within chat rooms

## [1.0.1.41] - 2026-04-24

### Added
- 📊 **Chat Sorting by Last Message**: Chats now sorted by time of last message instead of creation time
  - Added last_message_time field to ChatInfo protobuf definition
  - Server now computes MAX(m.created_at) for each chat
  - Client-side ChatInfo model includes lastMessageTime field
  - ChatListActivity sorts chats by lastMessageTime in descending order

### Fixed
- 🎨 **NewChatActivity Theme Application**: Fixed theme not applying correctly in chat activity
  - Moved applySavedColorScheme() call before super.onCreate()
  - Theme now applies correctly on activity launch
- 🎨 **Message Bubble Colors**: Updated message bubble colors for better theme consistency
  - Light theme: Incoming messages use pale_lilac (#F8F7FC) to match chat list cards
  - Dark theme: Incoming messages use lavender_mist (#967BB6) to match toolbar
  - Dark theme: Outgoing messages use dark purple (#4A3B6D)
- 🎨 **Chat Background**: Removed hardcoded chat background image
  - Chat background now uses theme-aware windowBackground
  - Light theme: pale_lilac background
  - Dark theme: dark_background (#04052E)

## [1.0.1.40] - 2026-04-24

### Added
- 🎨 **New Icons**: Added custom icons for settings and account
  - ic_account_circle for profile
  - ic_light_mode for theme toggle
  - ic_overflow_settings for menu overflow
  - ic_settings_account for account settings
- 🌐 **Language Display**: Language button now shows current language (e.g., "Language: English", "Язык: Русский")
- 👋 **Welcome Message**: Added welcome message for new users
  - Displayed when user has no chats
  - Describes app features and end-to-end encryption
- 📝 **Preview/Edit**: Added preview and edit buttons for profile

### Changed
- 🎨 **Chat List UI**: Redesigned chat list layout with improved spacing and visual hierarchy
- 🎨 **Menu Styling**: Updated menu overflow icon with custom style
- 🎨 **Theme Attributes**: Added CustomOverflowStyle for consistent menu icon styling
- 🔧 **Activity Refactoring**: Refactored ChatListActivity, EditProfileActivity, ProfileActivity, and NewChatActivity

## [1.0.1.39] - 2026-04-24

### Added
- 📁 **File Attachments**: Added support for file attachments in messages
  - PDF files with custom icon
  - Archive files (zip, rar, 7z) with custom icon
  - Clickable file names to open/download files
- 🔔 **Notification History**: Added notification history dialog
  - View FCM token with copy functionality
  - Test notification button for debugging
  - View last 20 notifications with timestamps
  - Clear notification history
- 📊 **Notification Tracking**: Added NotificationHistory object to track received notifications
  - Stores title, body, timestamp, and sender
  - Automatically limits to 20 most recent notifications

### Changed
- 🎨 **File Display**: Improved file message display
  - File names shown separately from URLs
  - Different icons for different file types
  - Clickable text to open files
- 🔧 **MessageAdapter**: Updated to handle file attachments with better UX

## [1.0.1.37] - 2026-04-23

### Fixed
- 🐛 **Dialog Inflation Fix**: Fixed dialog inflation issues in ChatListActivity
  - Changed inflate parameter from findViewById to null for dialog root
  - Added false parameter to user item inflation to prevent duplicate attachment

## [1.0.1.36] - 2026-04-23

### Added
- 👤 **Edit Profile Activity**: Added dedicated activity for editing user profile
  - Change username, bio, and password
  - Upload and change avatar
  - Delete profile option
- 🔐 **Password Change Dialog**: Added dialog for changing user password
- 🧪 **Unit Tests**: Added comprehensive unit tests for ProtoUtils
  - Tests for MessageProto conversion
  - Tests for timestamp handling
  - Tests for reaction serialization
  - Round-trip conversion tests

### Changed
- 🔧 **Profile Activity**: Refactored profile activity to use EditProfileActivity for editing
- 🔧 **gRPC Client**: Added profile editing methods to gRPC client
- 🔧 **ProtoUtils**: Enhanced proto conversion utilities

## [1.0.1.35] - 2026-04-23

### Added
- 😀 **Message Reactions**: Added emoji reactions to messages
  - Long-press on message to add reaction
  - Reaction display with emoji and count
  - Multiple users can react to same message
- ↩️ **Reply to Messages**: Added reply functionality
  - Swipe right on message to reply
  - Reply preview shows original message
  - Reply indicator in message bubble
- 👤 **Profile Activity**: Added dedicated profile activity
  - View user profile with avatar, bio, and status
  - Edit own profile
  - View group participant list
- 📎 **Attachment Picker**: Added dialog for selecting attachment type
  - Photo from camera
  - Image from gallery
  - File attachment
  - Location sharing
- 📍 **Location Sharing**: Added ability to share location in messages
- 🗑️ **Delete Messages Dialog**: Added dialog for deleting selected messages
- 👥 **Participant List**: Added item layout for displaying participants
- 🎨 **New Icons**: Added multiple new icons
  - ic_attach, ic_camera, ic_gallery, ic_file
  - ic_location, ic_forward, ic_edit, ic_reply_swipe
- 🎨 **Reaction Background**: Added drawable for reaction bubbles

### Changed
- 🎨 **Message Layout**: Redesigned message item layout
  - Added reaction display
  - Added reply preview
  - Improved spacing and visual hierarchy
- 🎨 **Chat Layout**: Updated chat activity layout
  - Added attachment picker button
  - Added reaction emoji button
  - Improved bottom panel design
- 🔧 **MessageAdapter**: Enhanced to handle reactions and replies
- 🔧 **gRPC Client**: Added reaction and reply support
- 🔧 **Proto Definition**: Updated protobuf with reaction and reply fields
- 🎨 **Color Scheme**: Updated colors for better visual consistency

## [1.0.1.34] - 2026-04-21

### Changed
- 🎨 **Material Design 3 Color System**: Restructured color scheme following Material Design 3 guidelines
  - Added colorPrimaryContainer, colorSecondaryContainer, colorSurfaceContainer, colorSurfaceVariant
  - Separated colors into categories: Primary, Secondary, Background, Dialog, Text, Button
  - Light theme: pale_lilac background, white dialogs, deep_purple text
  - Dark theme: deep_purple background, lavender_mist dialogs, lavender_mist/white text
- 🔧 **Default Theme**: Changed default theme in AndroidManifest from dark to light
  - Light theme is now the base, dark theme is applied on first launch
  - This ensures proper theme switching behavior
- 🐛 **Theme Override Fix**: Removed conflicting windowBackground override in values-night/themes.xml
  - This was preventing the correct pale_lilac background from showing in light theme
- 📱 **Landscape Layout Fix**: Fixed hardcoded background in layout-land/activity_main.xml
  - Changed from splash_background to windowBackground for theme consistency
- 🗑️ **Person Icon Removal**: Completely removed person icon (usersButton) from chat list toolbar
- 👤 **Profile Icon**: Changed profile icon from ic_menu_edit to standard edit icon (pencil)
  - Using simple vector drawable with theme-aware tinting
- 🎨 **Dialog Text Visibility**: Fixed text visibility in welcome dialog for dark theme
  - Changed text colors from textColorPrimary/textColorSecondary to colorOnPrimary
  - Improved contrast on lavender_mist background
- 🔧 **Code Refactoring**: Replaced getColor() calls with theme.resolveAttribute() for Material Design colors
- 💬 **Message Colors**: Updated message background colors in light theme
  - Changed incoming messages to pale_lilac for better contrast with toolbar
  - Changed outgoing messages to soft_lilac for distinction
  - Prevents messages from blending with deep_purple toolbar
- 🎨 **Dialog Text Colors**: Fixed dialog text visibility in light theme
  - Changed dialog background from colorSurface to colorSurfaceContainer (white)
  - Changed dialog text from colorOnPrimary to colorOnSurface for proper contrast
  - Applied to both portrait and landscape dialog layouts
- 💬 **Message Background Unification**: Unified message background colors in light theme
  - Changed outgoing messages from soft_lilac to pale_lilac
  - Now both incoming and outgoing messages use the same background as chat cards
- 🎨 **Button Style System**: Created PrimaryButton style for consistent button design
  - Based on Join button on main screen (240dp width, 56dp height, 18sp text)
  - Applied to Join button in Welcome dialog (portrait)
  - Applied to Join button on main screen (portrait)
  - Changed button background to colorPrimaryContainer to match main background in dark theme
- 🎨 **Landscape Button Style**: Created PrimaryButtonCompact style for landscape layouts
  - Smaller dimensions (200dp width, 48dp height, 16sp text)
  - Uses colorPrimary background to match other landscape buttons
  - Applied to Join button in Welcome dialog (landscape)
  - Applied to Join button on main screen (landscape)
- 🎨 **Button Text Case**: Reverted to uppercase first letter for button text
  - Changed "join" to "Join" in English localization
  - Changed "войти" to "Войти" in Russian localization
  - Removed textAllCaps="false" from button styles (using default Material behavior)
- 🎨 **Dark Theme Background**: Added dark_background color (#04052E) from logo edge
  - Changed dark theme windowBackground and colorSurface from deep_purple to dark_background
  - Applied to all main screens in dark theme only
- 🎨 **Button Text Case**: Disabled textAllCaps in all button styles
  - Added android:textAllCaps="false" to PrimaryButton and PrimaryButtonCompact
  - Button text now displays exactly as defined in string resources
- 🎨 **Dialog Text Visibility**: Fixed dialog text visibility in dark theme
  - Changed colorOnSurface from lavender_mist to white for better contrast on lavender_mist dialog background
  - Changed textColorPrimary from lavender_mist to white for server address spinner visibility
  - Applied to both portrait and landscape dialog layouts
- 🎨 **Activity Theme Configuration**: Added explicit theme for ChatActivity and ChatListActivity in AndroidManifest
  - Set Theme_MsgClientAndroid_Dark as default theme for ChatActivity
  - Set Theme_MsgClientAndroid_Dark as default theme for ChatListActivity
  - Prevents theme confusion and ensures dark theme is applied correctly
- 🎨 **Message Background Colors**: Fixed message backgrounds in light theme
  - Changed outgoing message background from colorPrimary (deep_purple) to pale_lilac
  - Changed incoming message background from incoming_message_light (white) to pale_lilac
  - Created drawable-night versions for dark theme (lavender_mist for outgoing, deep_purple for incoming)
  - Messages now match chat cards background in light theme
- 🎨 **Chat UI Improvements**: Removed avatars from messages and disabled toolbar animation
  - Hidden avatarImageView in item_message.xml (avatars will be shown in toolbar instead)
  - Disabled animateToolbarTitle() call in ChatActivity.kt
  - Toolbar now shows only static text without animation
- 📥 **Download Progress Enhancement**: Added download progress display in megabytes and button blocking
  - Added downloadProgressText TextView to show downloaded/total size in MB
  - Implemented setButtonsEnabled() to block all buttons during download
  - Buttons become semi-transparent (alpha 0.5) when disabled
  - Progress text updates in real-time during download (e.g., "5.23 / 10.45 MB")
- 🎨 **Update Button Styling**: Changed download update button to match PrimaryButton style
  - Replaced TextView with Button in activity_main.xml
  - Applied PrimaryButton style for portrait layout
  - Applied PrimaryButtonCompact style for landscape layout
  - Button now matches Join button design across the app
- 🐛 **Crash Fix**: Fixed crash when clicking Update button
  - Added null checks for view initialization in downloadAndInstallApk()
  - Added null checks for all buttons in setButtonsEnabled()
  - Added try-catch block in setupDownloadUpdateButton() with error logging
  - Added minWidth and minHeight to update button for proper sizing
- 🔄 **Orientation Change Fix**: Prevented download interruption on screen rotation
  - Added android:configChanges="orientation|screenSize|keyboardHidden" to MainActivity in AndroidManifest
  - Added onConfigurationChanged() method to handle orientation changes without activity recreation
  - Download continues in background when device is rotated
- 📝 **Button Font Size Unification**: Unified font size across all buttons on main screen
  - Changed PrimaryButton textSize from 18sp to 16sp (portrait)
  - Changed PrimaryButtonCompact textSize from 16sp to 14sp (landscape)
  - Changed languageButton textSize from 14sp to 16sp (portrait), 12sp to 14sp (landscape)
  - Changed colorSchemeButton textSize from 14sp to 16sp (portrait), 12sp to 14sp (landscape)
  - All buttons now match logoutButton font size in their respective orientations
- 🎨 **ChatActivity Theme Fix**: Removed explicit dark theme from ChatActivity in AndroidManifest
  - ChatActivity now uses app's default theme (respects user's light/dark preference)
  - Incoming messages now show correct pale_lilac background in light theme
  - Previously ChatActivity was forced to dark theme regardless of user preference
- 🎨 **ChatListActivity Theme Fix**: Removed explicit dark theme from ChatListActivity
  - Re-added applySavedColorScheme() call to ChatActivity and ChatListActivity onCreate
  - Removed default "dark" scheme saving from applySavedColorScheme() in both activities
  - Both activities now read and apply user's theme preference without overriding it
  - MainActivity remains the only place that sets default "dark" theme on first launch
- 🔄 **Theme Sync Across Activities**: Added theme synchronization between all activities
  - Added currentColorScheme tracking in MainActivity
  - Added onResume check for theme changes in MainActivity
  - Changed default theme from "light" to "dark" in ChatListActivity toggleColorScheme()
  - Changed default theme from "light" to "dark" in ChatListActivity updateColorSchemeIcon()
  - Changed default theme from "light" to "dark" in ChatActivity onOptionsItemSelected
  - Changed default theme from "light" to "dark" in ChatActivity updateThemeIcon()
  - Now theme changes in chat activities sync with MainActivity when returning
- 🎨 **Unified Theme Toggle Icon**: Made ChatActivity theme toggle match ChatListActivity
  - Added colorSchemeMenuItem variable to ChatActivity for menu item reference
  - Changed updateThemeIcon() to use saved reference instead of menu parameter
  - Replaced applyTheme() with toggleColorScheme() to match ChatListActivity implementation
  - Both activities now use identical theme toggle logic and icons
- 🐛 **ChatActivity Light Theme Fix**: Fixed light theme not applying correctly in ChatActivity
  - Changed applySavedColorScheme() to use Base_Theme_MsgClientAndroid for light theme
  - Previously used non-existent Theme_MsgClientAndroid style causing fallback to default theme
  - Now light theme applies correctly with proper colors and styling

## [1.0.1.33] - 2026-04-21

### Changed
- 🎨 **Dark Theme Background**: Updated dark theme background to deep_purple for chat list and main screen
- 🎨 **Dialog Backgrounds**: Added lavender_mist background for all dialogs in dark theme
- 🎨 **Button Styles**: Updated button styles with rounded corners (24dp) and strokes in dark theme
- 🎨 **Text Contrast**: Improved text contrast for better visibility on dark backgrounds
- 🎨 **Icon Removal**: Removed person icon from chat list and chat activities
- 🎨 **Dialog Consistency**: Updated dialog styling for consistency across the app
- 🎨 **Button Visibility**: Fixed button visibility issues in dark theme dialogs

## [1.0.1.32] - 2026-04-21

### Fixed
- ⏱️ **Chat Creation Timeout**: Increased timeout from 100ms to 500ms for direct and group chat creation callbacks
- 🔄 **Room Switching**: Fixed issue where opening a direct chat would show general chat messages instead
- 🧹 **Message Clearing**: Added message clearing when switching between chats to prevent message leakage
- 📱 **Activity Mode**: Added `singleTask` launch mode to ChatActivity for proper intent handling
- 🔀 **onNewIntent**: Implemented proper handling of new intents when ChatActivity is already running
- 🎨 **Dark Theme Dialogs**: Fixed white backgrounds in chat creation and profile dialogs in dark theme
  - TextInputLayout now uses `?attr/colorSurface` for proper dark theme support
  - EditText fields use `?attr/colorSurface` instead of window background
- 👤 **Avatar Contrast**: Improved default avatar visibility in dark theme
  - Added `android:tint="?attr/colorOnSecondary"` to use lighter color in dark theme
  - Added `app:civ_circle_background_color="?attr/colorSurface"` for better contrast

### Changed
- 🔔 **Connection Notification**: Improved toast notification when connecting to a chat
  - Friendly text instead of technical message
  - Russian: «Пользователь [Имя] вошёл в чат [Название чата]»
  - English: «User [Name] joined the chat [Chat Name]»
  - Duration increased to Toast.LENGTH_LONG (3.5 seconds)
  - Proper localization for both languages
  - Displays real chat name (e.g., "General" or group name) instead of technical ID

- 🎨 **Profile Dialog Buttons**: Reduced button text size from 14sp to 12sp for better fit of long Russian text

### Result
- ✅ Direct chats now reliably create without timeout errors
- 🎯 Opening a chat always shows the correct room messages
- 🧹 Messages from previous chats don't leak into newly opened chats
- 🔄 Smooth switching between different chats
- 👋 Clear and friendly connection notifications in both languages
- 📱 Long Russian text fits properly in profile dialog buttons
- 🌙 Dialogs now properly follow dark theme color scheme
- 👤 Default avatars are clearly visible on dark backgrounds
- 🎨 Consistent appearance across light and dark themes

## [1.0.1.28] - 2026-04-20

### Fixed
- 🐛 **Avatar Caching**: Fixed avatar caching issue where avatar URLs were not being saved for users with avatars
- 💾 **Cache Persistence**: All avatar URLs (including empty strings) are now correctly saved to cache
- 🚫 **Performance**: Removed fade-in animation for avatars in chat list for better performance
- ⏳ **Loading Order**: Chat list now displays only after all participant avatars are loaded
- ⏱️ **Timeout**: Added 5-second fallback timeout for avatar loading
- 🔄 **Consistency**: Same fixes applied to startPollingChats and onResume for consistency

### Result
- 📊 Avatars now correctly display in chat list (small icons next to chat names)
- 👥 Avatars correctly display in user list dialog
- ✨ No more flickering or missing avatars

## [1.0.1.27] - 2026-04-20

### Added
- 🖼️ **Image Attachments**: Users can now attach images to messages
- 📸 **Image Picker**: Image selection from gallery with progress indicator
- 🎬 **GIF Support**: Animated GIFs can be attached and displayed without resizing
- 📐 **Image Resize**: Images automatically resized to 1024x1024px before upload
- 📐 **Avatar Resize**: Avatars automatically resized to 256x256px before upload
- 🗑️ **Image Deletion**: Image files physically deleted from server when message is deleted
- 💬 **Send Without Text**: Send button enabled when image is selected, even without text

### Changed
- 🎨 **Chat List UI**: Participant avatars now displayed on the right side of chat items
- 📍 **Unread Count Position**: Unread count badge moved to left side of chat name with fixed indentation
- 📐 **Larger Avatars**: Avatar size increased from 48dp to 96dp (2x larger) for better visibility
- ✨ **Avatar Animation**: Smooth fade-in animation (300ms) when avatars load
- 🔄 **Auto Avatar Loading**: Current user avatar loads automatically when chat list opens
- 🎭 **Default Avatars**: Participants without custom avatars show default avatar icon
- 👥 **Direct Chat Avatars**: All participant avatars shown in direct chats (not just current user)
- 📱 **Avatar Cache Integration**: Chat list uses avatar cache from gRPC client for efficient loading
- ⚡ **Server Upload Size**: Increased max upload size from 5MB to 10MB

## [1.0.1.26] - 2026-04-20

### Added
- 👤 **Avatar Support**: Users can now upload and display profile pictures
- 🖼️ **Avatar Display**: Avatars shown in chat messages next to sender's name
- 📸 **Avatar Selection**: Avatar picker in profile dialog with progress indicator
- 🌐 **Server Storage**: Server-side avatar storage and serving via HTTP (port 8082)
- 🎨 **Default Avatar**: Default avatar icon for users without custom avatars
- 🔄 **Glide Integration**: Added Glide library for efficient image loading

## [1.0.1.25] - 2026-04-20

### Added
- 📱 **Landscape Layout Support**: Added landscape layouts for main screen, join chat dialog, and profile dialog
- 🔄 **Orientation Adaptation**: Improved UI adaptation for different screen orientations

## [1.0.1.24] - 2026-04-20

### Fixed
- 🔔 **Unread Count Calculation**: Fixed unread count to use `is_read` flag instead of `last_read_at` time comparison
- 🐛 **Zero Unread Count Bug**: Fixed issue where unread count was always 0 due to incorrect time comparison logic

### Changed
- 🔄 **ChatAdapter**: Added DiffUtil for efficient UI updates when chat data changes
- ⚡ **Polling Interval**: Reduced from 5 to 3 seconds for faster unread count updates
- 🔄 **Chat List Refresh**: Added onResume() to refresh chat list when returning from chat activity

## [1.0.1.23] - 2026-04-20

### Added
- 🎨 MaterialCardView for chat list items with improved styling
- 🔔 Unread count badges for chats in chat list
- 📨 Message read status indicators (sent/read icons)

### Changed
- 🛠️ Updated toolbar to MaterialToolbar from androidx.appcompat.widget.Toolbar
- 🔧 Downgraded coreKtx dependency from 1.18.0 to 1.15.0 for stability

## [1.0.1.22] - 2026-04-20

### Changed
- 📝 **Version Format**: Updated version format to include build number as fourth digit (e.g., 1.0.1.22)
- 🎨 **Default Theme**: First launch now uses dark theme instead of system default
- 🌐 **Language Sync**: Language changes in chat activities now sync when returning to main screen

### Fixed
- 🔧 **Version Display**: Removed separate versionCode, now using only versionName with build number included

## [1.0.1] - 2026-04-20

### Added
- 📱 **Chat List Activity**: Added dedicated chat list activity
  - View all user's chats in dedicated screen
  - Chat creation via long press on user
  - Smart navigation: auto-open general chat if no chats exist
- 👤 **Profile Dialog**: Added profile dialog for viewing and editing user profile
- 🎨 **New App Icon**: Updated launcher icon with custom image
- 🎨 **App Logo**: Added custom app logo (app_logo.jpg)
- 📋 **Chat Item Layout**: Added item layout for displaying chats in list
- 👥 **User Item Layout**: Added item layout for displaying users
- 🎨 **Chat List Menu**: Added menu for chat list with theme and language options

### Changed
- 🎨 **Chat Activity**: Refactored chat activity with improved UI
- 🎨 **Main Activity**: Updated main activity with new navigation flow
- 🔧 **gRPC Client**: Enhanced gRPC client with new methods
- 🔧 **Proto Definition**: Updated protobuf with new fields
- 🎨 **Launcher Icons**: Replaced PNG launcher icons with JPG versions
- 🎨 **Chat Background**: Updated chat background image
- 🎨 **Color Scheme**: Added new colors for dark theme
- 🌐 **Localization**: Updated Russian translations

## [0.9.7] - 2026-04-19

### Changed
- 📝 **Download Button Text**: Shortened download button text to "update" (EN) and "обновить" (RU) for cleaner UI.
- 🎨 **New App Icon**: Updated launcher icon with new custom image from user.

### Added
- 😀 **Message Reactions**: Added ability to react to messages with emojis (long-press on a message).
- 🆔 **Unique Message IDs**: Switched to UUIDs for reliable message tracking across sessions and reactions.
- 🔔 **Push Notifications**: Integrated Firebase Cloud Messaging (FCM) to receive notifications about new messages even when the app is in the background.
- 🔄 **Smart De-duplication**: Implemented intelligent message matching (ID/Text/User/Time) to prevent duplicate messages when they "echo" back from the server.
- 🛡️ **gRPC Field Synchronization**: Strictly synchronized field indices between Android (manual marshaling) and Go (protoc) to prevent data corruption ("krakozyabry").
- 🔄 **Server-side Message Deletion**: Messages are now deleted from the gRPC server and PostgreSQL database, not just locally.
- 🗄️ **Robust DB Migrations**: Added `COALESCE` handling for legacy database records to ensure stability during version upgrades.
- 🧹 **Persistent Local Deletion**: Deleted messages stay hidden even after app restart (stored in SharedPreferences).
- 🎨 **Modern Chat Interface**: 
    - Moved delete button from FAB to the Toolbar for a cleaner view.
    - Added rounded input field with a modern "card" style.
    - Updated app icon with new lavender branding from user image.
    - Dynamic Toolbar: Theme and language icons hide when messages are selected to focus on deletion.
    - Improved connection status indicator inside the Toolbar.
- 🚀 **Performance**: Replaced deprecated Locale and gRPC methods with modern equivalents.

## [0.9.5] - 2026-04-19

### Added
- 🚀 **Multi-selection** for message deletion: Select multiple own messages to delete at once.
- 🎨 **New App Icon**: Adaptive icon with white logo on lavender background.
- 📡 **gRPC Stability**: 
    - Fixed `INTERNAL (Error in frame handler)` and `UNAVAILABLE` errors on various Android devices.
    - Added automatic retry logic for history loading and chat stream.
    - Increased inbound message size limit to 16MB for large chat histories.
    - Optimized KeepAlive settings for better mobile network compatibility.
- 💬 **New User Welcome**: Added a friendly welcome message for new users when chat history is empty.
- ⚠️ **Error Dialogs**: Detailed, copyable error dialogs replace short toasts for easier troubleshooting.
- 🔘 **Floating Delete Button**: New trash icon FAB in the chat area for easier access.
- 📦 **Download Progress**: Horizontal progress bar shows real-time APK download status on the main screen.
- 🧼 **Modern Chat UI**: 
    - Message grouping: Hides usernames and timestamps for consecutive messages from the same user.
    - Filtered "joined" system messages for a cleaner chat log from both history and live stream.
- ⚠️ **Enhanced Toasts**: Connection errors now appear at the top of the screen with a 5-second duration.
- 🔗 **Share & Copy**: Added buttons to copy the app link to clipboard and share it via external apps.
- 🎨 **Redesigned Main Screen**: New layout with slogan "Secure messaging" and improved information hierarchy.
- 📥 Download latest version button on main screen (now links to: `http://159.195.38.145:8081/lavender.apk`)

### Fixed
- 🛠️ **APK History Loading**: Fixed a bug where history wouldn't load in release builds by enabling `android:usesCleartextTraffic`.

### Technical Details
- Multi-selection tracked in MessageAdapter with `selectedPositions: MutableSet<Int>`
- Delete button visibility controlled by selection state (shown when at least one own message is selected)
- Message grouping logic implemented in `onBindViewHolder` based on time and user proximity
- APK download implemented using `HttpURLConnection` and `lifecycleScope` for progress tracking

## [0.9.4] - 2026-04-18

### Added
- 👥 Ability to view online users in real-time
- 💾 Message history with server-side storage
- 🔄 Server integration for online users tracking
- 🔄 Server integration for message history retrieval
- 📊 Improved message persistence across sessions

### Changed
- 🔧 Updated server integration to support new features

## [0.9.3] - 2026-04-18

### Added
- 🎨 Semantic theme attributes (colorPrimary, colorOnPrimary, colorSecondary, colorOnSecondary, colorSurface, colorOnSurface)
- 🎨 Theme-aware message backgrounds (bg_message_incoming, bg_message_outgoing)
- 🎨 Message bubbles now align left (incoming) or right (outgoing) for better UX
- 🎨 Outgoing messages hide username for cleaner interface
- 🎨 Improved join dialog with better spacing and typography
- 🎨 Join dialog now uses theme-aware colors for all elements

### Changed
- 🔧 Renamed MainActivityMinimal.kt → MainActivity.kt for consistency
- 🔧 Renamed activity_main_simple.xml → activity_main.xml for consistency
- 🎨 Migrated from hardcoded colors to semantic theme attributes throughout the app
- 🎨 Toolbar background now uses colorPrimary instead of deep_purple
- 🎨 Toolbar icons and text now use colorOnPrimary for theme adaptation
- 🎨 Chat backgrounds now use ?android:attr/windowBackground for proper theme support
- 🎨 Dialog elements now use theme attributes (colorSurface, colorPrimary, etc.)
- 🗑️ Removed ChatActivitySafe activity (unused)
- 🗑️ Removed hardcoded message colors from colors.xml
- 🗑️ Removed message_background_rounded.xml (replaced with theme-aware drawables)
- 🗑️ Removed toolbar_background.xml from drawable-night (now uses single theme-aware drawable)
- 🎨 Message time format changed from HH:mm:ss to HH:mm for cleaner display
- 🎨 Message layout restructured with messageContainer for better styling
- 🎨 MessageAdapter now uses theme attributes for text colors (textColorPrimary, textColorPrimaryInverse)
- 🎨 All icons now use ?attr/colorOnPrimary for theme adaptation
- 🎨 Join dialog buttons now use theme-aware colors (colorPrimary, colorSecondary)
- 🎨 Server status refresh button now uses colorPrimary
- 🔧 Removed unused R import from ChatActivity.kt
- 🔧 Updated ChatActivity logout intent to use MainActivity
