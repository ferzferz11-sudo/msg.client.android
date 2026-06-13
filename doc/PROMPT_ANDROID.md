# Промпт для новой сессии — v1.1.3.8 (Android)

**Дата:** 2026-06-13
**Версия:** v1.1.3.8
**Ветка:** feat/1.1.3.x

---

## СТАТУС: v1.1.3.8 — ИСПРАВЛЕНИЯ ОТПРАВЛЕНЫ В GIT, НУЖНА ПРОВЕРКА КОМПИЛЯЦИИ

### Что сделано в v1.1.3.8:

1. **"Агент не выбран" исправлен** — добавлен `ensureAgentSelected()`:
   - При отправке сообщения автоматически загружает агентов с сервера
   - Если сервер не поддерживает `ListRemoteAgents()` (старый сервер) — создаёт дефолтного агента локально
   - Убрана рекурсия в `sendMessageStreaming()`

2. **Панель статуса исправлена**:
   - ConstraintLayout вместо LinearLayout
   - Кнопки Start/Stop фиксированные 48dp
   - Текст статуса использует `?android:textColorPrimary` (контрастный)

3. **"Job was cancelled" подавлен**:
   - `loadAgents()` больше не пишет в `_error` (только AppLog.info)
   - Убраны дублирующие `refreshAgentStatus()` вызовы

### Что нужно проверить:

1. `./gradlew compileDebugKotlin` — должно собраться без ошибок
2. Установить APK на устройство
3. Войти через шлюз → открыть "Удалённый агент" → отправить команду
4. Ожидаемый результат: сообщение отправляется, ответ приходит, панель статуса видна

---

## КРИТИЧЕСКИЕ ФАЙЛЫ

- `ui/remote/RemoteAgentActivity.kt` — чат с агентом
- `ui/remote/RemoteAgentViewModel.kt` — sendMessageStreaming + selectAgent + loadAgents
- `data/grpc/HermesGrpc.kt` — gRPC методы, Channel-based streaming
- `data/updates/UpdateManager.kt` — возможный источник "Job was cancelled"
- `app/src/main/res/layout/activity_remote_agent.xml` — layout статус-бара

## КОНТЕКСТ

- Сервер: `/root/msg`, dev порт 50052, prod порт 50051
- Android: `/root/msg.client.android`
- Remote Agent: `/root/msg.remote.agent`
- Оба репозитория на ветке `feat/1.1.3.x`
- Пользователь собирает APK локально: `git pull && ./gradlew assembleRelease`

## ПРАВИЛА

- НЕ assembleRelease на сервере (OOM)
- Коммитить и пушить после каждого значимого изменения
- Версию НЕ менять без указания пользователя
