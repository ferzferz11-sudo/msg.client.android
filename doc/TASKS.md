# Lavender Messenger (Android) — Задачи

**Версия:** 1.1.1.14
**Обновлено:** 2026-06-09
**Ветка:** feat/1.1.1.x

---

## ✅ v1.1.1.14 — Дизайн + полировка
- Анимации сообщений (fade-in + slide), typing indicator (ValueAnimator)
- Bottom sheets: MaterialCardView, hover-эффекты, per-command иконки
- Splash screen анимация, statusBarColor = bgColor
- compileDebugKotlin ✅

---

## 📋 Бэклог

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
- **Статус:** исправлено в c873fbc
- **Причина:** startSync() вызывал setChats() без Favorites, что вызывало remove/insert каждые 5 секунд

---

## 🔑 Ключевые решения

| Решение | Обоснование |
|---------|-------------|
| Proto field номера 20/21 | Избежание конфликта с Android парсером |
| Room migration 8→9 | ALTER TABLE вместо destructive migration |
| ChatWidget-подход | Общий функционал через виджет, не копипаст |
| setExistingSession | Передача существующей сессии через intent |

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
