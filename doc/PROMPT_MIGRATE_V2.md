# Prompt: Migrate Android Client to Messages v2

**Server:** v1.3.0.26 | **Date:** 2026-06-27 | **Status:** ✅ COMPLETE

---

## Goal

Migrate Android client from v1 message RPCs to v2. Server has completed v1→v2 data migration and removed dual-write. All new messages are now only in `messages_v2` table.

---

## What Changed on Server

| Before (v1) | After (v2) |
|---|---|
| Messages stored in `messages` table (encrypted_text BYTEA) | Messages stored in `messages_v2` (plain text + content_type) |
| Reactions in separate `reactions` table | Reactions inline in `messages_v2.reactions` JSONB |
| Sender identified by `username` string | Sender identified by `sender_id` UUID |
| Dual-write (v1 + v2) | v2 only |

---

## RPC Migration Map

### Replace these v1 RPCs with v2 equivalents:

| v1 RPC | v2 RPC | Notes |
|---|---|---|
| `GetHistory` | `GetHistoryV2` | Cursor-based pagination (no OFFSET) |
| `Chat` (stream for sending) | `SendMessageV2` (unary) or `ChatV2` (stream) | `ChatV2` recommended |
| `EditMessage` | `EditMessageV2` | Uses `message_id` (UUID) |
| `DeleteMessages` | `DeleteMessageV2` | Soft delete (content_type='deleted') |
| `SetReaction` | `SetReactionV2` | Inline JSONB, not separate table |
| (new) | `SearchMessages` | Message search (single chat or cross-chat) |

---

## Proto Changes

### New/updated message type: `MessageV2`

```protobuf
message MessageV2 {
  string id = 1;           // UUID
  string room_id = 2;
  string sender_id = 3;    // UUID (not username!)
  oneof content {
    string text = 10;
    MessageMedia media = 11;
    MessageReply reply = 12;
  }
  bool edited = 20;
  bool is_read = 21;
  google.protobuf.Timestamp created_at = 22;
  bytes reactions = 23;     // JSON: {"uuid":"emoji",...}
  bool is_e2ee = 30;
  string e2ee_payload = 31;
}

message MessageMedia {
  string type = 1;       // "image" | "voice" | "file"
  string url = 2;
  repeated string urls = 3;
  int32 duration = 4;
}

message MessageReply {
  string message_id = 1;
  string preview = 2;
}
```

### GetHistoryV2 (cursor pagination)

```protobuf
rpc GetHistoryV2(GetHistoryV2Request) returns (GetHistoryV2Response);

message GetHistoryV2Request {
  string room_id = 1;
  int32 limit = 2;       // default 50, max 200
  string cursor = 3;     // from previous response
}

message GetHistoryV2Response {
  repeated MessageV2 messages = 1;
  string next_cursor = 2;  // empty = no more
  bool has_more = 3;
}
```

**Pagination:**
1. First: `cursor = ""`
2. Response has `next_cursor`
3. Pass `next_cursor` as `cursor` in next request
4. Stop when `has_more = false`

### SendMessageV2

```protobuf
rpc SendMessageV2(SendMessageV2Request) returns (SendMessageV2Response);

message SendMessageV2Request {
  string room_id = 1;
  oneof content {
    string text = 2;
    MessageMedia media = 3;
  }
  string reply_to_id = 4;
  bool is_e2ee = 5;
  string e2ee_payload = 6;
}

message SendMessageV2Response {
  MessageV2 message = 1;
  bool success = 2;
  string error = 3;
}
```

### EditMessageV2

```protobuf
rpc EditMessageV2(EditMessageV2Request) returns (EditMessageV2Response);

message EditMessageV2Request {
  string message_id = 1;
  string text = 2;
}
```

### DeleteMessageV2

```protobuf
rpc DeleteMessageV2(DeleteMessageV2Request) returns (DeleteMessageV2Response);

message DeleteMessageV2Request {
  repeated string message_ids = 1;
  string requester_user_id = 2;
}
```

### SetReactionV2

```protobuf
rpc SetReactionV2(SetReactionV2Request) returns (SetReactionV2Response);

message SetReactionV2Request {
  string message_id = 1;
  string emoji = 2;       // empty = remove reaction
}

message SetReactionV2Response {
  bool success = 1;
  bytes reactions = 2;    // updated reactions JSON
}
```

### SearchMessages (NEW)

```protobuf
rpc SearchMessages(SearchMessagesRequest) returns (SearchMessagesResponse);

message SearchMessagesRequest {
  string room_id = 1;       // optional: empty = all user's chats
  string query = 2;         // search keyword (required)
  int32 limit = 3;          // default 20, max 100
}

message SearchMessagesResponse {
  repeated SearchResult messages = 1;
}

message SearchResult {
  string message_id = 1;
  string room_id = 2;
  string username = 3;      // sender username
  string preview = 4;       // text snippet (first 200 chars)
  string created_at = 5;    // ISO 8601
}
```

### ChatV2 Stream (recommended for real-time)

```protobuf
rpc ChatV2(stream ChatV2Message) returns (stream ChatV2Message);

message ChatV2Message {
  string jwt_token = 1;     // first message only (auth)
  string room_id = 2;
  oneof payload {
    MessageV2 message = 10;
    ChatV2Typing typing = 11;
    ChatV2System system = 12;
  }
}
```

---

## Implementation Steps

### 1. Update Protobuf
- Copy new message definitions from this doc to `messenger.proto`
- Regenerate: `protoc --go_out=gen --go_opt=paths=source_relative --go-grpc_out=gen --go-grpc_opt=paths=source_relative messenger.proto`

### 2. Update Chat Screen
- Replace `GetHistory` with `GetHistoryV2`
- Implement cursor-based pagination (replace OFFSET with cursor)
- Map `sender_id` → display name via user cache
- Parse `reactions` JSONB as `Map<String, String>` (userId → emoji)

### 3. Update Message Sending
- Replace `Chat` stream sending with `SendMessageV2` (unary) or `ChatV2` stream
- Use `oneof content` for text/media/reply

### 4. Update Reactions
- Replace `SetReaction` with `SetReactionV2`
- Parse reactions from `bytes reactions` field (JSON)

### 5. Update Search
- Add `SearchMessages` call for message search UI
- Support single-chat and cross-chat search

### 6. Update Edit/Delete
- Replace `EditMessage` with `EditMessageV2`
- Replace `DeleteMessages` with `DeleteMessageV2`

---

## Key Differences for UI

| Feature | v1 | v2 |
|---|---|---|
| Sender | `username` (String) | `sender_id` (UUID) → resolve via user cache |
| Reactions | Separate object | `bytes reactions` → parse JSON `{"uuid":"emoji"}` |
| Content | Flat fields (text, image_url, voice_url) | `oneof content` (text / media / reply) |
| Pagination | OFFSET-based | Cursor-based |
| Delete | Hard delete | Soft delete (content_type='deleted') |

---

## Testing

```bash
# After proto regeneration
./gradlew compileDebugKotlin
```

Test cases:
- [x] Chat list loads with correct unread counts
- [x] Message history loads with cursor pagination (GetHistoryV2)
- [x] Send text message appears in chat (SendMessageV2)
- [x] Send image message works
- [x] Send voice message works
- [x] Reply to message works
- [x] Edit message works (EditMessageV2)
- [x] Delete message works (DeleteMessageV2)
- [x] Add/remove reactions works (SetReactionV2)
- [x] Search messages works (SearchMessages — implemented)
- [x] ChatV2 stream auth + real-time delivery works

## Completion Summary

**Completed:** 2026-06-27

### Changes made:
1. **ChatV2 stream** — `startChatV2()` replaces old v1 `startChat()`. Full system signal handling (FORCE_LOGOUT, ONLINE_USERS_UPDATE, CHAT_DELETED, CLEAR_CACHE, etc.)
2. **GetHistoryV2** — cursor-based pagination replaces offset-based
3. **SendMessageV2** — unary RPC replaces v1 stream-based send
4. **EditMessageV2** — replaces v1 EditMessage
5. **DeleteMessageV2** — replaces v1 DeleteMessages
6. **SetReactionV2** — replaces v1 SetReaction
7. **SearchMessages** — NEW: server-side message search (single chat + cross-chat)
8. **Dead v1 code removed:** GrpcMessageClient.kt, GrpcMessageClientTest.kt, v1 message marshallers, v1 proto classes (GetHistoryRequest/Response, EditMessageRequest/Response, DeleteMessagesRequest/Response, ReactionRequest/Response)
9. **LastChatRequest simplified** — reduced to (roomId, callback), no more username/password storage
10. **Reconnection** — exponential backoff in v2 stream error handler

### Files modified:
- `RealGrpcClient.kt` — removed v1 stream, simplified LastChatRequest
- `GrpcClient.kt` — removed v1 facade methods, added v2 methods
- `GrpcMessageV2Client.kt` — added SearchMessages
- `MessagesV2Proto.kt` — added SearchMessages proto classes
- `MessagesV2Marshallers.kt` — added SearchMessages marshallers
- `ChatViewModel.kt` — all methods use v2
- `NewChatActivity.kt` — uses startChatV2
- `ChatSelectionDelegate.kt` — uses deleteMessageV2/sendMessageV2
- `ChatMessageMenuDelegate.kt` — uses editMessageV2/deleteMessageV2/setReactionV2
- `ChatInputDelegate.kt` — uses sendMessageV2
- `ShareReceiverActivity.kt` — uses startChatV2/sendMessageV2
- `SessionManager.kt` — uses startChatV2
- `GrpcFavoritesClient.kt` — removed dead saveFavoriteMessage

### Files deleted:
- `GrpcMessageClient.kt` — v1 message client
- `GrpcMessageClientTest.kt` — v1 message client tests
