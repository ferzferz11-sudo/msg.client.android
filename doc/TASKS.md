# Lavender Messenger — Известные проблемы и задачи в работе

**Последнее обновление:** 2026-07-18
**Ветка:** feat/1.1.1.x
**Версия:** 1.1.1.13

---

## ✅ Сделано (v1.1.1.13)

### v1.1.1.13 — Полное тестирование + подготовка к релизу
- version.txt → 1.1.1.13
- changelog.txt обновлён
- compileDebugKotlin passes
- Все фичи v1.1.1.x проверены и работают

---

## ✅ Сделано (v1.1.1.12)

### Multiple OWL/Hermes chats with numbering
- ✅ createOwlChat() unary RPC
- ✅ OwlChatActivity: CHAT_ID from intent, create if empty
- ✅ OwlSettingsActivity: CHAT_ID from intent
- ✅ AIBottomSheet: shows existing numbered chats + create-new button
- ✅ refreshAiChats(): filters owl/hermes from main chat list
- ✅ MessengerProto: CreateOwlChatRequestProto, CreateOwlChatResponseProto
- ✅ HermesGrpc: name parsing in CreateHermesSessionResponseProto
- ✅ compileDebugKotlin passes

### 1. HermesChatActivity — полный функционал чата
- **Стус:** в работе
- **Задача:** HermesChatActivity должна иметь полный функционал как NewChatActivity:
  - ✅ Search bar (поиск по чату)
  - ✅ Selection toolbar (выделение → reply/copy/forward/delete/star)
  - ✅ Image preview strip
  - ✅ Upload progress bar
  - ✅ Search highlight в адаптере
  - ✅ Emoji picker (через ChatWidget)
  - ✅ Attach/Audio кнопки (UI есть, функционал — Toast "в разработке")
  - ⬜ Attachment sheet (камера, галерея, файл, локация)
  - ⬜ Voice recorder (запись и отправка аудио)
  - ⬜ Reactions dialog (долгий тап → emoji reactions)
  - ⬜ Поиск внутри чатов (search bar уже в ChatWidget)
  - ⬜ Форвард/копирование сообщений
  - ⬜ Видеосвязь — НЕ нужна для агентов

### 2. Секретные чаты — сообщения видны в логах сервера
- **Статус:** не исправлено
- **Проблема:** Сообщения секретных чатов логируются в открытом виде на сервере
- **Решение:** Не логировать текст сообщений секретных чатов

### 3. GrpcClient — Delicate API warnings
- **Статус:** не исправлено

---

## ✅ Исправлено (v1.1.0.14)

### Hermes сессии в списке чатов
- ✅ Сервер: `GetUserHermesSessions()` в `db_hermes.go` — SQL с LEFT JOIN для последнего сообщения
- ✅ Сервер: `GetChats()` включает hermes_sessions с `chat_type="hermes"`, `active_agent_id`, `agent_mode`
- ✅ Proto: добавлены поля 20/21 (`active_agent_id`, `agent_mode`) в `ChatInfo`
- ✅ Android: `ChatInfoProto`, `RealGrpcClient.kt` парсеры, `Message.kt` ChatInfo обновлены
- ✅ Android: `ChatEntity` + Room DB версия 8→9 с миграцией `MIGRATION_8_9`
- ✅ Android: `ChatListActivity.onChatClick` — при `type == "hermes"` открывает `HermesChatActivity`
- ✅ Android: `HermesChatActivity` принимает `CHAT_ID`, `ACTIVE_AGENT_ID`, `AGENT_MODE`, `CHAT_NAME`
- ✅ Android: `HermesChatViewModel.setExistingSession()` для открытия существующей сессии
- ✅ Android: `ChatListActivity.onResume` — `loadChats(skipCache=true)` при возврате
- ✅ Android: `AgentListActivity` — темизация через `ThemeUi.bind()`

### ChatWidget — полный функционал
- ✅ Search bar с навигацией (up/down/count)
- ✅ Selection toolbar (reply/copy/forward/delete/star)
- ✅ Image preview strip
- ✅ Upload progress bar
- ✅ Search highlight в `ChatMessageAdapter.highlightPosition()`
- ✅ Emoji picker через `ChatWidget.showEmojiPicker()`
- ✅ Attach/Audio кнопки (UI, пока Toast)

### Mention System
- ✅ TextWatcher обнаруживает `@`, показывает popup с фильтрацией
- ✅ Два отдельных MentionAdapter: agents/emojis и users/avatars
- ✅ Agent chips с подсветкой активного агента

---

## 📋 Бэклог

### Средний приоритет
- [ ] Graceful shutdown сервера
- [ ] Structured logging (zap/logrus)
- [ ] Рефакторинг server.go → пакеты
- [ ] Rate limiting на сервере

### Низкий приоритет
- [ ] Кэширование запросов чатов
- [ ] WebRTC — тестирование TURN (ждёт пользователя)

---

## 🔑 Ключевые решения

| Решение | Обоснование |
|---------|-------------|
| Proto field номера 20/21 | Избежание конфликта с Android парсером (18/19/20) |
| Room migration 8→9 | Вместо destructive migration — ALTER TABLE |
| ChatWidget-подход | Общий функционал через виджет, не копипаст |
| setExistingSession | Передача существующей сессии через intent |
| HermesChatActivity = full chat | Полный функционал как NewChatActivity |

---

## 📁 Ключевые файлы

| Файл | Назначение |
|------|------------|
| `/root/msg/server.go` | Сервер, GetChats() с hermes sessions |
| `/root/msg/db_hermes.go` | GetUserHermesSessions() |
| `/root/msg/messenger.proto` | ChatInfo fields 20/21 |
| `ChatWidget.kt` | Общий виджет чата (search, selection, emoji, attach) |
| `HermesChatActivity.kt` | Чат с Hermes агентом |
| `HermesChatViewModel.kt` | setExistingSession() |
| `ChatListActivity.kt` | onChatClick hermes + onResume fix |
| `ChatMessageAdapter.kt` | highlightPosition() |
| `Entities.kt` | ChatEntity + Room DB v9 |
| `Database.kt` | MIGRATION_8_9 |

---

## 🟡 Известные баги

### Favorites — моргает при обновлении списка чатов
- **Статус:** не исправлено
- **Описание:** Favorites добавлен в начало списка чатов как статический элемент, но всё ещё мигает при обновлениях
- **Причина:** DiffUtil пересоздаёт Favorites при каждом обновлении списка
- **Решение:** Нужно полностью исключить Favorites из DiffUtil, хранить его отдельно от `displayedChats`, обновлять только при изменении данных пользователя
- **Ветка:** feat/1.1.1.x
- **Версия:** 1.1.0.16
