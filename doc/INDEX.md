# Lavender Messenger — Android Documentation

**Version:** v1.1.3.38 | **Updated:** 2026-06-18

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
| Kotlin files | 163 |
| Total LOC | ~40,449 |
| Activities | 30 |
| gRPC modules | 21 |
| Unit tests | 9 files, ~5.5% coverage |
| Layout XML | 109 |
| String entries | 767 (EN + RU) |
| Min SDK | 29 (Android 10) |
| Kotlin | 2.3.21 |

---

## Architecture Overview

```
GrpcClient (facade, 711 LOC)
  └── RealGrpcClient (883 LOC) — orchestrator
        ├── GrpcConnectionManager, GrpcAuthClient, GrpcTypingClient
        ├── GrpcCallClient, GrpcChatListClient, GrpcProfileClient
        ├── GrpcDraftClient, GrpcFavoritesClient, GrpcMessageClient
        ├── GrpcServerDiscoveryClient, GrpcMarshallers
        ├── HermesGrpc (1872), OwlGrpc (1146) — AI
        └── AiChatGrpc, SecretChatGrpc, ProfileClient

ChatListActivity (382) → 10 modules (toolbar, tabs, FABs, auth, etc.)
NewChatActivity (755) → 6 delegates (toolbar, input, selection, search, E2EE, menu)
ProfileActivity (719) — monolithic, not refactored
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

---

## Servers

| | Dev | Prod |
|--|-----|------|
| gRPC | 50052 | 50051 |
| HTTP | 8083 | 8082 |
