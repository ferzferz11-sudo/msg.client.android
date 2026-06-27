# Lavender Messenger — Android Documentation

**Version:** v1.3.1.02 | **Updated:** 2026-06-27

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
| Unit tests | 332 (all passing) |
| Layout XML | 115 |
| String entries | 796 (EN) + 796 (RU) |
| Min SDK | 29 (Android 10) |
| Kotlin | 2.4.0 |
| Branch | feat/1.3.1.x (v1.3.1.01) |

---

## Architecture Overview (v1.3.1.01)

```
GrpcClient (facade)
  └── RealGrpcClient — orchestrator
        ├── GrpcConnectionManager — connect/reconnect/health check
        ├── GrpcAuthClient — JWT auth (v2 only)
        ├── GrpcTypingClient — typing stream
        ├── GrpcCallClient — calls
        ├── GrpcChatClient (~250) — getChats (cursor pagination), create/delete, participants, settings
        ├── GrpcChatListV2Client (~120) — pin/unpin, search, archive
        ├── GrpcChatAuxClient (~130) — users, FCM, mute
        ├── GrpcProfileClient — contacts, themes, devices, passwords (ChatService)
        ├── ProfileClient — profile, avatar, settings, delete (ProfileService v2, JWT)
        ├── GrpcDraftClient, GrpcFavoritesClient
        ├── GrpcMessageV2Client — ChatV2 stream, GetHistoryV2, SendMessageV2, Edit/Delete/ReactionV2
        ├── GrpcServerDiscoveryClient — server discovery
        ├── GrpcAIv2Client — AI v2 (ChatWithAIV2, Agent CRUD, Tools, Marketplace, Chat History)
        ├── SecretChatGrpc, ProfileClient
        ├── NotificationsGrpc — notifications (subscribe, history, read, unread)
        ├── RemoteAgentGrpc — Remote Agent (list, deploy, tokens, process)
        └── ChatKeepAliveService — foreground service, keep-alive connection

network/HttpClient.kt — singleton OkHttpClient (connection pool 5/5min, timeouts 30s)

ChatListActivity → 10 modules (toolbar, tabs, FABs, auth, etc.)
NewChatActivity → 6 delegates + ChatViewModel
AiV2ChatActivity → unified AI chat (simple/agent/pipeline) + rate limit + image support + multi-agent
AiV2AgentListActivity → unified agent management (4 tabs: Presets/My Agents/Discover/Remote Agent)
AiAgentSetupActivity → create/edit all agent types
AIBottomSheet → agent selection with checkboxes + AI Agents button

Auth: JWT only (v2), AuthManager + BearerTokenInterceptor
Session: SessionManager (token refresh EVERY entry point)
AI v2: ChatWithAIV2 streaming + tool calling loop + 8 provider types + image support
AI Chat History: GetAIV2ChatHistory + ListAIV2Chats (server-side)
AI Marketplace: Rate, Reviews, Stats, Share, Install, Usage + Search + Pagination + Sort + Filter
Biometric: BiometricPrompt after splash screen when enabled
Chat List: Cursor-based pagination (infinite scroll), Unread highlight
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
