# Changelog

All notable changes to Lavender Messenger (Android client) will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.0.2.0] - 2026-04-26

### Added
- 🎨 **Professional Theme Editor**:
  - New dedicated `EditThemeActivity` with tab-based navigation (Main, Colors, Background).
  - **Live Preview**: Minimized real-time preview of Chat List and Chat screens at the top.
  - **Visual Backgrounds**: Miniature image previews instead of URLs, with full-screen view and server-side deletion.
  - **Color Picker**: Integrated palette for quick color selection by tapping color circles.
  - **Descriptive Labels**: Color settings now explicitly state their usage (e.g., "Toolbar", "Outgoing Bubbles").
  - **Edit Icons**: Added visible edit and delete icons to the theme list for better discoverability.
- ℹ️ **Enhanced About Dialog**:
  - Modern redesign with app logo and structured version information.
  - Displays both **Client and Server versions** for better debugging.
  - Integrated "Update Now" button and developer feedback action (email draft).
- 🔔 **Advanced Notification Center**:
  - New dedicated activity for managing notification settings and history.
  - **Privacy Controls**: Options to disable incoming push notifications and "Notify others" (outgoing push control).
  - **Notification Log**: Structured incoming/outgoing history tabs for tracking delivery.
- 👑 **Server-Controlled Super Admin**:
  - Permissions are now managed server-side via `is_super_admin` flag.
  - Enlarged avatars (64dp) and click-to-view profile logic in the admin user list.
- 🚪 **Secure Logout**:
  - Renamed to "Log out of profile" with a mandatory confirmation dialog and permanent credential clearing warning.

### Fixed
- 🔌 **Connection Stability**:
  - Optimized gRPC keepalive parameters to be more tolerant of mobile network fluctuations.
  - Improved server-side Hub logic to reduce redundant status broadcasts and channel noise.
  - Fixed a deadlock issue where menu items could become permanently disabled.
- 🛠️ **UI Refinement**:
  - Restored classic overflow dots (three vertical dots) to the toolbar.
  - Rearranged toolbar icons: Contacts back to panel, Search to overflow for better spacing.
  - Fixed theme tinting issues where the overflow menu icon wouldn't follow theme colors.
  - Improved contacts selection contrast in Light theme.
  - Corrected `ic_settings_brightness` and updated `ic_contacts` design.
- 🧹 **Storage Management**:
  - Implemented automatic cleanup of theme background files from the server upon theme or profile deletion.

## [1.0.1.60] - 2026-04-26
... (keeping the rest)
