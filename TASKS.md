# Lavender Messenger — Известные проблемы и задачи в работе

**Последнее обновление:** 2026-06-03
**Ветка:** feat/1.1.0.x
**Версия:** 1.1.0.8

---

## 🔴 В процессе

### 1. WebRTC: TURN сервер для NAT traversal
- **Статус:** не начато
- **Проблема:** Звонки работают только в одной сети (WiFi). Из разных сетей — нет соединения.
- **Решение:** Установить и настроить coturn на сервере 13.140.25.249
- **Что сделано ранее по звонкам:**
  - handleAbruptDisconnect → HANGUP собеседнику
  - Connection timeout 30s в CallActivity
  - ICE connection state handling (FAILED → auto hangup)
  - senderId = UUID вместо username
  - BroadcastCall fallback по username
  - Убран дубликат PeerConnection.Observer в WebRtcClient
- **Файлы:** server.go, db.go, hub.go, CallActivity.kt, CallManager.kt, WebRtcClient.kt

---

## ✅ Исправлено

### WebRTC звонки — базовая функциональность
- HANGUP при abrupt disconnect
- Connection timeout 30s
- ICE state monitoring (FAILED → hangup)
- UUID senderId
- BroadcastCall fallback
- WebRtcClient: убран дубликат Observer

### FCM Push
- Обновлён firebase key, push работают

### Secret Chat (E2EE)
- Заглушка "not implemented" убрана, чаты работают

### ChangelogActivity
- Locale(ru) → Locale.forLanguageTag(ru)
- bg_release_card: hardcoded color вместо ?attr/colorSurfaceVariant
- Белый экран — требует logcat для диагностики

---

## 📋 Бэклог

### Средний приоритет
- [ ] Graceful shutdown сервера
- [ ] Structured logging (zap/logrus)

### Низкий приоритет
- [ ] Рефакторинг server.go → пакеты
- [ ] Rate limiting на сервере
- [ ] Кэширование запросов чатов

---

## 🔑 Ключевые решения

| Решение | Обоснование |
|---------|-------------|
| OWL чаты хранятся в `chats` с `type='owl'` | Единая таблица, не нужна отдельная |
| Participants формат: `["username"]` JSON array | Совместимость с существующим парсером |
| `ThemeUi.bind()` для тем | Единообразие с остальным приложением |
| `adjustResize` + `updateLayoutParams` | Правильная обработка клавиатуры |
| `CoroutineScope` вместо `lifecycleScope` для `loadHistory()` | Предотвращает отмену корутины |
| TURN (coturn) для WebRTC | NAT traversal для звонков из разных сетей |

---

## 📁 Ключевые файлы

| Файл | Назначение |
|------|------------|
| `/root/msg/server.go` | Сервер, версия 1.1.0.8 |
| `/root/msg/server/owl.go` | OWL сессии и БД |
| `/root/msg/messenger.proto` | gRPC определения |
| `OwlActivity.kt` | OWL чат UI |
| `CallActivity.kt` | Звонки UI |
| `CallManager.kt` | Управление звонками |
| `WebRtcClient.kt` | WebRTC клиент |
| `RealGrpcClient.kt` | gRPC канал и reconnect |
