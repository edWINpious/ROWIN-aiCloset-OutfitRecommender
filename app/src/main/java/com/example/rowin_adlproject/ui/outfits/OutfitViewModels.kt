package com.example.rowin_adlproject.ui.outfits

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.rowin_adlproject.ai.AiKeys
import com.example.rowin_adlproject.ai.GroqClient
import com.example.rowin_adlproject.ai.OutfitRecommender
import com.example.rowin_adlproject.data.ClothingItemEntity
import com.example.rowin_adlproject.data.Category
import com.example.rowin_adlproject.data.ImageStore
import com.example.rowin_adlproject.data.Occasion
import com.example.rowin_adlproject.data.OutfitEntity
import com.example.rowin_adlproject.data.OutfitWithItems
import com.example.rowin_adlproject.data.Season
import com.example.rowin_adlproject.data.WardrobeRepository
import com.example.rowin_adlproject.ui.rowinContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

/**
 * Outfit Planner (features #8-#11): slots for each category, manual picking,
 * and "Generate Outfit" via the AI recommender (feature #21).
 */
class PlannerViewModel(
    context: Context,
    private val repository: WardrobeRepository,
    val imageStore: ImageStore,
    private val userId: Long,
    /** Non-null when editing an existing outfit (feature #12). */
    private val editingOutfitId: Long?,
) : ViewModel() {

    /** Loaded once when editing. */
    private val _editingOutfit = MutableStateFlow<OutfitWithItems?>(null)
    val editingOutfit: StateFlow<OutfitWithItems?> = _editingOutfit

    init {
        editingOutfitId?.let { id ->
            viewModelScope.launch {
                _editingOutfit.value = repository.getOutfitById(id)
                _editingOutfit.value?.let { loaded -> restoreFrom(loaded) }
            }
        }
    }

    private fun restoreFrom(loaded: OutfitWithItems) {
        selectedTop.value = loaded.items.firstOrNull { it.category == Category.TOPS }?.id
        selectedBottom.value = loaded.items.firstOrNull { it.category == Category.BOTTOMS }?.id
        selectedShoes.value = loaded.items.firstOrNull { it.category == Category.SHOES }?.id
        selectedOuterwear.value = loaded.items.firstOrNull { it.category == Category.OUTERWEAR }?.id
        outfitName.value = loaded.outfit.name
        outfitSeason.value = loaded.outfit.season
        outfitOccasion.value = loaded.outfit.occasion
        _generationNote.value = loaded.outfit.aiNote
        nameFieldRevealed = true
    }

    /** True once the user has tapped Save (reveals the name field). */
    var nameFieldRevealed: Boolean = false

    val wardrobe: StateFlow<List<ClothingItemEntity>> =
        repository.observeClothing(userId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Slot -> selected item id. */
    val selectedTop = MutableStateFlow<Long?>(null)
    val selectedBottom = MutableStateFlow<Long?>(null)
    val selectedShoes = MutableStateFlow<Long?>(null)
    val selectedOuterwear = MutableStateFlow<Long?>(null)

    val outfitName = MutableStateFlow("")
    val outfitSeason = MutableStateFlow(Season.ALL_SEASON)
    val outfitOccasion = MutableStateFlow(Occasion.CASUAL)

    private val _generated = MutableStateFlow<List<OutfitRecommender.Suggestion>>(emptyList())
    val generated: StateFlow<List<OutfitRecommender.Suggestion>> = _generated

    private val _generating = MutableStateFlow(false)
    val generating: StateFlow<Boolean> = _generating

    private val _generationNote = MutableStateFlow<String?>(null)
    val generationNote: StateFlow<String?> = _generationNote

    private val recommender = OutfitRecommender(GroqClient())
    val isAiConfigured: Boolean get() = AiKeys.isGroqConfigured

    fun wardrobeForSlot(slot: Slot): List<ClothingItemEntity> =
        wardrobe.value.filter { it.category == slot.category }

    fun selectInSlot(slot: Slot, itemId: Long?) {
        when (slot) {
            Slot.TOP -> selectedTop.value = itemId
            Slot.BOTTOM -> selectedBottom.value = itemId
            Slot.SHOES -> selectedShoes.value = itemId
            Slot.OUTERWEAR -> selectedOuterwear.value = itemId
        }
    }

    fun selectionForSlot(slot: Slot): Long? = when (slot) {
        Slot.TOP -> selectedTop.value
        Slot.BOTTOM -> selectedBottom.value
        Slot.SHOES -> selectedShoes.value
        Slot.OUTERWEAR -> selectedOuterwear.value
    }

    /** Populates the slots from an AI suggestion. */
    fun applySuggestion(suggestion: OutfitRecommender.Suggestion) {
        val byId = wardrobe.value.associateBy { it.id }
        val items = suggestion.itemIds.mapNotNull { byId[it] }
        selectInSlot(Slot.TOP, items.firstOrNull { it.category == Category.TOPS }?.id)
        selectInSlot(Slot.BOTTOM, items.firstOrNull { it.category == Category.BOTTOMS }?.id)
        selectInSlot(Slot.SHOES, items.firstOrNull { it.category == Category.SHOES }?.id)
        selectInSlot(Slot.OUTERWEAR, items.firstOrNull { it.category == Category.OUTERWEAR }?.id)
        outfitSeason.value = suggestion.season
        outfitOccasion.value = suggestion.occasion
        if (outfitName.value.isBlank()) outfitName.value = suggestion.name
        _generationNote.value = suggestion.note
    }

    /**
     * Feature #9/21 - "Generate Outfit": asks Groq for complete outfits built
     * from the current wardrobe; falls back to the local stylist offline.
     */
    fun generateOutfits() {
        if (_generating.value) return
        _generating.value = true
        _generationNote.value = null
        viewModelScope.launch {
            val result = recommender.recommend(
                wardrobe = wardrobe.value,
                season = outfitSeason.value.takeIf { it != Season.ALL_SEASON },
                occasion = outfitOccasion.value,
            )
            _generated.value = result.getOrElse { emptyList() }
            if (result.isSuccess && _generated.value.isNotEmpty()) {
                _generationNote.value =
                    if (_generated.value.first().fromAi) "Generated with Groq AI" else "Matched locally (offline mode)"
            } else {
                _generationNote.value = "Could not generate outfits - add more items first."
            }
            _generating.value = false
        }
    }

    fun selectedItemCount(): Int = listOf(
        selectedTop.value, selectedBottom.value, selectedShoes.value,
        selectedOuterwear.value,
    ).count { it != null }

    fun imageFileFor(item: ClothingItemEntity): File? = imageStore.fileFor(item.imageFileName)

    /** Feature #11 - saves (or updates) the outfit with the current slots. */
    fun save(onSaved: (Long) -> Unit, onError: (String) -> Unit) {
        val ids = listOfNotNull(
            selectedTop.value, selectedBottom.value, selectedShoes.value,
            selectedOuterwear.value,
        )
        if (ids.isEmpty()) {
            onError("Select at least one clothing piece.")
            return
        }
        val name = outfitName.value.trim().ifEmpty {
            "Outfit ${System.currentTimeMillis() % 1000}"
        }
        val existing = _editingOutfit.value
        val entity = OutfitEntity(
            id = existing?.outfit?.id ?: 0,
            userId = existing?.outfit?.userId ?: userId,
            name = name,
            season = outfitSeason.value,
            occasion = outfitOccasion.value,
            aiGenerated = existing?.outfit?.aiGenerated ?: _generated.value.isNotEmpty(),
            aiNote = _generationNote.value ?: existing?.outfit?.aiNote,
            createdAt = existing?.outfit?.createdAt ?: System.currentTimeMillis(),
        )
        viewModelScope.launch {
            runCatching {
                if (existing == null) repository.saveOutfit(entity, ids)
                else {
                    repository.updateOutfit(entity, ids)
                    entity.id
                }
            }.onSuccess(onSaved).onFailure { onError(it.message ?: "Could not save the outfit.") }
        }
    }

    enum class Slot(val category: Category, val label: String, val optional: Boolean) {
        // Per the Stitch outfit_planner design: Outerwear (optional), Top,
        // Bottom, Shoes. Accessories stay saveable via AI suggestions.
        OUTERWEAR(Category.OUTERWEAR, "Outerwear", optional = true),
        TOP(Category.TOPS, "Top", optional = false),
        BOTTOM(Category.BOTTOMS, "Bottom", optional = false),
        SHOES(Category.SHOES, "Shoes", optional = false),
    }

    class Factory(
        private val context: Context,
        private val userId: Long,
        private val editingOutfitId: Long?,
    ) : androidx.lifecycle.ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val container = context.rowinContainer()
            return PlannerViewModel(context, container.repository, container.repository.imageStore, userId, editingOutfitId) as T
        }
    }
}

/**
 * Saved Outfits list (feature #14) and details (feature #10) + removal (#13).
 */
class OutfitsViewModel(
    context: Context,
    private val repository: WardrobeRepository,
    val imageStore: ImageStore,
    private val userId: Long,
) : ViewModel() {

    val outfits: StateFlow<List<OutfitWithItems>> =
        repository.observeOutfits(userId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun outfit(outfitId: Long): StateFlow<OutfitWithItems?> =
        repository.observeOutfit(outfitId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun imageFileFor(item: ClothingItemEntity): File? = imageStore.fileFor(item.imageFileName)

    /** Feature #13 - remove outfit, with confirmation handled in the UI. */
    fun deleteOutfit(outfit: OutfitWithItems) {
        viewModelScope.launch { repository.deleteOutfit(outfit.outfit) }
    }

    class Factory(private val context: Context, private val userId: Long) :
        androidx.lifecycle.ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val container = context.rowinContainer()
            return OutfitsViewModel(context, container.repository, container.repository.imageStore, userId) as T
        }
    }
}
