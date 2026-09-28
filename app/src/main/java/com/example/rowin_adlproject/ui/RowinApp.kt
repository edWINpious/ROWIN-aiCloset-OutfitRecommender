package com.example.rowin_adlproject.ui

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.NavType
import com.example.rowin_adlproject.data.SessionState
import com.example.rowin_adlproject.data.UserEntity
import com.example.rowin_adlproject.ui.alerts.AlertsScreen
import com.example.rowin_adlproject.ui.auth.LoginScreen
import com.example.rowin_adlproject.ui.auth.RegisterScreen
import com.example.rowin_adlproject.ui.chat.ChatScreen
import com.example.rowin_adlproject.ui.closet.AddClothingScreen
import com.example.rowin_adlproject.ui.closet.CameraCaptureScreen
import com.example.rowin_adlproject.ui.closet.ClothingDetailScreen
import com.example.rowin_adlproject.ui.closet.MyClosetScreen
import com.example.rowin_adlproject.ui.components.InitialsAvatar
import com.example.rowin_adlproject.ui.components.NotificationBell
import com.example.rowin_adlproject.ui.components.RowinBottomBar
import com.example.rowin_adlproject.ui.components.RowinDestination
import com.example.rowin_adlproject.ui.components.RowinTopBar
import com.example.rowin_adlproject.ui.home.HomeScreen
import com.example.rowin_adlproject.ui.outfits.OutfitDetailScreen
import com.example.rowin_adlproject.ui.outfits.OutfitPlannerScreen
import com.example.rowin_adlproject.ui.outfits.SavedOutfitsScreen
import com.example.rowin_adlproject.ui.profile.EditProfileScreen
import com.example.rowin_adlproject.ui.profile.ProfileScreen

/** Route names for the app's navigation graph. */
object Routes {
    const val LOGIN = "login"
    const val REGISTER = "register"
    const val HOME = "home"
    const val CLOSET = "closet"
    const val OUTFITS = "outfits" // the Outfits tab IS the planner (per the design shell)
    const val PROFILE = "profile"
    const val ADD_CLOTHING = "add_clothing"
    const val CAMERA = "camera"
    const val CLOTHING_DETAIL = "clothing/{itemId}"
    const val EDIT_CLOTHING = "edit_clothing/{itemId}"
    const val EDIT_OUTFIT = "edit_outfit/{outfitId}"
    const val OUTFIT_DETAIL = "outfit/{outfitId}"
    const val EDIT_PROFILE = "edit_profile"
    const val CHAT = "chat"
    const val ALERTS = "alerts"
    const val SAVED_OUTFITS = "saved_outfits"

    fun clothingDetail(itemId: Long) = "clothing/$itemId"
    fun editClothing(itemId: Long) = "edit_clothing/$itemId"
    fun editOutfit(outfitId: Long) = "edit_outfit/$outfitId"
    fun outfitDetail(outfitId: Long) = "outfit/$outfitId"
}

private val TOP_LEVEL_ROUTES = setOf(Routes.HOME, Routes.CLOSET, Routes.OUTFITS, Routes.PROFILE)

/**
 * Root composable (feature #22): auth gate, navigation graph with smooth
 * transitions, bottom navigation, and the branded surface. On sign-in it
 * starts Firebase sync (practical #4) and refreshes AI alerts (practical #10).
 */
@Composable
fun RowinApp() {
    val context = LocalContext.current
    val container = remember { context.rowinContainer() }
    val vm: RowinViewModel = viewModel(factory = RowinViewModel.factory(context))
    val session by vm.sessionState.collectAsState()
    val user by vm.user.collectAsState()

    // Session side effects: start/stop cloud sync with the signed-in account.
    // Sync waits until the profile (and thus the email join key) is loaded.
    LaunchedEffect(session, user?.email) {
        val state = session
        val email = user?.email
        if (state is SessionState.SignedIn && email != null) {
            container.onUserKnown(state.userId, email)
            container.firebaseSync.start(vm.applicationScope(), email)
        } else if (state !is SessionState.SignedIn) {
            container.firebaseSync.stop()
        }
    }

    Surface(color = MaterialTheme.colorScheme.background) {
        when (session) {
            SessionState.Loading -> Box(modifier = Modifier.fillMaxSize())

            SessionState.SignedOut -> AuthNavHost(
                onLogin = { email, password, cb -> vm.login(email, password, cb) },
                onRegister = { name, email, password, cb -> vm.register(name, email, password, cb) },
            )

            is SessionState.SignedIn -> {
                val userId = (session as SessionState.SignedIn).userId
                MainNavHost(
                    userId = userId,
                    user = user,
                    onUpdateProfile = vm::updateProfile,
                    onSignOut = vm::signOut,
                )
            }
        }
    }
}

@Composable
private fun AuthNavHost(
    onLogin: (String, String, (Result<Long>) -> Unit) -> Unit,
    onRegister: (String, String, String, (Result<Long>) -> Unit) -> Unit,
) {
    val navController = rememberNavController()
    NavHost(
        navController = navController,
        startDestination = Routes.LOGIN,
        enterTransition = { fadeIn(tween(220)) + slideInHorizontally(tween(220)) { it / 12 } },
        exitTransition = { fadeOut(tween(180)) },
        popEnterTransition = { fadeIn(tween(220)) },
        popExitTransition = { fadeOut(tween(180)) + slideOutHorizontally(tween(180)) { it / 12 } },
    ) {
        composable(Routes.LOGIN) {
            LoginScreen(
                onLogin = onLogin,
                onNavigateToRegister = { navController.navigate(Routes.REGISTER) },
            )
        }
        composable(Routes.REGISTER) {
            RegisterScreen(
                onRegister = onRegister,
                onNavigateToLogin = { navController.popBackStack() },
            )
        }
    }
}

@Composable
private fun MainNavHost(
    userId: Long,
    user: UserEntity?,
    onUpdateProfile: (UserEntity) -> Unit,
    onSignOut: () -> Unit,
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val isTopLevel = currentRoute in TOP_LEVEL_ROUTES
    val context = LocalContext.current
    val container = remember { context.rowinContainer() }
    val vm: RowinViewModel = viewModel(factory = RowinViewModel.factory(context))
    val alertsVersion by vm.alertsVersion.collectAsState()

    // AI alerts (practical #10): recompute whenever the wardrobe changes and
    // raise system notifications for new suggestions.
    LaunchedEffect(alertsVersion, userId) {
        val wardrobe = container.repository.getAllClothing(userId)
        container.alertsManager.refresh(wardrobe)
    }

    // Android 13+ notification permission (practical #10).
    val notifPermission = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
    ) { }
    DisposableEffect(Unit) {
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            notifPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
        onDispose { }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Shared top app bar on the four tabs: avatar + ROWIN wordmark + bell
        if (isTopLevel) {
            RowinTopBar(
                leading = {
                    InitialsAvatar(initial = user?.name?.firstOrNull()?.uppercase() ?: "R")
                },
                trailing = {
                    NotificationBell(
                        hasUnread = alertsVersion > 0,
                        onClick = { navController.navigate(Routes.ALERTS) },
                    )
                },
            )
        }
        // Content: weight(1f) so the bottom bar always sits BELOW it (never overlaps)
        Box(modifier = Modifier.weight(1f)) {
            NavHost(
                navController = navController,
                startDestination = Routes.HOME,
                enterTransition = { fadeIn(tween(240)) + slideInHorizontally(tween(240)) { it / 16 } },
                exitTransition = { fadeOut(tween(180)) },
                popEnterTransition = { fadeIn(tween(240)) },
                popExitTransition = { fadeOut(tween(180)) + slideOutHorizontally(tween(180)) { it / 16 } },
            ) {
                // Tab: Home (feature #2)
                composable(Routes.HOME) {
                    HomeScreen(
                        user = user,
                        userId = userId,
                        onOpenCloset = { navController.navigate(Routes.CLOSET) },
                        onOpenPlanner = { navController.navigate(Routes.OUTFITS) },
                        onOpenProfile = { navController.navigate(Routes.PROFILE) },
                        onOpenItem = { navController.navigate(Routes.clothingDetail(it)) },
                    )
                }
                // Tab: My Closet (features #3-#5)
                composable(Routes.CLOSET) {
                    MyClosetScreen(
                        userId = userId,
                        onOpenItem = { navController.navigate(Routes.clothingDetail(it)) },
                        onAddClothing = { navController.navigate(Routes.ADD_CLOTHING) },
                    )
                }
                // Tab: Outfits = Outfit Planner itself (per the Stitch shell)
                composable(Routes.OUTFITS) {
                    OutfitPlannerScreen(
                        userId = userId,
                        editingOutfitId = null,
                        onSaved = { outfitId ->
                            navController.navigate(Routes.outfitDetail(outfitId))
                        },
                    )
                }
                // Tab: Profile (features #15)
                composable(Routes.PROFILE) {
                    ProfileScreen(
                        user = user,
                        userId = userId,
                        avatarFile = user?.avatarFileName?.let {
                            container.repository.imageStore.avatarFileFor(it)
                        },
                        onEditProfile = { navController.navigate(Routes.EDIT_PROFILE) },
                        onOpenSavedOutfits = { navController.navigate(Routes.SAVED_OUTFITS) },
                        onOpenAssistant = { navController.navigate(Routes.CHAT) },
                        onOpenAlerts = { navController.navigate(Routes.ALERTS) },
                        onSignOut = onSignOut,
                    )
                }
                // Saved Outfits list (feature #14) - pushed above the shell
                composable(Routes.SAVED_OUTFITS) {
                    SavedOutfitsScreen(
                        userId = userId,
                        onOpenOutfit = { navController.navigate(Routes.outfitDetail(it)) },
                        onCreateOutfit = { navController.popBackStack() },
                        onBack = { navController.popBackStack() },
                    )
                }
                // Add clothing (features #5-#7, #20) - camera result plumbing
                composable(Routes.ADD_CLOTHING) {
                    val entry = navController.currentBackStackEntry
                    var pendingCameraFile by remember(entry) {
                        mutableStateOf<java.io.File?>(
                            entry?.savedStateHandle?.get<java.io.File>(CAMERA_RESULT_KEY),
                        )
                    }
                    DisposableEffect(entry) {
                        entry?.savedStateHandle?.remove<java.io.File>(CAMERA_RESULT_KEY)
                        onDispose { }
                    }
                    AddClothingScreen(
                        userId = userId,
                        editingItem = null,
                        onDone = { navController.popBackStack() },
                        onClose = { navController.popBackStack() },
                        onOpenCamera = { navController.navigate(Routes.CAMERA) },
                        pendingCameraFile = pendingCameraFile,
                        onCameraConsumed = { pendingCameraFile = null },
                    )
                }
                // REAL in-app camera (feature #20 / practical #7)
                composable(Routes.CAMERA) {
                    CameraCaptureScreen(
                        onCaptured = { file ->
                            navController.previousBackStackEntry
                                ?.savedStateHandle?.set(CAMERA_RESULT_KEY, file)
                            navController.popBackStack()
                        },
                        onClose = { navController.popBackStack() },
                    )
                }
                composable(
                    Routes.EDIT_CLOTHING,
                    arguments = listOf(navArgument("itemId") { type = NavType.LongType }),
                ) { entry ->
                    val itemId = entry.arguments?.getLong("itemId") ?: return@composable
                    EditClothingLoader(
                        itemId = itemId,
                        userId = userId,
                        onDone = { navController.popBackStack() },
                        onClose = { navController.popBackStack() },
                    )
                }
                // Clothing detail (feature #7)
                composable(
                    Routes.CLOTHING_DETAIL,
                    arguments = listOf(navArgument("itemId") { type = NavType.LongType }),
                ) { entry ->
                    val itemId = entry.arguments?.getLong("itemId") ?: return@composable
                    ClothingDetailScreen(
                        itemId = itemId,
                        userId = userId,
                        onBack = { navController.popBackStack() },
                        onEdit = { navController.navigate(Routes.editClothing(it.id)) },
                        onDeleted = { navController.popBackStack() },
                    )
                }
                composable(
                    Routes.EDIT_OUTFIT,
                    arguments = listOf(navArgument("outfitId") { type = NavType.LongType }),
                ) { entry ->
                    val outfitId = entry.arguments?.getLong("outfitId") ?: return@composable
                    OutfitPlannerScreen(
                        userId = userId,
                        editingOutfitId = outfitId,
                        onSaved = { navController.popBackStack() },
                        onBack = { navController.popBackStack() },
                    )
                }
                // Outfit detail (feature #10, #13)
                composable(
                    Routes.OUTFIT_DETAIL,
                    arguments = listOf(navArgument("outfitId") { type = NavType.LongType }),
                ) { entry ->
                    val outfitId = entry.arguments?.getLong("outfitId") ?: return@composable
                    OutfitDetailScreen(
                        outfitId = outfitId,
                        userId = userId,
                        onBack = { navController.popBackStack() },
                        onEdit = { navController.navigate(Routes.editOutfit(it)) },
                        onDeleted = { navController.popBackStack() },
                    )
                }
                // AI chatbot (practical #8)
                composable(Routes.CHAT) {
                    ChatScreen(
                        userId = userId,
                        userName = user?.name,
                        onBack = { navController.popBackStack() },
                    )
                }
                // AI alerts feed (practical #10)
                composable(Routes.ALERTS) {
                    val alerts by container.alertsManager.alerts.collectAsState()
                    AlertsScreen(
                        alerts = alerts,
                        onOpenRoute = { route ->
                            when (route) {
                                "closet" -> navController.navigate(Routes.CLOSET)
                                "add_clothing" -> navController.navigate(Routes.ADD_CLOTHING)
                                "planner" -> navController.navigate(Routes.OUTFITS)
                            }
                        },
                        onBack = { navController.popBackStack() },
                    )
                }
                // Edit profile (feature #16)
                composable(Routes.EDIT_PROFILE) {
                    val currentUser = user
                    if (currentUser == null) {
                        Text(text = "Loading profile...", modifier = Modifier.padding(24.dp))
                    } else {
                        EditProfileScreen(
                            user = currentUser,
                            avatarFile = currentUser.avatarFileName?.let {
                                container.repository.imageStore.avatarFileFor(it)
                            },
                            onSave = { updated ->
                                onUpdateProfile(updated)
                                navController.popBackStack()
                            },
                            onBack = { navController.popBackStack() },
                        )
                    }
                }
            }
        }

        // Bottom navigation (feature #17) - a Column sibling BELOW the content,
        // matching the Stitch shell: bar at the very bottom, content scrolls above it.
        if (isTopLevel) {
            RowinBottomBar(
                selected = when (currentRoute) {
                    Routes.CLOSET -> RowinDestination.CLOSET
                    Routes.OUTFITS -> RowinDestination.OUTFITS
                    Routes.PROFILE -> RowinDestination.PROFILE
                    else -> RowinDestination.HOME
                },
                onSelect = { destination ->
                    val target = when (destination) {
                        RowinDestination.HOME -> Routes.HOME
                        RowinDestination.CLOSET -> Routes.CLOSET
                        RowinDestination.OUTFITS -> Routes.OUTFITS
                        RowinDestination.PROFILE -> Routes.PROFILE
                    }
                    navController.navigate(target) {
                        popUpTo(Routes.HOME) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
            )
        }
    }
}

/** SavedStateHandle key for the file captured by the in-app camera. */
private const val CAMERA_RESULT_KEY = "camera_result_file"

/** App-lifetime scope for sync listeners (survives composition). */
private fun RowinViewModel.applicationScope(): kotlinx.coroutines.CoroutineScope =
    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO + kotlinx.coroutines.SupervisorJob())

/** Loads the clothing item for editing and shows AddClothingScreen. */
@Composable
private fun EditClothingLoader(itemId: Long, userId: Long, onDone: () -> Unit, onClose: () -> Unit) {
    val context = LocalContext.current
    val vm: RowinViewModel = viewModel(factory = RowinViewModel.factory(context))
    var item by remember {
        mutableStateOf<com.example.rowin_adlproject.data.ClothingItemEntity?>(null)
    }
    LaunchedEffect(itemId) {
        item = vm.repository.getClothingById(itemId)
    }
    val loaded = item
    if (loaded == null) {
        Box(modifier = Modifier.fillMaxSize())
    } else {
        AddClothingScreen(
            userId = userId,
            editingItem = loaded,
            onDone = onDone,
            onClose = onClose,
            onOpenCamera = { /* camera from edit is not wired to keep state simple */ },
            pendingCameraFile = null,
            onCameraConsumed = { },
        )
    }
}
