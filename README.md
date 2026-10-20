# ROWIN 👔🧥

**Your wardrobe, beautifully organized — a digital closet & AI outfit planner for Android.**

ROWIN is an Android application that digitizes your wardrobe. Photograph your clothes, let AI catalogue them, plan outfits in seconds, and get intelligent suggestions about what to wear — all in a clean, green-themed interface. Built with Kotlin and Jetpack Compose as an Application Development Lab project (Fr. C. Rodrigues Institute of Technology, Vashi).

---

## 📱 What it does

- **Snap or pick your clothes** — capture items with the in-app camera (CameraX) or choose from your gallery.
- **AI does the paperwork** — the Groq vision model analyses each photo and auto-fills a description, so adding items takes seconds.
- **Browse a closet that's actually organized** — search instantly and filter by category (Tops, Bottoms, Outerwear, Shoes).
- **Plan outfits** — combine items into outfits across four slots (Outerwear / Top / Bottom / Shoes), save them, and revisit them anytime.
- **Ask a stylist** — a wardrobe-aware AI chatbot answers questions like *"what should I wear for an interview this weekend?"* using only the items you own.
- **Get smart alerts** — the AI engine watches your wardrobe and notifies you about seasonal gaps, unused items, and stale entries.
- **Own your data** — everything is stored locally, works fully offline, and optionally syncs to the cloud so your wardrobe follows you across devices.

---

## ✨ Features

### Accounts & profile
- Register / login with email + password (salted **PBKDF2** hashing — passwords never stored in plain text)
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
- 💬 **Chatbot** — style assistant grounded in your actual wardrobe
- 🧠 **Outfit suggestions** — recommendations composed from your items
- 🔔 **AI alerts** — season-drift, wardrobe-gap and stale-item detection with Android system notifications (24-hour dedupe)

### Local database (Room / SQLite — `rowin.db`)
| Table | Purpose |
|---|---|
| `users` | accounts: name, email, phone, location, avatar, password hash & salt |
| `clothing_items` | wardrobe items + sync metadata (`remoteId`, `synced`) |
| `outfits` | saved outfits |
| `outfit_items` | junction table: outfit ↔ item slots |

### Cloud sync (Firebase Firestore — optional)
- Room is the offline source of truth; every write is mirrored to Firestore
- Live two-way sync: a snapshot listener streams cloud changes back into Room (newest-wins conflict resolution)
- Collection layout (no SQL tables — document database, keyed by account email):
  - `users/{email}` → profile mirror
  - `users/{email}/items/{itemId}` → wardrobe items
- Photos stay on-device; the cloud document carries a `hasImage` flag
- Graceful degradation: without `google-services.json` the app runs in **local-only mode** with everything still functional
- Firebase status surfaced in-app (App Settings → Connected / Local-only)

### Network & connectivity
- `NetworkMonitor` (ConnectivityManager) exposes live online/offline state
- Offline writes queue in Room and push to the cloud when connectivity returns
- All AI services run over HTTPS (Groq API)

### UI
- 100% Jetpack Compose (Material 3), custom green design system (Manrope + Inter typography)
- Bottom navigation: **Home · Closet · Outfits · Profile**
- Home dashboard with greeting, closet summary, recently added items
- Notification bell with unread badge, splash screen, smooth screen transitions

---

## 🛠️ Tech stack

| Layer | Technology |
|---|---|
| Language | Kotlin, Coroutines & Flow |
| UI | Jetpack Compose, Material 3 |
| Architecture | Manual DI (`AppContainer`), MVVM (ViewModel per feature), unidirectional data |
| Local DB | Room 2.8 (SQLite) with KSP |
| Session | DataStore Preferences |
| Camera | CameraX (LifecycleCameraController) |
| Images | Coil, ExifInterface |
| Cloud | Firebase BOM 34 — Cloud Firestore |
| AI | Groq API (`qwen` vision model for analysis, `gpt-oss` for chat) |
| Security | PBKDF2WithHmacSHA256 (32k iterations), per-user data scoping |
| Tests | JUnit + coroutine tests (21 unit tests: analyzer, recommender, greeting, view-models) |

**Requirements:** minSdk 24 (Android 7.0+) · targetSdk 37 · Android Studio with AGP 9+

---

## 🚀 Getting started

1. **Clone & open** the project in a recent Android Studio (AGP 9 requires a 2025.x+ release).
2. **AI features**: get a free key at [console.groq.com](https://console.groq.com), then add it to `local.properties` in the project root:
   ```properties
   GROQ_API_KEY=gsk_your_key_here
   ```
3. **Cloud sync (optional)**: drop your `google-services.json` (Firebase console → project settings) into the `app/` folder. Without it, the app simply runs local-only.
4. **Run** ▶ on an emulator or device. Register an account and start adding clothes!

> The Gradle Google-Services plugin activates only when `google-services.json` exists, and the Groq key is injected into `BuildConfig` at compile time — so the build never breaks over missing secrets.

---

## 📂 Project structure

```
app/src/main/java/com/example/rowin_adlproject/
├── data/          # Room entities, DAOs, repository, Firebase sync,
│                  # ImageStore, NetworkMonitor, AlertsEngine, PasswordHasher
├── ai/            # Groq client, clothing analyzer, chat service, API keys
├── ui/
│   ├── auth/      # Login & register screens
│   ├── home/      # Home dashboard
│   ├── closet/    # My Closet, add clothing, camera, item detail
│   ├── outfits/   # Outfit planner, outfit detail, saved outfits
│   ├── profile/   # Profile, edit profile, settings
│   ├── chat/      # AI style assistant
│   ├── alerts/    # AI alerts feed
│   ├── components/# Bottom bar, top bar, cards, text fields, icons
│   └── theme/     # Colors, typography (Manrope/Inter), Material theme
└── RowinApp.kt    # Navigation graph + session/sync orchestration
```

---

## 🧪 Testing

```bash
./gradlew testDebugUnitTest   # 21 unit tests across AI + domain logic
./gradlew assembleDebug       # build the debug APK
```

---

## 🎓 Academic context

Built for the **Application Development Lab (CELBC507)** practical list: UI & navigation, Room persistence, Firebase synchronization, network connectivity, camera & multimedia, AI chatbot, ML-based clothing analysis, and AI alerts — all exercised by this single app.

---

*Made with ☕ and Compose by Edwin Pious.*
