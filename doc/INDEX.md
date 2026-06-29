# Lavender Messenger — Android Documentation

**Version:** v1.3.1.07 | **Updated:** 2026-06-29

---

## Session Start Order

1. **PATTERNS.md** — code patterns, rules, architecture conventions
2. **PROMPT_NEXT_SESSION.md** — current plan and backlog
3. This file (INDEX) — project overview

---

## Index

| File | Purpose | When to read |
|------|---------|-------------|
| `doc/PATTERNS.md` | Code patterns, rules, conventions | Before writing code |
| `doc/GOTCHAS.md` | Discovered knowledge, gotchas, edge cases | Before debugging or fixing bugs |
| `doc/PROMPT_NEXT_SESSION.md` | Current plan + backlog | At session start |
| `doc/REMOTE_AGENT.md` | Remote Agent reference | When working with Remote Agent |
| `doc/AI_V2_TESTING.md` | AI v2 test scenarios | When testing AI features |
| `doc/PROMPT_HERMES_ACP_CLIENT.md` | Hermes ACP client plan | When working with Hermes Agent |
| `CHANGELOG.md` | Version history | Reference |

---

## Repos

| Repo | Path | What |
|------|------|------|
| **Android client** | `/Users/paveld/LavenderMessenger-Android` | Kotlin code in `app/src/main/java/lavender/client/android/` |
| **Server** | `/Users/paveld/LavenderMessenger-server` | Go code, proto files |

---

## Quick Stats

| Metric | Value |
|--------|-------|
| Kotlin files | ~173 |
| Activities | 29 |
| Fragments | 1 (RemoteAgentSettingsFragment) |
| Services | 3 (ChatKeepAliveService, RemoteAgentService, LavenderMessagingService) |
| gRPC files | 22 |
| Unit tests | 320 (all passing) |
| Layout XML | 115 |
| String entries | 796 (EN) + 796 (RU) |
| Min SDK | 29 (Android 10) |
| Kotlin | 2.4.0 |
| Branch | feat/1.3.1.x |

---

## Architecture Overview (v1.3.1.06)

```
GrpcClient (facade)
  └── RealGrpcClient — orchestrator
        ├── GrpcConnectionManager — connect/reconnect/health check
        ├── GrpcAuthClient — JWT auth (v2 only)
        ├── GrpcTypingClient — typing stream
        ├── GrpcCallClient — calls
        ├── GrpcChatClient (~250) — getChats (cursor pagination), create/delete, participants, settings
        ├── GrpcChatListV2Client (~120) — pin/unpin, search, archive
        ├── GrpcChatAuxClient (~130) — users, FCM, mute, getAdminUserList, getAdminUserSessions
        ├── GrpcProfileClient — contacts, themes, devices, passwords (ChatService)
        ├── ProfileClient — profile, avatar, settings, delete (ProfileService v2, JWT)
        ├── GrpcDraftClient, GrpcFavoritesClient (+ parseReactions)
        ├── GrpcMessageV2Client — messages v2 only (no v1 fallback), parseReactions (internal)
        ├── GrpcServerDiscoveryClient — server discovery
        ├── GrpcAIv2Client — AI v2 (ChatWithAIV2, Agent CRUD, Tools, Marketplace, Chat History)
        ├── SecretChatGrpc, ProfileClient
        ├── NotificationsGrpc — notifications (subscribe, history, read, unread)
        ├── RemoteAgentGrpc — Remote Agent (list, deploy, tokens, process)
        └── ChatKeepAliveService — foreground service, keep-alive connection

network/HttpClient.kt — singleton OkHttpClient (connection pool 5/5min, timeouts 30s)
network/AuthInterceptor.kt — JWT auth for HTTP requests

ChatListActivity → 10 modules (toolbar, tabs, FABs, auth, etc.)
NewChatActivity → 6 delegates + ChatViewModel (v2 only)
  └── ChatToolbarDelegate — toolbar, avatar, subtitle, navigation, E2EE status
  └── ChatInputDelegate — text input, send, attachments, audio, emoji, mentions
  └── ChatSelectionDelegate — selection mode, copy/pin/delete/forward
  └── ChatSearchDelegate — in-chat search
  └── ChatE2EEDelegate — end-to-end encryption for secret chats
  └── ChatMessageMenuDelegate — reactions, context menu
AiV2ChatActivity → unified AI chat + commands + rate limit + image/file support + multi-agent + errors as chat messages
AiV2AgentListActivity → unified agent management (5 tabs: Presets/My Agents/Discover/Remote Agent/Usage)
AiAgentSetupActivity → create/edit all agent types (API key, temperature, max tokens)
AIBottomSheet → agent selection with user agents only + loading/empty states + fixed footer
SuperAdminActivity → admin panel with GetAdminUserList (cursor pagination, search, sort) + GetAdminUserSessions (expandable device sessions)

Auth: JWT only (v2), AuthManager + BearerTokenInterceptor + AuthInterceptor (HTTP)
Session: SessionManager (async token refresh — no Main thread blocking)
SplashScreen: SplashActivity → animateAndNavigate() → navigateToTarget() → biometric (15s timeout) + 5s safety timeout
Chat Stream: ChatV2 bidirectional stream (messenger.ChatService/ChatV2) — JWT auth + clientVersion, system signals, typing
Messages: v2 only — GetHistoryV2, SendMessageV2, EditMessageV2, DeleteMessageV2, SetReactionV2
Reactions: optimistic UI → Room DB save → server response (incl. empty) → in-memory + Room DB update → REACTION_V2 stream → Room DB save (even if message not in _messages)
Message Dedup: content-based (getContentHash) — deduplicates temp ID vs server ID in loadHistoryV2
Unread: markAsRead optimistic clear + locallyReadChats tracking → gRPC MarkRead to server → server updates last_read_at
Real-time: ChatV2 stream messages added to _messages StateFlow + Room DB, auto markAsRead for active chat
Chat Search: server-side SearchMessages RPC with 300ms debounce, fallback to client-side filter
Chat List: Cursor-based pagination (infinite scroll), Unread highlight, scroll position preserved on refresh
Parallel Loading: regular + AI chats loaded concurrently via supervisorScope + CompletableDeferred
AI v2: ChatWithAIV2 streaming + tool calling loop + 9 provider types + image/file support
AI Chat History: GetAIV2ChatHistory + ListAIV2Chats (server-side)
AI Marketplace: Rate, Reviews, Stats, Share, Install, Usage + Search + Pagination + Sort + Filter
Biometric: BiometricPrompt after splash screen when enabled (15s timeout fallback)
Notifications: per-chat sound override via notification_sounds SharedPreferences
Graceful Shutdown: SERVER_SHUTTINGDOWN + health check + backoff
Logging: clean logs, no hot-path noise, performance timing in loadChats
```

---

## Rules

1. Do NOT compile Android on server (OOM)
2. Do NOT deploy to prod without explicit instruction
3. userId (UUID) — always as key, NOT username
4. i18n: all new strings simultaneously in values/strings.xml + values-ru/strings.xml
5. Do NOT initialize getString() in Activity class fields
6. Kotlin 2.4.0: cont.resume(value, onCancellation = {})
7. All errors via `ErrorHandler.handle()` — NOT direct `Log.e`
8. v2 server only — no v1 legacy fallbacks in client code
9. Chat toolbars: fixed `@dimen/custom_toolbar_height`, elevation 0dp
10. All chat activities: `setDecorFitsSystemWindows(window, false)` in onCreate
11. Marshallers: always include v2 proto fields
12. JWT freshness: `ensureFreshToken()` before Chat stream
13. Run `./gradlew assembleDebug` before committing
14. Do NOT bump version — only user bumps version
15. Marshallers field order: server proto defines field numbers
16. AI v2 RPC: all methods under `messenger.ChatService/*` (NOT `AIService`)
17. Unread count: based on `user_chat_metadata.last_read_at`, NOT `messages.is_read`
18. ProfileService v2: profile/avatar/delete/settings via `messenger.ProfileService/*` (JWT context)
19. **ALWAYS verify against server code**: Before any gRPC/marshaller change, check server source code
20. CHANGELOG: do NOT list documentation changes (README, doc/, comments) — only code changes

---

## Servers

| | Dev | Prod |
|--|-----|------|
| gRPC | 50052 | 50051 |
| HTTP | 8083 | 8082 |

---

## Branches

| Branch | Purpose |
|--------|---------|
| `master` | Production |
| `feat/1.3.1.x` | v1.3.1.x development (current) |
