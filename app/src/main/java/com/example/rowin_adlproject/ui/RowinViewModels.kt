package com.example.rowin_adlproject.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.rowin_adlproject.data.AppContainer
import com.example.rowin_adlproject.data.UserEntity
import com.example.rowin_adlproject.data.WardrobeRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * App-level state: which user is logged in and their profile.
 * Every screen observes this so all displayed data belongs to the
 * signed-in user (the "DYNAMIC" requirement).
 */
class RowinViewModel(
    application: Application,
    private val container: AppContainer,
) : AndroidViewModel(application) {

    val repository: WardrobeRepository = container.repository

    /** Null while signed out. */
    val sessionState: StateFlow<com.example.rowin_adlproject.data.SessionState> =
        container.sessionManager.sessionState
            .stateIn(viewModelScope, SharingStarted.Eagerly, com.example.rowin_adlproject.data.SessionState.Loading)

    /** The signed-in user's profile, or null when signed out. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val user: StateFlow<UserEntity?> = sessionState
        .flatMapLatest { state ->
            if (state is com.example.rowin_adlproject.data.SessionState.SignedIn) {
                repository.observeUser(state.userId)
            } else {
                flowOf(null)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /**
     * Bumped whenever the wardrobe changes so the AI alerts engine (practical
     * #10) recomputes; also drives the top-bar notification dot.
     */
    val alertsVersion: StateFlow<Int> = sessionState
        .flatMapLatest { state ->
            if (state is com.example.rowin_adlproject.data.SessionState.SignedIn) {
                repository.observeClothing(state.userId)
            } else {
                flowOf(emptyList())
            }
        }
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    fun register(name: String, email: String, password: String, onDone: (Result<Long>) -> Unit) {
        viewModelScope.launch {
            val result = repository.register(name, email, password)
            result.onSuccess { id -> container.sessionManager.startSession(id) }
            onDone(result)
        }
    }

    fun login(email: String, password: String, onDone: (Result<Long>) -> Unit) {
        viewModelScope.launch {
            val result = repository.login(email, password)
            result.onSuccess { id -> container.sessionManager.startSession(id) }
            onDone(result)
        }
    }

    fun updateProfile(updated: UserEntity) {
        viewModelScope.launch { repository.updateProfile(updated) }
    }

    fun signOut() {
        viewModelScope.launch { container.sessionManager.endSession() }
    }

    companion object {
        /**
         * Factory wired to the app container (manual DI, no Hilt needed).
         * Accepts any Context (an Activity from LocalContext works too) and
         * unwraps the Application to reach the container.
         */
        fun factory(context: Context): ViewModelProvider.Factory {
            val app = context.applicationContext as com.example.rowin_adlproject.RowinApplication
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    RowinViewModel(app, app.container) as T
            }
        }
    }
}

/** Convenience accessor for screens creating feature-scoped view models. */
fun Context.rowinContainer(): AppContainer =
    (applicationContext as com.example.rowin_adlproject.RowinApplication).container
