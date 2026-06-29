# Lavender Messenger — Plan

**Version:** v1.1.3.44 | **Branch:** feat/1.1.3.x-v1compat | **Updated:** 2026-06-29

---

## Completed — v1.1.3.42 (MessageAdapter Split + UserSession Tests)

### MessageAdapter Split
- ✅ MessageAdapter: 870 → 150 LOC (-83%) — adapter logic only
- ✅ MessageViewHolder (new, 733 LOC) — extracted ViewHolder with bind() and all content-type binders
- ✅ MessageColors.kt (new, 31 LOC) — data class + theme color helpers
- ✅ Clean separation: adapter manages selection/state, ViewHolder manages rendering

### Unit Tests (199 total, was 181)
- ✅ UserSessionTest (18 tests) — isLoggedIn, isJwtAuth, copy, equality, hashCode, toString

---

## Completed — v1.1.3.41 (GrpcClient Split + Unit Tests)

### GrpcChatListClient Split
- ✅ GrpcChatListClient: 642 → 244 LOC (-62%) — chat list operations only
- ✅ GrpcChatManagementClient (new, 260 LOC) — create/delete/update chats, participants
- ✅ GrpcChatAuxClient (new, 187 LOC) — users, AI chats, FCM tokens, mute
- ✅ RealGrpcClient updated to use all 3 clients with proper delegation
- ✅ Removed unused constructor params (chatDeletedEvent, allUsers, serverTime, scope) from GrpcChatListClient

### Unit Tests (181 total, was ~120)
- ✅ GrpcChatManagementClientTest (11 tests) — delete, create, update, participants
- ✅ GrpcChatAuxClientTest (8 tests) — users, AI chats, FCM, mute
- ✅ ChatViewModelTest (11 tests) — Message model, state, list operations
- ✅ AiChatManagerTest (13 tests) — initial state, session/message/settings models
- ✅ Updated GrpcChatListClientTest (8 tests) — new constructor, removed moved methods

---

## Completed — v1.1.3.40 (ProfileViewModel Integration)

### ProfileActivity Refactoring
- ✅ ProfileActivity: 719 → 531 LOC (-26%) — business logic moved to ProfileViewModel
- ✅ ProfileActivity now uses StateFlow observers for all profile state
- ✅ Removed duplicate uploadGroupAvatar/resizeImage*/extractUrlsFromResponse from Activity
- ✅ Kept UI-only code in Activity: bottom sheets, dialogs, theme application, image picking
- ✅ ProfileViewModel (407 LOC) owns: profile loading, group settings, participant management, avatar upload

### Unit Tests
- ✅ ProfileViewModelTest (18 tests) — state initialization, participant parsing, admin checks, URL extraction
- ✅ AiModelsTest (22 tests) — AiChatSession, AiChatMessage, AiChatSettings, AiStreamState, AiSource enum

### Server
- ✅ ServerVersion synced to v1.3.0.19 (matches running binary)

---

## Completed — v1.1.3.38 (v2 Client Release)

### UI Улучшения
- ✅ Имя собеседника в личных чатах (getDisplayName)
- ✅ Тулбар: прозрачность 30%, тень 6dp, тап → шторка профиля
- ✅ Убраны заголовки секций из списка чатов
- ✅ Шторка профиля: верхняя секция кликабельная → редактирование
- ✅ Фон темы: chatListBackground для списка чатов
- ✅ Предзагрузка пользователей при открытии чатов

### Функциональность
- ✅ Язык синхронизируется с сервером (toggleLanguage + SplashActivity)
- ✅ Создание чатов/секретных чатов/конференций — все пользователи
- ✅ Typing индикатор: фильтрация по username и userId

### Компиляция
- ✅ deployAgentTaskStream в HermesChatUseCase
- ✅ Scope leak retryDelay в OwlChatUseCase

---

## Completed — v1.1.3.44 (v1/v2 Negotiation Tests)

### Unit Tests (~220 total, was 199)
- ✅ ServerConfigTest (20 tests) — PROD/DEV endpoints, port mapping, `findKnown()`, `isDevServer()`
- ✅ ProfileClientTest (17 tests) — `isChatV2Supported()` / `isProfileV2Supported()` for v1, v2, edge cases

### v1/v2 Architecture
- ✅ Line B branch `feat/1.1.3.x-v1compat` — separates v1 (prod) and v2 (dev) code paths
- ✅ Capability negotiation via `fetchServerInfo()` + `isChatV2Supported()` guard
- ✅ Backward-compatible with prod server (v1 mode) and dev server (v2 mode)

---

## Backlog — Следующая сессия

### Приоритет 1: Безопасность
| Задача | Оценка |
|--------|--------|
| Keystore пароль → env vars | 0.5h |
| ServerConfig.kt — единый IP | 1h |
| EncryptedSharedPreferences | 2h |

### Приоритет 2: Тесты
| Задача | Оценка |
|--------|--------|
| Unit-тесты для CredentialStore | 2h |
| Unit-тесты для AuthManager | 2h |

---

## Key Decisions

| Решение | Обоснование |
|---------|-------------|
| Facade + inline delegates | Extension functions не работают через star import в Kotlin |
| MockK для тестов | MockK — Kotlin-native, Mockito не в зависимостях |
| ErrorHandler统一 | Все ошибки через ErrorHandler → AppLog + Log |
| Chat delegates | 6 делегатов вместо монолитного NewChatActivity |
| Optimistic READY | gRPC канал подключается лениво |
| Keepalive 30s/10s | Для мобильных сетей |
| ViewModel + StateFlow | Activity наблюдает за StateFlow из ViewModel, не вызывает GrpcClient напрямую |
| ViewHolder extraction | MessageViewHolder выделен из MessageAdapter для разделения ответственности |

---

## Архитектура

```
GrpcClient (facade, 711 LOC)
  └── RealGrpcClient (883 LOC) — orchestrator
        ├── GrpcConnectionManager, GrpcAuthClient, GrpcTypingClient
        ├── GrpcCallClient, GrpcChatListClient, GrpcProfileClient
        ├── GrpcDraftClient, GrpcFavoritesClient, GrpcMessageClient
        ├── GrpcServerDiscoveryClient, GrpcMarshallers
        ├── HermesGrpc (1872), OwlGrpc (1146) — AI
        └── AiChatGrpc, SecretChatGrpc, ProfileClient

ChatListActivity (382) → 10 modules
NewChatActivity (758) → 6 delegates + ChatViewModel
ProfileActivity (531) → ProfileViewModel (407) + UI delegates

MessageAdapter (150) → MessageViewHolder (733) + MessageColors (31)
  — adapter: selection state, diff callback
  — ViewHolder: rendering, content types, click handlers
  — Colors: theme color extraction
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
| 7 | v1.1.3.40 | ProfileViewModel integration, unit tests for AI models | ✅ |
| 8 | v1.1.3.42 | MessageAdapter split (870→150 LOC), UserSession tests | ✅ |
