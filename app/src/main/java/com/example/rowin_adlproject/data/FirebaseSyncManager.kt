package com.example.rowin_adlproject.data

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.SetOptions

/**
 * Practical #4 - dynamic Firebase connectivity + data synchronization.
 *
 * Model: Room stays the offline source of truth; every mutation in
 * [WardrobeRepository] is mirrored to Firestore here, and a snapshot listener
 * streams remote clothing documents back into Room while the user is signed
 * in. Firebase is optional: with no google-services.json the app runs in
 * "local-only" mode and everything still works.
 *
 * Firestore layout (all keyed by the account email, the stable join key):
 *   users/{email}                  -> profile mirror
 *   users/{email}/items/{itemId}   -> wardrobe items
 */
class FirebaseSyncManager(
    private val userDao: UserDao,
    private val clothingDao: ClothingDao,
    /** Resolves the local user row for the syncing email (scopes incoming items). */
    private val resolveUserId: (String) -> Long = { 1L },
) {

    enum class Status { LOCAL_ONLY, OFFLINE, CONNECTED, SYNCING, ERROR }

    data class SyncState(
        val status: Status = Status.LOCAL_ONLY,
        val detail: String = "Firebase not configured (local-only mode)",
        val lastSyncAt: Long? = null,
        val pendingUploads: Int = 0,
    )

    private val _state = MutableStateFlow(SyncState())
    val state: StateFlow<SyncState> = _state

    /** Non-null once Firebase is usable; everything no-ops when null. */
    private val db: FirebaseFirestore? = runCatching { FirebaseFirestore.getInstance() }.getOrNull()

    private var currentEmail: String? = null
    private var listenerJob: Job? = null

    /** Starts sync for the signed-in account; call again on account switch. */
    fun start(scope: CoroutineScope, email: String) {
        if (currentEmail == email) return
        stop()
        currentEmail = email
        if (db == null) {
            _state.value = SyncState(Status.LOCAL_ONLY, "Firebase not configured (local-only mode)")
            return
        }
        // Self-heal: rows duplicated by the pre-fix push/snapshot race.
        scope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val removed = runCatching { clothingDao.dedupeByRemoteId() }.getOrDefault(0)
            if (removed > 0) Log.d(TAG, "Removed $removed duplicate closet row(s)")
        }
        pushAllPending(scope)
        observeRemote(scope, email)
    }

    fun stop() {
        listenerJob?.cancel()
        listenerJob = null
        currentEmail = null
        if (db != null) _state.value = SyncState(Status.OFFLINE, "Sync paused")
    }

    // ------------------------------------------------------------ profile

    fun pushProfile(user: UserEntity) {
        val store = db ?: return
        val email = currentEmail ?: user.email
        _state.value = _state.value.copy(status = Status.SYNCING)
        store.collection(USERS).document(email.lowercase())
            .set(user.toFirebaseMap(), SetOptions.merge())
            .addOnSuccessListener {
                _state.value = _state.value.copy(
                    status = Status.CONNECTED,
                    detail = "Synced just now",
                    lastSyncAt = System.currentTimeMillis(),
                )
            }
            .addOnFailureListener { e ->
                _state.value = _state.value.copy(Status.ERROR, e.message?.take(120) ?: "Profile sync failed")
            }
    }

    // ------------------------------------------------------------ wardrobe

    /** Mirrors one item (insert or update) to Firestore; marks it synced. */
    fun pushItem(item: ClothingItemEntity) {
        val store = db ?: return
        val email = currentEmail ?: return
        _state.value = _state.value.copy(status = Status.SYNCING)
        val docId = item.remoteId ?: item.id.toString()
        store.collection(USERS).document(email.lowercase())
            .collection(ITEMS).document(docId)
            .set(item.toFirebaseMap(), SetOptions.merge())
            .addOnSuccessListener {
                _state.value = _state.value.copy(
                    status = Status.CONNECTED,
                    detail = "Synced just now",
                    lastSyncAt = System.currentTimeMillis(),
                )
                if (item.remoteId == null || !item.synced) {
                    CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                        clothingDao.update(item.copy(remoteId = docId, synced = true))
                    }
                }
            }
            .addOnFailureListener { e ->
                _state.value = _state.value.copy(Status.ERROR, e.message?.take(120) ?: "Item sync failed")
            }
    }

    fun deleteItem(item: ClothingItemEntity) {
        val store = db ?: return
        val email = currentEmail ?: return
        val docId = item.remoteId ?: item.id.toString()
        store.collection(USERS).document(email.lowercase())
            .collection(ITEMS).document(docId).delete()
    }

    /** Uploads every local item that has never been synced (offline backlog). */
    private fun pushAllPending(scope: CoroutineScope) {
        val email = currentEmail ?: return
        if (db == null) return
        scope.launch(kotlinx.coroutines.Dispatchers.IO) {
            // Mirror only the signed-in account's profile; items are pushed
            // lazily by the repository hooks and re-merged by the listener.
            val user = runCatching { userDao.getByEmail(email) }.getOrNull()
            if (user != null) {
                val itemCount = runCatching { clothingDao.getAll(user.id).size }.getOrDefault(0)
                _state.value = _state.value.copy(pendingUploads = itemCount)
                pushProfile(user)
            }
        }
    }

    /** Streams remote wardrobe documents into Room while signed in. */
    private fun observeRemote(scope: CoroutineScope, email: String) {
        val store = db ?: return
        listenerJob = scope.launch(kotlinx.coroutines.Dispatchers.IO) {
            snapshotFlow(store, email)
                .collect { snapshot ->
                    applySnapshot(snapshot)
                    _state.value = _state.value.copy(
                        status = Status.CONNECTED,
                        detail = "Live sync active",
                        lastSyncAt = System.currentTimeMillis(),
                    )
                }
        }
    }

    private fun snapshotFlow(
        store: FirebaseFirestore,
        email: String,
    ): kotlinx.coroutines.flow.Flow<QuerySnapshot> =
        kotlinx.coroutines.flow.callbackFlow {
            val registration = store.collection(USERS)
                .document(email.lowercase())
                .collection(ITEMS)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        _state.value = _state.value.copy(Status.ERROR, error.message?.take(120) ?: "Sync error")
                        return@addSnapshotListener
                    }
                    if (snapshot != null) trySend(snapshot)
                }
            awaitClose { registration.remove() }
        }

    private suspend fun applySnapshot(snapshot: QuerySnapshot) {
        val ownerUserId = currentEmail?.let(resolveUserId) ?: return
        for (doc in snapshot.documents) {
            val remote = doc.toClothingItem() ?: continue
            val existing =
                clothingDao.getByRemoteId(doc.id)
                    ?: matchLocalPush(doc.id, ownerUserId, remote)
            val incoming = remote.copy(remoteId = doc.id, synced = true, userId = ownerUserId)
            when {
                existing == null -> clothingDao.upsert(incoming)
                existing.synced -> Unit // ours already mirrored; nothing to do
                else -> {
                    // Conflict: newest write wins.
                    if (incoming.createdAt >= existing.createdAt) {
                        clothingDao.upsert(incoming.copy(id = existing.id, userId = existing.userId))
                    }
                }
            }
        }
    }

    /**
     * Finds the local row this document was pushed from.
     *
     * `pushItem` writes to `items/{localRowId}` while `remoteId` is still null,
     * and records it only in the write's success callback - so this snapshot can
     * legitimately land first. Without this match Room would insert a second row
     * for the same garment and the closet would show the item twice.
     *
     * The id alone is not proof: every device auto-generates row ids, so a doc
     * pushed from another phone could collide with an unrelated local row. The
     * garment has to match too, otherwise we would adopt - and then overwrite -
     * a row that has nothing to do with this document.
     */
    private suspend fun matchLocalPush(
        docId: String,
        ownerUserId: Long,
        incoming: ClothingItemEntity,
    ): ClothingItemEntity? {
        docId.toLongOrNull()?.let { localId ->
            clothingDao.getById(localId)?.takeIf { local ->
                local.userId == ownerUserId &&
                    local.name == incoming.name &&
                    local.category == incoming.category &&
                    local.createdAt == incoming.createdAt
            }?.let { return it }
        }
        // Exact-content match (same name/category/color/creation time).
        return clothingDao.findDuplicate(
            userId = ownerUserId,
            name = incoming.name,
            categoryName = incoming.category.name,
            color = incoming.color,
            createdAt = incoming.createdAt,
        )
    }

    // ---------------------------------------------------------- mapping

    private fun UserEntity.toFirebaseMap(): Map<String, Any?> = mapOf(
        "name" to name,
        "email" to email,
        "age" to age,
        "phone" to phone,
        "location" to location,
        "avatarFileName" to null, // binary images stay local; Firestore holds the rest
        "updatedAt" to FieldValue.serverTimestamp(),
    )

    private fun ClothingItemEntity.toFirebaseMap(): Map<String, Any?> = mapOf(
        "name" to name,
        "category" to category.name,
        "color" to color,
        "season" to season.name,
        "occasion" to occasion.name,
        "pattern" to pattern,
        "style" to style,
        "material" to material,
        "aiDescription" to aiDescription,
        "createdAt" to createdAt,
        "hasImage" to (imageFileName != null),
    )

    private fun DocumentSnapshot.toClothingItem(): ClothingItemEntity? {
        val name = getString("name") ?: return null
        return ClothingItemEntity(
            id = 0,
            userId = 0L,
            name = name,
            category = runCatching { Category.valueOf(getString("category") ?: "OTHER") }.getOrDefault(Category.OTHER),
            color = getString("color") ?: "",
            season = runCatching { Season.valueOf(getString("season") ?: "ALL_SEASON") }.getOrDefault(Season.ALL_SEASON),
            occasion = runCatching { Occasion.valueOf(getString("occasion") ?: "OTHER") }.getOrDefault(Occasion.OTHER),
            pattern = getString("pattern"),
            style = getString("style"),
            material = getString("material"),
            aiDescription = getString("aiDescription"),
            createdAt = (get("createdAt") as? Number)?.toLong() ?: System.currentTimeMillis(),
        )
    }

    private operator fun SyncState.invoke(status: Status, detail: String): SyncState =
        copy(status = status, detail = detail)

    private companion object {
        const val TAG = "FirebaseSync"
        const val USERS = "users"
        const val ITEMS = "items"
    }
}
