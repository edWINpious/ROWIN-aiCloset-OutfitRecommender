package com.example.rowin_adlproject.data

/**
 * The conflict rules [FirebaseSyncManager] applies to every incoming
 * Firestore document - kept free of Android/Firebase types so they are
 * unit-testable.
 */
object SyncMerge {

    /** What to do with one remote document. */
    enum class Action {
        /** No local row yet - insert the remote copy. */
        INSERT,

        /** The remote copy is newer - replace the local row with it. */
        OVERWRITE_LOCAL,

        /** The local row has a newer unconfirmed edit - its queued upload wins. */
        KEEP_LOCAL,

        /** Both sides already agree. */
        NONE,
    }

    /** The parts of a local row that matter for a merge decision. */
    data class Local(val synced: Boolean, val updatedAt: Long)

    /** Newest write wins; ties keep what we have. */
    fun decide(local: Local?, remoteUpdatedAt: Long): Action = when {
        local == null -> Action.INSERT
        remoteUpdatedAt > local.updatedAt -> Action.OVERWRITE_LOCAL
        !local.synced -> Action.KEEP_LOCAL
        else -> Action.NONE
    }

    /**
     * Remote ids of local rows that were deleted on another device.
     *
     * Only rows the server once confirmed ([confirmedRemoteIds]) can have been
     * deleted remotely - a never-uploaded row is simply not there yet. A
     * snapshot served from the offline cache may be incomplete, so it never
     * deletes anything.
     */
    fun deletedRemotely(
        confirmedRemoteIds: Collection<String>,
        serverIds: Set<String>,
        fromCache: Boolean,
    ): List<String> =
        if (fromCache) emptyList() else confirmedRemoteIds.filter { it !in serverIds }
}
