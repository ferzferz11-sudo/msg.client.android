# План рефакторинга RealGrpcClient — v1.1.3.20

## Текущее состояние
- RealGrpcClient: 4082 строки, 471 метод, 114 inner classes
- GrpcClient: 779 строк, проксирует ~40 методов
- UI вызывает только GrpcClient (не RealGrpcClient напрямую)

## Цель
Разделить RealGrpcClient на модули, сохранив обратную совместимость.

## План

### Шаг 1: Выделить GrpcConnectionManager
**Файл:** `data/grpc/GrpcConnectionManager.kt`
**Ответственность:** управление каналом, подключение, переподключение
**Методы:**
- `connect()`, `disconnect()`, `reconnect()`
- `scheduleReconnect()`, `resetReconnectBackoff()`
- `getChannel()`, `isConnectedTo()`
**StateFlow:** `connectionStatus`, `currentServerAddress`, `currentServerPort`

### Шаг 2: Выделить GrpcChatClient
**Файл:** `data/grpc/GrpcChatClient.kt`
**Ответственность:** чат-стрим, отправка сообщений, получение чатов
**Методы:**
- `startChat()`, `sendMessage()`, `addLocalMessage()`
- `getChats()`, `pollChats()`
- `pinChat()`, `unpinChat()`, `searchChats()`
- `archiveChat()`, `unarchiveChat()`
- `markRead()`, `deleteChat()`
**StateFlow:** `messages`, `users`, `typingUsers`, `chatDeletedEvent`, `newMessageEvent`

### Шаг 3: Выделить GrpcAuthClient
**Файл:** `data/grpc/GrpcAuthClient.kt`
**Ответственность:** аутентификация, JWT токены
**Методы:**
- `loginV2()`, `loginV1()`, `refreshToken()`
- `register()`, `signOut()`
- `getBearerToken()`, `getAccessToken()`
**StateFlow:** `authStatus`, `isSuperAdmin`

### Шаг 4: Выделить GrpcProfileClient
**Файл:** `data/grpc/GrpcProfileClient.kt`
**Ответственность:** профиль пользователя, настройки
**Методы:**
- `getProfile()`, `updateProfile()`
- `getUserSettings()`, `updateUserSettings()`
- `fetchServerInfo()`
**StateFlow:** `serviceProfileVersion`, `serviceChatVersion`, `serviceAuthVersion`

### Шаг 5: Выделить GrpcCallClient
**Файл:** `data/grpc/GrpcCallClient.kt`
**Ответственность:** видеозвонки, сигнализация
**Методы:**
- `startCallSession()`, `sendCallSignal()`
**StateFlow:** `callSignals`

### Шаг 6: Выделить GrpcTypingClient
**Файл:** `data/grpc/GrpcTypingClient.kt`
**Ответственность:** индикатор набора текста
**Методы:**
- `sendTypingSignal()`
**StateFlow:** `typingUsers`

### Шаг 7: Обновить RealGrpcClient
RealGrpcClient становится тонкой обёрткой (~200 строк):
- Содержит экземпляры всех модулей
- Проксирует StateFlow/SharedFlow из модулей
- Инициализация и lifecycle

### Шаг 8: Обновить GrpcClient
GrpcClient остаётся без изменений — он уже проксирует в RealGrpcClient

## Приоритет реализации
1. GrpcConnectionManager (самый независимый)
2. GrpcChatClient (основная функциональность)
3. GrpcAuthClient
4. GrpcProfileClient
5. GrpcCallClient + GrpcTypingClient
6. Рефакторинг RealGrpcClient

## Риски
- Нельзя сломать существующий функционал
- Все изменения должны быть обратно совместимы
- Тестировать на dev сервере после каждого шага
