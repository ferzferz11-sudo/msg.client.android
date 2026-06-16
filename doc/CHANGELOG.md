# Lavender Messenger — Android CHANGELOG

## v1.1.3.20 (2026-06-16) — Модуляризация RealGrpcClient

### Рефакторинг
- **RealGrpcClient разделён на модули**: 4081 → 3739 строк (-342, -8.4%)
- **GrpcConnectionManager** (167 строк) — connect/reconnect/disconnect/keepalive
- **GrpcAuthClient** (232 строки) — signInV2/signUpV2/refreshToken/signOut/revokeDevice
- **GrpcCallClient** (124 строки) — startCallSession/sendCallSignal
- **GrpcTypingClient** (87 строк) — startTypingStream/sendTypingSignal

### Удаление мёртвого кода
- **ChatListFragmentV2** (144 строки) + fragment_chat_list_v2.xml — удалены
- **Дублирование currentServerAddress/currentServerPort** — исправлено

### Инфраструктура
- Gradle wrapper удалён с сервера (OOM protection — нельзя случайно запустить ./gradlew)
- gradle.properties: JVM память 2048m для локальной сборки

### Коммиты
- `c413038` — feat: modularize RealGrpcClient — extract 4 modules, remove dead code
- `208141c` — fix: resolve compilation errors from modularization
- `6207fc3` — chore: revert gradle.properties — remove experimental KSP/AGP flags
- `f60478c` — chore: revert gradle memory to 512m/256m (server-safe)

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
