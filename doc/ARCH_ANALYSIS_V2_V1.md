# Lavender Messenger — Анализ архитектуры v2 vs v1

**Версия:** v1.1.3.19 (Android) / v1.2.0.1 (сервер dev)
**Дата:** 2026-06-16 (сессия 21)
**Цель:** Оптимизация v2 архитектуры до запуска на prod

---

## 1. Метрики кода

### 1.1 Размер компонентов

| Компонент | Файлы | Строки | LOC | Методы |
|-----------|-------|--------|-----|--------|
| **v1 ChatListActivity** | 1 | 2802 | 2358 | 69 |
| **v1 ChatAdapter** | 1 | 584 | 509 | 31 |
| **v1 итого** | 2 | 3386 | 2867 | 100 |
| **v2 ChatListActivityV2** | 1 | 676 | 568 | 36 |
| **v2 ChatAdapterV2** | 1 | 311 | 258 | 17 |
| **v2 ChatListViewModelV2** | 1 | 272 | 237 | 15 |
| **v2 ChatListSections** | 1 | 21 | 18 | 0 |
| **v2 ChatListFragmentV2** | 1 | 144 | 120 | 0 |
| **v2 итого** | 5 | 1424 | 1201 | 68 |
| **RealGrpcClient** (общий) | 1 | 4070 | ~3500 | 471 |
| **GrpcClient** (facade) | 1 | 779 | ~650 | 40 |

### 1.2 Ключевые наблюдения

- **v2 Activity в 4.1x меньше** v1 (676 vs 2802 строки) — благодаря ViewModel паттерну
- **v1 не имеет выделенного ViewModel** — вся логика в Activity (69 методов)
- **v2 использует 5 файлов вместо 2** — лучше разделение ответственности
- **RealGrpcClient — 471 метод в одном файле** — главная проблема архитектуры

---

## 2. Сравнение архитектурных паттернов

### 2.1 UI Layer

| Аспект | v1 | v2 |
|--------|----|----|
| Паттерн | God Activity | MVVM |
| ViewModel | Нет (всё в Activity) | ChatListViewModelV2 |
| Адаптер | ChatAdapter (notifyDataSetChanged) | ChatAdapterV2 (notifyDataSetChanged + секции) |
| Секции | Нет | Pinned/Favorites/All/Archived |
| Режим выбора | Нет | Selection Mode (ActionMode) |
| Поиск | Нет | SearchView + debounce 300ms |
| Табы | Нет | All/AI/Groups |
| FAB | 1 (add) | 2 (add + AI) |

### 2.2 Data Layer

| Аспект | v1 | v2 |
|--------|----|----|
| gRPC клиент | RealGrpcClient (монолит) | RealGrpcClient + ProfileClient |
| Auth | Password only | JWT (v2) + password fallback |
| Версия сервера | Не определяется | fetchServerInfo() с fallback |
| ChatList API | Basic (GetChats) | Extended (Pin/Search/Archive) |
| Кэш | Ручной | CacheUtils (единый) |

### 2.3 Общий RealGrpcClient — узкое место

**Проблема:** RealGrpcClient обслуживает И v1, И v2. Это 471 метод в одном файле.

**Статистика по категориям методов:**
- chat stream: 136 методов (29%)
- send/message: 75 методов (16%)
- other: 231 метод (49%)
- connect/disconnect: 6 методов
- profile/user: 11 методов
- getChats/poll: 1 метод

**v1/v2 ветвлений в RealGrpcClient:**
- `isChatV2Supported()` — 1 вызов
- v1 fallback — 3 места
- v2 specific — 61 место

---

## 3. Проблемы текущей архитектуры

### 3.1 Критические

#### 3.1.1 RealGrpcClient — монолит 4070 строк
- 471 метод, 14 StateFlow, 6 SharedFlow, 114 inner classes
- Обслуживает v1 и v2 одновременно
- Содержит proto-парсеры, marshallers, stream observers, callback handlers
- **Риск:** изменение для v1 может сломать v2 и наоборот

#### 3.1.2 Дублирование логики между v1 и v2
- 9 общих методов между ChatListActivity и ChatListActivityV2
- Оба вызывают `CredentialStore.getServerAddress()`, `GrpcClient.connect()`, `showAuthChoiceDialog()`
- Оба содержат `applyTheme()`, `openHermesChat()`, `openOwlChat()`
- **Риск:** исправление бага в v1 нужно дублировать в v2

#### 3.1.3 ChatAdapterV2 не использует DiffUtil
- v1 ChatAdapter: DiffUtil + notifyDataSetChanged (гибрид)
- v2 ChatAdapterV2: только notifyDataSetChanged
- **Риск:** мерцание списка при обновлении, потеря анимаций

### 3.2 Средние

#### 3.2.1 Нет единого ChatListPresenter
- v1: логика в Activity
- v2: логика в ViewModel
- Общая логика (навигация, темы, кэш) дублируется

#### 3.2.2 GrpcClient facade неполный
- Не все методы RealGrpcClient проксируются
- `newMessageEvent`, `currentRoomId` добавлены вручную по мере необходимости
- **Риск:** прямой доступ к RealGrpcClient из UI

#### 3.2.3 ProfileClient — object вместо класса
- `object ProfileClient` — singleton без DI
- Содержит mutable state (`serviceProfileVersion` и т.д.)
- **Риск:** состояние теряется при пересоздании процесса

### 3.3 Низкие

#### 3.3.1 ChatListFragmentV2 не используется
- Создан но не используется (заглушка для будущего)
- 144 строки мёртвого кода

#### 3.3.2 Дублирование layout-ов
- `item_chat.xml` используется и v1, и v2
- v2 добавляет `cbChatSelect` который v1 не использует
- **Риск:** изменение layout для v2 может сломать v1

---

## 4. Рекомендации по оптимизации

### 4.1 Добавить DiffUtil в ChatAdapterV2 ✅ (v1.1.3.19)
- ✅ `notifyDataSetChanged` заменён на DiffUtil + dispatchUpdatesTo
- ✅ Анимации добавления/удаления элементов, нет мерцания

### 4.2 Разделить RealGrpcClient на модули ✅ ЧАСТИЧНО (v1.1.3.20)
- ✅ GrpcConnectionManager (167 строк)
- ✅ GrpcAuthClient (232 строки)
- ✅ GrpcCallClient (124 строки)
- ✅ GrpcTypingClient (87 строк)
- ⏳ GrpcChatClient (~2000 строк) — СЛЕДУЮШИЙ
- ⏳ GrpcProfileClient — после GrpcChatClient
- ⏳ RealGrpcClient → тонкая обёртка ~200 строк — финальный шаг

**Проблема:** 9 общих методов дублируются между v1 и v2.

**Решение:** Создать базовый класс с общей логикой:

```
ChatListBaseActivity (abstract)
├── applyTheme()
├── showAuthChoiceDialog()
├── openHermesChat()
├── openOwlChat()
├── openHermesSettings()
├── openOwlSettings()
├── handleOnBackPressed()
├── onCreate() template
└── onResume() template

ChatListActivity extends ChatListBaseActivity (v1)
└── v1-specific: loadChats(), ChatAdapter, 2802 строки → ~800 строк

ChatListActivityV2 extends ChatListBaseActivity (v2)
└── v2-specific: ViewModel, sections, selection mode
```

**Эффект:** устранение ~200 строк дублирования, единая точка для общих изменений.

### 4.2 Разделить RealGrpcClient на модули

**Проблема:** 471 метод в одном файле.

**Решение:** Выделить функциональные модули:

```
GrpcConnectionManager
├── connect(), disconnect(), reconnect()
├── scheduleReconnect(), resetReconnectBackoff()
├── connectionStatus, currentServerAddress, currentServerPort
└── channel management

GrpcChatClient
├── startChat(), sendMessage(), onMessage()
├── getChats(), pollChats()
├── pinChat(), unpinChat(), searchChats()
├── archiveChat(), unarchiveChat()
└── markRead(), deleteChat()

GrpcAuthClient
├── loginV2(), loginV1(), refreshToken()
├── register(), signOut()
└── authStatus, token management

GrpcProfileClient (вынести из ProfileClient)
├── getProfile(), updateProfile()
├── getUserSettings(), updateUserSettings()
└── fetchServerInfo()

GrpcCallClient
├── startCallSession(), sendCallSignal()
└── callSignals

GrpcTypingClient
├── sendTypingSignal()
└── typingUsers

RealGrpcClient (facade, ~200 строк)
├── Содержит экземпляры всех модулей
├── Проксирует StateFlow/SharedFlow
└── Инициализация и lifecycle
```

**Эффект:** каждый модуль 200-400 строк, легче тестировать и поддерживать.

### 4.3 Добавить DiffUtil в ChatAdapterV2

**Проблема:** notifyDataSetChanged вызывает полный перерисовку списка.

**Решение:**

```kotlin
class ChatListDiffCallback(
    private val oldList: List<FlatItem>,
    private val newList: List<FlatItem>
) : DiffUtil.Callback() {
    override fun getOldListSize() = oldList.size
    override fun getNewListSize() = newList.size
    override fun areItemsTheSame(oldPos: Int, newPos: Int): Boolean {
        val old = oldList[oldPos]
        val new = newList[newPos]
        return when {
            old is FlatItem.ChatItem && new is FlatItem.ChatItem -> old.chat.id == new.chat.id
            old is FlatItem.SectionHeader && new is FlatItem.SectionHeader -> old.section == new.section
            else -> false
        }
    }
    override fun areContentsTheSame(oldPos: Int, newPos: Int): Boolean {
        return oldList[oldPos] == newList[newPos]
    }
}

// В ChatAdapterV2:
fun setSections(newSections: List<SectionItem>) {
    val newFlat = buildFlatList(newSections)
    val diff = DiffUtil.calculateDiff(ChatListDiffCallback(flatItems, newFlat))
    flatItems = newFlat
    diff.dispatchUpdatesTo(this)
}
```

**Эффект:** анимации добавления/удаления элементов, нет мерцания, плавные обновления.

### 4.4 Унифицировать навигацию к чатам

**Проблема:** navigateToChat() в v1 и v2 дублируется с небольшими отличиями.

**Решение:** Вынести в ChatListBaseActivity:

```kotlin
protected fun navigateToChat(chat: ChatInfo, username: String) {
    when (chat.type) {
        "favorites" -> navigateToFavorites(chat, username)
        "hermes" -> navigateToHermes(chat)
        "owl" -> navigateToOwl(chat)
        else -> navigateToRegularChat(chat, username)
    }
}
```

### 4.5 Оптимизировать подключение к серверу

**Проблема:** v1 и v2 вызывают `GrpcClient.connect()` независимо, возможны конфликты.

**Решение:** Единая точка подключения в ChatListBaseActivity:

```kotlin
protected fun connectToServer() {
    val serverAddress = CredentialStore.getServerAddress(this) ?: return
    val parts = serverAddress.split(":")
    val host = parts[0]
    val port = parts.getOrNull(1)?.toIntOrNull() ?: 50051
    
    // Если уже подключен к тому же серверу — не переподключаться
    if (GrpcClient.isConnectedTo(host, port)) return
    
    GrpcClient.connect(host, false, port, this)
}
```

### 4.6 Убрать мёртвый код

- `ChatListFragmentV2.kt` — 144 строки, не используется
- `ChatListActivity` содержит методы которые не вызываются из v1 (v2-specific код остался в v1)

---

## 5. Предлагаемая структура v2 (после оптимизации)

```
app/src/main/java/lavender/client/android/
├── ui/
│   ├── chatlist/
│   │   ├── base/
│   │   │   ├── ChatListBaseActivity.kt      — общая логика v1+v2
│   │   │   └── ChatListNavigation.kt         — навигация к чатам
│   │   ├── v1/
│   │   │   ├── ChatListActivity.kt           — v1-specific (~800 строк)
│   │   │   └── ChatAdapter.kt                — v1 адаптер
│   │   ├── v2/
│   │   │   ├── ChatListActivityV2.kt         — v2-specific (~400 строк)
│   │   │   ├── ChatAdapterV2.kt              — v2 адаптер с DiffUtil
│   │   │   ├── ChatListViewModelV2.kt        — v2 ViewModel
│   │   │   └── ChatListSections.kt           — секции
│   │   └── shared/                           — shared UI компоненты
│   │       └── ChatItemViewHolder.kt         — общий ViewHolder
│   ├── hermes/
│   ├── owl/
│   └── widget/
│
├── data/
│   ├── grpc/
│   │   ├── GrpcClient.kt                     — facade (~100 строк)
│   │   ├── GrpcConnectionManager.kt          — connect/disconnect
│   │   ├── GrpcChatClient.kt                 — chat operations
│   │   ├── GrpcAuthClient.kt                 — auth operations
│   │   ├── GrpcProfileClient.kt              — profile operations
│   │   ├── GrpcCallClient.kt                 — call operations
│   │   ├── GrpcTypingClient.kt               — typing operations
│   │   ├── BearerTokenInterceptor.kt
│   │   └── ProfileClient.kt                  — version detection
│   ├── models/
│   ├── proto/
│   └── session/
```

---

## 6. Приоритеты реализации

### Фаза 1: Критические оптимизации (в процессе)
1. ✅ **Разделить RealGrpcClient** — 4 из 6 модулей выделены (v1.1.3.20)
2. ✅ **Добавить DiffUtil в ChatAdapterV2** — устранено мерцание (v1.1.3.19)
3. ⏳ **Выделить GrpcChatClient** — ~2000 строк из RealGrpcClient (СЛЕДУЮЩИЙ)

### Фаза 2: Средние оптимизации
4. **Выделить GrpcProfileClient** + рефакторинг RealGrpcClient в тонкую обёртку
5. **Выделить ChatListBaseActivity** — устранить дублирование v1/v2
6. **Оптимизировать подключение** — единая точка connect
7. **Убрать мёртвый код** ✅ ChatListFragmentV2 удалён (v1.1.3.20)

### Фаза 3: Долгосрочные (v1.2.0.x)

7. **DI контейнер** — Koin/Hilt для зависимостей
8. **Единый ChatListActivity** — одна Activity с режимами v1/v2 вместо двух
9. **Compose UI** — миграция на Jetpack Compose для v2

---

## 7. Ожидаемый эффект

| Метрика | Сейчас | После оптимизации |
|---------|--------|-------------------|
| RealGrpcClient | 4070 строк, 471 метод | 200 строк facade + 6 модулей по 200-400 строк |
| v1+v2 дублирование | ~200 строк | 0 (ChatListBaseActivity) |
| ChatAdapterV2 обновление | notifyDataSetChanged | DiffUtil + анимации |
| Мёртвый код | ~300 строк | 0 |
| Общий размер chatlist/ | ~4800 строк | ~3500 строк (-27%) |

---

## 8. Риски и ограничения

### 8.1 Обратная совместимость
- v1 НЕ должна быть затронута изменениями для v2
- Все изменения в общем коде должны быть протестированы на обоих серверах

### 8.2 Тестирование
- Каждый выделенный модуль должен быть протестирован отдельно
- Интеграционные тесты для v1→v2 переключения

### 8.3 Время
- Фаза 1: ~2-3 часа разработки + 1 час тестирования
- Фаза 2: ~1-2 часа
- Фаза 3: отдельная сессия

---

*Документ подготовлен для сессии 21. Обновлять при каждом значимом изменении архитектуры.*
