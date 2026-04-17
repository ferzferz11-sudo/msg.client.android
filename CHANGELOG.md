# Changelog

All notable changes to MSG Android Client will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Planned
- Push notifications support
- Dark theme implementation
- Message encryption
- File sharing functionality
- Voice messages

## [1.0.0] - 2024-04-17

### Added
- 🎉 Initial release of MSG Android Client
- 📱 Basic messaging functionality
- 🔐 User authentication system
- 💾 Local message storage
- 🎨 Modern Material Design UI
- 🔄 Real-time message synchronization
- 📱 ViewBinding implementation
- 🧭 Navigation Component integration
- 🏗️ MVVM architecture setup
- 🧪 Unit and instrumentation test framework

### Technical Details
- **Min SDK**: 29 (Android 10.0)
- **Target SDK**: 36 (Android 15)
- **Language**: Kotlin
- **Architecture**: MVVM with ViewBinding
- **Dependencies**:
  - AndroidX Core KTX
  - AndroidX AppCompat
  - Material Design Components
  - ConstraintLayout
  - Navigation Component

### Configuration
- Gradle build system with Kotlin DSL
- Version code: 1
- Version name: 1.0
- Package name: msg.client.android

---

## Version History Guidelines

### Version Format
- **MAJOR**: Breaking changes, major feature additions
- **MINOR**: New features, improvements
- **PATCH**: Bug fixes, small improvements

### Categories
- **Added**: New features
- **Changed**: Changes in existing functionality
- **Deprecated**: Features that will be removed in future versions
- **Removed**: Features removed in this version
- **Fixed**: Bug fixes
- **Security**: Security-related changes

### Release Process
1. Update version in `app/build.gradle.kts`
2. Update version code and version name
3. Add changelog entry
4. Create release tag
5. Build and distribute release APK
