# Lava Messenger (Android) — Задачи

**Версия:** v1.1.3.30
**Обновлено:** 2026-06-17 (сессия 36)
**Ветка:** feat/1.1.3.x

---

## ✅ v1.1.3.31 — ChatListActivity рефакторинг (Сессия 37)

### Разбиение ChatListActivity (1470 → 1085 строк, -26%)
- ✅ **ChatListToolbar.kt** (232 строк) — setupToolbarActions, showSettingsSheet, showAdditionalSettingsSheet, confirmDeleteProfile, showAboutDialog, shareApp, toggleLanguage
- ✅ **ChatListTabs.kt** (29 строк) — setupTabs с TabLayout
- ✅ **ChatListActionMode.kt** (120 строк) — createActionModeCallback, updateActionModeTitle, pinSelectedChats, muteSelectedChats, archiveSelectedChats, deleteSelectedChats
- ✅ **ChatListSearch.kt** (55 строк) — setupSearchMenu с SearchView + debounce
- Поля ChatListActivity изменены с `private` на `internal` для доступа из модулей того же пакета

### Архитектура после рефакторинга
```
ChatListActivity.kt (1085) — основной Activity
├── ChatListToolbar.kt (232) — toolbar + settings sheets
├── ChatListTabs.kt (29) — tabs
├── ChatListActionMode.kt (120) — selection mode
├── ChatListSearch.kt (55) — search
├── ChatListViewModel.kt (268) — ViewModel
├── ChatListSections.kt (20) — sections
└── UpdateCoordinator.kt (245) — updates
```

---

## ✅ v1.1.3.30 — FAB [+] восстановление + Favorites fix (Сессия 36)

### FAB [+] — ActionBottomSheet + SearchableListBottomSheet
- ✅ **ChatListActivity.showChatActionSheet()** — ActionBottomSheet с 4 пунктами (Add Contact, Start Chat, Secret Chat, Conference)
- ✅ **showAddContactDialog()** — SearchableListBottomSheet: поиск пользователей, фильтрация контактов, мультивыбор, чекбокс "Create direct chat after"
- ✅ **showCreateChatDialog()** — SearchableListBottomSheet: выбор 1 пользователя → direct chat, 2+ → group chat с полем имени
- ✅ **showCreateSecretChatDialog()** — SearchableListBottomSheet: одиночный выбор, E2EE ключи через E2EEManager.getPublicKeyBase64()
- ✅ **showCreateConferenceDialog()** — SearchableListBottomSheet: мультивыбор, поле topic, createGroupChat с type="conference"
- ✅ **setupFABs()** — замена NewChatBottomSheet на showChatActionSheet()

### Favorites — убрать из секций
- ✅ **ChatListViewModel.buildSections()** — убрана секция Favorites, убран loadFavorites()
- ✅ **ChatAdapter** — убран FavoritesItem, TYPE_FAVORITES, FavoritesViewHolder
- ✅ **ChatListSections.kt** — убран Section.FAVORITES
- ✅ **navigateToChat()** — убрана навигация на FavoritesActivity из списка

### Favorites — добавить в шторку профиля
- ✅ **bottom_sheet_user_menu.xml** — добавлен пункт actionFavorites (ic_star)
- ✅ **ChatListActivity.showSettingsSheet()** — добавлен обработчик Favorites → FavoritesActivity

### FavoritesActivity — исправления
- ✅ userId получается из SessionManager.session.value.userId напрямую
- ✅ Добавлен SwipeRefreshLayout для pull-to-refresh
- ✅ Добавлено пустое состояние "No favorites yet" / "Избранного пока нет"
- ✅ Обновлён layout activity_favorites.xml (SwipeRefreshLayout)

### Строки
- ✅ Добавлены строки: no_favorites_yet, contacts_added (en + ru)

---

## ✅ v1.1.3.29 — UI улучшения (Сессия 35)

### FAB [+] — шторка создания нового чата
- ✅ **bottom_sheet_new_chat.xml** — 4 пункта (Add Contact, Start Chat, Group, Secret Chat)
- ✅ **NewChatBottomSheet.kt** — переписан, AI чаты убраны (отдельно через FAB AI)
- ✅ **ChatListActivity.kt** — добавлен `showAddContactDialogPublic()` для ContactsActivity

### Favorites — секция в списке
- ✅ Favorites — секция в списке чатов (не таб), открывается FavoritesActivity
- ✅ **ChatListViewModel.loadFavorites()** — загрузка данных Favorites с сервера
- ✅ Таб "Favorites" убран, остались All/AI/Groups

### Кастомные темы
- ✅ **activity_chat_list.xml** — AppBarLayout ID, transparent bg, ivActionSettings всегда виден
- ✅ **ThemeApplier.kt** — AppBarLayout tinting, toolbar title/subtitle explicit colors, TabLayout transparent
- ✅ **ChatListActivity.applyTheme()** — добавлен `ThemeStore.init(this)` для загрузки кастомной темы из кэша

---

## ✅ v1.1.3.28 — Финальная модуляризация RealGrpcClient (Сессия 34)

### Новые модули
- ✅ **GrpcMessageClient** (341 LOC) — sendMessage, addLocalMessage, loadHistory, editMessage, deleteMessage, setReaction, markRead, resendPendingMessages, resendPendingReads, signal handlers
- ✅ **GrpcServerDiscoveryClient** (145 LOC) — fetchServersList, fetchServersFromHost, parseServerList, parseServerInfo, readVarint, skipField

### Результат
- RealGrpcClient: 1615 → 874 LOC (-741 LOC, -46%)
- Всего выделено модулей: 12 (ConnectionManager, Auth, Call, Typing, ChatList, Profile, Draft, Favorites, UnaryCallHelper, Marshallers, Message, ServerDiscovery)
- Итого с начала рефакторинга: RealGrpcClient 3810 → 874 LOC (-77%)

### Коммит
- TBD — refactor: extract GrpcMessageClient + GrpcServerDiscoveryClient, RealGrpcClient 1615→874 LOC

---

## ✅ v1.1.3.27 — Извлечение GrpcMarshallers (Сессия 33)

### Новый модуль
- ✅ **GrpcMarshallers.kt** (1394 LOC) — все 111 классов MethodDescriptor.Marshaller, извлечены из RealGrpcClient

### Результат
- RealGrpcClient: 2992 → 1611 LOC (-1381 LOC, -46%)
- Всего выделено модулей: 10 (ConnectionManager, Auth, Call, Typing, ChatList, Profile, Draft, Favorites, UnaryCallHelper, Marshallers)
- Дублирование маршаллеров устранено (были в обоих файлах — оставлены только в GrpcMarshallers.kt)

### Коммит
- TBD — refactor: extract GrpcMarshallers.kt — 111 marshaller classes, RealGrpcClient 2992→1611 LOC

---

## ✅ v1.1.3.26 — Продолжение модуляризации RealGrpcClient (Сессия 32)

### Новые модули
- ✅ **GrpcChatListClient** (638 LOC) — getChats, pinChat, searchChats, archiveChat, chat management
- ✅ **GrpcProfileClient** (506 LOC) — profile, avatar, contacts, themes, devices
- ✅ **GrpcDraftClient** (86 LOC) — saveDraft, getDraft, deleteDraft
- ✅ **GrpcFavoritesClient** (120 LOC) — addFavorite, removeFavorite, getFavorites, saveFavoriteMessage
- ✅ **GrpcUnaryCallHelper** (111 LOC) — универсальный helper для unary вызовов

### Результат
- RealGrpcClient: 3810 → 2992 LOC (-818 LOC, -21%)
- Всего выделено модулей: 9 (ConnectionManager, Auth, Call, Typing, ChatList, Profile, Draft, Favorites, UnaryCallHelper)
- Добавлен CODE_AUDIT.md

### Коммит
- `9d0b61a` — refactor: extract GrpcChatListClient, GrpcProfileClient, GrpcDraftClient, GrpcFavoritesClient, GrpcUnaryCallHelper

---

## ✅ v1.1.3.26 — RealGrpcClient полная модуляризация (Сессия 32)

### Рефакторинг
- **RealGrpcClient: 3810 → 1615 строк (-57%)**
- Создано 5 новых модулей:
  - GrpcChatListClient (639 строк) — chat list, pin/search/archive, chat management, users, AI chats
  - GrpcProfileClient (506 строк) — profile, avatar, contacts, themes, devices
  - GrpcDraftClient (86 строк) — saveDraft, getDraft, deleteDraft
  - GrpcFavoritesClient (121 строка) — addFavorite, removeFavorite, getFavorites
  - GrpcUnaryCallHelper (111 строк) — universal unaryCall helper
- RealGrpcClient теперь orchestrator с 8 модулями
- Все inline gRPC вызовы заменены на делегирование

### Коммиты
- `fbdbbd2` — refactor: modularize RealGrpcClient — extract 5 modules, reduce by 57%

---

## ✅ v1.1.3.25 — Update System восстановление (Сессия 31)

### Update System
- ✅ **UpdateManager интегрирован** — инициализация в setupUI(), наблюдение за StateFlow
- ✅ **Silent update check** — автопроверка при старте + автоскачивание
- ✅ **Manual update check** — кнопка в user menu вызывает checkManualUpdate()
- ✅ **Update dialog** — шторка с текущей/новой версией, кнопка Download/Force download
- ✅ **Update indicator** — llUpdateContainer в toolbar (progress, install, available states)
- ✅ **Progress dialog** — диалог прогресса скачивания с кнопкой отмены
- ✅ **APK install** — через FileProvider после скачивания
- ✅ **Drawable ресурсы** — ic_loading_renew, deployed_code_update_24, ic_checked
- ✅ **onResume/onPause** — регистрация/отписка UpdatePrefs listener

### Коммиты
- TBD — feat: restore update system — UpdateManager integration, silent check, manual check, progress dialog

---

## ✅ v1.1.3.24 — Auth flow fix + Settings Sheet (Сессия 30)

### Auth flow
- ✅ **logout() сохраняет server_address** — после CredentialStore.clear() восстанавливает server_address
- ✅ **showAuthChoiceDialog() с default server** — при пустом serverAddress берёт default из server list
- ✅ **showLoginBottomSheet()** — полная реализация: prefill, login, error handling, recreate
- ✅ **showRegisterBottomSheet()** — полная реализация: register, error handling, recreate
- ✅ **ServerAuthBottomSheet httpPort** — автоопределение HTTP порта по gRPC порту (50051→8082, 50052→8083)
- ✅ **Dismiss listeners** — все шторки перезапускают auth dialog при закрытии без логина
- ✅ **setContentView перед auth dialog** — Activity всегда имеет layout

### Settings Sheet
- ✅ **ProfileBottomSheet.kt удалён** — заменён на showSettingsSheet() в ChatListActivity
- ✅ **bottom_sheet_profile.xml удалён** — заменён на bottom_sheet_user_menu.xml
- ✅ **Клик на аватар** → showSettingsSheet() → bottom_sheet_user_menu.xml (с иконками)
- ✅ **Клик на ⚙️** → showAdditionalSettingsSheet() → bottom_sheet_additional_settings.xml (с иконками)
- ✅ **enableOnBackInvokedCallback="true"** — добавлен в манифест
- ✅ **Дублирующий connect() убран** — только initFromPrefs вызывает connect

### Коммиты
- `462d9f5` — feat: full auth flow with LoginBottomSheet + RegisterBottomSheet
- `2a806bd` — fix: remove duplicate GrpcClient.connect()
- `4b273c5` — fix: setContentView before auth dialog + enableOnBackInvokedCallback
- `f85b1c1` — refactor: remove ProfileBottomSheet, move settings to ChatListActivity

---

## ✅ v1.1.3.23 — Рефакторинг соединений, единый ChatListActivity (Сессия 28)

### Архитектура
- ✅ Удалён ChatListActivity.kt (v1) — 2802 строки мёртвого кода
- ✅ Удалён ChatAdapter.kt (v1)
- ✅ ChatListActivityV2 → ChatListActivity (единый)
- ✅ ChatListViewModelV2 → ChatListViewModel
- ✅ ChatAdapterV2 → ChatAdapter
- ✅ Убран fallbackToV1() — один Activity работает на v1 и v2

### Соединение
- ✅ JWT auth fallback: при JWT ошибке → clear tokens → retry с password
- ✅ getChats retry при shutdownNow (1.5с) вместо emptyList
- ✅ Backup chat stream restart при shutdownNow race condition (2с)
- ✅ Аватар в тулбаре: Glide + avatarCacheFlow
- ✅ Статус соединения: RECONNECTING и FAILED отображаются

---

## ✅ v1.1.3.22 — Rename Lavender → Lava (Сессия 27)
- ✅ Все user-facing строки обновлены (en + ru)

---

## ✅ v1.1.3.21 — FCM Push Notifications (Сессия 26)
- ✅ HIGH priority notifications, DND bypass, online user skip

---

## ✅ v1.1.3.20 — Модуляризация RealGrpcClient (Сессии 23-25)
- ✅ 4 модуля выделены, RealGrpcClient: 4081 → 3739 строк

---

## ✅ v1.1.3.19 — Стабильность (Сессия 22)
- ✅ JWT auth fix, reconnect optimization, DiffUtil, unread badges

---

## ✅ v1.1.3.18 — Стабильность соединения (Сессии 19-21)
- ✅ HTTP /info fix, keepalive, poll interval

---

## ✅ v1.1.3.17 — FAB AI (Сессия 17)
- ✅ AIBottomSheet, AI навигация

---

## ✅ v1.1.3.16 — Selection Mode, Search, Pin Message (Сессии 13-16)
- ✅ Selection Mode, поиск, Pin Message, CacheUtils

---

## 📋 Активные задачи

### Высокий приоритет (v1.1.3.32)
- [x] **ProfileService v2** — проверить работу на dev сервере (ferz подтвердил)
- [x] **Read receipts** — MarkAsRead с broadcast (readReceiptEvent SharedFlow → ChatListViewModel)
- [ ] **FavoritesActivity рефакторинг** — убрать отдельную Activity, использовать navigateToChat с favorites_ prefix

### Средний приоритет
- [ ] ChatListActivity дальнейшее разбиение (FABs, Auth, Navigation → отдельные файлы)

### Отложено
- [ ] NewChatActivity рефакторинг — отложено по решению ферзя
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
| fetchServerInfo strategy | Dev (50052): skip HTTP, assume v2. Prod (50051): try HTTP /info, fallback v1 |
| Optimistic READY | gRPC channel подключается лениво, health check не нужен |
| Unread badge by theme | Badge bg = primary color, text = adaptive (white/black) |
| onCancellation = {} | Обязательно в Kotlin 2.3.21 для cont.resume() |
| ChatListActivityV2 без фрагмента | RecyclerView+SwipeRefresh напрямую в Activity |
| CacheUtils | Единый утилит очистки кэша |
| Keepalive 30s/10s | Для мобильных сетей, меньше разрывов |
| Poll 30s | Уменьшение нагрузки на сервер |
| Gradle wrapper удалён с сервера | OOM protection |
| Settings Sheet вместо ProfileBottomSheet | showSettingsSheet() + showAdditionalSettingsSheet() в ChatListActivity |
| enableOnBackInvokedCallback | Убирает warning в логах на Android 13+ |

---

## 📁 Ключевые файлы

| Файл | Назначение |
|------|-----------|
| `ui/chatlist/ChatListActivity.kt` | ЕДИНЫЙ Activity: tabs, toolbar, FABs, navigation, selection mode, search, AI bottom sheet, settings sheets |
| `ui/chatlist/ChatListViewModel.kt` | ViewModel: loadChats, pinChat, setTabFilter, getChats |
| `ui/chatlist/ChatListSections.kt` | Section enum + SectionItem |
| `ui/adapter/ChatAdapter.kt` | Адаптер с секциями + selection state + DiffUtil |
| `ui/adapter/MessageAdapter.kt` | Адаптер сообщений + pinned badge |
| `ui/widget/AIBottomSheet.kt` | Шторка выбора AI чата (OWL/Hermes) |
| `ui/widget/ServerAuthBottomSheet.kt` | Шторка выбора входа (лого + сервер + статус + login/register) |
| `ui/widget/LoginBottomSheet.kt` | Шторка входа (username/password) |
| `ui/widget/RegisterBottomSheet.kt` | Шторка регистрации |
| `ui/widget/ActionBottomSheet.kt` | Шторка действий (Add Contact, Start Chat, Secret Chat, Conference) |
| `ui/widget/SearchableListBottomSheet.kt` | Шторка с поиском, списком пользователей, кнопкой действия |
| `ui/adapter/UserAdapter.kt` | Адаптер пользователей с выбором + поиск + аватарами |
| `data/cache/CacheUtils.kt` | Единый утилит очистки кэша |
| `data/grpc/GrpcClient.kt` | Facade (pinChat, pinMessage, searchChats, etc.) |
|| `data/grpc/RealGrpcClient.kt` | Оркестратор модулей (~874 строк, цель достигнута) |
| `data/grpc/GrpcMessageClient.kt` | sendMessage/loadHistory/editMessage/deleteMessage/setReaction/markRead (341 строк) |
| `data/grpc/GrpcServerDiscoveryClient.kt` | fetchServersList/fetchServersFromHost/proto parsing (145 строк) |
|| `data/grpc/GrpcConnectionManager.kt` | connect/reconnect/disconnect/keepalive (167 строк) |
|| `data/grpc/GrpcAuthClient.kt` | signInV2/signUpV2/refreshToken/signOut (232 строки) |
|| `data/grpc/GrpcCallClient.kt` | startCallSession/sendCallSignal (124 строки) |
|| `data/grpc/GrpcTypingClient.kt` | startTypingStream/sendTypingSignal (87 строк) |
|| `data/grpc/GrpcChatListClient.kt` | getChats/pinChat/searchChats/archiveChat (638 строк) |
|| `data/grpc/GrpcProfileClient.kt` | profile/avatar/contacts/themes/devices (506 строк) |
|| `data/grpc/GrpcDraftClient.kt` | saveDraft/getDraft/deleteDraft (86 строк) |
|| `data/grpc/GrpcFavoritesClient.kt` | addFavorite/removeFavorite/getFavorites (120 строк) |
|| `data/grpc/GrpcUnaryCallHelper.kt` | универсальный unary call helper (111 строк) |
| `data/grpc/GrpcMarshallers.kt` | все 111 marshaller classes (1394 LOC) |
|| `data/grpc/ProfileClient.kt` | ProfileService v2 client + version detection |
| `data/models/Message.kt` | Message (isPinned), ChatInfo (isPinned, isArchived, pinnedAt), AIChatInfo |
