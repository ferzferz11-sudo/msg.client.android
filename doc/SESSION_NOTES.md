# Lava Messenger — Android Session Notes

## Сессия 41 (2026-06-17) — v1.1.3.32 final
- Табы: порядок Все → Группы → ИИ чаты
- "AI" → "AI Chats" (en), "ИИ" → "ИИ чаты" (ru)
- Исправлена компиляция: `showAddContactDialogPublic()` → `showAddContactDialog()` в NewChatBottomSheet
- Коммит: `118f178`

## Сессия 40 (2026-06-17) — ChatListActivity модуляризация
- Вынесены: ChatListFABs (~450 LOC), ChatListNavigation (~60), ChatListAuth (~250)
- ChatListActivity: 1085 → ~600 LOC (-45%), всего 10 модулей
- Коммит: `335b5a6`

## Сессия 39 (2026-06-17) — ChatList stability fixes
- loadChats(): при timeout НЕ перезаписывать allChats (indexOfFirst проверка перед map)
- Read receipts: indexOfFirst вместо map по всему списку
- Коммит: `dd8ba35`

## Сессия 38 (2026-06-17) — Read receipts broadcast
- readReceiptEvent SharedFlow: RealGrpcClient → GrpcClient → ChatListViewModel
- GrpcMessageClient: onReadReceipt callback
- Цепочка: Server MarkRead → Broadcast → handleReadAllSignal → emit → clear unread

## Сессия 37 (2026-06-17) — ChatListActivity первичная модуляризация
- Вынесены: ChatListToolbar (232), ChatListTabs (29), ChatListActionMode (120), ChatListSearch (55)
- ChatListActivity: 1470 → 1085 LOC (-26%)

## Сессия 36 (2026-06-17) — FAB + Favorites
- FAB [+] восстановлен: ActionBottomSheet + SearchableListBottomSheet (v1 паттерн)
- Favorites убран из секций, добавлен в шторку профиля
- FavoritesActivity исправлен: SessionManager, SwipeRefresh, empty state
