# Release Notes — Lavender Messenger v1.3.4.8

**Дата:** 2026-08-01  
**Фокус:** Performance, Memory Leaks, Crash Fixes, DiffUtil Migration

---

## Выполнено (7 задач)

### HIGH Priority

#### 1. Proto marshallers — field number audit
**Статус:** DONE — 0 mismatches из 57+ marshallers  
Полная верификация всех клиентских marshallers против серверного `messenger.pb.go`. Все field numbers совпадают.

#### 2. E2EE key exchange reliability
**Статус:** DONE  
- Добавлен `exchangeInProgress` guard — предотвращает параллельные попытки обмена ключами
- Добавлен `retryJob` — отмена pending retry при уходе с экрана
- Добавлен `cancelPendingRetries()` + вызов в `NewChatActivity.onDestroy()`
- Проверка `activity.isFinishing || activity.isDestroyed` перед каждым retry

#### 3. SessionManager ANR risk
**Статус:** DONE  
- `waitForRefreshComplete()` — добавлен timeout 10s с принудительным сбросом `refreshGuard`
- Все вызовы `ensureFreshToken()`/`forceTokenRefresh()` идут через `Dispatchers.IO`
- Main thread guards уже на месте во всех критических методах

### MEDIUM Priority

#### 4. Sticker system edge cases
**Статус:** DONE  
- **CRITICAL FIX:** `StickerPreferencesManager.loadStickerList()` — `getPrefs()` был вне try-catch → `IllegalStateException` при нажатии звёздочки
- **CRITICAL FIX:** `StickerPackListAdapter` + `StickerPackAdapter` — `setAnimation(url)` на HTTP URL → `FileNotFoundException`. Исправлено на `setAnimationFromUrl()`
- `loadStickerList()` — `getString()` → `optString()` для защиты от потери всего списка при битом JSON
- `MAX_FAVORITES = 100` — ограничение роста SharedPreferences
- `StickerPackListAdapter.unbind()` — добавлена Glide очистка

#### 5. Chat list performance (500+ chats)
**Статус:** DONE  
- `onlineUsers` — кэширован как `Set<String>` (убран `.toSet()` на каждый bind)
- `allUsers` — кэширован как `Map<String, UserInfoProto>` (убран `.firstOrNull()` O(n))
- `getOtherParticipant()` — кэширован в `MutableMap` (убран JSON parse на каждый bind)
- Итого: **3 O(n) операции → O(1)** на каждый bind

#### 6. Forward message multi-device sync
**Статус:** DONE — уже реализован корректно  
- `forwardedFrom` field 50 в `MessageV2Proto` совпадает с сервером
- `forwardedFrom` field 8 в `SendMessageV2RequestProto` совпадает с сервером

### LOW Priority

#### 7. Sticker star/favorite crash
**Статус:** DONE (включено в #4)  
- Корневая причина: `getPrefs()` вне try-catch в `loadStickerList()`/`saveStickerList()`/`clearRecent()`

---

## Изменения в файлах

| Файл | Изменение |
|------|-----------|
| `ChatAdapter.kt` | `onViewRecycled()`, performance caches (Set/Map), `getOrComputeOtherParticipant()` |
| `UserAdapter.kt` | `onViewRecycled()` + `clearAvatar()` |
| `SuperAdminAdapter.kt` | `onViewRecycled()` + `clearAvatar()`/`clearIcon()` |
| `ParticipantAdapter.kt` | `onViewRecycled()` + `clearAvatar()`, миграция на ListAdapter |
| `MentionAdapter.kt` | `onViewRecycled()` + `clearAvatar()`, миграция на ListAdapter |
| `ThemeAdapter.kt` | Миграция на ListAdapter + DiffUtil |
| `DeviceAdapter.kt` | Миграция на ListAdapter + DiffUtil |
| `StickerPreferencesManager.kt` | try-catch обёртки, optString, MAX_FAVORITES=100 |
| `StickerPackListAdapter.kt` | `setAnimationFromUrl()` для HTTP, Glide cleanup в unbind |
| `StickerPackAdapter.kt` | `setAnimationFromUrl()` для HTTP |
| `ChatE2EEDelegate.kt` | exchangeInProgress guard, retryJob, cancelPendingRetries() |
| `NewChatActivity.kt` | `onDestroy()` → `cancelPendingRetries()` |
| `SessionManager.kt` | waitForRefreshComplete timeout 10s |
| `ThemeUtilsTest.kt` | NEW — 5 unit тестов |

---

## Proto Marshallers Audit Summary

| Категория | Количество | Совпадения |
|-----------|-----------|------------|
| Core message types | 4 | 4/4 |
| Chat-related | 14 | 14/14 |
| Participant & contact | 5 | 5/5 |
| Profile & avatar | 8 | 8/8 |
| Message operations | 7 | 7/7 |
| Draft | 3 | 3/3 |
| Notification & muting | 3 | 3/3 |
| Theme | 4 | 4/4 |
| Device | 2 | 2/2 |
| Password reset | 2 | 2/2 |
| Empty requests | 5 | 5/5 |
| **Total** | **57+** | **57+** |

---

## Оставшаяся работа

### LOW Priority

1. **Accessibility improvements**
   - Добавить `contentDescription` к `ivSelectionIndicator`, `ivStickerImage`, `ivReadStatus`
   - Обновить `ivMessageImage` contentDescription

2. **Dark mode — hardcoded colors в XML**
   - Заменить `android:textColor="#FFFFFF"` на `?attr/colorOnSurface`

3. **Unit test coverage**
   - Добавить тесты для адаптеров
   - Добавить интеграционные тесты для gRPC

4. **StickerPackCreateActivity**
   - Проверять результат `updateStickerPack()` перед показом success toast
   - Обрабатывать `OutOfMemoryError` в `compressImage()`

5. **Log tags**
   - Заменить `"TAG"` на meaningful identifiers во всех файлах
