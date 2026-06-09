# Lavender Messenger (Android) — Задачи

**Версия:** 1.1.1.16
**Обновлено:** 2026-06-09
**Ветка:** feat/1.1.1.x

---

## ✅ v1.1.1.16 — Багфикс + полировка
- SplashActivity: логотип 🦞 → ic_notification_logo
- SplashActivity: надпись "Лава" (ru) / "Lava" (en)
- AIBottomSheet: rebuildContent() + updateChats() для перестройки без закрытия
- AIBottomSheet: popup menu delete/settings не закрывает шторку
- ChatListActivity: shouldShowAiSheetOnResume — return из AI активити открывает шторку
- ThemeApplier: aiFab добавлен в список FAB для кастомных тем
- Save button в OWL settings: style="@style/PrimaryButton"
- compileDebugKotlin ✅

---

## ✅ v1.1.1.15 — Бесплатные модели + своя модель
- Бесплатные модели загружаются с сервера (GetFreeModels RPC)
- Без ключа: только бесплатные модели, OWL Alpha первая
- С ключом: бесплатные + «Своя модель» (текстовый ввод ID)
- Поле «Своя модель» скрыто без ключа + подсказка
- Favorites flickering fix: startSync() + updateAvatarCache() offset
- compileDebugKotlin ✅

---

## ✅ v1.1.1.14 — Дизайн + полировка
- Анимации сообщений (fade-in + slide), typing indicator (ValueAnimator)
- Bottom sheets: MaterialCardView, hover-эффекты, per-command иконки
- Splash screen анимация, statusBarColor = bgColor
- compileDebugKotlin ✅

---

## 📋 Бэклог

### Высокий приоритет
- [ ] Деплой на prod → v1.1.2.0

### Средний приоритет
- [ ] Graceful shutdown сервера
- [ ] Structured logging (zap/logrus)
- [ ] Рефакторинг server.go → пакеты

### Низкий приоритет
- [ ] Кэширование запросов чатов
- [ ] WebRTC — тестирование TURN

---

## 🟡 Известные баги

### Favorites — мерцание при обновлении списка чатов
- **Статус:** исправлено в c873fbc (v1.1.1.15)

---

## 🔑 Ключевые решения

| Решение | Обоснование |
|---------|-------------|
| Proto field номера 20/21 | Избежание конфликта с Android парсером |
| Room migration 8→9 | ALTER TABLE вместо destructive migration |
| ChatWidget-подход | Общий функционал через виджет, не копипаст |
| setExistingSession | Передача существующей сессии через intent |
| ThemeApplier FAB list | Новые FAB добавлять в список для кастомных тем |

---

## 📁 Ключевые файлы

| Файл | Назначение |
|------|------------|
| `ChatWidget.kt` | Общий виджет чата (search, selection, emoji, attach) |
| `HermesChatActivity.kt` | Чат с Hermes агентом |
| `HermesChatViewModel.kt` | setExistingSession() |
| `ChatListActivity.kt` | onChatClick hermes + onResume fix |
| `ChatMessageAdapter.kt` | highlightPosition(), анимации |
| `Entities.kt` | ChatEntity + Room DB v9 |
| `ThemeApplier.kt` | Применение кастомных тем к UI |
| `AIBottomSheet.kt` | AI шторка с чатами |
