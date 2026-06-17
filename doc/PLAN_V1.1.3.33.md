# Lava Messenger — План реализации v1.1.3.33+

**Дата:** 2026-06-17 | **Версия:** v1.1.3.32 → v1.1.3.33+
**Ветка:** feat/1.1.3.x

---

## Фаза 0: Стабилизация (v1.1.3.33) — ТЕКУЩАЯ

### 0.1 Тестирование на реальных чатах
- [ ] Протестировать read receipts broadcast в реальном общении
- [ ] Проверить loadChats() timeout behavior
- [ ] Проверить tab order (Все → Группы → ИИ чаты)
- [ ] Проверить FAB [+] → ActionBottomSheet → SearchableListBottomSheet
- [ ] Проверить Favorites из шторки профиля

### 0.2 Багфиксы (по результатам тестирования)
- [ ] Собрать и задокументировать найденные баги
- [ ] Приоритизировать и исправить

---

## Фаза 1: Рефакторинг NewChatActivity (v1.1.3.34)

**Проблема:** 1473 строки в одном Activity — создание чатов, поиск, UI, навигация.

**План:**
1. Создать `NewChatViewModel` — вся бизнес-логика (createDirectChat, createGroupChat, createSecretChat, getContacts, addContact, searchUsers)
2. Вынести UI в отдельные файлы:
   - `NewChatSearchDelegate` — поиск пользователей + UserAdapter
   - `NewChatDialogsDelegate` — диалоги создания чата (имя группы, topic конференции)
   - `NewChatActivity` — только onCreate, setupUI, lifecycle, proxy methods
3. Цель: NewChatActivity < 400 LOC

**Оценка:** 1 сессия

---

## Фаза 2: Унификация Error Handling (v1.1.3.35)

**Проблема:** ErrorHandler.kt существует, но используется частично. Где-то try-catch, где-то callback, где-то flow.

**План:**
1. Аудит всех error paths в gRPC модулях
2. Унифицировать: все gRPC ошибки → ErrorHandler → AppLog + Toast
3. Стандартизировать сообщения об ошибках (i18n)
4. Добавить error state в ChatListViewModel (StateFlow<String?>)

**Оценка:** 1 сессия

---

## Фаза 3: Тесты для gRPC клиента (v1.1.3.36)

**Проблема:** 0 unit-тестов для gRPC клиента.

**План:**
1. Создать тестовый фреймворк с мок-сервером (grpc-java in-process server)
2. Тесты для GrpcAuthClient (signIn, signUp, refreshToken)
3. Тесты для GrpcChatListClient (getChats, pinChat, searchChats)
4. Тесты для GrpcMessageClient (sendMessage, loadHistory, markRead)
5. Тесты для GrpcConnectionManager (connect, reconnect, disconnect)

**Оценка:** 1-2 сессии

---

## Фаза 4: Оптимизация GrpcClient facade (v1.1.3.37)

**Проблема:** 780 строк facade с множеством proxy-методов без логики.

**План:**
1. Заменить proxy-методы на extension functions
2. Группировать методы по доменам
3. Цель: GrpcClient < 400 LOC

**Оценка:** 1 сессия

---

## Фаза 5: AI Chats domain layer (v1.1.3.38)

**Проблема:** HermesGrpc (1880 LOC) + OwlGrpc (1145 LOC) = 3025 строк AI-кода в gRPC слое.

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

| Метрика | Текущая | Цель (v1.1.3.38) |
|---------|---------|-------------------|
| NewChatActivity | 1473 | < 400 |
| GrpcClient | 780 | < 400 |
| AI code in gRPC | 3025 | < 1000 (transport only) |
| Unit tests | 0 | > 20 |
| Error handling | partial | unified |
