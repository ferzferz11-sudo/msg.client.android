# Lava Messenger — План оптимизации Android клиента

**Дата:** 2026-06-18 | **Версия:** v1.1.3.35 | **Ветка:** feat/1.1.3.x

---

## 1. ТЕКУЩЕЕ СОСТОЯНИЕ

### 1.1 Общая статистика

| Метрика | Значение |
|---------|----------|
| Kotlin файлов (main) | ~130 |
| Kotlin файлов (test) | 11 |
| Общий LOC (main) | ~39,745 |
| Общий LOC (test) | ~2,206 |
| gRPC модулей | 12 + Marshallers |
| Unit-тестов | 53 (после v1.1.3.34) |
| Покрытие тестами | ~12% |

### 1.2 Топ-15 файлов по размеру

| # | Файл | LOC | Проблема |
|---|------|-----|----------|
| 1 | HermesGrpc.kt | 1,872 | AI-код в gRPC слое |
| 2 | MessengerProto.kt | 1,791 | Proto (автогенерация) |
| 3 | GrpcMarshallers.kt | 1,395 | Hand-written marshallers |
| 4 | OwlGrpc.kt | 1,146 | AI-код в gRPC слое |
| 5 | RealGrpcClient.kt | 883 | Orchestrator |
| 6 | MessageAdapter.kt | 870 | UI adapter, 10+ ViewHolder |
| 7 | NewChatActivity.kt | 755 | Делегаты уже вынесены |
| 8 | ProfileActivity.kt | 719 | Не рефакторена |
| 9 | GrpcClient.kt | 699 | Facade (после extensions: 106) |
| 10 | ShareReceiverActivity.kt | 642 | Не рефакторена |
| 11 | GrpcChatListClient.kt | 642 | 3 ответственности |
| 12 | RemoteAgentSettingsActivity.kt | 630 | Не рефакторена |
| 13 | RemoteAgentViewModel.kt | 602 | UI + бизнес-логика |
| 14 | ConferenceLobbyActivity.kt | 582 | Не рефакторена |
| 15 | ChatInputDelegate.kt | 567 | Chat delegate |

---

## 2. КРИТИЧЕСКИЕ ПРОБЛЕМЫ (P0)

### 2.1 Пароль keystore в исходном коде

**Файл:** `app/build.gradle.kts:42-47`

```kotlin
create("release") {
    storeFile = file("release.keystore")
    storePassword = "lavender123"
    keyAlias = "lavender"
    keyPassword = "lavender123"
}
```

**Решение:**
- Использовать environment variables или `local.properties` (не коммитить)
- `storePassword = System.getenv("KEYSTORE_PASSWORD") ?: ""`
- Добавить `local.properties` в `.gitignore`

### 2.2 Хардкод IP-адресов серверов (6 мест)

| Файл | Строка | IP |
|------|--------|----|
| SessionManager.kt | 546 | 13.140.25.249:50051 |
| LavenderMessagingService.kt | 60 | 82.146.43.235 |
| CallActivity.kt | 242 | 13.140.25.249:8082 |
| GrpcServerDiscoveryClient.kt | 25 | 13.140.25.249 |
| ChatListAuth.kt | 25-26 | 13.140.25.249:50051 |
| ServersActivity.kt | 176, 183 | 13.140.25.249 |

**Решение:**
- Единый `ServerConfig.kt` объект с fallback логикой
- Базовый адрес из BuildConfig (build flavor) или settings

### 2.3 Cleartext HTTP трафик

**Файл:** `AndroidManifest.xml:37` — `android:usesCleartextTraffic="true"`

**Решение:**
- Для dev: network_security_config.xml с explicit rules
- Для prod: только HTTPS
- TURN credentials через HTTPS endpoint

### 2.4 E2EE ключи в SharedPreferences

**Файл:** `E2EEManager.kt:31-36` — приватные ECDH ключи в `SharedPreferences("e2ee_keys")`

**Решение:**
- Android Keystore для приватных ключей
- `EncryptedSharedPreferences` для shared secrets
- Миграция существующих ключей

---

## 3. ВЫСОКИЕ ПРОBLEМЫ (P1)

### 3.1 Hand-written protobuf marshallers (1395 LOC)

**Файл:** `GrpcMarshallers.kt` — ручная сериализация через `CodedOutputStream`.

**Проблемы:**
- Хрупко: при изменении proto ломается без предупреждений
- Дублирование: те же поля сериализуются в 2+ местах
- Невозможно тестировать

**Решение:**
- Использовать protobuf-liteгенерацию для streaming методов
-или оставить если streaming требует кастомного framing

### 3.2 HermesGrpc (1872 LOC) + OwlGrpc (1146 LOC) = 3018 LOC в gRPC слое

AI-логика перемешана с transport layer.

**Решение:** см. Фазу 5 ниже — AI domain layer.

### 3.3 30 Activities без Navigation Component

Все переходы через `startActivity(Intent(...))`. Нет навигационного графа.

**Решение:** Постепенная миграция на Navigation Component (не для всех сразу).

### 3.4 Activity references в delegate classes

6 делегатов хранят прямую ссылку на `AppCompatActivity`:

| Delegate | Строка |
|----------|--------|
| ChatInputDelegate | 57 |
| ChatToolbarDelegate | -- |
| ChatSelectionDelegate | -- |
| ChatSearchDelegate | -- |
| ChatE2EEDelegate | -- |
| ChatMessageMenuDelegate | -- |

**Решение:** Использовать `WeakReference<AppCompatActivity>` или передавать `Context` + lifecycle.

### 3.5 211 `lateinit var` declarations

Многие в delegate классах — crash risk при неправильном порядке инициализации.

**Решение:** Заменить на constructor injection или `lazy` delegate.

---

## 4. СРЕДНИЕ ПРОБЛЕМЫ (P2)

### 4.1 Дублирование attachBaseContext (12+ Activity)

Каждая Activity копирует locale logic:
```kotlin
override fun attachBaseContext(newBase: Context) {
    val prefs = newBase.getSharedPreferences("lavender_prefs", MODE_PRIVATE)
    val languageCode = prefs.getString("language", "ru") ?: "ru"
    // ...
}
```

**Решение:** `BaseActivity` с этой логикой или Kotlin delegation.

### 4.2 Дублированные классы

| Класс | Файлы | Статус |
|-------|-------|--------|
| NotificationActivity | 2 (legacy + new) | Удалить legacy |
| ChatListViewModel | 2 (active + legacy) | Удалить legacy |
| MentionAdapter | 2 (в разных пакетах) | Объединить |

### 4.3 35+ `runOnUiThread` вместо structured concurrency

**Файлы:** ChatListFABs.kt (13), ChatInputDelegate.kt (10), ChatSelectionDelegate.kt (3)

**Решение:** Заменить на `withContext(Dispatchers.Main)` в coroutines.

### 4.4 10+ uncancellable CoroutineScope singletons

| Объект | Строка |
|--------|--------|
| GrpcClient | 21 |
| RealGrpcClient | 42 |
| SessionManager | 19 |
| CallManager | 19 |
| CallController | 31 |
| RemoteAgentService | 43 |

**Решение:** Один application-scoped `CoroutineScope`,注入 через DI.

### 4.5 SharedPreferences без шифрования

40+ вызовов `getSharedPreferences("lavender_prefs", MODE_PRIVATE)` — данные в открытом виде.

**Решение:** `EncryptedSharedPreferences` для чувствительных данных (токены, ключи).

### 4.6 isRetrying не @Volatile

**Файл:** `RealGrpcClient.kt:215` — `var isRetrying = false` используется из разных потоков.

**Решение:** Добавить `@Volatile` или использовать `AtomicBoolean`.

### 4.7 maxInboundMessageSize = 64MB

**Файл:** `GrpcConnectionManager.kt:103` — потенциальная DoS уязвимость.

**Решение:** Ограничить до 4-8 MB (разумный максимум для чата).

### 4.8 ProGuard отключен

**Файл:** `app/build.gradle.kts:55` — `isMinifyEnabled = false`.

**Решение:** Включить для release builds с правильными rules.

### 4.9 JSch вне version catalog

**Файл:** `app/build.gradle.kts:133` — `implementation("com.jcraft:jsch:0.1.55")` hardcoded.

**Решение:** Перенести в `libs.versions.toml`.

---

## 5. НИЗКИЕ ПРОБЛЕМЫ (P3)

### 5.1 ~40K LOC с 53 тестами

- Нет instrumented/UI тестов
- Нет тестов для Activities, ViewModels, delegates
- Нет integration тестов

### 5.2 Нет Compose

Весь UI на XML layouts (97 файлов). View-based без преимуществ типобезопасности.

### 5.3 97 layout XML файлов

Много мелких layouts, некоторые дублируются.

---

## 6. ПЛАН ОПТИМИЗАЦИИ ПО ФАЗАМ

### Фаза 1: Безопасность (v1.1.4.0)

**Цель:** Закрыть критические дыры.

| # | Задача | Оценка |
|---|--------|--------|
| 1 | Keystore пароль → env variables / local.properties | 1ч |
| 2 | ServerConfig.kt — единый источник IP-адресов | 2ч |
| 3 | network_security_config.xml — cleartext только для dev | 1ч |
| 4 | E2EE ключи → Android Keystore / EncryptedSharedPreferences | 4ч |
| 5 | EncryptedSharedPreferences для токенов | 2ч |

**Итого:** ~10ч

### Фаза 2: Архитектура — Дублирование (v1.1.4.1)

**Цель:** Убрать копипасту.

| # | Задача | Оценка |
|---|--------|--------|
| 1 | BaseActivity с locale logic (убрать 12 дублей) | 2ч |
| 2 | Удалить legacy классы (NotificationActivity, ChatListViewModel, MentionAdapter) | 1ч |
| 3 | replace runOnUiThread → withContext(Dispatchers.Main) | 3ч |
| 4 | Единый CoroutineScope (application-scoped) | 2ч |
| 5 | @Volatile для isRetrying + thread safety audit | 1ч |

**Итого:** ~9ч

### Фаза 3: Архитектура — Безопасность + Build (v1.1.4.2)

**Цель:** Улучшить build и уменьшить размер APK.

| # | Задача | Оценка |
|---|--------|--------|
| 1 | maxInboundMessageSize 64MB → 4MB | 0.5ч |
| 2 | Включить ProGuard для release | 2ч |
| 3 | JSch → libs.versions.toml | 0.5ч |
| 4 | EncryptedSharedPreferences для CredentialStore | 2ч |
| 5 | Delegate classes → WeakReference / Context | 2ч |

**Итого:** ~7ч

### Фаза 4: Тестирование (v1.1.4.3)

**Цель:** Увеличить покрытие.

| # | Задача | Оценка |
|---|--------|--------|
| 1 | Unit-тесты для HermesGrpc (streaming mock) | 4ч |
| 2 | Unit-тесты для OwlGrpc | 3ч |
| 3 | Unit-тесты для RealGrpcClient | 3ч |
| 4 | Unit-тесты для MessageAdapter | 2ч |
| 5 | Unit-тесты для ChatInputDelegate | 2ч |

**Итого:** ~14ч

### Фаза 5: AI Domain Layer (v1.1.4.4)

**Цель:** Вынести AI логику из gRPC слоя.

| # | Задача | Оценка |
|---|--------|--------|
| 1 | Создать `data/ai/` пакет | 0.5ч |
| 2 | AiChatManager — единый менеджер | 3ч |
| 3 | OwlDataSource — OWL логика | 3ч |
| 4 | HermesDataSource — Hermes логика | 4ч |
| 5 | gRPC модули → thin transport | 2ч |
| 6 | Тесты AI domain layer | 4ч |

**Итого:** ~16.5ч

### Фаза 6: UI Рефакторинг (v1.1.4.5)

**Цель:** Уменьшить размер Activity и адаптеров.

| # | Задача | Оценка |
|---|--------|--------|
| 1 | NewChatActivity финал (754→400 LOC) | 3ч |
| 2 | ProfileActivity делегаты (719→300 LOC) | 4ч |
| 3 | MessageAdapter split (870→300 LOC) | 4ч |
| 4 | GrpcChatListClient split (642→3×200) | 2ч |
| 5 | RemoteAgentSettingsActivity рефакторинг | 3ч |

**Итого:** ~16ч

---

## 7. СВОДКА ПЛАНА

| Фаза | Версия | Что | LOC эффект | Тестов | Часов |
|------|--------|-----|-----------|--------|-------|
| 1 | v1.1.4.0 | Безопасность | 0 | 0 | ~10 |
| 2 | v1.1.4.1 | Дублирование | -500 (dupes) | 0 | ~9 |
| 3 | v1.1.4.2 | Build + Delegate safety | -100 | 0 | ~7 |
| 4 | v1.1.4.3 | Тестирование | 0 | +30 | ~14 |
| 5 | v1.1.4.4 | AI Domain Layer | 3018→800 | +20 | ~16.5 |
| 6 | v1.1.4.5 | UI Рефакторинг | -2500 | +10 | ~16 |
| **Итого** | | | **~-3100** | **+60** | **~72.5** |

### Метрики после оптимизации

| Метрика | Сейчас | Цель |
|---------|--------|------|
| Файлов > 500 LOC | 15 | 5 |
| Покрытие тестами | ~12% | ~25% |
| AI-код в gRPC | 3018 | < 800 |
| Дублированные классы | 3 | 0 |
| runOnUiThread вызовов | 35+ | < 5 |
| lateinit var | 211 | < 100 |
| IP-адресов хардкод | 6 | 0 |

---

## 8. ИЗВЕСТНЫЕ ПРОБЛЕМЫ

| Проблема | Статус | Причина отсрочки |
|----------|--------|-----------------|
| Favorites flickering | Исправлено v1.1.2.8 | -- |
| Messages visible after agent response | Отложено | Нужна отладка на устройстве |
| server migration warnings | Не критично | PostgreSQL artifact |

---

## 9. ПРАВИЛА

1. NE запускать assembleRelease на сервере (OOM kill)
2. compileDebugKotlin только при необходимости
3. version.txt обновлять ДО release.sh
4. Коммитить после каждого значимого изменения
5. userId (UUID) — всегда как ключ, НЕ username
6. Для кастомных тем: новые FAB добавлять в ThemeApplier
7. Статический first item (Favorites) добавлять ДО загрузки данных
8. Новые gRPC методы → в соответствующий модуль
9. Proto поля: всегда сверять номера с messenger.proto

---

## 10. ПРИОРИТЕТЫ

### Немедленно (v1.1.4.0)
1. Keystore пароль → environment
2. ServerConfig — единый IP
3. E2EE ключи → Android Keystore

### Среднесрочно (v1.1.4.1-4.3)
4. BaseActivity + дублирование
5. AI domain layer (критично для архитектуры)
6. Delegate safety (WeakReference)

### Долгосрочно (v1.1.4.4-4.5)
7. Тестирование AI модулей
8. UI рефакторинг (ProfileActivity, MessageAdapter)
