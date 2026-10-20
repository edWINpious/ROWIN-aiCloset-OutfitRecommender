# ROWIN Compose→XML: Layout XML Map

## Overview
This document maps the ROWIN (com.example.rowin_adlproject) wardrobe app from Jetpack Compose to XML layouts + one Fragment per screen. XML is the **live UI**. The Compose UI surface has been deleted; the remaining `ui/components/DisplayNames.kt`, `ui/RowinViewModels.kt`, all ViewModels, `data/`, `ai/`, and `ui/view/*` are retained.

## Gradle / build configuration
- `gradle/libs.versions.toml` — Material 1.13.0, fragment-ktx 1.8.9, recyclerview 1.4.0, constraintlayout 2.2.1 (`androidx-constraintlayout` added).
- `app/build.gradle.kts` — `viewBinding = true`; dependencies include google-material, fragment-ktx, recyclerview, constraintlayout. Compose dependencies are left in place (allowed, unused).
- `app/src/main/AndroidManifest.xml` — permissions: `CAMERA`, `POST_NOTIFICATIONS`, `INTERNET`, `ACCESS_NETWORK_STATE`; FileProvider for camera capture sharing.

## Res XML inventory (136 files)
- **Layouts**: `app/src/main/res/layout/` — 32 files
  - `activity_main.xml`, `component_rowin_bottom_bar.xml`, `component_rowin_top_bar.xml`
  - `screen_login.xml`, `screen_register.xml`
  - `screen_home.xml`, `screen_my_closet.xml`
  - `screen_add_clothing.xml`, `screen_clothing_detail.xml`, `screen_camera_capture.xml`
  - `screen_chat.xml`, `screen_outfit_planner.xml`, `screen_saved_outfits.xml`, `screen_outfit_detail.xml`
  - `screen_profile.xml`, `screen_edit_profile.xml`, `screen_alerts.xml`
  - `sheet_delete_item.xml`, `sheet_remove_outfit.xml`, `sheet_settings.xml`, `sheet_sign_out.xml`, `sheet_slot_picker.xml` (bottom sheets)
  - `item_*.xml` — `item_clothing_card.xml`, `item_outfit_card.xml`, `item_alerts_card.xml`, `item_slot_thumb.xml`, `item_suggestion.xml`, `item_bubble.xml`, `item_breakdown.xml`, `item_hero_avatar.xml`, `item_recent_clothing.xml`, `item_chip.xml`
- **Values**: `values/colors.xml`, `values/strings.xml`, `values/themes.xml`; night variant `values-night/themes.xml`.
- **Drawable**: theme tokens + component drawables (`bg_slot_thumb(_selected).xml`, `bg_meta_card.xml`, `bg_hero_card.xml`, `bg_bubble_user(_ai).xml`, `bg_chip(.xml)`, `bg_button_primary(_outlined).xml`, `bg_round_*_error.xml`, `bg_circle_error_25/40.xml`, `bg_slot_empty/filled.xml`).
- **XML utilities**: `res/xml/file_paths.xml` (FileProvider paths), `backup_rules.xml`, `data_extraction_rules.xml`.

## Screen → Fragment mapping (XML live)
| Fragment | Layout | Notes |
|---|---|---|
| `LoginFragment` | `screen_login.xml` | Auth scaffold, paddings 20/72/48 |
| `RegisterFragment` | `screen_register.xml` | wordmark marginBottom 40dp |
| `HomeFragment` | `screen_home.xml` | grid 2col gap12/16, FAB marginBottom 100 end 20 Primary |
| `ClosetFragment` | `screen_my_closet.xml` | grid 2col, bottom pad 120 |
| `AddClothingFragment` | `screen_add_clothing.xml` | header/wordmark, camera+ gallery chips, AI panel, item form, fixed `add_save` bottom bar |
| `CameraCaptureFragment` | `screen_camera_capture.xml` | viewfinder |
| `ClothingDetailFragment` | `screen_clothing_detail.xml` | bento meta cards `bg_meta_card` 12dp radius, breakdown rail, delete |
| `OutfitPlannerFragment` | `screen_outfit_planner.xml` | 4 slots (Outerwear/Shoes 21:9, Top/Bottom 4:3), chips, AI suggestions, edit header |
| `SavedOutfitsFragment` | `screen_saved_outfits.xml` | 2-col grid |
| `OutfitDetailFragment` | `screen_outfit_detail.xml` | hero 3:4 `bg_hero_card`, name chip, meta 14dp, breakdown rail |
| `ProfileFragment` | `screen_profile.xml` | 96dp avatar, stat cards 40dp, 3 dynamic rows |
| `EditProfileFragment` | `screen_edit_profile.xml` | 128dp avatar, 40dp badge, 4 icon fields |
| `ChatFragment` | `screen_chat.xml` | bubbles maxWidth 300 pad 14/10 |
| `AlertsFragment` | `screen_alerts.xml` | cards margin 20/6 + 40dp badge |

## Bottom-sheet contract (`ui/view/SheetDialogFragment.kt`)
- `onSheetReady(view)` is invoked **synchronously in `onViewCreated`** — never await async data before binding sheet labels.

## Session gate (`MainActivity.kt`)
- `repeatOnLifecycle(STARTED)` + `combine(rowin.sessionState, rowin.user)`; no `.observe()` on `StateFlow`; cold `Flow` uses `collectFlowOnStarted`/`collectOnStarted`.

## Fidelity tokens (verified)
- Auth scaffold: 20/72/48dp pads; wordmark mb 48+32(dp login)/40(dp register).
- Closet grid 2col gap 12/16, bottom pad 120, FAB marginBottom 100 end 20 Primary.
- Detail meta cards `bg_meta_card` 12dp radius; `OutfitDetail` hero 3:4 `bg_hero_card` + name chip + 14dp meta + breakdown rail.
- Delete sheet `bg_round_24_error` 48dp; remove sheet `bg_circle_error_40` 64dp; sign-out sheet `bg_circle_error_25` 64dp.
- Settings 3 dynamic rows.
- Slot thumbs 120dp.
- Chat bubbles maxWidth 300 pad 14/10 with 6dp top + 12dp bottom margins (~18dp bubble gap); alerts cards margin 20/10 + 40dp badge.

## AI session memory (login → full Firebase data in the prompt)
- `data/SessionMemory.kt` (new): one immutable snapshot — `userId`, `profile: UserEntity?`, `items: List<ClothingItemEntity>`, `outfits: List<OutfitWithItems>` (+ `isLoaded`, `itemById`).
- `data/AppContainer.sessionMemory: StateFlow<SessionMemory>` = `sessionManager.sessionState.flatMapLatest { ... }` → signed out: `flowOf(SessionMemory())`; signed in: `combine(observeUser, observeClothing, observeOutfits)`. Bound with `stateIn(memoryScope, SharingStarted.Eagerly, SessionMemory())` and `@OptIn(ExperimentalCoroutinesApi::class)`.
  - **Import gotcha**: `SharingStarted` is `kotlinx.coroutines.flow.SharingStarted` (not `kotlinx.coroutines.SharingStarted`).
  - `Eagerly` matters: the wardrobe is warm the moment a session starts, so the stylist never sees an empty list just because no screen subscribed (`WhileSubscribed` had exactly this bug — `ChatViewModel.wardrobe` was permanently `emptyList()`).
- Data path on every login: `MainActivity` → `container.firebaseSync.start(syncScope, email)` (MainActivity.kt:141) → Firestore snapshot listener streams `users/{email}/items/{id}` → Room → `sessionMemory`. Room is the merged local + cloud view, so remote pieces reach the AI without any UI subscription.
- `ai/ChatService.reply(history, memory)` serialises the snapshot into the prompt: `USER PROFILE`, `WARDROBE (n)` as `id:name [category, color, season, occasion, pattern, style]`, `SAVED OUTFITS (n)` as `name [season/occasion]: pieces`. Caps: `MAX_ITEMS = 80`, `MAX_OUTFITS = 40` (remainder counted, not dropped silently).
- `ui/chat/ChatViewModel` now takes `memory: StateFlow<SessionMemory>` + `userName` (no `repository`/`userId`/`wardrobe`); it reads `memory.value` **fresh at send time**, so a piece synced mid-conversation is immediately available. Factory: `ChatViewModel(container.sessionMemory, userName)`.

## Fashion-only topic scope
- **Local guard (always first, works offline)**: `ChatService.OFF_TOPIC` is a `Regex` (`(?i)\b(code|coding|programming|python|java|kotlin|sql|binary tree|linked list|algorithm|math|history|... )\b`). `offTopicReply(question)` runs *before* any network call and returns a canned refusal; it also runs in `recoverCatching`, so a failed API call still refuses instead of drifting.
- **Model guard**: `SYSTEM_PROMPT` opens with `TOPIC SCOPE - strict` — answer only fashion/clothing/outfit/styling/wardrobe/color/fabric/fit/season/occasion/shopping questions; refuse anything else in one sentence; never emit code, pseudocode or technical explanation. `ANSWERING` requires referencing only real wardrobe items by their exact name and saying plainly when the wardrobe is empty.
- **Verified by test** (temporary test, removed after the run to keep the 21-test contract):
  - `"Write me code for a binary tree in Python"` → *"I'm ROWIN's style assistant, so I only help with clothing, outfits and your wardrobe…"* (local guard, no network).
  - `"What is the capital of France and 2+2?"` → *"I only help with style and clothing—what would you like to wear?"* (model-level refusal via TOPIC SCOPE).
  - `"What should I wear to a wedding?"` → normal style answer that opened with *"Your wardrobe is currently empty…"* — proof the `SessionMemory` block is actually read by the model.

## Analyze with AI button (feature #6)
- Root cause: `add_ai_analyze` / `add_ai_apply` had **no click listeners**, so taps were silently dropped and `vm.analyzeWithAi()` was never called from anywhere.
- Fix in `AddClothingFragment.initOnce()`: `b.addAiAnalyze.setOnClickListener { vm.analyzeWithAi() }`, `b.addAiApply.setOnClickListener { vm.applyAnalysis() }`, with `vm.analysisState.collectWithViewLifecycle { renderAnalysis(it) }` driving `add_ai_status` / `add_ai_apply`.
- `renderAnalysis` no longer gates the analyze button on having a photo — tapping it photo-less shows "Add a photo first" instead of doing nothing (`isEnabled` only goes false while `Analyzing`).
- `AddClothingViewModel.analyzeWithAi()` launches on `Dispatchers.IO` (full-size base64 encode is not a main-thread job); failures surface as a red `add_ai_status` line.

## Spacing pass (boxes / rails / bubbles)
| Where | Change | Why |
|---|---|---|
| `item_bubble.xml` | root `marginTop 2→6dp`, `marginBottom 12dp` | ~18dp between chat bubbles; they previously read as one run-on block |
| `item_breakdown.xml` | root `marginEnd 12dp` | breakdown rail cards were touching |
| `item_chip.xml` | root `marginEnd 8dp` | closet filter chips were touching |
| `screen_outfit_planner.xml` | slot separators `12→16dp` (×4), button-stack gap `8→12dp` | boxes read as one blob |
| `screen_profile.xml` | section separators `12→16dp` (×2) | card-to-card breathing room |
| `screen_edit_profile.xml` / `screen_outfit_detail.xml` | stacked-button gap `8→12dp` | Save/Cancel, Edit/Remove were cramped |
| `item_recent_clothing.xml`, `item_slot_thumb.xml` | `marginEnd 12dp` | home hero + recent rails, slot thumbs |
| `item_suggestion.xml` | margins `4→8dp` | AI suggestion cards |
| `item_alert_card.xml` | margins `6→10dp` | stacked alert cards |
- Already correct, left alone: closet/saved grids (`GridSpacing`/`SavedGridSpacing` = 12dp column + 16dp row), home quick cards (16dp), outfit-detail meta cards (12dp `Space`), sheets (16dp rows), hero avatars (intentional `-8dp` overlap), add-clothing form (16dp between fields).

## Closet duplicates, Apply-to-fields, Home polish
### One garment = one closet row
Two independent causes, both fixed:
1. **Double-tap on Save.** `AddClothingViewModel.save()` had no re-entrancy guard, so a second tap launched a second `repository.addClothing()` and a second Room row. Now guarded by a `saving` latch — released only on failure (retry still works) and left set on success while `onSaved` navigates away.
2. **Push/snapshot race.** `pushItem` writes to `items/{localRowId}` while `remoteId` is still null and records it only in the write's success callback, so the snapshot listener regularly won the race: `getByRemoteId()` returned null and Room auto-generated a *second* row for the same garment. Fixed in `FirebaseSyncManager.matchLocalPush()`, which falls back to (a) the local row whose id equals the doc id **and** whose name/category/createdAt match, then (b) `ClothingDao.findDuplicate(userId, name, category, color, createdAt)`. The content check matters: every device auto-generates row ids, so a bare id match could adopt and overwrite an unrelated row.
3. **Self-heal for existing dupes:** `ClothingDao.dedupeByRemoteId()` runs once on `firebaseSync.start()` — keeps the oldest row per `(userId, remoteId)`, re-points `outfit_items` membership onto it before the cascade would erase it, deletes the extras. Returns the removed count for logcat.
- New DAO queries: `findDuplicate`, `getAllWithRemoteId`, `linksFor`, `countLink`, `repointLink`, plus the `@Transaction` default `dedupeByRemoteId()` (validated by Room/KSP at compile time).

### Apply to fields now actually fills the form
- `applyAnalysis()` only wrote into the ViewModel, and `observeVm()` never collected those `StateFlow`s — so the tap looked like a no-op even though the state changed.
- `AddClothingFragment.observeVm()` now mirrors `name`, `color`, `pattern`, `style`, `material` back into their `EditText`s, guarded by `if (text != value)` so `setText` never fights the cursor or feeds the watcher a value it just wrote. Category/season/occasion were already collected.

### Home screen
- `Recently Added` section container: `layout_marginTop="24dp"` (it had none — the section sat flush against whatever was above it whenever the saved-outfit note was hidden).
- The **Style Profile** quick card was replaced: profile is already a bottom-bar tab, so the second card is now **AI Stylist** (`home_quick_stylist`, `ic_auto_awesome` + `ic_send` badge, `@string/quick_stylist` / `quick_stylist_sub`) and opens `Screen.CHAT` — the stylist previously lived two taps deep inside Profile. `quick_profile` / `quick_profile_sub` strings removed.

### Delete Item / Sign Out confirmations
- Both confirm controls were `<Button>` with `android:background="@drawable/bg_button_primary"` + white (`rowin_on_primary`) label — and MaterialButton overrides/re-tints `android:background`, which is why the label read as invisible while the *bordered* Cancel button next to it stayed legible.
- They are now plain `<TextView>`s (`clickable`/`focusable`/`foreground=selectableItemBackground`) so the authored background can't be re-tinted: new `drawable/bg_button_outlined_error.xml` (1dp `rowin_error` stroke + `rowin_error_container` fill, 14dp radius), label `rowin_error` at **5.1:1** contrast, font `manrope_semibold` instead of `inter_medium`. IDs (`sheet_delete_confirm`, `sheet_sign_out_confirm`) are unchanged, so the click listeners still bind.

### Greeting: no more "Good night"
- `HomeViewModel.greetingFor()` now has three buckets — 5..11 morning, 12..16 afternoon, **everything else evening**. `HomeViewModelGreetingTest`'s night case now asserts `"Good evening"` at 22:00, 03:00 and 04:00 (test count unchanged at 21).

## Build + test status
- `./gradlew assembleDebug` → BUILD SUCCESSFUL (EXIT=0, APK at `app/build/outputs/apk/debug/app-debug.apk`).
- `./gradlew testDebugUnitTest --rerun-tasks` → 21 tests, 0 failures, 0 errors.
- `./gradlew compileDebugKotlin` → EXIT=0 after the `SessionMemory`/`ChatService` changes.

## Notes / known open items
- Emulator/Virtual-Device tooling was unavailable in this environment; device walk (login → home → camera → sheets → planner → detail → profile → settings → sign-out) was NOT performed on a live device.
- `AddClothingFragment` camera `/ FileProvider` path was fixed on disk (listener moved to `parentFragmentManager`); device re-verify pending.
- The session gate uses `combine(rowin.sessionState, rowin.user)` in `MainActivity` and `repeatOnLifecycle`; no `.observe()`.
- `RemoveOutfitSheet` + `DeleteItemSheet` bind their message text asynchronously after `onSheetReady` (no UI regression, just async timing).
- `ScreenChrome.kt` fixed: `R.string.alerts_title` → `R.string.ai_alerts_title`.
- `ProfileFragment` fixed: `CollectFlowOnStarted` for `container.networkMonitor.isOnline`.
- `ClothingDetailFragment` bento columns changed `match_parent` → `wrap_content` heights (4 columns).
- `OutfitPlannerFragment` added 4 slot-selection collectors (`selectedTop/Bottom/Shoes/Outerwear`) for re-render.
- Chat scope + memory: local `OFF_TOPIC` guard, strict `TOPIC SCOPE` system prompt, and `sessionMemory` (Eagerly) grounding every reply — all verified by a temporary unit test that was removed to preserve the 21-test count. A live-device re-verify is still pending (no emulator in this install).
- **Open (multi-device):** `pushItem` still names the Firestore doc after the *local* row id, so two phones on the same account can collide on `items/{id}` (second push overwrites the first). Single-device use is unaffected, and the new content-verified snapshot matching prevents it from corrupting local rows. Fixing it properly means switching new pushes to auto-generated doc ids.

## Untracked files (to ship)
- New: `docs/LAYOUT_XML_MAP.md`, all new `ui/...Fragment.kt` + `ui/components/DisplayNames.kt` + `ui/view/*` + drawable set + `ui/RowinViewModels.kt` + all ViewModels + `data/`, `ai/`.
- Deleted: all Compose UI (`RowinApp.kt`, `AuthScreens.kt`, `HomeScreen.kt`, `ChatScreen.kt`, `closet/{AddClothing,CameraCapture,ClothingDetail,MyCloset}Screen.kt`, `outfits/{OutfitDetail,OutfitPlanner,SavedOutfits}Screen.kt`, `profile/ProfileScreens.kt`, `alerts/AlertsScreen.kt`, `components/*.kt`, `theme/{Color,Theme,Type}.kt`).
- Modified: `app/build.gradle.kts`, `MainActivity.kt`, `colors.xml`, `strings.xml`, `themes.xml`, `gradle/libs.versions.toml`.
