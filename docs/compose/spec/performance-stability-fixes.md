---
feature: performance-stability-fixes
status: delivered
updated: 2026-08-13
branch: feat/perf-stability
commits: 68915f1..HEAD
---

# Performance & Stability Fixes — Android Client

## Report

**What was built** — 10 критических исправлений стабильности и производительности Android-клиента. Устранены ANR-риски (Thread.sleep polling → CountDownLatch), race conditions (chatV2RequestObserver → synchronized), баг reconnect backoff (60s cap → 30s), неоптимальный I/O (SharedPreferences debounce, DB index, avatar cache debounce), и ресурсные утечки (shutdownNow для forced reconnect, guard для loadDeletedMessages). Удалён мёртвый код (isRetrying). Убран оптимистичный READY статус — теперь подтверждается первым ChatV2 ответом.

**Verification** — `./gradlew assembleDebug` BUILD SUCCESSFUL, `./gradlew test` 596 tests passed (1 test updated for new CONNECTING behavior).

**Journey log:**
- Thread.sleep polling в waitForRefreshComplete заменён на CountDownLatch — безопасно для любого потока
- chatV2RequestObserver теперь защищён synchronized блоком при всех read/write операциях
- Reconnect backoff cap исправлен: единый 30s лимит вместо 60s
- SharedPreferences запись для deleted_hashes debounce 500ms — одна запись на пачку удалений
- Room migration v17 добавляет индекс на deleted_messages.deletedAt

## [S1] Problem
Android-приложение имеет несколько критических проблем стабильности и производительности: race conditions при работе с gRPC стримами, ANR-риски из-за блокировки Main-потока, неоптимальные I/O паттерны (запись в SharedPreferences на каждое удаление сообщения), баг в reconnect backoff, и утечки ресурсов при переподключении.

## [S2] Design

### 2.1 ANR: Thread.sleep в waitForRefreshComplete
**Файл:** `SessionManager.kt:112-121`
**Проблема:** `Thread.sleep(100)` в цикле polling блокирует вызывающий поток. Хотя метод аннотирован `@WorkerThread`, нет защиты от вызова с Main.
**Решение:** Заменить polling на `CountDownLatch` или `suspendCancellableCoroutine` с таймаутом. Добавить явную проверку Main thread (уже есть в `ensureFreshToken`, но нет в `waitForRefreshComplete`).

### 2.2 Race condition: chatV2RequestObserver
**Файл:** `RealGrpcClient.kt:301,452-471,737,750`
**Проблема:** `chatV2RequestObserver` — `@Volatile var`, читается/пишется из gRPC callback thread и coroutine scope без синхронизации. `onNext` в response observer и `startChatV2` могут одновременно модифицировать observer.
**Решение:** Обернуть в `synchronized` при создании и null-сбросе. Или использовать `AtomicReference`.

### 2.3 Reconnect backoff: cap mismatch
**Файл:** `GrpcReconnectStrategy.kt:41-44`
**Проблема:** `delayMs.coerceAtMost(30000L)` cap для текущей задержки, но `reconnectDelayMs` может вырасти до 60000L. После cap на 30s, следующий `reconnectDelayMs` удваивается до 60s, но снова cap на 30s — backoff «залипает» на 30s навсегда.
**Решение:** Единый cap: `reconnectDelayMs = (reconnectDelayMs * 2).coerceAtMost(30000L)` и использовать `reconnectDelayMs` напрямую.

### 2.4 Dead code: isRetrying
**Файл:** `RealGrpcClient.kt:303,459`
**Проблема:** `isRetrying` объявлен, проверяется в `startChatV2`, но нигде не устанавливается в `true`. Логика reconnect не работает как задумано.
**Решение:** Удалить `isRetrying` и связанную проверку, либо правильно использовать для предотвращения дублирования reconnect.

### 2.5 Disk I/O на каждое удаление: addDeletedHash
**Файл:** `RealGrpcClient.kt:140-157`
**Проблема:** Каждый вызов `addDeletedHash` делает `SharedPreferences.edit().putStringSet().apply()` + Room INSERT. При получении пачки DELETE_MESSAGE_V2 это N записей на диск.
**Решение:** Debounce SharedPreferences запись (500ms). Room INSERT уже в корутине — нормально. Добавить индекс на `deleted_messages.deletedAt` для cleanup-запроса.

### 2.6 Missing DB index: deleted_messages.deletedAt
**Файл:** `AppDatabase.kt:245-256`, `Daos.kt:162`
**Проблема:** `cleanupOlderThan(before)` делает `DELETE FROM deleted_messages WHERE deletedAt < :before` без индекса. Full table scan при cleanup.
**Решение:** Добавить `CREATE INDEX IF NOT EXISTS index_deleted_messages_deletedAt ON deleted_messages (deletedAt)` в миграцию или новую миграцию (version 17).

### 2.7 Channel shutdown: in-flight calls leak
**Файл:** `GrpcConnectionManager.kt:174`
**Проблема:** `oldChannel?.shutdown()` — graceful, ждёт завершения in-flight вызовов. Если вызов завис, reconnect блокируется.
**Решение:** Использовать `shutdownNow()` вместо `shutdown()` при принудительном reconnect. Для обычного disconnect оставить `shutdown()`.

### 2.8 loadDeletedMessages на каждое подключение
**Файл:** `RealGrpcClient.kt:359,984-998`
**Проблема:** `loadDeletedMessages()` вызывается в `connect()`, загружая все хеши из SharedPreferences + Room при каждом подключении. Дублирование если уже загружены.
**Решение:** Добавить guard: загружать только если `deletedMessageHashes.isEmpty()`.

### 2.9 Avatar cache: лишние копии
**Файл:** `RealGrpcClient.kt:953-957`
**Проблема:** `avatarCacheFlow.value = avatarCache.toMap()` создаёт полную копию карты при каждом обновлении аватара. Если обновляется 10 аватаров подряд — 10 копий.
**Решение:** Debounce: обновлять flow не чаще раза в 500ms.

### 2.10 buildChannel: оптимистичный READY
**Файл:** `GrpcConnectionManager.kt:189-192`
**Проблема:** `connectionStatus.value = ConnectionStatus.READY` устанавливается сразу после `build()`, до фактического подключения. Если сервер недоступен, UI показывает READY, а gRPC вызовы падают.
**Решение:** Убрать оптимистичный READY. Вместо этого — CONNECTING, а READY устанавливать при первом успешном response в ChatV2 stream (уже есть логика в `startChatV2Stream`).

## [S3] Out of Scope
- ProGuard/R8 включение
- Серверные изменения (задачи в `/Users/paveld/LavenderMessenger-server/doc/`)
- Новые фичи
- UI/UX изменения
- Рефакторинг архитектуры (MVVM → Compose и т.д.)

## Tasks
- [x] T1: Fix ANR risk in `waitForRefreshComplete` — replace Thread.sleep polling with CountDownLatch/suspendCancellableCoroutine (covers: S2.1; acceptance: no Thread.sleep in SessionManager)
- [x] T2: Thread-safe `chatV2RequestObserver` — wrap creation/null-reset in synchronized block (covers: S2.2; acceptance: no concurrent modification of observer)
- [x] T3: Fix reconnect backoff cap mismatch — single 30s cap on reconnectDelayMs (covers: S2.3; acceptance: backoff never exceeds 30s)
- [x] T4: Remove dead `isRetrying` field or wire it correctly (covers: S2.4; acceptance: field removed or functional)
- [x] T5: Debounce `addDeletedHash` SharedPreferences write — batch writes with 500ms delay (covers: S2.5; acceptance: single SP write per batch of deletes)
- [x] T6: Add DB index on `deleted_messages.deletedAt` — new migration v17 (covers: S2.6; acceptance: index exists, cleanup query uses it)
- [x] T7: Use `shutdownNow()` for forced reconnects in GrpcConnectionManager (covers: S2.7; acceptance: reconnect doesn't hang on stuck calls)
- [x] T8: Guard `loadDeletedMessages` — skip if already loaded (covers: S2.8; acceptance: no duplicate loading on reconnect)
- [x] T9: Debounce `avatarCacheFlow` updates (covers: S2.9; acceptance: flow updates batched at 500ms)
- [x] T10: Remove optimistic READY in `activateChannel` — stay CONNECTING until first ChatV2 response (covers: S2.10; acceptance: READY only after confirmed stream)
