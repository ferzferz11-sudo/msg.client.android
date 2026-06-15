# Lavender Messenger (Android) — Задачи

**Версия:** v1.1.3.16
**Обновлено:** 2026-06-16 (сессия 13)
**Ветка:** feat/1.1.3.x

---

## ✅ v1.1.3.16 — ChatList v2 UI полная реализация (Сессия 13)

### Новое
- ✅ **ChatListActivityV2** — полная переработка: RecyclerView+SwipeRefresh напрямую в Activity (без фрагмента)
- ✅ **TabLayout** — табы All / AI / Groups с фильтрацией через ViewModel.setTabFilter
- ✅ **Toolbar** — avatar→ProfileActivity, title→ServersActivity, search/settings icons
- ✅ **FABs** — fabAi (TODO), fabAddChat→NewChatActivity
- ✅ **Навигация** — favorites→NewChat, hermes→HermesChat, owl→OwlChat, other→NewChat
- ✅ **Connection status** — subtitle с connecting/online/offline
- ✅ **SplashActivity** — маршрутизация v1/v2 по наличию server host
- ✅ **ChatAdapterV2** — исправлено дублирование cachedColors
- ✅ **AndroidManifest** — регистрация ChatListActivityV2, удалены дубликаты
- ✅ **strings.xml** — connection status строки (en+ru)

### Исправления билда
- ✅ Дубликат `connecting` в values/strings.xml и values-ru/strings.xml
- ✅ SplashActivity: полный путь для ChatListActivityV2
- ✅ SplashActivity: закрывающая скобка класса
- ✅ ChatListFragmentV2: убран вызов несуществующего viewModel.onChatClick()

### Коммиты
- `bd4e22c` — feat: ChatListActivityV2 full implementation
- `d270215` — fix: remove duplicate connecting string
- `4cdd9a0` — fix: full package path for ChatListActivityV2
- `a9d487a` — fix: missing closing brace
- `35e6b2b` — fix: ChatListFragmentV2 unresolved reference

---

## ✅ v1.1.3.16 — ChatList v2 UI scaffold (Сессия 12)
- ✅ ChatListActivityV2, ChatListFragmentV2, ChatAdapterV2, ChatListViewModelV2, ChatListSections
- ✅ TabLayout (заглушка), v2 context menu, fallback на v1
- ✅ i18n: 17 новых строк (en + ru)

---

## ✅ v1.1.3.15 — Последняя стабильная v1 (prod сервер) — ВЫПУЩЕН

---

## ✅ v1.1.3.14 — ChatStream v2 + ChatList v2 (Сессия 11)

---

## ✅ v1.1.3.13 — ProfileService v2 client (Сессия 9)

---

## ✅ v1.1.3.12 — Bearer Token Interceptor + Token Refresh (Сессия 8)

---

## 📋 Активные задачи (Сессия 14)

### Высокий приоритет
- [x] **Selection Mode** — long press = ActionMode toolbar (Pin/Delete/Archive/Mute), тап = toggle selection (множественный выбор)
- [x] **Поиск** — SearchView в toolbar + debounce 300ms + локальная фильтрация allChats
- [ ] **Pin Message** — серверные RPC PinMessage/UnPinMessage + таблица pinned_messages
- [ ] **Pin Message клиент** — pinMessage/unPinMessage в GrpcClient + RealGrpcClient
- [ ] **Pin Message UI** — отображение закреплённого сообщения в MessageAdapter
- [ ] **Тестирование** — на dev и prod серверах

### Средний приоритет
- [ ] **Pin Message** — серверные RPC PinMessage/UnPinMessage + таблица pinned_messages
- [ ] **Pin Message клиент** — pinMessage/unPinMessage в GrpcClient + RealGrpcClient
- [ ] **Pin Message UI** — отображение закреплённого сообщения в MessageAdapter
- [ ] **Тестирование** — на dev и prod серверах

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
| Pin Message — отдельная фича | В шторке сообщения (bottom sheet), нужны новые серверные RPC |
| Archive — отдельная сущность | Заархивированные но не удалённые чаты |
| Favorites ≠ Archive | Существующий чат "Личное хранилище" — это не Archive! |
| fetchServerInfo fallback | Если /info недоступен → v1 для всех сервисов |
| onCancellation = {} | Обязательно в Kotlin 2.3.21 для cont.resume() |
| ChatListActivityV2 без фрагмента | Проще — RecyclerView+SwipeRefresh напрямую в Activity |

---

## 📁 Ключевые файлы

| Файл | Назначение |
|------|------------|
| `ui/chatlist/ChatListActivityV2.kt` | v2 Activity: tabs, toolbar, FABs, navigation |
| `ui/chatlist/ChatAdapterV2.kt` | v2 адаптер с секциями (единый кэш цветов) |
| `ui/chatlist/ChatListViewModelV2.kt` | v2 ViewModel: loadChats, pinChat, setTabFilter |
| `ui/chatlist/ChatListSections.kt` | Section enum + SectionItem |
| `ui/chatlist/ChatListFragmentV2.kt` | v2 фрагмент (не используется, для справки) |
| `res/layout/activity_chat_list_v2.xml` | v2 layout: SwipeRefresh+RecyclerView, TabLayout, FABs |
| `ProfileClient.kt` | ProfileService v2 client + fetchServerInfo |
| `GrpcClient.kt` | Facade (pinChat, searchChats, archiveChat, etc.) |
| `Message.kt` | ChatInfo модель (isPinned, isArchived, pinnedAt) |
| `doc/PLAN_CHATLIST_V2.md` | Детальный план реализации |
