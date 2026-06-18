# Lavender Messenger — Plan

**Version:** v1.2.0.4 | **Branch:** feat/1.2.0.x | **Updated:** 2026-06-18

---

## Completed — v1.2.0.x (v2 Server Migration)

### Critical Fixes
- ✅ Fix getChats callback never firing on empty response (10s timeout)
- ✅ Fix v2 marshallers sending zero-byte request bodies
- ✅ Parse v2 fields in ChatInfo marshaller (isPinned/isMuted/isArchived/pinnedAt)
- ✅ Add marshallers for PinChat, UnPinChat, SearchChats, ArchiveChat, PinMessage, etc.
- ✅ Fix JWT token refresh before chat stream connection (token expired error)

### Toolbar Fix
- ✅ Fixed height `@dimen/custom_toolbar_height` on chat toolbars (was `wrap_content`)
- ✅ Remove elevation 4dp from chat toolbars (matches contacts: 0dp)
- ✅ Add `setDecorFitsSystemWindows(window, false)` to chat activities
- ✅ Fix status bar area not being filled with toolbar color

### v1 Legacy Cleanup (-269 LOC)
- ✅ ProfileClient: remove legacy ChatService fallbacks, always use ProfileService v2
- ✅ SessionManager: remove `loginV1()` and v1 fallback in `loginV2()`
- ✅ AuthManager: remove `setLegacyAuth()`, simplify `isJwtAuthenticated()`
- ✅ BearerTokenInterceptor: remove `isChatV2Supported()` check, always attach JWT
- ✅ Delete dead `ui/viewmodel/ChatListViewModel.kt` (21 LOC legacy stub)

### Branch Management
- ✅ Merged `feat/1.1.3.x` → `master` (v1.1.3.38)
- ✅ Created `feat/1.2.0.x` branch for v2 server development
- ✅ Version bumped to 1.2.0.4

---

## Backlog — Следующая сессия

### Приоритет 1: ContactsActivity
- `getContacts()`依赖 `currentUserId`, который не установлен на v1
- Нужно: fallback на username если userId пуст, либо загрузка через allUsers

### Приоритет 2: Архитектура
| Задача | Что | LOC Эффект | Оценка |
|--------|-----|-----------|--------|
| ViewModel для NewChatActivity | Бизнес-логика → ViewModel | 755→~400 | 2h |
| ViewModel для ProfileActivity | Бизнес-логика → ViewModel | 719→~300 | 2h |
| Разделить GrpcChatListClient | 3 класса | 642→3×200 | 1h |
| Разделить MessageAdapter | ViewHolder по типам | 870→~300 | 2h |

### Приоритет 3: Тесты
| Задача | Оценка |
|--------|--------|
| Unit-тесты для ViewModels | 3h |
| Unit-тесты для SessionManager | 2h |
| Unit-тесты для data/ai/ | 2h |

### Приоритет 4: Безопасность
| Задача | Оценка |
|--------|--------|
| Keystore пароль → env vars | 0.5h |
| ServerConfig.kt — единый IP | 1h |
| EncryptedSharedPreferences | 2h |

---

## Key Decisions

| Решение | Обоснование |
|---------|-------------|
| v2 only | v1 клиенты unsupported, AuthInterceptor fallback на сервере |
| Facade + inline delegates | Extension functions не работают через star import в Kotlin |
| MockK для тестов | MockK — Kotlin-native, Mockito не в зависимостях |
| ErrorHandler统一 | Все ошибки через ErrorHandler → AppLog + Log |
| Chat delegates | 6 делегатов вместо монолитного NewChatActivity |
| Optimistic READY | gRPC канал подключается лениво |
| Keepalive 30s/10s | Для мобильных сетей |
| Sync token refresh | ensureFreshToken() перед Chat stream для предотвращения expired token |

---

## Архитектура

```
GrpcClient (facade, ~700 LOC)
  └── RealGrpcClient (~880 LOC) — orchestrator
        ├── GrpcConnectionManager (167) — connect/reconnect/disconnect
        ├── GrpcAuthClient (232) — JWT auth (v2 only)
        ├── GrpcTypingClient (87) — typing stream
        ├── GrpcCallClient (125) — calls
        ├── GrpcChatListClient (647) — chat list, pin/search/archive
        ├── GrpcProfileClient (506) — profile, avatar, contacts, themes
        ├── GrpcDraftClient (86) — drafts
        ├── GrpcFavoritesClient (120) — favorites
        ├── GrpcMessageClient (345) — messages, history, reactions, mark read
        ├── GrpcServerDiscoveryClient (145) — server discovery
        ├── HermesGrpc (1872), OwlGrpc (1146) — AI
        └── AiChatGrpc, SecretChatGrpc, ProfileClient

ChatListActivity → 10 modules (toolbar, tabs, FABs, auth, etc.)
NewChatActivity → 6 delegates (toolbar, input, selection, search, E2EE, menu)
ProfileActivity — monolithic, not refactored

Auth: JWT only (v2), AuthManager + BearerTokenInterceptor
Session: SessionManager (token refresh, device sync, FCM)
```

---

## Completed Phases

| Phase | Version | What | Status |
|-------|---------|------|--------|
| 0-2 | v1.1.3.33 | Stabilization, NewChatActivity refactor, Error handling | ✅ |
| 3 | v1.1.3.34 | Unit tests for gRPC client (42 tests) | ✅ |
| 4 | v1.1.3.35 | GrpcClient facade optimization (780→~400 LOC) | ✅ |
| 5 | v1.1.3.36 | AI domain layer (OwlChatUseCase, HermesChatUseCase) | ✅ |
| 6 | v1.1.3.38 | v2 Client Release — UI improvements, language sync, contacts | ✅ |
| 7 | v1.2.0.4 | v2 Server Migration — chat list fix, toolbar, legacy cleanup, JWT refresh | ✅ |
