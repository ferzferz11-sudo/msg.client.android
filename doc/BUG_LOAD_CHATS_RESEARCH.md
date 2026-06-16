# Исследование бага: Чаты не загружаются при входе на новый сервер

**Дата:** 2026-06-15
**Приоритет:** Высокий (следующая сессия)

---

## Симптомы

1. При входе на новый сервер (v1 или v2) сначала виден только "Избранное"
2. Другие чаты не прогружаются
3. Свап вниз (pull-to-refresh) помогает — чаты появляются
4. Очистка локального кэша в настройках тоже помогает
5. При восстановлении вчерашнего сеанса свап не помог, пришлось выгружать приложение из памяти

---

## Анализ кода

### Поток загрузки чатов (v1 — ChatListActivity)

**onResume** (строка 1315):
1. Проверяет `connectionStatus`
2. Если DISCONNECTED/FAILED/shouldForceReconnect → вызывает `grpcClient.connect()`
3. НЕ вызывает `loadChats()` напрямую — полагается на `connectionStatus` → READY

**serversActivityLauncher** (строка 1510):
1. После возврата от ServersActivity с RESULT_OK:
   - Обновляет username/password/userId
   - Если сервер изменился → `disconnect()` + `connect()` + ждёт READY (15 сек) → `loadChats(skipCache=true)`
   - Если сервер НЕ изменился → ничего не делает (justReturnedFromServersActivity = true)

**loadChats** (строка 765):
1. Проверяет `isLoadingChats` — предотвращает двойную загрузку
2. Проверяет `connectionStatus == READY` — если не READY, пропускает
3. Загружает с сервера через `getChats()` с таймаутом 10 сек
4. Применяет к UI

**startSync** (строка 1204):
1. Вызывается после успешной `loadChats()`
2. Каждые 5 сек опрашивает сервер
3. Пропускает пустые результаты

### Поток загрузки чатов (v2 — ChatListActivityV2)

**setupRecyclerView** (строка 226):
1. Подписывается на `viewModel.connectionStatus`
2. При READY → `viewModel.loadChats()`

**ChatListViewModelV2.loadChats** (строка 61):
1. Подписывается на `GrpcClient.connectionStatus` в `init`
2. При READY → `loadChats()`
3. Загружает с сервера через `getChats()` с таймаутом 10 сек

---

## Корневая причина (гипотеза)

### Проблема 1: Race condition между connect и loadChats

**v1 (ChatListActivity):**
- `onResume` вызывает `connect()` если нужно
- Но `loadChats()` вызывается ТОЛЬКО из `serversActivityLauncher` при смене сервера
- Если пользователь уже был подключён к серверу (сессия восстановлена), `onResume` не вызывает `loadChats()`
- Подписка на `connectionStatus` в `setupRecyclerView` (v2) может не сработать если статус уже READY

**v2 (ChatListActivityV2):**
- `viewModel.init` подписывается на `connectionStatus`
- Но если `connectionStatus` уже READY при создании ViewModel, событие не произойдёт
- `loadChats()` не вызывается автоматически

### Проблема 2: Кэш мешает

- `getChats()` сначала загружает из кэша (если `skipCache=false`)
- Если кэш пуст (новый сервер) → callback не вызывается для кэша
- Затем идёт запрос на сервер
- Но если сервер не ответил за 10 сек → `withTimeoutOrNull` возвращает `emptyList()`
- `emptyList()` → `buildSections(emptyList())` → показывается только Favorites

### Проблема 3: SwipeRefresh не помогает при пустом кэше

- `loadChats(skipCache=true)` при pull-to-refresh
- Но если `connectionStatus != READY` → `loadChats` просто выходит
- Пользователь свапает, но соединение ещё не готово

---

## Рекомендации по исправлению

### 1. Добавить принудительную загрузку при входе

**ChatListActivity.onResume:**
```kotlin
// После успешного подключения, если чаты не загружены
if (grpcClient.connectionStatus.value == ConnectionStatus.READY && !isChatsLoaded) {
    loadChats(skipCache = true)
}
```

**ChatListViewModelV2:**
```kotlin
// Добавить метод для принудительной перезагрузки
fun forceReload() {
    allChats = emptyList()
    _sections.value = emptyList()
    loadChats()
}
```

### 2. Исправить таймауты

- Увеличить таймаут с 10 до 15 секунд
- Добавить retry при пустом результате

### 3. Улучшить обработку пустого кэша

- При пустом кэше НЕ вызывать callback сразу
- Дождаться ответа сервера
- Показывать loading indicator

### 4. Добавить логирование

- Логировать каждый шаг загрузки
- Логировать причины пропуска loadChats

---

## Затронутые файлы

| Файл | Что изменить |
|------|-------------|
| `ChatListActivity.kt` | onResume: добавить проверку isChatsLoaded |
| `ChatListActivity.kt` | loadChats: улучшить обработку пустого кэша |
| `ChatListActivityV2.kt` | setupRecyclerView: добавить подписку на connectionStatus |
| `ChatListViewModelV2.kt` | Добавить forceReload(), улучшить loadChats() |
| `RealGrpcClient.kt` | getChats: улучшить обработку пустого кэша |
