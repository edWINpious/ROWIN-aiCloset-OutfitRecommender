package com.example.rowin_adlproject.ui.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.example.rowin_adlproject.data.ClothingItemEntity
import com.example.rowin_adlproject.ui.components.RowinIcons
import com.example.rowin_adlproject.ui.components.displayName
import com.example.rowin_adlproject.ui.theme.OnPrimary
import com.example.rowin_adlproject.ui.theme.OnPrimaryContainer
import com.example.rowin_adlproject.ui.theme.PrimaryContainer
import com.example.rowin_adlproject.ui.theme.PrimaryFixed
import com.example.rowin_adlproject.ui.theme.PrimaryFixedDim
import java.io.File

/**
 * Feature #2 - Home Dashboard. Personalized greeting (dynamic from the user
 * profile + device clock), live wardrobe stats, quick access cards, and the
 * recently-added rail. Tapping cards routes to Closet / Planner / Profile.
 */
@Composable
fun HomeScreen(
    user: com.example.rowin_adlproject.data.UserEntity?,
    userId: Long,
    onOpenCloset: () -> Unit,
    onOpenPlanner: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenItem: (Long) -> Unit,
) {
    val context = LocalContext.current
    val factory = remember(userId) { HomeViewModel.Factory(context, userId) }
    val vm: HomeViewModel = viewModel(factory = factory)

    val clothingCount by vm.clothingCount.collectAsState()
    val outfitsCount by vm.outfitsCount.collectAsState()
    val newThisWeek by vm.newThisWeek.collectAsState()
    val recentItems by vm.recentItems.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(modifier = Modifier.padding(horizontal = 20.dp)) {
            Text(
                text = "${HomeViewModel.currentGreeting()}, ${user?.name?.split(" ")?.firstOrNull() ?: "there"}",
                style = MaterialTheme.typography.displayLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = "Ready to curate your look for today?",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        // Hero closet card (bento main)
        ClosetHeroCard(
            itemCount = clothingCount,
            newThisWeek = newThisWeek,
            items = recentItems.take(3),
            imageFileFor = vm::imageFileFor,
            onClick = onOpenCloset,
            modifier = Modifier.padding(horizontal = 20.dp),
        )

        // Secondary quick cards
        Row(
            modifier = Modifier.padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            QuickCard(
                icon = RowinIcons.Styler,
                title = "Outfit Planner",
                subtitle = "Plan your week.",
                trailing = RowinIcons.CalendarMonth,
                onClick = onOpenPlanner,
                modifier = Modifier.weight(1f),
            )
            QuickCard(
                icon = RowinIcons.PersonOutlined,
                title = "Style Profile",
                subtitle = "Update preferences.",
                trailing = RowinIcons.Settings,
                onClick = onOpenProfile,
                modifier = Modifier.weight(1f),
            )
        }

        // Saved outfits strip (if any)
        if (outfitsCount > 0) {
            Text(
                text = "You have $outfitsCount saved ${if (outfitsCount == 1) "outfit" else "outfits"}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
        }

        // Recently added rail
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Recently Added",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "See all",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.clickable(onClick = onOpenCloset),
                )
            }
            if (recentItems.isEmpty()) {
                Text(
                    text = "Your closet is empty. Add your first item!",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                )
            } else {
                LazyRow(
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(top = 16.dp),
                ) {
                    items(recentItems.toList(), key = { it.id }) { item ->
                        RecentItemCard(item, vm.imageFileFor(item), onClick = { onOpenItem(item.id) })
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun ClosetHeroCard(
    itemCount: Int,
    newThisWeek: Int,
    items: List<ClothingItemEntity>,
    imageFileFor: (ClothingItemEntity) -> File?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Box(
        modifier = modifier
            .fillMaxWidth()
            .scale(if (pressed) 0.99f else 1f)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.verticalGradient(listOf(PrimaryContainer, Color(0xFF26503F))),
            )
            .padding(24.dp),
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = RowinIcons.ClosetOutlined,
                    contentDescription = null,
                    tint = PrimaryFixed,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = "  MY CLOSET",
                    style = MaterialTheme.typography.labelMedium,
                    color = PrimaryFixed,
                )
            }
            Text(
                text = "$itemCount Items",
                style = MaterialTheme.typography.headlineLarge,
                color = OnPrimary,
                modifier = Modifier.padding(top = 12.dp),
            )
            Text(
                text = if (newThisWeek > 0) {
                    "$newThisWeek new additions this week. Explore your curated collection."
                } else {
                    "Start curating - add your first pieces to your collection."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = OnPrimaryContainer,
                modifier = Modifier.padding(top = 4.dp),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy((-8).dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    items.forEach { item ->
                        val file = imageFileFor(item)
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .border(2.dp, PrimaryContainer, CircleShape)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceContainer),
                        ) {
                            if (file != null) {
                                AsyncImage(
                                    model = file,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.weight(1f))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "View wardrobe",
                        style = MaterialTheme.typography.labelMedium,
                        color = OnPrimary,
                    )
                    Icon(
                        imageVector = RowinIcons.Forward,
                        contentDescription = null,
                        tint = OnPrimary,
                        modifier = Modifier
                            .padding(start = 4.dp)
                            .size(16.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun QuickCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    trailing: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Column(
        modifier = modifier
            .scale(if (pressed) 0.98f else 1f)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .border(1.dp, MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(20.dp))
            .padding(20.dp)
            .height(96.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.weight(1f))
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerLow),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = trailing,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 4.dp),
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun RecentItemCard(
    item: ClothingItemEntity,
    imageFile: File?,
    onClick: () -> Unit,
) {
    Column(modifier = Modifier.width(140.dp).clickable(onClick = onClick)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                .border(1.dp, MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center,
        ) {
            if (imageFile != null) {
                AsyncImage(
                    model = imageFile,
                    contentDescription = item.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Icon(
                    imageVector = RowinIcons.ImagePlaceholder,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(28.dp),
                )
            }
        }
        Text(
            text = item.name,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp),
        )
        Text(
            text = item.category.displayName(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline,
        )
    }
}
