package com.example.rowin_adlproject.ui.outfits

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.example.rowin_adlproject.data.OutfitWithItems
import com.example.rowin_adlproject.ui.components.RowinEmptyState
import com.example.rowin_adlproject.ui.components.RowinIcons
import com.example.rowin_adlproject.ui.components.displayName

/**
 * Feature #14 - Saved Outfits: grid of previously saved outfits with the
 * outfit name, season and occasion; opens the detail screen on tap.
 */
@Composable
fun SavedOutfitsScreen(
    userId: Long,
    onOpenOutfit: (Long) -> Unit,
    onCreateOutfit: () -> Unit,
    onBack: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val factory = remember(userId) { OutfitsViewModel.Factory(context, userId) }
    val vm: OutfitsViewModel = viewModel(factory = factory)
    val outfits by vm.outfits.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onBack != null) {
                androidx.compose.material3.IconButton(onClick = onBack) {
                    Icon(
                        imageVector = RowinIcons.Back,
                        contentDescription = "Back",
                    )
                }
            }
            Column(modifier = Modifier.padding(horizontal = if (onBack != null) 8.dp else 20.dp, vertical = 8.dp)) {
                Text(
                    text = "Saved Outfits",
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "${outfits.size} in your collection",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (outfits.isEmpty()) {
            RowinEmptyState(
                icon = RowinIcons.Styler,
                title = "No saved outfits yet",
                description = "Combine your clothing pieces in the planner and save your first look.",
                actionLabel = "Create Outfit",
                onAction = onCreateOutfit,
            )
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 120.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(outfits.toList(), key = { it.outfit.id }) { outfit ->
                    SavedOutfitCard(outfit, vm.imageStore, onClick = { onOpenOutfit(outfit.outfit.id) })
                }
            }
        }
    }
}

@Composable
private fun SavedOutfitCard(
    outfit: OutfitWithItems,
    imageStore: com.example.rowin_adlproject.data.ImageStore,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column {
            // First item's photo as the card visual
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .background(MaterialTheme.colorScheme.surfaceContainer),
                contentAlignment = Alignment.Center,
            ) {
                val firstWithImage = outfit.items.firstOrNull { it.imageFileName != null }
                val file = firstWithImage?.imageFileName?.let { imageStore.fileFor(it) }
                if (file != null) {
                    AsyncImage(
                        model = file,
                        contentDescription = outfit.outfit.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Icon(
                        imageVector = RowinIcons.Styler,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(32.dp),
                    )
                }
            }
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = outfit.outfit.name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${outfit.items.size} pieces - ${outfit.outfit.occasion.displayName()}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
