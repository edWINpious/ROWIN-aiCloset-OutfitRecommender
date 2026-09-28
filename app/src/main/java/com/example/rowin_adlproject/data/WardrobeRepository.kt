package com.example.rowin_adlproject.data

import android.content.Context
import kotlinx.coroutines.flow.Flow

/**
 * Single facade over the DAOs. Every method takes the session [userId] so no
 * screen can ever read another user's wardrobe (dynamic per-user data).
 *
 * Firebase sync (practical #4): the mutating methods below optionally mirror
 * writes to Firestore through [sync] after the local write succeeds.
 */
class WardrobeRepository(
    private val userDao: UserDao,
    private val clothingDao: ClothingDao,
    private val outfitDao: OutfitDao,
    val imageStore: ImageStore,
    /** Set by [AppContainer] after construction; nullable = sync disabled. */
    var sync: FirebaseSyncManager? = null,
) {
    // -------------------------------------------------------- auth / users

    suspend fun register(name: String, email: String, password: String): Result<Long> {
        val normalizedEmail = email.trim()
        if (userDao.emailExists(normalizedEmail)) {
            return Result.failure(IllegalArgumentException("An account with this email already exists."))
        }
        val salt = PasswordHasher.newSalt()
        val user = UserEntity(
            name = name.trim(),
            email = normalizedEmail,
            // The Stitch register design has no age field; profile age is set
            // later in Edit Profile if needed.
            age = 0,
            passwordHash = PasswordHasher.hash(password, salt),
            passwordSalt = salt,
        )
        return runCatching { userDao.insert(user) }
    }

    suspend fun login(email: String, password: String): Result<Long> {
        val user = userDao.findByEmail(email.trim())
            ?: return Result.failure(IllegalArgumentException("No account found with this email."))
        if (!PasswordHasher.verify(password, user.passwordSalt, user.passwordHash)) {
            return Result.failure(IllegalArgumentException("Incorrect password. Please try again."))
        }
        return Result.success(user.id)
    }

    fun observeUser(userId: Long): Flow<UserEntity?> = userDao.observeUser(userId)

    suspend fun updateProfile(user: UserEntity) {
        userDao.update(user)
        sync?.pushProfile(user)
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
        val id = clothingDao.insert(item)
        sync?.pushItem(item.copy(id = id))
        return id
    }

    suspend fun updateClothing(item: ClothingItemEntity) {
        clothingDao.update(item)
        sync?.pushItem(item)
    }

    /** Deletes the item and its photo file. Cascade removes it from outfits. */
    suspend fun deleteClothing(item: ClothingItemEntity) {
        clothingDao.delete(item)
        sync?.deleteItem(item)
        item.imageFileName?.let { imageStore.delete(it) }
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
        val id = outfitDao.insert(outfit)
        outfitDao.replaceItems(id, itemIds)
        return id
    }

    suspend fun updateOutfit(outfit: OutfitEntity, itemIds: List<Long>) {
        outfitDao.update(outfit)
        outfitDao.replaceItems(outfit.id, itemIds)
    }

    suspend fun deleteOutfit(outfit: OutfitEntity) = outfitDao.delete(outfit)

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
