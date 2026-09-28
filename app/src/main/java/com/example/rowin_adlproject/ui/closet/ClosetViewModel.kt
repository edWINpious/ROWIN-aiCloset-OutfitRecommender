package com.example.rowin_adlproject.ui.closet

import android.app.Application
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.rowin_adlproject.data.ClothingItemEntity
import com.example.rowin_adlproject.data.Category
import com.example.rowin_adlproject.data.ImageStore
import com.example.rowin_adlproject.data.WardrobeRepository
import com.example.rowin_adlproject.ui.rowinContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.io.File

/**
 * Drives My Closet (features #3-#5): full list + search + category filter,
 * all scoped to the signed-in user.
 */
class ClosetViewModel(
    context: Context,
    val repository: WardrobeRepository,
    val imageStore: ImageStore,
    private val userId: Long,
) : ViewModel() {

    val searchQuery = MutableStateFlow("")
    val selectedCategory = MutableStateFlow<Category?>(null)

    val items: StateFlow<List<ClothingItemEntity>> =
        combine(repository.observeClothing(userId), searchQuery, selectedCategory) { all, query, category ->
            all.filter { item ->
                val matchesQuery = query.isBlank() ||
                    item.name.contains(query, ignoreCase = true) ||
                    item.color.contains(query, ignoreCase = true) ||
                    item.pattern?.contains(query, ignoreCase = true) == true ||
                    item.style?.contains(query, ignoreCase = true) == true
                val matchesCategory = category == null || item.category == category
                matchesQuery && matchesCategory
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun imageFileFor(item: ClothingItemEntity): File? = imageStore.fileFor(item.imageFileName)

    suspend fun deleteItem(item: ClothingItemEntity) = repository.deleteClothing(item)

    class Factory(private val context: Context, private val userId: Long) :
        androidx.lifecycle.ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val container = context.rowinContainer()
            return ClosetViewModel(context, container.repository, container.repository.imageStore, userId) as T
        }
    }
}
