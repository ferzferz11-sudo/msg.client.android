# Lavender Messenger (Android) — Задачи

**Версия:** 1.1.2.8
**Обновлено:** 2026-06-11
**Ветка:** feat/1.1.2.x
**Тег:** v1.1.2.8 (выпущен)
**APK:** /var/www/lavender/lavender.apk
**GitHub релиз:** https://github.com/ferzferz11-sudo/msg.client.android/releases/tag/v1.1.2.8

---

## ✅ v1.1.2.8 — AI чат улучшения, Favorites fix, Changelog fix

### AI Чаты
- **Убран прелоадер** во время ожидания ответа агента (HermesChatActivity, OwlChatActivity) — достаточно typing indicator
- **Таймаут стрима 120 сек** с сбросом при каждом полученном сообщении (OwlGrpc, HermesGrpc) — показывает user-friendly ошибку на русском
- **Шторка AI реорганизована**: чаты разделены по типам — Hermes чаты в секции "Лава ИИ", OWL чаты в секции "OWL агент". Больше нет смешанного списка "Все AI чаты"

### Favorites — исправлено
- **Favorites отображается сразу при входе** — не нужно создавать чат чтобы увидеть Избранное
- Показывается даже при недоступном сервере (offline-first)
- Добавлен fallback при ошибке загрузки чатов

### Changelog
- **ChangelogAdapter**: цвета из `ThemeStore` вместо `resolveColorAttr` — читаемый текст на кастомных тёмных темах
- **Порядок загрузки**: сначала GitHub API, fallback (bundled) только через 3с если сеть не ответила

### Мелкие исправления
- Убран deprecated `overridePendingTransition` в SplashActivity
- Убран дебаг-логгинг из production кода

---

## ✅ v1.1.2.7 — Splash улучшения, удаление онбординга, чекбокс чата

### SplashActivity
- Увеличено расстояние логотип→текст (60px → 90dp)
- Новый SplashLoadingActivity — оверлей загрузки для логина/регистрации
- Login/Register: показывается SplashLoadingActivity во время авторизации

### Онбординг удалён
- Удалён welcomeContainer (логотип + описание)
- Удалены onboardingProfileBubble, onboardingFabBubble
- Удалена установка first_login_/onboarding_completed_ prefs
- Удалена темизация онбординга из ThemeApplier
- Список чатов показывается сразу при входе

### Чекбокс "Создать чат"
- CheckBox "Сразу создать личный чат" в шторке добавления контакта
- Включён по умолчанию
- Создаёт чат → переход в NewChatActivity через SplashLoadingActivity
- Работает в ChatListActivity и ContactsActivity

### Исправления
- Crash при выборе чатов с пустым списком (selectedPositions.clear в setChats)
- getSelectedChats offset для Favorites на позиции 0
- Удалён loadingContainer (прелоадер "Загрузка" в центре)
- Убран deprecated statusBarColor в ThemeApplier
- compileDebugKotlin ✅

---

## ✅ v1.1.2.6 — ChangelogActivity: bundled changelog + ссылки на GitHub

- **Bundled changelog**: добавлен `app/src/main/assets/changelog_bundled.txt`
- **Новая логика загрузки**: bundled → GitHub API → server fallback
- **Ссылки на полные CHANGELOG.md**: кнопки «Ченджлог сервера» и «Ченджлог клиента»
- **changelog.txt удалён** из проекта и деплоя
- compileDebugKotlin ✅

---

## ✅ v1.1.2.5 — ChangelogActivity тема

- ChangelogActivity: ThemeUi.bind добавлен (белый экран исправлен)
- compileDebugKotlin ✅

---

## ✅ v1.1.1.15 — Бесплатные модели + своя модель

- Бесплатные модели загружаются с сервера (GetFreeModels RPC)
- Favorites flickering fix: startSync() + updateAvatarCache() offset
- compileDebugKotlin ✅

---

## ✅ v1.1.1.14 — Дизайн + полировка

- Анимации сообщений, typing indicator
- Bottom sheets: MaterialCardView, hover-эффекты
- compileDebugKotlin ✅

---

## 📋 Бэклог

### Высокий приоритет
- [ ] Favorites при пустом списке — не отображается при входе после очистки памяти

### Средний приоритет
- [ ] Graceful shutdown сервера
- [ ] Structured logging (zap/logrus)
- [ ] Рефакторинг server.go → пакеты

### Низкий приоритет
- [ ] Кэширование запросов чатов
- [ ] WebRTC — тестирование TURN

---

## 🟡 Известные баги

### Favorites — отображение при пустом списке чатов
- **Статус:** не исправлено, v1.1.2.7
- **Симптом:** при входе после очистки памяти Favorites не отображается если нет созданных чатов. Появляется после создания первого чата.
- **Попытки:** selectedPositions.clear(), post{notifyDataSetChanged()}, удаление loadChatsFromCache — не помогли
- **Нужно:** отладить почему getItemCount() возвращает 1 но RecyclerView не рендерит

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
| SplashLoadingActivity | Отдельный оверлей вместо ProgressBar на кнопке |

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
| `SplashActivity.kt` | Сплеш-экран с анимацией |
| `SplashLoadingActivity.kt` | Оверлей загрузки для авторизации |
