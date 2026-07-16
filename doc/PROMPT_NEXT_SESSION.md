# Prompt: Android Client — Next Session

**Версия:** v1.3.2.13 (готова к выпуску) | **Ветка:** feat/1.3.2.x | **Дата:** 2026-07-16

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
- `doc/GOTCHAS.md` — known gotchas (500+ entries)
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

---

## v1.3.2.13 — Token Refresh Race Condition Fix

### Исправлено

**Token refresh race condition — вынужденный повторный вход:**
- Три независимых пути вызывали `GrpcClient.refreshToken()`: периодический (60s), `ensureFreshToken()` (перед gRPC), `forceTokenRefresh()` (pull-to-refresh)
- Сервер использует ротацию refresh token с reuse detection: повторная отправка старого JTI → `RevokeDevice()` → устройство отключается
- `isRefreshing` флаг защищал только `ensureFreshToken()`. Два других пути работали без блокировки → параллельная отправка одного токена → reuse detected → forced re-login
- Заменён `isRefreshing: Boolean` на `refreshGuard: AtomicBoolean` с `compareAndSet` для всех трёх путей. `waitForRefreshComplete()` polling 100ms

**Lint — HardwareIds warning:**
- `@SuppressLint("HardwareIds")` на `getDeviceId()` — ANDROID_ID используется намеренно

**Duration API:**
- `withTimeoutOrNull(10000)` → `withTimeoutOrNull(10.seconds)` (3 места)

---

## v1.3.2.12 — Defensive Error Handling for Chat Entry Crashes

### Исправлено

**ThemeApplier.apply() — главный источник крашей на некоторых устройствах:**
- ~50 операций с view без try-catch. На некоторых комбинациях manufacturer + API level падал `WindowInsetsControllerCompat`, `DrawableCompat.wrap(bg.mutate())` или другие операции
- Разбит на 5 секций с try-catch: WindowInsets, background, toolbar, widgets, panels/forms
- `ThemeUi.bind()` — обёрнут `ThemeApplier.apply()` в try-catch

**ChatToolbarDelegate.setup():**
- `setSupportActionBar()` + `ThemeStore.currentTheme().toColorInt()` без try-catch
- Все 3 метода (secret/favorites/normal) обёрнуты с fallback на базовый UI

**NewChatActivity — полная защита от крашей:**
- `initDelegates()` / `initSharedViews()` → try-catch + finish()
- `setupDelegates()` → try-catch
- `combine` flow collector → try-catch
- `fetchChatMetadata` callback → try-catch
- `setupTheme()` → try-catch
- `setDecorFitsSystemWindows` → try-catch

**Убран мёртвый код:**
- ThemeApplier искал `tvToolbarTitle` / `tvToolbarSubtitle` которых нет в layout

---

## v1.3.2.11 — Crash Fixes

### Исправлено

**Android 12 краш при входе в чат:**
- `NewChatActivity.onCreate()` — `setDecorFitsSystemWindows(false)` вызывался ДО `super.onCreate()` (единственная активити с таким порядком)
- На API 31+ это ломало инициализацию decor view → краш
- Перемещён вызов после `super.onCreate()`

**Краш при шаринге картинки:**
- `ShareReceiverActivity.uploadFile()` — `readBytes()` читает весь файл в память
- Большая картинка → `OutOfMemoryError` (extends `Error`, не `Exception`) → не ловился
- Добавлен `catch (e: OutOfMemoryError)` с Toast "Файл слишком большой"

---

## v1.3.2.10 — Call Notification Fixes + Company Theme + AddMember Widget

### Исправлено

**Звонки — уведомление не исчезает:**
- `CallActivity.onDestroy()` + `CallManager.hangup/reject/clearCurrentCall` теперь вызывают `dismissCallNotification()`

**Звонки — push при открытом CallActivity:**
- `LavenderMessagingService.handleIncomingCall()` — проверка `CallManager.currentCall.value != null` перед показом push

**Звонки — push всегда при INITIATE (сервер):**
- Push теперь отправляется только если `!delivered` (receiver offline)

**Звонки — stale ACCEPT/REJECT (сервер):**
- `GetCallStatus()` guard — ACCEPT/REJECT игнорируются если статус `completed`/`rejected`
- Клиент игнорирует ACCEPT когда нет активного звонка

**Тема — название компании чёрное на тёмном:**
- `ThemeApplier` добавил `tvCompanyName.setTextColor(textPrimary)`

### Добавлено

**AddMemberSheet:**
- Unified виджет добавления участников (группы + компании)
- Поиск, мульти-выбор, кнопка действия
- Для компаний — диалог выбора должности

---

## v1.3.2.9 — Call Fix + Typing Fix

### Исправлено

**Звонки — TURN credentials без JWT:**
- `CallActivity.fetchTurnCredentials()` не отправлял Authorization заголовок → сервер возвращал 401 → fallback на STUN-only → за CGNAT P2P не работал
- Добавлен `AuthManager.getBearerToken()` в Authorization заголовок

**Звонки — имя собеседника:**
- При исходящем звонке показывался UUID вместо имени
- `CallNavigator.startCall()` не передавал `SENDER_NAME` — добавлен параметр `senderName`

**Сервер (требует деплой):**
- `server_chat.go` — добавлена обработка room switch: сервер теперь обновляет `currentRoom` и `hub.SetV2Room()` при переключении чата
- Без этого typing рассылался в старый чат (тот что при авторизации)

---

## v1.3.2.5 — Client Audit: Critical Marshaller Fixes + Thread Safety

### Исправлено

**Marshaller'ы (3 критических бага):**
- `AuthResponseV2` — User field mapping: пропускалось поле 4 (avatar_url), поля 5-7 были сдвинуты
- `GetPinnedMessagesRequest` — userId/chatId поменяны местами (field 1/2)
- `GetFavoritesResponse` — сервер отправляет v1 `Message`, клиент парсил как v2. Добавлен конвертер v1→v2
- `GetPinnedMessagesRequest` — добавлены limit/offset (field 3/4)

**Thread safety (4 исправления):**
- `RealGrpcClient.currentUsername` — никогда не присваивался (всегда null). Добавлен `setUsername()` + вызов из `SessionManager.updateSession()`
- `markRead` callback — вызывался дважды (onMessage + onClose). Убран из onMessage
- `toggleMute`/`deleteChat` — callback'и на gRPC thread мутировали `allChats`. Обёрнуты в `viewModelScope.launch(Dispatchers.Main)`
- `db()` — улучшена проверка null-before-assign

**Темы:**
- `CustomThemeProto` — добавлено поле `isDark`
- Парсинг/сериализация `outgoingTextColor` (field 18), `incomingTextColor` (field 19)

**ViewModel:**
- `ChatListActivity` — теперь использует `ViewModelProvider` вместо ручного создания

**Сервер:**
- `chatV2RowToProto` — добавлены IsSecret, PeerPublicKey, E2eeReady, ActiveAgentId, AgentMode, CompanyId, CompanyChatAccess, CompanyMinPositionLevel
- `ChatWithAIV2` response — добавлены HasRagContext и ModelUsed
- `MarkRead` — JWT контекст + BroadcastToRoom("READ_ALL")
- JWT auth fix — все handlers извлекают `GetUserID(ctx)` с fallback

---

## v1.3.2.4 — Toolbar Standardization + Group Chat Fix + Theme Consistency

### Добавлено

- **Конференции — визуальное отличие:** значок в списке чатов, кнопка лобби, тип чата
- **Конференции — сервер:** поле `type` в `CreateGroupChatRequest`, сервер создаёт `conference` тип
- **Информация о группе/конференции:** FAB [+] для добавления участников, шторка с опциями
- **О программе:** отображение версии приложения
- **Групповой чат из контактов:** при добавлении 2+ контактов с галкой "Создать чат сразу" создаётся групповой чат
- **Подсказка "Все контакты добавлены":** в шторке добавления участников, когда список пуст

### Улучшено

- **Темы:** чекбокс выбора, уведомления, FAB [+] адаптированы к кастомным темам
- **Тулбары — единый стандарт:** все тулбары используют `toolbar_background` + `custom_toolbar_height` + `navigationIconTint` + `titleTextColor` = `colorOnPrimary`
- **Аватар в тулбаре чатов:** увеличен 48dp → 56dp
- **Аватар в шторке профиля:** увеличен 100dp → 120dp, сдвинут влево
- **Toast при добавлении контактов:** показывает количество ("Добавлено контактов: 6")

### Исправлено

- **Навигация:** кнопка "назад" из Безопасности/Уведомлений возвращает на "Доп. настройки"
- **isNavigatingDeeper:** флаг сбрасывается только в `settingsActivityLauncher` callback
- **Групповой чат:** добавление 2+ контактов с галкой "Создать чат" теперь создаёт группу, а не прямые чаты
- **Цвет стрелки "назад" в ContactsActivity:** `setSupportActionBar()` конфликтовал с темой — убран, тулбар управляется напрямую
- **Цвет стрелки в ThemesActivity:** аналогичный фикс
- **Цвет иконок в SuperAdminActivity:** `setHomeAsUpIndicator()` + ручной tint после смены

### Изменения в файлах

| Файл | Изменение |
|------|-----------|
| `activity_chat_list.xml` | Аватар 48dp → 56dp (FrameLayout + CircleImageView) |
| `dialog_profile.xml` | Аватар 100dp → 120dp, marginEnd=40dp |
| `activity_log_viewer.xml` | Фон `toolbar_background`, высота `custom_toolbar_height`, +navigationIconTint/titleTextColor, +xmlns:app |
| `activity_theme_palette.xml` | Высота `custom_toolbar_height`, фон `toolbar_background`, +navigationIconTint |
| `activity_new_chat.xml` | +titleTextColor |
| `widget_chat.xml` | +titleTextColor |
| `activity_themes.xml` | +titleTextColor |
| `activity_contacts.xml` | +titleTextColor, убран toolbarUserAvatar |
| `ContactsActivity.kt` | Убран `setSupportActionBar()`, убран toolbarUserAvatar, +getColorOnPrimary/setBackIcon, toast contacts_added с количеством |
| `ThemesActivity.kt` | Убран `setSupportActionBar()`, +getColorOnPrimary/setBackIcon |
| `SuperAdminActivity.kt` | +getColorOnPrimary, tint после setHomeAsUpIndicator |
| `LogViewerActivity.kt` | Убран `setDisplayHomeAsUpEnabled(true)` |
| `ProfileActivity.kt` | +setEmptyState для шторки добавления участников |
| `strings.xml` (EN/RU) | +all_contacts_already_in_group, contacts_added с %d, -contact_added |
| `ChatListFABs.kt` | R.string.create_chat_after (было create_direct_chat_after) |

---

## v1.3.2.2 — Company Polish

### Исправлено

- **Fatal crash** в EditProfileActivity — кнопка "Удалить профиль" не имела `android:id` → NPE
- **Диалог создания компании** — подсказка была от редактирования username, исправлена на "Название компании"
- **Навигация после создания** — теперь открывает "Моя компания" вместо возврата на главную

### Улучшено

- **CompanyProfileActivity** — адаптация к кастомным темам (ThemeUi.bind + applyThemeToViews)
- **Логотип компании** — загрузка в "Моя компания" (клик → галерея), отображение в карточке в профиле
- **companyCard** добавлен в ThemeApplier для автоматической адаптации

### Изменения в файлах

| Файл | Изменение |
|------|-----------|
| `activity_edit_profile.xml` | +btnDeleteProfile id, companyCard с логотипом |
| `activity_company_profile.xml` | +ivCompanyLogo, адаптация к темам |
| `EditProfileActivity.kt` | Исправлен диалог создания, загрузка логотипа, навигация |
| `CompanyProfileActivity.kt` | ThemeUi, applyThemeToViews, загрузка логотипа |
| `ThemeApplier.kt` | +companyCard в список карточек |

---

## v1.3.2.0 — Company System

### Новые компоненты

| Компонент | Файл | Назначение |
|-----------|------|------------|
| Proto модели | `data/proto/CompanyProto.kt` | 30+ data classes (Company, Position, Member, CompanyChat, UserPublicInfo) |
| Marshallers | `data/grpc/CompanyMarshallers.kt` | 30+ marshallers для Company RPC |
| gRPC клиент | `data/grpc/GrpcCompanyClient.kt` | 17 RPC методов |
| CompanyProfileActivity | `CompanyProfileActivity.kt` | Управление компанией (табы: участники/позиции/чаты) |
| AddMemberActivity | `AddMemberActivity.kt` | Добавление участников из контактов |
| CompanyListFragment | `ui/company/CompanyListFragment.kt` | Списки с actions |
| Адаптеры | `ui/company/Company*.kt` | Member, Position, Chat адаптеры |

### Изменения в существующих файлах

| Файл | Изменение |
|------|-----------|
| `UserSession.kt` | +4 поля: companyId, companyName, positionTitle, positionLevel |
| `MessengerProto.kt` | GetProfileResponseProto +4, ChatInfoProto +3 |
| `GrpcMarshallers.kt` | Парсинг company полей (3 места) |
| `Entities.kt` | ChatEntity +3, toEntity/toDomain |
| `AppDatabase.kt` | Миграция 12→13 |
| `GrpcChatClient.kt` | Конвертация company полей |
| `GrpcChatListV2Client.kt` | Конвертация company полей |
| `RealGrpcClient.kt` | fetchAdminStatus сохраняет company info |
| `SessionManager.kt` | updateSession +4 company параметра |
| `ChatListViewModel.kt` | buildSections: company filter + access control |
| `ChatListTabs.kt` | Таб "Компания" |
| `ChatAdapter.kt` | Company badge |
| `EditProfileActivity.kt` | Секция "Компания" (создать/открыть) |
| `ProfileActivity.kt` | Company card в профиле |
| `ProfileViewModel.kt` | ProfileData +4 company полей |

### Access Control

| Уровень | member chats | management chats | owner_only chats |
|---------|-------------|-----------------|-----------------|
| Employee (0) | ✅ | ❌ | ❌ |
| Manager (1) | ✅ | ✅ | ❌ |
| Top Manager (2) | ✅ | ✅ | ❌ |
| Owner (3) | ✅ | ✅ | ✅ |

### Company RPCs (messenger.CompanyService)

```
CreateCompany, GetCompany, UpdateCompany, DeleteCompany, ListCompanies
CreatePosition, UpdatePosition, DeletePosition, ListPositions
AddMember, RemoveMember, UpdateMemberPosition, ListMembers
CreateCompanyChat, SetCompanyChatAccess, GetCompanyChats
JoinCompany, LeaveCompany
GetUserInfo, GetCompanyByUser
GetUserCompanies, SetPrimaryCompany
```

### Multi-Company Support

- Пользователь может состоять в нескольких компаниях
- `GetUserCompanies` — список всех компаний с позициями и is_primary флагом
- `SetPrimaryCompany` — выбор основной компании (отображается в профиле)
- Автоматический выбор основной компании при создании
- Long-press на кнопке "Моя компания" → switcher для выбора компании
- Per-company access control: для каждого чата проверяется позиция в конкретной компании

### String Resources (EN + RU)

+35 company strings (company, members, positions, chats, access levels)

---

## Backlog

**Company:**
- [ ] Invite code for JoinCompany
- [ ] Company chat notifications
- [ ] Company settings (edit name, delete)
- [ ] Push notifications for company events
- [ ] Per-company position cache (for non-primary companies in multi-company)

**Optimization (medium):**
- [ ] `ChatAdapter.notifyDataSetChanged()` → `notifyItemChanged()` для updateOnlineUsers/updateAllUsers (сейчас полный rebind при каждом обновлении онлинов)
- [ ] `deletedMessageHashes` — добавить LRU cap (10000) для предотвращения неограниченного роста в долгих сессиях
- [ ] `readBytes()` в ChatInputDelegate — проверять размер файла через ContentResolver.query() перед загрузкой в память (OOM риск на устройствах с 2GB RAM)

**Cleanup (low):**
- [ ] `GrpcClient.kt` — unused `context` параметр в facade методах (pinChat, unpinChat, etc.)
- [ ] `GrpcClient.kt` — redundant CoroutineScope (life time = app, same as RealGrpcClient.scope)
- [ ] `ChatListActivity` — два OnScrollListener на одном RecyclerView (объединить)
