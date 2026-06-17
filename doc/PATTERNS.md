# Android — Паттерны и правила разработки

**Версия:** v1.1.3.34 | **Обновлено:** 2026-06-17

---

## Паттерны

### gRPC Client Modular Pattern
```
RealGrpcClient (orchestrator, 882 LOC) делегирует в:
├── GrpcConnectionManager (167) — connect/reconnect/disconnect
├── GrpcAuthClient (232) — JWT auth
├── GrpcTypingClient (87) — typing stream
├── GrpcCallClient (125) — calls
├── GrpcChatListClient (638) — chat list, pin/search/archive
├── GrpcProfileClient (506) — profile, avatar, contacts, themes
├── GrpcDraftClient (86) — drafts
├── GrpcFavoritesClient (120) — favorites
├── GrpcMessageClient (341) — messages, history, reactions, mark read
├── GrpcServerDiscoveryClient (145) — server discovery
└── GrpcMarshallers (1394) — 111 marshaller classes
```
- Каждый модуль — отдельный класс с чёткой ответственностью
- DI через конструктор (без фреймворка)
- RealGrpcClient: StateFlow declarations → module init → chat stream → proxy-методы
- **КРИТИЧНО:** StateFlow объявляются ДО модулей (Kotlin object top-to-bottom init)

### ChatListActivity Modular Pattern
```
ChatListActivity (~600) — onCreate, setupUI, lifecycle, proxy methods
├── ChatListToolbar (232) — toolbar + settings sheets
├── ChatListTabs (30) — tabs (All/Groups/AI Chats)
├── ChatListActionMode (120) — selection mode
├── ChatListSearch (56) — search
├── ChatListFABs (470) — FABs + action sheets + AI bottom sheet
├── ChatListNavigation (60) — navigateToChat
├── ChatListAuth (212) — auth dialogs
├── ChatListViewModel (290) — ViewModel
├── ChatListSections (20) — sections
└── UpdateCoordinator (245) — updates
```
- При выносе: `internal` для полей/методов, прокси-методы в Activity
- Top-level internal fun файлы в том же пакете — нужны явные импорты

### UpdateCoordinator Pattern
Activity → Coordinator → Manager → Utils. Activity только создаёт, наблюдает StateFlow, делегирует UI.

### StandardBottomSheet Pattern
Все шторки наследуют `StandardBottomSheet`: ServerAuth, Login, Register, AI, NewChat.
Drag handle автоматически. Dismiss listener через `setOnDismissListener`.

### Bearer Token Interceptor Pattern
- Подставляет JWT во все gRPC вызовы (кроме AuthService, Chat stream)
- No-op если токен null (совместимость с v1)
- Proactive refresh каждые 60с, за 5 мин до истечения
- Per-server validation: токены привязаны к серверу

### Server Switch Pattern
- `serverAddress` сохраняется ТОЛЬКО после успешного `SessionManager.login()`
- НЕ сохранять до входа — двойной вход

### ChatStream v2 Auth Pattern
```kotlin
if (ProfileClient.isChatV2Supported()) {
    token?.let { builder.setJwtToken(it) } ?: builder.setPassword(password)
} else {
    builder.setPassword(password)
}
```

### Connection Readiness Pattern
- Optimistic READY сразу после `builder.build()`
- Keepalive: 30s interval, 10s timeout, idleTimeout 25min
- Reconnect только при FAILED, не при shutdownNow

### Server Version Detection
- Dev (50052): skip HTTP, assume v2
- Prod (50051): try HTTP /info, fallback v1

### getChats() Callback Pattern
- Всегда вызывать callback (success/error/timeout)
- Poll interval: 30s
- При timeout: НЕ перезаписывать allChats, логировать warning

### Read Receipts Broadcast Pattern
```
Server: MarkRead → Broadcast("READ_ALL:username") → Hub → All clients
  → RealGrpcClient.chatStream → handleReadAllSignal()
  → GrpcMessageClient.onReadReceipt(targetRoomId, reader)
  → RealGrpcClient._readReceiptEvent.emit(Pair(roomId, reader))
  → ChatListViewModel → clear unread count
```

---

## Правила

### Kotlin
- `is` не `instanceof`; прямой доступ к proto полям: `proto.fieldName`
- `cont.resume(value, onCancellation = {})` — обязательно в Kotlin 2.3.21
- CancellationException ловить ОТДЕЛЬНО, re-throw, НЕ показывать toast
- Channel(UNLIMITED) + flow{} + trySend() вместо callbackFlow/awaitClose

### Error Handling
- Все Toast ошибки дублировать в AppLog.error()
- CancellationException → AppLog.info() (не ERROR)
- gRPC StatusRuntimeException → AppLog.error() с кодом статуса

### Темы
- НЕ использовать `?attr/` в XML для текста на кастомных тёмных темах
- Цвета программно через ThemeUtils.parseSafeColor()
- ThemeApplier.apply() ДО setContentView()

### Сборка
- ⚠️ НЕ компилировать Android на сервере (OOM)
- `compileDebugKotlin` / `assembleRelease` — ТОЛЬКО локально

### i18n (ОБЯЗАТЕЛЬНО)
- ВСЕ user-facing строки в `values/strings.xml` (en) + `values-ru/strings.xml` (ru)
- Приложение: "Lava" (en) / "Лава" (ru)
- НЕ инициализировать getString() в полях Activity (crash до onCreate)
- Проверка: `grep -rn '"[А-Яа-я]' app/src/main/java/ --include="*.kt" | grep -v "R\.string"` → 0 результатов

### Версии
- Версия Android в `version.txt`
- НЕ менять версию без явного указания пользователя

---

## Серверы

| | Dev | Prod |
|--|-----|------|
| gRPC | 50052 | 50051 |
| HTTP | 8083 | 8082 |
| Сервис | lavender-server-dev | lavender-server |
| DB | chat_db_dev | chat_db |

---

## ID Naming Convention

| Префикс | Тип | Пример |
|---------|-----|--------|
| `btn_` | Кнопки | `btnSend` |
| `et_` | Поля ввода | `etSearch` |
| `tv_` | Текст | `tvChatName` |
| `iv_` | Изображения | `ivAvatar` |
| `rv_` | RecyclerView | `rvChatList` |
| `fab_` | FAB | `fabAi` |
