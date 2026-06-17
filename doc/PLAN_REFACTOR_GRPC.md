# План рефакторинга RealGrpcClient — v1.1.3.26+

## Текущее состояние (после сессии 32)
- ✅ RealGrpcClient: 3810 → 1615 строк (-57%)
- ✅ 8 модулей выделены и работают
- ✅ GrpcClient facade без изменений

## Выполнено

### ✅ Шаг 1: GrpcConnectionManager (сессия 23)
### ✅ Шаг 2: GrpcChatListClient (сессия 32)
### ✅ Шаг 3: GrpcAuthClient (сессия 23)
### ✅ Шаг 4: GrpcProfileClient (сессия 32)
### ✅ Шаг 5: GrpcCallClient (сессия 23)
### ✅ Шаг 6: GrpcTypingClient (сессия 23)
### ✅ Шаг 7: GrpcDraftClient + GrpcFavoritesClient (сессия 32)
### ✅ Шаг 8: GrpcUnaryCallHelper (сессия 32)
### ✅ Шаг 9: GrpcMarshallers (предыдущая сессия)

---

## Осталось сделать

### ⏳ Шаг 10: Выделить GrpcMessageClient (СЛЕДУЮЩИЙ)
**Файл:** `data/grpc/GrpcMessageClient.kt` (~800 строк из RealGrpcClient)
**Ответственность:** отправка сообщений, история, реакции, редактирование, удаление
**Методы:**
- `sendMessage()`, `addLocalMessage()`
- `loadHistory()`, `resendPendingMessages()`
- `editMessage()`, `deleteMessage()`
- `setReaction()`, `markRead()`, `resendPendingReads()`
**StateFlow:** `messages`, `newMessageEvent`

**Сложность:** ВЫСОКАЯ — тесно связан с chat stream и message cache
**Риск:** Средний — нужно аккуратно вынести не сломав стримы

### ⏳ Шаг 11: Выделить GrpcServerDiscoveryClient
**Файл:** `data/grpc/GrpcServerDiscoveryClient.kt` (~150 строк)
**Ответственность:** обнаружение серверов, raw protobuf parsing
**Методы:**
- `fetchServersList()`, `fetchServersFromHost()`
- `parseServerList()`, `parseServerInfo()`
- `readVarint()`, `skipField()`

### ⏳ Шаг 12: Рефакторинг RealGrpcClient в тонкий orchestrator (~200 строк)
RealGrpcClient содержит только:
- Ссылку на GrpcClient (facade)
- Инициализацию модулей
- Проксирование StateFlow/SharedFlow из модулей

---

## Архитектура после полного рефакторинга
```
GrpcClient (facade, 779 строк) — публичный API
    ↓
RealGrpcClient (orchestrator, ~200 строк) — координация модулей
    ├── GrpcConnectionManager (170)
    ├── GrpcAuthClient (232)
    ├── GrpcTypingClient (87)
    ├── GrpcCallClient (125)
    ├── GrpcChatListClient (639)
    ├── GrpcProfileClient (506)
    ├── GrpcDraftClient (86)
    ├── GrpcFavoritesClient (121)
    ├── GrpcMessageClient (~800) — следующий
    ├── GrpcServerDiscoveryClient (~150)
    └── GrpcMarshallers (1394) — отдельный файл
```

## Приоритет реализации
1. **GrpcMessageClient** (самый большой оставшийся кусок)
2. **GrpcServerDiscoveryClient** (легко выделяется)
3. **Финальный рефакторинг RealGrpcClient**

## Риски
- Нельзя сломать существующий функционал
- Все изменения должны быть обратно совместимы
- Тестировать на dev сервере после каждого шага
