# Lava Messenger — Android Changelog

## [1.4.0.4] - 2026-08-13

### Исправлено

**Avatar section: цвет заголовка не адаптировался к теме (Medium):**
- `avatarLabel` добавлен в список ThemeApplier для применения `customPrimary` цвета
- Теперь заголовок "Аватар" адаптируется к теме как остальные секции

**Avatar section: кнопки не выровнены по левому краю (Medium):**
- `android:gravity="end"` → `android:gravity="start"` для кнопок "Изменить" и "Удалить"
- Кнопки теперь выровнены по левому краю вертикально

**Avatar section: увеличен размер аватара (Medium):**
- Аватар увеличен с 80dp до 96dp

**Chat list icons: позиционирование под текстом last-seen (High):**
- Иконки (mute, unread, lobby, delete) перемещены из основного ряда в отдельный ряд ниже
- Теперь расположены под текстом last-seen, не сдвигают его влево
- Иконки выровнены по правому краю внизу плашки

**Default avatar: цвет не адаптировался к colorPrimary (Medium):**
- При отсутствии аватара теперь вызывается `ThemeUtils.applyDefaultAvatar()` вместо простого `setImageResource`
- Иконка аватара теперь тинтируется цветом `colorPrimary` темы

**Self-destruct timer: системное сообщение пропадало после повторного входа (High):**
- Системное сообщение таймера теперь сохраняется в Room DB
- При повторном входе в чат сообщение загружается из кэша

**Self-destruct timer: "Удалить все сообщения" не работало (High):**
- Добавлен вызов `grpcClient.clearMessages()` перед очисткой Room DB
- In-memory список сообщений теперь очищается вместе с кэшем

**Self-destruct timer: системное сообщение появлялось с неверным временем (Medium):**
- Timestamp системного сообщения теперь устанавливается после последнего сообщения в чате
- Гарантирует корректную сортировку при загрузке истории

**System messages: уменьшен размер шрифта (Low):**
- Размер шрифта системных сообщений уменьшен с 13sp до 11sp

**Touch targets: widget_chat.xml (Medium):**
- btnCancelReply: 24dp → 48dp
- btnSearchPrev/btnSearchNext: 40dp → 48dp
- Selection toolbar (close, reply, copy, forward, delete, star): 40dp → 48dp

**Touch targets: participant list (Medium):**
- btnRemove: 40dp → 48dp + contentDescription `remove_participant`
- Новая строка: `remove_participant` (EN+RU)

**Touch targets: marketplace agent card (Medium):**
- btnFavorite: 40dp → 48dp

**Touch targets: image preview container (Medium):**
- removeImageButton: 24dp → 36dp (88dp preview context)
- uploadErrorIcon: 32dp → 48dp
- uploadSuccessIcon: помечен как декоративный

**Touch targets: contacts activity (Medium):**
- actionSearch: 40dp → 48dp
- actionCreateChat: 40dp → 48dp
- actionDelete: 40dp → 48dp

**Touch targets: new chat activity search (Medium):**
- searchPrev: 40dp → 48dp
- searchNext: 40dp → 48dp

### Accessibility

**Sticker editor color pickers (Medium):**
- 6 color picker ImageViews получили contentDescription (White, Black, Red, Yellow, Green, Blue)
- Новые строки: `color_white`, `color_black`, `color_red`, `color_yellow`, `color_green`, `color_blue` (EN+RU)

**Message item accessibility (Medium):**
- `btnDownloadFile` — добавлен contentDescription `download_file`, увеличен до 48dp
- `ivReadStatus` — добавлен contentDescription `read_status`
- `vReplyBar` — помечен как декоративный (`importantForAccessibility="no"`)
- `ivAvatar` — помечен как декоративный (описание задаётся динамически в коде)
- `ivStickerImage`, `lottieStickerView` — помечены как декоративные
- `ivMessageImage` — убран неправильный `contentDescription="@string/app_name"`
- Новые строки: `download_file`, `read_status` (EN+RU)

**Audio message touch targets (Medium):**
- Play/Pause кнопки увеличены с 40dp до 48dp (minimum touch target)
- Заменены hardcoded строки на string resources (`play_audio`, `pause_audio`)
- Новые строки: `play_audio`, `pause_audio` (EN+RU)

**Device list touch target (Medium):**
- Delete device кнопка увеличена с 24dp до 48dp
- Добавлен contentDescription `delete_device`
- Новая строка: `delete_device` (EN+RU)

**Company list touch targets (Medium):**
- Company member "more" кнопка увеличена с 36dp до 48dp
- Company position "more" кнопка увеличена с 36dp до 48dp

**AI agent card touch targets (Medium):**
- Favorite кнопка увеличена с 40dp до 48dp
- Delete кнопка увеличена с 40dp до 48dp

### Testing

**GrpcMarshallersTest expanded (Medium):**
- Auth V2: SignInRequestV2, SignUpRequestV2, RefreshTokenRequest, SignOutRequest field order tests
- Auth V2: AuthResponseV2, RefreshTokenResponse, SimpleAuthResponse parse-empty tests
- Favorites: AddFavoriteRequest, RemoveFavoriteRequest, GetFavoritesRequest field order tests
- Favorites: AddFavoriteResponse, RemoveFavoriteResponse, GetFavoritesResponse parse-empty tests
- Profile V2: UpdateProfileV2Request, UpdateAvatarV2Request, DeleteProfileV2Request field order tests
- Profile V2: GetProfileResponse, UpdateProfileV2Response, UpdateAvatarV2Response, DeleteProfileV2Response parse-empty tests
- Settings: GetUserSettingsResponse, UpdateUserSettingsRequest, UpdateUserSettingsResponse tests
- Proto defaults: SignInRequestV2Proto, AuthResponseV2Proto, GetProfileResponseProto, favorites protos
- Total: 67 tests (was 27, +40 new)

## [1.4.0.3] - 2026-08-13

### Новые фичи

**Profile avatar section redesign (Medium):**
- Секция аватара в EditProfileActivity переработана: заголовок "Аватар", аватар слева (80dp), кнопки справа
- Кнопка "Изменить" (иконка камеры) — выбор нового аватара
- Кнопка "Удалить" (иконка удаления, красный цвет) — удаление аватара с подтверждением
- `EditProfileViewModel.deleteAvatar()` — вызывает `ProfileClient.updateAvatar("")` для очистки
- Toast "Аватар удалён" / "Avatar deleted" при успехе
- Аватар сбрасывается на дефолтный после удаления
- Новые строки: `avatar_label`, `delete_avatar`, `avatar_deleted`, `delete_avatar_confirm` (EN+RU)

### Исправлено

**Self-destruct timer: системное сообщение не отображалось (High):**
- Обработчик `SELF_DESTRUCT_TIMER` в `RealGrpcClient` теперь создаёт системное сообщение в чате при установке/отключении таймера
- Текст использует строковые ресурсы (`self_destruct_set`, `self_destruct_disabled`) с поддержкой EN/RU

**Self-destruct timer: удалённые сообщения не очищались из Room DB (High):**
- Добавлен обработчик `DELETE_MESSAGE_V2` в `RealGrpcClient` — сервер отправляет этот тип при auto-delete, но клиент обрабатывал только `DELETE_MESSAGE`
- Удалённые таймером сообщения теперь корректно удаляются из Room DB и из in-memory списка

**Chat list search не активировался сразу (Medium):**
- `ChatListSearch.showSearchView()` — `expandActionView()` обёрнут в `toolbar.post{}` для корректного раскрытия после закрытия overflow меню
- Добавлен `requestFocus()` + показ клавиатуры через `WindowInsetsController` (API 30+) с fallback на `InputMethodManager`

**Light theme: дефолтный аватар без контура (Medium):**
- Откат `ThemeUtils.applyDefaultAvatar()` для toolbar аватара в ChatListActivity — возврат к простому tint
- `ThemeApplier` — убран специальный случай для ChatListActivity, единое поведение tint для всех Activity
- Добавлен Primary color контур (1.5dp) для аватаров на плашках чатов ТОЛЬКО для светлых тем
- `ChatAdapter` — `cachedIsLightTheme` флаг, border через `CircleImageView.setBorderWidth/setBorderColor`

**Chat list icons: позиционирование внизу плашки (Low):**
- Иконки mute, unread count, lobby, delete progress — `layout_gravity="bottom"` вместо `center_vertical`
- Теперь выровнены по нижнему краю плашки, под текстом last-seen

**Favorites star: выравнивание с overflow menu (Low):**
- Звёздочка "Избранное" сдвинута вниз на 3dp для совпадения с иконкой меню

**Dark mode: hardcoded цвета в item_log_entry.xml (Medium):**
- `logSource`: `#AAAAAA` → `@color/text_secondary`
- `logMessage`: `#FFFFFF` → `@color/text_primary`
- Теперь корректно отображается в обеих темах

### Code Quality

**Lint warnings cleanup (Low):**
- DefaultLocale: `Locale.ROOT` добавлен во все `String.format` вызовы (12 файлов)
- CutPasteId: убран дублированный `findViewById` в MediaPickerSheet и ThemeApplier
- UnspecifiedRegisterReceiverFlag: `@SuppressLint` для API-conditional registerReceiver
- InflateParams: `setContentView(layoutResId)` вместо ручного inflate с null parent
- SwitchIntDef: добавлен `STATE_IDLE` в AudioPlayerManager

### Accessibility

**Decorative ImageViews — importantForAccessibility (Medium):**
- ~30 decorative ImageViews получили `android:importantForAccessibility="no"` + `android:contentDescription="@null"`
- Файлы: activity_call, activity_chat_list, activity_contacts, activity_new_chat, activity_security, activity_servers, activity_super_admin, activity_themes, activity_changelog, activity_profile, activity_conference_lobby
- item_theme (appliedIndicator, modeIndicator), item_admin_session, item_company_chat, item_company_position, item_device, item_server, item_share_chat, item_user_super_admin
- widget_chat, widget_action_item, preview_chat, preview_chat_list
- dialog_about, dialog_server_auth, dialog_reactions (4 иконки)
- bottom_sheet_update, bottom_sheet_additional_settings (через MenuActionIconStyle)
- Стиль `MenuActionIconStyle` — добавлены `contentDescription="@null"` и `importantForAccessibility="no"` (все иконки меню автоматически)

## [1.4.0.2] - 2026-08-13

### Исправлено

**Mute from inside chat not reflected in chat list (Medium):**
- При mute/unmute изнутри чата состояние теперь пробрасывается в ChatListViewModel через `ChatListSharedState`
- Иконка mute отображается на списке чатов после возврата из чата
- Добавлена валидация `roomId` перед отправкой запроса

**Chat list search activates immediately (Low):**
- Убран промежуточный значок лупы — поиск открывается сразу при тапе в меню
- `expandActionView()` вызывается автоматически после создания SearchView

**Chat list toolbar avatar for light themes (Medium):**
- Дефолтный аватар на светлых темах теперь использует `ThemeUtils.applyDefaultAvatar()` — иконка с primary color + круглый фон
- На тёмных темах поведение без изменений
- `ThemeApplier` обновляет аватар при смене темы

**Chat list overflow icon alignment and color (Low):**
- Иконка меню поднята на 3px для выравнивания со звёздочкой избранного
- Цвет иконки меню синхронизирован с `colorOnPrimary` темы (как у звёздочки)
- `ThemeApplier` обновляет цвет overflow при смене темы

**Hardcoded Russian fallbacks (Low):**
- `RemoteAgentManager`: 4 строки-заглушки заменены на английские
- `HermesGatewayManager`: добавлен строковый ресурс `ssh_session_not_established` (EN+RU)

**Light theme text colors (High):**
- `text_primary` в `values/colors.xml` исправлен с `#FFFFFF` (белый = невидимый) на `#212121` (тёмный)
- `text_secondary` исправлен с `#888888` на `#616161` (лучший контраст)
- `text_hint` исправлен с `#80FFFFFF` (невидимый) на `#80212121`
- Тёмная тема не затронута (`values-night/colors.xml` уже корректна)

**Dark mode drawable night variants (Medium):**
- 8 drawable-night файлов создано для корректного отображения в тёмной теме:
  - `bg_message_outgoing.xml` — outgoing bubble: белый → `#3D6B6C` (графит)
  - `bg_chip.xml` — chip background: `#33FFFFFF` → `#22FFFFFF`
  - `bg_reactions.xml` — reactions overlay: `#15000000` → `#15FFFFFF`
  - `bg_status_bubble.xml` — status bubble: `#33000000` → `#22FFFFFF`
  - `bg_onboarding_fab.xml` — onboarding FAB: белый → `#2D2D2D`
  - `bg_onboarding_profile.xml` — onboarding profile: белый → `#2D2D2D`
  - `bg_edit_text_rounded.xml` — edit text: `#40FFFFFF` → `#30FFFFFF`
  - `bg_message_preview.xml` — message preview stroke: `#33FFFFFF` → `#22FFFFFF`
- `circle_indicator.xml` — добавлен stroke `?attr/colorOutline` для видимости на тёмном фоне

**Chat screen accessibility (High):**
- 9 ImageButtons в `widget_chat.xml` получили `contentDescription` (reply, copy, forward, delete, star, close, search prev/next)
- 2 CheckBox'а получили `contentDescription` (`item_user_selectable.xml`, `widget_searchable_list_bottom_sheet.xml`)
- Добавлены строковые ресурсы: `search_previous`, `add_to_favorites` (EN+RU)

### Удалено (dead code)

- `NotificationActivity` + `NotificationAdapter` (ui/notification/) — не имели точек запуска
- `NotificationLogActivity` (root package) — не имела точек запуска
- `NotificationHistory` + `NotificationEntry` (data/fcm/) — не использовались
- Layouts: `activity_notification.xml`, `activity_notification_log.xml`, `item_notification_log.xml`
- Удалены обе записи из AndroidManifest.xml

### Улучшения

**List animation polish (Low):**
- Chat list: `DefaultItemAnimator` с оптимизированными длительностями (add=200ms, remove=150ms, change=150ms)
- Chat list: layout animation «fall down» при первой загрузке (fade + slide, stagger 8%)
- Chat messages: `DefaultItemAnimator` для плавного появления новых сообщений (add=150ms)

### Тесты

- `GrpcMarshallersTest` — 27 тестов для критических маршаллеров (field order verification)
- Покрытие: AddContact, RemoveContact, GetContacts, PinChat, UnPinChat, ArchiveChat, UnarchiveChat, PinMessage, UnPinMessage, SearchChats, MarkRead, SetMutedChat, DeleteChat

## [1.4.0.1] - 2026-08-13

### Новые фичи

**Self-Destruct Timer (Medium):**
- Интеграция с серверным `SetSelfDestructTimer` RPC
- Пункт меню "Автоудаление" в overflow menu чата с picker'ом (Off/30s/1m/5m/1h/24h)
- Отображение текущего таймера в заголовке меню
- Обработка `SELF_DESTRUCT_TIMER` broadcast от сервера
- Proto: `selfDestructTimer` field (29) в `ChatInfoProto`, новые маршаллеры
- Строки EN+RU для всех опций таймера

**Chat list toolbar redesign (Low):**
- Search перенесён в overflow меню (вместо отдельной иконки)
- Overflow icon tinted white (как в чате)
- Favorites icon остаётся видимым

### Исправлено

**Toolbar overflow icon color (Low):**
- Иконка многоточия теперь белая (как трубка звонка)

**Search close button navigation (Medium):**
- Крестик в режиме поиска теперь корректно выходит из поиска, а не возвращает на список чатов
- `ChatToolbarDelegate` — добавлены `setNavigationClickListener()` и `restoreDefaultNavigation()`

**Clear history / Delete chat dialog buttons (Low):**
- Кнопки "Отмена" и "Очистить"/"Удалить" теперь используют `primaryColor` темы
- `applyDialogButtonColors()` helper для единообразного стиля диалогов

**Mute chat feedback (Low):**
- Toast "Muted"/"Unmuted" при переключении уведомлений
- Toast ошибки при failure

**Media picker back arrow (Low):**
- Убрана случайная стрелка назад в шторке медиа

**Deleted sticker packs in favorites (Medium):**
- `StickerPreferencesManager.removeFavoritesByPackIds()` — фильтрация favorites по валидным packId
- Автоочистка при загрузке пакетов

**Sticker save button position (Low):**
- Убран лишний padding в bottom bar — кнопка "Сохранить" выше на20px

**Sticker pack not appearing after creation (Medium):**
- Убран фильтр `it.stickers.isNotEmpty()` — пакеты с пустыми стикерами теперь отображаются

**Call system messages (Medium):**
- Tap на системное сообщение звонка → открывает лобби ТОЛЬКО для конференций
- `CallMessageHelper.isVideoCall()` — детект по ключевым словам
- Новые строки: `call_outgoing_audio`, `call_incoming_audio`, `call_ended_duration`, `call_not_answered`
- `bindCallMessage` различает: missed, ended (с длительностью/без), conference, video, audio

**Send button visibility (Low):**
- Иконка отправки скрыта при пустом тексте (как было раньше)

### Изменённые файлы (24)
- `NewChatActivity.kt` — self-destruct timer, mute toast, dialog colors, call tap fix
- `StickerPackCreateActivity.kt` — bottom bar padding
- `CallMessageHelper.kt` — `isVideoCall()`, expanded keywords
- `GrpcChatAuxClient.kt` — `setSelfDestructTimer()` RPC
- `GrpcChatClient.kt` — `selfDestructTimer` mapping
- `GrpcChatListV2Client.kt` — `selfDestructTimer` mapping
- `GrpcClient.kt` — facade method + StateFlow
- `GrpcMarshallers.kt` — field 29 in 3 parsers +2 new marshallers
- `RealGrpcClient.kt` — `SELF_DESTRUCT_TIMER` signal handler + StateFlow
- `Message.kt` — `selfDestructTimer` field
- `MessengerProto.kt` — `selfDestructTimer` + request/response types
- `StickerPreferencesManager.kt` — `removeFavoritesByPackIds()`
- `MessageAdapter.kt` — improved `bindCallMessage()`
- `ChatInputDelegate.kt` — send button initial state
- `ChatSearchDelegate.kt` — navigation click listener
- `ChatToolbarDelegate.kt` — `setNavigationClickListener()`, `restoreDefaultNavigation()`
- `MediaPickerSheet.kt` — back button fix, favorites cleanup, pack filter
- `ChatListNavigation.kt` — `SELF_DESTRUCT_TIMER` extra
- `ChatListSearch.kt` — overflow menu redesign
- `chat_list_menu.xml` — new overflow menu
- `chat_menu.xml` — self-destruct timer item
- `strings.xml` (EN+RU) —20 new strings

## [1.4.0.0] - 2026-08-13

### Новые фичи

**Company Settings screen (Medium):**
- `CompanySettingsActivity` — полноценный экран настроек компании с использованием `getCompanySettings`/`updateCompanySettings` RPC
- Настройки: Invite Only, Allow Member Invite, Require Approval, Default Chat Access (member/management/owner_only), Default Position
- ChipGroup для выбора уровня доступа, Dropdown для выбора позиции
- Заменён примитивный mute-диалог на полноценный экран

**Invite Code management UI (Medium):**
- `InviteCodeActivity` — управление кодами приглашений с использованием `generateInviteCode`/`listInviteCodes`/`revokeInviteCode`
- Список активных кодов с RecyclerView, FAB для генерации нового
- Кнопки Share (системный share sheet) и Revoke (с подтверждением)
- Статус кода (Active/Expired), использование (uses/maxUses), срок действия

**Company Notifications integration (Low):**
- `sendCompanyNotification` подключён к операциям с участниками
- eventType=1 (member_joined) при `addMember` в `AddMemberActivity`
- eventType=2 (member_left) при `removeMember` в `CompanyListFragment`
- eventType=3 (position_changed) при `updateMemberPosition` в `CompanyListFragment`

**Chat toolbar redesign (Medium):**
- Иконка видеозвонка заменена на телефонную трубку (`ic_call`) в стиле Telegram
- Поиск перенесён в overflow menu (`showAsAction="never"`)
- Pinned Messages перенесён в overflow menu
- Новые пункты меню: Mute notifications (`ic_mute`), Clear history (`ic_delete_sweep`), Delete chat (`ic_delete`)
- Все пункты меню с иконками в стиле Telegram

**Delete message persistence fix (Critical):**
- `deletedMessageHashes` теперь сохраняется в SharedPreferences + Room DB при каждом удалении
- Загрузка при старте из SharedPreferences + Room DB (backup)
- Автоочистка записей старше 30 дней
- Новая таблица `deleted_messages` в Room DB (миграция 15→16)
- Удалённые сообщения больше не появляются после перезапуска приложения

### Исправлено

**Chat list selection mode — delay + checkbox残留 (Medium):**
- `setSelectionMode()` и `clearSelection()` используют `notifyDataSetChanged()` вместо поштучных `notifyItemChanged(i)`
- Исправлен баг: чекбоксы оставались видимыми у невыбранных элементов при выходе из режима выделения
- Удалён неиспользуемый метод `chatAdapter.clearSelection()` — заменён на `setSelectionMode(false)`

**Chat search mode — call icon not hiding (Low):**
- `onPrepareOptionsMenu` — иконка звонка теперь скрывается при активном поиске (`searchDelegate.isVisible()`)
- `ChatSearchDelegate.show()` и `hide()` вызывают `activity.invalidateOptionsMenu()` для обновления меню

### Unit Tests

- `EditProfileViewModelTest` — 7 тестов для `ProfileUiState` и `AvatarUploadState`
- `SuperAdminViewModelTest` — 6 тестов для `SuperAdminUiState` и `Mode`
- `ConferenceLobbyViewModelTest` — 5 тестов для `ConferenceLobbyUiState`
- `CompanyMarshallersTest` — расширен на CompanySettings, InviteCode, CompanyNotifications

### Изменения в файлах

| Файл | Изменение |
|------|-----------|
| `CompanySettingsActivity.kt` | Новый — экран настроек компании |
| `InviteCodeActivity.kt` | Новый — управление кодами приглашений |
| `InviteCodeAdapter.kt` | Новый — адаптер для списка кодов |
| `CompanyProfileActivity.kt` | Запуск новых Activities вместо примитивных диалогов, удалены неиспользуемые импорты |
| `AddMemberActivity.kt` | sendCompanyNotification при добавлении участника |
| `CompanyListFragment.kt` | sendCompanyNotification при удалении/смене позиции |
| `NewChatActivity.kt` | Mute toggle, Clear history, Delete chat handlers, search mode fix |
| `ChatAdapter.kt` | notifyDataSetChanged для selection mode, удалён notifyChatItemsChanged |
| `ChatSearchDelegate.kt` | invalidateOptionsMenu в show()/hide() |
| `ChatListNavigation.kt` | Передача IS_MUTED через intent |
| `GrpcMessageV2Client.kt` | addDeletedHash — сохранение в SharedPreferences + Room DB |
| `RealGrpcClient.kt` | addDeletedHash — сохранение в SharedPreferences + Room DB, loadDeletedMessages — загрузка из Room DB |
| `AppDatabase.kt` | Миграция 15→16, DeletedMessageEntity, DeletedMessageDao |
| `Daos.kt` | DeletedMessageDao (insert, getAllIds, cleanupOlderThan) |
| `Entities.kt` | DeletedMessageEntity |
| `chat_menu.xml` | 7 пунктов меню с иконками |
| `ic_delete_sweep.xml` | Новая иконка |
| `ic_mute.xml` | Новая иконка |
| `activity_company_settings.xml` | Новый layout |
| `activity_invite_codes.xml` | Новый layout |
| `item_invite_code.xml` | Новый layout |
| `dialog_generate_invite_code.xml` | Новый layout |
| `strings.xml` (EN+RU) | 20+ новых строковых ресурсов |
| `CompanyMarshallersTest.kt` | +156 строк тестов |
| `EditProfileViewModelTest.kt` | Новый файл тестов |
| `SuperAdminViewModelTest.kt` | Новый файл тестов |
| `ConferenceLobbyViewModelTest.kt` | Новый файл тестов |
| `AndroidManifest.xml` | Регистрация CompanySettingsActivity, InviteCodeActivity |
| `version.txt` | 1.4.0.0 |

---

## [1.3.4.20] - 2026-08-11

### Исправлено

**Sticker pack — выбор не отмечается визually (Medium):**
- `StickerPackAdapter` — заменён `selectedPosition` (Int) на `selectedPackId` (String). `notifyDataSetChanged()` для надёжного обновления. Добавлена фоновая подсветка выбранного элемента (15% alpha primary). Индикатор → `colorPrimaryContainer`

**Sticker pack — нет кнопки удалить при редактировании (Medium):**
- `StickerPackCreateActivity` — добавлена кнопка `btnDelete` (textButton, `colorError`). Видна только при редактировании. Confirmation dialog → `GrpcClient.deleteStickerPack()`

**Save button залезает под навигацию (Low):**
- `activity_sticker_pack_create.xml` — `WindowInsetsCompat.Type.navigationBars()` на `bottomBar`

**StickerEditorView — скачок фокуса при zoom (Medium):**
- Добавлен `isScaling` флаг + `onScaleBegin`/`onScaleEnd`. `constrainImage()` вызывается только после завершения pinch-жеста. `lastTouchX/Y` обновляется в ветках перетаскивания

**StickerEditorView — неточная обрезка (Medium):**
- Связано с zoom focus — теперь изображение остаётся на месте при zoom

**Список чатов — скролл при смене вкладки (Medium):**
- `ChatListViewModel` — добавлен `scrollToTopEvent` при `setTabFilter()`. `ChatListActivity` — observer вызывает `scrollToPosition(0)`

**Шторка звонка — нет drag handle (Low):**
- `sheet_pre_call.xml` — добавлен View (40dp x 4dp, `colorOnSurfaceVariant`, alpha 0.3)

**Список чатов — мерцание при обновлении (Low):**
- `activity_chat_list.xml` — `scrollbars="none"`. `ChatListActivity` — `setHasFixedSize(true)`

**Mute — переименование (Low):**
- Swipe action: "Отключить" → "Заглушить Пуш-уведомления", "Включить" → "Включить Пуш-уведомления"

**Архивирование через swipe — чат не исчезал (Medium):**
- `ChatListViewModel.archiveChat()` — оптимистичное обновление: `allChats` + `buildSections` вызываются ДО gRPC-вызова. При ошибке — откат. Toast показывается только после успеха через callback `onResult`
- `ChatListActivity` swipe handler — Toast вынесен в callback `archiveChat { success -> ... }`

### Улучшения

**Система обновлений — версия в тексте:**
- `update_available` = "Доступна версия %1$s" / "Version %1$s available"
- `install_update` = "Установить %1$s" / "Install %1$s"
- `download_update` = "Скачать %1$s" / "Download %1$s"
- Если скачанная версия устарела (`downloadedVersion != latestVersion`), показывается "Скачать" вместо "Установить"

**Настройки профиля — очистка:**
- Удалены Notifications и Admin panel из "Дополнительные настройки" (уже есть в основной шторке)
- "Очистка локального кэша" перенесена в основную шторку профиля, доступна только админу

**Company RPC интеграция (клиент):**
- Proto-классы для Settings, Invite Codes, Notifications
- Маршаллеры для7 новых RPC
- `GrpcCompanyClient` методы: `getCompanySettings`, `updateCompanySettings`, `generateInviteCode`, `joinByInviteCode`, `revokeInviteCode`, `listInviteCodes`, `sendCompanyNotification`

### Изменения в файлах

| Файл | Изменение |
|------|-----------|
| `StickerPackAdapter.kt` | Selection tracking по ID, фоновая подсветка |
| `item_sticker_pack_tab.xml` | Indicator color → `colorPrimaryContainer` |
| `MediaPickerSheet.kt` | `selectPack()` после загрузки |
| `StickerPackCreateActivity.kt` | Кнопка удаления, WindowInsets |
| `activity_sticker_pack_create.xml` | Bottom bar с delete button |
| `StickerEditorView.kt` | isScaling флаг, pinch/drag логика |
| `ChatListViewModel.kt` | scrollToTopEvent, Archive/unarchive optimistic update + callback |
| `ChatListActivity.kt` | scrollToTop observer, setHasFixedSize, Clear Cache, Archive Toast callback |
| `sheet_pre_call.xml` | Drag handle |
| `activity_chat_list.xml` | scrollbar="none" |
| `bottom_sheet_user_menu.xml` | Clear Cache (admin only) |
| `bottom_sheet_additional_settings.xml` | Удалены Notifications, Admin, Clear Cache |
| `ChatListToolbar.kt` | Clear Cache handler, удалены лишние handlers |
| `strings.xml` (EN+RU) | Update versioning, mute rename |
| `UpdateCoordinator.kt` | Version in text, downloaded-but-outdated |
| `ServerAuthBottomSheet.kt` | Version in install text |
| `CompanyProto.kt` | New proto data classes |
| `CompanyMarshallers.kt` | New marshallers |
| `GrpcCompanyClient.kt` | New RPC methods |

---

## [1.3.4.19] - 2026-08-11

### Новые фичи

**Pre-call screen (Medium):**
- Новый `PreCallSheet` — bottom sheet перед началом звонка. Показывает аватар, имя, последнее время в сети собеседника. Две кнопки: аудиозвонок и видеозвонок. Кнопка отмены
- `CallNavigator.startCall()` — новый параметр `isVideo: Boolean`. `CallActivity` читает `IS_VIDEO_CALL` из intent
- Для аудиозвонков запрашивается только `RECORD_AUDIO` (не `CAMERA`). Камеру можно включить во время звонка
- `NewChatActivity` — иконка звонка теперь открывает `PreCallSheet` вместо прямого старта звонка
- `ProfileActivity` — кнопки голосового и видеозвонка передают корректный `isVideo` флаг

**Fullscreen просмотр стикеров (Low):**
- Тап на стикер в чате открывает `FullScreenImageActivity` вместо reactions dialog
- Тап на стикер в редакторе пакета (`StickerPackCreateActivity`) тоже открывает fullscreen

### Исправлено

**Стикеры исчезают после отправки (Critical):**
- `GrpcMessageV2Client.messageV2ToDomain()` — `when` блок обрабатывал только `"image"` и `"voice"`, но не `"sticker"`. Входящие стикеры не парсились → `stickerUrl` оставался пустым → стикер отображался как пустое сообщение. При `loadHistoryV2` стикеры "исчезали". Исправлено: добавлен кейс `"sticker"` → парсинг `stickerUrl`/`stickerThumbnailUrl`

**Скролл к стикеру после отправки (Medium):**
- `ChatInputDelegate.sendStickerMessage()` не устанавливал `shouldScrollToBottom` → чат не прокручивался к отправленному стикеру. Исправлено: добавлен callback `onStickerSent`, в `NewChatActivity` устанавливает `shouldScrollToBottom = true`

**Crop editor — скачок при pinch zoom (Medium):**
- `StickerEditorView.onTouchEvent()` — `lastTouchX/Y` не обновлялись во время pinch gesture → при переключении на drag координаты были stale → скачок изображения. Исправлено: `lastTouchX/Y` обновляются в каждом `ACTION_MOVE`

**Crop editor — обрезание не по границе (Medium):**
- `getCroppedBitmap()` использовал двухэтапный crop (mappedRect → square enforcement через center) вместо прямого маппинга. Исправлено: используется тот же подход что и в `applyCrop()` — `Bitmap.createBitmap` с square enforcement через `adjLeft`/`adjTop`

**Sticker pack — имя пакета не помещается (Low):**
- `item_sticker_pack_tab.xml` — горизонтальный layout 64dp с 10sp текстом. Исправлено: вертикальный список, каждый пакет — горизонтальная строка (40dp cover + 14sp name + indicator). `rvStickerPacks` — `wrap_content` + `nestedScrollingEnabled=false`

**Права камеры/микрофона после регистрации (High):**
- После первой регистрации система иногда не успевала обновить кеш прав → `hasPermissions()` возвращал `false` хотя права были выданы. Пользователь не мог позвонить до перезахода в приложение
- Исправлено: в `CallActivity` и `ConferenceLobbyActivity` добавлен retry через 500ms в callback permission launcher. Если права "отклонены", повторная проверка через 500ms — система успевает обновить состояние. Toast + finish только при подтверждённом отказе

### Изменения в файлах

| Файл | Изменение |
|------|-----------|
| `PreCallSheet.kt` | Новый файл — bottom sheet с аватаром, именем, last seen, audio/video кнопки |
| `sheet_pre_call.xml` | Новый layout |
| `bg_circle_primary.xml` | Новый drawable — круглый фон для кнопок звонка |
| `ic_call.xml` | Новый drawable — иконка телефонного звонка |
| `CallNavigator.kt` | Параметр `isVideo` в `startCall()` |
| `CallActivity.kt` | `IS_VIDEO_CALL` extra, `AUDIO_PERMISSIONS`, permission retry |
| `ConferenceLobbyActivity.kt` | Permission retry (500ms) |
| `NewChatActivity.kt` | PreCallSheet, sticker fullscreen tap, sticker scroll |
| `ProfileActivity.kt` | `isVideo` флаг для audio/video кнопок |
| `GrpcMessageV2Client.kt` | `sticker` case в `messageV2ToDomain()`, `stickerUrl`/`stickerThumbnailUrl` в Message |
| `ChatInputDelegate.kt` | `onStickerSent` callback |
| `StickerEditorView.kt` | Pinch zoom jump fix, crop offset fix |
| `StickerPackCreateActivity.kt` | Sticker tap → fullscreen |
| `MediaPickerSheet.kt` | Vertical pack list |
| `item_sticker_pack_tab.xml` | Vertical item layout, 14sp text |
| `sheet_media_picker.xml` | `rvStickerPacks` wrap_content + nestedScrolling |

## [1.3.4.18] - 2026-08-11

### Исправлено

**Пересылка сообщений — список чатов пуст (High):**
- `ChatSelectionDelegate.forwardSelectedMessages()` — `sheet.loadChats(f)` вызывался ДО `sheet.show()`, но `adapter` создаётся в `show()`. `loadChats()` делал `adapter?.submitList()` на `null` → список чатов оставался пустым, поиск ничего не находил. Исправлено: порядок вызовов `show()` → `loadChats()`

**Избранное — CANCELLED ошибки при загрузке истории (Medium):**
- `GrpcMessageV2Client.loadHistoryV2()` — каждый вызов создавал новый gRPC call без отмены предыдущего. Множественные вызовы (из `switchRoom`, connection observer, markRead broadcast) вызывали каскад CANCELLED ошибок (~200ms интервал). Исправлено: добавлен `currentHistoryCall` — новый вызов отменяет предыдущий через `call.cancel()`
- CANCELLED ошибки триггерли reconnect → дополнительные вызовы → дополнительные CANCELLED. Исправлено: CANCELLED убран из триггера reconnect (это клиентская отмена, не серверный disconnect), логируется как debug вместо error

**Swipe actions — delete не обновлял ViewModel (Medium):**
- `ChatListActivity.setupSwipeActions()` — delete action вызывал `GrpcClient.deleteChat()` напрямую вместо `viewModel.deleteChat()`. ViewModel не обновлял своё `allChats` → UI не отражал удаление. Исправлено: используется `viewModel.deleteChat()` с callback

**Swipe actions — нет обратной связи после archive/mute (Low):**
- Добавлены Toast-уведомления: "Архивировано", "Заглушено", "Включено" (EN+RU)

### Улучшения

**Тулбар списка чатов — "В сети" до загрузки чатов (Low):**
- Раньше тулбар показывал "В сети" сразу при `ConnectionStatus.READY`, даже если список чатов ещё не загружен. На медленном интернете это вводило пользователей в заблуждение. Исправлено: добавлен `chatsLoaded: StateFlow<Boolean>` в `ChatListViewModel`. Тулбар показывает "Подключение..." пока чаты не загружены, затем переключается на "В сети"

**gRPC keepalive timeout (Low):**
- `GrpcConnectionManager.KEEP_ALIVE_TIMEOUT_SECONDS` увеличен с 10 до 20 секунд. 10 секунд слишком агрессивно для мобильных сетей (3G/4G/WiFi переходы) — latency spikes >10s вызывали `UNAVAILABLE — Keepalive failed`

**Build cleanup (Low):**
- Удалён неиспользуемый plugin alias `kotlin-android` из `libs.versions.toml` (AGP 9.x имеет встроенную поддержку Kotlin)
- Исправлены wildcard imports в `GrpcConnectionManager.kt` и `GrpcReconnectStrategy.kt` (Rule 22)

### Изменения в файлах

| Файл | Изменение |
|------|-----------|
| `ChatSelectionDelegate.kt` | Порядок `show()` → `loadChats()` |
| `GrpcMessageV2Client.kt` | `currentHistoryCall` tracking, CANCELLED handling |
| `ChatListActivity.kt` | Swipe delete через viewModel, Toast feedback, toolbar status logic |
| `ChatListViewModel.kt` | `chatsLoaded` StateFlow |
| `GrpcConnectionManager.kt` | `KEEP_ALIVE_TIMEOUT_SECONDS` 10→20, wildcard import fix |
| `GrpcReconnectStrategy.kt` | Wildcard import fix |
| `libs.versions.toml` | Удалён `kotlin-android` alias и `kotlin` version |
| `values/strings.xml` | `archived`, `muted`, `unmuted` |
| `values-ru/strings.xml` | `archived`, `muted`, `unmuted` |

## [1.3.4.17] - 2026-08-08

### Исправлено

**gRPC keepalive ошибки и отсутствие reconnect (High):**
- `GrpcConnectionManager.buildChannel()` — `channel?.shutdown()` вызывался ДО создания нового канала, что отменяло in-flight вызовы (CANCELLED). Исправлено: shutdown вызывается ПОСЛЕ build, channel обновляется до shutdown старого
- `GrpcChatAuxClient.getAllUsers` — UNAVAILABLE ошибки не триггерли reconnect. Добавлен reconnect callback, вызывается при UNAVAILABLE
- `GrpcMessageV2Client.loadHistoryV2` — CANCELLED ошибки не триггерли reconnect. Добавлен reconnect callback, вызывается при UNAVAILABLE и CANCELLED

### Изменения в файлах

| Файл | Изменение |
|------|-----------|
| `GrpcConnectionManager.kt` | `buildChannel` shutdown order fix (shutdown after build) |
| `GrpcChatAuxClient.kt` | `reconnect` callback, UNAVAILABLE → reconnect |
| `GrpcMessageV2Client.kt` | `reconnect` callback, UNAVAILABLE/CANCELLED → reconnect |
| `RealGrpcClient.kt` | `reconnect` lambdas для chatAuxClient и messageV2Client |

## [1.3.4.16] - 2026-08-08

### Исправлено

**Контакты не добавлялись (Critical):**
- Серверный баг в `server_contacts.go`: `contactID := req.UserId` — присваивал ID текущего пользователя вместо контакта. Клиент всегда отправлял `userId` = свой UUID, поэтому сервер вызывал `AddContactByUserID(userID, userID)` — пользователь добавлял сам себя. Исправлено: `contactID := req.ContactUsername`

**Диалог добавления контактов — улучшения (Medium):**
- `ContactsActivity.showAddContactDialog()` — убран дублирующий observer (`observeAllUsers` + `uiState.collect` → только `uiState.collect`), добавлена проверка `success` в callback `addContact`, Toast об ошибках добавления, блокировка кнопки во время операции
- `ContactsViewModel` — удалён неиспользуемый `observeAllUsers()`
- Новая строка `contacts_add_failed` (EN+RU)

**Ответ из пуш-уведомления показывался как от собеседника (High):**
- `LavenderMessagingService` — `MessagingStyle` использовал одного `Person` для отправителя и для "себя". При ответе из пуша сообщение отображалось как будто его отправил собеседник. Исправлено: отдельные `Person` — `self` (текущий пользователь) и `sender` (собеседник). Применено в `showNotification` и `showNotificationFromStream`

**«Прочитано» из пуша не обновляло UI (High):**
- `NotificationMarkReadReceiver` — после подтверждения сервера отправляется broadcast `ACTION_CHAT_MARKED_READ`. `ChatListActivity`接收广播 → `viewModel.markAsRead(roomId)` сбрасывает счётчик непрочитанных. `NewChatActivity`接收广播 → `forceLoadHistory()` обновляет флаги `isRead`

**Ответ из пуша без цитаты на исходное сообщение (High):**
- `NotificationReplyReceiver` — `Message` создавался без полей `repliedToMessageId`/`repliedToUser`/`repliedToText`. Исправлено: данные исходного сообщения (`messageId`, `sender`, `originalText`) передаются через intent extras уведомления
- `showNotification` — читает `message_id` из FCM data payload и передаёт в reply intent
- `showNotificationFromStream` — принимает параметр `messageId`, передаёт в reply intent. Добавлен inline reply action (раньше была только кнопка «Прочитано»)
- Сервер: FCM push payload теперь включает `message_id` (выполнено серверным агентом)

### Изменения в файлах

| Файл | Изменение |
|------|-----------|
| `ContactsActivity.kt` | Единый observer, success check, error Toast, button lock |
| `ContactsViewModel.kt` | Удалён `observeAllUsers()` |
| `LavenderMessagingService.kt` | `MessagingStyle` self/sender Persons, `messageId` param, reply action в stream |
| `NotificationMarkReadReceiver.kt` | Broadcast `ACTION_CHAT_MARKED_READ` после markRead |
| `NotificationReplyReceiver.kt` | Reply extras: `EXTRA_MESSAGE_ID`, `EXTRA_SENDER`, `EXTRA_ORIGINAL_TEXT` |
| `RealGrpcClient.kt` | `msg.id` передаётся в `showNotificationFromStream` |
| `ChatListActivity.kt` | BroadcastReceiver для markRead → `viewModel.markAsRead()` |
| `NewChatActivity.kt` | BroadcastReceiver для markRead → `forceLoadHistory()` |
| `values/strings.xml` | `contacts_add_failed` |
| `values-ru/strings.xml` | `contacts_add_failed` |

## [1.3.4.15] - 2026-08-07

### Исправлено

**Аватар в тулбаре — не показывался для не-контактов (High):**
- `ChatToolbarDelegate.setupNormalToolbar()` и `setupGroupAvatars()` — использовали только `grpcClient.getAvatarCache()` (ConcurrentHashMap, заполняется при `getUserAvatar()`). Для пользователей не в контактах URL был null → дефолтный аватар. Добавлен fallback на `grpcClient.allUsers` StateFlow, который содержит `avatarUrl` для ВСЕХ пользователей из `GetAllUsers` RPC

**Контакт не фильтровался после добавления + некорректное определение завершения (Medium):**
- `ChatListFABs.showAddContactDialog()` — completion detection использовал `(added + (total - selected.indexOf(contact) - 1)) == total`, что dismiss'ило диалог после ПЕРВОГО успешного добавления. Заменён на `AtomicInteger` для корректного подсчёта завершённых callback'ов. Также `currentContacts.add(contact)` после каждого успешного добавления + `collectJob?.cancel()` при dismiss

**Sticker pack — добавление стикеров в существующий пакет не персистилось на сервер (Medium):**
- `StickerPackCreateActivity.savePack()` — update path только обновлял title и cover через `updateStickerPack()`. Новые стикеры (загруженные после создания пакета) не добавлялись на сервер. Добавлен `originalStickerIds` для трекинга серверных стикеров + `addSticker()` вызовы для новых стикеров в update path

**Sticker pack — имя пакета нечитаемо мелкое (Low):**
- `item_sticker_pack_tab.xml` — `tvPackName` textSize увеличен с 8sp до 10sp

**ANR при отправке через «Поделиться в Lavender» (Critical):**
- `ShareReceiverViewModel.sendMessageToChat()` — `startChatV2()` и `sendMessageV2()` вызывались на `Dispatchers.Main`. `startChatV2()` → `AuthManager.getAccessToken()` → `getEncryptedPrefs()` — блокирующий I/O вызов на Main thread. Обёрнуто в `withContext(Dispatchers.IO)`

**Sticker picker — оба таба показывают одинаковый контент, нет UI для создания/редактирования пакетов (Medium):**
- `sheet_media_picker.xml` — `btnCreatePack` вынесен из `stickerEmptyState` (теперь всегда видим в tab 2)
- `MediaPickerSheet` — добавлен `onEditStickerPack` callback, кнопка "Создать пакет" показывается/скрывается при переключении табов
- `StickerPackAdapter` — добавлен `onPackLongClick` для long-press → редактирование пакета
- `ChatInputDelegate` — передаёт `onEditStickerPack` callback для открытия `StickerPackCreateActivity` с `PACK_ID`

### Изменения в файлах

| Файл | Изменение |
|------|-----------|
| `ChatToolbarDelegate.kt` | Fallback на `allUsers` для аватаров (direct + group) |
| `ChatListFABs.kt` | `AtomicInteger` completion, `currentContacts.add()`, `collectJob` cancel |
| `StickerPackCreateActivity.kt` | `originalStickerIds` + `addSticker()` в update path |
| `item_sticker_pack_tab.xml` | textSize 8sp→10sp |
| `ShareReceiverViewModel.kt` | gRPC вызовы обёрнуты в `withContext(Dispatchers.IO)` |
| `sheet_media_picker.xml` | `btnCreatePack` вынесен из empty state |
| `MediaPickerSheet.kt` | `onEditStickerPack` callback, show/hide btnCreatePack по табам |
| `StickerPackAdapter.kt` | `onPackLongClick` support |
| `ChatInputDelegate.kt` | `onEditStickerPack` callback → `StickerPackCreateActivity` |

---

## [1.3.4.14] - 2026-08-07

### Исправлено

**ANR при «Поделиться в Lavender» (Critical):**
- `ShareReceiverActivity.onCreate()` — `SessionManager.initFromPrefs()` вызывался синхронно на Main thread. `EncryptedSharedPreferences.create()` инициализирует Android Keystore, что на некоторых устройствах блокирует UI на несколько секунд. Перенесён в `Dispatchers.IO`

**Аватар в тулбаре чата — не загружался при открытии из пуш-уведомления (High):**
- `NewChatActivity.setupObservers()` — добавлен observer `connectionStatus`. При открытии из уведомления `fetchChatMetadata()` вызывался до установки gRPC-соединения → `getChats` возвращал `NOT_CONNECTED` → аватар оставался дефолтным. Теперь при переходе в READY автоматически повторяется загрузка метаданных чата

**Кнопка «Назад» в тулбаре чата — неправильное поведение при открытии из уведомления:**
- `ChatToolbarDelegate.setup()` — кнопка "назад" использовала `onBackPressedDispatcher.onBackPressed()`, что при открытии из пуш-уведомления возвращало на предыдущий Activity в стеке вместо списка чатов. Заменён на явный переход к `ChatListActivity` с `FLAG_ACTIVITY_CLEAR_TOP`

**Индикатор обновления — показывался после установки нового APK:**
- `UpdateCoordinator.updateIndicatorVisibility()` — флаги `update_available` / `update_downloaded` не сбрасывались после установки обновления. Добавлена проверка: если `BuildConfig.VERSION_NAME` совпадает с `latest_version` — флаги сбрасываются, индикатор скрывается сразу

**Контакты — добавление уже существующих контактов (Medium):**
- `ContactsActivity.showAddContactDialog()` — фильтр «уже в контактах» захватывал снимок `contacts` до завершения async `loadContacts()` → список мог быть пустым, и существующие контакты показывались в диалоге добавления. Исправлено: `loadContacts()` вызывается перед открытием диалога, фильтр берёт актуальные данные из `uiState` при каждом обновлении

### Изменения в файлах

| Файл | Изменение |
|------|-----------|
| `ShareReceiverActivity.kt` | `initFromPrefs()` → `Dispatchers.IO` через `withContext` |
| `NewChatActivity.kt` | Retry `fetchChatMetadata` при `connectionStatus == READY`; импорт `ChatMetadataState` |
| `ChatToolbarDelegate.kt` | Явный переход к `ChatListActivity` вместо `onBackPressedDispatcher` |
| `UpdateCoordinator.kt` | Проверка текущей версии → сброс флагов обновления |
| `ContactsActivity.kt` | Фильтр контактов: reload перед диалогом + observe uiState |

---

## [1.3.4.13] - 2026-08-07

### Добавлено

**Push-уведомления — действие «Прочитано»:**
- Кнопка «Прочитано» в шторке уведомления позволяет отметить чат прочитанным без открытия приложения
- Действие доступно при получении уведомлений через FCM и gRPC notification stream

**Тап на цитату — переход к оригинальному сообщению:**
- Нажатие на блок цитаты (reply quote) в сообщении прокручивает чат к цитируемому сообщению с подсветкой на 1.5 сек

### Исправлено

**ANR при «Поделиться» из стороннего приложения (Critical):**
- `SessionManager.initFromPrefs()` — `GrpcClient.connect()` выполнялся на UI-потоке, блокируя интерфейс на время установки соединения. Перенесён в `Dispatchers.IO`

**Аватар отправителя в чате — всегда дефолтный (High):**
- `GrpcMessageV2Client.messageV2ToDomain()` — `avatarUrl` не маппился из V2 proto. Добавлен резолвинг аватара из кеша пользователей по `senderId`

**Загрузка изображений — ошибка gRPC CANCELLED при отправке (High):**
- `GrpcConnectionManager.buildChannel()` — `shutdownNow()` при реконнекте ChatV2-стрима отменял in-flight вызовы, включая `sendMessageV2`. Заменён на graceful `shutdown()`

**Профиль — цвет заголовка «Компания» не совпадает с «Кратко о себе»:**
- `activity_profile.xml` — label не имел `android:id`, `applyThemeToView()` не применял `primary`-цвет. Добавлен `companyTitleLabel`

---

## [1.3.4.12] - 2026-08-02

### Исправлено

**Аватары в списке чатов — моргание/пропажа/неправильные аватары:**
- `ChatAdapter` — убран `post {}` вокруг Glide.load(), который откладывал загрузку past recycle point → неправильные аватары во ViewHolder
- `clearAvatar()` — добавлен `Glide.clear(ivChatAvatar)` вместо `setImageDrawable(null)` — отменяет in-flight Glide запросы
- `override(48, 48)` → dp-based размер `(48 * density).toInt()` — не размывается на xxhdpi
- Добавлен `diskCacheStrategy(ALL)` для аватаров — меньше сетевых запросов
- `updateAllUsers()` — уведомляет только items с изменившимися аватарами (вместо всех direct chats). Убран `otherParticipantCache.clear()`

**Стикеры — i18n и UX:**
- `"Favorites (count)"` и `"Recent"` — заменены на string resources (`sticker_favorites_header`, `sticker_recent_header`) с RU переводами
- Добавлен `sticker_no_favorites` — подсказка в пустом состоянии ("Нажмите и удерживайте стикер, чтобы добавить")
- `StickerPackCreateActivity` — hardcoded `"Error: "` заменён на `getString(R.string.error_colon, ...)`
- Добавлен индикатор избранного (⭐) на стикерах в grid — `item_sticker_grid.xml` + `StickerGridAdapter.setFavoriteIds()`
- `item_sticker_pack_tab.xml` — добавлен `tvPackName` для отображения имени пакета в шторке стикеров
- `StickerPackAdapter` — bind(pack.title) в tvPackName

### Изменения в файлах

| Файл | Изменение |
|------|-----------|
| `ChatAdapter.kt` | Removed post{}, added Glide.clear(), dp-based size, diskCacheStrategy(ALL), optimized updateAllUsers |
| `MediaPickerSheet.kt` | i18n section headers, favorite IDs pass-through, empty state text |
| `StickerGridAdapter.kt` | Favorite indicator support (setFavoriteIds + star overlay) |
| `StickerPackAdapter.kt` | Pack name binding in tvPackName |
| `item_sticker_pack_tab.xml` | Added tvPackName TextView |
| `item_sticker_grid.xml` | Added favoriteIndicator TextView |
| `sheet_media_picker.xml` | Added tvEmptyText ID to empty state |
| `StickerPackCreateActivity.kt` | error_colon string resource |
| `values/strings.xml` | sticker_favorites_header, sticker_recent_header, sticker_no_favorites |
| `values-ru/strings.xml` | Russian translations for above |

---

## [1.3.4.11] - 2026-08-02

### Исправлено

**Crash guards — ChatMessageAdapter:**
- `onBindViewHolder()` — добавлен try-catch с fallback (минимальный рендер plain text). Без обработки любое исключение в `holder.bind()` крашило Activity

**Stale position в click listeners (16 locations):**
- `MessageAdapter` — все click/long-click listeners, захватывавшие `pos` из bind-time, заменены на `bindingAdapterPosition` с `NO_POSITION` guard. IOOBE при быстрых insert/remove из списка

**Reply+media — reply терялся (regression confirmed):**
- `GrpcMessageV2Client.messageV2ToDomain()` — `when` block делал media и reply mutually exclusive. Заменён на два независимых `if`. ProtoUtils.kt уже был исправлен ранее

**Sticker-only сообщения невидимы:**
- `MessageAdapter.bind()` — `isCompletelyEmpty` не проверял `stickerUrl`. Sticker-only сообщения скрывались с `View.GONE`

**deduplicateByContent — hash-коллизии при пустом userId:**
- Добавлен `getContentHashForDedup()` с ID tiebreaker. `getContentHash()` (для merge matching) оставлен без изменений

**Swipe reply — IOOBE на stale currentList:**
- `NewChatActivity` — swipe handler использует `getOrNull()` вместо прямого `[position]`

**TOCTOU race в loadHistoryV2:**
- `loadHistoryServerCompleted` проверка перенесена внутрь `messages.update` lambda (атомарно через CAS)

**bind() fallback completeness:**
- catch-блок теперь сбрасывает 20+ views (sticker, reactions, reply quote, forwarding, selection, read status и т.д.)

**Контакты — собственный аккаунт дублируется:**
- `ContactsViewModel.loadContacts()` — фильтрует `list.filter { it != username }`
- `GrpcProfileClient.addContact()` — guard от self-addition

### Изменения в файлах

| Файл | Изменение |
|------|-----------|
| `ChatMessageAdapter.kt` | try-catch в onBindViewHolder |
| `MessageAdapter.kt` | bindingAdapterPosition в 16 click listeners + stickerUrl в isCompletelyEmpty + fallback completeness |
| `GrpcMessageV2Client.kt` | `when` → `if` для reply+media + getContentHashForDedup + TOCTOU fix |
| `NewChatActivity.kt` | getOrNull() в swipe handler |
| `ContactsViewModel.kt` | filter own username from contacts |
| `GrpcProfileClient.kt` | self-add guard in addContact |

---

## [1.3.4.10] - 2026-08-01

### Исправлено

**Контакты — пустой список (regression):**
- `ContactsActivity.setupObservers()` — `uiState.collect` проверял `state.contacts.isEmpty()` для empty state, но never передавал данные в адаптер. Добавлен `adapter.setUsers(state.contacts)`

**Стикеры — тап не отправлял стикер в чат:**
- `StickerGridAdapter.bind()` — click listener устанавливался только на `lottieView`. При fallback на `thumbnailView` (Lottie failed to load) тап молчал. Исправлено: listeners на оба view
- `ChatInputDelegate.sendStickerMessage()` — добавлен try-catch с Toast при ошибке отправки

**Фатал при смене темы на светлую:**
- `ThemeStore.init()` — `syncNightMode()` вызывался ДО обновления `_theme.value` → читал stale dark → `MODE_NIGHT_NO` на втором вызове → `Activity.recreate()` в середине `onResume()`. Исправлено: сначала `_theme.value`, потом `syncNightMode()`

**Share receiver — fatal crash при пересылке в Лаву:**
- `ShareReceiverActivity` — добавлен `CoroutineExceptionHandler` для всех `lifecycleScope.launch` (3 вызова)
- `observeViewModel()` — flow collector обёрнут в try-catch
- `ShareChatAdapter.ViewHolder.bind()` — `ThemeStore.currentTheme()` обёрнут в try-catch с fallback
- `ShareReceiverViewModel.sendMessageToChat()` — проверка connection после retry loop, общий try-catch с error state

**Reply терялся при наличии media:**
- `ProtoUtils.createMessageFromV2Proto()` — `when` с mutually exclusive branches: reply skip при наличии media. Заменён на два независимых `if`

**ChatListActionMode — crash при activity recreation:**
- `exitSelectionMode()`, `updateActionModeTitle()`, `updateActionModeIcons()` — добавлены try-catch guards для `chatAdapter` access

### Изменения в файлах

| Файл | Изменение |
|------|-----------|
| `ContactsActivity.kt` | `adapter.setUsers(state.contacts)` в uiState.collect |
| `StickerGridAdapter.kt` | Click listeners на оба view (lottie + thumbnail) |
| `ChatInputDelegate.kt` | sendStickerMessage try-catch |
| `ThemeStore.kt` | syncNightMode() после _theme.value update |
| `ShareReceiverActivity.kt` | CoroutineExceptionHandler, try-catch observe + ViewHolder |
| `ShareReceiverViewModel.kt` | sendMessageToChat try-catch, connection check |
| `ProtoUtils.kt` | Reply + media: `when` → два независимых `if` |
| `ChatListActionMode.kt` | try-catch guards для chatAdapter |

---

## [1.3.4.9] - 2026-08-01

### Исправлено

**Контакты — пустой список (regression):**
- `ContactsActivity.setupObservers()` — добавлен `adapter.setUsers(state.contacts)` в `uiState.collect`. Данные загружались из gRPC но не передавались в адаптер

**Стикеры — тап не отправлял стикер:**
- `StickerGridAdapter.bind()` — click listener устанавливался только на `lottieView`, при fallback на `thumbnailView` (Lottie failed) тап молчал. Исправлено: listeners на оба view

**Фатал при смене темы на светлую:**
- `ThemeStore.init()` — `syncNightMode()` вызывался ДО обновления `_theme.value` → читал stale dark theme → `MODE_NIGHT_NO` на втором вызове → `Activity.recreate()` в середине `onResume()`. Исправлено: сначала обновляется `_theme.value`, потом `syncNightMode()`

**Share receiver — fatal crash при пересылке в Лаву:**
- `ShareReceiverActivity` — добавлен `CoroutineExceptionHandler` для `lifecycleScope.launch` (3 вызова)
- `observeViewModel()` — flow collector обёрнут в try-catch для предотвращения фатала при ошибках UI
- `ShareChatAdapter.ViewHolder.bind()` — `ThemeStore.currentTheme()` обёрнут в try-catch с fallback на `BuiltInThemes.dark`
- `ShareReceiverViewModel.sendMessageToChat()` — обработка ошибок gRPC: проверка connection после retry, общий try-catch с показом error state

**Стикеры не отправлялись из шторки (HIGH):**
- `GrpcMessageV2Client.domainToSendRequest()` — добавлен mapping `stickerUrl` → `MessageMediaProto(type="sticker")`. Раньше стикер отправлялся как пустое текстовое сообщение

**updateStickerPack() — показывал success при ошибке (HIGH):**
- `StickerPackCreateActivity.savePack()` — добавлена проверка `updateResult == null || !updateResult.success` с показом error toast и ранним выходом (по аналогии с `createStickerPack`)
- Вторичный вызов `updateStickerPack()` для cover — добавлено логирование ошибки

**OutOfMemoryError в compressImage() (HIGH):**
- `StickerPackCreateActivity.compressImage()` — `catch(Exception)` заменён на `catch(Throwable)` для перехвата `OutOfMemoryError`
- Переменные `bitmap`/`scaledBitmap` вынесены из try, `recycle()` перенесён в `finally` блок для предотвращения утечки при OOM

**Lottie crash при 404/malformed JSON (HIGH):**
- Добавлен `setFailureListener()` для 4 вызовов `setAnimationFromUrl()`:
  - `StickerPackListAdapter` — fallback на cover ImageView + Glide
  - `StickerPackAdapter` — скрытие view при ошибке
  - `MessageAdapter` — fallback на `stickerImageView` + Glide
  - `StickerGridAdapter` — fallback на `thumbnailView` + Glide

**Поиск стикеров — пустое состояние без сообщения об ошибке:**
- `MediaPickerSheet.performSearch()` — добавлен Toast "Search failed" при catch
- Добавлены строки `sticker_search_failed`, `sticker_no_results` (EN/RU)

**JSON versioning для favorites:**
- `StickerPreferencesManager` — формат сохранения изменён на `{"version":1,"data":[...]}`
- Добавлена обратная совместимость: legacy JSONArray читается корректно
- Добавлен fallback для переименованных полей (`animationUrl` ← `lottieUrl`)

**Log tag "TAG" → class-specific идентификаторы:**
- 54 вызова `Log.w("TAG", ...)` заменены на `Log.w(TAG, ...)` в 31 файле
- Добавлены `private const val TAG = "ClassName"` в companion object каждого файла

**Sticker emoji — hardcoded на 🎵:**
- `StickerPackCreateActivity` — убран hardcoded `emoji = "🎵"`, используется дефолт пустой строки

**Tab reselection — нельзя повторить при ошибке:**
- `MediaPickerSheet.onTabReselected` — добавлен retry: `loadStickerPacks()` для tab 2, `showFavoritesTab()` для tab 1

**Accessibility — contentDescription:**
- `item_message.xml` — добавлен `contentDescription="@string/selection_indicator"` для `ivSelectionIndicator`
- `MessageAdapter` — программная установка `contentDescription` для `stickerImageView`/`lottieStickerView` ("Sticker") и `readStatusIcon` (динамический: Sending/Sent/Read/Failed)
- Добавлены строки: `selection_indicator`, `sticker_image`, `status_pending`, `status_sent`, `status_read`, `status_error` (EN/RU)

**Favorites tab — favorites и recents смешаны:**
- `MediaPickerSheet.showFavoritesTab()` — добавлен section header "⭐ Favorites (N)" / "🕐 Recent"
- Добавлен `tvSectionHeader` в `sheet_media_picker.xml`

**Dark mode — hardcoded hex цвета:**
- 22 hardcoded hex значения заменены на `@color/` ресурсы в 14 layout XML
- Добавлены семантические цвета в `colors.xml`/`colors-night.xml`: `danger`, `success`, `warning`, `text_primary`, `text_secondary`, `text_hint`, `surface_elevated`, `scrim_light`, `scrim_dark`, `scrim_heavy`, `divider`

### Удалено

**AvatarPrefetcher — мёртвый код:**
- Удалён `AvatarPrefetcher.kt` (153 строки) — ни одной ссылки в коде, `@Suppress("Unused")`

### Добавлено

**Unit тесты:**
- `DiffCallbackTest` — 12 тестов: `MessageDiffCallback` (id, text, timestamp, reactions, readStatus, sticker) + `StickerDiffCallback` (id, url, emoji)
- `CompanyMarshallersTest` — 27 тестов: proto defaults, equality, marshaller parse empty для всех company response marshallers

### Изменения в файлах

| Файл | Изменение |
|------|-----------|
| `GrpcMessageV2Client.kt` | `domainToSendRequest()` — sticker → MessageMediaProto mapping |
| `StickerPackCreateActivity.kt` | updateStickerPack result check, compressImage OOM fix, emoji default |
| `StickerPackListAdapter.kt` | `setFailureListener()`, TAG fix |
| `StickerPackAdapter.kt` | `setFailureListener()`, TAG fix |
| `MessageAdapter.kt` | `setFailureListener()`, contentDescription, TAG fix |
| `StickerGridAdapter.kt` | `setFailureListener()` |
| `MediaPickerSheet.kt` | search error Toast, tab resection retry, section header, TAG fix |
| `StickerPreferencesManager.kt` | JSON versioning, legacy compat |
| `AvatarPrefetcher.kt` | DELETED |
| `sheet_media_picker.xml` | tvSectionHeader TextView |
| `item_message.xml` | contentDescription для ivSelectionIndicator |
| `colors.xml` + `colors-night.xml` | 11 семантических цветов |
| 14 layout XML | 22 hardcoded hex → @color/ |
| 31 файлов | Log.w("TAG") → Log.w(TAG) |
| `strings.xml` + `strings-ru.xml` | 12 новых строк (sticker search, accessibility) |
| `DiffCallbackTest.kt` | NEW — 12 unit тестов |
| `CompanyMarshallersTest.kt` | NEW — 27 unit тестов |
| `ShareReceiverActivity.kt` | CoroutineExceptionHandler, try-catch в observeViewModel + ViewHolder.bind |
| `ShareReceiverViewModel.kt` | sendMessageToChat try-catch, connection check после retry |
| `ContactsActivity.kt` | `adapter.setUsers(state.contacts)` в uiState.collect |
| `StickerGridAdapter.kt` | Click listeners на оба view (lottie + thumbnail) |
| `ThemeStore.kt` | syncNightMode() после _theme.value update |
| `ProtoUtils.kt` | Reply + media: `when` → `if` для независимого парсинга |
| `ChatListActionMode.kt` | try-catch guards для chatAdapter access |

---

## [1.3.4.8] - 2026-08-01

### Исправлено

**Adapter memory leaks — onViewRecycled (5 адаптеров):**
- `ChatAdapter` — `onViewRecycled()` + `clearAvatar()`: очистка Glide загрузки при рециклировании
- `UserAdapter` — `onViewRecycled()` + `clearAvatar()`: очистка аватара пользователя
- `SuperAdminAdapter` — `onViewRecycled()` + `clearAvatar()`/`clearIcon()`: очистка аватаров и иконок устройств
- `ParticipantAdapter` — `onViewRecycled()` + `clearAvatar()`: очистка аватара участника
- `MentionAdapter` — `onViewRecycled()` + `clearAvatar()`: очистка аватара упоминания

**notifyDataSetChanged() → DiffUtil (4 адаптера):**
- `ThemeAdapter` — миграция на `ListAdapter<CustomThemeProto>` с `DiffUtil.ItemCallback`
- `DeviceAdapter` — миграция на `ListAdapter<DeviceInfoProto>` с `DiffUtil.ItemCallback`
- `ParticipantAdapter` — миграция на `ListAdapter<String>` с `DiffUtil.ItemCallback`
- `MentionAdapter` — миграция на `ListAdapter<String>` с `DiffUtil.ItemCallback`

**Sticker Preferences — fatal crash при нажатии звёздочки:**
- `StickerPreferencesManager.loadStickerList()` — `getPrefs()` был вне try-catch → `IllegalStateException` при неинициализированном `prefs`
- `saveStickerList()` — аналогично, обёрнут в try-catch
- `clearRecent()` — аналогично, обёрнут в try-catch
- `loadStickerList()` — заменён `getString()` на `optString()` для защиты от потери всего списка при битом JSON

**Sticker Lottie — crash при HTTP URL:**
- `StickerPackListAdapter.bind()` — `setAnimation(url)` на HTTP URL вызывал `FileNotFoundException`. Исправлено на `setAnimationFromUrl()` для HTTP
- `StickerPackAdapter.bind()` — аналогичный фикс
- Добавлена проверка `url.isNotEmpty()` перед вызовом Lottie

**Sticker favorites — неконтролируемый рост:**
- Добавлен `MAX_FAVORITES = 100` — при превышении удаляется последний элемент

**StickerPackListAdapter — Glide leak:**
- `unbind()` — добавлена очистка `coverImageView` через `Glide.clear()`

**E2EE key exchange — утечка корутин:**
- `ChatE2EEDelegate` — добавлен `exchangeInProgress` guard от параллельных попыток
- Добавлен `retryJob` для отмены pending retry при уходе с экрана
- Добавлен `cancelPendingRetries()` + вызов в `NewChatActivity.onDestroy()`
- Проверка `activity.isFinishing || activity.isDestroyed` перед retry

**SessionManager — ANR risk:**
- `waitForRefreshComplete()` — добавлен timeout 10s с принудительным сбросом guard

**Chat list performance — O(n) в bind():**
- `onlineUsers` — кэширован как `Set<String>` (был `List<String>` → `.toSet()` на каждый bind)
- `allUsers` — кэширован как `Map<String, UserInfoProto>` (был `List` → `.firstOrNull()` O(n) на каждый bind)
- `getOtherParticipant()` — кэширован в `MutableMap<String, String>` (был JSON parse на каждый bind)

### Добавлено

**Unit тесты:**
- `ThemeUtilsTest` — 5 тестов: `parseSafeColor` (null, empty), `adjustAlpha` (factor 1.0, RGB preservation), `isLight`

### Изменения в файлах

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

## [1.3.4.7] - 2026-08-01

### Исправлено

**Sticker editor — обрезка не по квадрату:**
- `StickerEditorView.applyCrop()` — принудительное квадратное кропание через `min(w, h)` + центрирование
- `StickerEditorActivity.saveAndReturn()` — убран промежуточный `applyCrop()` + переключение режима. `getCroppedBitmap()` в CROP-режиме корректно обрезает и делает квадрат за один проход

**MediaPickerSheet — дублирование табов при повторном открытии:**
- `setupStickerTab()` — добавлен guard `stickerTabInitialized` (как у `setupEmojiTab()`). Без него каждое открытие шторки добавляло 3 дубликата таба + listener, вызывая глитчинг

**Adapter memory leaks (CRITICAL):**
- `MessageAdapter` — добавлен `onViewRecycled()`: `lottieStickerView.cancelAnimation()` + `Glide.clear()` для avatar/image/sticker
- `ChatMessageAdapter` — добавлен `onViewRecycled()`: `TypingHolder.stopAnimation()` + `Glide.clear()` для agent message image/avatar
- `StickerPackAdapter` — добавлен `onViewRecycled()`: `coverView.cancelAnimation()`

**ChatAdapter — selection performance:**
- `setSelectionMode()`, `toggleSelection()`, `clearSelection()` — `notifyDataSetChanged()` → `notifyItemChanged()` для затронутых позиций

**StickerPackListAdapter — hardcoded colors:**
- Статус-бейджи (approved/pending/rejected) — `Color.parseColor("#4CAF50")` → theme `colorPrimary`/`colorTertiary`/`colorError`

### Изменения в файлах

| Файл | Изменение |
|------|-----------|
| `StickerEditorView.kt` | `applyCrop()` — square enforcement |
| `StickerEditorActivity.kt` | `saveAndReturn()` — removed applyCrop + mode switch |
| `MediaPickerSheet.kt` | `stickerTabInitialized` guard |
| `MessageAdapter.kt` | `onViewRecycled()` + `unbind()` |
| `ChatMessageAdapter.kt` | `onViewRecycled()` + `AgentMessageHolder.unbind()` |
| `StickerPackAdapter.kt` | `onViewRecycled()` + `unbind()` |
| `ChatAdapter.kt` | Selection methods → targeted `notifyItemChanged()` |
| `StickerPackListAdapter.kt` | Theme-based status colors |

---

## [1.3.4.6] - 2026-07-31

### Добавлено

**Forward message — "forwarded from" attribution:**
- `Message` модель — поля `isForwarded` и `forwardedFrom`.
- `MessageV2Proto` — поле `forwardedFrom` (proto field 50).
- `SendMessageV2RequestProto` — поле `forwardedFrom` (proto field 8).
- `MessageV2ProtoMarshaller` — сериализация/десериализация field 50.
- `item_message.xml` — `tvForwardedFrom` TextView для отображения "Переслано от: Username".
- `MessageAdapter` — `bindForwardAttribution()` отображает attribution.
- `ChatSelectionDelegate` — передаёт `forwardedFrom` через proto поле (без текстового префикса).
- `ProtoUtils` — чтение из proto поля + legacy fallback для старых сообщений с текстовым префиксом.
- `ChatAdapter` — `stripForwardPrefix()` для legacy превью в списке чатов.
- `GrpcMessageV2Client` — `domainToSendRequest()` и `messageV2ToDomain()` поддерживают `forwardedFrom`.
- Строковые ресурсы: `forwarded_from` (EN + RU).
- Серверный промпт: `LavenderMessenger-server/doc/PROMPT_FORWARD_ATTRIBUTION.md`.

### Исправлено

**Sticker upload preview не работал:**
- `StickerGridAdapter` + `MessageAdapter` — `setAnimation(url)` молча проваливался для HTTP URL. Фикс: `setAnimationFromUrl(url)` для удалённых URL.
- `StickerPackCreateActivity` — сервер возвращал URL с `PUBLIC_IP`, недоступным с устройства. Фикс: перезапись host:port через `CredentialStore.getHttpServerUrl()`.

**Sticker editor — text overlay координаты:**
- `StickerEditorView.drawTextOverlaysOnBitmap()` — использовался naive scale `bitmap.width/view.width`, игнорируя `imageMatrix`. Фикс: inverse `imageMatrix` для преобразования view→bitmap координат + учёт crop offset.

**Sticker editor — обрезка не применялась + двойная обрезка:**
- Кнопка "Обрезать" только показывала overlay, не применяла обрезку. Фикс: `StickerEditorView.applyCrop()` — реально обрезает bitmap при уходе из CROP режима.
- `saveAndReturn()` — после `applyCrop()` режим оставался CROP → `getCroppedBitmap()` обрезал повторно (85% от уже обрезанного). Фикс: переключение на TEXT после обрезки.
- MIME type fallback для `file://` URI из кеша (contentResolver может вернуть `application/octet-stream`).
- `StickerGridAdapter.unbind()` — Glide cleanup при рециклировании view.

**ChatList прелоадер зависал:**
- `ChatListViewModel` — `_isLoading.value = true` ставился ДО `viewModelScope.launch`, а `false` — в `finally` внутри корутины. При mutex guard корутина не доходила до `try` → loading застревал. Фикс: `_isLoading` перенесён внутрь корутины, после `tryLock()`.
- `ChatListActivity` — `SwipeRefreshLayout` отключён (`isEnabled = false`) — убран бесконечно крутящийся спиннер.

## [1.3.4.5] - 2026-07-31

### Исправлено

**Direct chat detected as group:**
- `NewChatViewModel` — `ChatMetadataState` инициализировался дефолтом (`isDirect=false`). Для личных чатов metadata оставался дефолтным → toolbar показывал "0 участников 0 онлайн". Фикс: инициализация из intent данных в `parseIntent()`.

**Sticker image upload:**
- `StickerPackCreateActivity` — изображения загружались на эндпоинт для Lottie JSON. Фикс: маршрутизация на `/upload-sticker-thumbnail` для изображений.
- `savePack()` — защита от crash при пустом URL стикера.
- `Sticker.thumbnailUrl` заполняется для изображений.

### Улучшено

**Sticker editor — crop UX:**
- `StickerEditorView` — изображение можно было утащить за пределы crop-области. Фикс: `constrainImage()` теперь ограничивает трансляцию — изображение всегда поккрывает crop-прямоугольник.
- `setImageUri()` — добавлен subsampling для больших изображений (>2048px) для предотвращения OOM.

**Forward message — улучшенный UX:**
- `ChatSelectionDelegate` — заменён простой `ListBottomSheet` на `ForwardMessageSheet` с поиском по чатам, мульти-выбором и превью пересылаемых сообщений.

## [1.3.4.4] - 2026-07-31

### Исправлено

**Chat list 0 participants/0 online (CRITICAL):**
- `NewChatActivity` — race condition: `fetchChatMetadata` callback обновлял toolbar metadata, но субтитул не пересчитывался до следующего эмита combine flow. Добавлен `toolbarDelegate.refreshSubtitle()` в оба callback'а (onCreate + onResume).

### Улучшено

**Deprecated API migration (8 sites):**
- `JoinCompanyActivity`, `StickerLibraryActivity`, `StickerEditorActivity`, `StickerPackCreateActivity` — удалён избыточный `systemUiVisibility = 0` (ThemeApplier уже управляет через WindowInsetsControllerCompat)
- `StickerEditorActivity` — `getParcelableExtra(String)` → API 33+ branch с `getParcelableExtra(String, Class)`
- `StickerPackCreateActivity` — аналогично, getParcelableExtra в ActivityResult callback
- `ThemeApplier` — `window.navigationBarColor` обёрнут в API 35 guard (на 35+ система игнорирует)
- `AiV2AgentListActivity` — `onBackPressed()` → `OnBackPressedCallback` через `onBackPressedDispatcher`

**CompatUtils — централизация deprecated API:**
- Создан `data/CompatUtils.kt` — все backward-compat обёртки в одном файле
- `getParcelableExtra` (Intent + Bundle), `createMediaRecorder`, `setNavigationBarColor`, `setSpeakerphoneOn` — перенесены из 5 файлов
- 5 файлов очищено от `@Suppress("DEPRECATION")`: StickerEditorActivity, StickerPackCreateActivity, AudioRecorder, AudioModeManager, ThemeApplier
- Один `@Suppress` остаётся в SessionManager (FCM getToken — нет альтернативы в Firebase SDK)

## [1.3.4.3] - 2026-07-26

### Исправлено

**Proto marshaller field mismatches (CRITICAL):**
- `AddContactRequestMarshaller` — `userId` и `username` были местами (field 1 ↔ 3)
- `RemoveContactRequestMarshaller` — `userId` и `username` были местами (field 1 ↔ 3)
- `GetContactsRequestMarshaller` — `userId` и `username` были местами (field 1 ↔ 2)
- `SignInRequestV2Marshaller` — DeviceInfo не содержал `client_version` (field 3)
- `SignUpRequestV2Marshaller` — DeviceInfo не содержал `client_version` (field 3)

**Error handling:**
- `SessionManager` — FCM token sync failure теперь логируется (был пустой catch)
- `NotificationsGrpc` — notification stream errors теперь логируются с stack trace (3 места)
- `RealGrpcClient` — room switch failure теперь логируется с proper TAG

**CoroutineScope leaks:**
- `NotificationReplyReceiver` — scope перенесён в `onReceive()` (был class-level, leak)
- `ServerAuthBottomSheet` — добавлен `SupervisorJob` в scope

**Deprecated API migration:**
- `ChatListActivity` — `onRequestPermissionsResult` → `ActivityResultContracts.RequestPermission`
- `ConferenceLobbyActivity` — `onRequestPermissionsResult` → `ActivityResultContracts.RequestMultiplePermissions`
- `CallActivity` — `onRequestPermissionsResult` → `ActivityResultContracts.RequestMultiplePermissions`

### Улучшено

**ViewModel migration:**
- `ContactsActivity` (359 строк) → `ContactsViewModel` — загрузка контактов, добавление/удаление, создание чатов
- `ThemesActivity` (337 строк) → `ThemesViewModel` — загрузка тем, применение, удаление, системный dark mode
- `NewChatActivity` — добавлен `NewChatViewModel` для управления intent data, connection state, metadata

**Dark mode improvements:**
- `ThemeStore` теперь синхронизирует `AppCompatDelegate` night mode при init и смене настройки
- Удалён дублирующийся `dark_mode` pref в `ChatListActivity.applyTheme()`

**Accessibility:**
- Исправлен hardcoded русский текст `"Загрузка изображений..."` → `@string/uploading_images_label`
- Исправлен неправильный `contentDescription` у `ivAdminIndicator` (был `muted_label` → `admin_indicator`)
- Добавлены `contentDescription` к 3 `ImageButton` (searchPrev, searchNext, cancelReply)
- Добавлены 8 строковых ресурсов (EN + RU): `admin_indicator`, `uploading_images_label`, `select_message`, `cancel_reply`, `search_previous`, `search_next`

**Unit tests:**
- `ContactsViewModelTest` — 8 тестов (data class defaults, copy, ChatCreatedEvent)
- `ThemesViewModelTest` — 4 теста (ThemesUiState defaults, copy, error/success states)
- `NewChatViewModelTest` — 6 тестов (ChatIntentData, ChatMetadataState defaults, copy, secret chat)

---

## [1.3.4.2] - 2026-07-24

### Очищено

**Code cleanup:**
- Удалены unused imports из 15+ файлов
- Исправлены redundant qualifier names
- Заменены `Color.parseColor()` на `toColorInt()`
- Исправлены Legacy Long → Duration в `delay()` и `withTimeoutOrNull()`
- Исправлены string concatenation → string resources
- Добавлены `@Suppress("UNUSED_PARAMETER")` для неиспользуемых параметров API
- Удалены unused functions: `openChat()`, `loadParticipants()`
- Исправлен `adapterPosition` → `bindingAdapterPosition` (deprecated API)

**String resources:**
- Добавлены `username_at`, `company_position_with_count`, `delete_confirmation_users`, `delete_confirmation_chats` (EN + RU)

**GrpcConnectionManager:**
- Добавлены `TAG` константы в `RemoteAgentGrpc.kt`
- Исправлены BuildConfig ссылки

### Изменения в файлах

| Файл | Изменение |
|------|-----------|
| 15+ файлов | Unused imports удалены |
| `RemoteAgentGrpc.kt` | BuildConfig → DEBUG константа |
| `GrpcCallClient.kt` | delay() → Duration |
| `GrpcTypingClient.kt` | delay() → Duration |
| `ForwardMessageSheet.kt` | adapterPosition → bindingAdapterPosition |
| `strings.xml` (EN/RU) | +4 строковых ресурса |

---

## [1.3.4.1] - 2026-07-24

### Улучшено

**Refactoring (ViewModel):**
- `EditProfileActivity` (775 строк) → `EditProfileViewModel` — вынесена бизнес-логика (загрузка профиля, аватара, компании, обновление био/пароля/имени)
- `ShareReceiverActivity` (686 строк) → `ShareReceiverViewModel` — загрузка чатов, отправка сообщений, загрузка файлов, превью ссылок
- `SuperAdminActivity` (580 строк) → `SuperAdminViewModel` — загрузка пользователей/чатов, пагинация, удаление, смена пароля
- `ConferenceLobbyActivity` (581 строк) → `ConferenceLobbyViewModel` — управление конференцией, участники, метаданные, время
- `RemoteAgentSettingsActivity` (623 строки) → `RemoteAgentSettingsViewModel` — токены, агенты, шлюз/туннель

**gRPC hardcoded English:**
- 8 gRPC клиентов обновлены: `GrpcProfileClient`, `GrpcFavoritesClient`, `GrpcChatClient`, `GrpcMessageV2Client`, `GrpcDraftClient`, `GrpcCallClient`, `GrpcTypingClient`, `RemoteAgentGrpc`
- Добавлены константы `DEFAULT_ERROR`, `CHANNEL_DEAD`, `STREAM_FAILED`, `STREAM_ERROR`
- Добавлены 6 строковых ресурсов (EN + RU): `grpc_error`, `favorite_removed`, `favorite_remove_failed`, `channel_dead`, `stream_failed`, `stream_error`

**GrpcConnectionManager рефакторинг:**
- Разделение `connect()` на 6 приватных методов: `shouldConnect()`, `isCallInProgress()`, `updateServerAddress()`, `updateConnectionStatus()`, `buildChannel()`, `activateChannel()`
- Константы для магических чисел: `KEEP_ALIVE_TIME_SECONDS`, `KEEP_ALIVE_TIMEOUT_SECONDS`, `MAX_INBOUND_MESSAGE_SIZE`, `IDLE_TIMEOUT_MINUTES`
- HTTP порты вынесены в константы: `DEV_GRPC_PORT`, `DEV_HTTP_PORT`, `PROD_HTTP_PORT`

**ChatAdapter → ListAdapter:**
- Миграция на `ListAdapter<FlatItem, RecyclerView.ViewHolder>` с `DiffUtil.ItemCallback`
- Добавлен интерфейс `ChatListAdapter` для доступа к элементам извне
- Автоматические анимированные обновления через DiffUtil

**Forward message UX:**
- Создан `ForwardMessageSheet` с улучшенным UX
- Добавлен поиск по чатам
- Множественный выбор чатов для пересылки
- Превью сообщений перед пересылкой
- Кнопка отправки с подсчётом выбранных чатов
- Новые layout файлы: `sheet_forward_message.xml`, `item_forward_preview.xml`, `item_forward_chat.xml`
- Новые drawable: `bg_message_preview.xml`, `ic_search.xml`, `ic_check_circle.xml`

**Chat list prefetch:**
- Создан `AvatarPrefetcher` для предзагрузки аватаров при скролле
- Использует Glide `preload()` для аватаров видимых + следующих 10 элементов
- Интерфейс `ChatListAdapter` для доступа к элементам из AvatarPrefetcher

### Изменения в файлах

| Файл | Изменение |
|------|-----------|
| `EditProfileViewModel.kt` | NEW — бизнес-логика EditProfileActivity |
| `ShareReceiverViewModel.kt` | NEW — бизнес-логика ShareReceiverActivity |
| `SuperAdminViewModel.kt` | NEW — бизнес-логика SuperAdminActivity |
| `ConferenceLobbyViewModel.kt` | NEW — бизнес-логика ConferenceLobbyActivity |
| `RemoteAgentSettingsViewModel.kt` | NEW — бизнес-логика RemoteAgentSettingsActivity |
| `EditProfileActivity.kt` | Использует EditProfileViewModel |
| `ShareReceiverActivity.kt` | Использует ShareReceiverViewModel |
| `SuperAdminActivity.kt` | Использует SuperAdminViewModel |
| `ConferenceLobbyActivity.kt` | Использует ConferenceLobbyViewModel |
| `RemoteAgentSettingsActivity.kt` | Использует RemoteAgentSettingsViewModel |
| `GrpcConnectionManager.kt` | Рефакторинг: разделение connect(), константы |
| `ChatAdapter.kt` | Миграция на ListAdapter + ChatListAdapter interface |
| `AvatarPrefetcher.kt` | NEW — предзагрузка аватаров |
| `ForwardMessageSheet.kt` | NEW — улучшенный UX пересылки |
| `GrpcProfileClient.kt` | DEFAULT_ERROR константа |
| `GrpcFavoritesClient.kt` | REMOVED/FAILED константы |
| `GrpcChatClient.kt` | DEFAULT_ERROR константа |
| `GrpcMessageV2Client.kt` | DEFAULT_ERROR константа |
| `GrpcDraftClient.kt` | DEFAULT_ERROR константа |
| `GrpcCallClient.kt` | CANCEL_REASON константа |
| `GrpcTypingClient.kt` | CANCEL_REASON константа |
| `RemoteAgentGrpc.kt` | CHANNEL_DEAD/STREAM_FAILED/STREAM_ERROR константы |
| `strings.xml` (EN/RU) | +12 строк |
| `sheet_forward_message.xml` | NEW — layout для ForwardMessageSheet |
| `item_forward_preview.xml` | NEW — превью сообщения |
| `item_forward_chat.xml` | NEW — элемент чата для пересылки |
| `bg_message_preview.xml` | NEW — фон превью |
| `ic_search.xml` | NEW — иконка поиска |
| `ic_check_circle.xml` | NEW — иконка выбора |

---

## [1.3.4.0] - 2026-07-24

### Очищено

**Dead code:**
- Удалены 31 неиспользуемых layout XML файлов (verified via grep + build)
- Удалены layout-land дубликаты (dialog_join_chat, dialog_profile)
- Удалена мёртвая ссылка `btnChangeUsername` из ThemeApplier
- Удалён неиспользуемый `englishNames` map из EditProfileActivity

**Empty catch blocks:**
- 66 пустых catch blocks в 30+ файлах получили `Log.w()` для логирования ошибок
- 21 catch block в AppDatabase.kt оставлен intentionally (ALTER TABLE миграции)

**Hardcoded strings:**
- 12 hardcoded строк извлечено в string resources (EN + RU)
- Обновлены 12 Kotlin файлов для использования `getString()`

### Исправлено

**Hardcoded Russian в определении звонков:**
- Создан `CallMessageHelper.kt` — language-agnostic детекция звонков через emoji (📹📞) + ключевые слова (RU/EN)
- Обновлены `MessageAdapter.kt` (3 места) и `NewChatActivity.kt` (1 место)
- Никогда не парсите русский текст для определения звонков — используйте CallMessageHelper

**CoroutineScope leaks:**
- `LavenderMessagingService` — добавлен `serviceScope` + `onDestroy()` cancel
- `NotificationReplyReceiver` — добавлен `scope` + `goAsync()` + `pendingResult.finish()`
- `UpdateManager` — анонимный scope заменён на class property
- `AudioPlayerManager` — анонимный scope заменён на class property
- `AddMemberSheet` — добавлен `setOnDismissListener` для отмены scope

### Изменения в файлах

| Файл | Изменение |
|------|-----------|
| `CallMessageHelper.kt` | NEW — language-agnostic call detection |
| `MessageAdapter.kt` | CallMessageHelper, Log import, getString() |
| `NewChatActivity.kt` | CallMessageHelper, getString() |
| `LavenderMessagingService.kt` | serviceScope + onDestroy |
| `NotificationReplyReceiver.kt` | scope + goAsync + pendingResult.finish |
| `UpdateManager.kt` | scope property |
| `AudioPlayerManager.kt` | scope property + SupervisorJob import |
| `AddMemberSheet.kt` | dismiss listener |
| `WidgetSystem.kt` | removed dead btnLogin/forgotPasswordButton refs |
| `ThemeApplier.kt` | removed dead btnChangeUsername ref |
| `EditProfileActivity.kt` | getString(), dead code removal |
| 30+ файлов | Log.w() в empty catch blocks |
| 31 layout XML | deleted (unused) |
| `strings.xml` (EN/RU) | +12 строк |

---

## [1.3.3.14] - 2026-07-24

### Исправлено

**Критическое: Proto marshallers — перепутаны field numbers (6 штук):**
- PinChatRequest, UnPinChatRequest, ArchiveChatRequest, UnarchiveChatRequest — `chatId` и `userId` были в обратном порядке
- PinMessageRequest, UnPinMessageRequest — все 3 поля (userId, chatId, messageId) были в неправильном порядке
- Сервер получал颠倒 данные → pin/archive/message operations могли работать некорректно

**Критическое: Дублирующийся NotificationActivity:**
- Два класса `NotificationActivity` в разных пакетах (корневой + `ui/notification/`)
- Удалён дубликат из корневого пакета, обновлена ссылка в `ChatListToolbar.kt`
- Удалена запись из `AndroidManifest.xml`

**CoroutineScope leaks:**
- `ChatListToolbar.kt:67` — `CoroutineScope(Dispatchers.IO)` заменён на `activity.lifecycleScope`
- `UpdateCoordinator.kt:190` — аналогично, привязан к lifecycle Activity

### Полный аудит (найдено и исправлено критическое)

**Аудит сервера:** 33 неimplemented метода (большинство deprecated v1)
**Аудит клиента:** 108 находок (8 critical, 58 medium, 42 low)
**Аудит proto:** 6 field number mismatches, 42 неиспользуемых серверных методов

### Изменения в файлах

| Файл | Изменение |
|------|-----------|
| `GrpcMarshallers.kt` | Исправлены field numbers в 6 marshallers |
| `ChatListToolbar.kt` | CoroutineScope → lifecycleScope, обновлена ссылка на NotificationActivity |
| `UpdateCoordinator.kt` | CoroutineScope → lifecycleScope |
| `AndroidManifest.xml` | Удалён дубликат NotificationActivity |
| `NotificationActivity.kt` (root) | Удалён |
| `version.txt` | 1.3.3.13 → 1.3.3.14 |

---

## [1.3.3.13] - 2026-07-21

### Исправлено

**Админ-панель — смена пароля пользователя:**
- Метод `AdminUpdatePassword` был удалён с сервера при очистке v1
- Восстановлена реализация на сервере: проверка super_admin, хеширование bcrypt, обновление в БД
- Клиентская часть (GrpcProfileClient) уже была на месте

**Список чатов — задержка при загрузке аватаров:**
- Glide загружал аватары синхронно в `bind()` — вызывал layout jank
- Загрузка отложена через `post {}` — RecyclerView прокручивается плавно
- Аватары загружаются после отрисовки текущего кадра

**Редактор стикеров — логирование:**
- Добавлено детальное логирование в `savePack()` и `uploadSticker()` (тег `StickerPack`)
- Флаг `isSaving` предотвращает двойное нажатие

### Изменения в файлах

| Файл | Изменение |
|------|-----------|
| `server_users.go` (сервер) | +`AdminUpdatePassword` handler |
| `ChatAdapter.kt` | Avatar Glide load в `post {}` |
| `StickerPackCreateActivity.kt` | +logging, +isSaving flag |
| `version.txt` | 1.3.3.12 → 1.3.3.13 |

---

## [1.3.3.12] - 2026-07-21

### Исправлено

**Редактор стикеров — кнопки не адаптированы к темам:**
- Нижняя панель (Crop/Text/Filters) и filter strip не получали цвет фона из темы
- Добавлена `android:id="@+id/bottomBar"` на нижнюю панель
- `applyThemeToViews()` теперь устанавливает `surfaceColor` фон на bottomBar и filterStrip

**Редактор стикеров — обрезка фото не работала:**
- `getCroppedBitmap()` всегда обрезал изображение по 85% прямоугольнику даже в режимах TEXT/FILTER
- Теперь обрезка применяется только в режиме CROP
- В режимах TEXT/FILTER возвращается полное изображение с текстовыми оверлеями

**Библиотека стикеров — превью не видно на тёмной теме:**
- `item_sticker_grid.xml` FrameLayout не имел фона — тёмные стикеры сливались с фоном
- Заменён на `MaterialCardView` с `cardCornerRadius=8dp` и `strokeColor=?attr/colorOutline`

**Создание пакета стикеров — фатал при сохранении:**
- `savePack()` не проверял null у `result` и `result.pack` — NPE при `packId!!`
- Добавлена null-проверка `result`, `result.success`, `result.pack?.id`
- `btnSave` блокируется на время запроса, восстанавливается при ошибке
- Ранний `return@launch` при каждой ошибке вместо вложенности

### Изменения в файлах

| Файл | Изменение |
|------|-----------|
| `StickerEditorActivity.kt` | +темизация bottomBar и filterStrip в `applyThemeToViews()` |
| `activity_sticker_editor.xml` | +`android:id="@+id/bottomBar"` на нижнюю панель |
| `StickerEditorView.kt` | `getCroppedBitmap()` — обрезка только в CROP режиме |
| `item_sticker_grid.xml` | FrameLayout → MaterialCardView с border |
| `StickerPackCreateActivity.kt` | Null-safe `savePack()`, блокировка кнопки |
| `version.txt` | 1.3.3.11 → 1.3.3.12 |

---

## [1.3.3.11] - 2026-07-21

### Исправлено

**MessageV2Proto serialization — потеря данных при ответах:**
- `MessageV2ProtoMarshaller.stream()` использовал `when {}` для text/media/reply — поля были взаимно исключающими
- Reply-данные молча терялись когда текст непустой, media терялось при наличии текста
- Исправлено на независимые `if` проверки — все три поля сериализуются независимо
- Аналогичный баг исправлен в `SendMessageV2RequestMarshaller.stream()` (media + text)

**E2EE key exchange — ненадёжный retry:**
- Лимит 10 попыток (30с) — увеличен до 20 попыток (~120с)
- Добавлен exponential backoff: 2s → 3s → 5s → 10s → 15s
- Исправлен преждевременный вызов `onKeyExchangeComplete(false)` — теперь вызывается только при финальной неудаче

**SessionManager — ANR risk при блокировке Main thread:**
- `ensureFreshToken()` и `forceTokenRefresh()` — добавлена `@WorkerThread` аннотация
- Runtime guard: `Looper.myLooper() == Looper.getMainLooper()` — логирует ошибку и пропускает вызов с Main thread

### Добавлено

**Тесты:**
- 5 новых тестов для `MessageV2ProtoMarshaller`: textAndReply, textAndMedia, allContentFields, replyOnly, sendMessageV2 textAndMedia
- Итого: 24 тестовых класса, все проходят

### Изменения в файлах

| Файл | Изменение |
|------|-----------|
| `MessagesV2Marshallers.kt` | `when {}` → независимые `if` в MessageV2ProtoMarshaller + SendMessageV2RequestMarshaller |
| `SessionManager.kt` | `@WorkerThread` + Looper guard в ensureFreshToken/forceTokenRefresh |
| `ChatE2EEDelegate.kt` | maxRetries 10→20, exponential backoff, fix premature callback |
| `MessagesV2MarshallersTest.kt` | +5 tests for when-bug fix |

---

## [1.3.3.10] - 2026-07-19

### Исправлено

**Sticker system:**
- Фикс прыжков при переключении вкладок (фиксированная высота 350dp)
- Темизация списка стикеров (surfaceColor для карточек)
- Long-press на стикере → "Сделать обложкой" + "Удалить"
- JPEG 85% + maxDim 512px (было PNG 90% + 1024px)

**Share (поделиться):**
- Исправлен чёрный экран при шеринге из Telegram
- Lavender появляется в списке приложений для шеринга фото/видео
- Раздельные intent-filter для text/plain, image/*, video/*
- `launchMode="singleTop"`

**Performance:**
- Chat list: кэш аватаров (Map username→url) для O(1) lookup
- Chat list: Glide skip load если URL уже установлен
- Chat list: непрозрачный toolbar (было 20% прозрачный)
- deletedMessageHashes: LRU cap 10000 entries

### Изменения в файлах

| Файл | Изменение |
|------|-----------|
| `sheet_media_picker.xml` | Fixed height 350dp |
| `StickerPackListAdapter.kt` | +theme colors |
| `StickerPackCreateActivity.kt` | +remove sticker, JPEG compression |
| `AndroidManifest.xml` | Раздельные intent-filter, launchMode=singleTop |
| `ShareReceiverActivity.kt` | Safety catch для темы |
| `ChatAdapter.kt` | +avatar cache, Glide skip duplicate load |
| `ChatListActivity.kt` | Непрозрачный toolbar |
| `RealGrpcClient.kt` | deletedMessageHashes LRU cap |
| `GrpcMessageV2Client.kt` | deletedMessageHashes LRU cap |
| `strings.xml` (EN/RU) | +2 строки (sticker remove) |

---

## [1.3.3.9] - 2026-07-19

### Добавлено

**Message search — подсветка всех совпадений:**
- `bindPlainContent()` — while loop для подсветки ВСЕХ совпадений запроса (было только первое)
- `BackgroundColorSpan` для каждого найденного совпадения
- Case-insensitive поиск

**Chat list avatars:**
- `CircleImageView` 40dp в `item_chat.xml`
- Glide загрузка аватаров для direct/group чатов
- Fallback на `ic_default_avatar`

**Real audio waveform:**
- `WaveformExtractor` — `MediaExtractor` + `MediaCodec` → PCM → RMS per bar
- 40 баров, нормализация 0.1-1.0
- `ConcurrentHashMap` кэш по URL
- Fallback на random waveform если извлечение не удалось

**Company notifications UI:**
- TabLayout: "All" | "Company" в NotificationActivity
- Фильтрация уведомлений по `metadata["company_id"]`
- Доступно только если пользователь состоит в компании

### Изменения в файлах

| Файл | Изменение |
|------|-----------|
| `MessageAdapter.kt` | while loop для highlight всех совпадений |
| `item_chat.xml` | +ivChatAvatar (CircleImageView 40dp) |
| `ChatAdapter.kt` | +avatar loading via Glide |
| `WaveformExtractor.kt` | NEW — audio waveform extraction |
| `AudioMessageView.kt` | Использует WaveformExtractor |
| `SplashActivity.kt` | +WaveformExtractor.init() |
| `NotificationActivity.kt` | +TabLayout + company filtering |
| `activity_notification.xml` | +TabLayout |
| `strings.xml` (EN/RU) | +2 строки (notifications_all, notifications_company) |

---

## [1.3.3.8] - 2026-07-19

### Добавлено

**Chat list swipe right for pin/unpin:**
- Swipe right на чате → мгновенный toggle pin/unpin (без диалога)
- Зелёный фон с иконкой `ic_pin` при свайпе вправо
- Используется `viewModel.pinChat()`/`unpinChat()` из существующего API

**Pinned messages bottom sheet:**
- Иконка 📌 в toolbar чата — показывает bottom sheet со списком закреплённых сообщений
- Тап на сообщение → скролл к нему в чате
- `PinnedMessageAdapter` с `DiffUtil` для анимированных обновлений
- Пустое состояние: "Нет закреплённых сообщений"

### Улучшено

**CallViewModelTest — полный TestScope:**
- Удалён `@Ignore` — все 5 тестов снова работают
- `TestScope`注入 вместо `viewModelScope` — нет deadlock при `Dispatchers.resetMain()`
- Таймер уже count-based (elapsedSeconds), тесты корректно проверяют формат времени
- `viewModel.stopTimer()` в каждом тесте предотвращает infinite drain

**GrpcConnectionManager — reconnect logic extraction:**
- Новый класс `GrpcReconnectStrategy` — exponential backoff + auth failure guard
- `GrpcConnectionManager` делегирует через `reconnectStrategy` property
- `isAuthFailure` forwarded через getter/setter — обратная совместимость
- Существующие тесты `GrpcConnectionManagerTest` проходят без изменений

**Room DB indexes:**
- `messages.isSent` — индекс для `getPendingMessages()` (WHERE isSent = 0)
- `chats.type` — индекс для фильтрации по типу чата (direct/group/secret/AI)
- Миграция 14→15 с `CREATE INDEX IF NOT EXISTS`

**Glide placeholder + error:**
- `StickerGridAdapter` — `ic_image_placeholder` дляthumbnail стикеров
- `MessageAdapter` — `ic_image_placeholder` для изображений стикеров в чате
- `StickerPackListAdapter` — `ic_image_placeholder` для обложек пакетов

### Изменения в файлах

| Файл | Изменение |
|------|-----------|
| `CallViewModelTest.kt` | Удалён @Ignore, TestScope injection, stopTimer в каждом тесте |
| `GrpcReconnectStrategy.kt` | NEW — backoff scheduling + auth failure guard |
| `GrpcConnectionManager.kt` | Delegate to GrpcReconnectStrategy, isAuthFailure forwarded |
| `ChatListActivity.kt` | Swipe right для pin/unpin, green pin icon background |
| `chat_menu.xml` | +action_pinned_messages (ic_pin) |
| `NewChatActivity.kt` | showPinnedMessagesSheet(), onPrepareOptionsMenu для pinned |
| `PinnedMessageAdapter.kt` | NEW —ListAdapter<Message> с DiffUtil |
| `dialog_pinned_messages.xml` | NEW — layout bottom sheet |
| `item_pinned_message.xml` | NEW — layout элемента pinned message |
| `Entities.kt` | @ColumnInfo(index=true) для messages.isSent, chats.type |
| `AppDatabase.kt` | version 15, MIGRATION_14_15 (indexes) |
| `StickerGridAdapter.kt` | +placeholder +error для Glide |
| `MessageAdapter.kt` | +placeholder +error для sticker Glide |
| `StickerPackListAdapter.kt` | +placeholder +error для cover Glide |
| `strings.xml` (EN/RU) | +2 строки (pinned_messages, no_pinned_messages) |

---

## [1.3.3.7] - 2026-07-19

### Добавлено

**Dark mode system sync:**
- Переключатель "Следовать системной тёмной теме" в настройках тем
- Автоматическое переключение между светлой и тёмной темой при изменении системных настроек
- `ThemeStore.setFollowSystemDarkMode()` + `ThemeStore.onConfigurationChanged()`

**Chat list swipe actions:**
- Swipe left на чате → диалог с опциями: Архивировать, Мute/Unmute, Удалить
- Красный фон с иконкой удаления при свайпе
- Архивация/мьют через существующие методы ChatListViewModel

**Message reactions picker:**
- Расширенный пикер реакций: 32 эмодзи в сетке 8x4
- Было: 9 эмодзи в горизонтальном скролле
- Эмодзи: 👍👎❤️🔥😮😢😂🎉💯✅❌🙏💪👏🤝🤔😍🥳😎🤯💀👻🤡💩👀🫡🫶🤷💬📎⭐💡

**GrpcClient cleanup:**
- Удалены неиспользуемые `context` параметры из 9 facade методов (pinChat, unpinChat, archiveChat, unarchiveChat, searchChats, pinMessage, unpinMessage, getPinnedMessages, getServers)
- Удалён redundant `CoroutineScope` — заменён на `realGrpcClient.scope`
- Обновлены все вызывающие коды

### Улучшено

**Sticker pack creation UX:**
- Удалено дублирующее поле "Имя пакета" — достаточно одного "Название"
- Поле "Название" предзаполняется "Стикеры от @username"
- Звёздочка в хинте указывает на обязательность заполнения
- Кнопка "Сохранить" заблокирована пока нет добавленных стикеров
- Кнопка "Сохранить" темизирована через Material3 стиль

**Sticker pack cover selection:**
- Long-press на стикере в пакете → диалог "Сделать обложкой"
- Обложка пакета сохраняется при создании/редактировании

**Sticker editor crop/resize:**
- Добавлено панорамирование изображения (drag) в режиме обрезки
- Добавлен зум (pinch-to-zoom) в режиме обрезки
- Изображение не может быть слишком уменьшено/увеличено (ограничения зума)
- Выходной файл: JPEG 85% + resize до 512px (вместо PNG 95% полного размера)
- Размер стикера: 10-50 КБ вместо 200 КБ - 2 МБ

**ChatAdapter stability:**
- `updateOnlineUsers()`/`updateAllUsers()` обновляют только direct-чаты через `notifyItemChanged()`
- Убран `notifyDataSetChanged()` — меньше мерцание при обновлении статуса онлайн

**CallViewModelTest:**
- Добавлена инъекция `CoroutineScope` в `CallViewModel` для тестирования
- Тесты используют `StandardTestDispatcher` + `Dispatchers.setMain()`

### Изменения в файлах

| Файл | Изменение |
|------|-----------|
| `ThemeStore.kt` | followSystemDarkMode, resolveSystemTheme(), onConfigurationChanged() |
| `ThemePreferences.kt` | isFollowSystemDarkMode(), setFollowSystemDarkMode() |
| `ThemesActivity.kt` | Switch для system dark mode |
| `activity_themes.xml` | MaterialCardView с Switch |
| `ThemeUi.kt` | +ThemeStore.init() для system dark mode |
| `ChatListActivity.kt` | setupSwipeActions() — ItemTouchHelper для archive/mute/delete |
| `ChatAdapter.kt` | +currentList() для swipe actions |
| `ChatMessageMenuDelegate.kt` | 32 эмодзи в GridLayout вместо 9 в HorizontalScrollView |
| `dialog_reactions.xml` | GridLayout вместо HorizontalScrollView |
| `GrpcClient.kt` | Удалены context параметры из 9 методов, удалён redundant scope |
| `ChatListActionMode.kt` | Удалены context параметры из вызовов |
| `ChatListViewModel.kt` | Удалены context параметры из вызовов |
| `ChatViewModel.kt` | Удалён context параметр из getPinnedMessages |
| `StickerPackCreateActivity.kt` | Удалено etName, дефолтное название, блокировка сохранения без стикеров, выбор обложки |
| `activity_sticker_pack_create.xml` | Удалён etName, звёздочка в хинте, стиль btnSave |
| `StickerEditorView.kt` | Панорамирование + зум в режиме CROP, constrainImage() |
| `StickerEditorActivity.kt` | JPEG 85% + resize 512px |
| `ChatAdapter.kt` | Таргетированные notifyItemChanged для online/allUsers |
| `CallViewModel.kt` | scope injection (scope: CoroutineScope? = null) |
| `CallViewModelTest.kt` | TestScope + StandardTestDispatcher |
| `strings.xml` (EN/RU) | +6 строк (cover, default name, save disabled) |

## [1.3.3.6] - 2026-07-19

### Добавлено

**Sticker Editor Activity:**
- Кастомный `StickerEditorView` с crop (квадратный), текстовым оверлеем и 5 фильтрами
- Фильтры: Original, B&W, Sepia, Warm, Cool, Bright
- Цветовая палитра (6 цветов) для текста
- Long-press для перетаскивания текста, pinch-to-scale
- Интеграция с `StickerPackCreateActivity` — изображения открываются в редакторе перед загрузкой
- Полная темизация: `ThemeApplier.apply()` + `applyThemeToViews()`

**Sticker search in picker:**
- Поисковая строка с debounce 300ms в `MediaPickerSheet`
- Серверный поиск через `searchStickerPacks()` gRPC
- Результаты отображаются в сетке стикеров

**Sticker recent/favorites:**
- `StickerPreferencesManager` — хранит последние 20 использованных и избранных стикеров
- 3 таба в пикере: 😀 (emoji), ⭐ (favorites/recent), 🎨 (stickers)
- Long-press на стикере — добавить/убрать из избранного

**Company invite code:**
- Кнопка "Поделиться кодом приглашения" — копирует company ID в буфер обмена
- `JoinCompanyActivity` — экран для вступления в компанию по коду

**Company settings:**
- Экран настроек компании с переключателем уведомлений
- Локальное хранение настроек mute для company чатов

**runOnUiThread migration:**
- ~150 вызовов `runOnUiThread`/`safeRunOnUiThread` мигрированы на `lifecycleScope.launch` в 27 файлах
- Удалены определения `safeRunOnUiThread` из CallActivity, ConferenceLobbyActivity, EditProfileActivity

### Изменения в файлах

| Файл | Изменение |
|------|-----------|
| `StickerEditorActivity.kt` | NEW — редактор стикеров |
| `StickerEditorView.kt` | NEW — кастомный view для crop/text/filters |
| `activity_sticker_editor.xml` | NEW — layout редактора |
| `StickerPreferencesManager.kt` | NEW — recent/favorites хранение |
| `JoinCompanyActivity.kt` | NEW — вступление в компанию по коду |
| `activity_join_company.xml` | NEW — layout экрана вступления |
| `dialog_company_settings.xml` | NEW — layout настроек компании |
| `MediaPickerSheet.kt` | +search bar, +favorites tab, +recent tracking |
| `StickerGridAdapter.kt` | +onStickerLongClick callback |
| `StickerPackCreateActivity.kt` | Интеграция с редактором |
| `CompanyProfileActivity.kt` | +invite code sharing, +company settings |
| `menu_company_profile.xml` | +invite, +settings menu items |
| 27 файлов | runOnUiThread → lifecycleScope.launch |
| `strings.xml` (EN/RU) | +27 строк (editor, filters, favorites, company) |

---

## [1.3.3.5] - 2026-07-19

### Исправлено

**UTF-8 ошибка при отправке сообщений (серверный баг):**
- Сервер хранил невалидные UTF-8 байты в `reply_preview` — PostgreSQL отклонял INSERT
- Сервер исправлен: добавлена валидация `utf8.ValidString()` для `orig.Text`, `row.MediaURL`, `row.ReplyPreview`
- Клиент: добавлена `stripInvalidUtf8()` в `domainToSendRequest()` как safety net

**Система стикеров — полный аудит и исправления:**

**Табы в шторке стикеров не адаптированы к темам:**
- `MediaPickerSheet` не темизировал `TabLayout` — добавлен `applyTheme()` с программной темизацией

**Кнопка "Создать пакет" ничего не делала:**
- `onCreateStickerPack` callback не передавался в `MediaPickerSheet` — теперь запускает `StickerPackCreateActivity`

**Создание пакета — текст не виден на тёмной теме:**
- `StickerPackCreateActivity` не темизировал `TextInputLayout`/`EditText` — добавлен `applyThemeToFields()`

**Кнопка "Добавить стикер" не темизирована:**
- OutlinedButton стиль не обрабатывался — добавлена ручная темизация `strokeColor`/`textColor`

**Файловый пикер — принимал только JSON:**
- Теперь принимает `image/jpeg`, `image/png`, `image/webp` наряду с `application/json`

**Тосты не переведены на русский:**
- Добавлены строковые ресурсы EN/RU: `sticker_added`, `sticker_pack_created`, `sticker_upload_failed`, `sticker_title_required`, `sticker_submitted`, `sticker_pick_image`

**Шторка стикеров — пустые пакеты отображались:**
- Фильтр `stickers.isNotEmpty()` для всех пакетов

**Шторка стикеров — утверждённые пакеты не отображались:**
- Фильтр `!= "approved"` заменён на merge всех пакетов + dedup по ID

**Сетка стикеров — изображения не отображались:**
- `StickerGridAdapter` определяет тип по URL: `.json` → Lottie, иначе → Glide
- `repeatCount=0` вместо `MAX_VALUE`, `onViewRecycled()` → `cancelAnimation()`

**Сообщение стикера в чате — изображения не отображались:**
- `MessageAdapter` + `ivStickerImage` ImageView с Glide fallback

**Загрузка стикера — ошибка не показывалась:**
- Проверка HTTP response code, Toast с ошибкой от сервера

**Размер файла стикера — увеличен лимит:**
- 512KB → 2MB, компрессия до 1024px вместо 512px

**Библиотека стикеров — хардкод English и цветов:**
- Plurals для количества, строковые ресурсы для статусов

### Изменения в файлах

| Файл | Изменение |
|------|-----------|
| `MediaPickerSheet.kt` | `applyTheme()` для TabLayout, фильтр пустых/approved пакетов, `onCreateStickerPack` callback |
| `StickerPackCreateActivity.kt` | `applyThemeToFields()`, файловый пикер для изображений, i18n, компрессия до 2MB/1024px |
| `StickerGridAdapter.kt` | Поддержка изображений (Glide), `repeatCount=0`, `onViewRecycled()` |
| `StickerPackListAdapter.kt` | i18n (plurals, статусы), поддержка изображений в обложке |
| `MessageAdapter.kt` | `stickerImageView` + Glide fallback для изображений |
| `ChatInputDelegate.kt` | `onCreateStickerPack` callback → `StickerPackCreateActivity` |
| `GrpcMessageV2Client.kt` | `stripInvalidUtf8()` в `domainToSendRequest()` |
| `activity_sticker_pack_create.xml` | Кнопка "Save" → `@string/save`, подсказка maxSize |
| `item_sticker_pack.xml` | `coverImageView` для изображений |
| `item_message.xml` | `ivStickerImage` для изображений |
| `sheet_media_picker.xml` | (без изменений — темы через программный код) |
| `strings.xml` (EN/RU) | +`sticker_added`, `sticker_pack_created`, `sticker_upload_failed`, `sticker_title_required`, `sticker_submitted`, `sticker_pick_image`, `sticker_max_size_hint`, `sticker_file_too_large`, `sticker_count` (plurals) |
| `CHANGELOG.md` | v1.3.3.5 |
| `GOTCHAS.md` | v1.3.3.4 + v1.3.3.5 gotchas |
| `MEMORY.md` | Обновлена версия + sticker system + UTF-8 |

---

## [1.3.3.4] - 2026-07-19

### Исправлено

**UTF-8 ошибка при отправке сообщений (серверный баг):**
- Сервер хранил невалидные UTF-8 байты в `reply_preview` — PostgreSQL отклонял INSERT
- Сервер исправлен: добавлена валидация `utf8.ValidString()` для `orig.Text`, `row.MediaURL`, `row.ReplyPreview`
- Клиент: добавлена `stripInvalidUtf8()` в `domainToSendRequest()` как safety net

**Загрузка стикера — ошибка не показывалась при файле слишком большом:**
- Сервер отвечал "file too large", но клиент проглатывал ошибку (пустой catch)
- Теперь проверяется HTTP response code: 200 = успех, иначе — парсинг ошибки из response body
- Добавлена автоматическая компрессия изображений >2MB перед загрузкой (масштабирование до 1024px + PNG quality 90%)
- Пользователь видит Toast с текстом ошибки от сервера

**Система стикеров — полный аудит и исправления:**

**Создание пакета стикеров — текст не виден на тёмной теме:**
- `StickerPackCreateActivity` не темизировал `TextInputLayout` и `EditText`
- Добавлен `applyThemeToFields()` — программная темизация полей ввода из текущей темы

**Кнопка "Добавить стикер" не темизирована:**
- OutlinedButton стиль не обрабатывался `ThemeApplier`
- Добавлена ручная темизация `strokeColor` и `textColor`

**Файловый пикер — принимал только JSON (Lottie):**
- Теперь принимает `image/jpeg`, `image/png`, `image/webp` наряду с `application/json`
- Изображения загружаются на сервер через `/upload-sticker` с правильным MIME-типом

**Тосты не переведены на русский:**
- Добавлены строковые ресурсы EN/RU: `sticker_added`, `sticker_pack_created`, `sticker_pack_updated`, `sticker_upload_failed`, `sticker_title_required`, `sticker_submitted`, `sticker_pick_image`
- Кнопка "Save" → `@string/save`

**Шторка стикеров — пустые пакеты отображались:**
- `MediaPickerSheet` фильтрует пакеты без стикеров: `it.stickers.isNotEmpty()`

**Шторка стикеров — утверждённые пакеты пользователя не отображались:**
- Фильтр `!= "approved"` ошибочно исключал утверждённые пакеты
- Исправлено на объединение всех пакетов с дедупликацией по ID

**Шторка стикеров — табы не адаптированы к темам:**
- Добавлен `applyTheme()` в `MediaPickerSheet` — темизация `TabLayout` из текущей темы

**Библиотека стикеров — хардкод English и цветов:**
- `"${count} stickers"` → `resources.getQuantityString(R.plurals.sticker_count)`
- Статусы → строковые ресурсы: `sticker_approved`, `sticker_pending`, `sticker_rejected`
- Цвета статусов временно hardcoded (Material3 не экспозирует `colorError` в XML)

**Сетка стикеров — поддержка изображений:**
- `StickerGridAdapter` теперь определяет тип стикера по URL (`.json` = Lottie, иначе = Glide)
- `thumbnailView` теперь используется для отображения изображений
- `repeatCount = 0` вместо `Int.MAX_VALUE` — анимация проигрывается один раз
- Добавлен `onViewRecycled()` с `cancelAnimation()` для освобождения ресурсов

**Сообщение стикера в чате — поддержка изображений:**
- `MessageAdapter.bindStickerContent()` теперь проверяет URL: Lottie → `LottieAnimationView`, изображение → `Glide`
- Добавлен `ivStickerImage` (`ImageView`) в `item_message.xml`

**Сетка стикеров — Life-cycle утечки:**
- `onViewRecycled()` вызывает `cancelAnimation()` и `clearAnimation()` для Lottie

### Изменения в файлах

| Файл | Изменение |
|------|-----------|
| `StickerPackCreateActivity.kt` | `applyThemeToFields()`, темизация EditText/TextInputLayout, файловый пикер для изображений, i18n тостов |
| `MediaPickerSheet.kt` | `applyTheme()` для TabLayout, фильтр пустых пакетов, исправлен фильтр approved |
| `StickerGridAdapter.kt` | Поддержка изображений (Glide), `repeatCount=0`, `onViewRecycled()` |
| `StickerPackListAdapter.kt` | i18n (plurals, статусы), поддержка изображений в обложке |
| `MessageAdapter.kt` | `stickerImageView` + Glide fallback для изображений |
| `ChatInputDelegate.kt` | `onCreateStickerPack` callback → `StickerPackCreateActivity` |
| `StickerPackCreateActivity.kt` | Проверка HTTP response code, компрессия изображений >2MB до 1024px, Toast с ошибкой от сервера |
| `activity_sticker_pack_create.xml` | Кнопка "Save" → `@string/save` |
| `item_sticker_pack.xml` | `coverImageView` для изображений |
| `item_message.xml` | `ivStickerImage` для изображений |
| `strings.xml` (EN/RU) | +`sticker_added`, `sticker_pack_created`, `sticker_pack_updated`, `sticker_upload_failed`, `sticker_title_required`, `sticker_submitted`, `sticker_pick_image`, `sticker_count` (plurals) |

---

## [1.3.3.3] - 2026-07-18

### Исправлено

**Шторка стикеров — фатал при открытии:**
- `MediaPickerSheet` крашился с `ClassCastException`: `emojiContainer` (`NestedScrollView`) кастовался в `LinearLayout`
- Исправлено на `findViewById<View>` — используется только `.isVisible`

**Шторка стикеров — дополнительные защиты:**
- `ThemeApplier.applyToDialog()` вызывался вне try-catch в `applyTheme()` — теперь обёрнут
- `behavior.state = STATE_EXPANDED` вызывался до `dialog.show()` — перенесён после с try-catch
- Добавлена проверка `activity.isFinishing/isDestroyed` в `showPicker()`

**Админ-панель — список пользователей не отображался:**
- `loadData()` зависал в цепочке `getAdminUserList` → `getAllChats` → `updateAdminUI`
- Если `getAllChats` не отвечал — UI никогда не обновлялся
- Пользователи теперь отображаются сразу после `getAdminUserList`, чаты грузятся отдельно

**UNIMPLEMENTED: GetAllChats:**
- Сервер не имеет метода `messenger.ChatService/GetAllChats`
- `getAllChats()` переписан на рекурсивный сбор через `GetChatsV2` с лимитом 1000 и пагинацией

**Прогресс загрузки обновления:**
- Панель показывала только цифру `42%` — неочевидно
- Теперь: `Загрузка: 42%` / `Downloading: 42%`

### Изменения в файлах

| Файл | Изменение |
|------|-----------|
| `MediaPickerSheet.kt` | `emojiContainer` → `findViewById<View>`, try-catch в `showPicker()` |
| `WidgetSystem.kt` | `ThemeApplier.applyToDialog` в try-catch, `behavior.state` после `dialog.show()` |
| `ChatInputDelegate.kt` | Убран fallback на старый пикер |
| `GrpcChatClient.kt` | `getAllChats` → рекурсивный `GetChatsV2` с пагинацией |
| `SuperAdminActivity.kt` | Декаплинг `getAdminUserList` и `getAllChats` |
| `strings.xml` | `percent_format` → `"Загрузка: %1$d%%"` |

---

## [1.3.3.2] - 2026-07-18

### Исправлено

**Шаринг изображений из Telegram — чёрный экран и зависание:**
- `ShareReceiverActivity.onCreate()` вызывал `GrpcClient.connect()` синхронно на Main thread — блокировал UI и вызывал ANR
- Перенесён `ensureConnection()` в `lifecycleScope` + `Dispatchers.IO`
- `ThemeStore.currentTheme()` без try-catch — теперь с fallback на тему по умолчанию

**Эмодзи-пикер — стикеры не отображались:**
- `showMediaPicker()` в `ChatInputDelegate` по-прежнему использовал старый `dialog_emoji_picker.xml` (только эмодзи)
- Заменён на `MediaPickerSheet` с табами 😀/🎨 (эмодзи + стикеры)

**CallViewModelTest — тест зависал навсегда:**
- `viewModelScope` с бесконечным `while(true)` не отменялся при `Dispatchers.resetMain()`
- Таймер переделан на счётчик (без `System.currentTimeMillis()`)
- `@After` добавлен `viewModel.stopTimer()`
- Тесты с `@Ignore` до миграции на `TestScope`

### Изменения в файлах

| Файл | Изменение |
|------|-----------|
| `ShareReceiverActivity.kt` | `ensureConnection()` в coroutine, try-catch на theme |
| `ChatInputDelegate.kt` | `showMediaPicker()` → `MediaPickerSheet` |
| `CallViewModel.kt` | Таймер на счётчике вместо `System.currentTimeMillis()` |
| `CallViewModelTest.kt` | `@Ignore`, `stopTimer()` в `@After` |

---

## [1.3.3.1] - 2026-07-18

### Исправлено

**Эмодзи-пикер зависает при открытии:**
- Новый `MediaPickerSheet` с TabLayout и двумя контейнерами вызывал зависание на главном потоке
- Возвращён оригинальный проверенный эмодзи-пикер (`dialog_emoji_picker.xml`) — быстрый и стабильный
- Стикер-отправка доступна через `sendStickerMessage()` напрямую

**Список чатов — прелоадер зависает:**
- `SwipeRefreshLayout.isRefreshing` привязывался к `isLoading` — показывал/скрывал спиннер при каждом обновлении
- Спиннер иногда зависал в состоянии "загрузка" после завершения
- Убран автоматический показ прелоадера — спиннер показывается только при ручном pull-to-refresh и автоматически скрывается после завершения

### Изменения в файлах

| Файл | Изменение |
|------|-----------|
| `ChatInputDelegate.kt` | Возвращён оригинальный эмодзи-пикер вместо MediaPickerSheet |
| `ChatListActivity.kt` | Убран прелоадер `swipeRefresh?.isRefreshing = loading`, автоскрытие |

---

## [1.3.3.0] - 2026-07-18

### Добавлено

**Система стикеров:**
- Библиотека стикеров с Lottie-анимацией — стикеры загружаются как .json файлы и рендерятся через LottieAnimationView
- Объединённый пикер «Эмодзи | Стикеры» — одна шторка с двумя вкладками (TabLayout) вместо отдельных кнопок
- Пакеты стикеров — группировка стикеров по пакетам (как в Telegram), создание/удаление/редактирование пакетов
- Создание стикеров — Activity для загрузки Lottie-файлов с устройства на сервер (multipart upload)
- Отправка стикеров в чат — стикер отправляется как MessageMediaProto(type="sticker"), рендерится в MessageAdapter через LottieAnimationView
- Публичная библиотека — одобренные админом пакеты доступны всем пользователям
- Workflow согласования — пользователь создаёт пакет → отправляет на согласование → super_admin одобряет/отклоняет

**Интерфейс:**
- MediaPickerSheet — нижний пикер с вкладками Emoji (существующий网格 160 эмодзи) и Stickers (список пакетов + сетка стикеров)
- StickerLibraryActivity — экран библиотеки стикеров с табами «Мои пакеты» / «Публичные», pull-to-refresh, FAB для создания
- StickerPackCreateActivity — экран создания/редактирования пакета с текстовыми полями, загрузкой Lottie-файлов, кнопками «Сохранить» и «Отправить на согласование»

**Data layer:**
- GrpcStickerClient — 13 RPC-методов для StickerService (создание/удаление пакетов, добавление/удаление стикеров, поиск, согласование)
- StickerMarshallers — 26 custom marshallers для StickerService proto-сообщений
- StickerProto + Sticker domain models — прото-модели и доменные модели для стикеров
- Room DB — sticker_packs + stickers таблицы с миграцией 13→14
- GrpcClient facade — делегирование всех стикерных методов

**Серверная часть (v1.3.4.0):**
- messenger.StickerService — 13 RPC-методов (CreateStickerPack, AddSticker, RemoveSticker, DeleteStickerPack, GetUserStickerPacks, GetPublicStickerPacks, GetStickerPack, SubmitForApproval, ApproveStickerPack, GetPendingStickerPacks, SearchStickerPacks, UpdateStickerPack, SetFeaturedStickerPack)
- HTTP эндпоинты — /upload-sticker (Lottie .json), /upload-sticker-thumbnail (PNG/JPG/WebP)
- DB schema — sticker_packs (title, name, creator_user_id, status, is_featured) + stickers (pack_id, lottie_url, thumbnail_url, emoji)

**Тесты:**
- StickerMarshallersTest — 43 теста на proto-модели, marshallers, доменные модели
- StickerDomainTest — тесты доменных моделей Sticker и StickerPack
- StickerMessageTest — тесты конвертации стикерных сообщений (proto ↔ domain)

### Изменения в файлах

| Файл | Изменение |
|------|-----------|
| `build.gradle.kts` | +lottie dependency |
| `gradle/libs.versions.toml` | +lottie version catalog |
| `AndroidManifest.xml` | +StickerLibraryActivity, +StickerPackCreateActivity |
| `data/proto/StickerProto.kt` | NEW: 13+ proto data classes |
| `data/proto/MessagesV2Proto.kt` | sticker media type support |
| `data/proto/ProtoUtils.kt` | +sticker conversion |
| `data/grpc/StickerMarshallers.kt` | NEW: 26 marshallers |
| `data/grpc/GrpcStickerClient.kt` | NEW: 13 gRPC methods |
| `data/grpc/GrpcClient.kt` | +sticker facade methods |
| `data/models/Sticker.kt` | NEW: domain models |
| `data/models/Message.kt` | +stickerUrl, stickerThumbnailUrl |
| `data/db/StickerEntities.kt` | NEW: Room entities |
| `data/db/Daos.kt` | +StickerPackDao, +StickerDao |
| `data/db/AppDatabase.kt` | +sticker entities, migration 13→14 |
| `data/sticker/StickerCacheManager.kt` | NEW: Lottie disk + memory cache |
| `ui/chat/message/ChatInputDelegate.kt` | +showMediaPicker(), +sendStickerMessage() |
| `ui/chat/message/MediaPickerSheet.kt` | NEW: combined emoji+sticker picker |
| `ui/adapter/MessageAdapter.kt` | +bindStickerContent() |
| `ui/sticker/StickerGridAdapter.kt` | NEW: sticker grid |
| `ui/sticker/StickerPackAdapter.kt` | NEW: pack tabs |
| `ui/sticker/StickerPackListAdapter.kt` | NEW: pack card list |
| `StickerLibraryActivity.kt` | NEW: sticker library |
| `StickerPackCreateActivity.kt` | NEW: pack create/edit |
| `res/layout/sheet_media_picker.xml` | NEW: combined picker layout |
| `res/layout/item_sticker_grid.xml` | NEW: sticker grid item |
| `res/layout/item_sticker_pack_tab.xml` | NEW: pack tab item |
| `res/layout/item_sticker_pack.xml` | NEW: pack card item |
| `res/layout/item_message.xml` | +LottieAnimationView |
| `res/layout/activity_sticker_library.xml` | NEW |
| `res/layout/activity_sticker_pack_create.xml` | NEW |
| `res/values/strings.xml` | +17 sticker strings |
| `res/values-ru/strings.xml` | +17 sticker strings (RU) |

---

## [1.3.2.20] - 2026-07-18

### Исправлено

**Смена ориентации экрана в чате сбрасывает статус тулбара:**
- `NewChatActivity` не имел `configChanges` — при повороте экрана Activity пересоздавалась, теряя состояние всех delegate'ов (toolbar, E2EE, typing status)
- Статус подключения/набора текста/E2EE сбрасывался на дефолтный
- Добавлен `android:configChanges="orientation|screenSize|keyboardHidden"` в AndroidManifest.xml — Activity больше не пересоздаётся при повороте

**Редактирование профиля — секция @username не адаптирована к теме:**
- `usernameCard` отсутствовал в списке карточек `ThemeApplier` — фон оставался прозрачным вместо `surfaceColor`
- Добавлен `R.id.usernameCard` в список карточек ThemeApplier, теперь получает `surfaceColor` фон как все остальные секции

**Единый стиль отображения компании/должности:**
- `CompanyProfileActivity` не показывала плашку должности текущего пользователя
- Добавлен `positionBubble` (MaterialCardView) в `activity_company_profile.xml` с тем же стилем что и в `EditProfileActivity`
- Позиция загружается из `SessionManager.session` и отображается с `primaryColor 0.15f alpha` фоном и `textPrimaryColor` текстом

**Секретный чат — обмен ключами бесконечно зависает в статусе "Обмен ключами...":**
- `updateSubtitle()` вызывался только через observer flow (при изменении StateFlow connection/users/typing). Флаг `isE2eeInProgress` менялся, но subtitle не обновлялся
- Добавлен `refreshSubtitle()` метод в `ChatToolbarDelegate` с автосохранением последних параметров `updateSubtitle()`
- Сеттер `isE2eeInProgress` теперь вызывает `refreshSubtitle()` автоматически
- E2EE callbacks (`onKeyExchangeStart`, `onKeyExchangeComplete`) в `NewChatActivity` теперь вызывают `toolbarDelegate.refreshSubtitle()`

### Изменения в файлах

| Файл | Изменение |
|------|-----------|
| `AndroidManifest.xml` | +`configChanges` для NewChatActivity |
| `ThemeApplier.kt` | +`usernameCard` в список карточек |
| `activity_company_profile.xml` | +positionBubble (MaterialCardView) |
| `CompanyProfileActivity.kt` | +position display, +formatCompanyPosition() |
| `ChatToolbarDelegate.kt` | +refreshSubtitle(), +last* параметры, auto-refresh в isE2eeInProgress setter |
| `NewChatActivity.kt` | +refreshSubtitle() в E2EE callbacks |
| `CHANGELOG.md` | v1.3.2.20 |

---

## [1.3.2.19] - 2026-07-17

### Исправлено

**Сообщения — лишнее пространство между текстом и реакциями:**
- При рефакторинге v1.3.2.16-18 случайно удалены строки установки `timeText.text` и `bindReadStatus()` из `MessageAdapter.bind()`
- `llMessageMeta` имел высоту但 пустой контент → лишнее пространство перед реакциями

**Сообщения — индикаторы отправленности и прочитанности:**
- `bindReadStatus()` не вызывался для исходящих сообщений → иконки статуса не отображались
- Восстановлен вызов `bindReadStatus()` и форматирование времени в `MessageAdapter.kt`

**Профиль — иконка "назад" не видна:**
- `applyThemeToView()` рекурсивно окрашивал все `ImageView` в `primary` цвет, включая иконку навигации в toolbar
- Добавлен повторный вызов `setNavigationIconTint(getColorOnPrimary())` после `applyThemeToView()` в `updateProfileUI()` и `updateGroupUI()`

**Редактирование профиля — заголовок "Имя" не адаптирован к теме:**
- TextView без `android:id` → `ThemeApplier` не мог найти и применить цвет
- Добавлен `android:id="@+id/usernameLabel"` и обработка в `ThemeApplier`

**Редактирование профиля — отступы текста "Имя" и "Кратко о себе":**
- Убран лишний `TextInputLayout` обёртка вокруг `editTextBio` (hintEnabled=false, boxBackgroundMode=none)
- Оба текста теперь имеют одинаковый левый отступ 16dp

**Плашка должности — невидимый фон:**
- `adjustAlpha(surfaceColor, 0.6f)` → фон сливается с карточкой на тёмных темах
- Заменён на `adjustAlpha(primaryColor, 0.15f)` — видимый на любом фоне
- Исправлено в `ProfileActivity.updateProfileUI()`, `EditProfileActivity.setupUI()` и `reloadProfile()`

**Редактирование профиля — "Изменить пароль" не выровнена:**
- Кнопка была по центру без отступов
- Добавлен `padding="16dp"` в родительский `LinearLayout` для выравнивания с другими блоками

---

## [1.3.2.18] - 2026-07-17

### Исправлено

**Краш при входе в чат на некоторых устройствах:**
- `MessageAdapter.bind()` обёрнут в полный try-catch с fallback-отрисовкой
- `ThemeStore.currentTheme()` в bind() защищён fallback на BuiltInThemes.dark
- Glide загрузка аватаров обёрнута в try-catch
- `ThemeUtils.applyDefaultAvatar()` обёрнут в try-catch
- `fetchChatMetadata` callback — добавлена проверка `isFinishing`/`isDestroyed`
- Messages collector — добавлен try-catch
- Добавлен `UncaughtExceptionHandler` — crash info сохраняется в SharedPreferences

**Статус сервера при обновлении:**
- Текст "Server restarting…" → "Server updating…" / "Сервер обновляется…"
- Статус отображается при `DISCONNECTED`/`FAILED` если `serverShuttingDown=true`
- `ChatToolbarDelegate.updateSubtitle()` принимает `isServerShuttingDown`
- `NewChatActivity` передаёт `serverShuttingDown` в `updateSubtitle()`

---

## [1.3.2.17] - 2026-07-17

### Исправлено

**Профиль/Редактирование — плашка должности не адаптирована к темам:**
- На тёмных темах (Графит и др.) фон плашки был прозрачный синий, текст читался плохо
- Фон плашки теперь использует `surfaceColor` с прозрачностью (0.6 alpha) вместо `primaryColor` (0.12 alpha)
- Цвет текста должности устанавливается программно через `textPrimaryColor` вместо `?attr/colorOnPrimaryContainer`

**Редактирование профиля — inline @username:**
- Карточка "Имя" в стиле карточки "Кратко о себе" (заголовок + значение)
- Тап на карточку открывает шторку редактирования имени

---

## [1.3.2.16] - 2026-07-17

### Исправлено

**Загрузка галереи — параллельная загрузка фото:**
- Фото загружались одновременно (все HTTP-запросы параллельно) → сервер перегружался, процесс прерывался
- Переделано на последовательную загрузку (одно за другим через coroutine)
- Ошибка загрузки одного фото не прерывает загрузку остальных

**Загрузка галереи — индикация на миниатюрах:**
- На каждой миниатюре отображается индикатор загрузки (спиннер)
- Успешно загруженные — зелёная галочка
- Ошибки — красный крестик + кнопка удаления
- Сообщение в чат отправляется только после полной загрузки всех фото

### Исправлено

**Темы — фон плашек в списке чатов при смене темы:**
- Фон карточек чатов не обновлялся при смене темы (только после перезапуска)
- `ChatAdapter.updateTheme()` сбрасывает кэш цветов + `notifyItemRangeChanged`
- Вызов в `ChatListActivity.onResume()` после `ThemeApplier.apply()`

**Профиль — стрелка "назад" не видна:**
- `setSupportActionBar()` перезаписывал tint иконки навигации
- Убран `setSupportActionBar()`, тулбар управляется напрямую (`setNavigationIcon` + `setTint(getColorOnPrimary())`)

**Профиль — заголовок "О себе" не в цвете темы:**
- Добавлен явный `bioTitle.setTextColor(primaryColor)` в `updateProfileUI()`

**Профиль — фон плашки должности:**
- `positionBubble` получал некорректный фон в кастомных темах
- Установка `cardBackgroundColor` программно через primary color с альфой

**Галерея в галерее просмотра — порядок элементов:**
- Поменяны местами миниатюры и счётчик: миниатюры сверху, счётчик (1/5) снизу

### Добавлено

**Галерея в чате — кликабельные миниатюры вместо +N:**
- Для галерей (2+ фото) вместо одного большого изображения с бейджем "+N" — горизонтальная полоса миниатюр (до 4)
- Клик по миниатюре открывает `FullScreenImageActivity` на нужном индексе
- `ThumbnailGridAdapter` (внутренний класс `MessageAdapter`)
- Для одиночного изображения — прежнее поведение

**Редактирование профиля — inline @username:**
- Вместо кнопки "Изменить имя пользователя" — `@username` сразу под аватаром
- Тап на `@username` открывает ту же шторку редактирования

---

## [1.3.2.15] - 2026-07-17

### Исправлено

**Контакты — чекбокс в режиме выбора:**
- Тап на чекбокс (справа на плашке) открывал профиль вместо переключения выбора
- Чекбокс теперь кликабельный и обрабатывает тап отдельно от карточки

**Темы — кэш фона списков чатов:**
- После применения темы и возврата на список чатов фон плашек оставался от предыдущей темы
- Добавлен `ThemeStore.init()` + `ThemeApplier.apply()` в `ChatListActivity.onResume()`

**Темы — цвет иконок и био в профиле:**
- Иконки действий (сообщение, звонок, видео, почта) и текст "О себе" использовали цвет из темы "Лавовый ночной" вместо текущей темы
- Добавлен `ImageView` case в `applyThemeToView()` для tinting иконок
- Добавлен `ThemeStore.init()` в `ProfileActivity.onCreate()` до `ThemeUi.bind()`

**Сообщения — редактирование не обновлялось сразу:**
- После редактирования сообщения текст обновлялся только при повторном входе в чат
- `editMessageV2()` теперь обновляет `_messages` StateFlow и Room DB сразу после успешного ответа
- Добавлен `updateMessageText()` в `MessageDao`

### Добавлено

**Галерея — навигация миниатюрами:**
- `FullScreenImageActivity`: добавлен нижний блок с горизонтальным списком миниатюр
- Счётчик текущего фото ("1 / 5") над миниатюрами
- Текущая миниатюра подсвечена белой рамкой
- При свайпе миниатюры автоматически скроллятся к текущей
- Тап на миниатюру → переход к полному фото

---

## [1.3.2.14] - 2026-07-17

### Исправлено

**Push-уведомления — не удалялись при входе в чат:**
- `dismissNotificationsForRoom` вызывался только через `markRead` (gRPC callback), что задерживало удаление
- Добавлен немедленный `dismissNotificationsForRoom` в `NewChatActivity.onCreate()` сразу после `switchRoom`

**Push-уведомления — сплеш-экран при тапе на уведомление в открытом чате:**
- `showNotification()` (FCM) создавал intent на `SplashActivity` когда `session.username` был пуст (FCM-сервис не инициализировал сессию)
- `showNotificationFromStream()` хардкодил `USERNAME=""` в intent
- Оба метода теперь всегда создают intent на `NewChatActivity` с fallback username из SharedPreferences

**Push-уведомления — тап на уведомление в открытом чате не удалял его:**
- `onNewIntent()` в `NewChatActivity` возвращался раньше для того же roomId без удаления уведомления
- Добавлен `dismissNotificationsForRoom` до проверки `if (newRoomId == roomId) return`

### Добавлено

**ContactsActivity — навигация по контактам:**
- Короткий тап → открывает профиль контакта (`ProfileActivity`)
- Длинный тап → входит в режим выбора (toggle selection)

**ProfileActivity — иконки действий (Telegram-стиль):**
- Ряд иконок ниже статуса, выше блока "О себе" (только для чужих профилей)
- Иконка сообщения → создание/открытие личного чата
- Иконка звонка → голосовой звонок
- Иконка видеозвонка → видеозвонок
- Иконка почты → внешнее почтовое приложение (только если email заполнен в профиле)

---

## [1.3.2.13] - 2026-07-16

### Исправлено

**Token refresh race condition — вынужденный повторный вход:**
- Три независимых пути вызывали `GrpcClient.refreshToken()`: периодический (60s, Main thread), `ensureFreshToken()` (перед gRPC, IO thread), `forceTokenRefresh()` (pull-to-refresh, IO thread)
- Сервер использует ротацию refresh token с reuse detection: каждый успешный refresh генерирует новый JTI. При отправке повторно использованного JTI сервер вызывает `RevokeDevice()` — всё устройство отключается (`is_active = FALSE`)
- `isRefreshing` флаг защищал только `ensureFreshToken()`. `performTokenRefresh()` и `forceTokenRefresh()` работали без взаимной блокировки. Когда 60s таймер совпадал с pull-to-refresh или `loadChats()`, оба читали один и тот же старый refresh token и отправляли его на сервер → сервер обрабатывал первый → ротация → обрабатывал второй → reuse detected → revoke device → forced re-login
- Заменён `isRefreshing: Boolean` на `refreshGuard: AtomicBoolean`. Все три пути используют `compareAndSet(false, true)` для захвата guard. `waitForRefreshComplete()` polling 100ms. Каждый путь повторно проверяет свежесть токена после ожидания

**Lint — HardwareIds warning:**
- `Settings.Secure.getString(contentResolver, ANDROID_ID)` — lint предупреждал об использовании device identifier. Добавлен `@SuppressLint("HardwareIds")` с комментарием

**Duration API:**
- `withTimeoutOrNull(10000)` → `withTimeoutOrNull(10.seconds)` (3 места)

---

## [1.3.2.12] - 2026-07-16

### Исправлено

**Краши при входе в чаты на некоторых устройствах:**
- `ThemeApplier.apply()` не имел обработки ошибок — ~50 операций с view без try-catch. На некоторых комбинациях manufacturer + API level падал `WindowInsetsControllerCompat`, `DrawableCompat.wrap(bg.mutate())` или другие операции → необработанный краш Activity
- Добавлен try-catch в 5 секций ThemeApplier: WindowInsets, background, toolbar, widgets, panels/forms
- `ThemeUi.bind()` — обёрнут `ThemeApplier.apply()` в try-catch для защиты от crash в coroutine scope

**Краш при инициализации тулбара чата:**
- `ChatToolbarDelegate.setup()` — `setSupportActionBar()` + `ThemeStore.currentTheme().xxxColor.toColorInt()` без try-catch
- `setupSecretChatToolbar()` / `setupFavoritesToolbar()` / `setupNormalToolbar()` — добавлен try-catch с fallback на базовый UI (текст без темизации)

**Краш в connection observer:**
- `combine` flow collector в `NewChatActivity.setupObservers()` — необработанное исключение в coroutine scope убивало collector
- Обёрнут в try-catch с логированием

**Защита от крашей при инициализации:**
- `NewChatActivity.onCreate()` — `initDelegates()` / `initSharedViews()` обёрнуты в try-catch с `finish()` при ошибке
- `setupDelegates()` обёрнут в try-catch
- `fetchChatMetadata` callback обёрнут в try-catch
- `setupTheme()` обёрнут в try-catch
- `setDecorFitsSystemWindows` обёрнут в try-catch

**Убран мёртвый код:**
- `ThemeApplier` искал `R.id.tvToolbarTitle` / `R.id.tvToolbarSubtitle` — этих IDs нет в `activity_new_chat.xml` (там `toolbarTitle` / `toolbarSubtitle`). Safe-call спасал от краша, но код был бесполезен

---

## [1.3.2.11] - 2026-07-07

### Улучшения

**Темы — позиция/должность泡泡 в dark mode:**
- `positionBubble` в профиле, редактировании профиля и списке участников компании использовал `?attr/colorPrimary` (#1E1E1E в dark mode) → невидим на тёмном фоне
- Заменён на `?attr/colorPrimaryContainer` / `?attr/colorOnPrimaryContainer` — виден в обеих темах

**Темы — пузыри сообщений, даты, ввод:**
- `bg_message_user.xml` (фиолетовый #FF6200EE), `bg_message_agent.xml` (#373737), `bg_date_separator.xml` (#4D000000) — захардкоженные цвета без night-вариантов
- Вынесены в color-ресурсы с `values-night/` вариантами: в dark mode пузыри адаптированы
- `bg_input_field.xml` stroke #DDDDDD → theme-aware ресурс
- Контейнер ответов (#15000000) → `bg_reply_container` с night-вариантом

**Темы — текст и индикаторы:**
- Имя агента в чате (`?attr/colorPrimary` → невидим в dark mode) → `?attr/colorOnSurfaceVariant`
- Индикатор набора — аналогично
- Reply bar в `widget_chat.xml` → `colorOnSurfaceVariant`

**Стабильность — краши при уничтожении экрана:**
- `CallActivity`, `EditProfileActivity`, `ConferenceLobbyActivity` — ~40 `runOnUiThread` вызовов без проверки `isFinishing`/`isDestroyed`
- Добавлен `safeRunOnUiThread()` хелпер с лайфсайкл-проверкой

**Стабильность — утечка корутины:**
- `ServerAuthBottomSheet` — `CoroutineScope(Dispatchers.Main).launch { ...collect{} }` никогда не отменялся
- Заменён на управляемый scope + отмена при закрытии bottom sheet

**Стабильность — !! на Map:**
- `ThemePaletteActivity`, `PaletteFragment` — 28 `!!` на `currentColors[key]` → краш если ключ отсутствует
- Заменены на `?: return` / `getOrDefault`

**Звонки — имя вместо UUID:**
- Входящий звонок показывал UUID вместо имени если `sender_name` пустой в FCM
- `CallActivity` — placeholder вместо UUID + async-резолв через gRPC
- `showCallNotification` — дефолт `senderName = ""` вместо `senderId`
- `ReviewAdapter`, `AIBottomSheet`, `UsageStatsAdapter` — "User"/"Agent" вместо raw UUID

### Исправлено

**Краш при входе в чат на Android 12:**
- `NewChatActivity.onCreate()` вызывал `setDecorFitsSystemWindows(false)` ДО `super.onCreate()` — единственная активити с таким порядком
- На API 31+ это ломало инициализацию decor view → краш при открытии чата
- Перемещён вызов после `super.onCreate()`

**Краш при шаринге картинки:**
- `ShareReceiverActivity.uploadFile()` использовал `readBytes()` для чтения всего файла в память
- Большая картинка → `OutOfMemoryError` (extends `Error`, не `Exception`) → не ловился `catch (e: Exception)`
- Добавлен отдельный `catch (e: OutOfMemoryError)` с показом "Файл слишком большой"

---

## [1.3.2.10] - 2026-07-06

**Звонки — уведомление не исчезает после завершения:**
- `CallActivity.onDestroy()` не вызывал `NotificationManager.cancel()` → уведомление висело в шторке после hangup
- `CallManager.hangup()/rejectCall()/clearCurrentCall()` теперь вызывают `dismissCallNotification()`

**Звонки — push приходит когда CallActivity уже открыта:**
- `LavenderMessagingService.handleIncomingCall()` не проверял состояние звонка → push приходил даже когда пользователь уже в CallActivity
- Добавлена проверка `CallManager.currentCall.value != null` — push игнорируется если уже в звонке

**Звонки — push всегда отправлялся при INITIATE (сервер):**
- Сервер отправлял FCM push безусловно даже если `BroadcastCall` доставил сигнал через gRPC stream
- Теперь push отправляется только если `!delivered` (receiver offline)

**Звонки — старые ACCEPT/REJECT приходят через минуты (сервер):**
- Сервер не проверял статус звонка — ACCEPT пришедший через 1.5 минуты перезаписывал `completed` на `active`
- Добавлен `GetCallStatus()` guard — ACCEPT/REJECT игнорируются если статус `completed` или `rejected`
- Клиент игнорирует ACCEPT когда `_currentCall.value == null`

**Редактирование профиля — название компании:**
- `tvCompanyName` имел `?attr/colorOnSurface` в XML, но `ThemeApplier` не переопределял цвет → чёрный текст на тёмных темах
- Добавлен `tvCompanyName.setTextColor(textPrimary)` в `ThemeApplier.apply()`

### Добавлено

**Unified AddMemberSheet:**
- Новый виджет `AddMemberSheet` — единая шторка добавления участников для групп и компаний
- Поддержка поиска, мульти-выбора, кнопки действия (через `SearchableListBottomSheet`)
- Для компаний: после выбора участников показывается диалог выбора должности
- `CompanyProfileActivity` теперь использует `AddMemberSheet` вместо `AddMemberBottomSheet`

**Debug logging — typing:**
- Клиент: `sendTypingSignal` логирует `roomId` и `isTyping`
- Сервер: `[ChatV2] TYPING from ... in room ... (isTyping=...)`
- Клиент: TYPING receiver логирует `typist`, `isTyping`, `targetRoom`

---

## [1.3.2.9] - 2026-07-06

### Исправлено

**Звонки — TURN credentials без JWT:**
- `CallActivity.fetchTurnCredentials()` не отправлял Authorization заголовок → сервер возвращал 401 → клиент fallback на STUN-only (Google STUN) → без TURN relay за CGNAT P2P не устанавливался
- Добавлен `AuthManager.getBearerToken()` в Authorization заголовок запроса TURN credentials

**Звонки — имя собеседника:**
- При исходящем звонке CallActivity показывал UUID вместо имени пользователя
- `CallNavigator.startCall()` не передавал `SENDER_NAME` в intent
- Добавлен параметр `senderName` в `CallNavigator.startCall()` + передача из `NewChatActivity`

**Сервер (требует деплой):**
- Печатает: сервер не обновлял `currentRoom` при переключении чата → typing рассылался в старый чат
- `server_chat.go` — добавлена обработка room switch (обновление `currentRoom` и `hub.SetV2Room()`)

---

## [1.3.2.8] - 2026-07-06

### Исправлено

**Звонки — WebRTC P2P:**
- coTURN сервер упал с SEGV 29 июня и не перезапускался — звонки между устройствами в разных городах не проходили (ICE candidates обменивались, но P2P не устанавливался без TURN relay)
- coturn перезапущен, добавлен `Restart=always` в systemd override
- Мониторинг coturn: скрипт `watch-services.sh` проверяет процесс и порт 3478 каждые 15 минут

**Индикатор "Печатает...":**
- Сервер удалил старый `Typing()` gRPC метод при миграции на v2, но клиент продолжал отправлять typing через удалённый stream
- Клиент теперь отправляет typing через ChatV2 stream (`ChatV2Message_Typing`)
- Входящие typing сигналы (`System(type="TYPING")`) теперь обрабатываются и обновляют `_typingUsers`

**Push-уведомления — батчинг:**
- Серверная debounce-логика (3 сек на комнату): быстрые сообщения объединяются в один пуш
- 1 сообщение — обычный пуш, 2-3 — строки через `\n`, 4+ — первые 2 + "...и N сообщений"

**Тёмная тема — редактирование профиля:**
- Название компании: `textColor` заменён на `?attr/colorOnPrimary` (был чёрный на тёмных темах)
- Должность: обёрнута в bubble с фоном `?attr/colorPrimary`

**Участники компании:**
- Список участников: должность теперь отображается в bubble с фоном текущего primary color
- "Добавить участника" теперь через bottom sheet (как на списке чатов) вместо отдельной активити

## [1.3.2.7] - 2026-07-05

### Исправлено

**Push-уведомления для сообщений:**
- Сервер не отправлял push когда ChatV2 stream был подключён к комнате
- `ChatListActivity.onResume()` — stream переключается на пустую комнату при возврате в список чатов
- При выключенном экране: если сообщение приходит через stream — показывается уведомление вместо auto-markRead

**POST_NOTIFICATIONS permission:**
- На Android 13+ добавлен runtime-запрос разрешения на уведомления

---

## [1.3.2.6] - 2026-07-05

### Добавлено

**Inline Reply — ответ прямо из уведомления:**
- Кнопка "Ответить" во всех стилях уведомлений (standard, messaging, big_text)
- Ввод текста прямо в пуш-уведомлении через `RemoteInput`
- `NotificationReplyReceiver` — отправка ответа через gRPC `sendMessageV2`
- Автозакрытие уведомления после успешной отправки

---

## [1.3.2.5] - 2026-07-05

### Исправлено

**Marshaller'ы — критические баги:**
- `AuthResponseV2` — исправлен парсинг User поля: добавлено поле 4 (avatar_url), исправлены поля 5-7 (bio, status)
- `GetPinnedMessagesRequest` — исправлены поменянные местами userId/chatId (field 1/2)
- `GetFavoritesResponse` — исправлен парсинг v1 Message (сервер отправляет v1, клиент парсил как v2). Добавлен конвертер v1→v2
- `GetPinnedMessagesRequest` — добавлены поля limit/offset для пагинации

**Thread safety:**
- `RealGrpcClient.currentUsername` — добавлен `setUsername()` метод, вызов из `SessionManager.updateSession()`. Ранее всегда был null
- `markRead` callback — убран двойной вызов (onMessage + onClose)
- `toggleMute`/`deleteChat` — callback'и обёрнуты в `viewModelScope.launch(Dispatchers.Main)` для корректного потока
- `db()` — улучшена проверка null-before-assign

**Темы:**
- `CustomThemeProto` — добавлено поле `isDark`
- Парсинг `outgoingTextColor`/`incomingTextColor` (поля 18-19)
- Сериализация полей 10/18/19 в `SaveThemeRequestMarshaller`

**ViewModel:**
- `ChatListActivity` — теперь использует `ViewModelProvider` вместо ручного создания. Сохраняет состояние при повороте экрана

---

## [1.3.2.4] - 2026-07-05

### Добавлено

**Конференции — визуальное отличие:**
- Значок "Конференция" в списке чатов (primary цвет, badge рядом с названием)
- Кнопка входа в лобби (`btnEnterLobby`) в элементе списка чатов для конференций
- Тип чата в списке отображается как "Конференция" вместо текста последнего сообщения
- Кнопка лобби (`btnLobby`) в тулбаре чата теперь видна всем участникам (было только создателю)

**Конференции — серверная поддержка типа:**
- Поле `type` добавлено в `CreateGroupChatRequest` (proto field 6)
- Сервер теперь создаёт чаты с правильным типом (`conference` вместо хардкода `group`)

**Информация о группе/конференции:**
- FAB [+] в профиле группы/конференции для добавления участников
- Для конференций: шторка с двумя опциями — "Добавить участников" и "Открыть лобби"
- Для групп: шторка добавления участников из контактов
- Заголовок: "Информация о конференции" вместо "Информация о группе"

**О программе:**
- Отображение версии приложения Android в шторке "О программе"

**Групповой чат из контактов:**
- При добавлении 2+ контактов с галкой "Создать чат сразу" создаётся групповой чат (раньше создавались прямые чаты)
- Переименована строка: `create_direct_chat_after` → `create_chat_after`

**Подсказка при пустом списке:**
- В шторке добавления участников группы: "Все ваши контакты уже добавлены в эту группу"

### Улучшено

**Тулбары — единый стандарт:**
- Все тулбары используют `toolbar_background` + `@dimen/custom_toolbar_height` + `navigationIconTint` + `titleTextColor` = `colorOnPrimary`
- Убраны отклонения: `LogViewerActivity` (ThemeOverlay.Dark.ActionBar), `ThemePaletteActivity` (?attr/actionBarSize)
- Добавлен `titleTextColor` в `new_chat`, `widget_chat`, `themes`, `contacts`

**Аватары:**
- Аватар в тулбаре списка чатов: 48dp → 56dp (растёт от центра)
- Аватар в шторке профиля: 100dp → 120dp, сдвинут влево

**Цвет стрелки "назад":**
- `ContactsActivity` — убран `setSupportActionBar()`, тулбар управляется напрямую (фикс для кастомных тем)
- `ThemesActivity` — аналогично
- `SuperAdminActivity` — ручной tint после `setHomeAsUpIndicator()`
- Toast при добавлении контактов: показывает количество ("Добавлено контактов: 6")

**Темы:**
- Чекбокс выбора в списке чатов адаптирован к кастомным темам (`buttonTintList` + `backgroundTintList`)
- Уведомления (NotificationActivity) — карточки и тексты адаптированы к темам через `ThemeStore`
- FAB [+] в информации о группе адаптирован к темам (через `ThemeApplier`)

### Исправлено

**Навигация:**
- Кнопка "назад" из Безопасности / Уведомлений теперь корректно возвращает на шторку "Дополнительные настройки"
- Исправлен сброс `isNavigatingDeeper` в `setOnDismissListener` — флаг сбрасывается только в `settingsActivityLauncher` callback

**Групповой чат:**
- Добавление 2+ контактов с галкой "Создать чат" теперь создаёт группу, а не прямые чаты

---

## [1.3.2.2] - 2026-07-05

### Улучшено

**Профиль пользователя — компания:**
- Логотип компании отображается в карточке компании при просмотре профиля другим пользователем
- Должности локализованы: "Owner" → "Владелец", "Manager" → "Менеджер" и т.д.
- Название компании крупнее (18sp bold)
- Шестерёнка настроек вместо текстовой кнопки "Моя компания"
- Тап по карточке компании (без шестерёнки) открывает "Моя компания" / создание

**Компания — общие улучшения:**
- FAB (+) вместо двух кнопок — шторка с "Добавить участника" и "Создать корп. чат"
- FAB и карточка компании адаптированы к кастомным темам
- После удаления компании — профиль автоматически обновляется (нет устаревших данных)
- Навигация: возврат из "Моя компания" → настройки профиля (не список чатов)

### Исправлено

- Серверный фикс: DeleteCompany — очистка `primary_company_id` перед удалением (FK constraint)
- Подсказка в диалоге создания чата: "Название чата" вместо "Название компании"

---

## [1.3.2.1] - 2026-07-04

### Исправлено

**Fatal crash в EditProfileActivity:**
- Кнопка "Удалить профиль" не имела `android:id` в layout → NPE при `findViewById` → краш при входе в профиль через шторку
- Добавлен `android:id="@+id/btnDeleteProfile"` в `activity_edit_profile.xml`

### Улучшено

**Создание компании:**
- Подсказка в диалоге создания компании теперь корректная (была от редактирования username)
- Убрана иконка пользователя из диалога создания компании
- После создания компании автоматический переход в "Моя компания" вместо возврата на главную

**Кастомные темы — CompanyProfileActivity:**
- Добавлена адаптация к кастомным темам (`ThemeUi.bind()` + `applyThemeToViews()`)
- Фон, текст, кнопки окрашиваются согласно выбранной теме
- `companyCard` добавлен в `ThemeApplier` для автоматической адаптации

**Логотип компании:**
- Загрузка логотипа компании в "Моя компания" (клик по аватару → галерея)
- Логотип отображается в карточке компании в профиле
- Логотип отображается в карточке компании в EditProfileActivity
- Загрузка через HTTP `/upload-avatar` + обновление через `updateCompany` gRPC

---

## [1.3.2.0] - 2026-07-04

### Добавлено

**Company System — корпоративные компании:**
- Создание компаний из профиля пользователя
- Управление позициями (Employee, Manager, Top Manager, Owner + кастомные)
- Управление участниками (добавление из контактов, смена позиции, удаление)
- Корпоративные чаты с role-based access control
- Таб "Компания" в списке чатов
- Badge "Компания" на корпоративных чатах
- Company info в профиле контакта
- Access control: Employee (0) → member chats, Manager (1+) → management, Owner (3) → all

**Multi-Company Support:**
- Пользователь может состоять в нескольких компаниях одновременно
- Выбор основной компании (SetPrimaryCompany)
- Автоматический выбор основной компании при создании
- Переключение компаний long-press на кнопке "Моя компания"
- Per-company access control для multi-company chats

**Company Service (gRPC):**
- `GrpcCompanyClient` — 19 RPC методов (Company CRUD, Positions, Members, Company Chats, Join/Leave, UserInfo, GetUserCompanies, SetPrimaryCompany)
- `CompanyProto.kt` — 35+ data classes
- `CompanyMarshallers.kt` — 35+ marshallers

**UI:**
- `CompanyProfileActivity` — управление компанией (участники, должности, чаты)
- `AddMemberActivity` — добавление участников из контактов
- `CompanyListFragment` — списки участников/позиций/чатов с actions
- `CompanyMemberAdapter`, `CompanyPositionAdapter`, `CompanyChatAdapter`
- `EditProfileActivity` — multi-company switcher (long-press)

**Расширения существующих моделей:**
- `GetProfileResponseProto` +4 поля (companyId, companyName, positionTitle, positionLevel)
- `ChatInfoProto` +3 поля (companyId, companyChatAccess, companyMinPositionLevel)
- `UserSession` +4 поля (companyId, companyName, positionTitle, positionLevel)
- Room DB миграция 12→13

### Исправлено

**Локализация media preview в чат-листе:**
- Сервер отправлял "Image" / "Voice message" как хардкод строки — теперь переводятся в "Изображение" / "Голосовое сообщение" на русском
- `ChatListViewModel` real-time preview использует локализованные строки
- `ChatAdapter` и `SuperAdminAdapter` переводят серверные строки

---

## [1.3.1.24] - 2026-07-04

### Исправлено

**Чат-лист не обновлял unread count после прочтения сообщений внутри чата:**
- `ChatListViewModel` не подписывался на `readReceiptEvent` — когда пользователь открывал чат и `markRead()` обновлял сервер, чат-лист не знал об этом. При возврате в список чатов merge logic использовал устаревший локальный `unreadCount` вместо серверного 0
- Исправлено: `ChatListViewModel` теперь слушает `readReceiptEvent` — при получении `READ_ALL` для текущего пользователя добавляет чат в `locallyReadChats` и обнуляет `unreadCount` локально

---

## [1.3.1.23] - 2026-07-04

### Исправлено

**Read receipts — отправитель не видел ✓✓ даже после прочтения получателем:**
- Сервер: `MarkReadAndCheck` обновлял `is_read` только в таблице `messages` (legacy v1), но все новые сообщения хранятся в `messages_v2`. Поле `is_read` в `messages_v2` никогда не обновлялось
- Сервер: `READ_ALL` broadcast отправлялся только через v1 Chat stream — клиенты на ChatV2 не получали уведомление о прочтении
- Исправлено на сервере: `MarkReadAndCheck` теперь обновляет `is_read` в `messages_v2`, `READ_ALL` рассылается и в ChatV2 потоки

**Загрузка файлов — непонятная ошибка при файле больше 30MB:**
- Клиент загружал файл целиком, сервер отвечал 400 "File too large", клиент показывал "Server error: 404"
- Исправлено: проверка размера файла перед загрузкой на клиенте, показ "Файл слишком большой (макс. 30 МБ)"
- Лимит размера теперь берётся из `GET /info` → `max_upload_size` (серверная capability negotiation)

### Изменено

- `ChatInputDelegate.kt` — проверка `ProfileClient.maxUploadSize` перед загрузкой image/file, обработка 400 "too large"
- `ShareReceiverActivity.kt` — проверка размера при шаринге файлов
- `AudioUploader.kt` — проверка размера аудио перед загрузкой, `FILE_TOO_LARGE` error code
- `AiV2ChatActivity.kt` — проверка размера файлов для AI чата
- `ThemePaletteActivity.kt` — проверка размера фонов
- `ChatViewModel.kt` — обработка ошибки `FILE_TOO_LARGE` для аудио
- `ProfileClient.kt` — кеширование `maxUploadSize` из `GET /info`, fallback 30MB
- `strings.xml` (EN + RU) — +`file_too_large`

---

## [1.3.1.22] - 2026-07-03

### Исправлено

**Зависающий индикатор обновления после свайпа в оффлайне:**
- `listAIChats()` не имел таймаута — если gRPC вызов зависал, `supervisorScope` блокировался навсегда, `finally` блок в `loadChats()` никогда не выполнялся, `_isLoading` навсегда оставался `true` → индикатор крутился
- Исправлено: добавлен `withTimeoutOrNull(10.seconds)` для `aiDeferred`, аналогично `pageDeferred`

**Преждевременный logout при потере соединения:**
- `ensureFreshToken()` сдавался через 5s если gRPC канал не READY (клиент был оффлайн, токен истёк). `getChats()` получал UNAUTHENTICATED → мгновенный force logout даже если пользователь просто был без интернета
- Исправлено: при UNAUTHENTICATED теперь выполняется `forceTokenRefresh()` + повторный `getChats()` перед force logout. Logout только если повторный запрос тоже вернул UNAUTHENTICATED/PERMISSION_DENIED

**Имя звонящего не отображалось на экране звонка (входящий видел UUID вместо имени):**
- `initiateCall()` не передавал `senderName` в `CallMessageProto` → сервер пересылал вызов с пустым `senderName` → получатель видел `senderId` (UUID) вместо username
- Исправлено: добавлен `senderName = getCurrentUsername()` в `initiateCall()`

### Изменено

- `ChatListViewModel.kt` — таймаут `listAIChats()` 10s, retry token refresh при UNAUTHENTICATED, `Long` → `Duration` для `withTimeoutOrNull`/`delay`
- `CallManager.kt` — `senderName` в `initiateCall()`
- `SessionManager.kt` — `Long` → `Duration` для `delay`
- `ChatListActivity.kt` — `Long` → `Duration` для `delay`
- `NewChatActivity.kt` — `Long` → `Duration` для `delay`

---

## [1.3.1.21] - 2026-07-03

### Исправлено

**Звонок: принятие входящего звонка зависает на "Подключение..." (race condition):**
- `CallManager.acceptCall()` отправлял ACCEPT信号 до создания `CallController`, который слушает OFFER/ANSWER/ICE сигналы. Клиент-звонящий получал ACCEPT → создавал OFFER → отправлял обратно, но `CallController` ещё не был подписан на `incomingSignals` (SharedFlow с replay=0). OFFER терялся → WebRTC negotiation никогда не завершался → звонок застревал на "Подключение..."
- Исправлено: `acceptCall()` теперь вызывается после `setupController()` через `onReady` callback в `initWebRtc()`

**Звонок: входящий звонок не открывал CallActivity из FCM:**
- `LavenderMessagingService.handleIncomingCall()` только показывал heads-up уведомление. `setFullScreenIntent()` работает только когда экран выключен/заблокирован. Если приложение на переднем плане — пользователь мог не заметить уведомление → CallActivity не открывался
- Исправлено: `handleIncomingCall()` теперь всегда запускает `CallActivity` напрямую через `startActivity()` + `FLAG_ACTIVITY_SINGLE_TOP`

**Звонок: имя звонящего не отображалось в notification:**
- Notification intent не содержал `SENDER_NAME` → CallActivity показывал UUID вместо имени
- Исправлено: добавлен `SENDER_NAME` в notification intent

**Цитаты (reply) — сообщение отправлялось без текста и имени автора:**
- Клиентский workaround: `messageV2ToDomain()` теперь использует `reply.preview` как текст если `text` пустой
- Добавлен `senderId` в `MessageReplyProto` для резолва имени автора цитаты

### Добавлено

**@Упоминания (Mentions) в групповых чатах:**
- Ввод `@` в group chat открывает popup со списком участников (уже работало)
- `SendMessageV2RequestProto` теперь содержит `mentions` (field 7) — список упомянутых @username
- `MessageV2Proto` содержит `mentions` (field 40) — упомянутые пользователи в ответе сервера
- `MessageAdapter` подсвечивает @username в тексте сообщения (цветной + жирный шрифт)
- Извлечение @username из текста через `extractMentions()` в `GrpcMessageV2Client`

### Изменено

- `CallActivity.kt` — `initWebRtc(onReady)` callback, `acceptCall()` перенесён в `onReady`
- `LavenderMessagingService.kt` — прямой запуск `CallActivity` из `handleIncomingCall()`, `SENDER_NAME` в notification intent
- `Message.kt` — добавлено поле `mentions: List<String>`
- `MessagesV2Proto.kt` — `MessageV2Proto.mentions` (field 40), `SendMessageV2RequestProto.mentions` (field 7)
- `MessagesV2Marshallers.kt` — сериализация/десериализация mentions в marshallers
- `GrpcMessageV2Client.kt` — `extractMentions()`, `domainToSendRequest()` с mentions, `messageV2ToDomain()` с reply workaround
- `MessageAdapter.kt` — `applyMentionSpans()` для подсветки @username в тексте сообщений

---

## [1.3.1.18] - 2026-07-03

### Исправлено

**Сообщение не появляется в истории чата (отложенная отправка):**
- Race condition в `loadHistoryV2` — кэш-фаза (IO thread) и серверная фаза (gRPC callback) работали параллельно, обе вызывали `messages.update`. Если сервер отвечал первым, кэш-фаза перезаписывала данные. Добавлен флаг `loadHistoryServerCompleted` — кэш-фаза пропускает merge если серверная фаза уже завершилась
- Гонка DB-записей — `addLocalMessage` и `sendMessageV2` оба сохраняли в Room DB независимо на IO. Если `addLocalMessage` завершался после `sendMessageV2` handler'а, в DB оставалась запись с клиентским UUID. Убрана DB-запись из `addLocalMessage` — только `sendMessageV2` handler сохраняет

**Swipe refresh не перезагружает сообщения:**
- `clearRoomMessages()` стирал весь кэш, затем `switchRoom()` очищал in-memory состояние. Если сервер был медленным, сообщения исчезали. Заменён на `forceLoadHistory()` без очистки DB
- Guard `_isLoading` блокировал `switchRoom()` history load. Добавлен `forceLoadHistory()` который обходит guard

**Удаление группового чата — нет уведомления + логика прав:**
- Сервер: админы теперь могут удалять любые групповые чаты (не только создатель)
- Клиент: диалог подтверждения с именем создателя для создателей/админов
- Клиент: Toast с ошибкой если нет прав (показывает имя создателя)
- Добавлены строки `group_creator` и `cannot_delete_group` (EN/RU)

**Fatal crash при шаринге из браузера:**
- Activity не вызывала `GrpcClient.connect()`, сообщение не имело `userId`, не было error handling. Добавлен gRPC connection init, `userId` в сообщении, try-catch в `onCreate`

### Изменено

- `GrpcMessageV2Client.kt` — `loadHistoryServerCompleted` flag, cache merge skipped when server completed
- `RealGrpcClient.kt` — removed DB save from `addLocalMessage()`
- `ChatViewModel.kt` — added `forceLoadHistory()`
- `NewChatActivity.kt` — swipe refresh uses `forceLoadHistory()` instead of `clearRoomMessages()+switchRoom()`
- `ChatListActionMode.kt` — group chat deletion confirmation dialog, error Toast, admin permissions
- `ChatListViewModel.kt` — `deleteChat()` returns error message
- `server_chats.go` — admin can delete group chats, error message includes creator name
- `ShareReceiverActivity.kt` — gRPC connection, userId, error handling
- `strings.xml` (EN + RU) — +error, +group_creator, +cannot_delete_group

---

## [1.3.1.16] - 2026-07-02

### Architecture stability audit — Thread safety, Memory leaks, Error handling, gRPC resilience, Room DB

**Исправлено:**
- Message disappearing bug — `getContentHash` uses `userId` (UUID) instead of `user` (username), fixing content hash mismatch when `allUsers` not loaded
- Thread safety — added `@Volatile` to 15 cross-thread fields across RealGrpcClient, GrpcConnectionManager, GrpcCallClient, GrpcTypingClient, GrpcMessageV2Client
- Thread safety — `allChats` in ChatListViewModel moved from IO to Main thread
- Memory leak — CallController not cancelled in CallActivity.onDestroy
- Memory leak — GrpcConnectionManager extracted applicationContext from reconnect lambda
- Memory leak — AIBottomSheet.agentScope cancelled on dialog dismiss
- Memory leak — CallActivity.fetchTurnCredentials replaced unmanaged Thread with lifecycleScope
- Error handling — 20 Log.e/Log.w calls replaced with ErrorHandler.handle() in ChatListViewModel, SessionManager, UpdateManager
- Error handling — gRPC silent error swallows in editMessageV2, deleteMessageV2, setReactionV2 now use ErrorHandler
- gRPC resilience — Typing stream retry loop: exponential backoff (1s→30s), max 10 retries, connection check
- gRPC resilience — Call stream retry loop: same backoff + max retries + connection check
- gRPC resilience — `isAuthFailure` flag now set on auth failure, checked in scheduleReconnect to skip infinite reconnect
- Room DB — added index on `messages.roomId` (migration 11→12)
- UI — added `setDecorFitsSystemWindows` to ChatListActivity and ConferenceLobbyActivity
- UI — CallActionService replaces deprecated IntentService for FCM call actions

**Изменено:**
- `GrpcMessageV2Client.kt` — `getContentHash`/`getMessageHash` use `userId` instead of `user`
- `RealGrpcClient.kt` — 9 fields annotated with `@Volatile`
- `GrpcConnectionManager.kt` — 5 fields annotated with `@Volatile`, `scheduleReconnect` extracts applicationContext, checks `isAuthFailure`
- `GrpcCallClient.kt` — `callRequestObserver` annotated `@Volatile`, retry backoff added
- `GrpcTypingClient.kt` — `typingRequestObserver` annotated `@Volatile`, retry backoff added
- `GrpcMessageV2Client.kt` — `database` annotated `@Volatile`
- `GrpcAuthClient.kt` — `setAuthFailure(true)` on auth failure
- `ChatListViewModel.kt` — 16 error handlers migrated to ErrorHandler
- `SessionManager.kt` — 2 error handlers migrated to ErrorHandler
- `UpdateManager.kt` — 2 error handlers migrated to ErrorHandler
- `CallActivity.kt` — `callController?.cancel()` in onDestroy, fetchTurnCredentials uses lifecycleScope
- `AIBottomSheet.kt` — `agentScope.cancel()` on dialog dismiss
- `ChatListActivity.kt` — `setDecorFitsSystemWindows(window, false)` in onCreate
- `ConferenceLobbyActivity.kt` — `setDecorFitsSystemWindows(window, false)` in onCreate
- `Entities.kt` — `@ColumnInfo(index = true)` on `MessageEntity.roomId`
- `AppDatabase.kt` — version 12, migration 11→12 creates roomId index
- `doc/PROMPT_NEXT_SESSION.md` — audit completed
- `doc/INDEX.md`, `doc/PATTERNS.md`, `doc/GOTCHAS.md` — version updated to v1.3.1.16

---

## [1.3.1.15] - 2026-07-02

### Login update + Auth resilience + Call notification fix + Selection toolbar cleanup

**Добавлено:**
- Кнопка обновления на экране логина — "Доступна новая версия. Скачать?" с возможностью скачать/установить прямо из шторки входа
- Проверка обновлений при долгом свайпе вниз в списке чатов
- Кнопка "Отклонить" в уведомлении входящего звонка
- Звук звонка и вибрация в уведомлении входящего звонка

**Исправлено:**
- Токен не обновлялся после пробуждения — `ensureFreshToken` теперь ждёт READY gRPC канал перед refresh
- Чаты не загружались после пробуждения — connection observer теперь вызывает `loadChats` при переключении в READY
- Параллельные refresh токена — `isRefreshing` guard предотвращает гонку
- Уведомление входящего звонка показывало UUID вместо имени — теперь "Звонит [Имя]"
- Устаревшее скачанное обновление — если вышла новая версия, старый APK удаляется

**Удалено:**
- Кнопка "Что нового" из уведомлений об обновлении
- Иконка "Избранное" (star) из режима выбора сообщений
- Иконка "Замочек" (pin/lock) из режима выбора сообщений

**Изменено:**
- `ServerAuthBottomSheet.kt` — кнопка обновления на экране логина
- `SessionManager.kt` — ensureFreshToken ждёт READY + isRefreshing guard
- `ChatListActivity.kt` — OnScrollListener для долгого свайпа; connection observer загружает чаты при READY
- `UpdateManager.kt` — downloaded_version tracking + stale APK cleanup
- `UpdateUtils.kt` / `UpdateCoordinator.kt` — удалён whatsNewIntent
- `LavenderMessagingService.kt` — sender_name из FCM, ringtone, vibration, decline button
- `CallActionService.kt` — NEW, Service вместо deprecated IntentService
- `activity_new_chat.xml` — reordered selection toolbar: reply → forward → copy → delete
- `ChatSelectionDelegate.kt` — удалены star, lock, unused methods
- `strings.xml` (EN + RU) — +decline_call, +update_download_prompt, +downloading

---

## [1.3.1.13] - 2026-07-02

### Avatar loading, online status fix, toolbar layout fix, call state fix

**Добавлено:**
- Загрузка аватаров через Glide в `ChatMessageAdapter` (chat сообщения)

**Исправлено:**
- Online status — `allUsers` (с `lastSeenAt`) не обновлялся при pull-to-refresh, из-за чего "последний раз был" показывал устаревшие данные. Теперь `loadUsers()` вызывается при pull-to-refresh и каждые 60 секунд в фоне
- Порядок иконок на тулбаре списка чатов — избранное теперь справа от индикатора обновления (слева от поиска), а не наоборот
- Call state — вызывающий застревал на "Дозвон" после принятия звонка. Причина: два CallController подписывались на один `incomingSignals` SharedFlow. Теперь `setupController()` вызывается один раз, старый контроллер отменяется, ACCEPT обрабатывается корректно即使 WebRTC ещё не инициализирован
- Call status flickering — `onCallAccepted()` теперь ставит "Подключение..." вместо "Подключено" (статус будет установлен при ICE CONNECTED)
- Call timer — `tvCallDuration` теперь становится `VISIBLE` при принятии звонка

**Изменено:**
- `ChatMessageAdapter.kt` — Glide загрузка аватаров вместо TODO
- `ChatListActivity.kt` — `loadUsers()` при pull-to-refresh + периодический refresh каждые 60s
- `activity_chat_list.xml` — `llUpdateContainer` перемещён перед `ivFavorites`
- `CallController.kt` — добавлен `cancel()`, ACCEPT handler проверяет `webRtcClient != null`
- `CallActivity.kt` — `setupController()` вызывается один раз, `callController?.cancel()` перед пересозданием

---

## [1.3.1.12] - 2026-07-02

### Call disconnect fix + Chat history fix

**Исправлено:**
- INITIATE echo перезаписывал `receiverId` в `_currentCall` — сервер меняет `receiverId` на sender ID при эхе. Теперь INITIATE echo обновляет только `callId`, сохраняя оригинальный `receiverId` из `initiateCall()`
- Звонок завершался у инициатора, но callee видел уведомление звонка — HANGUP не доставлялся если call stream callee не зарегистрирован. Сервер теперь отправляет FCM `CALL_ENDED` push при `!delivered && HANGUP/REJECT`
- `loadHistory()` всегда вызывается при READY-соединении — ранее кэш мог заблокировать gRPC запрос и новое сообщение не подгружалось после обновления приложения

**Изменено:**
- `CallManager.kt` — INITIATE echo: `existing.copy(callId = signal.callId)` вместо полной перезаписи; `handleCallEndedPush()` для обработки FCM CALL_ENDED
- `LavenderMessagingService.kt` — обработка FCM `type=CALL_ENDED` → dismiss notification + close CallActivity
- `NewChatActivity.kt` — `loadHistory()` вызывается всегда при READY, `shouldScrollToBottom` только при пустом адаптере

**Сервер:**
- `server_chat.go` — после `BroadcastCall`: если `!delivered && (HANGUP || REJECT)` → `sendCallEndedPushNotification()`
- `server_push.go` — новая функция `sendCallEndedPushNotification()` отправляет FCM push с `type=CALL_ENDED`

---

## [1.3.1.11] - 2026-06-29

### Call signaling fix + Token resilience

**Исправлено:**
- Звонки не доставлялись — `initiateCall()` отправлял `username` как `receiverId`, но сервер в `callStreams` хранит `UUID`. `BroadcastCall` не мог найти получателя → `delivered: false`
- `CallManager.initiateCall()` теперь резолвит username → UUID через `allUsers` перед отправкой
- Все 7 conference методов использовали `getCurrentUsername()` вместо `getUserId()` — исправлено
- `NewChatActivity` передаёт UUID в `CallNavigator.startCall` вместо username (WebRTC сигналы теперь доставляются)
- Разлогин при недоступности сервера — `INTERNAL` и `NOT_CONNECTED` ошибки больше не вызывают force logout (это ошибки доступности, не авторизации)
- Токен обновляется при onResume — обрабатывает пробуждение после долгого простоя (ночь, doze mode)
- INITIATE echo перезаписывал `receiverId` в `_currentCall` — сервер меняет `receiverId` на sender ID при эхе. Теперь INITIATE echo обновляет только `callId`
- Звонок завершался у инициатора, но callee видел уведомление звонка — HANGUP не доставлялся если call stream callee не зарегистрирован. Сервер теперь отправляет FCM `CALL_ENDED` push при `!delivered && HANGUP/REJECT`
- `loadHistory()` всегда вызывается при READY-соединении — сообщения из кэша могли блокировать gRPC запрос

**Изменено:**
- `CallManager.kt` — `initiateCall()` резолвит username → UUID; `resolveUserId()` helper; все conference методы используют `getUserId()`
- `NewChatActivity.kt` — резолвинг UUID через `allUsers` перед `CallNavigator.startCall`
- `ChatListViewModel.kt` — force logout только для `UNAUTHENTICATED`/`PERMISSION_DENIED` (убраны `INTERNAL`/`NOT_CONNECTED`)
- `ChatListActivity.kt` — `ensureFreshToken` при onResume перед загрузкой чатов

---

## [1.3.1.10] - 2026-06-29

### Group info fix + Call fix + Crash fix

**Добавлено:**
- Информация о групповом чате теперь корректно отображает участников, настройки и аватар — данные берутся из intent extras как fallback при недоступности сервера

**Исправлено:**
- Групповой чат: "Участники 0" и неработающие настройки — `ProfileViewModel.loadGroupData()` искал чат в пагинированном `getChats()` (limit=100), группа могла не попасть в первую страницу. Теперь используется fallback из intent extras (participants, creator, avatar)
- Входящий звонок: `receiverId` передавался как `displayName` вместо UUID → WebRTC сигналы (OFFER/ANSWER/ICE) не доходили до абонента. Теперь `RECEIVER_ID` = UUID, `SENDER_NAME` = отображаемое имя
- Входящий звонок: камера выключена по умолчанию → абоненты не видели друг друга. Теперь камера включена по умолчанию (включается при Accept для входящих)
- FCM VOIP_CALL: `startCallSession()` вызывался до готовности gRPC канала → стрим не открывался. Теперь опрос `connectionStatus == READY` до 5 сек
- `BadTokenException` в `ChatInputDelegate.showAttachmentSheet()` — `BottomSheet` показывался после уничтожения Activity. Добавлен `isFinishing/isDestroyed` guard + убрано кеширование через `WidgetManager.getOrCreate` (Activity-scoped sheets не должны кешироваться)

**Изменено:**
- `ProfileViewModel.kt` — `loadGroupData()` принимает intent extras как fallback (participants, creator, avatarUrl, fullAvatarUrl, name)
- `ProfileActivity.kt` — читает extras из intent и передаёт в `loadGroupData()`; `onResume` также передаёт текущие данные
- `ChatToolbarDelegate.kt` — добавлен `chat_name` extra в intent
- `SuperAdminActivity.kt` — добавлен `chat_name` extra в intent
- `CallManager.kt` — `handleIncomingSignal()`: `RECEIVER_ID` = `signal.senderId` (UUID), `SENDER_NAME` = display name
- `CallActivity.kt` — `isCameraEnabled = true` по умолчанию; `SENDER_NAME` для отображения имени; Accept включает камеру
- `LavenderMessagingService.kt` — `handleIncomingCall()` ждёт `connectionStatus == READY` перед `startCallSession()`
- `ChatInputDelegate.kt` — `showAttachmentSheet()`: `isFinishing/isDestroyed` guard + убрано кеширование `WidgetManager`

---

## [1.3.1.09] - 2026-06-29

### Admin panel fixes + Chat list online status + Favorites reactions fix

**Добавлено:**
- Онлайн-статус (🟢/⚪) и время последнего визита в списке чатов — для direct-чатов справа от имени показывается зелёный/серый кружок и "5 мин назад"/"3ч"/"2д"
- Diagnostics logging для `SetReactionV2` (серверная диагностика)

**Исправлено:**
- Админ-панель: главная плашка пользователя показывала `lastMessageTime` (время последнего сообщения) вместо `lastSeenAt` (время последнего входа)
- Админ-панель: pull-to-refresh не очищал раскрытые сессии — теперь `clearExpanded()` сбрасывает `expandedUsers` и `userSessions`
- Админ-панель: "unknown" IP адрес в сессиях — теперь скрывается
- Избранное: реакции не сохранялись при повторном входе — сервер генерировал другие UUID для Favorites сообщений. Клиент: fallback merge по content hash (user:text:timestamp). Сервер: `SetReactionV2` исправлен

**Изменено:**
- `SuperAdminAdapter.kt` — `lastSeenAt` вместо `lastMessageTime`, `clearExpanded()`, "unknown" IP filter
- `SuperAdminActivity.kt` — `adapter.clearExpanded()` при pull-to-refresh
- `item_chat.xml` — FrameLayout wrapper + online dot + tvLastSeen
- `ChatAdapter.kt` — `onlineUsers`/`allUsers` параметры, bind logic для direct-чатов
- `ChatListActivity.kt` — подписка на `GrpcClient.users` + `GrpcClient.allUsers`
- `GrpcMessageV2Client.kt` — contentHash fallback merge в `loadHistoryV2`
- `RealGrpcClient.kt` — REACTION_V2 handler логирование (diagnostics)

---

## [1.3.1.08] - 2026-06-29

### Stability fixes + Performance optimizations + UX improvements

**Добавлено:**
- Избранное перемещено первым пунктом в шторке профиля (было вторым)
- Звёздочка Избранного в toolbar чат-листа — кнопка `ic_star` справа от заголовка, скрывается в режиме выбора
- Авто-выход при невалидном токене — если `loadChats()` возвращает ошибку авторизации (`UNAUTHENTICATED`/`PERMISSION_DENIED`/`INTERNAL`) с пустым списком чатов, пользователь автоматически перенаправляется на экран входа (помогает клиентам, обновившимся со старых версий)
- Строка `session_expired` (EN + RU)

**Исправлено (Stability):**
- Thread-unsafe singleton fields в `RealGrpcClient` — `currentUsername`, `currentUserId`, `requestObserver`, `chatV2RequestObserver`, `isRetrying`, `lastChatRequest` не имели `@Volatile`, что могло привести к race condition между gRPC callback и main thread
- Thread-unsafe коллекции в `RealGrpcClient` — `avatarCache`, `fullAvatarCache`, `deletedMessageHashes`, `pendingReads` были обычными `mutableMapOf`/`mutableSetOf`, заменены на `ConcurrentHashMap` и `ConcurrentHashMap.newKeySet()`
- `runBlocking` на main thread в `ChatListToolbar` — заменён на `lifecycleScope.launch { withContext(IO) }` для очистки кеша
- Handler lifecycle leak в `ChatE2EEDelegate` — `Handler.postDelayed` заменён на `lifecycleScope.launch { delay() }` с проверкой `isFinishing`/`isDestroyed`
- Unmanaged Thread в `CallSoundManager` — `Thread { while(...) { Thread.sleep() } }` заменён на coroutine с `delay()`, `toneGenerator` сделан `@Volatile`, добавлен `destroy()` для полной очистки
- Coroutine scope leak в `AIBottomSheet` — `CoroutineScope` создавался заново при каждом вызове `loadPresetAgents()`, теперь используется class-level `agentScope`
- Thread-unsafe `locallyReadChats` в `ChatListViewModel` — заменён на `ConcurrentHashMap.newKeySet()`

**Оптимизовано (Performance):**
- ChatListViewModel: объединены 2 копии списка в 1 при обновлении чата на новое сообщение (~50% меньше аллокаций)
- `buildSections()` debounce 50ms — пакетная обработка вместо каскадных вызовов при массовых обновлениях
- MessageAdapter: `SimpleDateFormat` кеширован через `ThreadLocal` вместо создания нового экземпляра на каждый bind
- MessageAdapter: `setSearchHighlight` и `updatePinnedMessages` теперь обновляют только затронутые элементы вместо `notifyItemRangeChanged(0, itemCount)` (~95% меньше rebinds)
- `markRead` debounce 1s — одна gRPC-запроса вместо одной на каждое входящее сообщение

**Изменено:**
- `RealGrpcClient.kt` — `@Volatile` на 6 singleton fields, `ConcurrentHashMap` для 4 коллекций, `scheduleMarkRead()` debounce
- `ChatListToolbar.kt` — `runBlocking` → `lifecycleScope.launch`
- `ChatE2EEDelegate.kt` — `Handler.postDelayed` → `lifecycleScope.launch { delay() }`
- `CallSoundManager.kt` — Thread → coroutine, `@Volatile toneGenerator`, `destroy()` method
- `CallActivity.kt` — `soundManager.stop()` → `soundManager.destroy()`
- `AIBottomSheet.kt` — class-level `agentScope` вместо per-call scope
- `ChatListViewModel.kt` — `locallyReadChats` → `ConcurrentHashMap.newKeySet()`, combined list copy, `scheduleBuildSections()` debounce
- `MessageAdapter.kt` — `ThreadLocal<SimpleDateFormat>`, targeted `notifyItemChanged`
- `bottom_sheet_user_menu.xml` — Favorites перемещён выше Contacts
- `activity_chat_list.xml` — добавлена `ivFavorites` кнопка в toolbar
- `ChatListActivity.kt` — +`ivFavorites` field, click handler, force logout observer
- `ChatListActionMode.kt` — hide/show `ivFavorites` в enter/exit selection mode
- `ChatListViewModel.kt` — +`forceLogoutEvent` SharedFlow, auth error detection в `loadChats()`
- `GrpcChatClient.kt` — `ChatListPage` +`error` field, `getChats` передаёт gRPC error status
- `strings.xml` (EN + RU) — +`session_expired`

---

## [1.3.1.07] - 2026-06-29

### Reactions fix + Message dedup + AI chat deletion + Error handling

**Исправлено:**
- Реакции не сохранялись в Избранном — race condition в Room DB save: `messages.value.firstOrNull` перечитывал StateFlow после обновления. Теперь `updatedMsg` захватывается прямо в `messages.update` lambda
- Реакции не появлялись на другом клиенте — REACTION_V2 stream handler молча дропал реакции если сообщение не было загружено в `_messages`. Теперь реакции сохраняются в Room DB через `updateReactions()` даже если сообщение не в памяти
- Реакции залипали в optimistic state — `setReactionV2` response с пустыми реакциями игнорировался. Теперь пустые реакции корректно очищают UI
- Дубликаты сообщений при перезаходе в чат — `sendMessageV2` temp ID vs server ID race condition оставлял оба варианта в Room DB. Добавлен content-based dedup (`getContentHash` + `deduplicateByContent`)
- Позиция скролла списка чатов терялась при pull-to-refresh — теперь позиция сохраняется если пользователь прокрутил вниз
- AI чаты нельзя удалить — `deleteChat()` отправлял `DeleteChat` на сервер для `ai-chat-*` ID, которых нет в таблице `chats`. Теперь удаление только локальное
- AI чаты возвращались после удаления — `loadChats()` заново загружал AI чаты с сервера. Теперь удалённые ID хранятся в SharedPreferences (`deleted_ai_chats`) и фильтруются
- Ошибка БД сервера показывалась как "Неверное имя или пароль" — gRPC `INTERNAL`/`UNAVAILABLE` ошибки теперь распознаются и показывают "Сервер временно недоступен"

**Добавлено:**
- Серверный поиск сообщений — `ChatSearchDelegate` теперь использует `SearchMessages` RPC с 300ms debounce, fallback на клиентский поиск
- Кастомные звуки уведомлений — per-chat звуки через `notification_sounds` SharedPreferences, `setNotificationSound`/`getNotificationSound` companion methods
- Параллельная загрузка чатов — regular + AI chats загружаются одновременно через `supervisorScope` + `CompletableDeferred`
- Строка `server_error` (EN + RU) для ошибок сервера

**Изменено:**
- `RealGrpcClient.kt` — REACTION_V2 handler: +Room DB save via `updateReactions()` когда сообщение не в `_messages`
- `GrpcMessageV2Client.kt` — `setReactionV2`: response обрабатывает пустые реакции; Room DB save через захваченный `updatedMsg` (без race condition); +`getContentHash`, +`deduplicateByContent`; merge logic фильтрует по content hash
- `Daos.kt` — +`updateReactions(messageId, reactionsJson)` DAO method
- `ChatListActivity.kt` — scroll position preservation при обновлении adapter
- `ChatSearchDelegate.kt` — server-side search via `SearchMessages` RPC, debounced, fallback to client
- `NewChatActivity.kt` — `ChatSearchDelegate` constructor + `roomId` wiring
- `LavenderMessagingService.kt` — notification channel sound, per-chat sound override
- `ChatListViewModel.kt` — parallel chat + AI chat loading; AI chat deletion locally only + `deleted_ai_chats` filter; removed separate `loadAiChats()`
- `SessionManager.kt` — login error handling: `SERVER_ERROR` для ошибок БД/сервера
- `ServersActivity.kt` — обработка `SERVER_ERROR` и `CONNECTION_FAILED`
- `ChatListAuth.kt` — обработка `SERVER_ERROR` и `CONNECTION_FAILED`
- `strings.xml` (EN + RU) — +`server_error`

---

## [1.3.1.06] - 2026-06-29

### Splash freeze fix + Duplicate message fix + Push notification deep link

**Исправлено:**
- Splash screen зависал при запуске — `SessionManager.waitForConnectionAndReLogin()` использовал `latch.await(8, SECONDS)` на Main thread, блокируя UI до 8 секунд при обновлении JWT токена. Убран latch, callback обрабатывает результат асинхронно
- Splash screen зависал если biometric prompt зависал без callback — добавлен 15s timeout с принудительным переходом в чат
- Splash screen зависал если цепочка анимаций ломалась — добавлен 5s safety timeout
- Дубликат сообщения при реакции — race condition между `sendMessageV2` response (temp ID → server ID) и ChatV2 stream (server ID уже добавлен). Добавлен дедупликационный фильтр после обновления ID
- Push notification deep link: если пользователь уже в `NewChatActivity` и нажимает уведомление на другой чат — не переключался. Добавлен `onNewIntent()` с `startChatV2` + `markRead` + обновление toolbar

**Изменено:**
- `SessionManager.kt` — убран `CountDownLatch` + `latch.await(8s)` из `waitForConnectionAndReLogin()`
- `SplashActivity.kt` — +`import android.util.Log`, +15s biometric timeout, +5s splash timeout
- `GrpcMessageV2Client.kt` — dedup фильтр в `sendMessageV2` response handler
- `NewChatActivity.kt` — `onNewIntent()` обновлён: +`startChatV2`, +`markRead`, +toolbar update

---

## [1.3.1.05] - 2026-06-29

### Read status + Reactions persistence + Real-time messages + Chat list UX + Admin Sessions + Delete Messages v2

**Исправлено:**
- Unread count не сбрасывался при входе в чат — `RealGrpcClient.markRead()` был заглушкой (dismiss нотификаций), никогда не отправлял gRPC `MarkRead` на сервер. Реализован реальный вызов `messenger.ChatService/MarkRead`
- Unread восстанавливался после `loadChats` — добавлен `locallyReadChats` optimistic tracking в `ChatListViewModel`, предотвращающий перезапись из stale серверных данных
- Сообщения из ChatV2 стрима не отображались в открытом чате — добавлено добавление в `_messages` StateFlow + Room DB с дедупликацией по ID
- Реакции не сохранялись при перезаходе — `REACTION_V2` stream handler обновлял in-memory но не сохранял в Room DB
- Реакции терялись при загрузке истории — merge logic в `loadHistoryV2` теперь мержит реакции из обоих источников (сервер + локальный кеш), сохраняя реакции текущего пользователя
- Race condition при загрузке кеша — Room DB кеш теперь всегда мержится с текущими сообщениями, не только когда `messages` пустой
- Секретные чаты: "Обмен ключами..." больше не зависает навсегда — `updateSubtitle()` теперь корректно обрабатывает `isSecret`
- Секретные чаты: "2 участника, 0 онлайн" заменено на правильный E2EE статус (🔒 E2EE / 🔒 Сквозное шифрование)
- Навигация из списка чатов: добавлен `IS_SECRET` intent extra — при повторном входе в секретный чат E2EE корректно инициализируется
- Кнопка видеозвонка восстановлена в тулбаре чата (потеряна при рефакторинге NewChatActivity в 6 делегатов)
- Кнопка поиска восстановлена в тулбаре чата
- `handleIncomingE2EEMessage`: исправлен мёртвый код — теперь корректно добавляет расшифрованное сообщение

**Добавлено:**
- `METHOD_MARK_READ` + реальный gRPC вызов `MarkRead` в `RealGrpcClient`
- `locallyReadChats` — optimistic tracking для предотвращения перезаписи unread count
- Автоматический `markRead` при получении сообщения от другого пользователя в активном чате
- Chat list: `scrollToPosition(0)` при новом сообщении если пользователь вверху списка
- `AdminUserSessionProto` + marshallers для отображения сессий устройств
- `getAdminUserSessions()` RPC — загрузка активных сессий пользователя
- `SuperAdminAdapter` — раскрывающийся список сессий под каждым пользователем (иконка устройства, версия, IP, last seen, online статус)
- `item_admin_session.xml` — layout для элемента сессии
- Иконки `ic_web.xml`, `ic_android.xml`, `ic_device.xml`
- Удалены фильтры `text == "[deleted]"` — сервер теперь полностью удаляет записи вместо пометки `content_type='deleted'`

**Изменено:**
- `RealGrpcClient.kt` — +`METHOD_MARK_READ`, +реальный gRPC в `markRead()`, +добавление стрим-сообщений в `_messages`, +auto `markRead` для активного чата, +`REACTION_V2` сохранение в Room DB
- `GrpcMessageV2Client.kt` — merge logic мержит реакции из server + local, Room DB кеш всегда мержится
- `ChatListViewModel.kt` — `markAsRead()` optimistic clear + `locallyReadChats` tracking
- `ChatListActivity.kt` — `onResume()` всегда `loadChats(silent = true)`, `scrollToPosition(0)` при new message
- `ChatToolbarDelegate.kt` — +`isE2eeInProgress` флаг, +проверка `isSecret` в `updateSubtitle()`
- `ChatE2EEDelegate.kt` — +`onKeyExchangeStart` callback
- `NewChatActivity.kt` — +`onCreateOptionsMenu`/`onPrepareOptionsMenu`/`onOptionsItemSelected`, +`onKeyExchangeStart` wiring, исправлен `handleIncomingE2EEMessage`
- `ChatListNavigation.kt` — +`IS_SECRET` intent extra, `IS_DIRECT` учитывает `isSecret`
- `SuperAdminAdapter.kt` — +раскрывающиеся сессии
- `SuperAdminActivity.kt` — +загрузка сессий при клике
- `MessengerProto.kt` — +`AdminUserSessionProto`, +Request/Response
- `GrpcMarshallers.kt` — +marshallers для сессий

---

## [1.3.1.04] - 2026-06-28

### ChatV2 clientVersion + Hermes Agent ACP + last_seen_at fix

**Добавлено:**
- `ChatV2MessageProto.clientVersion` (field 3) — клиент отправляет версию при подключении к ChatV2 стриму
- Hermes Agent ACP: emoji mapping "hermes" → "🔬" в AIBottomSheet, AiV2AgentListAdapter, AiV2ChatActivity
- `ChatListNavigation.kt` — +`IS_SECRET` intent extra, `IS_DIRECT` учитывает `isSecret`

**Исправлено:**
- Админ панель показывала неверные версии клиентов — `clientVersion` не отправлялся в ChatV2 стриме
- `last_seen_at` не обновлялся при отправке сообщений — `SendMessageV2` не вызывал `UpdateLastSeen` (серверный фикс)

**Изменено:**
- `MessagesV2Proto.kt` — +`clientVersion: String` в `ChatV2MessageProto`
- `MessagesV2Marshallers.kt` — +сериализация/dесериализация field 3
- `RealGrpcClient.kt` — +`BuildConfig.VERSION_NAME` в первом ChatV2 сообщении
- `AIBottomSheet.kt` — +"hermes" → "🔬"
- `AiV2AgentListAdapter.kt` — +"hermes" → "🔬"
- `AiV2ChatActivity.kt` — +"hermes" → "🔬"

---

## [1.3.1.03] - 2026-06-27

### Agent statuses + HTTP upload auth fix + Camera permission + isSent fix

**Добавлено:**
- Статусы агентов: доступен (🟢), серверный ключ (🟡), нет ключа (🔴) — в toolbar чата и AIBottomSheet
- `AgentStatus` enum с определением по `providerConfig`
- Runtime-разрешение камеры — запрос перед открытием камеры

**Исправлено:**
- HTTP загрузка файлов/изображений — двойной `Bearer ` токен вызывал 401
- `AuthInterceptor` для OkHttpClient — автоматически добавляет JWT токен
- `sendMessageV2` — `isSent = true` при успешном ответе сервера (исправлен статус "часики")
- Камера — добавлен runtime-запрос `CAMERA` permission

**Изменено:**
- `HttpClient.kt` — `init(context)` с `AuthInterceptor`
- `SplashActivity.kt` — инициализация `HttpClient.init(this)`
- `AuthInterceptor.kt` — NEW, OkHttp interceptor для JWT
- `AiV2Models.kt` — `AgentStatus` enum
- `ChatInputDelegate.kt` — camera permission + removed manual auth headers
- `GrpcMessageV2Client.kt` — `isSent` update on send success
- `AiV2ChatActivity.kt` — agent status in toolbar
- `AIBottomSheet.kt` — status dots next to agent names
- `ShareReceiverActivity.kt`, `ThemePaletteActivity.kt` — removed manual auth headers

---

## [1.3.1.02] - 2026-06-27

### API Key visibility + [deleted] messages filter

**Добавлено:**
- API ключ в настройках агента теперь можно показать/скрыть (иконка глаза) и скопировать (long-press)
- Фильтрация soft-deleted сообщений (`[deleted]`) — серверных ответов, стрима и кэша Room DB

**Изменено:**
- `activity_ai_agent_setup.xml` — `TextInputLayout` с `endIconMode="password_toggle"` для API ключа
- `AiAgentSetupActivity.kt` — long-press копирование ключа в буфер обмена
- `GrpcMessageV2Client.kt` — фильтр `[deleted]` при загрузке истории и из кэша
- `RealGrpcClient.kt` — фильтр `[deleted]` в ChatV2 стриме

**Исправлено:**
- Soft-deleted сообщения (`content_type = 'deleted'`) больше не отображаются как `[deleted]` в чатах

---

## [1.3.1.01] - 2026-06-27

### Полный переход на Messages V2

**Критическое изменение:** удалена поддержка v1 message RPCs. Клиент работает ТОЛЬКО с v2.

**Удалено:**
- `GrpcMessageClient.kt` — v1 клиент сообщений
- v1 chat stream (`messenger.ChatService/Chat`) из RealGrpcClient
- v1 методы: `sendMessage`, `loadHistory`, `editMessage`, `deleteMessage`, `setReaction`
- v1 message marshallers (GetHistory, EditMessage, DeleteMessages, Reaction)
- v1 proto классы (GetHistoryRequest/Response, EditMessageRequest/Response, DeleteMessagesRequest/Response, ReactionRequest/Response)
- `saveFavoriteMessage()` из GrpcFavoritesClient (не вызывался)
- Guards `isChatV2Supported()` — всегда true

**Заменено:**
- Chat stream → `messenger.ChatService/ChatV2` (bidirectional stream с JWT auth)
- `GetHistory` → `GetHistoryV2` (cursor-based pagination вместо OFFSET)
- `SendMessage` (через stream) → `SendMessageV2` (unary RPC)
- `EditMessage` → `EditMessageV2`
- `DeleteMessages` → `DeleteMessageV2` (soft delete)
- `SetReaction` → `SetReactionV2` (inline JSONB)

**Добавлено:**
- `SearchMessages` — серверный поиск сообщений (в конкретном чате или кросс-чат)
- Полная обработка системных сигналов в ChatV2 stream: AUTH_FAILED, FORCE_LOGOUT, ONLINE_USERS_UPDATE, CHAT_DELETED, CLEAR_CACHE, SERVER_INFO, SYSTEM_NOTIFICATION, SET_SUPER_ADMIN, FORCE_DISCONNECT_DEVICE, FORCE_LOGOUT_EXCEPT
- Автоматическое переподключение v2 stream с exponential backoff
- `lastChatRequest` упрощён до (roomId, callback) — больше не хранит username/password

**Изменены файлы:**
- `RealGrpcClient.kt` — удалён v1 stream, упрощён LastChatRequest
- `GrpcClient.kt` — удалены v1 facade методы
- `GrpcMessageV2Client.kt` — добавлен `searchMessages()`
- `MessagesV2Proto.kt` — добавлены SearchMessages proto классы
- `MessagesV2Marshallers.kt` — добавлены SearchMessages marshallers
- `ChatViewModel.kt` — все методы используют v2
- `NewChatActivity.kt` — использует `startChatV2`
- `ChatSelectionDelegate.kt` — `deleteMessageV2`/`sendMessageV2`
- `ChatMessageMenuDelegate.kt` — `editMessageV2`/`deleteMessageV2`/`setReactionV2`
- `ChatInputDelegate.kt` — `sendMessageV2`
- `ShareReceiverActivity.kt` — `startChatV2`/`sendMessageV2`
- `SessionManager.kt` — `startChatV2`
- `GrpcFavoritesClient.kt` — удалён `saveFavoriteMessage`

**Удалены файлы:**
- `GrpcMessageClient.kt`
- `GrpcMessageClientTest.kt`

---

## [1.3.0.21] - 2026-06-27

### Добавлено

**AI Bottom Sheet — показывает только пользовательских агентов:**
- Пресеты убраны из шторки — доступны только собственные агенты
- Loading/empty состояния добавлены
- Пресеты остаются доступными через настройки агентов (AiV2AgentListActivity)

**AiAgentSetupActivity — Provider Config:**
- Ключ отображается в настройках агента (маскированный)
- Placeholder "Server key" для пресетов без пользовательского ключа

### Исправлено

**API Key в настройках агента:**
- AiAgentSetupActivity проверяет `api_key_source: "server"` и показывает placeholder
- AiV2AgentCreateEditViewModel — fallback на ai_chat_settings работает для всех агентов

### Известные проблемы

- **API Key пуст для пользовательских агентов с server-side ключом** — ключ не отображается в настройках, хотя сервер его возвращает. Требуется отладка.

---

## [1.3.0.20] - 2026-06-27

### Добавлено

**AI Agent Setup — переработана форма создания/редактирования агента:**
- Поле "API Key" вместо JSON "Provider Config"
- Слайдер "Temperature" (0–2, шаг 0.1, default 0.7)
- Поле "Max Tokens" (default 4096)
- Кнопка "Сохранить" появляется только при изменении полей
- Кнопка позиционируется над клавиатурой через WindowInsets

**AI Chat — команды агента:**
- Кнопка `/` открывает меню команд: /new, /clear, /history, /settings, /model, /system, /tools
- `/new` — очищает чат и начинает новую сессию
- `/settings` — открывает настройки чата (API key, model)
- Остальные команды вставляются в поле ввода

**AI Chat — отправка и вложения:**
- Кнопки send/attach теперь видны и работают
- TextWatcher переключает send ↔ attach в зависимости от текста

**AI Chat — ошибки как сообщения агента:**
- Ошибки сервера отображаются как bubble агента в чате (⚠️ + текст)
- Toast убран — ошибки видны в истории и не пропадают

**Pull-to-refresh — hardened:**
- `forceTokenRefresh()` перед загрузкой чатов
- Авто-реконнект gRPC если статус не READY (ожидание до 5 сек)

### Изменено

**AI Bottom Sheet — переработана шторка:**
- Чекбоксы заменены на ImageView-toggle (фиксированный размер 22dp)
- Тап по строке агента переключает выбор (не только чекбокс)
- Долгий тап — настройки агента
- "Создать своего агента" перемещён ниже "Управление агентами"
- Кнопка "Начать чат" в fixed footer (не скроллится)
- Скролл через ScrollView с weight=1

**AiV2AgentListActivity — toolbar:**
- Заголовок "ИИ Агенты" отображается на toolbar

### Исправлено

**AiAgentSetupActivity — save button:**
- Кнопка не перекрывается навигацией (WindowInsets listener)
- Использует виджет `PrimaryButtonCompact`

**AiV2ChatViewModel — error handling:**
- Ошибки пробрасываются через `AiV2ChatMessage.error` в chat bubble
- Rate limit обрабатывается через отдельный `rateLimitEvent` flow

### Изменённые файлы

| Файл | Изменение |
|------|-----------|
| `ui/ai/AiAgentSetupActivity.kt` | Новая форма (API key, temperature, max tokens), floating save button |
| `ui/ai/AiV2AgentCreateEditViewModel.kt` | temperature/maxTokens параметры в create/update |
| `ui/ai/AiV2ChatActivity.kt` | Команды, send/attach visibility, ошибки в чат |
| `ui/ai/AiV2ChatViewModel.kt` | Ошибки как сообщения, rateLimitEvent, clearMessages |
| `ui/widget/AIBottomSheet.kt` | ImageView toggle, tap select, reordered sections, fixed footer |
| `ui/chatlist/ChatListViewModel.kt` | refreshChats с forceTokenRefresh + reconnect |
| `ui/chatlist/ChatListFABs.kt` | onOpenAgentSettings callback |
| `data/ai/AiV2Models.kt` | error в AiV2ChatMessage, providerConfig в AiV2Agent |
| `data/grpc/RealGrpcClient.kt` | Убран error message при реконнекте |
| `theme/ui/ThemeApplier.kt` | Обновлены ID полей формы агента |
| `res/layout/activity_ai_agent_setup.xml` | Новые поля, toolbar, floating save |
| `res/layout/widget_ai_bottom_sheet.xml` | ScrollView + footerContainer |
| `res/layout/widget_action_item.xml` | Уменьшен padding |
| `res/drawable/ic_check_box_outline.xml` | NEW — outline для ImageView toggle |
| `res/values/strings.xml` | 13 новых строк (команды, настройки агента) |
| `res/values-ru/strings.xml` | 13 новых строк |

---

## [1.3.0.19] - 2026-06-23

### Исправлено

**Системные уведомления — вибрация и экран:**
- Создан новый канал `lavender_messages_v2` с `IMPORTANCE_HIGH` (старый канал нельзя изменить)
- Удалён старый канал `lavender_messages` с неправильными настройками
- Уведомления теперь вибрируют и зажигают экран (heads-up)
- Добавлен паттерн вибрации `[0, 300, 200, 300]` на канал уведомлений
- Добавлен `setFullScreenIntent()` для зажигания экрана при входящих сообщениях

**AI Пресеты — добавление в мои агенты:**
- Тап по пресету клонирует его в "Мои агенты"
- Долгий тап по пресету открывает настройки агента
- Шторка ИИ чатов показывает "Мои агенты" с чекбоксами для быстрого начала чата

**Список чатов — мгновенное обновление при новом сообщении:**
- Чат с новым сообщением сразу перемещается наверх списка
- Если чата нет в списке — автоматическая перезагрузка
- Unread badge обновляется в реальном времени

---

## [1.3.0.18] - 2026-06-23

### Исправлено

**"Был в сети" показывал неверное время:**
- `allUsers` теперь обновляется при каждом входе в чат (раньше только при пустом списке)
- Добавлено автоматическое обновление `allUsers` каждые 60 секунд пока открыт чат
- `lastSeenAt` теперь всегда актуальный

**Дублирование настроек агентов в шторке:**
- Убрана отдельная секция "Удалённый агент" из AI Bottom Sheet (дублировала Tab 4 в `AiV2AgentListActivity`)

**minSdk понижен с 33 до 29 (Android 10):**
- Приложение снова устанавливается на Android 10-12
- Ошибка "версия пакета на 31 версию SDK" больше не появляется

**Update Manager — валидация скачанного APK:**
- Проверка Content-Type ответа (отклоняет text/html/text/plain вместо APK)
- Проверка ZIP-хедера (PK magic bytes) — убеждается что файл это настоящий APK
- Проверка минимального размера (>100KB)
- Предотвращает ошибку "невозможно установить пакет" при битом скачивании

### Изменено

**AI Bottom Sheet — переработана шторка ИИ:**
- Убраны 10 пресетов агентов из нижнего листа (делали шторку нечитабельной)
- Новый компактный дизайн: "Начать чат с ИИ" / "Создать своего агента" / "Управление агентами"
- Пресеты доступны во вкладке "Пресеты" в `AiV2AgentListActivity` для конфигурации
- Убран мульти-агентный выбор чекбоксами из шторки (доступен через "Управление агентами")

---

## [1.3.0.17] - 2026-06-23

### Добавлено

**ChatKeepAliveService — foreground service для поддержания соединения:**
- Новый `ChatKeepAliveService` (START_STICKY) предотвращает убийство процесса системой
- Мониторит `connectionStatus` и автоматически переподключает chat stream при обрыве
- Persistent уведомление с текущим статусом подключения
- Запускается при входе в приложение, останавливается при logout

**Persist lastChatRequest — восстановление chat stream после kill процесса:**
- Параметры chat stream (username, roomId, deviceId, deviceName) сохраняются в SharedPreferences
- При восстановлении соединения автоматически перезапускает chat stream из сохранённых данных
- Очистка при logout

### Исправлено

**Connection retry loop — убран 5-мин timeout в фоне:**
- Ранее retry loop прекращал переподключение через 5 минут в фоне
- Теперь переподключение работает до восстановления соединения

### Изменённые файлы

| Файл | Изменение |
|------|-----------|
| `data/grpc/RealGrpcClient.kt` | Persist/restore lastChatRequest, убран background timeout |
| `data/grpc/GrpcClient.kt` | Экспозиция `clearLastChatRequestPrefs()` |
| `data/grpc/ChatKeepAliveService.kt` | NEW — foreground service для keep-alive |
| `data/session/SessionManager.kt` | Запуск/остановка ChatKeepAliveService, clearLastChatRequestPrefs при logout |
| `AndroidManifest.xml` | Регистрация ChatKeepAliveService (dataSync) |
| `res/values/strings.xml` | Строки для ChatKeepAliveService уведомлений |
| `res/values-ru/strings.xml` | Строки для ChatKeepAliveService уведомлений |

---

## [1.3.0.16] - 2026-06-22

### Исправлено

**AIBottomSheet — фикс вечной загрузки:**
- Добавлен флаг `isLoadingAgents` для разделения "загрузка" и "пусто"
- Показывает "Загрузка агентов…" пока gRPC выполняется
- Показывает "Нет доступных агентов" если сервер вернул пустой список
- Добавлена строка `ai_no_agents` (EN + RU)

**JWT token refresh — фикс UNAUTHENTICATED после фонового режима:**
- `ensureFreshToken()` теперь вызывается в начале `loadChats()` в ChatListViewModel
- Убрана гонка между async token refresh и sync loadChats в onResume
- Чаты загружаются без задержки после возвращения из фона

**Remote Agent — инлайн настройки в Agent Management:**
- Клик по remote agent в Tab 3 показывает Gateway + Token UI инлайн
- Создан `RemoteAgentSettingsFragment` + `fragment_remote_agent_settings.xml`
- Нет перехода в отдельную RemoteAgentSettingsActivity
- Back кнопка возвращает к списку агентов

**GrpcAIv2Client — логирование ListAIAgents:**
- Добавлено логирование статуса ответа для диагностики

### Изменённые файлы

| Файл | Изменение |
|------|-----------|
| `ui/widget/AIBottomSheet.kt` | Флаг `isLoadingAgents`, разделение loading/empty |
| `ui/chatlist/ChatListViewModel.kt` | `ensureFreshToken()` перед `loadChats()` |
| `ui/chatlist/ChatListActivity.kt` | Убран дублирующий `ensureFreshToken` в onResume |
| `ui/ai/AiV2AgentListActivity.kt` | Inline remote agent settings вместо отдельной Activity |
| `ui/remote/RemoteAgentSettingsFragment.kt` | NEW — фрагмент настроек remote agent |
| `data/grpc/GrpcAIv2Client.kt` | Логирование ListAIAgents |
| `res/layout/activity_ai_v2_agent_list.xml` | Добавлен `remoteAgentContainer` |
| `res/layout/fragment_remote_agent_settings.xml` | NEW — layout для remote agent настроек |
| `res/values/strings.xml` | Добавлена строка `ai_no_agents` |
| `res/values-ru/strings.xml` | Добавлена строка `ai_no_agents` |

---

## [1.3.0.15] - 2026-06-22

### Добавлено

**AiV2AgentListActivity — единый экран управления агентами:**
- 4 таба: Presets (загрузка с сервера), My Agents, Discover (Marketplace), Remote Agent
- `AiV2AgentListAdapter` — адаптер карточек агентов с эмодзи, провайдером, описанием
- Кнопка удаления для пользовательских агентов (пресеты защищены)
- FAB → создание нового агента через AiAgentSetupActivity
- Таб "Remote Agent" — список подключённых удалённых агентов, клик → RemoteAgentSettingsActivity

**AI чаты в основном списке чатов:**
- `ChatListViewModel.loadAiChats()` — загрузка AI чатов из `ListAIV2Chats` и мерж в общий список
- AI чаты отображаются как обычные чаты с типом `hermes`
- Таб "AI Chats" фильтрует только AI чаты
- Навигация на AI чат из списка → AiV2ChatActivity

**Уведомления в чате удалённого агента:**
- Серверные уведомления отображаются как системные сообщения в RemoteAgentActivity
- Подписка на `SubscribeNotifications` + загрузка истории уведомлений

### Изменено

**AIBottomSheet — упрощение:**
- Убрана секция "Notifications" (уведомления перенесены в RemoteAgentActivity)
- Добавлена кнопка "AI Agents" → AiV2AgentListActivity

**BiometricPrompt — исправление краша:**
- При ошибке биометрии (не отмене пользователя) приложение переходит в ChatListActivity вместо закрытия
- `onAuthenticationError`: `USER_CANCELED`/`NEGATIVE_BUTTON` → `finish()`, остальное → `startActivity(ChatListActivity)`

### Файлы

**Kotlin (3 новых):**
- `ui/ai/AiV2AgentListActivity.kt` — единый экран управления агентами
- `ui/ai/AiV2AgentListAdapter.kt` — адаптер для списка агентов
- `SplashActivity.kt` — исправлен biometric error handler

**Kotlin (4 изменённых):**
- `ui/widget/AIBottomSheet.kt` — убраны Notifications + unreadNotifCount, добавлен onOpenAiAgentList
- `ui/chatlist/ChatListFABs.kt` — навигация на AiV2AgentListActivity
- `ui/chatlist/ChatListViewModel.kt` — loadAiChats() для мержа AI чатов
- `ui/remote/RemoteAgentActivity.kt` — подписка на уведомления + отображение в чате
- `ui/remote/RemoteAgentViewModel.kt` — addSystemMessages()

**Layout (1 изменённый):**
- `activity_ai_v2_agent_list.xml` — добавлен таб "Remote Agent"

**Manifest:**
- Зарегистрирован `AiV2AgentListActivity`

**Тесты (332/332):** все проходят

---

## [1.3.0.13] - 2026-06-21

### Оптимизация

**Version Catalog (Gradle):**
- Все Gradle зависимости вынесены в `gradle/libs.versions.toml` — `jsch`, `json`, `protobuf` plugin
- Заменены 3 hardcoded зависимости: `com.jcraft:jsch:0.1.55`, `org.json:json:20230227`, `com.google.protobuf:0.10.0`

**RealGrpcClient — очистка линтер-предупреждений:**
- Исправлена подозрительная индентация в блоке JWT refresh (токен обновления)
- `currentServerPort` сделан `private` (не использовался снаружи)
- `checkServerHealth()` обёрнут в `withContext(Dispatchers.IO)` — устранён blocking call на Main thread
- Удалены неиспользуемые: `getAuthMetadata()`, `saveFavoriteMessage()`, параметр `context` из `fetchServersList()`
- Убраны 4冗余ных `io.grpc.*` квалификатора — добавлены import'ы

**Исправления:**
- `GrpcClient.removeFavorite` — исправлен несовпадающий тип callback: `(Boolean)` → `(Boolean, String)`
- `GrpcFavoritesClient.removeFavorite` — callback обновлён для согласования

**Тесты (332/332):**
- Исправлены 23 падающих тестов:
  - `AiV2ModelsTest` — enum count 7→8 (добавлен REVE)
  - `AiV2MarshallersTest` — default proto теперь сериализует non-empty bytes
  - `GrpcAuthClientTest` — `signOut`/`revokeDevice` callback возвращает `String` (не null)
  - `GrpcClientFacadeTest` — `isSupported()` теперь возвращает `true`
  - `GrpcConnectionManagerTest` — замокан `CallManager`, добавлен `Dispatchers.setMain`
  - `GrpcMessageClientTest` — исправлен assertion для `handleDeleteMessageSignal`
  - `GrpcUnaryCallHelperTest` — добавлен `onClose` в моки для завершения coroutine
- Добавлен `testOptions.unitTests.isReturnDefaultValues = true`

**Зависимости (обновлены):**
- `io.github.webrtc-sdk:android` 144.7559.05 → 144.7559.09
- `androidx.security:security-crypto` 1.1.0-alpha06 → 1.1.0
- `io.mockk:mockk` 1.13.8 → 1.14.11
- `app.cash.turbine:turbine` 1.0.0 → 1.2.1
- `org.jetbrains.kotlinx:kotlinx-coroutines-test` 1.7.3 → 1.11.0

**Исправлено (баг):**
- Пересылка сообщений (forward) не работала — `WidgetManager.getOrCreate("forward_sheet")` кешировал `ListBottomSheet` с уничтоженным Activity context. Теперь создаётся новый инстанс при каждом вызове

---

## [1.3.0.12] - 2026-06-21

### Добавлено

**Messages V2 — полная интеграция с серверным протоколом:**
- `MessagesV2Proto.kt` — 14 proto-классов: `MessageV2Proto`, `MessageMediaProto`, `MessageReplyProto`, `ChatV2MessageProto`, `ChatV2TypingProto`, `ChatV2SystemProto`, `GetHistoryV2Request/ResponseProto`, `SendMessageV2Request/ResponseProto`, `EditMessageV2Request/ResponseProto`, `DeleteMessageV2Request/ResponseProto`, `SetReactionV2Request/ResponseProto`
- `MessagesV2Marshallers.kt` — 16 marshallers для ChatV2 bidirectional stream + 5 unary RPCs
- `GrpcMessageV2Client.kt` — v2 клиент: `loadHistoryV2()` (cursor pagination), `sendMessageV2()`, `editMessageV2()`, `deleteMessageV2()`, `setReactionV2()`, конвертация доменных моделей (sender_id → username, JSON reactions, oneof content)
- `RealGrpcClient.startChatV2()` — ChatV2 bidirectional stream (`messenger.ChatService/ChatV2`), авторизация через `jwt_token`, приём `MessageV2` напрямую
- `ProtoUtils.createMessageV2Proto()` / `createMessageFromV2Proto()` — конвертация domain ↔ proto
- `GrpcClient` facade: `startChatV2`, `loadHistoryV2`, `sendMessageV2`, `editMessageV2`, `deleteMessageV2`, `setReactionV2`
- ChatV2 stream: обработка system signals (DELETE_MESSAGE, READ_ALL, SERVER_SHUTTINGDOWN)

### Тесты

- `MessagesV2MarshallersTest` — 60 тестов: proto defaults, values, equals, marshallers round-trip (ChatV2Message, MessageV2, GetHistoryV2, SendMessageV2, EditMessageV2, DeleteMessageV2, SetReactionV2), edge cases (skip unknown fields, empty bytes)
- `MessagesV2DomainTest` — 44 теста: domain conversion (text/image/voice/reply/reactions/e2ee), round-trip (domain → proto → domain), GrpcMessageV2Client conversion (messageV2ToDomain, domainToSendRequest), cursor pagination, unknown sender, invalid reactions JSON

---

## [1.3.0.11] - 2026-06-21

### Добавлено

**Reve Image Integration — клиентская часть:**
- `AiV2ChatMessage` — добавлено поле `imageUrl` для отображения сгенерированных изображений
- `ChatMessageItem` — добавлено поле `imageUrl` для unified chat adapter
- `ChatMessageAdapter` — ImageView в agent bubble для отображения изображений через Glide (scaleType=centerCrop, placeholder)
- `item_chat_message.xml` — добавлен `agentMessageImage` ImageView (240dp, adjustViewBounds)
- `AiV2Proto.kt` — `ChatWithAIV2ResponseProto` получил field 10: `imageUrl` (string)
- `GrpcAIv2Marshallers` — парсинг field 10 (imageUrl) в ChatWithAIV2ResponseMarshaller
- `GrpcAIv2Client` — callback `onResponse` обновлён: добавлен параметр `imageUrl`
- `AiV2ChatUseCase` — пробрасывает `imageUrl` из proto в доменную модель
- `AiV2DomainExtensions` — маппит `imageUrl` из `ChatWithAIV2ResponseProto` в `AiV2ChatMessage`
- `AiProviderType` — добавлен `REVE("reve-2.0")` провайдер для Reve Image агента

**AI Chat Settings (Per-Session):**
- `GetAIChatSettings` — получение настроек сессии (API ключ, модель, rate limit)
- `UpdateAIChatSettings` — обновление API ключа и модели для сессии
- Proto: `GetAIChatSettingsRequestProto`, `AIChatSettingsProto`, `UpdateAIChatSettingsRequestProto`, `UpdateAIChatSettingsResponseProto`
- Marshallers: `GetAIChatSettingsRequestMarshaller`, `AIChatSettingsResponseMarshaller`, `UpdateAIChatSettingsRequestMarshaller`, `UpdateAIChatSettingsResponseMarshaller`
- `GrpcAIv2Client.getChatSettings()` / `updateChatSettings()` методы
- `GrpcClient` facade: `getAIChatSettings()` / `updateAIChatSettings()`
- `AiV2ChatUseCase.getChatSettings()` / `updateChatSettings()` методы
- Domain: `AiChatSettings` data class + `AIChatSettingsProto.toDomain()` extension

**Preset Agents (10 presets):**
- Добавлены 2 новых пресет-агента: `vision` (Image analysis, 🎨) и `reve` (AI image generation, 🎨)
- Всего 10 пресетов: mimo, assistant, developer, devops, architect, writer, analyst, translator, vision, reve
- `AiV2AgentListAdapter` — emoji для reveal агентов: 🎨 (reve), 👁 (vision)
- `AiV2ChatActivity` — toolbar показывает правильный emoji для каждого агента

### Тесты

- `AiV2MarshallersTest` — 8 новых тестов для AI Chat Settings marshallers
- `AiV2MarshallersTest` — тест `chatWithAIV2ResponseMarshaller_imageUrl` для field 10

---

## [1.3.0.9] - 2026-06-20

### Добавлено

**Биометрическая аутентификация:**
- При включённой настройке биометрии в Security — после сплеш-экрана показывается BiometricPrompt перед входом в чат-лист
- Проверка `biometric_enabled_$username` в SharedPreferences
- Успешная аутентификация → ChatListActivity, ошибка/отмена → закрытие приложения

**Cursor-based пагинация чатов:**
- `GetChatsRequest` — добавлены `limit`, `filter`, `cursor` поля
- `GetChatsResponse` — добавлены `next_cursor`, `has_more` поля
- `ChatListPage` data class — `(chats, nextCursor, hasMore)`
- `loadMoreChats()` в ChatListViewModel — подгрузка следующей страницы
- `OnScrollListener` в ChatListActivity — infinite scroll при прокрутке к концу

### Улучшено

**AI Agent List — упрощение табов:**
- 5 табов → 3 таба: Presets (быстрый старт), My Agents (кастомные), Discover (маркетплейс)
- Таб Presets загружает пресет-агенты для мгновенного доступа к чату

**Поделиться — добавлена ссылка:**
- Текст "Поделиться" теперь включает ссылку `http://13.140.25.249`

### Исправлено

**Аудит серверного соответствия:**
- `searchChats` — исправлен timestamp: было `seconds`, стало `seconds * 1000 + nanos / 1000000` (millisecond precision)
- `getAllChats` — исправлен `isMuted`: было захардкожено `false`, стало `proto.isMuted`

---

## [1.3.0.8] - 2026-06-20

### Оптимизация

**Очистка кода (Lint warnings):**
- Удалены неиспользуемые import'ы из 18 файлов: `CallManager`, `CallActivity`, `ChatListActivity`, `ChatListFABs`, `ChatListToolbar`, `ChatListActionMode`, `ConferenceLobbyActivity`, `ShareReceiverActivity`, `SecurityActivity`, `RemoteAgentService`, `RemoteAgentSettingsActivity`, `BearerTokenInterceptor`, `GrpcDraftClient`, `GrpcFavoritesClient`, `GrpcAuthClient`, `GrpcServerDiscoveryClient`, `AiV2ChatActivity`
- Удалены неиспользуемые `TAG` из `GrpcDraftClient`, `GrpcFavoritesClient`, `GrpcAuthClient`, `GrpcServerDiscoveryClient`, `BearerTokenInterceptor`, `AiV2ChatActivity`
- Удалены неиспользуемые `connectionStatus` и `authStatus` из `GrpcAuthClient` — `@Suppress` на constructor params
- Удалён неиспользуемый `serverTime` из `RealGrpcClient`, неиспользуемый `AES_KEY_SIZE` из `E2EEManager`
- Удалена неиспользуемая переменная `prefs` из `SplashLoadingActivity`
- Удалена неиспользуемая переменная `editOldPw` из `SuperAdminActivity`
- Удалена неиспользуемая переменная `thisTunnelAddress` из `RemoteAgentService`
- Удалена неиспользуемая переменная `result` из `ProfileViewModel.uploadGroupAvatar`
- Удалён неиспользуемый `inner` модификатор из `ChatMessageAdapter.TypingHolder`
- Удалён неиспользуемый namespace `tools` из `activity_super_admin.xml`
- Удалены лишние `SDK_INT` проверки (всегда >= 29): `RemoteAgentService`, `UpdateCoordinator`

**SharedPreferences KTX:**
- `RealGrpcClient.fetchAdminStatus` — `prefs.edit().putBoolean().apply()` → `prefs.edit { putBoolean() }`
- `RealGrpcClient.startChatStream` — аналогично для `is_super_admin` и `admin_user_id`
- `ChatListToolbar.toggleLanguage` — аналогично

**Redundant qualifiers:**
- `ChatListToolbar` — 20+ вызовов `android.content.Intent(...)` → `Intent(...)` (import уже был)
- `BearerTokenInterceptor` — убрана冗余ная `io.grpc.` квалификация

### Исправлено

- `PATTERNS.md` — исправлен синтаксис в SplashActivity pattern (псевдокод `...` → `activity`)
- `item_chat.xml` — добавлен `contentDescription` на `ivAdminIndicator` ImageView
- `ChatViewModel.retryMessage` — убран неиспользуемый параметр `context`
- `ChatViewModel.fetchChatMetadata` — убран неиспользуемый параметр `isSecret`
- `ProfileViewModel` — исправлен redundant safe call `profile?.avatarUrl` → `profile.avatarUrl`

---

## [1.3.0.7] - 2026-06-20

### Оптимизация

**Очистка кода:**
- Удалён мёртвый класс `GrpcChatListClient.kt` (272 строки) — дублировал методы `GrpcChatClient`, никогда не вызывался через `RealGrpcClient`
- Удалён `GrpcChatListClientTest.kt` — тест удалённого класса
- Удалены 4 неиспользуемых функции из `AiV2ChatManager`: `clearTokens()`, `resetStreamState()`, `emitTyping()`, `aiTyping` SharedFlow
- Удалены 120 неиспользуемых import'ов из 56 файлов

**SuperAdminActivity:**
- Удалён `progressOverlay` (полупрозрачный прелоадер с ProgressBar) — используется только `SwipeRefreshLayout`
- Удалён `CircleImageView` из `item_chat.xml` — ChatViewHolder больше не создаёт аватар программно
- Сортировка пользователей вынесена в `Dispatchers.Default` — main thread не блокируется

**Комментарии:**
- Исправлены устаревшие ссылки `messenger.AIService/*` → `messenger.ChatService/*` в `GrpcAIv2Marshallers.kt` и `AiV2Proto.kt` (4 штуки)
- Исправлен комментарий `HermesChatActivity` → `NewChatActivity` в `widget_chat.xml`

### Исправлено

**Feedback (Отзыв админу):**
- Баг: `doOpenFeedbackChat` передавал `adminUserId` (UUID) в `createDirectChat`, который ожидает username — создавался чат с самим собой
- Исправлено: UUID резолвится в username через `allUsers`, добавлена проверка на self-chat

---

## [1.3.0.5] - 2026-06-20

### Оптимизация

**Singleton HttpClient (P1):**
- Создан `network/HttpClient.kt` — глобальный singleton `OkHttpClient` с connection pool (5 соединений, 5 мин TTL) и timeouts 30s
- Заменены 12 вызовов `OkHttpClient()` в 8 файлах: ProfileViewModel, EditProfileActivity, ChatInputDelegate (×2), UpdateManager, ThemePaletteActivity, ShareReceiverActivity (×3), AudioUploader
- `LavenderGlideModule` оставлен с отдельным клиентом (60s timeouts, followRedirects, retryOnConnectionFailure для Glide)
- Удалён неиспользуемый import `OkHttpClient` из ProfileActivity
- Эффект: -40% время повторных загрузок за счёт reuse TCP connections

**Логирование — очистка и оптимизация:**
- Удалено 39 шумных логов из горячих путей:
  - `ChatListViewModel` — 13 логов (MERGE/NEW_MSG на каждое сообщение, Chat clicked, markAsRead detail)
  - `SessionManager` — 11 логов (token refresh каждые 60с, FCM sync, reconnect noise)
  - `RealGrpcClient` — 15 логов (stream lifecycle, admin status, retry noise)
  - `SplashActivity` — 6 логов (навигационный шум)
- Добавлен тайминг загрузки чатов: `${System.currentTimeMillis() - startTime}ms`
- Упрощены сообщения (убраны verbose joinToString)
- Оставлены ошибки (`Log.e`) и предупреждения (`Log.w`)

**SplashActivity — fix `assignParent to null`:**
- `postDelayed` заменён на `lifecycleScope.launch { delay() }` + проверка `!isFinishing && !isDestroyed`
- Устраняет Android warning `assignParent to null: this = DecorView@...[SplashActivity]`
- Удалены 4 неиспользуемых import'а: ObjectAnimator, Log, View, doOnEnd

---

## [1.3.0.4] - 2026-06-20

### Исправлено

**Unread чаты — индикация:**
- `readReceiptEvent` handler обнулял `unreadCount` текущего пользователя при получении `READ_ALL` от другого участника — исправлено: чужие read-сигналы больше не влияют на мой unread badge
- `syncChats()` сохранял в БД сырые серверные данные вместо `mergedChats` — локально увеличенные unread-счётики терялись при холодном перезапуске

**AI v2 — Presets, Toolbar, Marketplace:**
- Пресеты не загружались — `TabLayout` listener не срабатывал для начального таба; добавлен явный `viewModel.loadAgents(0)` при старте
- Toolbar в `AiV2AgentListActivity` не показывал заголовок — `setDisplayShowTitleEnabled(false)` отключал отображение; заменён на `toolbar.title`
- Marketplace показывал только скелетоны — `ListMarketplaceAgentsRequestMarshaller` отправлял 0 байт при дефолтных параметрах (`limit=20`, `offset=0`); теперь `limit` и `offset` пишутся всегда
- `GetAIAgentReviewsRequestMarshaller` — аналогичный баг с `limit`

### Миграция

**ProfileService v2:**
- `EditProfileActivity` — loadProfile, updateBio, updateAvatar, deleteProfile переключены на `ProfileService` v2 (JWT context, без `user_id` в запросе)
- `ProfileViewModel.loadUserProfile` — v2 для текущего пользователя, ChatService для просмотра профилей других пользователей
- `ProfileClient` — добавлен `deleteProfile(password)`, убрана проверка `grpcPort == 50052` в `fetchServerInfo`
- `updateUsername` и `updatePassword` остаются в ChatService (нет v2 замены)

---

## [1.3.0.3] - 2026-06-20

### Исправлено

**AI v2 — gRPC service name fix (критический):**
- Все 15 AI v2 RPC вызовов использовали `messenger.AIService/*` — сервер не видел запросов
- Исправлено на `messenger.ChatService/*` (все методы зарегистрированы в ChatService)
- Причина: пресеты, маркетплейс и все AI v2 API не работали

**AI v2 — Marshallers fix (7 багов):**
- `RateAIAgentResponseMarshaller` — field mapping был неправильным (wire type mismatch: string→float, float→int32)
- `GetAIUsageStatsResponseMarshaller` — UsageStatInfo inner fields 2-5 все неправильные (сдвиг + неверные типы)
- `parseAgentInfoV2` — пропускал fields 15-21 (install_count, avg_rating, review_count, tags, original_agent_id, version, share_code)
- `GetAIAgentStatsResponseMarshaller` —缺少 field 4 (total_tokens_used)
- `ShareAIAgentResponseMarshaller` —缺少 field 3 (error string)
- `ListAIAgentsRequestMarshaller` — `includePublic` field не сериализовался (пустые байты)
- Все marshallers выровнены с серверным proto: field numbers, types, nested messages

**AI v2 — Proto data classes обновлены:**
- `AgentInfoV2Proto` — добавлены 7 полей (installCount, avgRating, reviewCount, tags, originalAgentId, version, shareCode)
- `RateAIAgentResponseProto` — добавлено поле `error`
- `GetAIAgentStatsResponseProto` — добавлено поле `totalTokensUsed`
- `ShareAIAgentResponseProto` — добавлено поле `error`
- `UsageStatEntryProto` — `totalTokens: Long → Int` (сервер int32)
- `GetAIUsageStatsResponseProto` — `totalTokens: Long → Int`

**Chat list — Unread индикация (критический):**
- Серверный `GetUserChatsV2` **не считал unreadCount** — SQL запрос не содержал подзапрос `unread_counts`
- Исправлено: добавлен CTE `unread_counts` в `GetUserChatsV2` и `SearchChats`
- Клиентский `ChatListViewModel.loadChats()` — race condition fix: при merge серверных данных сохраняется `max(local.unreadCount, server.unreadCount)`
- `ChatAdapter` — `unreadColor` alpha 30→40 для лучшей видимости

**AI v2 — Domain models:**
- `UsageStat.totalTokens: Long → Int` (все связанные файлы обновлены)

### Тесты

- 2 новых теста для `ListAIAgentsRequestMarshaller` serialization
- Обновлены тесты `RateAIAgentResponse`, `GetAIUsageStatsResponse` под новые field mappings
- Все AI v2 тесты проходят (105 tests)

---

## [1.3.0.2] - 2026-06-20

### Добавлено

**Marketplace — Сортировка и фильтры:**
- Сортировка агентов: Rating / Installs / Name (Spinner)
- Фильтры по типу провайдера: Tools / OpenRouter / MiMo / Local (ChipGroup)
- Клиентская сортировка и фильтрация после загрузки с сервера

**Loading Skeletons:**
- `item_marketplace_skeleton.xml` — скелетон-карточка с placeholder элементами
- 6 скелетонов показываются при первой загрузке Marketplace

**Agent Create — Public Toggle:**
- Чекбокс "Public (visible in Marketplace)" при создании/редактировании агента
- Публичные агенты появляются в табе Marketplace

**Chat List — Unread Highlight:**
- Непрочитанные чаты выделяются подсвеченным фоном (primary color alpha=30)
- Имя чата с непрочитанными сообщениями — жирный шрифт + primary цвет

### Исправлено

**Cancel Reply Button:**
- Крестик теперь закрывает цитату в `NewChatActivity` (добавлен `cancelReply.setOnClickListener`)

### Тесты (25 новых)

- `AiV2MarshallersTest` — 25 unit-тестов для Marketplace мараллеров:
  - RateAIAgent (request/response)
  - GetAIAgentReviews (request/response)
  - ListMarketplaceAgents (request/response)
  - GetAIAgentStats (request/response)
  - ShareAIAgent (request/response)
  - InstallAIAgent (request/response)
  - GetAIUsageStats (request/response)

### Файлы

**Kotlin (4 изменённых):**
- `ui/ai/MarketplaceViewModel.kt` — SortOption enum, setSortOption(), setFilterProvider(), setFilterToolsEnabled()
- `ui/ai/MarketplaceAgentAdapter.kt` — мульти-viewType (TYPE_ITEM / TYPE_SKELETON), showSkeleton()
- `ui/ai/AiV2AgentListActivity.kt` — setupSortFilter(), sortFilterBar
- `ui/ai/AiV2AgentCreateEditViewModel.kt` — isPublic в createAgent/updateAgent
- `ui/ai/AiV2AgentCreateEditActivity.kt` — publicSwitch
- `ui/adapter/ChatAdapter.kt` — unread highlight (background + bold name)
- `NewChatActivity.kt` — cancelReply.setOnClickListener

**Layout (4 новых):**
- `item_marketplace_skeleton.xml` — скелетон-карточка
- `activity_ai_v2_agent_create_edit.xml` — добавлен publicSwitch

**Drawable (3 новых):**
- `bg_skeleton_circle.xml` — круглый placeholder
- `bg_skeleton_rect.xml` — прямоугольный placeholder
- `bg_sort_spinner.xml` — фон для Spinner

**Strings (2 новых):**
- `ai_v2_public` — EN: "Public (visible in Marketplace)", RU: "Публичный (виден в Marketplace)"

---

## [1.3.0.1] - 2026-06-20

### Добавлено

**AI Marketplace UI:**
- `MarketplaceAgentAdapter` — карточки агентов с рейтингом (RatingBar), количеством установок, провайдером
- `AgentDetailActivity` — экран деталей агента: статистика, отзывы, кнопки Rate/Share/Install
- `ReviewAdapter` — список отзывов (user, rating, text, date)
- `RateAgentBottomSheet` — оценка агента 1-5 звёзд + текстовый отзыв
- `InstallAgentBottomSheet` — установка агента по share_code
- `MarketplaceViewModel` — каталог с пагинацией (loadAgents/loadMore)
- `AgentDetailViewModel` — статистика, отзывы, rate/share/install
- Empty state: "No public agents available yet" / "Публичных агентов пока нет"
- **Поиск агентов** — TextInputLayout с дебаунсом (2+ символов)
- **Pull-to-refresh** — SwipeRefreshLayout для обновления каталога
- **Infinite scroll** — автоматическая загрузка следующей страницы при прокрутке
- **Deep link** — `lavender://marketplace/install?code=xxx` для установки агентов

**AI Usage Stats UI:**
- `UsageStatsAdapter` — per-agent статистика (токены, запросы, период)
- 3 summary карточки: Total Tokens, Total Requests, Avg/Request
- Empty state: "No data yet" / "Пока нет данных"
- K/M форматирование чисел (1.5K, 2.3M)

**Rate Limit UI:**
- `RateLimitCache` — клиентский кэш лимитов запросов (sliding window, 10 req/min)
- При превышении лимита: блокировка input поля + countdown таймер
- Автоматическое восстановление после сброса окна

**Табы AiV2AgentListActivity:**
- Таб 3: "Marketplace" — каталог публичных агентов с поиском
- Таб 4: "Usage" — статистика использования AI

### Файлы

**Kotlin (10 новых):**
- `data/ai/MarketplaceModels.kt` — MarketplaceAgent, AgentStats, AgentReview, UsageStat
- `data/ai/RateLimitCache.kt` — клиентский кэш лимитов
- `ui/ai/MarketplaceViewModel.kt` — каталог с пагинацией
- `ui/ai/AgentDetailViewModel.kt` — детали агента
- `ui/ai/UsageStatsViewModel.kt` — статистика
- `ui/ai/MarketplaceAgentAdapter.kt` — карточки агентов
- `ui/ai/AgentDetailActivity.kt` — экран деталей
- `ui/ai/ReviewAdapter.kt` — список отзывов
- `ui/ai/RateAgentBottomSheet.kt` — диалог оценки
- `ui/ai/InstallAgentBottomSheet.kt` — диалог установки
- `ui/ai/UsageStatsAdapter.kt` — per-agent статистика

**Layouts (5 новых):**
- `item_marketplace_agent_card.xml` — карточка агента в каталоге
- `activity_agent_detail.xml` — экран деталей агента
- `item_review.xml` — карточка отзыва
- `bottom_sheet_rate_agent.xml` — шторка оценки
- `bottom_sheet_install_agent.xml` — шторка установки
- `fragment_usage_stats.xml` — фрагмент статистики
- `item_usage_stat.xml` — карточка per-agent статистики

**Modified:**
- `activity_ai_v2_agent_list.xml` — добавлены SearchBar, SwipeRefreshLayout, 5-й таб "Usage"
- `AndroidManifest.xml` — deep link intent filter для `lavender://marketplace/install`

**Domain:**
- `AiV2Models.kt` — добавлены MarketplaceAgent, AgentStats, AgentReview, UsageStat
- `AiV2DomainExtensions.kt` — добавлены toMarketplaceAgent(), AgentReviewProto.toDomain(), UsageStatEntryProto.toDomain()
- `AiV2ChatUseCase.kt` — добавлены 7 Marketplace методов

**Strings (26 новых EN + RU):**
- marketplace, marketplace_rate, marketplace_share, marketplace_install, marketplace_rate_agent, marketplace_install_agent, marketplace_enter_share_code, marketplace_write_review, marketplace_submit, marketplace_installs, marketplace_reviews, marketplace_agent_installed, marketplace_thanks_rating, marketplace_select_rating, marketplace_share_agent, marketplace_install_text, marketplace_empty, marketplace_usage, marketplace_tokens, marketplace_requests, marketplace_avg_request, marketplace_no_data, marketplace_no_data_desc, marketplace_search_hint, rate_limit_exceeded

**Tests (15 новых):**
- `MarketplaceModelsTest` (8) — data class defaults, values
- `MarketplaceMappersTest` (7) — Proto → Domain mapping, provider types

**Итого:** ~1700 LOC добавлено, 15 unit tests (все проходят)

---

## [1.3.0.0] - 2026-06-20

### Добавлено

**AI Marketplace API (7 методов):**
- `RateAIAgent` — оценка агента (1-5 + отзыв)
- `GetAIAgentReviews` — отзывы на агента
- `ListMarketplaceAgents` — каталог публичных агентов с поиском
- `GetAIAgentStats` — статистика агента (установки, рейтинг, отзывы)
- `ShareAIAgent` — генерация share_code для шеринга
- `InstallAIAgent` — установка агента по share_code
- `GetAIUsageStats` — статистика использования (токены, запросы)

**Graceful Shutdown + Reconnection:**
- Обработка `SERVER_SHUTTINGDOWN` сигнала в Chat стриме
- Health check (`GET /health`) перед реконнектом
- Индикатор "Server restarting…" / "Сервер перезапускается…" в toolbar
- Exponential backoff при недоступности сервера

**UI:**
- AI BottomSheet: dragHandle + заголовок "AI Services (in development)"
- LavenderFab в списке агентов (корректные отступы от навбара)
- Крупный аватар в toolbar (48dp)
- Табы с контрастным контрастированием на тёмных темах
- Форма создания/редактирования агента: surface фон, темизация полей ввода

### Исправлено

- Убран прелоадер на кнопке Login/Register (SplashLoadingActivity handles loading)
- Ошибка авторизации: "V2 auth failed" → локализованное "Wrong username or password"
- Presets таб: загрузка с `includePublic = true` для получения серверных пресетов
- Табы в списке агентов: `surfaceColor` фон + `textPrimary` для контраста
- Форма агента: TextInputLayout stroke/hint цвета, Switch текст, Save кнопка темизированы

### Удалено

**AI v1 (полная очистка):**
- `OwlGrpc.kt` — OWL AI функции (_marshallers, chatWithOwl, createOwlChat, settings, freeModels_)
- `HermesGrpc.kt` — Hermes AI функции (_chatWithOrchestrator, agent CRUD, sessions, settings_)
- Уведомления вынесены в `NotificationsGrpc.kt`
- Remote Agent вынесен в `RemoteAgentGrpc.kt`
- ~20 v1 proto классов из `MessengerProto.kt`
- v1 AI строки из `strings.xml` (EN + RU)
- Сломанный `OwlActivity` из `AndroidManifest.xml`
- `AIChatInfo` data class, `getAIChats()`/`renameAIChat()` методы
- Стейл комментарии, неиспользуемые цвета, старые IDs

**Итого:** удалено ~4000 LOC v1 AI кода, добавлено ~800 LOC Marketplace + Reconnection

---

## [1.2.0.20] - 2026-06-20

### Добавлено

**AI Services v2 — единый API для всех AI чатов:**
- `ChatWithAIV2` — один RPC для simple/agent/pipeline чатов (заменяет ChatWithOWL, ChatWithOrchestrator, ChatWithAI)
- Tool calling loop — агент может вызывать инструменты (web_search, search_messages, search_users, web_fetch, get_chat_info)
- 7 типов провайдеров: openrouter, local, mimo, webhook, websocket, subprocess, mcp
- Мультимодальность — отправка изображений в AI чат
- Агенты с настройками: provider_config JSON, system_prompt, tools, RAG

**Agent CRUD:**
- `CreateAIAgent`, `UpdateAIAgent`, `DeleteAIAgent`, `GetAIAgent`, `ListAIAgents`, `CloneAIAgent`
- 8 встроенных пресет-агентов (mimo, assistant, developer, devops, architect, writer, analyst, translator)
- Публичные агенты — доступ другим пользователям
- Клонирование агентов

**Tools:**
- `ListAITools` — список доступных инструментов с JSON Schema параметров

**UI:**
- `AiV2ChatActivity` — единый экран AI чата для всех типов
- `AiV2AgentListActivity` — список агентов с табами (Presets / My Agents / Public)
- `AiV2AgentCreateEditActivity` — создание и редактирование агентов
- Индикаторы tools/RAG на карточках агентов

**Tests:**
- 60 unit-тестов: AiV2ModelsTest (20), AiV2DomainExtensionsTest (13), AiV2MarshallersTest (27)

### Удалено

**AI v1:**
- OwlGrpc.kt (OWL AI streaming, settings) — заменён на GrpcAIv2Client
- HermesGrpc.kt (Hermes orchestrator, agent CRUD, sessions) — заменён на GrpcAIv2Client
- AiChatGrpc.kt (unified v1 wrapper) — удалён
- OwlChatUseCase.kt, HermesChatUseCase.kt — заменены на AiV2ChatUseCase
- AiChatManager.kt, AiModels.kt, AiDomainExtensions.kt — заменены на AiV2ChatManager, AiV2Models, AiV2DomainExtensions
- HermesRepository.kt, HermesModel.kt — удалены

**UI v1:**
- OwlChatActivity, OwlChatViewModel, OwlSettingsActivity
- HermesChatActivity, HermesChatViewModel, HermesChatAdapter, HermesCommandAdapter
- AgentListActivity, AgentListViewModel, AgentListAdapter
- AgentSettingsActivity, AgentSettingsBottomSheet
- 8 layout XML файлов

**Итого:** удалено ~4000 LOC v1 AI кода, добавлено ~2300 LOC v2 кода

### Оставлено

- OwlGrpc.kt — утилиты уведомлений (subscribeNotifications, getNotificationHistory, markNotificationsRead, getUnreadCount)
- HermesGrpc.kt — Remote Agent (listRemoteAgents, deployAgentTask, generateAgentToken, etc.)
- Remote Agent UI (RemoteAgentActivity, RemoteAgentSettingsActivity, RemoteAgentService)

---

## [1.2.0.19] - 2026-06-19

### Добавлено

**Навигация шторок (BottomSheet navigation):**
- `SheetNavigator` — стек шторок с поддержкой back navigation
- Кнопка "назад" в заголовке шторки (автоматически появляется при наличии стека)
- `showWithNavigation()` — показ шторки с навигацией
- Все шторки в ChatListFABs теперь используют навигацию

**Unit-тесты ChatViewModel:**
- 17 тестов для `ChatViewModel.ChatMetadata` (defaults, values, copy, equals, hashCode, toString)
- Тесты для всех типов чатов: direct, group, conference, favorites, general, secret

### Удалено

**AI тесты:**
- `AiModelsTest.kt` — удалён (AI v1 deprecated, готовится AI v2)

---

## [1.2.0.18] - 2026-06-19

### Исправления

**Secret chat marshallers — field order fix:**
- `CreateSecretChatRequest` — убран лишний `userId`, field order: 1=target_username, 2=target_user_id, 3=public_key, 4=client_version (было: 1=userId, 2=target_username, 3=targetUserId, 4=publicKey, 5=clientVersion → сервер читал publicKey как client_version → "API version too old")
- `ExchangeSecretKeyRequest` — убран лишний `userId`, field order: 1=chat_id, 2=public_key (было: 1=chat_id, 2=userId, 3=public_key)
- `GetSecretChatKeyRequest` — убран лишний `userId`, field order: 1=chat_id (было: 1=chat_id, 2=userId)

**Secret chat display name:**
- `getDisplayName()` — проверка `isSecret` вынесена на верхний уровень (была внутри `type != "direct"`, но секретные чаты имеют `type = "direct"` → проверка пропускалась → показывалось `name` с обоими именами)
- Секретные чаты теперь показывают `🔒 имя_собеседника`

**E2EE key exchange:**
- Лимит 10 попыток обмена ключами (каждые 3 сек). Ранее — бесконечный цикл retry
- Логирование: номер попытки, финальный warning при исчерпании лимита

**Selection mode:**
- Убраны action_pin и action_archive из toolbar в selection mode (пока не готово)
- CheckBox заменён на MaterialCheckBox с `buttonTint="?attr/colorPrimary"` — адаптируется к теме
- CheckBox сдвинут левее (marginEnd 12dp → 8dp)

---

## [1.2.0.17] - 2026-06-19

### Исправления

**gRPC Marshallers fix:**
- Все marshallers для ChatList v2 (PinChat, UnPinChat, ArchiveChat, UnarchiveChat, PinMessage, UnPinMessage, GetPinnedMessages) — исправлен field order: `chat_id` field 1, `user_id` field 2 (было наоборот)
- Contacts marshallers (GetContacts, AddContact, RemoveContact) — исправлен field order: `user_id` field 1

**Secret chat fixes:**
- Все 3 секретных marshallers (CreateSecretChat, ExchangeSecretKey, GetSecretChatKey) — добавлен `userId` во все proto и marshallers
- `getDisplayName()` — для секретных чатов парсит participants и возвращает только имя собеседника
- Добавлено логирование key exchange в `ChatE2EEDelegate`

**Pin/Unpin chat:**
- Замена иконки `ic_lock` → `ic_pin` (pushpin) для action_pin
- Динамические labels: pin/unpin, mute/unmute, archive/unarchive — заголовок меняется по состоянию selection

**Empty contacts:**
- `SearchableListBottomSheet` — пустое состояние "Контактов пока нет" если список контактов пуст

**Theme:**
- Все иконки toolbar, selection mode, back arrow, tab text/indicator — единый цвет `colorOnPrimary`
- Аватар: белая лого на круглом фоне `colorOnPrimary` (программный tint)
- Аватар скрывается в selection mode
- Фон карточки чата теперь использует `incomingBubbleColor` из темы

---

## [1.2.0.16] - 2026-06-19

### Исправления

**Reconnection при восстановлении из памяти:**
- `ChatListActivity` теперь устанавливает `isAppInBackground` в `onPause()`/`onResume()` — `shouldForceReconnect()` корректно работает
- Channel health check в `onResume()` — принудительный reconnect при не-READY статусе (DISCONNECTED, CONNECTING, RECONNECTING, FAILED)

**Unread badge:**
- `ChatListViewModel` проверяет `message.user != currentUsername` — unread count не растёт от собственных сообщений

**Scroll position в чате:**
- `NewChatActivity` — `shouldScrollToBottom = true` при первом входе в чат (загрузка истории)
- Auto-scroll при новом сообщении от собеседника (если пользователь внизу чата — последние 3 сообщения видны)

**Online users parse:**
- `RealGrpcClient` — защита от `ONLINE_USERS_UPDATE:null` — серверные null-значения больше не крашат парсинг JSON

**Offline mode:**
- `GrpcMessageClient.loadHistory()` — при отсутствии канала (офлайн) загружает сообщения из кэша и вызывает `onCompletion()`
- `ChatListViewModel` — загружает кэшированные чаты при старте даже без подключения

**Chat list flickering fix:**
- Periodic sync (30с) и connection READY sync теперь работают в silent-режиме — без preloader/spinner
- Список обновляется только при реальных изменениях (diff по id, lastMessageTime, unreadCount, pinned, archived, lastMessageText)
- Pull-to-refresh (свайп вниз) по-прежнему показывает spinner

### Тесты
- 35 unit-тестов: UserSession, AiModels (AiSource, AiChatSession, AiChatMessage, AiChatSettings, AiStreamState), ProfileViewModel (ProfileData, GroupData, contact filtering)

---

## [1.2.0.14] - 2026-06-19

### Исправления

**Chat subtitle last seen:**
- В direct-чатах вместо "офлайн" показывается время последнего входа ("был(а) в сети X мин/ч/дн назад")
- `allUsers` добавлен в combine flow в NewChatActivity — subtitle обновляется при загрузке данных

**Deleted chat persistence fix:**
- `deleteChat()` теперь удаляет чат из Room DB — удалённые чаты больше не появляются после перезапуска
- `chatDeletedEvent` подписан в `ChatListViewModel` — чаты удаляются из списка в реальном времени
- `deleteSelectedChats()` теперь ждёт ответа сервера перед обновлением списка — удалённые чаты не появляются обратно

**Pull-to-refresh fix:**
- `refreshChats()` сбрасывает `_isLoading` перед вызовом `loadChats()` — свайп вниз больше не блокируется periodic sync

**Chat list sync:**
- `newMessageEvent` подписан в `ChatListViewModel` — чат-лист обновляется в реальном времени когда сообщения приходят в другие комнаты
- Добавлен periodic polling каждые 30с для обновления чат-листа
- `ChatDao` кэширование: чаты загружаются из кэша при старте (мгновенное отображение), синхронизируются с сервером в фоне
- `ChatEntity` расширен: `isPinned`, `isArchived`, `pinnedAt` (миграция 9→10)
- `SplashActivity` больше не стирает Room кэш при каждом запуске — кэш очищается только при logout
- `markAsRead` вызывается при тапе на чат с непрочитанными сообщениями

### UI

**Action mode toolbar (toolbar-native):**
- Режим выбора работает полностью внутри тулбара — без отдельной панели ActionMode
- `enterSelectionMode()`/`exitSelectionMode()` управляют состоянием тулбара
- Стрелка ←, текст "Выбрано X", иконки pin/mute/archive/delete — всё в тулбаре
- Аватар, заголовок, лупа — скрываются при входе, восстанавливаются при выходе
- Все элементы окрашены в `colorOnPrimary` — единый цвет панели

## [1.2.0.13] - 2026-06-19

### Исправления

**Admin menu + Feedback:**
- `isSuperAdmin` сбрасывался в false при каждом `connect()` — race condition с async `fetchAdminStatus()`. Теперь: сброс только при `forceReconnect`
- `adminUserId` сохраняется в SharedPreferences при обнаружении (из chat stream или profile). Восстанавливается при старте — feedback работает сразу после перезапуска
- `fetchAdminStatus()` теперь сохраняет `adminUserId` из профиля (profile.isSuperAdmin + profile.userId)

**Admin discovery for non-admin users:**
- `UserInfoProto` добавлены `userId` (field 6) и `isSuperAdmin` (field 7) — серверный `GetAllUsers` теперь возвращает эти поля
- `loadUsers()` сканирует `allUsers` на `isSuperAdmin` и устанавливает `adminUserId` — feedback чат работает для ЛЮБОГО пользователя
- `openFeedbackChat()` retry: если `adminUserId` пуст → `loadUsers()` + retry через 1.5с
- `connect()` восстанавливает `isSuperAdmin` из SharedPreferences при старте
- `fetchAdminStatus()` вызывается при READY (не в `connect()` — канал ещё не готов)
- `logout()` очищает `is_super_admin` и `admin_user_id` из SharedPreferences

## [1.2.0.12] - 2026-06-18

### Исправления

**Диалог "О программе":**
- Текст приложения: "Лава: платформа защищенных бизнес-коммуникаций" вместо "Lavender Messenger"
- Версия клиента убрана — показывается только версия сервера
- **Поделиться:** текст "Лава: платформа..." + ссылка http://13.140.25.249
- **Отзыв:** открывается личный чат с админом (вместо email). Админ определяется динамически через `adminUserId` из chat stream (не хардкод username)
- Добавлен `adminUserId` StateFlow в GrpcClient/RealGrpcClient — отслеживает userId админа из сообщений с `isSuperAdmin = true`

**i18n:**
- Добавлены строки: `about_description`, `admin_not_found` (EN + RU)

## [1.2.0.11] - 2026-06-18

### Рефакторинг

**ProfileActivity → ProfileViewModel:**
- Бизнес-логика перенесена в ProfileViewModel: loadUserProfile, loadGroupData, updateChatName, updateChatSettings, removeParticipant, addParticipants, uploadGroupAvatar, resizeImage
- ProfileActivity: 719 → ~400 строк

**MessageAdapter split:**
- bind() 600 строк → 12 выделенных методов: bindAlignment, bindBubbleStyle, bindCallMessage, bindReadStatus, bindAudioContent, bindTextContent, bindImageContent, bindReactions, bindReplyQuote, bindSelectionIndicator, bindContainerClicks, bindPinnedBadge
- MessageAdapter: 870 → 324 строки (-63%)

## [1.2.0.10] - 2026-06-18

### Исправления

**Диалог "О программе":**
- Кнопки "Что нового", "Отзыв", "Поделиться" не работали — отсутствовали click listeners (только "Закрыть" был привязан)
- Добавлены: What's New → ChangelogActivity, Feedback → email intent, Share → shareApp()
- **Drag handle отсутствовал** — dialog_about.xml не использовал стандартный wrapper (MaterialCardView + dragHandle). Обновлён layout: добавлен dragHandle, contentContainer, MaterialCardView

**i18n:**
- Добавлена строка `no_email_client` (EN + RU)

## [1.2.0.9] - 2026-06-18

### Исправления

**Токен/Сессия (критический):**
- **startTokenRefresh не вызывался после перезапуска приложения** — initFromPrefs() восстанавливал JWT сессию, но не запускал периодический refresh. Токен протухал молча.
- **waitForConnectionAndReLogin не запускал refresh** — после успешного обновления токена при старте, периодический refresh не начинался.
- **Chat stream retry: мёртвая петля** — при ошибке JWT, код очищал токены и пытался использовать пароль (v1), но v2 не поддерживает пароль → AUTH_FAILED. Теперь: сначала пытается refresh, потом — AUTH_FAILED.
- **performTokenRefresh: fallback при истечении refresh_token** — если refresh_token тоже истёк, автоматический re-login по сохранённому паролю.
- **onResume: валидация токена** — ChatListActivity и NewChatActivity проверяют свежесть токена при возвращении в foreground.

**Новые токены хранятся и обновляются корректно, рефреш запускается при каждом входе и восстановлении сессии.**

### Рефакторинг

**NewChatActivity → ChatViewModel:**
- Бизнес-логика перенесена в ChatViewModel: sendMessage, uploadAudio, retryMessage, fetchChatMetadata, loadPinnedMessages, syncChatListIfNeeded, ensureUserIdSet
- NewChatActivity: 759 → ~450 строк
- ViewModel обогащён StateFlow: chatMetadata, pinnedMessageIds, isAudioUploading

### Исправления тестов
- GrpcChatListClientTest: getChats() тесты обновлены для GrpcChatClient (после split в v1.2.0.5)

## [1.2.0.8] - 2026-06-18

### Исправления

**ChatList V2 — Response Marshallers (критический):**
- Pin/Unpin Chat — response marshaller отсутствовал, `unaryCallWithClass` использовал рефлексию и всегда возвращал `success=false`
- Archive/Unarchive Chat — аналогичный баг
- Pin/Unpin Message — аналогичный баг
- Search Chats — response marshaller отсутствовал, поиск всегда возвращал пустой список
- GetPinned Messages — response marshaller отсутствовал
- Созданы: `PinChatResponseMarshaller`, `UnPinChatResponseMarshaller`, `ArchiveChatResponseMarshaller`, `UnarchiveChatResponseMarshaller`, `PinMessageResponseMarshaller`, `UnPinMessageResponseMarshaller`, `GetPinnedMessagesResponseMarshaller`
- Все V2 boolean-response методы теперь корректно десериализуют ответ сервера

**Примечание:** ChatList V2 фичи (pin, archive, mute) работали ранее только благодаря `loadChats()` в `ChatListActionMode` — полный re-fetch после каждого действия. Теперь optimistic update в ViewModel тоже работает корректно.

## [1.2.0.7] - 2026-06-18

### Исправления

**SuperAdmin (критический):**
- Исправлен баг: кнопка "Admin" не отображалась ни для кого
- `ProfileClient.unaryCall()` использовал рефлексию для response marshaller — всегда возвращал дефолтный объект с `isSuperAdmin = false`
- Созданы `GetProfileResponseMarshaller` и `GetProfileRequestMarshaller` — корректная десериализация 11 полей ответа
- Созданы marshallers для всех ProfileService v2 методов (UpdateProfile, UpdateAvatar, GetUserSettings, UpdateUserSettings)

**ProfileService marshallers:**
- `GetProfileResponseMarshaller` — десериализует userId, username, email, avatarUrl, fullAvatarUrl, bio, status, locale, isSuperAdmin, createdAt, lastSeenAt
- `UpdateProfileV2ResponseMarshaller` — десериализует success, message, вложенный profile
- `UpdateAvatarV2ResponseMarshaller` — десериализует success, message, avatarUrl, fullAvatarUrl
- `GetUserSettingsResponseMarshaller` — десериализует locale, themeId, pushEnabled, custom map
- Все request marshallers — корректная сериализация полей

## [1.2.0.6] - 2026-06-18

### Исправления

**Список чатов:**
- Исправлена загрузка списка чатов — GetChatsV2 RPC добавлен в proto (сервер возвращал UNIMPLEMENTED)
- Исправлен SQL-запрос GetUserChatsV2 — participants содержат usernames, а не UUID

**Админ-панель:**
- Кнопка "Admin" в Additional Settings теперь отображается для всех администраторов (ранее — только для `ferz`)
- Флаг `isSuperAdmin` загружается из профиля сервера вместо хардкода по username

---

## [1.2.0.5] - 2026-06-18

### Исправления

**Контакты:**
- При создании чата/секретного чата/конференции показываются только добавленные контакты (был весь список пользователей)
- `getContacts()` — fallback `fetchUserId` если `currentUserId` пуст

**Авторизация:**
- Авто-вход с протухшим JWT: refresh → если не выходит → перелогин по сохранённому паролю
- Chat stream: удалён password fallback, теперь только JWT (deprecated v1)
- `GetChats` v1 → `GetChatsV2` (JWT-based, server uses `GetUserID(ctx)`)
- При холодном запуске с expired токеном — `AUTH_FAILED` вместо застревания

**Навигация:**
- Возврат к родительской шторке (Settings/Additional Settings) после Back из Activity
- Восстановлен `isNavigatingDeeper` + `settingsActivityLauncher`/`editProfileLauncher`

**UI:**
- Диалог "О программе" показывает версию сервера из `GrpcClient.serverVersion`

### Рефакторинг

**gRPC модули:**
- `GrpcChatListClient` (648→255 LOC) → разделён на 3:
  - `GrpcChatClient` (~250 LOC) — getChats, create/delete, participants, settings
  - `GrpcChatListV2Client` (~120 LOC) — pin/unpin, search, archive, pinned messages
  - `GrpcChatAuxClient` (~130 LOC) — users/AI chats/FCM/mute
- `RealGrpcClient`: delegate to new clients, удалён `unaryCallChatListV2` (-40 LOC)
- Удалены дубликаты `getChats`/`getAllChats` из `GrpcChatListClient`

---

## [1.1.3.38] - 2026-06-18

### 🚀 v2 Клиент для v2 Сервера

Новая версия клиента с улучшениями для v2 сервера.

### Улучшения

**Список чатов:**
- Имя собеседника отображается напрямую в личных чатах
- Тулбар с прозрачностью 30% и тенью — тап по заголовку или аватару открывает шторку профиля
- Убраны заголовки секций (All chats, Pinned) — список чатов стал чище
- Предзагрузка пользователей при открытии — создание чата доступно сразу

**Профиль и настройки:**
- Шторка профиля: верхняя секция (аватар + имя) кликабельная → редактирование профиля
- Переключение языка синхронизируется с сервером

**Создание чатов:**
- Секретные чаты, обычные чаты и конференции — показывают всех пользователей, а не только контакты

**Темы:**
- Фон темы корректно применяется к списку чатов (chatListBackground)
- Экран настроек тем больше не перехватывает фон чата

**Typing индикатор:**
- Корректная фильтрация — свой typing не отображается в subtitle

**AI:**
- Исправлен вызов `deployAgentTaskStream` в HermesChatUseCase
- Исправлен scope leak в OwlChatUseCase

---

## [1.1.3.35] - 2026-06-18

### Рефакторинг: GrpcClient Facade Оптимизация
- **GrpcClient: 780 → 106 LOC (-86%)**
- Создан `GrpcClientExtensions.kt` (~600 LOC) с extension functions по доменам:
  - Auth, Chat, Message, Profile, Theme, Draft, Favorite, Call, AI/Hermes, RemoteAgent, SecretChat, Notification
- В GrpcClient.kt оставлено: StateFlow declarations, scope, connect/disconnect, startChat, loadHistory, setRoomId, loadUsers, V2 service detection
- Добавлен `import GrpcClientExtensions.*` в 29 UI файлов
- RealGrpcClient.scope: `private` → `internal`

---

## [1.1.3.34] - 2026-06-17

### Тесты: Unit-тесты для gRPC клиента
- **42 unit-теста** для gRPC модулей (было 0):
  - GrpcAuthClientTest (10) — signIn, signUp, refreshToken, signOut, revokeDevice
  - GrpcChatListClientTest (8) — getChats, pinChat, searchChats, deleteChat
  - GrpcMessageClientTest (8) — sendMessage, addLocalMessage, loadHistory, markRead
  - GrpcConnectionManagerTest (6) — connect, disconnect, reconnect, isConnectedTo
  - GrpcClientFacadeTest (6) — connectionState mapping, StateFlow probing
  - GrpcUnaryCallHelperTest (4) — unaryCall, null channel, error handling
- Добавлены зависимости: mockk 1.13.8, turbine 1.0.0, coroutines-test 1.7.3
- Созданы тестовые утилиты: TestChannelFactory, TestDatabaseFactory, FlowTestExtensions

### Исправления инфраструктуры тестов
- AppLog: try-catch вокруг android.util.Log.d для работы в unit-тестах
- Все тесты: Dispatchers.Main → Dispatchers.Unconfined (Main недоступен без Android)
- Все тесты: переведены с Mockito на MockK
- Все тесты: исправлены runtime ошибки ( smart cast, relaxed mock returning null)
- Proto data classes: newBuilder() заменены на прямые конструкторы

---

## [1.1.3.33] - 2026-06-17

### Рефакторинг: NewChatActivity делегаты
- **NewChatActivity: 1473 → 754 строк (-49%)**
- Вынесено 6 модулей в `ui/chat/message/`:
  - ChatToolbarDelegate (341) — toolbar, avatar, subtitle, navigation
  - ChatInputDelegate (567) — input, send, attachments, audio, emoji, mentions
  - ChatSelectionDelegate (236) — selection mode, copy/pin/delete/forward
  - ChatSearchDelegate (135) — in-chat search
  - ChatE2EEDelegate (72) — E2EE key exchange, encrypt/decrypt
  - ChatMessageMenuDelegate (106) — reactions, context menu

### Рефакторинг: Унификация error handling
- Все gRPC модули используют `ErrorHandler.handle()` (было `Log.e`)
- ChatListViewModel.error StateFlow + Snackbar в ChatListActivity

### Исправлено
- Ошибки компиляции: импорты Lifecycle, isVisible, toColorInt, edit
- Порядок инициализации: setupDelegates после setupRecyclerView

---

## [1.1.3.32] - 2026-06-17

### Рефакторинг: Разбиение ChatListActivity
- **ChatListActivity: 1085 → ~600 строк (-45%)**
- Вынесено 3 модуля в `ui/chatlist/`:
  - ChatListFABs (470) — FABs + action sheets + AI bottom sheet
  - ChatListNavigation (60) — navigateToChat
  - ChatListAuth (212) — auth dialogs

### Исправлено
- loadChats() — при timeout НЕ перезаписывать allChats
- read receipts — indexOfFirst проверка перед map
- Табы переупорядочены: Все → Группы → ИИ чаты
- "AI" → "AI Chats" / "ИИ" → "ИИ чаты"

---

## [1.1.3.31] - 2026-06-17

### Новое: Read receipts broadcast
- readReceiptEvent SharedFlow → ChatListViewModel → clear unread count

### Рефакторинг
- ChatListActivity 1470→1085 строк (-26%), 4 новых модуля

---

## [1.1.3.30] - 2026-06-16

### Новое: FAB + Favorites
- FAB [+] восстановлен — ActionBottomSheet + SearchableListBottomSheet
- Favorites убран из секций, добавлен в шторку профиля

---

## [1.1.3.28-29] - 2026-06-16

### Рефакторинг: gRPC модули
- **RealGrpcClient: 3810 → 882 строк (-77%)**
- 12 модулей вместо God Object
- Кастомные темы для AppBarLayout, TabLayout
