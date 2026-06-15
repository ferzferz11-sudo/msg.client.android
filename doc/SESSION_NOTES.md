# Заметки сессии 12 — 2026-06-16

## Что сделано

### План ChatList v2 UI
- Создан детальный план: `doc/PLAN_CHATLIST_V2.md`
- Ключевое решение: РАЗДЕЛЕНИЕ v1/v2 архитектуры
- v1 файлы НЕ ТРОГАТЬ (ChatListActivity.kt, ChatAdapter.kt)
- v2 — в новой папке `ui/chatlist/`

### ЭТАП 0: v1.1.3.15 (последняя v1 версия) — ВЫПУЩЕН РЕЛИЗ
- version.txt: 1.1.3.14 → 1.1.3.15
- CHANGELOG.md: добавлена секция v1.1.3.15
- **Ферз выпустил релиз Android v1.1.3.15** — последняя версия с полной поддержкой v1

### ЭТАП 1: ChatList v2 UI scaffold
- Создана папка `ui/chatlist/` с 5 файлами:
  - ChatListActivityV2.kt — новый Activity с определением версии сервера
  - ChatListFragmentV2.kt — фрагмент с RecyclerView + SwipeRefresh
  - ChatAdapterV2.kt — адаптер с секциями (Pinned/Favorites/All)
  - ChatListViewModelV2.kt — ViewModel: loadChats, pinChat, archiveChat, searchChats
  - ChatListSections.kt — Section enum + SectionItem data class
- Layout файлы: activity_chat_list_v2.xml, fragment_chat_list_v2.xml, item_chat_section_header.xml
- Меню: chat_context_menu.xml (Pin/Mute/Archive/Delete)
- i18n: 17 новых строк (en + ru)

### Архитектурные решения (уточнено ферзём)
- **Pin Message** — закрепление СООБЩЕНИЯ внутри чата (как в Telegram), кнопка в toolbar чата
  - Нужны новые серверные RPC: PinMessage/UnPinMessage
  - Новая таблица: pinned_messages (chat_id, message_id, pinned_at, pinned_by)
  - НЕ PinChat (который pin'ит чат в списке) — это другое!
- **Favorites** — существующий чат "Личное хранилище" в списке, заменяет Archive
- **Список чатов** — context menu без изменений (mute/delete/edit), Pin/Archive НЕ добавляются
- **v1.1.3.15** = последняя версия с полной поддержкой v1 (prod сервер) — ВЫПУЩЕН
- **v1.1.3.16+** = v2 клиент с ChatList v2 UI + Pin Message (dev сервер)

## Следующие шаги
1. ЭТАП 2: TabLayout + ViewPager2 для табов All/AI/Groups
2. ЭТАП 3: Pin Message — серверные RPC + клиентская реализация
3. ЭТАП 4: Переключение v1/v2 при старте
4. ЭТАП 5: Тестирование на dev сервере
