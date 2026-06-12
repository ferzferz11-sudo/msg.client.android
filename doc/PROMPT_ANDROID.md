# Промпт для новой сессии — v1.1.3.5 (Android)

**Дата:** 2026-06-13
**Версия:** v1.1.3.4
**Ветка:** feat/1.1.3.x
**Текущая версия APK:** v1.1.3.4 (выпущен)
**GitHub релиз:** https://github.com/ferzferz11-sudo/msg.client.android/releases/tag/v1.1.3.4

---

## СТАТУС

- Remote Agent UI реализован и работает
- Token Management (генерация, список, отзыв, копирование) — работает
- HermesGrpc — все методы реализованы
- **Hermes Gateway** — SSH туннель работает (JSch)
- **tunnel_mode** — передаётся в DeployAgentTask
- **Проблема:** при входе/выходе из Activity подключение к агенту теряется

---

## 🔴 Приоритетная задача: Remote Agent — фоновое подключение

### Проблема
SSH туннель и gRPC подключение привязаны к Activity lifecycle.
При переходе между Activity или повороте экрана — всё теряется.

### Решение: Foreground Service + Singleton Manager

1. **`RemoteAgentService.kt`** — foreground service
   - Управляет SSH туннелем через `HermesGatewayManager`
   - Держит gRPC подключение
   - Уведомление с статусом подключения
   - `START_STICKY` — перезапускается системой

2. **`RemoteAgentManager.kt`** — singleton
   - `bindService()` / `unbindService()` из Activity
   - `isConnected()` / `sendTask()` / `getStatus()`
   - Callback для результатов задач

3. **Activity привязываются к сервису** через `ServiceConnection`
   - `RemoteAgentSettingsActivity` — настройки + статус
   - `RemoteAgentActivity` — чат + отправка задач

### Файлы
- `ui/remote/RemoteAgentService.kt` — НОВЫЙ
- `ui/remote/RemoteAgentManager.kt` — НОВЫЙ (singleton)
- `ui/remote/RemoteAgentSettingsActivity.kt` — обновить привязку
- `ui/remote/RemoteAgentActivity.kt` — обновить привязку
- `AndroidManifest.xml` — добавить сервис и разрешения

### Ключевые моменты
- `unbindService()` при уничтожении Activity, но сервис продолжает работать
- Сервис останавливается явно через кнопку "Отключить"
- Уведомление: "Агент подключён к 13.140.25.249:50051" / "Отключено"

---

## ЧТО СДЕЛАНО В v1.1.3.4

- `HermesGatewayManager.kt` — SSH туннель через JSch
- `RemoteAgentSettingsActivity.kt` — UI "Подключение через шлюз"
- `activity_remote_agent_settings.xml` — layout с полями
- `MessengerProto.kt` — tunnel_mode поля
- `HermesGrpc.kt` — сериализация tunnel_mode
- JSch зависимость (`com.jcraft:jsch:0.1.55`)
- Понятные ошибки (SSH alias vs IP, auth failed, timeout)

---

## КРИТИЧЕСКИЕ ФАЙЛЫ

### UI
- `ui/remote/RemoteAgentActivity.kt` — чат с агентом
- `ui/remote/RemoteAgentSettingsActivity.kt` — настройки + SSH туннель
- `ui/remote/RemoteAgentViewModel.kt` — ViewModel

### Сервисы (новые)
- `ui/remote/RemoteAgentService.kt` — foreground service
- `ui/remote/RemoteAgentManager.kt` — singleton manager

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
