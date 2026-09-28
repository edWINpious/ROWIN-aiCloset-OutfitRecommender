package com.example.rowin_adlproject.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A registered ROWIN user.
 *
 * Firebase note (feature #4 - data synchronization): [FirebaseSyncManager]
 * mirrors this row into Firestore `users/{uid}` using [UserEntity.email] as
 * the stable join key, so a cloud profile follows the same local identity.
 */
@Entity(
    tableName = "users",
    indices = [Index("email", unique = true)],
)
data class UserEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val email: String,
    val age: Int,
    /** Optional contact details shown in Edit Profile (per the Stitch design). */
    val phone: String? = null,
    val location: String? = null,
    /** File name inside ImageStore dir for the profile photo, or null. */
    val avatarFileName: String? = null,
    /** Salted SHA-256 hash, never the raw password. See PasswordHasher. */
    val passwordHash: String,
    val passwordSalt: String,
    val createdAt: Long = System.currentTimeMillis(),
)
