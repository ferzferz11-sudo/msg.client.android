# Android — Паттерны и правила разработки

**Версия:** v1.1.3.8
**Обновлено:** 2026-06-13

---

## Паттерны

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
- Новые FAB добавлять в ThemeApplier: listOf(R.id.aiFab, R.id.addChatFab, ...)

### Сборка
- НЕ компилировать на сервере (OOM kill)
- compileDebugKotlin — рискованно (~1GB), только если > 2GB free
- assembleRelease — ТОЛЬКО локально
- Для проверки синтаксиса — читать файлы, не компилировать

### Версии
- Версия сервера в server.go:33
- Версия Android в version.txt
- НЕ менять версию без явного указания пользователя
- changelog.txt УДАЛЁН — использовать bundled changelog в APK

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
