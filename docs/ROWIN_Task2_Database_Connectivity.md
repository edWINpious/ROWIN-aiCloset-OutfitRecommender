# ADL Task – 2 — ROWIN handout content

Fill in the three header lines, then paste the blocks below into your sheet in this
order. Screenshots are yours to take (3 of them) — everything else is below.

---

## HEADER (top of page 1, bold)

```
ADL Task – 2
<Your Name>
<Your Reg. No.>
ROWIN – Login, Registration and Database Connectivity (Room + Cloud Firestore)
```

**Screenshots to capture before printing**

| # | Screen | How to get there |
|---|--------|------------------|
| 1 | Login | Run the app → first screen |
| 2 | Create Account | Tap the "Create Account" footer link on Login |
| 3 | Closet (saved data) | Log in → bottom bar → Closet |

---

## PAGE 1

### [Screenshot 1 — Login screen]

**Layout XML — `app/src/main/res/layout/screen_login.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<ScrollView xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="@color/rowin_surface">

    <LinearLayout
        android:id="@+id/login_root"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="vertical"
        android:gravity="center_horizontal"
        android:paddingStart="20dp"
        android:paddingEnd="20dp"
        android:paddingTop="72dp"
        android:paddingBottom="48dp">

        <ImageView
            android:layout_width="wrap_content"
            android:layout_height="32dp"
            android:src="@drawable/wordmark_rowin"
            android:contentDescription="@string/app_name" />

        <TextView
            android:layout_width="match_parent"
            android:gravity="center"
            android:text="@string/login_welcome"
            android:textSize="28sp"
            android:fontFamily="@font/manrope"
            android:textStyle="bold"
            android:textColor="@color/rowin_primary" />

        <TextView
            android:layout_width="match_parent"
            android:layout_marginTop="8dp"
            android:gravity="center"
            android:text="@string/login_subtitle"
            android:textSize="16sp"
            android:textColor="@color/rowin_secondary" />

        <!-- Email field -->
        <EditText
            android:id="@+id/login_email"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:minHeight="56dp"
            android:background="@drawable/bg_field"
            android:paddingStart="16dp"
            android:paddingEnd="16dp"
            android:hint="@string/hint_enter_email"
            android:inputType="textEmailAddress"
            android:singleLine="true" />

        <!-- Password field + show/hide toggle -->
        <EditText
            android:id="@+id/login_password"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:minHeight="56dp"
            android:background="@drawable/bg_field"
            android:hint="@string/hint_enter_password"
            android:inputType="textPassword"
            android:singleLine="true" />

        <ImageButton
            android:id="@+id/login_toggle_password"
            android:layout_width="48dp"
            android:layout_height="56dp"
            android:src="@drawable/ic_visibility"
            android:contentDescription="@string/show_password" />

        <!-- Inline validation error (hidden until a check fails) -->
        <TextView
            android:id="@+id/login_error"
            android:layout_width="match_parent"
            android:visibility="gone"
            android:textSize="13sp"
            android:textColor="@color/rowin_error" />

        <Button
            android:id="@+id/login_button"
            android:layout_width="match_parent"
            android:layout_height="56dp"
            android:background="@drawable/bg_button_primary"
            android:text="@string/log_in"
            android:textSize="14sp"
            android:textColor="@color/rowin_on_primary" />

        <!-- Footer link to the Register screen -->
        <TextView
            android:id="@+id/login_footer"
            android:layout_width="match_parent"
            android:gravity="center"
            android:textSize="14sp"
            android:textColor="@color/rowin_secondary" />
    </LinearLayout>
</ScrollView>
```

### Database Connectivity:

ROWIN uses **two databases together**:

1. **Room (SQLite)** — `rowin.db`, the local source of truth. Accounts, wardrobe
   items and outfits live here, so the app works offline. Every screen reads
   through a `Flow` from a DAO, so the UI updates by itself when data changes.
2. **Cloud Firestore** — the online mirror. Every local write is pushed to
   `users/{email}/items/{itemId}`, and a snapshot listener streams cloud changes
   back into Room. If `google-services.json` is missing the app simply reports
   `LOCAL_ONLY` and keeps working.

Firebase keys the documents by the account **email**, which is the same unique
index Room enforces on the `users` table — that is what joins the two databases.

---

## PAGE 2

### [Screenshot 2 — Create Account]   **[Screenshot 3 — Closet]**

**Client-side validation — `ui/auth/RegisterFragment.kt`**

```kotlin
private fun validate(): String? = when {
    b.registerName.text.isNullOrBlank() ->
        getString(R.string.error_enter_name)

    !Patterns.EMAIL_ADDRESS
        .matcher(b.registerEmail.text?.toString()?.trim().orEmpty())
        .matches() ->
        getString(R.string.error_valid_email)

    (b.registerPassword.text?.length ?: 0) < 6 ->
        getString(R.string.error_password_length)

    b.registerPassword.text?.toString() != b.registerConfirm.text?.toString() ->
        getString(R.string.error_password_match)

    else -> null   // all checks passed -> write to the database
}
```

**Register / log in against the local database — `data/WardrobeRepository.kt`**

```kotlin
// INSERT a new user row
suspend fun register(name: String, email: String, password: String): Result<Long> {
    val normalizedEmail = email.trim()
    if (userDao.emailExists(normalizedEmail)) {
        return Result.failure(
            IllegalArgumentException("An account with this email already exists.")
        )
    }
    val salt = PasswordHasher.newSalt()
    val user = UserEntity(
        name     = name.trim(),
        email    = normalizedEmail,
        age      = 0,
        passwordHash = PasswordHasher.hash(password, salt),  // salted SHA-256
        passwordSalt = salt,                                 // never store raw password
    )
    return runCatching { userDao.insert(user) }
}

// READ the row back and verify the password
suspend fun login(email: String, password: String): Result<Long> {
    val user = userDao.findByEmail(email.trim())
        ?: return Result.failure(
            IllegalArgumentException("No account found with this email.")
        )
    if (!PasswordHasher.verify(password, user.passwordSalt, user.passwordHash)) {
        return Result.failure(
            IllegalArgumentException("Incorrect password. Please try again.")
        )
    }
    return Result.success(user.id)   // -> SessionManager stores the signed-in id
}
```

### Database Update Application:

**Entity + DAO — the actual SQLite schema and queries**

```kotlin
@Entity(tableName = "users", indices = [Index("email", unique = true)])
data class UserEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val email: String,
    val age: Int,
    val passwordHash: String,
    val passwordSalt: String,
    val createdAt: Long = System.currentTimeMillis(),
)

@Dao
interface UserDao {
    @Insert
    suspend fun insert(user: UserEntity): Long

    @Update
    suspend fun update(user: UserEntity)

    @Query("SELECT * FROM users WHERE email = :email COLLATE NOCASE LIMIT 1")
    suspend fun findByEmail(email: String): UserEntity?

    @Query("SELECT EXISTS(SELECT 1 FROM users WHERE email = :email COLLATE NOCASE)")
    suspend fun emailExists(email: String): Boolean
}
```

**Building the database (singleton, with a versioned migration)**

```kotlin
@Database(
    entities = [UserEntity::class, ClothingItemEntity::class,
                OutfitEntity::class, OutfitItemEntity::class],
    version = 2,
    exportSchema = false,
)
abstract class RowinDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun clothingDao(): ClothingDao
    abstract fun outfitDao(): OutfitDao

    companion object {
        @Volatile private var instance: RowinDatabase? = null

        fun get(context: Context): RowinDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    RowinDatabase::class.java,
                    "rowin.db",
                )
                    .addMigrations(MIGRATION_1_2)   // users.phone/location/avatar, items.remoteId
                    .build().also { instance = it }
            }
    }
}
```

**Insert + update, mirrored to Firestore (Room first, cloud second)**

```kotlin
suspend fun addClothing(item: ClothingItemEntity): Long {
    val id = clothingDao.insert(item)        // 1. local SQLite insert
    sync?.pushItem(item.copy(id = id))       // 2. mirror the same row to Firestore
    return id
}

suspend fun updateClothing(item: ClothingItemEntity) {
    clothingDao.update(item)                 // 1. local SQLite update
    sync?.pushItem(item)                     // 2. overwrite the cloud document
}

suspend fun updateProfile(user: UserEntity) {
    userDao.update(user)
    sync?.pushProfile(user)                  // users/{email} profile document
}
```

**The Firebase connection itself — `data/FirebaseSyncManager.kt`**

```kotlin
class FirebaseSyncManager(
    private val userDao: UserDao,
    private val clothingDao: ClothingDao,
    private val resolveUserId: (String) -> Long = { 1L },
) {
    // Non-null once Firebase is usable; every push no-ops when null,
    // so the app still runs with no google-services.json (local-only mode).
    private val db: FirebaseFirestore? =
        runCatching { FirebaseFirestore.getInstance() }.getOrNull()

    // Firestore layout:
    //   users/{email}                 -> profile mirror
    //   users/{email}/items/{itemId}  -> wardrobe items
    fun start(scope: CoroutineScope, email: String) {
        if (currentEmail == email) return
        stop()
        currentEmail = email
        if (db == null) { _state.value = SyncState(Status.LOCAL_ONLY, "…"); return }
        pushAllPending(scope)        // upload anything queued while offline
        observeRemote(scope, email)  // snapshot listener -> Room (download)
    }

    fun pushItem(item: ClothingItemEntity) {
        val store = db ?: return
        val email = currentEmail ?: return
        val docId = item.remoteId ?: item.id.toString()
        store.collection("users").document(email.lowercase())
            .collection("items").document(docId)
            .set(item.toFirebaseMap(), SetOptions.merge())
            .addOnSuccessListener {
                _state.value = _state.value.copy(status = Status.CONNECTED,
                                                 detail = "Synced just now")
                CoroutineScope(Dispatchers.IO).launch {
                    clothingDao.update(item.copy(remoteId = docId, synced = true))
                }
            }
            .addOnFailureListener { e ->
                _state.value = _state.value.copy(Status.ERROR, e.message ?: "Item sync failed")
            }
    }
}
```

**Starting / stopping the connection on sign-in and sign-out — `MainActivity.kt`**

```kotlin
combine(rowin.sessionState, rowin.user) { session, user -> session to user }
    .collect { (session, user) ->
        val email = user?.email
        if (session is SessionState.SignedIn && email != null) {
            container.onUserKnown(session.userId, email)
            container.firebaseSync.start(syncScope, email)   // connect
        } else if (session !is SessionState.SignedIn) {
            container.firebaseSync.stop()                    // disconnect
        }
    }
```

**Gradle setup — `app/build.gradle.kts` + `gradle/libs.versions.toml`**

```kotlin
// applied only when app/google-services.json exists, so the build stays green
val googleServicesJson = layout.projectDirectory.file("google-services.json").asFile
if (googleServicesJson.exists()) apply(plugin = "com.google.gms.google-services")

dependencies {
    // Room (local SQLite)
    implementation(libs.androidx.room.runtime)   // androidx.room:room-runtime 2.8.5
    implementation(libs.androidx.room.ktx)       // androidx.room:room-ktx
    ksp(libs.androidx.room.compiler)             // androidx.room:room-compiler

    // Firebase
    implementation(platform(libs.firebase.bom))  // com.google.firebase:firebase-bom 34.2.0
    implementation(libs.firebase.auth)           // com.google.firebase:firebase-auth
    implementation(libs.firebase.firestore)      // com.google.firebase:firebase-firestore
}
```

---

## Suggested spoken summary (30 seconds)

> The login screen validates the email and password before touching the
> database. Accounts are stored in a local Room database called `rowin.db` as
> salted SHA-256 hashes — never plain text — with a unique index on the email so
> nobody can register twice. Wardrobe items and outfits go into the same Room
> database through DAOs, which is why the app still works offline. On every
> write the repository also mirrors the row to Cloud Firestore under
> `users/{email}/items/{id}`, and a snapshot listener brings cloud changes back
> into Room, so the data is synchronised across devices. If Firebase is not
> configured the sync manager reports local-only mode and the app keeps working.

---

## Honesty note (read before submitting)

ROWIN **does not call FirebaseAuth** — `register()`/`login()` write to and read
from the local Room `users` table via `PasswordHasher`. Firebase is used for
**Firestore data sync** only. (`firebase-auth` is declared as a dependency but
never used in source.)

- If Task 2 only asks for **database connectivity**: the content above is
  accurate as written.
- If Task 2 explicitly requires **Firebase Authentication**: say so and the
  `register()`/`login()` pair can be rewired to `FirebaseAuth
  .createUserWithEmailAndPassword()` / `signInWithEmailAndPassword()` (the
  dependency and `google-services.json` are already in place).
