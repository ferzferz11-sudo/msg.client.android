# Android клиент — Промпт для новой сессии

Текущая версия: v1.1.3.0 (dev)
Ветка: feat/1.1.3.x

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

## ТЕКУЩАЯ ЗАДАЧА: Интеграция агента с реальным бэкендом

### Что уже сделано (v1.1.2.x → merged in master):
- ✅ Proto классы + gRPC методы (токены, агенты)
- ✅ Серверная часть (Generate/Revoke/List tokens)
- ✅ RemoteAgentActivity (чат, статус, toolbar)
- ✅ RemoteAgentSettingsActivity (управление токенами)
- ✅ TokenDialog, AIBottomSheet интеграция
- ✅ Кастомные темы

### Что нужно сделать в v1.1.3.x:
- ⬜ Интеграция чата агента с реальным бэкендом (сейчас echo-заглушка в RemoteAgentViewModel.sendMessage)
- ⬜ Отправка задач агенту через gRPC streaming (Connect + OrchestratorMessage)
- ⬜ Получение результатов в реальном времени
- ⬜ Отображение типов задач (shell, git, build, deploy, docker, ai)
- ⬜ Статус подключения агента (heartbeat)
- ⬜ Тестирование

### Правила:

**RemoteAgentActivity** — отдельный экран (НЕ в списке чатов):
- Toolbar: "Агенты" + статус (подключён/отключён) + шестерёнка → настройки
- Чат: сообщения пользователя + ответы агента
- Без кнопок токенов (вынесены в RemoteAgentSettingsActivity)

**RemoteAgentSettingsActivity** — управление токенами:
- Список активных токенов (card с именем, хэшем, capabilities, сроком)
- Кнопка "Сгенерировать новый токен" → TokenDialog
- Кнопка "Отозвать" на каждом токене

**TokenDialog** — диалог генерации токена:
- Поля: Agent Name, Capabilities (мультивыбор), TTL
- Результат: токен (показать один раз + копировать в буфер)

**AIBottomSheet** — пункт "🖥 Агенты" → RemoteAgentActivity

### Правила:
- Стиль кода как в существующих файлах
- ViewBinding (не findViewById)
- Корутины + lifecycleScope
- DiffUtil для адаптера чата
- Тема через ThemeStore (программно, не XML ?attr)
- **НЕ запускать assembleRelease на сервере** (OOM, нужно 2GB+)
- **НЕ запускать compileDebugKotlin без крайней необходимости** — сначала `free -h`, если < 2GB free → НЕ запускать
- **НЕ запускать никакие ./gradlew задачи** если память < 2GB free
- Если нужно проверить синтаксис — делай это через чтение файлов и анализ кода, а не через компиляцию на сервере

---

## ВАЖНО

- Package: `lavender.client.android` (НЕ ru.lavender.messenger)
- Proto: ручные data classes в MessengerProto.kt (НЕ генерируются)
- version.txt обновлять ДО release.sh
- Коммитить и пушить после каждого значимого изменения
