# Промпт для новой сессии — v1.1.3.8 (stable)

**Дата:** 2026-06-13
**Версия:** 1.1.3.8
**Ветка:** feat/1.1.3.x

---

## СТАТУС: v1.1.3.8 — СТАБИЛЬНАЯ ВЕРСИЯ

Релиз выпущен: https://github.com/ferzferz11-sudo/msg.client.android/releases/tag/v1.1.3.8

---

## АРХИТЕКТУРА

### Сервер (/root/msg)
```
main.go                    — Entry point, gRPC server
server.go                  — Структура server (ServerVersion = "1.1.3.8")
server_remote.go           — Remote Agent RPC (DeployAgentTaskStream fix)
hermes_remote_manager.go   — HandleTaskStream, StreamDone flag
server_remote_test.go      — 6 unit-тестов для streaming
messenger.proto            — DeployAgentTaskStream RPC
```

### Android (/root/msg.client.android)
```
ui/remote/
├── RemoteAgentActivity.kt         — Чат с агентом (toolbar fix, status bar)
├── RemoteAgentSettingsActivity.kt — Настройки (input fields theming)
├── RemoteAgentViewModel.kt        — sendMessageStreaming (full stdout/stderr)
├── RemoteAgentService.kt          — Foreground service
├── RemoteAgentManager.kt          — Singleton manager
└── HermesGatewayManager.kt        — SSH туннель

ui/chat/widget/ChatWidget.kt       — Общий виджет чата
ui/adapter/ChatAdapter.kt          — filter() fix (dispatchUpdatesTo)
theme/ui/ThemeApplier.kt           — Remote Agent input fields added
```

---

## КЛЮЧЕВЫЕ РЕШЕНИЯ (v1.1.3.8)

### Streaming fix
- **Сервер**: DeployAgentTaskStream отправляет `done=True` ровно один раз с полными данными из TaskResult
- **Android**: RemoteAgentViewModel при `done=True` использует полные буферы из `update.stdout`/`update.stderr`
- **Anti-pattern**: НЕ отправлять done=True дважды (пустой + полный)

### Remote Agent UI
- **Тулбар**: `toolbar_background` + `ThemeUi.bind()` — единообразно с другими активити
- **Status bar**: `LinearLayout` вместо `ConstraintLayout` — кнопки не уезжают
- **Input fields**: All gateway fields в `ThemeApplier.commonInputs`
- **Кнопка Start**: Скрыта если агент не настроен (нет туннеля/токена/агента)

### ChatAdapter filter()
- **Anti-pattern**: `notifyItemRangeChanged` не обновляет размер списка → crash
- **Правило**: `diffResult.dispatchUpdatesTo()` с ListUpdateCallback и offset +1 для Favorites

### Espresso Testing IDs
- Все `android:id` в XML следуют системе именования `snake_case` с префиксами (`btn_`, `et_`, `tv_`, `iv_`, `rv_`, `fab_`, `cv_`, `ll_`, `fl_`, `pb_`, `srl_`, `til_`, `actv_`, `barrier_`)
- Динамические View в Kotlin получают ID через `View.generateViewId()`
- При переименовании ID в XML — обязательно обновлять все ссылки в Kotlin-коде

---

## ПРАВИЛА

1. НЕ компилировать на сервере (OOM kill)
2. Коммитить и пушить после каждого значимого изменения
3. Версия сервера в `server.go:33`, версия Android в `version.txt`
4. Разделение архитектуры — каждый домен в своём server_*.go файле
5. userId (UUID) — всегда как ключ, НЕ username
6. changelog.txt БОЛЬШЕ НЕ ИСПОЛЬЗУЕТСЯ — использовать bundled changelog в APK
7. Agent tokens: в БД хранится SHA-256 хеш, не сам токен
8. JWT секрет: минимум 32 байта, НЕ коммитить
9. Темы: цвета программно через `ThemeUtils.parseSafeColor()`, НЕ `?attr/` в XML
10. ChatAdapter: при фильтрации с Favorites использовать `dispatchUpdatesTo` с offset +1
11. Remote Agent: кнопка Start скрыта если нет туннеля/токена/агента
12. String resources: НЕ конкатенировать в `setText`, использовать `getString` с placeholders
13. `vala` SSH ключ: `~/.ssh/vala` для подключения к серверу (lava)
14. **ID naming**: все новые ID в XML — snake_case с префиксом типа (`btn_`, `et_`, `tv_`, `iv_`, `rv_`, `fab_`, `cv_`, `ll_`, `fl_`, `pb_`, `srl_`, `til_`, `actv_`, `barrier_`)
15. **Dynamic Views**: при создании View в Kotlin — использовать `View.generateViewId()` для Espresso-совместимости

---

## ИЗВЕСТНЫЕ ПРОБЛЕМЫ

- Агент (hermes_remote_agent.py) ещё НЕ отправляет streaming updates — сервер готов, клиент готов
- Server migration warnings: `role "lavender" does not exist` (не критично)

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
./scripts/release.sh 1.1.3.8

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
