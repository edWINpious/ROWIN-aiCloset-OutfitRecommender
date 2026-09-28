package com.example.rowin_adlproject.ui.closet

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items as lazyItems
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.rowin_adlproject.data.Category
import com.example.rowin_adlproject.ui.components.ClothingCard
import com.example.rowin_adlproject.ui.components.RowinEmptyState
import com.example.rowin_adlproject.ui.components.RowinFilterChip
import com.example.rowin_adlproject.ui.components.RowinIcons
import com.example.rowin_adlproject.ui.components.displayName
import com.example.rowin_adlproject.ui.components.RowinTextField

/**
 * Features #3-#5: My Closet - all items as cards, search, category filtering,
 * FAB to Add Clothing, and the informative empty state (#19).
 */
@Composable
fun MyClosetScreen(
    userId: Long,
    onOpenItem: (Long) -> Unit,
    onAddClothing: () -> Unit,
) {
    val context = LocalContext.current
    val factory = remember(userId) { ClosetViewModel.Factory(context, userId) }
    val vm: ClosetViewModel = viewModel(factory = factory)

    val items by vm.items.collectAsState()
    val query by vm.searchQuery.collectAsState()
    val category by vm.selectedCategory.collectAsState()

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header: title + count + filter icon (visual per design)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                Text(
                    text = "My Closet",
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Box(modifier = Modifier.weight(1f))
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(MaterialTheme.colorScheme.surfaceContainerLowest, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = RowinIcons.Filter,
                        contentDescription = "Filters",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }

            // Search bar
            RowinTextField(
                value = query,
                onValueChange = { vm.searchQuery.value = it },
                label = "",
                placeholder = "Search your wardrobe",
                modifier = Modifier.padding(horizontal = 20.dp),
            )

            // Category chips
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp),
                modifier = Modifier.padding(vertical = 16.dp),
            ) {
                item {
                    RowinFilterChip(
                        label = "All",
                        selected = category == null,
                        onClick = { vm.selectedCategory.value = null },
                    )
                }
                lazyItems(Category.entries.toList()) { categoryItem ->
                    RowinFilterChip(
                        label = categoryItem.displayName(),
                        selected = category == categoryItem,
                        onClick = {
                            vm.selectedCategory.value =
                                if (category == categoryItem) null else categoryItem
                        },
                    )
                }
            }

            // Grid or empty state
            if (items.isEmpty()) {
                RowinEmptyState(
                    icon = RowinIcons.ClosetOutlined,
                    title = if (query.isBlank() && category == null) "Your closet is empty"
                    else "No items found",
                    description = if (query.isBlank() && category == null) {
                        "Start building your digital wardrobe by adding your first clothing item."
                    } else {
                        "Try a different search or category filter."
                    },
                    actionLabel = if (query.isBlank() && category == null) "Add Clothing" else null,
                    onAction = if (query.isBlank() && category == null) onAddClothing else null,
                )
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        start = 20.dp, end = 20.dp, bottom = 120.dp,
                    ),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(items, key = { it.id }) { item ->
                        ClothingCard(
                            item = item,
                            imageFile = vm.imageFileFor(item),
                            onClick = { onOpenItem(item.id) },
                        )
                    }
                }
            }
        }

        // FAB: Add Clothing (design shows extended pill FAB)
        ExtendedFabRow(
            label = "Add Clothing",
            onClick = onAddClothing,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = 100.dp),
        )
    }
}

@Composable
private fun ExtendedFabRow(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        shadowElevation = 6.dp,
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}
