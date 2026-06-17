# Lava Messenger — План реализации v1.1.3.33+

**Дата:** 2026-06-17 | **Версия:** v1.1.3.33

---

## Фаза 0: Стабилизация (v1.1.3.33) — ✅ ЗАВЕРШЕНА
- Тестирование на реальных чатах ✅
- Багов не найдено ✅

## Фаза 1: Рефакторинг NewChatActivity (v1.1.3.33) — ✅ ЗАВЕРШЕНА
- NewChatActivity: 1473→754 LOC (-49%) ✅
- 6 модулей: ChatToolbarDelegate, ChatInputDelegate, ChatSelectionDelegate, ChatSearchDelegate, ChatE2EEDelegate, ChatMessageMenuDelegate ✅

## Фаза 2: Унификация Error Handling (v1.1.3.33) — ✅ ЗАВЕРШЕНА
- Все gRPC модули используют ErrorHandler.handle() ✅
- ChatListViewModel.error StateFlow ✅
- ChatListActivity Snackbar для ошибок ✅

---

## Фаза 3: Тесты для gRPC клиента (v1.1.3.34) — СЛЕДУЮЩАЯ

**Проблема:** 0 unit-тестов для gRPC клиента.

**План:**
1. Создать тестовый фреймворк с мок-сервером (grpc-java in-process server)
2. Тесты для GrpcAuthClient (signIn, signUp, refreshToken)
3. Тесты для GrpcChatListClient (getChats, pinChat, searchChats)
4. Тесты для GrpcMessageClient (sendMessage, loadHistory, markRead)
5. Тесты для GrpcConnectionManager (connect, reconnect, disconnect)

**Оценка:** 1-2 сессии

---

## Фаза 4: Оптимизация GrpcClient facade (v1.1.3.35)

**Проблема:** 780 строк facade с множеством proxy-методов без логики.

**План:**
1. Заменить proxy-методы на extension functions
2. Группировать методы по доменам
3. Цель: GrpcClient < 400 LOC

**Оценка:** 1 сессия

---

## Фаза 5: AI Chats domain layer (v1.1.3.36)

**Проблема:** HermesGrpc (1876 LOC) + OwlGrpc (1145 LOC) = 3021 строк AI-кода в gRPC слое.

**План:**
1. Создать `data/ai/` слой:
   - `AiChatManager` — единый менеджер AI чатов
   - `OwlDataSource` — OWL-специфичная логика
   - `HermesDataSource` — Hermes-специфичная логика
2. Вынести AI логику из gRPC модулей
3. gRPC модули оставить как thin transport layer

**Оценка:** 1-2 сессии

---

## Backlog (отложено)

- Pagination для чатов (GetChatsRequest limit/offset)
- Incremental history loading
- Certificate pinning
- Encrypted SharedPreferences
- Shared element transitions
- Qdrant + CLIP (production RAG)
- ProfileActivity рефакторинг (719 LOC)
- ConferenceLobbyActivity рефакторинг (581 LOC)
- EditProfileActivity рефакторинг (535 LOC)

---

## Метрики успеха

| Метрика | Текущая | Цель (v1.1.3.36) |
|---------|---------|-------------------|
| NewChatActivity | 754 | < 400 |
| GrpcClient | 780 | < 400 |
| AI code in gRPC | 3021 | < 1000 (transport only) |
| Unit tests | 0 | > 20 |
| Error handling | unified | unified ✅ |
