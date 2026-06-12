# Lavender Messenger (Android) — Задачи

**Версия:** 1.1.3.5
**Обновлено:** 2026-06-13
**Ветка:** feat/1.1.3.x

---

## ✅ v1.1.3.5 — Remote Agent: UI исправления (commit ee5e115)

### Исправления чата с агентом
- ✅ TextWatcher для send button — показывается только при наличии текста
- ✅ CommandButton с CommandBottomSheet — 12 команд агента (help, status, logs, deploy, restart, git, docker, ps, df, uptime)
- ✅ Авто-прокрутка чата при новых сообщениях
- ✅ Исправлен баг: сообщения не отправлялись после ввода текста
- ✅ Исправлен баг: иконка команд была без обработчика

---

## ✅ v1.1.3.5 — Remote Agent: фоновое подключение (persistent connection)

### Foreground Service + Singleton Manager
- ✅ `RemoteAgentService.kt` — foreground service с SSH туннелем + gRPC
- ✅ `RemoteAgentManager.kt` — singleton для bind/unbind UI к сервису
- ✅ `RemoteAgentSettingsActivity.kt` — ServiceConnection + RemoteAgentStateListener
- ✅ `RemoteAgentActivity.kt` — ServiceConnection + RemoteAgentStateListener
- ✅ `AndroidManifest.xml` — RemoteAgentService + FOREGROUND_SERVICE_CONNECTED_DEVICE
- ✅ Notification показывает статус подключения
- ✅ START_STICKY — перезапускается системой

---

## ✅ v1.1.3.4 — Hermes Gateway (SSH туннель)
- ✅ `HermesGatewayManager.kt` — класс для управления SSH туннелем (JSch)
- ✅ `RemoteAgentSettingsActivity.kt` — UI секция "Подключение через шлюз"
- ✅ `activity_remote_agent_settings.xml` — layout с полями SSH хоста, портов, кнопками
- ✅ JSch зависимость `com.jcraft:jsch:0.1.55` в build.gradle.kts
- ✅ Сохранение настроек туннеля в SharedPreferences
- ✅ Команды агента используют туннельный адрес при активном туннеле

---

## ✅ v1.1.3.2 — Remote Agent Token Management

### Android
- **Генерация JWT токенов** — работает через `hermes_agent.HermesAgentService/GenerateAgentToken`
- **Список токенов** — отображается сразу после генерации (локальный кэш)
- **Копирование токена/команды** — кнопки в каждом элементе списка
- **Отзыв токена** — кнопка "Отозвать" с подтверждением
- **Запуск/остановка агента** — StartAgent/StopAgent RPC
- **UI статуса** — зелёный индикатор при запущенном агенте
- **Персистентность** — выбранный агент сохраняется в SharedPreferences

---

## ✅ v1.1.3.1 — Мелкие исправления и полировка

### UI/UX
- Убран Toast "Вход выполнен" после авторизации
- Авто-прокрутка вниз при отправке сообщения
- Версия приложения на SplashActivity (BuildConfig.VERSION_NAME)

### Code quality
- Debug логи обёрнуты в BuildConfig.DEBUG

---

## 📋 Бэклог

### Высокий приоритет
- [x] Фильтрация токенов по пользователю (сервер) ✅ уже реализовано в v1.1.3.4
- [ ] Streaming результатов задач агентом обратно клиенту

### Средний приоритет
- [ ] Кэширование запросов чатов

---

## 🔑 Ключевые решения

| Решение | Обоснование |
|---------|-------------|
| Proto field номера 20/21 | Избежание конфликта с Android парсером |
| ChatWidget-подход | Общий функционал через виджет, не копипаст |
| Hermes DB persistence | Сообщения сохраняются в Room, не только в памяти |
| Token local cache | Токен добавляется в локальный список сразу после генерации |
| activityScope | Независимый CoroutineScope, переживает пересоздание Activity |
| RemoteAgentService + RemoteAgentManager | Foreground service + singleton для persistent connection (v1.1.3.5) |

---

## 📁 Ключевые файлы

| Файл | Назначение |
|------|------------|
| `GrpcClient.kt` | Единая точка доступа к gRPC (facade) |
| `HermesGrpc.kt` | Hermes/Remote Agent gRPC методы |
| `RemoteAgentSettingsActivity.kt` | Управление токенами и агентом |
| `RemoteAgentActivity.kt` | Чат с remote agent |
| `RemoteAgentService.kt` | Foreground service (v1.1.3.5) |
| `RemoteAgentManager.kt` | Singleton manager (v1.1.3.5) |
| `HermesGatewayManager.kt` | SSH туннель (JSch) |
| `RemoteAgentViewModel.kt` | ViewModel для Remote Agent |
