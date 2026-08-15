---
feature: connection-auth-fix
status: designed
updated: 2026-08-14
branch: feat/1.4.0.x
---

# Connection & Auth Resilience Fix

## Report

## [S1] Problem
Periodic UNAUTHENTICATED errors from expired tokens cause cascading failures:

1. **ChatV2 stream dies with no recovery** — `onError`/`onClose` (RealGrpcClient:742-763) set `FAILED` but never schedule a reconnect. Stream is a one-shot; recovery depends on external triggers (keep-alive service, activity resume) which may not fire.
2. **Token refresh blocked by dead connection** — `ensureFreshToken()` (SessionManager:184-196) waits for `connectionStatus == READY` with 5s timeout. If connection is FAILED, refresh silently skips → stale token → UNAUTHENTICATED cascade.
3. **Reconnect loop on expired token** — When connection does reconnect, ChatV2 starts with stale token → UNAUTHENTICATED → FAILED again. `isAuthFailure` flag exists in `GrpcReconnectStrategy` but is never set from the stream error path.
4. **getAdminUserList/getAdminUserSessions have NO UNAUTHENTICATED retry** (GrpcChatAuxClient:214-262). Other methods (getAllUsers, setMutedChat) retry once.
5. **Status flapping** — UI shows "не в сети" ↔ "подключение" because FAILED→CONNECTING→FAILED cycles have no debounce.
6. **No network detection** — no-internet and server-down are treated identically; futile RPCs fire regardless.

## [S2] Design

### S2.1 — ChatV2 stream auto-reconnect (Critical)
When ChatV2 `onClose` fires with non-OK status (RealGrpcClient:756-763):
- `UNAUTHENTICATED`: set `isAuthFailure = true`, attempt `forceTokenRefresh()` once. If refresh succeeds → clear `isAuthFailure`, reconnect. If fails → set FAILED, emit `authStatus = "AUTH_FAILED"`.
- `UNAVAILABLE` / `DEADLINE_EXCEEDED` / `UNKNOWN`: schedule reconnect via `connectionManager.scheduleReconnect()` with exponential backoff.
- `OK`: set DISCONNECTED (no reconnect needed — intentional close).

When ChatV2 `onError` fires (line 742-745): same logic based on exception type.

### S2.2 — Token refresh bypass for dead connection (Critical)
In `ensureFreshToken()` (SessionManager:184-196): if connection is not READY after 5s, DON'T silently return. Instead:
- If we have a refresh token, attempt the refresh RPC directly (the gRPC channel may still work for unary calls even if ChatV2 stream is dead)
- Only skip if the channel itself is null/shutdown

### S2.3 — GrpcChatAuxClient UNAUTHENTICATED retry (High)
Add retry-once pattern to `getAdminUserList` and `getAdminUserSessions`, matching existing `getAllUsers`/`setMutedChat` pattern (lines 43-47).

### S2.4 — Status change debounce (Medium)
In `GrpcConnectionManager`: add a cooldown (500ms) on status transitions. If status changes from FAILED→CONNECTING→FAILED within 500ms, suppress the intermediate CONNECTING — UI stays at FAILED until a stable CONNECTING persists.

### S2.5 — Connection status refinement (Medium)
Add `OFFLINE` state: when all reconnects fail with UNAVAILABLE and device has no internet (check via ConnectivityManager). ChatListActivity shows "Нет интернета" instead of flapping.

## [S3] Out of Scope
- UI redesign of connection status indicators
- Offline message queueing
- Server-side token TTL changes
- BearerTokenInterceptor proactive refresh (would require interceptor-level async token refresh — too invasive)
- `registerToken()` error handling (GrpcChatAuxClient:105 — low impact)

## Tasks
- [ ] T1: ChatV2 stream reconnect — handle UNAUTHENTICATED (refresh+retry) and UNAVAILABLE (backoff reconnect) in `onClose`/`onError` (covers: S2.1)
- [ ] T2: Token refresh bypass — don't block on READY when channel exists for unary calls (covers: S2.2)
- [ ] T3: GrpcChatAuxClient — add UNAUTHENTICATED retry to `getAdminUserList` + `getAdminUserSessions` (covers: S2.3)
- [ ] T4: Status debounce — suppress rapid FAILED→CONNECTING→FAILED flapping (covers: S2.4)
- [ ] T5: Build + test + CHANGELOG (covers: all)
