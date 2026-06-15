# Lavender Messenger (Android) — Задачи

**Версия:** v1.1.3.16
**Обновлено:** 2026-06-16 (сессия 16)
**Ветка:** feat/1.1.3.x
**Тег:** v1.1.3.16

---

## ✅ v1.1.3.16 — Все фичи завершены (Сессии 13-16)

### Новое
- ✅ **ChatListActivityV2** — полная реализация: RecyclerView+SwipeRefresh напрямую в Activity
- ✅ **TabLayout** — табы All / AI / Groups с фильтрацией через ViewModel
- ✅ **Toolbar** — avatar→ProfileActivity, title→ServersActivity, search/settings icons
- ✅ **FABs** — fabAi (TODO), fabAddChat→NewChatActivity
- ✅ **Навигация** — favorites→NewChat, hermes→HermesChat, owl→OwlChat
- ✅ **Connection status** — subtitle с connecting/online/offline
- ✅ **SplashActivity** — маршрутизация v1/v2 по server host
- ✅ **Selection Mode** — long press = ActionMode toolbar, тап = toggle selection
- ✅ **Поиск** — SearchView в toolbar + debounce 300ms
- ✅ **Pin Message** — серверные RPC + клиент + UI (selection toolbar + pinned badge)
- ✅ **ServersActivity** — prefill username, splash после входа, все серверы доступны
- ✅ **CacheUtils** — единый утилит очистки кэша (clearAllSync + clearAllWithGlide)
- ✅ **Очистка кэша при входе** — silent, без Toast

### Исправления
- ✅ Дубликат `connecting` в strings.xml
- ✅ SplashActivity: полный путь для ChatListActivityV2
- ✅ ChatListFragmentV2: убран вызов несуществующего метода
- ✅ Deprecated onBackPressed → OnBackPressedDispatcher

### Коммиты
- `bd4e22c` — ChatListActivityV2 full implementation
- `4ddc712` — Selection Mode + Search
- `0256dab` — fix deprecated onBackPressed
- `7301de3` — Pin Message server
- `e05da8d` — Pin Message Android client
- `ec530ff` — Pin Message UI
- `b367998` — ServersActivity improvements
- `9929b32` — clear cache on login
- `ed40305` — extract CacheUtils
- `da0c3ae` — Pin Message via selection toolbar (v1-style)
- `7973b83` — fix CacheUtils

---

## ✅ v1.1.3.15 — Последняя стабильная v1 (prod сервер)

---

## 📋 Активные задачи (Сессия 17)

### Высокий приоритет
- [ ] **Тестирование** — на dev и prod серверах
- [ ] **protoc генерация** на сервере (после добавления PinMessage в proto)

### Средний приоритет
- [ ] **FAB AI** — создание AI чата (OwlActivity/HermesChatActivity)
- [ ] **HermesChatActivity / OwlChatActivity** — интеграция с v2 навигацией

### Отложено
- [ ] Qdrant + CLIP (production RAG)
- [ ] Shared element transitions
- [ ] Infinite scroll + pagination
- [ ] Unread badges улучшение

---

## 🔑 Ключевые решения

| Решение | Обоснование |
|---------|-------------|
| v1/v2 разделение | Новые файлы в ui/chatlist/, v1 без изменений |
| Long press = режим выбора | ActionMode toolbar с действиями Pin/Delete/Archive (как в Telegram) |
| Pin Chat в toolbar выбора | НЕ в обычном toolbar — только в режиме выбора |
| Pin Message — selection toolbar | Кнопка pin/unpin в selection toolbar (v1-style), НЕ PopupMenu |
| Archive — отдельная сущность | Заархивированные но не удалённые чаты |
| Favorites ≠ Archive | Существующий чат "Личное хранилище" — это не Archive! |
| fetchServerInfo fallback | Если /info недоступен → v1 для всех сервисов |
| onCancellation = {} | Обязательно в Kotlin 2.3.21 для cont.resume() |
| ChatListActivityV2 без фрагмента | Проще — RecyclerView+SwipeRefresh напрямую в Activity |
| CacheUtils | Единый утилит очистки кэша, не дублировать код |
| Очистка кэша при входе | Silent (без Toast), синхронная через CacheUtils.clearAllSync() |

---

## 📁 Ключевые файлы

| Файл | Назначение |
|------|------------|
| `ui/chatlist/ChatListActivityV2.kt` | v2 Activity: tabs, toolbar, FABs, navigation, selection mode |
| `ui/chatlist/ChatAdapterV2.kt` | v2 адаптер с секциями + selection state |
| `ui/chatlist/ChatListViewModelV2.kt` | v2 ViewModel: loadChats, pinChat, setTabFilter |
| `ui/chatlist/ChatListSections.kt` | Section enum + SectionItem |
| `ui/adapter/MessageAdapter.kt` | Адаптер сообщений + pinned badge |
| `data/cache/CacheUtils.kt` | Единый утилит очистки кэша |
| `data/grpc/GrpcClient.kt` | Facade (pinChat, pinMessage, searchChats, etc.) |
| `data/models/Message.kt` | Message (isPinned), ChatInfo (isPinned, isArchived, pinnedAt) |
| `res/layout/activity_chat_list_v2.xml` | v2 layout: SwipeRefresh+RecyclerView, TabLayout, FABs |
| `res/layout/item_message.xml` | Layout сообщения + pinned badge |
