# Lavender Messenger (Android) — Задачи

**Версия:** 1.1.3.2
**Обновлено:** 2026-06-12
**Ветка:** feat/1.1.3.x
**Тег:** v1.1.3.2 (выпущен)
**APK:** /var/www/lavender/lavender.apk
**GitHub релиз:** https://github.com/ferzferz11-sudo/msg.client.android/releases/tag/v1.1.3.2

---

## ✅ v1.1.3.2 — Remote Agent Token Management

### Android
- **Генерация JWT токенов** — работает через `hermes_agent.HermesAgentService/GenerateAgentToken`
- **Список токенов** — отображается сразу после генерации (локальный кэш)
- **Копирование токена/команды** — кнопки в каждом элементе списка
- **Отзыв токена** — кнопка "Отозвать" с подтверждением
- **Запуск/остановка агента** — StartAgent/StopAgent RPC
- **UI статуса** — зелёный индикатор при запущенном агенте, белый текст для остальных
- **Персистентность** — выбранный агент сохраняется в SharedPreferences
- **Исправлено**: диалог токена не закрывается при копировании
- **Исправлено**: ошибки сервера переведены на русский

---

## ✅ v1.1.3.1 — Мелкие исправления и полировка

### UI/UX
- Убран Toast "Вход выполнен" после авторизации
- Авто-прокрутка вниз при отправке сообщения (текст + изображения)
- Версия приложения на SplashActivity (BuildConfig.VERSION_NAME)
- Шторка "Дополнительные настройки": Очистка кэша и Журнал ошибок перемещены выше "Удалить профиль"
- "Logs" → "Журнал ошибок" (строковый ресурс error_log)

### Code quality
- Debug логи обёрнуты в BuildConfig.DEBUG (HermesGrpc.kt, RemoteAgentSettingsActivity.kt)
- Добавлен импорт BuildConfig в RemoteAgentSettingsActivity

---

## ✅ v1.1.3.0 — Remote Agent UI + Token Management

### Remote Agent — интеграция
- **Интеграция чата с реальным агентом** — замена echo-заглушки на gRPC streaming
- Отправка задач через Connect + OrchestratorMessage
- Получение результатов в реальном времени
- Отображение типов задач (shell, git, build, deploy, docker, ai)
- Heartbeat статус подключения

---

## 📋 Бэклог

### Высокий приоритет
- [ ] Исправить hermes_remote_agent.py (сервер) — агент падает при подключении
- [ ] Фильтрация токенов по пользователю (сервер)
- [ ] Streaming результатов задач агентом обратно клиенту

### Средний приоритет
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
| Hermes DB persistence | Сообщения сохраняются в Room, не только в памяти |
| AI sheet await refresh | suspendCancellableCoroutine для ожидания getAIChats перед показом шторки |
| AI sheet local delete | Удаление из локального списка без сетевого запроса — мгновенный rebuild |
| No auto-scroll | Автоскролл на последнее сообщение полностью убран — позиция сохраняется |
| Token local cache | Токен добавляется в локальный список сразу после генерации |
| activityScope | Независимый CoroutineScope для generateToken, переживает пересоздание Activity |

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
| `HermesGrpc.kt` | Hermes/Remote Agent gRPC методы |
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
| `RemoteAgentSettingsActivity.kt` | Управление токенами и агентом |
| `RemoteAgentActivity.kt` | Чат с remote agent |
| `TokenDialog.kt` | Диалог генерации токена |
| `scripts/release.sh` | Скрипт выпуска релиза |
