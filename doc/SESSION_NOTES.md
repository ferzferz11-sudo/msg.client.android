# Lavender Messenger — Android Session Notes

## Сессия 19 (2026-06-16) — Фаза 1: Исправление бага загрузки чатов

### Корневые причины бага
1. `RealGrpcClient.connect()` ставил READY сразу после `builder.build()`, до установления TCP
2. `ChatListActivityV2.setupRecyclerView()` вызывал `loadChats()` дублируя `ViewModel.init`
3. Cache-first логика в `getChats()` вызывала `callback(emptyList())` при пустом кэше

### Исправления
- `RealGrpcClient.connect()`: CONNECTING → health check `/health` → READY
- `RealGrpcClient.getChats()`: убрана cache-first логига; callback всегда вызывается
- `ChatListActivityV2`: убран двойной `loadChats()`; добавлен `onResume()` safety net
- `ChatListActivity` (v1): добавлен `onResume()` safety net
- Коммит: `3f808bf`

### Коммиты
- `3f808bf` — fix: resolve chat loading race condition (Phase 1)

---

## Сессия 18 (2026-06-15) — Подготовка к v1.1.3.18+ / v1.2.0.2+

### Контекст
- Продолжаем работу над Android 1.1.3.17+ и Server 1.2.0.1+
- Обе версии с обратной совместимостью v1 сервера
- Полное разделение v1 и v2 архитектуры
- v1 сервер (prod) → ChatListActivity (v1)
- v2 сервер (dev) → ChatListActivityV2 (v2)

### Что сделано
- Обновлена документация: PROMPT_ANDROID.md, TASKS.md, INDEX.md, PLAN_CHATLIST_V2.md
- Обновлён статус релизов: v1.1.3.17 выпущен, v1.1.3.18+ в разработке
- Определены следующие приоритеты: тестирование, доработки, новые фичи

### Сервер
- v1.2.0.1 работает на dev (порт 50052/8083)
- v1.1.3.10 работана prod (порт 50051/8082)
- Оба сервера активны и работают

### Следующие шаги
1. Тестирование на dev и prod серверах
2. Доработки HermesChatActivity / OwlChatActivity
3. Новые фичи для v1.1.3.18+

---

## Сессия 17 (2026-06-15) — FAB AI + интеграция

### Сервер
- protoc генерация после добавления PinMessage в messenger.proto
- Деплой на dev сервер (v1.2.0.1 с PinMessage RPC)

### FAB AI (ChatListActivityV2)
- AIBottomSheet подключён к FAB AI (вместо TODO)
- Создание Hermes чата → HermesChatActivity с пустым chatId → createSession на сервере
- Создание OWL чата → OwlChatActivity с пустым chatId → createOwlChat на сервере
- Существующие AI чаты (hermes/owl) отображаются в AIBottomSheet
- Удаление AI чатов через контекстное меню
- Настройки: Hermes → OwlSettingsActivity (isHermes=true), OWL → OwlSettingsActivity

### ChatListViewModelV2
- Добавлен публичный метод `getChats(): List<ChatInfo>`

### Версия
- Android: v1.1.3.16 → v1.1.3.17
- Сервер: v1.2.0.1 (без изменений)

### Коммиты сессии 17
- `58f7115` — feat: FAB AI — AIBottomSheet integration in ChatListActivityV2
- `1d989f1` — chore: protoc regeneration + docs update for v1.1.3.17

---

## Сессия 16 (2026-06-16) — Завершение Pin Message + рефакторинг

### Рефакторинг Pin Message → v1-style selection
- **Убран PopupMenu context menu** — long press сразу входит в selection mode (как в v1)
- **Кнопка Pin** в selection toolbar (видна при выборе 1 сообщения)
- `pinSelectedMessages()` использует `GrpcClient.pinMessage/unpinMessage` через `lifecycleScope`
- `loadPinnedMessages()` обновляет и локальный `pinnedMessageIds`, и адаптер
- Вызов `loadPinnedMessages()` в `onResume` и при подключении
- Удалён `message_context_menu.xml` (больше не нужен)

### CacheUtils — единый утилитный метод очистки кэша
- Создан `CacheUtils` object с `clearAllSync()` и `clearAllWithGlide()`
- `clearAllSync(context)` — синхронная очистка БД (messages, chats) — используется при входе
- `clearAllWithGlide(context)` — полная очистка + Glide — из настроек
- Заменены все дублированные `clearAllCache()` в Activity на `CacheUtils.clearAllSync()`
- `clearLocalCache()` в ChatListActivity теперь использует `CacheUtils.clearAllWithGlide()`
- Очистка кэша при входе — без Toast (silent)

### Коммиты сессии 16
- `da0c3ae` — refactor: Pin Message via selection toolbar (v1-style)
- `7973b83` — fix: CacheUtils — remove userDao, fix context type
- `9929b32` — feat: clear local cache silently on successful login
- `ed40305` — refactor: extract CacheUtils

---

## Сессия 15 (2026-06-16) — Pin Message + ServersActivity

### Pin Message (сервер)
- messenger.proto: PinMessageRequest/Response, UnPinMessageRequest/Response, GetPinnedMessagesRequest/Response
- db_chatlist_v2.go: pinned_messages table, PinnedMessageRow, CRUD методы
- server_chatlist_v2.go: PinMessage/UnPinMessage/GetPinnedMessages RPC handlers
- Все RPC используют только userId (без username)

### Pin Message (клиент)
- MessengerProto.kt: PinMessageRequestProto, PinMessageResponseProto, etc.
- RealGrpcClient: pinMessage(), unpinMessage(), getPinnedMessages()
- GrpcClient: facade с v1/v2 version check
- Message.kt: isPinned field
- MessageAdapter: pinnedMessageIds, updatePinnedMessages(), pinned badge
- NewChatActivity: showMessageContextMenu() (упрощено в сессии 16)
- item_message.xml: pinned badge layout
- pinned_message, pin_message, unpin_message строки (en + ru)

### ServersActivity improvements
- Prefill последнего логина в login bottom sheet
- Splash после успешного входа/регистрации (showSplashAndFinish)
- Все серверы (включая dev) доступны всем пользователям
- CredentialStore: getLastUsername/setLastUsername

### Коммиты сессии 15
- `7301de3` — Pin Message server
- `e05da8d` — Pin Message Android client
- `ec530ff` — Pin Message UI
- `b367998` — ServersActivity improvements

---

## Сессия 14 (2026-06-16) — Selection Mode + Search

### Selection Mode (множественный выбор чатов)
- ChatAdapterV2: selection state, CheckBox, визуальная подсветка
- ChatListActivityV2: ActionMode.Callback, массовые действия
- onBackPressed → выход из selection mode (OnBackPressedDispatcher)

### Поиск чатов
- SearchView в toolbar через inflateMenu
- Debounce 300ms через coroutine Job
- Локальная фильтрация allChats (работает на v1 и v2)

### Коммиты сессии 14
- `4ddc712` — feat: Selection Mode + Search
- `0256dab` — fix: replace deprecated onBackPressed

---

## Сессия 13 (2026-06-16) — ChatList v2 UI

### ChatListActivityV2 — полная реализация
- RecyclerView+SwipeRefresh напрямую в Activity (без фрагмента)
- TabLayout с табами All/AI/Groups
- Toolbar: avatar→ProfileActivity, title→ServersActivity
- FABs: fabAi, fabAddChat
- Connection status subtitle
- SplashActivity маршрутизация v1/v2

### Коммиты сессии 13
- `bd4e22c` — feat: ChatListActivityV2 full implementation

---

## Теги
- Android: `v1.1.3.17`
- Server: `v1.2.0.1`

## Следующие шаги (сессия 18)
1. **Тестирование v1.1.3.17** на dev и prod серверах
2. **Исправление багов** по результатам тестирования
3. **Unread badges** — улучшение счётчика непрочитанных
4. **Push notifications** — FCM интеграция для v2
