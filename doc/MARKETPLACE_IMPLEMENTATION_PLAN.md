# AI Marketplace — План реализации (Android клиент)

**Версия:** v1.3.0.1 | **Ветка:** feat/1.3.0.x | **Дата:** 2026-06-20

---

## Краткое описание

Реализация UI для AI Marketplace API (7 gRPC методов): каталог публичных агентов, детали/отзывы/оценка, шеринг/установка по share_code, статистика использования. API уже реализован в `GrpcAIv2Client.kt`.

---

## Зависимости

| Зависимость | Статус |
|-------------|--------|
| GrpcAIv2Client — 7 Marketplace методов | ✅ Done |
| GrpcAIv2Marshallers — 14 marshallers | ✅ Done |
| AiV2Proto — 15 proto моделей | ✅ Done |
| AiV2AgentListActivity — табы Presets/My/Public | ✅ Done |
| Сервер v1.3.0.4+ (Marketplace API) | ✅ Deployed |

---

## Архитектура

```
UI Layer                        Domain Layer                Data Layer
┌──────────────────────────┐    ┌──────────────────┐    ┌────────────────────┐
│ MarketplaceActivity      │    │ MarketplaceUseCase│    │ GrpcAIv2Client     │
│   └── MarketplaceTabFrag │◄──►│ (suspend + Flow) │◄──►│ (7 методов)        │
│   └── MyAgentsTabFrag    │    └──────────────────┘    └────────────────────┘
│   └── UsageStatsTabFrag  │
│ AgentDetailActivity      │
│ RateBottomSheet          │
│ InstallBottomSheet       │
│ ShareBottomSheet         │
└──────────────────────────┘
```

Следуем паттерну проекта: **Activity → delegates/fragments + ViewModel** (как ChatListActivity → 10 modules).

---

## Фазы реализации

### Фаза 1: Data Layer — Domain модели + UseCase (~200 LOC)

**Цель:** Подготовить domain модели и use-case слой для работы с Marketplace API.

| # | Задача | Файлы | LOC |
|---|--------|-------|-----|
| 1.1 | Domain модели: `MarketplaceAgent`, `AgentStats`, `AgentReview`, `UsageStat`, result-классы | `domain/ai/MarketplaceModels.kt` | ~80 |
| 1.2 | Proto → Domain маппинг extension functions | `data/grpc/MarketplaceMappers.kt` | ~40 |
| 1.3 | `MarketplaceUseCase` — обёртка над `GrpcAIv2Client`, suspend функции, error handling через `ErrorHandler` | `domain/ai/MarketplaceUseCase.kt` | ~120 |

**Ключевые решения:**
- UseCase возвращает `Result<T>` (Kotlin stdlib) — не custom wrapper
- Все ошибки через `ErrorHandler.handle()` (правило 7)
- `withContext(Dispatchers.IO)` в UseCase, не в Client

---

### Фаза 2: ViewModel (~150 LOC)

**Цель:** ViewModel для Marketplace экрана с StateFlow для UI.

| # | Задача | Файлы | LOC |
|---|--------|-------|-----|
| 2.1 | `MarketplaceViewModel` — loadAgents, loadMore, search, pagination state | `ui/ai/marketplace/MarketplaceViewModel.kt` | ~100 |
| 2.2 | `AgentDetailViewModel` — stats, reviews, rate, share, install | `ui/ai/marketplace/AgentDetailViewModel.kt` | ~80 |
| 2.3 | `UsageStatsViewModel` — usage stats, per-agent breakdown | `ui/ai/usage/UsageStatsViewModel.kt` | ~50 |

**StateFlow модели:**
```kotlin
// MarketplaceViewModel
data class MarketplaceUiState(
    val agents: List<MarketplaceAgent> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val hasMore: Boolean = true,
    val error: String? = null,
    val searchQuery: String = ""
)

// AgentDetailViewModel
data class AgentDetailUiState(
    val agent: MarketplaceAgent? = null,
    val stats: AgentStats? = null,
    val reviews: List<AgentReview> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)
```

---

### Фаза 3: UI — Экраны и компоненты (~800 LOC)

**Цель:** Экраны Marketplace с табами, карточками агентов, диалогами.

#### 3.1 Табы Marketplace (в AiV2AgentListActivity)

Добавить 4-й таб "Marketplace" в существующий `AiV2AgentListActivity`:

| # | Задача | Файлы |
|---|--------|-------|
| 3.1.1 | `MarketplaceTabFragment` — RecyclerView (GridLayoutManager 2 колонки), SearchBar, SwipeRefresh, pagination | `ui/ai/marketplace/MarketplaceTabFragment.kt` |
| 3.1.2 | `MarketplaceAgentAdapter` — ViewHolder с name, model, rating stars, install count, provider icon | `ui/ai/marketplace/MarketplaceAgentAdapter.kt` |
| 3.1.3 | `item_marketplace_agent.xml` — карточка агента (MaterialCardView) | `res/layout/item_marketplace_agent.xml` |

#### 3.2 Экран деталей агента

| # | Задача | Файлы |
|---|--------|-------|
| 3.2.1 | `AgentDetailActivity` — toolbar, avatar, name, description, stats, reviews list, action buttons | `ui/ai/marketplace/AgentDetailActivity.kt` |
| 3.2.2 | `activity_agent_detail.xml` — layout | `res/layout/activity_agent_detail.xml` |
| 3.2.3 | `ReviewAdapter` — список отзывов (user, rating bar, text, date) | `ui/ai/marketplace/ReviewAdapter.kt` |
| 3.2.4 | `item_review.xml` — карточка отзыва | `res/layout/item_review.xml` |

#### 3.3 Диалоги

| # | Задача | Файлы |
|---|--------|-------|
| 3.3.1 | `RateBottomSheet` — RatingBar (1-5), TextInputEditText (отзыв), кнопка "Отправить" | `ui/ai/marketplace/RateBottomSheet.kt` + layout |
| 3.3.2 | `InstallBottomSheet` — TextInputEditText (share_code), кнопка "Установить" | `ui/ai/marketplace/InstallBottomSheet.kt` + layout |
| 3.3.3 | `ShareBottomSheet` — отображение share_code, кнопка "Копировать", "Поделиться" | `ui/ai/marketplace/ShareBottomSheet.kt` + layout |

#### 3.4 Статистика использования

| # | Задача | Файлы |
|---|--------|-------|
| 3.4.1 | `UsageStatsFragment` — общая статистика (токены, запросы), per-agent таблица | `ui/ai/usage/UsageStatsFragment.kt` |
| 3.4.2 | `fragment_usage_stats.xml` — layout | `res/layout/fragment_usage_stats.xml` |
| 3.4.3 | `UsageStatsAdapter` — per-agent строки (name, tokens, requests) | `ui/ai/usage/UsageStatsAdapter.kt` |

#### 3.5 Навигация

| # | Задача | Файлы |
|---|--------|-------|
| 3.5.1 | Таб "Marketplace" в `AiV2AgentListActivity` (добавить к существующим Presets/My/Public) | `AiV2AgentListActivity.kt` |
| 3.5.2 | Deep link: `lavender://marketplace/install?code=xxx` в `AndroidManifest.xml` | `AndroidManifest.xml` |
| 3.5.3 | Навигация: AgentList → AgentDetailActivity (Intent с agent_id) | `AiV2AgentListActivity.kt` |

---

### Фаза 4: Строки и темизация (~100 LOC)

| # | Задача | Файлы |
|---|--------|-------|
| 4.1 | Строки EN: marketplace, install, rate, share, reviews, usage_stats, etc. | `values/strings.xml` |
| 4.2 | Строки RU: marketplace, install, rate, share, reviews, usage_stats, etc. | `values-ru/strings.xml` |
| 4.3 | Цвета/стили для карточек Marketplace | `values/colors.xml`, `values/styles.xml` |

---

### Фаза 5: Тесты (~200 LOC)

| # | Задача | Файлы |
|---|--------|-------|
| 5.1 | Unit-тесты `MarketplaceModels` — data classes, маппинг | `test/.../MarketplaceModelsTest.kt` |
| 5.2 | Unit-тесты `MarketplaceMappers` — Proto → Domain | `test/.../MarketplaceMappersTest.kt` |
| 5.3 | Unit-тесты `MarketplaceUseCase` — mock GrpcAIv2Client | `test/.../MarketplaceUseCaseTest.kt` |

---

## Итого

| Метрика | Значение |
|---------|----------|
| Новые файлы | ~15 (3 Kotlin + 5 layouts + 3 adapters + 3 dialogs + 1 ViewModel) |
| Изменённые файлы | ~5 (AiV2AgentListActivity, AndroidManifest, strings.xml x2, GrpcClient facade) |
| LOC | ~1450 (200 data + 150 viewmodel + 800 UI + 100 strings + 200 tests) |
| Тесты | ~30 |

---

## Порядок работы

```
1. Domain модели + UseCase (Фаза 1)
2. ViewModels (Фаза 2)
3. UI компоненты (Фаза 3.1-3.4)
4. Навигация + Deep links (Фаза 3.5)
5. Строки + темизация (Фаза 4)
6. Тесты (Фаза 5)
7. ./gradlew assembleDebug
```

---

## Правила (обязательно)

1. Все новые строки ОДНОВРЕМЕННО в `values/strings.xml` + `values-ru/strings.xml`
2. getString() НЕ в полях Activity — только в методах
3. Все ошибки через `ErrorHandler.handle()` — НЕ `Log.e`
4. Chat toolbar: фиксированная высота `@dimen/custom_toolbar_height`, elevation 0dp
5. Все chat activities: `setDecorFitsSystemWindows(window, false)` в onCreate
6. Перед коммитом: `./gradlew assembleDebug`
7. НЕ bump'ать версию
8. Marshallers field order: server proto определяет field numbers

---

## Файлы для справки

| Файл | Назначение |
|------|------------|
| `doc/ANDROID_AI_MARKETPLACE_INTEGRATION.md` | Server-side integration guide |
| `doc/PATTERNS.md` | Код-паттерны проекта |
| `doc/AI_V2_TESTING.md` | Тест-кейсы AI v2 |
| `app/src/main/java/lavender/client/android/data/grpc/GrpcAIv2Client.kt` | API методы (7 Marketplace) |
| `app/src/main/java/lavender/client/android/data/proto/AiV2Proto.kt` | Proto модели |
| `app/src/main/java/lavender/client/android/ui/ai/AiV2AgentListActivity.kt` | Текущий список агентов |
