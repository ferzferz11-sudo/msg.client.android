# Промпт для новой сессии — v1.1.3.5 (Android)

**Дата:** 2026-06-13
**Версия:** v1.1.3.5
**Ветка:** feat/1.1.3.x
**Текущая версия APK:** v1.1.3.4 (выпущен, ferz собирает локально)

---

## СТАТУС

- Remote Agent UI реализован и работает
- Token Management (генерация, список, отзыв, копирование) — работает
- HermesGrpc — все методы реализованы
- **Hermes Gateway** — SSH туннель работает (JSch)
- **tunnel_mode** — передаётся в DeployAgentTask
- **RemoteAgentService** — foreground service создан и работает
- **RemoteAgentManager** — singleton для привязки UI к сервису

---

## ✅ Что сделано в v1.1.3.5

### Foreground Service + Singleton Manager
- `RemoteAgentService.kt` — foreground service с SSH туннелем + gRPC
- `RemoteAgentManager.kt` — singleton для bind/unbind UI к сервису
- `RemoteAgentSettingsActivity.kt` — привязка к сервису через ServiceConnection + RemoteAgentStateListener
- `RemoteAgentActivity.kt` — привязка к сервису через ServiceConnection + RemoteAgentStateListener
- `AndroidManifest.xml` — добавлен RemoteAgentService + FOREGROUND_SERVICE_CONNECTED_DEVICE
- `RemoteAgentViewModel.kt` — tunnel check через RemoteAgentManager

### Архитектура persistent connection

```
┌─────────────────────────────────────────────────────────────┐
│                    RemoteAgentService                        │
│                    (Foreground Service)                      │
│                                                             │
│  ┌─────────────────┐  ┌──────────────────┐                 │
│  │ HermesGateway   │  │ GrpcClient       │                 │
│  │ Manager         │  │ (persistent)     │                 │
│  │ (SSH tunnel)    │  │                  │                 │
│  └────────┬────────┘  └────────┬─────────┘                 │
│           │                    │                            │
│  ┌────────┴────────────────────┴─────────┐                 │
│  │         RemoteAgentManager            │                 │
│  │         (singleton, binds to App)      │                 │
│  └───────────────────────────────────────┘                 │
└─────────────────────────────────────────────────────────────┘
           │                              │
           │ ServiceConnection            │ ServiceConnection
           ▼                              ▼
┌──────────────────────┐    ┌──────────────────────────┐
│ RemoteAgentSettings  │    │ RemoteAgentActivity      │
│ Activity             │    │ (чат с агентом)          │
│ (настройки туннеля)  │    │                          │
└──────────────────────┘    └──────────────────────────┘
```

---

## КРИТИЧЕСКИЕ ФАЙЛЫ

### UI
- `ui/remote/RemoteAgentActivity.kt` — чат с агентом
- `ui/remote/RemoteAgentSettingsActivity.kt` — настройки + SSH туннель
- `ui/remote/RemoteAgentViewModel.kt` — ViewModel

### Сервисы
- `ui/remote/RemoteAgentService.kt` — foreground service (new in v1.1.3.5)
- `ui/remote/RemoteAgentManager.kt` — singleton manager (new in v1.1.3.5)

### gRPC / Proto
- `data/grpc/HermesGrpc.kt` — gRPC методы
- `data/grpc/GrpcClient.kt` — фасад
- `data/proto/MessengerProto.kt` — proto типы

---

## ПРАВИЛА
- НЕ assembleRelease на сервере (OOM)
- Коммитить/пушить после каждого изменения
- Версию НЕ менять без указания пользователя

## ДОКУМЕНТАЦИЯ
- `doc/PROMPT_ANDROID.md` → `doc/TASKS.md` → `doc/INDEX.md`
- `doc/REMOTE_AGENT.md` — Remote Agent документация
- `doc/STRUCTURE.md` — структура кода
