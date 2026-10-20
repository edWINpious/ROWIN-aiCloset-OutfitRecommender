package com.example.rowin_adlproject.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/**
 * Single facade over the DAOs. Every method takes the session [userId] so no
 * screen can ever read another user's wardrobe (dynamic per-user data).
 *
 * Accounts (practical #1): with Firebase configured, [auth] is the source of
 * truth for credentials and the local password hash only enables offline
 * sign-in. Without Firebase, accounts are purely local.
 *
 * Firebase sync (practical #4): the mutating methods below mirror writes to
 * Firestore through [sync] after the local write succeeds.
 */
class WardrobeRepository(
    private val userDao: UserDao,
    private val clothingDao: ClothingDao,
    private val outfitDao: OutfitDao,
    val imageStore: ImageStore,
    /** Set by [AppContainer] after construction; nullable = sync disabled. */
    var sync: FirebaseSyncManager? = null,
    /** Set by [AppContainer]; null or unavailable = local-only accounts. */
    var auth: AuthService? = null,
) {
    // -------------------------------------------------------- auth / users

    suspend fun register(name: String, email: String, password: String): Result<Long> {
        val normalizedEmail = email.trim()
        if (userDao.emailExists(normalizedEmail)) {
            return failure("An account with this email already exists. Sign in instead.")
        }
        val cloud = auth?.takeIf { it.isAvailable }
        val uid = if (cloud == null) null else {
            when (val outcome = cloud.createAccount(normalizedEmail, password)) {
                is AuthService.Outcome.Success -> outcome.uid
                AuthService.Outcome.EmailInUse ->
                    return failure("An account with this email already exists. Sign in instead.")
                AuthService.Outcome.WeakPassword -> return failure("Choose a stronger password.")
                AuthService.Outcome.Offline ->
                    return failure("Creating an account needs an internet connection.")
                AuthService.Outcome.InvalidCredentials -> return failure("Enter a valid email address.")
                is AuthService.Outcome.Failure -> return failure(outcome.message)
            }
        }
        val salt = PasswordHasher.newSalt()
        val user = UserEntity(
            name = name.trim(),
            email = normalizedEmail,
            // The Stitch register design has no age field; profile age is set
            // later in Edit Profile if needed.
            age = 0,
            passwordHash = hash(password, salt),
            passwordSalt = salt,
            firebaseUid = uid,
        )
        return runCatching { userDao.insert(user) }
    }

    suspend fun login(email: String, password: String): Result<Long> {
        val normalizedEmail = email.trim()
        val local = userDao.findByEmail(normalizedEmail)
        val cloud = auth?.takeIf { it.isAvailable } ?: return localLogin(local, password)

        return when (val outcome = cloud.signIn(normalizedEmail, password)) {
            is AuthService.Outcome.Success ->
                Result.success(adoptCloudAccount(local, normalizedEmail, password, outcome.uid))
            // No network: an account that has signed in here before still works offline.
            AuthService.Outcome.Offline ->
                if (local == null) {
                    failure("You're offline, and this account hasn't signed in on this phone yet.")
                } else {
                    localLogin(local, password)
                }
            AuthService.Outcome.InvalidCredentials ->
                if (local != null && verify(password, local)) {
                    upgradeLocalAccount(local, password)
                } else {
                    failure("Incorrect email or password.")
                }
            AuthService.Outcome.EmailInUse, AuthService.Outcome.WeakPassword ->
                failure("Incorrect email or password.")
            is AuthService.Outcome.Failure -> failure(outcome.message)
        }
    }

    /** Emails a Firebase password-reset link; fails in local-only builds. */
    suspend fun sendPasswordReset(email: String): Result<Unit> {
        val cloud = auth?.takeIf { it.isAvailable }
            ?: return failure("Password reset needs Firebase, which isn't set up in this build.")
        return when (val outcome = cloud.sendPasswordReset(email.trim())) {
            is AuthService.Outcome.Success -> Result.success(Unit)
            AuthService.Outcome.Offline -> failure("You're offline. Connect to the internet and try again.")
            is AuthService.Outcome.Failure -> failure(outcome.message)
            else -> failure("Enter the email address you registered with.")
        }
    }

    fun signOut() {
        auth?.signOut()
    }

    /**
     * An account created before Firebase Auth existed only lives in Room.
     * Its password was just verified locally, so create the matching Firebase
     * account with it - the user never has to register again.
     */
    private suspend fun upgradeLocalAccount(local: UserEntity, password: String): Result<Long> =
        when (val outcome = auth!!.createAccount(local.email, password)) {
            is AuthService.Outcome.Success ->
                Result.success(adoptCloudAccount(local, local.email, password, outcome.uid))
            // A Firebase account exists with a different password: the one typed is wrong for it.
            AuthService.Outcome.EmailInUse -> failure("Incorrect email or password.")
            AuthService.Outcome.Offline -> localLogin(local, password)
            AuthService.Outcome.WeakPassword ->
                failure("This account's password is too short for cloud sign-in. Register a new account.")
            AuthService.Outcome.InvalidCredentials -> failure("Incorrect email or password.")
            is AuthService.Outcome.Failure -> failure(outcome.message)
        }

    /** Links (or creates) the local row for a Firebase account and refreshes the offline hash. */
    private suspend fun adoptCloudAccount(
        local: UserEntity?,
        email: String,
        password: String,
        uid: String,
    ): Long {
        val salt = PasswordHasher.newSalt()
        val hash = hash(password, salt)
        if (local != null) {
            userDao.update(local.copy(firebaseUid = uid, passwordHash = hash, passwordSalt = salt))
            return local.id
        }
        // First sign-in on this phone: restore the profile from the cloud.
        val profile = sync?.fetchProfile(uid, email)
        val now = System.currentTimeMillis()
        return userDao.insert(
            UserEntity(
                name = profile?.name ?: email.substringBefore('@'),
                email = email,
                age = profile?.age ?: 0,
                phone = profile?.phone,
                location = profile?.location,
                passwordHash = hash,
                passwordSalt = salt,
                firebaseUid = uid,
                updatedAt = profile?.updatedAt ?: now,
            ),
        )
    }

    private suspend fun localLogin(local: UserEntity?, password: String): Result<Long> = when {
        local == null -> failure("No account found with this email.")
        !verify(password, local) -> failure("Incorrect password. Please try again.")
        else -> Result.success(local.id)
    }

    // PBKDF2 is deliberately slow - keep it off the main thread.
    private suspend fun hash(password: String, salt: String): String =
        withContext(Dispatchers.Default) { PasswordHasher.hash(password, salt) }

    private suspend fun verify(password: String, user: UserEntity): Boolean =
        withContext(Dispatchers.Default) { PasswordHasher.verify(password, user.passwordSalt, user.passwordHash) }

    private fun <T> failure(message: String): Result<T> = Result.failure(IllegalArgumentException(message))

    fun observeUser(userId: Long): Flow<UserEntity?> = userDao.observeUser(userId)

    suspend fun updateProfile(user: UserEntity) {
        val updated = user.copy(updatedAt = System.currentTimeMillis())
        userDao.update(updated)
        sync?.pushProfile(updated)
    }

    // ------------------------------------------------------------ clothing

    fun observeClothing(userId: Long): Flow<List<ClothingItemEntity>> =
        clothingDao.observeAll(userId)

    fun observeClothingCount(userId: Long): Flow<Int> = clothingDao.observeCount(userId)

    fun observeClothingCountSince(userId: Long, sinceMillis: Long): Flow<Int> =
        clothingDao.observeCountSince(userId, sinceMillis)

    fun observeClothingById(id: Long): Flow<ClothingItemEntity?> = clothingDao.observeById(id)

    suspend fun getClothingById(id: Long): ClothingItemEntity? = clothingDao.getById(id)

    suspend fun getAllClothing(userId: Long): List<ClothingItemEntity> =
        clothingDao.getAll(userId)

    suspend fun addClothing(item: ClothingItemEntity): Long {
        // The cloud id is fixed before the row exists, so the snapshot echo of
        // this write always finds it (no duplicate rows) - even offline.
        val stored = item.copy(
            remoteId = sync?.newItemId(),
            synced = false,
            updatedAt = System.currentTimeMillis(),
        )
        val id = clothingDao.insert(stored)
        sync?.pushItem(stored.copy(id = id))
        return id
    }

    suspend fun updateClothing(item: ClothingItemEntity) {
        // Edit screens build a fresh entity; keep the row's cloud identity.
        val current = clothingDao.getById(item.id)
        val updated = item.copy(
            remoteId = current?.remoteId ?: item.remoteId,
            synced = false,
            updatedAt = System.currentTimeMillis(),
        )
        clothingDao.update(updated)
        sync?.pushItem(updated)
    }

    /** Deletes the item and its photo file. Cascade removes it from outfits. */
    suspend fun deleteClothing(item: ClothingItemEntity) {
        val current = clothingDao.getById(item.id) ?: item
        clothingDao.delete(current)
        sync?.deleteItem(current)
        current.imageFileName?.let { imageStore.delete(it) }
    }

    // -------------------------------------------------------------- outfits

    fun observeOutfits(userId: Long): Flow<List<OutfitWithItems>> =
        outfitDao.observeAllWithItems(userId)

    fun observeOutfitCount(userId: Long): Flow<Int> = outfitDao.observeCount(userId)

    fun observeOutfit(outfitId: Long): Flow<OutfitWithItems?> =
        outfitDao.observeByIdWithItems(outfitId)

    suspend fun getOutfitById(outfitId: Long): OutfitWithItems? =
        outfitDao.getByIdWithItems(outfitId)

    suspend fun saveOutfit(outfit: OutfitEntity, itemIds: List<Long>): Long {
        val id = outfitDao.insert(
            outfit.copy(
                remoteId = sync?.newOutfitId(),
                synced = false,
                updatedAt = System.currentTimeMillis(),
            ),
        )
        outfitDao.replaceItems(id, itemIds)
        sync?.pushOutfit(id)
        return id
    }

    suspend fun updateOutfit(outfit: OutfitEntity, itemIds: List<Long>) {
        val current = outfitDao.getById(outfit.id)
        outfitDao.update(
            outfit.copy(
                remoteId = current?.remoteId ?: outfit.remoteId,
                synced = false,
                updatedAt = System.currentTimeMillis(),
            ),
        )
        outfitDao.replaceItems(outfit.id, itemIds)
        sync?.pushOutfit(outfit.id)
    }

    suspend fun deleteOutfit(outfit: OutfitEntity) {
        val current = outfitDao.getById(outfit.id) ?: outfit
        outfitDao.delete(current)
        sync?.deleteOutfit(current)
    }

    // ------------------------------------------------- alerts / sync helpers

    suspend fun getOldestClothingFirst(userId: Long): List<ClothingItemEntity> =
        clothingDao.getOldestFirst(userId)

    companion object {
        fun from(context: Context): WardrobeRepository {
            val db = RowinDatabase.get(context)
            return WardrobeRepository(
                userDao = db.userDao(),
                clothingDao = db.clothingDao(),
                outfitDao = db.outfitDao(),
                imageStore = ImageStore(context.applicationContext),
            )
        }
    }
}
