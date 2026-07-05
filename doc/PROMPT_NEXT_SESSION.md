# Prompt: Android Client — Next Session

**Версия:** v1.3.2.3 | **Ветка:** feat/1.3.2.x | **Дата:** 2026-07-05

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

## v1.3.2.3 — Conference Fixes + Theme Adaptation + Navigation

### Добавлено

- **Конференции — визуальное отличие:** значок в списке чатов, кнопка лобби, тип чата
- **Конференции — сервер:** поле `type` в `CreateGroupChatRequest`, сервер создаёт `conference` тип
- **Информация о группе/конференции:** FAB [+] для добавления участников, шторка с опциями
- **О программе:** отображение версии приложения

### Улучшено

- **Темы:** чекбокс выбора, уведомления, FAB [+] адаптированы к кастомным темам

### Исправлено

- **Навигация:** кнопка "назад" из Безопасности/Уведомлений возвращает на "Доп. настройки"
- **isNavigatingDeeper:** флаг сбрасывается только в `settingsActivityLauncher` callback

### Изменения в файлах

| Файл | Изменение |
|------|-----------|
| `item_chat.xml` | +tvConferenceBadge, btnEnterLobby виден для конференций |
| `ChatAdapter.kt` | Conference badge, тип чата "Конференция", btnEnterLobby с кликом |
| `ChatToolbarDelegate.kt` | btnLobby для всех участников, requestLayout(), openProfile для конференций |
| `ProfileActivity.kt` | chatType, setupGroupFab(), showConferenceActionSheet(), showAddParticipantSheet() |
| `activity_profile.xml` | +fabAddMember FAB |
| `ThemeApplier.kt` | +fabAddMember, +tvConferenceBadge |
| `ChatListToolbar.kt` | showAboutDialog: +appVersion, fix isNavigatingDeeper reset |
| `dialog_about.xml` | +appVersionText |
| `NotificationAdapter.kt` | ThemeStore colors вместо хардкода |
| `NewChatActivity.kt` | Убран FAB из чата (перенесён в ProfileActivity) |
| `activity_new_chat.xml` | Убран fabAddParticipant |
| `ChatListFABs.kt` | showCreateConferenceDialog — type "conference" |
| `messenger.proto` (server) | +type field 6 в CreateGroupChatRequest |
| `server_chats.go` (server) | req.Type вместо хардкода "group" |

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

- [ ] Invite code for JoinCompany
- [ ] Company chat notifications
- [ ] Company settings (edit name, delete)
- [ ] Push notifications for company events
- [ ] Per-company position cache (for non-primary companies in multi-company)
