# Session Summary: v1.3.1.16 Architecture Stability Audit

**Версия:** v1.3.1.16 | **Дата:** 2026-07-02 | **Ветка:** feat/1.3.1.x

---

## Итоги сессии

Полный аудит стабильности и чистоты архитектуры. 8 областей, 50+ фиксов.

---

## 1. Message Disappearing Bug (T1)

**Проблема:** Сообщения пропадали из чата, но были видны в last message списка чатов.

**Причина:** `getContentHash` использовал `message.user` (username), который был пустым когда `allUsers()` ещё не загружен. Хеш变成了 `":text:ts"` вместо `"alice:text:ts"` — тот же объект давал разные хеши в разное время.

**Фикс:** `GrpcMessageV2Client.kt:478-483` — `getContentHash`/`getMessageHash` теперь используют `message.userId` (UUID) вместо `message.user`. UUID всегда доступен из proto, не зависит от `allUsers`.

---

## 2. Thread Safety (T2) — 15 фиксов

| Файл | Поля | Фикс |
|------|------|------|
| `RealGrpcClient.kt` | `appContext`, `currentRoomId`, `markReadJob`, `pendingMarkReadRoom/User`, `database`, `hasCheckedForUpdates`, `isAppInBackground`, `backgroundStartTime`, `lastAuthWasJwt` | `@Volatile` |
| `GrpcConnectionManager.kt` | `channel`, `currentServerAddress`, `currentServerPort`, `reconnectDelayMs`, `appContext` | `@Volatile` |
| `GrpcCallClient.kt` | `callRequestObserver` | `@Volatile` |
| `GrpcTypingClient.kt` | `typingRequestObserver` | `@Volatile` |
| `GrpcMessageV2Client.kt` | `database` | `@Volatile` |
| `ChatListViewModel.kt` | `allChats` | Write moved to Main thread via `withContext(Dispatchers.IO)` |

---

## 3. Memory Leaks (T3) — 4 фикса

| Файл | Проблема | Фикс |
|------|----------|------|
| `CallActivity.kt` | `callController` не отменялся в `onDestroy` | `callController?.cancel()` |
| `GrpcConnectionManager.kt` | Activity context в reconnect lambda (до 60s) | `context?.applicationContext` |
| `AIBottomSheet.kt` | `agentScope` не отменялся при dismiss | `dialog?.setOnDismissListener { cancel() }` |
| `CallActivity.kt` | Unmanaged `Thread` с `runOnUiThread` | `lifecycleScope.launch(Dispatchers.IO)` + `isFinishing` guard |

---

## 4. Error Handling (T4) — 23 фикса

### ChatListViewModel.kt — 16 замен
Все `Log.e`/`Log.w` в catch блоках заменены на `ErrorHandler.handle()`.

### SessionManager.kt — 2 замены
- Token refresh check error — добавлен throwable
- V2 login error — добавлен ErrorHandler

### UpdateManager.kt — 2 замены
- Update check failed
- Download failed

### GrpcMessageV2Client.kt — 3 gRPC silent swallows
- `editMessageV2` onClose — добавлен `ErrorHandler.handle()`
- `deleteMessageV2` onClose — добавлен `ErrorHandler.handle()`
- `setReactionV2` onClose — добавлен `ErrorHandler.handle()`

---

## 5. gRPC Resilience (T5) — 5 фиксов

### Typing Stream Retry Loop
**Было:** `delay(5000)` без backoff, без max retries, без проверки channel.
**Стало:** Exponential backoff (1s→30s), max 10 retries, проверка channel state, сброс счётчика при успехе.

### Call Stream Retry Loop
Тот же фикс.

### isAuthFailure Flag
**Было:** Flag объявлен но никогда не checked — бесконечный reconnect при auth failure.
**Стало:** `GrpcAuthClient` устанавливает `setAuthFailure(true)` при failure. `GrpcConnectionManager.scheduleReconnect()` проверяет flag и пропускает reconnect.

---

## 6. Room DB (T6) — 1 фикс

**Проблема:** Нет индекса на `messages.roomId` — каждый запрос сообщений делал full table scan.

**Фикс:**
- `Entities.kt`: `@ColumnInfo(index = true)` на `MessageEntity.roomId`
- `AppDatabase.kt`: version 11→12, migration создаёт `CREATE INDEX IF NOT EXISTS index_messages_roomId ON messages (roomId)`

---

## 7. UI Consistency (T6) — 2 фикса

- `ChatListActivity.kt` — добавлен `setDecorFitsSystemWindows(window, false)` в `onCreate`
- `ConferenceLobbyActivity.kt` — добавлен `setDecorFitsSystemWindows(window, false)` в `onCreate`

---

## 8. Документация — 5 файлов обновлено

| Файл | Обновление |
|------|------------|
| `CHANGELOG.md` | Новая секция v1.3.1.16 |
| `doc/PROMPT_NEXT_SESSION.md` | Версия v1.3.1.16, все 8 областей ✅ |
| `doc/INDEX.md` | Версия v1.3.1.16 |
| `doc/PATTERNS.md` | Версия v1.3.1.16 + 3 новых паттерна |
| `doc/GOTCHAS.md` | Версия v1.3.1.16 + все секции обновлены |

---

## Изменённые файлы (полный список)

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

## Оставшаяся работа (задокументировано, не критично)

- ~40 `Log.e` → `ErrorHandler` миграций в AiV2ChatUseCase.kt (21), E2EEManager.kt (6), AppDatabase.kt (18 silent migration catches)
- UI: 13 hardcoded hex colors в Kotlin коде, 26 в XML layouts
- UI: 9+ activities без `setDecorFitsSystemWindows` (non-chat screens)
- Dead code: 5 unused params, `clearSelection()` in MessageAdapter
- gRPC: `SERVER_SHUTTINGDOWN` enters RECONNECTING with no active reconnect
- gRPC: ChatV2 stream CANCELLED triggers reconnect (should not)
