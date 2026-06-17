# Промпт для новой сессии — Android v1.1.3.32+

**Дата:** 2026-06-17
**Версия:** v1.1.3.31
**Ветка:** feat/1.1.3.x
**Тег:** v1.1.3.31

---

## СТАТУС: v1.1.3.31 — завершена, тег выпущен

### Что сделано (CHANGELOG):
- ✅ ChatListActivity разбиение: 1470 → 1085 строк (-26%)
- ✅ ChatListToolbar.kt (232), ChatListTabs.kt (29), ChatListActionMode.kt (120), ChatListSearch.kt (55)
- ✅ Read receipts — MarkAsRead с broadcast (readReceiptEvent SharedFlow → ChatListViewModel)
- ✅ ProfileService v2 — ferz подтвердил работу на dev сервере
- ✅ FavoritesActivity убрана, навигация через navigateToChat с favorites_ prefix
- ✅ Документация оптимизирована

---

## ПРИОРИТЕТЫ СЛЕДУЮЩЕЙ СЕССИИ (v1.1.3.32)

### 🔴 Главный приоритет
1. **Стабильность ChatList** — тестировать на реальных чатах, находить и фиксить баги
2. **Read receipts** — проверить в реальном общении (unread count должен обновляться)

### 🟡 Средний приоритет
3. ChatListActivity дальнейшее разбиение (FABs, Auth, Navigation)

### 🟢 Отложено
- NewChatActivity рефакторинг — отложено по решению ферзя
- Qdrant + CLIP (production RAG)
- Shared element transitions
- Infinite scroll + pagination

---

## АРХИТЕКТУРА

### ChatListActivity (v1.1.3.31)
```
ChatListActivity.kt (1085) — основной Activity
├── ChatListToolbar.kt (232) — toolbar + settings sheets
├── ChatListTabs.kt (29) — tabs
├── ChatListActionMode.kt (120) — selection mode
├── ChatListSearch.kt (55) — search
├── ChatListViewModel.kt (268) — ViewModel
├── ChatListSections.kt (20) — sections
└── UpdateCoordinator.kt (245) — updates
```

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
| Версия | v1.2.0.2 | v1.1.3.0 |

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
10. При выносе кода из ChatListActivity — использовать `internal` для полей/методов, прокси-методы в Activity

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
| `doc/SESSION_NOTES.md` | Заметки сессий (35-38) |
| `doc/SESSION_NOTES_ARCHIVE.md` | Архив сессий (23-34) |
| `doc/PATTERNS.md` | Паттерны разработки |
| `doc/REMOTE_AGENT.md` | Remote Agent интеграция |
| `../CHANGELOG.md` | История изменений |
