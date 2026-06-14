# Промпт для новой сессии — v1.1.3.11 (dev)

**Дата:** 2026-06-14
**Версия:** 1.1.3.11
**Ветка:** feat/1.1.3.x

---

## СТАТУС: v1.1.3.11 — DEV

Исправлен баг двойного входа при смене сервера.
Следующий шаг: тестирование на dev + AuthService v2 интеграция.

---

## АРХИТЕКТУРА

### Сервер (/root/msg)
```
main.go                    — Entry point, gRPC server
server.go                  — Структура server (ServerVersion = "1.1.3.10")
server_remote.go           — Remote Agent RPC (DeployAgentTaskStream fix)
hermes_remote_manager.go   — HandleTaskStream, StreamDone flag
server_remote_test.go      — 6 unit-тестов для streaming
messenger.proto            — DeployAgentTaskStream RPC
```

### Android (/root/msg.client.android)
```
ui/remote/
├── RemoteAgentActivity.kt         — Чат с агентом (streaming)
├── RemoteAgentSettingsActivity.kt — Настройки (input fields theming)
├── RemoteAgentViewModel.kt        — ViewModel (sendMessageStreaming), AndroidViewModel
├── RemoteAgentService.kt           — Foreground service
├── RemoteAgentManager.kt           — Singleton manager
└── HermesGatewayManager.kt         — SSH туннель

ui/chat/widget/ChatWidget.kt       — Общий виджет чата
ui/adapter/ChatAdapter.kt          — filter() fix (dispatchUpdatesTo)
theme/ui/ThemeApplier.kt           — Remote Agent input fields added

data/
├── proto/MessengerProto.kt         — Proto data classes
├── grpc/GrpcClient.kt              — Facade
├── grpc/HermesGrpc.kt              — Remote Agent gRPC (unary + streaming)
├── models/ErrorHandler.kt           — Единый обработчик ошибок
└── models/AppLog.kt                — Глобальный логгер
```

---

## КЛЮЧЕВЫЕ РЕШЕНИЯ (v1.1.3.9)

### i18n — мультиязычность
- Все пользовательские строки в values/strings.xml (en) + values-ru/strings.xml
- Для новых строк: ОДНОВРЕМЕННО в оба файла
- ViewModel → AndroidViewModel для доступа к getString()
- Adapter/BottomSheet → context.getString()
- НЕ инициализировать getString() в полях класса Activity (crash до onCreate)

### Espresso Testing
- Все XML ID: snake_case + префиксы (btn_, et_, tv_, iv_, rv_, fab_, cv_, ll_, fl_, pb_, srl_, til_, actv_, barrier_)
- Динамические View: View.generateViewId()
- 4 тест-класса: ChatListActivityTest, RemoteAgentActivityTest, ChatWidgetTest, EmptyChatTextTest

### Empty chat text
- Favorites → "Personal storage" / "Личное хранилище"
- Обычные пустые чаты → "No messages" / "Нет сообщений"
- Проверять chat.type == "favorites", не lastMessageText

---

## ПРАВИЛА

1. НЕ компилировать на сервере (OOM kill)
2. Коммитить и пушить после каждого значимого изменения
3. Версия сервера в `server.go:34`, версия Android в `version.txt`
4. Разделение архитектуры — каждый домен в своём server_*.go файле
5. userId (UUID) — всегда как ключ, НЕ username
6. changelog.txt БОЛЬШЕ НЕ ИСПОЛЬЗУЕТСЯ — использовать bundled changelog в APK
7. Agent tokens: в БД хранится SHA-256 хеш, не сам токен
8. JWT секрет: минимум 32 байта, НЕ коммитить
9. Темы: цвета программно через `ThemeUtils.parseSafeColor()`, НЕ `?attr/` в XML
10. ChatAdapter: при фильтрации с Favorites использовать `dispatchUpdatesTo` с offset +1
11. String resources: НЕ конкатенировать в `setText`, использовать `getString` с placeholders
12. **i18n**: все новые строки ОДНОВРЕМЕННО в values/strings.xml (en) + values-ru/strings.xml
13. **getString() в ViewModel**: использовать AndroidViewModel + getApplication<Application>().getString()
14. **getString() в Adapter/BottomSheet**: использовать context.getString()
15. **НЕ инициализировать getString() в полях класса Activity** — только в onCreate()
16. **Форматирование строк**: при нескольких подстановках использовать позиционные форматтеры (%1$s, %2$d)

---

## ИЗВЕСТНЫЕ ПРОБЛЕМЫ

- Агент (hermes_remote_agent.py) ещё НЕ отправляет streaming updates — сервер готов, клиент готов
- Server migration warnings: `role "lavender" does not exist` (не критично)

---

## i18n — НЕЗАВЕРШЁННАЯ РАБОТА

**Вынесено (15 файлов):** AIBottomSheet, RemoteAgentActivity, RemoteAgentSettingsActivity, RemoteAgentService, ChatListActivity, ConferenceLobbyActivity, OwlSettingsActivity, OwlChatActivity, LogViewerActivity, HermesChatActivity, AgentSettingsActivity/BottomSheet, AgentListActivity, ChatMessageAdapter, CommandBottomSheet, OwlChatViewModel.

**Осталось вынести (средний приоритет):**
- NewChatActivity: upload progress text, conference/call detection strings
- MessageAdapter: call status strings ("Пропущенный вызов", "Входящий/Исходящий", "Вызов не принят")
- HermesGatewayManager: SSH error messages (6 строк)
- RemoteAgentManager: status text "Отключено", "Сервис не запущен"
- HermesChatViewModel: error в data class (нет контекста — нужен другой подход)
- SecurityActivity: Toast "Другие сеансы завершены", "Ошибка при завершении сеансов"
- ThemesActivity: "Лавандовый ночной"
- CallActivity: Toast "Не удалось соединиться"
- AgentListActivity: PREFILL_MESSAGE "Расскажи подробнее о модели"
- RemoteAgentActivity: agentCommands descriptions (12 строк команд)
- ChatAdapter: "📷 Фото" / "📷 Photo" (уже есть lang check — можно оставить)

---

## КОМАНДЫ

```bash
# === СЕРВЕР ===
cd /root/msg
export PATH=$PATH:/usr/local/go/bin:~/go/bin

# Сборка и деплой на dev
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

# === ANDROID ===
cd /root/msg.client.android
# НЕ запускать assembleRelease на сервере (OOM)!

# Релиз
./scripts/release.sh 1.1.3.10

# SSH к серверу
ssh lava
```

---

## DEV vs PROD

| Характеристика | Dev | Prod |
|----------------|-----|------|
| Порт | 50052 | 50051 |
| Сервис | lavender-server-dev | lavender-server |
| Конфиг | .env.dev | .env |
| DB | chat_db_dev | chat_db |

---

## ДОКУМЕНТАЦИЯ

- Индекс: `/root/msg.client.android/doc/INDEX.md`
- Паттерны: `/root/msg.client.android/doc/PATTERNS.md`
- Remote Agent: `/root/msg.client.android/doc/REMOTE_AGENT.md`
- Сервер: `/root/msg/doc/INTEGRATION_SESSION.md`, `/root/msg/doc/TASKS.md`
- Подводные камни: `/root/msg/doc/PITFALLS.md`
- CHANGELOG: `/root/msg.client.android/CHANGELOG.md` (Android), `/root/msg/CHANGELOG.md` (сервер)
