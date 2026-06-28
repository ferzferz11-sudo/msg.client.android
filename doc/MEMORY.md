# Project Memory

Lavender Messenger — Android client. Kotlin, gRPC, AI v2 marketplace, secret chats, remote agent support.

## Rules
- **Server SSH**: SSH alias `lava`, server code at `/Users/paveld/LavenderMessenger-server/` locally, `/root/msg/` on prod.
- **Testing target**: User tests on production server (prod). Dev server may not have preset agents.
- **Latest library versions**: User explicitly states project targets latest stable library versions.
- **Server changes via prompts**: Server-side changes documented as prompts in `/Users/paveld/LavenderMessenger-server/doc/`, not made directly by Android agent.
- **Token refresh before gRPC**: Always call `SessionManager.ensureFreshToken()` synchronously via `withContext(Dispatchers.IO)` inside ViewModel methods BEFORE any gRPC call.

## Documentation Index

| File | Content |
|------|---------|
| `doc/INDEX.md` | Project overview, architecture, quick stats, rules |
| `doc/PATTERNS.md` | Code patterns, architecture conventions, rules |
| `doc/GOTCHAS.md` | Discovered knowledge, gotchas, edge cases |
| `doc/PROMPT_NEXT_SESSION.md` | Current plan, backlog, session context |
| `doc/REMOTE_AGENT.md` | Remote Agent reference |
| `doc/AI_V2_TESTING.md` | AI v2 test scenarios |
| `doc/PROMPT_HERMES_ACP_CLIENT.md` | Hermes ACP client plan |
| `CHANGELOG.md` | Version history |

## Architecture decisions

- **Hermes Agent ACP integration (v1.3.1.04)**: New provider type `hermes_acp` on server. Spawns `hermes acp` as persistent child process per user. JSON-RPC 2.0 over stdin/stdout. Client needs only emoji mapping (🔬) in 3 files — no proto/gRPC changes. Preset agent "hermes" seeded with tools=true. Server: `ai_provider_hermes_acp.go`, `ai_provider_registry.go`, `db_ai_v2.go`. Client: `AIBottomSheet.kt`, `AiV2AgentListAdapter.kt`, `AiV2ChatActivity.kt`.
- **ChatV2 clientVersion field (v1.3.1.04)**: `ChatV2MessageProto` now includes `clientVersion` (field 3). First auth message sends `BuildConfig.VERSION_NAME`. Server updates `users.last_client_version` and `users.last_seen_at`. Fixed stale admin panel versions.
- **SendMessageV2 UpdateLastSeen (v1.3.1.04)**: Server `SendMessageV2` handler now calls `UpdateLastSeen` — previously `last_seen_at` was never updated on message send because client uses unary RPC, not ChatV2 stream.
- **AI services redesign (completed)**: Bottom sheet: Add Agent (checkboxes) → Create Chat → Remote Agents. Notifications removed from bottom sheet. Multi-agent AI chats supported. Full chat parity (files, images, camera). Old Activities deleted. AiV2ChatActivity preserved and enhanced. New AiAgentSetupActivity for agent creation. AiV2AgentListActivity re-added with 5 tabs (Presets, My Agents, Discover, Remote Agent, Usage).
- **ChatWidget is the shared chat UI component**: Used in NewChatActivity (regular chats) and AiV2ChatActivity (AI chats). Provides toolbar, RecyclerView, input panel with attach/audio/emoji buttons, reply preview, mention popup, search bar.
- **Client-side routing for multi-agent chats (Вариант A)**: Client sends separate ChatWithAIV2 requests for each selected agent and aggregates responses.
- **AI chat history and list RPCs**: GetAIV2ChatHistory (session_id, limit → messages) and ListAIV2Chats (empty request → chats) implemented as unary RPCs.
- **Client-side AI chat merging**: Server's `GetUserChatsV2` excludes AI chats. Client loads AI chats separately via `ListAIV2Chats` RPC and merges into main chat list.
- **Notifications consolidated into remote agent chat**: Notifications appear as system messages in `RemoteAgentActivity` chat via real-time gRPC subscription.
- **ChatKeepAliveService prevents process kill (v1.3.0.18)**: START_STICKY foreground service monitors `connectionStatus` and auto-reconnects.
- **Consolidated agent settings screen (v1.3.0.20+)**: All agent configuration (API key, temperature, maxTokens, model, etc.) into AiAgentSetupActivity.
- **AgentInfoV2 provider_config deployed (v1.3.0.22)**: `string provider_config = 22` in `AgentInfoV2` proto. Server `agentToProto()` marshals `ProviderConfig` as JSON. For preset agents: `{"api_key_source": "server", ...}` — no `api_key`. For user agents: `{"api_key": "sk-...", ...}`.
- **AIBottomSheet shows only user agents (v1.3.0.22)**: Presets removed from bottom sheet. Users access presets via AiV2AgentListActivity. Loading/empty states added.
- **ProviderConfig display fix (v1.3.0.22)**: AiAgentSetupActivity now checks `api_key_source` and shows "Server key" placeholder for presets. Masked key shown for user agents with keys.
- **Favorites implemented client-side via SharedPreferences**: `FavoriteAgentsManager` singleton uses `favorite_agents` prefs with JSON array of agent IDs. No server support.
- **Marketplace cache in Room DB**: `marketplace_agents` table (version 11 migration). `MarketplaceDao.sync()` clears and reinserts. Cache fallback on network error.
- **Usage Stats tab (5th tab)**: `UsageStatsFragment` with summary cards + per-agent RecyclerView. Auto-refresh 30s.
- **AIBottomSheet navigation re-open pattern**: Set `activity.shouldReopenAIBottomSheet = true` before `startActivity()`. In `ChatListActivity.onResume()`, check flag and re-call `showAIBottomSheet()`.

## Discovered durable knowledge

- **Secret chat E2EE status must NOT be overwritten by observer flow**: `updateSubtitle()` is called by `combine(grpcClient.users, grpcClient.connectionStatus, grpcClient.typingUsers, grpcClient.allUsers)`. For secret chats, this overwrites E2EE status with participant/online count. Fix: `isSecret` check with early return at the start of `updateSubtitle()`.
- **ChatE2EEDelegate must use callbacks, not direct toolbarSubtitle**: Setting `toolbarSubtitle.text` directly causes race condition with observer flow. Use `onKeyExchangeStart`/`onKeyExchangeComplete` callbacks → `toolbarDelegate.isE2eeInProgress`.
- **`E2EEManager.isE2EEActive(context, chatId)`** checks SharedPreferences for shared secret. Returns true if key exchange completed.
- **Secret chats need `IS_SECRET` intent extra**: Without it, `isSecret = false` on re-entry → no E2EE init, wrong toolbar status.
- **Call button was lost in bae73d5 refactor**: `onCreateOptionsMenu`/`onPrepareOptionsMenu`/`onOptionsItemSelected` removed from NewChatActivity but not migrated to any of the 6 delegates. Restored in v1.3.1.05.
- **`ONLINE_USERS_UPDATE` may not include current user**: Server sends list of online usernames. Current user might be missing → "0 online" even when connected. Secret chats now show E2EE status instead.

## Discovered durable knowledge

- **Auth handled at channel level, not per-call**: `GrpcConnectionManager.kt:107` adds `BearerTokenInterceptor` to the managed channel builder.
- **AI v2 architecture**: Client uses hand-rolled protobuf marshallers (no .proto files). All gRPC calls through messenger.ChatService/*. 9 LLM providers (openrouter, local, mimo, webhook, websocket, subprocess, mcp, reve, hermes_acp). AiV2ChatUseCase orchestrates tool calling loop (max 10 iterations).
- **AI database tables**: agents_v2, ai_chats_v2, ai_messages_v2, ai_rate_limits, ai_usage_stats, agent_reviews.
- **FileProvider configured**: `${applicationId}.provider` in AndroidManifest.xml.
- **RemoteAgents vs AI Agents are separate systems**: `RemoteAgentGrpc` for machine/SSH agents. AI v2 preset agents use `GrpcAIv2Client.listAgents()`.
- **Server AI preset agent seeding**: `seedPresetAgents()` uses `ON CONFLICT (id) DO UPDATE`. Seeds 11 agents (was 10, +hermes).
- **Don't call gRPC in ViewModel init**: gRPC channels may not be fully ready during init phase.
- **Server silent empty on auth fail (AI v2)**: Handlers return empty proto responses when auth fails. Client cannot distinguish "no data" from "auth failure".
- **All AI proto field numbers verified match**: Server proto and client marshallers in sync across all AI messages.
- **AI Chat Settings toolbar subtitle**: Shows masked key / "Server key" / "No key".
- **Room DB version 11**: `marketplace_agents` table added.
- **File attachments in AI chat via HTTP upload**: Non-image files uploaded via HTTP to `{server}/upload-file`, URL embedded in message text.
- **Image sending reads bytes in Activity, not UseCase**: UseCase tried reading via `RealGrpcClient.appContext?.contentResolver` which may be null. Activity reads bytes directly.
- **API key stored in providerConfig JSON (snake_case)**: Server stores as `{"api_key": "..."}`. Server `resolveAPIKey()` reads `agent.ProviderConfig["api_key"]`. For preset agents: `{"api_key_source": "server", "default_model": "..."}` — no `api_key` field.
- **API key in TWO places**: `agents_v2.provider_config` (per-agent) AND `ai_chat_settings` (per-session).
- **Agent edit save button UX**: Save button created dynamically as FrameLayout overlay. Only appears when changes detected.
- **AI chat error propagation**: Errors shown as chat messages (⚠️ prefix). Rate limit gets dedicated StateFlow.
- **ChatWidget button defaults are both GONE**: Activities must manually toggle via TextWatcher.
- **AI chat list is separate from server chat list**: `GetUserChatsV2` excludes AI chats.
- **Messages V2 migration complete (v1.3.1.01)**: All v1 message RPCs removed. Only v2: ChatV2 stream, GetHistoryV2, SendMessageV2, EditMessageV2, DeleteMessageV2, SetReactionV2, SearchMessages.
- **Message ID sync critical**: Server returns its own ID in SendMessageV2Response. Client MUST update local message + Room DB with server ID to prevent duplicates on re-entry.
- **Single reconnection path**: Only ChatKeepAliveService triggers reconnection. Stream handlers set FAILED status, ChatKeepAliveService detects and calls connect(). No retry loops in stream handlers.
- **Favorites is virtual room**: `favorites_<username>` — messages stored in messages_v2. GetHistoryV2 returns them. getFavorites() uses v2 marshallers with UUID→username resolution.
- **Server soft-deletes messages**: Sets `content_type = 'deleted'`, returns `"[deleted]"` as text. Client filters these out in GetHistoryV2 response, Room DB cache, and ChatV2 stream (v1.3.1.02).
- **API key visibility toggle**: TextInputLayout uses `endIconMode="password_toggle"` for eye icon. Long-press copies key to clipboard (v1.3.1.02).
- **AuthManager.getBearerToken() returns "Bearer " prefix**: Do NOT add another "Bearer " — results in double prefix → 401.
- **HttpClient AuthInterceptor**: `HttpClient.init(context)` in SplashActivity. Interceptor reads `getBearerToken()` per request. Replaces all manual auth headers.
- **ChatV2MessageProto clientVersion (field 3)**: Server proto has `client_version` at field 3. Client was missing it entirely. Fixed in v1.3.1.04: added to proto, marshaller, and first auth message.
- **SendMessageV2 missing UpdateLastSeen**: Client uses unary RPC, not ChatV2 stream. Server handler never called UpdateLastSeen. Fixed in v1.3.1.04: added to SendMessageV2, EditMessageV2, DeleteMessageV2, SetReactionV2.
- **Hermes ACP provider**: Server-side `ai_provider_hermes_acp.go` with JSON-RPC 2.0, persistent sessions. Client needs only emoji mapping — no proto changes.
