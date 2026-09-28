package com.example.rowin_adlproject.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.sessionDataStore by preferencesDataStore(name = "rowin_session")

/** Session lifecycle: Loading until the persisted session has been read. */
sealed interface SessionState {
    data object Loading : SessionState
    data object SignedOut : SessionState
    data class SignedIn(val userId: Long) : SessionState
}

/**
 * Persists which user is logged in (feature #1) so the app can restore the
 * session across launches. All wardrobe data displayed is scoped to this id,
 * which is what makes every screen dynamic per-user.
 */
class SessionManager(private val context: Context) {

    private val KEY_USER_ID = longPreferencesKey("logged_in_user_id")

    /**
     * Session state as a cold flow: the first emission reflects the stored
     * session (SignedIn/SignedOut); before that, collectors see nothing, so
     * the UI should hold on [SessionState.Loading].
     */
    val sessionState: Flow<SessionState> = context.sessionDataStore.data.map { prefs ->
        prefs[KEY_USER_ID]?.let { SessionState.SignedIn(it) } ?: SessionState.SignedOut
    }

    suspend fun startSession(userId: Long) {
        context.sessionDataStore.edit { it[KEY_USER_ID] = userId }
    }

    suspend fun endSession() {
        context.sessionDataStore.edit { it.remove(KEY_USER_ID) }
    }
}
