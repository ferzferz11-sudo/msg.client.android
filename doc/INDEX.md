# Lavender Messenger — Android Documentation

**Version:** v1.2.0.4 | **Updated:** 2026-06-18

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
| Kotlin files | ~160 |
| Total LOC | ~39,000 |
| Activities | 28 |
| gRPC modules | 21 |
| Unit tests | 9 files |
| Layout XML | 108 |
| String entries | 767 (EN + RU) |
| Min SDK | 29 (Android 10) |
| Kotlin | 2.3.21 |
| Branch | feat/1.2.0.x (v2 server) |

---

## Architecture Overview

```
GrpcClient (facade, ~700 LOC)
  └── RealGrpcClient (~880 LOC) — orchestrator
        ├── GrpcConnectionManager, GrpcAuthClient, GrpcTypingClient
        ├── GrpcCallClient, GrpcChatListClient, GrpcProfileClient
        ├── GrpcDraftClient, GrpcFavoritesClient, GrpcMessageClient
        ├── GrpcServerDiscoveryClient, GrpcMarshallers
        ├── HermesGrpc, OwlGrpc — AI
        └── AiChatGrpc, SecretChatGrpc, ProfileClient

ChatListActivity → 10 modules (toolbar, tabs, FABs, auth, etc.)
NewChatActivity → 6 delegates (toolbar, input, selection, search, E2EE, menu)
ProfileActivity — monolithic, not refactored
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
| `feat/1.2.0.x` | v2 server development (current) |
| `feat/1.1.3.x` | Previous release (merged to master) |
