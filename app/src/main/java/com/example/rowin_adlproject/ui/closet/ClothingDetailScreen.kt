package com.example.rowin_adlproject.ui.closet

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
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.example.rowin_adlproject.data.ClothingItemEntity
import com.example.rowin_adlproject.ui.components.RowinButton
import com.example.rowin_adlproject.ui.components.RowinIcons
import com.example.rowin_adlproject.ui.components.RowinOutlinedButton
import com.example.rowin_adlproject.ui.components.displayName
import com.example.rowin_adlproject.ui.theme.Error
import com.example.rowin_adlproject.ui.theme.ErrorContainer
import java.io.File

/**
 * Feature #7 - Clothing Details: full info, AI attributes, Edit and Delete
 * (with confirmation bottom sheet per feature #18).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClothingDetailScreen(
    itemId: Long,
    userId: Long,
    onBack: () -> Unit,
    onEdit: (ClothingItemEntity) -> Unit,
    onDeleted: () -> Unit,
) {
    val context = LocalContext.current
    val factory = remember(userId) { ClosetViewModel.Factory(context, userId) }
    val vm: ClosetViewModel = viewModel(factory = factory)
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    val item by vm.repository.observeClothingById(itemId).collectAsState(initial = null)
    var showDeleteSheet by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            // Detail header
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
                    text = "Clothing Detail",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                )
                IconButton(onClick = { item?.let(onEdit) }) {
                    Icon(
                        imageVector = RowinIcons.Edit,
                        contentDescription = "Edit item",
                        tint = MaterialTheme.colorScheme.primaryContainer,
                    )
                }
            }

            // Hero image
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .aspectRatio(4f / 5f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                    .border(1.dp, MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center,
            ) {
                val file: File? = item?.imageFileName?.let { vm.imageStore.fileFor(it) }
                if (file != null) {
                    AsyncImage(
                        model = file,
                        contentDescription = item?.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Icon(
                        imageVector = RowinIcons.ImagePlaceholder,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(56.dp),
                    )
                }
            }

            // Name
            Text(
                text = item?.name ?: "",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 20.dp),
            )

            // Bento metadata grid
            Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    MetadataCard(
                        icon = RowinIcons.Checkroom,
                        label = "CATEGORY",
                        value = item?.category?.displayName(),
                        modifier = Modifier.weight(1f),
                    )
                    MetadataCard(
                        icon = RowinIcons.Palette,
                        label = "COLOR",
                        value = item?.color,
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    MetadataCard(
                        icon = RowinIcons.CalendarMonth,
                        label = "SEASON",
                        value = item?.season?.displayName(),
                        modifier = Modifier.weight(1f),
                    )
                    MetadataCard(
                        icon = RowinIcons.LocalCafe,
                        label = "OCCASION",
                        value = item?.occasion?.displayName(),
                        modifier = Modifier.weight(1f),
                    )
                }
                if (!item?.pattern.isNullOrBlank() || !item?.style.isNullOrBlank() || !item?.material.isNullOrBlank()) {
                    Spacer(Modifier.height(12.dp))
                    MetadataCard(
                        icon = RowinIcons.AutoAwesome,
                        label = "AI ATTRIBUTES",
                        value = listOfNotNull(
                            item?.pattern,
                            item?.style,
                            item?.material,
                        ).joinToString(" - ").ifEmpty { null },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (!item?.aiDescription.isNullOrBlank()) {
                    Spacer(Modifier.height(12.dp))
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "AI DESCRIPTION",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.secondary,
                            )
                            Text(
                                text = item?.aiDescription ?: "",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(32.dp))
            // Delete action
            RowinOutlinedButton(
                text = "Delete Item",
                onClick = { showDeleteSheet = true },
                danger = true,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
            Spacer(Modifier.height(24.dp))
        }
    }

    // Delete confirmation bottom sheet (feature #18, per Stitch design)
    if (showDeleteSheet && item != null) {
        ModalBottomSheet(
            onDismissRequest = { showDeleteSheet = false },
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            DeleteItemSheetContent(
                itemName = item?.name ?: "",
                onDelete = {
                    showDeleteSheet = false
                    val toDelete = item
                    if (toDelete != null) {
                        scope.launch {
                            vm.deleteItem(toDelete)
                            onDeleted()
                        }
                    }
                },
                onCancel = { showDeleteSheet = false },
            )
        }
    }
}

@Composable
private fun DeleteItemSheetContent(
    itemName: String,
    onDelete: () -> Unit,
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
                .size(48.dp)
                .background(ErrorContainer, RoundedCornerShape(24.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = RowinIcons.Delete,
                contentDescription = null,
                tint = Error,
            )
        }
        Text(
            text = "Delete Item?",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 16.dp),
        )
        Text(
            text = "Are you sure you want to remove \"$itemName\" from your closet? This action cannot be undone.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp),
        )
        RowinButton(
            text = "Delete",
            onClick = onDelete,
            modifier = Modifier.padding(top = 24.dp),
        )
        RowinOutlinedButton(
            text = "Cancel",
            onClick = onCancel,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
private fun MetadataCard(
    icon: ImageVector,
    label: String,
    value: String?,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant),
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(20.dp),
            )
            Column(modifier = Modifier.padding(start = 12.dp)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary,
                )
                Text(
                    text = value ?: "-",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}
