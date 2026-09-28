package com.example.rowin_adlproject.ui.outfits

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.example.rowin_adlproject.ui.components.RowinButton
import com.example.rowin_adlproject.ui.components.RowinIcons
import com.example.rowin_adlproject.ui.components.RowinOutlinedButton
import com.example.rowin_adlproject.ui.components.displayName
import com.example.rowin_adlproject.ui.theme.Error
import com.example.rowin_adlproject.ui.theme.ErrorContainer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Feature #10 - Outfit Details: name, occasion, season, created date, the
 * item breakdown rail, plus Edit Outfit (#12) and Remove Outfit (#13, with
 * the confirmation bottom sheet from #18).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OutfitDetailScreen(
    outfitId: Long,
    userId: Long,
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
    onDeleted: () -> Unit,
) {
    val context = LocalContext.current
    val factory = remember(userId) { OutfitsViewModel.Factory(context, userId) }
    val vm: OutfitsViewModel = viewModel(factory = factory)
    val outfit by vm.outfit(outfitId).collectAsState()

    var showRemoveSheet by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(imageVector = RowinIcons.Back, contentDescription = "Back")
            }
            Text(
                text = "Outfit Details",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
            )
            IconButton(onClick = { showRemoveSheet = true }) {
                Icon(
                    imageVector = RowinIcons.Delete,
                    contentDescription = "Remove outfit",
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }

        val current = outfit
        if (current == null) {
            Text(
                text = "Loading...",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(24.dp),
            )
            return@Column
        }

        // Hero: first item image on green
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .aspectRatio(3f / 4f)
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            val heroFile = current.items.firstOrNull { it.imageFileName != null }
                ?.let { vm.imageFileFor(it) }
            if (heroFile != null) {
                AsyncImage(
                    model = heroFile,
                    contentDescription = current.outfit.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Icon(
                    imageVector = RowinIcons.Styler,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(48.dp),
                )
            }
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.92f),
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(12.dp),
            ) {
                Text(
                    text = current.outfit.name,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
        }

        // Metadata cards
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 20.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutfitMetaCard(
                    icon = RowinIcons.CalendarMonth,
                    label = "Season",
                    value = current.outfit.season.displayName(),
                    modifier = Modifier.weight(1f),
                )
                OutfitMetaCard(
                    icon = RowinIcons.Styler,
                    label = "Occasion",
                    value = current.outfit.occasion.displayName(),
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(12.dp))
            OutfitMetaCard(
                icon = RowinIcons.History,
                label = "Created",
                value = SimpleDateFormat("EEEE, MMMM d'th' yyyy", Locale.getDefault())
                    .format(Date(current.outfit.createdAt)),
                modifier = Modifier.fillMaxWidth(),
            )
            current.outfit.aiNote?.let { note ->
                Spacer(Modifier.height(12.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryFixed.copy(alpha = 0.3f),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = RowinIcons.AutoAwesome,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            text = "  $note",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // Outfit breakdown
            Text(
                text = "Outfit Breakdown",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 24.dp, bottom = 12.dp),
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(current.items.toList(), key = { it.id }) { item ->
                    Column(modifier = Modifier.size(width = 120.dp, height = 160.dp)) {
                        Box(
                            modifier = Modifier
                                .size(width = 120.dp, height = 120.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                                .border(1.dp, MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center,
                        ) {
                            val file = vm.imageFileFor(item)
                            if (file != null) {
                                AsyncImage(
                                    model = file,
                                    contentDescription = item.name,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            } else {
                                Icon(
                                    imageVector = RowinIcons.ImagePlaceholder,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.outline,
                                    modifier = Modifier.size(24.dp),
                                )
                            }
                        }
                        Text(
                            text = item.name,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                        Text(
                            text = item.category.displayName(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline,
                        )
                    }
                }
            }

            Spacer(Modifier.height(28.dp))
            RowinButton(
                text = "Edit Outfit",
                onClick = { onEdit(current.outfit.id) },
            )
            Spacer(Modifier.height(8.dp))
            RowinOutlinedButton(
                text = "Remove Outfit",
                onClick = { showRemoveSheet = true },
                danger = true,
            )
            Spacer(Modifier.height(32.dp))
        }
    }

    // Remove outfit confirmation (feature #13 + #18)
    if (showRemoveSheet && outfit != null) {
        ModalBottomSheet(
            onDismissRequest = { showRemoveSheet = false },
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            RemoveOutfitSheetContent(
                outfitName = outfit!!.outfit.name,
                onRemove = {
                    showRemoveSheet = false
                    vm.deleteOutfit(outfit!!)
                    onDeleted()
                },
                onCancel = { showRemoveSheet = false },
            )
        }
    }
}

@Composable
private fun OutfitMetaCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant),
        modifier = modifier,
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(14.dp),
                )
                Text(
                    text = "  $label",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun RemoveOutfitSheetContent(
    outfitName: String,
    onRemove: () -> Unit,
    onCancel: () -> Unit,
) {
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
                .background(ErrorContainer.copy(alpha = 0.4f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(imageVector = RowinIcons.Delete, contentDescription = null, tint = Error)
        }
        Text(
            text = "Remove Outfit?",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(top = 16.dp),
        )
        Text(
            text = "Are you sure you want to remove \"$outfitName\" from your saved outfits? This action cannot be undone.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp),
        )
        RowinButton(
            text = "Remove",
            onClick = onRemove,
            modifier = Modifier.padding(top = 24.dp),
        )
        RowinOutlinedButton(
            text = "Cancel",
            onClick = onCancel,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}
