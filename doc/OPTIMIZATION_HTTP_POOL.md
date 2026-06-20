# Оптимизация: Singleton OkHttpClient

**Приоритет:** P1 | **Сложность:** Лёгкая | **Эффект:** -40% время повторных загрузок

---

## Проблема

`OkHttpClient()` создаётся заново в `EditProfileActivity` и `ProfileViewModel` каждый раз при загрузке. Нет connection pooling — каждый запрос устанавливает новое TCP соединение.

## Решение

Создать глобальный singleton `OkHttpClient` с connection pool и переиспользовать его везде.

### 1. Создать `network/HttpClient.kt`

```kotlin
package com.lavender.messenger.network

import okhttp3.OkHttpClient
import okhttp3.ConnectionPool
import java.util.concurrent.TimeUnit

object HttpClient {
    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .connectionPool(ConnectionPool(5, 5, TimeUnit.MINUTES))
        .build()
}
```

### 2. Найти все места, где создаётся `OkHttpClient()`

Выполнить в проекте поиск:

```
OkHttpClient()
```

Заменить каждое создание на `HttpClient.client`:

```kotlin
// Было:
val client = OkHttpClient()
val request = Request.Builder().url(url).build()
client.newCall(request).enqueue(callback)

// Стало:
import com.lavender.messenger.network.HttpClient

val request = Request.Builder().url(url).build()
HttpClient.client.newCall(request).enqueue(callback)
```

### 3. Что проверить

- `EditProfileActivity` — загрузка аватара
- `ProfileViewModel` — загрузка профиля
- Любые другие места с `OkHttpClient()` или `OkHttpClient.Builder()`
- Не трогать `gRPC` клиенты — у них своя логика

### 4. Тест

После изменений:
1. Загрузить аватар — должен работать
2. Повторная загрузка — должна быть быстрее (reuse TCP connection)
3. Проверить в debugger: `HttpClient.client.connectionPool.connectionCount()` > 0 после первого запроса
