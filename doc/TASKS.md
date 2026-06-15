# Lavender Messenger (Android) — Задачи

**Версия:** v1.1.3.16
**Обновлено:** 2026-06-16 (сессия 12)
**Ветка:** feat/1.1.3.x

---

## ✅ v1.1.3.16 — ChatList v2 UI (Сессия 12)

### Новое: ChatList v2 UI (dev server)
- ✅ **ChatListActivityV2** — новый Activity с определением версии сервера (v1/v2)
- ✅ **ChatListFragmentV2** — фрагмент с SwipeRefresh + RecyclerView
- ✅ **ChatAdapterV2** — адаптер с секциями (Pinned/Favorites/All Chats)
- ✅ **ChatListViewModelV2** — ViewModel: loadChats, pinChat, archiveChat, searchChats
- ✅ **ChatListSections.kt** — управление секциями
- ✅ **TabLayout** — табы All / AI / Groups (заглушка)
- ✅ **v2 context menu** — Pin/Mute/Delete в списке чатов (long press)
- ✅ **Fallback на v1** — при подключении к prod серверу автоматически запускается ChatListActivity v1
- ✅ **i18n** — 17 новых строк (en + ru)

### Исправления билда
- ✅ `@++id/` → `@+id/` — невалидный XML синтаксис
- ✅ ConstraintLayout атрибуты → layout_gravity в CoordinatorLayout
- ✅ Убран несуществующий TextAppearance стиль
- ✅ parseSafeColor — добавлен defaultColor параметр
- ✅ ThemeApplier.apply — исправлена сигнатура
- ✅ ServerAuthBottomSheet — исправлены параметры конструктора

### Коммиты
- `7d087bc` — v2 scaffold
- `0f500ce` — fix ConstraintLayout attrs
- `23a2a79` — fix TextAppearance
- `6fb3453` — fix @++id/
- `28c2715` — fix compilation errors
- `f0b06e1` — restore version.txt to 1.1.3.15

---

## ✅ v1.1.3.15 — Последняя стабильная v1 (prod сервер) — ВЫПУЩЕН
- version.txt: 1.1.3.14 → 1.1.3.15
- **Ферз выпустил релиз v1.1.3.15**

---

## ✅ v1.1.3.14 — ChatStream v2 + ChatList v2
(Сессия 11 — завершено)

---

## ✅ v1.1.3.13 — ProfileService v2 client
(Сессия 9 — завершено)

---

## ✅ v1.1.3.12 — Bearer Token Interceptor + Token Refresh
(Сессия 8 — завершено)

---

## 📋 Активные задачи (Сессия 13)

### Высокий приоритет
- [ ] **Режим выбора (selection mode)** — long press на чате = toolbar с Pin/Delete/Edit/Archive (как в Telegram). Короткий тап = вход в чат. Заменить context menu на selection mode в ChatListFragmentV2 + ChatAdapterV2
- [ ] **TabLayout + ViewPager2** — табы All / AI / Groups с фильтрацией
- [ ] **Переключение v1/v2 при старте** — программный выбор Activity (fetchServerInfo → isChatV2Supported)
- [ ] **AndroidManifest.xml** — регистрация ChatListActivityV2
- [ ] **ThemeApplier** — добавить новые FAB в список для тем

### Средний приоритет
- [ ] **Pin Message** — серверные RPC PinMessage/UnPinMessage + таблица pinned_messages
- [ ] **Pin Message клиент** — pinMessage/unPinMessage в GrpcClient + RealGrpcClient
- [ ] **Pin Message UI** — отображение закреплённого сообщения в MessageAdapter
- [ ] **Тестирование** — на dev и prod серверах

### Отложено
- [ ] Qdrant + CLIP (production RAG) — см. AI_SERVICES.md
- [ ] Shared element transitions
- [ ] Infinite scroll + pagination
- [ ] Unread badges улучшение

---

## 🔑 Ключевые решения

| Решение | Обоснование |
|---------|-------------|
| v1/v2 разделение | Новые файлы в ui/chatlist/, v1 без изменений |
| Long press = режим выбора | Toolbar с действиями Pin/Delete/Edit (как в Telegram) |
| Pin Chat в toolbar выбора | НЕ в обычном toolbar — только в режиме выбора |
| Pin Message — отдельная фича | В шторке сообщения (bottom sheet), нужны новые серверные RPC |
| Archive — отдельная сущность | Заархивированные но не удалённые чаты |
| Favorites ≠ Archive | Существующий чат "Личное хранилище" — это не Archive! |
| fetchServerInfo fallback | Если /info недоступен → v1 для всех сервисов |
| onCancellation = {} | Обязательно в Kotlin 2.3.21 для cont.resume() |

---

## 📁 Ключевые файлы

| Файл | Назначение |
|------|------------|
| `ui/chatlist/ChatListActivityV2.kt` | v2 Activity с определением версии сервера |
| `ui/chatlist/ChatListFragmentV2.kt` | v2 фрагмент с RecyclerView |
| `ui/chatlist/ChatAdapterV2.kt` | v2 адаптер с секциями |
| `ui/chatlist/ChatListViewModelV2.kt` | v2 ViewModel |
| `ui/chatlist/ChatListSections.kt` | Управление секциями |
| `res/layout/activity_chat_list_v2.xml` | v2 layout с TabLayout |
| `res/menu/chat_list_context_menu_v2.xml` | УДАЛИТЬ — заменить на selection mode |
| `ProfileClient.kt` | ProfileService v2 client + fetchServerInfo |
| `GrpcClient.kt` | Facade (pinChat, searchChats, archiveChat, etc.) |
| `Message.kt` | ChatInfo модель (isPinned, isArchived, pinnedAt) |
| `doc/PLAN_CHATLIST_V2.md` | Детальный план реализации |
