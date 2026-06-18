# Prompt: Android Client — Next Session

**Версия:** v1.2.0.5 → v1.2.0.6 | **Ветка:** feat/1.2.0.x | **Дата:** 2026-06-18

---

## Контекст предыдущей сессии

### Что исправлено (серверная сторона)
1. **GetChatsV2 RPC отсутствовал в proto** — клиент вызывал `messenger.ChatService/GetChatsV2`, но proto содержал только `GetChats`. Сервер возвращал UNIMPLEMENTED → пустой список чатов. Исправлено: добавлен `rpc GetChatsV2` в messenger.proto, перегенерирован Go код.
2. **GetUserChatsV2 WHERE clause bug** — запрос искал по UUID в `participants`, но participants содержат usernames. Исправлено: используется `participants::jsonb @> jsonb_build_array($4::text)` с username.
3. Серверные коммиты: `b5367e9`, `d3719bf` на `feat/1.2.0.x`

### Что в работе на сервере (другой агент)
- Миграция `chats.participants` → добавление `participant_user_ids` (JSONB UUID массив). Промпт: `/root/msg/doc/PROMPT_USERID_MIGRATION.md`

---

## Задачи на сессию

### Приоритет 1: Отладка и стабильность

#### 1.1 SuperAdmin кнопка — ✅ ИСПРАВЛЕНО (v1.2.0.7)
- ProfileClient.unaryCall() рефлексия заменена на GetProfileResponseMarshaller

#### 1.2 GetChatsV2 — ✅ ПРОВЕРЕНО
- Поток корректный: GrpcChatClient.getChats() → GetChatsV2 → marshallers с v2 полями

#### 1.3 Auto-login с протухшим JWT — ✅ ПРОВЕРЕНО
- Нет риска infinite loop

### Приоритет 2: Баги найдены пользователем
- Описать найденные баги в следующей сессии

---

### Приоритет 2: Архитектура (если время будет)

#### 2.1 ViewModel для NewChatActivity
- Вынести бизнес-логику из Activity в ViewModel
- Activity только UI + наблюдение за StateFlow
- Оценка: ~2h

#### 2.2 ViewModel для ProfileActivity
- Аналогично NewChatActivity
- Оценка: ~2h

---

## Правила (обязательно к прочтению)

1. **НЕ компилировать Android на сервере** (OOM kill) —assembleRelease ТОЛЬКО локально
2. **НЕ деплоить на prod** без явного указания
3. userId (UUID) — всегда как ключ, НЕ username
4. Все новые строки ОДНОВРЕМЕННО в `values/strings.xml` + `values-ru/strings.xml`
5. getString() НЕ в полях Activity — только в методах
6. Kotlin 2.3.21: `cont.resume(value, onCancellation = {})`
7. Все ошибки через `ErrorHandler.handle()` — НЕ `Log.e`
8. v2 server only — никаких v1 fallbacks
9. Chat toolbar: фиксированная высота `@dimen/custom_toolbar_height`, elevation 0dp
10. Все chat activities: `setDecorFitsSystemWindows(window, false)` в onCreate
11. Marshallers: всегда включать v2 proto поля
12. JWT freshness: `ensureFreshToken()` перед Chat stream

---

## Сервер

| | Dev | Prod |
|--|-----|------|
| gRPC | 50052 | 50051 |
| HTTP | 8083 | 8082 |
| Сервис | lavender-server-dev | lavender-server |

**Деплой сервера:** НЕ делать — другой агент управляет сервером. Если нужен серверный фикс — написать промпт-файл в `/root/msg/doc/`.

---

## Архитектура клиента

```
GrpcClient (facade) → RealGrpcClient (orchestrator)
  ├── GrpcConnectionManager — connect/reconnect
  ├── GrpcAuthClient — JWT auth (v2 only)
  ├── GrpcChatClient — getChats, create/delete, participants
  ├── GrpcChatListV2Client — pin/unpin, search, archive
  ├── GrpcChatAuxClient — users, AI, FCM, mute
  ├── GrpcProfileClient — profile, avatar, contacts, themes
  ├── GrpcDraftClient, GrpcFavoritesClient, GrpcMessageClient
  └── GrpcMarshallers (~1500 LOC)

ChatListActivity → 10 modules (toolbar, tabs, FABs, auth, etc.)
NewChatActivity → 6 delegates (toolbar, input, selection, search, E2EE, menu)
```

---

## Полезные ссылки

- Документация клиента: `doc/INDEX.md`, `doc/PATTERNS.md`, `doc/PLAN.md`
- Документация сервера: `/root/msg/doc/INDEX.md`
- Промпт миграции userId: `/root/msg/doc/PROMPT_USERID_MIGRATION.md`
- v1 reference: `doc/ChatListActivity_v1_REFERENCE.kt`
