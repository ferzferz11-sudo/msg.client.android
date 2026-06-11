# Lavender Messenger (Android) — Задачи

**Версия:** 1.1.3.0
**Обновлено:** 2026-06-11
**Ветка:** feat/1.1.3.x
**Тег:** v1.1.2.10 (выпущен)
**APK:** /var/www/lavender/lavender.apk
**GitHub релиз:** https://github.com/ferzferz11-sudo/msg.client.android/releases/tag/v1.1.2.9

---

## 🔄 v1.1.3.0 — Интеграция агента с реальным бэкендом

### Remote Agent — интеграция
- **Интеграция чата с реальным агентом** — замена echo-заглушки на gRPC streaming
- Отправка задач через Connect + OrchestratorMessage
- Получение результатов в реальном времени
- Отображение типов задач (shell, git, build, deploy, docker, ai)
- Heartbeat статус подключения

---

## ✅ v1.1.2.10 — AI шторка: новый чат виден сразу + удаление не ломает список

### AI BottomSheet — исправлено
- **Новый чат с оркестратором отображается сразу** — не нужно переоткрывать шторку
- Root cause: `refreshAiChats()` запускал асинхронный `getAIChats()` с коллбэком, но `showAIActionSheet()` читал `currentAiChats` мгновенно — данные ещё не пришли
- Fix: добавлен `refreshAiChatsAwait()` — suspend-функция через `suspendCancellableCoroutine`, которая ждёт ответа сервера
- `showAIActionSheet()` теперь suspend, вызывает `refreshAiChatsAwait()` перед построением списка
- Все вызовы `showAIActionSheet()` обёрнуты в `lifecycleScope.launch`

### AI BottomSheet удаление — исправлено
- **Удаление чата оркестратора не скрывает чат агента** — оба остаются на месте
- Root cause: `onDeleteChat` вызывал `refreshAiChats()` (асинхронный коллбек), а затем сразу `updateChats(currentAiChats.toList())` — в момент когда `currentAiChats` уже очищен, но ответ ещё не пришёл → передавался пустой список
- Fix: удаление из локального списка без сетевого запроса — `currentAiChats.removeAll { it.id == chat.id }` + `sheet.removeChat(chat.id)` + `rebuildContent()`
- Добавлен `AIBottomSheet.removeChat(chatId)` — удаляет конкретный чат из `existingChats`

### Технические детали
- `refreshAiChatsAwait()` — новый suspend-метод, обёртка над `GrpcClient.getAIChats()`
- `showAIActionSheet()` — теперь `suspend fun` вместо `fun`
- `AIBottomSheet.removeChat(chatId)` — новый метод для удаления одного чата
- `AIBottomSheet.updateChats()` + `rebuildContent()` — уже были, работают корректно

---

## ✅ v1.1.2.9 — Исправления чатов

### OWL — исправлено
- **Сообщения пользователя отображаются сразу** — не нужно ждать ответа агента
- Root cause: typing indicator мутировал `adapter.currentList` напрямую, ломая DiffUtil
- Fix: typing placeholder теперь часть единого списка в `OwlChatViewModel._owlMessages`
- Убрана мутация `adapter.currentList` из `OwlChatActivity.observeState()`

### Hermes — исправлено
- **История чата сохраняется в локальную БД (Room)** — не теряется при перезапуске
- Root cause: `HermesChatViewModel` хранил сообщения только в памяти
- Fix: добавлен `messageDao`, `HermesMessage` ↔ `MessageEntity` mapping
- Пользовательские сообщения сохраняются при отправке
- Ответы агентов сохраняются при завершении стрима (`finished = true`)
- История загружается из локальной БД сначала, потом обновляется с сервера
- `deleteSession()` очищает локальные сообщения

### Технические детали
- `OwlMessage` — добавлено поле `isTyping: Boolean`
- `HermesChatViewModel` — добавлены `messageDao`, `Dispatchers`, `withContext`
- `Entities.kt` — добавлены `HermesMessage.toMessageEntity()` и `MessageEntity.toHermesMessage()`

---

## ✅ v1.1.2.8 — AI чат улучшения, Favorites fix, Changelog fix

### AI Чаты
- **Убран прелоадер** во время ожидания ответа агента — достаточно typing indicator
- **Таймаут стрима 120 сек** с сбросом при каждом сообщении — показывает ошибку на русском
- **Шторка AI реорганизована**: чаты разделены по типам — Hermes чаты в секции "Лава ИИ", OWL чаты в секции "OWL агент"

### Favorites — исправлено
- **Favorites отображается сразу при входе** — не нужно создавать чат чтобы увидеть Избранное
- Показывается даже при недоступном сервере (offline-first)

### Changelog
- **Цвета текста** из ThemeStore вместо resolveColorAttr — читаемый текст на кастомных тёмных темах
- **Порядок загрузки**: сначала GitHub API, fallback только через 3с при отсутствии сети

---

## ✅ v1.1.2.7 — Splash улучшения, удаление онбординга

- Увеличено расстояние логотип→текст (60px → 90dp)
- Новый SplashLoadingActivity — оверлей загрузки для логина/регистрации
- Онбординг полностью удалён
- Чекбокс "Создать чат" при добавлении контакта

---

## ✅ v1.1.2.6 — Bundled changelog + ссылки на GitHub

- Встроенный changelog (`changelog_bundled.txt`) — показывается мгновенно
- Ссылки на полные CHANGELOG.md на GitHub
- `changelog.txt` удалён из проекта и деплоя

---

## 📋 Бэклог

### Высокий приоритет
- [ ] **Remote Agent v1.1.3** — интеграция с реальным бэкендом (gRPC streaming, heartbeat, типы задач)

### Средний приоритет
- [ ] Модульные тесты для OWL streaming

### Низкий приоритет
- [ ] Кэширование запросов чатов

---

## 🔑 Ключевые решения

| Решение | Обоснование |
|---------|-------------|
| Proto field номера 20/21 | Избежание конфликта с Android парсером |
| Room migration 8→9 | ALTER TABLE вместо destructive migration |
| ChatWidget-подход | Общий функционал через виджет, не копипаст |
| setExistingSession | Передача существующей сессии через intent |
| ThemeApplier FAB list | Новые FAB добавлять в список для кастомных тем |
| SplashLoadingActivity | Отдельный оверлей вместо ProgressBar на кнопке |
| Typing в ViewModel | Typing indicator часть единого списка, не мутация adapter |
|| Hermes DB persistence | Сообщения сохраняются в Room, не только в памяти |
|| AI sheet await refresh | suspendCancellableCoroutine для ожидания getAIChats перед показом шторки |
|| AI sheet local delete | Удаление из локального списка без сетевого запроса — мгновенный rebuild |
|| No auto-scroll | Автоскролл на последнее сообщение полностью убран — позиция сохраняется |

---

## 📁 Ключевые файлы

| Файл | Назначение |
|------|------------|
| `ChatWidget.kt` | Общий виджет чата (search, selection, emoji, attach) |
| `NewChatActivity.kt` | Обычный чат (группы/личные) |
| `HermesChatActivity.kt` | Чат с Hermes агентом |
| `HermesChatViewModel.kt` | ViewModel Hermes + локальная БД |
| `OwlChatActivity.kt` | Чат с OWL AI |
| `OwlChatViewModel.kt` | ViewModel OWL + typing indicator |
| `GrpcClient.kt` | Единая точка доступа к gRPC (facade) |
| `RealGrpcClient.kt` | Реализация gRPC клиента |
| `HermesGrpc.kt` | Hermes gRPC методы |
| `OwlGrpc.kt` | OWL gRPC методы |
| `ChatListActivity.kt` | Главный список чатов + AI шторка |
| `AIBottomSheet.kt` | AI шторка с чатами |
| `ChatMessageAdapter.kt` | Адаптер сообщений с DiffUtil |
| `Entities.kt` | Room Entity + mapping функций |
| `AppDatabase.kt` | Room DB v9 |
| `ThemeApplier.kt` | Применение кастомных тем к UI |
| `ThemeStore.kt` | Хранилище текущей темы |
| `SplashActivity.kt` | Сплеш-экран |
| `SplashLoadingActivity.kt` | Оверлей загрузки для авторизации |
| `scripts/release.sh` | Скрипт выпуска релиза |
