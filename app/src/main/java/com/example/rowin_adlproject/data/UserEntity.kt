package com.example.rowin_adlproject.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A registered ROWIN user.
 *
 * Firebase note (feature #4 - data synchronization): [FirebaseSyncManager]
 * mirrors this row into Firestore `users/{firebaseUid}`. The local password
 * hash is only kept so the account can still sign in while offline.
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
    /** Firebase Auth uid; null for a local-only account (no google-services.json). */
    val firebaseUid: String? = null,
    /** Last profile edit on any device; the newer copy wins a sync conflict. */
    val updatedAt: Long = createdAt,
)
