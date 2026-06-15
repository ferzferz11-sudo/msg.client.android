# План: ChatList v2 UI + Разделение v1/v2 архитектуры

**Дата:** 2026-06-16
**Версия плана:** 1.1
**Статус:** В работе

---

## СТАТУС РЕЛИЗОВ

| Версия | Статус | Дата | Коммит |
|--------|--------|------|--------|
| v1.1.3.15 | ✅ ВЫПУЩЕН | 2026-06-16 | `b95a6f4` |
| v1.1.3.16+ | 🔨 В разработке | — | — |

**v1.1.3.15** — последняя версия с полной поддержкой v1 (prod сервер). Выпущен ферзём.
Все изменения сессии 12 (v2 scaffold) пойдут в **v1.1.3.16+**.

---

## КРИТИЧЕСКОЕ АРХИТЕКТУРНОЕ РЕШЕНИЕ: Разделение v1/v2

### Проблема
Сейчас v1 и v2 код перемешан в одних файлах (ChatListActivity.kt ~2800 строк, ChatAdapter.kt ~580 строк).
v1 сервер (prod) не поддерживает ChatList v2 API. v2 сервер (dev) поддерживает.
Нельзя ломать v1 функционал пользователям на prod сервере.

### Решение: Чистое разделение

```
app/src/main/java/lavender/client/android/
├── ChatListActivity.kt          ← v1 (НЕ ТРОГАТЬ — стабильная версия)
├── ChatAdapter.kt               ← v1 (НЕ ТРОГАТЬ — стабильная версия)
│
├── ui/
│   ├── chatlist/                ← v2 НОВАЯ ПАПКА
│   │   ├── ChatListActivityV2.kt    — новый Activity для v2
│   │   ├── ChatListFragmentV2.kt    — фрагмент с секциями/табами
│   │   ├── ChatAdapterV2.kt         — адаптер с секциями
│   │   ├── ChatListViewModelV2.kt   — ViewModel для v2
│   │   └── ChatListSections.kt      — управление секциями (Pinned/Favorites/All)
│   │
│   ├── adapter/
│   │   └── ChatAdapter.kt       ← v1 (НЕ ТРОГАТЬ)
│   │
│   └── ...
```

### Принцип работы
1. **v1.1.3.15** — последняя версия с полной поддержкой v1 (prod сервер)
   - ChatListActivity.kt остаётся без изменений
   - Все v2 файлы создаются НОВЫМИ, не трогая v1
   
2. **v1.1.3.16+** — v2 клиент для dev сервера
   - При запуске: fetchServerInfo() → определяем версию сервера
   - v1 сервер → запускаем ChatListActivity (v1)
   - v2 сервер → запускаем ChatListActivityV2 (v2)
   - Переключение в AndroidManifest через activity-alias или программный выбор

### Критические правила
- **НЕ ИЗМЕНЯТЬ** ChatListActivity.kt и ChatAdapter.kt до выпуска v1.1.3.15
- Все v2 изменения — в НОВЫХ файлах в папке `ui/chatlist/`
- v1 пользователи получают стабильную версию без изменений
- v2 пользователи получают новый UI с секциями, табами, поиском

---

## ЭТАП 0: Выпуск v1.1.3.15 (последняя v1 версия)

### Цель
Дать пользователям на prod сервере стабильную версию с полной поддержкой v1 API.

### Что делаем
1. Обновляем version.txt: 1.1.3.14 → 1.1.3.15
2. Обновляем CHANGELOG.md — добавляем секцию v1.1.3.15
3. Коммитим и пушим
4. **НЕ добавляем никаких v2 UI изменений в основные файлы**

### Коммиты
- `docs: add PLAN_CHATLIST_V2.md, update version to 1.1.3.15`

---

## ЭТАП 1: Создание v2 каркаса

### Цель
Создать новую папку `ui/chatlist/` с базовой структурой v2.

### Файлы для создания

#### 1.1. ChatListActivityV2.kt
```kotlin
package lavender.client.android.ui.chatlist

// Новый Activity для v2 серверов
// Использует fetchServerInfo() для определения версии
// При v2: показывает ChatListFragmentV2
// При v1: fallback на обычный ChatListActivity
```

Ключевые отличия от v1:
- TabLayout + ViewPager2 для табов (All / AI / Groups)
- Секции чатов: Pinned / Favorites / All Chats
- SearchView в toolbar
- SwipeRefreshLayout
- FAB для создания чата (как в v1)

#### 1.2. ChatListFragmentV2.kt
```kotlin
package lavender.client.android.ui.chatlist

// Фрагмент с RecyclerView + секциями
// Управляет ChatAdapterV2
// Обрабатывает swipe-to-refresh
```

#### 1.3. ChatAdapterV2.kt
```kotlin
package lavender.client.android.ui.chatlist

// Новый адаптер с поддержкой секций
// ViewType: SECTION_HEADER, CHAT_ITEM, FAVORITES
// DiffUtil для секций
```

#### 1.4. ChatListViewModelV2.kt
```kotlin
package lavender.client.android.ui.chatlist

// AndroidViewModel
// State: chats, pinnedChats, favorites, searchQuery, selectedTab
// Methods: loadChats, pinChat, unpinChat, searchChats, archiveChat
```

#### 1.5. ChatListSections.kt
```kotlin
package lavender.client.android.ui.chatlist

// Управление секциями
// enum class Section { PINNED, FAVORITES, ALL }
// data class SectionItem(val section: Section, val chats: List<ChatInfo>)
// Сортировка pinned по pinnedAt desc
```

### Layout файлы

#### activity_chat_list_v2.xml
```xml
<CoordinatorLayout>
    <AppBarLayout>
        <MaterialToolbar>
            <!-- SearchView -->
            <!-- TabLayout (All / AI / Groups) -->
        </MaterialToolbar>
    </AppBarLayout>
    
    <ViewPager2 android:id="@+id/viewPager" />
    
    <LavenderFab android:id="@+id/fabAddChat" />
    <LavenderFab android:id="@+id/fabAi" />
</CoordinatorLayout>
```

#### fragment_chat_list_v2.xml
```xml
<SwipeRefreshLayout>
    <RecyclerView android:id="@+id/rvChatList" />
</SwipeRefreshLayout>
```

#### item_chat_section_header.xml
```xml
<LinearLayout>
    <TextView android:id="@+id/tvSectionName" />  <!-- "Pinned", "Favorites", "All Chats" -->
    <TextView android:id="@+id/tvSectionCount" /> <!-- "(3)" -->
</LinearLayout>
```

---

## ЭТАП 2: Секции чатов (Pinned / Favorites / All)

### Логика секций

```
Исходный список с сервера:
  [chat1, chat2, chat3, chat4, chat5]

После фильтрации:
  pinnedChats = [chat1, chat3]  (isPinned=true, sorted by pinnedAt desc)
  regularChats = [chat2, chat4, chat5]  (isPinned=false, isArchived=false)

Итоговый список для RecyclerView:
  [0] SECTION_HEADER "Pinned (2)"
  [1] chat1
  [2] chat3
  [3] SECTION_HEADER "Favorites"
  [4] Favorites (special chat)
  [5] SECTION_HEADER "All Chats"
  [6] chat2
  [7] chat4
  [8] chat5
```

### Фильтрация по табам
- **All**: все чаты (pinned + regular + favorites)
- **AI**: только chat.type == "owl" || chat.type == "hermes"
- **Groups**: только chat.type == "group" || chat.type == "general" || chat.type == "conference"

### Сортировка
1. Pinned чаты: по pinnedAt desc (новые сверху)
2. Favorites: всегда один, после Pinned
3. All Chats: по lastMessageTime desc

---

## ЭТАП 3: Контекстное меню + Actions

### Контекстное меню (long press)
```
┌──────────────────────────┐
│  📌 Pin / Unpin          │  ← GrpcClient.pinChat / unpinChat
│  🔇 Mute / Unmute        │  ← GrpcClient.setMutedChat
│  📦 Archive / Unarchive  │  ← GrpcClient.archiveChat / unarchiveChat
│  🗑 Delete                │  ← GrpcClient.deleteChat
└──────────────────────────┘
```

### Логика Pin
1. Вызов GrpcClient.pinChat(chatId) → сервер обновляет user_chat_metadata
2. Лобновляем ChatInfo.isPinned = true, pinnedAt = now()
3. Перемещаем чат в секцию Pinned с анимацией
4. Unpin — обратно, убираем из Pinned секции

### Логика Archive
1. Вызов GrpcClient.archiveChat(chatId)
2. Убираем чат из списка (или показываем в отдельной Archived секции)
3. Unarchive — возвращаем в All Chats

---

## ЭТАП 4: Поиск

### Локальный поиск
- SearchView в MaterialToolbar
- Debounce 300ms (coroutine delay)
- Фильтрация по: chat.name, chat.lastMessageText
- Обновление через ChatAdapterV2.filter()

### Серверный поиск (опционально)
- GrpcClient.searchChats(query, limit) — для v2 серверов
- Показываем результаты с подсветкой

---

## ЭТАП 5: Swipe-to-refresh + Infinite scroll

### Swipe-to-refresh
- SwipeRefreshLayout оборачивает RecyclerView
- onRefresh → loadChats(skipCache=true)
- Цвет индикатора из темы

### Infinite scroll
- GetChatsRequestProto.limit + offset
- OnScrollListener: при достижении конца → loadMore()
- Loading footer: ProgressBar в конце списка
- Предотвращение двойной загрузки (isLoading flag)

---

## ЭТАП 6: Shared element transitions

### При клике на чат → ChatActivity
- Transition для аватара + имени чата
- ActivityOptions.makeSceneTransitionAnimation()
- Имена переходов: "chat_avatar_{chatId}", "chat_name_{chatId}"

---

## ЭТАП 7: Интеграция и переключение v1/v2

### AndroidManifest.xml
```xml
<!-- v1 Activity (основной) -->
<activity android:name=".ChatListActivity" android:exported="true">
    <intent-filter>
        <action android:name="android.intent.action.MAIN" />
        <category android:name="android.intent.category.LAUNCHER" />
    </intent-filter>
</activity>

<!-- v2 Activity -->
<activity android:name=".ui.chatlist.ChatListActivityV2" />
```

### SplashActivity или программный выбор
```kotlin
// При старте приложения:
val serverAddress = CredentialStore.getServerAddress(context)
val host = serverAddress.split(":")[0]
val port = serverAddress.split(":").getOrNull(1)?.toIntOrNull() ?: 50051

// Пробуем fetchServerInfo
ProfileClient.fetchServerInfo(host, port) { info ->
    if (info != null && ProfileClient.isChatV2Supported()) {
        // Запускаем v2
        startActivity(Intent(this, ChatListActivityV2::class.java))
    } else {
        // Запускаем v1
        startActivity(Intent(this, ChatListActivity::class.java))
    }
}
```

---

## ЭТАП 8: ThemeApplier обновление

### Новые FAB для v2
```kotlin
// ThemeApplier.kt — добавить в список FAB
listOf(R.id.fabAi, R.id.fabAddChat, R.id.fabSearch)
```

---

## ЭТАП 9: i18n

### Новые строки (values/strings.xml + values-ru/strings.xml)

```xml
<!-- Табы -->
<string name="tab_all">All</string>
<string name="tab_ai">AI</string>
<string name="tab_groups">Groups</string>

<!-- Секции -->
<string name="section_pinned">Pinned</string>
<string name="section_favorites">Favorites</string>
<string name="section_all_chats">All Chats</string>
<string name="section_archived">Archived</string>

<!-- Действия -->
<string name="action_pin">Pin</string>
<string name="action_unpin">Unpin</string>
<string name="action_archive">Archive</string>
<string name="action_unarchive">Unarchive</string>
<string name="action_mute">Mute</string>
<string name="action_unmute">Unmute</string>

<!-- Поиск -->
<string name="search_chats">Search chats…</string>
<string name="search_no_results">No results</string>

<!-- Русский -->
<string name="tab_all">Все</string>
<string name="tab_ai">ИИ</string>
<string name="tab_groups">Группы</string>
<string name="section_pinned">Закреплённые</string>
<string name="section_favorites">Избранное</string>
<string name="section_all_chats">Все чаты</string>
<string name="section_archived">Архив</string>
<string name="action_pin">Закрепить</string>
<string name="action_unpin">Открепить</string>
<string name="action_archive">Архивировать</string>
<string name="action_unarchive">Разархивировать</string>
<string name="action_mute">Отключить звук</string>
<string name="action_unmute">Включить звук</string>
<string name="search_chats">Поиск чатов…</string>
<string name="search_no_results">Ничего не найдено</string>
```

---

## ПОРЯДОК РЕАЛИЗАЦИИ

| Шаг | Что делаем | Статус |
|-----|-----------|--------|
| 0 | Выпуск v1.1.3.15 (последняя v1) — **выпущен ферзём** | ✅ ЗАВЕРШЁН |
| 1 | Создать папку ui/chatlist/ + базовые файлы | ✅ ЗАВЕРШЁН (коммит `7d087bc`) |
| 2 | Layout файлы для v2 | ✅ ЗАВЕРШЁН |
| 3 | Секции чатов (Pinned/Favorites/All) | ✅ ЗАВЕРШЁН (ChatAdapterV2) |
| 4 | Контекстное меню + Pin/Archive | ✅ ЗАВЕРШЁН (chat_context_menu.xml) |
| 5 | Поиск | ✅ ЗАВЕРШЁН (ChatAdapterV2.filter()) |
| 6 | Swipe-to-refresh | ✅ ЗАВЕРШЁН (fragment_chat_list_v2.xml) |
| 7 | i18n — все новые строки | ✅ ЗАВЕРШЁН (17 строк en+ru) |
| 8 | TabLayout + ViewPager2 (табы All/AI/Groups) | 🔨 СЛЕДУЮЩИЙ |
| 9 | Переключение v1/v2 при старте | ⬜ |
| 10 | AndroidManifest.xml — регистрация V2 | ⬜ |
| 11 | Тестирование на dev сервере | ⬜ |
| 12 | Коммит + пуш | ⬜ |

---

## КЛЮЧЕВЫЕ ПАТТЕРНЫ (ОБЯЗАТЕЛЬНО СОБЛЮДАТЬ)

1. **НЕ ИЗМЕНЯТЬ** ChatListActivity.kt и ChatAdapter.kt — v1.1.3.15 уже выпущен
2. **fetchServerInfo** — всегда проверять isChatV2Supported() перед v2 API
3. **ChatAdapter.clearAll()** — вместо setChats(emptyList()) — crash!
4. **CancellableContinuation.resume(value, onCancellation = {})** — Kotlin 2.3.21
5. **import kotlinx.coroutines.suspendCancellableCoroutine** — НЕ kotlin.coroutines
6. **Все строки ОДНОВРЕМЕННО** в values/strings.xml + values-ru/strings.xml
7. **getString() в полях Activity** — NPE crash, только lateinit + onCreate
8. **ThemeApplier.apply() ДО setContentView()**
9. **Новые FAB добавлять в ThemeApplier** список
10. **userId (UUID)** — ключ, НЕ username
11. **НЕ компилировать на сервере** (OOM kill)
12. **НЕ деплоить на prod** без тестирования на dev

---

## СЕРВЕРНЫЕ КОМАНДЫ

```bash
# Сборка и деплой на dev
cd /root/msg && export PATH=$PATH:/usr/local/go/bin:~/go/bin
go build -o /tmp/lavender-server-dev .
systemctl stop lavender-server-dev
cp /tmp/lavender-server-dev /root/LavenderMessenger/run/lavender-server-dev
systemctl start lavender-server-dev

# Логи
journalctl -u lavender-server-dev -f
```

## ANDROID КОМАНДЫ

```bash
cd /root/msg.client.android
# assembleRelease ТОЛЬКО локально!
```
