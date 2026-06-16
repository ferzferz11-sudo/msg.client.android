# Lavender Messenger (Android) — Задачи

**Версия:** v1.1.3.17
**Обновлено:** 2026-06-15 (сессия 17)
**Ветка:** feat/1.1.3.x
**Тег:** v1.1.3.17

---

## ✅ v1.1.3.17 — FAB AI + интеграция (Сессия 17)

### Новое
- ✅ **FAB AI** — AIBottomSheet подключён к ChatListActivityV2
- ✅ **Создание AI чата** — Hermes/OWL через AIBottomSheet
- ✅ **Существующие AI чаты** — отображаются в списке с навигацией
- ✅ **Удаление/Настройки** AI чатов

### Исправления
- ✅ HermesSettingsActivity → OwlSettingsActivity с isHermes=true
- ✅ AIChatInfo mapping: только id, name, type

### Коммиты
- `58f7115` — feat: FAB AI — AIBottomSheet integration in ChatListActivityV2
- `1d989f1` — chore: protoc regeneration + docs update

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

## ✅ v1.1.3.18 — Исправление бага загрузки чатов (Сессия 19)

### Исправлено
- ✅ **Баг: чаты не загружаются при входе на новый сервер**
- `RealGrpcClient.connect()`: health check перед READY
- `RealGrpcClient.getChats()`: убрана cache-first логика
- `ChatListActivityV2`: убран двойной loadChats()
- `ChatListActivityV2` + `ChatListActivity`: onResume() safety net

### Коммиты
- `3f808bf` — fix: resolve chat loading race condition (Phase 1)

---

## 📋 Активные задачи (Сессия 19)

### Высокий приоритет
- [ ] **Тестирование v1.1.3.18** — проверить исправление бага на dev и prod

### Средний приоритет
- [ ] **Unread badges** — улучшение счётчика непрочитанных в списке чатов
- [ ] **Push notifications** — FCM интеграция для v2

### Отложено
- [ ] Qdrant + CLIP (production RAG)
- [ ] Shared element transitions
- [ ] Infinite scroll + pagination
- [ ] Read receipts (MarkAsRead)

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
| HermesSettings → OwlSettingsActivity | Переиспользование с isHermes=true, нет отдельного класса |
| AIChatInfo минимальная | Только id, name, type — не содержит полей ChatInfo |

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
| `data/models/Message.kt` | Message (isPinned), ChatInfo (isPinned, isArchived, pinnedAt), AIChatInfo |
| `res/layout/activity_chat_list_v2.xml` | v2 layout: SwipeRefresh+RecyclerView, TabLayout, FABs |
| `res/layout/item_message.xml` | Layout сообщения + pinned badge |
