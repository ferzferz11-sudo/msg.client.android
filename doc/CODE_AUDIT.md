# Lava Messenger — Android Code Audit

**Дата:** 2026-06-17 | **Версия:** v1.1.3.32

---

## 1. Общая статистика

| Метрика | Значение |
|---------|----------|
| Kotlin файлов | 153 |
| RealGrpcClient | 882 (было 3810, -77%) |
| gRPC модулей | 12 + Marshallers |
| ChatListActivity | ~364 LOC (было 2802 в v1, -87%) |
| ChatList модулей | 10 |

---

## 2. Топ-10 файлов по размеру

| # | Файл | LOC | Статус |
|---|------|-----|--------|
| 1 | HermesGrpc.kt | 1880 | 🟡 Большой, но изолированный |
| 2 | MessengerProto.kt | 1791 | 🟢 Proto-код, генерируемый |
| 3 | NewChatActivity.kt | 1473 | 🟡 Требует рефакторинга |
| 4 | GrpcMarshallers.kt | 1394 | 🟢 Marshaller classes |
| 5 | OwlGrpc.kt | 1145 | 🟢 Изолированный AI-модуль |
| 6 | MessageAdapter.kt | 870 | 🟢 Нормальный для адаптера |
| 7 | RealGrpcClient.kt | 882 | 🟢 Orchestrator, рефакторинг завершён |
| 8 | GrpcClient.kt | 780 | 🟢 Facade, стабильный |
| 9 | ChatListFABs.kt | 470 | 🟡 Самый большой модуль ChatList |
| 10 | ProfileActivity.kt | 719 | 🟡 Можно разбить |

---

## 3. Архитектурный аудит

### Сильные стороны ✅
1. Модульная структура gRPC — 12 модулей, God Object устранён
2. Facade паттерн — GrpcClient чистый API для UI
3. MVVM для ChatList — ViewModel выделен
4. ChatListActivity модули — 10 файлов, каждый <500 LOC
5. BearerTokenInterceptor — автоматическая подстановка JWT
6. E2EE — выделено в E2EEManager
7. Theme system — ThemeApplier + ThemeUtils
8. i18n — все строки в resources, en+ru
9. Read receipts broadcast — SharedFlow chain
10. UpdateCoordinator — чистый вынос логики

### Слабые стороны 🔴

#### Средний приоритет
1. **NewChatActivity — 1473 строки** — всё в одном Activity
2. **GrpcClient facade — 780 строк** — много proxy-методов без логики
3. **Error handling не унифицирован** — ErrorHandler.kt используется частично
4. **HermesGrpc + OwlGrpc — суммарно 3025 строк** — AI-логика в gRPC слое

#### Низкий приоритет
5. **Тестирование отсутствует** — 0 unit-тестов для gRPC
6. **Hardcoded IP** — `13.140.25.249` в `fetchServersList()`
7. **usePlaintext()** — все каналы без TLS (dev)
8. **SharedPreferences без шифрования** — CredentialStore

---

## 4. Производительность

### Проблемы
1. Нет batching запросов — каждый чат отдельно
2. Background retry loop — до 50 retry (30s max) — батарея
3. Lazy channel connecting — первый вызов медленный

### Возможные оптимизации
1. Pagination для чатов (GetChatsRequest поддерживает limit/offset)
2. Incremental history loading (loadHistory загружает все 100 сразу)

---

## 5. Рекомендации по приоритету

### 🟡 Следующие 2-3 сессии
1. Рефакторинг NewChatActivity (ViewModel + Fragments)
2. Унификация error handling
3. Тесты для gRPC клиента

### 🟢 Backlog
4. HermesGrpc/OwlGrpc выделение в domain layer
5. Pagination для чатов
6. Incremental history loading
7. Certificate pinning
8. Encrypted SharedPreferences
