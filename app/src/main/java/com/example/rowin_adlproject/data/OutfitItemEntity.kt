package com.example.rowin_adlproject.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * Join table: which clothing items belong to which outfit.
 * Deleting a clothing item cascades to remove it from all outfits.
 */
@Entity(
    tableName = "outfit_items",
    primaryKeys = ["outfitId", "clothingItemId"],
    foreignKeys = [
        ForeignKey(
            entity = OutfitEntity::class,
            parentColumns = ["id"],
            childColumns = ["outfitId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = ClothingItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["clothingItemId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("clothingItemId")],
)
data class OutfitItemEntity(
    val outfitId: Long,
    val clothingItemId: Long,
)
