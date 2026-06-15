# Заметки сессии 11 — 2026-06-16

## Что сделано

### ChatStream v2 (сервер)
- `messenger.proto`: добавлен `jwt_token` (field 26) в Message
- `server_chat.go`: Chat stream поддерживает `jwt_token` (v2) + `password` (v1 fallback)
- При JWT auth: извлекает user_id и username из claims, валидирует токен
- При password auth: полная обратная совместимость с v1
- `ChatServiceVersion` = "2.0"

### ChatList v2 (сервер)
- `messenger.proto`: добавлены RPC методы PinChat, UnPinChat, SearchChats, ArchiveChat, UnarchiveChat
- `messenger.proto`: добавлены `is_pinned`, `is_muted`, `is_archived`, `pinned_at` в ChatInfo
- `messenger.proto`: добавлены `limit`, `offset`, `filter` в GetChatsRequest
- `server_chatlist_v2.go`: реализация всех новых RPC методов
- `db_chatlist_v2.go`: миграции (user_chat_metadata: pinned/pinned_at/archived), методы DB

### ProfileClient (Android)
- `fetchServerInfo()`: парсит все версии сервисов (chat/auth/profile/ai)
- Добавлены `isChatV2Supported()`, `isAuthV2Supported()`
- Fallback на v1 если /info недоступен

### BearerTokenInterceptor (Android)
- Убран пропуск Chat stream для v2 серверов
- Token теперь прикрепляется к Chat stream если `isChatV2Supported()`

### RealGrpcClient (Android)
- `startChat()`: использует JWT token для v2, password для v1
- Добавлены `pinChat()`, `unpinChat()`, `searchChats()`, `archiveChat()`, `unarchiveChat()`
- Добавлен `unaryCallChatListV2()` — низкоуровневый gRPC unary call helper

### GrpcClient (Android)
- Публичные методы `pinChat()`, `unpinChat()`, `searchChats()`, `archiveChat()`, `unarchiveChat()`
- `isChatV2Supported`, `chatServiceVersion`

### MessengerProto.kt / Message.kt
- Добавлены `PinChatRequestProto`, `SearchChatsResponseProto` и другие ChatList v2 proto classes
- `ChatInfoProto`: добавлены `isPinned`, `isMuted`, `isArchived`, `pinnedAt`
- `GetChatsRequestProto`: добавлены `limit`, `offset`, `filter`
- `MessageProto`: добавлены `jwtToken`, `isE2Ee`, `e2EePayload` + Builder методы

### Pitfalls learned
- Kotlin 2.3.21 / coroutines 1.11: `CancellableContinuation.resume()` имеет deprecated параметр `onCancellation`
- Необходимо всегда передавать `onCancellation = {}` при вызове `cont.resume()`
- `import kotlinx.coroutines.suspendCancellableCoroutine` (не `kotlin.coroutines`)

## Коммиты
- Сервер: `0daf87b`, `5d35914`
- Android: `cd2294d`, `cc759b7`, `a4a29ae`, `bfe0412`, `f15500f`, `ff6bba2`, `cb1cf84`, `8731367`, `f15500f`

## Следующие шаги
1. Тестирование на dev сервере (после деплоя)
2. Исправление warning'ов (deprecated `resume()` → `resumeWith()`)
3. Обновление документации PATTERNS.md
