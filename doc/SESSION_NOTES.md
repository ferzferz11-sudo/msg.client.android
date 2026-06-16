# Lavender Messenger — Android Session Notes

## Сессия 23 (2026-06-16) — Модуляризация RealGrpcClient

### Контекст
- Продолжаем работу над v1.1.3.20
- Цель: разделить RealGrpcClient на модули

### Что сделано
- Извлечены 4 модуля из RealGrpcClient:
  - GrpcConnectionManager (167 строк) — connect/reconnect/disconnect/keepalive
  - GrpcAuthClient (232 строки) — signInV2/signUpV2/refreshToken/signOut/revokeDevice
  - GrpcCallClient (124 строки) — startCallSession/sendCallSignal
  - GrpcTypingClient (87 строк) — startTypingStream/sendTypingSignal
- Удалён мёртвый код: ChatListFragmentV2 (144 строки), fragment_chat_list_v2.xml
- Исправлено дублирование currentServerAddress/currentServerPort
- Итоговая статистика:
  - RealGrpcClient: 4081 → 3739 строк (-342, -8.4%)
  - Новые файлы: 4 модуля (610 строк суммарно)
  - Удалено: ChatListFragmentV2 + layout (144 строки)

### Коммиты
- `TBD` — feat: modularize RealGrpcClient — extract 4 modules, remove dead code

### Следующие шаги
- Выделить GrpcChatClient из оставшихся ~3700 строк RealGrpcClient
- Методы: getChats, sendMessage, loadHistory, pinChat, searchChats, archiveChat, draft, favorites, reactions, profile, chat management

---

## Сессия 22 (2026-06-16) — JWT auth fix, reconnect optimization, architecture analysis

### Контекст
- Продолжаем работу над v1.1.3.19
- Тестирование v2 на dev сервере

### Проблемы и исправления

#### 1. JWT token malformed
- **Симптом**: `token is malformed: could not base64 decode header: illegal base64 data at input byte 6`
- **Причина**: `getBearerToken()` возвращал `"Bearer <token>"` с префиксом, а `setJwtToken()` ожидал чистый токен
- **Решение**: Используем `getAccessToken()` вместо `getBearerToken()` для ChatStream JWT auth
- **Коммит**: 9726929

#### 2. Бесконечный reconnect loop при auth failure
- **Симптом**: `UNKNOWN - authentication failed` → бесконечный retry loop
- **Причина**: `UNKNOWN` код ошибки не ловился как auth error в onError
- **Решение**: Добавлена проверка `description.contains("authentication failed")` → FAILED status, без retry
- **Коммит**: 9726929

#### 3. Дублированный reconnect logic (3 источника)
- **Симптом**: onClose, onError, getChats() onClose — все вызывали reconnect независимо
- **Решение**:
  - onClose больше не вызывает scheduleReconnect, только делегирует в onError
  - getChats() onClose не трогает connection status
  - onError — единственный источник reconnect с isRetrying guard
- **Коммит**: 63ed73f

#### 4. DiffUtil в ChatAdapterV2
- **Симптом**: notifyDataSetChanged вызывал мерцание списка
- **Решение**: Заменён на DiffUtil.calculateDiff() + dispatchUpdatesTo()
- **Коммит**: 959a79f

#### 5. Unread badges
- **Симптом**: Бейдж не стилизовался по теме, не обновлялся в реальном времени
- **Решение**:
  - Цвет бейджа = primary color темы, текст адаптивный
  - MarkAsRead при клике на чат
  - newMessageEvent SharedFlow для реал-тайм обновления
- **Коммит**: e029aa7

### Документация
- Создан `doc/ARCH_ANALYSIS_V2_V1.md` — полный анализ архитектуры v2 vs v1
- Метрики: v1 ChatListActivity 2802 строки, v2 ChatListActivityV2 664 строки
- RealGrpcClient: 4070 строк, 471 метод — главная проблема архитектуры

### Коммиты
- `9726929` — fix: JWT auth and infinite reconnect on auth failure
- `63ed73f` — fix: eliminate duplicate reconnect logic
- `959a79f` — feat: add DiffUtil to ChatAdapterV2
- `e029aa7` — feat: unread badges — theme colors, mark-as-read, real-time update
- `583bf3f` — docs: add ARCH_ANALYSIS_V2_V1.md

### Тестирование
- ✅ dev (v2): JWT auth работает, чаты загружаются, AI чат работает
- ✅ Нет бесконечного reconnect loop
- ✅ Логи чистые

---

## Сессия 21 (2026-06-16) — Fix HTTP /info для dev сервера

### Проблема
- Dev сервер (порт 50052) недоступен по HTTP (NAT/firewall), fetchServerInfo падал с 5с таймаутом
- В логе появлялся warning "Failed to fetch /info" хотя fallback работал
- Корневая причина: для dev сервера вообще нет смысла пытаться HTTP /info

### Исправление
- Если grpcPort == 50052 (dev) — сразу ставим v2 версии без HTTP запроса
- Если grpcPort == 50051 (prod) — пробуем HTTP /info, при неудаче v1 fallback
- Убран warning лог, вместо него debug-лог для prod fallback
- Коммит: f45bdd3

### Результат
- Dev сервер: нет HTTP таймаута, нет warning в логе, v2 определяется мгновенно
- Prod сервер: поведение не изменилось

---

## Сессия 20 (2026-06-16) — Оптимизация соединения и стабильность

### Контекст
- Продолжаем работу над Android v1.1.3.18
- Баг загрузки чатов из сессии 19 исправлен, но обнаружены новые проблемы

### Проблемы и исправления

#### 1. HTTP /info недоступен на dev сервере (NAT/firewall)
- **Симптом**: `fetchServerInfo` падал с `failed to connect to /13.140.25.249 (port 8083)`
- **Причина**: Эмулятор в локальной сети не может достучаться до публичного IP на порту 8083
- **Решение**: Добавлен fallback — если HTTP /info недоступен, используется heuristic по gRPC порту:
  - `grpcPort == 50052` → dev сервер → v2 по умолчанию
  - `grpcPort == 50051` → prod сервер → v1 fallback
- **Результат**: dev сервер определяется как v2 даже при недоступном HTTP

#### 2. Keepalive failures
- **Симптом**: `UNAVAILABLE: Keepalive failed. The connection is likely gone`
- **Причина**: Слишком агрессивные таймауты keepalive (10s/5s)
- **Решение**: Увеличены таймауты:
  - `keepAliveTime`: 10s → 30s
  - `keepAliveTimeout`: 5s → 10s
  - Добавлен `idleTimeout(25min)` для переподключения до MaxConnectionAge сервера

#### 3. Множественные reconnect при shutdownNow
- **Симптом**: `Channel shutdownNow invoked` при каждом reconnect
- **Причина**: `getChats()` onClose вызывал reconnect даже при shutdownNow (наш собственный reconnect)
- **Решение**: Проверка `status.description.contains("shutdownNow")` — если наш shutdown, reconnect не нужен

#### 4. Hardcoded порт 50052 в reconnect
- **Симптом**: `scheduleReconnect(addr, false, 50052, appContext)` — всегда dev порт
- **Решение**: Сохраняем `currentServerPort` в `connect()`, используем в reconnect

#### 5. Poll interval слишком частый
- **Симптом**: `getChats` вызывался каждые 5 секунд
- **Решение**: Увеличен до 30 секунд

### Коммиты
- `52065b1` — fix: remove HTTP health check, use optimistic READY + reconnect on failure
- `85a99de` — fix: optimize connection stability (keepalive, port, poll interval)
- `09c1acd` — fix: correct idleTimeout method name, suppress shutdownNow reconnect
- `ff90513` — fix: use gRPC port heuristic for version detection
- `8047309` — chore: cleanup debug logs
- `9be6d9e` — chore: remove debug logs from Phase 1 fix

### Тестирование
- ✅ dev (v2): чаты загружаются, profile=2.0, AI чат работает
- ✅ prod (v1): чаты загружаются, profile=пусто (v1 fallback)
- ✅ Keepalive failures уменьшились
- ✅ Нет множественных reconnect

---

## Сессия 19 (2026-06-16) — Фаза 1: Исправление бага загрузки чатов

### Корневые причины бага
1. `RealGrpcClient.connect()` ставил READY сразу после `builder.build()`, до установления TCP
2. `ChatListActivityV2.setupRecyclerView()` вызывал `loadChats()` дублируя `ViewModel.init`
3. Cache-first логика в `getChats()` вызывала `callback(emptyList())` при пустом кэше

### Исправления
- `RealGrpcClient.connect()`: убран HTTP health check, используется optimistic READY
- `RealGrpcClient.getChats()`: убрана cache-first логика; callback всегда вызывается
- `ChatListActivityV2`: убран двойной `loadChats()`; добавлен `onResume()` safety net
- `ChatListActivity` (v1): добавлен `onResume()` safety net
- Добавлен reconnect при transport errors (UNAVAILABLE, UNAUTHENTICATED, INTERNAL)

### Коммиты
- `3f808bf` — fix: resolve chat loading race condition (Phase 1)
- `f2ec6a4` — chore: update docs for v1.1.3.18

---

## Сессия 18 (2026-06-15) — Подготовка к v1.1.3.18+ / v1.2.0.2+

### Контекст
- Продолжаем работу над Android 1.1.3.17+ и Server 1.2.0.1+
- Обе версии с обратной совместимостью v1 сервера
- Полное разделение v1 и v2 архитектуры

### Что сделано
- Обновлена документация: PROMPT_ANDROID.md, TASKS.md, INDEX.md
- Определены следующие приоритеты: тестирование, доработки, новые фичи

### Сервер
- v1.2.0.1 работает на dev (порт 50052/8083)
- v1.1.3.10 работает на prod (порт 50051/8082)

---

## Сессия 17 (2026-06-15) — FAB AI + интеграция

### FAB AI (ChatListActivityV2)
- AIBottomSheet подключён к FAB AI
- Создание Hermes/OWL чата → пустой chatId → сервер создаёт
- Существующие AI чаты отображаются в AIBottomSheet
- Удаление AI чатов через контекстное меню
- Настройки: Hermes → OwlSettingsActivity (isHermes=true), OWL → OwlSettingsActivity

### Коммиты
- `58f7115` — feat: FAB AI — AIBottomSheet integration in ChatListActivityV2
- `1d989f1` — chore: protoc regeneration + docs update

---

## Сессия 16 (2026-06-16) — Завершение Pin Message + рефакторинг

### Рефакторинг Pin Message → v1-style selection
- Убран PopupMenu context menu — long press сразу входит в selection mode
- Кнопка Pin в selection toolbar
- loadPinnedMessages() обновляет и локальный pinnedMessageIds, и адаптер

### CacheUtils — единый утилитный метод очистки кэша
- clearAllSync(context) — синхронная очистка БД при входе (без Toast)
- clearAllWithGlide(context) — полная очистка + Glide из настроек

### Коммиты
- `da0c3ae` — refactor: Pin Message via selection toolbar (v1-style)
- `7973b83` — fix: CacheUtils
- `9929b32` — feat: clear local cache silently on successful login
- `ed40305` — refactor: extract CacheUtils

---

## Сессия 15 (2026-06-16) — Pin Message + ServersActivity

### Pin Message
- messenger.proto: PinMessage/UnPinMessage/GetPinnedMessages RPC
- db_chatlist_v2.go: pinned_messages table, CRUD методы
- server_chatlist_v2.go: RPC handlers
- MessageAdapter: pinned badge

### ServersActivity improvements
- Prefill последнего логина
- Splash после успешного входа

---

## Сессия 14 (2026-06-16) — Selection Mode + Search

### Selection Mode
- Long press → ActionMode toolbar
- Тап в режиме выбора → toggle selection
- OnBackPressedDispatcher для выхода

### Поиск
- SearchView в toolbar + debounce 300ms

---

## Сессия 13 (2026-06-16) — ChatList v2 UI

### ChatListActivityV2 — полная реализация
- RecyclerView+SwipeRefresh напрямую в Activity (без фрагмента)
- TabLayout с табами All/AI/Groups
- Toolbar: avatar→ProfileActivity, title→ServersActivity
- FABs: fabAi, fabAddChat
- Connection status subtitle
- SplashActivity маршрутизация v1/v2

---

## Теги
- Android: `v1.1.3.20`
- Server dev: `v1.2.0.1`
- Server prod: `v1.1.3.10`

## Следующие шаги (сессия 21)
1. **Unread badges** — улучшение счётчика непрочитанных
2. **Push notifications** — FCM интеграция
3. **ProfileService v2** — проверить что работает на dev
