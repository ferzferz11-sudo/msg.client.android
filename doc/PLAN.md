# Lavender Messenger — Plan

**Version:** v1.2.0.12 | **Branch:** feat/1.2.0.x | **Updated:** 2026-06-18

---

## Completed — v1.2.0.12

### Session 2026-06-18 (About Dialog UX)
- ✅ About dialog: текст "Лава: платформа...", убрана версия клиента, оставлена только серверная
- ✅ Share: текст + ссылка http://13.140.25.249
- ✅ Feedback: открывается личный чат с админом (adminUserId динамический из chat stream, без хардкода username)
- ✅ GrpcClient.adminUserId — StateFlow, отслеживает userId адмира из isSuperAdmin сообщений

---

## Completed — v1.2.0.11

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

## Backlog — Следующая сессия (v1.2.0.13)

### Приоритет 1: Отладка
- [ ] Протестировать токен-фикс на dev сервере (требуется удалённая проверка)
- [ ] Протестировать feedback чат с админом (adminUserId из chat stream)
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
| Admin ID dynamic tracking | adminUserId из chat stream isSuperAdmin (не хардкод username) |

---

## Архитектура (v1.2.0.12)

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
Admin tracking: adminUserId StateFlow from chat stream isSuperAdmin messages
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
