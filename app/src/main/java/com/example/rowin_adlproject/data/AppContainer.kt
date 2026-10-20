package com.example.rowin_adlproject.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn

/**
 * Minimal manual DI container - everything the app needs, created once.
 *
 * Firebase (practicals #1/#4): [AuthService] and [FirebaseSyncManager] both
 * initialize lazily; without google-services.json accounts stay local, the
 * sync manager reports LOCAL_ONLY and every push no-ops, keeping the app
 * fully functional.
 */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    private val database = RowinDatabase.get(appContext)

    val repository: WardrobeRepository = WardrobeRepository.from(appContext)
    val sessionManager: SessionManager = SessionManager(appContext)
    val networkMonitor: NetworkMonitor = NetworkMonitor(appContext)
    val alertsManager: AlertsManager = AlertsManager(appContext)

    private val memoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * The whole signed-in account's data as one live snapshot, loaded the
     * moment a session starts (practical #8).
     *
     * This is what the AI chat is grounded in: because it is [SharingStarted]
     * .Eagerly, the wardrobe is already warm the first time the stylist is
     * asked a question - it is never an empty list just because nothing in the
     * UI happened to subscribe yet. Room is the merged local + Firestore view,
     * so anything Firebase syncs into the closet shows up here automatically.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val sessionMemory: StateFlow<SessionMemory> = sessionManager.sessionState
        .flatMapLatest { state ->
            if (state !is SessionState.SignedIn) {
                flowOf(SessionMemory())
            } else {
                combine(
                    repository.observeUser(state.userId),
                    repository.observeClothing(state.userId),
                    repository.observeOutfits(state.userId),
                ) { profile, items, outfits ->
                    SessionMemory(
                        userId = state.userId,
                        profile = profile,
                        items = items,
                        outfits = outfits,
                    )
                }
            }
        }
        .stateIn(memoryScope, SharingStarted.Eagerly, SessionMemory())

    val auth: AuthService = AuthService()

    val firebaseSync: FirebaseSyncManager = FirebaseSyncManager(
        userDao = database.userDao(),
        clothingDao = database.clothingDao(),
        outfitDao = database.outfitDao(),
        tombstoneDao = database.tombstoneDao(),
        deleteImage = { repository.imageStore.delete(it) },
    ).also { sync ->
        repository.sync = sync
        repository.auth = auth
    }

    /**
     * Starts cloud sync for the signed-in [user] - but only when Firebase
     * Auth holds a session for that same account, because the Firestore
     * rules reject every other uid anyway.
     */
    fun startSync(user: UserEntity) {
        val uid = auth.currentUid
        when {
            !firebaseSync.isAvailable -> firebaseSync.stop()
            uid != null && uid == user.firebaseUid -> firebaseSync.start(user, uid)
            // Signed in before cloud accounts existed, or offline at sign-in.
            else -> firebaseSync.pause("Sign out and sign in again to turn on cloud sync")
        }
    }

    /** Ends the local session and the Firebase one together. */
    suspend fun signOut() {
        firebaseSync.stop()
        repository.signOut()
        sessionManager.endSession()
    }
}
