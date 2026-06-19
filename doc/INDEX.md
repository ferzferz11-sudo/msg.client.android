# Lavender Messenger — Android Documentation

**Version:** v1.2.0.13 | **Updated:** 2026-06-19

---

## Session Start Order

1. **PATTERNS.md** — code patterns, rules, architecture conventions
2. **PLAN.md** — current plan and backlog
3. This file (INDEX) — project overview

---

## Index

| File | Purpose | When to read |
|------|---------|-------------|
| `doc/PATTERNS.md` | Code patterns, rules, conventions | Before writing code |
| `doc/PLAN.md` | Current plan + backlog | At session start |
| `doc/REMOTE_AGENT.md` | Remote Agent reference | When working with Remote Agent |
| `CHANGELOG.md` | Version history | Reference |
| `PROMPT_ANDROID_DEPRECATED.md` | Deprecated v1 methods to remove | When cleaning up legacy code |

---

## Repos

| Repo | Path | What |
|------|------|------|
| **Android client** | `/root/msg.client.android/` | Kotlin code in `app/src/main/java/lavender/client/android/` |
| **Server** | `/root/msg/` | Go code, proto files |

---

## Quick Stats

| Metric | Value |
|--------|-------|
| Kotlin files | ~163 |
| Total LOC | ~40,000 |
| Activities | 28 |
| gRPC modules | 24 |
| Unit tests | 9 files |
| Layout XML | 108 |
| String entries | 767 (EN + RU) |
| Min SDK | 29 (Android 10) |
| Kotlin | 2.3.21 |
| Branch | feat/1.2.0.x (v2 server) |

---

## Architecture Overview (v1.2.0.5)

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
        ├── GrpcChatListClient (~255) — chat list version
        ├── GrpcProfileClient — profile, avatar, contacts, themes
        ├── GrpcDraftClient, GrpcFavoritesClient, GrpcMessageClient
        ├── GrpcServerDiscoveryClient — server discovery
        ├── HermesGrpc, OwlGrpc — AI
        └── AiChatGrpc, SecretChatGrpc, ProfileClient

ChatListActivity → 10 modules (toolbar, tabs, FABs, auth, etc.)
NewChatActivity → 6 delegates (toolbar, input, selection, search, E2EE, menu)

Auth: JWT only (v2), no password fallback
Session: SessionManager (token refresh, auto-login recovery, FCM)
```

---

## Rules

1. Do NOT compile Android on server (OOM)
2. Do NOT deploy to prod without explicit instruction
3. Commit after each significant change
4. userId (UUID) — always as key, NOT username
5. i18n: all new strings simultaneously in values/strings.xml + values-ru/strings.xml
6. Do NOT initialize getString() in Activity class fields
7. Kotlin 2.3.21: cont.resume(value, onCancellation = {})
8. No forceReconnect — one connect at start, reconnect only on FAILED
9. Favorites — not a section in list, but a separate chat (type="favorites")
10. When extracting code from Activity — `internal` for fields/methods, proxy methods in Activity
11. Do not add new features without explicit request
12. Do not refactor working code without explicit request
13. All errors via `ErrorHandler.handle()` — NOT direct `Log.e`
14. v2 server only — no v1 legacy fallbacks in client code
15. All chat activities must call `WindowCompat.setDecorFitsSystemWindows(window, false)` in onCreate
16. Chat toolbars must use fixed `@dimen/custom_toolbar_height`, elevation 0dp
17. Chat stream: JWT only, no password fallback
18. Use GetChatsV2 (not v1 GetChats) for chat list

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
| `master` | Production (v1.1.3.38) |
| `feat/1.2.0.x` | v2 server development (current, v1.2.0.5) |
| `feat/1.1.3.x` | Previous release (merged to master) |
