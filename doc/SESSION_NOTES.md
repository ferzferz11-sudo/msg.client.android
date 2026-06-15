# Заметки сессии 11 — 2026-06-16

## Что сделано

### ChatStream v2 (сервер)
- `messenger.proto`: добавлен `jwt_token` (field 26) в Message
- `server_chat.go`: Chat stream поддерживает `jwt_token` (v2) + `password` (v1 fallback)
- `ChatServiceVersion` = "2.0"

### ChatList v2 (сервер)
- `messenger.proto`: PinChat, UnPinChat, SearchChats, ArchiveChat, UnarchiveChat RPC
- `messenger.proto`: is_pinned, is_muted, is_archived, pinned_at в ChatInfo
- `messenger.proto`: limit, offset, filter в GetChatsRequest
- `server_chatlist_v2.go`: реализация RPC методов
- `db_chatlist_v2.go`: миграции + методы DB

### ChatStream v2 + ChatList v2 (Android)
- ProfileClient: fetchServerInfo парсит все версии, isChatV2Supported()
- BearerTokenInterceptor: убран пропуск Chat stream для v2
- RealGrpcClient.startChat(): JWT для v2, password для v1
- RealGrpcClient: pinChat, unpinChat, searchChats, archiveChat, unarchiveChat
- GrpcClient: публичные методы ChatList v2
- MessengerProto.kt: новые proto classes
- ChatInfo: isPinned, isArchived, pinnedAt
- MessageProtoMarshaller: jwt_token, isE2Ee, e2EePayload

### Документация
- INTEGRATION_SESSION.md: полный рефакторинг
- PROMPT.md, PROMPT_ANDROID.md: обновлены
- CHANGELOG.md (сервер + Android): обновлены
- PATTERNS.md: новые паттерны (Kotlin 2.3.21, fetchServerInfo, ChatStream v2, ChatList v2)
- INDEX.md (сервер + Android): обновлены
- TASKS.md (сервер + Android): обновлены

## Pitfalls learned
- Kotlin 2.3.21 / coroutines 1.11: `CancellableContinuation.resume()` требует `onCancellation = {}`
- `import kotlinx.coroutines.suspendCancellableCoroutine` (не `kotlin.coroutines`)
- data class с `repeated` proto полем: `List<T>` напрямую (не `getXxxList()`)
- `suspendCancellableCoroutine` с дженериками: нужен явный тип `CancellableContinuation<T>`

## Коммиты
Сервер: `0daf87b`, `840a708`, `de3d55d`, `88cf8d4`
Android: `cd2294d`, `cc759b7`, `a4a29ae`, `bfe0412`, `f15500f`, `ff6bba2`, `cb1cf84`, `8731367`, `5bb47b6`, `7c872f1`

## Следующие шаги
1. ChatList v2 UI (ChatListActivity v2)
2. Тесты для ProfileService v2, ChatStream v2, ChatList v2
3. Деплой prod сервера (после Android)
