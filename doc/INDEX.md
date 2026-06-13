# Lavender Messenger — Android Документация

**Версия:** v1.1.3.9
**Обновлено:** 2026-06-13
**Ветка:** feat/1.1.3.x

---

## Быстрый старт

1. **PROMPT_ANDROID.md** — промпт для новой сессии (читать первым)
2. **STRUCTURE.md** — структура проекта, ключевые файлы, паттерны
3. **REMOTE_AGENT.md** — документация Remote Agent (архитектура, протокол, streaming)
4. **PATTERNS.md** — паттерны и анти-patterns разработки
5. **TASKS.md** — таск-трекер
6. **CHANGELOG.md** — история изменений

---

## Архитектура

```
app/src/main/java/lavender/client/android/
├── ui/
│   ├── remote/
│   │   ├── RemoteAgentActivity.kt         — чат с агентом
│   │   ├── RemoteAgentSettingsActivity.kt — настройки (шлюз + токен)
│   │   ├── RemoteAgentViewModel.kt        — ViewModel (sendMessageStreaming)
│   │   ├── RemoteAgentService.kt          — foreground service
│   │   ├── RemoteAgentManager.kt          — singleton manager
│   │   └── HermesGatewayManager.kt        — SSH туннель
│   ├── chat/widget/ChatWidget.kt          — общий виджет чата
│   ├── adapter/ChatAdapter.kt             — адаптер списка чатов
│   └── ...
├── data/
│   ├── grpc/GrpcClient.kt                 — facade
│   ├── grpc/HermesGrpc.kt                 — Remote Agent gRPC
│   ├── proto/MessengerProto.kt            — proto data classes
│   └── ...
└── theme/ui/
    ├── ThemeApplier.kt                    — применение тем
    └── ThemeUi.kt                         — ThemeUi.bind()
```

---

## Ключевые паттерны

### Remote Agent
- **Тулбар**: `toolbar_background` + `ThemeUi.bind()` единообразно
- **Streaming**: `DeployAgentTaskStream` → `callbackFlow` → `flow.collect`
- **done=True**: сервер отправляет ровно один раз с полными данными
- **Кнопка Start**: скрыта если агент не настроен (нет туннеля/токена)

### Темы
- `ThemeApplier.apply()` до `setContentView()`
- Цвета программно через `ThemeUtils.parseSafeColor()`
- НЕ использовать `?attr/` в XML для текста на кастомных темах

### Фильтрация чатов
- `ChatAdapter.filter()` — `dispatchUpdatesTo` с offset +1 для Favorites
- НЕ использовать `notifyItemRangeChanged` — не обновляет размер списка

---

## Команды

```bash
# Сборка
./gradlew assembleRelease    # ТОЛЬКО локально (OOM на сервере)

# Релиз
./scripts/release.sh 1.1.3.9

# SSH к серверу
ssh lava
```

---

## Серверы

| | Dev | Prod |
|--|-----|------|
| Порт | 50052 | 50051 |
| SSH | lava (13.140.25.249) | same |
