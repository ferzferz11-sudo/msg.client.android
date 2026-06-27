# Client Plan: Hermes Agent ACP Integration

**Версия:** v1.0 | **Дата:** 2026-06-28 | **Ветка:** feat/1.3.1.x

---

## Обзор

Hermes Agent будет доступен как пресет-агент в AI v2. Клиенту НЕ нужно изменений в архитектуре — провайдер `hermes_acp` работает через тот же `ChatWithAIV2` streaming RPC. Достаточно минимальных UI-изменений.

## Что нужно сделать (КЛИЕНТ)

### Приоритет 1: Агент появляется в списке (0 изменений)

Hermes добавляется как пресет-агент на сервере. Клиент уже:
- Загружает пресеты через `ListAIAgents(includePublic=true)` → фильтрует `isPreset`
- Отображает в `AiV2AgentListActivity` (Tab 0: Presets)
- Отправляет сообщения через `ChatWithAIV2` с `agent_id = "hermes"`
- Получает стриминг токенов

**Ничего не нужно менять** — агент появится автоматически после серверного seed.

### Приоритет 2: UI-polish (минимальные изменения)

#### 2.1 Emoji для Hermes

В `AIBottomSheet.kt` и `AiV2AgentListAdapter.kt` добавить маппинг эмодзи:

```kotlin
"hermes" -> "\uD83D\uDD2C"  // 🔬 (лаборатория/исследования)
```

Файлы:
- `ui/widget/AIBottomSheet.kt` — маппинг эмодзи в `buildContent()`
- `ui/ai/AiV2AgentListAdapter.kt` — маппинг эмодзи в `onBindViewHolder()`

#### 2.2 Описание в toolbar

При выборе Hermes агента в чате toolbar покажет:
- Имя: "Hermes Agent"
- Статус: AVAILABLE (🟢) — если hermes бинарник доступен на сервере
- Это уже работает через `AgentStatus` enum

### Приоритет 3: Тестирование

#### Сценарии тестирования

| # | Сценарий | Ожидаемый результат |
|---|----------|---------------------|
| T1 | Выбрать Hermes в AI шторке | Агент отображается с эмодзи 🔬 |
| T2 | Отправить "Привет" | Стриминг токенов, ответ от Hermes |
| T3 | Отправить "Создай файл test.go" | Hermes выполняет tool calling |
| T4 | Отправить 2 сообщения подряд | Сессия персистится, контекст сохраняется |
| T5 | Подождать 30+ минут, отправить | Новая сессия (старая очищена) |
| T6 | Проверить статус агента | 🟢 AVAILABLE (hermes бинарник на сервере) |

### Изменённые файлы

| Файл | Изменение |
|------|-----------|
| `ui/widget/AIBottomSheet.kt` | +emoji mapping "hermes" |
| `ui/ai/AiV2AgentListAdapter.kt` | +emoji mapping "hermes" |

**Итого:** 2 файла, ~4 строки кода.

### Что НЕ нужно менять

- `GrpcAIv2Client.kt` — `chatWithAIV2` уже поддерживает любой `agent_id`
- `AiV2ChatActivity.kt` — стриминг работает для всех провайдеров
- `AiV2ChatUseCase.kt` — tool calling loop уже реализован
- `MessagesV2Proto.kt` — proto не меняется
- `MessagesV2Marshallers.kt` — marshallers не меняются
- `AiAgentSetupActivity.kt` — редактирование пресетов не нужно

## Деплой

1. **Сервер:** `go run .` → Hermes Agent seeded в БД
2. **Клиент:** 2 файла изменены → `./gradlew assembleDebug`
3. **Тест:** открыть AI шторку → Hermes Agent должен быть в списке пресетов

## Зависимости

- Серверная часть (PROMPT_HERMES_ACP.md) должна быть реализована ПЕРВОЙ
- Hermes бинарник должен быть установлен на сервере: `/usr/local/bin/hermes`
- ACP mode должен быть поддерживаемым в Hermes (`hermes acp`)
