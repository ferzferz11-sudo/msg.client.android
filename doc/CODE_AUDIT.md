# Code Audit — Unused Imports, Functions & Outdated Code

**Date:** 2026-06-20 | **Version:** v1.3.0.5

---

## 1. Unused Functions (AiV2ChatManager)

`data/ai/AiV2ChatManager.kt` — 3 functions defined but never called:

| Function | Line | Status |
|----------|------|--------|
| `clearTokens()` | 77 | Never called from any file |
| `resetStreamState()` | 73 | Never called from any file |
| `emitTyping()` | 60 | Never called from any file |

**Action:** Consider removing or wiring into the UI flow.

---

## 2. Duplicate Dead Code (GrpcChatListClient)

`data/grpc/GrpcChatListClient.kt` — methods duplicated in `GrpcChatClient.kt`, never called:

| Method | GrpcChatListClient line | Called via RealGrpcClient? |
|--------|------------------------|---------------------------|
| `deleteChatWithUserId()` | 76 | No — delegates to `GrpcChatClient` |
| `updateChatAvatar()` | 150 | No — delegates to `GrpcChatClient` |
| `updateChatSettings()` | 172 | No — delegates to `GrpcChatClient` |
| `updateChatName()` | 194 | No — delegates to `GrpcChatClient` |
| `addParticipant()` | 216 | No — delegates to `GrpcChatClient` |
| `addParticipants()` | 239 | No — delegates to `GrpcChatClient` |
| `removeParticipant()` | 250 | No — delegates to `GrpcChatClient` |

These methods in `GrpcChatListClient` are dead code. `RealGrpcClient` routes all calls through `GrpcChatClient`, which has its own implementation.

---

## 3. Outdated Comments — AIService References

Code comments still reference the old `messenger.AIService/*` service name (fixed to `messenger.ChatService/*` in v1.3.0.3):

| File | Line | Current | Should Be |
|------|------|---------|-----------|
| `data/grpc/GrpcAIv2Marshallers.kt` | 9 | `messenger.AIService/*` | `messenger.ChatService/*` |
| `data/proto/AiV2Proto.kt` | 4 | `messenger.AIService/ChatWithAIV2` | `messenger.ChatService/ChatWithAIV2` |
| `data/proto/AiV2Proto.kt` | 40 | `messenger.AIService/CreateAIAgent, UpdateAIAgent, etc.` | `messenger.ChatService/CreateAIAgent, ...` |
| `data/proto/AiV2Proto.kt` | 155 | `messenger.AIService/ListAITools` | `messenger.ChatService/ListAITools` |

---

## 4. Outdated Layout Comment

| File | Line | Reference |
|------|------|-----------|
| `res/layout/widget_chat.xml` | 4 | Comment mentions `HermesChatActivity` — should reference `NewChatActivity` or be removed |

---

## 5. Unused Imports Check

All AI v2 files were checked for unused imports. **No unused imports found** in the 24 files audited:

- `AiV2ChatActivity.kt` — all imports used
- `AiV2AgentListActivity.kt` — all imports used
- `AiV2AgentCreateEditActivity.kt` — all imports used
- `AiV2AgentCreateEditViewModel.kt` — all imports used
- `AgentDetailActivity.kt` — all imports used
- `AgentDetailViewModel.kt` — all imports used
- `MarketplaceViewModel.kt` — all imports used
- `MarketplaceAgentAdapter.kt` — all imports used
- `RateAgentBottomSheet.kt` — all imports used
- `InstallAgentBottomSheet.kt` — all imports used
- `ReviewAdapter.kt` — all imports used
- `UsageStatsAdapter.kt` — all imports used
- `UsageStatsViewModel.kt` — all imports used
- `AiV2AgentListViewModel.kt` — all imports used
- `AiV2ChatViewModel.kt` — all imports used
- `AiV2ChatUseCase.kt` — all imports used
- `AiV2ChatManager.kt` — all imports used
- `AiV2Models.kt` — no imports (pure data classes)
- `AiV2DomainExtensions.kt` — all imports used
- `RateLimitCache.kt` — no imports
- `GrpcAIv2Client.kt` — all imports used
- `GrpcAIv2Marshallers.kt` — all imports used
- `GrpcChatListClient.kt` — all imports used
- `GrpcChatAuxClient.kt` — all imports used

---

## 6. Outdated Documentation Files

### README.md — **Severely outdated**
- Version shows `1.1.1.16` (current is `1.3.0.5`)
- References `OWL + Hermes` AI — replaced by AI v2 in v1.2.0.20
- Project structure lists `OwlGrpc.kt`, `HermesGrpc.kt` — deleted in v1.2.0.20
- Lists `OwlChatActivity`, `OwlSettingsActivity`, `HermesChatActivity`, etc. — deleted
- Missing: AI v2 activities, Remote Agent, HttpClient singleton
- Missing: `ui/ai/`, `ui/remote/`, `network/` packages

### AI_V2_TESTING.md — **Outdated service name**
- Line 9: References `messenger.AIService/*` — should be `messenger.ChatService/*`
- Test scenario T5.1-T5.4 mention HermesChatActivity/OwlSettingsActivity which no longer exist

### PATTERNS.md — **Correct and up to date**

### INDEX.md — **Correct and up to date**

### PROMPT_NEXT_SESSION.md — **Backlog outdated**
- Shows "v1.3.0.4 (релиз)" but current is v1.3.0.5
- Backlog items need review against current state

### REMOTE_AGENT.md — **Correct and up to date**

---

## Summary

| Category | Count | Priority |
|----------|-------|----------|
| Unused functions (AiV2ChatManager) | 3 | Medium |
| Dead code (GrpcChatListClient) | 7 methods | Low |
| Outdated comments (AIService) | 4 | Low |
| Outdated layout comment | 1 | Low |
| Unused imports | 0 | — |
| Outdated docs (README.md) | 1 | High |
| Outdated docs (AI_V2_TESTING.md) | 1 | Medium |
| Outdated docs (PROMPT_NEXT_SESSION.md) | 1 | Medium |
