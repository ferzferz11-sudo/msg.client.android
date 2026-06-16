# Lavender Messenger (Android) — Задачи

**Версия:** v1.1.3.20
**Обновлено:** 2026-06-16 (сессия 23)
**Ветка:** feat/1.1.3.x

---

## ✅ v1.1.3.20 — Модуляризация RealGrpcClient (Сессия 23)

### Исправлено
- ✅ **RealGrpcClient разделён на модули**: 4081 → 3739 строк (-342, -8.4%)
- ✅ **GrpcConnectionManager** (167 строк) — connect/reconnect/disconnect/keepalive
- ✅ **GrpcAuthClient** (232 строки) — signInV2/signUpV2/refreshToken/signOut/revokeDevice
- ✅ **GrpcCallClient** (124 строки) — startCallSession/sendCallSignal
- ✅ **GrpcTypingClient** (87 строк) — startTypingStream/sendTypingSignal
- ✅ **ChatListFragmentV2** (144 строки) — удалён мёртвый код
- ✅ **Дублирование currentServerAddress/currentServerPort** — исправлено

### Коммиты
- `TBD` — feat: modularize RealGrpcClient — extract 4 modules, remove dead code

---

## ✅ v1.1.3.19 — Стабильность и оптимизация (Сессия 22)

### Исправлено
- ✅ **JWT auth** — используем getAccessToken() вместо getBearerToken() для ChatStream
- ✅ **Auth failure reconnect loop** — проверка authentication failed → FAILED без retry
- ✅ **Дублированный reconnect** — onClose/getChats больше не вызывают reconnect
- ✅ **DiffUtil** — ChatAdapterV2 использует DiffUtil вместо notifyDataSetChanged

### Новое
- ✅ **Unread badges** — цвета по теме, mark-as-read, реал-тайм обновление

### Коммиты
- `9726929` — fix: JWT auth and infinite reconnect on auth failure
- `63ed73f` — fix: eliminate duplicate reconnect logic
- `959a79f` — feat: add DiffUtil to ChatAdapterV2
- `e029aa7` — feat: unread badges

---

## ✅ v1.1.3.19 — Unread Badges (Сессия 21)

### Исправлено
- ✅ **Badge colors by theme** — primary color background, adaptive text color
- ✅ **Mark as read on click** — clear badge + server MarkAsRead on chat open
- ✅ **Real-time update** — newMessageEvent SharedFlow for background messages
- ✅ **Test on dev** — ✅ протестировано на dev сервере

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

## 📋 Активные задачи (Сессия 22)

### Высокий приоритет
- [x] **Unread badges** — theme colors, mark-as-read on click, real-time update (v1.1.3.19)
- [x] **JWT auth fix** — getAccessToken() вместо getBearerToken() (v1.1.3.19)
- [x] **Reconnect stability** — убраны дубли reconnect, auth failure detection (v1.1.3.19)
- [ ] **Push notifications** — FCM интеграция

### Средний приоритет
- [ ] **ProfileService v2** — проверить работу на dev сервере
- [ ] **Read receipts** — MarkAsRead
- [ ] **Разделить RealGrpcClient** — выделить модули (ConnectionManager, ChatClient, AuthClient)

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
| `data/grpc/RealGrpcClient.kt` | Оркестратор модулей (3739 строк) |
| `data/grpc/GrpcConnectionManager.kt` | connect/reconnect/disconnect/keepalive (167 строк) |
| `data/grpc/GrpcAuthClient.kt` | signInV2/signUpV2/refreshToken/signOut (232 строки) |
| `data/grpc/GrpcCallClient.kt` | startCallSession/sendCallSignal (124 строки) |
| `data/grpc/GrpcTypingClient.kt` | startTypingStream/sendTypingSignal (87 строк) |
| `data/grpc/ProfileClient.kt` | ProfileService v2 client + version detection |
| `data/models/Message.kt` | Message (isPinned), ChatInfo (isPinned, isArchived, pinnedAt), AIChatInfo |
