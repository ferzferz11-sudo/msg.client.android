# Заметки сессии 10 — 2026-06-15

## Что сделано

### ProfileClient fixes (Android)
- Исправлены все проблемы с ProfileClient после первоначального PR
- `unaryCall()` — единообразное использование вместо сломанных method references
- Inline Marshaller objects вместо `newInstance()` (deprecation fix)
- Добавлены недостающие imports для ProfileV2 proto classes
- ProtoMarshaller сделан internal

### Документация
- Обновлены TASKS.md, INTEGRATION_SESSION.md, PROMPT.md, PROMPT_ANDROID.md
- Актуализированы версии: сервер v1.2.1.0, Android v1.1.3.13
- Обновлены индексы документации

## Коммиты
- `7782993` — fix: ProfileClient — use unaryCall consistently
- `73da2e1` — fix: use inline Marshaller objects in ProfileClient.unaryCall
- `d707fa8` — fix: add missing imports for ProfileV2 proto classes
- `1a73dee` — fix: suppress newInstance deprecation warning in ProfileClient

## Следующие шаги
1. **ChatList v2** — новая версия списка чатов с улучшенным UI/UX
2. **Тесты для ProfileService v2** — unit-тесты (сервер + Android)
3. Тестирование ProfileService v2 на dev сервере (после того как ferz соберёт APK)
