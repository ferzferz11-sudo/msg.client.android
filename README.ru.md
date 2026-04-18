# Lavanda (Android клиент)

Безопасный обмен сообщениями

**Автор:** Pavel Davydov (ferz)

Безопасный обмен сообщениями в реальном времени с gRPC сервером и множественными клиентскими реализациями.

---

## Описание проекта

Lavanda (Android клиент) - это нативное Android приложение, предназначенное для обмена сообщениями через систему Lavanda. Приложение разработано на Kotlin с использованием современных Android практик и архитектурных подходов.

## Основные функции

- 📱 Отправка и получение сообщений в реальном времени через gRPC
- 🔌 Bidirectional streaming с Go сервером
- 👤 Система имен пользователей для чата
- 💾 Локальное хранение истории сообщений
- 🎨 Современный пользовательский интерфейс (Material Design)
- 🔄 Дубликат-фильтрация сообщений для предотвращения эха
- 📊 Отслеживание состояния подключения в реальном времени

## Технический стек

- **Язык программирования**: Kotlin
- **Min SDK**: 29 (Android 10.0)
- **Target SDK**: 37 (Android 14)
- **Compile SDK**: 37
- **Архитектура**: MVVM
- **UI Framework**: Android Jetpack (ViewBinding)
- **Асинхронность**: Kotlin Coroutines + StateFlow
- **Сетевой протокол**: gRPC (bidirectional streaming)
- **Протокол**: Protobuf (protobuf-lite)
- **Сервер**: Go gRPC сервер (localhost:50051)

## Требования

- Android 10.0 (API level 29) и выше
- Минимум 2 ГБ оперативной памяти
- Запущенный Go gRPC сервер на localhost:50051 (или 10.0.2.2:50051 для эмулятора)
- Интернет-соединение для работы с сервером

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
│   │   ├── MainActivityMinimal.kt   # Точка входа с диалогом имени
│   │   ├── ChatActivity.kt          # Главный чат UI с RecyclerView
│   │   ├── ChatViewModel.kt         # Управление состоянием с gRPC
│   │   ├── ui/
│   │   │   ├── MessageAdapter.kt    # RecyclerView адаптер для сообщений
│   │   │   └── MessageViewHolder.kt # ViewHolder для сообщений
│   │   ├── data/
│   │   │   ├── models/
│   │   │   │   └── Message.kt       # Модель сообщения
│   │   │   ├── proto/
│   │   │   │   ├── MessageProto.kt  # Protobuf сообщение
│   │   │   │   └── ProtoUtils.kt    # Утилиты для protobuf
│   │   │   └── grpc/
│   │   │       ├── GrpcClient.kt    # Обертка для gRPC клиента
│   │   │       └── RealGrpcClient.kt # Реализация gRPC с custom marshaller
│   │   └── viewmodel/
│   ├── res/                         # Ресурсы
│   └── AndroidManifest.xml          # Манифест приложения
└── build.gradle.kts                 # Конфигурация сборки
```

## Конфигурация

### Сервер
Приложение подключается к Go gRPC серверу по адресу:
- **Устройство**: localhost:50051
- **Эмулятор Android**: 10.0.2.2:50051

### gRPC Настройки
- **Протокол**: Bidirectional streaming
- **Keep-alive**: 30 секунд интервал, 5 секунд таймаут
- **Marshaller**: Custom MessageProtoMarshaller
- **Метод**: messenger.ChatService/Chat

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

Текущая версия: **0.9.2** (versionCode: 10)

### Версия 0.9.2 - Цветовая палитра Lavender
- 🎨 Новая цветовая палитра Lavender (Deep Purple, Lavender Mist, Soft Lilac, Silver Fog, Dark Slate)
- 🎨 Обновлены светлая и темная темы с новыми цветами
- 📝 Выбор адреса сервера из предустановленного списка (192.168.1.135:50051, 10.0.2.2:50051, localhost:50051)
- 👤 Имя пользователя предустанавливается в диалоге приветствия, если ранее вводилось
- 🟢 Индикатор статуса сервера под списком выбора сервера
- 🔘 Кнопка "Войти" отключена, когда сервер недоступен
- 🔄 Кнопка обновления для ручной проверки доступности сервера
- 🎨 Улучшен UI: кнопка темы показывает текущее значение (Тема: Светлая/Темная)
- 🎨 Заменен разделитель на декоративные точки между основной кнопкой и настройками
- 🎨 Цвет заголовка приложения использует lavender_mist в обеих темах
- 🎨 Исправлен индикатор статуса сервера - теперь круглая форма
- 📝 Сокращены тексты кнопок и подсказок для лучшей видимости
- 📝 Локализовано сообщение входа в чат
- 🗑️ Удален неиспользуемый экспертный режим
- 🗑️ Удален тест соединения из меню (функционал остался в диалоге)

### Версия 0.9.1 - Working Bidirectional Streaming
- ✅ Работающий bidirectional gRPC streaming
- ✅ Сервер получает сообщения и транслирует обратно
- ✅ Custom protobuf marshaller
- ✅ Дубликат-фильтрация сообщений
- ✅ Корректная обработка ошибок подключения

## Лицензия

[Добавьте информацию о лицензии]

## Контакты

- Разработчик: Pavel Davydov (ferz)
- Email: [ваш-email@example.com]
- GitHub: [ваш-github-username]

## Вклад в проект

1. Fork проекта
2. Создайте feature branch (`git checkout -b feature/AmazingFeature`)
3. Commit изменения (`git commit -m 'Add some AmazingFeature'`)
4. Push в branch (`git push origin feature/AmazingFeature`)
5. Откройте Pull Request

---

## English Documentation

Для документации на английском языке, см. [README.md](README.md)
