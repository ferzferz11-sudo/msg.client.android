# Lava Messenger — Анализ текущего состояния и план оптимизации

**Дата:** 2026-06-17 | **Версия:** v1.1.3.34 | **Автор:** OWL (на основе аудита v1.1.3.33)

---

## 1. ТЕКУЩЕЕ СОСТОЯНИЕ ПРОЕКТА

### 1.1 Общая статистика

| Метрика | Значение |
|---------|----------|
| Kotlin файлов (main) | 159 |
| Kotlin файлов (test) | 11 |
| Общий LOC (main) | ~39,943 |
| Общий LOC (test) | ~2,206 |
| Пакетов (main) | 32 |
| gRPC модулей | 12 + Marshallers |
| Unit-тестов | 11 (до v1.1.3.34) → 53 (после v1.1.3.34) |

### 1.2 Топ-15 файлов по размеру

| # | Файл | LOC | Проблема |
|---|------|-----|----------|
| 1 | HermesGrpc.kt | 1,876 | AI-код в gRPC слое |
| 2 | MessengerProto.kt | 1,791 | Proto-код (автогенерация) |
| 3 | GrpcMarshallers.kt | 1,395 | Marshaller classes (автогенерация) |
| 4 | OwlGrpc.kt | 1,145 | AI-код в gRPC слое |
| 5 | RealGrpcClient.kt | 883 | Orchestrator |
| 6 | MessageAdapter.kt | 870 | UI adapter |
| 7 | GrpcClient.kt | 780 | Facade (proxy-методы) |
| 8 | NewChatActivity.kt | 754 | Activity (рефакторинг завершён) |
| 9 | ProfileActivity.kt | 719 | Activity (не рефакторена) |
| 10 | GrpcChatListClient.kt | 642 | gRPC модуль |
| 11 | ShareReceiverActivity.kt | 641 | Activity |
| 12 | RemoteAgentSettingsActivity.kt | 629 | Activity |
| 13 | RemoteAgentViewModel.kt | 601 | ViewModel |
| 14 | ConferenceLobbyActivity.kt | 581 | Activity |
| 15 | ChatInputDelegate.kt | 567 | Chat delegate |

### 1.3 Распределение кода по слоям

```
Слой                    LOC      Файлы    Проблемы
─────────────────────────────────────────────────────
gRPC клиент            ~8,500    22       God Object устранён, но HermesGrpc+OwlGrpc = 3021 LOC
UI (Activities)         ~5,200    15       4 Activity > 500 LOC
UI (Adapters)           ~1,500    8        MessageAdapter 870 LOC
UI (Chat modules)       ~2,100    12       6 делегатов NewChatActivity
Data (models/db/proto)  ~3,200    15       Proto файлы = 40% от слоя
Theme                    ~400     4        Нормальный
Auth/Session             ~800     4        Нормальный
FCM/Push                 ~300     3        Нормальный
Remote Agent             ~1,200   4        Изолированный
```

### 1.4 Архитектурные проблемы (по приоритету)

#### Критичные

1. **HermesGrpc (1876 LOC) + OwlGrpc (1145 LOC) = 3021 LOC AI-кода в gRPC слое**
   - AI-логика перемешана с transport layer
   - Невозможно тестировать AI отдельно от gRPC
   - Нарушение Single Responsibility Principle

2. **GrpcClient facade (780 LOC) — proxy-методы без логики**
   - Каждый метод — 1 строка делегирования
   - 780 строк boilerplate кода
   - Добавление нового метода = 3 изменения (GrpcClient + RealGrpcClient + модуль)

3. **NewChatActivity (754 LOC) всё ещё содержит бизнес-логику**
   - observers, calls, drafts всё ещё в Activity
   - Можно вынести ещё ~300 LOC

#### Средние

4. **ProfileActivity (719 LOC) — не рефакторена**
   - Монолитная Activity без делегатов
   - Содержит: профиль, аватар, настройки, темы

5. **ConferenceLobbyActivity (581 LOC) — не рефакторена**
   - Отдельный Activity для конференций

6. **ShareReceiverActivity (641 LOC) — не рефакторена**
   - Обработка входящих шарингов

7. **MessageAdapter (870 LOC) — большой адаптер**
   - Содержит логику для разных типов сообщений
   - ViewHolder для 10+ view types

#### Низкие

8. **GrpcChatListClient (642 LOC) — можно разделить**
   - Chat list operations + chat management + pin/search/archive
   - 3 ответственности в одном классе

9. **GrpcProfileClient (506 LOC) — можно разделить**
   - Profile + avatar + contacts + themes + devices
   - 5 ответственностей

10. **RemoteAgentViewModel (601 LOC) — большой ViewModel**
    - Содержит UI-логику + бизнес-логику

---

## 2. АНАЛИЗ ТЕКУЩИХ МЕТРИК КАЧЕСТВА

### 2.1 Покрытие тестами

| Слой | Тестов | Покрытие |
|------|--------|----------|
| gRPC клиент | 0 → 42 (v1.1.3.34) | ~15% |
| UI (Activities) | 0 | 0% |
| UI (Adapters) | 15 (ChatAdapterTest) | ~30% |
| ErrorHandler | 11 (ErrorHandlerTest) | ~80% |
| **Итого** | **26 → 68** | **~12%** |

### 2.2 Технический долг

| Категория | Количество | Описание |
|-----------|-----------|----------|
| TODO/FIXME | ~15 | Разбросаны по коду |
| Hardcoded IP | 1 | `13.140.25.249` в `fetchServersList()` |
| usePlaintext() | Все каналы | Без TLS (dev) |
| SharedPreferences без шифрования | CredentialStore | Нет EncryptedSharedPreferences |
| Force unwrap (!!) | ~20 | Потенциальные NPE |
| Thread.sleep() | 2 | В продакшен коде |

### 2.3 Дублирование кода

| Паттерн | Дубликатов | Локация |
|---------|-----------|---------|
| gRPC MethodDescriptor + call + listener | ~30 | Все gRPC модули |
| ErrorHandler.handle() + Log.e | ~5 | Некоторые модули |
| SharedPreferences.edit{} | ~10 | Разные Activity |
| AlertDialog создание | ~8 | Разные Activity |
| Glide загрузка аватаров | ~6 | Toolbar, Profile, Chat |

---

## 3. ПЛАН ОПТИМИЗАЦИИ

### Фаза 4: GrpcClient facade оптимизация (v1.1.3.35)

**Проблема:** 780 LOC proxy-методов без логики.

**Решение:** Заменить на extension functions + группировку по доменам.

**План:**
1. Создать `GrpcClientExtensions.kt` с extension functions для каждого домена:
   ```kotlin
   // Вместо 780 строк proxy-методов:
   fun GrpcClient.signIn(...) = realGrpcClient.signInV2(...)
   fun GrpcClient.getChats(...) = realGrpcClient.getChats(...)
   ```

2. Группировать по доменам через extension files:
   - `GrpcClientAuth.kt` — signIn, signUp, refreshToken, signOut
   - `GrpcClientChat.kt` — getChats, createChat, deleteChat, pinChat
   - `GrpcClientMessage.kt` — sendMessage, deleteMessage, editMessage
   - `GrpcClientProfile.kt` — updateProfile, updateAvatar, getContacts

3. Цель: GrpcClient < 300 LOC (только StateFlow declarations + scope)

**Оценка:** 1 сессия
**Риски:** Низкие — чистый рефакторинг, без изменения поведения

---

### Фаза 5: AI Chats domain layer (v1.1.3.36)

**Проблема:** HermesGrpc (1876 LOC) + OwlGrpc (1145 LOC) = 3021 LOC AI-кода в gRPC слое.

**Решение:** Выделить AI логику в отдельный domain слой.

**План:**
1. Создать `data/ai/` пакет:
   ```
   data/ai/
   ├── AiChatManager.kt          — единый менеджер AI чатов
   ├── OwlDataSource.kt          — OWL-специфичная логика
   ├── HermesDataSource.kt       — Hermes-специфичная логика
   ├── AiChatSession.kt          — модель сессии
   └── AiChatMessage.kt          — модель сообщения
   ```

2. Вынести из HermesGrpc:
   - Управление сессиями (создание, удаление, история)
   - Маршрутизация сообщений
   - Streaming логика
   - Rate limiting

3. Вынести из OwlGrpc:
   - OpenRouter API интеграция
   - Модели и настройки
   - Streaming ответов

4. gRPC модули оставить как thin transport layer:
   ```kotlin
   // Было (HermesGrpc, 1876 LOC):
   fun chatWithOrchestrator(...) {
       // 500 строк логики
   }
   
   // Станет:
   fun chatWithOrchestrator(...) {
       HermesDataSource.chat(...)
   }
   ```

**Оценка:** 1-2 сессии
**Риски:** Средние — требует аккуратного переноса streaming логики

---

### Фаза 6: NewChatActivity финальный рефакторинг (v1.1.3.37)

**Проблема:** 754 LOC — можно вынести ещё ~300 LOC.

**Решение:** Вынести observers, calls, drafts в отдельные делегаты.

**План:**
1. Создать `ChatCallDelegate.kt` (~100 LOC):
   - Управление звонками в чате
   - Call session lifecycle

2. Создать `ChatDraftDelegate.kt` (~80 LOC):
   - Управление черновиками
   - Auto-save логика

3. Вынести observers из Activity:
   - `ChatObservers.kt` (~120 LOC)
   - Подписки на StateFlow/SharedFlow

4. Цель: NewChatActivity < 400 LOC

**Оценка:** 1 сессия
**Риски:** Низкие — паттерн делегатов уже отработан

---

### Фаза 7: ProfileActivity рефакторинг (v1.1.3.38)

**Проблема:** 719 LOC монолитная Activity.

**Решение:** Разделить на делегаты по аналогии с NewChatActivity.

**План:**
1. Создать `ui/profile/` пакет:
   ```
   ui/profile/
   ├── ProfileToolbarDelegate.kt    — toolbar, avatar, navigation
   ├── ProfileInfoDelegate.kt       — username, bio, status
   ├── ProfileSettingsDelegate.kt   — настройки, темы
   └── ProfileAvatarDelegate.kt     — загрузка, кроп, удаление
   ```

2. Вынести логику из ProfileActivity
3. Цель: ProfileActivity < 300 LOC

**Оценка:** 1 сессия
**Риски:** Низкие

---

### Фаза 8: GrpcChatListClient разделение (v1.1.3.39)

**Проблема:** 642 LOC — 3 ответственности.

**Решение:** Разделить на 3 класса.

**План:**
1. `GrpcChatListQuery.kt` (~200 LOC) — getChats, getAllChats, getChatListVersion
2. `GrpcChatListManagement.kt` (~250 LOC) — pinChat, searchChats, archiveChat, deleteChat
3. `GrpcChatListParticipants.kt` (~190 LOC) — addParticipant, removeParticipant, createDirectChat, createGroupChat

**Оценка:** 0.5 сессии
**Риски:** Низкие

---

### Фаза 9: MessageAdapter рефакторинг (v1.1.3.40)

**Проблема:** 870 LOC — 10+ ViewHolder.

**Решение:** Разделить по типам сообщений.

**План:**
1. Создать `ui/chat/message/type/` пакет:
   ```
   ui/chat/message/type/
   ├── TextMessageViewHolder.kt
   ├── ImageMessageViewHolder.kt
   ├── VoiceMessageViewHolder.kt
   ├── FileMessageViewHolder.kt
   ├── SystemMessageViewHolder.kt
   └── AiMessageViewHolder.kt
   ```

2. Базовый `BaseMessageViewHolder` с общей логикой
3. MessageAdapter — только диспетчеризация по viewType
4. Цель: MessageAdapter < 300 LOC

**Оценка:** 1 сессия
**Риски:** Средние — много view types

---

## 4. СВОДКА ПЛАНА

| Фаза | Версия | Что | LOC эффект | Тестов | Сессий |
|------|--------|-----|-----------|--------|--------|
| 4 | v1.1.3.35 | GrpcClient facade → extensions | 780→300 | 0 | 1 |
| 5 | v1.1.3.36 | AI domain layer | 3021→800 | 10 | 1-2 |
| 6 | v1.1.3.37 | NewChatActivity финал | 754→400 | 5 | 1 |
| 7 | v1.1.3.38 | ProfileActivity рефакторинг | 719→300 | 5 | 1 |
| 8 | v1.1.3.39 | GrpcChatListClient split | 642→3×200 | 3 | 0.5 |
| 9 | v1.1.3.40 | MessageAdapter split | 870→300 | 10 | 1 |
| **Итого** | | | **-5,286 LOC** | **+33** | **~6** |

### Метрики после оптимизации

| Метрика | Сейчас | Цель |
|---------|--------|------|
| Файлов > 500 LOC | 15 | 3 |
| Файлов > 300 LOC | 25 | 10 |
| Unit-тестов | 11 → 53 | 86 |
| Покрытие | ~12% | ~25% |
| AI-код в gRPC | 3021 | < 800 |
| NewChatActivity | 754 | < 400 |
| ProfileActivity | 719 | < 300 |

---

## 5. РЕКОМЕНДАЦИИ ПО ПРИОРИТЕТУ

### Немедленно (v1.1.3.35-36)
1. **GrpcClient facade** — быстрый win, 1 сессия, убирает 480 LOC boilerplate
2. **AI domain layer** — критично для архитектуры, позволяет тестировать AI отдельно

### Среднесрочно (v1.1.3.37-38)
3. **NewChatActivity финал** — завершает рефакторинг чата
4. **ProfileActivity** — последняя большая Activity без делегатов

### Долгосрочно (v1.1.3.39-40)
5. **GrpcChatListClient split** — улучшает читаемость
6. **MessageAdapter split** — упрощает поддержку новых типов сообщений

---

## 6. НЕРЕШЁННЫЕ ВОПРОСЫ

1. **HermesGrpc streaming** — как тестировать streaming без реального сервера?
   - Ответ: grpc-in-process сервер + mock генератор ответов

2. **AI domain layer boundary** — где проходит граница между domain и transport?
   - Ответ: Domain = сессии, история, маршрутизация. Transport = gRPC calls.

3. **MessageAdapter view types** — сколько типов сообщений реально используется?
   - Нужен аудит: посмотреть MessageAdapter и выявить все viewType

4. **ProfileActivity зависимости** — какие модули уже есть в профиле?
   - Нужен анализ: проверить что можно переиспользовать из существующих делегатов
