# ADL — TASK 2

**ROWIN — Digital Wardrobe & Outfit Planner**

Name: Edwin Pious | Roll No: ______ | Class: ______

---

## Practical 3 — Aim:
Develop an Android application based on given ps using a local database such as SQLite / Room for locally required data.

## Practical 4 — Aim:
Perform data synchronization with Firebase / cloud services for given ps.

## Practical 5 — Aim:
Implement appropriate network connectivity for data exchange using mobile services for given ps.

---

## Theory:

### 1) Database Used — Cloud Database: Firebase Firestore
- Firebase is used as the cloud backend for ROWIN. Cloud Firestore is used as the cloud database for storing and retrieving structured app data.
- It can maintain info like:
  - User profile — name, email, phone, location
  - Wardrobe items — name, category, colour, season, occasion, pattern, style, material, AI description
  - Item metadata — createdAt, hasImage flag
- Firestore has no tables — data is stored as documents in 2 collection paths, keyed by the account email (the stable join key):
  - `users/{email}` → profile mirror
  - `users/{email}/items/{itemId}` → wardrobe items

### 2) Local & Cloud Storage
- Room (SQLite) is the local database — file `rowin.db` with 4 tables: **users, clothing_items, outfits, outfit_items** (junction table linking outfits to items). Room is the offline source of truth.
- Photos are stored **on the device only** by ImageStore (app folders: wardrobe_images, profile_images). Images are NOT uploaded to Firestore — the cloud document only carries a `hasImage` flag.
- Storage Flow:
  - Camera / Gallery → Android App Storage (ImageStore) → Room DB → Firestore (item document with hasImage flag)
- Because Room is the source of truth, the whole wardrobe works fully offline; the Firestore mirror keeps the same structured data in the cloud. Stored photos can be viewed by the user in My Closet, item details and the outfit planner.

### 3) Data Synchronization with Cloud
- ROWIN uses Firebase as its cloud backend to maintain centralized user info.
- Flow:
  - App (WardrobeRepository) → FirebaseSyncManager → Firebase Firestore → Cloud DB
- eg: user adds a clothing item → item is saved in Room → repository hook mirrors it to Firestore → status shows "Synced just now".
- When the user signs in, a snapshot listener (addSnapshotListener) streams remote item documents back into Room, so any change in the cloud is reflected live on the device; conflicts are resolved newest-wins.
- Profile edits (name, phone, location) also mirror to `users/{email}`. Firebase status is shown in App Settings (Connected / local-only mode).

### 4) Network Connectivity
- Support funcs like:
  - User login / registration (local, works offline)
  - Wardrobe & profile cloud sync (Firestore)
  - AI services — clothing image analysis, outfit generation & style chatbot (Groq API over HTTPS)
  - Network monitoring
- Flow:
  - ROWIN Android App → Internet / Mobile network → Firebase / Groq API → Firestore DB / AI response
- A NetworkMonitor (ConnectivityManager) exposes online/offline state. When offline, the app runs in LOCAL_ONLY mode — writes stay in Room and are pushed to the cloud when connectivity returns.

### 5) Data Used in ROWIN
- **User Data** → Name, Email-ID, Phone, Location, Avatar, Age, Password hash & salt
- **Clothing Data** → Item name, Category (Tops / Bottoms / Outerwear / Shoes), Colour, Season, Occasion, Pattern, Style, Material, AI description, Photo file, createdAt, remoteId & synced flags
- **Outfit Data** → Outfit name, Season, Occasion, 4 slots (Outerwear / Top / Bottom / Shoes), item references
- **AI Data** → Photo analysis results, chatbot messages, outfit suggestions
- **Alert Data** → Season-drift alerts, wardrobe gaps, stale-item alerts, last-notified time (24-h dedupe), notification status

### 6) Security & Access Control
- ROWIN handles user accounts & personal wardrobe data, so authentication & controlled access are important.
- Local authentication: passwords are stored as salted PBKDF2 hashes (never plain text); the session is persisted with DataStore so login survives restarts.
- Every DB query is scoped by userId — no screen can read another user's wardrobe.
- Firestore documents are keyed by the account email; access is controlled by Firestore Security Rules (currently test mode — should restrict each email to its own documents before the 30-day expiry).
- Photos never leave the device, so personal images are not exposed to the cloud.

### 7) Project Navigation Flow
- **Auth flow:** Login → Register (name, email, password) → App
- **Main tabs (bottom navbar):**
  - Home → greeting + My Closet summary card + recently added → quick cards to Closet / Planner / Profile
  - My Closet → search + category filters → Add Clothing (Camera / Gallery) → AI analysis panel → item saved → item detail / edit
  - Outfits → 4-slot outfit planner → save outfit → outfit detail → saved outfits
  - Profile → edit profile (avatar, phone, location) → AI Style Assistant (chat) → AI Alerts → App Settings (Firebase status, network, notifications) → Sign out

### 8) RESULT
- A functional Android application — 'ROWIN' — acts as a personal digital wardrobe: users register / login, add clothing via camera or gallery with AI-generated descriptions, plan outfits in a 4-slot planner, and receive AI wardrobe alerts & styling help through a chatbot.
- Room (local SQLite) is the source of truth; Firebase Firestore is used as the cloud database for synchronization; the Groq API provides the AI features.
