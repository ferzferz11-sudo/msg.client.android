# Prompt: Android Client — Next Session

**Версия:** v1.3.1.21 | **Ветка:** feat/1.3.1.x | **Дата:** 2026-07-03

---

## Быстрый старт

- Проект: `/Users/paveld/LavenderMessenger-Android`
- Сборка: `./gradlew assembleDebug`
- Сервер: `/Users/paveld/LavenderMessenger-server/`
- Сервер docs: `/Users/paveld/LavenderMessenger-server/doc/CLIENT_INTEGRATION.md`

---

## Сервер

| | Dev | Prod |
|--|-----|------|
| gRPC | 50052 | 50051 |
| HTTP | 8083 | 8082 |
| Сервис | lavender-server-dev | lavender-server |

**Деплой сервера:** НЕ делать без явного указания.

---

## Полезные ссылки

- `doc/PATTERNS.md` — паттерны кода и правила
- `doc/GOTCHAS.md` — known gotchas (500+ entries, v1.3.1.21)
- `doc/INDEX.md` — project overview, архитектура
- `doc/AI_V2_TESTING.md` — AI v2 testing
- `CHANGELOG.md` — version history

---

## Правила

См. полный список в `doc/PATTERNS.md` §Rules (20 правил).

Ключевые:
1. НЕ компилировать Android на сервере (OOM kill)
2. НЕ деплоить на prod без явного указания
3. UUID ALWAYS for routing, username ONLY for display
4. Все ошибки через `ErrorHandler.handle()`
5. v2 server only — никаких v1 fallbacks
6. Перед коммитом: `./gradlew assembleDebug`
7. НЕ bump'ать версию — bump делает только пользователь
