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
- Multiple chat rooms
- User presence indicators

## [2.0.0] - 2026-04-17

### Added
- ✅ Working bidirectional gRPC streaming
- ✅ RealGrpcClient with custom MessageProtoMarshaller
- ✅ Manual ClientCall setup for bidirectional streaming
- ✅ Duplicate message detection (prevents echo by checking last 5 messages within 2 seconds)
- ✅ Real-time connection status tracking (Connecting/Connected/Disconnected)
- ✅ Join message system with welcome responses
- ✅ Atomic message list updates with StateFlow
- ✅ Proper cleanup with shutdownNow() on disconnect
- ✅ isChatStarted flag to prevent duplicate stream creation
- ✅ Keep-alive configuration (30s intervals, 5s timeout)
- ✅ Go server integration (localhost:50051 / 10.0.2.2:50051 for emulator)

### Changed
- Updated Target SDK from 36 to 37
- Updated Compile SDK to 37
- Replaced simulation with real gRPC bidirectional streaming
- Improved error handling for connection failures
- Enhanced message processing with duplicate filtering

### Technical Details
- **gRPC**: Custom marshaller using CodedOutputStream/CodedInputStream
- **Streaming**: Manual ClientCall.newCall() with method descriptor
- **Pattern**: StreamObserver for request/response
- **State Management**: StateFlow for reactive UI updates
- **Concurrency**: Coroutines for async operations
- **Dependencies**: grpc-okhttp, grpc-protobuf-lite, grpc-stub

### Server Integration
- Connects to Go gRPC server at localhost:50051
- Server receives messages via bidirectional stream
- Server broadcasts messages back to all clients
- Join messages trigger welcome responses from server

### Fixed
- Fixed message echo issue with duplicate detection
- Fixed connection cleanup with proper shutdown
- Fixed stream recreation on repeated startChat calls

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
- **Target SDK**: 37 (Android 14)
- **Compile SDK**: 37
- **Language**: Kotlin
- **Architecture**: MVVM with ViewBinding
- **Dependencies**:
  - AndroidX Core KTX
  - AndroidX AppCompat
  - Material Design Components
  - ConstraintLayout
  - Navigation Component
  - gRPC: grpc-okhttp, grpc-protobuf-lite, grpc-stub

### Configuration
- Gradle build system with Kotlin DSL
- Version code: 2
- Version name: 2.0
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
