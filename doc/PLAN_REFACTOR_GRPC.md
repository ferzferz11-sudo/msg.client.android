# План рефакторинга RealGrpcClient — v1.1.3.20+

## Текущее состояние (после сессии 23)
- ✅ RealGrpcClient: 4081 → 3739 строк (-342)
- ✅ 4 модуля извлечены и работают
- ⏳ Осталось ~3700 строк в RealGrpcClient (цель: ~200)

## Выполнено

### ✅ Шаг 1: Выделить GrpcConnectionManager (сессия 23)
**Файл:** `data/grpc/GrpcConnectionManager.kt` (167 строк)
- `connect()`, `disconnect()`, `reconnect()`
- `scheduleReconnect()`, `resetReconnectBackoff()`
- `getChannel()`, `isConnectedTo()`
- StateFlow: `connectionStatus`, `currentServerAddress`, `currentServerPort`

### ✅ Шаг 5: Выделить GrpcCallClient (сессия 23)
**Файл:** `data/grpc/GrpcCallClient.kt` (124 строки)
- `startCallSession()`, `sendCallSignal()`
- StateFlow: `callSignals`

### ✅ Шаг 6: Выделить GrpcTypingClient (сессия 23)
**Файл:** `data/grpc/GrpcTypingClient.kt` (87 строк)
- `startTypingStream()`, `sendTypingSignal()`
- StateFlow: `typingUsers`

### ✅ Шаг 3: Выделить GrpcAuthClient (сессия 23)
**Файл:** `data/grpc/GrpcAuthClient.kt` (232 строки)
- `signInV2()`, `signUpV2()`, `refreshToken()`, `signOut()`, `revokeDevice()`
- StateFlow: `authStatus`

---

## Осталось сделать

### ⏳ Шаг 2: Выделить GrpcChatClient (СЛЕДУЮЩИЙ)
**Файл:** `data/grpc/GrpcChatClient.kt` (~2000 строк из RealGrpcClient)
**Ответственность:** чат-стрим, отправка сообщений, получение чатов
**Методы:**
- `startChat()`, `sendMessage()`, `addLocalMessage()`
- `getChats()`, `pollChats()`
- `pinChat()`, `unpinChat()`, `searchChats()`
- `archiveChat()`, `unarchiveChat()`
- `markRead()`, `deleteChat()`
**StateFlow:** `messages`, `users`, `typingUsers`, `chatDeletedEvent`, `newMessageEvent`

**Сложность:** ВЫСОКАЯ — это самый большой и связанный блок кода
**Риск:** Средний — нужно аккуратно вынести не сломав стримы

### ⏳ Шаг 4: Выделить GrpcProfileClient
**Файл:** `data/grpc/GrpcProfileClient.kt`
**Ответственность:** профиль пользователя, настройки, server info
**Методы:**
- `getProfile()`, `updateProfile()`
- `getUserSettings()`, `updateUserSettings()`
- `fetchServerInfo()`
**StateFlow:** `serviceProfileVersion`, `serviceChatVersion`, `serviceAuthVersion`
**Примечание:** ProfileClient (object) уже существует — нужно интегрировать

### ⏳ Шаг 7: Рефакторинг RealGrpcClient
RealGrpcClient становится тонкой обёрткой (~200 строк):
- Содержит экземпляры всех модулей
- Проксирует StateFlow/SharedFlow из модулей
- Инициализация и lifecycle
- GrpcClient facade остаётся без изменений

---

## Приоритет реализации
1. **GrpcChatClient** (самый большой оставшийся кусок, ~2000 строк)
2. **GrpcProfileClient**, затем рефакторинг RealGrpcClient

## Риски
- Нельзя сломать существующий функционал
- Все изменения должны быть обратно совместимы
- Тестировать на dev сервере после каждого шага
