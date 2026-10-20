package com.example.rowin_adlproject.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A Firestore delete that has not been confirmed by the server yet.
 *
 * Deleting a synced row removes it from Room immediately, so without this
 * record a delete made while sync was paused would be undone by the next
 * snapshot (the document would still be in the cloud and come back).
 */
@Entity(tableName = "sync_tombstones")
data class SyncTombstone(
    @PrimaryKey val remoteId: String,
    /** Firestore collection the document lives in. */
    val collection: String,
    val userId: Long,
)
