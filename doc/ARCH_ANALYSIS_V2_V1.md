# Lava Messenger — Анализ архитектуры v2 vs v1

**Версия:** v1.1.3.29 (Android) / v1.1.3.0 (сервер)
**Дата:** 2026-06-17 (обновлено после сессии 35)
**Цель:** Оптимизация v2 архитектуры до запуска на prod

---

## 1. Метрики кода

### 1.1 Размер компонентов

| Компонент | Файлы | LOC | Статус |
|-----------|-------|-----|--------|
| **ChatListActivity** | 1 | 1113 | ✅ Единый Activity (v1+v2 объединены) |
| **ChatAdapter** | 1 | 356 | ✅ С секциями + DiffUtil |
| **ChatListViewModel** | 1 | 272 | ✅ MVVM |
| **ChatListSections** | 1 | 21 | ✅ Section enum |
| **RealGrpcClient** | 1 | 874 | ✅ Orchestrator (было 3810, -77%) |
| **GrpcClient** (facade) | 1 | 779 | ✅ Стабильный |
| **12 gRPC модулей** | 13 | ~3800 | ✅ Выделены из монолита |

### 1.2 Ключевые наблюдения

- **v2 Activity в 2.5x меньше** v1 reference (1113 vs 2802 строки) — благодаря ViewModel паттерну
- **v1 reference удалён** из проекта (сохранён в doc/ для справки)
- **RealGrpcClient — 874 строк** вместо 3810 — God Object устранён
- **12 модулей + Marshallers** — чистая модульная архитектура

---

## 2. Сравнение архитектурных паттернов

### 2.1 UI Layer

| Аспект | v1 (reference) | v2 (текущий) |
|--------|----------------|--------------|
| Паттерн | God Activity | MVVM |
| ViewModel | Нет | ChatListViewModel |
| Адаптер | ChatAdapter (notifyDataSetChanged) | ChatAdapter (DiffUtil + секции) |
| Секции | Нет | Pinned/Favorites/All/Archived |
| Режим выбора | Нет | Selection Mode (ActionMode) |
| Поиск | Нет | SearchView + debounce 300ms |
| Табы | Нет | All/AI/Groups/Favorites |
| FAB | 1 (add) | 2 (add + AI) |
| Тема | Частичная | Полная (AppBarLayout, TabLayout, toolbar) |

### 2.2 Data Layer

| Аспект | v1 (reference) | v2 (текущий) |
|--------|----------------|--------------|
| gRPC клиент | RealGrpcClient (монолит 3810 LOC) | 12 модулей + orchestrator |
| Auth | Password only | JWT + password fallback |
| Версия сервера | Не определяется | fetchServerInfo() с fallback |
| ChatList API | Basic (GetChats) | Extended (Pin/Search/Archive) |
| Кэш | Ручной | CacheUtils (единый) |

---

## 3. Проблемы текущей архитектуры

### 3.1 Средние

#### 3.1.1 NewChatActivity — 1473 строки
- Создание чатов, поиск пользователей, UI, навигация — всё в одном Activity
- **Риск:** сложно поддерживать и тестировать
- **Решение:** выделить ViewModel + Fragments (отложено по решению ферзя)

#### 3.1.2 ChatListActivity — 1113 строк
- Toolbar, tabs, FABs, search, selection mode, settings sheets, update coordinator wiring
- **Рisk:** Activity всё ещё большая
- **Решение:** выделить ToolbarManager, TabManager

#### 3.1.3 GrpcClient facade — 779 строк
- Значительная часть — proxy-методы без логики
- **Риск:** дублирование сигнатур методов

### 3.2 Низкие

#### 3.2.1 Нет единого ChatListBaseActivity
- Общая логика (навигация, темы, кэш) дублируется если появится v1 снова
- **Риск:** при добавлении нового Activity — дублирование

#### 3.2.2 HermesGrpc + OwlGrpc — суммарно 3025 строк
- AI-специфичный код доменной логики в gRPC слое
- **Риск:** смешение слоёв архитектуры

---

## 4. Рекомендации по оптимизации

### ✅ Выполнено

1. **Разделить RealGrpcClient на модули** — 12 модулей выделены (v1.1.3.26-28)
2. **Добавить DiffUtil в ChatAdapter** — анимации без мерцания (v1.1.3.19)
3. **Единый ChatListActivity** — v1/v2 объединены (v1.1.3.23)
4. **Полная адаптация к кастомным темам** — AppBarLayout, TabLayout, toolbar (v1.1.3.29)
5. **Favorites в табах** — таб "Favorites" добавлен (v1.1.3.29)
6. **NewChatBottomSheet с полным меню** — 7 пунктов (v1.1.3.29)

### 🟡 Следующие 2-3 сессии

7. **Рефакторинг NewChatActivity** — ViewModel + Fragments
8. **Унификация error handling** — ErrorHandler.kt везде
9. **Разбиение ChatListActivity** — ToolbarManager, TabManager

### 🟢 Backlog

10. **Тесты для gRPC клиента** — unit + integration
11. **HermesGrpc/OwlGrpc выделение в domain layer**
12. **Pagination для чатов** — limit/offset в UI
13. **Incremental history loading** — постраничная загрузка сообщений
14. **Certificate pinning** — безопасность
15. **Encrypted SharedPreferences** — безопасность

---

## 5. Предлагаемая структура (после оптимизации)

```
app/src/main/java/lavender/client/android/
├── ui/
│   ├── chatlist/
│   │   ├── ChatListActivity.kt           — ЕДИНЫЙ Activity (~600 строк после разбиения)
│   │   ├── ChatListViewModel.kt          — ViewModel
│   │   ├── ChatListSections.kt           — секции
│   │   ├── UpdateCoordinator.kt          — update system
│   │   ├── ToolbarManager.kt             — управление toolbar (TODO)
│   │   └── TabManager.kt                 — управление табами (TODO)
│   ├── adapter/
│   │   ├── ChatAdapter.kt                — с DiffUtil + секции
│   │   └── MessageAdapter.kt             — с pinned badge
│   ├── widget/
│   │   ├── ServerAuthBottomSheet.kt
│   │   ├── LoginBottomSheet.kt
│   │   ├── RegisterBottomSheet.kt
│   │   ├── AIBottomSheet.kt
│   │   └── NewChatBottomSheet.kt
│   ├── hermes/
│   ├── owl/
│   └── remote/
├── data/
│   ├── grpc/
│   │   ├── GrpcClient.kt                 — facade
│   │   ├── RealGrpcClient.kt             — orchestrator (874 LOC)
│   │   ├── GrpcConnectionManager.kt      — 12 модулей
│   │   └── ...
│   ├── models/
│   ├── session/
│   └── cache/
└── theme/ui/
```

---

## 6. Ожидаемый эффект

| Метрика | Сейчас | После оптимизации |
|---------|--------|-------------------|
| RealGrpcClient | 874 строк | 874 строк (завершён) |
| ChatListActivity | 1113 строк | ~600 строк (-46%) |
| NewChatActivity | 1473 строк | ~600 строк (-59%) |
| Мёртвый код | 0 | 0 |
| Общий размер chatlist/ | ~1100 строк | ~800 строк |

---

## 7. Риски и ограничения

### 7.1 Обратная совместимость
- v1 сервер НЕ должна быть затронута изменениями для v2
- Все изменения в общем коде должны быть протестированы на обоих серверах

### 7.2 Тестирование
- Каждый выделенный модуль должен быть протестирован отдельно
- Интеграционные тесты для v1→v2 переключения

---

*Документ обновлён после сессии 35. Рефакторинг gRPC завершён. Следующий фокус — NewChatActivity и ChatListActivity разбиение.*
