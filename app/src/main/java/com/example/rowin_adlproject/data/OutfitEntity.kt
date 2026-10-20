package com.example.rowin_adlproject.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A saved outfit (features #9-#14). Items are joined through [OutfitItemEntity].
 */
@Entity(
    tableName = "outfits",
    indices = [Index("userId"), Index("remoteId")],
)
data class OutfitEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val userId: Long,
    val name: String,
    val season: Season,
    val occasion: Occasion,
    /** Populated automatically by the Groq recommendation engine. */
    val aiGenerated: Boolean = false,
    val aiNote: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    /** Firestore `outfits/{remoteId}` document id (practical #4). Null = never synced. */
    val remoteId: String? = null,
    /** True once the server has confirmed the latest local version. */
    val synced: Boolean = false,
    /** Last edit time on any device; the newer copy wins a sync conflict. */
    val updatedAt: Long = createdAt,
)
