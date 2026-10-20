package com.example.rowin_adlproject

import com.example.rowin_adlproject.data.SyncMerge
import com.example.rowin_adlproject.data.SyncMerge.Action
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Tests for practical #4 (two-way Firestore sync) - the conflict rules
 * applied to every incoming cloud document.
 */
class SyncMergeTest {

    @Test
    fun `unknown remote document is inserted`() {
        assertEquals(Action.INSERT, SyncMerge.decide(local = null, remoteUpdatedAt = 10))
    }

    @Test
    fun `newer remote edit overwrites a synced local row`() {
        assertEquals(Action.OVERWRITE_LOCAL, SyncMerge.decide(SyncMerge.Local(synced = true, updatedAt = 10), 20))
    }

    @Test
    fun `newer remote edit wins over an older pending local edit`() {
        assertEquals(Action.OVERWRITE_LOCAL, SyncMerge.decide(SyncMerge.Local(synced = false, updatedAt = 10), 20))
    }

    @Test
    fun `newer pending local edit is kept for upload`() {
        assertEquals(Action.KEEP_LOCAL, SyncMerge.decide(SyncMerge.Local(synced = false, updatedAt = 30), 20))
    }

    @Test
    fun `echo of our own confirmed write changes nothing`() {
        assertEquals(Action.NONE, SyncMerge.decide(SyncMerge.Local(synced = true, updatedAt = 20), 20))
    }

    @Test
    fun `confirmed rows missing from a server snapshot were deleted remotely`() {
        val deleted = SyncMerge.deletedRemotely(
            confirmedRemoteIds = listOf("a", "b", "c"),
            serverIds = setOf("a", "c"),
            fromCache = false,
        )
        assertEquals(listOf("b"), deleted)
    }

    @Test
    fun `a cached snapshot never deletes anything`() {
        val deleted = SyncMerge.deletedRemotely(
            confirmedRemoteIds = listOf("a", "b"),
            serverIds = emptySet(),
            fromCache = true,
        )
        assertEquals(emptyList<String>(), deleted)
    }
}
