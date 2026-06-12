# Промпт для новой сессии — v1.1.3.x (Android)

**Дата:** 2026-06-14
**Версия:** v1.1.3.1
**Ветка:** feat/1.1.3.x
**Текущая версия APK:** 1.1.3.1 (выпущен)

---

## СТАТУС

- Remote Agent UI реализован и работает
- Token Management (генерация, список, отзыв) — есть
- HermesGrpc — все методы реализованы
- APK v1.1.3.1 собран и залит

---

## ЧТО СДЕЛАНО В v1.1.3.1

- ✅ Убран Toast "Вход выполнен" после авторизации
- ✅ Авто-прокрутка вниз при отправке сообщения (текст + изображения)
- ✅ Версия приложения на SplashActivity (BuildConfig.VERSION_NAME)
- ✅ Debug логи обёрнуты в BuildConfig.DEBUG
- ✅ Шторка "Дополнительные настройки": Очистка кэша и Журнал ошибок перемещены выше "Удалить профиль"
- ✅ "Logs" → "Журнал ошибок" (строковый ресурс error_log)

---

## ИЗВЕСТНЫЕ ПРОБЛЕМЫ

### P1: Токен не появляется в списке после генерации
**Статус:** Требует отладки на сервере
**Симптом:** Генерация проходит, но список токенов остаётся пустым
**Логи:** `loadTokens: userId=ea577733-3f2c-4752-ac0e-1b2a88a6836b`, `generateToken error JobCancellationException`
**Текущее состояние:** JobCancellationException исправлен в Android. Вероятная причина — hermesDB == nil на сервере (SaveAgentToken молча пропускается).
**Файлы:** `RemoteAgentSettingsActivity.kt:173`, `HermesGrpc.kt:1266`

---

## ЗАДАЧИ ДЛЯ НОВОЙ СЕССИИ

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
