# Android клиент — Промпт для новой сессии

Текущая версия: v1.1.2.6 (prod)
Следующая версия: v1.1.2.7

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
├── ChangelogActivity.kt       — экран «Что нового» (bundled + GitHub)
├── ChatListActivity.kt        — главный список чатов + AI шторка
├── HermesChatActivity.kt      — чат с Hermes оркестратором
├── HermesChatViewModel.kt     — ViewModel Hermes чата
├── OwlChatActivity.kt         — чат с OWL AI
├── OwlChatViewModel.kt        — ViewModel OWL чата
├── AIBottomSheet.kt           — шторка AI (OWL + Hermes + уведомления)
├── GrpcClient.kt              — единая точка доступа к gRPC
├── HermesGrpc.kt              — gRPC методы Hermes
├── OwlGrpc.kt                 — gRPC методы OWL
├── RealGrpcClient.kt          — реализация gRPC клиента
├── theme/
│   ├── ThemeApplier.kt        — применение кастомных тем
│   ├── ThemeStore.kt          — хранилище текущей темы
│   ├── ThemeUi.kt             — привязка темы к Activity
│   ├── ThemeUtils.kt          — утилиты цветов
│   └── Theme.kt               — data class темы
├── data/changelog/
│   ├── ChangelogRepository.kt  — загрузка релизов (GitHub API → cache)
│   ├── ChangelogParser.kt      — парсинг JSON + ReleaseInfo
│   ├── ReleaseInfo.kt          — модели данных релиза
│   └── MarkdownRenderer.kt     — рендер markdown в Spannable
└── ui/adapter/
    └── ChangelogAdapter.kt     — адаптер списка релизов

app/src/main/res/
├── layout/
│   ├── activity_changelog.xml  — layout экрана ченджлога
│   ├── item_release.xml        — карточка релиза
│   └── item_release_asset.xml  — файл релиза (APK)
├── values/strings.xml          — строки (en)
├── values-ru/strings.xml       — строки (ru)
└── drawable/                   — фоны, теги, иконки

app/src/main/assets/
└── changelog_bundled.txt       — встроенный ченджлог (fallback)
```

---

## АРХИТЕКТУРА AI ЧАТОВ

Полная изоляция OWL и Hermes:
- Разные файлы: OwlGrpc.kt / HermesGrpc.kt
- Разные SharedFlows: owlTyping/owlResponses vs hermesTyping/hermesResponses
- Разные Activity: OwlChatActivity / HermesChatActivity
- Разные ViewModels: OwlChatViewModel / HermesChatViewModel
- Разные rate limiters

Единый AI Chat (v1.1.2.3+):
- ai_chat_manager.go — единый менеджер на сервере
- ai_chat_sessions, ai_chat_messages, ai_chat_settings таблицы
- ChatWithAI RPC — единый стриминг
- AiChatGrpc.kt — единый gRPC клиент
- Старые RPC (ChatWithOWL, ChatWithOrchestrator) — deprecated

---

## ТЕМЫ (ВАЖНО!)

Кастомные темы через ThemeApplier:
- `ThemeApplier.apply(activity, theme)` — вызывать **ДО** setContentView
- `ThemeUi.bind(this, "")` — для обновления при смене темы
- Цвета: backgroundColor, primaryColor, onPrimaryColor, textPrimaryColor,
  textSecondaryColor, surfaceColor, onSurfaceColor
- FAB кнопки: добавлять в список в ThemeApplier (aiFab, addChatFab и т.д.)
- ChangelogActivity: цвета fallback устанавливаются программно из ThemeStore

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
cd /root/msg.client.android && ./scripts/deploy_android.sh
# (скопирует APK + version.txt на 13.140.25.249)
```

---

## ИЗВЕСТНЫЕ ПРОБЛЕМЫ

1. **ChangelogAdapter** — на кастомных тёмных темах текст может быть нечитаемым
   (resolveColorAttr возвращает тёмный цвет). Приоритет низкий.

2. **Rate limiter** — счётчик показывает макс 19 вместо 20 для Hermes.
   Причина не найдена, отложено.

---

## ПРАВИЛА

1. Коммитить после каждого значимого изменения, пушить в `feat/1.1.2.x`
2. Не ломать существующий функционал
3. `assembleRelease` НЕ запускать на сервере (OOM kill)
4. Версия Android в `version.txt` — обновлять при релизе
5. При релизе: git tag + push tag, CHANGELOG.md, bundled, version.txt
6. Дизайн — минималистичный, чистый, без лишнего декора
7. `userId` (UUID) — всегда как ключ, НЕ username
8. Для кастомных тем: новые FAB добавлять в ThemeApplier

---

## ДОКУМЕНТАЦИЯ (читать при старте)

Сервер:
- `/root/msg/doc/INDEX.md`
- `/root/msg/doc/INTEGRATION_SESSION.md`
- `/root/msg/doc/TASKS.md`
- `/root/msg/doc/PITFALLS.md`
- `/root/msg/doc/CHANGELOG.md`

Android:
- `/root/msg.client.android/doc/TASKS.md`

Memory pad:
- `/root/.hermes/memory/pad.md`
