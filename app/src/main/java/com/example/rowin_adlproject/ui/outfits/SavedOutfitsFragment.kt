package com.example.rowin_adlproject.ui.outfits

import android.graphics.Rect
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.rowin_adlproject.R
import com.example.rowin_adlproject.data.OutfitWithItems
import com.example.rowin_adlproject.databinding.ItemOutfitCardBinding
import com.example.rowin_adlproject.databinding.ScreenSavedOutfitsBinding
import com.example.rowin_adlproject.ui.components.displayName
import com.example.rowin_adlproject.ui.view.HostedFragment
import com.example.rowin_adlproject.ui.view.Loaders
import com.example.rowin_adlproject.ui.view.Screen
import com.example.rowin_adlproject.ui.view.Views

/**
 * Feature #14 - Saved Outfits: grid of previously saved outfits; opens the
 * detail screen on tap.
 */
class SavedOutfitsFragment : HostedFragment(R.layout.screen_saved_outfits) {

    private var _b: ScreenSavedOutfitsBinding? = null
    private val b get() = _b!!

    private lateinit var vm: OutfitsViewModel
    private lateinit var adapter: OutfitsAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _b = ScreenSavedOutfitsBinding.inflate(inflater, container, false)
        return b.root
    }

    override fun onViewReady(view: View) {
        vm = androidx.lifecycle.ViewModelProvider(
            this, Loaders.outfits(requireContext(), host.userId),
        )[OutfitsViewModel::class.java]

        adapter = OutfitsAdapter(vm::imageFileFor) { outfit ->
            host.open(Screen.OUTFIT_DETAIL, outfit.outfit.id)
        }
        b.savedGrid.layoutManager = GridLayoutManager(requireContext(), 2)
        b.savedGrid.addItemDecoration(SavedGridSpacing())
        b.savedGrid.adapter = adapter

        b.savedBack.setOnClickListener { host.back() }
        b.savedCreate.setOnClickListener { host.back() }

        vm.outfits.collectWithViewLifecycle { outfits ->
            b.savedCount.text = "${outfits.size} in your collection"
            adapter.submit(outfits)
            Views.toggle(
                showViews = if (outfits.isEmpty()) listOf(b.savedEmpty) else listOf(b.savedGrid),
                hideViews = if (outfits.isEmpty()) listOf(b.savedGrid) else listOf(b.savedEmpty),
            )
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _b = null
    }

    private class OutfitsAdapter(
        private val imageFileFor: (com.example.rowin_adlproject.data.ClothingItemEntity) -> java.io.File?,
        private val onClick: (OutfitWithItems) -> Unit,
    ) : RecyclerView.Adapter<OutfitsAdapter.Holder>() {

        private val outfits = mutableListOf<OutfitWithItems>()

        fun submit(newItems: List<OutfitWithItems>) {
            outfits.clear()
            outfits += newItems
            notifyDataSetChanged()
        }

        class Holder(val binding: ItemOutfitCardBinding) : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
            Holder(ItemOutfitCardBinding.inflate(LayoutInflater.from(parent.context), parent, false))

        override fun getItemCount(): Int = outfits.size

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val outfit = outfits[position]
            val binding = holder.binding
            val firstWithImage = outfit.items.firstOrNull { it.imageFileName != null }
            val bitmap = Views.decodeSampled(
                firstWithImage?.let { imageFileFor(it) }, 480,
            )
            if (bitmap != null) {
                binding.outfitImage.setImageBitmap(bitmap)
                Views.hide(binding.outfitImagePlaceholder)
            } else {
                binding.outfitImage.setImageDrawable(null)
                Views.show(binding.outfitImagePlaceholder)
            }
            binding.outfitName.text = outfit.outfit.name
            binding.outfitCaption.text =
                "${outfit.items.size} pieces - ${outfit.outfit.occasion.displayName()}"
            binding.outfitCard.setOnClickListener { onClick(outfit) }
        }
    }

    /** 12dp column gap, 16dp row gap. */
    private class SavedGridSpacing : RecyclerView.ItemDecoration() {
        override fun getItemOffsets(
            outRect: Rect,
            view: View,
            parent: RecyclerView,
            state: RecyclerView.State,
        ) {
            val span = (view.layoutParams as? GridLayoutManager.LayoutParams)?.spanIndex ?: 0
            outRect.left = if (span == 1) dp(view, 6) else 0
            outRect.right = if (span == 0) dp(view, 6) else 0
            outRect.bottom = dp(view, 16)
        }

        private fun dp(view: View, value: Int): Int =
            (value * view.resources.displayMetrics.density).toInt()
    }
}
