# Lavender Messenger — Известные проблемы и задачи в работе

**Последнее обновление:** 2026-06-03
**Ветка:** feat/1.1.0.x
**Версия:** 1.1.0.9

---

## 🔴 В процессе

### 1. Секретные чаты — сообщения видны в логах сервера
- **Статус:** не исправлено
- **Проблема:** Сообщения секретных чатов логируются в открытом виде на сервере
  - `[ferz] in secret_xxx: Едешь?` — текст сообщения виден в journalctl
  - Это нарушает приватность E2EE
- **Причина:** Сервер логирует сообщения до шифрования при сохранении в БД и отправке push
- **Решение:** Не логировать текст сообщений секретных чатов. Лог должен содержать только: chat_id, sender, message_id (без тела сообщения)

### 2. GrpcClient — Delicate API warnings
- **Статус:** не исправлено
- **Проблема:** 3 warning'а при сборке:
  - `GrpcClient.kt:345` — delicate API
  - `GrpcClient.kt:357` — delicate API
  - `GrpcClient.kt:364` — delicate API

### 3. Секретные чаты — интеграция и улучшения
- **Статус:** не начато
- **Задача:** Проверить и улучшить работу секретных чатов (E2EE)

### 4. WebRTC — тестирование TURN
- **Статус:** ждёт тестирования пользователем
- **Что проверить:** Звонки из разных сетей (WiFi ↔ мобильный интернет)

---

## ✅ Исправлено (v1.1.0.9)

### WebRTC — TURN сервер
- Coturn установлен на сервере, порт 3478
- `/turn-credentials` endpoint на HTTP 8082
- HMAC-based временные креденшалы (TTL 24h)
- CallActivity: fetchTurnCredentials() + fallback STUN
- WebRtcClient: убран дубликат PeerConnection.Observer

### OWL AI
- Обновлён OpenRouter API ключ, OWL работает

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
| TURN (coturn) для WebRTC | NAT traversal для звонков из разных сетей |
| HMAC-based креденшалы | Безопаснее чем статический пароль |
| Не логировать текст E2EE сообщений | Приватность секретных чатов |

---

## 📁 Ключевые файлы

| Файл | Назначение |
|------|------------|
| `/root/msg/server.go` | Сервер, версия 1.1.0.9 |
| `/root/msg/http_server.go` | HTTP endpoints, /turn-credentials |
| `/root/msg/server/secret_chat.go` | Секретные чаты |
| `CallActivity.kt` | Звонки UI, fetchTurnCredentials() |
| `WebRtcClient.kt` | WebRTC клиент |
| `GrpcClient.kt` | gRPC клиент (delicate API warnings) |
