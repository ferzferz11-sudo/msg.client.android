# Промпт для новой сессии — Android v1.1.3.31+

**Дата:** 2026-06-17
**Версия:** v1.1.3.30
**Ветка:** feat/1.1.3.x
**Тег:** v1.1.3.30

---

## СТАТУС: v1.1.3.30 — завершена, тег выпущен

### Что сделано (CHANGELOG):
- ✅ FAB [+] — ActionBottomSheet + SearchableListBottomSheet (Add Contact, Start Chat, Secret Chat, Conference)
- ✅ Favorites — убран из секций, добавлен в шторку профиля (звёздочка)
- ✅ Favorites — навигация через navigateToChat() → NewChatActivity (ROOM_ID=favorites_$username)
- ✅ Удалён FavoritesActivity.kt (не нужен, всё через NewChatActivity)
- ✅ Добавлены строки: no_favorites_yet, contacts_added (en + ru)

---

## ПРИОРИТЕТЫ СЛЕДУЮЩЕЙ СЕССИИ (v1.1.3.31)

### 🔴 Высокий приоритет
1. ProfileService v2 — проверить работу на dev сервере
2. Read receipts — MarkAsRead с broadcast

### 🟡 Средний приоритет
3. ChatListActivity разбиение (ToolbarManager, TabManager)

### 🟢 Отложено
- NewChatActivity рефакторинг — отложено по решению ферзя
- Qdrant + CLIP (production RAG)
- Shared element transitions
- Infinite scroll + pagination

---

## АРХИТЕКТУРА

### gRPC Client (v1.1.3.30)
```
GrpcClient (facade, 779 LOC)
    ↓
RealGrpcClient (orchestrator, 874 LOC)
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
    └── GrpcMarshallers (1394) — all marshaller classes (separate file)
```

### Серверы
| | Dev | Prod |
|--|-----|------|
| Порт gRPC | 50052 | 50051 |
| Порт HTTP | 8083 | 8082 |
| Версия | v1.1.3.0 | v1.1.3.0 |

---

## ПРАВИЛА

1. ⚠️ НЕ компилировать Android на сервере
2. НЕ деплоить на prod без прямого указания ферзя
3. Коммитить и пушить после каждого значимого изменения
4. userId (UUID) — всегда как ключ, НЕ username
5. i18n: все новые строки ОДНОВРЕМЕННО в values/strings.xml + values-ru/strings.xml
6. НЕ инициализировать getString() в полях класса Activity
7. Kotlin 2.3.21: cont.resume(value, onCancellation = {})
8. НЕТ forceReconnect — один connect при старте, reconnect только если FAILED
9. Favorites — НЕ секция в списке, а отдельный чат (type="favorites"), открывается из шторки профиля

---

## КОМАНДЫ

```bash
# Сервер
cd /root/msg && export PATH=$PATH:/usr/local/go/bin:~/go/bin
go build -o /tmp/lavender-server-dev . && systemctl stop lavender-server-dev && cp /tmp/lavender-server-dev /root/LavenderMessenger/run/lavender-server-dev && systemctl start lavender-server-dev

# Android (НЕ компилировать на сервере!)
cd /root/msg.client.android
```

---

## ДОКУМЕНТАЦИЯ

| Файл | Назначение |
|------|-----------|
| `doc/INDEX.md` | Индекс всей документации |
| `doc/TASKS.md` | Таск-трекер |
| `doc/PROMPT_ANDROID.md` | Этот файл |
| `doc/SESSION_NOTES.md` | История сессий |
| `doc/CHANGELOG.md` | История изменений |
| `doc/PATTERNS.md` | Паттерны и анти-patterns |
| `doc/ChatListActivity_v1_REFERENCE.kt` | Копия v1 Activity (2802 строки) |
