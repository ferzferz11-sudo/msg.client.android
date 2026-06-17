# План рефакторинга RealGrpcClient — v1.1.3.28+

## СТАТУС: ✅ ЗАВЕРШЁН (все 12 шагов выполнены)

---

## Выполнено

### ✅ Шаг 1: GrpcConnectionManager (сессия 23)
### ✅ Шаг 2: GrpcChatListClient (сессия 32)
### ✅ Шаг 3: GrpcAuthClient (сессия 23)
### ✅ Шаг 4: GrpcProfileClient (сессия 32)
### ✅ Шаг 5: GrpcCallClient (сессия 23)
### ✅ Шаг 6: GrpcTypingClient (сессия 23)
### ✅ Шаг 7: GrpcDraftClient + GrpcFavoritesClient (сессия 32)
### ✅ Шаг 8: GrpcUnaryCallHelper (сессия 32)
### ✅ Шаг 9: GrpcMarshallers (сессия 33)
### ✅ Шаг 10: GrpcMessageClient (сессия 34)
### ✅ Шаг 11: GrpcServerDiscoveryClient (сессия 34)
### ✅ Шаг 12: Финальный рефакторинг RealGrpcClient (сессия 34)

---

## Архитектура после полного рефакторинга
```
GrpcClient (facade, 779 строк) — публичный API
    ↓
RealGrpcClient (orchestrator, 874 строк) — chat stream + coordination
    ├── GrpcConnectionManager (167) — channel lifecycle
    ├── GrpcAuthClient (232) — JWT auth
    ├── GrpcTypingClient (87) — typing stream
    ├── GrpcCallClient (125) — calls
    ├── GrpcChatListClient (638) — chat list, pin/search/archive, management
    ├── GrpcProfileClient (506) — profile, avatar, contacts, themes, devices
    ├── GrpcDraftClient (86) — drafts
    ├── GrpcFavoritesClient (120) — favorites
    ├── GrpcMessageClient (341) — messages, history, reactions, mark read
    ├── GrpcServerDiscoveryClient (145) — server discovery, proto parsing
    └── GrpcMarshallers (1394) — 111 marshaller classes (separate file)
```

## Статистика

| Метрика | До | После | Изменение |
|---------|-----|-------|-----------|
| RealGrpcClient | 3810 | 874 | -77% |
| Всего модулей | 0 | 12 | +12 |
| Новых файлов | 0 | 13 | +13 |
| Вынесено строк | 0 | ~4700 | - |

---

## Следующие приоритеты (после рефакторинга gRPC)

Рефакторинг gRPC завершён. Следующие приоритеты:
1. **FAB [+] восстановление** — ✅ v1.1.3.30
2. **Favorites fix** — ✅ v1.1.3.30 (убрать из секций, добавить в шторку, исправить FavoritesActivity)
3. **ProfileService v2** — проверить работу на dev сервере
4. **Read receipts** — ✅ v1.1.3.31 (readReceiptEvent SharedFlow → ChatListViewModel)
5. **ChatListActivity разбиение** — ✅ v1.1.3.31 (ToolbarManager, TabManager, ActionMode, Search)
- NewChatActivity рефакторинг — отложено по решению ферзя
