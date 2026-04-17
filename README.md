# MSG Android Client

Android клиент для системы MSG (Message System).

## Описание проекта

MSG Android Client - это нативное Android приложение, предназначенное для обмена сообщениями через систему MSG. Приложение разработано на Kotlin с использованием современных Android практик и архитектурных подходов.

## Основные функции

- 📱 Отправка и получение сообщений в реальном времени
- 🔐 Безопасная аутентификация пользователей
- 💾 Локальное хранение истории сообщений
- 🎨 Современный пользовательский интерфейс
- 🔄 Синхронизация данных между устройствами

## Технический стек

- **Язык программирования**: Kotlin
- **Min SDK**: 29 (Android 10.0)
- **Target SDK**: 36 (Android 15)
- **Архитектура**: MVVM
- **UI Framework**: Android Jetpack (ViewBinding)
- **Навигация**: Android Navigation Component

## Требования

- Android 10.0 (API level 29) и выше
- Минимум 2 ГБ оперативной памяти
- Интернет-соединение для работы с сервером MSG

## Установка

### Из исходного кода

1. Клонируйте репозиторий:
```bash
git clone <repository-url>
cd msg/client/android
```

2. Откройте проект в Android Studio или используйте командную строку:

```bash
# Сборка проекта
./gradlew build

# Установка на устройство
./gradlew installDebug
```

## Структура проекта

```
app/
├── src/main/
│   ├── java/msg/client/android/
│   │   ├── MainActivity.kt          # Главная активность
│   │   ├── ui/                      # UI компоненты
│   │   ├── data/                    # Слой данных
│   │   ├── viewmodel/               # ViewModel
│   │   └── network/                 # Сетевой слой
│   ├── res/                         # Ресурсы
│   └── AndroidManifest.xml          # Манифест приложения
└── build.gradle.kts                 # Конфигурация сборки
```

## Конфигурация

Приложение требует настройки серверных endpoints для подключения к MSG системе. Конфигурационные параметры находятся в файле `local.properties`:

```properties
# MSG Server Configuration
msg.server.url=https://your-msg-server.com
msg.server.api_key=your-api-key
```

## Сборка и тестирование

### Сборка Debug версии
```bash
./gradlew assembleDebug
```

### Сборка Release версии
```bash
./gradlew assembleRelease
```

### Запуск тестов
```bash
# Юнит-тесты
./gradlew test

# Инструментальные тесты
./gradlew connectedAndroidTest
```

## Версионирование

Проект следует семантическому версионированию (SemVer):
- **MAJOR.MINOR.PATCH** (например, 1.0.0)

Текущая версия: **1.0** (versionCode: 1)

## Лицензия

[Добавьте информацию о лицензии]

## Контакты

- Разработчик: [Ваше имя]
- Email: [ваш-email@example.com]
- GitHub: [ваш-github-username]

## Вклад в проект

1. Fork проекта
2. Создайте feature branch (`git checkout -b feature/AmazingFeature`)
3. Commit изменения (`git commit -m 'Add some AmazingFeature'`)
4. Push в branch (`git push origin feature/AmazingFeature`)
5. Откройте Pull Request
