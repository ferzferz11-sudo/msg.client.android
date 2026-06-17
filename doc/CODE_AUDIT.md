# Lava Messenger — Android Code Audit

**Дата:** 2026-06-17 | **Версия:** v1.1.3.33

---

## 1. Общая статистика

| Метрика | Значение |
|---------|----------|
| Kotlin файлов | 160 |
| RealGrpcClient | 882 (было 3810, -77%) |
| gRPC модулей | 12 + Marshallers |
| ChatListActivity | ~364 LOC |
| NewChatActivity | ~754 LOC (было 1473, -49%) |
| Chat модулей | 6 |

---

## 2. Топ-10 файлов по размеру

| # | Файл | LOC | Статус |
|---|------|-----|--------|
| 1 | HermesGrpc.kt | 1876 | 🟡 Большой, но изолированный |
| 2 | MessengerProto.kt | 1791 | 🟢 Proto-код |
| 3 | NewChatActivity.kt | 754 | 🟢 Рефакторинг завершён |
| 4 | GrpcMarshallers.kt | 1395 | 🟢 Marshaller classes |
| 5 | OwlGrpc.kt | 1145 | 🟢 Изолированный AI-модуль |
| 6 | MessageAdapter.kt | 870 | 🟢 Нормальный |
| 7 | RealGrpcClient.kt | 882 | 🟢 Orchestrator |
| 8 | GrpcClient.kt | 780 | 🟡 Facade, требует оптимизации |
| 9 | GrpcChatListClient.kt | 641 | 🟢 Нормальный |
| 10 | ChatInputDelegate.kt | 567 | 🟢 Самый большой модуль чата |

---

## 3. Сильные стороны ✅

1. Модульная структура gRPC — 12 модулей, God Object устранён
2. Модульная структура NewChatActivity — 6 делегатов
3. Единый ErrorHandler — все ошибки через AppLog
4. Facade паттерн — GrpcClient чистый API для UI
5. MVVM для ChatList — ViewModel выделен
6. E2EE — выделено в ChatE2EEDelegate
7. Theme system — ThemeApplier + ThemeUtils
8. i18n — все строки в resources, en+ru
9. Read receipts broadcast — SharedFlow chain
10. BearerTokenInterceptor — автоматическая подстановка JWT

## 4. Слабые стороны 🔴

### Средний приоритет
1. **GrpcClient facade — 780 строк** — много proxy-методов без логики → фаза 4
2. **HermesGrpc + OwlGrpc — суммарно ~3021 строк** → фаза 5
3. **NewChatActivity всё ещё 754 строки** — можно вынести observers, calls, drafts

### Низкий приоритет
4. **Тестирование отсутствует** — 0 unit-тестов → фаза 3
5. **Hardcoded IP** — `13.140.25.249` в `fetchServersList()`
6. **usePlaintext()** — все каналы без TLS (dev)
7. **SharedPreferences без шифрования** — CredentialStore

---

## 5. Рекомендации по приоритету

### 🟡 Следующие (v1.1.3.34-36)
1. Unit-тесты для gRPC клиента
2. GrpcClient facade оптимизация
3. AI Chats domain layer

### 🟢 Backlog
4. Pagination для чатов
5. Incremental history loading
6. Certificate pinning
7. Encrypted SharedPreferences
8. ProfileActivity рефакторинг (719 LOC)
