# Lavender Messenger (Android) — Задачи

**Версия:** v1.1.3.19
**Обновлено:** 2026-06-16 (сессия 21)
**Ветка:** feat/1.1.3.x

---

## 🚧 v1.1.3.19 — Unread Badges (Сессия 21)

### В процессе
- [x] **Badge colors by theme** — primary color background, adaptive text color
- [x] **Mark as read on click** — clear badge + server MarkAsRead on chat open
- [x] **Real-time update** — newMessageEvent SharedFlow for background messages
- [ ] **Test on dev** — verify unread badges work on v2 server

### Коммиты
- `e029aa7` — feat: unread badges — theme colors, mark-as-read on click, real-time update

---

## ✅ v1.1.3.18 — Fix HTTP /info для dev (Сессия 21)

### Исправлено
- ✅ **HTTP /info warning на dev** — для порта 50052 HTTP запрос не делается вообще, сразу v2
- ✅ **5с таймаут на dev** — убран, v2 определяется мгновенно

### Коммиты
- `f45bdd3` — fix: skip HTTP /info for dev server (port 50052)

---

## ✅ v1.1.3.18 — Стабильность соединения (Сессия 20)

### Исправлено
- ✅ **HTTP /info недоступен на dev** — fallback по gRPC порту (50052→v2, 50051→v1)
- ✅ **Keepalive failures** — увеличены таймауты (30s/10s), добавлен idleTimeout 25min
- ✅ **Множественные reconnect** — подавлен reconnect при shutdownNow
- ✅ **Hardcoded порт в reconnect** — сохранение currentServerPort
- ✅ **Poll interval** — увеличен 5s → 30s

### Коммиты
- `52065b1` — fix: remove HTTP health check, use optimistic READY
- `85a99de` — fix: optimize connection stability
- `09c1acd` — fix: correct idleTimeout, suppress shutdownNow reconnect
- `ff90513` — fix: gRPC port heuristic for version detection
- `8047309` — chore: cleanup debug logs

---

## ✅ v1.1.3.18 — Баг загрузки чатов (Сессия 19)

### Исправлено
- ✅ **Баг: чаты не загружаются** — убран двойной loadChats, cache-first, добавлен reconnect

### Коммиты
- `3f808bf` — fix: resolve chat loading race condition

---

## ✅ v1.1.3.17 — FAB AI (Сессия 17)
- ✅ AIBottomSheet, создание AI чатов, настройки

---

## ✅ v1.1.3.16 — Selection Mode, Search, Pin Message, CacheUtils (Сессии 13-16)
- ✅ Selection Mode, поиск, Pin Message, CacheUtils, ServersActivity

---

## ✅ v1.1.3.15 — Стабильная v1 (prod)

---

## 📋 Активные задачи (Сессия 21)

### Высокий приоритет
- [x] **Unread badges** — theme colors, mark-as-read on click, real-time update (v1.1.3.19)
- [ ] **Push notifications** — FCM интеграция

### Средний приоритет
- [ ] **ProfileService v2** — проверить работу на dev сервере
- [ ] **Read receipts** — MarkAsRead

### Отложено
- [ ] Qdrant + CLIP (production RAG)
- [ ] Shared element transitions
- [ ] Infinite scroll + pagination

---

## 🔑 Ключевые решения

| Решение | Обоснование |
|---------|-------------|
| v1/v2 разделение | Новые файлы в ui/chatlist/, v1 без изменений |
| Long press = режим выбора | ActionMode toolbar с действиями Pin/Delete/Archive |
| Pin Chat в toolbar выбора | НЕ в обычном toolbar — только в режиме выбора |
| Pin Message — selection toolbar | Кнопка pin/unpin в selection toolbar (v1-style) |
| fetchServerInfo strategy | Dev (50052): skip HTTP, assume v2. Prod (50051): try HTTP /info, fallback v1 |
| Optimistic READY | gRPC channel подключается лениво, health check не нужен |
| Unread badge by theme | Badge bg = primary color, text = adaptive (white/black) |
| newMessageEvent | SharedFlow<Pair<roomId, messageId>> for real-time unread increment |
| onCancellation = {} | Обязательно в Kotlin 2.3.21 для cont.resume() |
| ChatListActivityV2 без фрагмента | RecyclerView+SwipeRefresh напрямую в Activity |
| CacheUtils | Единый утилит очистки кэша |
| HermesSettings → OwlSettingsActivity | Переиспользование с isHermes=true |
| AIChatInfo минимальная | Только id, name, type |
| Keepalive 30s/10s | Для мобильных сетей, меньше разрывов |
| Poll 30s | Уменьшение нагрузки на сервер |

---

## 📁 Ключевые файлы

| Файл | Назначение |
|------|-----------|
| `ui/chatlist/ChatListActivityV2.kt` | v2 Activity: tabs, toolbar, FABs, navigation, selection mode, search, AI bottom sheet |
| `ui/chatlist/ChatAdapterV2.kt` | v2 адаптер с секциями + selection state |
| `ui/chatlist/ChatListViewModelV2.kt` | v2 ViewModel: loadChats, pinChat, setTabFilter, getChats |
| `ui/chatlist/ChatListSections.kt` | Section enum + SectionItem |
| `ui/adapter/MessageAdapter.kt` | Адаптер сообщений + pinned badge |
| `ui/widget/AIBottomSheet.kt` | Шторка выбора AI чата (OWL/Hermes) |
| `data/cache/CacheUtils.kt` | Единый утилит очистки кэша |
| `data/grpc/GrpcClient.kt` | Facade (pinChat, pinMessage, searchChats, etc.) |
| `data/grpc/RealGrpcClient.kt` | Реализация gRPC: connect, reconnect, keepalive, version detection |
| `data/grpc/ProfileClient.kt` | ProfileService v2 client + version detection |
| `data/models/Message.kt` | Message (isPinned), ChatInfo (isPinned, isArchived, pinnedAt), AIChatInfo |
