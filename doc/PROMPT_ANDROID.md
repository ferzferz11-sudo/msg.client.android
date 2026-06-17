# Промпт для новой сессии — Android v1.1.3.31+

**Дата:** 2026-06-17
**Версия:** 1.1.3.30
**Ветка:** feat/1.1.3.x

---

## СТАТУС: v1.1.3.30 — завершена

### Что сделано в этой сессии:
- ✅ FAB [+] — восстановлен как в v1: ActionBottomSheet + SearchableListBottomSheet
- ✅ showAddContactDialog() — поиск пользователей, добавление в контакты, чекбокс "Create direct chat after"
- ✅ showCreateChatDialog() — выбор 1 пользователя → direct, 2+ → group
- ✅ showCreateSecretChatDialog() — одиночный выбор, E2EE ключи
- ✅ showCreateConferenceDialog() — мультивыбор, поле topic
- ✅ Favorites — убран из секций списка (Section.FAVORITES, FavoritesItem)
- ✅ Favorites — добавлен в шторку профиля (actionFavorites)
- ✅ FavoritesActivity — исправлена загрузка (SessionManager, SwipeRefresh, empty state)

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

### gRPC Client (v1.1.3.29)
```
GrpcClient (facade, 779 LOC)
    ↓
RealGrpcClient (orchestrator, 874 LOC)
    ├── GrpcConnectionManager (167)
    ├── GrpcAuthClient (232)
    ├── GrpcTypingClient (87)
    ├── GrpcCallClient (125)
    ├── GrpcChatListClient (638)
    ├── GrpcProfileClient (506)
    ├── GrpcDraftClient (86)
    ├── GrpcFavoritesClient (120)
    ├── GrpcMessageClient (341)
    ├── GrpcServerDiscoveryClient (145)
    └── GrpcMarshallers (1394)
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
