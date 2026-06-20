# Code Audit — Unused Imports, Functions & Outdated Code

**Date:** 2026-06-20 | **Version:** v1.3.0.9

---

## Результат аудита v1.3.0.8

Все найденные проблемы были исправлены в v1.3.0.8.

---

## Результат аудита v1.3.0.9

### 1. Серверное соответствие

Проверены все gRPC методы по `CLIENT_INTEGRATION.md` (server v1.3.0.16):

**Корректно:**
- AuthService: SignInV2, SignUpV2, RefreshToken, SignOut, RevokeDevice (messenger.AuthService)
- ProfileService v2: GetProfile, UpdateProfile, UpdateAvatar, DeleteProfile, GetUserSettings, UpdateUserSettings (messenger.ProfileService)
- AI v2: все 15 RPC на messenger.ChatService, все 26 marshallers корректны
- Chat CRUD: GetChatsV2, CreateDirectChat, CreateGroupChat, DeleteChat
- Pin/Archive/Search: PinChat, UnPinChat, ArchiveChat, UnarchiveChat, SearchChats
- Secret Chat: CreateSecretChat, ExchangeSecretKey, GetSecretChatKey
- Messages: GetHistory, MarkRead
- GetAllUsers: userId (field 6) + isSuperAdmin (field 7)

**Исправлено:**
- `searchChats` timestamp: `seconds` → `seconds * 1000 + nanos / 1000000` (millisecond precision)
- `getAllChats` isMuted: hardcoded `false` → `proto.isMuted`

### 2. Cursor Pagination

Добавлена cursor-based пагинация для GetChatsV2:
- `GetChatsRequestProto`: добавлен `cursor` field
- `GetChatsResponseProto`: добавлены `nextCursor`, `hasMore`
- `GetChatsRequestMarshaller`: сериализация `filter` (field 5), `cursor` (field 6)
- `GetChatsResponseMarshaller`: парсинг `next_cursor` (field 2), `has_more` (field 3)

### 3. GrpcProfileClient

Старый `GrpcProfileClient` (v1) оставлен — методы themes/contacts/devices/passwords не имеют v2 замены в `ProfileClient`. Все методы корректно работают через `messenger.ChatService`.

---

## Статистика изменений v1.3.0.9

| Category | Count |
|----------|-------|
| Файлов изменено | 12 |
| Proto fields добавлено | 3 |
| Marshallers обновлено | 2 |
| Timestamp исправлено | 2 |
| UI табов упрощено | 5 → 3 |
