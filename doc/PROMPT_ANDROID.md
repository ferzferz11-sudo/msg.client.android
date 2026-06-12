# Промпт для новой сессии — v1.1.3.x (Android)

**Дата:** 2026-06-14
**Версия:** v1.1.3.1
**Ветка:** feat/1.1.3.x
**Текущая версия APK:** 1.1.3.1

---

## СТАТУС

- Remote Agent UI реализован и работает
- Token Management (генерация, список, отзыв) — есть
- HermesGrpc — все методы реализованы
- APK v1.1.3.0 собран и залит

---

## ИЗВЕСТНЫЕ ПРОБЛЕМЫ

### P1: Токен не появляется в списке после генерации
**Симптом:** Генерация проходит, но список токенов остаётся пустым
**Логи:** `loadTokens: userId=ea577733-3f2c-4752-ac0e-1b2a88a6836b`, `generateToken error JobCancellationException`
**Текущее состояние:** JobCancellationException исправлен, требует проверки после пересборки

### P1: Debug логи в production коде
**Где:**
- `HermesGrpc.kt:914` — `Log.d("HermesGrpc", "listRemoteAgents: calling...")`
- `HermesGrpc.kt:1186` — `Log.d("HermesGrpc", "generateAgentToken: onMessage...")`
- `RemoteAgentSettingsActivity.kt:172` — `Log.d("RemoteAgentSettings", "generateToken:...")`

---

## ЗАДАЧИ

### P1 — Критические

#### 1.1 Проверить токен flow
- Собрать debug APK
- Протестировать: генерация → появление в списке
- Если не работает — проверить логи `RemoteAgentSettings` и `HermesGrpc`
- **Файлы:** `RemoteAgentSettingsActivity.kt:169-191`, `HermesGrpc.kt:1144-1210`

#### 1.2 Убрать debug логи
- `HermesGrpc.kt` — убрать все Log.d/Log.e (или обернуть в BuildConfig.DEBUG)
- `RemoteAgentSettingsActivity.kt` — убрать логи из generateToken, loadTokens

### P2 — Важные

#### 2.1 Индикатор "агент не подключён"
- В RemoteAgentActivity показывать подсказку если агент отключён
- Отобразить инструкцию по запуску агента
- **Файл:** `RemoteAgentActivity.kt:308-320`

#### 2.2 Кнопка "Скопировать команду"
- В токене диалоге: кнопка "Скопировать команду запуска"
- Формат: `python3 hermes_remote_agent.py --server host:port --token <jwt>`
- **Файл:** `TokenDialog.kt`, `RemoteAgentSettingsActivity.kt`

#### 2.3 Объединить AgentListActivity + RemoteAgentActivity
- Убрать дублирование экранов
- RemoteAgentActivity — чат с remote agent (задачи)
- AgentListActivity — список AI агентов (Hermes)

### P3 — Средние

#### 3.1 Автоматический рефреш агентов
- Каждые 30 сек обновлять список агентов
- **Файл:** `RemoteAgentActivity.kt:122-127`

#### 3.2 Agent flow polish
- Показывать toast "Токен скопирован" после копирования
- Показывать прогресс при загрузке RemoteAgentSettingsActivity
- Визуально разделять секции

---

## КРИТИЧЕСКИЕ ФАЙЛЫ

| Файл | Назначение |
|------|-----------|
| `data/grpc/HermesGrpc.kt` | gRPC методы (token RPC, listRemoteAgents, deployTask) |
| `ui/remote/RemoteAgentSettingsActivity.kt` | Управление токенами |
| `ui/remote/RemoteAgentActivity.kt` | Чат с агентом, список агентов |
| `ui/remote/RemoteAgentViewModel.kt` | Состояние агентов, сообщений |
| `ui/remote/TokenDialog.kt` | Диалог генерации токена |
| `data/proto/MessengerProto.kt` | Proto классы |

---

## АРХИТЕКТУРА

```
┌──────────────────────────────────────────────────────────────┐
│ AIBottomSheet                                                │
│  └── "🖥 Агенты" → RemoteAgentActivity                       │
│       ├── loadAgents() → listRemoteAgents() gRPC             │
│       ├── sendMessage() → deployAgentTask() gRPC             │
│       └── ⚙ → RemoteAgentSettingsActivity                    │
│            ├── generateToken() → GenerateAgentToken gRPC     │
│            ├── loadTokens() → ListAgentTokens gRPC            │
│            └── revokeToken() → RevokeAgentToken gRPC          │
└──────────────────────────────────────────────────────────────┘
```

---

## ТЕСТОВЫЕ ДАННЫЕ

**User (dev server):**
- userId: `ea577733-3f2c-4752-ac0e-1b2a88a6836b`
- username: `ferz11`

**Сервер:**
- Dev: `localhost:50052`
- Prod: `13.140.25.249:50051`
