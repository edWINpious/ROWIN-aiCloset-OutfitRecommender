package com.example.rowin_adlproject.ui.profile

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.example.rowin_adlproject.data.FirebaseSyncManager
import com.example.rowin_adlproject.data.UserEntity
import com.example.rowin_adlproject.ui.rowinContainer
import com.example.rowin_adlproject.ui.components.RowinButton
import com.example.rowin_adlproject.ui.components.RowinIcons
import com.example.rowin_adlproject.ui.components.RowinOutlinedButton
import com.example.rowin_adlproject.ui.components.RowinTextField
import com.example.rowin_adlproject.ui.theme.Error
import com.example.rowin_adlproject.ui.theme.ErrorContainer

/**
 * Feature #15 - User Profile - 1:1 with the Stitch profile design: big
 * avatar (editable), identity, My Activity stats, Account rows (Edit
 * Profile / Saved Outfits / AI Assistant / App Settings) and the Sign Out
 * confirmation bottom sheet (#18). App Settings shows live Firebase sync
 * status (practical #4) and network state (practical #5).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    user: UserEntity?,
    userId: Long,
    avatarFile: java.io.File?,
    onEditProfile: () -> Unit,
    onOpenSavedOutfits: () -> Unit,
    onOpenAssistant: () -> Unit,
    onOpenAlerts: () -> Unit,
    onSignOut: () -> Unit,
) {
    val context = LocalContext.current
    val container = remember { context.rowinContainer() }
    var showSignOutSheet by remember { mutableStateOf(false) }
    var showSettingsSheet by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        // Profile header (editable avatar per profile_detail design)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 24.dp, bottom = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest, CircleShape)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onEditProfile,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (avatarFile != null) {
                    AsyncImage(
                        model = avatarFile,
                        contentDescription = "Profile photo",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().clip(CircleShape),
                    )
                } else {
                    Text(
                        text = user?.name?.firstOrNull()?.uppercase() ?: "R",
                        style = MaterialTheme.typography.displayLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Text(
                text = user?.name ?: "ROWIN User",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 16.dp),
            )
            Text(
                text = user?.email ?: "",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // My Activity stats (dynamic)
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
            Text(
                text = "MY ACTIVITY",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            ProfileStats(userId = userId)
        }

        // Account section
        Column(modifier = Modifier.padding(horizontal = 20.dp)) {
            Text(
                text = "ACCOUNT",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Column {
                    AccountRow(
                        icon = RowinIcons.PersonOutlined,
                        label = "Edit Profile",
                        onClick = onEditProfile,
                    )
                    AccountRow(
                        icon = RowinIcons.Bookmark,
                        label = "Saved Outfits",
                        onClick = onOpenSavedOutfits,
                    )
                    AccountRow(
                        icon = RowinIcons.AutoAwesome,
                        label = "AI Style Assistant",
                        onClick = onOpenAssistant,
                    )
                    AccountRow(
                        icon = RowinIcons.Notifications,
                        label = "AI Alerts",
                        onClick = onOpenAlerts,
                    )
                    AccountRow(
                        icon = RowinIcons.Settings,
                        label = "App Settings",
                        onClick = { showSettingsSheet = true },
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
            RowinOutlinedButton(
                text = "Sign Out",
                onClick = { showSignOutSheet = true },
                danger = true,
            )
            Spacer(Modifier.height(32.dp))
        }
    }

    // Sign out confirmation (feature #18, per Stitch design)
    if (showSignOutSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSignOutSheet = false },
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            SignOutSheetContent(
                onSignOut = {
                    showSignOutSheet = false
                    onSignOut()
                },
                onCancel = { showSignOutSheet = false },
            )
        }
    }

    // App Settings: Firebase connectivity + notifications (practicals #4/#5/#10)
    if (showSettingsSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSettingsSheet = false },
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            SettingsSheetContent(onClose = { showSettingsSheet = false })
        }
    }
}

@Composable
private fun SettingsSheetContent(onClose: () -> Unit) {
    val context = LocalContext.current
    val container = remember { context.rowinContainer() }
    val sync by container.firebaseSync.state.collectAsState()
    val online by container.networkMonitor.isOnline.collectAsState(initial = true)
    val alertsOn = remember { container.alertsManager.notificationsEnabled() }

    val (statusIcon, statusColor) = when (sync.status) {
        FirebaseSyncManager.Status.CONNECTED -> RowinIcons.CloudDone to MaterialTheme.colorScheme.primary
        FirebaseSyncManager.Status.SYNCING -> RowinIcons.Sync to MaterialTheme.colorScheme.primary
        FirebaseSyncManager.Status.LOCAL_ONLY -> RowinIcons.CloudOff to MaterialTheme.colorScheme.onSurfaceVariant
        else -> RowinIcons.CloudOff to MaterialTheme.colorScheme.error
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .navigationBarsPadding()
            .padding(bottom = 24.dp),
    ) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .align(Alignment.CenterHorizontally),
        )
        Text(
            text = "App Settings",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 16.dp, bottom = 12.dp),
        )

        SettingsRow(
            icon = statusIcon,
            title = "Firebase ${sync.status.name.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }}",
            subtitle = sync.detail,
            tint = statusColor,
        )
        SettingsRow(
            icon = if (online) RowinIcons.CloudDone else RowinIcons.CloudOff,
            title = if (online) "Network online" else "Network offline",
            subtitle = "Wardrobe data stays available offline and syncs automatically.",
            tint = if (online) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SettingsRow(
            icon = RowinIcons.Notifications,
            title = if (alertsOn) "AI alerts enabled" else "AI alerts blocked",
            subtitle = if (alertsOn) "Stylist notifications are on (Android 13+ permission)." else "Allow notifications in system settings to receive stylist alerts.",
            tint = if (alertsOn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        RowinOutlinedButton(text = "Close", onClick = onClose)
    }
}

@Composable
private fun SettingsRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, tint: Color) {
    Row(modifier = Modifier.padding(vertical = 10.dp), verticalAlignment = Alignment.Top) {
        Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        Column(modifier = Modifier.padding(start = 12.dp)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Dynamic stats: live wardrobe + outfit counts from the database. */
@Composable
private fun ProfileStats(userId: Long) {
    val context = LocalContext.current
    val factory = remember(userId) {
        com.example.rowin_adlproject.ui.home.HomeViewModel.Factory(context, userId)
    }
    val vm: com.example.rowin_adlproject.ui.home.HomeViewModel = viewModel(factory = factory)
    val clothing by vm.clothingCount.collectAsState()
    val outfits by vm.outfitsCount.collectAsState()

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        StatCard(count = clothing, label = "Wardrobe Items", icon = RowinIcons.ClosetOutlined, modifier = Modifier.weight(1f))
        StatCard(count = outfits, label = "Outfits Saved", icon = RowinIcons.Styler, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun StatCard(
    count: Int,
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant),
        modifier = modifier,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(MaterialTheme.colorScheme.surfaceContainerLow, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
            }
            Text(
                text = "$count",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 12.dp),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AccountRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp),
        )
        Icon(
            imageVector = RowinIcons.Forward,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.outlineVariant,
            modifier = Modifier.size(20.dp),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SignOutSheetContent(onSignOut: () -> Unit, onCancel: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .navigationBarsPadding()
            .padding(bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        )
        Box(
            modifier = Modifier
                .padding(top = 24.dp)
                .size(64.dp)
                .background(ErrorContainer.copy(alpha = 0.25f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = RowinIcons.Logout,
                contentDescription = null,
                tint = Error,
                modifier = Modifier.size(28.dp),
            )
        }
        Text(
            text = "Sign Out?",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(top = 16.dp),
        )
        Text(
            text = "Are you sure you want to sign out of your ROWIN account?",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp),
        )
        RowinButton(
            text = "SIGN OUT",
            onClick = onSignOut,
            modifier = Modifier.padding(top = 24.dp),
        )
        RowinOutlinedButton(
            text = "Stay Signed In",
            onClick = onCancel,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

/**
 * Feature #16 - Edit Profile - 1:1 with the Stitch profile_detail design:
 * avatar with edit badge, Full Name / Email / Phone / Location icon fields.
 */
@Composable
fun EditProfileScreen(
    user: UserEntity,
    avatarFile: java.io.File?,
    onSave: (UserEntity) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val container = remember { context.rowinContainer() }
    var name by remember { mutableStateOf(user.name) }
    var email by remember { mutableStateOf(user.email) }
    var phone by remember { mutableStateOf(user.phone ?: "") }
    var location by remember { mutableStateOf(user.location ?: "") }
    var error by remember { mutableStateOf<String?>(null) }
    var newAvatar by remember { mutableStateOf<String?>(null) }

    val avatarPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri: Uri? ->
        uri?.let {
            runCatching { container.repository.imageStore.importAvatarFromUri(it) }
                .onSuccess { fileName ->
                    user.avatarFileName?.let(container.repository.imageStore::deleteAvatar)
                    newAvatar = fileName
                }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            androidx.compose.material3.IconButton(onClick = onBack) {
                Icon(imageVector = RowinIcons.Back, contentDescription = "Back")
            }
            Text(
                text = "Edit Profile",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
            )
            Text(
                text = "Save",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .padding(horizontal = 12.dp)
                    .clickable {
                        when {
                            name.isBlank() -> error = "Please enter your name."
                            !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches() ->
                                error = "Enter a valid email address."
                            else -> onSave(
                                user.copy(
                                    name = name.trim(),
                                    email = email.trim(),
                                    phone = phone.trim().ifEmpty { null },
                                    location = location.trim().ifEmpty { null },
                                    avatarFileName = newAvatar ?: user.avatarFileName,
                                ),
                            )
                        }
                    },
            )
        }

        Column(
            modifier = Modifier.padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Avatar + edit badge (per mock)
            Box(
                modifier = Modifier
                    .padding(top = 16.dp, bottom = 4.dp)
                    .size(128.dp),
            ) {
                val avatarToShow = newAvatar?.let { container.repository.imageStore.avatarFileFor(it) } ?: avatarFile
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest, CircleShape)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    if (avatarToShow != null) {
                        AsyncImage(
                            model = avatarToShow,
                            contentDescription = "Profile photo",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize().clip(CircleShape),
                        )
                    } else {
                        Text(
                            text = user.name.firstOrNull()?.uppercase() ?: "R",
                            style = MaterialTheme.typography.displayLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                Surface(
                    onClick = {
                        avatarPicker.launch(
                            androidx.activity.result.PickVisualMediaRequest(
                                ActivityResultContracts.PickVisualMedia.ImageOnly,
                            ),
                        )
                    },
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    border = androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.surface),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(40.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = RowinIcons.Edit,
                            contentDescription = "Update photo",
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
            Text(
                text = "UPDATE PHOTO",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )

            Spacer(Modifier.height(24.dp))
            RowinTextField(
                value = name,
                onValueChange = { name = it; error = null },
                label = "Full Name",
                placeholder = "Your name",
                leadingIcon = {
                    Icon(RowinIcons.Person, contentDescription = null, tint = MaterialTheme.colorScheme.outline)
                },
            )
            Spacer(Modifier.height(16.dp))
            RowinTextField(
                value = email,
                onValueChange = { email = it; error = null },
                label = "Email Address",
                placeholder = "you@example.com",
                leadingIcon = {
                    Icon(RowinIcons.Mail, contentDescription = null, tint = MaterialTheme.colorScheme.outline)
                },
            )
            Spacer(Modifier.height(16.dp))
            RowinTextField(
                value = phone,
                onValueChange = { phone = it; error = null },
                label = "Phone Number",
                placeholder = "+1 (555) 000-0000",
                leadingIcon = {
                    Icon(RowinIcons.Phone, contentDescription = null, tint = MaterialTheme.colorScheme.outline)
                },
            )
            Spacer(Modifier.height(16.dp))
            RowinTextField(
                value = location,
                onValueChange = { location = it; error = null },
                label = "Location",
                placeholder = "City, Country",
                leadingIcon = {
                    Icon(RowinIcons.LocationOn, contentDescription = null, tint = MaterialTheme.colorScheme.outline)
                },
            )

            if (error != null) {
                Text(
                    text = error!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }

            Spacer(Modifier.height(28.dp))
            RowinButton(
                text = "Save Changes",
                onClick = {
                    when {
                        name.isBlank() -> error = "Please enter your name."
                        !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches() ->
                            error = "Enter a valid email address."
                        else -> onSave(
                            user.copy(
                                name = name.trim(),
                                email = email.trim(),
                                phone = phone.trim().ifEmpty { null },
                                location = location.trim().ifEmpty { null },
                                avatarFileName = newAvatar ?: user.avatarFileName,
                            ),
                        )
                    }
                },
            )
            Spacer(Modifier.height(8.dp))
            RowinOutlinedButton(text = "Cancel", onClick = onBack)
            Spacer(Modifier.height(32.dp))
        }
    }
}
