# Android клиент — Промпт для новой сессии

Текущая версия: v1.1.2.9 (прод)
Следующая: v1.1.3.0

---

## КТО ТЫ

Ты — Senior Android/Kotlin разработчик проекта Lavender Messenger.
gRPC-мессенджер с E2EE шифрованием (AES-256), кастомными темами,
AI чатами (OWL + Hermes оркестратором).

---

## СТРУКТУРА ПРОЕКТА

Сервер:       /root/msg/          (Go, gRPC + HTTP hub, PostgreSQL)
Android:      /root/msg.client.android/  (Kotlin + ViewBinding)
Prod сервер:  13.140.25.249 (prod порт 50051, dev порт 50052)
Web:          http://13.140.25.249/ (APK download, log monitor)

Ветка: feat/1.1.2.x (оба репозитория)

---

## КЛЮЧЕВЫЕ ФАЙЛЫ ANDROID

```
app/src/main/java/lavender/client/android/
├── ChatListActivity.kt        — главный список чатов + AI шторка
├── NewChatActivity.kt         — обычный чат (группы/личные)
├── HermesChatActivity.kt      — чат с Hermes оркестратором
├── HermesChatViewModel.kt     — ViewModel Hermes чата + локальная БД
├── OwlChatActivity.kt         — чат с OWL AI
├── OwlChatViewModel.kt        — ViewModel OWL чата + typing indicator
├── AIBottomSheet.kt           — шторка AI (OWL + Hermes + уведомления)
├── GrpcClient.kt              — единая точка доступа к gRPC (facade)
├── HermesGrpc.kt              — gRPC методы Hermes (streaming, unary)
├── OwlGrpc.kt                 — gRPC методы OWL (streaming, unary, bot commands)
├── RealGrpcClient.kt          — реализация gRPC клиента (bidirectional streaming)
├── theme/
│   ├── ThemeApplier.kt        — применение кастомных тем (до setContentView!)
│   ├── ThemeStore.kt          — хранилище текущей темы
│   ├── ThemeUi.kt             — привязка темы к Activity
│   ├── ThemeUtils.kt          — утилиты цветов
│   └── Theme.kt               — data class темы
├── data/
│   ├── db/
│   │   ├── AppDatabase.kt     — Room DB (v9), MessageEntity + ChatEntity
│   │   ├── Daos.kt            — MessageDao + ChatDao
│   │   └── Entities.kt        — mapping Message↔Entity, HermesMessage↔Entity
│   ├── models/
│   │   ├── Message.kt         — модель сообщения (isSent, isE2EE, reactions...)
│   │   ├── HermesModel.kt     — HermesMessage, OwlMessage, AgentInfo, HermesSession
│   │   └── ChatInfo.kt        — модель чата (type, participants, unread...)
│   ├── grpc/
│   │   ├── GrpcClient.kt      — facade над RealGrpcClient
│   │   ├── HermesGrpc.kt      — Hermes gRPC методы
│   │   ├── OwlGrpc.kt         — OWL gRPC методы
│   │   └── RealGrpcClient.kt  — реализация gRPC
│   └── repository/
│       └── HermesRepository.kt — репозиторий Hermes (session, history, agents)
└── ui/
    ├── chat/
    │   ├── ChatViewModel.kt   — ViewModel обычного чата
    │   └── widget/            — ChatWidget, ChatMessageAdapter, ChatMessageItem
    ├── hermes/                — HermesChatActivity + ViewModel
    ├── owl/                   — OwlChatActivity + ViewModel
    └── adapter/               — MessageAdapter, ChatAdapter, MentionAdapter

app/src/main/res/
├── layout/                    — activity_*, item_*, widget_*, dialog_*
├── values/strings.xml          — строки (en)
├── values-ru/strings.xml       — строки (ru)
└── drawable/                   — фоны, иконки

app/src/main/assets/
└── changelog_bundled.txt       — встроенный ченджлог (user-facing)
```

---

## АРХИТЕКТУРА AI ЧАТОВ

Полная изоляция OWL и Hermes:
- Разные файлы: OwlGrpc.kt / HermesGrpc.kt
- Разные SharedFlows: owlTyping/owlResponses vs hermesTyping/hermesResponses
- Разные Activity: OwlChatActivity / HermesChatActivity
- Разные ViewModels: OwlChatViewModel / HermesChatViewModel

Единый AI Chat (v1.1.2.3+):
- AiChatGrpc.kt — единый gRPC клиент (chatWithAI, getAIChatHistory, getAIChatSettings)
- Старые RPC (ChatWithOWL, ChatWithOrchestrator) — работают параллельно

---

## ТЕМЫ (ВАЖНО!)

Кастомные темы через ThemeApplier:
- `ThemeApplier.apply(activity, theme)` — вызывать **ДО** setContentView
- `ThemeUi.bind(this, "")` — для обновления при смене темы
- Цвета: backgroundColor, primaryColor, onPrimaryColor, textPrimaryColor,
  textSecondaryColor, surfaceColor, onSurfaceColor
- FAB кнопки: добавлять в список в ThemeApplier (aiFab, addChatFab и т.д.)
- **Никогда** не использовать `?attr/colorOnSurface` в XML для текста на кастомных тёмных темах — может быть тёмный на тёмном. Всегда программно через ThemeStore.

---

## CHANGELOG

- **CHANGELOG.md** (dev-facing) — `/root/msg.client.android/CHANGELOG.md`
- **Bundled** (user-facing) — `app/src/main/assets/changelog_bundled.txt`
- **changelog.txt УДАЛЁН** из проекта и деплоя
- При релизе: обновлять ОБА файла

Формат bundled:
```
🚀 Lavender X.X.X.X: Заголовок
— Пункт 1
— Пункт 2

(пустая строка между версиями)
```

---

## СБОРКА И ДЕПЛОЙ

Сборка ТОЛЬКО локально (assembleRelease → OOM на сервере):
```bash
cd /root/msg.client.android && ./gradlew assembleRelease
```

Проверка компиляции (можно на сервере):
```bash
cd /root/msg.client.android && ./gradlew compileDebugKotlin
```

Деплой на сервер:
```bash
# Скопировать APK на сервер
scp app/build/outputs/apk/release/app-release.apk lava:/var/www/lavender/lavender.apk

# Полный релиз (tag + deploy + GitHub Release)
./scripts/release.sh 1.1.2.9
```

---

## ИЗВЕСТНЫЕ ПРОБЛЕМЫ

1. **Favorites при пустом списке** — при входе после очистки памяти Favorites может не отображаться если нет созданных чатов. Появляется после создания первого чата.

---

## ПРАВИЛА

1. Коммитить после каждого значимого изменения, пушить в `feat/1.1.2.x`
2. Не ломать существующий функционал
3. `assembleRelease` НЕ запускать на сервере (OOM kill)
4. Версия Android в `version.txt` — обновлять при релизе
5. При релизе: `./scripts/release.sh X.X.X.X` (git tag + deploy + GitHub Release)
6. Дизайн — минималистичный, чистый, без лишнего декора
7. `userId` (UUID) — всегда как ключ, НЕ username
8. Для кастомных тем: новые FAB добавлять в ThemeApplier
9. Сообщения пользователя должны быть видны сразу после отправки (не ждать ответа агента)
10. История AI чатов должна сохраняться в локальную БД

---

## ДОКУМЕНТАЦИЯ (читать при старте)

Android:
- `/root/msg.client.android/doc/INDEX.md` — индекс документации
- `/root/msg.client.android/doc/TASKS.md` — таск-трекер, бэклог, известные баги
- `/root/msg.client.android/doc/PROMPT_ANDROID.md` — этот файл
- `/root/msg.client.android/CHANGELOG.md` — история версий (dev-facing)

Сервер:
- `/root/msg/doc/INDEX.md`
- `/root/msg/doc/INTEGRATION_SESSION.md`
- `/root/msg/doc/TASKS.md`
- `/root/msg/doc/PITFALLS.md`

Memory pad:
- `/root/.hermes/memory/pad.md`
