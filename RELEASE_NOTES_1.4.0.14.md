# Release Notes — Lavender Messenger v1.4.0.14

**Дата:** 2026-08-16
**Фокус:** Auth Resilience, Accessibility, Bug Fixes

---

## Исправлено

### CRITICAL: UNAUTHENTICATED cascade
Токен протухал → `markRead` и `loadHistoryV2` получали UNAUTHENTICATED ошибки 10+ минут без восстановления. Периодический refresh (каждые 60с) не успевал — unary вызовы не ретраили.

**Исправлено:**
- `markRead` и `loadHistoryV2` теперь ретраят один раз с `ensureFreshToken()` при UNAUTHENTICATED
- `ensureFreshToken()` делает fallback на re-login с сохранённым паролем если refresh token протух
- Паттерн аналогичен `loadAllUsers`/`setMutedChat` в `GrpcChatAuxClient`

### CRITICAL: Saved Messages не сохранялись на сервере
Сообщения отправлялись через `SendMessageV2` с `roomId=saved_messages_{username}`, но сервер не мог нормализовать roomId.

**Исправлено (сервер v1.4.0.5):**
- Нормализация `saved_messages_{username}` → `saved_messages_{userId}` в `handleSavedMessagesSend`
- Добавлено логирование на клиенте для трассировки отправки

### MEDIUM: Системные сообщения в превью чат-листа
Таймер авто-удаления (🔥) и звонки (📹📞) показывались как last message в списке чатов.

**Исправлено:**
- Клиент: фильтрация системных сообщений в `ChatListViewModel.newMessageEvent` и `ChatAdapter`
- Сервер v1.4.0.5: фильтр системных сообщений из `last_message_text`

---

## Улучшено

### Accessibility — touch targets (P2)
22 интерактивных элемента увеличены до 48dp minimum в 14 layout файлах:
- Кнопки редактирования, удаления, отмены (24dp → 48dp)
- Кнопки Remote Agent, Invite Code (36dp → 48dp)
- Выбор цвета в Sticker Editor (36dp → 48dp)
- Saved Messages star, Conference edit, Audio cancel (40dp → 48dp)

### PluralsCandidate lint (P2)
2 false positive подавлены (`tools:ignore`):
- `online_count_format` — unused string с двумя %d
- `ssh_port_in_use` — "port %d" means "port number N", not "N ports"

---

## Тесты

- 7 новых тестов для `isSystemMessage` (ChatAdapterTest)
- 3 новых теста для `SendMessageV2RequestProto` с `saved_messages_*` roomId (GrpcMarshallersTest)
- Все 370+ тестов проходят

---

## Сервер

Требуется сервер v1.4.0.5+:
- Фильтр системных сообщений из `last_message_text`
- Нормализация `saved_messages` roomId
- gRPC keepalive tuning (MaxConnectionAge 30m→2h)

---

## Файлы

### Изменено (клиент)
- `RealGrpcClient.kt` — UNAUTHENTICATED retry для markRead
- `GrpcMessageV2Client.kt` — UNAUTHENTICATED retry для loadHistoryV2, refreshToken callback, sendMessageV2 logging
- `SessionManager.kt` — ensureFreshToken fallback на re-login
- `ChatListViewModel.kt` — isSystemMessage filter в newMessageEvent
- `ChatAdapter.kt` — isSystemMessagePreview filter для lastMessageText
- 14 layout XML файлов — touch target fixes

### Тесты
- `ChatAdapterTest.kt` — 7 isSystemMessage tests
- `GrpcMarshallersTest.kt` — 3 sendMessageV2 saved_messages tests
