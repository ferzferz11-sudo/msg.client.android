# Lavender Messenger — Plan

**Version:** v1.2.0.15 | **Branch:** feat/1.2.0.x | **Updated:** 2026-06-19

---

## Completed — v1.2.0.15

### Session 2026-06-19 (Secret Chat Fixes + ServerConfig)
- ✅ **E2EE init:** `ChatE2EEDelegate.initE2EE()` called in `NewChatActivity.setupDelegates()` — key exchange happens on chat open
- ✅ **History decryption:** `GrpcMessageClient.loadHistory()` decrypts E2EE messages via `decryptE2EEMessages()`
- ✅ **Chat list privacy:** Secret chats show "🔒 End-to-end encrypted" instead of last message text
- ✅ **newMessageEvent privacy:** Secret chat messages don't show plaintext preview in chat list
- ✅ **ServerConfig:** Centralized `ServerConfig.kt` — PROD_HOST, PROD_GRPC_PORT, PROD_HTTP_PORT, DEV equivalents. Hardcoded IPs replaced.

---

## Completed — v1.2.0.14

### Session 2026-06-19 (Status Fix + Chat List Sync + Deleted Chat Fix)
- ✅ **Chat subtitle last seen:** `ChatToolbarDelegate.updateSubtitle()` принимает `otherUserLastSeenAt: Timestamp?`. Если пользователь offline — показывает `ProtoUtils.formatLastSeen()` ("был(а) в сети X мин/ч/дн назад") вместо просто "офлайн".
- ✅ **newMessageEvent subscription:** ChatListViewModel подписывается на `newMessageEvent` — чат-лист обновляется в реальном времени. Тип изменён на `Message` для полных данных.
- ✅ **Periodic polling:** 30с интервал для обновления чат-листа (как в v1).
- ✅ **ChatDao caching:** чаты загружаются из кэша при старте, синхронизируются с сервером в фоне.
- ✅ **ChatEntity expansion:** добавлены `isPinned`, `isArchived`, `pinnedAt` (миграция 9→10).
- ✅ **Stop cache wipe:** SplashActivity больше не стирает Room кэш при каждом запуске.
- ✅ **Deleted chat fix:** `deleteChat()` удаляет чат из Room DB. `chatDeletedEvent` подписан в ChatListViewModel.
- ✅ **markAsRead on tap:** badge очищается при тапе на чат с непрочитанными.
- ✅ **Action mode toolbar:** Все 4 иконки (pin/mute/archive/delete) `showAsAction="always"`.

---

## Completed — v1.2.0.13

### Session 2026-06-19 (Admin Fix + Feedback)
- ✅ **isSuperAdmin race condition:** connect() reset → только при forceReconnect
- ✅ **adminUserId persistence:** сохраняется в SharedPreferences, восстанавливается при старте
- ✅ **fetchAdminStatus():** сохраняет adminUserId из profile (userId + isSuperAdmin)
- ✅ **Admin discovery for non-admin users:** UserInfoProto добавлены userId (field 6) и isSuperAdmin (field 7). loadUsers() сканирует всех пользователей и находит адмира
- ✅ **Feedback chat retry:** openFeedbackChat() вызывает loadUsers() + retry через 1.5с если adminUserId пуст
- ✅ **SharedPreferences cleanup:** logout() очищает is_super_admin и admin_user_id

---

## Completed — v1.2.0.12

### Session 2026-06-18 (Architecture Refactor)
- ✅ **ProfileViewModel:** ProfileActivity 719→~400 строк. Бизнес-логика: loadUserProfile, loadGroupData, updateChatName, updateChatSettings, removeParticipant, addParticipants, uploadGroupAvatar, resizeImage
- ✅ **MessageAdapter split:** 870→324 строки (-63%). bind() → 12 выделенных методов по типам контента

---

## Completed — v1.2.0.10

### Session 2026-06-18 (About Dialog Fix)
- ✅ **About dialog buttons:** btnWhatsNew → ChangelogActivity, btnFeedback → email, btnShare → shareApp
- ✅ **About dialog drag handle:** dialog_about.xml переписан с MaterialCardView wrapper + dragHandle + contentContainer (как widget_standard_bottom_sheet)
- ✅ **i18n:** no_email_client строка добавлена (EN + RU)

---

## Completed — v1.2.0.9

### Session 2026-06-18 (Token Fix + Refactor)
- ✅ **Token refresh fix (критический):** startTokenRefresh() теперь вызывается при каждом восстановлении JWT сессии (initFromPrefs, waitForConnectionAndReLogin)
- ✅ **Chat stream retry fix:** JWT failure → refresh token → retry (вместо мёртвой петли с password fallback)
- ✅ **performTokenRefresh fallback:** refresh_token expired → automatic re-login с saved password
- ✅ **onResume token validation:** ChatListActivity и NewChatActivity проверяют свежесть токена
- ✅ **NewChatActivity → ChatViewModel:** бизнес-логика перенесена (sendMessage, uploadAudio, retryMessage, fetchChatMetadata, loadPinnedMessages, syncChatListIfNeeded, ensureUserIdSet)
- ✅ **GrpcChatListClientTest fix:** getChats() тесты обновлены для GrpcChatClient

---

## Completed — v1.2.0.7

### Session 2026-06-18 (P1 Debugging)
- ✅ SuperAdmin button fix — ProfileClient reflection-based marshaller replaced with proper GetProfileResponseMarshaller
- ✅ All ProfileService v2 marshallers created (GetProfile, UpdateProfile, UpdateAvatar, GetUserSettings, UpdateUserSettings)
- ✅ GetChatsV2 — verified working correctly, no fallback needed
- ✅ Auto-login — verified no infinite loop risks, minor UX issue (silent failure) noted

---

## Completed — v1.2.0.5

### Session 2026-06-18
- ✅ Contacts: show only added contacts in create chat sheets
- ✅ getContacts: fetchUserId fallback when currentUserId is empty
- ✅ GrpcChatListClient split: GrpcChatClient + GrpcChatListV2Client + GrpcChatAuxClient
- ✅ Auto-login with expired JWT: refresh → password re-login fallback
- ✅ Chat stream: remove password fallback, JWT-only auth
- ✅ GetChats v1 → GetChatsV2 endpoint
- ✅ Sheet navigation: return to parent sheet after Back from Activity
- ✅ About dialog: show server version
- ✅ Deprecated v1 patterns cleanup per PROMPT_ANDROID_DEPRECATED.md

---

## Backlog — Следующая сессия (v1.2.0.15)

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

## Key Decisions

| Решение | Обоснование |
|---------|-------------|
| v2 only | v1 клиенты unsupported, AuthInterceptor fallback на сервере |
| JWT-only Chat stream | deprecated v1 password auth удалён |
| GetChatsV2 | серверная версия с пагинацией и фильтрами |
| Facade + inline delegates | Extension functions не работают через star import в Kotlin |
| GrpcChatClient/GrpcChatListV2Client/GrpcChatAuxClient | 3 домена вместо монолитного GrpcChatListClient |
| Sheet navigation | isNavigatingDeeper + ActivityResultContracts + OnDismissListener |
| Auto-login recovery | refresh → password re-login при expired JWT на startup |
| Token refresh on session restore | startTokenRefresh() вызывается в initFromPrefs() и waitForConnectionAndReLogin() |
| JWT failure → refresh first | Chat stream retry: refresh token → retry (не password fallback) |
| Admin ID dynamic tracking | adminUserId из chat stream isSuperAdmin + GetAllUsers response (не хардкод username) |

---

## Архитектура (v1.2.0.13)

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
        ├── GrpcDraftClient — drafts
        ├── GrpcFavoritesClient — favorites
        ├── GrpcMessageClient — messages, history, reactions
        ├── GrpcServerDiscoveryClient — server discovery
        ├── HermesGrpc, OwlGrpc — AI
        └── AiChatGrpc, SecretChatGrpc, ProfileClient

ChatListActivity → 10 modules (toolbar, tabs, FABs, auth, etc.)
NewChatActivity → 6 delegates + ChatViewModel (toolbar, input, selection, search, E2EE, menu)
ProfileActivity → ProfileViewModel (profile/group data, avatar upload, participants)
MessageAdapter → 12 focused bind methods (text, image, audio, file, location, call, system)

Auth: JWT only (v2), AuthManager + BearerTokenInterceptor
Session: SessionManager (token refresh EVERY entry point, device sync, FCM, auto-login recovery)
Token lifecycle: initFromPrefs → startTokenRefresh | ensureFreshToken on chat stream | refresh-on-failure in onError
Admin tracking: adminUserId StateFlow + SharedPreferences persistence + GetAllUsers admin scan
Chat list sync: newMessageEvent (real-time) + 30s periodic polling + ChatDao cache (Room)
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
| 8 | v1.2.0.5 | Contacts fix, gRPC split, auto-login recovery, deprecated v1 cleanup | ✅ |
| 9 | v1.2.0.9 | Token refresh fix (startTokenRefresh on all entry points), ChatViewModel refactor | ✅ |
| 10 | v1.2.0.10 | About dialog fix (buttons + drag handle) | ✅ |
| 11 | v1.2.0.11 | ProfileViewModel, MessageAdapter split | ✅ |
| 12 | v1.2.0.12 | About dialog UX (share/feedback/admin tracking), v1.2.0.12 release | ✅ |
| 13 | v1.2.0.13 | Admin discovery for non-admin users (UserInfoProto, loadUsers, feedback retry) | ✅ |
| 14 | v1.2.0.14 | Chat subtitle last seen, chat list sync, DB caching, deleted chat fix, action mode toolbar | ✅ |
