# Lavender Messenger — Plan

**Version:** v1.2.0.5 | **Branch:** feat/1.2.0.x | **Updated:** 2026-06-18

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

## Backlog — Следующая сессия

### Приоритет 1: Стабильность
- [ ] Тестирование авто-входа с протухшим JWT (profile glitch)
- [ ] Проверить что `GetChatsV2` работает корректно на dev сервере
- [ ] Проверить навигацию шторок в реальном приложении

### Приоритет 2: Архитектура
| Задача | Что | LOC Эффект | Оценка |
|--------|-----|-----------|--------|
| ViewModel для NewChatActivity | Бизнес-логика → ViewModel | 755→~400 | 2h |
| ViewModel для ProfileActivity | Бизнес-логика → ViewModel | 719→~300 | 2h |
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
| JWT-only Chat stream | deprecated v1 password auth удалён |
| GetChatsV2 | серверная версия с пагинацией и фильтрами |
| Facade + inline delegates | Extension functions не работают через star import в Kotlin |
| GrpcChatClient/GrpcChatListV2Client/GrpcChatAuxClient | 3 домена вместо монолитного GrpcChatListClient |
| Sheet navigation | isNavigatingDeeper + ActivityResultContracts + OnDismissListener |
| Auto-login recovery | refresh → password re-login при expired JWT на startup |

---

## Архитектура (v1.2.0.5)

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
NewChatActivity → 6 delegates (toolbar, input, selection, search, E2EE, menu)

Auth: JWT only (v2), AuthManager + BearerTokenInterceptor
Session: SessionManager (token refresh, device sync, FCM, auto-login recovery)
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
