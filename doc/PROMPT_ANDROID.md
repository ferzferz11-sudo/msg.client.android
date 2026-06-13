# Промпт для новой сессии — v1.1.3.7 (Android)

**Дата:** 2026-06-13
**Версия:** v1.1.3.7
**Ветка:** feat/1.1.3.x

---

## СТАТУС: v1.1.3.7 — P0 ИСПРАВЛЕНИЯ ВНЕСЕНЫ, НУЖНА ПРОВЕРКА КОМПИЛЯЦИИ

### Что исправлено:

1. **"Агент не выбран"** — добавлен `ensureAgentSelected()` в `RemoteAgentViewModel`
2. **Status bar** — ConstraintLayout + фиксированные кнопки + контрастный текст
3. **"Job was cancelled"** — подавлен (loadAgents не пишет в _error)
4. **Сервер** — Remote Agent RPC вынесен в `server_remote.go`

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
