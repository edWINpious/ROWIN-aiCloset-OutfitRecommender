package com.example.rowin_adlproject.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Checkroom
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.outlined.Checkroom
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Style
import androidx.compose.ui.graphics.vector.ImageVector

/** Screen destinations for the bottom navigation (feature #17). */
enum class RowinDestination(
    val label: String,
    val filledIcon: ImageVector,
    val outlinedIcon: ImageVector,
) {
    HOME("Home", Icons.Filled.Home, Icons.Outlined.Home),
    CLOSET("Closet", Icons.Filled.Checkroom, Icons.Outlined.Checkroom),
    OUTFITS("Outfits", Icons.Filled.Style, Icons.Outlined.Style),
    PROFILE("Profile", Icons.Filled.Person, Icons.Outlined.Person),
}
