package com.example.rowin_adlproject.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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
