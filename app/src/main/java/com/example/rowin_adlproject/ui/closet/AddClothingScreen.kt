package com.example.rowin_adlproject.ui.closet

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.example.rowin_adlproject.R
import com.example.rowin_adlproject.data.ClothingItemEntity
import com.example.rowin_adlproject.data.Category
import com.example.rowin_adlproject.data.Occasion
import com.example.rowin_adlproject.data.Season
import com.example.rowin_adlproject.ui.components.RowinButton
import com.example.rowin_adlproject.ui.components.RowinIcons
import com.example.rowin_adlproject.ui.components.RowinOutlinedButton
import com.example.rowin_adlproject.ui.components.RowinTextField
import com.example.rowin_adlproject.ui.components.displayName
import java.io.File

/**
 * Features #5-#7 & #20: Add (or edit) a clothing item - 1:1 with the Stitch
 * add_clothing design: header with close + ROWIN wordmark, 4:5 image area
 * with Camera / Gallery pill actions, the "Analyze with AI" panel, Item
 * Details form, and the fixed "Add to Closet" bottom bar.
 */
@Composable
fun AddClothingScreen(
    userId: Long,
    editingItem: ClothingItemEntity?,
    onDone: () -> Unit,
    onClose: () -> Unit,
    onOpenCamera: () -> Unit,
    pendingCameraFile: File?,
    onCameraConsumed: () -> Unit,
) {
    val context = LocalContext.current
    val factory = remember(userId, editingItem) {
        AddClothingViewModel.Factory(context, userId, editingItem)
    }
    val vm: AddClothingViewModel = viewModel(factory = factory)

    val name by vm.name.collectAsState()
    val category by vm.category.collectAsState()
    val color by vm.color.collectAsState()
    val season by vm.season.collectAsState()
    val occasion by vm.occasion.collectAsState()
    val pattern by vm.pattern.collectAsState()
    val style by vm.style.collectAsState()
    val material by vm.material.collectAsState()
    val imageFileName by vm.imageFileName.collectAsState()
    val analysis by vm.analysisState.collectAsState()

    var saveError by remember { mutableStateOf<String?>(null) }

    // A capture returned from the in-app CameraX screen lands here.
    androidx.compose.runtime.LaunchedEffect(pendingCameraFile) {
        pendingCameraFile?.let {
            vm.importCameraFile(it)
            onCameraConsumed()
        }
    }

    // Gallery picker (feature #20 / practical #7 multimedia) - photo picker
    // on modern Android, GetContent fallback elsewhere.
    val galleryPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri: Uri? -> uri?.let(vm::importFullImage) }
    val galleryFallback = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { uri: Uri? -> uri?.let(vm::importFullImage) }

    fun launchGallery() {
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            galleryPicker.launch(
                androidx.activity.result.PickVisualMediaRequest(
                    ActivityResultContracts.PickVisualMedia.ImageOnly,
                ),
            )
        } else {
            galleryFallback.launch("image/*")
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding(),
    ) {
        // ---- Header: close + centered ROWIN + spacer (per mock)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            androidx.compose.material3.IconButton(onClick = onClose) {
                Icon(imageVector = RowinIcons.Close, contentDescription = "Close")
            }
            androidx.compose.foundation.Image(
                painter = androidx.compose.ui.res.painterResource(R.drawable.wordmark_rowin),
                contentDescription = "ROWIN",
                modifier = Modifier
                    .weight(1f)
                    .height(16.dp),
                alignment = Alignment.Center,
            )
            Spacer(Modifier.size(48.dp))
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            // ---- Page title
            Text(
                text = if (editingItem == null) "Add Clothing" else "Edit Clothing",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "Add an item to your wardrobe.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp, bottom = 16.dp),
            )

            // ---- Image area with Camera / Gallery pills
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(4f / 5f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainer),
                contentAlignment = Alignment.Center,
            ) {
                val file: File? = imageFileName?.let { vm.imageStore.fileFor(it) }
                if (file != null) {
                    AsyncImage(
                        model = file,
                        contentDescription = "Clothing photo",
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
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    MediaChip(
                        icon = RowinIcons.CameraAlt,
                        label = "Camera",
                        onClick = {
                            val granted = ContextCompat.checkSelfPermission(
                                context, Manifest.permission.CAMERA,
                            ) == PackageManager.PERMISSION_GRANTED
                            if (granted) {
                                onOpenCamera()
                            } else {
                                // Screen will re-request on open; still ask here
                                // so the flow matches the mock's Camera pill.
                                onOpenCamera()
                            }
                        },
                    )
                    MediaChip(
                        icon = RowinIcons.PhotoLibrary,
                        label = "Gallery",
                        onClick = { launchGallery() },
                    )
                }
            }

            // ---- AI analysis panel (feature #6)
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.primaryFixed.copy(alpha = 0.28f),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f),
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = RowinIcons.AutoAwesome,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                        Text(
                            text = "  Analyze with AI",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Text(
                        text = "Automatically identify category, color, season and occasion.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
                    )
                    when (val state = analysis) {
                        is AddClothingViewModel.AnalysisState.Analyzing -> {
                            Text(
                                text = "Analyzing your item with Groq AI...",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        is AddClothingViewModel.AnalysisState.Done -> {
                            Text(
                                text = buildString {
                                    append(state.attributes.colorName ?: "Color detected. ")
                                    append(state.attributes.pattern?.let { "$it. " } ?: "")
                                    append(state.attributes.style?.let { "$it. " } ?: "")
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            RowinButton(
                                text = if (state.applied) "Applied" else "Apply to fields",
                                onClick = vm::applyAnalysis,
                                modifier = Modifier.padding(top = 8.dp),
                                enabled = !state.applied,
                            )
                        }
                        is AddClothingViewModel.AnalysisState.Failed -> {
                            Text(
                                text = state.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                        AddClothingViewModel.AnalysisState.Idle -> Unit
                    }
                    RowinOutlinedButton(
                        text = "Analyze with AI",
                        onClick = vm::analyzeWithAi,
                        modifier = Modifier.padding(top = 8.dp),
                        enabled = imageFileName != null &&
                            analysis !is AddClothingViewModel.AnalysisState.Analyzing,
                    )
                }
            }

            // ---- Item details form
            Spacer(Modifier.height(24.dp))
            Text(
                text = "Item Details",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(16.dp))

            RowinTextField(value = name, onValueChange = { vm.name.value = it }, label = "Name", placeholder = "e.g. White Oxford Shirt")
            Spacer(Modifier.height(16.dp))
            RowinDropdown(
                label = "Category",
                options = Category.entries.map { it.displayName() },
                selectedIndex = Category.entries.indexOf(category),
                onSelect = { vm.category.value = Category.entries[it] },
            )
            Spacer(Modifier.height(16.dp))
            RowinTextField(value = color, onValueChange = { vm.color.value = it }, label = "Color", placeholder = "e.g. Navy Blue")
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth()) {
                Box(modifier = Modifier.weight(1f)) {
                    RowinDropdown(
                        label = "Season",
                        options = Season.entries.map { it.displayName() },
                        selectedIndex = Season.entries.indexOf(season),
                        onSelect = { vm.season.value = Season.entries[it] },
                    )
                }
                Box(modifier = Modifier.weight(1f)) {
                    RowinDropdown(
                        label = "Occasion",
                        options = Occasion.entries.map { it.displayName() },
                        selectedIndex = Occasion.entries.indexOf(occasion),
                        onSelect = { vm.occasion.value = Occasion.entries[it] },
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth()) {
                Box(modifier = Modifier.weight(1f)) {
                    RowinTextField(value = pattern, onValueChange = { vm.pattern.value = it }, label = "Pattern", placeholder = "e.g. Striped")
                }
                Box(modifier = Modifier.weight(1f)) {
                    RowinTextField(value = style, onValueChange = { vm.style.value = it }, label = "Style", placeholder = "e.g. Casual")
                }
            }
            Spacer(Modifier.height(16.dp))
            RowinTextField(value = material, onValueChange = { vm.material.value = it }, label = "Material", placeholder = "e.g. Cotton")

            if (saveError != null) {
                Text(
                    text = saveError!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
            Spacer(Modifier.height(24.dp))
        }

        // ---- Fixed bottom action bar (per mock)
        Surface(color = MaterialTheme.colorScheme.surface) {
            Column {
                com.example.rowin_adlproject.ui.components.HorizontalHairline()
                RowinButton(
                    text = if (editingItem == null) "Add to Closet" else "Save Changes",
                    onClick = {
                        vm.save(
                            onSaved = { onDone() },
                            onError = { saveError = it },
                        )
                    },
                    modifier = Modifier
                        .padding(20.dp)
                        .navigationBarsPadding(),
                )
            }
        }
    }
}

@Composable
private fun MediaChip(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.95f),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shadowElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(16.dp))
            Text(text = label, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(start = 6.dp))
        }
    }
}

/** Simple exposed dropdown used for Category / Season / Occasion. */
@Composable
fun RowinDropdown(
    label: String,
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 6.dp, start = 4.dp),
        )
        Box {
            Surface(
                onClick = { expanded = true },
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = options.getOrNull(selectedIndex) ?: "Select",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(
                        imageVector = Icons.Filled.ArrowDropDown,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            androidx.compose.material3.DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.background(MaterialTheme.colorScheme.surfaceContainerLowest),
            ) {
                options.forEachIndexed { index, option ->
                    androidx.compose.material3.DropdownMenuItem(
                        text = {
                            Text(
                                text = option,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (index == selectedIndex) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface,
                            )
                        },
                        onClick = {
                            onSelect(index)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}
