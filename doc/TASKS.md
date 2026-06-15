# Lavender Messenger (Android) — Задачи

**Версия:** v1.1.3.15
**Обновлено:** 2026-06-16 (сессия 12)
**Ветка:** feat/1.1.3.x

---

## ✅ v1.1.3.15 — Последняя стабильная v1 версия (prod сервер) — ВЫПУЩЕН РЕЛИЗ
- version.txt: 1.1.3.14 → 1.1.3.15
- CHANGELOG.md: добавлена секция v1.1.3.15
- **Ферз выпустил релиз v1.1.3.15** — последняя версия с полной поддержкой v1
- Все изменения сессии 12 (v2 scaffold) пойдут в v1.1.3.16+

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

## 📋 ChatList v2 UI (v1.1.3.16+) — НОВАЯ АРХИТЕКТЕРА

### Принцип: Чистое разделение v1/v2
- **v1 файлы НЕ ТРОГАТЬ**: ChatListActivity.kt, ChatAdapter.kt
- **v2 — новые файлы** в папке `ui/chatlist/`
- Переключение: fetchServerInfo() → isChatV2Supported() → выбор Activity

### ЭТАП 1: v2 каркас — ✅ ЗАВЕРШЁН
- ✅ Создана папка `ui/chatlist/`
- ✅ ChatListActivityV2.kt — новый Activity с fallback на v1
- ✅ ChatListFragmentV2.kt — фрагмент с RecyclerView + SwipeRefresh
- ✅ ChatAdapterV2.kt — адаптер с секциями (Pinned/Favorites/All)
- ✅ ChatListViewModelV2.kt — ViewModel: loadChats, pinChat, archiveChat, searchChats
- ✅ ChatListSections.kt — Section enum + SectionItem data class
- ✅ Layout: activity_chat_list_v2.xml, fragment_chat_list_v2.xml, item_chat_section_header.xml
- ✅ Меню: chat_context_menu.xml (Pin/Mute/Archive/Delete)
- ✅ i18n: 17 новых строк (en + ru)
- Коммит: `7d087bc`

### ЭТАП 2: Табы + интеграция — В РАБОТЕ
- [ ] TabLayout + ViewPager2 для табов All / AI / Groups
- [ ] Интеграция с v1 AI/Owl/Hermes чатами в v2 адаптере
- [ ] Переключение v1/v2 при старте (программный выбор в SplashActivity)
- [ ] AndroidManifest.xml — регистрация ChatListActivityV2
- [ ] Тестирование на dev сервере

### ЭТАП 3: Pin Message (сервер + клиент)
- [ ] **Сервер:** новые RPC PinMessage/UnPinMessage в messenger.proto
- [ ] **Сервер:** таблица pinned_messages (chat_id, message_id, pinned_at, pinned_by)
- [ ] **Сервер:** реализация PinMessage/UnPinMessage в server_pinned_messages.go
- [ ] **Клиент:** pinMessage/unPinMessage в GrpcClient.kt + RealGrpcClient.kt
- [ ] **Клиент:** кнопка Pin в toolbar NewChatActivity
- [ ] **Клиент:** отображение закреплённого сообщения в MessageAdapter
- [ ] **Клиент:** proto классы для PinMessageRequest/Response

### ЭТАП 4: Дополнительные фичи (после основного)
- [ ] Shared element transitions
- [ ] ThemeApplier обновление для новых FAB
- [ ] Infinite scroll + pagination
- [ ] Unread badges улучшение

---

## 🔑 Ключевые решения

| Решение | Обоснование |
|---------|-------------|
| fetchServerInfo fallback | Если /info недоступен → v1 для всех сервисов |
| ChatStream v2 auth | JWT token в первом сообщении stream вместо password |
| ChatList v2 API | Отдельные RPC методы (PinChat, SearchChats, etc.) |
| v2 методы на v1 сервере | Возвращают false/empty — UI адаптируется |
| onCancellation = {} | Обязательно в Kotlin 2.3.21 для cont.resume() |
| v1/v2 разделение | Новые файлы в ui/chatlist/, v1 без изменений |
| Pin Message | Отдельная таблица pinned_messages, НЕ PinChat (чат) |
| Favorites = Archive | Существующий чат "Личное хранилище" заменяет Archive |

---

## 📁 Ключевые файлы

| Файл | Назначение |
|------|------------|
| `ui/chatlist/ChatListActivityV2.kt` | v2 Activity с определением версии сервера |
| `ui/chatlist/ChatListFragmentV2.kt` | v2 фрагмент с RecyclerView |
| `ui/chatlist/ChatAdapterV2.kt` | v2 адаптер с секциями |
| `ui/chatlist/ChatListViewModelV2.kt` | v2 ViewModel |
| `ui/chatlist/ChatListSections.kt` | Управление секциями |
| `ProfileClient.kt` | ProfileService v2 client + fetchServerInfo |
| `GrpcClient.kt` | Facade (pinChat, searchChats, archiveChat, etc.) |
| `Message.kt` | ChatInfo модель (isPinned, isArchived, pinnedAt) |
| `doc/PLAN_CHATLIST_V2.md` | Детальный план реализации |
