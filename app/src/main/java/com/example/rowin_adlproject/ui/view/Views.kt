package com.example.rowin_adlproject.ui.view

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import androidx.lifecycle.ViewModelProvider
import com.example.rowin_adlproject.data.ClothingItemEntity
import com.example.rowin_adlproject.ui.chat.ChatViewModel
import com.example.rowin_adlproject.ui.closet.AddClothingViewModel
import com.example.rowin_adlproject.ui.closet.ClosetViewModel
import com.example.rowin_adlproject.ui.home.HomeViewModel
import com.example.rowin_adlproject.ui.outfits.OutfitsViewModel
import com.example.rowin_adlproject.ui.outfits.PlannerViewModel
import com.example.rowin_adlproject.ui.RowinViewModel
import java.io.File

/** Common View helpers shared by every XML-backed screen. */
object Views {

    fun show(vararg views: View) = views.forEach { it.visibility = View.VISIBLE }

    fun hide(vararg views: View) = views.forEach { it.visibility = View.GONE }

    /** Shows the first block of views and hides the rest (ViewSwitcher role). */
    fun toggle(showViews: List<View>, hideViews: List<View>) {
        showViews.forEach { it.visibility = View.VISIBLE }
        hideViews.forEach { it.visibility = View.GONE }
    }

    /** Field value shown in detail cards; renders a dash when empty. */
    fun detailValue(value: String?): String = value?.takeIf { it.isNotBlank() } ?: "-"

    /** Joins the AI attribute values the same way ClothingCard/MetadataCard did. */
    fun fold(vararg parts: String?): String =
        parts.filterNotNull().filter { it.isNotBlank() }.joinToString(" - ")

    /** Decodes a local image file (null when missing/unreadable). */
    fun bitmapFor(file: File?): Bitmap? =
        file?.let { BitmapFactory.decodeFile(it.absolutePath) }

    /**
     * Memory-friendly decode for grid/rail thumbnails: samples the file down
     * to about [reqWidth] px wide before allocating the final bitmap.
     */
    fun decodeSampled(file: File?, reqWidth: Int): Bitmap? {
        if (file == null || !file.exists()) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= reqWidth) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        return BitmapFactory.decodeFile(file.absolutePath, options)
    }

    /** Async-style local image bind: bitmap when present, icon placeholder otherwise. */
    fun ImageView.bindImage(file: File?, placeholderRes: Int) {
        val bitmap = bitmapFor(file)
        if (bitmap != null) {
            setImageBitmap(bitmap)
        } else {
            setImageResource(placeholderRes)
        }
    }
}

/** Text-change callback mirroring Compose onValueChange semantics. */
fun EditText.onTextChanged(block: (String) -> Unit) {
    addTextChangedListener(object : android.text.TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        override fun afterTextChanged(s: android.text.Editable?) {
            block(s?.toString().orEmpty())
        }
    })
}

/** ViewModel factories kept in one registry (manual DI, no Hilt needed). */
object Loaders {
    fun rowin(context: Context): ViewModelProvider.Factory = RowinViewModel.factory(context)
    fun home(context: Context, userId: Long): ViewModelProvider.Factory =
        HomeViewModel.Factory(context, userId)
    fun closet(context: Context, userId: Long): ViewModelProvider.Factory =
        ClosetViewModel.Factory(context, userId)
    fun addClothing(context: Context, userId: Long, editing: ClothingItemEntity?): ViewModelProvider.Factory =
        AddClothingViewModel.Factory(context, userId, editing)
    fun planner(context: Context, userId: Long, editingOutfitId: Long?): ViewModelProvider.Factory =
        PlannerViewModel.Factory(context, userId, editingOutfitId)
    fun outfits(context: Context, userId: Long): ViewModelProvider.Factory =
        OutfitsViewModel.Factory(context, userId)
    fun chat(context: Context, userName: String?): ViewModelProvider.Factory =
        ChatViewModel.Factory(context, userName)
}
