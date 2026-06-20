# Lavender Messenger — Android Documentation

**Version:** v1.3.0.5 | **Updated:** 2026-06-20

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
| `doc/PROMPT_NEXT_SESSION.md` | Current plan + backlog | At session start |
| `doc/REMOTE_AGENT.md` | Remote Agent reference | When working with Remote Agent |
| `doc/AI_V2_TESTING.md` | AI v2 test scenarios | When testing AI features |
| `doc/CODE_AUDIT.md` | Unused code & import audit | Code cleanup |
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
| Kotlin files | ~160 |
| Activities | 25 |
| gRPC modules | 27 |
| Unit tests | 15 files |
| Layout XML | 109 |
| String entries | 826 (EN + RU) |
| Min SDK | 29 (Android 10) |
| Kotlin | 2.3.21 |
| Branch | feat/1.3.0.x (v1.3.0.5) |

---

## Architecture Overview (v1.3.0.5)

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
        ├── GrpcProfileClient — contacts, themes (ChatService)
        ├── ProfileClient — profile, avatar, settings, delete (ProfileService v2, JWT)
        ├── GrpcDraftClient, GrpcFavoritesClient, GrpcMessageClient
        ├── GrpcServerDiscoveryClient — server discovery
        ├── GrpcAIv2Client — AI v2 (ChatWithAIV2, Agent CRUD, Tools, Marketplace)
        ├── SecretChatGrpc, ProfileClient
        ├── NotificationsGrpc — notifications (subscribe, history, read, unread)
        └── RemoteAgentGrpc — Remote Agent (list, deploy, tokens, process)

network/HttpClient.kt — singleton OkHttpClient (connection pool 5/5min, timeouts 30s)

ChatListActivity → 10 modules (toolbar, tabs, FABs, auth, etc.)
NewChatActivity → 6 delegates + ChatViewModel
AiV2ChatActivity → unified AI chat (simple/agent/pipeline) + rate limit
AiV2AgentListActivity → agent list (tabs: Presets/My/Public/Marketplace/Usage)
AiV2AgentCreateEditActivity → agent create/edit

Auth: JWT only (v2), AuthManager + BearerTokenInterceptor
Session: SessionManager (token refresh EVERY entry point)
AI v2: ChatWithAIV2 streaming + tool calling loop + 7 provider types
AI Marketplace: Rate, Reviews, Stats, Share, Install, Usage + Search + Pagination + Sort + Filter
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
6. Kotlin 2.3.21: cont.resume(value, onCancellation = {})
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
| `feat/1.3.0.x` | v1.3.0.x development (current) |

---

## Quick Stats

| Metric | Value |
|--------|-------|
| Kotlin files | ~160 |
| Activities | 25 |
| gRPC modules | 27 |
| Unit tests | 15 files |
| Layout XML | 109 |
| String entries | 826 (EN + RU) |
| Min SDK | 29 (Android 10) |
| Kotlin | 2.3.21 |
| Branch | feat/1.3.0.x (v1.3.0.5) |
