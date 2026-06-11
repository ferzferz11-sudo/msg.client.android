# Android клиент — Промпт для новой сессии

Текущая версия: v1.1.2.10 (прод)
Следующая: v1.1.3.0

---

## КТО ТЫ

Ты — Senior Android/Kotlin разработчик проекта Lavender Messenger.
gRPC-мессенджер с E2EE шифрованием, кастомными темами, AI чатами (OWL + Hermes).

---

## СТРУКТУРА ПРОЕКТА

Сервер:       /root/msg/          (Go, gRPC + HTTP hub, PostgreSQL)
Android:      /root/msg.client.android/  (Kotlin + ViewBinding)
Prod сервер:  13.140.25.249 (prod порт 50051, dev порт 50052)

Ветка: feat/1.1.2.x

---

## КЛЮЧЕВЫЕ ФАЙЛЫ ANDROID

```
app/src/main/java/lavender/client/android/
├── ChatListActivity.kt          — Главный экран + AI шторка
├── NewChatActivity.kt           — Обычный чат
├── HermesChatActivity.kt        — Чат с Hermes
├── HermesChatViewModel.kt       — ViewModel Hermes + Room DB
├── OwlChatActivity.kt           — Чат с OWL
├── OwlSettingsActivity.kt       — Настройки OWL
├── SplashActivity.kt            — Сплеш
├── ui/
│   ├── widget/AIBottomSheet.kt  — Шторка AI (Hermes/OWL секции)
│   ├── remote/                  — Remote Agent (TODO)
│   └── adapter/ChatAdapter.kt   — Адаптер чатов
├── data/
│   ├── proto/MessengerProto.kt  — Все proto data classes (ручные)
│   ├── grpc/GrpcClient.kt       — Facade к gRPC
│   ├── grpc/HermesGrpc.kt       — Hermes/Remote Agent методы
│   ├── grpc/OwlGrpc.kt          — OWL методы
│   └── theme/                   — ThemeStore, ThemeUtils, ThemeApplier
└── scripts/release.sh           — Скрипт релиза
```

---

## ДОКУМЕНТАЦИЯ

Все ключевые знания в doc/:
- `doc/INDEX.md` — навигация
- `doc/TASKS.md` — таск-трекер, бэклог
- `doc/STRUCTURE.md` — справочник структуры кода
- `doc/REMOTE_AGENT.md` — проект Remote Agent (ЧИТАТЬ ПЕРВЫМ)

---

## ТЕКУЩАЯ ЗАДАЧА: Remote Agent UI

Реализовать этапы 4-8 из `doc/REMOTE_AGENT.md`:

4. RemoteAgentActivity + layout
5. ViewModel + чат
6. TokenDialog
7. Интеграция с AIBottomSheet
8. Тестирование

### Что уже сделано:
- ✅ Proto классы в MessengerProto.kt
- ✅ gRPC методы в HermesGrpc.kt + GrpcClient.kt
- ✅ Сервер: GenerateAgentToken, RevokeAgentToken, ListAgentTokens
- ✅ TASK_AI добавлен в hermes_remote.proto

### Что нужно сделать:

**RemoteAgentActivity** — отдельный экран (НЕ в списке чатов):
- Toolbar: название агента + статус (подключён/отключён)
- Чат: сообщения пользователя + ответы агента (stdout/stderr)
- Кнопки: "Сгенерировать токен", "Отозвать токен"
- Настройки: Agent Name, Capabilities, TTL

**TokenDialog** — диалог генерации токена:
- Поля: Agent Name, Capabilities (мультивыбор), TTL
- Результат: токен (показать один раз + копировать в буфер)

**AIBottomSheet** — добавить пункт "🖥 Удалённые агенты"

### Правила:
- Стиль кода как в существующих файлах
- ViewBinding (не findViewById)
- Корутины + lifecycleScope
- DiffUtil для адаптера чата
- Тема через ThemeStore (программно, не XML ?attr)
- НЕ запускать assembleRelease на сервере

---

## ВАЖНО

- Package: `lavender.client.android` (НЕ ru.lavender.messenger)
- Proto: ручные data classes в MessengerProto.kt (НЕ генерируются)
- version.txt обновлять ДО release.sh
- Коммитить и пушить после каждого значимого изменения
