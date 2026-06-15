# Заметки сессии 13 — 2026-06-16

## Что сделано

### ChatListActivityV2 — полная реализация (без фрагмента)
- Убрана зависимость от ChatListFragmentV2 — всё в одном Activity
- Прямой RecyclerView + SwipeRefreshLayout в activity_chat_list_v2.xml
- TabLayout с табами All/AI/Groups (фильтрация через ViewModel)
- Toolbar: avatar→ProfileActivity, title→ServersActivity, search/settings icons
- FABs: fabAi (TODO AI chat), fabAddChat→NewChatActivity
- Навигация: favorites→NewChat, hermes→HermesChat, owl→OwlChat, other→NewChat
- Connection status subtitle (connecting/online/offline)

### SplashActivity — маршрутизация v1/v2
- При shouldProceed + наличии server host → ChatListActivityV2
- Без server host → ChatListActivity (v1)
- ChatListActivityV2 сам делает fetchServerInfo и fallback на v1 если нужно

### ChatAdapterV2 — исправление
- Убрано дублирование cachedColors из ViewHolder'ов
- Единый кэш цветов в адаптере, передаётся в ViewHolders как параметры

### AndroidManifest.xml
- Зарегистрирован ChatListActivityV2
- Удалён дубликат RemoteAgentSettingsActivity
- Удалён дубликат LogViewerActivity

### Строки (en + ru)
- connecting / Подключение…
- connection_online / В сети
- connection_offline / Не в сети

### Коммит
- `bd4e22c` — feat: ChatListActivityV2 — full v2 chat list with tabs, navigation, FABs, theme integration

## Следующие шаги (сессия 14)
1. **Selection Mode** — long press = ActionMode toolbar (Pin/Delete/Archive)
2. **Pin Message** — серверные RPC + клиент
3. **Поиск** — SearchView в toolbar + debounce
4. **Тестирование** на dev и prod серверах
