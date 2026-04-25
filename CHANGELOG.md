# Changelog

All notable changes to Lavender Messenger (Android client) will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.0.1.60] - 2026-04-26

### Added
- 👑 **Super Admin Panel**:
  - Exclusive access for user `ferz` to manage all server users and groups.
  - Global user search and deletion with automatic session termination (`FORCE_DISCONNECT`).
  - Global group search and management to clean up abandoned or incorrect rooms.
- 👑 **Admin Status Visibility**: Added a new settings (gear) icon on the right side of the chat card for group administrators.
- 🛡️ **Improved Group Deletion Flow**:
  - Deletion now transitions back to the chat list immediately, performing the task in the background with a main preloader.
  - This fixes issues with hanging screens and ensures a safe return to the main interface.
- 🛡️ **Selective Multi-Delete**:
  - In selection mode, irrelevant toolbar icons are hidden.
  - Restrictions prevent deleting groups where you are not an administrator, with immediate toast feedback.
- 📝 **Group Name Management**:
  - Default group name is now "Group" (or "Группа").
  - Admins can now rename groups by tapping the name in Group Info or using the gear icon.

### Fixed
- 🛡️ **Admin Safety**: Restricted the "Delete Group" button to group admins only to prevent crashes and unauthorized attempts.
- 🔄 **FAB Animation Fix**: Resolved issue where the "Add Chat" floating button wouldn't stop spinning.
- 📍 **Theme-Aware Icons**: Location and file icons now correctly adapt their colors to both light and dark themes.

## [1.0.1.59] - 2026-04-26
... (keeping the rest)
