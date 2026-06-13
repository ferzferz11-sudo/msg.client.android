# Промпт для новой сессии — v1.1.3.7 (Android)

**Дата:** 2026-06-13
**Версия:** v1.1.3.7
**Ветка:** feat/1.1.3.x
**Текущая версия APK:** v1.1.3.7 (выпущен, ferz собирает локально)

---

## СТАТУС

- Streaming результатов задач работает (сервер + клиент)
- ErrorHandler — единый обработчик ошибок с AppLog
- AppLog.error() во всех catch-блоках с Toast
- Fix: "Job was cancelled" тост больше не появляется
- ✅ Исправлен баг: `import HermesGrpc` → удалён из RemoteAgentService.kt

---

## ✅ Что сделано в v1.1.3.7

### Streaming
- `MessengerProto.kt`: `DeployAgentTaskStreamResponseProto`
- `HermesGrpc.kt`: `deployAgentTaskStream()` → callbackFlow
- `GrpcClient.kt`: `deployAgentTaskStream()` facade
- `RemoteAgentViewModel.kt`: `sendMessageStreaming()` — real-time Flow collection
- `RemoteAgentActivity.kt`: использует sendMessageStreaming

### Error Handling
- `ErrorHandler.kt` — единый обработчик (CancellationException→INFO, остальное→ERROR)
- `AppLog.error()` / `AppLog.warn()` во всех catch-блоках с Toast ошибками
- Fix: CancellationException в sendMessage → не показывает тост, логирует как INFO

---

## КРИТИЧЕСКИЕ ФАЙЛЫ

### UI
- `ui/remote/RemoteAgentActivity.kt` — чат с агентом (streaming mode)
- `ui/remote/RemoteAgentSettingsActivity.kt` — настройки + SSH туннель
- `ui/remote/RemoteAgentViewModel.kt` — ViewModel + sendMessageStreaming

### Сервисы
- `ui/remote/RemoteAgentService.kt` — foreground service
- `ui/remote/RemoteAgentManager.kt` — singleton manager

### gRPC / Proto
- `data/grpc/HermesGrpc.kt` — gRPC методы (unary + streaming)
- `data/grpc/GrpcClient.kt` — фасад
- `data/proto/MessengerProto.kt` — proto типы

### Error Handling
- `data/models/ErrorHandler.kt` — единый обработчик ошибок
- `data/models/AppLog.kt` — глобальный логгер

---

## ПРАВИЛА
- НЕ assembleRelease на сервере (OOM)
- Коммитить/пушить после каждого изменения
- Версию НЕ менять без указания пользователя

## ДОКУМЕНТАЦИЯ
- `doc/PROMPT_ANDROID.md` → `doc/TASKS.md` → `doc/INDEX.md`
- `doc/REMOTE_AGENT.md` — Remote Agent документация
- `doc/STRUCTURE.md` — структура кода
