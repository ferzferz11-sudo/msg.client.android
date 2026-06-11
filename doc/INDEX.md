# Lavender Messenger (Android) — Документация

Индекс документов Android-клиента.

---

## Быстрый старт

1. **doc/PROMPT_ANDROID.md** — промпт для новой сессии (читать первым)
2. **CHANGELOG.md** (в корне) — история версий Android-клиента
3. **doc/TASKS.md** — таск-трекер Android

---

## Файлы документации

| Файл | Назначение | Когда читать |
|------|-----------|-------------|
| `doc/PROMPT_ANDROID.md` | Промпт для новой сессии — структура, правила, архитектура | В начале каждой сессии |
| `doc/TASKS.md` | Известные проблемы, бэклог, ключевые решения | В начале сессии |
| `doc/INDEX.md` | Этот файл — индекс | Для навигации |

---

## Скрипты

| Скрипт | Назначение |
|--------|------------|
| `scripts/release.sh` | Выпуск нового релиза Android (git tag, deploy, GitHub Release) |
| `scripts/deploy_android.sh` | Деплой APK на сервер (SCP, архив, versions.json) |

### Выпуск релиза

```bash
# 1. Собери APK локально и загрузи на сервер
scp app/build/outputs/apk/release/app-release.apk lava:/var/www/lavender/lavender.apk

# 2. Обнови version.txt
echo "1.1.2.9" > version.txt

# 3. Запусти скрипт релиза
./scripts/release.sh 1.1.2.9
```

Скрипт автоматически:
- Проверяет version.txt
- Коммитит и пушит изменения
- Создаёт git tag v1.1.2.9
- Скачивает APK с сервера, загружает в GitHub Release
- Копирует APK в архив на сервере
- Обновляет version.txt и versions.json
- Создаёт GitHub Release с changelog и APK

---

## Процесс разработки

### Коммит и пуш

1. OWL вносит изменения в код
2. `git add -A && git commit -m "описание" && git push`
3. Пользователь локально: `git pull && ./gradlew assembleRelease`
4. Проверяет APK, при необходимости — деплой на сервер

### Важно

- **НЕ запускать `./gradlew assembleRelease` на сервере** — OOM kill (нужно 2GB+, на сервере не хватает)
- `compileDebugKotlin` на сервере — OK (использует ~1GB)
- APK собирать локально, затем загружать на сервер через SCP

---

## Связанные документы (сервер)

Документация серверной части в репозитории `/root/msg/doc/`:
- `INTEGRATION_SESSION.md` — текущий контекст интеграции
- `TASKS.md` — серверный таск-трекер
- `AI_SERVICES.md` — архитектура AI сервисов (OWL + Hermes), API, proto mapping
- `PITFALLS.md` — подводные камни Android и сервера
- `HERMES_ORCHESTRATOR_DOC.md` — архитектура Hermes
