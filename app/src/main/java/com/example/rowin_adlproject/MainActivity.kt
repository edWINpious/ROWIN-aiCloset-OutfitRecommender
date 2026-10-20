package com.example.rowin_adlproject

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.rowin_adlproject.data.AppContainer
import com.example.rowin_adlproject.data.SessionState
import com.example.rowin_adlproject.databinding.ActivityMainBinding
import com.example.rowin_adlproject.databinding.ComponentRowinBottomBarBinding
import com.example.rowin_adlproject.databinding.ComponentRowinTopBarBinding
import com.example.rowin_adlproject.ui.RowinViewModel
import com.example.rowin_adlproject.ui.alerts.AlertsFragment
import com.example.rowin_adlproject.ui.auth.LoginFragment
import com.example.rowin_adlproject.ui.auth.RegisterFragment
import com.example.rowin_adlproject.ui.chat.ChatFragment
import com.example.rowin_adlproject.ui.closet.AddClothingFragment
import com.example.rowin_adlproject.ui.closet.CameraCaptureFragment
import com.example.rowin_adlproject.ui.closet.ClosetFragment
import com.example.rowin_adlproject.ui.closet.ClothingDetailFragment
import com.example.rowin_adlproject.ui.home.HomeFragment
import com.example.rowin_adlproject.ui.outfits.OutfitDetailFragment
import com.example.rowin_adlproject.ui.outfits.OutfitPlannerFragment
import com.example.rowin_adlproject.ui.outfits.SavedOutfitsFragment
import com.example.rowin_adlproject.ui.profile.EditProfileFragment
import com.example.rowin_adlproject.ui.profile.ProfileFragment
import com.example.rowin_adlproject.ui.view.Destination
import com.example.rowin_adlproject.ui.view.Loaders
import com.example.rowin_adlproject.ui.view.NavController
import com.example.rowin_adlproject.ui.view.Screen
import com.example.rowin_adlproject.ui.view.ScreenChrome
import com.example.rowin_adlproject.ui.view.ScreenHost
import com.example.rowin_adlproject.ui.view.ScreenRows
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Single-activity host: splash + session gate, the shared top/bottom chrome,
 * and one fragment per screen (the XML layouts are the live UI).
 */
class MainActivity : AppCompatActivity(), ScreenHost {

    private lateinit var binding: ActivityMainBinding
    private lateinit var topBar: ComponentRowinTopBarBinding
    private lateinit var bottomBar: ComponentRowinBottomBarBinding

    override lateinit var rowin: RowinViewModel
        private set

    private val container: AppContainer by lazy {
        (application as RowinApplication).container
    }

    override val userId: Long
        get() = (rowin.sessionState.value as? SessionState.SignedIn)?.userId ?: 1L

    private var nav: NavController? = null
    private var signedIn = false
    private var hasUnreadAlerts = false

    /** App-lifetime scope for the Firestore snapshot listener. */
    private val syncScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    private val navHost: NavController.NavigationHost = object : NavController.NavigationHost {
        override fun present(destination: Destination) = presentScreen(destination)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        rowin = ViewModelProvider(this, Loaders.rowin(this))[RowinViewModel::class.java]

        topBar = ComponentRowinTopBarBinding.inflate(layoutInflater, binding.topBarContainer)
        bottomBar = ComponentRowinBottomBarBinding.inflate(layoutInflater, binding.bottomBarContainer)

        // System back mirrors the Compose NavHost: pop, or exit when at root.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (nav?.back() != true) finish()
            }
        })

        observeSession()
        observeChromeState()
    }

    // ------------------------------------------------------------ session gate

    private fun observeSession() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                rowin.sessionState.collect { state ->
                    val nowSignedIn = state is SessionState.SignedIn
                    if (nowSignedIn != signedIn) {
                        signedIn = nowSignedIn
                        nav = NavController(navHost)
                        if (nowSignedIn) {
                            requestNotificationPermissionIfNeeded()
                            nav?.reset(Destination(Screen.HOME))
                        } else {
                            container.firebaseSync.stop()
                            nav?.reset(Destination(Screen.LOGIN))
                        }
                    } else if (nav == null) {
                        nav = NavController(navHost)
                        nav?.reset(Destination(if (nowSignedIn) Screen.HOME else Screen.LOGIN))
                    }
                }
            }
        }

        // Firebase sync (practical #4) keyed on the signed-in account's email.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(rowin.sessionState, rowin.user) { session, user -> session to user }
                    .collect { (session, user) ->
                        val email = user?.email
                        if (session is SessionState.SignedIn && email != null) {
                            container.onUserKnown(session.userId, email)
                            container.firebaseSync.start(syncScope, email)
                        } else if (session !is SessionState.SignedIn) {
                            container.firebaseSync.stop()
                        }
                    }
            }
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // ---------------------------------------------------------------- chrome

    private fun observeChromeState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                // AI alerts (practical #10): recompute on every wardrobe change
                // and keep the top-bar unread dot in sync.
                rowin.alertsVersion.collect { version ->
                    hasUnreadAlerts = version > 0
                    lifecycleScope.launch {
                        container.alertsManager.refresh(container.repository.getAllClothing(userId))
                    }
                    if (binding.topBarContainer.visibility == android.view.View.VISIBLE) {
                        bindTopBar()
                    }
                }
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                rowin.user.collect {
                    if (binding.topBarContainer.visibility == android.view.View.VISIBLE) {
                        bindTopBar()
                    }
                }
            }
        }
    }

    private fun bindTopBar() {
        ScreenChrome.bindTopBar(
            binding = topBar,
            userName = rowin.user.value?.name,
            hasUnread = hasUnreadAlerts,
            onBell = { open(Screen.ALERTS) },
        )
    }

    private fun presentScreen(destination: Destination) {
        val screen = destination.screen
        val topLevel = signedIn && screen in NavController.TOP_LEVEL_ROUTES

        binding.topBarContainer.visibility = if (topLevel) android.view.View.VISIBLE else android.view.View.GONE
        binding.bottomBarContainer.visibility = if (topLevel) android.view.View.VISIBLE else android.view.View.GONE
        if (topLevel) {
            bindTopBar()
            ScreenRows.bindBottomBar(bottomBar, screen) { tab -> nav?.openTab(tab) }
        }

        supportFragmentManager.beginTransaction()
            .replace(R.id.nav_host_container, createFragment(destination), "screen_${screen.name}")
            .commitAllowingStateLoss()
    }

    private fun createFragment(destination: Destination): Fragment {
        val args = Bundle().apply { destination.itemId?.let { putLong(ARG_ITEM_ID, it) } }
        return when (destination.screen) {
            Screen.LOGIN -> LoginFragment()
            Screen.REGISTER -> RegisterFragment()
            Screen.HOME -> HomeFragment()
            Screen.CLOSET -> ClosetFragment()
            Screen.OUTFITS -> OutfitPlannerFragment().apply { arguments = args }
            Screen.PROFILE -> ProfileFragment()
            Screen.ADD_CLOTHING -> AddClothingFragment().apply { arguments = args }
            Screen.CAMERA -> CameraCaptureFragment()
            Screen.CLOTHING_DETAIL -> ClothingDetailFragment().apply { arguments = args }
            Screen.EDIT_CLOTHING -> AddClothingFragment().apply { arguments = args }
            Screen.EDIT_OUTFIT -> OutfitPlannerFragment().apply { arguments = args }
            Screen.OUTFIT_DETAIL -> OutfitDetailFragment().apply { arguments = args }
            Screen.EDIT_PROFILE -> EditProfileFragment()
            Screen.CHAT -> ChatFragment()
            Screen.ALERTS -> AlertsFragment()
            Screen.SAVED_OUTFITS -> SavedOutfitsFragment()
        }
    }

    // ------------------------------------------------------------ ScreenHost

    override fun open(screen: Screen, itemId: Long?) {
        nav?.open(screen, itemId)
    }

    override fun back() {
        nav?.back()
    }

    companion object {
        /** Optional itemId / outfitId argument for detail + edit screens. */
        const val ARG_ITEM_ID = "arg_item_id"
    }
}
