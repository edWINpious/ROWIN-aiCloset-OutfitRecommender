package com.example.rowin_adlproject.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Checkroom
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Mail
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.Checkroom
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Style
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Single registry of icons used across ROWIN. The Stitch designs use Material
 * Symbols; these are the closest Material Icons available in Compose.
 */
object RowinIcons {
    // navigation
    val HomeFilled = Icons.Filled.Home
    val HomeOutlined = Icons.Outlined.Home
    val ClosetFilled = Icons.Filled.Checkroom
    val ClosetOutlined = Icons.Outlined.Checkroom
    val OutfitsFilled = Icons.Filled.Style
    val OutfitsOutlined = Icons.Outlined.Style
    val PersonFilled = Icons.Filled.Person
    val PersonOutlined = Icons.Outlined.Person

    // actions
    val Add = Icons.Filled.Add
    val Back = Icons.AutoMirrored.Filled.ArrowBack
    val Forward = Icons.AutoMirrored.Filled.ArrowForward
    val Send = Icons.AutoMirrored.Filled.Send
    val Close = Icons.Filled.Close
    val Delete = Icons.Filled.Delete
    val Edit = Icons.Filled.Edit
    val Search = Icons.Filled.Search
    val Filter = Icons.Filled.Tune
    val Notifications = Icons.Filled.Notifications
    val Settings = Icons.Filled.Settings
    val Bookmark = Icons.Filled.Bookmark
    val Save = Icons.Filled.Save
    val Logout = Icons.Filled.Logout
    val Check = Icons.Filled.Check
    val History = Icons.Filled.History
    val AutoAwesome = Icons.Filled.AutoAwesome

    // cloud / sync (practical #4)
    val CloudDone = Icons.Filled.CloudDone
    val CloudOff = Icons.Filled.CloudOff
    val Sync = Icons.Filled.Sync

    // media / attributes
    val PhotoLibrary = Icons.Filled.PhotoLibrary
    val CameraAlt = Icons.Filled.PhotoCamera
    val ImagePlaceholder = Icons.Filled.Image
    val Visibility = Icons.Filled.Visibility
    val VisibilityOff = Icons.Filled.VisibilityOff
    val Checkroom = Icons.Filled.Checkroom
    val Palette = Icons.Filled.Palette
    val CalendarMonth = Icons.Filled.CalendarMonth
    val LocalCafe = Icons.Filled.LocalCafe
    val Styler = Icons.Filled.Style
    val Person = Icons.Filled.Person
    val Phone = Icons.Filled.Phone
    val Mail = Icons.Filled.Mail
    val LocationOn = Icons.Filled.LocationOn
}
