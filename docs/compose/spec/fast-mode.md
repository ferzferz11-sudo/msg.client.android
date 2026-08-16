---
feature: fast-mode
status: delivered
updated: 2026-08-16
branch: feat/1.4.0.x
commits: a861658..2495ba8
---

# Fast Mode — Chat List Performance Toggle

## Report

## [S1] Problem
On low-end devices or large chat lists, animations, avatar loading (Glide), and heavy graphics cause lag. Users need a "fast mode" that strips visual overhead for a snappier experience.

## [S2] Design

### Menu Entry
- Location: chat list overflow menu (`chat_list_menu.xml`), below "Search"
- ID: `action_fast_mode`
- Title: dynamically "Mode: Fast" or "Mode: Full" depending on current state
- Icon: `ic_speed` (⚡) or `ic_check` — no icon, text-only menu item

### Behavior
- Tap toggles between Full ↔ Fast
- Toast confirms: "Fast mode enabled" / "Full mode enabled"
- Setting persists immediately to server via `updateUserSettings(custom = mapOf("chat_list_mode" to "fast"|"full"))`
- On app start / login: read from `getUserSettings().custom["chat_list_mode"]`, default "full"

### Fast Mode Changes (ChatAdapter + ChatListActivity)
1. **No avatars:** Hide `ivChatAvatar`, show placeholder icon (no Glide calls)
2. **No animations:** Disable `DefaultItemAnimator` (set `itemAnimator = null`), skip `layoutAnimation`
3. **No border:** Skip avatar border logic
4. **No online dots:** Hide online status indicator
5. **No status dots:** Hide connection status animations

### Full Mode
- Current behavior, no changes

### Persistence Contract
- Server proto: `UpdateUserSettingsRequestProto.custom` (field 4, `map<string,string>`)
- Key: `"chat_list_mode"`, values: `"fast"` | `"full"` (absent = full)
- Client reads on `ChatListActivity.onResume` → `getUserSettings()`
- Client writes on toggle → `updateUserSettings(custom = ...)`

### Error Handling
- If server write fails: setting reverts, Toast "Failed to save setting"
- If server read fails: default to Full mode
- Setting cached in SharedPreferences as fallback (`chat_list_mode` key)

## [S3] Out of Scope
- Fast mode for message list (NewChatActivity) — future work
- Per-chat fast mode — global only
- Fast mode for AI chats — same adapter, applies globally

## Tasks
- [ ] T1: Add `action_fast_mode` menu item to `chat_list_menu.xml` + handle in `ChatListToolbar.kt` — acceptance: menu item visible, tap toggles state (covers: S2)
- [ ] T2: Add `fastModeEnabled` state to `ChatListViewModel` + SharedPreferences cache — acceptance: state persists across activity recreation (covers: S2)
- [ ] T3: Wire persistence to server `updateUserSettings`/`getUserSettings` via `custom` map — acceptance: setting survives app restart (covers: S2)
- [ ] T4: Modify `ChatAdapter.bind()` to skip avatars/animations in fast mode — acceptance: no Glide calls, no border logic when fast mode on (covers: S2)
- [ ] T5: Modify `ChatListActivity` to disable `DefaultItemAnimator` and `layoutAnimation` in fast mode — acceptance: no animations when fast mode on (covers: S2)
- [ ] T6: Add unit tests for fast mode state persistence and adapter behavior — acceptance: tests pass (covers: S2)
- [ ] T7: Add string resources EN+RU for menu item and toast — acceptance: localized strings (covers: S2)
