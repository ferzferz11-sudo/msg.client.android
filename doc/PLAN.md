# Lavender Messenger — Plan

**Version:** v1.1.3.39 | **Branch:** feat/1.1.3.x | **Updated:** 2026-06-20

---

## Completed — v1.1.3.38 (v2 Client Release)

### UI Улучшения
- ✅ Имя собеседника в личных чатах (getDisplayName)
- ✅ Тулбар: прозрачность 30%, тень 6dp, тап → шторка профиля
- ✅ Убраны заголовки секций из списка чатов
- ✅ Шторка профиля: верхняя секция кликабельная → редактирование
- ✅ Фон темы: chatListBackground для списка чатов
- ✅ Предзагрузка пользователей при открытии чатов

### Функциональность
- ✅ Язык синхронизируется с сервером (toggleLanguage + SplashActivity)
- ✅ Создание чатов/секретных чатов/конференций — все пользователи
- ✅ Typing индикатор: фильтрация по username и userId

### Компиляция
- ✅ deployAgentTaskStream в HermesChatUseCase
- ✅ Scope leak retryDelay в OwlChatUseCase

---

## Backlog — Следующая сессия

### ✅ Приоритет 1: Dead Code Cleanup (выполнено)
- ✅ Удален `ui/viewmodel/ChatListViewModel.kt` (21 LOC legacy stub)
- ✅ Переименован `NotificationActivity` (root) → `NotificationSettingsActivity`

### ✅ Приоритет 2: ContactsActivity пуст на v1 сервере (выполнено)
- ✅ Сервер: добавлен fallback на `req.Username` в `GetContacts`, `AddContact`, `RemoveContact`
- ✅ Dev сервер обновлён до v1.3.0.17

### Приоритет 3: Архитектура
| Задача | Что | LOC Эффект | Оценка |
|--------|-----|-----------|--------|
| ViewModel для NewChatActivity | Бизнес-логика → ViewModel | 755→~400 | 2h |
| ViewModel для ProfileActivity | Бизнес-логика → ViewModel | 719→~300 | 2h |
| Разделить GrpcChatListClient | 3 класса | 642→3×200 | 1h |
| Разделить MessageAdapter | ViewHolder по типам | 870→~300 | 2h |

### Приоритет 4: Тесты
| Задача | Оценка |
|--------|--------|
| Unit-тесты для ViewModels | 3h |
| Unit-тесты для SessionManager | 2h |
| Unit-тесты для data/ai/ | 2h |

### Приоритет 5: Безопасность
| Задача | Оценка |
|--------|--------|
| Keystore пароль → env vars | 0.5h |
| ServerConfig.kt — единый IP | 1h |
| EncryptedSharedPreferences | 2h |

---

## Key Decisions

| Решение | Обоснование |
|---------|-------------|
| Facade + inline delegates | Extension functions не работают через star import в Kotlin |
| MockK для тестов | MockK — Kotlin-native, Mockito не в зависимостях |
| ErrorHandler统一 | Все ошибки через ErrorHandler → AppLog + Log |
| Chat delegates | 6 делегатов вместо монолитного NewChatActivity |
| Optimistic READY | gRPC канал подключается лениво |
| Keepalive 30s/10s | Для мобильных сетей |

---

## Архитектура

```
GrpcClient (facade, 711 LOC)
  └── RealGrpcClient (883 LOC) — orchestrator
        ├── GrpcConnectionManager, GrpcAuthClient, GrpcTypingClient
        ├── GrpcCallClient, GrpcChatListClient, GrpcProfileClient
        ├── GrpcDraftClient, GrpcFavoritesClient, GrpcMessageClient
        ├── GrpcServerDiscoveryClient, GrpcMarshallers
        ├── HermesGrpc (1872), OwlGrpc (1146) — AI
        └── AiChatGrpc, SecretChatGrpc, ProfileClient

ChatListActivity (382) → 10 modules
NewChatActivity (755) → 6 delegates
ProfileActivity (719) — monolithic
```

---

## Completed Phases

| Phase | Version | What | Status |
|-------|---------|------|--------|
| 0-2 | v1.1.3.33 | Stabilization, NewChatActivity refactor, Error handling | ✅ |
| 3 | v1.1.3.34 | Unit tests for gRPC client (42 tests) | ✅ |
| 4 | v1.1.3.35 | GrpcClient facade optimization (780→~400 LOC) | ✅ |
| 5 | v1.1.3.36 | AI domain layer (OwlChatUseCase, HermesChatUseCase) | ✅ |
| 6 | v1.1.3.38 | v2 Client Release — UI improvements, language sync, contacts | ✅ |
