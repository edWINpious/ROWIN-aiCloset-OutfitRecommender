# ROWIN 👔🧥

**Your wardrobe, beautifully organized — a digital closet & AI outfit planner for Android.**

ROWIN is an Android application that digitizes your wardrobe. Photograph your clothes, let AI catalogue them, plan outfits in seconds, and get intelligent suggestions about what to wear — all in a clean, green-themed interface. Built with Kotlin, XML layouts and Fragments as an Application Development Lab project (Fr. C. Rodrigues Institute of Technology, Vashi).

---

## 📱 What it does

- **Snap or pick your clothes** — capture items with the in-app camera (CameraX) or choose from your gallery.
- **AI does the paperwork** — the Groq vision model analyses each photo and auto-fills a description, so adding items takes seconds.
- **Browse a closet that's actually organized** — search instantly and filter by category (Tops, Bottoms, Outerwear, Shoes).
- **Plan outfits** — combine items into outfits across four slots (Outerwear / Top / Bottom / Shoes), save them, and revisit them anytime.
- **Ask a stylist** — a wardrobe-aware AI chatbot answers questions like *"what should I wear for an interview this weekend?"* using only the items you own.
- **Get smart alerts** — the AI engine watches your wardrobe and notifies you about seasonal gaps, unused items, and stale entries.
- **Own your data** — everything is stored locally, works fully offline, and syncs to the cloud so your wardrobe follows you across devices.

---

## ✨ Features

### Accounts & profile
- Register / login with **Firebase Authentication** (email + password); the same account works on every phone
- Offline sign-in for accounts that have signed in on the phone before (salted **PBKDF2** hash kept locally — passwords never stored in plain text)
- Accounts created before Firebase Auth was added are upgraded automatically on their next login
- "Forgot password?" emails a Firebase reset link
- Session persistence — stay logged in across app restarts and device reboots
- Editable profile: name, email, phone, location, avatar (camera or gallery)

### Wardrobe (My Closet)
- Add clothing via **real in-app camera** (shutter, flip, torch) or gallery picker
- **AI clothing analysis**: photo → auto-generated description via Groq multimodal model
- Item attributes: name, category, colour, season, occasion, pattern, style, material
- Live search + category filter chips; friendly empty states
- Item detail & edit screens; delete cascades cleanly (photo removed too)

### Outfit planner
- Four-slot planner: OUTERWEAR · TOP · BOTTOM · SHOES
- Name, season and occasion per outfit; save, edit, view and delete outfits
- Saved Outfits gallery

### AI features (Groq API)
- 👁️ **Vision** — clothing photo analysis with structured JSON results
- 💬 **Chatbot** — style assistant grounded in your actual wardrobe and saved outfits, limited to fashion topics
- 🧠 **Outfit suggestions** — recommendations composed from your items
- 🔔 **AI alerts** — season-drift, wardrobe-gap and stale-item detection with Android system notifications (24-hour quiet period per alert, kept across restarts); tapping one opens the matching screen

### Local database (Room / SQLite — `rowin.db`, schema v3)
| Table | Purpose |
|---|---|
| `users` | accounts: name, email, phone, location, avatar, Firebase uid, offline password hash & salt |
| `clothing_items` | wardrobe items + sync metadata (`remoteId`, `synced`, `updatedAt`) |
| `outfits` | saved outfits + sync metadata (`remoteId`, `synced`, `updatedAt`) |
| `outfit_items` | junction table: outfit ↔ item slots |
| `sync_tombstones` | deletes waiting for the server to confirm them |

### Cloud sync (Firebase Auth + Cloud Firestore)
- Room is the offline source of truth; every write is mirrored to Firestore
- **Two-way live sync**: snapshot listeners stream adds, edits **and deletes** from other devices back into Room
- Conflicts: the copy with the newer `updatedAt` wins (`SyncMerge`, unit-tested)
- Offline edits and deletes are queued and uploaded when the phone reconnects
- Firestore layout — one top-level collection per kind of data:

  | Collection | Document | Contents |
  |---|---|---|
  | `users` | `{uid}` | profile: name, email, phone, location, age |
  | `clothingItems` | auto id | `ownerId` + name, category, colour, season, occasion, pattern, style, material, AI description, `hasImage` |
  | `outfits` | auto id | `ownerId` + name, season, occasion, AI note, `itemIds` (clothingItems ids) |

- Every document is owned by a Firebase Auth uid; `firestore.rules` lets each account read and write only its own data
- The old layout (`users/{email}/items/…`) is migrated per account on its first login after the update, then removed
- Photos stay on-device (Cloud Storage needs the paid Blaze plan); the cloud document carries a `hasImage` flag
- Graceful degradation: without `google-services.json` the app runs in **local-only mode** with everything still functional
- Sync status surfaced in-app (Profile → App Settings)

### Network & connectivity
- `NetworkMonitor` (ConnectivityManager) exposes live online/offline state
- All AI services run over HTTPS (Groq API)

### UI
- XML layouts + one Fragment per screen (ViewBinding, Material Components), custom green design system (Manrope + Inter typography)
- Single activity with a back-stack `NavController`; the current screen survives rotation and process death
- Bottom navigation: **Home · Closet · Outfits · Profile**
- Home dashboard with greeting, closet summary, recently added items, AI Stylist shortcut
- Notification bell with unread badge, splash screen held until the session is known

---

## 🛠️ Tech stack

| Layer | Technology |
|---|---|
| Language | Kotlin, Coroutines & Flow |
| UI | XML layouts, Fragments, ViewBinding, Material Components |
| Architecture | Manual DI (`AppContainer`), MVVM (ViewModel per feature), unidirectional data |
| Local DB | Room 2.8 (SQLite) with KSP |
| Session | DataStore Preferences |
| Camera | CameraX (LifecycleCameraController) |
| Images | ExifInterface (orientation) |
| Cloud | Firebase BOM 34 — Authentication + Cloud Firestore |
| AI | Groq API (`qwen` vision model for analysis, `gpt-oss` for chat) |
| Security | Firebase Auth + Firestore rules, PBKDF2WithHmacSHA256 (32k iterations) for offline sign-in |
| Tests | JUnit + coroutine tests (29 unit tests: analyzer, recommender, greeting, sync merge rules, alerts) |

**Requirements:** minSdk 24 (Android 7.0+) · targetSdk 37 · Android Studio with AGP 9+

---

## 🚀 Getting started

1. **Clone & open** the project in a recent Android Studio (AGP 9 requires a 2025.x+ release).
2. **AI features**: get a free key at [console.groq.com](https://console.groq.com), then add it to `local.properties` in the project root:
   ```properties
   GROQ_API_KEY=gsk_your_key_here
   ```
3. **Cloud sync**: drop your `google-services.json` (Firebase console → project settings) into the `app/` folder. Without it, the app simply runs local-only. Then, in the Firebase console:
   - **Authentication → Sign-in method → Email/Password → Enable**
   - **Firestore Database → Rules** → paste [`firestore.rules`](firestore.rules) → **Publish**
4. **Run** ▶ on an emulator or device. Register an account and start adding clothes!

> The Gradle Google-Services plugin activates only when `google-services.json` exists, and the Groq key is injected into `BuildConfig` at compile time — so the build never breaks over missing secrets.

> **Note on the Groq key:** a key compiled into an APK can be extracted by anyone who has the APK. That's fine for a lab demo; a production app would call Groq through its own server.

---

## 📂 Project structure

```
app/src/main/java/com/example/rowin_adlproject/
├── MainActivity.kt   # Single activity: splash, session gate, chrome, fragment host
├── data/             # Room entities & DAOs, repository, AuthService, FirebaseSyncManager,
│                     # SyncMerge, ImageStore, NetworkMonitor, AlertsEngine, PasswordHasher
├── ai/               # Groq client, clothing analyzer, chat service, outfit recommender
└── ui/
    ├── auth/         # Login & register
    ├── home/         # Home dashboard
    ├── closet/       # My Closet, add clothing, camera, item detail
    ├── outfits/      # Outfit planner, outfit detail, saved outfits
    ├── profile/      # Profile, edit profile, settings / sign-out sheets
    ├── chat/         # AI style assistant
    ├── alerts/       # AI alerts feed
    ├── components/   # Display-name helpers
    └── view/         # NavController, HostedFragment, sheets, shared view helpers
app/src/main/res/layout/   # One XML layout per screen, sheet and list item
firestore.rules            # Cloud Firestore security rules
```

---

## 🧪 Testing

```bash
./gradlew testDebugUnitTest   # 29 unit tests across AI, sync and domain logic
./gradlew assembleDebug       # build the debug APK
```

---

## 🎓 Academic context

Built for the **Application Development Lab (CELBC507)** practical list: UI & navigation, Room persistence, Firebase synchronization, network connectivity, camera & multimedia, AI chatbot, ML-based clothing analysis, and AI alerts — all exercised by this single app.

---

*Made with ☕ by Edwin Pious.*
