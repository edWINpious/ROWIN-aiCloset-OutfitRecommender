package com.example.rowin_adlproject.ui.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.rowin_adlproject.data.ClothingItemEntity
import com.example.rowin_adlproject.data.ImageStore
import com.example.rowin_adlproject.data.OutfitWithItems
import com.example.rowin_adlproject.data.WardrobeRepository
import com.example.rowin_adlproject.ui.rowinContainer
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import java.io.File
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * Home Dashboard (feature #2): dynamic greeting, live wardrobe/outfit counts,
 * items added this week, and the recently-added rail.
 */
class HomeViewModel(
    context: Context,
    repository: WardrobeRepository,
    val imageStore: ImageStore,
    userId: Long,
) : ViewModel() {

    val clothingCount: StateFlow<Int> =
        repository.observeClothingCount(userId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val outfitsCount: StateFlow<Int> =
        repository.observeOutfitCount(userId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val newThisWeek: StateFlow<Int> =
        repository.observeClothingCountSince(userId, System.currentTimeMillis() - TimeUnit.DAYS.toMillis(7))
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    /** Most recent items for the horizontal rail. */
    val recentItems: StateFlow<List<ClothingItemEntity>> =
        repository.observeClothing(userId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val recentOutfits: StateFlow<List<OutfitWithItems>> =
        repository.observeOutfits(userId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun imageFileFor(item: ClothingItemEntity): File? = imageStore.fileFor(item.imageFileName)

    companion object {
        /**
         * "Good morning/afternoon/evening" from device time.
         *
         * There is deliberately no night bucket: "Good night" is never shown,
         * so late hours fall through to evening.
         */
        fun greetingFor(hourOfDay: Int): String = when (hourOfDay) {
            in 5..11 -> "Good morning"
            in 12..16 -> "Good afternoon"
            else -> "Good evening"
        }

        fun currentGreeting(): String = greetingFor(Calendar.getInstance().get(Calendar.HOUR_OF_DAY))
    }

    class Factory(private val context: Context, private val userId: Long) :
        androidx.lifecycle.ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val container = context.rowinContainer()
            return HomeViewModel(context, container.repository, container.repository.imageStore, userId) as T
        }
    }
}
