package com.example.rowin_adlproject.ui.closet

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.rowin_adlproject.ai.AiKeys
import com.example.rowin_adlproject.ai.ClothingAnalyzer
import com.example.rowin_adlproject.ai.GroqClient
import com.example.rowin_adlproject.data.ClothingItemEntity
import com.example.rowin_adlproject.data.Category
import com.example.rowin_adlproject.data.ImageStore
import com.example.rowin_adlproject.data.Occasion
import com.example.rowin_adlproject.data.Season
import com.example.rowin_adlproject.data.AiAttributes
import com.example.rowin_adlproject.data.WardrobeRepository
import com.example.rowin_adlproject.ui.rowinContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File

/**
 * Add/Edit Clothing (features #5-#7): holds the draft, imports photos from
 * camera/gallery, runs Groq AI analysis, and persists the item.
 */
class AddClothingViewModel(
    context: Context,
    private val repository: WardrobeRepository,
    val imageStore: ImageStore,
    private val userId: Long,
    /** Non-null when editing an existing item. */
    val editingItem: ClothingItemEntity?,
) : ViewModel() {

    val name = MutableStateFlow(editingItem?.name ?: "")
    val category = MutableStateFlow(editingItem?.category ?: Category.TOPS)
    val color = MutableStateFlow(editingItem?.color ?: "")
    val season = MutableStateFlow(editingItem?.season ?: Season.ALL_SEASON)
    val occasion = MutableStateFlow(editingItem?.occasion ?: Occasion.CASUAL)
    val pattern = MutableStateFlow(editingItem?.pattern ?: "")
    val style = MutableStateFlow(editingItem?.style ?: "")
    val material = MutableStateFlow(editingItem?.material ?: "")

    private val _imageFileName = MutableStateFlow(editingItem?.imageFileName)
    val imageFileName: StateFlow<String?> = _imageFileName

    private val _analysisState = MutableStateFlow<AnalysisState>(AnalysisState.Idle)
    val analysisState: StateFlow<AnalysisState> = _analysisState

    private val analyzer = ClothingAnalyzer(GroqClient())

    val isAiConfigured: Boolean get() = AiKeys.isGroqConfigured

    /** Uri the system-camera flow writes into while a capture is in flight. */
    var pendingCameraUri: Uri? = null

    /** Creates a content Uri in cache/camera/ for TakePicture (feature #20). */
    fun newCameraCaptureUri(context: Context): Uri? = runCatching {
        val dir = File(context.cacheDir, "camera").apply { mkdirs() }
        val file = File(dir, "capture_${System.currentTimeMillis()}.jpg")
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }.getOrNull()

    /**
     * Imports a photo captured by the in-app CameraX screen. The capture
     * already lives in app storage, so it is simply committed to the store.
     */
    fun importCameraFile(file: File) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            runCatching { imageStore.commitPending(file) }
                .onSuccess { name ->
                    if (name != null) _imageFileName.value = name
                }
                .onFailure {
                    _analysisState.value =
                        AnalysisState.Failed("Could not save the capture: ${it.message}")
                }
        }
    }

    /** Imports a gallery pick at full quality (practical #7 multimedia). */
    fun importFullImage(uri: Uri) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            runCatching { imageStore.importFullFromUri(uri) }
                .onSuccess { _imageFileName.value = it }
                .onFailure {
                    _analysisState.value =
                        AnalysisState.Failed("Could not load image: ${it.message}")
                }
        }
    }

    sealed interface AnalysisState {
        data object Idle : AnalysisState
        data object Analyzing : AnalysisState
        data class Done(val attributes: AiAttributes, val applied: Boolean) : AnalysisState
        data class Failed(val message: String) : AnalysisState
    }

    /** Imports the picked/captured image and immediately offers AI analysis. */
    fun importImage(uri: Uri) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            runCatching { imageStore.importFromUri(uri) }
                .onSuccess { _imageFileName.value = it }
                .onFailure { _analysisState.value = AnalysisState.Failed("Could not load image: ${it.message}") }
        }
    }

    /**
     * Feature #6 - Groq vision analysis of the imported photo.
     * Results prefill the form; the user can apply or ignore them.
     */
    fun analyzeWithAi() {
        val file = _imageFileName.value ?: run {
            _analysisState.value = AnalysisState.Failed("Add a photo first.")
            return
        }
        _analysisState.value = AnalysisState.Analyzing
        viewModelScope.launch {
            val base64 = imageStore.toBase64(file)
            val result = if (base64 != null) analyzer.analyze(base64) else null
            result
                ?.onSuccess { attrs ->
                    _analysisState.value = AnalysisState.Done(attrs, applied = false)
                }
                ?.onFailure {
                    _analysisState.value = AnalysisState.Failed(
                        it.message?.take(160) ?: "Analysis failed. Check your Groq API key.",
                    )
                }
                ?: run { _analysisState.value = AnalysisState.Failed("Could not read the image.") }
        }
    }

    /** Applies the AI result to the form fields (called from the Apply chip). */
    fun applyAnalysis() {
        val state = _analysisState.value
        if (state is AnalysisState.Done && !state.applied) {
            state.attributes.category?.let { category.value = it }
            state.attributes.colorName?.let { color.value = it }
            state.attributes.season?.let { season.value = it }
            state.attributes.occasion?.let { occasion.value = it }
            state.attributes.pattern?.let { pattern.value = it }
            state.attributes.style?.let { style.value = it }
            state.attributes.material?.let { material.value = it }
            if (name.value.isBlank()) {
                state.attributes.rawDescription?.let { name.value = it.take(40) }
            }
            _analysisState.value = state.copy(applied = true)
        }
    }

    fun save(onSaved: (Long) -> Unit, onError: (String) -> Unit) {
        val trimmedName = name.value.trim()
        if (trimmedName.isEmpty()) {
            onError("Give the item a name first.")
            return
        }
        viewModelScope.launch {
            val existing = editingItem
            val entity = ClothingItemEntity(
                id = existing?.id ?: 0,
                userId = existing?.userId ?: userId,
                name = trimmedName,
                category = category.value,
                color = color.value.trim().ifEmpty { "Unspecified" },
                imageFileName = _imageFileName.value,
                season = season.value,
                occasion = occasion.value,
                pattern = pattern.value.trim().ifEmpty { null },
                style = style.value.trim().ifEmpty { null },
                material = material.value.trim().ifEmpty { null },
                aiDescription = (analysisState.value as? AnalysisState.Done)?.attributes?.rawDescription
                    ?: existing?.aiDescription,
                createdAt = existing?.createdAt ?: System.currentTimeMillis(),
            )
            runCatching {
                if (existing == null) repository.addClothing(entity) else {
                    repository.updateClothing(entity)
                    existing.id
                }
            }.onSuccess(onSaved).onFailure { onError(it.message ?: "Could not save the item.") }
        }
    }

    class Factory(
        private val context: Context,
        private val userId: Long,
        private val editing: ClothingItemEntity?,
    ) : androidx.lifecycle.ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val container = context.rowinContainer()
            return AddClothingViewModel(
                context, container.repository, container.repository.imageStore, userId, editing,
            ) as T
        }
    }
}
