# Lava Messenger — Android Code Audit

**Дата:** 2026-06-18 | **Версия:** v1.1.3.35

---

## 1. Общая статистика

| Метрика | Значение |
|---------|----------|
| Kotlin файлов | 161 |
| RealGrpcClient | 883 (было 3810, -77%) |
| gRPC модулей | 12 + Marshallers + Extensions |
| ChatListActivity | ~364 LOC |
| GrpcClient | 106 LOC (было 780, -86%) |
| NewChatActivity | ~754 LOC (было 1473, -49%) |
| Chat модулей | 6 |

---

## 2. Топ-10 файлов по размеру

| # | Файл | LOC | Статус |
|---|------|-----|--------|
| 1 | HermesGrpc.kt | 1876 | 🟡 Большой, но изолированный |
| 2 | MessengerProto.kt | 1791 | 🟢 Proto-код |
| 3 | GrpcMarshallers.kt | 1395 | 🟢 Marshaller classes |
| 4 | OwlGrpc.kt | 1145 | 🟢 Изолированный AI-модуль |
| 5 | RealGrpcClient.kt | 883 | 🟢 Orchestrator |
| 6 | MessageAdapter.kt | 870 | 🟢 Нормальный |
| 7 | GrpcClientExtensions.kt | ~600 | 🟢 Domain extensions |
| 8 | NewChatActivity.kt | 754 | 🟢 Рефакторинг завершён |
| 9 | GrpcChatListClient.kt | 641 | 🟢 Нормальный |
| 10 | ChatInputDelegate.kt | 567 | 🟢 Самый большой модуль чата |

---

## 3. Сильные стороны ✅

1. Модульная структура gRPC — 12 модулей + extensions, God Object устранён
2. Модульная структура NewChatActivity — 6 делегатов
3. Единый ErrorHandler — все ошибки через AppLog
4. Facade + Extensions паттерн — GrpcClient 106 LOC, домены в extensions
5. MVVM для ChatList — ViewModel выделен
6. E2EE — выделено в ChatE2EEDelegate
7. Theme system — ThemeApplier + ThemeUtils
8. i18n — все строки в resources, en+ru
9. Read receipts broadcast — SharedFlow chain
10. BearerTokenInterceptor — автоматическая подстановка JWT

## 4. Слабые стороны 🔴

### Средний приоритет
1. **HermesGrpc + OwlGrpc — суммарно ~3021 строк** → фаза 5
2. **NewChatActivity всё ещё 754 строки** — можно вынести observers, calls, drafts
3. **GrpcClientExtensions ~600 строк** — большой файл, но это extensions (не facade)

### Низкий приоритет
4. **Тестирование отсутствует для UI** — 0 unit-тестов для Activities
5. **Hardcoded IP** — `13.140.25.249` в `fetchServersList()`
6. **usePlaintext()** — все каналы без TLS (dev)
7. **SharedPreferences без шифрования** — CredentialStore

---

## 5. Рекомендации по приоритету

### 🟡 Следующие (v1.1.3.36)
1. AI Chats domain layer
2. NewChatActivity финальный рефакторинг

### 🟢 Backlog
3. Pagination для чатов
4. Incremental history loading
5. Certificate pinning
6. Encrypted SharedPreferences
7. ProfileActivity рефакторинг (719 LOC)
