# Prompt: Android Client — Next Session

**Версия:** v1.2.0.15 | **Ветка:** feat/1.2.0.x | **Дата:** 2026-06-19

---

## Быстрый старт

1. `doc/PATTERNS.md` — паттерны кода, правила, архитектура
2. `doc/PLAN.md` — текущий план и бэклог
3. Этот файл — контекст сессии

Проект: `/Users/paveld/LavenderMessenger-Android`
Сборка: `./gradlew assembleDebug` (запускать локально на Mac)

---

## Что сделано (v1.2.0.5 → v1.2.0.15)

### Критические фиксы
- **Токен/сессия:** `startTokenRefresh()` вызывается при каждом входе/восстановлении. Chat stream retry: refresh → retry (не password dead-end). `onResume` валидация токена.
- **Admin menu:** `isSuperAdmin` race condition исправлен. `adminUserId` сохраняется в SharedPreferences, восстанавливается при старте. `fetchAdminStatus()` вызывается при READY (не в `connect()`).
- **Admin discovery for non-admin users:** `UserInfoProto` добавлены `userId` (field 6) и `isSuperAdmin` (field 7). `loadUsers()` сканирует `allUsers` и находит адмира. Feedback чат работает для ЛЮБОГО пользователя.
- **Feedback retry:** `openFeedbackChat()` вызывает `loadUsers()` + retry через 1.5с если `adminUserId` пуст.
- **SuperAdmin marshallers:** GetProfileResponseMarshaller + все ProfileService v2 marshallers.
- **Chat subtitle last seen:** В direct-чатах вместо "офлайн" теперь показывается `ProtoUtils.formatLastSeen()` ("был(а) в сети X мин/ч/дн назад"). `allUsers` добавлен в combine flow в NewChatActivity.

### Архитектура
- **ChatViewModel:** NewChatActivity 759→~450 строк. Бизнес-логика: sendMessage, uploadAudio, retryMessage, fetchChatMetadata, loadPinnedMessages, syncChatListIfNeeded, ensureUserIdSet.
- **ProfileViewModel:** ProfileActivity 719→~400 строк. Бизнес-логика: loadUserProfile, loadGroupData, updateChatName, updateChatSettings, uploadGroupAvatar.
- **MessageAdapter:** 870→324 строки (-63%). bind() → 12 выделенных методов.

### UI
- **About dialog:** текст "Лава: платформа...", ссылка http://13.140.25.249, feedback → чат с админом (adminUserId динамический из GetAllUsers), drag handle.
- **gRPC split:** GrpcChatClient + GrpcChatListV2Client + GrpcChatAuxClient (вместо монолитного GrpcChatListClient).

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
```

---

## Бэклог — Следующая сессия (v1.2.0.16)

### Приоритет 1: Отладка
- [ ] Навигация шторок в реальном приложении

### Приоритет 2: Тесты
| Задача | Оценка |
|--------|--------|
| Unit-тесты для ChatViewModel | 2h |
| Unit-тесты для ProfileViewModel | 2h |
| Unit-тесты для SessionManager | 2h |
| Unit-тесты для data/ai/ | 2h |

### Приоритет 3: Безопасность
| Задача | Оценка |
|--------|--------|
| Keystore пароль → env vars | 0.5h |
| ServerConfig.kt — единый IP | 1h |
| EncryptedSharedPreferences | 2h |

### Приоритет 4: UX
| Задача | Оценка |
|--------|--------|
| Offline mode — показать cached messages без подключения | 3h |
| Push notification deep link — переход в чат из уведомления | 2h |

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
- Документация сервера: `/root/msg/doc/INDEX.md`
- Changelog: `CHANGELOG.md`
- v1 reference: `doc/ChatListActivity_v1_REFERENCE.kt`
