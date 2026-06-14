# Заметки сессии 9 — 2026-06-14

## Что сделано

### ProfileClient (Android)
- Создан `data/grpc/ProfileClient.kt`
- Автоопределение ProfileService v2 через /info endpoint (profile >= "2.0")
- Fallback на legacy ChatService методы для prod
- Методы: getProfile, updateProfile, updateAvatar, getUserSettings, updateUserSettings
- Вызов fetchServerInfo() автоматически при connect()

### Proto messages
- Добавлены data classes для ProfileService v2 в MessengerProto.kt
- GetProfileRequestProto, GetProfileResponseProto, UpdateProfileV2RequestProto, etc.

### GrpcClient facade
- Добавлены ProfileService v2 методы в фасад
- isProfileV2Supported, profileServiceVersion

## Коммиты
- `dbbf266` — feat: ProfileService v2 client + Typing/CallSession compat

## Следующие шаги
1. Тестирование ProfileService v2 на dev сервере (после того как ferz соберёт APK)
2. Редеплой prod сервера до v1.2.1.0
3. Тесты для ProfileService v2
