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
- Сервер v1.1.3.1 — выпущен (token flow fix, rate limit, proto dedup)

---

## ЧТО СДЕЛАНО В v1.1.3.1

- ✅ Убран Toast "Вход выполнен" после авторизации
- ✅ Авто-прокрутка вниз при отправке сообщения (текст + изображения)
- ✅ Версия приложения на SplashActivity (BuildConfig.VERSION_NAME)
- ✅ Debug логи обёрнуты в BuildConfig.DEBUG
- ✅ Шторка "Дополнительные настройки": Очистка кэша и Журнал ошибок перемещены выше "Удалить профиль"
- ✅ "Logs" → "Журнал ошибок" (строковый ресурс error_log)

---

## ЗАДАЧИ ДЛЯ НОВОЙ СЕССИИ

### P1 — Android баги (исправить первыми)
- Проверить все remote agent activity на краши и NPE
- Проверить token list refresh после генерации
- Проверить revoke token flow
- **Файлы:** `RemoteAgentSettingsActivity.kt`, `RemoteAgentActivity.kt`, `TokenDialog.kt`

### P2 — Agent flow в Android
- **Индикатор "агент не подключён"** в RemoteAgentActivity — показывать подсказку если агент offline
- **Кнопка "Скопировать команду"** в TokenDialog: `python3 hermes_remote_agent.py --server host:port --token <jwt>`
- **Авто-рефреш** списка агентов каждые 30 сек
- **Объединить** AgentListActivity + RemoteAgentActivity (убрать дублирование)
- **AgentSettingsActivity** — полноценные настройки агента (server URL, token, capabilities)

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
