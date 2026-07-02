# Plan: Update doc/PROMPT_NEXT_SESSION.md + Sync All Doc Versions

**Status:** COMPLETED (v1.3.1.16)
**Date:** 2026-07-02
**Moved from:** `.mimocode/plans/1782971591177-calm-rocket.md`

---

## Context

Project is at v1.3.1.15 (branch `feat/1.3.1.x`). The `doc/PROMPT_NEXT_SESSION.md` already exists and is mostly current. Other doc files have version drift (INDEX.md at v1.3.1.12, PATTERNS.md/GOTCHAS.md at v1.3.1.13). v1.3.1.14 was skipped in CHANGELOG.

## Changes (COMPLETED)

### 1. `doc/PROMPT_NEXT_SESSION.md` — refresh content
- ✅ Added `CallActionService.kt` to key files audit table
- ✅ Added v1.3.1.14-15 context to audit scope
- ✅ Added `CallController.kt` to key files
- ✅ Architecture tree verified current

### 2. `doc/INDEX.md` — update version
- ✅ Version v1.3.1.12 → v1.3.1.16
- ✅ Architecture overview version label updated
- ✅ `CallActionService` added to Services count (4 services)

### 3. `doc/PATTERNS.md` — add v1.3.1.14-15 patterns
- ✅ Version v1.3.1.13 → v1.3.1.16
- ✅ CallActionService pattern added
- ✅ SessionManager resilience pattern added
- ✅ gRPC retry backoff pattern added (v1.3.1.16)
- ✅ Content hash UUID pattern added (v1.3.1.16)
- ✅ isAuthFailure guard pattern added (v1.3.1.16)

### 4. `doc/GOTCHAS.md` — add v1.3.1.14-15 gotchas
- ✅ Version v1.3.1.13 → v1.3.1.16
- ✅ CallActionService replaces IntentService
- ✅ `SessionManager.ensureFreshToken` READY wait
- ✅ `isRefreshing` guard
- ✅ Stale APK cleanup
- ✅ Thread safety audit gotchas (v1.3.1.16)
- ✅ Memory leak fixes gotchas (v1.3.1.16)
- ✅ gRPC resilience fixes gotchas (v1.3.1.16)
- ✅ Room DB index gotcha (v1.3.1.16)

## Verification

- ✅ All version references consistent at v1.3.1.16
- ✅ Architecture tree matches current codebase
- ✅ Key files table covers all critical files
- ✅ CHANGELOG.md has v1.3.1.16 entry
