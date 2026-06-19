# Prompt: Android Client — Next Session

**Версия:** v1.2.0.16 (в работе) | **Ветка:** feat/1.2.0.x | **Дата:** 2026-06-19

---

## Быстрый старт

1. `doc/PATTERNS.md` — паттерны кода, правила, архитектура
2. `doc/PLAN.md` — текущий план и бэклог
3. Этот файл — контекст сессии

Проект: `/Users/paveld/LavenderMessenger-Android`
Сборка: `./gradlew assembleDebug` (запускать локально на Mac)

---

## Что сделано (v1.2.0.14 → v1.2.0.15)

### Secret chat fixes
- **E2EE init:** `ChatE2EEDelegate.initE2EE()` вызывается в `NewChatActivity.setupDelegates()` — key exchange происходит при открытии секретного чата
- **History decryption:** `GrpcMessageClient.loadHistory()` расшифровывает E2EE через `decryptE2EEMessages()` — история загружается и отображается корректно
- **Chat list privacy:** Секретные чаты показывают "🔒 End-to-end encrypted" вместо lastMessageText
- **newMessageEvent privacy:** Секретные чаты не показывают plaintext превью в обновлениях чат-листа
- **Post key-exchange reload:** После завершения key exchange — очистка Room DB кэша + clearMessages + loadHistory

### ServerConfig
- **ServerConfig.kt:** Централизованный `ServerConfig.kt` — PROD_HOST, PROD_GRPC_PORT, PROD_HTTP_PORT, DEV equivalents
- **Хардкод IP убран:** SessionManager, ChatListAuth, CallActivity — все ссылаются на ServerConfig

### Серверные фиксы (E2EE)
- **GetHistory:** `e2ee_payload` = один base64 слой (был двойной)
- **EditMessage:** проверка `IsE2EE` при decrypt + broadcast
- **GetFavorites:** E2EE возвращают `e2ee_payload`, не расшифровку
- **backfillLastMessageText:** исключены `is_secret` чаты

---

## Критические фиксы (v1.2.0.5 → v1.2.0.14)

- **Токен/сессия:** `startTokenRefresh()` вызывается при каждом входе/восстановлении. Chat stream retry: refresh → retry (не password dead-end). `onResume` валидация токена.
- **Admin menu:** `isSuperAdmin` race condition исправлен. `adminUserId` сохраняется в SharedPreferences, восстанавливается при старте.
- **Admin discovery for non-admin users:** UserInfoProto добавлены userId (field 6) и isSuperAdmin (field 7). loadUsers() сканирует allUsers и находит адмира.
- **Feedback retry:** openFeedbackChat() вызывает loadUsers() + retry через 1.5с если adminUserId пуст.
- **SuperAdmin marshallers:** GetProfileResponseMarshaller + все ProfileService v2 marshallers.
- **Chat subtitle last seen:** В direct-чатах показывается `ProtoUtils.formatLastSeen()`.
- **Deleted chat fix:** deleteChat() удаляет чат из Room DB. chatDeletedEvent подписан в ChatListViewModel.
- **Pull-to-refresh fix:** refreshChats() сбрасывает `_isLoading`.
- **Chat list sync:** newMessageEvent подписан в ChatListViewModel — чат-лист обновляется в реальном времени. 30с periodic polling. ChatDao caching.
- **Action mode toolbar:** toolbar-native selection mode — без Android ActionMode bar.
- **Архитектура:** ChatViewModel, ProfileViewModel, MessageAdapter рефакторинг.

---

## Текущая архитектура

```
GrpcClient (facade)
  └── RealGrpcClient — orchestrator
        ├── GrpcConnectionManager — connect/reconnect/disconnect
        ├── GrpcAuthClient — JWT auth (v2 only)
        ├── GrpcTypingClient — typing stream
        ├── GrpcCallClient — calls
        ├── GrpcChatClient (~250) — getChats, create/delete, participants
        ├── GrpcChatListV2Client (~120) — pin/unpin, search, archive
        ├── GrpcChatAuxClient (~130) — users, AI, FCM, mute
        ├── GrpcChatListClient (~255) — chat list version, create/delete
        ├── GrpcProfileClient — profile, avatar, contacts, themes
        ├── GrpcDraftClient, GrpcFavoritesClient, GrpcMessageClient
        ├── GrpcServerDiscoveryClient — server discovery
        ├── HermesGrpc, OwlGrpc — AI
        └── AiChatGrpc, SecretChatGrpc, ProfileClient

ChatListActivity → 10 modules (toolbar, tabs, FABs, auth, etc.)
NewChatActivity → 6 delegates + ChatViewModel
ProfileActivity → ProfileViewModel
MessageAdapter → 12 focused bind methods

Auth: JWT only (v2), AuthManager + BearerTokenInterceptor
Session: SessionManager (token refresh EVERY entry point)
Admin tracking: adminUserId StateFlow + SharedPreferences persistence + GetAllUsers admin scan
Chat list sync: newMessageEvent (real-time) + 30s periodic polling + ChatDao cache (Room)
Selection mode: toolbar-native (enterSelectionMode/exitSelectionMode), no Android ActionMode bar
ServerConfig: centralized PROD_HOST/GRPC_PORT/HTTP_PORT in ServerConfig.kt
E2EE: E2EEManager (ECDH + AES-256-GCM), ChatE2EEDelegate, decryptE2EEMessages() in GrpcMessageClient
```

---

## Бэклог — Следующая сессия (v1.2.0.16)

### Приоритет 1: Тесты
| Задача | Оценка |
|--------|--------|
| Unit-тесты для ChatViewModel | 2h |
| Unit-тесты для ProfileViewModel | 2h |
| Unit-тесты для SessionManager | 2h |
| Unit-тесты для data/ai/ | 2h |

### Приоритет 2: UX
| Задача | Оценка |
|--------|--------|
| Offline mode — показать cached messages без подключения | 3h |
| Push notification deep link — переход в чат из уведомления | 2h |

### Приоритет 3: Отладка
- [ ] Навигация шторок в реальном приложении

---

## Правила (обязательно к прочтению)

1. **НЕ компилировать Android на сервере** (OOM kill) — assembleRelease ТОЛЬКО локально
2. **НЕ деплоить на prod** без явного указания
3. userId (UUID) — всегда как ключ, НЕ username
4. Все новые строки ОДНОВРЕМЕННО в `values/strings.xml` + `values-ru/strings.xml`
5. getString() НЕ в полях Activity — только в методах
6. Kotlin 2.3.21: `cont.resume(value, onCancellation = {})`
7. Все ошибки через `ErrorHandler.handle()` — НЕ `Log.e`
8. v2 server only — никаких v1 fallbacks
9. Chat toolbar: фиксированная высота `@dimen/custom_toolbar_height`, elevation 0dp
10. Все chat activities: `setDecorFitsSystemWindows(window, false)` в onCreate
11. Marshallers: всегда включать v2 proto поля
12. JWT freshness: `ensureFreshToken()` перед Chat stream
13. НЕ хардкодить username — использовать adminUserId / userId
14. **Перед коммитом всегда запускать `./gradlew assembleDebug`**
15. **НЕ bump'ать версию — bump делает только пользователь**

---

## Сервер

| | Dev | Prod |
|--|-----|------|
| gRPC | 50052 | 50051 |
| HTTP | 8083 | 8082 |
| Сервис | lavender-server-dev | lavender-server |
| Сайт | http://13.140.25.249 |

**Деплой сервера:** НЕ делать — другой агент управляет сервером. Если нужен серверный фикс — написать промпт-файл в `/root/msg/doc/`.

---

## Полезные ссылки

- Документация клиента: `doc/INDEX.md`, `doc/PATTERNS.md`, `doc/PLAN.md`
- Документация сервера: `/Users/paveld/LavenderMessenger-server/doc/CLIENT_INTEGRATION.md`
- Changelog: `CHANGELOG.md`
- v1 reference: `doc/ChatListActivity_v1_REFERENCE.kt`
