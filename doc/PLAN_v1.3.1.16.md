# Plan: v1.3.1.16 Architecture Stability Audit

**Статус:** ✅ Выполнено | **Дата:** 2026-07-02 | **Ветка:** feat/1.3.1.x

---

## Контекст

Полный аудит стабильности и чистоты архитектуры. Запланировано 8 областей анализа — все выполнены.

---

## Выполненные работы

### 1. Message Disappearing Bug ✅
- `GrpcMessageV2Client.kt:478-483` — `getContentHash`/`getMessageHash` теперь используют `userId` (UUID) вместо `user` (username)

### 2. Thread Safety ✅ (15 фиксов)
- `@Volatile` добавлен к 15 полям в RealGrpcClient, GrpcConnectionManager, GrpcCallClient, GrpcTypingClient, GrpcMessageV2Client
- `allChats` в ChatListViewModel — write перемещён на Main thread

### 3. Memory Leaks ✅ (4 фикса)
- `CallActivity.kt` — `callController?.cancel()` в onDestroy
- `GrpcConnectionManager.kt` — `context?.applicationContext` в reconnect lambda
- `AIBottomSheet.kt` — `agentScope.cancel()` на dismiss
- `CallActivity.kt` — Thread → lifecycleScope + isFinishing guard

### 4. Error Handling ✅ (23 фикса)
- 16 замен в ChatListViewModel: `Log.e`/`Log.w` → `ErrorHandler.handle()`
- 2 замены в SessionManager
- 2 замены в UpdateManager
- 3 gRPC silent swallow fixes в GrpcMessageV2Client

### 5. gRPC Resilience ✅ (5 фиксов)
- Typing/call stream retry loops: exponential backoff + max 10 + connection check
- `isAuthFailure` flag: set on failure, checked in scheduleReconnect

### 6. Room DB ✅ (1 фикс)
- `@ColumnInfo(index = true)` на `MessageEntity.roomId`
- Migration 11→12: `CREATE INDEX`

### 7. UI Consistency ✅ (2 фикса)
- `setDecorFitsSystemWindows` добавлен в ChatListActivity и ConferenceLobbyActivity

### 8. Документация ✅ (6 файлов)
- CHANGELOG.md, PROMPT_NEXT_SESSION.md, INDEX.md, PATTERNS.md, GOTCHAS.md, SESSION_SUMMARY_v1.3.1.16.md

---

## Файлы (21)

```
app/src/main/java/lavender/client/android/data/grpc/GrpcMessageV2Client.kt
app/src/main/java/lavender/client/android/data/grpc/RealGrpcClient.kt
app/src/main/java/lavender/client/android/data/grpc/GrpcConnectionManager.kt
app/src/main/java/lavender/client/android/data/grpc/GrpcCallClient.kt
app/src/main/java/lavender/client/android/data/grpc/GrpcTypingClient.kt
app/src/main/java/lavender/client/android/data/grpc/GrpcAuthClient.kt
app/src/main/java/lavender/client/android/ui/chatlist/ChatListViewModel.kt
app/src/main/java/lavender/client/android/ui/chatlist/ChatListActivity.kt
app/src/main/java/lavender/client/android/ui/widget/AIBottomSheet.kt
app/src/main/java/lavender/client/android/data/session/SessionManager.kt
app/src/main/java/lavender/client/android/data/updates/UpdateManager.kt
app/src/main/java/lavender/client/android/data/db/Entities.kt
app/src/main/java/lavender/client/android/data/db/AppDatabase.kt
app/src/main/java/lavender/client/android/CallActivity.kt
app/src/main/java/lavender/client/android/ConferenceLobbyActivity.kt
CHANGELOG.md
doc/PROMPT_NEXT_SESSION.md
doc/INDEX.md
doc/PATTERNS.md
doc/GOTCHAS.md
doc/SESSION_SUMMARY_v1.3.1.16.md
```

---

## Оставшаяся работа (не критично)

- ~40 AiV2ChatUseCase ErrorHandler миграций
- 13 hardcoded hex colors в Kotlin, 26 в XML
- 9+ activities без setDecorFitsSystemWindows
- 5 unused params, clearSelection() dead code
- gRPC: SERVER_SHUTTINGDOWN dead-end, CANCELLED triggers reconnect
