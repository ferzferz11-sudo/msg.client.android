# Release Notes — Lavender Messenger v1.4.0.15

**Дата:** 2026-08-16
**Фокус:** Fast Mode, Performance, Bug Fixes

---

## Добавлено

### Fast Mode — быстрый режим чат-листа
Новый пункт "Режим: Полный/Быстрый" в overflow menu чат-листа (ниже Поиска).

**Быстрый режим отключает:**
- Аватары собеседников (нет Glide вызовов, статичная иконка)
- Анимации элементов (нет DefaultItemAnimator)
- Layout animation при первой загрузке
- Обводку аватаров

**Полный режим** — текущее поведение, без изменений.

Настройка сохраняется:
- Локально: SharedPreferences (`chat_list_fast_mode`)
- На сервер: `UpdateUserSettings.custom["chat_list_mode"]` = `"fast"` / `"full"`
- Восстанавливается при входе через `getUserSettings().custom`

---

## Исправлено

### Подвисание поля ввода чата (Performance)
При вводе текста происходило затормаживание из-за `handleMention()`, который парсил `JSONArray(participantsJson)` на каждое нажатие клавиши.

**Исправлено:**
- Participants парсится один раз в `configure()`, кэшируется в `cachedParticipants`
- Mention detection debounce 150ms — не обрабатывает каждый keystroke
- Draft сохраняется ТОЛЬКО в `onPause()`, не при вводе текста

### GrpcSavedMessagesClient — молчаливые потери callback
При ошибке gRPC (channel null, UNAUTHENTICATED, UNAVAILABLE) callback не вызывался — UI зависал в loading состоянии.

**Исправлено:**
- `getSavedMessages`: channel null → `callback(emptyList())`, onClose error → `callback(emptyList())`
- `addSavedMessage`: channel null → `callback(false, ...)`, onClose error → `callback(false, ...)`
- `removeSavedMessage`: аналогично
- UNAUTHENTICATED retry для `getSavedMessages`

### UpdateUserSettingsRequestMarshaller — custom map не сериализовался
Поле 4 (`custom: Map<String, String>`) не записывалось в wire format — настройки не синхронизировались с сервером.

**Исправлено:** сериализация map entries в field 4.

---

## Тесты

- 2 новых теста для `UpdateUserSettingsRequest` с custom map
- Все 625+ тестов проходят

---

## Файлы

### Добавлено
- `FastModeManager.kt` — управление состоянием fast mode

### Изменено
- `ChatListSearch.kt` — menu item handler + toggle
- `ChatListActivity.kt` — applyFastMode(), fast mode check при инициализации
- `ChatAdapter.kt` — fastMode parameter в bind(), пропуск аватаров
- `ChatInputDelegate.kt` — cachedParticipants + mention debounce
- `GrpcSavedMessagesClient.kt` — callback safety + UNAUTHENTICATED retry
- `GrpcMarshallers.kt` — custom map serialization
- `ProfileClient.kt` — custom parameter в updateUserSettings
- `GrpcClient.kt` — custom parameter в facade
- `chat_list_menu.xml` — action_fast_mode item
- `strings.xml` / `strings-ru.xml` — fast mode strings
