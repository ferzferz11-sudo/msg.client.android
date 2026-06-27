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
| `CHANGELOG.md` | Version history |

## Architecture decisions

- **AI services redesign (completed)**: Bottom sheet: Add Agent (checkboxes) → Create Chat → Remote Agents. Notifications removed from bottom sheet. Multi-agent AI chats supported. Full chat parity (files, images, camera). Old Activities deleted. AiV2ChatActivity preserved and enhanced. New AiAgentSetupActivity for agent creation. AiV2AgentListActivity re-added with 5 tabs (Presets, My Agents, Discover, Remote Agent, Usage).
- **ChatWidget is the shared chat UI component**: Used in NewChatActivity (regular chats) and AiV2ChatActivity (AI chats). Provides toolbar, RecyclerView, input panel with attach/audio/emoji buttons, reply preview, mention popup, search bar.
- **Client-side routing for multi-agent chats (Вариант A)**: Client sends separate ChatWithAIV2 requests for each selected agent and aggregates responses.
- **AI chat history and list RPCs**: GetAIV2ChatHistory (session_id, limit → messages) and ListAIV2Chats (empty request → chats) implemented as unary RPCs.
- **Client-side AI chat merging**: Server's `GetUserChatsV2` excludes AI chats. Client loads AI chats separately via `ListAIV2Chats` RPC and merges into main chat list.
- **Notifications consolidated into remote agent chat**: Notifications appear as system messages in `RemoteAgentActivity` chat via real-time gRPC subscription.
- **ChatKeepAliveService prevents process kill (v1.3.0.18)**: START_STICKY foreground service monitors `connectionStatus` and auto-reconnects.
- **Consolidated agent settings screen (v1.3.0.20+)**: All agent configuration (API key, temperature, maxTokens, model, etc.) into AiAgentSetupActivity.
- **AgentInfoV2 provider_config deployed (v1.3.0.21)**: `string provider_config = 22` in `AgentInfoV2` proto. Server `agentToProto()` marshals `ProviderConfig` as JSON. Server prompt: `/Users/paveld/LavenderMessenger-server/doc/PROMPT_PROVIDER_CONFIG.md`.
- **Favorites implemented client-side via SharedPreferences**: `FavoriteAgentsManager` singleton uses `favorite_agents` prefs with JSON array of agent IDs. No server support.
- **Marketplace cache in Room DB**: `marketplace_agents` table (version 11 migration). `MarketplaceDao.sync()` clears and reinserts. Cache fallback on network error.
- **Usage Stats tab (5th tab)**: `UsageStatsFragment` with summary cards + per-agent RecyclerView. Auto-refresh 30s.
- **AIBottomSheet navigation re-open pattern**: Set `activity.shouldReopenAIBottomSheet = true` before `startActivity()`. In `ChatListActivity.onResume()`, check flag and re-call `showAIBottomSheet()`.
- **AIBottomSheet shows only user agents (v1.3.0.22)**: Presets removed from bottom sheet. Users access presets via AiV2AgentListActivity. Loading/empty states added.
- **ProviderConfig display fix (v1.3.0.22)**: Preset agents have `{"api_key_source": "server", ...}` without `api_key`. AiAgentSetupActivity now checks `api_key_source` and shows "Server key" placeholder. Masked key shown for user agents with keys.

## Discovered durable knowledge

- **Auth handled at channel level, not per-call**: `GrpcConnectionManager.kt:107` adds `BearerTokenInterceptor` to the managed channel builder.
- **AI v2 architecture**: Client uses hand-rolled protobuf marshallers (no .proto files). All gRPC calls through messenger.ChatService/*. 8 LLM providers. AiV2ChatUseCase orchestrates tool calling loop (max 10 iterations).
- **AI database tables**: agents_v2, ai_chats_v2, ai_messages_v2, ai_rate_limits, ai_usage_stats, agent_reviews.
- **FileProvider configured**: `${applicationId}.provider` in AndroidManifest.xml.
- **RemoteAgents vs AI Agents are separate systems**: `RemoteAgentGrpc` for machine/SSH agents. AI v2 preset agents use `GrpcAIv2Client.listAgents()`.
- **Server AI preset agent seeding**: `seedPresetAgents()` uses `ON CONFLICT (id) DO UPDATE`. Seeds 10 agents.
- **Don't call gRPC in ViewModel init**: gRPC channels may not be fully ready during init phase.
- **Server silent empty on auth fail (AI v2)**: Handlers return empty proto responses when auth fails. Client cannot distinguish "no data" from "auth failure".
- **All AI proto field numbers verified match**: Server proto and client marshallers in sync across all 15 AI messages.
- **AI Chat Settings toolbar subtitle**: Shows masked key / "Server key" / "No key".
- **Room DB version 11**: `marketplace_agents` table added.
- **File attachments in AI chat via HTTP upload**: Non-image files uploaded via HTTP to `{server}/upload-file`, URL embedded in message text.
- **Image sending reads bytes in Activity, not UseCase**: UseCase tried reading via `RealGrpcClient.appContext?.contentResolver` which may be null. Activity reads bytes directly.
- **API key stored in providerConfig JSON (snake_case)**: Server stores as `{"api_key": "..."}`. Server `resolveAPIKey()` reads `agent.ProviderConfig["api_key"]`. For preset agents: `{"api_key_source": "server", "default_model": "..."}` — no `api_key` field.
- **ALWAYS verify against server code**: Before any gRPC/marshaller change, check `/Users/paveld/LavenderMessenger-server/doc/CLIENT_INTEGRATION.md` AND the actual server source code at `/Users/paveld/LavenderMessenger-server/`.
- **API key is stored in TWO places**: `agents_v2.provider_config` (per-agent) AND `ai_chat_settings` (per-session).
- **Agent edit save button UX**: Save button created dynamically as FrameLayout overlay. Only appears when changes detected.
- **AI chat error propagation**: Errors shown as chat messages (⚠️ prefix). Rate limit gets dedicated StateFlow.
- **ChatWidget button defaults are both GONE**: Activities must manually toggle via TextWatcher.
- **AI chat list is separate from server chat list**: `GetUserChatsV2` excludes AI chats.
