# Lava Messenger (Android) — Задачи

**Версия:** v1.1.3.27
**Обновлено:** 2026-06-17 (сессия 33)
**Ветка:** feat/1.1.3.x

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

### Высокий приоритет
- [x] **Восстановить функциональность обновлений** — UpdateManager интегрирован, silent check, manual check, progress dialog, APK install
- [x] **Выделить GrpcChatClient** — из оставшихся ~3700 строк RealGrpcClient
  - ✅ GrpcChatListClient (638 LOC) — getChats, pinChat, searchChats, archiveChat
  - ✅ GrpcProfileClient (506 LOC) — profile, avatar, contacts, themes, devices
  - ✅ GrpcDraftClient (86 LOC) — saveDraft, getDraft, deleteDraft
  - ✅ GrpcFavoritesClient (120 LOC) — addFavorite, removeFavorite, getFavorites
  - ✅ GrpcUnaryCallHelper (111 LOC) — universal unary call helper
  - ✅ GrpcMarshallers (1394 LOC) — all 111 marshaller classes
  - ⬜ Осталось в RealGrpcClient: ~1611 строк (chat stream, messages, history, reactions, AI chats, server discovery)

### Средний приоритет
- [ ] **ProfileService v2** — проверить работу на dev сервере
- [ ] **Read receipts** — MarkAsRead

### Отложено
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
| `ui/widget/NewChatBottomSheet.kt` | Шторка создания чата |
| `data/cache/CacheUtils.kt` | Единый утилит очистки кэша |
| `data/grpc/GrpcClient.kt` | Facade (pinChat, pinMessage, searchChats, etc.) |
|| `data/grpc/RealGrpcClient.kt` | Оркестратор модулей (~1611 строк, цель: ~200) |
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
