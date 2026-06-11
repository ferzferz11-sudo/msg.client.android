# Lavender Messenger (Android) — Документация

Индекс документов Android-клиента.

---

## Быстрый старт

1. **README.md** (в корне) — описание проекта, сборка, структура
2. **CHANGELOG.md** (в корне) — история версий Android-клиента
3. **doc/TASKS.md** — таск-трекер Android

---

## Файлы документации

| Файл | Назначение | Когда читать |
|------|-----------|-------------|
| `doc/TASKS.md` | Известные проблемы и задачи Android-клиента | В начале сессии |
| `doc/README.ru.md` | Описание проекта на русском | Для общего контекста |

---

## Связанные документы (сервер)

Документация серверной части в репозитории `/root/msg/doc/`:
- `INTEGRATION_SESSION.md` — текущий контекст интеграции
- `TASKS.md` — серверный таск-трекер
- `AI_SERVICES.md` — архитектура AI сервисов (OWL + Hermes), API, proto mapping
- `PITFALLS.md` — подводные камни Android и сервера
- `HERMES_ORCHESTRATOR_DOC.md` — архитектура Hermes

---

## Скрипты

| Скрипт | Назначение |
|--------|------------|
| `scripts/release.sh` | Выпуск нового релиза Android (git tag, deploy, GitHub Release) |
| `scripts/deploy_android.sh` | Деплой APK на сервер (SCP, архив, versions.json) |

### Выпуск релиза

```bash
# 1. Собери APK локально и загрузи на сервер
scp app/build/outputs/apk/release/app-release.apk root@13.140.25.249:/var/www/lavender/lavender.apk

# 2. Обнови version.txt
echo "1.1.2.9" > version.txt

# 3. Запусти скрипт релиза
./scripts/release.sh 1.1.2.9
```

Скрипт автоматически:
- Проверяет version.txt
- Коммитит и пушит изменения
- Создаёт git tag v1.1.2.9
- Копирует APK в архив на сервере
- Обновляет version.txt и versions.json
- Создаёт GitHub Release (если установлен gh CLI)
