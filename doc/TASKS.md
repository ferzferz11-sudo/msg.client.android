# Lava Messenger (Android) — Задачи

**Версия:** v1.1.3.24
**Обновлено:** 2026-06-16 (сессия 29)
**Ветка:** feat/1.1.3.x

---

## ✅ v1.1.3.23 — Fix auth flow после logout (Сессия 29)

### Исправлено
- ✅ **logout() сохраняет server_address** — после CredentialStore.clear() восстанавливает server_address, чтобы шторка авторизации знала какой сервер показывать
- ✅ **showAuthChoiceDialog() с default server** — при пустом serverAddress берёт default из server list
- ✅ **showLoginBottomSheet()** — полная реализация с prefill, login, error handling, recreate
- ✅ **showRegisterBottomSheet()** — полная реализация с register, error handling, recreate
- ✅ **ServerAuthBottomSheet httpPort** — автоопределение HTTP порта по gRPC порту (50051→8082, 50052→8083)
- ✅ **Dismiss listeners** — все шторки перезапускают auth dialog при закрытии без логина

### Коммиты
- `462d9f5` — feat: full auth flow with LoginBottomSheet + RegisterBottomSheet in ChatListActivity

---

## ✅ v1.1.3.23 — Рефакторинг соединений, единый ChatListActivity (Сессия 28)

### Архитектура
- ✅ Удалён ChatListActivity.kt (v1) — 2802 строки мёртвого кода
- ✅ Удалён ChatAdapter.kt (v1)
- ✅ ChatListActivityV2 → ChatListActivity (единый)
- ✅ ChatListViewModelV2 → ChatListViewModel
- ✅ ChatAdapterV2 → ChatAdapter
- ✅ Убран fallbackToV1() — один Activity работает на v1 и v2
- ✅ Обновлён SplashActivity — всегда route на ChatListActivity
- ✅ Обновлён AndroidManifest — одна запись ChatListActivity
- ✅ activity_chat_list_v2.xml → activity_chat_list.xml

### Соединение
- ✅ JWT auth fallback: при JWT ошибке → clear tokens → retry с password
- ✅ getChats retry при shutdownNow (1.5с) вместо emptyList
- ✅ Backup chat stream restart при shutdownNow race condition (2с)
- ✅ Аватар в тулбаре: Glide + avatarCacheFlow
- ✅ Статус соединения: RECONNECTING и FAILED отображаются

### Коммиты
- `cde8776` — chore: rename Lavender → Lava
- `33ce3a5` — fix: update share text and descriptions
- `86ecb9f` — fix: getChats retry after shutdownNow
- `01313ae` — fix: force chat stream restart after shutdownNow race
- `1b43a27` — fix: AuthManager.clearTokens null-safety
- `44485a7` — debug: add stack trace logging for forceReconnect
- `383292f` — refactor: merge v1/v2 ChatList into single Activity

---

## ✅ v1.1.3.22 — Rename Lavender → Lava (Сессия 27)

### Android
- ✅ Все значения strings.xml: Lavender → Lava (en), Lavender → Лава (ru)
- ✅ Каналы уведомлений: "Lavender Calls" → "Lava Calls", "Lavender Messages" → "Lava Messages"
- ✅ Тема: "Lavender Night" → "Lava Night" (en)
- ✅ Hardcoded строки в Kotlin заменены на R.string.*:
  - FullScreenImageActivity: "Lavender_*.jpg" → R.string.filename_prefix
  - ShareReceiverActivity: "Lavender Messenger" → R.string.share_app_description
  - VideoPlayerActivity: "Lavender Messenger" → R.string.share_app_description
  - ChatListActivity: "Lavender Messenger Feedback" → R.string.feedback_subject
- ✅ Добавлены новые строки в strings.xml (en + ru): filename_prefix, share_app_description, feedback_subject

### Коммиты
- `cde8776` — chore: rename Lavender → Lava in all user-facing strings (en + ru)
- `33ce3a5` — fix: update share text and descriptions — Lava: secure business communications platform

---

## ✅ v1.1.3.21 — FCM Push Notifications uplevel (Сессия 26)

### Сервер (v1.2.0.2)
- ✅ Hub.IsUserOnline(userId, username) — проверка онлайн-статуса
- ✅ Hub.SetUserId() + clientUserIds map
- ✅ sendPushNotification — skip online + collapse key + TTL
- ✅ GetAllUsers() возвращает UserId
- ✅ server_push_test.go — 7 тестов
- ✅ Исправлена миграция user_chat_metadata

### Android
- ✅ Канал IMPORTANCE_HIGH + PRIORITY_HIGH + CATEGORY_MESSAGE
- ✅ DND bypass switch + channel.setBypassDnd()
- ✅ i18n: push_bypass_dnd + lavender_messages_channel_desc
- ✅ Исправлены ошибки компиляции

### Коммиты
- `8b1dd90` — feat: FCM push — HIGH priority notifications
- `a3bb5b9` — feat: FCM push — DND bypass + online user skip
- `883eef1` — fix: Android compilation errors

---

## ✅ v1.1.3.20 — Модуляризация RealGrpcClient + Cleanup (Сессии 23-25)

### Исправлено
- ✅ **RealGrpcClient разделён на модули**: 4081 → 3739 строк (-342, -8.4%)
- ✅ **GrpcConnectionManager** (167 строк) — connect/reconnect/disconnect/keepalive
- ✅ **GrpcAuthClient** (232 строки) — signInV2/signUpV2/refreshToken/signOut/revokeDevice
- ✅ **GrpcCallClient** (124 строки) — startCallSession/sendCallSignal
- ✅ **GrpcTypingClient** (87 строк) — startTypingStream/sendTypingSignal
- ✅ **ChatListFragmentV2** (144 строки) — удалён мёртвый код
- ✅ **changelog_bundled.txt** — удалён, ChangelogActivity упрощён
- ✅ **Дублирование currentServerAddress/currentServerPort** — исправлено
- ✅ **Gradle wrapper** — удалён с сервера, восстановлен в git для локальной сборки

### Коммиты
- `c413038` — feat: modularize RealGrpcClient
- `208141c` — fix: resolve compilation errors from modularization
- `389aa02` — chore: remove changelog_bundled.txt and all usage in code
- `30e1fac` — chore: restore Gradle wrapper for local builds
- `e22ec93` — fix: restore missing Dispatchers and withContext imports

### Тег
- ✅ `v1.1.3.20` — выпущен (релиз отложен до стабильности)

---

## ✅ v1.1.3.19 — Стабильность и оптимизация (Сессия 22)

- ✅ JWT auth fix — getAccessToken() вместо getBearerToken()
- ✅ Reconnect stability — единый источник onError, auth failure detection
- ✅ DiffUtil — ChatAdapterV2 использует DiffUtil
- ✅ Unread badges — цвета по теме, mark-as-read, реал-тайм обновление

---

## ✅ v1.1.3.18 — Стабильность соединения (Сессии 19-21)

- ✅ HTTP /info fix — dev сервер определяется мгновенно
- ✅ Keepalive 30s/10s, idleTimeout 25min
- ✅ Баг загрузки чатов — убран двойной loadChats, cache-first
- ✅ Poll interval 5s → 30s

---

## ✅ v1.1.3.17 — FAB AI (Сессия 17)

- ✅ AIBottomSheet, создание AI чатов, настройки

---

## ✅ v1.1.3.16 — Selection Mode, Search, Pin Message, CacheUtils (Сессии 13-16)

- ✅ Selection Mode, поиск, Pin Message, CacheUtils, ServersActivity

---

## ✅ v1.1.3.15 — Стабильная v1 (prod)

---

## 📋 Активные задачи

### Высокий приоритет
- [ ] **Выделить GrpcChatClient** — из оставшихся ~3700 строк RealGrpcClient
  - Методы: getChats, sendMessage, loadHistory, pinChat, searchChats, archiveChat, draft, favorites, reactions, profile, chat management
  - ~2000 строк — самый большой оставшийся кусок
- [ ] **Выпуск тега v1.1.3.21** — после локальной сборки APK

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
| Pin Message — selection toolbar | Кнопка pin/unpin в selection toolbar (v1-style) |
| fetchServerInfo strategy | Dev (50052): skip HTTP, assume v2. Prod (50051): try HTTP /info, fallback v1 |
| Optimistic READY | gRPC channel подключается лениво, health check не нужен |
| Unread badge by theme | Badge bg = primary color, text = adaptive (white/black) |
| newMessageEvent | SharedFlow<Pair<roomId, messageId>> for real-time unread increment |
| onCancellation = {} | Обязательно в Kotlin 2.3.21 для cont.resume() |
| ChatListActivityV2 без фрагмента | RecyclerView+SwipeRefresh напрямую в Activity |
| CacheUtils | Единый утилит очистки кэша |
| HermesSettings → OwlSettingsActivity | Переиспользование с isHermes=true |
| AIChatInfo минимальная | Только id, name, type |
| Keepalive 30s/10s | Для мобильных сетей, меньше разрывов |
| Poll 30s | Уменьшение нагрузки на сервер |
| Gradle wrapper удалён с сервера | OOM protection — нельзя случайно запустить ./gradlew на сервере |

---

## 📁 Ключевые файлы

| Файл | Назначение |
|------|-----------|
| `ui/chatlist/ChatListActivityV2.kt` | v2 Activity: tabs, toolbar, FABs, navigation, selection mode, search, AI bottom sheet |
| `ui/chatlist/ChatAdapterV2.kt` | v2 адаптер с секциями + selection state + DiffUtil |
| `ui/chatlist/ChatListViewModelV2.kt` | v2 ViewModel: loadChats, pinChat, setTabFilter, getChats |
| `ui/chatlist/ChatListSections.kt` | Section enum + SectionItem |
| `ui/adapter/MessageAdapter.kt` | Адаптер сообщений + pinned badge |
| `ui/widget/AIBottomSheet.kt` | Шторка выбора AI чата (OWL/Hermes) |
| `data/cache/CacheUtils.kt` | Единый утилит очистки кэша |
| `data/grpc/GrpcClient.kt` | Facade (pinChat, pinMessage, searchChats, etc.) |
| `data/grpc/RealGrpcClient.kt` | Оркестратор модулей (~3700 строк, цель: ~200) |
| `data/grpc/GrpcConnectionManager.kt` | connect/reconnect/disconnect/keepalive (167 строк) |
| `data/grpc/GrpcAuthClient.kt` | signInV2/signUpV2/refreshToken/signOut (232 строки) |
| `data/grpc/GrpcCallClient.kt` | startCallSession/sendCallSignal (124 строки) |
| `data/grpc/GrpcTypingClient.kt` | startTypingStream/sendTypingSignal (87 строк) |
| `data/grpc/ProfileClient.kt` | ProfileService v2 client + version detection |
| `data/models/Message.kt` | Message (isPinned), ChatInfo (isPinned, isArchived, pinnedAt), AIChatInfo |
