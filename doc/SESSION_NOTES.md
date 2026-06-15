# Заметки сессии 12 — 2026-06-16

## Что сделано

### План ChatList v2 UI + разделение v1/v2
- Создан детальный план: `doc/PLAN_CHATLIST_V2.md`
- Ключевое решение: ЧИСТОЕ РАЗДЕЛЕНИЕ v1/v2 архитектуры
- v1 файлы НЕ ТРОГАТЬ (ChatListActivity.kt, ChatAdapter.kt)
- v2 — в новой папке `ui/chatlist/`

### ЭТАП 0: v1.1.3.15 — ВЫПУЩЕН РЕЛИЗ
- version.txt: 1.1.3.14 → 1.1.3.15
- **Ферз выпустил релиз v1.1.3.15** на prod сервер

### ЭТАП 1: ChatList v2 UI scaffold (v1.1.3.16)
Создана папка `ui/chatlist/`:
- ChatListActivityV2.kt — определение версии сервера + fallback на v1
- ChatListFragmentV2.kt — SwipeRefresh + RecyclerView
- ChatAdapterV2.kt — адаптер с секциями (Pinned/Favorites/All)
- ChatListViewModelV2.kt — loadChats, pinChat, archiveChat, searchChats
- ChatListSections.kt — Section enum + SectionItem

Layout: activity_chat_list_v2.xml, fragment_chat_list_v2.xml, item_chat_section_header.xml
Меню: chat_list_context_menu_v2.xml (Pin/Mute/Delete)
i18n: 17 строк (en + ru)

### Архитектура v2 (уточнено ферзём)
```
Pin Chat — context menu списка (long press), НЕ toolbar
Pin Message — в меню сообщения (long press), нужны новые серверные RPC
Favorites — существующий чат "Личное хранилище" = заменяет Archive
Секции списка: Pinned / Favorites / All Chats
Табы: All / AI / Groups
```

### Исправления ошибок билда
- `@++id/` → `@+id/` — невалидный XML синтаксис ломал data binding
- `app:layout_constraint*` → `android:layout_gravity` — ConstraintLayout в CoordinatorLayout
- Убран `tabTextAppearance` — несуществующий стиль
- `parseSafeColor` — добавлен defaultColor параметр
- `ThemeApplier.apply` — исправлена сигнатура (activity, theme)
- `ServerAuthBottomSheet` — исправлены параметры конструктора
- Удалён неиспользуемый `chat_context_menu.xml`

### Коммиты сессии
- `b95a6f4` — v1.1.3.15 release + PLAN_CHATLIST_V2.md
- `7d087bc` — v2 scaffold
- `484bc61` — docs: v1.1.3.15 released, v2 scaffold status
- `0f500ce` — fix ConstraintLayout attrs in CoordinatorLayout
- `23a2a79` — fix TextAppearance missing style
- `bf00543` — remove unused chat_context_menu.xml, restore i18n
- `6fb3453` — fix @++id/ double plus syntax
- `28c2715` — fix compilation errors
- `f0b06e1` — restore version.txt to 1.1.3.15

## Следующие шаги (сессия 13)
1. TabLayout + ViewPager2 для табов All/AI/Groups
2. Pin Message — серверные RPC + клиентская реализация
3. Переключение v1/v2 при старте (программный выбор Activity)
4. Тестирование на dev и prod серверах
