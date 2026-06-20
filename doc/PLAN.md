# Lavender Messenger — Plan

**Version:** v1.3.0.1 | **Branch:** feat/1.3.0.x | **Updated:** 2026-06-20

---

## Completed — v1.3.0.0

### Session 2026-06-20 (Marketplace + Graceful Shutdown + Cleanup)
- ✅ **Marketplace API:** 7 methods (Rate, Reviews, Marketplace, Stats, Share, Install, Usage)
- ✅ **Graceful Shutdown:** SERVER_SHUTTINGDOWN signal, health check, exponential backoff
- ✅ **v1 AI Cleanup:** Removed OwlGrpc, HermesGrpc, ~20 proto classes, v1 strings (~4000 LOC)
- ✅ **NotificationsGrpc + RemoteAgentGrpc:** Extracted from OwlGrpc/HermesGrpc
- ✅ **AI BottomSheet:** dragHandle + title "AI Services (in development)"
- ✅ **LavenderFab:** Agent list FAB with proper system bar insets
- ✅ **Avatar 48dp:** Enlarged toolbar avatar
- ✅ **Tab contrast:** Improved visibility on dark themes
- ✅ **Agent form theming:** Surface background, TextInputLayout colors, Save button
- ✅ **Login fixes:** Removed button preloader, localized error message
- ✅ **Presets fix:** includePublic=true for server presets

### Session 2026-06-20 (Marketplace UI)
- ✅ **Domain models:** MarketplaceAgent, AgentStats, AgentReview, UsageStat
- ✅ **Domain extensions:** toMarketplaceAgent(), AgentReviewProto.toDomain(), UsageStatEntryProto.toDomain()
- ✅ **UseCase methods:** 7 Marketplace methods in AiV2ChatUseCase (listMarketplace, stats, reviews, rate, share, install, usage)
- ✅ **ViewModels:** MarketplaceViewModel, AgentDetailViewModel, UsageStatsViewModel
- ✅ **MarketplaceAgentAdapter:** RecyclerView adapter with rating bar, install count
- ✅ **AgentDetailActivity:** Full agent detail screen with stats, reviews, action buttons
- ✅ **ReviewAdapter:** Review list adapter
- ✅ **RateAgentBottomSheet:** Rating 1-5 + text review
- ✅ **InstallAgentBottomSheet:** Share code input
- ✅ **4th tab "Marketplace":** Added to AiV2AgentListActivity with search
- ✅ **5th tab "Usage":** Added to AiV2AgentListActivity
- ✅ **UsageStats UI:** 3 summary cards (tokens, requests, avg) + per-agent list
- ✅ **UsageStatsAdapter:** Per-agent usage stats with K/M formatting
- ✅ **Search:** TextInputLayout with debounce for Marketplace search
- ✅ **Pull-to-refresh:** SwipeRefreshLayout for Marketplace and Usage tabs
- ✅ **Infinite scroll:** Automatic pagination when scrolling to bottom
- ✅ **Deep link:** `lavender://marketplace/install?code=xxx` for agent installation
- ✅ **Layouts:** 10 XML layouts (marketplace card, agent detail, review, rate/install/usage sheets)
- ✅ **Strings:** 26 marketplace strings (EN + RU)
- ✅ **AndroidManifest:** AgentDetailActivity registered, deep link intent filter
- ✅ **Unit tests:** 15 tests (MarketplaceModelsTest 8, MarketplaceMappersTest 7) — all pass
- ✅ **Empty state:** Marketplace shows "No public agents available yet" when empty
- ✅ **Rate limit UI:** RateLimitCache + countdown + disable input on limit

---

## Completed — v1.2.0.20

### Session 2026-06-20 (AI v2 Migration)
- ✅ **GrpcAIv2Client:** ChatWithAIV2 streaming + Agent CRUD + Tools
- ✅ **AiV2ChatUseCase:** Tool calling loop (max 10 iterations, server executes tools)
- ✅ **AiV2ChatManager:** Unified SharedFlow/StateFlow for AI v2
- ✅ **AiV2ChatActivity:** Unified AI chat screen for all types (simple/agent/pipeline)
- ✅ **AiV2AgentListActivity:** Agent list with tabs (Presets/My/Public)
- ✅ **AiV2AgentCreateEditActivity:** Agent create/edit with provider selection
- ✅ **60 unit tests:** AiV2ModelsTest (20), AiV2DomainExtensionsTest (13), AiV2MarshallersTest (27)
- ✅ **Cleanup v1:** Removed 20 files, 8 layouts, 3 dirs (~4000 LOC deleted)
- ✅ **Navigation:** ChatListActivity → AiV2ChatActivity for hermes/owl types

---

## Completed — v1.2.0.19

### Session 2026-06-19 (Sheet Navigation + ChatViewModel Tests + AI Cleanup)
- ✅ **SheetNavigator:** BottomSheet navigation stack with back button in title bar
- ✅ **ChatViewModel tests:** 17 unit tests (ChatMetadata data class, all chat types)
- ✅ **AI tests removed:** AiModelsTest.kt deleted (AI v1 deprecated)
- ✅ **v1 reference removed:** ChatListActivity_v1_REFERENCE.kt deleted

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

## Backlog — Следующая сессия (v1.3.0.2)

### Приоритет 1: Тестирование AI v2 с сервером
| Задача | Статус |
|--------|--------|
| Тестирование ChatWithAIV2 на реальном сервере | 🔲 |
| Тестирование Agent CRUD | 🔲 |
| Тестирование Tool Calling loop | 🔲 |
| Тестирование Marketplace API (каталог, отзывы, оценки) | 🔲 |
| Тестирование Graceful Shutdown | 🔲 |
| Тестирование Rate Limit | 🔲 |

### Приоритет 2: Тесты
| Задача | Статус |
|--------|--------|
| Unit-тесты AI v2 (models, marshallers, extensions) | ✅ Done (60 tests) |
| Unit-тесты Marketplace (models, mappers) | ✅ Done (15 tests) |
| Unit-тесты Marketplace marshallers | 🔲 |
| Unit-тесты для ChatViewModel | ✅ Done (v1.2.0.19) |
| Unit-тесты для ProfileViewModel | ✅ Done (v1.2.0.16) |
| Unit-тесты для SessionManager | ✅ Done (v1.2.0.16) |
| Интеграционные тесты AI v2 с сервером | 🔲 |

### Приоритет 3: UX улучшения
| Задача | Статус |
|--------|--------|
| Offline mode | ✅ Done (v1.2.0.16) |
| Push notification deep link | ✅ Done (v1.2.0.16) |
| Sheet navigation | ✅ Done (v1.2.0.19) |
| Graceful Shutdown UI | ✅ Done (v1.3.0.0) |
| Agent form dark theme | ✅ Done (v1.3.0.0) |
| Marketplace empty state | ✅ Done (v1.3.0.1) |
| Rate limit UI | ✅ Done (v1.3.0.1) |
| Loading skeletons для Marketplace | 🔲 |
| Кэширование Marketplace в Room DB | 🔲 |

### Приоритет 4: Новые фичи
| Задача | Статус |
|--------|--------|
| Уведомления о новых отзывах на агентов | 🔲 |
| Сортировка агентов в Marketplace (rating, installs, newest) | 🔲 |
| Фильтры в Marketplace (provider type, tools enabled) | 🔲 |
| Избранное в Marketplace (сохранять понравившихся агентов) | 🔲 |
| Автообновление статистики Usage | 🔲 |

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
| 5 табов в AgentListActivity | Marketplace и Usage — отдельные табы для удобства навигации |
| SearchBar в табе Marketplace | API поддерживает query параметр для фильтрации |
| SwipeRefreshLayout | Стандартный Android паттерн для pull-to-refresh |
| Infinite scroll через OnScrollListener | Автоматическая пагинация при приближении к концу списка |
| Deep link lavender://marketplace/install | Удобная установка агентов по ссылке |
| RateLimitCache клиентский | Серверный rate limit, клиентский кэш только для UX |

---

## Архитектура (v1.3.0.1)

```
GrpcClient (facade)
  └── RealGrpcClient — orchestrator
        ├── GrpcConnectionManager — connect/reconnect/health check
        ├── GrpcAuthClient — JWT auth (v2 only)
        ├── GrpcTypingClient — typing stream
        ├── GrpcCallClient — calls
        ├── GrpcChatClient (~250) — getChats, create/delete, participants
        ├── GrpcChatListV2Client (~120) — pin/unpin, search, archive
        ├── GrpcChatAuxClient (~130) — users, FCM, mute
        ├── GrpcChatListClient (~255) — chat list version, create/delete
        ├── GrpcProfileClient — profile, avatar, contacts, themes
        ├── GrpcDraftClient, GrpcFavoritesClient, GrpcMessageClient
        ├── GrpcServerDiscoveryClient — server discovery
        ├── GrpcAIv2Client — AI v2 (ChatWithAIV2, Agent CRUD, Tools, Marketplace)
        ├── SecretChatGrpc, ProfileClient
        ├── NotificationsGrpc — notifications (subscribe, history, read, unread)
        └── RemoteAgentGrpc — Remote Agent (list, deploy, tokens, process)

ChatListActivity → 10 modules (toolbar, tabs, FABs, auth, etc.)
NewChatActivity → 6 delegates + ChatViewModel
AiV2ChatActivity → unified AI chat (simple/agent/pipeline) + rate limit
AiV2AgentListActivity → 5 tabs (Presets/My/Public/Marketplace/Usage)
AiV2AgentCreateEditActivity → agent create/edit
AgentDetailActivity → agent detail (stats, reviews, rate/share/install)

Auth: JWT only (v2), AuthManager + BearerTokenInterceptor
Session: SessionManager (token refresh EVERY entry point)
AI v2: ChatWithAIV2 streaming + tool calling loop + 7 provider types
AI Marketplace: Rate, Reviews, Stats, Share, Install, Usage + Search + Pagination
Rate Limit: RateLimitCache + countdown + disable input
Graceful Shutdown: SERVER_SHUTTINGDOWN + health check + backoff
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
| 15 | v1.3.0.0 | AI v2 migration, Marketplace API, Graceful Shutdown, v1 AI cleanup | ✅ |
| 16 | v1.3.0.1 | Marketplace UI (5 tabs, search, pagination, deep link), UsageStats, Rate limit | ✅ |
