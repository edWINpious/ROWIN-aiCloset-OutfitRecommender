package com.example.rowin_adlproject.ui.view

import android.os.Bundle
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

    /** The whole back stack, so rotation or process death returns to the same screen. */
    fun saveState(): Bundle {
        val entries = backStack + current
        return Bundle().apply {
            putStringArray(KEY_SCREENS, entries.map { it.screen.name }.toTypedArray())
            putLongArray(KEY_ITEM_IDS, entries.map { it.itemId ?: NO_ITEM }.toLongArray())
        }
    }

    /** Loads a [saveState] bundle without presenting anything; false if it is unusable. */
    fun restore(state: Bundle): Boolean {
        val screens = state.getStringArray(KEY_SCREENS) ?: return false
        val ids = state.getLongArray(KEY_ITEM_IDS) ?: return false
        if (screens.isEmpty() || screens.size != ids.size) return false
        val entries = screens.indices.map { i ->
            val screen = Screen.entries.firstOrNull { it.name == screens[i] } ?: return false
            Destination(screen, ids[i].takeIf { it != NO_ITEM })
        }
        backStack.clear()
        backStack.addAll(entries.dropLast(1))
        current = entries.last()
        return true
    }

    companion object {
        private const val KEY_SCREENS = "nav_screens"
        private const val KEY_ITEM_IDS = "nav_item_ids"
        private const val NO_ITEM = -1L

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
