# Lava Messenger — Android Code Audit

**Дата:** 2026-06-17
**Версия:** v1.1.3.25
**Аудитор:** OWL (автоматический аудит)

---

## 1. Общая статистика

| Метрика | Значение |
|---------|----------|
| Всего Kotlin файлов | 139 |
| Общий LOC | 38,492 |
| Топ-5 файлов по размеру | RealGrpcClient (3810), HermesGrpc (1880), MessengerProto (1791), NewChatActivity (1473), OwlGrpc (1145) |
| TODO/FIXME/HACK | 1 (avatar загрузка) |
| Пустых методов | 0 |

---

## 2. Топ-10 файлов по размеру

| # | Файл | LOC | Статус |
|---|------|-----|--------|
| 1 | RealGrpcClient.kt | 3810 | 🔴 Критично — требует немедленного рефакторинга |
| 2 | HermesGrpc.kt | 1880 | 🟡 Большой, но изолированный |
| 3 | MessengerProto.kt | 1791 | 🟢 Proto-код, генерируемый |
| 4 | NewChatActivity.kt | 1473 | 🟡 Требует рефакторинга (можно разбить) |
| 5 | OwlGrpc.kt | 1145 | 🟢 Изолированный AI-модуль |
| 6 | ChatListActivity.kt | 1104 | 🟡 Улучшить после UpdateCoordinator |
| 7 | MessageAdapter.kt | 870 | 🟢 Нормальный для адаптера |
| 8 | GrpcClient.kt | 779 | 🟢 Facade, стабильный |
| 9 | ProfileActivity.kt | 719 | 🟡 Можно разбить на фрагменты |
| 10 | ShareReceiverActivity.kt | 641 | 🟢 Нормальный |

---

## 3. Архитектурный аудит

### 3.1 Сильные стороны ✅

1. **Модульная структура gRPC** — 4 модуля уже выделены (ConnectionManager, Auth, Call, Typing)
2. **Facade паттерн** — GrpcClient предоставляет чистый API для UI
3. **MVVM для ChatList** — ViewModel выделен из Activity
4. **UpdateCoordinator** — чистый пример выноса логики из Activity
5. **BearerTokenInterceptor** — правильная автоматическая подстановка JWT
6. **CacheUtils** — единый утилит для кэша
7. **ProfileClient** — отдельный object для ProfileService v2
8. **E2EE** — шифрование выделено в E2EEManager
9. **Theme system** — ThemeApplier + ThemeUtils — чистая архитектура тем
10. **i18n** — все строки в resources, дублирование en+ru

### 3.2 Слабые стороны 🔴

#### КРИТИЧНО

1. **RealGrpcClient — 3810 строк, ~445 методов**
   - Это God Object анти-паттерн
   - Содержит: chat stream, messages, history, chats, favorites, drafts, reactions, profile, avatar, contacts, themes, AI chats, server discovery, proto parsers
   - Все в одном `object` (singleton)
   - 24 StateFlow/SharedFlow переменных
   - Плюс ~20 уникальных Marshaller classes в конце файла (ещё ~2000 строк сериализации)

2. **Дублирование сериализации**
   - Каждый gRPC вызов создаёт MethodDescriptor вручную с inline Marshaller
   - 100+ одинаковых паттернов `.setType(UNARY).setFullMethodName(...)`
   - Можно заменить на дженерик-обёртку или кодогенерацию

3. **GrpcClient facade — 779 строк**
   - 40% — это proxy-методы без логики (пустой `realGrpcClient.xxx(...)`)
   - Нужно: auto-generate или extension functions

#### СРЕДНЕ

4. **NewChatActivity — 1473 строки**
   - Создание чатов, поиск пользователей, UI, навигация — всё в одном Activity
   - Решение: выделить ViewModel + Fragment/Composables

5. **ChatListActivity — 1104 строки**
   - Toolbar, tabs, FABs, search, selection mode, settings sheets, update coordinator wiring
   - Решение: выделить ToolbarManager, TabManager

6. **Нет универсального unaryCall helper** (до ChatList v2)
   - Каждый метод дублирует boilerplate: MethodDescriptor, call, start, listener, resume
   - ChatList v2 ввёл `unaryCallChatListV2` — хороший подход, но только для v2
   - Нужно: один универсальный `unaryCall<T, R>()` для всех

7. **Error handling не унифицирован**
   - Где-то try-catch, где-то callback, где-то flow
   - ErrorHandler.kt существует, но используется частично

8. **Тестирование отсутствует**
   - 0 unit-тестов для gRPC клиента
   - 0 instrumented тестов для UI
   - Нет mock-реализаций

#### НИЗКОЕ ПРИОРИТЕТНО

9. **HermesGrpc + OwlGrpc — суммарно 3025 строк**
   - AI-специфичный код доменной логики в gRPC слое
   - Решение: выделить domain layer

10. **Server discovery через raw protobuf parsing** (строки 157-272)
    - parseServerList, parseServerInfo — ручной парсинг байтов
    - Решение: использовать proto-generated классы

---

## 4. Проблемы безопасности

1. **Hardcoded IP** — `13.140.25.249` в `fetchServersList()` (строка 158)
2. **usePlaintext()** — все каналы без TLS (но это осознанно для dev)
3. **SharedPreferences без шифрования** — CredentialStore использует обычный SharedPreferences
4. **Нет certificate pinning** — при переходе на TLS нужно добавить

---

## 5. Производительность

### Проблемы

1. **Lazy channel connecting** — первый вызов может быть медленным (это нормально для gRPC)
2. **Нет batching запросов** — каждый чат запрашивается отдельно
3. **_messages StateFlow** — полная копия списка при каждом обновлении (уже частично решено через DiffUtil)
4. **_connectionStatus** — множество `.value =` вместо update{} — потенциальные race conditions
5. **Background retry loop** — до 50 retry с exponential backoff (30с max) — может разряжать батарею

### Оптимизации

1. **Pagination для чатов** — GetChatsRequest поддерживает limit/offset, но UI не использует
2. **Incremental history loading** — loadHistory загружает все 100 сообщений сразу
3. **Glide avatar caching** — уже есть avatarCache + fullAvatarCache — хорошо

---

## 6. Мёртвый код

1. **`updateMessage(m: Message)`** (строка 2266) — пустая реализация `{}`
2. **`loadUsers()`** (строка 2265) — вызывает `loadAllUsers {}` с пустым callback
3. **`shouldForceReconnect()`** (строка 275) — приватное поле `isAppInBackground` установлено, но метод не вызывается из connectionManager (после рефакторинга v1.1.3.23)

---

## 7. Анти-паттерны

1. **God Object** — RealGrpcClient
2. **Long Method** — startChat (130+ строк), getChats (100+ строк)
3. **Feature Envy** — ChatListActivity слишком много знает о gRPC
4. **Primitive Obsession** — много String констант вместо enum/sealed class
5. **Shotgun Surgery** — изменение одного RPC метода требует правок в 3 местах (RealGrpcClient, GrpcClient, Activity)
6. **Mutable public StateFlow** — `_typingUsers`, `_chatDeletedEvent` — должны быть private mutable / public immutable

---

## 8. Рекомендации по приоритету

### 🔴 Немедленно (эта сессия)
1. Выделить модули из RealGrpcClient (GrpcChatClient, GrpcChatListClient, GrpcProfileClient, etc.)
2. Создать универсальный unaryCall helper
3. Удалить мёртвый код (updateMessage, loadUsers как no-op)

### 🟡 Следующие 2-3 сессии
4. Рефакторинг NewChatActivity (ViewModel + Fragments)
5. Унификация error handling
6. Разбиение ChatListActivity (ToolbarManager, TabManager)

### 🟢 Backlog
7. Тесты для gRPC клиента
8. HermesGrpc/OwlGrpc выделение в domain layer
9. Pagination для чатов
10. Incremental history loading
11. Certificate pinning
12. Encrypted SharedPreferences
