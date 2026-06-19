# Prompt: Android Client — Next Session

**Версия:** v1.2.0.19 (релиз) | **Ветка:** feat/1.2.0.x | **Дата:** 2026-06-19

---

## Быстрый старт

1. `doc/PATTERNS.md` — паттерны кода, правила, архитектура
2. `doc/PLAN.md` — текущий план и бэклог
3. Этот файл — контекст сессии

Проект: `/Users/paveld/LavenderMessenger-Android`
Сборка: `./gradlew assembleDebug` (запускать локально на Mac)

---

## Что сделано (v1.2.0.18 → v1.2.0.19)

### Навигация шторок (BottomSheet navigation)
- **SheetNavigator:** Стек шторок с push/pop/clear — навигация между нижними панелями
- **Back button:** Кнопка "←" в заголовке шторки (автоматически появляется при наличии стека)
- **showWithNavigation():** Метод показа шторки с навигацией
- **Все шторки в ChatListFABs** теперь используют навигацию (ActionBottomSheet → SearchableListBottomSheet)

### Unit-тесты ChatViewModel
- 17 тестов для `ChatViewModel.ChatMetadata` (defaults, values, copy, equals, hashCode, toString)
- Тесты для всех типов чатов: direct, group, conference, favorites, general, secret

### Удалено
- `AiModelsTest.kt` — AI v1 deprecated, готовится AI v2
- `ChatListActivity_v1_REFERENCE.kt` — устаревший файл

---

## Что сделано (v1.2.0.17 → v1.2.0.18)

### Secret chat marshallers — field order fix
- `CreateSecretChatRequest` — убран лишний `userId`, field order: 1=target_username, 2=target_user_id, 3=public_key, 4=client_version
- `ExchangeSecretKeyRequest` — field order: 1=chat_id, 2=public_key
- `GetSecretChatKeyRequest` — field order: 1=chat_id

### Secret chat display name
- `getDisplayName()` — проверка `isSecret` вынесена на верхний уровень
- Секретные чаты показывают `🔒 имя_собеседника`

### E2EE key exchange
- Лимит 10 попыток обмена ключами (каждые 3 сек)
- Логирование: номер попытки, финальный warning

### Selection mode
- Убраны action_pin и action_archive (пока не готово)
- MaterialCheckBox с адаптацией к теме

---

## Критические фиксы (v1.2.0.5 → v1.2.0.17)

- **Токен/сессия:** `startTokenRefresh()` при каждом входе/восстановлении. Chat stream retry: refresh → retry.
- **Admin menu:** `isSuperAdmin` race condition исправлен. `adminUserId` сохраняется в SharedPreferences.
- **Admin discovery:** UserInfoProto добавлены userId + isSuperAdmin. loadUsers() сканирует allUsers.
- **Chat subtitle last seen:** В direct-чатах показывается `ProtoUtils.formatLastSeen()`.
- **Deleted chat fix:** deleteChat() удаляет чат из Room DB. chatDeletedEvent подписан в ChatListViewModel.
- **Pull-to-refresh fix:** refreshChats() сбрасывает `_isLoading`.
- **Chat list sync:** newMessageEvent + 30с periodic polling + ChatDao caching.
- **Action mode toolbar:** toolbar-native selection mode.
- **E2EE:** decryptE2EEMessages(), onKeyExchangeComplete cache clear + reload.
- **Chat list privacy:** isSecret masking в buildSections + ChatAdapter.

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
Admin tracking: adminUserId StateFlow + SharedPreferences persistence
Chat list sync: newMessageEvent (real-time) + 30s periodic polling + ChatDao cache
Selection mode: toolbar-native (enterSelectionMode/exitSelectionMode)
Sheet navigation: SheetNavigator (push/pop/back button)
E2EE: E2EEManager (ECDH + AES-256-GCM), ChatE2EEDelegate, decryptE2EEMessages()
```

---

## Бэклог — Следующая сессия (v1.2.0.20)

### Приоритет 1: AI v2
- Адаптация клиента под AI v2 API

### Приоритет 2: Тесты
| Задача | Статус |
|--------|--------|
| Unit-тесты для ChatViewModel | ✅ Done (v1.2.0.19) |
| Unit-тесты для ProfileViewModel | ✅ Done (v1.2.0.16) |
| Unit-тесты для SessionManager | ✅ Done (v1.2.0.16) |

### Приоритет 3: UX
| Задача | Статус |
|--------|--------|
| Offline mode | ✅ Done (v1.2.0.16) |
| Push notification deep link | ✅ Done (v1.2.0.16) |
| Sheet navigation | ✅ Done (v1.2.0.19) |

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
13. **Перед коммитом всегда запускать `./gradlew assembleDebug`**
14. **НЕ bump'ать версию — bump делает только пользователь**
15. **Marshallers field order:** server proto определяет field numbers. `chat_id` всегда field 1, `user_id` field 2

---

## Сервер

| | Dev | Prod |
|--|-----|------|
| gRPC | 50052 | 50051 |
| HTTP | 8083 | 8082 |
| Сервис | lavender-server-dev | lavender-server |
| Сайт | http://13.140.25.249 |

**Деплой сервера:** НЕ делать — другой агент управляет сервером.

---

## Полезные ссылки

- Документация клиента: `doc/INDEX.md`, `doc/PATTERNS.md`, `doc/PLAN.md`
- Документация сервера: `/Users/paveld/LavenderMessenger-server/doc/CLIENT_INTEGRATION.md`
- Changelog: `CHANGELOG.md`
