# Lavender Messenger — Известные проблемы и задачи в работе

**Последнее обновление:** 2026-06-01
**Ветка:** feat/1.1.0.x
**Версия:** 1.1.0.8

---

## 🔧 В процессе

### 1. WebRTC: звонки не соединяются
- **Статус:** частично исправлено
- **Что сделано:**
  - handleAbruptDisconnect теперь отправляет HANGUP собеседнику
  - Добавлен connection timeout (30s) в CallActivity
  - Добавлен ICE connection state handling
  - Исправлен senderId (UUID вместо username) в call signals
  - BroadcastCall fallback по username
- **Что осталось:**
  - TURN сервер для NAT traversal (coturn)
  - FCM key обновление (ждём пользователя)
- **Файлы:** server.go, db.go, hub.go, CallActivity.kt, CallManager.kt, WebRtcClient.kt

### 2. FCM: Invalid JWT Signature
- **Статус:** ждём новый Firebase key от пользователя
- **Описание:** Push уведомления не работают, звонки не пробуждают устройство
- **Решение:** пользователь создаст завтра новый key в Firebase Console

---

## ✅ Исправлено

### 1. Звонки: abrupt disconnect → HANGUP
- handleAbruptDisconnect отправляет HANGUP собеседнику
- Сообщение "Соединение потеряно" сохраняется в чат

### 2. Звонки: connection timeout
- 30s timeout для исходящих звонков
- ICE FAILED → автоматический hangup

### 3. Звонки: senderId/UUID путаница
- CallManager.sendWebRtcSignal теперь использует UUID

### 4. Сервер: BroadcastCall fallback
- BroadcastCall теперь ищет по ReceiverId ИЛИ ReceiverName

---

## 📋 Бэклог

### Высокий приоритет
- [ ] TURN сервер для WebRTC (coturn)
- [ ] FCM key update (ждём пользователя)
- [ ] Обновить ServerVersion до 1.1.0.8

### Средний приоритет
- [ ] Секретный чат — заглушка "not implemented in this build"
- [ ] Медленная загрузка "Избранное"
- [ ] Graceful shutdown сервера
- [ ] Structured logging (zap/logrus)

### Низкий приоритет
- [ ] Рефакторинг server.go → пакеты
- [ ] Rate limiting на сервере
- [ ] Кэширование запросов чатов
- [ ] OWL: поле ввода перекрывает кнопки навигации

---

## 🔑 Ключевые решения

| Решение | Обоснование |
|---------|-------------|
| OWL чаты хранятся в `chats` с `type='owl'` | Единая таблица, не нужна отдельная |
| Participants формат: `["username"]` JSON array | Совместимость с существующим парсером |
| `ThemeUi.bind()` для тем | Единообразие с остальным приложением |
| `adjustResize` + `translationY` | Edge-to-edge конфликт с adjustResize |
| `CoroutineScope` вместо `lifecycleScope` для `loadHistory()` | Предотвращает отмену корутины при смене activity |

---

## 📁 Ключевые файлы

| Файл | Назначение |
|------|------------|
| `/root/msg/server.go` | Сервер, версия 1.1.0.4 |
| `/root/msg/server/owl.go` | OWL сессии и БД |
| `/root/msg/messenger.proto` | gRPC определения |
| `OwlActivity.kt` | OWL чат UI |
| `OwlGrpc.kt` | gRPC вызовы OWL |
| `RealGrpcClient.kt` | gRPC канал и reconnect |
| `GrpcClient.kt` | Фасад для gRPC |
| `ChatListActivity.kt` | Список чатов, `createNewOwlChat()` |
| `ThemeApplier.kt` | Применение тем, `enableEdgeToEdge()` |
| `activity_owl.xml` | Layout OWL чата |
| `AndroidManifest.xml` | `windowSoftInputMode` для OWL |
