# Lavender Messenger (Android) — Задачи

**Версия:** v1.1.3.14
**Обновлено:** 2026-06-16 (сессия 11)
**Ветка:** feat/1.1.3.x

---

## ✅ v1.1.3.14 — ChatStream v2 + ChatList v2

### Новое: ChatStream v2 (JWT auth)
- ✅ **ProfileClient.fetchServerInfo()** — парсит все версии сервисов (chat/auth/profile/ai)
- ✅ **isChatV2Supported()** / **isAuthV2Supported()** helpers
- ✅ **BearerTokenInterceptor** — убран пропуск Chat stream для v2 серверов
- ✅ **RealGrpcClient.startChat()** — JWT token для v2, password для v1
- ✅ Fallback на v1 если /info недоступен

### Новое: ChatList v2
- ✅ **GrpcClient** — pinChat, unpinChat, searchChats, archiveChat, unarchiveChat
- ✅ **RealGrpcClient** — низкоуровневый unaryCallChatListV2 для новых RPC
- ✅ **ChatInfo** — isPinned, isArchived, pinnedAt поля
- ✅ Все v2 методы возвращают false/empty на v1 серверах

### Proto updates
- ✅ PinChatRequestProto, SearchChatsResponseProto, etc.
- ✅ ChatInfoProto: isPinned, isMuted, isArchived, pinnedAt
- ✅ GetChatsRequestProto: limit, offset, filter
- ✅ MessageProto: jwtToken, isE2Ee, e2EePayload + Builder методы
- ✅ MessageProtoMarshaller: сериализация/deserialization новых полей

### Коммиты
- `cd2294d` — feat: ChatStream v2 + ChatList v2 Android client
- `cc759b7` — fix: add jwtToken to MessageProto
- `8731367` — fix: add onCancellation parameter
- `5bb47b6` — docs: update all docs

---

## ✅ v1.1.3.13 — ProfileService v2 client
(Сессия 9 — завершено)

---

## ✅ v1.1.3.12 — Bearer Token Interceptor + Token Refresh
(Сессия 8 — завершено)

---

## 📋 Активные задачи

### Высокий приоритет
- [ ] **ChatList v2 UI** — новая ChatListActivity с:
  - Секции: Pinned / Favorites / All Chats
  - Табы: All / AI / Groups
  - Search bar с real-time фильтрацией
  - Unread badges
  - Context menu: Pin, Mute, Archive, Delete
  - Swipe-to-refresh + infinite scroll
  - Shared element transitions

### Средний приоритет
- [ ] **Тесты для ProfileService v2** — unit-тесты для ProfileClient
- [ ] **Тесты для ChatList v2** — unit-тесты для pinChat/searchChats/archiveChat
- [ ] **Read receipts UI** — подключить MarkAsRead в ChatActivity

### Отложено
- [ ] Qdrant + CLIP (production RAG) — см. AI_SERVICES.md
- [ ] Выпуск Android v1.1.3.14 — делается ферзем лично после завершения v2 UI

---

## 🔑 Ключевые решения

| Решение | Обоснование |
|---------|-------------|
| fetchServerInfo fallback | Если /info недоступен → v1 для всех сервисов |
| ChatStream v2 auth | JWT token в первом сообщении stream вместо password |
| ChatList v2 API | Отдельные RPC методы (PinChat, SearchChats, etc.) |
| v2 методы на v1 сервере | Возвращают false/empty — UI адаптируется |
| onCancellation = {} | Обязательно в Kotlin 2.3.21 для cont.resume() |

---

## 📁 Ключевые файлы

| Файл | Назначение |
|------|------------|
| `ProfileClient.kt` | ProfileService v2 client + fetchServerInfo |
| `BearerTokenInterceptor.kt` | JWT Bearer token interceptor (v2: Chat stream) |
| `RealGrpcClient.kt` | gRPC реализация (JWT auth, ChatList v2 RPC) |
| `GrpcClient.kt` | Facade (pinChat, searchChats, archiveChat, etc.) |
| `MessengerProto.kt` | Proto data classes (ChatList v2, jwt_token, etc.) |
| `Message.kt` | ChatInfo model (isPinned, isArchived, pinnedAt) |
