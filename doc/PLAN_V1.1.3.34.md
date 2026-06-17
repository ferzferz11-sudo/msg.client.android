# План реализации v1.1.3.34 — Фаза 3: Unit-тесты для gRPC клиента

**Дата:** 2026-06-17 | **Версия:** v1.1.3.34 | **Статус:** В работе

---

## Контекст

v1.1.3.33 завершена:
- Фаза 1: NewChatActivity 1473→754 LOC (-49%), 6 делегатов
- Фаза 2: ErrorHandler унификация — все gRPC модули используют handle()

Следующий шаг по приоритетам (CODE_AUDIT.md, PROMPT_ANDROID.md):
- **Фаза 3: Unit-тесты для gRPC клиента** — сейчас 0 тестов для gRPC слоя

## Проблема

gRPC клиент (GrpcClient + RealGrpcClient + 12 модулей) — ~4000 строк кода без unit-тестов.
Любое изменение в gRPC слое требует ручного тестирования на устройстве.

## Цель

42 unit-теста для gRPC модулей. Покрытие > 70%. Изолированные тесты без реального сервера.

---

## Архитектура тестов

### Инструменты
- **JUnit 4** — уже используется (ErrorHandlerTest, ChatAdapterTest)
- **MockK 1.13.8** — мокирование gRPC stubs, ManagedChannel
- **Turbine 1.0.0** — тестирование StateFlow/SharedFlow
- **kotlinx-coroutines-test 1.7.3** — Dispatchers.setMain, runTest

### Структура
```
app/src/test/java/lavender/client/android/data/grpc/
├── GrpcAuthClientTest.kt       (10 тестов)
├── GrpcChatListClientTest.kt   (8 тестов)
├── GrpcMessageClientTest.kt    (8 тестов)
├── GrpcConnectionManagerTest.kt (6 тестов)
├── GrpcClientFacadeTest.kt     (6 тестов)
└── GrpcUnaryCallHelperTest.kt  (4 теста)
```

### Тестовые утилиты
```
app/src/test/java/lavender/client/android/data/grpc/testutil/
├── TestChannelFactory.kt       — mock ManagedChannel + stub
├── TestDatabaseFactory.kt      — in-memory Room database
└── FlowTestExtensions.kt       — StateFlow/SharedFlow assertion helpers
```

---

## Детальные тесты по модулям

### 1. GrpcAuthClientTest (10 тестов)

Модуль: `GrpcAuthClient` (232 LOC)
Зависимости: `getChannel()`, `connectionStatus`, `authStatus`, `setAuthFailure()`

| # | Тест | Что проверяем | Мокирование |
|---|------|--------------|-------------|
| 1 | signInV2_Success | success=true, token не пустой, user заполнен | channel.newCall → mock stub с success response |
| 2 | signInV2_WrongPassword | success=false, сообщение об ошибке | stub → success=false |
| 3 | signInV2_UserNotFound | success=false | stub → success=false, "user not found" |
| 4 | signInV2_EmptyUsername | callback(null, error) | проверка валидации на уровне сервера |
| 5 | signInV2_NullChannel | callback(null, "Not connected") | getChannel → null |
| 6 | signUpV2_Success | success=true, token не пустой | stub → success response |
| 7 | signUpV2_DuplicateUsername | success=false | stub → success=false |
| 8 | refreshToken_Success | новая пара токенов | stub → RefreshTokenResponse |
| 9 | signOut_Success | success=true | stub → SimpleAuthResponse |
| 10 | revokeDevice_Success | success=true | stub → SimpleAuthResponse |

### 2. GrpcChatListClientTest (8 тестов)

Модуль: `GrpcChatListClient` (641 LOC)
Зависимости: `getChannel()`, `getUserId()`, `getUsername()`, `chatDeletedEvent`, `allUsers`, `serverTime`, `scope`

| # | Тест | Что проверяем | Мокирование |
|---|------|--------------|-------------|
| 1 | getChats_Success | callback с списком ChatInfo | stub → GetChatsResponse с чатами |
| 2 | getChats_EmptyList | callback с emptyList | stub → пустой список |
| 3 | getChats_NullChannel | callback(emptyList) без crash | getChannel → null |
| 4 | pinChat_V2Supported | вызывается pin | ProfileClient mock → v2 |
| 5 | pinChat_V1Fallback | возвращает false | ProfileClient mock → v1 |
| 6 | searchChats_V2Supported | возвращает результаты | ProfileClient mock → v2 |
| 7 | searchChats_V1Fallback | возвращает emptyList | ProfileClient mock → v1 |
| 8 | deleteChat_Success | callback с success | stub → DeleteChatResponse |

### 3. GrpcMessageClientTest (8 тестов)

Модуль: `GrpcMessageClient` (344 LOC)
Зависимости: `getChannel()`, `getUserId()`, `getUsername()`, `messages`, `deletedMessageHashes`, `pendingReads`, `scope`, `appContext`, `onReadReceipt`

| # | Тест | Что проверяем | Мокирование |
|---|------|--------------|-------------|
| 1 | sendMessage_Valid | onNext вызван с proto | mock requestObserver |
| 2 | sendMessage_NullObserver | нет crash, лог ошибки | requestObserver = null |
| 3 | addLocalMessage | messages StateFlow обновлён | mock database |
| 4 | loadHistory_Success | messages заполнен из ответа | stub → GetHistoryResponse |
| 5 | loadHistory_CacheFirst | кэш загружается первым | in-memory Room |
| 6 | markRead_Ready | markRead вызывается | connectionStatus = READY |
| 7 | markRead_NotReady | pendingReads добавляется | connectionStatus = CONNECTING |
| 8 | resendPendingReads | pending reads отправляются | 2 pending → 2 markRead calls |

### 4. GrpcConnectionManagerTest (6 тестов)

Модуль: `GrpcConnectionManager` (167 LOC)
Зависимости: `scope`, `connectionStatus`, `onFetchServerInfo`, `onAutoResumeChat`

| # | Тест | Что проверяем | Мокирование |
|---|------|--------------|-------------|
| 1 | connect_Success | connectionStatus = READY | in-process channel |
| 2 | connect_AlreadyConnected | без переподключения | тот же адрес + READY |
| 3 | disconnect | connectionStatus = DISCONNECTED | — |
| 4 | reconnect | connect вызывается с forceReconnect | — |
| 5 | isConnectedTo_True | совпадение адреса + READY | — |
| 6 | isConnectedTo_False | другой адрес → false | — |

### 5. GrpcClientFacadeTest (6 тестов)

Модуль: `GrpcClient` (780 LOC) — facade
Зависимости: `RealGrpcClient` (internal)

| # | Тест | Что проверяем | Мокирование |
|---|------|--------------|-------------|
| 1 | connectionState_MapsReady | READY → true | RealGrpcClient.connectionStatus |
| 2 | connectionState_MapsDisconnected | DISCONNECTED → false | RealGrpcClient.connectionStatus |
| 3 | messages_Probed | проброс из RealGrpcClient | RealGrpcClient.messages |
| 4 | error_Probed | проброс из RealGrpcClient | RealGrpcClient.error |
| 5 | isChatV2Supported_Delegates | делегирует в ProfileClient | ProfileClient mock |
| 6 | scope_Created | scope инициализирован | — |

### 6. GrpcUnaryCallHelperTest (4 теста)

Модуль: `GrpcUnaryCallHelper.kt` (111 LOC) — top-level функции
Зависимости: `getChannel()`

| # | Тест | Что проверяем | Мокирование |
|---|------|--------------|-------------|
| 1 | unaryCall_Success | возвращает response | in-process server |
| 2 | unaryCall_NullChannel | возвращает null | getChannel → null |
| 3 | unaryCall_ServerError | возвращает null | server → error status |
| 4 | unaryCallWithClass_Success | class-based variant | in-process server |

---

## Шаги реализации

### Шаг 1: Зависимости
Файл: `app/build.gradle.kts`

Добавить:
```
testImplementation("io.mockk:mockk:1.13.8")
testImplementation("app.cash.turbine:turbine:1.0.0")
testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
```

### Шаг 2: Тестовые утилиты
- `TestChannelFactory.kt` — mock ManagedChannel + MethodDescriptor
- `TestDatabaseFactory.kt` — in-memory Room database
- `FlowTestExtensions.kt` — StateFlow/SharedFlow helpers

### Шаг 3-8: Тесты по модулям
Порядок: AuthClient → UnaryCallHelper → ChatListClient → MessageClient → ConnectionManager → ClientFacade

### Шаг 9: Финализация
- Исправление ошибок
- Проверка всех тестов локально
- Обновление документации

---

## Критерии приёмки

- [ ] 42 теста проходят (`./gradlew testDebugUnitTest`)
- [ ] Покрытие gRPC модулей > 70%
- [ ] Тесты изолированы (не зависят от порядка)
- [ ] Тесты не требуют реального сервера
- [ ] CI-совместимость

---

## Риски и митигация

| Риск | Митигация |
|------|-----------|
| gRPC channel не мокается напрямую | Использовать in-process канал или mock stub |
| Room database требует Android context | Использовать in-memory database в androidTest |
| StateFlow тестирование сложное | Использовать Turbine для flow assertions |
| MockK + Kotlin 2.3.21 совместимость | Проверить версию mockk перед добавлением |
