# Android — Паттерны и правила разработки

**Версия:** v1.1.3.11
**Обновлено:** 2026-06-14

---

## Паттерны

### Auth widgets pattern (v1.1.3.11)
Аутентификация вынесена в 3 виджета:
- `ServerAuthBottomSheet` — шторка выбора входа (лого + сервер + статус + login/register)
- `LoginBottomSheet` — шторка входа (username/password)
- `RegisterBottomSheet` — шторка регистрации (username/password/email)
- Все наследуют StandardBottomSheet
- Health check через http://host:8082/health
- Используются в: ChatListActivity, ServersActivity

### Server switch pattern (v1.1.3.11)
При смене сервера через ServersActivity:
- НЕ сохранять `serverAddress` в CredentialStore до успешного входа
- Сохранять `serverAddress` ТОЛЬКО после успешного `SessionManager.login()` в SUCCESS callback
- В `ChatListActivity.serversActivityLauncher` НЕ делать auto-login — пользователь уже вошёл
- Использовать флаг `justReturnedFromServersActivity` для предотвращения лишнего reconnect в `onResume()`
- Анти-pattern: `CredentialStore.setServerAddress()` до `SessionManager.login()` → двойной вход

### ChatWidget reuse pattern
При использовании ChatWidget в кастомных Activity ОБЯЗАТЕЛЬНО:
- Добавить TextWatcher для видимости send button
- Установить commandButton listener
- Скрыть внутренний toolbar (toolbar.visibility = GONE)
- Добавить auto-scroll при новых сообщениях

Без TextWatcher send button не появляется/исчезает при вводе.

### ChatAdapter filter() — фильтрация с Favorites
При фильтрации списка чатов с Favorites (position 0):
- Использовать `diffResult.dispatchUpdatesTo()` с ListUpdateCallback и offset +1
- НЕ использовать `notifyItemRangeChanged()` — не обновляет размер списка → crash
- Паттерн аналогичен `setChats()` — см. ChatAdapter.kt:176-189

### ChatAdapter Favorites offset
Favorites всегда на position 0, не участвует в DiffUtil:
- `allChats` — без Favorites
- `displayedChats` — без Favorites
- `getItemCount()` = displayedChats.size + 1 (если Favorites есть)
- `onBindViewHolder()` — position 0 = Favorites, остальные offset -1
- Все notify* вызовы смещены на +1 для Favorites

---

## Правила

### Kotlin
- `is` не `instanceof` (Java-стиль не работает)
- Прямой доступ к полям proto: `proto.fieldName` не `proto.getFieldName()`
- НЕ использовать callbackFlow/awaitClose/trySendBlocking — unresolved
- Использовать Channel(UNLIMITED) + flow{} + trySend()
- CancellationException ловить ОТДЕЛЬНО до generic Exception, re-throw, НЕ показывать toast

### Error Handling
- Все Toast ошибки ОБЯЗАТЕЛЬНО дублировать в AppLog.error()
- ErrorHandler.kt — единая точка входа для ошибок
- CancellationException → AppLog.info() (не ERROR)
- gRPC StatusRuntimeException → AppLog.error() с кодом статуса
- Network errors, SecurityException → AppLog.error()

### Темы
- НЕ использовать `?attr/` в XML для текста на кастомных тёмных темах
- Цвета устанавливать программно через ThemeUtils.parseSafeColor()
- ThemeApplier.apply() ДО setContentView()
- Новые FAB добавлять в ThemeApplier: listOf(R.id.fabAi, R.id.fabAddChat, ...)

### Сборка
- НЕ компилировать на сервере (OOM kill)
- compileDebugKotlin — рискованно (~1GB), только если > 2GB free
- assembleRelease — ТОЛЬКО локально
- Для проверки синтаксиса — читать файлы, не компилировать

### Версии
- Версия сервера в server.go:34
- Версия Android в version.txt
- НЕ менять версию без явного указания пользователя
- changelog.txt УДАЛЁН — использовать bundled changelog в APK

### i18n (ОБЯЗАТЕЛЬНО)
- ВСЕ user-facing строки ДОЛЖНЫ быть в `values/strings.xml` (en) + `values-ru/strings.xml` (ru)
- НИКОГДА не использовать hardcoded строки в Kotlin/Java коде
- Использовать `getString(R.string.xxx)` с правильным контекстом:
  - Activity: `getString(R.string.xxx)` — работает напрямую
  - Adapter/ViewHolder: `context.getString(R.string.xxx)` или `itemView.context.getString()`
  - ViewModel: `AndroidViewModel` + `getApplication<Application>().getString()`
  - Data классы: передать context как параметр
- НЕ инициализировать `getString()` в полях класса Activity (до `onCreate()`) — crash!
- При добавлении новой строки: добавить в ОБА файла (en + ru)
- Проверка: `grep -rn '"[А-Яа-я]' app/src/main/java/ --include="*.kt" | grep -v "R\.string"` — должно быть 0 результатов

---

## Espresso Testing

### Система именования ID

Все `android:id` в XML-разметке следуют единой системе именования:

| Префикс | Тип элемента | Пример |
|---------|-------------|--------|
| `btn_` | Кнопки | `btnSend`, `btnCancelDownload`, `btnRevoke` |
| `et_` | Поля ввода | `etSearch`, `etMessageInput`, `etApiKey` |
| `tv_` | Текстовые поля | `tvChatName`, `tvMessageText`, `tvToolbarTitle` |
| `iv_` | Изображения/Иконки | `ivAvatar`, `ivMuteIndicator`, `ivUpdateAvailable` |
| `rv_` | RecyclerView | `rvChatList`, `rvMessages`, `rvMentionList` |
| `srl_` | SwipeRefreshLayout | `srlChatList` |
| `fl_` | FrameLayout | `flProgressOverlay`, `flAvatarContainer` |
| `ll_` | LinearLayout | `llChatInfo`, `llInputRow`, `llSearchBar` |
| `pb_` | ProgressBar | `pbDownload`, `pbUpload`, `pbDeleteChat` |
| `fab_` | FAB | `fabAi`, `fabAddChat` |
| `cv_` | CardView | `cvReplyPreview`, `cvBottomPanel` |
| `til_` | TextInputLayout | `tilApiKey`, `tilModel` |
| `actv_` | AutoCompleteTextView | `actvModel` |
| `item_` | Контейнер элемента списка | `item_chat_container` |
| `barrier_` | Barrier | `barrierReplyPreview` |

### Правила

1. **Все интерактивные элементы** должны иметь `android:id` с правильным префиксом
2. **Все проверяемые элементы** (текст, состояния) должны иметь `android:id`
3. **Динамические View** в Kotlin-коде получают ID через `View.generateViewId()`
4. **ViewBinding** автоматически генерирует поля на основе XML ID
5. **Stale ID** — при переименовании ID в XML обязательно обновлять все ссылки в Kotlin-коде

### Примеры Espresso-тестов

```kotlin
// Проверка видимости элемента
onView(withId(R.id.btnSend)).check(matches(isDisplayed()))

// Ввод текста
onView(withId(R.id.etMessageInput)).perform(typeText("Hello"))

// Клик по кнопке
onView(withId(R.id.btnSend)).perform(click())

// Проверка текста
onView(withId(R.id.tvChatName)).check(matches(withText("Pavel")))

// RecyclerView — клик по элементу
onView(withId(R.id.rvChatList))
    .perform(RecyclerViewActions.actionOnItemAtPosition<ViewHolder>(0, click()))

// RecyclerView — проверка элемента
onView(withId(R.id.rvChatList))
    .perform(RecyclerViewActions.scrollToPosition<ViewHolder>(5))
```

### Запуск тестов

```bash
# Unit-тесты
./gradlew test

# Instrumented-тесты (Espresso)
./gradlew connectedAndroidTest

# Конкретный тест
./gradlew connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=lavender.client.android.ChatListTest
```

---

## Известные проблемы

### Исправлено в v1.1.3.8
- **DeployAgentTaskStream** — done=True отправлялся дважды (пустой + полный). Теперь один done=True с полными данными из TaskResult
- **ChatAdapter filter()** — notifyItemRangeChanged не обновлял размер списка при фильтрации с Favorites → crash. Исправлено на dispatchUpdatesTo с offset +1

### Исправлено в v1.1.3.7
- **Favorites flickering** — вынесен как отдельный favoritesItem, не участвует в DiffUtil
- **"Агент не выбран"** — ensureAgentSelected() с fallback
- **"Job was cancelled"** — CancellationException обрабатывается отдельно
- **ErrorHandler** — единый обработчик ошибок с AppLog

---

## Серверы

| Характеристика | Dev | Prod |
|----------------|-----|------|
| Порт | 50052 | 50051 |
| Сервис | lavender-server-dev | lavender-server |
| Конфиг | .env.dev | .env |
| DB | chat_db_dev | chat_db |

---

## Команды

```bash
# Сборка и деплой на dev
cd /root/msg && export PATH=$PATH:/usr/local/go/bin:~/go/bin
go build -o /tmp/lavender-server-dev .
systemctl stop lavender-server-dev
cp /tmp/lavender-server-dev /root/LavenderMessenger/run/lavender-server-dev
systemctl start lavender-server-dev

# Сборка и деплой на prod
go build -o /tmp/lavender-server .
systemctl stop lavender-server
cp /tmp/lavender-server /root/LavenderMessenger/run/lavender-server
systemctl start lavender-server

# Тесты
go test ./...

# Android (НЕ компилировать на сервере!)
cd /root/msg.client.android
# assembleRelease ТОЛЬКО локально!
```

---

## Паттерны i18n (v1.1.3.9)

### getString() в разных контекстах
- **Activity/Fragment**: `getString(R.string.xxx)` — работает напрямую
- **Adapter/ViewHolder**: `context.getString(R.string.xxx)` или `itemView.context.getString(R.string.xxx)`
- **BottomSheet/Dialog**: `context.getString(R.string.xxx)`
- **ViewModel**: НЕ использовать обычный ViewModel, только `AndroidViewModel` + `getApplication<Application>().getString(R.string.xxx)`
- **НЕ инициализировать getString() в полях класса Activity** — crash до onCreate(), использовать lateinit + инициализацию в onCreate()

### Форматирование строк
- Одна подстановка: `"Text %s"` — OK
- Несколько подстановок: использовать позиционные форматтеры `"Text %1$s %2$d"`
- НЕ использовать непозиционные форматтеры с несколькими подстановками — ошибка сборки

### Добавление новых строк
1. Добавить в `values/strings.xml` (английский)
2. Добавить в `values-ru/strings.xml` (русский)
3. Проверить что нет дубликатов (поиск по имени в обоих файлах)
4. Использовать `getString(R.string.xxx)` с правильным контекстом
