# Lava Messenger — Android Code Audit

**Дата:** 2026-06-17
**Версия:** v1.1.3.30
**Аудитор:** OWL (автоматический аудит)

---

## 1. Общая статистика

| Метрика | Значение |
|---------|----------|
| Всего Kotlin файлов | 141 |
| RealGrpcClient | 874 (было 3810, -77%) |
| gRPC модулей | 12 + Marshallers |
| ChatListActivity | ~1445 LOC (добавлены SearchableListBottomSheet методы) |

---

## 2. Топ-10 файлов по размеру

| # | Файл | LOC | Статус |
|---|------|-----|--------|
| 1 | HermesGrpc.kt | 1880 | 🟡 Большой, но изолированный |
| 2 | MessengerProto.kt | 1791 | 🟢 Proto-код, генерируемый |
| 3 | NewChatActivity.kt | 1473 | 🟡 Требует рефакторинга (можно разбить) |
| 4 | GrpcMarshallers.kt | 1394 | 🟢 Marshaller classes, отдельный файл |
| 5 | OwlGrpc.kt | 1145 | 🟢 Изолированный AI-модуль |
| 6 | ChatListActivity.kt | 1113 | 🟡 Улучшить (ToolbarManager, TabManager) |
| 7 | MessageAdapter.kt | 870 | 🟢 Нормальный для адаптера |
| 8 | RealGrpcClient.kt | 874 | 🟢 Orchestrator, рефакторинг завершён |
| 9 | GrpcClient.kt | 779 | 🟢 Facade, стабильный |
| 10 | ProfileActivity.kt | 719 | 🟡 Можно разбить на фрагменты |

---

## 3. Архитектурный аудит

### 3.1 Сильные стороны ✅

1. **Модульная структура gRPC** — 12 модулей + Marshallers, God Object устранён (-77%)
2. **Facade паттерн** — GrpcClient предоставляет чистый API для UI
3. **MVVM для ChatList** — ViewModel выделен из Activity
4. **UpdateCoordinator** — чистый пример выноса логики из Activity
5. **BearerTokenInterceptor** — правильная автоматическая подстановка JWT
6. **CacheUtils** — единый утилит для кэша
7. **ProfileClient** — отдельный object для ProfileService v2
8. **E2EE** — шифрование выделено в E2EEManager
9. **Theme system** — ThemeApplier + ThemeUtils — чистая архитектура тем
10. **i18n** — все строки в resources, дублирование en+ru
11. **AppBarLayout tinting** — программная краска через ThemeApplier
12. **ThemeStore.init()** — загрузка кастомной темы из кэша при старте

### 3.2 Слабые стороны 🔴

#### СРЕДНЕ

1. **NewChatActivity — 1473 строки**
   - Создание чатов, поиск пользователей, UI, навигация — всё в одном Activity
   - Решение: выделить ViewModel + Fragment/Composables

2. **ChatListActivity — 1113 строки**
   - Toolbar, tabs, FABs, search, selection mode, settings sheets, update coordinator wiring
   - Решение: выделить ToolbarManager, TabManager

3. **GrpcClient facade — 779 строк**
   - Значительная часть — proxy-методы без логики (пустой `realGrpcClient.xxx(...)`)
   - Можно упростить через extension functions

4. **Error handling не унифицирован**
   - Где-то try-catch, где-то callback, где-то flow
   - ErrorHandler.kt существует, но используется частично

5. **HermesGrpc + OwlGrpc — суммарно 3025 строк**
   - AI-специфичный код доменной логики в gRPC слое
   - Решение: выделить domain layer

#### НИЗКОЕ ПРИОРИТЕТНО

6. **Тестирование отсутствует**
   - 0 unit-тестов для gRPC клиента
   - 0 instrumented тестов для UI

7. **Server discovery через raw protobuf parsing**
   - parseServerList, parseServerInfo — ручной парсинг байтов
   - Решение: использовать proto-generated классы

---

## 4. Проблемы безопасности

1. **Hardcoded IP** — `13.140.25.249` в `fetchServersList()` (строка 158)
2. **usePlaintext()** — все каналы без TLS (осознанно для dev)
3. **SharedPreferences без шифрования** — CredentialStore использует обычный SharedPreferences
4. **Нет certificate pinning** — при переходе на TLS нужно добавить

---

## 5. Производительность

### Проблемы

1. **Lazy channel connecting** — первый вызов может быть медленным (это нормально для gRPC)
2. **Нет batching запросов** — каждый чат запрашивается отдельно
3. **Background retry loop** — до 50 retry с exponential backoff (30с max) — может разряжать батарею

### Оптимизации

1. **Pagination для чатов** — GetChatsRequest поддерживает limit/offset, но UI не использует
2. **Incremental history loading** — loadHistory загружает все 100 сообщений сразу
3. **Glide avatar caching** — уже есть avatarCache + fullAvatarCache — хорошо

---

## 6. Мёртвый код

1. **`updateMessage(m: Message)`** (строка 2266 в v1 reference) — пустая реализация `{}`
2. **`loadUsers()`** (строка 2265 в v1 reference) — вызывает `loadAllUsers {}` с пустым callback

---

## 7. Анти-паттерны

1. **Long Method** — startChat (130+ строк), getChats (100+ строк)
2. **Feature Envy** — ChatListActivity слишком много знает о gRPC
3. **Primitive Obsession** — много String констант вместо enum/sealed class
4. **Shotgun Surgery** — изменение одного RPC метода требует правок в 3 местах (RealGrpcClient, GrpcClient, Activity)

---

## 8. Рекомендации по приоритету

### 🟡 Следующие 2-3 сессии
1. Рефакторинг NewChatActivity (ViewModel + Fragments)
2. Унификация error handling
3. Разбиение ChatListActivity (ToolbarManager, TabManager)

### 🟢 Backlog
4. Тесты для gRPC клиента
5. HermesGrpc/OwlGrpc выделение в domain layer
6. Pagination для чатов
7. Incremental history loading
8. Certificate pinning
9. Encrypted SharedPreferences
