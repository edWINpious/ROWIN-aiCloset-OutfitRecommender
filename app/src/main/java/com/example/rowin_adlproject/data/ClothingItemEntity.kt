package com.example.rowin_adlproject.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A clothing item in the user's digital closet (features #3-#7).
 * Always queried per-user; userId is the session user's [UserEntity.id].
 */
@Entity(
    tableName = "clothing_items",
    indices = [Index("userId"), Index("userId", "category"), Index(value = ["remoteId"], unique = false)],
)
data class ClothingItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val userId: Long,
    val name: String,
    val category: Category,
    val color: String,
    /** File name inside ImageStore dir, or null when no photo was added. */
    val imageFileName: String? = null,
    val season: Season,
    val occasion: Occasion,
    /** AI-inferred extras (feature #6). */
    val pattern: String? = null,
    val style: String? = null,
    val material: String? = null,
    val aiDescription: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    /** Firestore `clothingItems/{remoteId}` document id (practical #4). Null = never synced. */
    val remoteId: String? = null,
    /** True once the server has confirmed the latest local version. */
    val synced: Boolean = false,
    /** Last edit time on any device; the newer copy wins a sync conflict. */
    val updatedAt: Long = createdAt,
)
