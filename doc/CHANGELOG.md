# Lava Messenger — Android CHANGELOG

## v1.1.3.24 (2026-06-16) — Auth flow fix + Settings Sheet

### Auth flow
- **logout() сохраняет server_address** — после CredentialStore.clear() восстанавливает адрес сервера
- **showAuthChoiceDialog()** — при пустом serverAddress берёт default server из server list
- **showLoginBottomSheet()** — полная реализация: prefill username, login, error handling, recreate
- **showRegisterBottomSheet()** — полная реализация: register, error handling, recreate
- **ServerAuthBottomSheet httpPort** — автоопределение HTTP порта по gRPC порту (50051→8082, 50052→8083)
- **Dismiss listeners** — все шторки перезапускают auth dialog при закрытии без логина
- **setContentView перед auth dialog** — Activity всегда имеет layout

### Settings Sheet
- **ProfileBottomSheet.kt удалён** — заменён на showSettingsSheet() в ChatListActivity
- **bottom_sheet_profile.xml удалён** — заменён на bottom_sheet_user_menu.xml
- **Клик на аватар** → showSettingsSheet() → bottom_sheet_user_menu.xml (с иконками)
- **Клик на ⚙️** → showAdditionalSettingsSheet() → bottom_sheet_additional_settings.xml (с иконками)
- **enableOnBackInvokedCallback="true"** — добавлен в манифест
- **Дублирующий connect() убран** — только initFromPrefs вызывает connect

### Коммиты
- `462d9f5` — feat: full auth flow with LoginBottomSheet + RegisterBottomSheet
- `2a806bd` — fix: remove duplicate GrpcClient.connect()
- `4b273c5` — fix: setContentView before auth dialog + enableOnBackInvokedCallback
- `f85b1c1` — refactor: remove ProfileBottomSheet, move settings to ChatListActivity

---

## v1.1.3.23 (2026-06-16) — Рефакторинг соединений, единый ChatListActivity

### Архитектура
- **Единый ChatListActivity** — v1/v2 объединены, один Activity работает на обоих серверах
- **Удалён ChatListActivity (v1)** — 2802 строки мёртвого кода
- **Удалён ChatAdapter (v1)**

### Соединение
- **JWT auth fallback** — при JWT ошибке → clear tokens → retry с password
- **getChats retry** — при shutdownNow через 1.5с вместо emptyList
- **Backup chat restart** — при shutdownNow race condition через 2с
- **Аватар в тулбаре** — Glide + avatarCacheFlow
- **Статус соединения** — RECONNECTING и FAILED отображаются

### Коммиты
- `383292f` — refactor: merge v1/v2 ChatList into single Activity
- `86ecb9f` — fix: getChats retry after shutdownNow
- `01313ae` — fix: force chat stream restart after shutdownNow race
- `1b43a27` — fix: AuthManager.clearTokens null-safety

---

## v1.1.3.22 (2026-06-16) — Rename Lavender → Lava

### Изменения
- Все значения strings.xml: Lavender → Lava (en), Lavender → Лава (ru)
- Каналы уведомлений: "Lavender Calls/Messages" → "Lava Calls/Messages"
- Тема: "Lavender Night" → "Lava Night" (en)
- 4 hardcoded строки в Kotlin заменены на R.string.*

### Коммиты
- `cde8776` — chore: rename Lavender → Lava in all user-facing strings (en + ru)
- `33ce3a5` — fix: update share text and descriptions

---

## v1.1.3.21 (2026-06-16) — FCM Push Notifications

### Сервер (v1.2.0.2)
- Hub.IsUserOnline(userId, username) — проверка онлайн-статуса
- sendPushNotification — skip online + collapse key + TTL
- server_push_test.go — 7 тестов

### Android
- Канал IMPORTANCE_HIGH + PRIORITY_HIGH + CATEGORY_MESSAGE
- DND bypass switch + channel.setBypassDnd()
- i18n: push_bypass_dnd + lavender_messages_channel_desc

### Коммиты
- `8b1dd90` — feat: FCM push — HIGH priority notifications
- `a3bb5b9` — feat: FCM push — DND bypass + online user skip
- `883eef1` — fix: Android compilation errors

---

## v1.1.3.20 (2026-06-16) — Модуляризация RealGrpcClient + Cleanup

### Рефакторинг
- **RealGrpcClient разделён на модули**: 4081 → 3739 строк (-342, -8.4%)
- **GrpcConnectionManager** (167 строк) — connect/reconnect/disconnect/keepalive
- **GrpcAuthClient** (232 строки) — signInV2/signUpV2/refreshToken/signOut/revokeDevice
- **GrpcCallClient** (124 строки) — startCallSession/sendCallSignal
- **GrpcTypingClient** (87 строк) — startTypingStream/sendTypingSignal

### Удаление мёртвого кода
- **ChatListFragmentV2** (144 строки) + fragment_chat_list_v2.xml — удалены
- **changelog_bundled.txt** — удалён

### Тег
- `v1.1.3.20` — выпущен (релиз отложен до стабильности)

---

## v1.1.3.19 (2026-06-16) — Стабильность и оптимизация

- JWT auth fix — getAccessToken() вместо getBearerToken()
- Reconnect stability — единый источник onError
- DiffUtil — ChatAdapterV2 использует DiffUtil
- Unread badges — цвета по теме, mark-as-read, реал-тайм обновление

---

## v1.1.3.18 (2026-06-15) — Стабильность соединения

- HTTP /info fix — dev сервер определяется мгновенно
- Keepalive 30s/10s, idleTimeout 25min
- Баг загрузки чатов — убран двойной loadChats
- Poll interval 5s → 30s

---

## v1.1.3.17 (2026-06-15) — FAB AI

- AIBottomSheet подключён к ChatListActivity
- AI навигация — Hermes/OWL чаты

---

## v1.1.3.16 (2026-06-16) — Selection Mode, Search, Pin Message, CacheUtils

- Selection Mode — long press → ActionMode toolbar
- Поиск — SearchView в toolbar + debounce 300ms
- Pin Message — selection toolbar
- CacheUtils — единый утилит очистки кэша

---

## v1.1.3.15 и ранее — Стабильная v1 (prod)

### Рефакторинг
- **RealGrpcClient разделён на модули**: 4081 → 3739 строк (-342, -8.4%)
- **GrpcConnectionManager** (167 строк) — connect/reconnect/disconnect/keepalive
- **GrpcAuthClient** (232 строки) — signInV2/signUpV2/refreshToken/signOut/revokeDevice
- **GrpcCallClient** (124 строки) — startCallSession/sendCallSignal
- **GrpcTypingClient** (87 строк) — startTypingStream/sendTypingSignal

### Удаление мёртвого кода
- **ChatListFragmentV2** (144 строки) + fragment_chat_list_v2.xml — удалены
- **changelog_bundled.txt** — удалён (GitHub API грузится стабильно)
- **ChangelogActivity.kt** — упрощён: убран bundled fallback, server fallback, delay(3000)

### Инфраструктура
- Gradle wrapper удалён с сервера, восстановлен в git для локальной сборки
- gradle.properties: 512m/256m сервер, 2048m локально

### Коммиты
- `c413038` — feat: modularize RealGrpcClient — extract 4 modules, remove dead code
- `208141c` — fix: resolve compilation errors from modularization
- `6207fc3` — chore: revert gradle.properties — remove experimental KSP/AGP flags
- `f60478c` — chore: revert gradle memory to 512m/256m (server-safe)
- `389aa02` — chore: remove changelog_bundled.txt and all usage in code
- `30e1fac` — chore: restore Gradle wrapper for local builds
- `e22ec93` — fix: restore missing Dispatchers and withContext imports

### Тег
- `v1.1.3.20` — выпущен (релиз отложен до стабильности)

---

## v1.1.3.19 (2026-06-16) — Стабильность и оптимизация

### Исправления
- **JWT auth** — getAccessToken() вместо getBearerToken() для ChatStream
- **Auth failure reconnect loop** — проверка "authentication failed" → FAILED без retry
- **Дублированный reconnect** — onClose/getChats больше не вызывают reconnect, единственный источник onError
- **DiffUtil** — ChatAdapterV2 использует DiffUtil вместо notifyDataSetChanged
- **HTTP /info fix** — dev сервер определяется мгновенно без HTTP запроса

### Новое
- **Unread badges** — цвета по теме, mark-as-read при клике, реал-тайм обновление через SharedFlow
- **ARCH_ANALYSIS_V2_V1.md** — полный анализ архитектуры v2 vs v1

### Коммиты
- `9726929` — fix: JWT auth and infinite reconnect on auth failure
- `63ed73f` — fix: eliminate duplicate reconnect logic
- `959a79f` — feat: add DiffUtil to ChatAdapterV2
- `e029aa7` — feat: unread badges — theme colors, mark-as-read, real-time update
- `f45bdd3` — fix: skip HTTP /info for dev server (port 50052)
- `583bf3f` — docs: add ARCH_ANALYSIS_V2_V1.md

---

## v1.1.3.18 (2026-06-15) — Стабильность соединения

### Исправления
- **HTTP /info недоступен на dev** — fallback по gRPC порту (50052→v2, 50051→v1)
- **Keepalive failures** — таймауты увеличены (30s/10s), idleTimeout 25min
- **Множественные reconnect** — подавлен reconnect при shutdownNow
- **Hardcoded порт в reconnect** — сохранение currentServerPort
- **Poll interval** — увеличен 5s → 30s
- **Баг загрузки чатов** — убран двойной loadChats, cache-first, добавлен reconnect

### Коммиты
- `52065b1` — fix: remove HTTP health check, use optimistic READY
- `85a99de` — fix: optimize connection stability
- `09c1acd` — fix: correct idleTimeout, suppress shutdownNow reconnect
- `ff90513` — fix: gRPC port heuristic for version detection
- `3f808bf` — fix: resolve chat loading race condition
- `8047309` — chore: cleanup debug logs

---

## v1.1.3.17 (2026-06-15) — FAB AI

### Новое
- **FAB AI** — AIBottomSheet подключён к ChatListActivityV2
- **AI навигация** — Hermes/OWL чаты создаются с пустым chatId → сервер создаёт
- **Настройки** — Hermes → OwlSettingsActivity (isHermes=true), OWL → OwlSettingsActivity

### Коммиты
- `58f7115` — feat: FAB AI — AIBottomSheet integration in ChatListActivityV2
- `1d989f1` — chore: protoc regeneration + docs update

---

## v1.1.3.16 (2026-06-16) — Selection Mode, Search, Pin Message, CacheUtils

### Новое
- **Selection Mode** — long press → ActionMode toolbar (Pin/Mute/Archive/Delete)
- **Поиск** — SearchView в toolbar + debounce 300ms
- **Pin Message** — selection toolbar (кнопка pin/unpin), pinned badge в MessageAdapter
- **CacheUtils** — единый утилит очистки кэша (clearAllSync, clearAllWithGlide)
- **Cache очистка** — синхронная очистка БД при входе (без Toast)

### Коммиты
- `da0c3ae` — refactor: Pin Message via selection toolbar (v1-style)
- `7973b83` — fix: CacheUtils
- `9929b32` — feat: clear local cache silently on successful login
- `ed40305` — refactor: extract CacheUtils

---

## v1.1.3.15 и ранее — Стабильная v1 (prod)

- ChatListActivity (v1) — базовая функциональность
- gRPC клиент с password auth
- Поддержка prod сервера (v1.1.3.10)
