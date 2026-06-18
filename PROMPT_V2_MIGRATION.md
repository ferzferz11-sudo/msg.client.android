# Промт для сервера: v1/v2 миграция AuthInterceptor

Сделай AuthInterceptor обратно совместимым с v1 клиентами.

## Проблема

v2 сервер требует JWT Bearer token на всех unary-вызовах (GetChats, GetProfile, etc.), но v1 клиенты (которые ещё не обновились) не отправляют Bearer token → получают Unauthenticated → пустой список чатов, нет профиля.

## Решение в auth_interceptor.go

1. В `AuthInterceptor`: если Bearer token отсутствует, попробовать fallback на username-based auth:
   - Прочитать username из gRPC metadata (ключ "username")
   - Если username найден и валиден → вставить userID/username в context и пропустить запрос
   - Если username не найден → вернуть Unauthenticated как раньше

2. `AuthStreamInterceptor` уже пропускает Chat/Typing/CallSession — это ОК, не трогай.

3. Для `GetChats` specifically: в `server_chats.go::GetChats` — если `GetUserID(ctx)` пустой, fallback на `req.Username` (как уже сделано в v1 версии). Это уже работает, но AuthInterceptor блокирует запрос ДО вызова handler.

Ключевой момент: fallback на username должен работать ТОЛЬКО для v1 клиентов. v2 клиенты (с JWT) должны продолжать работать через Bearer token как раньше.

Подход: в AuthInterceptor, если Bearer token отсутствует, попробовать `extractUsernameFromMetadata(ctx)`. Если username есть → вставить в context. Если нет → Unauthenticated.

Также: в `GetChatsV2` (`server_chatlist_v2.go`) — если `GetUserID(ctx)` пустой, использовать `req.Username` как fallback (как в v1 `GetChats`).

После изменений — пересобрать и задеплоить сервер.
