# Lavender Messenger (Android) — Задачи

**Версия:** v1.1.3.20
**Обновлено:** 2026-06-16 (сессия 24)
**Ветка:** feat/1.1.3.x

---

## ✅ v1.1.3.20 — Модуляризация RealGrpcClient + OOM Protection (Сессии 23-24)

### Исправлено
- ✅ **RealGrpcClient разделён на модули**: 4081 → 3739 строк (-342, -8.4%)
- ✅ **GrpcConnectionManager** (167 строк) — connect/reconnect/disconnect/keepalive
- ✅ **GrpcAuthClient** (232 строки) — signInV2/signUpV2/refreshToken/signOut/revokeDevice
- ✅ **GrpcCallClient** (124 строки) — startCallSession/sendCallSignal
- ✅ **GrpcTypingClient** (87 строк) — startTypingStream/sendTypingSignal
- ✅ **ChatListFragmentV2** (144 строки) — удалён мёртвый код
- ✅ **Дублирование currentServerAddress/currentServerPort** — исправлено
- ✅ **Gradle wrapper удалён с сервера** — OOM protection

### Коммиты
- `c413038` — feat: modularize RealGrpcClient
- `208141c` — fix: resolve compilation errors from modularization

---

## ✅ v1.1.3.19 — Стабильность и оптимизация (Сессия 22)

- ✅ JWT auth fix — getAccessToken() вместо getBearerToken()
- ✅ Reconnect stability — единый источник onError, auth failure detection
- ✅ DiffUtil — ChatAdapterV2 использует DiffUtil
- ✅ Unread badges — цвета по теме, mark-as-read, реал-тайм обновление

---

## ✅ v1.1.3.18 — Стабильность соединения (Сессии 19-21)

- ✅ HTTP /info fix — dev сервер определяется мгновенно
- ✅ Keepalive 30s/10s, idleTimeout 25min
- ✅ Баг загрузки чатов — убран двойной loadChats, cache-first
- ✅ Poll interval 5s → 30s

---

## ✅ v1.1.3.17 — FAB AI (Сессия 17)

- ✅ AIBottomSheet, создание AI чатов, настройки

---

## ✅ v1.1.3.16 — Selection Mode, Search, Pin Message, CacheUtils (Сессии 13-16)

- ✅ Selection Mode, поиск, Pin Message, CacheUtils, ServersActivity

---

## ✅ v1.1.3.15 — Стабильная v1 (prod)

---

## 📋 Активные задачи

### Высокий приоритет
- [ ] **Выделить GrpcChatClient** — из оставшихся ~3700 строк RealGrpcClient
  - Методы: getChats, sendMessage, loadHistory, pinChat, searchChats, archiveChat, draft, favorites, reactions, profile, chat management
  - ~2000 строк — самый большой оставшийся кусок
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
| Gradle wrapper удалён с сервера | OOM protection — нельзя случайно запустить ./gradlew на сервере |

---

## 📁 Ключевые файлы

| Файл | Назначение |
|------|-----------|
| `ui/chatlist/ChatListActivityV2.kt` | v2 Activity: tabs, toolbar, FABs, navigation, selection mode, search, AI bottom sheet |
| `ui/chatlist/ChatAdapterV2.kt` | v2 адаптер с секциями + selection state + DiffUtil |
| `ui/chatlist/ChatListViewModelV2.kt` | v2 ViewModel: loadChats, pinChat, setTabFilter, getChats |
| `ui/chatlist/ChatListSections.kt` | Section enum + SectionItem |
| `ui/adapter/MessageAdapter.kt` | Адаптер сообщений + pinned badge |
| `ui/widget/AIBottomSheet.kt` | Шторка выбора AI чата (OWL/Hermes) |
| `data/cache/CacheUtils.kt` | Единый утилит очистки кэша |
| `data/grpc/GrpcClient.kt` | Facade (pinChat, pinMessage, searchChats, etc.) |
| `data/grpc/RealGrpcClient.kt` | Оркестратор модулей (~3700 строк, цель: ~200) |
| `data/grpc/GrpcConnectionManager.kt` | connect/reconnect/disconnect/keepalive (167 строк) |
| `data/grpc/GrpcAuthClient.kt` | signInV2/signUpV2/refreshToken/signOut (232 строки) |
| `data/grpc/GrpcCallClient.kt` | startCallSession/sendCallSignal (124 строки) |
| `data/grpc/GrpcTypingClient.kt` | startTypingStream/sendTypingSignal (87 строк) |
| `data/grpc/ProfileClient.kt` | ProfileService v2 client + version detection |
| `data/models/Message.kt` | Message (isPinned), ChatInfo (isPinned, isArchived, pinnedAt), AIChatInfo |
