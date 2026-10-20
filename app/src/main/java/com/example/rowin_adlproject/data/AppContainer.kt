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
import kotlinx.coroutines.launch

/**
 * Minimal manual DI container - everything the app needs, created once.
 *
 * Firebase (practical #4): Firestore initializes lazily inside
 * [FirebaseSyncManager]; without google-services.json the manager reports
 * LOCAL_ONLY and every push no-ops, keeping the app fully functional.
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

    // Resolves the local user id for a cloud email by scanning cached users
    // (in-memory list, no suspend needed by the sync callback).
    private val userEmails = java.util.concurrent.ConcurrentHashMap<String, Long>()

    val firebaseSync: FirebaseSyncManager = FirebaseSyncManager(
        userDao = database.userDao(),
        clothingDao = database.clothingDao(),
        resolveUserId = { email -> userEmails[email.lowercase()] ?: 1L },
    ).also { sync ->
        repository.sync = sync
        // Seed the email -> id map as users register/sign in.
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            database.userDao().getAllUsers().forEach { userEmails[it.email.lowercase()] = it.id }
        }
    }

    /** Call on register/login so cloud items land under the right account. */
    fun onUserKnown(id: Long, email: String) {
        userEmails[email.lowercase()] = id
    }
}
