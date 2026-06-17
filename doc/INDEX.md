# Lava Messenger — Android Документация

**Версия:** v1.1.3.32 | **Обновлено:** 2026-06-17

---

## Порядок чтения для новой сессии

1. **PROMPT_ANDROID.md** — полный контекст: статус, приоритеты, changelog, правила, архитектура
2. **SESSION_NOTES.md** — история последних сессий
3. **PATTERNS.md** — паттерны и правила перед написанием кода
4. **CODE_AUDIT.md** — аудит кода (сильные/слабые места)
5. **CHANGELOG.md** — история изменений

---

## Индекс

| Файл | Назначение | Когда читать |
|------|-----------|-------------|
| `doc/PROMPT_ANDROID.md` | Промпт (статус + задачи + changelog + правила) | **Всегда в начале** |
| `doc/SESSION_NOTES.md` | Заметки сессий | В начале сессии |
| `doc/PATTERNS.md` | Паттерны и правила | Перед написанием кода |
| `doc/CODE_AUDIT.md` | Аудит кода | При планировании оптимизаций |
| `doc/REMOTE_AGENT.md` | Remote Agent | При работе с Remote Agent |
| `doc/ChatListActivity_v1_REFERENCE.kt` | v1 reference (2802 LOC) | Для переноса кода из v1 |
| `doc/PLAN_REFACTOR_GRPC.md` | План рефакторинга gRPC (ЗАВЕРШЁН) | Справочно |
| `../CHANGELOG.md` | История изменений | Справочно |

## Сервер

| Файл | Назначение |
|------|-----------|
| `/root/msg/doc/INDEX.md` | Индекс серверной документации |
| `/root/msg/doc/INTEGRATION_SESSION.md` | Интеграционная сессия |

---

## Правила документации

- При старте новой сессии: PROMPT_ANDROID → SESSION_NOTES → PATTERNS
- После каждого значимого изменения: SESSION_NOTES + PROMPT_ANDROID
- При каждом релизе: CHANGELOG, PROMPT_ANDROID (changelog + статус)
- SESSION_NOTES ≤ 200 строк → архивировать старые сессии
- Не создавать новые doc-файлы без крайней необходимости
