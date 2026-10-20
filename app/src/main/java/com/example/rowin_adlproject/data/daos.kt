package com.example.rowin_adlproject.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.Junction
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

// ---------------------------------------------------------------- users

@Dao
interface UserDao {
    @Insert
    suspend fun insert(user: UserEntity): Long

    @Update
    suspend fun update(user: UserEntity)

    @Query("SELECT * FROM users WHERE id = :id")
    fun observeUser(id: Long): Flow<UserEntity?>

    @Query("SELECT * FROM users WHERE id = :id")
    suspend fun getById(id: Long): UserEntity?

    @Query("SELECT * FROM users WHERE email = :email COLLATE NOCASE LIMIT 1")
    suspend fun findByEmail(email: String): UserEntity?

    @Query("SELECT EXISTS(SELECT 1 FROM users WHERE email = :email COLLATE NOCASE)")
    suspend fun emailExists(email: String): Boolean

    @Query("SELECT * FROM users WHERE email = :email COLLATE NOCASE LIMIT 1")
    suspend fun getByEmail(email: String): UserEntity?

    @Query("SELECT * FROM users")
    suspend fun getAllUsers(): List<UserEntity>

    @Upsert
    suspend fun upsert(user: UserEntity)

    @Query("DELETE FROM users WHERE id = :id")
    suspend fun delete(id: Long)
}

// ------------------------------------------------------------- clothing

@Dao
interface ClothingDao {
    @Insert
    suspend fun insert(item: ClothingItemEntity): Long

    @Update
    suspend fun update(item: ClothingItemEntity)

    @Delete
    suspend fun delete(item: ClothingItemEntity)

    @Query("SELECT * FROM clothing_items WHERE userId = :userId ORDER BY createdAt DESC")
    fun observeAll(userId: Long): Flow<List<ClothingItemEntity>>

    @Query("SELECT * FROM clothing_items WHERE id = :id")
    fun observeById(id: Long): Flow<ClothingItemEntity?>

    @Query("SELECT * FROM clothing_items WHERE id = :id")
    suspend fun getById(id: Long): ClothingItemEntity?

    @Query("SELECT * FROM clothing_items WHERE userId = :userId ORDER BY createdAt DESC")
    suspend fun getAll(userId: Long): List<ClothingItemEntity>

    @Query("SELECT COUNT(*) FROM clothing_items WHERE userId = :userId")
    fun observeCount(userId: Long): Flow<Int>

    @Query(
        "SELECT COUNT(*) FROM clothing_items WHERE userId = :userId " +
            "AND createdAt >= :sinceMillis"
    )
    fun observeCountSince(userId: Long, sinceMillis: Long): Flow<Int>

    @Query("SELECT * FROM clothing_items WHERE userId = :userId ORDER BY createdAt DESC")
    suspend fun getOldestFirst(userId: Long): List<ClothingItemEntity>

    // ---- Firestore sync (practical #4)

    @Query("SELECT * FROM clothing_items WHERE userId = :userId AND remoteId = :remoteId LIMIT 1")
    suspend fun getByRemoteId(userId: Long, remoteId: String): ClothingItemEntity?

    /** Rows whose latest version the server has not confirmed yet. */
    @Query("SELECT * FROM clothing_items WHERE userId = :userId AND synced = 0")
    suspend fun getPending(userId: Long): List<ClothingItemEntity>

    /** Rows the server has confirmed; one missing from the cloud was deleted elsewhere. */
    @Query("SELECT * FROM clothing_items WHERE userId = :userId AND synced = 1 AND remoteId IS NOT NULL")
    suspend fun getConfirmed(userId: Long): List<ClothingItemEntity>

    @Query("UPDATE clothing_items SET remoteId = :remoteId WHERE id = :id")
    suspend fun setRemoteId(id: Long, remoteId: String)

    /** Only marks the version that was uploaded - a newer local edit stays pending. */
    @Query("UPDATE clothing_items SET synced = 1 WHERE id = :id AND updatedAt = :updatedAt")
    suspend fun markSynced(id: Long, updatedAt: Long)

    /** Exact-content match, used to recognise pieces when importing the legacy cloud layout. */
    @Query(
        "SELECT * FROM clothing_items WHERE userId = :userId AND name = :name " +
            "AND category = :categoryName AND color = :color AND createdAt = :createdAt LIMIT 1",
    )
    suspend fun findDuplicate(
        userId: Long,
        name: String,
        categoryName: String,
        color: String,
        createdAt: Long,
    ): ClothingItemEntity?

    @Query("SELECT COUNT(*) FROM clothing_items")
    suspend fun countAll(): Int

    @Upsert
    suspend fun upsert(item: ClothingItemEntity): Long
}

// -------------------------------------------------------------- outfits

/** Outfit + its joined clothing items, loaded with @Transaction. */
data class OutfitWithItems(
    @Embedded val outfit: OutfitEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(
            value = OutfitItemEntity::class,
            parentColumn = "outfitId",
            entityColumn = "clothingItemId",
        ),
    )
    val items: List<ClothingItemEntity>,
)

@Dao
interface OutfitDao {
    @Insert
    suspend fun insert(outfit: OutfitEntity): Long

    @Update
    suspend fun update(outfit: OutfitEntity)

    @Delete
    suspend fun delete(outfit: OutfitEntity)

    @Insert
    suspend fun insertItems(links: List<OutfitItemEntity>)

    @Query("DELETE FROM outfit_items WHERE outfitId = :outfitId")
    suspend fun clearItems(outfitId: Long)

    @Transaction
    suspend fun replaceItems(outfitId: Long, itemIds: List<Long>) {
        clearItems(outfitId)
        insertItems(itemIds.map { OutfitItemEntity(outfitId, it) })
    }

    @Transaction
    @Query("SELECT * FROM outfits WHERE userId = :userId ORDER BY createdAt DESC")
    fun observeAllWithItems(userId: Long): Flow<List<OutfitWithItems>>

    @Transaction
    @Query("SELECT * FROM outfits WHERE id = :outfitId")
    fun observeByIdWithItems(outfitId: Long): Flow<OutfitWithItems?>

    @Transaction
    @Query("SELECT * FROM outfits WHERE id = :outfitId")
    suspend fun getByIdWithItems(outfitId: Long): OutfitWithItems?

    @Query("SELECT COUNT(*) FROM outfits WHERE userId = :userId")
    fun observeCount(userId: Long): Flow<Int>

    // ---- Firestore sync (practical #4)

    @Query("SELECT * FROM outfits WHERE id = :id")
    suspend fun getById(id: Long): OutfitEntity?

    @Query("SELECT clothingItemId FROM outfit_items WHERE outfitId = :outfitId")
    suspend fun itemIdsFor(outfitId: Long): List<Long>

    @Query("SELECT * FROM outfits WHERE userId = :userId AND remoteId = :remoteId LIMIT 1")
    suspend fun getByRemoteId(userId: Long, remoteId: String): OutfitEntity?

    @Query("SELECT * FROM outfits WHERE userId = :userId AND synced = 0")
    suspend fun getPending(userId: Long): List<OutfitEntity>

    @Query("SELECT * FROM outfits WHERE userId = :userId AND synced = 1 AND remoteId IS NOT NULL")
    suspend fun getConfirmed(userId: Long): List<OutfitEntity>

    @Query("UPDATE outfits SET remoteId = :remoteId WHERE id = :id")
    suspend fun setRemoteId(id: Long, remoteId: String)

    @Query("UPDATE outfits SET synced = 1 WHERE id = :id AND updatedAt = :updatedAt")
    suspend fun markSynced(id: Long, updatedAt: Long)
}

// ----------------------------------------------------------- tombstones

@Dao
interface TombstoneDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(tombstone: SyncTombstone)

    @Query("DELETE FROM sync_tombstones WHERE remoteId = :remoteId")
    suspend fun delete(remoteId: String)

    @Query("SELECT * FROM sync_tombstones WHERE userId = :userId")
    suspend fun forUser(userId: Long): List<SyncTombstone>

    @Query("SELECT EXISTS(SELECT 1 FROM sync_tombstones WHERE remoteId = :remoteId)")
    suspend fun exists(remoteId: String): Boolean
}


