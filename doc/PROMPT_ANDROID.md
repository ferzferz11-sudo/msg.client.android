# Промпт для новой сессии — Android v1.1.3.30+

**Дата:** 2026-06-17
**Версия:** 1.1.3.29
**Ветка:** feat/1.1.3.x

---

## СТАТУС: v1.1.3.29 — завершена

### Что сделано в этой сессии:
- ✅ Кастомные темы — полная адаптация тулбара (AppBarLayout, TabLayout, toolbar title/subtitle)
- ✅ ThemeStore.init() в applyTheme() для загрузки кастомной темы из кэша
- ✅ Favorites — убран таб, возвращён как секция в списке
- ✅ Убрана шестерёнка настроек (дублировала шторку профиля)
- ✅ Обновления — убрана автозагрузка, текстовый индикатор "New version available"
- ✅ Серверы — logout сбрасывает на prod, dev всегда зелёный
- ✅ Исправлен краш updateCoordinator при возврате из профиля

---

## ПРИОРИТЕТЫ СЛЕДУЮЩЕЙ СЕССИИ (v1.1.3.30)

### 🔴 Высокий приоритет

#### 1. Восстановить шторку FAB [+] как в v1
Сейчас: простая шторка с 4 пунктами и extras которые NewChatActivity не понимает.
Нужно: ActionBottomSheet → SearchableListBottomSheet для выбора пользователей.

**Пункты шторки:**
- **Add Contact** → SearchableListBottomSheet с поиском пользователей, добавление в контакты
- **Start Chat** → SearchableListBottomSheet, выбор 1 пользователя → createDirectChat, выбор 2+ → createGroupChat
- **Secret Chat** → SearchableListBottomSheet, выбор 1 пользователя → createSecretChat (E2EE)
- **Conference (in development)** → SearchableListBottomSheet, выбор участников → createConference

**Нужно создать:**
- `ActionBottomSheet` — простая шторка со списком действий (аналог из v1)
- `SearchableListBottomSheet` — шторка с поиском, списком пользователей, кнопкой действия
- `UserAdapter` — адаптер для списка пользователей с выбором

**v1 reference:** `doc/ChatListActivity_v1_REFERENCE.kt` строки 1996-2412

#### 2. Favorites — убрать из секций, добавить в шторку профиля
- Убрать Favorites из `ChatListViewModel.buildSections()`
- Добавить пункт "Favorites" в `showSettingsSheet()` (шторка профиля по клику на аватар)
- По тапу открывать `FavoritesActivity` с передачей USER_ID

#### 3. FavoritesActivity — исправить загрузку данных
- Получать userId из intent напрямую
- Исправить отображение списка избранных сообщений

### 🟡 Средний приоритет
4. ProfileService v2 — проверить работу на dev сервере
5. Read receipts — MarkAsRead с broadcast

### 🟢 Отложено
- NewChatActivity рефакторинг — отложено по решению ферзя
- ChatListActivity разбиение (ToolbarManager, TabManager)
- Qdrant + CLIP (production RAG)
- Shared element transitions
- Infinite scroll + pagination

---

## АРХИТЕКТУРА

### gRPC Client (v1.1.3.29)
```
GrpcClient (facade, 779 LOC)
    ↓
RealGrpcClient (orchestrator, 874 LOC)
    ├── GrpcConnectionManager (167)
    ├── GrpcAuthClient (232)
    ├── GrpcTypingClient (87)
    ├── GrpcCallClient (125)
    ├── GrpcChatListClient (638)
    ├── GrpcProfileClient (506)
    ├── GrpcDraftClient (86)
    ├── GrpcFavoritesClient (120)
    ├── GrpcMessageClient (341)
    ├── GrpcServerDiscoveryClient (145)
    └── GrpcMarshallers (1394)
```

### Серверы
| | Dev | Prod |
|--|-----|------|
| Порт gRPC | 50052 | 50051 |
| Порт HTTP | 8083 | 8082 |
| Версия | v1.1.3.0 | v1.1.3.0 |

---

## ПРАВИЛА

1. ⚠️ НЕ компилировать Android на сервере
2. НЕ деплоить на prod без прямого указания ферзя
3. Коммитить и пушить после каждого значимого изменения
4. userId (UUID) — всегда как ключ, НЕ username
5. i18n: все новые строки ОДНОВРЕМЕННО в values/strings.xml + values-ru/strings.xml
6. НЕ инициализировать getString() в полях класса Activity
7. Kotlin 2.3.21: cont.resume(value, onCancellation = {})
8. НЕТ forceReconnect — один connect при старте, reconnect только если FAILED

---

## КОМАНДЫ

```bash
# Сервер
cd /root/msg && export PATH=$PATH:/usr/local/go/bin:~/go/bin
go build -o /tmp/lavender-server-dev . && systemctl stop lavender-server-dev && cp /tmp/lavender-server-dev /root/LavenderMessenger/run/lavender-server-dev && systemctl start lavender-server-dev

# Android (НЕ компилировать на сервере!)
cd /root/msg.client.android
```

---

## ДОКУМЕНТАЦИЯ

| Файл | Назначение |
|------|-----------|
| `doc/INDEX.md` | Индекс всей документации |
| `doc/TASKS.md` | Таск-трекер |
| `doc/PROMPT_ANDROID.md` | Этот файл |
| `doc/SESSION_NOTES.md` | История сессий |
| `doc/CHANGELOG.md` | История изменений |
| `doc/PATTERNS.md` | Паттерны и анти-patterns |
| `doc/ChatListActivity_v1_REFERENCE.kt` | Копия v1 Activity (2802 строки) |
