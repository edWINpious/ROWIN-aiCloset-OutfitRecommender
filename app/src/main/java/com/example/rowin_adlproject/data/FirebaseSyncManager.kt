package com.example.rowin_adlproject.data

import android.util.Log
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.Source
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.Date

/**
 * Practical #4 - Firebase connectivity + two-way data synchronization.
 *
 * Room stays the offline source of truth. Every local write is mirrored to
 * Cloud Firestore, and snapshot listeners stream remote adds, edits and
 * deletes back into Room while the user is signed in.
 *
 * Firestore layout - one top-level collection per kind of data, every
 * document owned by a Firebase Auth uid (enforced by `firestore.rules` in the
 * project root):
 *
 *   users/{uid}               profile: name, email, phone, location, age
 *   clothingItems/{itemId}    ownerId + the garment's fields (photos stay on the phone)
 *   outfits/{outfitId}        ownerId + name, season, occasion, itemIds (clothingItems ids)
 *
 * Conflicts follow [SyncMerge]: the copy with the newer `updatedAt` wins.
 * Firebase is optional - without google-services.json the manager reports
 * LOCAL_ONLY and every call no-ops.
 */
class FirebaseSyncManager(
    private val userDao: UserDao,
    private val clothingDao: ClothingDao,
    private val outfitDao: OutfitDao,
    private val tombstoneDao: TombstoneDao,
    /** Removes a photo file when its item was deleted on another device. */
    private val deleteImage: (String) -> Unit,
) {

    enum class Status { LOCAL_ONLY, OFFLINE, CONNECTED, SYNCING, ERROR }

    data class SyncState(
        val status: Status = Status.LOCAL_ONLY,
        val detail: String = "Firebase not configured (local-only mode)",
        val lastSyncAt: Long? = null,
        val pendingUploads: Int = 0,
    )

    /** A profile read from the cloud when an account signs in on a new phone. */
    data class CloudProfile(
        val name: String?,
        val phone: String?,
        val location: String?,
        val age: Int?,
        val updatedAt: Long?,
    )

    /** The account being synced: its local row and its Firebase Auth uid. */
    private data class Account(val userId: Long, val uid: String, val email: String)

    private val _state = MutableStateFlow(SyncState())
    val state: StateFlow<SyncState> = _state

    /** Non-null once Firebase is usable; everything no-ops when null. */
    private val db: FirebaseFirestore? = runCatching { FirebaseFirestore.getInstance() }.getOrNull()

    val isAvailable: Boolean get() = db != null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Snapshots apply one at a time, so items always land before the outfits that use them. */
    private val applyLock = Mutex()

    @Volatile
    private var account: Account? = null
    private var sessionJob: Job? = null

    /** Latest outfits snapshot - re-applied when items arrive after the outfits that use them. */
    private var lastOutfits: QuerySnapshot? = null

    /** Starts sync for a signed-in account; a no-op if it is already running for it. */
    fun start(user: UserEntity, uid: String) {
        val running = account
        if (running != null && running.uid == uid && running.userId == user.id) return
        stop()
        if (db == null) {
            _state.value = SyncState()
            return
        }
        val acct = Account(user.id, uid, user.email)
        account = acct
        _state.value = SyncState(Status.SYNCING, "Connecting to Firestore...")
        sessionJob = scope.launch {
            val legacy = readLegacyLayout(acct)
            legacy?.let { importLegacyItems(acct, it) }
            val uploads = uploadPending(acct)
            if (legacy != null) launch { removeLegacyOnceUploaded(acct, legacy, uploads) }
            launch { listenProfile(acct) }
            launch { listenItems(acct) }
            launch { listenOutfits(acct) }
        }
    }

    /** Signed in, but this session can't sync (shown in App Settings). */
    fun pause(detail: String) {
        stop()
        if (db != null) _state.value = SyncState(Status.OFFLINE, detail)
    }

    fun stop() {
        sessionJob?.cancel()
        sessionJob = null
        account = null
        lastOutfits = null
        if (db != null) _state.value = SyncState(Status.OFFLINE, "Sync paused")
    }

    // ------------------------------------------------------------ profile

    fun pushProfile(user: UserEntity) {
        val store = db ?: return
        val acct = account?.takeIf { it.userId == user.id } ?: return
        track(store.collection(USERS).document(acct.uid).set(user.toFirestore()))
    }

    /** Reads the profile for an account signing in on this phone for the first time. */
    suspend fun fetchProfile(uid: String, email: String): CloudProfile? {
        val store = db ?: return null
        val doc = listOf(uid, email.lowercase()).firstNotNullOfOrNull { id ->
            runCatching { store.collection(USERS).document(id).get().await() }
                .getOrNull()
                ?.takeIf { it.exists() }
        } ?: return null
        return CloudProfile(
            name = doc.getString("name"),
            phone = doc.getString("phone"),
            location = doc.getString("location"),
            age = doc.getLong("age")?.toInt(),
            updatedAt = doc.millis("updatedAt"),
        )
    }

    // ----------------------------------------------------------- clothing

    /** Mirrors one item (insert or update); it is marked synced once the server confirms. */
    suspend fun pushItem(item: ClothingItemEntity) {
        uploadItem(item)
    }

    suspend fun deleteItem(item: ClothingItemEntity) {
        deleteRemote(CLOTHING_ITEMS, item.remoteId, item.userId)
    }

    /** A Firestore id for a new row, generated on the phone (works offline). */
    fun newItemId(): String? = db?.collection(CLOTHING_ITEMS)?.document()?.id

    private suspend fun uploadItem(item: ClothingItemEntity): Task<Void>? {
        val store = db ?: return null
        val acct = account?.takeIf { it.userId == item.userId } ?: return null
        val docId = ensureItemId(item)
        return track(store.collection(CLOTHING_ITEMS).document(docId).set(item.toFirestore(acct.uid))) {
            clothingDao.markSynced(item.id, item.updatedAt)
        }
    }

    private suspend fun ensureItemId(item: ClothingItemEntity): String =
        item.remoteId ?: db!!.collection(CLOTHING_ITEMS).document().id
            .also { clothingDao.setRemoteId(item.id, it) }

    // ------------------------------------------------------------ outfits

    suspend fun pushOutfit(outfitId: Long) {
        uploadOutfit(outfitId)
    }

    suspend fun deleteOutfit(outfit: OutfitEntity) {
        deleteRemote(OUTFITS, outfit.remoteId, outfit.userId)
    }

    fun newOutfitId(): String? = db?.collection(OUTFITS)?.document()?.id

    private suspend fun uploadOutfit(outfitId: Long): Task<Void>? {
        val store = db ?: return null
        val outfit = outfitDao.getById(outfitId) ?: return null
        val acct = account?.takeIf { it.userId == outfit.userId } ?: return null
        val docId = outfit.remoteId
            ?: store.collection(OUTFITS).document().id.also { outfitDao.setRemoteId(outfit.id, it) }
        // The outfit references pieces by their Firestore id, so each must have one.
        val itemIds = outfitDao.itemIdsFor(outfit.id).mapNotNull { id ->
            val item = clothingDao.getById(id) ?: return@mapNotNull null
            item.remoteId ?: ensureItemId(item).also { uploadItem(item.copy(remoteId = it)) }
        }
        return track(store.collection(OUTFITS).document(docId).set(outfit.toFirestore(acct.uid, itemIds))) {
            outfitDao.markSynced(outfit.id, outfit.updatedAt)
        }
    }

    // ------------------------------------------------------- shared upload

    /**
     * Records the delete first, so it survives a restart or a paused session,
     * then removes the document; the record is cleared once the server confirms.
     */
    private suspend fun deleteRemote(collection: String, remoteId: String?, userId: Long): Task<Void>? {
        val store = db ?: return null
        remoteId ?: return null
        tombstoneDao.insert(SyncTombstone(remoteId, collection, userId))
        if (account?.userId != userId) return null
        return track(store.collection(collection).document(remoteId).delete()) {
            tombstoneDao.delete(remoteId)
        }
    }

    /** Re-sends everything the server has not confirmed: edits made offline or while paused. */
    private suspend fun uploadPending(acct: Account): List<Task<*>> {
        val store = db ?: return emptyList()
        val tasks = mutableListOf<Task<*>>()
        clothingDao.getPending(acct.userId).forEach { item -> uploadItem(item)?.let(tasks::add) }
        outfitDao.getPending(acct.userId).forEach { outfit -> uploadOutfit(outfit.id)?.let(tasks::add) }
        tombstoneDao.forUser(acct.userId).forEach { tombstone ->
            tasks += track(store.collection(tombstone.collection).document(tombstone.remoteId).delete()) {
                tombstoneDao.delete(tombstone.remoteId)
            }
        }
        return tasks
    }

    /** Counts a write as pending until the server answers, then reports the result. */
    private fun <T> track(task: Task<T>, onConfirmed: suspend () -> Unit = {}): Task<T> {
        _state.update { it.copy(pendingUploads = it.pendingUploads + 1) }
        task.addOnCompleteListener { result ->
            _state.update { it.copy(pendingUploads = (it.pendingUploads - 1).coerceAtLeast(0)) }
            if (result.isSuccessful) {
                scope.launch { onConfirmed() }
                _state.update {
                    it.copy(
                        status = Status.CONNECTED,
                        detail = "Synced just now",
                        lastSyncAt = System.currentTimeMillis(),
                    )
                }
            } else {
                reportError(result.exception)
            }
        }
        return task
    }

    // ---------------------------------------------------------- listeners

    /** Newest profile wins in both directions; a missing cloud profile is created. */
    private suspend fun listenProfile(acct: Account) {
        db!!.collection(USERS).document(acct.uid).snapshots().collect { doc ->
            // A cached view may be stale - only the server copy can decide.
            if (doc.metadata.isFromCache) return@collect
            applyLock.withLock {
                val local = userDao.getById(acct.userId) ?: return@withLock
                val remoteUpdatedAt = doc.millis("updatedAt")
                if (!doc.exists() || remoteUpdatedAt == null || remoteUpdatedAt < local.updatedAt) {
                    pushProfile(local)
                } else if (remoteUpdatedAt > local.updatedAt) {
                    userDao.update(
                        local.copy(
                            name = doc.getString("name") ?: local.name,
                            phone = doc.getString("phone"),
                            location = doc.getString("location"),
                            age = doc.getLong("age")?.toInt() ?: local.age,
                            updatedAt = remoteUpdatedAt,
                        ),
                    )
                }
            }
        }
    }

    private suspend fun listenItems(acct: Account) {
        db!!.collection(CLOTHING_ITEMS).whereEqualTo(OWNER_ID, acct.uid).snapshots().collect { snapshot ->
            applyLock.withLock {
                applyItems(acct, snapshot)
                lastOutfits?.let { applyOutfits(acct, it) }
            }
            reportListening(snapshot)
        }
    }

    private suspend fun listenOutfits(acct: Account) {
        db!!.collection(OUTFITS).whereEqualTo(OWNER_ID, acct.uid).snapshots().collect { snapshot ->
            applyLock.withLock {
                lastOutfits = snapshot
                applyOutfits(acct, snapshot)
            }
            reportListening(snapshot)
        }
    }

    private suspend fun applyItems(acct: Account, snapshot: QuerySnapshot) {
        for (doc in snapshot.documents) {
            if (tombstoneDao.exists(doc.id)) continue
            val remote = doc.toClothingItem(acct.userId) ?: continue
            val local = clothingDao.getByRemoteId(acct.userId, doc.id)
            when (SyncMerge.decide(local?.let { SyncMerge.Local(it.synced, it.updatedAt) }, remote.updatedAt)) {
                SyncMerge.Action.INSERT -> clothingDao.insert(remote)
                SyncMerge.Action.OVERWRITE_LOCAL ->
                    // Photos never leave the phone, so keep the local one.
                    clothingDao.update(remote.copy(id = local!!.id, imageFileName = local.imageFileName))
                SyncMerge.Action.KEEP_LOCAL, SyncMerge.Action.NONE -> Unit
            }
        }
        val deleted = SyncMerge.deletedRemotely(
            confirmedRemoteIds = clothingDao.getConfirmed(acct.userId).mapNotNull { it.remoteId },
            serverIds = snapshot.documents.mapTo(HashSet()) { it.id },
            fromCache = snapshot.metadata.isFromCache,
        )
        for (remoteId in deleted) {
            val item = clothingDao.getByRemoteId(acct.userId, remoteId) ?: continue
            clothingDao.delete(item)
            item.imageFileName?.let(deleteImage)
        }
    }

    private suspend fun applyOutfits(acct: Account, snapshot: QuerySnapshot) {
        for (doc in snapshot.documents) {
            if (tombstoneDao.exists(doc.id)) continue
            val remote = doc.toOutfit(acct.userId) ?: continue
            val itemIds = (doc.get("itemIds") as? List<*>).orEmpty()
                .filterIsInstance<String>()
                .mapNotNull { clothingDao.getByRemoteId(acct.userId, it)?.id }
            val local = outfitDao.getByRemoteId(acct.userId, doc.id)
            when (SyncMerge.decide(local?.let { SyncMerge.Local(it.synced, it.updatedAt) }, remote.updatedAt)) {
                SyncMerge.Action.INSERT -> outfitDao.replaceItems(outfitDao.insert(remote), itemIds)
                SyncMerge.Action.OVERWRITE_LOCAL -> {
                    outfitDao.update(remote.copy(id = local!!.id))
                    outfitDao.replaceItems(local.id, itemIds)
                }
                // Same version, but a piece may only just have arrived - fill it in.
                SyncMerge.Action.NONE ->
                    if (outfitDao.itemIdsFor(local!!.id).toSet() != itemIds.toSet()) {
                        outfitDao.replaceItems(local.id, itemIds)
                    }
                SyncMerge.Action.KEEP_LOCAL -> Unit
            }
        }
        val deleted = SyncMerge.deletedRemotely(
            confirmedRemoteIds = outfitDao.getConfirmed(acct.userId).mapNotNull { it.remoteId },
            serverIds = snapshot.documents.mapTo(HashSet()) { it.id },
            fromCache = snapshot.metadata.isFromCache,
        )
        for (remoteId in deleted) {
            outfitDao.getByRemoteId(acct.userId, remoteId)?.let { outfitDao.delete(it) }
        }
    }

    private fun reportListening(snapshot: QuerySnapshot) {
        _state.update {
            if (snapshot.metadata.isFromCache) {
                it.copy(status = Status.OFFLINE, detail = "Offline - changes upload when you reconnect")
            } else {
                it.copy(
                    status = Status.CONNECTED,
                    detail = "Live sync active",
                    lastSyncAt = System.currentTimeMillis(),
                )
            }
        }
    }

    private fun reportError(error: Exception?) {
        val detail =
            if ((error as? FirebaseFirestoreException)?.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                "Firestore rules rejected this account - publish firestore.rules"
            } else {
                error?.message?.take(120) ?: "Sync failed"
            }
        Log.w(TAG, detail, error)
        _state.update { it.copy(status = Status.ERROR, detail = detail) }
    }

    private fun Query.snapshots(): Flow<QuerySnapshot> = callbackFlow {
        // INCLUDE: also deliver the cache -> server switch, which is what
        // lets a complete server snapshot reconcile remote deletes.
        val registration = addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
            if (error != null) reportError(error) else if (snapshot != null) trySend(snapshot)
        }
        awaitClose { registration.remove() }
    }

    private fun DocumentReference.snapshots(): Flow<DocumentSnapshot> = callbackFlow {
        val registration = addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
            if (error != null) reportError(error) else if (snapshot != null) trySend(snapshot)
        }
        awaitClose { registration.remove() }
    }

    // -------------------------------------------------- legacy migration

    /** The pre-v3 cloud layout (`users/{email}` + `users/{email}/items`) for one account. */
    private class LegacyLayout(val profile: DocumentReference, val items: QuerySnapshot)

    /** Null when there is nothing to move (or the server can't be reached - retried next start). */
    private suspend fun readLegacyLayout(acct: Account): LegacyLayout? {
        val store = db ?: return null
        val profile = store.collection(USERS).document(acct.email.lowercase())
        if (profile.id == acct.uid) return null
        val items = runCatching { profile.collection(LEGACY_ITEMS).get(Source.SERVER).await() }
            .getOrNull() ?: return null
        val profileExists = runCatching { profile.get(Source.SERVER).await().exists() }.getOrDefault(false)
        return if (items.isEmpty && !profileExists) null else LegacyLayout(profile, items)
    }

    /** Brings pieces that only exist in the old layout (e.g. added on another phone) into Room. */
    private suspend fun importLegacyItems(acct: Account, legacy: LegacyLayout) {
        for (doc in legacy.items.documents) {
            val item = doc.toClothingItem(acct.userId)?.copy(remoteId = null, synced = false) ?: continue
            val known = clothingDao.findDuplicate(
                userId = acct.userId,
                name = item.name,
                categoryName = item.category.name,
                color = item.color,
                createdAt = item.createdAt,
            )
            if (known == null) clothingDao.insert(item)
        }
    }

    /** Deletes the old copy only after every upload to the new layout is confirmed. */
    private suspend fun removeLegacyOnceUploaded(acct: Account, legacy: LegacyLayout, uploads: List<Task<*>>) {
        val confirmed = runCatching { Tasks.whenAll(uploads).await() }.isSuccess
        if (!confirmed || account != acct) return
        val removed = legacy.items.documents.count { doc -> runCatching { doc.reference.delete().await() }.isSuccess }
        runCatching { legacy.profile.delete().await() }
        Log.i(TAG, "Moved ${acct.email} to the new layout; removed $removed legacy item doc(s)")
    }

    // ---------------------------------------------------------- mapping

    private fun UserEntity.toFirestore(): Map<String, Any?> = mapOf(
        "name" to name,
        "email" to email,
        "age" to age,
        "phone" to phone,
        "location" to location,
        "createdAt" to timestamp(createdAt),
        "updatedAt" to timestamp(updatedAt),
    )

    private fun ClothingItemEntity.toFirestore(ownerId: String): Map<String, Any?> = mapOf(
        OWNER_ID to ownerId,
        "name" to name,
        "category" to category.name,
        "color" to color,
        "season" to season.name,
        "occasion" to occasion.name,
        "pattern" to pattern,
        "style" to style,
        "material" to material,
        "aiDescription" to aiDescription,
        // Binary images stay on the phone; the cloud only knows one exists.
        "hasImage" to (imageFileName != null),
        "createdAt" to timestamp(createdAt),
        "updatedAt" to timestamp(updatedAt),
    )

    private fun OutfitEntity.toFirestore(ownerId: String, itemIds: List<String>): Map<String, Any?> = mapOf(
        OWNER_ID to ownerId,
        "name" to name,
        "season" to season.name,
        "occasion" to occasion.name,
        "aiGenerated" to aiGenerated,
        "aiNote" to aiNote,
        "itemIds" to itemIds,
        "createdAt" to timestamp(createdAt),
        "updatedAt" to timestamp(updatedAt),
    )

    private fun DocumentSnapshot.toClothingItem(userId: Long): ClothingItemEntity? {
        val name = getString("name") ?: return null
        val createdAt = millis("createdAt") ?: System.currentTimeMillis()
        return ClothingItemEntity(
            userId = userId,
            name = name,
            category = enumOr(getString("category"), Category.OTHER),
            color = getString("color") ?: "",
            season = enumOr(getString("season"), Season.ALL_SEASON),
            occasion = enumOr(getString("occasion"), Occasion.OTHER),
            pattern = getString("pattern"),
            style = getString("style"),
            material = getString("material"),
            aiDescription = getString("aiDescription"),
            createdAt = createdAt,
            remoteId = id,
            synced = true,
            updatedAt = millis("updatedAt") ?: createdAt,
        )
    }

    private fun DocumentSnapshot.toOutfit(userId: Long): OutfitEntity? {
        val name = getString("name") ?: return null
        val createdAt = millis("createdAt") ?: System.currentTimeMillis()
        return OutfitEntity(
            userId = userId,
            name = name,
            season = enumOr(getString("season"), Season.ALL_SEASON),
            occasion = enumOr(getString("occasion"), Occasion.OTHER),
            aiGenerated = getBoolean("aiGenerated") ?: false,
            aiNote = getString("aiNote"),
            createdAt = createdAt,
            remoteId = id,
            synced = true,
            updatedAt = millis("updatedAt") ?: createdAt,
        )
    }

    /** Timestamps are stored as Firestore Timestamps (readable in the console); old docs used millis. */
    private fun DocumentSnapshot.millis(field: String): Long? = when (val value = get(field)) {
        is Timestamp -> value.toDate().time
        is Number -> value.toLong()
        else -> null
    }

    private fun timestamp(millis: Long) = Timestamp(Date(millis))

    private inline fun <reified E : Enum<E>> enumOr(name: String?, fallback: E): E =
        name?.let { runCatching { enumValueOf<E>(it) }.getOrNull() } ?: fallback

    private companion object {
        const val TAG = "FirebaseSync"
        const val USERS = "users"
        const val CLOTHING_ITEMS = "clothingItems"
        const val OUTFITS = "outfits"
        const val OWNER_ID = "ownerId"

        /** Subcollection of the pre-v3 layout, read once per account to migrate it. */
        const val LEGACY_ITEMS = "items"
    }
}
