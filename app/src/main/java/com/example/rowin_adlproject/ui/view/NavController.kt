package com.example.rowin_adlproject.ui.view

import com.example.rowin_adlproject.data.SessionState
import com.example.rowin_adlproject.ui.RowinViewModel

/** Route names for the app's navigation graph (mirrors the Compose Routes). */
enum class Screen {
    LOGIN,
    REGISTER,
    HOME,
    CLOSET,
    OUTFITS,
    PROFILE,
    ADD_CLOTHING,
    CAMERA,
    CLOTHING_DETAIL,
    EDIT_CLOTHING,
    EDIT_OUTFIT,
    OUTFIT_DETAIL,
    EDIT_PROFILE,
    CHAT,
    ALERTS,
    SAVED_OUTFITS,
}

data class Destination(val screen: Screen, val itemId: Long? = null)

/**
 * Back-stack based navigation manager replacing the Compose NavHost.
 * The Activity hosts fragment containers; this object decides which
 * destination is presented (including the optional item/outfit argument).
 */
class NavController(private val host: NavigationHost) {

    interface NavigationHost {
        fun present(destination: Destination)
    }

    private val backStack = ArrayDeque<Destination>()
    var current: Destination = Destination(Screen.HOME)
        private set

    fun open(screen: Screen, itemId: Long? = null) {
        backStack.addLast(current)
        current = Destination(screen, itemId)
        host.present(current)
    }

    /** Root-style navigation for bottom-bar tabs (HOME stays beneath). */
    fun openTab(screen: Screen) {
        backStack.clear()
        if (screen != Screen.HOME) backStack.addLast(Destination(Screen.HOME))
        current = Destination(screen)
        host.present(current)
    }

    /** Replaces the whole stack (session flips: login/logout). */
    fun reset(destination: Destination) {
        backStack.clear()
        current = destination
        host.present(current)
    }

    /** @return true when a destination was popped; false when the stack is empty. */
    fun back(): Boolean {
        val previous = backStack.removeLastOrNull() ?: return false
        current = previous
        host.present(current)
        return true
    }

    /** Re-renders the current destination (used after the session gate flips). */
    fun redeliver() = host.present(current)

    companion object {
        val TOP_LEVEL_ROUTES = setOf(Screen.HOME, Screen.CLOSET, Screen.OUTFITS, Screen.PROFILE)

        /** Signed-out session gate (same behavior as the Compose RowinApp). */
        fun SessionState?.isAuthenticated(): Boolean = this is SessionState.SignedIn

        fun RowinViewModel.signedInUserId(): Long? =
            (sessionState.value as? SessionState.SignedIn)?.userId
    }
}

/**
 * What every screen fragment gets from the hosting activity: the session
 * user id, the shared RowinViewModel, and navigation callbacks.
 */
interface ScreenHost {
    val userId: Long
    val rowin: com.example.rowin_adlproject.ui.RowinViewModel

    fun open(screen: Screen, itemId: Long? = null)
    fun back()
}
