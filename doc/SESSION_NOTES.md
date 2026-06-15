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
- **Ферз выпустил релиз Android v1.1.3.15** — последняя версия с полной поддержкой v1 (prod сервер)
- Все изменения сессии 12 (план + v2 scaffold) пойдут в следующую версию

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

### Архитектурные решения
- v1.1.3.15 = последняя версия с полной поддержкой v1 (prod сервер) — ВЫПУЩЕН
- v1.1.3.16+ = v2 клиент с ChatList v2 UI (dev сервер) — в разработке
- Переключение: fetchServerInfo() → isChatV2Supported() → выбор Activity
- План сохранён в `doc/PLAN_CHATLIST_V2.md`

## Следующие шаги
1. ЭТАП 2: TabLayout + ViewPager2 для табов All/AI/Groups
2. ЭТАП 3: Интеграция с v1 AI/Owl/Hermes чатами
3. ЭТАП 4: Переключение v1/v2 при старте (программный выбор)
4. ЭТАП 5: Тестирование на dev сервере
5. Выпуск v1.1.3.16 с полным v2 UI
